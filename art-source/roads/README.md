# Road authoring

Launch `gradlew.bat layerAuthoring` and select **Roads**.

- Start from the curved junction sample, choose **New** for another map size, or **Open** a road JSON file.
- Drag gold anchors; double-click to insert one; **Remove anchor** deletes the selected point. **Undo** restores prior edits.
- Add roads to build junctions. Select a road to change width, gravel shoulder, asphalt/concrete, wear or wear-pattern seed. Choose round ends for free road stubs or flush (`BUTT`) ends for aprons and exact facility thresholds.
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

All six story maps consume documents from `mod/data/world/roads/` at application
load: Relief, Counterattack, Open the Junction, Last Lift, Relay Nine and Thunder
Run. Public approaches use asphalt; loading/service routes and boat aprons use
concrete. Each route has a named wear setting. Open its document in **Roads** to
tune it; custom saved documents do not automatically replace a mission.

The story maps retain their gate widths, berth footprints and script-owned route
graphs. Changing a spline is not a corresponding change to the vehicle or
reinforcement route: revalidate the complete mission when editing geometry.
Thunder Run additionally refuses changes outside its fixed freight/ambush mask.
The five other documents validate their map extent at load/save. Relief's larger
debug maps extend only its boundary approaches, not the checkpoint itself.

The `endCap` field defaults to `ROUND` for existing version-1 documents; `BUTT`
ends stop at the first and last anchor. Later room floors, walls and painted
landing/inspection stripes replace pavement surfaces before finalization.

`gradlew.bat createSnapshots -Psnapshot=story-roads` renders all five converted
maps and close-ups at 1920×1080, checks three seeds per map plus expanded Relief,
and rejects old road tiles, pavement leaking into rooms/markings, or blocked
berths. Each generator also validates its mission route connectivity. Use the
existing `commanderEvidence -Pmission=<name>` harness for arrival and battle
replays; `roads` snapshots still exercise the actual editor interactions.

Current boundaries: the smooth spline is rasterized to square terrain cells;
there are no subcell mesh edges, automatic junction markings, bridges or elevated
roads. A curve's appearance is not a vehicle turning-radius guarantee. Validate
mission layouts using production chassis clearance and convoy replays.
