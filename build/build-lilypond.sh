#!/bin/bash
# Build LilyPond and everything it links against for Android, under Engraver's own prefix
# (/data/data/com.wanderwildwood.josho/files/usr), with the termux-packages recipes, inside
# termux-packages' official builder image (ghcr.io/termux/package-builder).
#
# Run on a Linux build machine with Docker:   build-lilypond.sh x86_64|aarch64 [jobs=5]
# Output: .deb files in ~/josho-build/tp/output-<arch>/, unpacked later into the app.
#
# Changes to the recipes: the app package name in scripts/properties.sh, which moves the prefix,
# and lilypond-recipe.patch. Every dependency is built from source; nothing prebuilt is downloaded.
#
# Why not termux-packages' own scripts/run-docker.sh: it loads its AppArmor profiles with sudo,
# which the build user does not have. This does what it does otherwise for a 64-bit build (same
# image, same mount, its seccomp profile, SYS_ADMIN + /dev/fuse for the fuse-overlayfs toolchain
# mount, same uid fix-up). Without its profile AppArmor runs unconfined, which is run-docker.sh's
# own fallback when apparmor_parser is missing. The container is capped at 6 CPUs and 8 GB so
# the build machine keeps headroom.
set -euo pipefail
ARCH=${1:?x86_64 or aarch64}; JOBS=${2:-5}
COMMIT=47d0a637e92fb9ecdf78ac75f0b7c94b75340c96   # same as third_party/termux-packages
IMAGE=ghcr.io/termux/package-builder
NAME=josho-builder
W=$HOME/josho-build; mkdir -p "$W"; cd "$W"

if [ ! -d tp ]; then
    git clone -q https://github.com/termux/termux-packages.git tp
fi
git -C tp fetch -q origin "$COMMIT" 2>/dev/null || true
git -C tp checkout -q "$COMMIT"
git -C tp checkout -q -- scripts/properties.sh packages/lilypond/build.sh   # start clean every run
# Our one recipe change: LilyPond without ghostscript and python at runtime (the Cairo backend
# writes PDF/SVG itself; python only runs the helper scripts). Ghostscript would also pull in
# aosp-libs, a 27 GB AOSP checkout.
git -C tp apply "$(dirname "$(readlink -f "$0")")/lilypond-recipe.patch"
sed -i 's/^TERMUX_APP__PACKAGE_NAME="com.termux"$/TERMUX_APP__PACKAGE_NAME="com.wanderwildwood.josho"/' tp/scripts/properties.sh
grep -q '^TERMUX_APP__PACKAGE_NAME="com.wanderwildwood.josho"$' tp/scripts/properties.sh

docker image inspect "$IMAGE" >/dev/null 2>&1 || docker pull "$IMAGE"
docker image inspect --format '{{index .RepoDigests 0}}' "$IMAGE"

if ! docker container inspect "$NAME" >/dev/null 2>&1; then
    docker run --detach --init --tty --name "$NAME" \
        --cpus 6 --memory 8g \
        --security-opt seccomp="$W/tp/scripts/profile.json" --security-opt apparmor=unconfined \
        --cap-add CAP_SYS_ADMIN --device /dev/fuse \
        --volume "$W/tp:/home/builder/termux-packages" \
        "$IMAGE" >/dev/null
    if [ "$(id -u)" != 1001 ]; then
        docker exec "$NAME" sudo chown -R "$(id -u):$(id -g)" /home/builder /data
        docker exec "$NAME" sudo usermod -u "$(id -u)" builder
        docker exec "$NAME" sudo groupmod -g "$(id -g)" builder
    fi
fi
docker start "$NAME" >/dev/null 2>&1 || true

# -j: build-package.sh otherwise uses nproc (16), whatever the container CPU cap.
docker exec --env GC_MARKERS=2 --workdir /home/builder/termux-packages "$NAME" \
    ./build-package.sh -j "$JOBS" -a "$ARCH" -o "output-$ARCH" lilypond
