#!/usr/bin/env python3
"""Generate the chunky pixel-art button icons, 10x10 with a baked drop shadow.

They go under textures/gui/sprites/, which makes each one a GUI atlas sprite named
"chest-utils:<file>" - the same kind of id vanilla's own widget art uses, so the client
blits them by name at whatever size it wants and nothing has to ship a pixel size.

The buttons themselves wear vanilla's own widget sprites; these are the marks on them.
Font glyphs (arrows, stars, boxed keys) render as one-pixel hairlines that read as a
web page next to vanilla's art, so each mark is drawn here at the weight vanilla text
carries: solid white strokes two pixels wide where the shape allows, with the same
one-pixel offset shadow the font renderer bakes under every label.

Deterministic, stdlib-only, same approach as generate_chest_textures.py. The icon ids
the Java constants name are these files verbatim.
"""

import os
import struct
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "src/main/resources/assets/chest-utils/textures/gui/sprites")

WHITE = (0xFF, 0xFF, 0xFF, 0xFF)
SHADOW = (0x3F, 0x3F, 0x3F, 0xFF)
CLEAR = (0, 0, 0, 0)

# 9x9 art on a 10x10 canvas, so the +1/+1 shadow always has room.
#
# W = stroke. Solid arrows move everything; the open variants (outline only) move just
# what the far side already has - same committed/hollow reading the old glyphs carried,
# now at a size where both are legible.
ICONS = {
    # Every mark is symmetric about column 4, the middle of the nine. An arrowhead that
    # stops at column 7 leaves a blank column down the right and the whole row reads as
    # shoved left, which is exactly how the first cut of these looked on the header.
    "icon_sort": [
        "WWWWWWWWW",
        "WWWWWWWWW",
        ".........",
        ".WWWWWWW.",
        ".WWWWWWW.",
        ".........",
        "..WWWWW..",
        "..WWWWW..",
        ".........",
    ],
    "icon_in": [
        "....W....",
        "...WWW...",
        "..WWWWW..",
        ".WWWWWWW.",
        "WWWWWWWWW",
        "...WWW...",
        "...WWW...",
        "...WWW...",
        "...WWW...",
    ],
    "icon_in_match": [
        "....W....",
        "...W.W...",
        "..W...W..",
        ".W.....W.",
        "WWW...WWW",
        "..W...W..",
        "..W...W..",
        "..W...W..",
        "..WWWWW..",
    ],
    "icon_out": [
        "...WWW...",
        "...WWW...",
        "...WWW...",
        "...WWW...",
        "WWWWWWWWW",
        ".WWWWWWW.",
        "..WWWWW..",
        "...WWW...",
        "....W....",
    ],
    "icon_out_match": [
        "..WWWWW..",
        "..W...W..",
        "..W...W..",
        "..W...W..",
        "WWW...WWW",
        ".W.....W.",
        "..W...W..",
        "...W.W...",
        "....W....",
    ],
    "icon_star": [
        "....W....",
        "....W....",
        "...WWW...",
        "WWWWWWWWW",
        ".WWWWWWW.",
        "..WWWWW..",
        "..WWWWW..",
        ".WWW.WWW.",
        ".W.....W.",
    ],
    "icon_star_open": [
        "....W....",
        "....W....",
        "...W.W...",
        "WWWW.WWWW",
        ".W.....W.",
        "..W...W..",
        "..W...W..",
        ".WW.W.WW.",
        ".W.....W.",
    ],
    "icon_lock": [
        "..WWWWW..",
        "..W...W..",
        "..W...W..",
        ".WWWWWWW.",
        ".WWWWWWW.",
        ".WWW.WWW.",
        ".WWW.WWW.",
        ".WWWWWWW.",
        ".WWWWWWW.",
    ],
    # A magnifier: the ring and its handle, the one mark everybody reads as "find".
    "icon_search": [
        ".WWWW....",
        "WW..WW...",
        "W....W...",
        "W....W...",
        "WW..WW...",
        ".WWWWWW..",
        ".....WWW.",
        "......WWW",
        ".......WW",
    ],
    # The cross that closes things, two pixels wide so it matches the arrows' weight.
    "icon_close": [
        "WW.....WW",
        "WWW...WWW",
        ".WWW.WWW.",
        "..WWWWW..",
        "...WWW...",
        "..WWWWW..",
        ".WWW.WWW.",
        "WWW...WWW",
        "WW.....WW",
    ],
    "icon_unlock": [
        "....WWWWW",
        "....W...W",
        "....W....",
        ".WWWWWWW.",
        ".WWWWWWW.",
        ".WWW.WWW.",
        ".WWW.WWW.",
        ".WWWWWWW.",
        ".WWWWWWW.",
    ],
    # Public: shut, because the chest is still somebody's, but hollow, because the lid is
    # not - the same hollow-means-partly the arrows carry.
    "icon_public": [
        "..WWWWW..",
        "..W...W..",
        "..W...W..",
        ".WWWWWWW.",
        ".W.....W.",
        ".W..W..W.",
        ".W..W..W.",
        ".W.....W.",
        ".WWWWWWW.",
    ],
}


def write_png(path, rows):
    height, width = len(rows), len(rows[0])
    raw = b"".join(b"\x00" + b"".join(bytes(p) for p in row) for row in rows)

    def chunk(tag, body):
        c = tag + body
        return struct.pack(">I", len(body)) + c + struct.pack(">I", zlib.crc32(c))

    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
           + chunk(b"IDAT", zlib.compress(raw, 9))
           + chunk(b"IEND", b""))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(png)
    print("wrote %s (%dx%d)" % (path, width, height))


def render(art):
    """10x10 canvas: the 9x9 art, with a one-pixel drop shadow baked at +1/+1."""
    size = 10
    stroke = {(x, y) for y, row in enumerate(art) for x, ch in enumerate(row) if ch == "W"}
    rows = []
    for y in range(size):
        row = []
        for x in range(size):
            if (x, y) in stroke:
                row.append(WHITE)
            elif (x - 1, y - 1) in stroke:
                row.append(SHADOW)
            else:
                row.append(CLEAR)
        rows.append(row)
    return rows


def main():
    for name, art in ICONS.items():
        assert len(art) == 9 and all(len(r) == 9 for r in art), name
        write_png(os.path.join(OUT, name + ".png"), render(art))


if __name__ == "__main__":
    main()
