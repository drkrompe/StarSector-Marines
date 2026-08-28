"""Pack the parked-vehicle strips into one fixed-grid doodad atlas.

The two source strips are AI-generated: their frames sit at whatever widths the
model drew them at, which is why they were originally sliced by alpha at load
time. A doodad reads its art from a uniform cell grid instead, so the frames are
sliced once here and re-laid into slots of a fixed cell size. That moves the
slicing from every game start to this build step, and lets a parked truck be an
ordinary registry doodad rather than a second kind of prop.

A slot is the doodad's footprint in cells. The art is *fitted* into it and
padded with transparency rather than stretched to fill: the footprint says how
much deck the truck covers, which is a judgement about the object, and a truck
is not exactly three cells by two. Stretching to the slot would squash the
longer sprites by a fifth.

Run from the repository root:

    python art-source/tilesets/pack_parked_vehicles.py
"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
REPO_ROOT = HERE.parent.parent
OUTPUT = REPO_ROOT / "mod" / "graphics" / "tilesets" / "parked-vehicles.png"

# Matches SpriteSheetSlicer, which produced these frames at runtime before the
# atlas existed. Kept identical so the ingest reproduces the frames the game
# has been drawing rather than a new reading of the same sheets.
ALPHA_THRESHOLD = 16
MIN_GAP = 4

# Cell size of the packed atlas. Well above the game's 32px grid: the sources
# are ~380px across, and sampling them down to the grid would throw away the
# detail that makes the sheet worth shipping.
CELL_PX = 128
FOOTPRINT_CELLS_X = 3
FOOTPRINT_CELLS_Y = 2
SLOT_W = CELL_PX * FOOTPRINT_CELLS_X
SLOT_H = CELL_PX * FOOTPRINT_CELLS_Y
SLOT_COLUMNS = 3


@dataclass(frozen=True)
class Piece:
    """One parked vehicle: which strip it comes from, and its frame in it."""

    doodad_id: str
    raw: str
    frame_index: int


# Order fixes the atlas layout, so it is also the order the tileset definition's
# col/row pairs are written against. Appending is safe; reordering is not.
PIECES = (
    Piece("doodad.parked-utility-truck", "parked-vehicles-1.raw.png", 0),
    Piece("doodad.parked-flatbed-truck", "parked-vehicles-1.raw.png", 1),
    Piece("doodad.parked-tanker-truck", "parked-vehicles-1.raw.png", 2),
    Piece("doodad.parked-box-van", "parked-vehicles-1.raw.png", 3),
    Piece("doodad.parked-cargo-truck", "parked-vehicles-2.raw.png", 0),
    Piece("doodad.parked-armored-apc", "parked-vehicles-2.raw.png", 1),
)


def slice_strip(image: Image.Image) -> list[tuple[int, int, int, int]]:
    """Tight bounding boxes of each sprite on a single-row strip.

    Columns holding any pixel at or above the alpha threshold are content;
    runs of content columns are frames, and a transparent run shorter than
    MIN_GAP is within-sprite negative space rather than a frame boundary.
    """
    width, height = image.size
    pixels = image.load()
    has_content = [
        any(pixels[x, y][3] >= ALPHA_THRESHOLD for y in range(height))
        for x in range(width)
    ]

    spans: list[tuple[int, int]] = []
    start: int | None = None
    gap = 0
    for x in range(width):
        if has_content[x]:
            if start is None:
                start = x
            gap = 0
            continue
        if start is None:
            continue
        gap += 1
        if gap >= MIN_GAP:
            spans.append((start, x - gap))
            start = None
            gap = 0
    if start is not None:
        spans.append((start, width - 1))

    boxes = []
    for left, right in spans:
        rows = [
            y for y in range(height)
            if any(pixels[x, y][3] >= ALPHA_THRESHOLD for x in range(left, right + 1))
        ]
        boxes.append((left, rows[0], right - left + 1, rows[-1] - rows[0] + 1))
    return boxes


def fit_into_slot(sprite: Image.Image) -> Image.Image:
    """Scale to fit the slot preserving aspect, centered on transparency."""
    src_w, src_h = sprite.size
    scale = min(SLOT_W / src_w, SLOT_H / src_h)
    draw_w = max(1, round(src_w * scale))
    draw_h = max(1, round(src_h * scale))
    slot = Image.new("RGBA", (SLOT_W, SLOT_H), (0, 0, 0, 0))
    slot.paste(
        sprite.resize((draw_w, draw_h), Image.LANCZOS),
        ((SLOT_W - draw_w) // 2, (SLOT_H - draw_h) // 2),
    )
    return slot


def main() -> None:
    strips: dict[str, tuple[Image.Image, list[tuple[int, int, int, int]]]] = {}
    for raw in sorted({piece.raw for piece in PIECES}):
        image = Image.open(HERE / raw).convert("RGBA")
        strips[raw] = (image, slice_strip(image))

    rows = (len(PIECES) + SLOT_COLUMNS - 1) // SLOT_COLUMNS
    atlas = Image.new("RGBA", (SLOT_COLUMNS * SLOT_W, rows * SLOT_H), (0, 0, 0, 0))

    for index, piece in enumerate(PIECES):
        image, boxes = strips[piece.raw]
        if piece.frame_index >= len(boxes):
            raise SystemExit(
                f"{piece.raw} sliced into {len(boxes)} frames; "
                f"{piece.doodad_id} wants frame {piece.frame_index}"
            )
        x, y, w, h = boxes[piece.frame_index]
        slot_col = index % SLOT_COLUMNS
        slot_row = index // SLOT_COLUMNS
        atlas.paste(
            fit_into_slot(image.crop((x, y, x + w, y + h))),
            (slot_col * SLOT_W, slot_row * SLOT_H),
        )
        print(
            f"{piece.doodad_id}: {piece.raw}[{piece.frame_index}] {w}x{h} "
            f"-> col {slot_col * FOOTPRINT_CELLS_X} row {slot_row * FOOTPRINT_CELLS_Y}"
        )

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    atlas.save(OUTPUT)
    print(f"wrote {OUTPUT} ({atlas.width}x{atlas.height}, cellPx {CELL_PX})")


if __name__ == "__main__":
    main()
