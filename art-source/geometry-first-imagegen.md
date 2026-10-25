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

**Draw small, boxy pieces large.** ImageGen keeps a minimum of bevel and detail
per piece, so a short strip on a mostly empty canvas comes back too big and far
too deep: real-size armory benches and lockers drawn at 128 px per cell returned
twice at 1.2x their width and two to four times their depth, whatever the brief
said, while the slim gym machines held at the same density. At 320 px per cell,
one 3x1 frame per row, they landed within a few pixels of the guide. Measure each
returned piece's opaque box against its guide box before cutting.

**Re-drawing part of a kit.** When only some pieces of an accepted sheet change,
generate just those, then join the accepted raw and the new generation side by
side with `art-source/templates/real-scale-montage.py`; the authoring document
cuts each piece from its own half. No pixel is altered, and the unchanged pieces
keep their registered damage states.

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
| Field salvage | `field-salvage-guide.py`; three clipped-corner open recovery skids, exposed material contents and shared lifting rails | `templates/field-salvage-guide.*`, `tilesets/field-salvage.*` | `field-salvage` |
| Parked vehicles | `parked-vehicles-guide.ps1`; six east-facing overhead industrial/colonial chassis, symmetric tread tops and registered 3x2 placement frames | `templates/parked-vehicles-guide.*`, `tilesets/parked-vehicles.*` | `parked-vehicles` |
| APC and roof MG | `vehicles/apc/guide.ps1`; six-wheel overhead hull and independent MG; registered live/wreck bearings, ballistic muzzles and distance-driven tire-top UVs | `vehicles/apc/` | `apc` |
| Breakthrough tank | `vehicles/tank/guide.ps1`; flat-fronted surplus hull and short single cannon; registered live/wreck frames preserve bearings, while narrow tread UV strips follow accepted chassis travel | `vehicles/tank/` | `tank` |
| Technical and freight hauler | Retained technical wheel-well edit guide and `vehicles/hauler/guide.ps1` overhead cab/saddle/freight planes; registered live/wreck components keep final bearings and covered wheels | `vehicles/technical/`, `vehicles/hauler/` | `freight-vehicles` |
| Vehicle scuffs | `vehicles/scuffs/make_guide.py`; a 4x3 grid of mark footprints (scrape/pock, scorch, gouge/dent rows), each inside the middle of its cell so a turned mark keeps its reach; the exporter cuts the grid mechanically at 128 px a cell | `vehicles/scuffs/` | `vehicle-scuffs` |
| Rav wear | `rav-wear/make_guide.py`; the same 4x3 grid for organic marks (scored/chipped chitin, bruising and cracked plate, weeping wound rows), the Matriarch's body sprite as material reference; cut mechanically at 128 px a cell | `rav-wear/` | `rav-wear` |
| Barrel damage | `BarrelDamageReskinTemplate`; matching drum groups, dented lids, open curved metal shells and separate scraps | `templates/barrel-*.png`, `tilesets/barrel-damage.*` | `barrel-damage` |
| Compound equipment damage | `CompoundPropDamageTemplate`; original placement frames, torn panels and low separate wreck fragments | `templates/compound-props-*.png`, `tilesets/compound-machinery-damage.*`, `tilesets/compound-command-damage.*` | `compound-prop-damage` |
| Residential bed damage | `ComposableDamageTemplate` over `console-bank/damage/berthing-bed.tsv`; four two-cell headings, torn bedding and separated low frame/mattress fragments | `console-bank/damage/berthing-bed-*`, `tilesets/berthing-bed-damage.*` | `berthing-bed-damage` |
| Yard stock and furniture damage | `FurnishingDamageTemplate`; full-frame damaged silhouettes, open reel arcs, split slats/tubes and separated low furniture panels | `templates/furnishing-*.png`, `tilesets/compound-yard-damage.*`, `tilesets/compound-furniture-damage.*` | `furnishing-damage` |
| Field stones and rock damage | `templates/field-stones-guide.py`; seven smooth top-lit stone groups, one per frame; `field-stones-damage-guide.py` cuts the cracked and rubble guides from the accepted stones so all three states share each group's alpha box (`tilesets/nature-tiles-refit.py` joins and cuts them) | `templates/field-stones-*`, `tilesets/field-stones/`, `tilesets/rock-damage.*` | `rock-damage`, `undergrowth-look` |
| Tree damage | `TreeDamageReskinTemplate`; broken crowns, low stumps and separated fallen limbs inside original footprints | `templates/tree-*.png`, `tilesets/forest-damaged.*`, `tilesets/forest-destroyed.*` | `tree-damage` |
| Forest undergrowth | `Undergrowth` and `UndergrowthReskinTemplate`; six floor-growth silhouettes, standing/damaged/trampled sheets whose cuts are exact footprint boxes | `templates/undergrowth-*.png`, `tilesets/forest-undergrowth*`, `forests/` | `undergrowth-look` |
| Planet formations | `MineralFormation` and `PlanetFormationTemplate`; convex overhead rock/ice bodies, shared registration across intact/cracked/rubble states | `templates/planet-formations.png`, `tilesets/planet-formations.*` | `planet-landscapes` |
| Glacial terrain | `IceTerrainTemplate`; nine quiet material swatches, runtime contours own ridge geometry | `templates/ice-terrain.png`, `tilesets/ice-terrain.*` | `planet-landscapes` |
| Ship bay study | `ShipBayReskinTemplate`; deck plates, grates, narrow markings and connected recessed pipes | `templates/ship-bay.png`, `tilesets/ship-bay.*` | `ship-bay` |
| Warehouse storage study | `WarehouseRackTemplate`; full rack bed, independent stock slots and transparent retaining frame | `templates/warehouse-racks.png`, `tilesets/warehouse-racks.*` | `warehouse-racks` |
| Ship briefing room | Central table, inset tactical overlay, four chair headings and display from the original guide; `BriefingRoomTemplate` re-draws the lectern at real size (1x1, 320 px per cell), joined to the original raw by `real-scale-montage.py` | `templates/briefing-room.png`, `tilesets/briefing-room.*` | `ship-materials` |
| Console bank prototype | `ConsoleBankTemplate`; full-cell straights, inside/outside elbows and caps, reskinned as connected neighborhoods then cut into reusable cells | `templates/console-bank*.png`, `tilesets/console-bank.*`, `console-bank/` | `console-banks` |
| Composable furnishing damage | `ComposableDamageTemplate`; intact placement padding, damaged connected hulls, low separated fragments and transparent layered stock/linen/frame states | `console-bank/damage/`, `tilesets/console-bank-damage.*`, `tilesets/main-stores-damage.*`, `tilesets/sickbay-clinic-damage.*` | `composable-damage` |
| Fort power machinery | `power-plant-guide.py`; 3x3 turbine-alternator and 2x3 cooling skid, intact/damaged/low wreck rows | `templates/power-plant-guide.*`, `tilesets/power-plant.*`, `power-plant/` | `power-halls`, `composable-damage` |
| Factory production machinery | `factory-machines-guide.ps1`; 3x3 fabrication cell, 3x2 assembly bed and 2x2 coolant skid, standing damage and separated low wrecks | `templates/factory-machines-guide.*`, `tilesets/factory-machines.*`, `factory-machines/` | `console-rooms`, `composable-damage` |
| Ship gym | `ShipGymTemplate`; real-size 1x3 treadmill and rower (seat a separate sliding piece), 3x2 bench press, dumbbell rack, rubber mats, coping and water on whole-cell frames at 128 px per cell | `templates/ship-gym.png`, `tilesets/ship-gym.*` | `ship-materials`, `ShipGymWaterGlEvidence` |
| Ship gym track | `ship-gym-track-guide.py`; seamless running-track rubber and a 3x1 poolside lounger for the cruise-ship gymnasium | `templates/ship-gym-track-guide.*`, `tilesets/ship-gym-track.*` | `ship-duty` |
| Ship utility rooms | `ShipUtilityTemplate`; compact server/service equipment, layered shallow shelves and contained pump/locker modules | `templates/ship-utility.png`, `tilesets/ship-utility.*` | `ship-materials` |
| Ship floors and trim | `ShipFloorTrimTemplate`; quiet material fields, registered edge strips, bulkhead ribs and flush covers | `templates/ship-floor-trim.png`, `tilesets/ship-floor-trim.*` | `ship-floor-trim`, `ship-materials` |
| Ship firing range | `FiringRangeTemplate`; heavy bullet traps, ballistic dividers, preparation and control benches, quiet lane materials | `templates/firing-range.png`, `tilesets/firing-range.*` | `ship-materials` |
| Ship washrooms | Toilet and shower cubicles and quiet non-slip deck from the original guide; `ShipWashroomTemplate` re-draws the basin counter at real size (1x1, 320 px per cell), joined to the original raw by `real-scale-montage.py` | `templates/ship-washrooms.png`, `tilesets/ship-washrooms.*` | `ship-materials` |
| Ship provisions bay | Continuous roller conveyor, separate cargo layers and staging deck from the original guide; `LoadingBayTemplate` re-draws the manifest desk (2x1) and the open-fork pallet tug (1x3) at real size (320 px per cell), joined to the original raw by `real-scale-montage.py` | `templates/loading-bay.png`, `tilesets/loading-bay.*` | `ship-materials` |
| Ship shuttle bay | Ground-power unit, fluid skid, shelf loads and landing material from the original guide; `ShuttleBayTemplate` re-draws the flight desk (2x1), service cart (1x2) and a small flush tie-down plate at real size (320 px per cell), joined to the original raw by `real-scale-montage.py` | `templates/shuttle-bay.png`, `tilesets/shuttle-bay.*` | `ship-materials` |
| Ship crew lounge | Opposing sofas, layered coffee tables, pool table and recreation groups from the original guide; `CrewLoungeTemplate` re-draws the card table and its game, the round stool and the bookshelf at real size (320 px per cell), joined to the original raw by `real-scale-montage.py` | `templates/crew-lounge.png`, `tilesets/crew-lounge.*` | `ship-materials` |
| Ship main stores | `MainStoresTemplate`; shelf beds, independent cargo, transparent retaining frames and pallet staging | `templates/main-stores.png`, `tilesets/main-stores.*` | `ship-materials` |
| Ship armory | Issue counter, layered weapons racks and separate cabinets from the original guide; `ArmoryWorkshopTemplate` re-draws the repair bench, its tools and the ready-locker bank at real size (3x1, 320 px per cell), joined to the original raw by `real-scale-montage.py` | `templates/armory-workshop.png`, `tilesets/armory-workshop.*` | `ship-materials` |
| Ship sickbay | Head monitors and the clinic bank from the original guide; the ward bed and both linen layers are the original 1x2 generation (a 1x3 real-length refit was tried and rolled back: three cells read as a bed for a giant beside the head-and-shoulders figure) | `templates/sickbay-clinic.png`, `tilesets/sickbay-clinic.*`, `tilesets/sickbay-clinic-damage.*` | `ship-materials`, `composable-damage` |
| Ship mess | `MessGalleyTemplate`; long tables, separate meals and stools, coordinated galley appliances | `templates/mess-galley.png`, `tilesets/mess-galley.*` | `ship-materials` |
| Ship berthing | Personal locker banks from the original guide; the bunk base, bedding and rails are the original 1x2 generation (the 1x3 refit is kept in the raw but not exported) | `templates/barracks-bunks.png`, `tilesets/barracks-bunks.*`, `tilesets/barracks-bunks-worn.*` | `ship-materials`, `ship-duty` |
| Neutral ship bulkheads | `ShipBulkheadTemplate`; sixteen exposed-face masks, quiet charcoal/enamel materials | `templates/ship-bulkheads.png`, `tilesets/ship-bulkheads.*`, `tilesets/ship-neutral.README.md` | `ship-neutral` |
| Ground-defence structures | `art-source/templates/defence-structures-guide.py`; static 3x3 plinths, a rotating gun house with barrel recoil layer, a spinning emitter ring and a tint-by-holder core, pivots at canvas centre; exporter splits and registers mechanically (Codex CLI: `art-source/defences/generate.sh`) | `templates/defence-structures-*.py`, `templates/fortress-battery-*-guide.png`, `templates/shield-generator-*-guide.png`, `defences/*.prompt.txt`, `defences/raw/` | `turrets`, `fortress-battery`, `shield-dome` |
| Fort earthworks | `art-source/templates/fort-earthworks-guide.py`; sixteen trench sections addressed by open-arm mask (channel 30%-70% at every edge so neighbours join), eight thick sandbag runs and corners and a hedgehog on 192px full-cell frames (Codex CLI: `art-source/tilesets/generate-fort-earthworks.sh`); the belt's fence is the catalogue industrial fence, so the sheet carries no wire | `templates/fort-earthworks-guide.*`, `tilesets/fort-earthworks.*` | `battery-fort-map` |
| Fort hedgehog damage | `art-source/templates/fort-hedgehog-damage-guide.py`; two full-cell frames at the intact hedgehog's proportions: damaged (standing on its hub, one girder bent, one torn loose) and destroyed (girders flat and splayed, hub loose); the intact `fort-earthworks` frame is the material reference | `templates/fort-hedgehog-damage-guide.*`, `templates/fort-hedgehog-reference.png`, `tilesets/fort-hedgehog-damage.*` | `roadblock` |
| Fort works by style | `art-source/fortworks/make_guide.py`; one sheet per emplacement style: the fort-emplacement collar plate, a 3x3 barricade ring (its eight cells the eight run shapes) and the sixteen wall-face masks, painted in one pass with the style's turret plinth as material reference; `make_doc.py` measures every cut from the raw (wall tiles from their alpha gutters, cut inside ImageGen's tile outline) and exports through the authoring tools (`fort-works.md`) | `fortworks/guides/`, `fortworks/prompts/`, `fortworks/raw/`, `tilesets/fortworks-<style>.*` | `fort-works-look` |
| Battle-map roofs | `art-source/roofs/make_guide.py`; per roof style (clay, slab, corrugated, plate) one sheet: a 2x2-cell field swatch with its grain, an eave edge strip, and holed / collapsed / burnt cells whose voids are a separate mask; `export.py` makes the swatch seamless, builds the eave, outside-corner and inside-corner trims from the strip at `RoofCover.EAVE_DEPTH`, and cuts damage alpha from the mask (Codex CLI: `roofs/generate.py <style>`) | `roofs/guides/`, `roofs/prompts/`, `roofs/raw/`, `mod/graphics/tilesets/roofs.png` | `roofs` |
| Fort buildings | `art-source/templates/fort-buildings-guide.py`; per aspect, four panels (intact roof, damaged, destroyed wreck, lights overlay) on the building's interior floor rectangle at one pixel scale, a parts sheet (spinning radar and fans, 3x3 interior bases) and a gatehouse sheet; the same four panels for each ante-room (`opshall` 5x5, `bay` 8x3, `ctrlhall` 3x5), the anti-air control room (`aircontrol` 7x5) and the 11x5 generator hall (`power`), plus a `blastdoor` pair (3x1 wall-run piece, intact and damaged); exporter crops mechanically at 64 px/cell (Codex CLI: `art-source/fort-buildings/generate.sh <asset> [reference.png ...]`, extra images are material references only) | `templates/fort-buildings-*-guide.png`, `fort-buildings/prompts/`, `fort-buildings/raw/`, `fort-buildings/export.py` | `battery-fort-map` |
| Fort fittings | `art-source/templates/fort-fittings-guide.py`; the operator seat (single cell, round, rotation-neutral), drawn six times over a 3x2 grid of 512px frames because image generation did not hold absolute scale for one prop on a mostly empty canvas (two such attempts came back 1.5-2x too large); frame 0 is exported at 128px (Codex CLI: `art-source/tilesets/generate-fort-fittings.sh`) | `templates/fort-fittings-guide.*`, `tilesets/fort-fittings.*` | `battery-fort-map` |
| Infantry held weapons | `art-source/templates/infantry-weapons-guide.py`; six overhead weapons (rifle, pulse rifle, SMG, railgun DMR, anti-materiel rifle, rocket launcher) on one sheet, each frame the shipped sprite's exact size x8 with the muzzle on the top edge and the grip/shoulder at the layout pivot, so poses need no edits; surplus rifle and masterwork DMR reskin the same frames with the first sheet as style reference, as do the pulse rifle's second geometry and the packed launcher (its own one-frame sheet), with enclosed guide openings cleared on export; their armory icons are the same guns in profile from `infantry-weapon-icons-guide.py`, tilted up to the right on export (`tools/codex_imagegen.py`, mechanical export by `export.py`) | `templates/infantry-weapons-*`, `infantry-weapons/*.prompt.txt`, `infantry-weapons/raw/` | `layers`, `armory` |
| Comms radio | `art-source/templates/comms-radio-guide.py`; a top-down worn pack (0.40 x 0.42 shoulder body mount: top edge against the torso, flap, buckles, empty handset cradle, short rear whip) and a separate hand set (0.20 x 0.50: earpiece, grip with talk bar, mouthpiece, cord stub) raised only during the call, joined by a code-drawn coiled cord stretched between the two ports; plus an upright icon (Codex CLI: `art-source/comms-radio/generate.sh <asset>`, mechanical export by `export.py`) | `templates/comms-radio-*-guide.png`, `comms-radio/*.prompt.txt`, `comms-radio/raw/`, `comms-radio/export.py` | `armory`, `layers`, `equipment-mounts` |
| Flamer pack and wand | `art-source/templates/flamer-guide.py`; a futuristic WW2 backpack flamethrower: the worn fuel pack from above (0.70 x 0.50 shoulder body mount: harness yoke against the torso, twin upright tanks seen end-on as armoured caps, a pressure bottle between and behind, a valve elbow and hose coupling on the right tank's flank) and the wand (0.16 x 0.62: nozzle and igniter on top, heat shroud, fore grip, valve housing, rear grip, hose fitting at the bottom) held at the hip only while picked, joined by a code-drawn armoured hose stretched between the two ports; plus a profile icon (`tools/codex_imagegen.py`, mechanical export by `export.py`) | `templates/flamer-*-guide.png`, `flamer/*.prompt.txt`, `flamer/raw/`, `flamer/export.py` | `marine-left-special` |
| Swarm bores and spore colonies | `art-source/swarm-bores/make_guide.py`; seeded, lopsided heightfields (a slanted shaft with an upheaved and a collapsed side, thrown paving and earth; uneven sac clusters) lit by the battle's default sun with ray-marched shadows, several variants per body on canvases larger than the body so its shadow fits; centre-pivoted upright layers chosen per structure by `StructureArt.variantFor`; the exporter fits each raw to its guide, lays the guide's own ground layer (cast shadow, contact shade, cracks) underneath, lifts the amber as a pulsing `-glow` layer, and recolours the creature materials to the settled palette from the guide's material map; wounded and remains damage states are built from the same seeded geometry and reskinned with the intact raw as identity reference (Codex CLI: `swarm-bores/generate.sh <part> <variant> [state]`) | `templates/swarm-*-<n>[-<state>]-guide.png`, `swarm-bores/prompts/`, `swarm-bores/raw/`, `swarm-bores/export.py` | `swarm-spore-colonies`, `swarm-bore-collapse`, `swarm-damage-states`, `spore-colony-fire` |
| Heart origin props | `art-source/heart-origins/make_guide.py`; nine kits (the four origins' telling objects in five states, their yard kits in four: clean, grown, damaged, destroyed), the creep mat drawn under each prop from where its story says the growth came out and held a margin inside the frame so no state is cut square; `export.py` cuts every frame as its guide slot scaled to the raw (Codex CLI: `heart-origins/generate.sh <kit>`) | `templates/heart-*-guide.png`, `heart-origins/prompts/`, `tilesets/origin-*.raw.png` | `origin-props-look` |
| Boarding Jack power suit | `marine-modular-topdown/boarding-jack/make_guide.py`; Specter shell registration (150x95 torso and hinged mismatched shoulders, 72x69 helmet, 38x52 foot; the left shoulder is a hinged boarding shield carrying Latch Assist) drawn as vector edit targets at exact multiples of fixed export windows; a soot-based `pirate` paint has its own weathered raws; the exporter fits each raw's opaque extent to its guide, bounds alpha by the grown silhouette and derives casing paint masks by colour (Codex CLI: `boarding-jack/generate.sh <part> [reference.png]`, torso raw as the material reference for the rest) | `templates/boarding-jack-*-guide.png`, `boarding-jack/prompts/`, `boarding-jack/raw/`, `boarding-jack/export_assets.py` | `boarding-jack-layers` |
| Deck motion parts | `deck-motion-guide.py`; moving parts cut from their static bases on whole-cell frames: crane runway and bridge, rack lift rail and carriage, fan rotor, seamless belt tiles (Codex CLI) | `templates/deck-motion-guide.*`, `tilesets/deck-motion.*` | `deck-props` |
| Marked apron | `templates/marked-apron-guide.py`; concrete hardstanding with one painted edge line on each exposed side and a whole-period diagonal keep-clear hatch, two `edge-faces-4x4` blocks on a 6x6 plate | `templates/marked-apron-guide.*`, `tilesets/marked-apron.*` | `marked-apron-look`, `freight-yard-look` |
| Street dressing | `templates/street-dressing-guide.py`; two sheets at 300 px a cell: three 3x1 shop awnings (wall rail on the frame's north edge) and two 1x1 street lamps (arm north); a 3x1 overhead pipe rack cut per cell with flange collars on every cell boundary so a straight cell repeats, the gantry column, a 2x1 jersey barrier, a bollard, a boom-gate post and its 3x1 arm | `templates/street-dressing-guide.*`, `templates/industrial-street-kit-guide.png`, `tilesets/street-dressing.*`, `tilesets/industrial-street-kit.*` | `main-street-look`, `street-kit-look` |
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
