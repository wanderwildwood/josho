#!/usr/bin/env python3
"""Write config/libraries and config/licenses entries for every bundled native package.

    native-licences.py <debs dir> <arch> <recipes dir> <config dir>

Gradle cannot see LilyPond, Guile and the libraries under them, so each gets an entry for the
About's Licenses drawer: name, version, homepage, the licence the recipe declares, and as its text
the package's own licence files (Termux installs them in share/doc/<package>/).
Run after a build; the package list is the one pack-prefix.py ships.
"""
import json, os, re, subprocess, sys, tempfile

debs, arch, recipes, config = sys.argv[1:5]
SKIP = {"python", "ghostscript", "ca-certificates", "ttf-dejavu"}
PREFIX = "data/data/com.wanderwildwood.josho/files/usr/share/doc/"


def control(path):
    o = subprocess.run(["dpkg-deb", "-f", path], capture_output=True, text=True, check=True).stdout
    return dict(re.findall(r"^([A-Za-z-]+): (.*)$", o, re.M))


pk = {}
for f in os.listdir(debs):
    if f.endswith(f"_{arch}.deb") or f.endswith("_all.deb"):
        c = control(os.path.join(debs, f))
        pk[c["Package"]] = (os.path.join(debs, f), c)
want, seen = ["lilypond", "libcrypt"], []
while want:
    p = want.pop()
    if p in seen or p in SKIP:
        continue
    seen.append(p)
    for d in re.split(r",\s*", pk[p][1].get("Depends", "")):
        if d:
            want.append(d.split("|")[0].strip().split(" ")[0])

os.makedirs(os.path.join(config, "libraries"), exist_ok=True)
os.makedirs(os.path.join(config, "licenses"), exist_ok=True)
for p in sorted(seen):
    deb, c = pk[p]
    recipe = open(os.path.join(recipes, p, "build.sh")).read()
    m = re.search(r'^TERMUX_PKG_LICENSE="?([^"\n]*)"?', recipe, re.M)
    declared = m.group(1) if m else "see licence text"
    m = re.search(r'^TERMUX_PKG_LICENSE_FILE="?([^"\n]*)"?', recipe, re.M)
    licence_files = {os.path.basename(x.strip()) for x in m.group(1).split(",")} if m else set()
    tmp = tempfile.mkdtemp()
    subprocess.run(["dpkg-deb", "-x", deb, tmp], check=True)
    doc = os.path.join(tmp, PREFIX, p)
    texts = []
    if os.path.isdir(doc):
        for f in sorted(os.listdir(doc)):
            # Licence files only: some packages also put their ChangeLog or manual here.
            if not (re.search(r"(?i)copying|licen[cs]e|copyright|notice|^ofl|gust|lppl|^l?gpl-", f)
                    or f in licence_files):
                continue
            full = os.path.join(doc, f)
            if os.path.islink(full) and "LICENSES/" in os.readlink(full):
                # Termux links common texts to its termux-licenses package; take them from there.
                full = os.path.join(recipes, "termux-licenses", "LICENSES", os.path.basename(os.readlink(full)))
            if os.path.isfile(full):
                body = open(full, encoding="utf-8", errors="replace").read().strip()
                texts.append(f"{f}\n\n{body}")
    subprocess.run(["rm", "-rf", tmp])
    if not texts:
        sys.exit(f"{p}: no licence files in the package")
    lid = f"josho-native-{p}"
    json.dump({
        "uniqueId": f"native:{p}",
        "name": f"{p} {c['Version']} (native, in the bundled LilyPond)",
        "description": c.get("Description", ""),
        "website": c.get("Homepage", ""),
        "licenses": [lid],
    }, open(os.path.join(config, "libraries", f"native-{p}.json"), "w"), indent=2, ensure_ascii=False)
    json.dump({
        "hash": lid,
        "name": declared,
        "url": c.get("Homepage", ""),
        "content": "\n\n----\n\n".join(texts),
    }, open(os.path.join(config, "licenses", f"native-{p}.json"), "w"), indent=2, ensure_ascii=False)
print(len(seen), "native entries written")
