"""Geometry guide and material reference for the company ship's compartment doors (RoomDoorRenderSystem).

A compartment door is two sliding leaves meeting across the doorway. Each leaf is authored once, lying along x with
its jamb end on the left and its meeting (seam) end on the right, LEAF_CELLS long and LEAF_DEPTH cells deep through
the bulkhead; the renderer crops it from the jamb side as it withdraws, flips it for the other side and turns it a
quarter for a door across y. A sealed hatch draws the locked leaf pair and the clamp across the seam.

The guide owns every piece's box at CELL px per cell on a transparent canvas; ImageGen reskins it. pieces.json records
the boxes the cutter reads. Run from the repository root: python art-source/ship-doors/make_guide.py
"""
import json
import os
from PIL import Image, ImageDraw

CELL = 400
LEAF_CELLS = 1.5
LEAF_DEPTH = 0.42
CLAMP_CELLS = 0.9
G = 80
HERE = os.path.dirname(os.path.abspath(__file__))

LEAF_W, LEAF_H = int(CELL * LEAF_CELLS), int(CELL * LEAF_DEPTH)
CLAMP_W = int(CELL * CLAMP_CELLS)
W, H = 1600, 900

pieces = [
    {"id": "leaf", "x": G, "y": 120, "w": LEAF_W, "h": LEAF_H, "kind": "leaf"},
    {"id": "leaf-locked", "x": G, "y": 120 + LEAF_H + 2 * G, "w": LEAF_W, "h": LEAF_H, "kind": "leaf"},
    {"id": "clamp", "x": G + LEAF_W + 2 * G, "y": 120 + LEAF_H + 2 * G, "w": CLAMP_W, "h": LEAF_H, "kind": "clamp"},
]

guide = Image.new("RGBA", (W, H), (0, 0, 0, 0))
d = ImageDraw.Draw(guide)
for p in pieces:
    x, y, w, h = p["x"], p["y"], p["w"], p["h"]
    if p["kind"] == "leaf":
        locked = p["id"].endswith("locked")
        d.rectangle([x, y, x + w - 1, y + h - 1], fill=(70, 70, 70, 255))
        d.rectangle([x, y + 14, x + w - 1, y + h - 15], fill=(112, 112, 112, 255) if not locked else (96, 92, 90, 255))
        # Ribs across the leaf, and the meeting edge's band at the right-hand (seam) end.
        for rx in range(x + 120, x + w - 90, 130):
            d.rectangle([rx, y + 14, rx + 12, y + h - 15], fill=(140, 140, 140, 255))
        d.rectangle([x + w - 48, y, x + w - 1, y + h - 1], fill=(170, 150, 90, 255) if not locked else (160, 70, 60, 255))
    else:
        d.rectangle([x, y + 40, x + w - 1, y + h - 41], fill=(150, 64, 54, 255))
        d.rectangle([x + w // 2 - 50, y, x + w // 2 + 49, y + h - 1], fill=(90, 90, 90, 255))
guide.save(os.path.join(HERE, "guide.png"))
json.dump({"cellPx": CELL, "width": W, "height": H, "pieces": pieces},
          open(os.path.join(HERE, "pieces.json"), "w"), indent=2)

# Material reference: the ship's own bulkhead and interior material sheets, enlarged, on transparency.
root = os.path.dirname(os.path.dirname(HERE))
tiles = os.path.join(root, "mod/graphics/tilesets")
bulk = Image.open(os.path.join(tiles, "ship-bulkheads.png")).convert("RGBA").resize((640, 640), Image.LANCZOS)
mats = Image.open(os.path.join(tiles, "ship-interior-materials.png")).convert("RGBA").resize((1152, 192), Image.LANCZOS)
trim = Image.open(os.path.join(tiles, "ship-floor-trim.png")).convert("RGBA").resize((1152, 216), Image.LANCZOS)
ref = Image.new("RGBA", (1840, 680), (0, 0, 0, 0))
ref.alpha_composite(bulk, (0, 20))
ref.alpha_composite(mats, (680, 20))
ref.alpha_composite(trim, (680, 260))
ref.save(os.path.join(HERE, "material-reference.png"))
print(W, H, len(pieces))
