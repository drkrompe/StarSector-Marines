"""Export the military base's doodad sheet `mod/graphics/doodads/military-base.png` and its tileset.

The watchtower, saluting base and flag mast are cut from the painted raw (`raw/military-base.raw.png`, painted over
`guide.png`); the raw's drawn extent is registered on the guide's frames, since ImageGen does not always keep the
canvas. The parked armour is composited from the shipped vehicle sprites. The parade square's painted markings are drawn here: worn off-white paint over the paving the layout lays,
each line along the middle of its cell row so the pieces join end to end and at corners.

Usage: python art-source/raid-sites/military/export.py
"""
import json
import random
import sys
import tempfile
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
sys.path.insert(0, str(ROOT / "art-source" / "doodads"))
sys.path.insert(0, str(HERE))
from stitch_atlas import BuildOptions, build_atlas  # noqa: E402
import make_guide as G  # noqa: E402

SHEET = "military-base"
CELL_PX = 128
PAINT = (238, 234, 218)
PAINT_ALPHA = 240
# A painted line's width, in cells: bold, since the base close-up shows a cell at about 13 px.
LINE = 0.25

# id: (name, what, cells w, cells h, cover, extra)
PIECES = {
    "watchtower": ("Watchtower", "A perimeter lookout tower: a sandbagged platform on legs, cabin, searchlight, ladder.",
                   3, 3, "med", {"ballisticHalfHeight": 0.5}),
    "saluting-base": ("Saluting base", "The low reviewing dais at the head of the parade square.", 3, 2, "light", {}),
    "flag-mast": ("Flag mast", "A tall flag mast and plain pennant, with its long shadow.", 1, 1, "none", {}),
    "parade-line": ("Parade line", "A painted line along the middle of its row; runs join end to end.", 4, 1, "none", {}),
    "parade-corner": ("Parade corner", "The painted border's corner: lines along the middle of its north row and west "
                      "column, open to the south and east.", 2, 2, "none", {}),
    "parade-ranks": ("Parade ranks", "A dashed rank line marking where a file stands; runs join end to end.", 4, 1,
                     "none", {}),
    "parade-centre": ("Parade centre", "The square's painted centre mark: a cross in a ring.", 2, 2, "none", {}),
    "parked-tank": ("Parked tank", "A Hegemony medium tank parked in the vehicle park, turret and gun forward (west).",
                    4, 2, "heavy", {"ballisticHalfHeight": 0.55}),
    "parked-apc-army": ("Parked APC", "An army APC parked in the vehicle park, nose and turret forward (west).", 3, 2,
                        "heavy", {"ballisticHalfHeight": 0.55}),
    # Round 2 (`military-base-art-2`): the structures a working base needs.
    "satcom-dish": ("Satellite uplink dish", "A ground station dish tilted up on its pedestal, a feed horn on struts, "
                    "on a railed concrete pad; for the command post's dish farm.", 3, 3, "light", {}),
    "radar-tower": ("Search radar tower", "A steel lattice tower carrying a long search-radar antenna bar, its long "
                    "shadow to the south-east.", 3, 3, "light", {}),
    "fuel-bladder": ("Fuel bladder", "A pillow fuel bladder in a low earth bund, a hose to its pump; a fuel depot "
                     "cell.", 3, 3, "light", {}),
    "generator-set": ("Generator set", "A skid-mounted diesel generator in an olive housing: exhaust stack, fuel tank, "
                      "cable runs.", 3, 2, "med", {"ballisticHalfHeight": 0.45}),
    "antenna-mast": ("Antenna mast", "A tall guyed radio mast: base plate, three guy wires to anchors, its long thin "
                     "shadow.", 1, 1, "none", {}),
    "medical-marker": ("Medical marker", "A painted panel by the medical post: a plain white cross on a green square, "
                       "worn. A floor decal; blocks nothing.", 3, 3, "none", {}),
}
# The medical marker's paint: a plain white cross on green, never the red-on-white protective emblem.
MEDICAL_GREEN = (46, 112, 64)

# Parked armour composited from the shipped vehicle sprites (graphics/battle/vehicles, plain livery, intact frames):
# frame 0 the hull and frame 1 the turret, 768x576 each, nose to the west (-x). The turret is drawn on its ring
# `mount` px ahead of the hull frame's centre, at `turret` times the hull's scale (VehicleType's turretVisualCells over
# DRAWN_LENGTH). The vehicle is fitted inside its footprint with a small margin and given a soft shadow to the
# south-east.
VEHICLES = {
    "parked-tank": ("heg-medium-tank.png", 54, 1.0),
    "parked-apc-army": ("army-apc.png", 49, 1.42 / 2.4),
}
VEHICLE_FRAME = (768, 576)
VEHICLE_MARGIN = 0.92
SHADOW_ALPHA = 0.45


def register(raw, frames):
    a = np.asarray(raw.getchannel("A")) > 64
    ys, xs = np.nonzero(a)
    gx0, gy0 = min(f[1] for f in frames), min(f[2] for f in frames)
    gx1 = max(f[1] + f[3] * G.CELL for f in frames)
    gy1 = max(f[2] + f[4] * G.CELL for f in frames)
    sx = (xs.max() + 1 - xs.min()) / (gx1 - gx0)
    sy = (ys.max() + 1 - ys.min()) / (gy1 - gy0)
    return sx, sy, xs.min() - gx0 * sx, ys.min() - gy0 * sy


def register_on_guide(raw, guide):
    """Scale and offset taking guide pixels to raw pixels: the guide's drawn (non-white) extent onto the raw's drawn
    (alpha) extent. For a guide whose pieces do not reach their frames' edges."""
    g = np.asarray(guide.convert("L")) < 250
    gy, gx = np.nonzero(g)
    a = np.asarray(raw.getchannel("A")) > 64
    ys, xs = np.nonzero(a)
    sx = (xs.max() + 1 - xs.min()) / (gx.max() + 1 - gx.min())
    sy = (ys.max() + 1 - ys.min()) / (gy.max() + 1 - gy.min())
    return sx, sy, xs.min() - gx.min() * sx, ys.min() - gy.min() * sy


def painted():
    yield from cut_raw("military-base.raw.png", G.FRAMES, None)
    yield from cut_raw("military-base-2.raw.png", G.FRAMES2, "guide-2.png")


def cut_raw(name, frames, guide):
    raw = Image.open(HERE / "raw" / name).convert("RGBA")
    if guide is None:
        sx, sy, ox, oy = register(raw, frames)
    else:
        sx, sy, ox, oy = register_on_guide(raw, Image.open(HERE / guide))
    for pid, x, y, cw, ch in frames:
        box = (round(ox + x * sx), round(oy + y * sy), round(ox + (x + cw * G.CELL) * sx),
               round(oy + (y + ch * G.CELL) * sy))
        piece = raw.crop(box).resize((cw * CELL_PX, ch * CELL_PX), Image.LANCZOS)
        piece.putalpha(piece.getchannel("A").point(lambda v: 0 if v < 8 else v))
        yield pid, piece


def worn(mask, seed):
    """Paint from a coverage mask: bright off-white with a crisp edge, lightly worn in specks and scuffs."""
    w, h = mask.size
    rnd = np.random.default_rng(seed)
    noise = Image.fromarray((rnd.random((h // 4 + 1, w // 4 + 1)) * 255).astype(np.uint8)).resize((w, h),
                                                                                                    Image.BILINEAR)
    fine = Image.fromarray((rnd.random((h, w)) * 255).astype(np.uint8))
    wear = np.clip(0.55 + np.asarray(noise, np.float32) / 255, 0, 1) * (0.85 + 0.15 * np.asarray(fine, np.float32) / 255)
    alpha = np.asarray(mask, np.float32) / 255 * wear * PAINT_ALPHA
    alpha[alpha < 60] = 0
    out = Image.new("RGBA", (w, h), PAINT)
    out.putalpha(Image.fromarray(alpha.astype(np.uint8)))
    return out


def markings():
    c = CELL_PX
    lw = round(LINE * c)

    m = Image.new("L", (4 * c, c))
    ImageDraw.Draw(m).rectangle((0, c // 2 - lw // 2, 4 * c, c // 2 + lw // 2), fill=255)
    yield "parade-line", worn(m, 1)

    m = Image.new("L", (2 * c, 2 * c))
    d = ImageDraw.Draw(m)
    d.rectangle((c // 2 - lw // 2, c // 2 - lw // 2, 2 * c, c // 2 + lw // 2), fill=255)
    d.rectangle((c // 2 - lw // 2, c // 2 - lw // 2, c // 2 + lw // 2, 2 * c), fill=255)
    yield "parade-corner", worn(m, 2)

    m = Image.new("L", (4 * c, c))
    d = ImageDraw.Draw(m)
    for k in range(4):
        x0 = k * c + c // 4
        d.rectangle((x0, c // 2 - lw // 2, x0 + c // 2, c // 2 + lw // 2), fill=255)
    yield "parade-ranks", worn(m, 3)

    m = Image.new("L", (2 * c, 2 * c))
    d = ImageDraw.Draw(m)
    d.ellipse((c * 0.15, c * 0.15, c * 1.85, c * 1.85), outline=255, width=lw)
    d.rectangle((c - lw // 2, c * 0.45, c + lw // 2, c * 1.55), fill=255)
    d.rectangle((c * 0.45, c - lw // 2, c * 1.55, c + lw // 2), fill=255)
    yield "parade-centre", worn(m, 4)


def medical_marker():
    """A worn painted panel: a green square with a plain white cross, the cross a fifth of the panel wide."""
    c = CELL_PX
    size = 3 * c
    m = Image.new("L", (size, size))
    ImageDraw.Draw(m).rectangle((c * 0.25, c * 0.25, size - c * 0.25, size - c * 0.25), fill=255)
    green = worn(m, 5)
    green = Image.merge("RGBA", (*(Image.new("L", green.size, v) for v in MEDICAL_GREEN), green.getchannel("A")))
    arm, half = size * 0.2, size / 2
    m = Image.new("L", (size, size))
    d = ImageDraw.Draw(m)
    d.rectangle((half - arm / 2, c * 0.55, half + arm / 2, size - c * 0.55), fill=255)
    d.rectangle((c * 0.55, half - arm / 2, size - c * 0.55, half + arm / 2), fill=255)
    out = Image.new("RGBA", (size, size))
    out.alpha_composite(green)
    out.alpha_composite(worn(m, 6))
    yield "medical-marker", out


def vehicles():
    fw, fh = VEHICLE_FRAME
    for pid, (sprite, mount, turret_scale) in VEHICLES.items():
        sheet = Image.open(ROOT / "mod" / "graphics" / "battle" / "vehicles" / sprite).convert("RGBA")
        hull = sheet.crop((0, 0, fw, fh))
        top = sheet.crop((fw, 0, 2 * fw, fh))
        if turret_scale != 1.0:
            top = top.resize((round(fw * turret_scale), round(fh * turret_scale)), Image.LANCZOS)
        body = hull.copy()
        body.alpha_composite(top, (round(fw / 2 - mount - top.width / 2), round(fh / 2 - top.height / 2)))
        body = body.crop(body.getbbox())
        cw, ch = PIECES[pid][2], PIECES[pid][3]
        w, h = cw * CELL_PX, ch * CELL_PX
        scale = min(w * VEHICLE_MARGIN / body.width, h * VEHICLE_MARGIN / body.height)
        body = body.resize((round(body.width * scale), round(body.height * scale)), Image.LANCZOS)
        x, y = (w - body.width) // 2 - 3, (h - body.height) // 2 - 3
        out = Image.new("RGBA", (w, h))
        shade = Image.new("L", (w, h))
        shade.paste(body.getchannel("A"), (x + 8, y + 8))
        shade = shade.filter(ImageFilter.GaussianBlur(5)).point(lambda v: round(v * SHADOW_ALPHA))
        dark = Image.new("RGBA", (w, h), (0, 0, 0, 255))
        dark.putalpha(shade)
        out.alpha_composite(dark)
        out.alpha_composite(body, (x, y))
        yield pid, out


def metadata():
    assets = {}
    for order, (pid, (name, what, cw, ch, cover, extra)) in enumerate(PIECES.items(), 1):
        assets[f"{pid}.png"] = {"id": f"doodad.{pid}", "name": name, "description": what, "footprintCells": [cw, ch],
                                "preserveCanvas": True, "padding": 0, "order": order, "cover": cover, **extra}
    return {"defaults": {"cover": "none", "padding": 0, "scale": 1.0, "offset": [0, 0]}, "assets": assets}


def main():
    meta = HERE / "_atlas.json"
    meta.write_text(json.dumps(metadata(), indent=2) + "\n", encoding="utf-8")
    with tempfile.TemporaryDirectory() as scratch:
        staged = Path(scratch)
        for pid, piece in (*painted(), *markings(), *medical_marker(), *vehicles()):
            piece.save(staged / f"{pid}.png")
        build_atlas(BuildOptions(
            input_dir=staged,
            output_image=ROOT / "mod" / "graphics" / "doodads" / f"{SHEET}.png",
            output_manifest=ROOT / "mod" / "data" / "tilesets" / f"{SHEET}.tileset.json",
            sheet_path=f"graphics/doodads/{SHEET}.png",
            metadata_path=meta,
            columns=12,
            cell_size=CELL_PX,
            padding=4,
            id_prefix="doodad.",
            resample="lanczos",
            alpha_threshold=1,
            allow_opaque=False,
            only_prefix=None,
        ))
    print(f"Packed {len(PIECES)} {SHEET} assets")


if __name__ == "__main__":
    main()
