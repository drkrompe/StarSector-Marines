"""Build the ruins-themes prop sheet from the accepted ImageGen raws (``RuinsThemeDressing``).

Each piece is cut mechanically from its raw at its guide frame (``make_guide.py``) carried through the raw's measured
scale, widened by half a gutter so a piece that bled past its block keeps its edge, then packed by
``art-source/doodads/stitch_atlas.py`` at 128 px a cell, 24 cells wide, which crops to the visible pixels and fits each piece to its
footprint. Floor patches lose their faint outer ring (the near-white feather ImageGen drew round every spill, which
reads as a halo on the floor) and are capped below opaque so the floor shows through. Run from the repository root:

    python art-source/ruins-themes/export.py

Writes ``mod/graphics/doodads/ruins-themes.png`` and ``mod/data/tilesets/ruins-themes.tileset.json``.
"""

import sys
import tempfile
from pathlib import Path

import numpy as np
from PIL import Image, ImageChops, ImageFilter

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(ROOT / "art-source" / "doodads"))

import make_guide  # noqa: E402
from stitch_atlas import BuildOptions, build_atlas  # noqa: E402

# Family -> accepted raw, and the pieces of it that ship (the concept sheets' own patches do not).
RAWS = {
    "frost": ("ruins-frost.raw.png", ("cryo-rack", "frost-heatsink")),
    "vats": ("ruins-vats.raw.png", ("culture-vat", "grow-bed")),
    "patches": ("ruins-patches.raw.png", ("frost-patch-a", "frost-patch-b", "growth-patch-a", "growth-patch-b")),
    "plant-store": ("ruins-plant-store.raw.png", ("rack-stripped", "rack-crates", "dock-door")),
    "foundry-a": ("ruins-foundry-a.raw.png", ("crucible", "crucible-tipped", "fallen-ladle", "slag-heap", "pour-pulpit")),
    "foundry-b": ("ruins-foundry-b.raw.png", ("mould-grid", "grate-catwalk")),
    "products-a": ("ruins-products-a.raw.png", tuple(f"{p}-{s}" for p in ("cryo-unit", "vat-unit")
                                                     for s in ("whole", "half", "wrecked"))),
    "products-b": ("ruins-products-b.raw.png", tuple(f"{p}-{s}" for p in ("drone-frame", "forge-core")
                                                     for s in ("whole", "half", "wrecked"))),
    "products-c": ("ruins-products-c.raw.png", tuple(f"{p}-{s}" for p in ('core-cabinet', 'field-housing', 'magnet-segment')
                                                     for s in ("whole", "half", "wrecked"))),
    "products-d": ("ruins-products-d.raw.png", tuple(f"{p}-{s}" for p in ('catalyst-bed', 'spool-drum')
                                                     for s in ("whole", "half", "wrecked"))),
    "products-e": ("ruins-products-e.raw.png", tuple(f"{p}-{s}" for p in ('bore-head', 'projector-stack')
                                                     for s in ("whole", "half", "wrecked"))),
}

# Pieces cut at their exact guide frame instead of half a gutter wider: the dock door bled into the gutter beside
# the racks, so a wider rack cut picks up its edge.
# Products are cut at their frame too and keep the whole frame as their canvas, so a product's three states share one
# scale and the wrecked one's fallen fragments stay where they lie.
TIGHT = {"rack-stripped", "rack-crates", "crucible", "crucible-tipped", "mould-grid", "grate-catwalk"}.union(*(RAWS[f][1] for f in RAWS if f.startswith("products-")))

# The plant line, cut by measured boxes in the raw's own pixels rather than by its guide: ImageGen drew the sheet a
# sixth narrower than the guide and joined the module to the line end by its bed. The module and the line end share
# one vertical window centred on the bed (rows 247-462), so the bed's cross-section is the same at every seam, and
# each is cut at the middle of a roller gap one pitch (about 37.8 px) in from its end cap: the module from gap 75 to
# gap 1020 (25 pitches), the line end from the hopper's edge to gap 1539. Each is then stretched to its exact
# footprint, so a module meets the next module, or a line end, gap to gap. The cradle is cut at its own bounds.
CELL_PX = 128
LINE_ROWS = (65, 644)
PLANT_LINE = {
    "line-module": ((75, LINE_ROWS[0], 1020, LINE_ROWS[1]), (6, 3)),
    "line-end": ((1083, LINE_ROWS[0], 1539, LINE_ROWS[1]), (3, 3)),
    "empty-cradle": ((1612, 86, 2145, 627), (3, 3)),
}


def cut_plant_line():
    raw = Image.open(HERE / "raw" / "ruins-plant-line.raw.png").convert("RGBA")
    for pid, (box, (fx, fy)) in PLANT_LINE.items():
        yield pid, raw.crop(box).resize((fx * CELL_PX, fy * CELL_PX), Image.LANCZOS)
# Crucibles whose melt ships as its own overlay on the same footprint, so the engine can pulse it: the guide's MELT
# pixels, carried to the raw's scale and feathered a little, go to the overlay and the rest to the body; the two
# alphas sum to the piece's own, so laid together they are the piece as drawn.
MELT_SPLIT = {"crucible": "crucible-melt", "crucible-tipped": "crucible-tipped-spill"}
MELT_FEATHER = 2.5


def split_melt(piece, guide_frame):
    rgb = np.asarray(guide_frame.convert("RGB"))
    melt = Image.fromarray(np.where((rgb == make_guide.MELT[:3]).all(axis=2), 255, 0).astype(np.uint8), "L")
    melt = melt.resize(piece.size, Image.BILINEAR).filter(ImageFilter.GaussianBlur(MELT_FEATHER))
    alpha = piece.getchannel("A")
    body, over = piece.copy(), piece.copy()
    body.putalpha(ImageChops.multiply(alpha, ImageChops.invert(melt)))
    over.putalpha(ImageChops.multiply(alpha, melt))
    return body, over


# Patch alpha: below FEATHER is the halo ring and goes; the rest is rescaled to at most the family's cap. Frost is
# pale and reads as a white disc on the ruin floor unless it lets more of the floor through than growth does.
FEATHER = 110
CAP = {"frost": 150, "growth": 205}


def cut(family):
    raw_name, keep = RAWS[family]
    raw = Image.open(HERE / "raw" / raw_name).convert("RGBA")
    guide = Image.open(ROOT / "art-source" / "templates" / f"ruins-{family}-guide.png")
    sx, sy = raw.width / guide.width, raw.height / guide.height
    half = make_guide.GUTTER / 2
    for pid, x, y, w, h, kind in make_guide.frames(family):
        if pid not in keep:
            continue
        m = 0 if pid in TIGHT else half
        box = (round((x - m) * sx), round((y - m) * sy), round((x + w + m) * sx), round((y + h + m) * sy))
        piece = raw.crop(box)
        if kind == "organic":
            alpha = piece.getchannel("A").point(
                lambda v, cap=CAP[pid.split("-")[0]]: 0 if v < FEATHER else round((v - FEATHER) * cap / (255 - FEATHER)))
            piece.putalpha(alpha)
        if pid in MELT_SPLIT:
            body, over = split_melt(piece, guide.crop((x, y, x + w, y + h)))
            yield pid, body
            yield MELT_SPLIT[pid], over
            continue
        yield pid, piece


def main() -> None:
    with tempfile.TemporaryDirectory() as scratch:
        staged = Path(scratch)
        for family in RAWS:
            for pid, piece in cut(family):
                piece.save(staged / f"{pid}.png")
        for pid, piece in cut_plant_line():
            piece.save(staged / f"{pid}.png")
        image, manifest, count = build_atlas(BuildOptions(
            input_dir=staged,
            output_image=ROOT / "mod" / "graphics" / "doodads" / "ruins-themes.png",
            output_manifest=ROOT / "mod" / "data" / "tilesets" / "ruins-themes.tileset.json",
            sheet_path="graphics/doodads/ruins-themes.png",
            metadata_path=HERE / "_atlas.json",
            columns=24,
            cell_size=CELL_PX,
            padding=4,
            id_prefix="doodad.",
            resample="lanczos",
            alpha_threshold=1,
            allow_opaque=False,
            only_prefix=None,
        ))
    print(f"Packed {count} assets")
    print(image)
    print(manifest)


if __name__ == "__main__":
    main()
