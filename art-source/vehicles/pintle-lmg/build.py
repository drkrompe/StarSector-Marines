"""Build the roof light machine gun sprite in mod/graphics/battle/vehicles/ from the ImageGen master beside this file.

  pintle-dark-master.png -> pintle-lmg.png   blackened gun, olive drum: reads against the olive gun plate (adopted)
  pintle-master.png                           candidate: the gun painted olive like the plate, lost against it

The master is reskinned from guide.png with true alpha. The cut is mechanical and measured, not trimmed by eye:

- the muzzle tip is the first opaque row and the rear of the spade grips the last, so the sprite runs muzzle to rear
  exactly as the runtime draws it (VehicleGun.lengthCells, pivot VehicleGun.RING_FROM_REAR up from the bottom);
- the barrel axis is the mean midpoint of the opaque span over the barrel's rows (the top third), and the cut is
  symmetric about it, so the barrel stands on the sprite's centre column and turns on the plate's centre; the drum
  hangs to the right inside the cut, with clear canvas on the left to balance it;
- the cut is .6 of the length wide, the guide's own canvas share for the drum's reach.

Alpha below 8 (ImageGen's stray near-zero specks) is cleared; all other alpha is the generation's own. Facing north,
240 x 400 px.

Run from anywhere: python art-source/vehicles/pintle-lmg/build.py [master.png [out.png]]
"""
import sys
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[2]
OUT = REPO / "mod" / "graphics" / "battle" / "vehicles" / "pintle-lmg.png"
OUT_W, OUT_H = 240, 400
WIDTH_SHARE = OUT_W / OUT_H


def barrel_axis(alpha: Image.Image, top: int, bottom: int) -> float:
    mids = []
    for y in range(top + 2, top + (bottom - top) // 3, 4):
        xs = [x for x in range(alpha.width) if alpha.getpixel((x, y)) >= 128]
        if xs:
            mids.append((min(xs) + max(xs) + 1) / 2)
    return sum(mids) / len(mids)


def build(master: Path, out: Path = OUT) -> None:
    image = Image.open(master).convert("RGBA")
    alpha = image.getchannel("A").point(lambda v: 0 if v < 8 else v)
    image.putalpha(alpha)
    _, top, _, bottom = alpha.point(lambda v: 255 if v >= 24 else 0).getbbox()
    axis = barrel_axis(alpha, top, bottom)
    half = (bottom - top) * WIDTH_SHARE / 2
    left, _, right, _ = alpha.point(lambda v: 255 if v >= 24 else 0).getbbox()
    if left < axis - half or right > axis + half:
        raise SystemExit("the drawing reaches past the symmetric cut")
    cut = image.crop((round(axis - half), top, round(axis + half), bottom))
    cut.resize((OUT_W, OUT_H), Image.Resampling.LANCZOS).save(out)
    print(f"axis x {axis:.1f}, muzzle y {top}, rear y {bottom}; wrote {out}")


if __name__ == "__main__":
    build(Path(sys.argv[1]) if len(sys.argv) > 1 else HERE / "pintle-dark-master.png",
          Path(sys.argv[2]) if len(sys.argv) > 2 else OUT)
