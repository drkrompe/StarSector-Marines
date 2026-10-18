"""The Mudskipper's profile for the shared VFX kit (../second-wind-vfx/swfx_hull.py).

Loaded when SWFX_HULL points at this folder (export_effects.py passes
`swfx.apply(hull=<this folder>)`). Everything the kit does not find here stays
the Second Wind's.

Engines are the three drives in mudskipper.ship engineSlots order: 1 the
banded centre drum (width 24), 2 the port pod, 3 the starboard pod. The two
maneuvering jets (slots 4 and 5) are not drives: the export declares them as
geometry RCS anchors ('RCS port maneuvering jet anchor'), so the kit gives
them cold-gas puffs and no art-placed RCS quads. Her drives are LOW_TECH,
whose vanilla engine colour is orange (engine_styles.json, 255,125,25).
"""
import json
from pathlib import Path

NAME = 'Mudskipper'
GEOMETRY_JSON = Path(__file__).resolve().parent.parent / 'mudskipper-geometry' / 'geometry.json'
_geom = json.loads(GEOMETRY_JSON.read_text(encoding='utf-8'))
_drives = [e for e in _geom['engines'] if e['role'].endswith('drive')]

ENGINES = [(e['location'][0], e['location'][1], e['width']) for e in _drives]
ENGINE_INDICES = tuple(range(1, len(ENGINES) + 1))
BELL_R = {e['width']: e['bell_mouth_radius'] for e in _drives}
PORT_ENGINES = (2,)
STARBOARD_ENGINES = (3,)

# One working ferry: every drive healthy, lamps lit, the gun live.
STATES = {
    'clean': dict(engines={i: 'clean' for i in ENGINE_INDICES}, powered=True, guns=True,
                  seal_wisps=False, geometry='clean'),
}

# WS 003: a small twin light-MG turret resting trained aft (x, y, angle, scale, twin).
MOUNTS = [(-9, 0, 180, .55, True)]
DECLARED_MUZZLES = True

COLOURS = {
    'clean': dict(core=(1.0, .80, .52), plume=(1.0, .42, .08), warm=(1.0, .50, .12),
                  glow=(1.0, .72, .40), light=(1.0, .62, .30)),
}

RCS_QUADS = {}

# (object name, role, colour or None). Running lamps are red to port, green
# to starboard by their side of the centreline; the rest carry their colour.
WHITE, AMBER, RED = (1.0, .95, .85), (1.0, .70, .30), (1.0, .08, .04)
LAMPS = [
    ('Bridge navigation lamp port', 'running', None),
    ('Bridge navigation lamp starboard', 'running', None),
    ('Port sponson running light', 'running', None),
    ('Starboard sponson running light', 'running', None),
    ('Port aft running light', 'running', None),
    ('Starboard aft running light', 'running', None),
    ('Bridge mast anticollision lamp', 'beacon', RED),
    ('Hood navigation beacon lamp', 'beacon', RED),
    ('Bridge floodlight lens', 'work', WHITE),
    ('Hood deck floodlight lens', 'work', WHITE),
    ('Port bay work light', 'work', AMBER),
    ('Starboard bay work light', 'work', AMBER),
    ('Engine 2 drive warning light', 'drive-warning', RED),
    ('Engine 3 drive warning light', 'drive-warning', RED),
]
