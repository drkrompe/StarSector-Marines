"""The Hermes's profile for the shared VFX kit (../second-wind-vfx/swfx_hull.py).

Loaded when SWFX_HULL points at this folder (export_effects.py passes
`swfx.apply(hull=<this folder>)`). Everything the kit does not find here stays
the Second Wind's.

Engines are the three main drives in hermes.ship engineSlots order: 1 port,
2 centre, 3 starboard (width 8). The two maneuvering jets (slots 4 and 5) are
not drives: the export declares them as geometry RCS anchors ('RCS port
maneuvering jet anchor'), so the kit gives them cold-gas puffs and no
art-placed RCS quads. The Hermes is MIDLINE, whose vanilla engine colour is a
warm orange (engine_styles.json, 255,145,75).
"""
import json
from pathlib import Path

NAME = 'Hermes'
GEOMETRY_JSON = Path(__file__).resolve().parent.parent / 'hermes-geometry' / 'geometry.json'
_geom = json.loads(GEOMETRY_JSON.read_text(encoding='utf-8'))
_drives = [e for e in _geom['engines'] if e['role'].endswith('drive')]

ENGINES = [(e['location'][0], e['location'][1], e['width']) for e in _drives]
ENGINE_INDICES = tuple(range(1, len(ENGINES) + 1))
BELL_R = {e['width']: e['bell_mouth_radius'] for e in _drives}
PORT_ENGINES = (1,)
STARBOARD_ENGINES = (3,)

# One working boat: every drive healthy, lamps lit, the guns live.
STATES = {
    'clean': dict(engines={i: 'clean' for i in ENGINE_INDICES}, powered=True, guns=True,
                  seal_wisps=False, geometry='clean'),
}

# WS 001-003: three small twin ballistic turrets (x, y, angle, scale, twin). Scale is
# against the Second Wind's small mount in hull units: the Hermes gun house is about
# twice its size in units (0.11 m per unit against 0.65), so 1.5 sizes the flash to it.
MOUNTS = [(12, 18, 45, 1.5, True), (12, -18, -45, 1.5, True), (-18, 0, 180, 1.5, True)]
DECLARED_MUZZLES = True

COLOURS = {
    'clean': dict(core=(1.0, .86, .66), plume=(1.0, .50, .20), warm=(1.0, .57, .29),
                  glow=(1.0, .76, .52), light=(1.0, .66, .40)),
}

RCS_QUADS = {}

# (object name, role, colour or None). The sprite's lights layer carries the
# four nose lamps only: the outer pair are the running lights (red to port,
# green to starboard by side), the inner pair white forward work lights.
WHITE = (1.0, .95, .85)
LAMPS = [
    ('Nose lamp 3', 'running', None),
    ('Nose lamp 4', 'running', None),
    ('Nose lamp 1', 'work', WHITE),
    ('Nose lamp 2', 'work', WHITE),
]
