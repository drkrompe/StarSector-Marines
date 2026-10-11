# Doodad atlas builder

This folder turns individually generated transparent PNG cutouts into one
runtime-sized atlas and its matching tileset JSON.

The generated asset families' shared prompt recipes, including the transparent
military-compound set, are recorded in `IMAGEGEN-PROMPTS.md`.

## Basic use

1. Put cleaned transparent PNGs in `art-source/doodads/sources/`.
2. Name them with stable descriptive names such as `sandbag-straight-n.png` or
   `industrial-cable-reel.png`.
3. Run from the repository root:

   ```powershell
   python art-source/doodads/stitch_atlas.py
   ```

The default build produces:

- `mod/graphics/doodads/doodads.png`
- `mod/data/tilesets/doodads.tileset.json`

Files sort alphabetically unless `_atlas.json` gives them an `order`. Reordering
is safe because the generated manifest moves each stable doodad id to its new
`col`/`row` automatically.

## Image contract

- Each source may be any pixel size or aspect ratio.
- Sources must have a useful alpha channel. The build rejects fully opaque raw
  ImageGen canvases so matte or chroma-key backgrounds do not leak into game
  art. Use `--allow-opaque` only for deliberately opaque assets.
- Visible pixels are cropped, proportionally fitted, and centered in the
  authored cell footprint with 2px outer padding by default. Assets without a
  footprint remain 32x32.
- Lanczos resampling is the default for high-resolution ImageGen cutouts. Use
  `--resample nearest` for already-authored pixel art.

## Metadata

`sources/_atlas.json` contains global defaults and optional per-file overrides.
An asset can set:

- `id`: stable registry id; otherwise derived as `doodad.<filename>`
- `cover`: `none`, `light`, `med`, or `heavy`
- `name` and `description`: labels for the tileset viewer
- `order`: explicit numeric ordering before alphabetic fallback
- `preserveCanvas`: keep the full source canvas instead of trimming alpha bounds;
  use for geometry-authored pieces whose empty space and endpoints are meaningful
- `padding`: transparent inset within the cell
- `scale`: multiplier in `(0, 1]` after fitting
- `offset`: final `[x, y]` pixel adjustment
- `footprintCells`: rendered and tactical `[width, height]` cell span; defaults
  to `[1, 1]`
- `preferredWallSide`: optional authored edge (`N`, `S`, `E`, or `W`) that
  naturally belongs against a wall, such as a sofa back or bed head
- `enabled`: set to `false` to leave a source out of the atlas

Example:

```json
{
  "defaults": { "cover": "none", "padding": 2 },
  "assets": {
    "residential-sofa-h.png": {
      "id": "doodad.residential-sofa-h",
      "cover": "med",
      "footprintCells": [2, 1],
      "preferredWallSide": "N",
      "order": 72,
      "offset": [0, 0]
    }
  }
}
```

Useful overrides include `--columns`, `--cell-size`, `--padding`,
`--output-image`, `--output-manifest`, and `--sheet-path`. Run with `--help` for
the complete command line.

The emitted JSON already matches the `TileRegistry` doodad schema. Adding a new
standalone sheet to runtime still requires registering the generated tileset and
loading its texture; the atlas coordinates themselves require no hand editing.
Layouts may use `preferredWallSide` to select a matching cardinal variant; it
does not force generic scatterers to wall-mount the prop.

## Geometry-authored sandbags

`SandbagReskinTemplate` draws two staggered courses of rounded bags: a north
straight and a northwest elbow. Run `./gradlew.bat createSnapshots -Psnapshot=sandbags`
to regenerate its guide and review closed rings, open bays and all four rotations
through the production prop renderer. The retained guide is `../templates/sandbags.png`.

The exact built-in ImageGen edit prompt is `sandbags.prompt.txt`; the untouched
accepted output is `imagegen-raw/sandbags-geometry.raw.png`. ImageGen changed the
canvas size, so `sandbags-geometry.json` records reviewed square cell cuts in
that output's coordinates. These include the empty interior, not just visible
bag bounds. `derive_sandbag_frames.py` cuts those cells and makes the other six
facings with exact quarter rotations. No content recentering or per-piece scaling
is allowed: all eight metadata entries use `preserveCanvas: true`, `padding: 0`.

Rebuild in order:

```powershell
python art-source/doodads/derive_sandbag_frames.py
python art-source/doodads/stitch_atlas.py --only-prefix doodad.sandbag-
python -m unittest discover -s art-source/doodads -p 'test_*.py'
```

The old individually generated masters remain historical references. Current
sandbags retain their eight registry IDs, atlas slots, heavy cover and 0.3-cell
ballistic half-height. Their silhouettes control the art; collision and cover
still use the existing authored cells.

The prefix-limited rebuild replaces complete sandbag cells in the existing atlas
and requires an unchanged manifest. It preserves unrelated shipped props whose
sources may have drifted (the current medical privacy screen is one such case).
Use a full build when adding IDs or changing layout metadata.

## Connected industrial fences

`FenceReskinTemplate` draws one connected overhead frame. Built-in ImageGen
reskins it using `industrial-fence.prompt.txt`; the untouched accepted output
is `imagegen-raw/industrial-fence-geometry.raw.png`. The generated canvas is
1254px square, with the top and left rail centerlines near 314px and the
top middle post near 628px. `industrial-fence-geometry.json` records the
reviewed full 314px square cuts for one northwest elbow and one horizontal run.
`derive_industrial_fence_frames.py` derives all other facings by quarter turns.

All six pieces meet at the cell edge's midpoint. Keep `preserveCanvas: true`,
`padding: 0`, and `scale: 1`: alpha trimming, centering the visible bounds,
or separately fitting corners and straights breaks the assembly. The earlier
two unrelated masters remain historical references only. Registry ids, atlas
slots, light cover and ballistic height stay unchanged.

```powershell
python art-source/doodads/derive_industrial_fence_frames.py
python art-source/doodads/stitch_atlas.py --only-prefix doodad.industrial-fence-
python -m unittest discover -s art-source/doodads -p 'test_*.py'
./gradlew.bat createSnapshots -Psnapshot=fences
```

The `fences` snapshot suite publishes the guide and production-rendered closed
enclosures, a two-cell gate gap, and all four rotated elbows at 32px and 48px
per cell. Pixel tests check both rails at every valid pairing of cell edges.

### Fence damage and debris

`FenceDamageReskinTemplate` retains two rows of full cells: bent straight/elbow,
then broken straight/elbow. The built-in ImageGen reskin uses the intact frame
as its material reference. Keep `industrial-fence-damage.prompt.txt`,
`imagegen-raw/industrial-fence-damage.raw.png`, and the reviewed rectangles in
`industrial-fence-damage-geometry.json` together. The generated 1254px plate
shifted the four shapes; these cuts register their rail endpoints, including
empty space, rather than trimming alpha bounds. They normalize to canonical
square cells before exact quarter turns. Pixel tests check the shared rail
positions and the transparent center of each destroyed piece.

```powershell
python art-source/doodads/derive_fence_damage_frames.py
python -m unittest discover -s art-source/doodads -p 'test_*.py'
./gradlew.bat createSnapshots -Psnapshot=fences
```

The derivation writes `fence-damage-sources/` plus its metadata, then calls the
ordinary doodad packer to produce `mod/graphics/doodads/fence-damage.png` and
its tileset. It never repacks the intact atlas. The suite now also shows
alternating intact/damaged pieces and actual breached cells, preserving the
ground under the debris. Runtime cell-blocking fences start at 100 structural
HP unless their cell already has authored HP. Positive wall damage selects
the bent art; exhausted HP selects zero-cover debris and opens navigation.
