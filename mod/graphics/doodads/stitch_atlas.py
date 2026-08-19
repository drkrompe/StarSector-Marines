"""Build a fixed-grid doodad atlas from individual transparent PNG assets.

Drop PNG cutouts into ``sources/`` and run this file. Each image is trimmed to
its alpha bounds, scaled to fit its authored cell footprint, centered, and
written to a deterministic grid. The companion ``*.tileset.json`` is regenerated
with matching doodad ids, frame coordinates, and footprints, so source ordering
never has to be maintained by hand.
"""

from __future__ import annotations

import argparse
import json
import math
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from PIL import Image


HERE = Path(__file__).resolve().parent
MOD_ROOT = HERE.parent.parent
DEFAULT_INPUT = HERE / "sources"
DEFAULT_IMAGE = HERE / "doodads.png"
DEFAULT_MANIFEST = MOD_ROOT / "data" / "tilesets" / "doodads.tileset.json"
DEFAULT_SHEET_PATH = "graphics/doodads/doodads.png"
VALID_COVER = {"none", "light", "med", "heavy"}
VALID_WALL_SIDES = {"N", "S", "E", "W"}


@dataclass(frozen=True)
class AssetSpec:
    source: Path
    asset_id: str
    cover: str
    ballistic_half_height: float | None
    name: str
    description: str
    order: float
    padding: int
    scale: float
    offset_x: int
    offset_y: int
    footprint_x: int
    footprint_y: int
    preferred_wall_side: str | None


@dataclass(frozen=True)
class BuildOptions:
    input_dir: Path
    output_image: Path
    output_manifest: Path
    sheet_path: str
    metadata_path: Path
    columns: int = 8
    cell_size: int = 32
    padding: int = 2
    id_prefix: str = "doodad."
    resample: str = "lanczos"
    alpha_threshold: int = 1
    allow_opaque: bool = False


def _load_metadata(path: Path) -> dict[str, Any]:
    if not path.exists():
        return {}
    with path.open("r", encoding="utf-8") as stream:
        data = json.load(stream)
    if not isinstance(data, dict):
        raise ValueError(f"{path}: metadata root must be a JSON object")
    return data


def _slug(stem: str) -> str:
    value = re.sub(r"[^a-z0-9]+", "-", stem.lower()).strip("-")
    if not value:
        raise ValueError(f"Cannot derive an id from filename stem {stem!r}")
    return value


def _asset_metadata(metadata: dict[str, Any], path: Path) -> dict[str, Any]:
    defaults = metadata.get("defaults", {})
    assets = metadata.get("assets", {})
    if not isinstance(defaults, dict) or not isinstance(assets, dict):
        raise ValueError("metadata 'defaults' and 'assets' values must be JSON objects")
    override = assets.get(path.name, assets.get(path.stem, {}))
    if not isinstance(override, dict):
        raise ValueError(f"metadata for {path.name} must be a JSON object")
    return {**defaults, **override}


def _parse_offset(value: Any, source: Path) -> tuple[int, int]:
    if value is None:
        return 0, 0
    if not isinstance(value, list) or len(value) != 2:
        raise ValueError(f"{source.name}: offset must be [x, y]")
    return int(value[0]), int(value[1])


def _parse_footprint(value: Any, source: Path) -> tuple[int, int]:
    if value is None:
        return 1, 1
    if not isinstance(value, list) or len(value) != 2:
        raise ValueError(f"{source.name}: footprintCells must be [width, height]")
    width, height = int(value[0]), int(value[1])
    if width <= 0 or height <= 0:
        raise ValueError(f"{source.name}: footprintCells values must be positive")
    return width, height


def discover_assets(options: BuildOptions) -> list[AssetSpec]:
    if not options.input_dir.is_dir():
        raise ValueError(f"Input directory does not exist: {options.input_dir}")
    metadata = _load_metadata(options.metadata_path)
    output_resolved = options.output_image.resolve()
    candidates = sorted(
        (
            path
            for path in options.input_dir.iterdir()
            if path.is_file()
            and path.suffix.lower() == ".png"
            and path.resolve() != output_resolved
        ),
        key=lambda path: path.name.casefold(),
    )

    specs: list[AssetSpec] = []
    seen_ids: set[str] = set()
    for path in candidates:
        item = _asset_metadata(metadata, path)
        if item.get("enabled", True) is False:
            continue
        asset_id = str(item.get("id", options.id_prefix + _slug(path.stem)))
        if asset_id in seen_ids:
            raise ValueError(f"Duplicate doodad id {asset_id!r}")
        seen_ids.add(asset_id)
        cover = str(item.get("cover", "none")).lower()
        if cover not in VALID_COVER:
            raise ValueError(
                f"{path.name}: cover must be one of {sorted(VALID_COVER)}, got {cover!r}"
            )
        ballistic_half_height = item.get("ballisticHalfHeight")
        if ballistic_half_height is not None:
            ballistic_half_height = float(ballistic_half_height)
            if not math.isfinite(ballistic_half_height) or ballistic_half_height < 0:
                raise ValueError(
                    f"{path.name}: ballisticHalfHeight must be finite and non-negative"
                )
        padding = int(item.get("padding", options.padding))
        if padding < 0 or padding * 2 >= options.cell_size:
            raise ValueError(f"{path.name}: padding {padding} leaves no drawable cell area")
        scale = float(item.get("scale", 1.0))
        if not 0 < scale <= 1:
            raise ValueError(f"{path.name}: scale must be greater than 0 and at most 1")
        offset_x, offset_y = _parse_offset(item.get("offset"), path)
        footprint_x, footprint_y = _parse_footprint(item.get("footprintCells"), path)
        preferred_wall_side = item.get("preferredWallSide")
        if preferred_wall_side is not None:
            preferred_wall_side = str(preferred_wall_side).upper()
            if preferred_wall_side not in VALID_WALL_SIDES:
                raise ValueError(
                    f"{path.name}: preferredWallSide must be one of "
                    f"{sorted(VALID_WALL_SIDES)}"
                )
        specs.append(
            AssetSpec(
                source=path,
                asset_id=asset_id,
                cover=cover,
                ballistic_half_height=ballistic_half_height,
                name=str(item.get("name", path.stem)),
                description=str(item.get("description", "")),
                order=float(item.get("order", math.inf)),
                padding=padding,
                scale=scale,
                offset_x=offset_x,
                offset_y=offset_y,
                footprint_x=footprint_x,
                footprint_y=footprint_y,
                preferred_wall_side=preferred_wall_side,
            )
        )

    specs.sort(key=lambda spec: (spec.order, spec.source.name.casefold()))
    if not specs:
        raise ValueError(f"No enabled PNG assets found in {options.input_dir}")
    return specs


def _resampling(name: str) -> Image.Resampling:
    return {
        "nearest": Image.Resampling.NEAREST,
        "bilinear": Image.Resampling.BILINEAR,
        "bicubic": Image.Resampling.BICUBIC,
        "lanczos": Image.Resampling.LANCZOS,
    }[name]


def normalize_asset(spec: AssetSpec, options: BuildOptions) -> Image.Image:
    source = Image.open(spec.source).convert("RGBA")
    alpha = source.getchannel("A")
    minimum, maximum = alpha.getextrema()
    if maximum <= options.alpha_threshold:
        raise ValueError(f"{spec.source.name}: image has no visible pixels")
    if minimum == 255 and not options.allow_opaque:
        raise ValueError(
            f"{spec.source.name}: image is fully opaque; remove its matte/chroma-key "
            "background or pass --allow-opaque"
        )

    # Threshold only for finding the crop. Preserve the original soft alpha at
    # edges so high-resolution ImageGen cutouts downsample cleanly.
    crop_mask = alpha.point(
        lambda value: 255 if value > options.alpha_threshold else 0,
        mode="1",
    )
    bounds = crop_mask.getbbox()
    if bounds is None:
        raise ValueError(f"{spec.source.name}: image has no visible alpha bounds")
    sprite = source.crop(bounds)

    canvas_width = options.cell_size * spec.footprint_x
    canvas_height = options.cell_size * spec.footprint_y
    available_width = canvas_width - spec.padding * 2
    available_height = canvas_height - spec.padding * 2
    target_width = max(1, round(available_width * spec.scale))
    target_height = max(1, round(available_height * spec.scale))
    ratio = min(target_width / sprite.width, target_height / sprite.height)
    width = max(1, round(sprite.width * ratio))
    height = max(1, round(sprite.height * ratio))
    sprite = sprite.resize((width, height), _resampling(options.resample))

    x = (canvas_width - width) // 2 + spec.offset_x
    y = (canvas_height - height) // 2 + spec.offset_y
    if x < 0 or y < 0 or x + width > canvas_width or y + height > canvas_height:
        raise ValueError(
            f"{spec.source.name}: normalized sprite plus offset does not fit "
            f"inside its {spec.footprint_x}x{spec.footprint_y} footprint"
        )
    cell = Image.new("RGBA", (canvas_width, canvas_height), (0, 0, 0, 0))
    cell.alpha_composite(sprite, (x, y))
    return cell


def pack_assets(specs: list[AssetSpec], columns: int) -> tuple[list[tuple[int, int]], int]:
    """First-fit row-major packing on the atlas cell grid."""
    occupied: set[tuple[int, int]] = set()
    placements: list[tuple[int, int]] = []
    max_row = -1
    for spec in specs:
        if spec.footprint_x > columns:
            raise ValueError(
                f"{spec.source.name}: footprint width {spec.footprint_x} exceeds "
                f"atlas width {columns}"
            )
        row = 0
        while True:
            placed = False
            for col in range(columns - spec.footprint_x + 1):
                cells = [
                    (col + dx, row + dy)
                    for dy in range(spec.footprint_y)
                    for dx in range(spec.footprint_x)
                ]
                if any(cell in occupied for cell in cells):
                    continue
                occupied.update(cells)
                placements.append((col, row))
                max_row = max(max_row, row + spec.footprint_y - 1)
                placed = True
                break
            if placed:
                break
            row += 1
    return placements, max_row + 1


def build_atlas(options: BuildOptions) -> tuple[Path, Path, int]:
    if options.columns <= 0 or options.cell_size <= 0:
        raise ValueError("columns and cell-size must be positive")
    if not 0 <= options.alpha_threshold <= 254:
        raise ValueError("alpha-threshold must be between 0 and 254")

    specs = discover_assets(options)
    placements, rows = pack_assets(specs, options.columns)
    atlas = Image.new(
        "RGBA",
        (options.columns * options.cell_size, rows * options.cell_size),
        (0, 0, 0, 0),
    )
    doodads: list[dict[str, Any]] = []
    cells: list[dict[str, Any]] = []

    for spec, (col, row) in zip(specs, placements):
        atlas.alpha_composite(
            normalize_asset(spec, options),
            (col * options.cell_size, row * options.cell_size),
        )
        doodad: dict[str, Any] = {
            "id": spec.asset_id,
            "col": col,
            "row": row,
            "cover": spec.cover,
        }
        if spec.ballistic_half_height is not None:
            doodad["ballisticHalfHeight"] = spec.ballistic_half_height
        if spec.footprint_x != 1 or spec.footprint_y != 1:
            doodad["footprintCells"] = [spec.footprint_x, spec.footprint_y]
        if spec.preferred_wall_side is not None:
            doodad["preferredWallSide"] = spec.preferred_wall_side
        doodads.append(doodad)
        cell: dict[str, Any] = {"col": col, "row": row, "name": spec.name}
        if spec.description:
            cell["description"] = spec.description
        cells.append(cell)

    manifest = {
        "sheet": options.sheet_path,
        "cellPx": options.cell_size,
        "doodads": doodads,
        "cells": cells,
    }
    options.output_image.parent.mkdir(parents=True, exist_ok=True)
    options.output_manifest.parent.mkdir(parents=True, exist_ok=True)
    atlas.save(options.output_image, optimize=True)
    options.output_manifest.write_text(
        json.dumps(manifest, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )
    return options.output_image, options.output_manifest, len(specs)


def parse_args() -> BuildOptions:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input_dir", nargs="?", type=Path, default=DEFAULT_INPUT)
    parser.add_argument("--output-image", type=Path, default=DEFAULT_IMAGE)
    parser.add_argument("--output-manifest", type=Path, default=DEFAULT_MANIFEST)
    parser.add_argument("--sheet-path", default=DEFAULT_SHEET_PATH)
    parser.add_argument("--metadata", type=Path)
    parser.add_argument("--columns", type=int, default=8)
    parser.add_argument("--cell-size", type=int, default=32)
    parser.add_argument("--padding", type=int, default=2)
    parser.add_argument("--id-prefix", default="doodad.")
    parser.add_argument(
        "--resample",
        choices=("nearest", "bilinear", "bicubic", "lanczos"),
        default="lanczos",
    )
    parser.add_argument("--alpha-threshold", type=int, default=1)
    parser.add_argument("--allow-opaque", action="store_true")
    args = parser.parse_args()
    metadata_path = args.metadata or args.input_dir / "_atlas.json"
    return BuildOptions(
        input_dir=args.input_dir,
        output_image=args.output_image,
        output_manifest=args.output_manifest,
        sheet_path=args.sheet_path,
        metadata_path=metadata_path,
        columns=args.columns,
        cell_size=args.cell_size,
        padding=args.padding,
        id_prefix=args.id_prefix,
        resample=args.resample,
        alpha_threshold=args.alpha_threshold,
        allow_opaque=args.allow_opaque,
    )


def main() -> None:
    image_path, manifest_path, count = build_atlas(parse_args())
    print(f"Packed {count} assets")
    print(image_path.resolve())
    print(manifest_path.resolve())


if __name__ == "__main__":
    main()
