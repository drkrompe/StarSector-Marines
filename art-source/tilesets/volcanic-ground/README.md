# Volcanic ground art

A lava world's ground (`SurfacePalette.VOLCANIC`, `world-surface-palette.md`). Four ImageGen masters, made through
`tools/codex_imagegen.py` with the prompts beside them (`*.prompt.txt`, provenance in `*.png.provenance.json`):

- `basalt-flow.png`, `volcanic-ash.png`: square overhead material pictures. Eight 448 px crops of each were wrapped
  into 56 px tiles by `../normalize_ground_variants.py` (`--band 14`; basalt `--scale 2 --mean 82,76,74`, ash
  `--scale 4 --mean 128,124,121`) into `../atlas-material-source/basalt-variants/` and `ash-variants/`, then laid in
  `../volcanic-ground.raw.png` (row 0 basalt, row 1 ash) and exported with
  `'{"name":"volcanic-ground"}' | tools/authoring.cmd tileset_export -` as `volcanic.basalt` and `volcanic.ash`.
- `lava-fissures.png`, `ash-drifts.png`: light on black, cut by `derive_overlays.py` into
  `mod/graphics/battle/volcanic/` and drawn additively by `VolcanicGroundRenderSystem`.

Derived from these and the lava rift's basalt wall, each by its own script: `../basalt-terrain/derive.py` (the hill
finish, `terrain.basalt.*`) and `../volcanic-formations/derive.py` (basalt formations), each exported the same way.
