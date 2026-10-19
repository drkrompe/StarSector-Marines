"""The Colossus Mk.III's profile for the shared VFX kit (../second-wind-vfx/swfx_hull.py).
Forked from ../colossus-cinematic/vfx_hull.py.

Loaded when SWFX_HULL points at this folder (export_effects.py passes
`swfx.apply(hull=<this folder>)`). Everything the kit does not find here stays
the Second Wind's.

Engines 1-6 are the six main drives in colossus3.ship engineSlots order (1-3
port pod, 4-6 starboard pod, width 15). Engines 7-12 are the six ventral lift
thrusters (geometry.json lift_thrusters 1-6: two on the hold, one on each
cut-down module), declared as drives pointing down, each on its own throttle
(`thr_throttle_e7` .. `e12`). LOW_TECH, orange (engine_styles.json,
255,125,25). The kit's width keys are only labels for the bell radius: 15 the
main bell, 14 the hold's two big lift bells, 7 the modules' small ones.
"""
import json
from pathlib import Path

NAME = 'Colossus Mk.III'
GEOMETRY_JSON = Path(__file__).resolve().parent.parent / 'colossus-mk3-geometry' / 'geometry.json'
_geom = json.loads(GEOMETRY_JSON.read_text(encoding='utf-8'))

# cl_engines.BELL mouth_r; cl_hull.lift bell mouth .86 * well radius.
MAIN_BELL_R = 2.95
LIFT_WIDTH = {7.0: 14, 3.6: 7}
BELL_R = {15: MAIN_BELL_R, 14: .86 * 7.0, 7: .86 * 3.6}

ENGINES = [(e['location'][0], e['location'][1], e['width']) for e in _geom['engines']]
MAIN_INDICES = tuple(range(1, len(ENGINES) + 1))
ENGINES += [(t['anchor_location'][0], t['anchor_location'][1], LIFT_WIDTH[t['well_radius']])
            for t in _geom['lift_thrusters']]
LIFT_INDICES = tuple(range(len(MAIN_INDICES) + 1, len(ENGINES) + 1))
ENGINE_INDICES = tuple(range(1, len(ENGINES) + 1))
PORT_ENGINES = tuple(i for i, (x, y, w) in enumerate(ENGINES, 1) if y > 1.5 + 1)
STARBOARD_ENGINES = tuple(i for i, (x, y, w) in enumerate(ENGINES, 1) if y < 1.5 - 1)

# One armed raider: every drive and lift thruster healthy, lamps lit, guns live.
# A shot that is cruising sets thr_throttle_e7..e12 to 0; one lifting off runs them.
STATES = {
    'clean': dict(engines={i: 'clean' for i in ENGINE_INDICES}, powered=True, guns=True,
                  seal_wisps=False, geometry='clean'),
}

# Gun rigs in declared-muzzle order: the twelve small ballistic turrets (twin
# light autocannon, single long cannon, rotary chainguns). (x, y, angle, scale,
# twin); scale against the Second Wind's small mount.
MOUNTS = [(s['location'][0], s['location'][1], s['angle'], .9, s['gun'] == 'twin')
          for s in _geom['weapon_slots'] if s['mount'] == 'TURRET']
DECLARED_MUZZLES = True

COLOURS = {
    'clean': dict(core=(1.0, .80, .52), plume=(1.0, .42, .08), warm=(1.0, .50, .12),
                  glow=(1.0, .72, .40), light=(1.0, .62, .30)),
}

RCS_QUADS = {}

# (object name prefix, role, colour). The running lamps are warm white (c3_kit
# 'Running lamp'); the launch bay's rim and floor lamps are red (the sprite's red dots).
WHITE = (1.0, .95, .85)
RED = (1.0, .14, .05)
LAMPS = [
    ('Launch bay red lamps', 'work', RED),
    ('Bridge masthead lamp', 'masthead', WHITE),
    ('Bridge port yard lamp', 'beacon', WHITE),
    ('Bridge starboard yard lamp', 'beacon', WHITE),
    ('Hull aft port stern lamp', 'beacon', WHITE),
    ('Hull aft starboard stern lamp', 'beacon', WHITE),
    ('Hull port hatch edge lamps', 'work', WHITE),
    ('Hull starboard hatch edge lamps', 'work', WHITE),
]


# Shield (../second-wind-vfx/swfx_shield.py): vanilla FRONT, as the Colossus's
# (colossus3.ship keeps the Colossus's shield). Not in this export's groups: a shot
# asks for it with groups=(..., 'shield'). Fitted ~10 m clear (margin 20.0 units
# at 0.495 m/u). Colours: the 'low-tech' scheme.
SHIELD = dict(
    type='FRONT', anchor='COLOSSUS MK.III | root', center=(-6, 1), radius=170.0, arc=150.0, nodes=[],
    scheme='low-tech', efficiency=1.0, fit=dict(margin=20.0), raise_s=1.0, lower_s=1.0,
)
