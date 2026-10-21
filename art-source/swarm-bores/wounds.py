"""Where each wounded painting bleeds: the wound anchors of every grown structure's ``bleed`` block.

A wound is raw flesh the wounded guide shows that the intact one does not (a torn-out plate's socket, a gash through
the carapace, a burst sac's crown), so the anchors are read out of the same seeded geometry the painting was
reskinned from, and the ooze sits on the wound the painting shows. Wounds are found as connected patches of that new
flesh, largest first, one anchor per patch on the patch itself, patches closer than ``MIN_APART`` to a larger one
dropped, at most ``MAX_WOUNDS`` per painting. The largest opens first (``StructureArt.Bleed``).

An anchor is ``[u, v, degrees]``: the offset from the structure's centre as a share of its layer's edge, ``v`` down
the image, which is what the runtime needs whatever the layer's size, and the way the ooze runs from it, downhill on
the body's own heightfield (into the pit from a bore's lip, off the flank of a colony), in degrees of image space. The rest of the block (the sheet, its layout and the
ooze's size per part) is set here too, and the whole ``bleed`` value is written into each grown structure's ``art``
block in ``turret-emplacements.turret.json``.

Run from the repository root after changing ``make_guide.py``: ``python art-source/swarm-bores/wounds.py``.
"""

import json
import math
import sys
from collections import deque
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
import export  # noqa: E402
import make_guide  # noqa: E402
import ooze  # noqa: E402

CATALOG = "mod/data/marines/turret-emplacements.turret.json"
SHEET = "graphics/structures/swarm-ooze.png"
FPS = 10.0
GRID = 128
MAX_WOUNDS = {"bore": 5, "mother-bore": 6, "spore-colony-fissure": 3, "spore-colony-bore": 4,
              "spore-colony-maw": 4}
# The ooze's frame edge on the map, in cells: the pool in it is a little over a quarter of that across.
OOZE_CELLS = {"bore": 1.0, "mother-bore": 1.1, "spore-colony-fissure": 0.7, "spore-colony-bore": 0.8,
              "spore-colony-maw": 0.85}
MIN_APART = 0.09
MIN_AREA = 3
# Grid cells the heightfield is blurred over before its slope picks a run's direction.
RUN_BLUR = 3.0


def patches(mask: np.ndarray):
    """Connected patches of a boolean grid, as lists of (row, column), largest first."""
    seen = np.zeros_like(mask)
    found = []
    rows, cols = mask.shape
    for r0, c0 in zip(*np.nonzero(mask)):
        if seen[r0, c0]:
            continue
        seen[r0, c0] = True
        queue, cells = deque([(r0, c0)]), []
        while queue:
            r, c = queue.popleft()
            cells.append((r, c))
            for dr, dc in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                rr, cc = r + dr, c + dc
                if 0 <= rr < rows and 0 <= cc < cols and mask[rr, cc] and not seen[rr, cc]:
                    seen[rr, cc] = True
                    queue.append((rr, cc))
        found.append(cells)
    found.sort(key=len, reverse=True)
    return found


def anchors(part: str, variant: int):
    intact = make_guide.build(part, variant, "intact")
    wounded = make_guide.build(part, variant, "wounded")
    fresh = (wounded.m == make_guide.FLESH) & (intact.m != make_guide.FLESH)
    k = make_guide.N // GRID
    grid = fresh.reshape(GRID, k, GRID, k).mean(axis=(1, 3)) > 0.35
    # The body's lie at the scale of a run: the heightfield blurred over a few grid cells, then its slope.
    slope = np.gradient(blur(wounded.h.reshape(GRID, k, GRID, k).mean(axis=(1, 3)), RUN_BLUR))
    chosen = []
    for cells in patches(grid):
        if len(cells) < MIN_AREA or len(chosen) == MAX_WOUNDS[part]:
            break
        rs = np.array([r for r, _ in cells], np.float32)
        cs = np.array([c for _, c in cells], np.float32)
        mr, mc = rs.mean(), cs.mean()
        # On the wound itself, not wherever a crescent's centroid falls.
        i = int(np.argmin((rs - mr) ** 2 + (cs - mc) ** 2))
        u = (cs[i] + 0.5) / GRID - 0.5
        v = (rs[i] + 0.5) / GRID - 0.5
        if any(math.hypot(u - a, v - b) < MIN_APART for a, b, _ in chosen):
            continue
        chosen.append((round(float(u), 3), round(float(v), 3), downhill(slope, rs[i], cs[i])))
    return [list(a) for a in chosen]


def blur(grid: np.ndarray, sigma: float) -> np.ndarray:
    """A separable Gaussian blur, edges held."""
    radius = int(3 * sigma)
    kernel = np.exp(-0.5 * (np.arange(-radius, radius + 1) / sigma) ** 2)
    kernel /= kernel.sum()
    padded = np.pad(grid, radius, mode="edge")
    rows = np.apply_along_axis(lambda line: np.convolve(line, kernel, "valid"), 1, padded)
    return np.apply_along_axis(lambda line: np.convolve(line, kernel, "valid"), 0, rows)


def downhill(slope, row: float, col: float) -> int:
    """Which way the ooze runs from a grid cell: down the body's smoothed slope, in whole degrees of image space."""
    gy, gx = slope
    r, c = int(row), int(col)
    return round(math.degrees(math.atan2(-gy[r, c], -gx[r, c]))) % 360


def bleed_block(part: str) -> dict:
    return {
        "sheet": SHEET, "frames": ooze.FRAMES, "kinds": ooze.KINDS, "directions": ooze.DIRECTIONS, "fps": FPS,
        "cells": OOZE_CELLS[part],
        "wounds": [anchors(part, v) for v in range(1, make_guide.VARIANTS[part] + 1)],
    }


def value_end(text: str, start: int) -> int:
    """Index just past the JSON object that opens at ``start``."""
    depth = 0
    for i in range(start, len(text)):
        if text[i] == "{":
            depth += 1
        elif text[i] == "}":
            depth -= 1
            if depth == 0:
                return i + 1
    raise ValueError("unbalanced object")


def write(catalog: Path, part: str, block: dict) -> None:
    text = catalog.read_text(encoding="utf-8")
    key = f'"remains": "graphics/structures/{export.NAMES[part]}-{{variant}}-remains.png"'
    at = text.index(key)
    line_start = text.rindex("\n", 0, at) + 1
    art = text.index('"art": {', line_start)
    value = json.dumps(block, separators=(", ", ": "))
    old = text.find('"bleed": ', art, at)
    if old >= 0:
        end = value_end(text, old + len('"bleed": '))
        text = text[:old] + '"bleed": ' + value + text[end:]
    else:
        text = text[:at] + '"bleed": ' + value + ", " + text[at:]
    catalog.write_text(text, encoding="utf-8", newline="\n")


def main() -> None:
    root = Path(__file__).resolve().parents[2]
    catalog = root / CATALOG
    for part in make_guide.PARTS:
        block = bleed_block(part)
        write(catalog, part, block)
        print(part, [len(w) for w in block["wounds"]], flush=True)


if __name__ == "__main__":
    main()
