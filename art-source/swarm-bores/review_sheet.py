"""Review sheets for the swarm bore and spore-colony art: pass 1 against the current sprites, at two zooms,
and the round-3 purple recolour beside the pass-1 palette. 1920x1080, written under ``build/swarm-bores/review/``.

Pass 1 is read out of Git (``PASS1_COMMIT``), so the comparison needs nothing kept on disk. The grounds are flat
swatches of the colony's measured paving and grass and of a dull creep stand-in, so a sprite's
legibility is judged against each.

Run from the repository root after ``export.py`` (and ``export.py --palette purple`` for sheet 05):
``python art-source/swarm-bores/review_sheet.py``.
"""

import io
import random
import subprocess
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, str(Path(__file__).resolve().parent))
import make_guide  # noqa: E402
from export import NAMES  # noqa: E402

PASS1_COMMIT = "dd37175fe"
PASS1_CELLS = {"bore": 3.0, "mother-bore": 3.8, "spore-colony-fissure": 1.0, "spore-colony-bore": 1.35,
               "spore-colony-maw": 1.7}
LABELS = {"bore": "Bore", "mother-bore": "Mother bore", "spore-colony-fissure": "Colony, fissure grade",
          "spore-colony-bore": "Colony, bore grade", "spore-colony-maw": "Colony, maw grade"}
W, H = 1920, 1080
BACKDROP = (40, 40, 42)
GROUNDS = {"paving": (150, 124, 98), "grass": (96, 104, 72), "creep": (0x4c, 0x44, 0x3c)}
TEXT = (232, 230, 224)

ROOT = Path(__file__).resolve().parent.parent.parent


def font(size: int):
    for name in ("arial.ttf", "DejaVuSans.ttf", "Arial.ttf"):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default()


def pass1(part: str, glow: bool = False) -> Image.Image:
    name = NAMES[part] + ("-glow" if glow else "")
    blob = subprocess.run(["git", "show", f"{PASS1_COMMIT}:mod/graphics/structures/{name}.png"], cwd=ROOT,
                          capture_output=True, check=True).stdout
    return Image.open(io.BytesIO(blob)).convert("RGBA")


def current(part: str, variant: int, folder: Path | None = None) -> Image.Image:
    folder = folder or ROOT / "mod" / "graphics" / "structures"
    return Image.open(folder / f"{NAMES[part]}-{variant}.png").convert("RGBA")


def at(sprite: Image.Image, cells: float, ppc: float) -> Image.Image:
    edge = max(1, round(cells * ppc))
    return sprite.resize((edge, edge), Image.Resampling.LANCZOS)


def place(canvas: Image.Image, sprite: Image.Image, cx: float, cy: float):
    canvas.alpha_composite(sprite, (round(cx - sprite.width / 2), round(cy - sprite.height / 2)))


def heading(draw: ImageDraw.ImageDraw, text: str, y: int = 24):
    draw.text((32, y), text, fill=TEXT, font=font(30))


def label(draw: ImageDraw.ImageDraw, x: float, y: float, text: str, size: int = 20):
    f = font(size)
    w = draw.textlength(text, font=f)
    draw.text((x - w / 2, y), text, fill=TEXT, font=f)


def compare_sheet(parts, ppc: float, title: str) -> Image.Image:
    """Pass 1 on the left of each band, then every pass-2 variant, all on paving at one scale."""
    sheet = Image.new("RGBA", (W, H), BACKDROP)
    draw = ImageDraw.Draw(sheet)
    heading(draw, title)
    band_h = (H - 90) / len(parts)
    for row, part in enumerate(parts):
        y0 = 80 + row * band_h
        draw.rectangle((0, y0, W, y0 + band_h - 8), fill=GROUNDS["paving"])
        cy = y0 + (band_h - 8) / 2 - 10
        n = make_guide.VARIANTS[part]
        slots = n + 1
        slot_w = (W - 120) / slots
        for k in range(slots):
            cx = 60 + slot_w * (k + 0.5)
            if k == 0:
                place(sheet, at(pass1(part), PASS1_CELLS[part], ppc), cx, cy)
                place(sheet, at(pass1(part, glow=True), PASS1_CELLS[part], ppc), cx, cy)
                text = f"{LABELS[part]}: pass 1"
            else:
                place(sheet, at(current(part, k), make_guide.CANVAS_CELLS[part], ppc), cx, cy)
                text = f"pass 2, variant {k}"
            label(draw, cx, y0 + band_h - 38, text)
        if row == 0:
            # One-cell reference square.
            draw.rectangle((W - 50 - ppc, y0 + 10, W - 50, y0 + 10 + ppc), outline=TEXT, width=1)
    return sheet


def field_sheet(ppc: float) -> Image.Image:
    """The same scatter of bodies twice, pass 1 on the left and pass 2 on the right, over three grounds."""
    sheet = Image.new("RGBA", (W, H), BACKDROP)
    draw = ImageDraw.Draw(sheet)
    heading(draw, f"At play zoom, {ppc:.0f} px per cell: pass 1 (left) and pass 2 (right), on paving, grass and creep")
    rng = random.Random(7)
    layout = [("mother-bore", 0.5, 0.42)]
    for _ in range(2):
        layout.append(("bore", rng.uniform(0.15, 0.85), rng.uniform(0.15, 0.85)))
    for part in ("spore-colony-fissure", "spore-colony-fissure", "spore-colony-bore", "spore-colony-bore",
                 "spore-colony-maw"):
        layout.append((part, rng.uniform(0.1, 0.9), rng.uniform(0.1, 0.9)))
    # Push the scatter apart so nothing sits on the mother bore.
    for i, (p, x, y) in enumerate(layout):
        if i and abs(x - 0.5) < 0.22 and abs(y - 0.42) < 0.3:
            layout[i] = (p, x + (0.3 if x >= 0.5 else -0.3), y)
    grounds = list(GROUNDS.items())
    half_w = (W - 96) / 2
    band_h = (H - 100) / len(grounds)
    variant_count = {}
    for row, (gname, colour) in enumerate(grounds):
        for side in range(2):
            x0 = 32 + side * (half_w + 32)
            y0 = 80 + row * band_h
            box = (x0, y0, x0 + half_w, y0 + band_h - 10)
            draw.rectangle(box, fill=colour)
            tile = Image.new("RGBA", (round(half_w), round(band_h - 10)), (0, 0, 0, 0))
            for i, (part, fx, fy) in enumerate(layout):
                cx, cy = fx * tile.width, fy * tile.height
                if side == 0:
                    s = at(pass1(part), PASS1_CELLS[part], ppc)
                    place(tile, s, cx, cy)
                    place(tile, at(pass1(part, glow=True), PASS1_CELLS[part], ppc), cx, cy)
                else:
                    variant_count[part] = variant_count.get(part, 0) + 1
                    v = 1 + (i + row) % make_guide.VARIANTS[part]
                    place(tile, at(current(part, v), make_guide.CANVAS_CELLS[part], ppc), cx, cy)
            sheet.alpha_composite(tile, (round(x0), round(y0)))
            draw.text((x0 + 10, y0 + 6), f"{'pass 1' if side == 0 else 'pass 2'} on {gname}", fill=TEXT, font=font(18))
    return sheet


def palette_sheet(ppc: float, colony_ppc: float) -> Image.Image:
    """One painting of each body: pass 1's palette, pass 2 as shipped (runner palette), pass 2 recoloured to the round-3 purples."""
    purple_dir = ROOT / "build" / "swarm-bores" / "purple"
    sheet = Image.new("RGBA", (W, H), BACKDROP)
    draw = ImageDraw.Draw(sheet)
    heading(draw, f"Palette: pass 1 | pass 2, runner palette (shipped) | pass 2, Rav Bloom round-3 purples. "
                  f"Bores {ppc:.0f}, colonies {colony_ppc:.0f} px/cell")
    rows = [("pass 1 palette", None), ("pass 2, runner palette", "runner"), ("pass 2, round-3 purple", "purple")]
    parts = make_guide.PARTS
    band_h = (H - 100) / len(rows)
    slot_w = (W - 260) / len(parts)
    for row, (name, which) in enumerate(rows):
        y0 = 80 + row * band_h
        for k, part in enumerate(parts):
            ground = GROUNDS["paving"] if k < 2 else GROUNDS["creep"]
            x0 = 230 + slot_w * k
            draw.rectangle((x0 + 4, y0, x0 + slot_w - 4, y0 + band_h - 10), fill=ground)
            cx, cy = x0 + slot_w / 2, y0 + (band_h - 10) / 2
            scale = ppc if k < 2 else colony_ppc
            if which is None:
                place(sheet, at(pass1(part), PASS1_CELLS[part], scale), cx, cy)
                place(sheet, at(pass1(part, glow=True), PASS1_CELLS[part], scale), cx, cy)
            else:
                folder = purple_dir if which == "purple" else None
                place(sheet, at(current(part, 1, folder), make_guide.CANVAS_CELLS[part], scale), cx, cy)
            if row == 0:
                label(draw, cx, y0 + band_h - 36, LABELS[part], 18)
        draw.text((32, y0 + band_h / 2 - 12), name, fill=TEXT, font=font(20))
    return sheet


def main() -> None:
    out = ROOT / "build" / "swarm-bores" / "review"
    out.mkdir(parents=True, exist_ok=True)
    sheets = {
        "01-bores-pass1-vs-pass2-64px.png": compare_sheet(("bore", "mother-bore"), 64,
                                                          "Bores at 64 px per cell: pass 1, then each pass-2 variant (square = one cell)"),
        "02-colonies-pass1-vs-pass2-64px.png": compare_sheet(
            ("spore-colony-fissure", "spore-colony-bore", "spore-colony-maw"), 64,
            "Spore colonies at 64 px per cell: pass 1, then each pass-2 variant (square = one cell)"),
        "03-play-zoom-32px.png": field_sheet(32),
    }
    if (ROOT / "build" / "swarm-bores" / "purple").is_dir():
        sheets["05-palette-swatches.png"] = palette_sheet(64, 128)
    for name, image in sheets.items():
        image.convert("RGB").save(out / name)
        print(out / name)


if __name__ == "__main__":
    main()
