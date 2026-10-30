"""The rail head's gun, generated on its own and laid into the approved housing.

ImageGen refused every edit of the turret that asked for the rail mount, however it was worded, so the rail head is
made in two parts: the twin-rail boom is generated alone from an enlarged block guide (rail-part-guide.png, no turret
in it), then laid into the housing at the guide's place. The result is rail-raw.png, in turret-master.png's coordinates
like the other heads' raws, so pack.py treats it as it treats them.

  python art-source/vehicles/tank/heads/rail_part.py guide     writes rail-part-guide.png
  (generate rail-part-raw.png from it with tools/codex_imagegen.py and rail-part-prompt.txt)
  python art-source/vehicles/tank/heads/rail_part.py compose   writes rail-raw.png
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw

import guide

HERE = Path(__file__).resolve().parent
BOX = (1170, 410, 1380, 500)   # the rail blocks' extent in master coordinates (guide.rail)
UP = 4                         # the part guide's enlargement
MANTLET_AFT_X = 1366           # where the laid-in part ends aft, under the housing's front plate
FRONT_PLATE_X = 1358           # the housing's front plate, laid back over the part
OLD_GUN = (1100, 436, 1352, 474)  # the approved cannon's barrel and sleeve, forward of the mantlet stubs


def part_guide():
    canvas = Image.new("RGBA", (1774, 887), (0, 0, 0, 0))
    guide.rail(ImageDraw.Draw(canvas))
    crop = canvas.crop(BOX)
    big = crop.resize((crop.width * UP, crop.height * UP), Image.NEAREST)
    out = Image.new("RGBA", (big.width + 2 * 80, big.height + 2 * 80), (0, 0, 0, 0))
    out.paste(big, (80, 80))
    out.save(HERE / "rail-part-guide.png")
    # Material reference: the housing's bustle, aft of the gun, so the part is painted in the turret's own finish.
    guide.target().crop((1362, 330, 1774, 580)).save(HERE / "rail-part-material.png")
    print("wrote rail-part-guide.png and rail-part-material.png", out.size)


def compose():
    part = Image.open(HERE / "rail-part-raw.png").convert("RGBA")
    # The raw comes back on its own canvas. Its painted extent is scaled uniformly to run from the muzzle to the
    # mantlet's aft edge, centred on the turret's line, so the capacitor box fills the mantlet between the cheeks.
    painted = part.crop(part.getchannel("A").point(lambda v: 255 if v > 128 else 0).getbbox())
    width = MANTLET_AFT_X - guide.MUZZLE_X
    height = round(width * painted.height / painted.width)
    gun = painted.resize((width, height), Image.LANCZOS)
    # The approved turret keeps its twin mantlet stubs between the cheeks; only the old barrel and sleeve go.
    target = guide.target()
    out = target.copy()
    ImageDraw.Draw(out).rectangle(OLD_GUN, fill=(0, 0, 0, 0))
    out.alpha_composite(gun, (guide.MUZZLE_X, guide.CY - height // 2))
    # The housing's front plate stays over the part's aft end, as it sits over every head's mantlet.
    out.alpha_composite(target.crop((FRONT_PLATE_X, 0, target.width, target.height)), (FRONT_PLATE_X, 0))
    out.save(HERE / "rail-raw.png")
    print("wrote rail-raw.png")


if __name__ == "__main__":
    {"guide": part_guide, "compose": compose}[sys.argv[1] if len(sys.argv) > 1 else "guide"]()
