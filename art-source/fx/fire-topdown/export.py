"""Exports a reskinned top-down fire raw to the shipped sheet (`fire-nouns.md`, Look).

Mechanical only: the raw is the guide's 4x4 grid at whatever size ImageGen returned,
resampled whole to 256x256 so every frame lands on its 64 px cell: battle FX ship at
no more than 256 px (`RawArtStaysOutOfModTest`), and a bed is drawn about 64 px wide at play zoom.
Alpha is kept as generated. Run: python export.py <look>[@<version>] [...]
  where <look> is a raw name without its version, e.g. a-flame-bed@v2 (v2 if omitted).
Writes mod/graphics/fx/fire_topdown_<look>.png.
"""
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
MOD_FX = os.path.normpath(os.path.join(HERE, "..", "..", "..", "mod", "graphics", "fx"))
SIZE = 256


def export(look, version="v2"):
    raw = Image.open(os.path.join(HERE, "raw", f"{look}-{version}.png")).convert("RGBA")
    sheet = raw.resize((SIZE, SIZE), Image.LANCZOS)
    os.makedirs(MOD_FX, exist_ok=True)
    out = os.path.join(MOD_FX, f"fire_topdown_{look}.png")
    sheet.save(out, optimize=True)
    print(out)


if __name__ == "__main__":
    for name in sys.argv[1:]:
        look, _, version = name.partition("@")
        export(look, version or "v2")
