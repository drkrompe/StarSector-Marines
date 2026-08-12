"""Normalize raw ImageGen tileset edits back into runtime-compatible atlases.

The generated images use model-selected canvas sizes and a near-black matte.
This script preserves the generated surface rendering while restoring the
original atlas dimensions, fixed-grid alpha topology, and auto-strip frame
ordering. Originals are constraints only and are never overwritten.
"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter


HERE = Path(__file__).resolve().parent
TILESETS = HERE.parent


@dataclass(frozen=True)
class GridSpec:
    source: str
    raw: str
    output: str


@dataclass(frozen=True)
class StripSpec:
    source: str
    raw: str
    output: str
    frames: int


GRID_SPECS = (
    GridSpec("urban-tileset.png", "urban-tileset.raw.png", "urban-tileset-imagegen.png"),
    GridSpec("urban-tileset-2.png", "urban-tileset-2.raw.png", "urban-tileset-2-imagegen.png"),
    GridSpec("Floors_Tiles.png", "Floors_Tiles.raw.png", "Floors_Tiles-imagegen.png"),
    GridSpec("Water_tiles.png", "Water_tiles.raw.png", "Water_tiles-imagegen.png"),
)

STRIP_SPECS = (
    StripSpec("urban-tileset-3.png", "urban-tileset-3.raw.png", "urban-tileset-3-imagegen.png", 7),
    StripSpec("nature-tiles.png", "nature-tiles.raw.png", "nature-tiles-imagegen.png", 20),
)

# Repeating ground fields must tile without the dark outline ImageGen painted
# around isolated source sprites. Deliberately exclude wall, transition, and
# overlay cells: their edge contrast communicates topology rather than atlas
# separation.
GRID_GROUND_EDGE_CELLS = {
    "Floors_Tiles-imagegen.png": (
        16,
        (
            (17, 1), (16, 2), (17, 2), (18, 2), (17, 3),  # brick
            (1, 10), (2, 10), (3, 10),                    # grass
            (6, 10), (7, 10), (8, 10),                    # stone
            (11, 10), (12, 10), (13, 10),                 # dirt
            (6, 14), (7, 14), (8, 14),                    # sand
        ),
        2,
    ),
    "Water_tiles-imagegen.png": (
        16,
        ((6, 7), (7, 7), (8, 7)),
        2,
    ),
}

STRIP_GROUND_EDGE_FRAMES = {
    "urban-tileset-3-imagegen.png": (4, 3),
    # ImageGen's ground frames have a shallow 3px side outline but a much
    # deeper bottom shadow; sample vertical edges 6px inward.
    "nature-tiles-imagegen.png": (7, (3, 6)),
}

# The three 16px sand variants were generated with different left/right
# brightness ramps. Each one is seamless with itself after edge cleanup, but
# the runtime hash pool places unlike variants beside each other and exposes a
# periodic vertical join. Normalize only their horizontal edge columns to the
# shared pool mean; retain each variant's interior and top/bottom texture.
GRID_HORIZONTAL_EDGE_POOLS = {
    "Floors_Tiles-imagegen.png": (
        16,
        ((6, 14), (7, 14), (8, 14)),
        3,
    ),
}

GRID_HORIZONTAL_BIAS_POOLS = {
    "Floors_Tiles-imagegen.png": (
        16,
        ((6, 14), (7, 14), (8, 14)),
        0.85,
    ),
}


def _bbox(mask: np.ndarray) -> tuple[int, int, int, int]:
    ys, xs = np.where(mask)
    if not len(xs):
        raise ValueError("foreground mask is empty")
    return int(xs.min()), int(ys.min()), int(xs.max() + 1), int(ys.max() + 1)


def _raw_layers(image: Image.Image) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    """Return RGB, a confident foreground mask, and a matte-removal alpha.

    ImageGen's nominally empty background is overwhelmingly RGB 0 or 1. The
    confident mask locates artwork at values above 12. A small dilation lets us
    retain adjacent near-black outline pixels without admitting isolated matte
    noise from the rest of the canvas.
    """
    rgb = np.asarray(image.convert("RGB"))
    value = rgb.max(axis=2)
    strong = value > 12
    candidate = value > 3
    neighborhood = np.asarray(
        Image.fromarray((strong * 255).astype(np.uint8)).filter(ImageFilter.MaxFilter(7))
    ) > 0
    alpha = ((candidate & neighborhood) * 255).astype(np.uint8)
    return rgb, strong, alpha


def _sharpen(image: Image.Image) -> Image.Image:
    return image.filter(ImageFilter.UnsharpMask(radius=0.65, percent=85, threshold=2))


def _clone_rgb_edge_band(
    rgba: np.ndarray,
    box: tuple[int, int, int, int],
    band: int | tuple[int, int],
) -> None:
    """Mirror nearby interior RGB rows/columns through a tile's edge band.

    Alpha is intentionally untouched: fixed-grid alpha topology and sliced
    frame silhouettes remain byte-for-byte stable. Mirroring preserves local
    texture variation instead of creating a flat repeated-color stripe. Corners
    sample the mirrored interior corner, avoiding order-dependent overwrites.
    """
    x0, y0, x1, y1 = box
    width = x1 - x0
    height = y1 - y0
    band_x, band_y = (band, band) if isinstance(band, int) else band
    if width <= band_x * 2 or height <= band_y * 2:
        raise ValueError(f"edge band {band} is too large for box {box}")

    tile_rgb = rgba[y0:y1, x0:x1, :3].copy()
    xs = np.arange(width)
    ys = np.arange(height)
    xs[:band_x] = 2 * band_x - 1 - np.arange(band_x)
    xs[-band_x:] = width - band_x - 1 - np.arange(band_x)
    ys[:band_y] = 2 * band_y - 1 - np.arange(band_y)
    ys[-band_y:] = height - band_y - 1 - np.arange(band_y)
    rgba[y0:y1, x0:x1, :3] = tile_rgb[np.ix_(ys, xs)]


def _clean_grid_ground_edges(output: np.ndarray, output_name: str) -> None:
    config = GRID_GROUND_EDGE_CELLS.get(output_name)
    if config is None:
        return
    cell_px, cells, band = config
    for col, row in cells:
        x0 = col * cell_px
        y0 = row * cell_px
        _clone_rgb_edge_band(
            output,
            (x0, y0, x0 + cell_px, y0 + cell_px),
            band,
        )


def _match_grid_pool_horizontal_edges(output: np.ndarray, output_name: str) -> None:
    config = GRID_HORIZONTAL_EDGE_POOLS.get(output_name)
    if config is None:
        return
    cell_px, cells, band = config
    tiles = np.stack([
        output[row * cell_px:(row + 1) * cell_px,
               col * cell_px:(col + 1) * cell_px, :3].copy()
        for col, row in cells
    ])

    # Preserve row-level sand ripples while eliminating variant-to-variant
    # brightness discontinuity at the join itself.
    left_bands = tiles[:, :, :band, :]
    right_bands_facing_left = tiles[:, :, -band:, :][:, :, ::-1, :]
    shared_join = np.rint(
        np.concatenate((left_bands, right_bands_facing_left), axis=0).mean(axis=0)
    ).astype(np.uint8)
    for col, row in cells:
        y0 = row * cell_px
        x0 = col * cell_px
        output[y0:y0 + cell_px, x0:x0 + band, :3] = shared_join
        output[y0:y0 + cell_px, x0 + cell_px - band:x0 + cell_px, :3] = shared_join[:, ::-1, :]


def _flatten_grid_pool_horizontal_bias(output: np.ndarray, output_name: str) -> None:
    config = GRID_HORIZONTAL_BIAS_POOLS.get(output_name)
    if config is None:
        return
    cell_px, cells, strength = config
    tiles = np.stack([
        output[row * cell_px:(row + 1) * cell_px,
               col * cell_px:(col + 1) * cell_px, :3].astype(np.float32)
        for col, row in cells
    ])

    # Remove only the palette drift shared by all variants at each X offset.
    # Row-level ripples and per-pixel grain remain intact.
    column_mean = tiles.mean(axis=(0, 1))
    pool_mean = tiles.mean(axis=(0, 1, 2))
    correction = (pool_mean - column_mean) * strength
    for col, row in cells:
        y0 = row * cell_px
        x0 = col * cell_px
        tile = output[y0:y0 + cell_px, x0:x0 + cell_px, :3].astype(np.float32)
        output[y0:y0 + cell_px, x0:x0 + cell_px, :3] = np.clip(
            np.rint(tile + correction[np.newaxis, :, :]), 0, 255
        ).astype(np.uint8)


def _clean_strip_ground_edges(
    output: np.ndarray,
    output_name: str,
    frame_boxes: list[tuple[int, int, int, int]],
) -> None:
    config = STRIP_GROUND_EDGE_FRAMES.get(output_name)
    if config is None:
        return
    frame_count, band = config
    for box in frame_boxes[:frame_count]:
        _clone_rgb_edge_band(output, box, band)


def normalize_grid(spec: GridSpec) -> None:
    source = Image.open(TILESETS / spec.source).convert("RGBA")
    raw = Image.open(HERE / spec.raw).convert("RGB")
    source_rgba = np.asarray(source)
    source_mask = source_rgba[:, :, 3] > 0
    raw_rgb, raw_strong, raw_alpha = _raw_layers(raw)

    sx0, sy0, sx1, sy1 = _bbox(source_mask)
    rx0, ry0, rx1, ry1 = _bbox(raw_strong)
    target_size = (sx1 - sx0, sy1 - sy0)

    color = Image.fromarray(raw_rgb[ry0:ry1, rx0:rx1]).resize(
        target_size, Image.Resampling.LANCZOS
    )
    color = _sharpen(color)
    fitted_alpha = Image.fromarray(raw_alpha[ry0:ry1, rx0:rx1]).resize(
        target_size, Image.Resampling.LANCZOS
    )

    color_arr = np.asarray(color).copy()
    fitted_alpha_arr = np.asarray(fitted_alpha)
    source_crop = source_rgba[sy0:sy1, sx0:sx1]

    # If the model left a hole inside an originally opaque tile, retain the
    # original pixel instead of turning valid atlas geometry into black.
    missing = (fitted_alpha_arr < 32) & (source_crop[:, :, 3] > 0)
    color_arr[missing] = source_crop[:, :, :3][missing]

    output = np.zeros_like(source_rgba)
    output[sy0:sy1, sx0:sx1, :3] = color_arr
    # Fixed-grid sheets use their original alpha topology as the hard contract:
    # exact tile silhouettes, holes, empty cells, and seam-facing edges.
    output[:, :, 3] = source_rgba[:, :, 3]
    _clean_grid_ground_edges(output, spec.output)
    _flatten_grid_pool_horizontal_bias(output, spec.output)
    _match_grid_pool_horizontal_edges(output, spec.output)
    Image.fromarray(output, "RGBA").save(TILESETS / spec.output)


def _column_runs(mask: np.ndarray) -> list[list[int]]:
    xs = np.where(mask.any(axis=0))[0]
    if not len(xs):
        return []
    runs: list[list[int]] = []
    start = previous = int(xs[0])
    for value in xs[1:]:
        x = int(value)
        if x > previous + 1:
            runs.append([start, previous + 1])
            start = x
        previous = x
    runs.append([start, previous + 1])
    return runs


def _merge_to_count(runs: list[list[int]], expected: int) -> list[list[int]]:
    if len(runs) < expected:
        raise ValueError(f"detected {len(runs)} runs, expected at least {expected}")
    merged = [run[:] for run in runs]
    while len(merged) > expected:
        gaps = [merged[i + 1][0] - merged[i][1] for i in range(len(merged) - 1)]
        index = min(range(len(gaps)), key=gaps.__getitem__)
        merged[index][1] = merged[index + 1][1]
        del merged[index + 1]
    return merged


def _frame_boxes(mask: np.ndarray, expected: int) -> list[tuple[int, int, int, int]]:
    boxes: list[tuple[int, int, int, int]] = []
    for x0, x1 in _merge_to_count(_column_runs(mask), expected):
        local = mask[:, x0:x1]
        y0 = int(np.where(local)[0].min())
        y1 = int(np.where(local)[0].max() + 1)
        boxes.append((x0, y0, x1, y1))
    return boxes


def normalize_strip(spec: StripSpec) -> None:
    source = Image.open(TILESETS / spec.source).convert("RGBA")
    raw = Image.open(HERE / spec.raw).convert("RGB")
    source_rgba = np.asarray(source)
    source_mask = source_rgba[:, :, 3] > 16
    raw_rgb, raw_strong, raw_alpha = _raw_layers(raw)

    source_boxes = _frame_boxes(source_mask, spec.frames)
    raw_boxes = _frame_boxes(raw_strong, spec.frames)
    output = np.zeros_like(source_rgba)
    raw_h, raw_w = raw_strong.shape

    for source_box, raw_box in zip(source_boxes, raw_boxes):
        sx0, sy0, sx1, sy1 = source_box
        rx0, ry0, rx1, ry1 = raw_box

        # Retain dark outline pixels just outside the confident (>12) bounds.
        pad = 3
        rx0 = max(0, rx0 - pad)
        ry0 = max(0, ry0 - pad)
        rx1 = min(raw_w, rx1 + pad)
        ry1 = min(raw_h, ry1 + pad)
        target_size = (sx1 - sx0, sy1 - sy0)

        color = Image.fromarray(raw_rgb[ry0:ry1, rx0:rx1]).resize(
            target_size, Image.Resampling.LANCZOS
        )
        color = _sharpen(color)
        alpha = Image.fromarray(raw_alpha[ry0:ry1, rx0:rx1]).resize(
            target_size, Image.Resampling.LANCZOS
        )

        color_arr = np.asarray(color)
        # Existing sprite strips use hard pixel-art transparency. A hard edge
        # also prevents the auto-slicer from seeing antialiasing dust as frames.
        alpha_arr = np.where(np.asarray(alpha) >= 72, 255, 0).astype(np.uint8)
        output[sy0:sy1, sx0:sx1, :3] = color_arr
        output[sy0:sy1, sx0:sx1, 3] = alpha_arr

    _clean_strip_ground_edges(output, spec.output, source_boxes)
    Image.fromarray(output, "RGBA").save(TILESETS / spec.output)


def validate() -> None:
    for spec in GRID_SPECS:
        source = Image.open(TILESETS / spec.source).convert("RGBA")
        output = Image.open(TILESETS / spec.output).convert("RGBA")
        if output.size != source.size:
            raise ValueError(f"{spec.output}: size {output.size}, expected {source.size}")
        if not np.array_equal(np.asarray(output)[:, :, 3], np.asarray(source)[:, :, 3]):
            raise ValueError(f"{spec.output}: fixed-grid alpha topology changed")

    for spec in STRIP_SPECS:
        source = Image.open(TILESETS / spec.source).convert("RGBA")
        output = Image.open(TILESETS / spec.output).convert("RGBA")
        if output.size != source.size:
            raise ValueError(f"{spec.output}: size {output.size}, expected {source.size}")
        count = len(_frame_boxes(np.asarray(output)[:, :, 3] > 16, spec.frames))
        if count != spec.frames:
            raise ValueError(f"{spec.output}: detected {count} frames, expected {spec.frames}")


def main() -> None:
    for spec in GRID_SPECS:
        normalize_grid(spec)
    for spec in STRIP_SPECS:
        normalize_strip(spec)
    validate()
    for spec in (*GRID_SPECS, *STRIP_SPECS):
        print(TILESETS / spec.output)


if __name__ == "__main__":
    main()
