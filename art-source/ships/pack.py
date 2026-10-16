"""Mechanical runtime export of the remastered hulls: placement and resampling only, no recolour or redraw.

Each raw master's opaque bounds are scaled onto the vanilla sprite's opaque bounds on a canvas that is a whole
multiple of the vanilla canvas, so a fraction of the sprite (a weapon slot, an engine nozzle, an armour-grid cell)
read off the vanilla .ship lands on the same part of the remaster.

Usage (from the repository root): python -I art-source/ships/pack.py
"""
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
TEMPLATES = os.path.join(ROOT, "art-source", "templates")
OUT = os.path.join(ROOT, "mod", "graphics", "battle", "ships")

# name: (vanilla guide, multiple of the vanilla canvas, shipped file)
SHIPS = {
    "venture": ("venture-vanilla-guide.png", 6, "venture.png"),
    "hound-pirates": ("hound-pirates-vanilla-guide.png", 16, "hound_pirates.png"),
}
OPAQUE = 16


def opaque_bounds(image):
    alpha = image.getchannel("A").point(lambda a: 255 if a > OPAQUE else 0)
    box = alpha.getbbox()
    if box is None:
        raise SystemExit("no opaque pixels")
    return box


def pack(name):
    guide_file, multiple, shipped = SHIPS[name]
    vanilla = Image.open(os.path.join(TEMPLATES, guide_file)).convert("RGBA")
    raw = Image.open(os.path.join(HERE, "raw", name + ".raw.png")).convert("RGBA")
    vl, vt, vr, vb = opaque_bounds(vanilla)
    rl, rt, rr, rb = opaque_bounds(raw)
    target = Image.new("RGBA", (vanilla.width * multiple, vanilla.height * multiple), (0, 0, 0, 0))
    size = ((vr - vl) * multiple, (vb - vt) * multiple)
    body = raw.crop((rl, rt, rr, rb)).resize(size, Image.LANCZOS)
    target.paste(body, (vl * multiple, vt * multiple))
    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, shipped)
    target.save(path, optimize=True)
    print(f"{name}: raw bounds {rr - rl}x{rb - rt} -> {size[0]}x{size[1]} on {target.width}x{target.height} -> {path}")


if __name__ == "__main__":
    for ship in (sys.argv[1:] or SHIPS):
        pack(ship)
