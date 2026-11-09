"""Cuts the painted swarm-drone and cradle sheets into the sprites the game draws (`derelict-drone-flocks.md`, slice 7).

    python pack.py --drone disc --cradle rack                      # the picked designs, into mod/
    python pack.py --drone quad --cradle silo --root <overlay dir>  # a look candidate, outside mod/

Each sheet holds four candidate designs (guide.py, cradle_guide.py), painted once per lineage
(swarm-domain-raw.png, swarm-contingency-raw.png, cradle-domain-raw.png, cradle-contingency-raw.png). A drone is cropped
to its alpha bounds, turned from facing LEFT to facing UP (the battle renderer's 0 degrees is +Y) and scaled so its long
axis is DRONE_PX; a cradle is cropped and scaled so its long axis is CRADLE_PX, unrotated. Written under
<root>/graphics/battle/drones/ as swarm-<lineage>.png and cradle-<lineage>.png, the paths `FlockLivery` names.
"""
import argparse
from pathlib import Path

from PIL import Image

import cradle_guide
import guide

HERE = Path(__file__).resolve().parent
MOD = HERE.parents[2] / "mod"
LINEAGES = ("domain", "contingency")
DRONE_PX = 128
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
    p.add_argument("--drone", choices=sorted(guide.PANELS), required=True)
    p.add_argument("--cradle", choices=sorted(cradle_guide.PANELS), required=True)
    p.add_argument("--root", type=Path, default=MOD)
    a = p.parse_args()
    out = a.root / "graphics" / "battle" / "drones"
    out.mkdir(parents=True, exist_ok=True)
    for lineage in LINEAGES:
        sheet = Image.open(HERE / f"swarm-{lineage}-raw.png").convert("RGBA")
        drone = cut(sheet, *guide.PANELS[a.drone]).rotate(-90, expand=True, resample=Image.BICUBIC)
        fit(drone, DRONE_PX).save(out / f"swarm-{lineage}.png")
        sheet = Image.open(HERE / f"cradle-{lineage}-raw.png").convert("RGBA")
        fit(cut(sheet, *cradle_guide.PANELS[a.cradle]), CRADLE_PX).save(out / f"cradle-{lineage}.png")
        print(lineage, a.drone, a.cradle, "->", out)


if __name__ == "__main__":
    main()
