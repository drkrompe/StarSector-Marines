# Forest brush

Run `gradlew.bat layerAuthoring` and choose **Forests**. Drag to paint trees,
right-drag to clear a path, and use the mouse wheel or radius control to change
the brush. Choose mixed woodland, broadleaf or conifers. A radius of at least
three cells gives a crown room to fit. One drag is one undoable stroke.

**Show blocked cells** overlays the actual collision grid. All cells touched by
the canopy are impassable, including leaves and branches away from the trunk.
Erasing excludes those cells from placement, so a cleared path has no canopy
overhang. Intact and damaged crowns are opaque obstacles. Combat can fell a
whole tree, leaving low broken trunks and branches and releasing its canopy
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
