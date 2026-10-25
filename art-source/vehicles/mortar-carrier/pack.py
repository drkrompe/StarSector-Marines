"""Pack the mortar carrier's sheet (VehicleType.FIRE_SUPPORT_APC) from the APC's hulls and an ImageGen turret.

  mod/graphics/battle/vehicles/mortar-carrier.png   4 x 768x576: APC hull, mortar turret, APC wreck, wrecked turret

The hulls are the heavy APC's (`army-apc.png`, slots 0 and 2) verbatim: the turret ring sits on the APC's roof
bearing (335, 288), as the twin MG's does. The turret is drawn at the hull's own scale (320 px a cell, so the
VehicleType's turretVisualCells is the hull's 2.4) with its pivot at the frame centre, gun pointing LEFT.

Each turret raw is an ImageGen repaint of a `guide.py` guide at 640 px a cell. ImageGen rescales what it is given, so
REGISTRATION records each raw's measured scale against its guide and where the guide's pivot landed. A wreck raw is an
edit of its live raw, but ImageGen rescales and moves the edit too, and torn plate sticks out past the live outline,
so `wreck_fit.fit_to` searches the scale and offset at which the wreck's solid (alpha > 128) shape overlaps the live one most.

    python art-source/vehicles/mortar-carrier/pack.py [enclosed|open|open-v2b]
"""
import sys
from pathlib import Path

from PIL import Image, ImageFilter

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from wreck_fit import fit_to  # noqa: E402

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
VEHICLES = ROOT / "mod" / "graphics" / "battle" / "vehicles"
APC = VEHICLES / "army-apc.png"
OUT = VEHICLES / "mortar-carrier.png"

SLOT_W, SLOT_H = 768, 576
LIVE, TURRET, WRECK, WRECK_TURRET = 0, 1, 2, 3
GUIDE_PX_PER_CELL = 640.0
SHEET_PX_PER_CELL = SLOT_W / 2.4

# Measured from the raws' alpha (> 128) bounding boxes against their guides' (guide.py): the raw's px per guide px
# along the gun, and the guide pivot's position in the raw.
REGISTRATION = {
    "enclosed": {"scale": 995 / 769, "pivot": (838.0, 526.0)},
    "open": {"scale": 475 / 505, "pivot": (723.0, 544.0)},
    "open-v2b": {"scale": 1.236, "pivot": (734.5, 543.5)},
}
DEFAULT = "open-v2b"


def slot(sheet, index):
    return sheet.crop((index * SLOT_W, 0, (index + 1) * SLOT_W, SLOT_H))


def turret_frame(raw, reg, like=None):
    """The raw scaled to sheet pixels with the guide's pivot on the frame centre. `like` fits a wreck to its live raw."""
    if like is not None:
        raw = fit_to(raw, like)
    k = SHEET_PX_PER_CELL / GUIDE_PX_PER_CELL / reg["scale"]
    size = (round(raw.width * k), round(raw.height * k))
    scaled = raw.convert("RGBa").resize(size, Image.LANCZOS).convert("RGBA")
    scaled = scaled.filter(ImageFilter.UnsharpMask(radius=1.5, percent=40, threshold=2))
    frame = Image.new("RGBA", (SLOT_W, SLOT_H), (0, 0, 0, 0))
    px, py = reg["pivot"]
    frame.alpha_composite(scaled, (round(SLOT_W / 2 - px * k), round(SLOT_H / 2 - py * k)))
    return frame


def main():
    option = sys.argv[1] if len(sys.argv) > 1 else DEFAULT
    reg = REGISTRATION[option]
    apc = Image.open(APC).convert("RGBA")
    if apc.size != (SLOT_W * 4, SLOT_H):
        raise SystemExit("army-apc.png changed size; re-measure before re-packing")
    live = Image.open(HERE / f"{option}-raw.png").convert("RGBA")
    wreck = Image.open(HERE / f"{option}-wreck-raw.png").convert("RGBA")
    out = Image.new("RGBA", (SLOT_W * 4, SLOT_H), (0, 0, 0, 0))
    out.paste(slot(apc, LIVE), (LIVE * SLOT_W, 0))
    out.paste(turret_frame(live, reg), (TURRET * SLOT_W, 0))
    out.paste(slot(apc, WRECK), (WRECK * SLOT_W, 0))
    out.paste(turret_frame(wreck, reg, like=live), (WRECK_TURRET * SLOT_W, 0))
    out.save(OUT)
    print(f"wrote {OUT} {out.size} ({option})")


if __name__ == "__main__":
    main()
