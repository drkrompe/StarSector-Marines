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
| `armour` | the hull's outer armour wall (sixteen exposed-face masks, a lip on each open face, a spine along each joined one), the same sixteen torn open (its breach roots), its hatch and viewport in four facings | 1 x 1 cells, 140 px on a 156 px pitch | 64 |
| `fittings` | the hull's own interior dressing: frame rib and conduit runs (three cells drawn, the middle ships), conduit junction, survey console, instrument rack, sensor drum, the bay's launch cradle, the forge's hopper, coolant tanks and cables, breach plating and scatter, two crushed charcoal bulkheads | 1x1 to 4x3 cells | 64 |
| `sheared` | the hull broken into sections: the armour wall sheared through (sixteen masks; each open face the cut cross-section, decks in layers, rib ends, cable), a breach's torn lip and a sheared end's spill as edge runs | 1 x 1 cells, 140 px on a 156 px pitch | 64 |
| `shower` | the debris shower: shards, torn plates, a pod, a hull slab, girders, an engine bell and a sensor mast, burnt; craters, scorch and an impact skid; the interior's wreckage | 1x1 to 5x2 cells | 64 |
| `hull-burnt` | the picked `hull-survey` painting repainted burnt and broken over itself (same registration) | as `hull-survey` | the roof |
| `stripper`, `hauler`, `rig` | walker masters on the derelict walker's layout: torso, thigh, shin, joint and the assembled walker (`rig` is pick `stripper-hauler` b, one unit) | the composer's | 128 |

The hull's roof is the ship: `hull_roof.py` stretches the picked lineage's body,
burnt (`raw/hull-burnt.png`), over the plan's 88 x 32 cells into
`mod/graphics/battle/fallen-hull/hull-roof.png`, and `HullRoof` cuts each roofed
cell of the hull out of it, turned to the hull's heading, in place of the roof
style's repeating field (damage still draws from the roof sheet). The hull broke
up, so it may lie as sections along its trench: `HullRoof` takes each section in
order from the stern as the next run of the plan, sharing out what the breaks
took away. The hull is
entered compartment by compartment: each compartment is its own building, so
its roof lifts alone when it is boarded while the rest still shows the ship.
`HullRoof` holds every roof lying wholly inside the hull's bounds, so a
compartment walled only by `ship.*` bulkheads is cut from the painting too. The
`hull` roof style (`art-source/roofs`, `RoofStyle.HULL`; its first painting is
kept as `raw/hull-v1`) is what any other building with `fallen-hull` walls
wears.

What ships, on each pick's recommended option until the owner answers:

- `tileset.py` cuts the map tileset `fallen-hull` from the accepted paintings:
  the hull's outer armour wall `installation.dark-concrete-wall@fallen-hull`
  (`CrashSiteStage.HULL_WALL`) with its own door `fallen-hull.armour-hatch`,
  window `fallen-hull.armour-port` and rubble `fallen-hull.armour-breach` (the
  breach edges), the trench floor `fallen-hull.trench`, the debris the hull shed
  on it (`fallen-hull.debris-*`, `CrashSiteStage.SHED`) and its interior
  dressing (`fallen-hull.rib-h`/`-v`, `conduit-h`/`-v`, `conduit-junction`,
  `survey-console`, `survey-rack-h`/`-v`, `sensor-drum`, `hangar-cradle`/`-v`,
  `forge-hopper`, `forge-coolant`, `forge-cables`, `breach-plating`,
  `breach-scatter`, `crushed-bulkhead-a`/`-b`). The interior's rooms are the
  generic ship tilesets (`ship-bulkheads`, `ship-interior-materials` and the
  rest); these pieces are what make them this ship. The breakup: each
  section's broken end is `fallen-hull.sheared-wall`, with
  `fallen-hull.shear-edge` on the ground beyond it and `fallen-hull.breach-edge`
  outside a breach; the debris shower is `fallen-hull.shower-*`,
  `fallen-hull.crater-small`/`-large` and `fallen-hull.scorch-splash`/`-streak`.
  The ground's names (`fallen-hull.armour-wall`, `fallen-hull.breach-spill`,
  `fallen-hull.interior-*`) are aliases of these pieces, laid walkable.
- `export.py --ship` cuts the ring forge (lit and put out), the spire stake's
  three stages and the stripper's and hauler's parts. `FallenHullLook` draws
  the forge and the stakes in the structure pass; `BattleSprites` gives the
  stripper and hauler chassis their own parts on the derelict leg rig.

`FallenHullLookSuite` cards every candidate over real look-preset frames;
`LongSurveyFallLookSuite` launches The Fall as the debug player does and looks
at the shipped pieces in place.
