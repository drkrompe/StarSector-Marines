"""The Colossus's profile for the shared VFX kit (../second-wind-vfx/swfx_hull.py).

Loaded when SWFX_HULL points at this folder (export_effects.py passes
`swfx.apply(hull=<this folder>)`). Everything the kit does not find here stays
the Second Wind's.

Engines 1-6 are the six main drives in colossus.ship engineSlots order (1-3
port pod, 4-6 starboard pod, width 15). Engines 7-16 are the ten ventral lift
thrusters (geometry.json lift_thrusters 1-10), declared as drives pointing
down so they get the kit's plume, core, bell glow and light, each on its own
throttle (`thr_throttle_e7` .. `e16`): a shot lifting off runs them and the
main drives separately. The Colossus is LOW_TECH, whose vanilla engine colour
is orange (engine_styles.json, 255,125,25). The kit's width keys are only
labels for the bell radius: 15 the main bell, 14 the hold's two big lift
bells, 7 the modules' eight small ones.
"""
import json
from pathlib import Path

NAME = 'Colossus'
GEOMETRY_JSON = Path(__file__).resolve().parent.parent / 'colossus-geometry' / 'geometry.json'
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

# One working freighter: every drive and lift thruster healthy, lamps lit, guns live.
# A shot that is cruising sets thr_throttle_e7..e16 to 0; one lifting off runs them.
STATES = {
    'clean': dict(engines={i: 'clean' for i in ENGINE_INDICES}, powered=True, guns=True,
                  seal_wisps=False, geometry='clean'),
}

# Gun rigs in declared-muzzle order: the four WS 001-004 twin light autocannon
# turrets. (x, y, angle, scale, twin); scale against the Second Wind's small mount.
MOUNTS = [(s['location'][0], s['location'][1], s['angle'], .9, True)
          for s in _geom['weapon_slots'] if s['mount'] == 'TURRET']
DECLARED_MUZZLES = True

COLOURS = {
    'clean': dict(core=(1.0, .80, .52), plume=(1.0, .42, .08), warm=(1.0, .50, .12),
                  glow=(1.0, .72, .40), light=(1.0, .62, .30)),
}

RCS_QUADS = {}

# (object name prefix, role, colour). Every lamp lens on the Colossus is warm white
# (cl_kit 'Running lamp'), so the lights are white too rather than red/green by side.
WHITE = (1.0, .95, .85)
LAMPS = [
    ('Bridge masthead lamp', 'masthead', WHITE),
    ('Bridge port yard lamp', 'beacon', WHITE),
    ('Bridge starboard yard lamp', 'beacon', WHITE),
    ('Hull aft port stern lamp', 'beacon', WHITE),
    ('Hull aft starboard stern lamp', 'beacon', WHITE),
    ('Hull port hatch edge lamps', 'work', WHITE),
    ('Hull starboard hatch edge lamps', 'work', WHITE),
]
