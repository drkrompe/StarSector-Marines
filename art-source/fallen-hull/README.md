# Fallen hull art

The Long Survey's looks (`fallen-hull-nouns.md`, `long-survey-arc.md`; its
`stories.md` row "Hull art"): the crashed survey mothership from above, its
trench and debris field, the autoforge, the survey stake in its stages, and the
stripper and hauler walkers. Geometry-first (`geometry-first-imagegen.md`):
`make_guide.py` draws every sheet's flat-colour guide, `generate.py` paints each
once through `tools/codex_imagegen.py` with the vanilla derelict ship sprites and
the accepted derelict-security and walker art as references, and `export.py`
cuts each piece at its guide frame.

```
python art-source/fallen-hull/make_guide.py         # guides/*-guide.png, guides/layout.json
python art-source/fallen-hull/generate.py <job>     # prompts/<job>.prompt.txt, raw/<job>.png (+ provenance)
python art-source/fallen-hull/export.py [--root mod] # <root>/graphics/battle/fallen-hull/
gradlew.bat createSnapshots -Psnapshot=fallen-hull-look   # the picture cards
```

| Sheet | Pieces | Footprint | Shipped density |
| --- | --- | --- | --- |
| `hull-survey`, `hull-carrier`, `hull-contingency` | the whole hull, bow east, its snapped stern apart (pick `hull-lineage`) | 64 x 21.3 cells frame | 32 px a cell |
| `forge` | autoforge `crucible`, `gantry`, `ring`, each `lit` and `out` | 6 x 6 cells | 96 |
| `stake` | stake `spire` and `tripod`, each `planted` (3.2 cells), `raised` (4.8: node mast and cradle) and the established `store` (3.2) | square frames | 128 |
| `debris` | four hull-plating sections (flat, vents, breached bay, torn edge), two loose plates, girder, pod, engine bell, mast, scrap heap | 1x1 to 8x2 cells | 64 |
| `trench` | seamless 2x2 trench floor, the lip strip, scorch / furrow / glassed patches | | 64 |
| `stripper`, `hauler`, `rig` | walker masters on the derelict walker's layout: torso, thigh, shin, joint and the assembled walker (`rig` is pick `stripper-hauler` b, one unit) | the composer's | 128 |

The hull's roof plating is not here: it is the `hull` roof style
(`art-source/roofs`, `RoofStyle.HULL`), a row of `mod/graphics/tilesets/roofs.png`
drawn over any building whose walls carry a surface id naming `fallen-hull`.

Until the hull, its stakes and its salvage walkers are places and units in a
battle, the cut art goes to `build/fallen-hull-art` and is carded by
`FallenHullLookSuite` over real look-preset frames (the civilian-rescue town,
the vast derelict ruin beside its real walkers). Shipping a look is
`export.py --root mod` and a `ModTextures` read in the place's or unit's
renderer.
