"""Build the Hegemony emitter sprite in mod/graphics/battle/vehicles/ from an ImageGen master beside this file.

  cannon-master.png     a converted cannon: blackened barrel, copper cooling rings, a recessed red lens
  projector-master.png  an industrial projector: tan plate shroud, radiator slats, a red port between hazard stripes

The master is reskinned from guide.png with true alpha. The cut is mechanical and measured, as the roof machine gun's
is (art-source/vehicles/pintle-lmg/build.py): the aperture's tip is the first opaque row and the rear of the power
coupling the last, so the sprite runs muzzle to rear as the runtime draws it; the barrel axis is the mean midpoint of
the opaque span over the barrel's rows (the top fifth), and the cut is symmetric about it and .4 of the length wide,
the mech PL-3 drawing's proportion. Alpha below 8 is cleared. Facing north, 160 x 400 px.

Run from anywhere: python art-source/vehicles/hegemony-emitter/build.py [master.png [out.png]]
"""
import sys
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[2]
OUT = REPO / "mod" / "graphics" / "battle" / "vehicles" / "hegemony-emitter.png"
OUT_W, OUT_H = 160, 400
WIDTH_SHARE = OUT_W / OUT_H


def barrel_axis(alpha: Image.Image, top: int, bottom: int) -> float:
    mids = []
    for y in range(top + 2, top + (bottom - top) // 5, 4):
        xs = [x for x in range(alpha.width) if alpha.getpixel((x, y)) >= 128]
        if xs:
            mids.append((min(xs) + max(xs) + 1) / 2)
    return sum(mids) / len(mids)


def build(master: Path, out: Path = OUT) -> None:
    image = Image.open(master).convert("RGBA")
    alpha = image.getchannel("A").point(lambda v: 0 if v < 8 else v)
    image.putalpha(alpha)
    solid = alpha.point(lambda v: 255 if v >= 24 else 0)
    left, top, right, bottom = solid.getbbox()
    axis = barrel_axis(alpha, top, bottom)
    half = (bottom - top) * WIDTH_SHARE / 2
    if left < axis - half or right > axis + half:
        raise SystemExit("the drawing reaches past the symmetric cut")
    cut = image.crop((round(axis - half), top, round(axis + half), bottom))
    cut.resize((OUT_W, OUT_H), Image.Resampling.LANCZOS).save(out)
    print(f"axis x {axis:.1f}, tip y {top}, rear y {bottom}; wrote {out}")


if __name__ == "__main__":
    build(Path(sys.argv[1]) if len(sys.argv) > 1 else HERE / "cannon-master.png",
          Path(sys.argv[2]) if len(sys.argv) > 2 else OUT)
