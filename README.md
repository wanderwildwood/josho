# Engraver

Type LilyPond, get an engraved score on the screen and a PDF beside the file. Runs LilyPond
2.26 itself, on the device, with no server and no internet permission. Made for the Mudita
Kompakt; held sideways, the source and the score sit side by side.

Pick a folder of `.ly` files (Cabinet's chooser when Cabinet is installed); `\include`s in that
folder are followed. See `SOURCES.md` for where every bundled binary comes from and `build/`
for how they are built.

Thanks to [zyny](https://forum.mudita.com/u/zyny) on the Mudita forum, whose request started
this app.

## License

GPL-3.0-only. See [LICENSE](LICENSE).

Copyright (C) 2026 wander wildwood

This program is free software: you can redistribute it and/or modify it under the terms of the
GNU General Public License as published by the Free Software Foundation, version 3.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License along with this program. If
not, see <https://www.gnu.org/licenses/>.

LilyPond is GPL-3.0-or-later and most of its libraries are LGPL or permissive; their licenses
travel with them (see `SOURCES.md`). The fonts keep their own: Emmentaler under the SIL OFL 1.1,
TeX Gyre under the GUST Font License.
