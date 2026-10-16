BROADSWORD - CINEMATIC GEOMETRY (PASS 2: PLAUSIBLE DETAIL)

Purpose
An editable scripted Blender model of the vanilla Broadsword heavy fighter,
built for the Jangala cinematic's shot C2, where the Hegemony Legion's air
wing launches from and recovers to its flight decks. It follows the
ship-model-pipeline skill (.claude/skills/ship-model-pipeline). Pass 1
(blockout) is kept in reference-history/v1-blockout/. Pass 2 adds the
detail the sprite and its up-res master imply. Pass 3 (owner review) is
next; there are no damage states, textures or VFX yet. It copies the Hound
kit helpers into bw_kit.py and imports no other hull's modules.

Hull identity (read-only install, starsector-core)
- data/hulls/broadsword.ship: hullId broadsword, FIGHTER, LOW_TECH, sprite
  graphics/ships/broadsword.png 30x33, centre (15, 13), no shield.
- data/hulls/ship_data.csv row broadsword: Heavy Fighter, crew 1/1, system
  flarelauncher_fighter.
- data/variants/fighters/broadsword_Fighter.variant: lightmg in WS 001/002,
  linked; missile slots WS 003/004 empty.
- data/hulls/wing_data.csv broadsword_wing; data/variants/legion/
  legion_xiv_Elite.variant flies two Broadsword wings (and two Hoplon).
- No lights layer exists for this hull, so there are no emissives.
- Detail reference: art-source/aircraft/fighter-upres/broadsword/master-v1.png.

Coordinates and names
+X = nose, +Y = port, +Z = dorsal. One sprite pixel is one unit about the
.ship centre, so slot and engine XY from broadsword.ship are model XY.
Sprite column c, row r (from the top) covers X 19-r..20-r, Y 14-c..15-c.
Heights are proposed art; the game gives none.

Append the collection "BROADSWORD | geometry" (143 objects: 133 meshes,
10 empties, no images, no linked datablocks). Every object is parented to
the empty "Broadsword root" at the .ship centre (the game's rotation
pivot), which carries custom properties metres_per_unit and axes. Move,
turn or scale the root; nothing else needs touching. Cameras and lights
live in "BROADSWORD | review rig" and are not part of the asset.

Anchors (ARROWS empties; local +X points along the slot angle):
- Engine 1 port exhaust anchor (-11.9, 5.5, -0.7) and Engine 2 starboard
  exhaust anchor (-11.9, -5.5, -0.7): at the mouth-lip plane on the bell
  axis, +X pointing aft. The exhaust direction is model -X.
- Weapon WS 001 anchor / WS 002 anchor (-5, +-10.5, 0.35): the hidden gun
  slots; Weapon WS 001 muzzle / WS 002 muzzle (9.1, +-9.4, 0.35): the
  barrel tips, where muzzle flash and tracers start.
- Weapon WS 003 / WS 004 anchor (-4, +-6.5): the empty missile slots.
- Weapon WS 005 anchor (-6, 0): the flare launcher, +X aft.
Engine parts follow the Second Wind names: Engine {i} aft-facing bell,
Engine {i} mouth lip, Engine {i} dark throat (i = 1 port, 2 starboard),
plus Engine {i} <side> nacelle and collar parts. Other prefixes: Hull,
Canopy, Wing <side>, Weapon <slot id>, Flare launcher WS 005.

The .ship slots are integers about a centre line that falls between two
pixel columns (11/-10, 7/-6, engines 6/-5). The model and its anchors are
symmetric at the mean (10.5, 6.5, 5.5); geometry.json lists both.

Scale: 0.50 m per unit (range 0.43-0.57), her own
Every hull gets its own real-world scale from what it physically holds
(owner policy). A fighter has no deck program, so the Broadsword is
scaled by her cockpit: one pilot under a canopy 6.1 units long, against
real single-seat canopies of 2.6-3.5 m (0.43-0.57 m per unit); 3.05 m
gives 0.50. METRES_PER_UNIT in build_broadsword.py and metres_per_unit in
geometry.json are the one number.
At 0.50 she is 15.5 m long, 14.1 m across the blades and 3.3 m keel to
canopy (an F-16 is 15.1 m, an F/A-18 17.1 m). A seated 1.8 m pilot (seat
pan 0.55 m below the deck skin, crown 0.92 m above the seat) clears the
glass by 0.21 m. Each exhaust bell is 1.8 m across. The canopy is broad,
1.9 m at the sill against 0.9-1.3 m on real aircraft: the sprite's wide
glazed hood, not a reason for a tighter scale.
Beside a hull at another scale, scale the root by 0.50 / that hull's
metres_per_unit: 0.77 beside the Legion (0.65). Her span then clears the
Legion's forward hangar mouth (23.4 m) easily, but the aft mouth (14.3 m
wide, 7.8 m high) by only 0.2 m. The hangars are under the wing arms and
fighters leave only through the mouths, so an aft launch must fly
straight out.
The battle-map landed-ship convention (landed-ships-true-scale.md) is a
different number.

Silhouette
Top view over broadsword.png: IoU 0.931 at pass 2 (sprite uncovered 3.6%,
model outside 3.5%); the blockout scored 0.927. The remaining misses are
edge slivers about half a pixel wide; the largest is the tail aft of the
strakes, where the sprite's diagonal edge is carried by the exhaust lip.
The sprite's 7..8 gap between the gun housing and the shoulders is kept.

Pass 2 detail (budget: hero-capable, not greeble density)
Long khaki nose slabs either side of a dark centre channel with an intake
grille, dark flank strakes, canopy shoulder plates with cooling slots, a
framed two-pane canopy, a dorsal plate and a slatted spine grille, the
three-tube flare launcher, khaki-over-steel gun housings, blades and
strakes with edge rails, breech blocks with grilles and feed covers,
banded barrel casings and muzzle brakes, nacelle collar ribs, top grilles
and inboard armour, bright exhaust lips, bells and throats, and bolts.
check_clashes.py lists only deliberate embedding (plates and parts sunk
into the structure they attach to).

Files
build_broadsword.py driver; bw_kit.py helpers; bw_hull.py nose, fuselage,
canopy, spine; bw_wings.py gun housings, blades, strakes; bw_guns.py guns
and slot anchors; bw_engines.py nacelles and bells; compose_review.py
overlay, IoU and sheets; check_clashes.py overlap sweep.
broadsword-geometry.blend, geometry.json (manifest: slots, engines,
anchors, scale, cameras, silhouette).
Renders (1920x1080): top.png, top-alpha.png, top-overlay.png,
port-side.png, front.png, stern.png, three-quarter.png, space-sun.png (hard
sun from high aft, black space), nose-, cockpit-, gun- and engine-detail.png,
broadsword-geometry.reference.png (sheet), pass2-before-after.png.

Rebuild (from this folder; render processes one at a time)
  "/c/Program Files/Blender Foundation/Blender 5.2/blender.exe" --background --factory-startup --python build_broadsword.py -- --views top,port-side,front,stern,three-quarter,space-sun,details
  python compose_review.py
Blockout: add --blockout --out reference-history/v1-blockout, then
  python compose_review.py --dir reference-history/v1-blockout
and delete the .blend it writes there. Options: --views, --cam, --samples,
--res, --no-save (see the driver's docstring).

Limits
- Heights, sections and the underside are proposed; only the plan comes
  from the game.
- Flat materials only: khaki, gunmetal, bright steel, dark recess and
  canopy glass. No textures, wear or decals.
- No damage states, engine glow or plume; the anchors are where they go.
- The Hegemony XIV livery is not applied: vanilla Broadswords keep the
  low-tech khaki.
