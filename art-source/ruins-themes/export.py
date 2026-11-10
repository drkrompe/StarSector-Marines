"""Build the ruins-themes prop sheet from the accepted ImageGen raws (``RuinsThemeDressing``).

Each piece is cut mechanically from its raw at its guide frame (``make_guide.py``) carried through the raw's measured
scale, widened by half a gutter so a piece that bled past its block keeps its edge, then packed by
``art-source/doodads/stitch_atlas.py`` at 128 px a cell, which crops to the visible pixels and fits each piece to its
footprint. Floor patches lose their faint outer ring (the near-white feather ImageGen drew round every spill, which
reads as a halo on the floor) and are capped below opaque so the floor shows through. Run from the repository root:

    python art-source/ruins-themes/export.py

Writes ``mod/graphics/doodads/ruins-themes.png`` and ``mod/data/tilesets/ruins-themes.tileset.json``.
"""

import sys
import tempfile
from pathlib import Path

from PIL import Image

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
}
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
        box = (round((x - half) * sx), round((y - half) * sy), round((x + w + half) * sx), round((y + h + half) * sy))
        piece = raw.crop(box)
        if kind == "organic":
            alpha = piece.getchannel("A").point(
                lambda v, cap=CAP[pid.split("-")[0]]: 0 if v < FEATHER else round((v - FEATHER) * cap / (255 - FEATHER)))
            piece.putalpha(alpha)
        yield pid, piece


def main() -> None:
    with tempfile.TemporaryDirectory() as scratch:
        staged = Path(scratch)
        for family in RAWS:
            for pid, piece in cut(family):
                piece.save(staged / f"{pid}.png")
        image, manifest, count = build_atlas(BuildOptions(
            input_dir=staged,
            output_image=ROOT / "mod" / "graphics" / "doodads" / "ruins-themes.png",
            output_manifest=ROOT / "mod" / "data" / "tilesets" / "ruins-themes.tileset.json",
            sheet_path="graphics/doodads/ruins-themes.png",
            metadata_path=HERE / "_atlas.json",
            columns=8,
            cell_size=128,
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
