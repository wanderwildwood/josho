# Sources of the bundled binaries

Engraver bundles LilyPond and the libraries it needs, built for Android with the
termux-packages build recipes. The GPL asks that the corresponding source of every bundled
binary be available. It is here:

- The build recipes and Termux's patches, copied unchanged from
  https://github.com/termux/termux-packages at commit
  `47d0a637e92fb9ecdf78ac75f0b7c94b75340c96` (2026-11-08), are in
  `third_party/termux-packages/packages/<name>/` (`build.sh` and `*.patch`).
  termux-packages' own license: `third_party/termux-packages/LICENSE.md`.
- The upstream source tarball of each package, with the sha256 the recipe checks, is below.
  The full hash is in each `build.sh` (`TERMUX_PKG_SHA256`).
- Our own changes to the recipes (the install prefix) are in `build/`.
- Built inside termux-packages' official builder image,
  `ghcr.io/termux/package-builder@sha256:e2de051a518d8b188c59c4a1ecf357d373b9ccf48d6c06d7681fdc3f61a77287`
  (pulled 2026-11-08), by `build/build-lilypond.sh`; `build/pack-prefix.py` turns the
  resulting .deb files into the app's `jniLibs/` and `assets/prefix-*.zip`.

python and ghostscript are left out: LilyPond's Cairo backend writes PDF and SVG without
ghostscript, and python is only needed for convert-ly and the other helper scripts.

| package | version | license | upstream source | sha256 | patches |
|---|---|---|---|---|---|
| brotli | 1.2.1 | MIT | https://github.com/google/brotli/archive/refs/tags/v1.2.1.tar.gz | `98d35d11a78dc453…` | 0 |
| fontconfig | 2.18.3 | MIT | https://gitlab.freedesktop.org/fontconfig/fontconfig/-/archive/2.18.3/fontconfig-2.18.3.tar.gz | `9ae01e1d53acdef5…` | 1 |
| freetype | 2.15.0 | GPL-2.0 | https://downloads.sourceforge.net/freetype/freetype-2.15.0.tar.xz | `93cd47eb719c3251…` | 0 |
| fribidi | 1.0.18 | LGPL-2.0 | https://github.com/fribidi/fribidi/releases/download/v1.0.18/fribidi-1.0.18.tar.xz | `1de5e8073d035388…` | 0 |
| glib | 2.90.1 | LGPL-2.1 | https://download.gnome.org/sources/glib/2.90/glib-2.90.1.tar.xz | `93c941aa17d5eb1d…` | 16 |
| guile | 3.0.11 | LGPL-3.0 | https://mirrors.kernel.org/gnu/guile/guile-3.0.11.tar.gz | `3c9c16972a73bb79…` | 0 |
| harfbuzz | 15.1.0 | MIT | https://github.com/harfbuzz/harfbuzz/archive/refs/tags/15.1.0.tar.gz | `5c057929413b8664…` | 1 |
| libandroid-execinfo | 0.1 | BSD 2-Clause |  (array or computed; see build.sh) | `…` | 0 |
| libandroid-shmem | 0.7 | BSD 3-Clause | https://github.com/termux/libandroid-shmem/archive/refs/tags/v0.7.tar.gz | `1e5ff8459bc0a8c2…` | 0 |
| libandroid-spawn | 0.3 | BSD 2-Clause |  (array or computed; see build.sh) | `…` | 0 |
| libandroid-support | (29 | Apache-2.0, MIT | (https://github.com/termux/libandroid-support/archive/refs/tags/v${TERMUX_PKG_VERSION[0]}.tar.gz (array or computed; see build.sh) | `(8f74ce0f9cf70ec…` | 0 |
| libbz2 | 1.0.8 | BSD | https://fossies.org/linux/misc/bzip2-1.0.8.tar.xz | `47fd74b2ff83effa…` | 3 |
| libc++ | 30 | NCSA | https://dl.google.com/android/repository/android-ndk-r30-linux.zip | `753611f410d002cf…` | 0 |
| libcairo | 1.18.6 | LGPL-2.1 | https://gitlab.freedesktop.org/cairo/cairo/-/archive/1.18.6/cairo-1.18.6.tar.bz2 | `42b785e00ef97a68…` | 3 |
| libexpat | 2.10.0 | MIT | https://github.com/libexpat/libexpat/releases/download/R_2_10_0/expat-2.10.0.tar.bz2 | `44e0139015f1c990…` | 0 |
| libffi | 3.8.0 | MIT | https://github.com/libffi/libffi/releases/download/v3.8.0/libffi-3.8.0.tar.gz | `7da3e2d9a171eb0a…` | 0 |
| libgc | 8.2.12 | MIT | https://github.com/bdwgc/bdwgc/releases/download/v8.2.12/gc-8.2.12.tar.gz | `42e5194ad06ab6ff…` | 0 |
| libgmp | 6.3.0 | LGPL-3.0 | https://mirrors.kernel.org/gnu/gmp/gmp-6.3.0.tar.xz | `a3c2b80201b89e68…` | 0 |
| libgraphite | 1.3.15 | LGPL-2.0 | https://github.com/silnrsi/graphite/archive/refs/tags/1.3.15.tar.gz | `ad47ac120d0fbd80…` | 1 |
| libiconv | 1.19.1 | LGPL-2.1, GPL-3.0 | https://mirrors.kernel.org/gnu/libiconv/libiconv-1.19.1.tar.gz | `9a06844846d664d7…` | 0 |
| liblzo | 2.10 | GPL-2.0 | https://www.oberhumer.com/opensource/lzo/download/lzo-2.10.tar.gz | `c0f892943208266f…` | 1 |
| libpixman | 0.46.4 | MIT | https://cairographics.org/releases/pixman-0.46.4.tar.gz | `d09c44ebc3bd5bee…` | 1 |
| libpng | 1.6.59 | Libpng | https://github.com/pnggroup/libpng/archive/refs/tags/v1.6.59.tar.gz | `2540302a1844ad2b…` | 0 |
| libunistring | 1.5 | LGPL-3.0, GPL-2.0 | https://mirrors.kernel.org/gnu/libunistring/libunistring-1.5.tar.xz | `fe89007a8a414ae4…` | 0 |
| libx11 | 1.8.14 | MIT, X11 | https://xorg.freedesktop.org/releases/individual/lib/libX11-1.8.14.tar.xz | `bbc78c4621717465…` | 2 |
| libxau | 1.0.12 | MIT | https://xorg.freedesktop.org/releases/individual/lib/libXau-1.0.12.tar.xz | `74d0e4dfa3d39ad8…` | 1 |
| libxcb | 1.17.0 | MIT | https://xorg.freedesktop.org/archive/individual/lib/libxcb-1.17.0.tar.xz | `599ebf9996710fea…` | 1 |
| libxdmcp | 1.1.5 | MIT | https://xorg.freedesktop.org/archive/individual/lib/libXdmcp-1.1.5.tar.xz | `d8a5222828c3adab…` | 0 |
| libxext | 1.3.7 | MIT, HPND, ISC | https://xorg.freedesktop.org/releases/individual/lib/libXext-1.3.7.tar.xz | `6c643c7035cdacf6…` | 0 |
| libxft | 2.3.9 | HPND | https://xorg.freedesktop.org/releases/individual/lib/libXft-2.3.9.tar.xz | `60a25b78945ed693…` | 0 |
| libxrender | 0.9.13 | MIT | https://xorg.freedesktop.org/releases/individual/lib/libXrender-0.9.13.tar.xz | `3fe88c609df6782e…` | 0 |
| lilypond | 2.26.0 | GPL-3.0 | https://lilypond.org/download/sources/v2.26/lilypond-2.26.0.tar.gz | `bbe82dbeba7f7f99…` | 0 |
| ncurses | (6.6.20260307+really6.5.20250830 | MIT | ("https://github.com/ThomasDickey/ncurses-snapshots/archive/${_SNAPSHOT_COMMIT}.tar.gz" (array or computed; see build.sh) | `(28cd102efe6a261…` | 3 |
| pango | 1.58.2 | LGPL-2.0 | https://download.gnome.org/sources/pango/1.58/pango-1.58.2.tar.xz | `342385b6ca3b7c73…` | 2 |
| pcre2 | 10.49 | BSD 3-Clause | https://github.com/PCRE2Project/pcre2/releases/download/pcre2-10.49/pcre2-10.49.tar.bz2 | `53c156e1ba416a20…` | 0 |
| readline | 8.3.6 | GPL-3.0 | https://mirrors.kernel.org/gnu/readline/readline-8.3.tar.gz | `fe5383204467828c…` | 1 |
| resolv-conf | 1.3 | Public Domain |  (array or computed; see build.sh) | `…` | 0 |
| tex-gyre | 2.501 | custom | https://www.gust.org.pl/projects/e-foundry/tex-gyre/whole/tg2_501otf.zip | `d7f8be5317bec4e6…` | 0 |
| ttf-dejavu | 2.37 | MIT | https://downloads.sourceforge.net/project/dejavu/dejavu/2.37/dejavu-fonts-ttf-2.37.tar.bz2 | `fa9ca4d13871dd12…` | 0 |
| xorg-util-macros | 1.20.2 | HPND, MIT | https://xorg.freedesktop.org/releases/individual/util/util-macros-1.20.2.tar.xz | `9ac269eba24f672d…` | 1 |
| xorgproto | 2026.1 | MIT | https://xorg.freedesktop.org/archive/individual/proto/xorgproto-2026.1.tar.xz | `f9bfe4a9ed8c8ab9…` | 3 |
| zlib | 1.4 | ZLIB | https://github.com/madler/zlib/releases/download/v1.4/zlib-1.4.tar.xz | `4f11c512053d7bab…` | 1 |
