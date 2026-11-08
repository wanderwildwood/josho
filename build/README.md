# Building the bundled LilyPond

1. `build-lilypond.sh x86_64` then `aarch64` (a Linux machine with Docker): the termux-packages
   recipes, our prefix, `lilypond-recipe.patch`. Output: .deb files in `~/josho-build/tp/output*`.
2. `native-licences.py` → `config/` (the Licenses entries for every bundled package).
3. `pack-prefix.py <debs> <arch> app/src/<flavor> --strip <llvm-strip> --go-keep guile-keep.txt --extra guile-bytecode`
   → `jniLibs/<abi>/liblilypond_exe.so`, `assets/prefix-*.zip`, `assets/prefix-*-symlinks.txt`.

## guile-keep.txt

The Guile .go files shipped compiled: every .go LilyPond opened on the emulator (`inotifyd` on the
ccache folders) while compiling the sample, `test/rich.ly` and `test/errors.ly`. The rest of
Guile ships as Scheme source and loads interpreted (`GUILE_AUTO_COMPILE=0`) if a score needs it.

## guile-bytecode/ (LilyPond's own Scheme, compiled)

Termux's LilyPond has no compiled copy of its 2.6 MB of Scheme, so Guile interpreted all of it on
every compile: 3.2 s on a Pixel 4a, 10 s on a Kompakt. LilyPond writes a compiled copy to
`share/lilypond/2.26.0/guile-bytecode/<absolute path of the source>.go` when Guile may
auto-compile, and reads it from there afterwards even when it may not. To make it:

1. Build the x86 debug APK without `--extra`, install it on the sb31 emulator.
2. Compile the sample, `test/rich.ly` and `test/errors.ly` with the debug hook
   `am start -n com.wanderwildwood.josho/.MainActivity --ez compile true --ez autocompile true`
   (the first takes about a minute; scores go in `files/scratch/`, the app's own folder).
3. `adb exec-out "run-as com.wanderwildwood.josho tar -C /data/data/com.wanderwildwood.josho/files -cf - usr/share/lilypond/2.26.0/guile-bytecode" | tar -C ~/josho-build/guile-bytecode -xf -`
4. Pack with `--extra ~/josho-build/guile-bytecode`.

Guile's .go files carry bytecode, not machine code (the JIT works at run time), and are the same
for both ABIs (344 of 344 of Guile's own match byte for byte between the x86_64 and aarch64
packages), so the copy made on the x86_64 emulator serves arm64 too. The path inside is the app's
own (`/data/data/com.wanderwildwood.josho/files/...`), so it holds for user 0 only; elsewhere
LilyPond falls back to interpreting, slower but correct. Redo it whenever LilyPond changes.
