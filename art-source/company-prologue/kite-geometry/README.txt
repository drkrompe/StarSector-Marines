Kite (P): the pirate boarding shuttle of the Asharu battle cinematic

The vanilla skin kite_pirates.skin on the base hull kite (designation
Shuttle, Midline, FRIGATE): sprite graphics/ships/aeroshuttle/
aeroshuttle_pirates.png (82x66), slots and engines from data/hulls/kite.ship,
fitted as kite_pirates_Raider (WS 001 Hammer, WS 002 Swarmer, WS 003 light
dual machine gun). Two of them carry the pirate boarders to the Colossus
(asharu-battle-shot.md: distant in S4, in S7, 350-550 m off in S9).

Build (from this folder):
  sh ../../blender.sh --background --factory-startup --python build_kite.py -- [--samples N] [--views a,b]
  python compose_review.py          # sprite overlay, IoU, sheets (Pillow)
Writes renders/ (ignored), kite-geometry.blend (ignored, a build output)
and geometry.json (tracked manifest).

Modules: kp_kit (materials and helpers, copied from the Hermes kit),
kp_hull (fuselage, wing body, dorsal spine, aft hump, missile sponsons),
kp_engines (the eight drive units at the .ship engine slots),
kp_weapons (two missile hardpoints and the WS 003 turret from the shared
turret kit). Axes +X bow, +Y port, +Z dorsal; one unit = one sprite pixel
about the .ship centre.

Condition states: clean only; no damage states are needed.
