"""Normalize and material-pack sources into the canonical runtime atlases.

The generated images use model-selected canvas sizes and a near-black matte.
This script preserves the generated surface rendering while restoring the
runtime atlas dimensions and fixed-grid alpha topology. The checked-in runtime
atlases are the geometry templates and are overwritten in place with normalized
output. Individual material replacements are applied afterward through
``texture-atlases.json``.

Borrowing the alpha topology makes a shipped atlas its own input, so this
script may only be pointed at a sheet whose raw plate is opaque. A sheet whose
raw art has been keyed is exported from its authoring document instead, and is
refused here rather than silently reverted.
"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter

from pack_texture_atlas import pack_manifest


HERE = Path(__file__).resolve().parent
# Raw art lives outside mod/, so the shipped folder never carries pre-pack
# inputs. Outputs are addressed from the repository root rather than from
# a sibling directory.
REPO_ROOT = HERE.parent.parent
TILESETS = REPO_ROOT / "mod" / "graphics" / "tilesets"


@dataclass(frozen=True)
class GridSpec:
    source: str
    raw: str
    output: str


GRID_SPECS = (
    GridSpec("Floors_Tiles.png", "Floors_Tiles.raw.png", "Floors_Tiles.png"),
    GridSpec("Water_tiles.png", "Water_tiles.raw.png", "Water_tiles.png"),
)

# Three sheets that were once listed above are deliberately absent, and the
# guard below is what keeps them absent. Their raw art now carries its own keyed
# alpha and their atlases are produced by the tileset authoring export from
# their authoring documents; this script is no longer their producer. The last
# of them took the whole auto-strip half of this script with it - the pinned
# frame boxes, the run merging and the ground-edge band - because a strip is a
# shape the exporter has now and every one of those was a coordinate held
# outside the tileset that described the atlas. See _refuse_keyed_raw_sheets for
# why the withdrawal is measured rather than written down.

# Repeating ground fields must tile without the dark outline ImageGen painted
# around isolated source sprites. Deliberately exclude wall, transition, and
# overlay cells: their edge contrast communicates topology rather than atlas
# separation.
GRID_GROUND_EDGE_CELLS = {
    "Floors_Tiles.png": (
        56,
        (
            (17, 1), (16, 2), (17, 2), (18, 2), (17, 3),  # brick
            (1, 10), (2, 10), (3, 10),                    # grass
            (6, 10), (7, 10), (8, 10),                    # stone
            (11, 10), (12, 10), (13, 10),                 # dirt
            (6, 14), (7, 14), (8, 14),                    # sand
        ),
        7,
    ),
    "Water_tiles.png": (
        16,
        ((6, 7), (7, 7), (8, 7)),
        2,
    ),
}

# The three generated sand variants have different left/right
# brightness ramps. Each one is seamless with itself after edge cleanup, but
# the runtime hash pool places unlike variants beside each other and exposes a
# periodic vertical join. Normalize only their horizontal edge columns to the
# shared pool mean; retain each variant's interior and top/bottom texture.
GRID_HORIZONTAL_EDGE_POOLS = {
    "Floors_Tiles.png": (
        56,
        ((6, 14), (7, 14), (8, 14)),
        10,
    ),
}

GRID_HORIZONTAL_BIAS_POOLS = {
    "Floors_Tiles.png": (
        56,
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


def validate() -> None:
    for spec in GRID_SPECS:
        source = Image.open(TILESETS / spec.source).convert("RGBA")
        output = Image.open(TILESETS / spec.output).convert("RGBA")
        if output.size != source.size:
            raise ValueError(f"{spec.output}: size {output.size}, expected {source.size}")
        if not np.array_equal(np.asarray(output)[:, :, 3], np.asarray(source)[:, :, 3]):
            raise ValueError(f"{spec.output}: fixed-grid alpha topology changed")


def _refuse_keyed_raw_sheets() -> None:
    """Refuse to run against any sheet whose raw art carries its own alpha.

    This script derives an atlas by transferring fresh colour onto the alpha of
    the atlas it is about to overwrite. That makes the shipped file its own
    input, which is fine only while the raw plate is opaque and has therefore
    recorded nothing about what is background and what is art.

    A raw sheet that has been keyed has recorded exactly that, at the source,
    per cell. Its atlas is exported from its authoring document instead, and
    this script is not its producer. Running it anyway would rebuild the sheet
    from the alpha topology of the file it is replacing and silently revert the
    export: a valid PNG, the right size, the wrong art, and nothing red at the
    moment it happens.

    The withdrawal is measured rather than declared. Keying the plate *is* the
    withdrawal, so there is no flag to set, no list to keep in step, and nothing
    for the next person to have read. Removing a sheet from the spec tuples
    above without this check would be an instruction, and an instruction is
    followed until it isn't.
    """
    keyed = []
    for spec in GRID_SPECS:
        with Image.open(HERE / spec.raw) as image:
            if "A" not in image.getbands():
                continue
            alpha = np.asarray(image.getchannel("A"))
        if bool((alpha < 128).any()):
            keyed.append(spec.raw)
    if keyed:
        raise SystemExit(
            "refusing to run: " + ", ".join(keyed) + " carries its own keyed "
            "alpha, so its atlas is exported from its authoring document and "
            "this script is not its producer. Normalizing it would rebuild it "
            "from the alpha of the atlas it is about to overwrite, silently "
            "reverting that export. Remove it from GRID_SPECS and "
            "export it through the tileset authoring exporter instead."
        )


def main() -> None:
    _refuse_keyed_raw_sheets()
    for spec in GRID_SPECS:
        normalize_grid(spec)
    packed = pack_manifest(HERE / "texture-atlases.json")
    validate()
    for spec in GRID_SPECS:
        print(TILESETS / spec.output)
    for atlas in packed:
        print(f"packed {atlas.atlas_id}: {atlas.placements} placement(s)")


if __name__ == "__main__":
    main()
