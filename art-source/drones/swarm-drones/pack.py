"""Cuts the painted flock cradle sheets into the sprites the game draws (`air-nouns.md`, Flocks).

    python pack.py --cradle carousel                     # the picked design, into mod/
    python pack.py --cradle rack --root <overlay dir>    # a look candidate, outside mod/

Each sheet holds four candidate designs (cradle_guide.py), painted once per lineage (cradle-domain-raw.png,
cradle-contingency-raw.png). A cradle is cropped to its alpha bounds and scaled so its long axis is CRADLE_PX,
unrotated. Written under <root>/graphics/battle/drones/ as cradle-<lineage>.png, the paths `FlockLivery` names. The
swarm drone itself is cut by ../fanless-drones/pack.py.
"""
import argparse
from pathlib import Path

from PIL import Image

import cradle_guide

HERE = Path(__file__).resolve().parent
MOD = HERE.parents[2] / "mod"
LINEAGES = ("domain", "contingency")
CRADLE_PX = 256


def cut(sheet, col, row):
    half_w, half_h = sheet.width // 2, sheet.height // 2
    panel = sheet.crop((col * half_w, row * half_h, (col + 1) * half_w, (row + 1) * half_h))
    box = panel.getchannel("A").point(lambda a: 255 if a > 24 else 0).getbbox()
    return panel.crop(box)


def fit(img, px):
    scale = px / max(img.size)
    return img.resize((max(1, round(img.width * scale)), max(1, round(img.height * scale))), Image.LANCZOS)


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--cradle", choices=sorted(cradle_guide.PANELS), required=True)
    p.add_argument("--root", type=Path, default=MOD)
    a = p.parse_args()
    out = a.root / "graphics" / "battle" / "drones"
    out.mkdir(parents=True, exist_ok=True)
    for lineage in LINEAGES:
        sheet = Image.open(HERE / f"cradle-{lineage}-raw.png").convert("RGBA")
        fit(cut(sheet, *cradle_guide.PANELS[a.cradle]), CRADLE_PX).save(out / f"cradle-{lineage}.png")
        print(lineage, a.cradle, "->", out)


if __name__ == "__main__":
    main()
