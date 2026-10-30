"""Build the active-protection kit's overlays: mod/graphics/battle/vehicles/kit-active-protection{,-turret}.png.

The active protection was the retired Parapet's own turret (art-source/vehicles/tank-screened, whose pack.py writes
screened-turret-frame.png here): an interceptor
launcher box on each rear flank of the turret and a sensor panel inset on each cheek, painted by ImageGen onto the
approved breakthrough turret. Now that it is a rack kit, those painted launchers and panels are lifted off that turret
as an overlay: on the Onager they ride its turret housing where the Parapet wore them, and on any other gun tank they
are set on the hull deck, launchers on the rear flanks inside the tracks and panels on the front cheeks.

Sources (both 768x768 live turret frames):
  * screened-turret-frame.png: the Parapet's live turret frame (army-tank-screened.png frame 1);
  * stock-turret-frame.png: the breakthrough's live turret frame (army-tank.png frame 1), the same registration.
A part is the pixels the two frames disagree on inside its box (the fit, not the housing under it). Each is scaled
from the turret frame's 98.6 px/cell (7.789194 cells) to the hull frame's 178.6 px/cell (4.3 cells), so it keeps its
painted size, and set on the hull frame with a soft shadow down and right, as the sheets' baked shadows fall.

Two outputs, each one 768x768 frame facing LEFT:
  * kit-active-protection-turret.png, in the Onager's turret frame registration: the fit where the Parapet wore it,
    on the angular turret housing every Onager head shares, so it turns with the head;
  * kit-active-protection.png, in the breakthrough hull frame's registration: the fit bolted to the hull deck, for a
    gun tank whose heads are not that housing (the laser tank), drawn exactly over the live hull.
Run from anywhere: python art-source/vehicles/kit-active-protection/build.py
"""
from pathlib import Path

from PIL import Image, ImageChops, ImageFilter

HERE = Path(__file__).resolve().parent
VEHICLES = HERE.parents[2] / "mod" / "graphics" / "battle" / "vehicles"
OUT = VEHICLES / "kit-active-protection.png"
OUT_TURRET = VEHICLES / "kit-active-protection-turret.png"
FRAME = 768
TURRET_PX_PER_CELL = FRAME / 7.789194
HULL_PX_PER_CELL = FRAME / 4.3
SCALE = HULL_PX_PER_CELL / TURRET_PX_PER_CELL

# Boxes in the turret frame (x0, y0, x1, y1), and where each part's centre lands on the hull frame. The hull's nose
# is to the left; its tracks run along y 187..216 and 550..574, the deck between them; the engine grilles are aft.
PARTS = [
    ((412, 286, 492, 330), (604, 252)),   # launcher, upper rear flank
    ((412, 437, 492, 482), (604, 516)),   # launcher, lower rear flank
    ((306, 312, 356, 348), (150, 250)),   # sensor panel, upper front cheek
    ((306, 427, 356, 463), (150, 518)),   # sensor panel, lower front cheek
]
DIFF_FLOOR = 60
SHADOW_OFFSET = (7, 7)
SHADOW_ALPHA = .55


def part(screened, stock, box):
    """The fit inside {@code box}: screened pixels that differ from the stock turret, alpha kept, the rest cleared."""
    a = screened.crop(box)
    b = stock.crop(box)
    diff = ImageChops.difference(a.convert("RGB"), b.convert("RGB")).convert("L")
    mask = diff.point(lambda v: 255 if v * 3 > DIFF_FLOOR else 0).filter(ImageFilter.MaxFilter(3)).filter(
        ImageFilter.MinFilter(3))
    alpha = ImageChops.multiply(a.getchannel("A"), mask)
    a.putalpha(alpha)
    return a


def shadowed(parts, offset, blur):
    """{@code parts} over a soft shadow of themselves, down and right."""
    out = Image.new("RGBA", (FRAME, FRAME), (0, 0, 0, 0))
    shade = parts.getchannel("A").point(lambda v: int(v * SHADOW_ALPHA)).filter(ImageFilter.GaussianBlur(blur))
    out.putalpha(ImageChops.offset(shade, *offset))
    out.alpha_composite(parts)
    return out


def main():
    screened = Image.open(HERE / "screened-turret-frame.png").convert("RGBA")
    stock = Image.open(HERE / "stock-turret-frame.png").convert("RGBA")
    hull = Image.new("RGBA", (FRAME, FRAME), (0, 0, 0, 0))
    turret = Image.new("RGBA", (FRAME, FRAME), (0, 0, 0, 0))
    for box, centre in PARTS:
        piece = part(screened, stock, box)
        turret.alpha_composite(piece, box[:2])
        w, h = round(piece.width * SCALE), round(piece.height * SCALE)
        piece = piece.convert("RGBa").resize((w, h), Image.LANCZOS).convert("RGBA")
        hull.alpha_composite(piece, (round(centre[0] - w / 2), round(centre[1] - h / 2)))
    shadowed(hull, SHADOW_OFFSET, 4).save(OUT)
    # On the turret the parts are painted where they stood, with their own baked shading: no added shadow.
    turret.save(OUT_TURRET)
    print(f"wrote {OUT} and {OUT_TURRET}")


if __name__ == "__main__":
    main()
