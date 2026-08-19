# Doodad atlas builder

This folder turns individually generated transparent PNG cutouts into one
runtime-sized atlas and its matching tileset JSON.

The generated asset families' shared prompt recipes, including the transparent
military-compound set, are recorded in `IMAGEGEN-PROMPTS.md`.

## Basic use

1. Put cleaned transparent PNGs in `sources/`.
2. Name them with stable descriptive names such as `sandbag-straight-n.png` or
   `industrial-cable-reel.png`.
3. Run from the repository root:

   ```powershell
   python mod/graphics/doodads/stitch_atlas.py
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
