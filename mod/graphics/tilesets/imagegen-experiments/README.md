# ImageGen tileset experiments

These are non-destructive raw ImageGen style-transfer attempts. They are not drop-in replacements: every generated PNG is RGB, uses near-black in place of transparency, and has a model-selected canvas size.

The original atlases in the parent directory are unchanged.

`normalize_tilesets.py` converts the raw attempts into exact-size RGBA runtime candidates in the parent directory. The manifests and `TileManifest` currently point at these normalized `*-imagegen.png` candidates.

Run the normalization again with:

```powershell
python mod\graphics\tilesets\imagegen-experiments\normalize_tilesets.py
```

## Outputs

| Output | Original | Raw result | Initial topology check |
| --- | --- | --- | --- |
| `urban-tileset.raw.png` | 320x320 RGBA | 1254x1254 RGB | Strong whole-sheet preservation; 2 originally empty cells contain spillover |
| `urban-tileset-2.raw.png` | 544x96 RGBA | 2172x724 RGB | All logical groups retained, but the 17x3 strip was vertically padded and cells drifted |
| `urban-tileset-3.raw.png` | 400x60 RGBA | 2166x726 RGB | All 7 auto-sliced frames retained in order |
| `Floors_Tiles.raw.png` | 400x416 RGBA | 1225x1284 RGB | Material families retained; most topology drift and blank-cell pollution |
| `Water_tiles.raw.png` | 400x400 RGBA | 1254x1254 RGB | Strong macro-layout preservation; some edge spill into empty cells |
| `nature-tiles.raw.png` | 1200x80 RGBA | 2172x724 RGB | All 20 auto-sliced frames retained in order |

## Shared prompt frame

All attempts used built-in ImageGen in `style-transfer` mode. The edit target was always Image 1. The approved `urban-tileset` experiment and/or marine sprites were supplied only as style references.

Shared rendering request:

> Re-skin only the existing artwork into grounded, richly shaded realistic pixel art. Use crisp top-down painted pixel art, deliberate pixel clusters, hard readable edges, believable materials, restrained contrast, and strong contact shading. Change only surface rendering. Preserve every sprite or tile position, silhouette, scale, orientation, order, negative region, and empty cell. Do not move, merge, crop, omit, duplicate, resize, or invent artwork. No text or watermark. Output only the atlas or strip, never a scene or mockup.

Sheet-specific constraints:

- `urban-tileset`: preserve a 10x10 grid of 32px cells, all wall/floor/doodad topology, and transparent regions.
- `urban-tileset-2`: preserve a 17x3 grid of 32px cells, every 3x3 logical block, road edges, center openings, and blank cells.
- `urban-tileset-3`: preserve exactly 7 auto-sliced sprites in their original order and footprints with at least 4 transparent pixels between frames.
- `Floors_Tiles`: preserve a 25x26 grid of 16px cells and all grass, stone, dirt, brick, snow, and sand autotile families and transition directions.
- `Water_tiles`: preserve a 25x25 grid of 16px cells, four top island sprites, the water edge/corner family, center textures, and shoreline topology.
- `nature-tiles`: preserve exactly 20 auto-sliced sprites in order: 7 ground tiles, 5 plants, 3 small-rock groups, 2 medium rocks, and 3 large rocks, with at least 4 transparent pixels between frames.

## Normalization strategy

The script restores the original canvas dimensions and alpha topology non-destructively. For fixed-grid sheets, it fits the generated content into the source content bounds and restores the source alpha mask exactly. For auto-strips, it detects generated frames, fits them to the original frame bounding boxes, and restores the original inter-frame gaps before the existing slicer runs.

The current normalized pass uses whole-content fitting for fixed-grid sheets and per-frame fitting for auto-strips. After fitting, it removes ImageGen's dark isolated-sprite outline from repeating ground fields by mirroring a narrow band of neighboring interior rows and columns through each tile edge. This cleanup is intentionally limited to:

- the reusable brick, grass, stone, dirt, sand, and water cells on the 16px fixed grids;
- the first four ground frames on `urban-tileset-3`;
- the first seven ground frames on `nature-tiles`.

Walls, transition autotiles, and overlays retain their authored edge contrast because those boundaries communicate topology. A future segmented regeneration can replace an individual material family without changing runtime paths.
