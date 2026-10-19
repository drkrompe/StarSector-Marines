PARAGON - TRI-TACHYON BATTLESHIP - CINEMATIC GEOMETRY
====================================================

The first Tri-Tachyon hull in the set. Built through the ship-model-pipeline
skill. High tech: smooth curved skin and glowing seams, not riveted plate.

Identity (read-only install, never copied into the repo)
  data/hulls/paragon.ship          weapon, engine and shield slots; centre (164, 190)
  graphics/ships/paragon.png       silhouette and livery (330 x 364)
  data/variants/paragon/paragon_Elite.variant   fittings, for the record
  ship_data.csv row 'paragon'      crew 400-1000, cargo 300, fuel 300; OMNI shield,
                                   Fortress Shield system
The build fails if the installed slots, engines, shield or centre stop matching.
On a machine without the game, point STARSECTOR_CORE at a copy of starsector-core.

Build (Blender 5.2; renders and the .blend are ignored)
  sh ../../blender.sh --background --factory-startup --python build_paragon.py -- [--out DIR] [--views ...]
  python3 compose_review.py [--dir DIR] [--title T] [--before DIR]   (Pillow + numpy)
  python3 trace_sprite.py      re-reads the sprite's outline into pg_outline.py (committed)

How the hull reads (owner, pass 1 rounds 2-3)
  Each part has a job, laid out by the sprite's three tones (skin = armour, white
  = command deck / bow module / reactor domes, dark = lining, wells, machinery,
  steep faces). Nested rings round the open bore: the dark bore lining (the
  shield emitter ring, r 56.5-76), the deep main ring (r 74-124, the backbone),
  and the armour belt out to the circular outer edge (r 136), whose sloped outer
  face is the sprite's dark rim. A groove parts ring and belt on the bow quarters.
  The prow is armour round two big recessed bays for the capital mounts (WS
  001/002), with the white bow module behind and skin horns either side. The
  command deck stands raised above the ring and the bore on a dark gantry, in
  white tiers up to the bridge, with the skin aft body behind it between the
  pods; the pods carry white reactor domes in dark wells. Depth follows function:
  ring deepest, belt and pods lower, deck tallest. Forms are faceted armour
  planes on curved plans, matte, not a moulded gloss skin. Turrets are
  placeholders (pucks on the named pivots) until real mounts are designed.

Turret wells and firing lines (owner, pass 1 round 4)
  Turrets are never lifted out of the hull; the hull is shaped round them. Each
  well is cut along the plane fitted to the plate it stands in (so it leans with a
  glacis), lined dark, with a dimple round it: a flat terrace at the well rim and
  a chamfer back up to the plate, the sprite's concentric rings. The main ring's
  outer course is a level gun shelf (r 99-130, z ~28) below its crown, carrying the
  big beam turrets and the beam mediums; the bow horns are flat gun platforms and
  the bow module sits below their barrel line; the bow-quarter lobes carry WS
  011/017. pg_arcs checks every slot's arc at the barrel line (level and 1.5 deg
  down) and the build prints the clear share; geometry.json keeps it under
  firing_lines. Missile slots are exempt (missiles leave upward and steer).

The shape gauntlet (run every iteration)
  build_paragon.py --views top-classes, then python3 gauntlet.py --dir DIR:
  scores each tone's IoU against the sprite (gauntlet.png) and draws the model's
  part edges over the sprite (gauntlet-edges.png). Silhouette IoU alone says
  nothing about the parts inside the outline.

Pass 2 detail (pg_detail; owner: machinery insets and dimples)
  Read off the sprite at 6x, each with a job: recessed machinery trays with pipe
  runs, pumps and junction boxes (beside the bow module, its flanks, beside WS 013,
  the pods' forward-outboard bays); rectangular vent and square dimples; arched
  niches on the pods' aft slopes; the prow grille vanes; segmented reactor glow on
  each reactor well; radial plate seams on the shelf and belt and transverse seams
  on the aft body; the blue-white seam light along the gun shelf's riser; finned
  machinery grilles in the pods' aft faces between the drives; glazed window slits
  along the command deck tiers; machinery ribs on the bore lining; service panels
  in the belt flank; frames and pipe runs on the deck gantry. Insets stay 3 units
  inside the outline; the silhouette IoU held (0.959 before, 0.958 after).

Forward hangar (pg_hangar; owner, pass 3)
  A boat bay let into the bore's forward wall, opening aft across the bore at the
  command deck: 21 m wide, 14 m high, 35 m deep (true metres), lined dark with
  deck guide lights and roof light strips, a pale mouth frame with seam lights,
  and a two-leaf door built closed. Each leaf slides sideways into the wall from
  its own empty (Hangar door <side> leaf slide: open_axis, open_sign, travel);
  --hangar-open poses it for review. Anchors: Hangar mouth (+X out into the
  bore), Hangar launch point (the bore centre). Its air (Hull forward hangar,
  unrendered) counts as enclosed volume, and ship_scale.py carries it as two boat
  bays.

Finish: hex ablative ceramic (pg_finish; owner's pick, pass 3)
  The armour (skin, pale, white) is crowned hexagonal ceramic plates with dark
  seams: it spalls plate by plate under beam fire and is re-plated. Plate size
  differs by part (pg_finish.HEX_M, metres: main ring 4.0, aft body 3.6, belt and
  pods 3.0, bow module 2.6, horns and lobes 2.4, prow and deck 2.2, bridge 1.8)
  and shrinks to 0.6x on steep faces; each object carries it as `hex_u`. Each
  point takes its dominant projection plane's plates (no blended fragments).
  Machinery is blue-black ceramic with pale ceramic caps. Procedural, triplanar.

Bridge and under-bridge (pg_bridge; owner, pass 3)
  Read off the sprite's command core at 7x: the nose's sensor slot and crossbar,
  longitudinal ribs and a centreline boss on the forward tier, the bridge tower's
  wraparound glazing, vent cluster, comms mast and sensor dish, chevron fins on
  the neck and a vent array on the spine. Under it, the gantry stops at the bore's
  edge (pg_hull.GANTRY_FRONT_X, on the lining's flat top) so the deck's nose
  cantilevers clear over the bore, raised above the loop: keel beam, transverse
  frames, pipe runs, downlights and a glazed observation blister looking down
  into the bore. The gantry's bulkhead stands on the lining up to the deck's
  underside: full-height ribs carrying the deck, plating panels between them, an
  observation gallery's window band and an airlock at its foot; its flanks carry
  struts leaning aft, a pipe bundle and machinery units. Windows sit flush on the
  vertical walls at the foot of each tier in dark frames (never on a chamfer), and
  the tower's glazing is one band round its vertical wall with dark mullions.

Guns (turret kit; owner, pass 3: the mounts read empty)
  Each turret carries its gun from the shared turret kit
  (../turret-kit/turret_kit.py), matched to the game's weapon sprite for the
  Elite fit and sitting low in its dimpled well: tachyon lances (WS 003/004),
  graviton beams, heavy needlers (WS 007/008), tactical lasers, point-defence
  lasers (WS 011/016/017/022/023: armoured collars kept red, power cabling in
  the hull's dark machinery, the emitter on a gimbal elevating -5 to +75
  degrees, recorded per slot as elevation_limits_deg) and reaper launchers.
  The bow hardpoints WS 001/002 carry the kit's autopulse lasers
  (tk.build_hardpoint): the breech on a sled in its bay, which is now cut
  through the belt as well as the prow, and the bulb, finned flange and long
  barrel running out past the prow as the game draws them. The manifest's
  dimensions are the hull's; with_guns adds the barrels (346 m against 312 m). pg_turrets asks
  the kit for each gun at its nominal ring (one sprite pixel a unit, as the game
  draws it) with hull_finish=True and sprite_tint=.5, so the guns wear the hull's hex
  ceramic mixed halfway toward each sprite's colour,
  blue plate, slate and lamps, and drops it to just under the well's rim. The
  kit's review sheets score each against its sprite (silhouette IoU 0.91-0.95).
  The kit hangs Weapon WS nnn elevation on the pivot and Weapon WS nnn muzzle
  anchor 1 on that, at the .wpn's fire offset.

Stern and belly (pg_engines, pg_belly; owner, pass 3)
  The drives are faceted blocks standing out of the pods' stern faces, the
  sprite's castellation, with radiator louvres across their tops and
  chamfered-rectangle nozzles (tall slots on the small drives, broad mouths on
  the big ones). The belly, which the sprite never shows, carries what a
  beam-armed capital needs underneath: twelve radiator banks round the main
  ring, finned radiator panels under each drive pod, a lit strake ring on the
  belt's underside, docking ports with iris doors under the aft body and pods,
  sensor blisters under the prow and bow quarters, and a keel with lights.

Bore machinery (pg_bore; owner, pass 3)
  The bore is the soft, exposed part of the ship. Read off the sprite's lining
  band at 8x: courses of curved plate segments in light, mid and dark greys
  (some with raised sub-panels, a seeded share missing to show pumps and junction
  boxes behind), pipe runs between the courses, conduits dropping over the lip
  every 22.5 degrees, a rim conduit on the flat top, capacitor drums and pump
  boxes on the top between the emitter nodes, and blue-white status lights. All
  detail; none of it counts as enclosed volume. The hangar mouth is cut through it.

Service fields (pg_service; owner, pass 3)
  Where the plating is left off: shallow recesses following the plate (floor
  parallel to the fitted surface plane, walls showing the substrate) with
  rounded-rectangle crew hatches (2.6 x 1.6 m, glowing seam, split line, status
  light), iris hatches, vent banks and radiator fins. Fields on the main ring's
  crown fore and aft, on each pod top and at the command deck's after end; a
  field that would meet a turret's dimple is left out.

Armour and machinery character study (study_armour.py, compose_study.py)
  Three energy-resistant ceramic treatments on the built model, same cameras:
  A thermal tiles (2.4 m grid; graphite and titanium machinery), B hex ablative
  (3.5 m crowned hexagonal plates; blue-black ceramic machinery), C refractive
  panels (8 x 4 m glazed thin-film ceramic with glowing seams; black-glass
  machinery). Procedural and triplanar in true metres; the study never saves the
  .blend. The owner's pick becomes pass 6's finish reference.
    sh ../../blender.sh --background <built .blend> --python study_armour.py -- --out DIR
    python3 compose_study.py --dir DIR

Scale (pass 4; owner override, 2026-10-18)
  Enclosed volume (SOLIDS: belt, main ring, prow, bow module, horns, lobes, deck
  tiers, spine, bridge, aft body, pods; never the open bore, the shield emitter
  lining, the deck gantry, drives, turrets, domes or detail) 2.48 M units3 against
  1,000 crew, 300 cargo, 300 fuel: the rule solves 0.748 m per unit (271 m). The
  owner wants it larger than the Onslaught (301 m), so 0.87 is published by
  override: 315 m long, 277 m across, 73 m deep. The hull was first slimmed to the
  Legion's and Onslaught's fullness (mean depth over plan 33.9 units against 28.4
  and 37.4): the belly is a shallow armoured plane, the tops unchanged. The driver
  re-measures the volume every build and fails if it drifts from ship_scale.HULLS;
  OVERRIDE in build_paragon.py records why the published value differs from the
  solve. Person-scale fittings are in true metres (window slits 2.0 x 0.9 m,
  bridge glazing band 1.8 m). A 1.8 m person is 2.1 units; shield radius 235 m.

Modules (one per area)
  pg_kit       materials (Tri-Tachyon palette, glow), primitives, smooth_mass
               (heightfield masses with holes), revolves, booleans, surface()
  pg_outline   the sprite's outline as half-widths (generated)
  pg_hull      belt, main ring, bore lining, prow with its two bays, bow module,
               horns, command deck tiers on their gantry, aft body, pods, reactor
               domes, aft ribs, bow-quarter grooves
  pg_engines   the ten drive units
  pg_turrets   21 turrets (barbette + pivot) and the two bow hardpoints
  pg_shield    the shield emitter anchor and the emitter nodes on the lining
  pg_arcs      firing-line check across every turret's arc
  pg_detail    pass 2 detail: insets, dimples, niches, grille, glow, seams, windows
  pg_hangar    the forward hangar in the bore wall, its door and anchors
  pg_finish    the hex ablative ceramic finish, machinery finish, substrate, plate sizes
  pg_service   service fields: uncovered plating with hatches and machinery
  pg_bore      the bore's exposed machinery: plate courses, pipes, conduits, units, lights
  pg_bridge    the bridge's detail and the under-bridge: nose underside, gantry face and flanks
  pg_belly     the belly: radiators, pod radiators, strake, docking ports, blisters, keel

Axes and units
  +X bow, +Y port, +Z dorsal. One sprite pixel per unit about the .ship centre:
  sprite col c, row r -> X 174-r, Y 164-c; slot locations are model XY unchanged.

Names (identifiers: later passes key off them)
  Hull belt / Hull main ring / Hull bore lining / Hull prow (+ bay parts) /
  Hull bow module / Hull <side> bow horn / Hull core trench / Hull command deck
  (+ upper, spine) / Bridge tower / Bridge glazing / Hull aft body / Hull <side> pod /
  Hull <side> reactor well / dome / Hull <side> bow lobe / Hull <side> bow groove
  Weapon WS nnn well (+ floor), barbette, Weapon WS nnn pivot (empty; rotate about local Z to train),
    the kit's Weapon WS nnn house <material> parts, Weapon WS nnn elevation (empty) and
    Weapon WS nnn muzzle anchor <k>; hardpoints: Weapon WS 00n hardpoint (anchor), housing,
    cradle, emitter, muzzle
  Engine i drive pod / aft-facing bell / mouth lip / dark throat; Engine i exhaust (empty)
  Detail ... (pass 2 parts), Hull <side> reactor glow k, Hull main ring seam light
  Bridge ... (nose sensor, ribs, boss, glazing, vents, mast, dish, chevrons, spine vents),
    Under bridge ... (keel beam, frames, downlights, pipes, blister), Gantry face ..., Gantry <side> ...
  Bore plate <course> <k> (+ panel), Bore pipe <k>, Bore conduit <k>, Bore rim conduit, Bore drum/pump <k>,
    Bore gap <course> <k> pump/box/light, Bore light <course> <k>
  Service <side> <field> ... (floor, hatch seam/plate/split/status light, iris, vents, fins)
  Hangar ... (floor, roof, walls, lights, frame, door leaves and slides, mouth, launch point),
    Hull forward hangar (the bay's air, unrendered; counted volume)
  Shield emitter (empty at shieldCenter; radius, arc, type as custom properties),
    Shield emitter node k (empties facing the bore axis)
