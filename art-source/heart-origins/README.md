# Heart origin props

The set dressing that tells, without words, how a heart's bloom got where it is (`heart-origins.md`). One kit
per origin, each with a telling object the first mother opens at:

| Origin | Telling object | Kit |
| --- | --- | --- |
| Workings | `origin-workings.shaft`: a capped shaft burst from below, its cap flung aside | pump shed, pipe run, elbow, and the pipe's mouth into the ground |
| Freight yard | `origin-freight.split-container`: a refrigerated container split open from inside | rust and blue containers for the stack, a pallet of seafood crates (burst and empty once grown), gantry crane (span and legs), a flatbed stopped mid-unload |
| Research site | `origin-lab.annex`: a sealed annex blown outward, its walls thrown clear, its aquarium tanks burst | specimen tank rack (a crustacean in each water tank) and sample freezer, broken open from inside; the fence and sandbags facing in are the catalogue's own |
| Park | `origin-park.bandstand`: an octagonal bandstand, half swallowed; `origin-park.pond`: the pond drained and torn open from below, where the first mother can open | swings, slide, climbing frame, roundabout, bench |

The crates, tanks and reefer carry the lobster thread (`swarm-lore-guide.md`): set dressing only, no lettering on
any prop, and nothing that names it.

Layouts place them by kind through `OriginProp` (`place(ctx, x, y, quarterTurns, state)`, over
`PreparedGround.prop`), never by sprite id.

## States

Every prop is drawn in four states, telling objects in five. The shipped ids:

- `<id>`: **grown**, the heart's state: what happened to it, and weeks of creep on it. Its battle-damage pair is
  `<id>-damaged` (grown, then fought over) and `<id>-destroyed` (low separated wreck that releases cover),
  registered in `PropDamage`.
- `<id>-clean`: the thing before the bloom. Static.
- `<id>-swallowed`: telling objects only, the growth thickest and the object half gone. Static; the owner's open
  look choice between it and grown.

The crane's span is coverless: it lies over the ground between its two legs, and units pass under it.

## Production

1. `python art-source/heart-origins/make_guide.py` draws one geometry guide per kit
   (`templates/heart-<kit>-guide.png`) and `geometry.json`, the slot of every prop and state. Code owns every
   silhouette, including where the creep lies: its mat is drawn under the prop, spreading from where the story
   says it came out, and kept a clear margin inside the frame so no state is cut square by its frame.
2. `art-source/heart-origins/generate.sh <kit> [--replace]` makes one paid ImageGen call: the brief is
   `prompts/common.txt` plus `prompts/<kit>.txt`, the guide is the edit target, the parked-vehicles raw is the
   prop material reference and the first mother bore's raw the growth's. It writes
   `art-source/tilesets/origin-<kit>.raw.png` and its provenance.
3. `python art-source/heart-origins/export.py [kit ...]` writes each kit's authoring document, every frame the
   guide's slot scaled by the raw's measured ratio (all ten came back at the guide's aspect), and exports it
   (`tileset_export`) to `mod/graphics/doodads/origin-<kit>.png` and `mod/data/tilesets/origin-<kit>.tileset.json`.
   Nothing is painted after generation; native alpha is kept.

## Review

`gradlew.bat createSnapshots -Psnapshot=origin-props-look` (`OriginPropsLookSuite`, opt-in) stands each origin's
set piece on real generated ground with the first mother opened beside it and the creep matured, a marine squad
off it for scale, at play zoom and close, the telling object grown and swallowed.
