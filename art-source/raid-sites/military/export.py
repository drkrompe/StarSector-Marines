"""Export the military base's doodad sheet `mod/graphics/doodads/military-base.png` and its tileset.

The watchtower, saluting base and flag mast are cut from the painted raw (`raw/military-base.raw.png`, painted over
`guide.png`); the raw's drawn extent is registered on the guide's frames, since ImageGen does not always keep the
canvas. The parade square's painted markings are drawn here: worn off-white paint over the paving the layout lays,
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
PAINT = (226, 218, 192)
PAINT_ALPHA = 200
LINE = 0.07  # a painted line's width, in cells

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
}


def register(raw, frames):
    a = np.asarray(raw.getchannel("A")) > 64
    ys, xs = np.nonzero(a)
    gx0, gy0 = min(f[1] for f in frames), min(f[2] for f in frames)
    gx1 = max(f[1] + f[3] * G.CELL for f in frames)
    gy1 = max(f[2] + f[4] * G.CELL for f in frames)
    sx = (xs.max() + 1 - xs.min()) / (gx1 - gx0)
    sy = (ys.max() + 1 - ys.min()) / (gy1 - gy0)
    return sx, sy, xs.min() - gx0 * sx, ys.min() - gy0 * sy


def painted():
    raw = Image.open(HERE / "raw" / "military-base.raw.png").convert("RGBA")
    sx, sy, ox, oy = register(raw, G.FRAMES)
    for pid, x, y, cw, ch in G.FRAMES:
        box = (round(ox + x * sx), round(oy + y * sy), round(ox + (x + cw * G.CELL) * sx),
               round(oy + (y + ch * G.CELL) * sy))
        piece = raw.crop(box).resize((cw * CELL_PX, ch * CELL_PX), Image.LANCZOS)
        piece.putalpha(piece.getchannel("A").point(lambda v: 0 if v < 8 else v))
        yield pid, piece


def worn(mask, seed):
    """Paint from a coverage mask: off-white, worn through in specks and scuffs."""
    w, h = mask.size
    rnd = np.random.default_rng(seed)
    noise = Image.fromarray((rnd.random((h // 4 + 1, w // 4 + 1)) * 255).astype(np.uint8)).resize((w, h),
                                                                                                    Image.BILINEAR)
    fine = Image.fromarray((rnd.random((h, w)) * 255).astype(np.uint8))
    wear = np.minimum(np.asarray(noise, np.float32) / 255 * 1.6, 1) * (0.6 + 0.4 * np.asarray(fine, np.float32) / 255)
    alpha = np.asarray(mask.filter(ImageFilter.GaussianBlur(0.8)), np.float32) / 255 * wear * PAINT_ALPHA
    alpha[alpha < 30] = 0
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
    for k in range(8):
        x0 = k * c // 2 + c // 8
        d.rectangle((x0, c // 2 - lw // 2, x0 + c // 4, c // 2 + lw // 2), fill=255)
    yield "parade-ranks", worn(m, 3)

    m = Image.new("L", (2 * c, 2 * c))
    d = ImageDraw.Draw(m)
    d.ellipse((c * 0.35, c * 0.35, c * 1.65, c * 1.65), outline=255, width=lw)
    d.rectangle((c - lw // 2, c * 0.55, c + lw // 2, c * 1.45), fill=255)
    d.rectangle((c * 0.55, c - lw // 2, c * 1.45, c + lw // 2), fill=255)
    yield "parade-centre", worn(m, 4)


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
        for pid, piece in (*painted(), *markings()):
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
