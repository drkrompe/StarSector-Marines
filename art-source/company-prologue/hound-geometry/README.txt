HOUND (A) - CINEMATIC GEOMETRY BLOCKOUT

Purpose
An editable Blender blockout of the vanilla Hound (A), a second cinematic
ship beside the Second Wind. It fixes the silhouette, sections, mass and
weapon/engine slots only. It has no fine detail, damage or surfacing yet.
It follows the Second Wind package's conventions (../second-wind-geometry/,
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
art; the game gives none. No metre scale is claimed.
Model extent: X -26.0..43.1, Y -30..30, Z -7.1..10.1.

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

Silhouette check
compose_review.py lays the sprite into the top camera frame and compares
its alpha with the render's alpha. IoU 0.940: 2.1% of the sprite is
uncovered and the model adds 4.1% outside it. Most of the excess is the
WS 001 gun, which the hull sprite does not draw, and the capsule cage's
proud rings. top-overlay.png shows the sprite at 50% over the render:
red = sprite not covered, cyan = model outside the sprite.

Stable object names (for later damage, surfacing and VFX layers)
  Hull ...                    cargo body, deckhouse, hatch ribs, spar, skids
  Fork ...                    starboard bow gun fork
  Bridge ...                  capsule, nose, glazing bands, cage
  Engine <i> <side> ...       pod sections, dorsal vent, engine can, exhaust bell
  Engine <i> <side> exhaust anchor   empty at the engine slot, +X pointing aft
  Weapon <slot id> ...        mount geometry
  Weapon <slot id> anchor     empty at the slot, +X along the slot angle
The hull parts the turret seats on are found by downward ray-cast
(hd_kit.Kit.surface). Booleans use the MANIFOLD solver.

Files
build_hound.py: builds the scene, cameras and renders; runs in Blender.
  hd_hull.py, hd_engines.py, hd_bridge.py, hd_fork.py, hd_mounts.py: one
  module per area. hd_kit.py: materials and primitives.
compose_review.py: top overlay, IoU, review sheet (Python + Pillow + numpy).
hound-geometry.blend: saved Blender 5.2 scene. The three-quarter camera is active.
geometry.json: identity, slots, engines, dimensions, cameras, IoU.
top.png, top-overlay.png, port-side.png, front.png, three-quarter.png,
  stern.png: 1920x1080 review renders. Every view renders the same objects.
top-alpha.png: the transparent top render the IoU is measured from.
hound-geometry.reference.png: 1920x1080 review sheet.

Rebuild from this folder
"/c/Program Files/Blender Foundation/Blender 5.2/blender.exe" --background --factory-startup --python build_hound.py
python compose_review.py
Options after "--": --out DIR, --views a,b, --samples N, --res WxH,
--no-save. The build takes about 50 s at 24 samples.

Shipping
Source-only reference art. Nothing here enters the mod or changes gameplay.
