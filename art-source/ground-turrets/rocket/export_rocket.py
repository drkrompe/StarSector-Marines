"""Cut the Locust battery's head into mod/graphics/structures/ground-turrets/rocket/ (`tender-ship.md`).

The head is the vanilla Locust turret repainted in the ground-turret family's olive-drab finish (locust-raw.png, from
locust-prompt.txt over upres_guide.py's guide), already nose up, scaled about the sprite's centre (the vanilla pivot) so
it turns on its mount. Rockets leave the tubes rather than recoil, so there is no barrel layer; the contact shadow is
baked in as for every head.

  locust-head.png  160x160 (visualCells 1.25): the body 0.8 cells long, inside its one-cell round collar

Prints the eight tube mouths (two rows of four) in cells from the pivot, x right and y forward, for the mount's muzzles.

Usage: python art-source/ground-turrets/rocket/export_rocket.py
"""
import sys
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))
from export import contact_shadow  # noqa: E402

ROOT = HERE.parent.parent.parent
OUT = ROOT / "mod" / "graphics" / "structures" / "ground-turrets" / "rocket"
PPC = 128
SIZE = 160
LENGTH_CELLS = 0.8
SHADOW = 0.3
# The tube mouths' centres on the raw canvas, measured on locust-raw.png: four columns, two rows.
TUBE_COLUMNS = (468, 567, 678, 786)
TUBE_ROWS = (345, 430)


def main():
    raw = Image.open(HERE / "locust-raw.png").convert("RGBA")
    box = raw.getchannel("A").point(lambda v: 255 if v > 32 else 0).getbbox()
    k = LENGTH_CELLS * PPC / (box[3] - box[1])
    scaled = raw.resize((round(raw.width * k), round(raw.height * k)), Image.LANCZOS)
    left, top = (scaled.width - SIZE) // 2, (scaled.height - SIZE) // 2
    head = scaled.crop((left, top, left + SIZE, top + SIZE))
    out = contact_shadow(SIZE, SHADOW)
    out.alpha_composite(head)
    OUT.mkdir(parents=True, exist_ok=True)
    out.save(OUT / "locust-head.png")
    cx, cy = raw.width / 2, raw.height / 2
    muzzles = [[round((x - cx) * k / PPC, 3), round((cy - y) * k / PPC, 3)] for y in TUBE_ROWS for x in TUBE_COLUMNS]
    print("locust muzzles", muzzles)


if __name__ == "__main__":
    main()
