# Forest brush

Run `gradlew.bat layerAuthoring` and choose **Forests**. Drag to paint trees,
right-drag to clear a path, and use the mouse wheel or radius control to change
the brush. Choose mixed woodland, broadleaf or conifers. A radius of at least
three cells gives a crown room to fit. One drag is one undoable stroke.

**Show blocked cells** overlays the actual collision grid. All cells touched by
the canopy are impassable, including leaves and branches away from the trunk.
Erasing excludes those cells from placement, so a cleared path has no canopy
overhang. Crowns are opaque, permanent natural obstacles, not destructible walls.

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
