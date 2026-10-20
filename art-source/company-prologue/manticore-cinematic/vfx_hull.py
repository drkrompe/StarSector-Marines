"""The Manticore (P)'s profile for the shared VFX kit (../second-wind-vfx/swfx_hull.py).

Loaded when SWFX_HULL points at this folder (export_effects.py passes
`swfx.apply(hull=<this folder>)`). Everything the kit does not find here stays
the Second Wind's.

Engines are manticore.ship's five engine slots in slot order (geometry.json
`engines`): 1-3 the width-20 main drives (port, centre, starboard), 4/5 the
width-12 outer drives. The export declares each mouth as 'Engine <i> exhaust
anchor' with its radius. The Manticore is LOW_TECH, whose vanilla engine colour
is orange (engine_styles.json, 255,125,25), as on the Colossus Mk.III.

Mounts are the six turret and hardpoint slots in geometry.json `turrets` order,
as mt_mounts numbers the declared muzzles: 1 the WS 000 Hellbore, 2 and 4 the
WS 001/003 light guns, 3 the WS 002 twin needler (barrel 1|2), 5 and 6 the
WS 004/005 Harpoon pods, launchers of three tubes each (launch blooms and
missiles, never muzzle flashes). Scale is mt_mounts' vfx_scale: ring radius
against the Second Wind's small mount for a gun, missile length against the
kit's missile for a launcher.
"""
import json
from pathlib import Path

NAME = 'Manticore (P)'
GEOMETRY_JSON = Path(__file__).resolve().parent.parent / 'manticore-geometry' / 'geometry.json'
_geom = json.loads(GEOMETRY_JSON.read_text(encoding='utf-8'))
_engines = _geom['engines']

ENGINES = [(e['location'][0], e['location'][1], e['width']) for e in _engines]
ENGINE_INDICES = tuple(e['index'] for e in _engines)
BELL_R = {e['width']: e['mouth_radius'] for e in _engines}
PORT_ENGINES = tuple(e['index'] for e in _engines if e['location'][1] > 1)
STARBOARD_ENGINES = tuple(e['index'] for e in _engines if e['location'][1] < -1)

# One fighting destroyer: battle-worn but working, every drive running, lamps lit,
# guns live. A shot that idles a drive sets its thr_throttle_e<i> to 0.
STATES = {
    'clean': dict(engines={i: 'clean' for i in ENGINE_INDICES}, powered=True, guns=True,
                  seal_wisps=False, geometry='clean'),
}

# (x, y, angle, scale, twin, kind) in turret index order.
MOUNTS = [(t['location'][0], t['location'][1], t['rest_angle_deg'], t['vfx_scale'], False, t['kind'])
          for t in sorted(_geom['turrets'], key=lambda t: t['index'])]
DECLARED_MUZZLES = True

# Warmer than the kit's LOW_TECH row (which the Lasher's profile also carries): at
# the Manticore's bell sizes and a close framing that row's pale core clipped to
# white and read as smoke (coordinator, 2026-10-20: warm it to the Lasher's
# orange drives). The core keeps its lead over the plume so the mouth stays hottest.
COLOURS = {
    'clean': dict(core=(1.0, .58, .22), plume=(1.0, .30, .04), warm=(1.0, .38, .06),
                  glow=(1.0, .52, .18), light=(1.0, .48, .16)),
}

RCS_QUADS = {}

# (object name, role, colour or None). No lights layer exists for the Manticore
# sprite; these are the geometry's lamps: the wing-tip side lights (red port,
# green starboard by side), the white stern light, the prow anti-collision
# strobe and the two red mast beacons on the command deck.
WHITE = (1.0, .95, .85)
RED = (1.0, .06, .03)
LAMPS = [
    ('Light port wing tip 1 lens', 'running', None),
    ('Light starboard wing tip 1 lens', 'running', None),
    ('Light centre stern 1 lens', 'masthead', WHITE),
    ('Light centre prow 1 lens', 'beacon', WHITE),
    ('Bridge mast beacon 1', 'beacon', RED),
    ('Bridge mast beacon 2', 'beacon', RED),
]
