"""Guide for the marked apron (GroundKind.STRIPED): concrete hardstanding with painted markings.

Two 16-frame blocks on a 6x6 sheet (3072x3072, square because that is a canvas ImageGen returns), one world
cell = 480 px inside a 512 px pitch with 16 px dark gutters. Frames run row-major: 0-15 are `apron.marked`,
16-31 `apron.keep-clear`, 32-35 spare open apron. Within a block, slot = exposed-edge mask, N=1 E=2 S=4 W=8
(the `edge-faces-4x4` layout): slot 0 is the open field, slot 15 a lone cell marked on every side. North is the
top of a frame.

- `apron.marked`: plain concrete; an exposed side carries one painted edge line from LINE_START to
  LINE_END of the cell, so a run of apron is outlined where it meets a wall or other ground.
- `apron.keep-clear`: the same concrete and edge line, and a diagonal yellow hatch over the rest of the
  cell, held HATCH_GAP inside any line. The no-stand margin in front of a dock door.

Hatch stripes are a function of the cell-local coordinate with a whole number of periods per cell, so a hatched
zone runs on unbroken across cells and every edge line meets its neighbour's at the seam.

Usage: python art-source/templates/marked-apron-guide.py <out.png>
"""
import sys

import numpy as np
from PIL import Image

PITCH, CELL, COLS = 512, 480, 6
MARGIN = (PITCH - CELL) // 2
LINE_START, LINE_END = 0.06, 0.12
HATCH_GAP = 0.05
STRIPE_PERIOD = 0.25          # cell fraction along u+v; 4 periods per cell keeps the hatch continuous
STRIPE_WIDTH = 0.36           # painted share of each period
CONCRETE = (128, 124, 116)
PAINT = (214, 170, 44)
GUTTER = (12, 12, 14)


def frame(mask, hatched):
    v, u = np.mgrid[0:CELL, 0:CELL].astype(np.float64)
    u = (u + 0.5) / CELL
    v = (v + 0.5) / CELL
    depths = [np.full_like(u, 9.0)]
    if mask & 1: depths.append(v)          # N: distance from the top edge
    if mask & 2: depths.append(1 - u)      # E
    if mask & 4: depths.append(1 - v)      # S
    if mask & 8: depths.append(u)          # W
    d = np.min(np.stack(depths), axis=0)
    out = np.empty((CELL, CELL, 3), np.uint8)
    out[:] = CONCRETE
    out[(d >= LINE_START) & (d < LINE_END)] = PAINT
    if hatched:
        phase = ((u + v) / STRIPE_PERIOD) % 1.0
        out[(phase < STRIPE_WIDTH) & (d >= LINE_END + HATCH_GAP)] = PAINT
    return out


def main(out):
    sheet = np.empty((PITCH * COLS, PITCH * COLS, 3), np.uint8)
    sheet[:] = GUTTER
    for i in range(COLS * COLS):
        hatched, mask = i // 16 == 1, i % 16 if i < 32 else 0
        x, y = i % COLS * PITCH + MARGIN, i // COLS * PITCH + MARGIN
        sheet[y:y + CELL, x:x + CELL] = frame(mask, hatched)
    Image.fromarray(sheet, "RGB").save(out)


if __name__ == "__main__":
    main(sys.argv[1])
