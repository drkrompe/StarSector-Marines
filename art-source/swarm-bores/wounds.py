"""Where each wounded painting bleeds: the wound anchors of every grown structure's ``bleed`` block.

A wound is raw flesh the wounded guide shows that the intact one does not (a torn-out plate's socket, a gash through
the carapace, a burst sac's crown), so the anchors are read out of the same seeded geometry the painting was
reskinned from, and the ooze sits on the wound the painting shows. Wounds are found as connected patches of that new
flesh, largest first, one anchor per patch on the patch itself, patches closer than ``MIN_APART`` to a larger one
dropped, at most ``MAX_WOUNDS`` per painting. The largest opens first (``StructureArt.Bleed``).

An anchor is ``[u, v, degrees, family]``: the offset from the structure's centre as a share of its layer's edge,
``v`` down the image, which is what the runtime needs whatever the layer's size; the way the ichor runs from it,
downhill on the body's own heightfield (into the pit from a bore's lip, off the flank of a colony), in degrees of
image space; and which family of wound it is. A round wound is a **puncture** (family 0) and pulses; a long one (a
gash, a torn plate's socket: a patch at least ``FISSURE_ELONGATION`` times as long as it is wide) is a **fissure**
(family 1) and sprays, its slit laid along the patch's length and its spray thrown across it on the downhill side.
Every painting gets at least one fissure: its most elongated wound when none is long enough. The rest of the block (the sheet, its layout and the
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
SPRAY_SHEET = "graphics/structures/swarm-spray.png"
PUNCTURE, FISSURE = 0, 1
FISSURE_ELONGATION = 2.0
FPS = ooze.FPS
GRID = 128
MAX_WOUNDS = {"bore": 5, "mother-bore": 6, "spore-colony-fissure": 3, "spore-colony-bore": 4,
              "spore-colony-maw": 4, "ground-colony": 4, "brood-spire": 5}
# The ooze's frame edge on the map, in cells: the pool in it is a little over a quarter of that across.
OOZE_CELLS = {"bore": 1.0, "mother-bore": 1.1, "spore-colony-fissure": 0.7, "spore-colony-bore": 0.8,
              "spore-colony-maw": 0.85, "ground-colony": 0.8, "brood-spire": 1.0}
# The spray's frame edge on the map, in cells: the gash is a fifth of it long, the spray lands up to four tenths out.
SPRAY_CELLS = {"bore": 2.4, "mother-bore": 2.6, "spore-colony-fissure": 1.4, "spore-colony-bore": 1.6,
               "spore-colony-maw": 1.7, "ground-colony": 1.6, "brood-spire": 2.4}
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
        if any(math.hypot(u - a[0], v - a[1]) < MIN_APART for a in chosen):
            continue
        run = downhill(slope, rs[i], cs[i])
        stretch, across = shape(rs, cs, run)
        chosen.append([round(float(u), 3), round(float(v), 3), run, PUNCTURE, stretch, across])
    if chosen and not any(a[4] >= FISSURE_ELONGATION for a in chosen):
        max(chosen, key=lambda a: a[4])[4] = FISSURE_ELONGATION
    out = []
    for u, v, run, _, stretch, across in chosen:
        if stretch >= FISSURE_ELONGATION:
            out.append([u, v, across, FISSURE])
        else:
            out.append([u, v, run, PUNCTURE])
    return out


def shape(rs, cs, run: int):
    """How elongated a wound patch is, and the way across it on its downhill side (degrees, image space)."""
    if len(rs) < 3:
        return 1.0, run
    cov = np.cov(np.vstack([cs, rs]))
    values, vectors = np.linalg.eigh(cov)
    stretch = math.sqrt(max(values[1], 1e-6) / max(values[0], 1e-6))
    # The minor axis is across the gash; of its two ends, the one nearer downhill.
    nx, ny = vectors[0, 0], vectors[1, 0]
    rx, ry = math.cos(math.radians(run)), math.sin(math.radians(run))
    if nx * rx + ny * ry < 0:
        nx, ny = -nx, -ny
    return stretch, round(math.degrees(math.atan2(ny, nx))) % 360


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
        "families": [
            {"sheet": SHEET, "frames": ooze.FRAMES, "kinds": ooze.KINDS, "directions": ooze.DIRECTIONS, "fps": FPS,
             "cells": OOZE_CELLS[part]},
            {"sheet": SPRAY_SHEET, "frames": ooze.FRAMES, "kinds": ooze.SPRAY_KINDS, "directions": ooze.DIRECTIONS,
             "fps": FPS, "cells": SPRAY_CELLS[part]},
        ],
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
        print(part, [f"{sum(1 for a in w if a[3] == FISSURE)}/{len(w)} fissures" for w in block["wounds"]], flush=True)


if __name__ == "__main__":
    main()
