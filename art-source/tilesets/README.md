# ImageGen tileset source

These are the raw ImageGen style-transfer sources and normalization tools used
to build the shipped tilesets in the parent directory. The raw images are not
drop-in runtime assets: an unkeyed one is RGB, uses near-black in place of
transparency, and has a model-selected canvas size.

Two producers exist and only one may own a sheet. `normalize_tilesets.py`
transfers fresh colour onto the alpha topology of the atlas it overwrites, so
the shipped file is its own input; that is tolerable only while the raw plate is
opaque and has therefore recorded nothing about what is background and what is
art. A raw sheet that has been keyed (see `key_background.py`) has recorded
exactly that, per cell, at the source, and its atlas is exported from its
`*.tileset-authoring.json` through the tileset authoring exporter instead.
Pointing the normalize script at a keyed sheet would rebuild it from the alpha
of the export it is replacing and silently revert it. It refuses rather than
doing so, and `KeyedSheetsAreExportedNotNormalizedTest` fails the build for a
re-add before anyone runs it. Neither guard is a list to keep in step: keying
the plate is itself the withdrawal, and both simply measure it.

The normalized production atlases use the canonical runtime names. The retired
pre-ImageGen atlases and temporary `*-imagegen.png` candidates are no longer
kept alongside them.

`normalize_tilesets.py` converts the general raw sources into exact-size RGBA
runtime atlases in the parent directory, then invokes the manifest-driven
`pack_texture_atlas.py` to place individual material textures. The spaceport
road sheet uses its own panel-extraction script.

Regenerate the canonical atlases with:

```powershell
python art-source\tilesets\normalize_tilesets.py
python art-source\tilesets\normalize_spaceport_apron.py
.\gradlew.bat :asset-pipeline:deriveTileMaps
```

`atlas-material-source/` contains the small checked-in authoring inputs for the
frames whose picture is a surface rather than drawn art. They are wrap-aware
FFmpeg downsamples of selected Game Buffs 4K materials: 52x52 grass and dirt
plus 45x47 sand for `nature-tiles`, and 52x52 sand and stone for
`Floors_Tiles`. Every hash-selected variant in a material pool uses the same
imported source so unlike variants cannot expose a join.

Which producer reads them differs by sheet, and that is the distinction to keep
straight. `nature-tiles` names its materials on its own authoring entries, so
its export places them and nothing runs afterwards. `Floors_Tiles` is still a
fixed-grid sheet produced by `normalize_tilesets.py`, so its materials are still
pasted in by `texture-atlases.json` at `cell` addresses. A sheet must never be
claimed by both: `KeyedSheetsAreExportedNotNormalizedTest` fails the build if a
keyed sheet's atlas is named in the manifest.

## Individual-material atlas packer

The packer supports auto-sliced strips, fixed grids, and explicit rectangles.
Each material can target multiple frames or cells. `guardPx` wraps the opposite
material edge into the atlas border, matching the renderer's source inset
without reintroducing a seam. It validates source dimensions, opacity when
requested, atlas bounds, duplicate ids, overlapping targets, and every output
before atomically replacing any atlas.

Validate or repack directly with:

```powershell
python art-source\tilesets\pack_texture_atlas.py pack `
  art-source\tilesets\texture-atlases.json --check
python art-source\tilesets\pack_texture_atlas.py pack `
  art-source\tilesets\texture-atlases.json
```

Import a large tileable source without ever checking in or visually loading the
4K original. The importer tiles it 3x3 in FFmpeg, downsamples the whole periodic
field with Lanczos, and crops the center tile so the resize filter sees wrapped
neighbors instead of clamped image edges:

```powershell
python art-source\tilesets\pack_texture_atlas.py import-tileable `
  "C:\path\to\Sand_Albedo.png" `
  art-source\tilesets\atlas-material-source\new-pack\sand.png `
  --size 52
```

For the 56px `Floors_Tiles` ground pools, a 2px runtime guard means each
checked-in material is 52x52. The manifest packs `Beach_Sand_Dry_1_Albedo.png`
into sand cells `[6, 14]`, `[7, 14]`, and `[8, 14]`, and
`Gravel_11_Albedo.png` into stone cells `[6, 10]`, `[7, 10]`, and `[8, 10]`.
The nature strip's irregular 49x51 sand frame uses a 45x47 import plus the same
2px guard. Rectangular imports use `--size WIDTHxHEIGHT`, such as
`--size 45x47`.

Run the packer tests with:

```powershell
python -m unittest discover `
  -s art-source\tilesets\tests `
  -p "test_*.py"
```

## Outputs

| Runtime atlas | Raw source | Initial topology check |
| --- | --- | --- |
| `urban-tileset.png` | `urban-tileset.raw.png` (1254x1254 RGBA, keyed) | Exported from `urban-tileset.tileset-authoring.json`; withdrawn from `normalize_tilesets.py` |
| `urban-tileset-2.png` | `urban-tileset-2-spaceport-apron.raw.png` (1254x1254 RGB) | The approved 3x3 spaceport apron is extracted panel-by-panel into the road atlas |
| `urban-tileset-3.png` | `urban-tileset-3.raw.png` (2166x726 RGBA, keyed) | Exported from `urban-tileset-3.tileset-authoring.json` as an auto-strip; withdrawn from `normalize_tilesets.py` |
| `Floors_Tiles.png` | `Floors_Tiles.raw.png` (1225x1284 RGB) + `atlas-material-source/` (52x52 RGBA) | 25x26 topology retained at 56px per cell; sand and stone fields use manifest-packed seamless materials |
| `Water_tiles.png` | `Water_tiles.raw.png` (1254x1254 RGB) | Strong macro-layout preservation; some edge spill into empty cells |
| `nature-tiles.png` | `nature-tiles.raw.png` (2172x724 RGBA, keyed) + `atlas-material-source/` (52x52 and 45x47 RGBA) | Exported from `nature-tiles.tileset-authoring.json` as an auto-strip; withdrawn from `normalize_tilesets.py`. Its five field frames declare a `material` and are exported at the material's own size plus the renderer's ground inset wrapped round it, so the sheet has one producer. See `nature-tiles-material-provenance.md` |

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
- `nature-tiles`: preserve exactly 20 auto-sliced sprites in order, with at least 4 transparent pixels between frames. The prompt said "7 ground, 5 plants, 3 small-rock groups, 2 medium, 3 large"; the art came back as 7 ground, **6** plants and **7** rock groups. The ids followed the prompt rather than the art until 2026-08-29, so frame 12 — a tall grass tuft — was named `nature.rock-small-1` and scattered as a rock; it is now `nature.tuft-3` and the small rocks are numbered from frame 13.

## Normalization strategy

The scripts preserve the runtime canvas dimensions and alpha topology:
normalization fits generated content into the current production content bounds
and restores its alpha mask exactly. Only fixed-grid sheets are produced this
way now — `Floors_Tiles` and `Water_tiles`.

The current normalized pass fits whole content into the production bounds. After
fitting, it removes ImageGen's dark isolated-sprite outline from the reusable
brick, grass, stone, dirt, sand, and water cells on the 16px fixed grids, by
mirroring a narrow band of neighbouring interior rows and columns through each
tile edge.

The auto-strip half of that pass is gone. It carried twenty pinned frame boxes
into `nature-tiles.png` and a ground-edge band beside them — coordinates into an
atlas held outside the tileset that describes it — and withdrawing the sheet
took all of it. The same treatment now lives in the exporter as
`spriteBorderPx`, authored per piece on the authoring document rather than
keyed by sheet name in a script.

Walls, transition autotiles, and overlays retain their authored edge contrast because those boundaries communicate topology. A future segmented regeneration can replace an individual material family without changing runtime paths.
