# Road authoring

Launch `gradlew.bat layerAuthoring` and select **Roads**.

- Start from the curved junction sample, choose **New** for another map size, or **Open** a road JSON file.
- Drag gold anchors; double-click to insert one; **Remove anchor** deletes the selected point. **Undo** restores prior edits.
- Add roads to build junctions. Select a road to change width, gravel shoulder, asphalt/concrete, wear or wear-pattern seed.
- **Save / Save as** preserves the versioned JSON document. Loading validates dimensions, anchor bounds, finite settings and road identities.
- `gradlew.bat createSnapshots -Psnapshot=roads` renders the wear ladder and exercises anchor dragging, the wear slider and save/reopen at 1920×1080.

`pavement.recipe.json` is the art source for the independent pavement sheet.
`gradlew.bat roadMaterials` deterministically rebuilds its albedo atlas and tile
catalog. It needs no image service or external download. Keep the recipe and
producer together; do not paint over the exported atlas. The source contains
64px cells, eight variants for each of three ages and two materials. Grid source
pixels are not a requested game zoom or UI scale.

New map recipes parse `RoadPlan`, rasterize it with `RoadRasterizer`, reserve the
returned road/shoulder mask against structures, and stamp it onto bare walkable
ground before facilities. Wear selects ordinary catalog surface ids and changes
no navigation facts. The first road wins material at an overlapping junction.

Thunder Run consumes `mod/data/world/roads/thunder-run.road.json` at application
load. Open that file to tune its pavement. Its freight/ambush corridors are a
mission contract: saving a geometry change there is refused; redesign its
buildings, route graph and convoy stops before changing that contract. Other
saved road plans can have arbitrary spline geometry and do not automatically
replace mission maps.

Current boundaries: the smooth spline is rasterized to square terrain cells;
there are no subcell mesh edges, automatic junction markings, bridges or elevated
roads. A curve's appearance is not a vehicle turning-radius guarantee. Validate
mission layouts using production chassis clearance and convoy replays.
