"""Geometry guide and material reference for the locked-door sheet (garrisonables.md, Locked doors and hardened walls).

The guide owns every piece's box at 320 px per cell on a transparent canvas; ImageGen reskins it. Each shut door spans
its length in cells along x and one cell across (the wall it stands in); each open jamb is 0.4 x 1 cell. pieces.json
records the boxes the cutter reads. Run from the repository root: python art-source/locked-doors/make_guide.py
"""
import json
import os
from PIL import Image, ImageDraw

CELL = 320
G = 64
HERE = os.path.dirname(os.path.abspath(__file__))
JAMB = int(CELL * 0.4)

# (id, x, y, w, h, kind): rows laid left to right with G gutters.
pieces = []
def row(y, items):
    x = G
    for pid, w, h, kind in items:
        pieces.append({"id": pid, "x": x, "y": y, "w": w, "h": h, "kind": kind})
        x += w + G
    return x

W = 2176
row(G, [("forcible-4", 4 * CELL, CELL, "shut"), ("forcible-1", CELL, CELL, "shut"), ("hardened-1", CELL, CELL, "shut")])
row(2 * G + CELL, [("forcible-3", 3 * CELL, CELL, "shut"), ("forcible-2", 2 * CELL, CELL, "shut"),
                   ("forcible-jamb-low", JAMB, CELL, "jamb"), ("forcible-jamb-high", JAMB, CELL, "jamb")])
row(3 * G + 2 * CELL, [("hardened-2", 2 * CELL, CELL, "shut"), ("hardened-jamb-low", JAMB, CELL, "jamb"),
                       ("hardened-jamb-high", JAMB, CELL, "jamb")])
H = 4 * G + 3 * CELL

guide = Image.new("RGBA", (W, H), (0, 0, 0, 0))
d = ImageDraw.Draw(guide)
for p in pieces:
    x, y, w, h = p["x"], p["y"], p["w"], p["h"]
    hard = p["id"].startswith("hardened")
    body = (128, 128, 128, 255) if not hard else (100, 104, 108, 255)
    d.rectangle([x, y, x + w - 1, y + h - 1], fill=(60, 60, 60, 255))
    d.rectangle([x + 18, y + 18, x + w - 19, y + h - 19], fill=body)
    if p["kind"] == "shut":
        mid = x + w // 2
        d.rectangle([mid - 6, y + 18, mid + 5, y + h - 19], fill=(45, 45, 45, 255))
    else:
        # the retracted leaf's edge on the passage side of the jamb
        edge = x + w - 30 if p["id"].endswith("low") else x
        d.rectangle([edge, y + 18, edge + 29, y + h - 19], fill=(80, 80, 80, 255))
guide.save(os.path.join(HERE, "guide.png"))
json.dump({"cellPx": CELL, "width": W, "height": H, "pieces": pieces}, open(os.path.join(HERE, "pieces.json"), "w"),
          indent=2)

# Material reference: the fort's own door art, enlarged, on transparency.
root = os.path.dirname(os.path.dirname(HERE))
blast = Image.open(os.path.join(root, "mod/graphics/fort/blast-door.png")).convert("RGBA")
leaves = Image.open(os.path.join(root, "mod/graphics/fort/gate-leaves.png")).convert("RGBA")
ref = Image.new("RGBA", (1300, 640), (0, 0, 0, 0))
ref.alpha_composite(blast.resize((960, 320), Image.LANCZOS), (0, 0))
ref.alpha_composite(leaves.resize((213, 320), Image.LANCZOS), (1020, 0))
ref.save(os.path.join(HERE, "material-reference.png"))
print(W, H, len(pieces))
