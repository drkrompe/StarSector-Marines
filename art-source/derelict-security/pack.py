"""Cuts the painted derelict security sheets into the sprites the game draws (`derelict-security-network.md`, slice 9).

    python pack.py --post popup --node mast                          # the picked designs, into mod/
    python pack.py --post cupola --node pylon --root <overlay dir>   # a look candidate, outside mod/

Each sheet (guide.py) is painted once per lineage (<question>-domain-raw.png, <question>-contingency-raw.png). A post's
parts are cut by the guide's own registration, never by their painted outline, so its head keeps its pivot on the
mount: a post's base the one-cell square about its panel's centre, scaled to 128; its folded and unfolded heads the 1.25-cell square (the
mount's drawn size), scaled to 160. A node is cropped to its alpha bounds and scaled so its long side is NODE_PX,
unrotated. Written under <root>/graphics/battle/derelict/ at the paths `DerelictLook` names.
"""
import argparse
from pathlib import Path

from PIL import Image

import guide

HERE = Path(__file__).resolve().parent
MOD = HERE.parents[1] / "mod"
LINEAGES = ("domain", "contingency")
BASE_PX = 128
HEAD_PX = 160
NODE_PX = 192
POST_FILES = {"base": "post-base", "folded": "post-folded", "unfolded": "post-head"}


def square(sheet, cx, cy, side):
    """The square of guide-pixel side {@code side} about guide point (cx, cy), in the painted sheet's pixels."""
    k = sheet.width / guide.SHEET
    half = side * k / 2
    return sheet.crop((round(cx * k - half), round(cy * k - half), round(cx * k + half), round(cy * k + half)))


def fit(img, px):
    scale = px / max(img.size)
    return img.resize((max(1, round(img.width * scale)), max(1, round(img.height * scale))), Image.LANCZOS)


def clean(img):
    """Drops the faint haze an image model leaves in empty alpha."""
    alpha = img.getchannel("A").point(lambda a: 0 if a < 12 else a)
    img.putalpha(alpha)
    return img


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--post", choices=sorted(guide.POST_ROWS), required=True)
    p.add_argument("--node", choices=sorted(guide.NODE_PANELS), required=True)
    p.add_argument("--root", type=Path, default=MOD)
    a = p.parse_args()
    out = a.root / "graphics" / "battle" / "derelict"
    out.mkdir(parents=True, exist_ok=True)
    for lineage in LINEAGES:
        sheet = Image.open(HERE / f"post-{lineage}-raw.png").convert("RGBA")
        row = guide.POST_ROWS[a.post]
        for col, part in enumerate(guide.POST_PARTS):
            cx, cy = (col + .5) * guide.POST_PANEL, (row + .5) * guide.POST_PANEL
            if part == "base":
                img = square(sheet, cx, cy, guide.CELL).resize((BASE_PX, BASE_PX), Image.LANCZOS)
            else:
                img = square(sheet, cx, cy, guide.CELL * 1.25).resize((HEAD_PX, HEAD_PX), Image.LANCZOS)
            clean(img).save(out / f"{POST_FILES[part]}-{lineage}.png")

        sheet = Image.open(HERE / f"node-{lineage}-raw.png").convert("RGBA")
        col, row = guide.NODE_PANELS[a.node]
        half_w, half_h = sheet.width // 2, sheet.height // 2
        panel = sheet.crop((col * half_w, row * half_h, (col + 1) * half_w, (row + 1) * half_h))
        box = panel.getchannel("A").point(lambda v: 255 if v > 24 else 0).getbbox()
        clean(fit(panel.crop(box), NODE_PX)).save(out / f"node-{lineage}.png")
        print(lineage, a.post, a.node, "->", out)


if __name__ == "__main__":
    main()
