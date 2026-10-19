COLOSSUS MK.III (PIRATE REFIT) - CINEMATIC GEOMETRY (PASSES 1, 2 AND 4; OWNER REVIEW NEXT)

Purpose
The pirates' prize ship in the Asharu battle cinematic (asharu-battle/asharu-battle-shot.md):
an armed Colossus that closes to take the merchant Colossus (S9, about 500-600 m off) and
fails, and carries off the captured Tarsus clamped under her keel instead (S10, mid-ground);
distant in S4. No damage states.
It follows the ship-model-pipeline skill and starts from the accepted Colossus
(../colossus-geometry/): every module is a fork, copied rather than imported (c3_*), and
object names keep the Colossus's, so the surfacing part tables carry over.

Hull identity (read-only install, starsector-core)
data/hulls/colossus3.ship (hullId colossus3, "Colossus Mk.III"), sprite
graphics/ships/colossus/colossus3_pirate.png (184 x 224, centre 93,128, as the Colossus's;
no lights layer). ship_data.csv row "colossus3": Modified Freighter, Pirate, CRUISER, crew
80-160, cargo 300, fuel 60, mass 1400, armor 600 (the Colossus's 300), FRONT shield, system
flarelauncher, built-ins civgrade, converted_bay, ground_support.

What the pirates changed (read off the two sprites, which agree aft of X -20)
- The side cargo modules are cut down: the walls stand at rel y 62.5 (sprite Y 63.5 / -61.5)
  where the Colossus's stood at 88. Each module end keeps one container cell (two boxes
  across, four high, 48 -> 8 per face), so the container-stack identity stays.
- Twelve small ballistic guns at colossus3.ship's slots, each on the sprite's big turret
  rings: sponson drums built out over the module walls on welded gussets (WS 001-004, 013,
  014; trimmed at the longeron flange), and solid deck tubs (WS 009-012, 015, 016). The
  bow guns WS 013-016 are rotary chainguns, WS 001/003/011/012 the Colossus's twin light
  autocannon, the broadside WS 002/004/009/010 single long light cannon. All share the
  Colossus's gun house with welded cheek armour and a roof searchlight.
- The converted launch bay (WS 017, converted_bay): the forward hatch cover and the deck
  cranes are gone; a well X 46 to the bow, Y +-17, 30 units (15 m) deep, open to the deck
  and through the bow face, lined in steel, red rim and floor lamps (the sprite's red dots),
  two catapult tracks, a blast deflector, the hangar lift door aft, hazard-framed mouth.
  'Launch bay WS 017 launch point 1-4' are WS 017's four locations, +X out of the mouth.
  The bow cargo door is the lower part of the Colossus's, under the bay mouth.
- The refit (c3_refit): heavy armour skirts on stand-off brackets over the module walls
  between the door rows (some in scavenged grey primer), a raked bow glacis either side of
  the bay mouth, and scavenged deck plates in old grey and faded blue, all framed in welds.
- Pirate scheme on the base materials: crimson on the hold, aft body and the bridge's deck,
  blackened rust-brown on the modules and pods, crimson blocks on the pods' aft plates
  (the sprite's red pod patches), the Colossus's pale longerons and bridge shell kept.
- Lift thrusters: the cut-down modules take one each (six in all, two on the hold).
- Keel grapples (c3_grapples), for the S10 prize: four telescoping booms that clamp the
  Tarsus's four dorsal receivers (tarsus-geometry/geometry.json 'docking', copied into
  TARSUS_RECEIVERS_M; the build warns if hers move). She hangs on the centreline, same
  heading, dorsal up, her top 1 m under the keel skids, fore-aft where the fore and aft
  booms rake out equally: the receivers are 51 m apart and the flat keel 46 m, so the booms
  splay like a claw. Built stowed (folded flat under the keel, retracted). Each: a fixed
  'Grapple <i> mount' at the pivot (local +X the deployed boom), 'Grapple <i> hinge'
  (rotation_euler[1] from its 'stowed_deg' to 0), 'Grapple <i> stage 2' and 'stage 3'
  (location[0] from 0 to 'travel'), and 'Grapple <i> clamp anchor' on the 2.6 m pad face,
  on receiver <i> at full extension. --grapples renders them deployed; --tarsus hangs the
  committed Tarsus build from them for review (never saved).

Slots (checked against the install on every build, WS 017 included)
WS 001 (42,60) 80/140, 002 (-6,63) 90/130, 003 (42,-58) -80/140, 004 (-6,-61) -90/130,
009 (19,-51) -90/130, 010 (18,53) 90/130, 011 (59,-39) -40/170, 012 (59,41) 50/170,
013 (80,-53) -45/200, 014 (80,55) 45/200, 015 (84,29) 6/160, 016 (84,-27) -6/160:
TURRET SMALL BALLISTIC -> 'Turret 1..12 WS 0nn ...' (numbered in slot order).
WS 005 (89,64) 45, 006 (89,-61) -45, 007 (-83,84) 100, 008 (-84,-81) 260: HIDDEN SYSTEM
-> 'Launcher 1..4 WS 00n flare launcher ...'. WS 017 LAUNCH_BAY (69,-5 / 69,9 / 52,-6 /
52,9). Engines 1-6 as the Colossus's (X -124, width 15, LOW_TECH).
Muzzle empties: '<turret> muzzle port|starboard' on the twins, '<turret> muzzle' on the
single bores, '<launcher> muzzle'.

Scale (pass 4)
0.495 m per unit (107 m long), the Asharu battle plan's settled size for the Colossus's
twin; option C solves 0.450 (97 m) against the measured 0.79 M units3 (the Colossus's
solids with the modules cut down and the bay cut out). 160 crew are 48,000 of 48,690 m3 of
contents. See ship-scale.md. --figure stands a 1.8 m mannequin on the bridge's aft door
landing.

Silhouette: top render against colossus3_pirate.png, IoU 0.945 (target 0.93).

Build (from this folder; renders and the .blend go to the ignored build/ tree)
  sh ../../blender.sh --background --factory-startup --python build_colossus_mk3.py -- --out ../../../build/colossus-mk3/geo --cpu [--views ...]
  python compose_review.py --dir ../../../build/colossus-mk3/geo
--cpu renders on the CPU: with other sessions holding the GPU, a GPU render stalled for
twenty minutes where the CPU takes seconds. Close-ups: bay-detail, bay-mouth, bow-guns,
sponson-detail, refit-wall, turret-detail, grapple-detail, prize-carry (with --tarsus) and
the Colossus's set. geometry.json is copied
here from the build.
