"""Export the Matriarch's corpse sheet: four lying poses, head-up, laid left to right with transparent gaps,
which is what the corpse sweep's SpriteSheetSlicer splits a strip on (see
`art-source/swarm-strains/export_corpses.py`). The renderer fits each frame's longest side to the body's
length (`UnitType.SWARM_MATRIARCH.renderScale`, 4.5 cells).

  python export_matriarch_corpse.py [raw.png ...]

With no arguments it exports the provisional sheet: four of the six corpse round 1 concepts
(exploration/matriarch/corpse-1/raw/), standing in until the owner picks one and its four poses are made.
Writes mod/graphics/battle/rav-matriarch-dead.png.
"""
import sys
from pathlib import Path

from PIL import Image

HERE = Path(__file__).parent
sys.path.insert(0, str(HERE / "exploration"))
sys.path.insert(0, str(HERE.parent / "swarm-strains"))
from export_corpses import column_groups, one_frame  # noqa: E402
from motion_brute import key  # noqa: E402

OUT = HERE.parent.parent / "mod" / "graphics" / "battle" / "rav-matriarch-dead.png"
PROVISIONAL = [HERE / "exploration" / "matriarch" / "corpse-1" / "raw" / f"c{i}.png" for i in (1, 2, 5, 6)]
FRAME_LONG = 400
GAP = 16
PAD = 4
ALPHA_FLOOR = 16


def frame(path):
    img = key(path)
    img.putalpha(img.getchannel("A").point(lambda v: 0 if v < ALPHA_FLOOR else v))
    img = img.crop(img.getchannel("A").getbbox())
    img = one_frame(img)                  # a fleck the slicer would cut off as its own frame is dropped
    img = img.crop(img.getchannel("A").getbbox())
    k = FRAME_LONG / max(img.size)
    return img.resize((max(1, round(img.width * k)), max(1, round(img.height * k))), Image.LANCZOS)


def main(paths):
    frames = [frame(p) for p in paths]
    width = sum(f.width for f in frames) + GAP * (len(frames) - 1) + PAD * 2
    height = max(f.height for f in frames) + PAD * 2
    sheet = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    x = PAD
    for f in frames:
        sheet.alpha_composite(f, (x, PAD + (height - 2 * PAD - f.height) // 2))
        x += f.width + GAP
    found = len(column_groups(sheet))
    if found != len(frames):
        raise ValueError(f"the slicer would find {found} frames, not {len(frames)}")
    sheet.save(OUT, optimize=True)
    print(OUT.name, sheet.size, [f.size for f in frames])


if __name__ == "__main__":
    main([Path(p) for p in sys.argv[1:]] or PROVISIONAL)
