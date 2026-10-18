"""Pack the two light-role sheets, both derived from the militia technical's pickup hull.

  mod/graphics/battle/vehicles/supply-truck.png  4 x 768x576: hull, (no turret), wreck hull, (no turret)
  mod/graphics/battle/vehicles/scout-car.png     4 x 640x576: hull, (no turret), wreck hull, (no turret)

Supply truck: the technical's live and wreck hulls verbatim, the pintle mount covered by the doodad crate stack
(art-source/doodads/sources/industrial-crate-stack.png) lashed in the bed. The wreck's crates are charred and
knocked askew. No gun frame: the truck carries no weapon.

Scout car: the same hull with 128 px of the open bed cut out and the cab slid aft to close the gap (a shorter
wheelbase), repainted from militia olive toward a dust grey, and a sensor mast (dish and whip) drawn on the
shortened bed in place of the pintle. The wreck keeps the mast stump only. Frames are 640 wide, so the
VehicleType's visual length is the technical's 3.0 cells scaled by 640/768 and the pixel scale is unchanged.

Everything is facing LEFT like every vehicle sheet. Run from anywhere:
    python art-source/vehicles/light-roles/pack.py
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageEnhance

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
VEHICLES = ROOT / "mod" / "graphics" / "battle" / "vehicles"
TECHNICAL = VEHICLES / "militia-technical.png"
CRATES = ROOT / "art-source" / "doodads" / "sources" / "industrial-crate-stack.png"

SLOT_W, SLOT_H = 768, 576
LIVE, WRECK = 0, 2
# The pintle bearing on both technical hull frames: the README's 0.9875 cells aft of a 3-cell frame centre.
MOUNT = (384 + round(0.9875 * 768 / 3), 288)
BED_CENTER = (575, 288)
# The scout's cut: a straight run of open bed between the cab and the pintle.
CUT_X, CUT_W = 420, 128
SCOUT_W = SLOT_W - CUT_W


def slot(sheet, index, width=SLOT_W):
    return sheet.crop((index * width, 0, (index + 1) * width, SLOT_H))


def crate_stack(scale, char=1.0, angle=0.0):
    src = Image.open(CRATES).convert("RGBA")
    src = src.crop(src.getbbox())
    if angle:
        src = src.rotate(angle, resample=Image.BICUBIC, expand=True)
    size = (round(src.width * scale), round(src.height * scale))
    src = src.convert("RGBa").resize(size, Image.LANCZOS).convert("RGBA")
    if char < 1.0:
        rgb = ImageEnhance.Brightness(src.convert("RGB")).enhance(char)
        rgb.putalpha(src.getchannel("A"))
        src = rgb
    return src


def paste_with_shadow(base, sprite, center, offset=(7, 7), shadow=0.45):
    x = center[0] - sprite.width // 2
    y = center[1] - sprite.height // 2
    a = sprite.getchannel("A").point(lambda v: int(v * shadow))
    dark = Image.new("RGBA", sprite.size, (10, 10, 8, 0))
    dark.putalpha(a)
    base.alpha_composite(dark, (x + offset[0], y + offset[1]))
    base.alpha_composite(sprite, (x, y))


def supply_truck(technical):
    sheet = Image.new("RGBA", (SLOT_W * 4, SLOT_H), (0, 0, 0, 0))
    live = slot(technical, LIVE).copy()
    paste_with_shadow(live, crate_stack(0.41), BED_CENTER)
    sheet.paste(live, (0, 0))
    wreck = slot(technical, WRECK).copy()
    paste_with_shadow(wreck, crate_stack(0.36, char=0.5, angle=17), (BED_CENTER[0] + 10, BED_CENTER[1] - 14))
    sheet.paste(wreck, (WRECK * SLOT_W, 0))
    return sheet


def shorten(frame):
    """Remove [CUT_X, CUT_X + CUT_W) and slide the cab and hood aft, so the bed's rear stays put."""
    out = Image.new("RGBA", (SCOUT_W, SLOT_H), (0, 0, 0, 0))
    out.paste(frame.crop((CUT_X + CUT_W, 0, SLOT_W, SLOT_H)), (CUT_X, 0))
    out.paste(frame.crop((0, 0, CUT_X, SLOT_H)), (0, 0))
    # Centre the shortened hull in its narrower slot.
    box = out.getbbox()
    centred = Image.new("RGBA", out.size, (0, 0, 0, 0))
    dx = (SCOUT_W - (box[2] - box[0])) // 2 - box[0]
    centred.paste(out.crop(box), (box[0] + dx, box[1]))
    return centred, dx


def dust_grey(frame):
    """Shift militia olive toward a dust grey, keeping every value and the alpha."""
    r, g, b, a = frame.split()
    grey = Image.merge("RGB", (r, g, b)).convert("L")
    tinted = Image.merge("RGB", (
        grey.point(lambda v: min(255, int(v * 1.06 + 6))),
        grey.point(lambda v: min(255, int(v * 1.02 + 4))),
        grey.point(lambda v: int(v * 0.90)),
    ))
    out = Image.blend(Image.merge("RGB", (r, g, b)), tinted, 0.7)
    out.putalpha(a)
    return out


def mast(frame, center, broken=False):
    d = ImageDraw.Draw(frame)
    cx, cy = center
    # Base plate.
    d.rectangle((cx - 26, cy - 26, cx + 26, cy + 26), fill=(58, 60, 56, 255), outline=(24, 25, 23, 255), width=4)
    for bx, by in ((-18, -18), (18, -18), (-18, 18), (18, 18)):
        d.ellipse((cx + bx - 4, cy + by - 4, cx + bx + 4, cy + by + 4), fill=(32, 33, 31, 255))
    if broken:
        d.ellipse((cx - 11, cy - 11, cx + 11, cy + 11), fill=(30, 28, 26, 255), outline=(12, 12, 11, 255), width=3)
        d.line((cx + 4, cy - 2, cx + 30, cy - 34), fill=(20, 20, 19, 255), width=5)
        return
    # Mast shadow, dish and feed: a dish seen from above, its rim lit on the sun side.
    d.ellipse((cx - 50 + 9, cy - 50 + 9, cx + 50 + 9, cy + 50 + 9), fill=(10, 10, 8, 110))
    d.ellipse((cx - 50, cy - 50, cx + 50, cy + 50), fill=(86, 90, 88, 255), outline=(30, 31, 30, 255), width=5)
    d.ellipse((cx - 38, cy - 38, cx + 38, cy + 38), fill=(64, 67, 66, 255))
    d.arc((cx - 46, cy - 46, cx + 46, cy + 46), 200, 320, fill=(150, 154, 148, 255), width=4)
    d.line((cx - 30, cy, cx + 30, cy), fill=(40, 41, 40, 255), width=4)
    d.ellipse((cx - 9, cy - 9, cx + 9, cy + 9), fill=(200, 140, 40, 255), outline=(40, 30, 10, 255), width=2)
    # Whip antenna aft of the dish.
    d.line((cx + 58, cy + 62, cx + 104, cy + 112), fill=(18, 18, 17, 255), width=4)
    d.ellipse((cx + 52, cy + 56, cx + 64, cy + 68), fill=(40, 40, 38, 255))


def scout_car(technical):
    sheet = Image.new("RGBA", (SCOUT_W * 4, SLOT_H), (0, 0, 0, 0))
    for index, broken in ((LIVE, False), (WRECK, True)):
        frame, dx = shorten(slot(technical, index))
        frame = dust_grey(frame)
        # The mast stands on the technical's pintle ring, which moved aft-relative with the cut.
        mast(frame, (MOUNT[0] - CUT_W + dx, MOUNT[1]), broken)
        sheet.paste(frame, (index * SCOUT_W, 0))
    return sheet


def main():
    technical = Image.open(TECHNICAL).convert("RGBA")
    if technical.size != (SLOT_W * 4, SLOT_H):
        raise SystemExit("The technical sheet changed size; re-measure the bed before re-packing")
    for name, sheet in (("supply-truck.png", supply_truck(technical)), ("scout-car.png", scout_car(technical))):
        sheet.save(VEHICLES / name)
        print(f"wrote {VEHICLES / name} {sheet.size}")


if __name__ == "__main__":
    main()
