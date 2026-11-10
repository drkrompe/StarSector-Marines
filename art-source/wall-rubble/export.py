"""Exports the shipped wall rubble sheet from the accepted master.

master.png (a 4 x 4 grid, see make_guide.py) -> mod/graphics/decals/wall-rubble.png, a 4 x 4 grid of 128 px cells,
each master cell resampled whole, so a piece keeps its place in its cell (WallRubble places a heap by its cell's top
edge, where its wall stood). The scatter row's dust patch is thinned to a faint film (thin_dust): on the ground it is dust, not
a pale disc. Prints each heap's top edge, as a share of its cell, for WallRubble.HEAP_TOP.

    python art-source/wall-rubble/export.py
"""
from pathlib import Path

import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
OUT = HERE.parent.parent / "mod" / "graphics" / "decals" / "wall-rubble.png"
CELL = 128
GRID = 4
DUST_ALPHA = 0.12


def thin_dust(cell):
    """Fades the smooth dust patch under the scatter to DUST_ALPHA, keeping the fragments: a fragment's pixels are
    shaded light and dark within a few pixels, the dust's are not."""
    rgba = np.asarray(cell, dtype=np.float32)
    lum = rgba[..., :3].mean(axis=2)
    k = 3
    pad = np.pad(lum, k, mode="edge")
    windows = np.stack([pad[dy:dy + lum.shape[0], dx:dx + lum.shape[1]]
                        for dy in range(2 * k + 1) for dx in range(2 * k + 1)])
    rough = windows.std(axis=0) > 9
    # Grow the fragments a little so their soft edges stay whole.
    grown = np.pad(rough, 2)
    rough = np.any(np.stack([grown[dy:dy + lum.shape[0], dx:dx + lum.shape[1]]
                             for dy in range(5) for dx in range(5)]), axis=0)
    rgba[..., 3] = np.where(rough, rgba[..., 3], rgba[..., 3] * DUST_ALPHA)
    return Image.fromarray(rgba.clip(0, 255).astype(np.uint8), "RGBA")


def main():
    master = Image.open(HERE / "master.png").convert("RGBA")
    w, h = master.size
    sheet = Image.new("RGBA", (CELL * GRID, CELL * GRID), (0, 0, 0, 0))
    for row in range(GRID):
        for col in range(GRID):
            box = (round(col * w / GRID), round(row * h / GRID), round((col + 1) * w / GRID),
                   round((row + 1) * h / GRID))
            if row == 3:
                cell = thin_dust(master.crop(box)).resize((CELL, CELL), Image.LANCZOS)
            else:
                cell = master.crop(box).resize((CELL, CELL), Image.LANCZOS)
            if row == 0:
                alpha = cell.getchannel("A")
                top = next((y for y in range(CELL) if alpha.crop((0, y, CELL, y + 1)).getextrema()[1] > 128), 0)
                print(f"heap {col}: top edge at {top / CELL:.3f} of its cell")
            sheet.alpha_composite(cell, (col * CELL, row * CELL))
    OUT.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(OUT)
    print(f"wrote {OUT} {sheet.size}")


if __name__ == "__main__":
    main()
