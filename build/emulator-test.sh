#!/bin/bash
# On the build machine: fresh install of a debug APK on an emulator, compile the sample twice
# (cold after unpack, then warm), collect evidence.
#   emulator-test.sh <serial> <apk> <out dir>
S=$1; APK=$2; OUT=$3; A="$HOME/Android/Sdk/platform-tools/adb -s $S"
mkdir -p "$OUT"
$A uninstall com.wanderwildwood.josho >/dev/null 2>&1
$A install "$APK" >/dev/null || exit 1
run() {
    $A logcat -c
    $A shell am start -S -W -n com.wanderwildwood.josho/.MainActivity --ez compile true >/dev/null
    for i in $(seq 1 100); do
        $A logcat -d | grep -qE "Josho.*(compile:|Could not)" && break; sleep 3
    done
    sleep 2
    $A exec-out screencap -p > "$OUT/$1.png"
    $A logcat -d | grep -E " Josho|AndroidRuntime" | grep -vE "lilypond: (Processing|Parsing|Interpreting|Preprocessing|Finding|Fitting|Drawing|Layout)" | sed 's/^.* Josho *: /  /' > "$OUT/$1.txt"
}
run cold
run warm
$A shell run-as com.wanderwildwood.josho cat files/work/score.pdf > "$OUT/score.pdf"
echo "prefix on device: $($A shell run-as com.wanderwildwood.josho du -sk files/usr | cut -f1) KB"
echo "== cold"; cat "$OUT/cold.txt"; echo "== warm"; cat "$OUT/warm.txt"
