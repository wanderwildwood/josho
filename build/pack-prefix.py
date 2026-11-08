#!/usr/bin/env python3
"""Turn the built .deb files into what the app ships.

    pack-prefix.py <debs dir> <arch: x86_64|aarch64> <out dir> [--strip <llvm-strip>] [--go-keep <file>] [--extra <dir>]

<out dir> is the flavor's source set (app/src/arm64 or app/src/x86). Writes:
  jniLibs/<abi>/liblilypond_exe.so        LilyPond's executable (Android runs it from there)
  assets/prefix-<dir>.zip                 the rest of the prefix, paths relative to files/
  assets/prefix-<dir>-symlinks.txt        "link<TAB>target" lines, since zip keeps no symlinks

What goes in, and why it is smaller than the packages it comes from:
  - LilyPond's runtime dependencies only; not python (helper scripts), ghostscript (the Cairo
    backend writes PDF and SVG), ca-certificates or ttf-dejavu (LilyPond's own fonts are
    Emmentaler and TeX Gyre Schola, Heros and Cursor).
  - Shared libraries only if the executable reaches them through NEEDED entries; from openssl
    that is libcrypto alone (libcrypt, which Guile links, is built on it).
  - Of TeX Gyre, the three families LilyPond uses.
  - No headers, static libraries, docs, man/info pages, translations, introspection data,
    X11 locale data, LilyPond's python and vim files, or programs other than lilypond.
  - With --strip: every shared library and the executable stripped. Guile's .go files are
    ELF too but must keep their sections, so they are never stripped.
  - With --extra: every file under <dir> too, at its path relative to <dir> (relative to files/
    in the app). Used for guile-cache/, LilyPond's own Scheme compiled by Guile (see README).
  - With --go-keep: only the listed Guile .go files (the ones LilyPond was seen to load). The
    Scheme sources all stay, and the app runs Guile with auto-compilation off, so a module
    whose .go was left out still loads, interpreted.
"""
import os, re, shutil, subprocess, sys, tempfile, zipfile

args = sys.argv[1:]
debs, arch, out = args[0], args[1], args[2]
strip = args[args.index("--strip") + 1] if "--strip" in args else None
extra = args[args.index("--extra") + 1] if "--extra" in args else None
go_keep = None
if "--go-keep" in args:
    go_keep = {l.strip() for l in open(args[args.index("--go-keep") + 1]) if l.strip() and not l.startswith("#")}
ABI = {"x86_64": ("x86_64", "x86_64"), "aarch64": ("arm64-v8a", "arm64")}[arch]
PREFIX = "data/data/com.wanderwildwood.josho/files/"
SKIP = {"python", "ghostscript", "ca-certificates", "ttf-dejavu"}
DROP = re.compile(
    r"^usr/(include|share/(doc|man|info|gir-1\.0|locale|bash-completion|aclocal|gtk-doc|vala|X11"
    r"|lilypond/[^/]+/(python|vim))|lib/(pkgconfig|cmake|girepository-1\.0)|etc/tls)(/|$)"
    r"|\.a$|\.la$")
FONTS = re.compile(r"^usr/share/fonts/tex-gyre/(?!texgyre(schola|heros|cursor)-)")
SYSTEM = {"libc.so", "libm.so", "libdl.so", "liblog.so", "libandroid.so", "libz.so"}


def control(path):
    o = subprocess.run(["dpkg-deb", "-f", path], capture_output=True, text=True, check=True).stdout
    return dict(re.findall(r"^([A-Za-z-]+): (.*)$", o, re.M))


def needed(path):
    o = subprocess.run(["readelf", "-d", path], capture_output=True, text=True).stdout
    return re.findall(r"\(NEEDED\)\s+Shared library: \[(.+?)\]", o)


def is_elf(path):
    with open(path, "rb") as fh:
        return fh.read(4) == b"\x7fELF"


pk = {}
for f in os.listdir(debs):
    if f.endswith(f"_{arch}.deb") or f.endswith("_all.deb"):
        c = control(os.path.join(debs, f))
        pk[c["Package"]] = (os.path.join(debs, f), c)

# Guile links libcrypt when it finds it in the build prefix, but does not declare it.
want, seen = ["lilypond", "libcrypt"], []
while want:
    p = want.pop()
    if p in seen or p in SKIP:
        continue
    if p not in pk:
        sys.exit(f"missing package {p}")
    seen.append(p)
    for d in re.split(r",\s*", pk[p][1].get("Depends", "")):
        if d:
            want.append(d.split("|")[0].strip().split(" ")[0])
print(len(seen), "packages:", " ".join(sorted(seen)))

tmp = tempfile.mkdtemp()
for p in seen:
    subprocess.run(["dpkg-deb", "-x", pk[p][0], tmp], check=True)
root = os.path.join(tmp, PREFIX)
lib = os.path.join(root, "usr/lib")
exe = os.path.join(root, "usr/bin/lilypond")

# Libraries reachable from the executable and from Guile's extensions, by soname or file name.
by_name = {f: os.path.join(lib, f) for f in os.listdir(lib)}
reach, todo = set(), [exe] + [os.path.join(d, f) for d, _, fs in os.walk(os.path.join(lib, "guile")) for f in fs if f.endswith(".so")]
missing = set()
while todo:
    path = todo.pop()
    for n in needed(os.path.realpath(path)):
        if n in SYSTEM or n in reach:
            continue
        if n not in by_name:
            missing.add(f"{n} (needed by {os.path.relpath(path, root)})")
            continue
        reach.add(n)
        todo.append(by_name[n])
if missing:
    sys.exit("missing libraries:\n  " + "\n  ".join(sorted(missing)))
real_reach = {os.path.realpath(by_name[n]) for n in reach}


def keep(rel, full):
    if DROP.search(rel) or FONTS.search(rel):
        return False
    if rel.startswith("usr/bin/"):
        return False
    if rel.startswith("usr/lib/") and "/" not in rel[len("usr/lib/"):] and ".so" in rel:
        return full in reach_paths
    if go_keep is not None and rel.endswith(".go") and "/guile/" in rel:
        return rel in go_keep
    return True


reach_paths = {by_name[n] for n in reach} | real_reach

jni = os.path.join(out, "jniLibs", ABI[0])
assets = os.path.join(out, "assets")
os.makedirs(jni, exist_ok=True)
os.makedirs(assets, exist_ok=True)
exe_out = os.path.join(jni, "liblilypond_exe.so")
shutil.copy2(exe, exe_out)
if strip:
    subprocess.run([strip, "--strip-unneeded", exe_out], check=True)

zpath = os.path.join(assets, f"prefix-{ABI[1]}.zip")
links, n, size = [], 0, 0
with zipfile.ZipFile(zpath, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
    for d, dirs, files in os.walk(root):
        dirs.sort()
        for f in sorted(files) + [x for x in dirs if os.path.islink(os.path.join(d, x))]:
            full = os.path.join(d, f)
            rel = os.path.relpath(full, root)
            if not keep(rel, full):
                continue
            if os.path.islink(full):
                links.append(f"{rel}\t{os.readlink(full)}")
                continue
            if not os.path.isfile(full):
                continue
            if strip and is_elf(full) and not rel.endswith(".go"):
                subprocess.run([strip, "--strip-unneeded", full], check=True)
            z.write(full, rel)
            n += 1
            size += os.path.getsize(full)
    if extra:
        for d, _, files in os.walk(extra):
            for f in sorted(files):
                full = os.path.join(d, f)
                z.write(full, os.path.relpath(full, extra))
                n += 1
                size += os.path.getsize(full)
# A symlink whose target was left out would dangle; drop it.
names = set(zipfile.ZipFile(zpath).namelist())
links = [l for l in links if os.path.normpath(os.path.join(os.path.dirname(l.split("\t")[0]), l.split("\t")[1])) in names
         or l.split("\t")[1].startswith("/")]
with open(os.path.join(assets, f"prefix-{ABI[1]}-symlinks.txt"), "w") as fh:
    fh.write("\n".join(links) + "\n")
print(f"{n} files, {size // 1024} KB unpacked, zip {os.path.getsize(zpath) // 1024} KB, {len(links)} symlinks")
print("libraries kept:", len(reach), " executable", os.path.getsize(exe_out) // 1024, "KB")
shutil.rmtree(tmp)
