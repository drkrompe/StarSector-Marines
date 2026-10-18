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

How the hull reads (owner, pass 1)
  Nested rings round an open bore, read off the sprite: the dark bore lining
  (r 57-71, the shield emitter ring), the deep main ring (r 61-115, the hull's
  backbone), and the lower armour belt out to the circular outer edge (r 136),
  with the groove between ring and belt at r 113 on the bow quarters. The prow is
  the heaviest armoured block and carries the two capital mounts (WS 001/002)
  recessed in bays in its face. The white command core is an arch: it springs
  from the stern behind the ring, crosses over the ring's aft band with clear
  space beneath, and lands its nose foot on the bore lining. Depth follows
  function: ring deepest, belt and pods lower, bridge arch tallest.

Modules (one per area)
  pg_kit       materials (Tri-Tachyon palette, glow), primitives, smooth_mass
               (heightfield masses with holes), revolves, booleans, surface()
  pg_outline   the sprite's outline as half-widths (generated)
  pg_hull      belt (Hull ring), main ring (Hull bore ring), bore lining, prow with
               its two hardpoint bays, command core (bridge arch), aft pods
  pg_engines   the ten drive units
  pg_turrets   21 turrets (barbette + pivot) and the two bow hardpoints
  pg_shield    the shield emitter anchor and the emitter nodes on the lining

Axes and units
  +X bow, +Y port, +Z dorsal. One sprite pixel per unit about the .ship centre:
  sprite col c, row r -> X 174-r, Y 164-c; slot locations are model XY unchanged.

Names (identifiers: later passes key off them)
  Hull ring / Hull bore ring / Hull bore lining / Hull prow / Hull core /
  Hull port pod / Hull starboard pod
  Weapon WS nnn barbette, Weapon WS nnn pivot (empty; rotate about local Z to train),
    Weapon WS nnn muzzle [k]; hardpoints: Weapon WS 00n hardpoint (anchor), housing,
    cradle, emitter, muzzle
  Engine i drive pod / aft-facing bell / mouth lip / dark throat; Engine i exhaust (empty)
  Shield emitter (empty at shieldCenter; radius, arc, type as custom properties),
    Shield emitter node k (empties facing the bore axis)
