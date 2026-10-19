ENFORCER - CINEMATIC GEOMETRY (PASS 1 BLOCKOUT)

Purpose
A scripted Blender model of the vanilla Enforcer destroyer for the Asharu
battle cinematic (asharu-battle/asharu-battle-shot.md). One hull build serves
both fleets:
- merchant: the convoy's escort lead, an independent hull in the same scheme
  as the convoy's two Lashers. Hero of S6 "The screen breaks" (30 mm, about
  1 km: about 150 px long at 1080p), also seen in S1. Its damaged state (S6:
  shot up, hull breaches and fires, not destroyed) is pass 5.
- pirate: the Enforcer (P), distant (4-5 km) in S4, climbing out of the
  platform with the raiders.
It follows the ship-model-pipeline skill and the Lasher package's conventions
(../lasher-geometry/) but shares no code with another hull.

Hull identity
enforcer (data/hulls/enforcer.ship; ship_data.csv row "enforcer": Destroyer,
Low Tech, burndrive, 60-100 crew, 90 cargo, 90 fuel, FRONT shield 150 deg).
Sprite graphics/ships/enforcer/enforcer_base.png, 136 x 136, .ship centre
(68, 67). No lights layer exists for this hull. The pirate version is the skin
data/hulls/skins/enforcer_d_pirates.skin (hullName "Enforcer (P)", tag
pirate_bp), sprite enforcer_pirates.png: same slots, engines and outline; the
skin removes nothing. The driver checks both files against its constants on
every build. Install files are read where they are (../../starsector_path.py)
and never copied into the repository.

Coordinates
+X bow, +Y port, +Z dorsal. One sprite pixel is one unit about the .ship
centre: sprite column c, row r (from the top) covers X 68-r..69-r,
Y 67-c..68-c. Heights are proposed art.

Reading of the sprite (ef_hull)
- Carapace: the broad, nearly square armoured body every turret stands on,
  deck Z 6, keel Z -9. A ring of raised armour round sunk areas: the two
  diamond wells either side of the waist, the centre well ahead of the
  bridge, and the two tank bays aft with their pale tanks lying fore and aft.
  The sprite is see-through in the middle of each diamond well and in a slot
  beside the centre well; those are open through the hull. Its stern is
  notched on the centreline.
- Ventral keel under the carapace, down to Z -16.
- Neck and prow: a gunmetal neck forward to the anvil prow, a broad armoured
  ram 41 units wide.
- Aft spine on the centreline between the tank bays, carrying the bridge
  deckhouse (the pale octagon the sprite draws at (-15, 0)) with its
  glazing band.
- Engine blocks either side of the notch, each holding a big drive between
  two small ones.
- Missile pods: four gunmetal blocks proud of the carapace's forward edge.
- Pirate refit (ef_armour_p, --variant pirate only): red slabs welded over
  the tank bays, aft quarters, bow shoulders and aft edge; girders across the
  diamond wells and tank bays; a red cross on the prow; the neck sensor eye.

Slots (verified against the installed enforcer.ship on every build)
  id      mount      size    type       location    angle  arc
  WS 001  TURRET     MEDIUM  BALLISTIC  (-15, 44)   90     210   Turret 1
  WS 002  TURRET     MEDIUM  BALLISTIC  (12, 29)    30     125   Turret 2
  WS 003  TURRET     MEDIUM  BALLISTIC  (19, 0)     0      125   Turret 3
  WS 004  TURRET     MEDIUM  BALLISTIC  (12, -29)   -30    125   Turret 4
  WS 005  TURRET     MEDIUM  BALLISTIC  (-15, -44)  -90    210   Turret 5
  WS 006  HARDPOINT  SMALL   MISSILE    (29, 49)    0      5
  WS 007  HARDPOINT  SMALL   MISSILE    (38, 30)    0      5
  WS 008  HARDPOINT  SMALL   MISSILE    (38, -30)   0      5
  WS 009  HARDPOINT  SMALL   MISSILE    (29, -49)   0      5
Engines, LOW_TECH, angle 180, Z 0: Engine 1 (-66, 29) and Engine 2
(-66, -28) width 24 (the mains); Engines 3-6 at (-66, 38), (-65, 20),
(-65, -20), (-66, -37) width 8. Turrets rest at their slot angles; the build
fails if a rested barrel overlaps the hull (BVH).

Stable object names
  Hull ...                       carapace, ventral keel, neck, prow, aft spine,
                                 engine blocks, tanks
  Bridge deckhouse / glazing band
  Pod <side> inner|outer missile
  Engine <i> drive can / aft-facing bell / mouth lip / dark throat
  Engine <i> exhaust anchor      empty at the slot (bell mouth), +X down the exhaust
  Engine <i> drive glow anchor   empty at the throat, +X aft
  Turret <i> barbette, traverse gap      fixed
  Turret <i> pivot               empty; turn about hull +Z to train; custom
                                 properties slot, rest_angle_deg, arc_deg
  Turret <i> gunhouse, mantlet, barrel <k>, barrel <k> muzzle brake   trained
  Anchor turret <i> barrel <k> muzzle            trained; the VFX kit's declared muzzle
  Weapon <slot id> anchor / launcher / tube <k>
  Anchor weapon <slot id> muzzle                 launcher muzzle
  Light <side> <where> <k>       running lights; origin = lamp position
  Armour P ...                   pirate variant only

Materials are classes, coloured per livery (ef_kit.LIVERIES): Enforcer
armour, accent armour, machinery, tank cladding, recess, bridge glazing,
heat vent glow, running lights. "trim" is the accent on the merchant
(gunhouses, drive lips) and gunmetal on the pirate.

Scale
Provisional 0.71 m/u (the shot plan's 95 m estimate) until pass 4 adds the
hull to ../ship_scale.py.

Shot checks
shot-s6 and shot-s4 place the hull and camera exactly as
asharu-battle/battle_blockout.py's key frames k3 (S6) and k2 (S4) do, under
its sun (elevation 16, azimuth -40), black sky, no fill.

Rebuild from this folder (outputs go to build/enforcer/<variant>/, never tracked)
sh ../../blender.sh --background --factory-startup --python build_enforcer.py -- \
    --variant merchant --views top,three-quarter,port-side,front,stern,details,shot --manifest
python3 compose_review.py --variant merchant --manifest
(and the same with --variant pirate; its manifest is geometry-p.json.)
compose_review.py needs Pillow + numpy.

Shipping
Source-only reference art. Nothing here enters the mod or changes gameplay.
