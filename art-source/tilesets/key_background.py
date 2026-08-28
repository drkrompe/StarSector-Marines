"""Give an opaque plate its alpha, one cell of the plate at a time.

An ImageGen sheet arrives RGB and fully opaque, so what is background and what
is art has not been recorded anywhere. Until it is, the shipped atlas is the
only thing that knows, which makes the atlas an input its own source cannot
replace. Keying is therefore an *edit to the raw art*: this module produces the
alpha channel, the keyed PNG is checked in, and nothing at runtime or export
time runs it again.

It cannot be a global threshold. A sheet's own outlines sit inside the range
its background occupies, and a dark floor tile is background-dark by
construction, so the best single threshold either leaves background opaque or
punches holes in art. What counts as background is a fact about a *cell*: the
same dark gutter is background beside a crate and is the outer edge of the
floor tile next to it. Every rule below answers a failure seen on a real sheet:

* a cell's background is near-black, holds a pocket wider than the gutter, and
  is either reached from the cell's border or large enough to be the cell's own
  emptiness (a hollow wall block) - without the last clause a floor tile keys
  to a grid of holes and a bookshelf's interior keys as if it were outside;
* the flood travels only through *very* near-black and is grown a few pixels
  into merely-dark afterwards, so a piece keeps its own outline;
* a thick black pocket within a few pixels of the background joins it, because
  a chair's leg gap is sealed at the bottom by a three-pixel contact shadow and
  a flood that only travels through near-black can never enter it;
* an opaque island detached from the piece's body, and smaller or thinner than
  drawn art, is dust the key admitted or a sliver of the neighbouring cell the
  cut left behind - at 4:1 or 6:1 each one survives the downscale as a speck
  floating beside the prop.

The cells are supplied by the caller: a plate cut on its stated grid hands in
that grid's rectangles, and a strip of props hands in one band per piece. What
the module never does is decide which cells a sheet has.

Written for `urban-tileset.raw.png` (a 10x10 plate) and generalised to serve
`urban-tileset-3.raw.png` (a 7x1 strip of variable-width props). See
`moddable-tilesets-nouns.md` for the alpha law this implements.
"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

import numpy as np
from PIL import Image


@dataclass(frozen=True)
class KeyParams:
    """Tuning for one sheet. Every default is the value urban-tileset shipped with."""

    #: A pixel at or below this brightness may be background at all.
    tol_key: int = 28
    #: The flood itself travels only through this much darker pixels.
    tol_flow: int = 12
    #: A background pocket must survive this much erosion - it is wider than a gutter.
    radius: int = 7
    #: How far the keyed background is grown back into merely-dark pixels.
    reach: int = 4
    #: A near-black region this fraction of the cell is the cell's own emptiness.
    enclosed_min: float = 0.25
    #: A thick pocket this close to the background joins it (the chair's leg gap).
    bridge: int = 4
    #: Opaque islands smaller than this, detached from the body, are dust.
    min_island: int = 300
    #: Transparent pinholes smaller than this are filled back in.
    min_hole: int = 60
    #: An island thinner than this, detached from the body, is dust however large.
    thin: int = 3
    #: The cell's art is the filled rectangle it is drawn on.
    #:
    #: A repeating ground field is solid where it is drawn, and a hole in one is
    #: a hole in the road. Its sprite is a slab with a lit rim and a shadowed
    #: skirt, and the key reads the ragged outermost pixels of those as
    #: background - which is true of a *prop's* border and false of a surface
    #: the map paves with, where every carved pixel is a puncture the tiling
    #: repeats. What is background is a fact about the cell, so this is a
    #: setting on the cell rather than a threshold that could ever find it.
    solid: bool = False


def value_plane(image: Image.Image) -> np.ndarray:
    """The brightness the key reads: the maximum channel, ignoring any alpha."""
    return np.asarray(image.convert("RGB")).max(axis=2)


def erode(mask: np.ndarray, radius: int, outside_is_mask: bool = False) -> np.ndarray:
    """Diamond erosion.

    ``outside_is_mask`` says whether beyond the array counts as set. It is false
    for a background pocket, so a gutter running along the cell's own border is
    correctly too thin to seed.
    """
    out = mask
    for _ in range(radius):
        if outside_is_mask:
            e = out.copy()
            e[1:, :] &= out[:-1, :]
            e[:-1, :] &= out[1:, :]
            e[:, 1:] &= out[:, :-1]
            e[:, :-1] &= out[:, 1:]
        else:
            e = np.zeros_like(out)
            e[1:-1, 1:-1] = (out[1:-1, 1:-1] & out[:-2, 1:-1] & out[2:, 1:-1]
                             & out[1:-1, :-2] & out[1:-1, 2:])
        out = e
    return out


def dilate(mask: np.ndarray, radius: int, within: np.ndarray) -> np.ndarray:
    """Grow ``mask`` by ``radius``, never outside ``within``."""
    out = mask.copy()
    for _ in range(radius):
        e = out.copy()
        e[1:, :] |= out[:-1, :]
        e[:-1, :] |= out[1:, :]
        e[:, 1:] |= out[:, :-1]
        e[:, :-1] |= out[:, 1:]
        out = e & within
    return out


def label(mask: np.ndarray) -> tuple[np.ndarray, int]:
    """8-connected component labels, -1 outside the mask."""
    h, w = mask.shape
    comp = -np.ones((h, w), np.int32)
    n = 0
    for sy in range(h):
        row = mask[sy]
        for sx in range(w):
            if not row[sx] or comp[sy, sx] >= 0:
                continue
            stack = [(sy, sx)]
            comp[sy, sx] = n
            while stack:
                y, x = stack.pop()
                for dy in (-1, 0, 1):
                    for dx in (-1, 0, 1):
                        ny, nx = y + dy, x + dx
                        if 0 <= ny < h and 0 <= nx < w and mask[ny, nx] and comp[ny, nx] < 0:
                            comp[ny, nx] = n
                            stack.append((ny, nx))
            n += 1
    return comp, n


def despeckle(art: np.ndarray, min_island: int, min_hole: int, thin: int) -> np.ndarray:
    """Drop opaque islands and transparent pinholes that are not art.

    A piece is one body. Anything detached from it is either a speck of
    background the key admitted or a sliver of the neighbouring cell the cut
    left behind, and both are recognisable the same way the background is: by
    being smaller or thinner than anything drawn. The main body is never
    dropped, however thin it is.
    """
    comp, n = label(art)
    if n:
        sizes = np.bincount(comp[comp >= 0].ravel(), minlength=n)
        biggest = int(np.argmax(sizes))
        drop = [c for c in range(n)
                if c != biggest and (sizes[c] < min_island or not erode(comp == c, thin).any())]
        if drop:
            art = art & ~np.isin(comp, drop)
    comp, n = label(~art)
    if n:
        sizes = np.bincount(comp[comp >= 0].ravel(), minlength=n)
        art = art | np.isin(comp, [c for c in range(n) if sizes[c] < min_hole])
    return art


def fill_bbox(art: np.ndarray) -> np.ndarray:
    """The filled bounding box of ``art`` - what a solid cell is drawn on."""
    ys, xs = np.where(art)
    if not len(xs):
        return art
    filled = np.zeros(art.shape, bool)
    filled[ys.min():ys.max() + 1, xs.min():xs.max() + 1] = True
    return filled


def key_cell(value: np.ndarray, params: KeyParams) -> np.ndarray:
    """The art mask of one cell: true where the cell is opaque."""
    matte = value <= params.tol_key
    if not matte.any():
        return np.zeros(matte.shape, bool)
    pocket = erode(matte, params.radius)
    if not pocket.any():
        return np.ones(matte.shape, bool)          # a full-bleed tile
    flow = value <= params.tol_flow
    comp, n = label(flow)
    if n == 0:
        return np.ones(matte.shape, bool)
    area = matte.size
    sizes = np.bincount(comp[comp >= 0].ravel(), minlength=n)
    seeded = set(np.unique(comp[pocket & flow]).tolist()) - {-1}
    at_rim = (set(comp[0, :].tolist()) | set(comp[-1, :].tolist())
              | set(comp[:, 0].tolist()) | set(comp[:, -1].tolist())) - {-1}

    # Background proper: a thick near-black region reached from outside the cell,
    # or one large enough to be the cell's own emptiness (a wall block's hole).
    keep = {c for c in seeded if c in at_rim or sizes[c] >= params.enclosed_min * area}
    if not keep:
        return np.ones(matte.shape, bool)

    # Then admit any other thick pocket lying within `bridge` of it. A chair's
    # leg gap is sealed by a contact shadow a few pixels deep; a bookshelf's
    # interior is behind its frame and stays out.
    while True:
        core = np.isin(comp, list(keep))
        reachable = dilate(core, params.bridge, matte)
        joined = {c for c in seeded - keep if (comp == c)[reachable].any()}
        if not joined:
            break
        keep |= joined

    core = np.isin(comp, list(keep))
    art = ~dilate(core, params.reach, matte)
    art = despeckle(art, params.min_island, params.min_hole, params.thin)
    return fill_bbox(art) if params.solid else art


def key_sheet(value: np.ndarray,
              cells: list[tuple[int, int, int, int]],
              params: KeyParams | list[KeyParams] = KeyParams()) -> np.ndarray:
    """Key every supplied cell rect ``(x0, y0, x1, y1)``; everything else stays clear.

    ``params`` may be one setting for the whole sheet or one per cell. Per cell
    is not a convenience: a paving slab and a wrought-iron bench disagree about
    what counts as background on the same plate, because the bench's own frame
    is drawn inside the range that is background beside the slab. Keying the
    bench at the slab's threshold deletes its rails and leaves four planks
    floating; keying the slab at the bench's leaves a dark halo round a surface
    that has to tile.
    """
    per_cell = params if isinstance(params, list) else [params] * len(cells)
    if len(per_cell) != len(cells):
        raise ValueError("got %d parameter sets for %d cells" % (len(per_cell), len(cells)))
    art = np.zeros(value.shape, bool)
    for (x0, y0, x1, y1), cell_params in zip(cells, per_cell):
        art[y0:y1, x0:x1] = key_cell(value[y0:y1, x0:x1], cell_params)
    return art


def grid_cells(width: int, height: int, origin_x: float, pitch_x: float,
               origin_y: float, pitch_y: float, cols: int, rows: int
               ) -> list[tuple[int, int, int, int]]:
    """A plate's cells, from the cut its authoring document records."""
    cells = []
    for r in range(rows):
        y0 = max(0, int(round(origin_y + r * pitch_y)))
        y1 = min(height, int(round(origin_y + (r + 1) * pitch_y)))
        for c in range(cols):
            x0 = max(0, int(round(origin_x + c * pitch_x)))
            x1 = min(width, int(round(origin_x + (c + 1) * pitch_x)))
            cells.append((x0, y0, x1, y1))
    return cells


def band_cells(value: np.ndarray, expected: int, tol: int = 12,
               min_run: int = 8, min_gap: int = 8) -> list[tuple[int, int, int, int]]:
    """A strip's cells: one full-height band per piece, split down its gutters.

    A strip of props has no grid to cut on, so the bands come from the sheet's
    own column gutters. Runs narrower than ``min_run`` are matte dust rather
    than a piece and are absorbed into whichever piece they sit beside; the
    remaining runs are separated at the midpoint of the gap between them, so
    every column of the sheet belongs to exactly one band and a piece's own
    outline is never cut off by the band edge.

    A piece need not be one blob. A scatter of pebbles is one prop drawn as
    three stones with daylight between them, and the daylight inside it is
    narrower than the gutters that separate it from its neighbours - so runs
    closer together than ``min_gap`` are one band. Which those are is the same
    question the loader answers on the exported atlas with its own minimum gap,
    and answering it differently here would pack a piece the loader then splits.
    """
    lit = (value > tol).any(axis=0)
    xs = np.where(lit)[0]
    if not len(xs):
        raise ValueError("the sheet is empty at tol=%d" % tol)
    runs: list[list[int]] = []
    start = previous = int(xs[0])
    for value_x in xs[1:]:
        x = int(value_x)
        if x > previous + 1:
            runs.append([start, previous])
            start = x
        previous = x
    runs.append([start, previous])
    joined: list[list[int]] = [runs[0]]
    for run in runs[1:]:
        if run[0] - joined[-1][1] - 1 < min_gap:
            joined[-1][1] = run[1]
        else:
            joined.append(run)
    runs = [run for run in joined if run[1] - run[0] + 1 >= min_run]
    if len(runs) != expected:
        raise ValueError("found %d pieces at tol=%d, expected %d: %s"
                         % (len(runs), tol, expected, runs))
    height, width = value.shape
    bands = []
    for i, (x0, x1) in enumerate(runs):
        left = 0 if i == 0 else (runs[i - 1][1] + x0) // 2
        right = width if i == len(runs) - 1 else (x1 + runs[i + 1][0]) // 2 + 1
        bands.append((left, 0, right, height))
    return bands


def write_alpha(path: Path, art: np.ndarray) -> None:
    """Replace the sheet's alpha, leaving RGB byte-for-byte alone.

    The edit has to be reviewable as an edit: a re-keyed sheet whose colour also
    moved cannot be told apart from a re-generated one.
    """
    rgb = np.asarray(Image.open(path).convert("RGB"))
    rgba = np.dstack([rgb, np.where(art, 255, 0).astype(np.uint8)])
    Image.fromarray(rgba, "RGBA").save(path)


HERE = Path(__file__).resolve().parent

#: Props whose own ironwork is drawn inside the range the pavers call
#: background: the culvert's rim, and both benches' rails, arms and legs.
_IRONWORK = KeyParams(tol_key=8, tol_flow=4)


def _urban_tileset(value: np.ndarray) -> tuple[list, KeyParams | list[KeyParams]]:
    height, width = value.shape
    cells = grid_cells(width, height, 2.305555555555543, 123.25, 0.0, 125.4, 10, 10)
    return cells, KeyParams()


def _urban_tileset_3(value: np.ndarray) -> tuple[list, KeyParams | list[KeyParams]]:
    cells = band_cells(value, 7)
    return cells, [KeyParams()] * 4 + [_IRONWORK] * 3


#: A repeating ground field: solid where it is drawn, whatever its rim reads as.
_FIELD = KeyParams(solid=True)

#: Plants and rocks, drawn against black with a black outline of their own -
#: the same disagreement the pavers and the ironwork have on urban-tileset-3.
#: A rock is also several stones, so an island the size of one pebble is the
#: prop rather than dust the key admitted.
_SCATTER = KeyParams(tol_key=12, tol_flow=6, min_island=40, min_hole=20)


def _nature_tiles(value: np.ndarray) -> tuple[list, KeyParams | list[KeyParams]]:
    cells = band_cells(value, 20)
    return cells, [_FIELD] * 7 + [_SCATTER] * 13


#: Every sheet whose alpha was keyed here, and how. A sheet is keyed once and
#: the keyed PNG is what ships forward, so this table is not a build step - it
#: is what lets the edit be re-run, reviewed, and proved unchanged.
SHEETS = {
    "urban-tileset.raw.png": _urban_tileset,
    "urban-tileset-3.raw.png": _urban_tileset_3,
    "nature-tiles.raw.png": _nature_tiles,
}


def main(argv: list[str]) -> int:
    if len(argv) < 2 or argv[1] not in SHEETS:
        print("usage: key_background.py <%s> [--check]" % "|".join(SHEETS))
        return 2
    name = argv[1]
    check = "--check" in argv[2:]
    path = HERE / name
    image = Image.open(path)
    value = value_plane(image)
    cells, params = SHEETS[name](value)
    art = key_sheet(value, cells, params)
    if check:
        if not image.mode.endswith("A"):
            print("%s carries no alpha to check against" % name)
            return 1
        existing = np.asarray(image.convert("RGBA"))[:, :, 3] > 127
        differing = int((art != existing).sum())
        print("%s: %d of %d pixels differ from the checked-in alpha"
              % (name, differing, art.size))
        return 0 if differing == 0 else 1
    write_alpha(path, art)
    print("%s: keyed %d cells, %.4f opaque" % (name, len(cells), art.mean()))
    return 0


if __name__ == "__main__":
    import sys

    raise SystemExit(main(sys.argv))
