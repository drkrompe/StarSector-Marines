HOUND (A) - CINEMATIC GEOMETRY (PASS 3: OWNER REVIEW ROUND 1)

Purpose
An editable Blender model of the vanilla Hound (A), a second cinematic ship
beside the Second Wind, carried through the ship-model-pipeline skill's
passes (.claude/skills/ship-model-pipeline). Pass 1 (blockout) fixed the
silhouette, sections, mass and slots; it is kept in reference-history/
v1-blockout/. Pass 2 adds detail inferred from the sprite and lore, described
below. There is no damage or surfacing yet. It follows the Second Wind package's conventions (../second-wind-geometry/,
../second-wind-pipeline.md) but shares no code with it. hd_kit.py holds
copies of the helpers it needs.

Hull identity
"Hound (A)" is the skin hound_hegemony (data/hulls/skins/hound_hegemony.skin)
over the base hull hound (data/hulls/hound.ship; ship_data.csv row "hound",
Combat Freighter, Low Tech). It is on the Hegemony auxiliary list:
heg_aux_bp, 4 FP, 42 OP, value x1.5. Sprite:
graphics/ships/hound/hound_hegemony.png, 62x72, .ship centre (31, 27), the
same outline as hound_base.png. FRIGATE, LOW_TECH, no shield, system
flarelauncher. Built-in hullmods: rugged and shielded_holds (base hull),
armoredweapons (skin). There are no built-in weapons.

Coordinates
+X = bow, +Y = port, +Z = dorsal. One sprite pixel is one unit about the .ship
centre, so slot and engine XY from hound.ship are used unchanged. Sprite
column c, row r covers X 44-r..45-r and Y 30-c..31-c. Heights are proposed
art; the game gives none.

Scale (pass 4): 0.88 m per unit
One real-world scale, METRES_PER_UNIT in build_hound.py and metres_per_unit
in geometry.json. Every metric value (VFX sizes and speeds, lamp falloff,
cameras, depth of field, flight speeds, texel density) reads it; nothing
hard-codes metres. She is about 61 m long, 53 m across the pods and 18 m
from keel to mast top; a 1.8 m person is 2.05 units tall.
The figure comes from what she carries, by the Second Wind method. The mod's
deck program for a FRIGATE "Combat Freighter" with 10-40 crew and 75 cargo
(.claude/skills/ship-model-pipeline/DeckProgramProbe.java) is 2,427 m2 of
rooms; x1.40 circulation x3.2 m deck height = 10,873 m3. The pressure hull
(body, deckhouse, spar, bridge capsule and the drive pods' casings, as the
Second Wind's unioned hull counted its pods) measures about 28,960 cubic
units; the build re-measures it and records the solved value. At 55% usable
that needs 0.88 m per unit. Plausible range: 0.83 (65% usable, cramped) to
1.07 (pods hold no crew space). Fittings agree at 0.88: the bridge airlock
hatch is 1.7 x 2.1 m, viewports about 0.9 x 1.0 m, the stern cargo doors
9.7 x 4.8 m (wide enough for the program's 16 x 10 m boat bay's gig), and
the body 9.7 m keel to deck, three 3.2 m decks. She carries the same
program as the Cerberus and comes out a little smaller than the Second Wind
(66 m), as a frigate should. The battle-map landed-ship convention
(landed-ships-true-scale.md) serves tactical readability and is a different,
smaller number.
Model extent: see geometry.json "dimensions" (about 69 x 60 x 21 units).

Slots (verified against the installed hound.ship on every build)
  id      mount      size    type       location   angle  arc
  WS 001  HARDPOINT  MEDIUM  BALLISTIC  (35, -6)   0      5
  WS 002  TURRET     SMALL   BALLISTIC  (-4, 4)    145    290
  WS 003  HIDDEN     SMALL   SYSTEM     (-19, 0)   180    90   flare launcher
Engines: two, LOW_TECH, width 16, length 80, contrail 64, angle 180, at
(-25, 22) = Engine 1 (port) and (-25, -22) = Engine 2 (starboard), Z 0.
The build fails if the install's slots differ from these constants.
Each mount rests at its slot angle. The guns are generic placeholders sized
by slot. WS 001 sits between the fork tines. WS 002 is trained aft-to-port on
an armoured barbette (Armored Weapon Mounts). WS 003 is a flush four-tube
dispenser on the aft bulkhead in the stern notch.

Reading of the sprite
- Cargo body: the box between the pods, +-13 forward of the spar and +-14
  aft of it. A raised armoured deckhouse with hatch ribs stands over the
  forward hold (the ribbed block; read as the shielded holds).
- Engine spar: the transverse beam the pods hang on (rows 41-51).
- Drive pods: whole drive units, not nozzles in the hull. Each is a domed
  intake cap, a forward casing, an orange armour collar (the widest band,
  with the dorsal vent ring the sprite shows), an aft casing with a
  reinforcing band, a thrust housing and one twelve-sided engine can. The
  bell is sunk into the can's aft face. Seams open onto a dark core.
- Bridge capsule (port bow): a twelve-sided capsule with a cream glazed nose
  (the lit lamps) inside an elliptical crash cage. The cage is four ring
  frames at the sprite's proud rows and five fore-aft bars.
- Gun fork (starboard bow): the H around WS 001. A base, two tines with twin
  points and raked top/chin faces, and a cross bar over the gun gap.
- Stern skids: two U-shaped aft legs either side of the flare notch.
Sections are flat-shaded chamfered prisms and octagons: one keel chamfer,
one wall and one shoulder chamfer, plus single plane facets where the sprite
reads angled.

Pass 2 detail (inferred from the sprite at 14x, its lights layer, and lore)
Lore: an old, durable, shieldless frigate that ferries cargo, often
valuable and often illegal; the (A) is on the Hegemony auxiliary list,
serviced to military standard. So: old low-tech construction, well kept.
Every lamp is where hound_lights.png shows light.
- Hull (hd_hull): segmented deck plates over a dark seam underlay, the
  sprite's lighter spar plates, a four-segment dorsal spine (cols 34-36)
  doubling as the aft cargo hatch beam, bolted straps at the sprite's
  bands, an aft cargo hatch, octagonal service covers, a turret ring
  coaming. The deckhouse carries cream ribs on its port half, three amber
  lamps, starboard hold hatches and a crew hatch. A brass coolant loop runs
  round the deckhouse front and down the starboard edge (rows 25-34). Flank
  armour modules with seams, a recessed stern with clamshell cargo doors
  either side of the flare dispenser, skids with landing rams, pads and
  RCS blocks, and a plated, framed underside with a ventral hatch.
- Engines (hd_engines): intake dome with grille and sensor eye, plated
  cowl and collar with seam grooves, bolted side skirt, louvred heat-vent
  wells glowing orange at the four lights-layer points, radiator bay and
  keel ribs, clamp band with actuator boss, shroud bands, a gimbal pocket
  with twelve nozzle petals and rams, fuel and coolant lines from the body,
  maintenance hatches. The bell stays sunk in the can.
- Bridge (hd_bridge): banded armour sleeves over dark seams, a mullioned
  canopy under an armoured visor brow, six framed viewports at the lamp
  pairs, a dorsal airlock, a sensor mast with array and dish, whip
  antennas, navigation lamps, clamp blocks and tine saddles on the cage,
  and a strut yoke with conduits to the hull face.
- Fork and mounts (hd_fork, hd_mounts): channel-section tines with dark
  webs, ribs and cream tip caps, an I-beam cross bar with a targeting
  sensor, a gun trough with cradle rails, plated base with hatches.
  WS 001 is the shared turret kit's chaingun hardpoint (2026-10-20; it
  replaced the hull's own naval gun and the Hound (P)'s own chaingun):
  tk.build_hardpoint('chaingun') at k 0.5 on the empty 'Weapon WS 001
  mount' (X 24.95, axis Z 0.2), sunk in a bay cut from the fork base and
  the tine roots (hd_mounts.BAY; the old trough fittings are removed). The
  six-barrel cluster rides 'Weapon WS 001 rotor' inside 'Weapon WS 001
  recoil 1'; one muzzle, 'Weapon WS 001 muzzle anchor 1', on the cluster
  axis at X 39.45. Both states carry it. turret-kit/check_hardpoints.py
  must report it clear. WS 002 is a small naval turret: bolted armour
  collar, glacis, mantlet and trunnions, cupola with vision blocks, sight
  and ready-round box. WS 003 is an armoured, hazard-framed shutter with a
  4x3 tube grid. Both guns pass a BVH clearance check on every build.
The four modules were detailed in parallel by Opus subagents (the project
asks for Sol 6.1, which this harness does not offer) and integrated here.
Integration found two cross-module clashes the agents could not see (the
feed chute through the coolant loop, a yoke foot on its flange), now fixed;
check_clashes.py lists every cross-module overlap for the next round.

Pass 3, review round 1 (owner: "needs work on the 'bridge box' ... windows /
bridgify", and "a modeled hatch on the back underside ... given it's a small
freighter")
- The bridge is the deckhouse forward of the turret (hd_wheelhouse, new
  module; the deckhouse solid stays 'Hull shielded hold deckhouse' in
  hd_hull because WS 001's feed chute ray-casts against it). Its forward
  face is now a window wall, vertical to the sill and leaning back 20
  degrees above it. A raked window band with heavy mullions, a header and a
  sill sits under an overhanging visor brow; the starboard end stays armour
  round an armoured feed port where WS 001's belt chute leaves the hold.
  Window bands wrap both side walls forward, two small ports flank the aft
  door. The roof carries a rail (gap at the aft ladder), a lattice sensor
  mast with yard, nav lamps, radome and whip, a searchlight and a dish. The
  aft hatch is now a person-height door (1.4 x 2.0 m) with a step; a tread
  walkway runs from it round the port side behind a deck-edge rail. The
  side vents moved aft to make room for the windows.
  The forward capsule keeps its 'Bridge ...' object names (names are
  identifiers); read it as the forward flight-control and observation
  capsule, the deckhouse as the bridge proper.
- Aft ventral cargo ramp (hd_hull 'Hull aft ventral ramp ...'): an opening
  under the stern between the skids, 7.4 x 8.3 m, into a lit well up to the
  lower hold, framed with hazard bars and four jamb lights. The ramp (7.0 x
  7.9 m, treads inside, stiffeners outside, a toe plate, hinge barrel and
  two actuators) is hinged on the opening's forward edge and lowers its aft
  end, so loads come in from astern between the skid legs. All ramp parts
  are parented to the empty 'Hull aft ventral ramp hinge': animate its
  local Y rotation, negative to open. Its custom property
  ground_contact_deg (6.5) is the angle at which the toe meets the ground
  under the landing pads. The actuators swing rigidly with the ramp; a rig
  can split them into cylinder and rod later. The keel plates, keel beam,
  keel frame and dark underlay were cut back round the opening.
- Scale check: build_hound.py --figure adds a 1.8 m mannequin (2.05 units)
  on the bridge walkway for review renders; --ramp-open DEG lowers the ramp.
  Neither is saved in the committed .blend.
- IoU unchanged at 0.942: nothing new leaves the existing plan outline.

Silhouette check
compose_review.py lays the sprite into the top camera frame and compares
its alpha with the render's alpha. Pass 3: IoU 0.942 (pass 2: 0.942, pass 1: 0.940);
1.7% of the sprite is uncovered and the model adds 4.4% outside it. Most of
the excess is the WS 001 gun, which the hull sprite does not draw, and the
capsule cage's proud rings. top-overlay.png shows the sprite at 50% over the render:
red = sprite not covered, cyan = model outside the sprite.

Stable object names (for later damage, surfacing and VFX layers)
  Hull ...                    cargo body, deckhouse, hatch ribs, spar, skids
  Fork ...                    starboard bow gun fork
  Bridge ...                  capsule, nose, glazing bands, cage
  Engine <i> <side> ...       pod sections, dorsal vent, engine can, exhaust bell
  Engine <i> <side> exhaust anchor   empty at the engine slot, +X pointing aft
  Weapon <slot id> ...        mount geometry
  Weapon <slot id> anchor     empty at the slot, +X along the slot angle
  Wheelhouse ...              bridge glazing, brow, rails, mast, walkway
  Hull aft ventral ramp hinge empty; animate local Y (negative opens)
The hull parts the turret seats on are found by downward ray-cast
(hd_kit.Kit.surface). Booleans use the MANIFOLD solver.

Pirate state (Hound (P))
--state pirate builds the clean ship, then hd_pirate.py adds the Hound (P):
the skin hound_d_pirates (same hull and slots, sprite hound_pirates.png,
"heavily modified - and poorly maintained - by its pirate crew") with the
hound_d_pirates_Overdriven guns. The sprite's outline is the Hegemony one;
its large dark areas over formerly-orange armour are read as welded-on scrap
armour. --state clean (the default) is the committed Hound (A), unchanged.
- Armour: hd_pirate.SLABS, 17 thick (0.35-0.62 m), skewed, torch-cut slabs
  with cut corners and a notch, in the sprite's dark areas: starboard deck
  1-5 (deck, deckhouse roof and a doubler), aft deck 1-3, port pod 1-3
  (top, outboard chamfer, one on the side over the skirt), starboard pod 1-2,
  bow neck 1 (the capsule's aft sleeve between the cage bars), spar 1-3.
  Each is bedded by ray-cast (Kit.surface for deck slabs, the same cast along
  Y for pod sides): flat blocks fitted to the surface, or bent plates folded
  to it. Objects: 'Pirate armour <area> <k>' plus '... weld' (a fillet
  bead round its foot), '... bolts', '... lugs' on three; starboard deck 1
  and port pod 2 have a cut-out showing the original plate. Material
  'Pirate scrap armour'; welds 'Recess and machinery'.
- Guns (hd_mounts.build(kit, hd_pirate.GUNS) replaces only WS 002's barrel
  group; WS 001 is the kit chaingun of the clean state, see above).
  WS 002 twin light MG: 'Weapon WS 002 twin cradle', barrel 1-2,
  perforated barrel jacket 1-2, muzzle brake 1-2 and muzzle bore 1-2 (no
  plain '... muzzle brake'); trained with the house above the traverse gap.
  WS 003 unchanged. Absent in this state: WS 002 barrel, barrel jacket and
  muzzle brake.
- Scars: hd_pirate.SCARS, light wear, stable ids HD01-HD11, each placed by a
  ray-cast: scorches HD01 (port pod skirt), HD05 (deckhouse front, feed-port
  armour), HD07 (deck forward of the turret), HD09 (bridge capsule aft
  sleeve), HD11 (starboard pod collar); pocks HD02 (port pod skirt), HD03
  (port pod intake cap), HD06 (deckhouse front), HD10 (port pod collar top
  plate), each with a soot ring; gouge HD04 (port pod collar shoulder);
  patch HD08 (crude plate over an old hole in the port pod reinforcing
  band). Objects 'Scar HD<nn> scorch|patch plate|patch weld|patch bolts';
  pocks and the gouge are a live Boolean 'Scar cuts' on the base object they
  hit (cutter 'Scar cutter - <object>', hidden), as the Second Wind does.
  Materials 'Scar scorch', 'Scar bare metal' (the cut walls).
- Checks printed on every pirate build: gun overlaps (CLASHES, as clean),
  armour against every gun, and what each slab is bedded in.
- Build (never into this folder; the tracked .blend and geometry.json are
  the clean asset's, and the driver refuses):
  sh ../../blender.sh --background --factory-startup --python build_hound.py -- \
     --state pirate --out <dir> --views top,port-side,front,three-quarter,stern,fork-detail,turret-detail
  python compose_review.py --dir <dir>
  <dir>/hound-geometry.blend and <dir>/geometry.json (with states.pirate:
  armour list, guns, replaced parts, the scar table, checks). Extra pirate
  close-ups: armour-detail, armour-starboard, chaingun-detail,
  twin-mg-detail.

Files
build_hound.py: builds the scene, cameras and renders; runs in Blender.
  hd_hull.py, hd_wheelhouse.py, hd_engines.py, hd_bridge.py, hd_fork.py,
  hd_mounts.py, hd_pirate.py (the Hound (P) state): one
  module per area. hd_kit.py: materials and primitives.
compose_review.py: top overlay, IoU, review sheet and the pass-2
  before/after sheet (Python + Pillow + numpy).
check_clashes.py: cross-module overlap sweep; run in Blender on the .blend.
hound-geometry.blend: saved Blender 5.2 scene. The three-quarter camera is active.
geometry.json: identity, slots, engines, dimensions, cameras, IoU.
top.png, top-overlay.png, port-side.png, front.png, three-quarter.png,
  stern.png: 1920x1080 review renders. Every view renders the same objects.
bow-, bridge-, fork-, turret-, engine-, stern-detail.png: close-ups.
pass2-before-after.png: the close-ups against reference-history/v1-blockout.
pass3-before-after.png: the close-ups, the bridge close-up and the
  underside view against reference-history/v2-detail (the pass-2 state).
wheelhouse-detail.png, underside-aft.png: the bridge and the closed ramp.
wheelhouse-figure.png, underside-aft-open.png: the bridge with a 1.8 m
  scale figure, and the ramp lowered 25 degrees.
top-alpha.png: the transparent top render the IoU is measured from.
hound-geometry.reference.png: 1920x1080 review sheet.

Rebuild from this folder
sh ../../blender.sh --background --factory-startup --python build_hound.py
python compose_review.py
Options after "--": --out DIR, --views a,b ('details' = all close-ups),
--samples N, --res WxH, --no-save, --cam NAME:PX,PY,PZ:TX,TY,TZ:SCALE,
--state clean|pirate.
The committed set is --views top,port-side,front,three-quarter,stern,details
(about 2 minutes at 24 samples).

Shipping
Source-only reference art. Nothing here enters the mod or changes gameplay.
