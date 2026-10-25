# Forest brush

Run `gradlew.bat layerAuthoring` and choose **Forests**. Drag to paint trees,
right-drag to clear a path, and use the mouse wheel or radius control to change
the brush. Choose mixed woodland, broadleaf or conifers. A radius of at least
three cells gives a crown room to fit. One drag is one undoable stroke.

**Show blocked cells** overlays the actual collision grid. All cells touched by
the canopy are impassable, including leaves and branches away from the trunk.
Erasing excludes those cells from placement, so a cleared path has no canopy
overhang. Intact and damaged crowns are opaque obstacles. Combat can fell a
whole tree, leaving a low broken stump and the short butts of its limbs and releasing its canopy
cells wherever no surviving tree or hill still obstructs them.

The example `woodland.forest.json` is a 96x54 woodland with an open central lane.
Save/Open retains brush strokes, tree mix and seed. **Road context** optionally
loads a road plan at the same dimensions; the preview refuses canopy overlap
with its pavement. Undo/redo applies to strokes and forest settings.

## Reuse in map generation

Read a saved document through `ForestPlan.read`, then call `ForestBrush.stamp`
with the fresh map's navigation grid, topology, doodad list and optional
reserved-cell mask. Dimensions must match. Stamp before final navigation and
mission reachability validation. It returns the `ForestField` with deterministic
tree placements and the union of their blocked canopy cells.

The stamper validates complete canopy footprints and all referenced art before
mutation. It refuses structures, paved/engineered ground, existing fixtures,
doors and host reservations. It does not carve roads, assign objectives or
prove that required mission positions remain connected. Existing mission maps
adopt forest plans explicitly; saving in the workbench does not alter them.

## Art and verification

`TreeCanopy` owns the rounded geometry and intersected-cell mask. The matching
`TreeReskinTemplate` draws the retained guide. Built-in ImageGen produced six
overhead crowns from it, with source/prompt/cut metadata in
`art-source/tilesets/forest-canopies.*`. Full-frame cuts preserve pivots and
empty margins; the ordinary tileset exporter packs the multi-cell doodads at
64 source pixels per world cell. Quarter turns vary placement.

Re-export with `tools/authoring.cmd tileset_export -` and JSON
`{"name":"forest-canopies"}` on stdin. Use the `JAVA_HOME` in `CLAUDE.md`.
`gradlew.bat createSnapshots -Psnapshot=forest-brush` produces the production
forest scene, individual canopy examples and editor screenshots, and exercises
paint/erase/undo/redo/save/reload through real controls. Small unit tests verify
placement, refusal, rotation and that visible exported foliage lies inside the
blocked footprint.

### Combat damage art

`TreeDamageReskinTemplate` retains the six species' full-frame centers and
draws broken crowns or detached fallen limbs around low stumps. Built-in
ImageGen reskins the two guides under `art-source/templates/tree-*.png` using
the intact canopy sheet as the material reference. Accepted raw sheets, exact
prompts and reviewed crop documents live in
`art-source/tilesets/forest-damaged.*` and `forest-destroyed.*`.
The generated sheets retained the 1536x1024 canvas; reviewed cuts include
branch tips and empty margins without alpha trimming or recentering.
The felled trees ship flattened (owner, 2026-10-25): a heavy hull drives over
them, and the generation's long fallen limbs read as a tree still in the way.
`art-source/tilesets/forest-destroyed-flatten.py` keeps each cut's stump and
the butts of its limbs, broken at a ragged line 40% out, into
`forest-destroyed-flat/forest-destroyed.flat.png`, which the authoring document exports; the accepted
`forest-destroyed.raw.png` is never altered. Re-bake the blight's felled wood
(`gradlew.bat bakeCreepBlight`) after exporting.

Export each with `tools/authoring.cmd tileset_export -` and JSON
`{"name":"forest-damaged"}` or `{"name":"forest-destroyed"}` on stdin.
The ordinary exporter supplies six multi-cell props per sheet at 64 source
pixels per world cell, retaining generated alpha and runtime quarter turns.
`createSnapshots -Psnapshot=tree-damage` shows all three states, an overlapping
grove, and a tree felled on a hill through the production renderer.

Each tree starts with 200 structural HP. Positive weapon-authored wall damage
to any occupied canopy cell tears its crown; a blast spends its damage only
once per tree. Exhaustion swaps to zero-cover remnants and clears the whole
tree's obstruction, preserving overlapping live crowns, underlying hills and
the original ground. No doorway or masonry rubble is created. Trees on
walkable display-only placements do not acquire collision or combat durability.

### Undergrowth and forest-floor plants

`Undergrowth` owns six floor-growth silhouettes (bramble 3x2, small bramble 2x1,
thicket 3x3, small thicket 2x2, bracken 4x2, saplings 2x2) as lobes, like
`TreeCanopy`. `UndergrowthReskinTemplate` draws three 1536x1024 guides at 512 px
pitch (`art-source/templates/undergrowth-{standing,damaged,trampled}.png`); each
cut is the species' exact footprint box, 480 px on its longest side, so the art
maps one to one onto the closed cells. Built-in ImageGen reskinned the standing
guide with the canopy atlas as material reference, then the damaged and
trampled guides with the standing raw as identity reference. Raws, prompts,
provenance and authoring documents are `art-source/tilesets/forest-undergrowth*`.
The trampled state is deliberately quiet (owner pick, 2026-10-25): sparse, muted
leaf litter over about a fifth of the patch with no grain, so a flattened lane
recedes into the ground and a quarter-turned patch never looks stretched. Pale
snapped wood, a solid pressed mat and a streaked muted mat were tried and turned
down: the streaks read as stretched art when rotated, and full coverage made the
lane look like a carpet.
Export each with `tools/authoring.cmd tileset_export -` and
`{"name":"forest-undergrowth"}` (or `-damaged`, `-destroyed`).

The six one-cell floor plants (`nature.shrub-1..3`, `nature.tuft-1..3`) were
redrawn overhead from `art-source/templates/forest-floor-plants-guide.py` into
`art-source/tilesets/forest-floor-plants/forest-floor-plants.png`.
`art-source/tilesets/nature-tiles-refit.py` resamples them to the nature
plate's scale, joins them to the right of the accepted plate without touching
it, and points the six entries at them; it does the same for the seven field
stones (`rock-damage.README.md`). Then export `nature-tiles` and `rock-damage`
and re-run `gradlew.bat :asset-pipeline:deriveTileMaps`.

`createSnapshots -Psnapshot=undergrowth-look` shows the guides, every species in
its three states and four turns, and look shots: a wood whose tank lane is grown
over, crossed by a breakthrough tank, and open ground with the floor plants and
field stones in all three states.

## Outdoor map adoption

Outdoor city recipes (including precinct maps) and Relief, Counterattack,
Junction, Last Lift, Relay Nine and Thunder Run now add seeded groves on living
worlds. Grass and soil can carry trees; grassy hill summits can carry whole
crowns. Bare cliffs and the other planet surface palettes remain treeless.
Suitable open countryside also gains small hills unless the mission already
owns an authored terrain plan.

Placement reserves finished facilities, roads, landing approaches and connecting
routes, then declines each obstacle that cannot prove a local walkable detour.
This does not repair pre-existing map connectivity defects. The ordinary forest
brush remains free to paint barriers; this policy belongs to mission adoption.

Run `createSnapshots -Psnapshot=outdoor-forests` for the paired production-map
matrix and `-Psnapshot=thunder-terrain` for the freight chassis/flanking proof.
The diagnostic `-Dbattle.mapgen.outdoorLandscape=false` supplies the same-seed
control without new procedural hills or forests; authored hills remain.
