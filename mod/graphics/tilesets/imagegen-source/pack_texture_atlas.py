"""Pack individual material textures into existing runtime atlases.

The manifest describes where a material belongs; this tool resolves either
auto-sliced frame indices or fixed-grid cells, adds optional wrapped guard
pixels, validates the complete operation, and only then atomically replaces
the output atlas. It also exposes an FFmpeg importer for reducing large
tileable source textures without sampling clamped image edges.
"""

from __future__ import annotations

import argparse
import json
import os
import subprocess
import tempfile
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import numpy as np
from PIL import Image


class AtlasPackError(ValueError):
    """Raised when an atlas manifest or material cannot be packed safely."""


@dataclass(frozen=True)
class Rect:
    x: int
    y: int
    width: int
    height: int

    @property
    def right(self) -> int:
        return self.x + self.width

    @property
    def bottom(self) -> int:
        return self.y + self.height

    def overlaps(self, other: "Rect") -> bool:
        return (
            self.x < other.right
            and other.x < self.right
            and self.y < other.bottom
            and other.y < self.bottom
        )


@dataclass(frozen=True)
class PreparedAtlas:
    atlas_id: str
    output: Path
    image: Image.Image
    placements: int


def _object(value: Any, context: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise AtlasPackError(f"{context} must be an object")
    return value


def _array(value: Any, context: str) -> list[Any]:
    if not isinstance(value, list):
        raise AtlasPackError(f"{context} must be an array")
    return value


def _string(value: Any, context: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise AtlasPackError(f"{context} must be a non-empty string")
    return value


def _integer(value: Any, context: str, minimum: int = 0) -> int:
    if isinstance(value, bool) or not isinstance(value, int) or value < minimum:
        raise AtlasPackError(f"{context} must be an integer >= {minimum}")
    return value


def _resolve(manifest_dir: Path, value: Any, context: str) -> Path:
    path = Path(_string(value, context))
    return (path if path.is_absolute() else manifest_dir / path).resolve()


def _auto_strip_frames(
    image: Image.Image,
    alpha_threshold: int,
    min_gap: int,
) -> list[Rect]:
    alpha = np.asarray(image.getchannel("A"))
    content_columns = (alpha >= alpha_threshold).any(axis=0)
    ranges: list[tuple[int, int]] = []
    start = -1
    gap = 0

    for x, has_content in enumerate(content_columns):
        if has_content:
            if start < 0:
                start = x
            gap = 0
        elif start >= 0:
            gap += 1
            if gap >= min_gap:
                ranges.append((start, x - gap + 1))
                start = -1
                gap = 0
    if start >= 0:
        ranges.append((start, image.width - gap))

    frames: list[Rect] = []
    for x0, x1 in ranges:
        local = alpha[:, x0:x1] >= alpha_threshold
        ys = np.where(local)[0]
        if not len(ys):
            raise AtlasPackError(f"auto-strip frame at x={x0} has no opaque pixels")
        y0 = int(ys.min())
        y1 = int(ys.max() + 1)
        frames.append(Rect(x0, y0, x1 - x0, y1 - y0))
    return frames


def _target_rect(
    target: dict[str, Any],
    context: str,
    layout: dict[str, Any],
    frames: list[Rect] | None,
) -> Rect:
    selectors = [name for name in ("frame", "cell", "rect") if name in target]
    if len(selectors) != 1:
        raise AtlasPackError(f"{context} must contain exactly one of frame, cell, or rect")

    selector = selectors[0]
    if selector == "frame":
        if layout.get("mode") != "auto-strip" or frames is None:
            raise AtlasPackError(f"{context}.frame requires an auto-strip atlas")
        index = _integer(target["frame"], f"{context}.frame")
        if index >= len(frames):
            raise AtlasPackError(
                f"{context}.frame is {index}, but the atlas contains {len(frames)} frames"
            )
        return frames[index]

    values = _array(target[selector], f"{context}.{selector}")
    if selector == "cell":
        if layout.get("mode") != "grid":
            raise AtlasPackError(f"{context}.cell requires a grid atlas")
        if len(values) != 2:
            raise AtlasPackError(f"{context}.cell must be [column, row]")
        col = _integer(values[0], f"{context}.cell[0]")
        row = _integer(values[1], f"{context}.cell[1]")
        cell_px = _integer(layout.get("cellPx"), "layout.cellPx", minimum=1)
        return Rect(col * cell_px, row * cell_px, cell_px, cell_px)

    if len(values) != 4:
        raise AtlasPackError(f"{context}.rect must be [x, y, width, height]")
    return Rect(
        _integer(values[0], f"{context}.rect[0]"),
        _integer(values[1], f"{context}.rect[1]"),
        _integer(values[2], f"{context}.rect[2]", minimum=1),
        _integer(values[3], f"{context}.rect[3]", minimum=1),
    )


def _wrapped_material(material: Image.Image, guard: int) -> Image.Image:
    if guard == 0:
        return material.copy()
    rgba = np.asarray(material.convert("RGBA"))
    guarded = np.pad(rgba, ((guard, guard), (guard, guard), (0, 0)), mode="wrap")
    return Image.fromarray(guarded, "RGBA")


def _prepare_atlas(
    atlas_spec: dict[str, Any],
    manifest_dir: Path,
    atlas_index: int,
) -> PreparedAtlas:
    context = f"atlases[{atlas_index}]"
    atlas_id = _string(atlas_spec.get("id"), f"{context}.id")
    base_path = _resolve(manifest_dir, atlas_spec.get("base"), f"{context}.base")
    output_path = _resolve(manifest_dir, atlas_spec.get("output"), f"{context}.output")
    if base_path.suffix.lower() != ".png" or output_path.suffix.lower() != ".png":
        raise AtlasPackError(f"{context} base and output must be PNG files")
    if not base_path.is_file():
        raise AtlasPackError(f"{context}.base does not exist: {base_path}")

    with Image.open(base_path) as loaded:
        atlas = loaded.convert("RGBA")

    layout = _object(atlas_spec.get("layout"), f"{context}.layout")
    mode = _string(layout.get("mode"), f"{context}.layout.mode")
    frames: list[Rect] | None = None
    if mode == "auto-strip":
        threshold = _integer(
            layout.get("alphaThreshold", 16),
            f"{context}.layout.alphaThreshold",
            minimum=1,
        )
        min_gap = _integer(
            layout.get("minGap", 4),
            f"{context}.layout.minGap",
            minimum=1,
        )
        frames = _auto_strip_frames(atlas, threshold, min_gap)
        if "expectedFrames" in layout:
            expected_frames = _integer(
                layout["expectedFrames"],
                f"{context}.layout.expectedFrames",
                minimum=1,
            )
            if len(frames) != expected_frames:
                raise AtlasPackError(
                    f"{context}: auto-strip detected {len(frames)} frames; "
                    f"expected {expected_frames}"
                )
    elif mode == "grid":
        cell_px = _integer(layout.get("cellPx"), f"{context}.layout.cellPx", minimum=1)
        if atlas.width % cell_px or atlas.height % cell_px:
            raise AtlasPackError(
                f"{context}: atlas {atlas.size} is not divisible by cellPx {cell_px}"
            )
    else:
        raise AtlasPackError(f"{context}.layout.mode must be auto-strip or grid")

    occupied: list[tuple[Rect, str]] = []
    material_ids: set[str] = set()
    placements = 0
    material_values = _array(atlas_spec.get("materials"), f"{context}.materials")
    if not material_values:
        raise AtlasPackError(f"{context}.materials must not be empty")
    for material_index, value in enumerate(material_values):
        material_context = f"{context}.materials[{material_index}]"
        material_spec = _object(value, material_context)
        material_id = _string(material_spec.get("id"), f"{material_context}.id")
        if material_id in material_ids:
            raise AtlasPackError(f"{context} contains duplicate material id {material_id!r}")
        material_ids.add(material_id)

        source_path = _resolve(
            manifest_dir,
            material_spec.get("source"),
            f"{material_context}.source",
        )
        if not source_path.is_file():
            raise AtlasPackError(f"{material_context}.source does not exist: {source_path}")
        guard = _integer(material_spec.get("guardPx", 0), f"{material_context}.guardPx")
        require_opaque = material_spec.get("requireOpaque", False)
        if not isinstance(require_opaque, bool):
            raise AtlasPackError(f"{material_context}.requireOpaque must be boolean")

        with Image.open(source_path) as loaded:
            material = loaded.convert("RGBA")
        if require_opaque and np.any(np.asarray(material.getchannel("A")) != 255):
            raise AtlasPackError(f"{material_context}.source must be fully opaque")
        packed_material = _wrapped_material(material, guard)

        targets = _array(material_spec.get("targets"), f"{material_context}.targets")
        if not targets:
            raise AtlasPackError(f"{material_context}.targets must not be empty")
        for target_index, target_value in enumerate(targets):
            target_context = f"{material_context}.targets[{target_index}]"
            target = _object(target_value, target_context)
            rect = _target_rect(target, target_context, layout, frames)
            if rect.right > atlas.width or rect.bottom > atlas.height:
                raise AtlasPackError(
                    f"{target_context} resolves outside atlas {atlas.size}: {rect}"
                )
            if rect.width <= guard * 2 or rect.height <= guard * 2:
                raise AtlasPackError(f"{target_context} is too small for guardPx {guard}")
            if packed_material.size != (rect.width, rect.height):
                expected = (rect.width - guard * 2, rect.height - guard * 2)
                raise AtlasPackError(
                    f"{material_context}.source is {material.size}; {target_context} "
                    f"requires {expected} after its {guard}px guard"
                )
            for previous, previous_context in occupied:
                if rect.overlaps(previous):
                    raise AtlasPackError(
                        f"{target_context} overlaps {previous_context}: {rect} vs {previous}"
                    )
            occupied.append((rect, target_context))
            atlas.paste(packed_material, (rect.x, rect.y))
            placements += 1

    return PreparedAtlas(atlas_id, output_path, atlas, placements)


def pack_manifest(manifest_path: Path, check_only: bool = False) -> list[PreparedAtlas]:
    """Validate and pack every atlas in ``manifest_path``.

    All atlases are prepared in memory before any output is replaced. When
    ``check_only`` is true, the same validation runs without writing files.
    """
    manifest_path = manifest_path.resolve()
    try:
        manifest = _object(json.loads(manifest_path.read_text(encoding="utf-8")), "manifest")
    except (OSError, json.JSONDecodeError) as exc:
        raise AtlasPackError(f"cannot read manifest {manifest_path}: {exc}") from exc
    version = _integer(manifest.get("version"), "manifest.version", minimum=1)
    if version != 1:
        raise AtlasPackError(f"unsupported manifest version {version}; expected 1")

    atlas_values = _array(manifest.get("atlases"), "manifest.atlases")
    if not atlas_values:
        raise AtlasPackError("manifest.atlases must not be empty")
    prepared = [
        _prepare_atlas(_object(value, f"atlases[{index}]"), manifest_path.parent, index)
        for index, value in enumerate(atlas_values)
    ]
    outputs: set[Path] = set()
    for atlas in prepared:
        resolved = atlas.output.resolve()
        if resolved in outputs:
            raise AtlasPackError(f"multiple atlas entries write {resolved}")
        outputs.add(resolved)

    if check_only:
        return prepared

    temporary_outputs: list[tuple[Path, Path]] = []
    try:
        for atlas in prepared:
            atlas.output.parent.mkdir(parents=True, exist_ok=True)
            with tempfile.NamedTemporaryFile(
                prefix=f".{atlas.output.stem}-",
                suffix=".png",
                dir=atlas.output.parent,
                delete=False,
            ) as handle:
                temporary = Path(handle.name)
            atlas.image.save(temporary, "PNG")
            temporary_outputs.append((temporary, atlas.output))
        for temporary, output in temporary_outputs:
            os.replace(temporary, output)
    finally:
        for temporary, _ in temporary_outputs:
            if temporary.exists():
                temporary.unlink()
    return prepared


def _parse_size(value: str) -> tuple[int, int]:
    normalized = value.lower().replace("×", "x")
    parts = normalized.split("x", maxsplit=1)
    try:
        width = int(parts[0])
        height = int(parts[1]) if len(parts) == 2 else width
    except ValueError as exc:
        raise argparse.ArgumentTypeError("size must be N or WIDTHxHEIGHT") from exc
    if width < 1 or height < 1:
        raise argparse.ArgumentTypeError("size dimensions must be positive")
    return width, height


def import_tileable(
    source: Path,
    output: Path,
    size: tuple[int, int],
    ffmpeg: str = "ffmpeg",
    force: bool = False,
) -> None:
    """Use FFmpeg to periodically downsample a tileable texture."""
    source = source.resolve()
    output = output.resolve()
    if not source.is_file():
        raise AtlasPackError(f"source texture does not exist: {source}")
    if source == output:
        raise AtlasPackError("source and output must be different files")
    if output.exists() and not force:
        raise AtlasPackError(f"output already exists (pass --force to replace it): {output}")
    output.parent.mkdir(parents=True, exist_ok=True)

    width, height = size
    filter_graph = (
        "split=9[a][b][c][d][e][f][g][h][i];"
        "[a][b][c]hstack=3[r0];"
        "[d][e][f]hstack=3[r1];"
        "[g][h][i]hstack=3[r2];"
        "[r0][r1][r2]vstack=3,"
        f"scale={width * 3}:{height * 3}:flags=lanczos,"
        f"crop={width}:{height}:{width}:{height},format=rgba"
    )
    with tempfile.NamedTemporaryFile(
        prefix=f".{output.stem}-",
        suffix=".png",
        dir=output.parent,
        delete=False,
    ) as handle:
        temporary = Path(handle.name)
    temporary.unlink()
    command = [
        ffmpeg,
        "-hide_banner",
        "-loglevel",
        "error",
        "-y",
        "-i",
        str(source),
        "-filter_complex",
        filter_graph,
        "-frames:v",
        "1",
        str(temporary),
    ]
    try:
        subprocess.run(command, check=True)
        with Image.open(temporary) as imported:
            if imported.size != size:
                raise AtlasPackError(
                    f"FFmpeg wrote {imported.size}, expected {size}: {temporary}"
                )
        os.replace(temporary, output)
    except (OSError, subprocess.CalledProcessError) as exc:
        raise AtlasPackError(f"FFmpeg import failed for {source}: {exc}") from exc
    finally:
        if temporary.exists():
            temporary.unlink()


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)

    pack = commands.add_parser("pack", help="pack the atlases in a JSON manifest")
    pack.add_argument("manifest", type=Path)
    pack.add_argument("--check", action="store_true", help="validate without writing")

    importer = commands.add_parser(
        "import-tileable",
        help="wrap-aware FFmpeg downsample into a small individual material PNG",
    )
    importer.add_argument("source", type=Path)
    importer.add_argument("output", type=Path)
    importer.add_argument("--size", required=True, type=_parse_size)
    importer.add_argument("--ffmpeg", default="ffmpeg")
    importer.add_argument("--force", action="store_true")
    return parser


def main() -> None:
    args = _parser().parse_args()
    if args.command == "pack":
        prepared = pack_manifest(args.manifest, check_only=args.check)
        action = "validated" if args.check else "packed"
        for atlas in prepared:
            print(f"{action} {atlas.atlas_id}: {atlas.placements} placement(s) -> {atlas.output}")
        return
    import_tileable(args.source, args.output, args.size, args.ffmpeg, args.force)
    print(f"imported tileable material -> {args.output}")


if __name__ == "__main__":
    main()
