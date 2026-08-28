"""Measure a raw tileset sheet and draft the seed that annotates it.

This is the mechanical half of ingesting a new sheet. It answers only what can
be read off the pixels — how big the sheet is, whether it carries a usable alpha
channel, and how many pieces alpha-keying finds at each threshold — and it never
invents the other half. What a piece is for, which blocks a sheet contains, and
what is worth warning the next reader about are judgements about the art, and a
tool that guesses at them produces confident nonsense.

**It does not detect the cell grid.** Seam-energy and autocorrelation were both
tried against sheets whose grids were known from their shipped atlases, and both
read noise: for a 10x10 sheet the strongest seam candidate was 2 cells, and for
two 25-cell sheets neither method found 25 at all. Generated tile art blends
across its own boundaries, so the grid is something the generator knows and the
pixels do not say. State it with ``--cells`` — which is free when the sheet was
generated to a layout you asked for — and this will verify it rather than guess.
Cells need not be square: a 20-frame strip is ``--cells 20x1``, and the split
divides the plate into exactly that grid.

Usage:

    python art-source/tilesets/measure_sheet.py <sheet.raw.png> [--cells 10x10] [--write]
"""

from __future__ import annotations

import argparse
import json
import sys
from dataclasses import dataclass
from pathlib import Path

import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
REPO_ROOT = HERE.parent.parent

# Matches SheetSlicer.DEFAULT_ALPHA_MIN: above a soft key's background residue,
# below the anti-aliased edge of real art.
DEFAULT_ALPHA_MIN = 40
# Matches SheetSlicer.DEFAULT_MIN_AREA.
DEFAULT_MIN_AREA = 200
# Thresholds swept so the operator can see where a sheet stops fusing.
SWEEP = (16, 40, 96, 160)


@dataclass
class Measurement:
    path: Path
    width: int
    height: int
    has_alpha: bool
    transparent_fraction: float
    pieces: dict[int, int]


def _label_pieces(mask: np.ndarray) -> int:
    """Count 8-connected components of at least DEFAULT_MIN_AREA pixels.

    Two-pass union-find over rows rather than a flood fill: a fused sheet is one
    component covering a million pixels, and a per-pixel Python stack over that
    takes long enough to discourage running this at all.
    """
    height, width = mask.shape
    parent: list[int] = [0]
    labels = np.zeros((height, width), dtype=np.int32)

    def find(a: int) -> int:
        while parent[a] != a:
            parent[a] = parent[parent[a]]
            a = parent[a]
        return a

    def union(a: int, b: int) -> None:
        ra, rb = find(a), find(b)
        if ra != rb:
            parent[max(ra, rb)] = min(ra, rb)

    for y in range(height):
        row = mask[y]
        if not row.any():
            continue
        above = labels[y - 1] if y > 0 else None
        left = 0
        for x in np.flatnonzero(row):
            neighbours = []
            if left and mask[y, x - 1]:
                neighbours.append(left)
            if above is not None:
                for nx in range(max(0, x - 1), min(width, x + 2)):
                    if above[nx]:
                        neighbours.append(int(above[nx]))
            if neighbours:
                current = min(neighbours)
                for other in neighbours:
                    union(current, other)
            else:
                parent.append(len(parent))
                current = len(parent) - 1
            labels[y, x] = current
            left = current

    flat = labels.ravel()
    roots = np.zeros(len(parent), dtype=np.int32)
    for i in range(1, len(parent)):
        roots[i] = find(i)
    resolved = roots[flat]
    counts = np.bincount(resolved)
    counts[0] = 0
    return int((counts >= DEFAULT_MIN_AREA).sum())


def measure(path: Path) -> Measurement:
    rgba = Image.open(path).convert("RGBA")
    alpha = np.asarray(rgba)[:, :, 3]
    transparent_fraction = float((alpha < 255).mean())
    # RGBA does not mean keyed: a fully opaque RGBA sheet slices like an RGB one.
    has_alpha = transparent_fraction > 0.001
    pieces = {t: _label_pieces(alpha >= t) for t in SWEEP} if has_alpha else {}
    return Measurement(path, rgba.width, rgba.height, has_alpha, transparent_fraction, pieces)


@dataclass
class Grid:
    cells_across: int
    cells_down: int
    cell_x: float
    cell_y: float

    @property
    def square(self) -> bool:
        # Within a pixel: the split takes one cell size and rounds the counts.
        return abs(self.cell_x - self.cell_y) <= 1.0

    @property
    def grid_cell(self) -> int:
        return int(round((self.cell_x + self.cell_y) / 2))

    def splits_to(self, width: int, height: int) -> tuple[int, int]:
        cell = self.grid_cell
        return round(width / cell), round(height / cell)


def stated_grid(measurement: Measurement, cells: tuple[int, int]) -> Grid:
    return Grid(cells[0], cells[1],
                measurement.width / cells[0], measurement.height / cells[1])


def report(measurement: Measurement, grid: Grid | None) -> str:
    lines = [f"{measurement.path.name}: {measurement.width}x{measurement.height}"]
    if measurement.has_alpha:
        lines.append(f"  alpha channel: yes "
                     f"({measurement.transparent_fraction * 100:.1f}% of pixels not fully opaque)")
        sweep = "; ".join(f"{t}: {n}" for t, n in measurement.pieces.items())
        lines.append(f"  pieces by alpha threshold - {sweep}")
        lines.append("  -> a cut-out sheet. Pick the threshold where the count stops")
        lines.append("     changing; one piece means the key is too soft to separate them.")
    else:
        lines.append("  alpha channel: NO (every pixel fully opaque)")
        lines.append("  -> a fused plate. Slicing finds one piece covering the whole sheet,")
        lines.append("     which is correct: select it and split it on the grid.")

    if grid is None:
        if not measurement.has_alpha:
            lines.append("  grid: NOT STATED. This sheet cannot be cut without one, and it")
            lines.append("     cannot be read off the pixels - pass --cells COLSxROWS.")
        return "\n".join(lines)

    lines.append(f"  grid: {grid.cells_across}x{grid.cells_down} stated "
                 f"-> {grid.cell_x:.1f} x {grid.cell_y:.1f} px per cell")
    if not grid.square:
        lines.append("     Cells are not square, which is ordinary for generated art. The")
        lines.append("     split divides the plate into exactly the stated grid, so this is")
        lines.append("     cut the same as any other plate.")
    return "\n".join(lines)


def draft(measurement: Measurement, grid: Grid | None) -> dict:
    name = measurement.path.name
    for suffix in (".raw.png", ".png"):
        if name.endswith(suffix):
            name = name[: -len(suffix)]
            break
    sheet = measurement.path.resolve().relative_to(REPO_ROOT).as_posix()

    if measurement.has_alpha:
        best = max(measurement.pieces.items(), key=lambda kv: kv[1])
        alpha_min = best[0] if best[1] > 1 else DEFAULT_ALPHA_MIN
        measured = (f"{measurement.width}x{measurement.height}, alpha-keyed; "
                    f"slicing finds {best[1]} pieces at threshold {alpha_min}.")
    else:
        alpha_min = DEFAULT_ALPHA_MIN
        measured = (f"{measurement.width}x{measurement.height}, no alpha channel, so slicing "
                    "finds one fused piece covering the whole sheet.")
        if grid is not None:
            measured += (f" Select it and split on the {grid.cells_across}x{grid.cells_down} "
                         f"grid, whose cells are {grid.cell_x:.0f}x{grid.cell_y:.0f} px.")

    return {
        "sheet": sheet,
        "sheetName": name,
        "idPrefix": f"doodad.{name}",
        "cellPx": 32,
        "alphaMin": alpha_min,
        "gridCols": grid.cells_across if grid is not None else 1,
        "gridRows": grid.cells_down if grid is not None else 1,
        "note": f"TODO - say what this sheet is for and what the next reader would "
                f"otherwise learn by failing. Measured: {measured}",
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("sheet", type=Path, help="the raw sheet to measure")
    parser.add_argument("--cells", help="the grid as COLSxROWS; verified, never guessed")
    parser.add_argument("--write", action="store_true",
                        help="write the draft seed beside the sheet if none exists")
    parser.add_argument("--force", action="store_true",
                        help="with --write, replace an existing seed")
    options = parser.parse_args()

    if not options.sheet.is_file():
        raise SystemExit(f"no such sheet: {options.sheet}")
    cells = None
    if options.cells:
        try:
            across, down = options.cells.lower().split("x")
            cells = (int(across), int(down))
            if cells[0] < 1 or cells[1] < 1:
                raise ValueError
        except ValueError:
            raise SystemExit(f"--cells wants COLSxROWS, for example 10x10; got {options.cells!r}")

    measurement = measure(options.sheet)
    grid = stated_grid(measurement, cells) if cells else None
    print(report(measurement, grid))
    seed = draft(measurement, grid)
    print()
    print(json.dumps(seed, indent=2, ensure_ascii=False))

    if not options.write:
        return
    target = options.sheet.parent / f"{seed['sheetName']}.tileset-authoring.json"
    if target.exists() and not options.force:
        print(f"\n{target.name} already exists; not replacing it. Use --force to overwrite.",
              file=sys.stderr)
        return
    with target.open("w", encoding="utf-8", newline="\n") as f:
        json.dump(seed, f, indent=2, ensure_ascii=False)
        f.write("\n")
    print(f"\nwrote {target}")
    print("The note is a placeholder - it is the half this cannot measure.")


if __name__ == "__main__":
    main()
