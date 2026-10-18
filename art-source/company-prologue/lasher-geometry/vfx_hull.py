"""The Lasher's profile for the shared VFX kit (../second-wind-vfx/swfx_hull.py).

Loaded when SWFX_HULL points at this folder (export_effects.py passes
`swfx.apply(hull=<this folder>)`). Whatever is not set here stays the
Second Wind's.

Engines are lasher.ship's five engineSlots in order: 1 the main drive in the
spine's tail (width 16), 2 and 3 the port nacelle's pair, 4 and 5 the
starboard's (width 8). The build names their empties exactly as the Second
Wind does (`Engine <i> exhaust anchor`), so the kit reads them through this
table. All five are LOW_TECH, whose vanilla engine colour is orange
(engine_styles.json, 255,125,25). Turrets 1-3 are WS 005/006/007, twin small
ballistic guns resting at their slot angles, with declared muzzles
(`Anchor turret <i> barrel <k> muzzle`). The hardpoints carry `Anchor weapon
<slot id> muzzle` empties for a shot that wants them; the kit's gun rigs cover
the turrets.
"""
import json
from pathlib import Path

NAME = 'Lasher'
GEOMETRY_JSON = Path(__file__).resolve().parent / 'geometry.json'
_geom = json.loads(GEOMETRY_JSON.read_text(encoding='utf-8'))

ENGINES = [(e['location'][0], e['location'][1], e['width']) for e in _geom['engines']]
ENGINE_INDICES = tuple(range(1, len(ENGINES) + 1))
BELL_R = {e['width']: e['mouth_radius'] for e in _geom['engines']}
PORT_ENGINES = (2, 3)
STARBOARD_ENGINES = (4, 5)

# One serviceable warship in formation: every drive healthy, lamps lit, guns live.
STATES = {
    'clean': dict(engines={i: 'clean' for i in ENGINE_INDICES}, powered=True, guns=True,
                  seal_wisps=False, geometry='clean'),
}

# (x, y, angle, scale, twin) in turret index order: WS 005, WS 006, WS 007.
MOUNTS = [(t['location'][0], t['location'][1], t['rest_angle_deg'], .6, True) for t in _geom['turrets']]
DECLARED_MUZZLES = True

COLOURS = {
    'clean': dict(core=(1.0, .80, .52), plume=(1.0, .42, .08), warm=(1.0, .50, .12),
                  glow=(1.0, .72, .40), light=(1.0, .62, .30)),
}

RCS_QUADS = {}

# (object name, role, colour or None). Wing-tip lights take red to port and
# green to starboard by their side of the centreline.
WHITE = (1.0, .95, .85)
LAMPS = [
    ('Light port wing tip 1', 'running', None),
    ('Light starboard wing tip 1', 'running', None),
    ('Light centre stern 1', 'masthead', WHITE),
    ('Light centre prow 1', 'beacon', WHITE),
]
