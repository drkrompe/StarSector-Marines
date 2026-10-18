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

The shape gauntlet (run every iteration)
  build_paragon.py --views top-classes, then python3 gauntlet.py --dir DIR:
  scores each tone's IoU against the sprite (gauntlet.png) and draws the model's
  part edges over the sprite (gauntlet-edges.png). Silhouette IoU alone says
  nothing about the parts inside the outline.

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

Axes and units
  +X bow, +Y port, +Z dorsal. One sprite pixel per unit about the .ship centre:
  sprite col c, row r -> X 174-r, Y 164-c; slot locations are model XY unchanged.

Names (identifiers: later passes key off them)
  Hull belt / Hull main ring / Hull bore lining / Hull prow (+ bay parts) /
  Hull bow module / Hull <side> bow horn / Hull core trench / Hull command deck
  (+ upper, spine) / Bridge tower / Bridge glazing / Hull aft body / Hull <side> pod /
  Hull <side> reactor well / dome / Hull <side> aft rib / Hull <side> bow groove
  Weapon WS nnn barbette, Weapon WS nnn placeholder (+ stub), Weapon WS nnn pivot (empty; rotate about local Z to train),
    Weapon WS nnn muzzle [k]; hardpoints: Weapon WS 00n hardpoint (anchor), housing,
    cradle, emitter, muzzle
  Engine i drive pod / aft-facing bell / mouth lip / dark throat; Engine i exhaust (empty)
  Shield emitter (empty at shieldCenter; radius, arc, type as custom properties),
    Shield emitter node k (empties facing the bore axis)
