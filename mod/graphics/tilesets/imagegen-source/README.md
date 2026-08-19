# ImageGen tileset source

These are the raw ImageGen style-transfer sources and normalization tools used
to build the shipped tilesets in the parent directory. The raw images are not
drop-in runtime assets: each is RGB, uses near-black in place of transparency,
and has a model-selected canvas size.

The normalized production atlases use the canonical runtime names. The retired
pre-ImageGen atlases and temporary `*-imagegen.png` candidates are no longer
kept alongside them.

`normalize_tilesets.py` converts the general raw sources into exact-size RGBA
runtime atlases in the parent directory. The spaceport road sheet uses its own
panel-extraction script.

Regenerate the canonical atlases with:

```powershell
python mod\graphics\tilesets\imagegen-source\normalize_tilesets.py
python mod\graphics\tilesets\imagegen-source\normalize_spaceport_apron.py
.\gradlew.bat :asset-pipeline:deriveTileMaps
```

## Outputs

| Runtime atlas | Raw source | Initial topology check |
| --- | --- | --- |
| `urban-tileset.png` | `urban-tileset.raw.png` (1254x1254 RGB) | Strong whole-sheet preservation; 2 originally empty cells contain spillover |
| `urban-tileset-2.png` | `urban-tileset-2-spaceport-apron.raw.png` (1254x1254 RGB) | The approved 3x3 spaceport apron is extracted panel-by-panel into the road atlas |
| `urban-tileset-3.png` | `urban-tileset-3.raw.png` (2166x726 RGB) | All 7 auto-sliced frames retained in order |
| `Floors_Tiles.png` | `Floors_Tiles.raw.png` (1225x1284 RGB) | Material families retained; most topology drift and blank-cell pollution |
| `Water_tiles.png` | `Water_tiles.raw.png` (1254x1254 RGB) | Strong macro-layout preservation; some edge spill into empty cells |
| `nature-tiles.png` | `nature-tiles.raw.png` (2172x724 RGB) | All 20 auto-sliced frames retained in order |

## Shared prompt frame

All sources used built-in ImageGen in `style-transfer` mode. The edit target
was always Image 1. The approved `urban-tileset` production art and/or marine
sprites were supplied only as style references.

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

The scripts preserve the runtime canvas dimensions and alpha topology. For
fixed-grid sheets, normalization fits generated content into the current
production content bounds and restores its alpha mask exactly. For auto-strips,
it detects generated frames, fits them to the production frame bounding boxes,
and restores the inter-frame gaps before the existing slicer runs.

The current normalized pass uses whole-content fitting for fixed-grid sheets and per-frame fitting for auto-strips. After fitting, it removes ImageGen's dark isolated-sprite outline from repeating ground fields by mirroring a narrow band of neighboring interior rows and columns through each tile edge. This cleanup is intentionally limited to:

- the reusable brick, grass, stone, dirt, sand, and water cells on the 16px fixed grids;
- the first four ground frames on `urban-tileset-3`;
- the first seven ground frames on `nature-tiles`.

Walls, transition autotiles, and overlays retain their authored edge contrast because those boundaries communicate topology. A future segmented regeneration can replace an individual material family without changing runtime paths.
