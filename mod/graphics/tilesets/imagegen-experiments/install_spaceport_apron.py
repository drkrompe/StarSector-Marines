"""Extract an ImageGen 3x3 presentation grid into the runtime road atlas.

The model rendered each 32px logical tile as a separate framed panel. This
script removes the black gutters, downsamples panels independently, restores
the existing atlas alpha topology, and writes a versioned road sheet so the
candidate can be previewed without overwriting the currently-shipped art.
"""

from __future__ import annotations

from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter


HERE = Path(__file__).resolve().parent
TILESETS = HERE.parent
GENERATED = HERE / "urban-tileset-2-spaceport-apron.raw.png"
SOURCE = TILESETS / "urban-tileset-2-imagegen.png"
OUTPUT = TILESETS / "urban-tileset-2-spaceport-apron.png"

CELL_PX = 32
ATLAS_COL = 6
PANEL_X = ((9, 416), (423, 832), (838, 1245))
PANEL_Y = ((9, 418), (424, 835), (841, 1246))


def main() -> None:
    generated = Image.open(GENERATED).convert("RGB")
    source = Image.open(SOURCE).convert("RGBA")
    output = np.asarray(source).copy()
    source_alpha = np.asarray(source)[:, :, 3]

    for row, (y0, y1) in enumerate(PANEL_Y):
        for col, (x0, x1) in enumerate(PANEL_X):
            panel = generated.crop((x0, y0, x1, y1)).resize(
                (CELL_PX, CELL_PX), Image.Resampling.LANCZOS
            )
            panel = panel.filter(ImageFilter.UnsharpMask(radius=0.55, percent=80, threshold=2))
            pixels = np.asarray(panel)
            ax0 = (ATLAS_COL + col) * CELL_PX
            ay0 = row * CELL_PX
            output[ay0:ay0 + CELL_PX, ax0:ax0 + CELL_PX, :3] = pixels
            output[ay0:ay0 + CELL_PX, ax0:ax0 + CELL_PX, 3] = source_alpha[
                ay0:ay0 + CELL_PX, ax0:ax0 + CELL_PX
            ]

    Image.fromarray(output, "RGBA").save(OUTPUT)
    print(OUTPUT)


if __name__ == "__main__":
    main()
