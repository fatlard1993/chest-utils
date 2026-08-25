#!/usr/bin/env python3
"""Generate the dyed chest textures: vanilla's chest, tinted sixteen ways.

Reads the three vanilla chest textures out of the Minecraft jar and writes a tinted
copy of each per dye colour, into assets/chest-utils/textures/entity/chest/.

The tint multiplies rather than replaces, so the plank grain, the shadow under the
lid and the dark of the keyhole all survive it: a red chest reads as a chest that has
been painted, not as a red chest-shaped hole. The clasp is left alone - it is iron on
every chest in the game and dyeing the wood does not dye the fittings.

Run it after a build, so a Loom client jar exists to read from.
"""

import os
import struct
import sys
import zlib
import zipfile
import glob

_jars = sorted(glob.glob(os.path.expanduser(
    "~/.gradle/caches/fabric-loom/*/minecraft-client-only.jar")), key=os.path.getmtime)
if not _jars:
    sys.exit("no Loom client jar cached - run a build first")
JAR = _jars[-1]

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "src/main/resources/assets/chest-utils/textures/entity/chest")

# Vanilla's own dye colours, so a red chest matches red wool beside it.
DYES = {
    "white": (0xF9, 0xFF, 0xFE), "orange": (0xF9, 0x80, 0x1D), "magenta": (0xC7, 0x4E, 0xBD),
    "light_blue": (0x3A, 0xB3, 0xDA), "yellow": (0xFE, 0xD8, 0x3D), "lime": (0x80, 0xC7, 0x1F),
    "pink": (0xF3, 0x8B, 0xAA), "gray": (0x47, 0x4F, 0x52), "light_gray": (0x9D, 0x9D, 0x97),
    "cyan": (0x16, 0x9C, 0x9C), "purple": (0x89, 0x32, 0xB8), "blue": (0x3C, 0x44, 0xAA),
    "brown": (0x83, 0x54, 0x32), "green": (0x5E, 0x7C, 0x16), "red": (0xB0, 0x2E, 0x26),
    "black": (0x1D, 0x1D, 0x21),
}

# The iron fittings, left as they are. Vanilla draws them in greys that the wood never
# uses, so "is this pixel grey" is enough to tell clasp from plank.
def is_fitting(r, g, b):
    return abs(r - g) < 12 and abs(g - b) < 12 and abs(r - b) < 12

def read_png(data):
    """Decode a PNG into (width, height, rows of RGBA tuples)."""
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError("not a PNG")

    idat, palette, trns, width, height, depth, colour = b"", None, None, 0, 0, 0, 0
    pos = 8
    while pos < len(data):
        (length,) = struct.unpack(">I", data[pos:pos + 4])
        tag = data[pos + 4:pos + 8]
        body = data[pos + 8:pos + 8 + length]
        if tag == b"IHDR":
            width, height, depth, colour = struct.unpack(">IIBB", body[:10])
        elif tag == b"PLTE":
            palette = body
        elif tag == b"tRNS":
            trns = body
        elif tag == b"IDAT":
            idat += body
        elif tag == b"IEND":
            break
        pos += 12 + length

    if depth != 8:
        raise ValueError("only 8-bit PNGs are handled; got %d" % depth)

    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[colour]
    stride = width * channels
    raw = zlib.decompress(idat)

    # Undo the per-scanline filters. This is the whole of the PNG spec that
    # matters here, and it is short enough to not be worth a dependency.
    out, prev = [], bytearray(stride)
    pos = 0
    for _ in range(height):
        filt = raw[pos]
        line = bytearray(raw[pos + 1:pos + 1 + stride])
        pos += 1 + stride
        for i in range(stride):
            a = line[i - channels] if i >= channels else 0
            b = prev[i]
            c = prev[i - channels] if i >= channels else 0
            if filt == 1:
                line[i] = (line[i] + a) & 0xFF
            elif filt == 2:
                line[i] = (line[i] + b) & 0xFF
            elif filt == 3:
                line[i] = (line[i] + (a + b) // 2) & 0xFF
            elif filt == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                pred = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pred) & 0xFF
        out.append(line)
        prev = line

    rows = []
    for line in out:
        row = []
        for x in range(width):
            if colour == 3:
                idx = line[x]
                r, g, b = palette[idx * 3:idx * 3 + 3]
                alpha = trns[idx] if trns and idx < len(trns) else 255
                row.append((r, g, b, alpha))
            elif colour == 6:
                row.append(tuple(line[x * 4:x * 4 + 4]))
            elif colour == 2:
                row.append(tuple(line[x * 3:x * 3 + 3]) + (255,))
            else:
                raise ValueError("unhandled colour type %d" % colour)
        rows.append(row)
    return width, height, rows

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

def luma(r, g, b):
    return 0.299 * r + 0.587 * g + 0.114 * b


# The chest's own planks, averaged. Multiply can only ever darken, so this is what a dye has to
# be measured against: not how bright the dye looks, but how much of this it actually removes.
WOOD_MEAN = (113, 82, 35)

# A dye that leaves the wood this close to its original brightness has not tinted it at all.
# Of the sixteen only white is over the line - it came out at 85 against the wood's 86, which
# is to say a white chest was the vanilla chest to the pixel. Brown, cyan and green all sit
# above the wood in raw brightness and still darken it properly, which is why the test is on
# the result and not on the dye.
NO_EFFECT = 0.9

# How far a pale dye pulls the wood towards itself. Not all the way: at 1.0 the planks come out
# a flat white card with the grain scrubbed off, and the grain is the reason these are tinted
# rather than replaced. Two thirds reads as whitewashed and still has wood under it.
BLEACH = 0.66


def tint(rows, colour):
    """Colour every wooden pixel, keeping its own light and shade.

    Dyes darker than the wood multiply, which is what keeps the grain, the shadow under the lid
    and the iron where they are. A dye lighter than the wood cannot darken it into being that
    colour, so it lightens towards it instead - the same idea run the other way.
    """
    cr, cg, cb = colour
    mr, mg, mb = WOOD_MEAN
    pale = luma(mr * cr / 255, mg * cg / 255, mb * cb / 255) > luma(mr, mg, mb) * NO_EFFECT
    out = []
    for row in rows:
        line = []
        for px in row:
            r, g, b, a = px
            if a == 0 or is_fitting(r, g, b):
                line.append((r, g, b, a))
                continue
            if pale:
                line.append((
                    int(r + (cr - r) * BLEACH),
                    int(g + (cg - g) * BLEACH),
                    int(b + (cb - b) * BLEACH),
                    a))
            else:
                line.append((r * cr // 255, g * cg // 255, b * cb // 255, a))
        out.append(line)
    return out


def main():
    os.makedirs(OUT, exist_ok=True)
    written = 0

    with zipfile.ZipFile(JAR) as jar:
        for part in ("normal", "normal_left", "normal_right"):
            data = jar.read(f"assets/minecraft/textures/entity/chest/{part}.png")
            _, _, rows = read_png(data)

            for name, colour in DYES.items():
                suffix = part.replace("normal", "")
                write_png(os.path.join(OUT, f"{name}{suffix}.png"), tint(rows, colour))
                written += 1

    print(f"wrote {written} textures to {os.path.relpath(OUT, HERE)}")


if __name__ == "__main__":
    main()
