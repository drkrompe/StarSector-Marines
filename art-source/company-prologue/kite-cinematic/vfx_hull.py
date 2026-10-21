"""The Kite (P)'s profile for the shared VFX kit (../second-wind-vfx/swfx_hull.py).

Loaded when SWFX_HULL points at this folder (export_effects.py passes
`swfx.apply(hull=<this folder>)`). Everything the kit does not find here stays
the Second Wind's.

Engines are all eight of kite.ship's engine slots, in slot order: the two main
drives, two wing nacelles, two tail drives and the two canted wing-tip
maneuvering jets, which burn like the rest in the game. The export declares
each mouth as 'Engine <i> exhaust anchor'. The eight RCS nozzles on the nose
cheeks and wing tips are declared geometry RCS anchors, so the kit gives them
cold-gas puffs and places no quads of its own. The Kite is MIDLINE, whose
vanilla engine colour is a warm orange (engine_styles.json, 255,145,75).
"""
import json
from pathlib import Path

NAME = 'Kite (P)'
GEOMETRY_JSON = Path(__file__).resolve().parent.parent / 'kite-geometry' / 'geometry.json'
_geom = json.loads(GEOMETRY_JSON.read_text(encoding='utf-8'))
_engines = _geom['engines']

ENGINES = [(e['location'][0], e['location'][1], e['width']) for e in _engines]
ENGINE_INDICES = tuple(range(1, len(ENGINES) + 1))
BELL_R = {e['width']: e['bell_mouth_radius'] for e in _engines}
PORT_ENGINES = tuple(i for i, e in enumerate(_engines, 1) if e['location'][1] > 0)
STARBOARD_ENGINES = tuple(i for i, e in enumerate(_engines, 1) if e['location'][1] < 0)

# One working pirate shuttle: every drive lit, lamps on, the guns live.
STATES = {
    'clean': dict(engines={i: 'clean' for i in ENGINE_INDICES}, powered=True, guns=True,
                  seal_wisps=False, geometry='clean'),
}

# Weapons as the export numbers them ('Anchor turret <i> <tag> muzzle'): 1 the WS 001
# Hammer torpedo launcher, 2 the WS 002 Swarmer SRM box, 3 the WS 003 twin light
# machine gun. (x, y, angle, scale, twin, kind). Scale is against the Second Wind's
# small mount in hull units: at 0.80 m per unit against 0.65 a light gun's flash is
# about 0.8 of hers.
MOUNTS = [(6, 22, 0, 1.0, False, 'launcher'), (6, -22, 0, .8, False, 'launcher'), (-7, 0, 0, .8, False, 'gun')]
DECLARED_MUZZLES = True

COLOURS = {
    'clean': dict(core=(1.0, .86, .66), plume=(1.0, .50, .20), warm=(1.0, .57, .29),
                  glow=(1.0, .76, .52), light=(1.0, .66, .40)),
}

RCS_QUADS = {}

# (object name prefix, role, colour or None). No lights layer exists for the
# aeroshuttle sprites; these are pass 2's lamps: wing-tip navigation lights
# (red to port, green to starboard by side), a red dorsal beacon, a white stern
# light, two nose landing lights and the dorsal spotlight as forward work lights.
WHITE = (1.0, .95, .85)
LAMPS = [
    ('Lamp port wing-tip nav', 'running', None),
    ('Lamp starboard wing-tip nav', 'running', None),
    ('Lamp dorsal beacon', 'beacon', (1.0, .18, .08)),
    ('Lamp stern white', 'masthead', WHITE),
    ('Lamp port landing light', 'work', WHITE),
    ('Lamp starboard landing light', 'work', WHITE),
    ('Dorsal spotlight lens', 'work', WHITE),
]


# Shield (../second-wind-vfx/swfx_shield.py): vanilla OMNI, arc 150, radius 58,
# shieldCenter (0, 0) (kite.ship, ship_data.csv), efficiency 0.8. Colour by
# technology, not faction: the MIDLINE hull style, so the 'midline' scheme.
# Not in this export's groups: a shot builds it per ship with the film
# (swfx_shield.build(ctx, film=True, hull_collection=...), asharu-effects-plan.md W1).
# Fitted ~7 m clear of the hull (9 units at 0.8 m/u). vents: the hull parts
# that glow and shed vapour when she vents (shield_vent).
SHIELD = dict(
    type='OMNI', anchor='KITE (P) | root', center=(0, 0), radius=58.0, arc=150.0, nodes=[],
    scheme='midline', efficiency=0.8, fit=dict(margin=9.0), raise_s=1.0, lower_s=1.0,
    vents=['Hull port wing heat shield', 'Hull starboard wing heat shield'],
)
