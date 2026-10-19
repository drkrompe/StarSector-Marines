"""The Tarsus's profile for the shared VFX kit (../second-wind-vfx/swfx_hull.py).

Loaded when SWFX_HULL points at this folder (export_effects.py passes
`swfx.apply(hull=<this folder>)`). Everything the kit does not find here stays
the Second Wind's.

Engines 1-6 are tarsus.ship's engineSlots in order: 1 and 6 the two main drives
(width 20), 2-5 the four auxiliaries hung in the drive skirt (widths 6, 8, 8, 6).
LOW_TECH: the vanilla engine colour is orange (engine_styles.json, 255,125,25).

Two states. clean: every drive running, lamps lit, guns live. boarded (the
Asharu battle's prize): every drive dead, guns silent, the drive section's
lamps dark; export_effects.py adds smoke at the craters' smoke anchors and the
kit's own conduit sparks fire at the craters' spark anchors. export_effects.py
sets TARSUS_STATE before loading this profile, so the lamp list matches the
geometry's dead lenses.
"""
import json
import os
from pathlib import Path

NAME = 'Tarsus'
GEOMETRY_JSON = Path(__file__).resolve().parent.parent / 'tarsus-geometry' / 'geometry.json'
_geom = json.loads(GEOMETRY_JSON.read_text(encoding='utf-8'))

# Bell mouth radii from the drive bells' extents (ts_engines): main 9.6, auxiliaries 3.8 / 2.9.
BELL_R = {20: 9.6, 8: 3.8, 6: 2.9}
ENGINES = [(e['location'][0], e['location'][1], e['width']) for e in _geom['engines']]
ENGINE_INDICES = tuple(range(1, len(ENGINES) + 1))
MAIN_INDICES = tuple(i for i, (x, y, w) in enumerate(ENGINES, 1) if w >= 20)
PORT_ENGINES = tuple(i for i, (x, y, w) in enumerate(ENGINES, 1) if y > 1 + 1)
STARBOARD_ENGINES = tuple(i for i, (x, y, w) in enumerate(ENGINES, 1) if y < 1 - 1)

STATES = {
    'clean': dict(engines={i: 'clean' for i in ENGINE_INDICES}, powered=True, guns=True,
                  seal_wisps=False, geometry='clean'),
    'boarded': dict(engines={i: 'dead' for i in ENGINE_INDICES}, powered=True, guns=False,
                    seal_wisps=False, geometry='boarded'),
}

# The four WS 001-004 twin light autocannon turrets; (x, y, angle, scale, twin).
MOUNTS = [(s['location'][0], s['location'][1], s['angle'], .9, True)
          for s in _geom['weapon_slots'] if s['mount'] == 'TURRET']
DECLARED_MUZZLES = True

_ORANGE = dict(core=(1.0, .80, .52), plume=(1.0, .42, .08), warm=(1.0, .50, .12),
               glow=(1.0, .72, .40), light=(1.0, .62, .30))
COLOURS = {'clean': _ORANGE}     # keyed by engine mode (swfx_thrusters), not state

RCS_QUADS = {}

WHITE = (1.0, .95, .85)
RED, GREEN = (1.0, .12, .08), (.15, 1.0, .3)
LAMPS = [
    ('Bridge masthead lamp', 'masthead', WHITE),
    ('Mast port bow tip lamp', 'beacon', WHITE),
    ('Mast starboard bow tip lamp', 'beacon', WHITE),
    ('Hull port arm wall running lamp', 'beacon', WHITE),
    ('Hull starboard arm wall running lamp', 'beacon', WHITE),
    ('Kite collar lamps', 'work', WHITE),
]
DRIVE_LAMPS = [
    ('Drive stern lamp port lens', 'beacon', RED),
    ('Drive stern lamp starboard lens', 'beacon', GREEN),
    ('Drive stern lamp white lens', 'beacon', WHITE),
]
if os.environ.get('TARSUS_STATE', 'clean') == 'clean':
    LAMPS += DRIVE_LAMPS        # boarded: ts_damage L1 has those lenses dark

# Shield (swfx_shield): vanilla OMNI, radius 120, shieldCenter (18, 1) (tarsus.ship).
# Not in the export's groups; a shot asks for it with groups=(..., 'shield').
SHIELD = dict(
    type='OMNI', anchor='TARSUS | root', center=(18, 1), radius=120.0, arc=120.0, nodes=[],
    scheme='low-tech', efficiency=1.0, fit=dict(margin=12.0), raise_s=1.0, lower_s=1.0,
)
