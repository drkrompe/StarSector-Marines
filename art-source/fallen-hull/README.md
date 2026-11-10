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
python art-source/fallen-hull/export.py             # every candidate, build/fallen-hull-art (the cards)
python art-source/fallen-hull/export.py --ship      # the picked looks into mod/
python art-source/fallen-hull/tileset.py            # the map tileset: hull walls, trench floor, debris
python art-source/fallen-hull/hull_roof.py          # the hull's roof: the picked lineage laid over the plan
gradlew.bat createSnapshots -Psnapshot=fallen-hull-look      # the picture cards
gradlew.bat createSnapshots -Psnapshot=long-survey-fall-look # The Fall played, the looks in place
```

| Sheet | Pieces | Footprint | Shipped density |
| --- | --- | --- | --- |
| `hull-survey`, `hull-carrier`, `hull-contingency` | the whole hull, bow east, its snapped stern apart (pick `hull-lineage`) | 64 x 21.3 cells frame | 32 px a cell |
| `forge` | autoforge `crucible`, `gantry`, `ring`, each `lit` and `out` | 6 x 6 cells | 96 |
| `stake` | stake `spire` and `tripod`, each `planted` (3.2 cells), `raised` (4.8: node mast and cradle) and the established `store` (3.2) | square frames | 128 |
| `debris` | four hull-plating sections (flat, vents, breached bay, torn edge), two loose plates, girder, pod, engine bell, mast, scrap heap | 1x1 to 8x2 cells | 64 |
| `trench` | seamless 2x2 trench floor, the lip strip, scorch / furrow / glassed patches | | 64 |
| `stripper`, `hauler`, `rig` | walker masters on the derelict walker's layout: torso, thigh, shin, joint and the assembled walker (`rig` is pick `stripper-hauler` b, one unit) | the composer's | 128 |

The hull's roof is the ship: `hull_roof.py` stretches the picked lineage's body
(`raw/hull-survey.png`) over the plan's 88 x 32 cells into
`mod/graphics/battle/fallen-hull/hull-roof.png`, and `HullRoof` cuts each roofed
cell of the hull out of it, turned to the hull's heading, in place of the roof
style's repeating field (damage still draws from the roof sheet). The `hull`
roof style (`art-source/roofs`, `RoofStyle.HULL`; its first painting is kept as
`raw/hull-v1`) is what any other building with `fallen-hull` walls wears, and
the 2-cell field the hull's wall caps show where its roof is lifted.

What ships, on each pick's recommended option until the owner answers:

- `tileset.py` cuts the map tileset `fallen-hull` from the accepted paintings:
  the hull's wall block `installation.dark-concrete-wall@fallen-hull` (walls
  and roof one material; `CrashSiteStage.HULL_WALL`), the trench floor
  `fallen-hull.trench` and the debris the hull shed on it
  (`fallen-hull.debris-*`, `CrashSiteStage.SHED`).
- `export.py --ship` cuts the ring forge (lit and put out), the spire stake's
  three stages and the stripper's and hauler's parts. `FallenHullLook` draws
  the forge and the stakes in the structure pass; `BattleSprites` gives the
  stripper and hauler chassis their own parts on the derelict leg rig.

`FallenHullLookSuite` cards every candidate over real look-preset frames;
`LongSurveyFallLookSuite` launches The Fall as the debug player does and looks
at the shipped pieces in place.
