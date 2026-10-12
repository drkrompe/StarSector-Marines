# Geometry-first ImageGen assets

For modular terrain and cover, define the geometry in code and ask built-in
ImageGen to reskin that guide. The guide owns placement, connectivity, openings
and empty space; the generated pass owns material and surface detail. This is
the preferred approach for this project's walls, openings, directional cover,
rubble and ground damage.

## Repeatable production loop

1. Start from the runtime layout and render a deterministic guide. Keep the
   complete cell canvas, even for an edge-hugging object. Define clear gutters,
   exact pivots, a recorded slot order and meaningful transparent voids.
2. Inspect the guide, then pass it to built-in ImageGen as the edit target.
   Supply existing art separately as the material reference. Request a reskin
   that preserves geometry, camera, placement and alpha; exclude added slabs,
   aprons, floor backing and borders unless the consumer actually owns them.
3. Save the guide under `art-source/templates/`, and retain the accepted raw
   PNG and exact prompt beside its authoring document. ImageGen may change the
   resolution: inspect actual dimensions and cuts, never assume the requested
   canvas size came back unchanged.
4. Annotate stable ids and material pairings in the source document. Export
   using `tools/authoring.cmd tileset_export -` with a JSON object such as
   `{"name":"colony-ground-damage"}` on stdin. The wrapper rebuilds the tools.
   Do not invoke the stale generated launcher under `build/authoring/`.
5. Preserve full-frame cuts and generated alpha. Crop/downsample/quarter-turn
   mechanically through the retained pipeline. Do not trim/recenter an edge
   piece or patch the final atlas separately from its producer. Re-derive
   height/normal companions if that sheet has them.
6. Register new sheets in both `TileRegistry.BUILTIN_TILESETS` and the provider
   catalog. Optional material pairings must survive document editing, export,
   reload and complete-catalog reference validation.
7. Check the actual assembly through a registered `SnapshotSuite`, using
   `gradlew.bat createSnapshots -Psnapshot=<id>`. Review at game scale, with
   adjoining cells, all relevant orientations and before/after states. Test
   important open regions spatially; average alpha cannot prove an opening is
   still open. Use small unit tests for selection/state laws and the snapshot
   catalog for whole-render evidence.

Set `JAVA_HOME` to the path in `CLAUDE.md` before Gradle/authoring commands.
Keep source inputs out of `mod/`; only exported art and its runtime catalog ship.

## Working examples

| Asset | Geometry / contract | Retained sources | Visual suite |
| --- | --- | --- | --- |
| Concrete installations | `ConcreteInstallationTemplate`; reuses wall, opening and remnant geometry with two concrete palettes, plus quiet floor fields | `templates/concrete-*.png`, `templates/installation-floors.png`, `tilesets/concrete-*`, `tilesets/installation-floors.*` | `installation-ground` |
| Colony walls | `WallReskinTemplate`; 16 exposed-face masks, no privileged inside/outside | `templates/colony-walls.png`, `tilesets/colony-walls.*` | `building-stamps` |
| Windows and doors | `OpeningReskinTemplate`; matching wall family, transparent apertures, four cardinal poses | `templates/colony-openings.png`, `tilesets/colony-openings.*` | `building-stamps` |
| Sandbags | `SandbagReskinTemplate`; rounded straight run and L, exact quarter turns | `doodads/sandbags*`, doodad manifest and retained sources | `sandbags` |
| Sandbag damage | `SandbagDamageReskinTemplate`; matching rounded roots, torn seams, local sand spills and open breaches | `templates/sandbag-damage.png`, `doodads/sandbag-damage*`, retained raw and sources | `sandbags` |
| Industrial fences | `FenceReskinTemplate`; one connected frame, shared centerlines and full-cell cuts | `templates/industrial-fence.png`, `doodads/industrial-fence*`, retained raw and sources | `fences` |
| Fence damage | `FenceDamageReskinTemplate`; matching edge roots, bent spans and open breaches | `templates/industrial-fence-damage.png`, `doodads/industrial-fence-damage*`, `doodads/fence-damage-sources/` | `fences` |
| Wall rubble | `RubbleReskinTemplate`; broken roots retain former connectivity and leave ground exposed | `templates/colony-rubble.png`, `tilesets/colony-rubble.*` | `wall-rubble` |
| Ground damage | `GroundDamageReskinTemplate`; local cracks/gouges over the original material, clear perimeter | `templates/colony-ground-damage.png`, `tilesets/colony-ground-damage.*` | `ground-damage` |
| Ordinary terrain blast scars | `TerrainBlastTemplate`; four shallow earth/paving/metal scars with transparent cell boundaries | `templates/terrain-blast*.png`, `tilesets/terrain-blast.*` | `ground-damage` |
| Forest canopies | `TreeCanopy` and `TreeReskinTemplate`; overhead rounded crowns, full foliage obstruction across 3-6 cells | `templates/forest-canopies.png`, `tilesets/forest-canopies.*`, `forests/` | `forest-brush` |
| Crate damage | `CrateDamageReskinTemplate`; matching box/wood/metal silhouettes, broken panels and separated contents | `templates/crate-*.png`, `tilesets/crate-damage.*` | `crate-damage` |
| Barrel damage | `BarrelDamageReskinTemplate`; matching drum groups, dented lids, open curved metal shells and separate scraps | `templates/barrel-*.png`, `tilesets/barrel-damage.*` | `barrel-damage` |
| Compound equipment damage | `CompoundPropDamageTemplate`; original placement frames, torn panels and low separate wreck fragments | `templates/compound-props-*.png`, `tilesets/compound-machinery-damage.*`, `tilesets/compound-command-damage.*` | `compound-prop-damage` |
| Yard stock and furniture damage | `FurnishingDamageTemplate`; full-frame damaged silhouettes, open reel arcs, split slats/tubes and separated low furniture panels | `templates/furnishing-*.png`, `tilesets/compound-yard-damage.*`, `tilesets/compound-furniture-damage.*` | `furnishing-damage` |
| Rock damage | `RockDamageReskinTemplate`; intact silhouettes with crack guides and separated low fragments | `templates/rock-*.png`, `tilesets/rock-damage.*` | `rock-damage` |
| Tree damage | `TreeDamageReskinTemplate`; broken crowns, low stumps and separated fallen limbs inside original footprints | `templates/tree-*.png`, `tilesets/forest-damaged.*`, `tilesets/forest-destroyed.*` | `tree-damage` |
| Planet formations | `MineralFormation` and `PlanetFormationTemplate`; convex overhead rock/ice bodies, shared registration across intact/cracked/rubble states | `templates/planet-formations.png`, `tilesets/planet-formations.*` | `planet-landscapes` |
| Glacial terrain | `IceTerrainTemplate`; nine quiet material swatches, runtime contours own ridge geometry | `templates/ice-terrain.png`, `tilesets/ice-terrain.*` | `planet-landscapes` |
| Ship bay study | `ShipBayReskinTemplate`; deck plates, grates, narrow markings and connected recessed pipes | `templates/ship-bay.png`, `tilesets/ship-bay.*` | `ship-bay` |
| Warehouse storage study | `WarehouseRackTemplate`; full rack bed, independent stock slots and transparent retaining frame | `templates/warehouse-racks.png`, `tilesets/warehouse-racks.*` | `warehouse-racks` |
| Ship briefing room | `BriefingRoomTemplate`; central table, inset tactical overlay, four chair headings, display and lectern | `templates/briefing-room.png`, `tilesets/briefing-room.*` | `ship-materials` |
| Ship gym | `ShipGymTemplate`; treadmill, rower, bench press, dumbbell rack, rubber mats, coping and water | `templates/ship-gym.png`, `tilesets/ship-gym.*` | `ship-materials`, `ShipGymWaterGlEvidence` |
| Ship utility rooms | `ShipUtilityTemplate`; compact server/service equipment, layered shallow shelves and contained pump/locker modules | `templates/ship-utility.png`, `tilesets/ship-utility.*` | `ship-materials` |
| Ship floors and trim | `ShipFloorTrimTemplate`; quiet material fields, registered edge strips, bulkhead ribs and flush covers | `templates/ship-floor-trim.png`, `tilesets/ship-floor-trim.*` | `ship-floor-trim`, `ship-materials` |
| Ship firing range | `FiringRangeTemplate`; heavy bullet traps, ballistic dividers, preparation and control benches, quiet lane materials | `templates/firing-range.png`, `tilesets/firing-range.*` | `ship-materials` |
| Ship washrooms | `ShipWashroomTemplate`; basin counters, toilet and shower cubicles, quiet non-slip deck | `templates/ship-washrooms.png`, `tilesets/ship-washrooms.*` | `ship-materials` |
| Ship provisions bay | `LoadingBayTemplate`; manifest desk, open-fork tug, continuous roller conveyor, separate cargo layers and staging deck | `templates/loading-bay.png`, `tilesets/loading-bay.*` | `ship-materials` |
| Ship shuttle bay | `ShuttleBayTemplate`; contained service equipment, shelf loads, landing material and flush tie-downs | `templates/shuttle-bay.png`, `tilesets/shuttle-bay.*` | `ship-materials` |
| Ship crew lounge | `CrewLoungeTemplate`; opposing sofas, layered tables and distinct recreation groups | `templates/crew-lounge.png`, `tilesets/crew-lounge.*` | `ship-materials` |
| Ship main stores | `MainStoresTemplate`; shelf beds, independent cargo, transparent retaining frames and pallet staging | `templates/main-stores.png`, `tilesets/main-stores.*` | `ship-materials` |
| Ship armory | `ArmoryWorkshopTemplate`; issue counter, layered weapons racks and repair bench, separate cabinets | `templates/armory-workshop.png`, `tilesets/armory-workshop.*` | `ship-materials` |
| Ship sickbay | `SickbayClinicTemplate`; layered medical beds, head monitors and an adaptable clinic bank | `templates/sickbay-clinic.png`, `tilesets/sickbay-clinic.*` | `ship-materials` |
| Ship mess | `MessGalleyTemplate`; long tables, separate meals and stools, coordinated galley appliances | `templates/mess-galley.png`, `tilesets/mess-galley.*` | `ship-materials` |
| Ship berthing | `BarracksBunkTemplate`; full bunk bases, separate bedding and rails, personal locker banks | `templates/barracks-bunks.png`, `tilesets/barracks-bunks.*` | `ship-materials` |
| Neutral ship bulkheads | `ShipBulkheadTemplate`; sixteen exposed-face masks, quiet charcoal/enamel materials | `templates/ship-bulkheads.png`, `tilesets/ship-bulkheads.*`, `tilesets/ship-neutral.README.md` | `ship-neutral` |
| Ship openings and decks | `ShipInteriorMaterialTemplate`; full-cell cardinal openings and seamless resin samples | `templates/ship-interior-materials.png`, `tilesets/ship-interior-materials.*` | `ship-materials` |

Template classes live in `src/test/java/com/dillon/starsectormarines/tools/tilesetauthoring/`.
Each accepted sheet's prompt and authoring note record its precise layout.

## Details that matter

- Wall atlas masks use N=1, E=2, S=4, W=8; `CellTopology` uses N=1, S=2,
  E=4, W=8. Convert deliberately. Cardinal opening cells are N,E,S,W.
- A window does not own an extra sidewalk cell. Building footprint code owns
  the apron; image generation must not add one to individual pieces.
- Sandbags use `preserveCanvas:true` and zero packing padding. When updating
  only those entries, `stitch_atlas.py --only-prefix doodad.sandbag-` preserves
  unrelated atlas pixels and requires an unchanged manifest layout.
- Rubble is a transparent overlay over the captured floor, paired by
  `rubbleBlock`. Ground damage uses `damageBlock` and keeps the original floor
  directly underneath. This avoids mismatched replacement-floor seams.
- Ground-damage marks should read as recessed fractures or shallow gouges,
  not bright raised branches. Inspect them composited on their actual material.
- An image preview can exaggerate RGB noise in almost-transparent pixels.
  Inspect alpha and the real composite before deciding whether the asset has
  a visible halo. Preserve genuine source alpha instead of keying by color.

The general geometry-first technique is also linked from the installed
ImageGen skill. This file owns project paths and commands; the skill owns the
reusable prompting and verification approach.
