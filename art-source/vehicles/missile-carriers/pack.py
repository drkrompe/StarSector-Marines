"""Pack the two missile-carrier sheets, both derived from shipped hulls and the mech missile-pod sprites.

  mod/graphics/battle/vehicles/lrm-carrier.png   4 x 768x576: APC hull, LRM launcher, APC wreck, wrecked launcher
  mod/graphics/battle/vehicles/srm-technical.png 4 x 768x576: technical hull, SRM pod, technical wreck, wrecked pod

LRM carrier: the heavy APC's live and wreck hulls (`army-apc.png`, slots 0 and 2) with a turntable plate painted on
the roof where the twin MG stood, and ONE mech LRM pod (`mech-modular-topdown/lrm-pod.png`) alone in its turret
frame. The plate stays on the hull because the renderer foreshortens the turret frame as the pod stands up to fire
(`VehicleType.turretRaiseFold`), and a flat turntable must not tilt with it.
SRM technical: the militia technical's hulls verbatim (`militia-technical.png`, slots 0 and 2) with the mech SRM pod
(`mech-modular-topdown/srm-pod.png`) on a pintle plate. The pods are the same launchers a mech carries, which is
what the hulls fire.

Turret frames are drawn at the hull's own pixel scale, so each `VehicleType` sets turretVisualCells to its hull's
visual length and the frame's centre is the turntable's pivot. Pods are drawn nose-up in their own sprites and
are turned to face LEFT like every vehicle sheet. A wrecked pod is the pod's ImageGen damage edit
(`<name>-wreck-raw.png`, made from `wreck_guide.py`'s edit target with `<name>-wreck.prompt.txt`), thrown a few degrees
askew on its mount. Run from anywhere:
    python art-source/vehicles/missile-carriers/pack.py
"""
from pathlib import Path

import sys

from PIL import Image, ImageDraw, ImageFilter

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from wreck_fit import fit_to  # noqa: E402

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
GRAPHICS = ROOT / "mod" / "graphics"
VEHICLES = GRAPHICS / "battle" / "vehicles"
APC = VEHICLES / "army-apc.png"
TECHNICAL = VEHICLES / "militia-technical.png"
LRM_POD = GRAPHICS / "battle" / "mech-modular-topdown" / "lrm-pod.png"
SRM_POD = GRAPHICS / "battle" / "mech-modular-topdown" / "srm-pod.png"

SLOT_W, SLOT_H = 768, 576
LIVE, TURRET, WRECK, WRECK_TURRET = 0, 1, 2, 3
APC_PX_PER_CELL = SLOT_W / 2.4
TECHNICAL_PX_PER_CELL = SLOT_W / 3.0
LRM_POD_CELLS = 0.72                 # nose to tail; VehicleType.LRM_CARRIER's raise hinge is half of it
APC_ROOF_BEARING = (335, 288)
WRECK_GUIDE_LENGTH_PX = 720           # the pod's nose-to-tail length in wreck_guide.py's edit targets       # the twin MG's bearing in the APC's live and wreck frames (apc/README.md)


def slot(sheet, index):
    return sheet.crop((index * SLOT_W, 0, (index + 1) * SLOT_W, SLOT_H))


def pod(path, length_px):
    """The pod sprite cropped, turned nose-left and scaled so its nose-to-tail length is length_px."""
    src = Image.open(path).convert("RGBA")
    src = src.crop(src.getbbox()).rotate(90, expand=True)
    scale = length_px / src.width
    size = (round(src.width * scale), round(src.height * scale))
    out = src.convert("RGBa").resize(size, Image.LANCZOS).convert("RGBA")
    return out.filter(ImageFilter.UnsharpMask(radius=2, percent=60, threshold=2))


def wrecked_pod(name, length_px):
    """The pod's ImageGen damage edit (`<name>-wreck-raw.png`), fitted over its edit target and scaled like the pod."""
    guide = Image.open(HERE / f"{name}-wreck-guide.png").convert("RGBA")
    raw = Image.open(HERE / f"{name}-wreck-raw.png").convert("RGBA")
    fitted = fit_to(raw, guide)
    scale = length_px / WRECK_GUIDE_LENGTH_PX
    out = fitted.convert("RGBa").resize((round(fitted.width * scale), round(fitted.height * scale)),
                                        Image.LANCZOS).convert("RGBA")
    return out.crop(out.getbbox()).filter(ImageFilter.UnsharpMask(radius=2, percent=60, threshold=2))


def shadowed(frame, sprite, center, offset=(8, 8), alpha=0.42):
    x = round(center[0] - sprite.width / 2)
    y = round(center[1] - sprite.height / 2)
    dark = Image.new("RGBA", sprite.size, (10, 10, 8, 0))
    dark.putalpha(sprite.getchannel("A").point(lambda v: int(v * alpha)))
    frame.alpha_composite(dark, (x + offset[0], y + offset[1]))
    frame.alpha_composite(sprite, (x, y))


def plate(frame, center, radius, broken=False):
    d = ImageDraw.Draw(frame)
    cx, cy = center
    fill = (40, 41, 38, 255) if broken else (66, 70, 60, 255)
    d.ellipse((cx - radius + 8, cy - radius + 8, cx + radius + 8, cy + radius + 8), fill=(10, 10, 8, 100))
    d.ellipse((cx - radius, cy - radius, cx + radius, cy + radius), fill=fill, outline=(24, 25, 23, 255), width=5)
    d.ellipse((cx - radius + 14, cy - radius + 14, cx + radius - 14, cy + radius - 14),
              outline=(34, 36, 32, 255), width=3)


def lrm_launcher(broken=False):
    """One LRM pod centred on the frame centre (the turntable pivot); the plate is on the hull."""
    frame = Image.new("RGBA", (SLOT_W, SLOT_H), (0, 0, 0, 0))
    center = (SLOT_W // 2, SLOT_H // 2)
    length = round(LRM_POD_CELLS * APC_PX_PER_CELL)
    one = wrecked_pod("lrm", length).rotate(-11, resample=Image.BICUBIC, expand=True) if broken         else pod(LRM_POD, length)
    shadowed(frame, one, center)
    return frame


def with_plate(hull_frame, broken=False):
    out = hull_frame.copy()
    plate(out, APC_ROOF_BEARING, round(0.36 * APC_PX_PER_CELL), broken)
    return out


def srm_pod(broken=False):
    frame = Image.new("RGBA", (SLOT_W, SLOT_H), (0, 0, 0, 0))
    center = (SLOT_W // 2, SLOT_H // 2)
    plate(frame, center, round(0.32 * TECHNICAL_PX_PER_CELL), broken)
    length = round(1.05 * TECHNICAL_PX_PER_CELL)
    one = wrecked_pod("srm", length).rotate(14, resample=Image.BICUBIC, expand=True) if broken         else pod(SRM_POD, length)
    shadowed(frame, one, center)
    return frame


def sheet(hulls, live_turret, wreck_turret, plated=False):
    out = Image.new("RGBA", (SLOT_W * 4, SLOT_H), (0, 0, 0, 0))
    live, wreck = slot(hulls, LIVE), slot(hulls, WRECK)
    if plated:
        live, wreck = with_plate(live), with_plate(wreck, broken=True)
    out.paste(live, (LIVE * SLOT_W, 0))
    out.paste(live_turret, (TURRET * SLOT_W, 0))
    out.paste(wreck, (WRECK * SLOT_W, 0))
    out.paste(wreck_turret, (WRECK_TURRET * SLOT_W, 0))
    return out


def main():
    apc = Image.open(APC).convert("RGBA")
    technical = Image.open(TECHNICAL).convert("RGBA")
    for name, hulls in (("army-apc.png", apc), ("militia-technical.png", technical)):
        if hulls.size != (SLOT_W * 4, SLOT_H):
            raise SystemExit(f"{name} changed size; re-measure before re-packing")
    sheets = (
        ("lrm-carrier.png", sheet(apc, lrm_launcher(), lrm_launcher(broken=True), plated=True)),
        ("srm-technical.png", sheet(technical, srm_pod(), srm_pod(broken=True))),
    )
    for name, packed in sheets:
        packed.save(VEHICLES / name)
        print(f"wrote {VEHICLES / name} {packed.size}")


if __name__ == "__main__":
    main()
