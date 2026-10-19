PHAETON - CINEMATIC GEOMETRY (PASSES 1-2, 4, 5)

Purpose
An editable Blender model of the vanilla Phaeton tanker for the Asharu
battle cinematic (asharu-battle/asharu-battle-shot.md): the merchant
convoy's tanker, in the column in S1 and the first convoy ship lost in S6
("The screen breaks"), where Manticore (P) missiles hit her and she goes
up, seen mid-close at about 1-1.5 km, centre frame. It follows the
ship-model-pipeline skill (.claude/skills/ship-model-pipeline) and the
Second Wind's conventions (../second-wind-pipeline.md) but shares no code
with any other hull: ph_kit.py holds copies of the helpers it needs, and
ph_mounts.py's twin turret is the Shepherd's design, copied and scaled.

Hull identity (read-only install, starsector-core)
data/hulls/phaeton.ship (hullId phaeton; the .ship and hullId agree),
ship_data.csv row "phaeton": Tanker, Low Tech, DESTROYER, crew 10-20,
cargo 20, fuel 800, mass 400, OMNI shield (radius 160), system
flarelauncher, built-in civgrade. Sprite graphics/ships/tanker_medium.png,
120x234, .ship centre (60, 94). There is no lights layer for this hull.

Coordinates
+X = bow, +Y = port, +Z = dorsal. One sprite pixel is one unit about the
.ship centre, so slot and engine XY from phaeton.ship are used unchanged.
Sprite column c, row r covers X 139-r..140-r and Y 59-c..60-c. Heights are
proposed art; the game gives none. The build checks every slot, engine and
the centre against the installed phaeton.ship and stops on a mismatch.

What the sprite says (read at 6-8x)
- A long red tank hull (X -34..128) carrying three spherical cargo tanks
  on the centreline at X 0, 44 and 88, each about 30 units across: a
  Moss-type layout, the spheres standing half proud of the deck in their
  skirts. 800 fuel at 1.5 m3 a unit (ship_scale) is 1,200 m3; the three
  spheres hold about that at the published scale (scale.cross_checks_m).
- The bridge is the tan cab on the starboard bow shoulder; the raised red
  block on the port side amidships (X 54..84) with an amber strip on its
  crown is the crew gallery and its skylight.
- The grey lattice on the port bow is a hose-handling crane.
- Aft, a narrow machinery spine with a reactor dome joins the hull to the
  stern drive block: six LOW_TECH bells, two reaction-mass drums on its
  corners (red bands), and either side of the spine a vertical drive
  housing with a glowing crown port, ringed outboard by a tan guard arc
  whose tip carries a flare launcher.

Slots (exactly as phaeton.ship)
  WS 001  TURRET  SMALL BALLISTIC  (-75, 0)    angle 180, arc 150 -> 'Turret 1 WS 001 ...'
  WS 002  TURRET  SMALL BALLISTIC  (38, 38)    angle 90,  arc 180 -> 'Turret 2 WS 002 ...'
  WS 003  TURRET  SMALL BALLISTIC  (36, -34)   angle -90, arc 180 -> 'Turret 3 WS 003 ...'
  WS 004  TURRET  SMALL BALLISTIC  (126, 13)   angle 0,   arc 210 -> 'Turret 4 WS 004 ...'
  WS 005  HIDDEN  SMALL SYSTEM     (-51, 59)   angle 110, arc 5   -> 'Launcher 1 WS 005 ...'
  WS 006  HIDDEN  SMALL SYSTEM     (-51, -58)  angle 250, arc 5   -> 'Launcher 2 WS 006 ...'
Each mount has '<prefix> pivot' and '<prefix> muzzle' empties.

Engines (phaeton.ship order; names as on the Second Wind)
  1..6 at X -93, Y +36 (w12), +24, +9, -9, -24 (w16), -36 (w12), angle 180.
  'Engine <i> aft-facing bell / mouth lip / dark throat / exhaust anchor'.

Pass 2 detail (IoU 0.936; blockout 0.931)
Four modules were detailed in parallel (hull, deck, bow, engines) and
integrated; check_clashes.py is the cross-module overlap sweep (remaining
hits are deliberate joins).
- ph_hull: plate seams, coaming, keel strake, tank crown trunks, strake
  bays and lamps, gallery windows, deck hatches and vents, pump house,
  radiators, valve box.
- ph_deck: Moss-type cargo deck - pipe trunk with four cargo lines,
  catwalk over the domes, dome stairs, crown platforms, cross headers A/B,
  port and starboard manifolds, vent masts, floodlights.
- ph_bow: bridge cab and wheelhouse, windscreen, mast and radar, escape
  pod and davits, observation housing, twin-leg lattice crane with hose.
- ph_engines / ph_mounts: detailed bells (exhaust anchors unchanged),
  drive block aft face, drums, drive housings with iris and glow, guard
  arcs; flare launchers are 2x3 packs. Turret is the Shepherd's design at
  TURRET_SCALE 1.5.
reference-history/v1-blockout/ holds the blockout manifest; its close-up
renders are regenerated (ignored), never committed.

Pass 4 scale
ship_scale.py option C on the measured SOLIDS volume (hull body, strakes,
aft frame and spine, gallery, bridge, three tank spheres: 307,698 u3),
freighter class: solved 0.326 m/unit (0.318-0.334), published 0.33.
232 units = about 76 m: 1.24 Hounds (61 m), 0.70 Colossus (108 m). The
driver re-measures on every build and stops if the volume drifts >2%.

Pass 5 condition state: destroyed (ph_damage.py, --state destroyed)
Broken into three sections at S02 (X 22) and S01 (X 66) with jagged
cutters; tank 1 (S03) and tank 3 (S05) ruptured outward with petals,
tank 2 shattered by the strike crater (S04) with fragments flung out;
S06 debris, S07 exposed frames on the break faces. Base objects are never
renamed: the base keeps its stern-most piece and other pieces become
'Scar S0x <section> piece | <name>'. Sections are parented to 'Scar S02
stern section', 'Scar S04 mid section', 'Scar S01 bow section' empties
whose wreck_pose custom props store pivot/translate/rotation, so a
cinematic can drive the break-up from intact. Fire, vent and explosion
anchors ('... fire anchor', '... vent anchor') feed the shared SWFX
'fire' group.

Build
  sh ../../blender.sh --background --factory-startup --python build_phaeton.py -- --out <dir> [--state destroyed] [--device cpu] [--engine workbench]
  python compose_review.py --dir <dir>
Renders go to an ignored folder (build/phaeton/...); never commit them.
