"""The Shepherd's (Morrow's) profile for the shared VFX kit (../second-wind-vfx/swfx_hull.py).

Loaded when SWFX_HULL points at this folder (export_effects.py passes
`swfx.apply(hull=<this folder>)`). Everything the kit does not find here stays
the Second Wind's.

Engines are the two main drives in shepherd.ship engineSlots order: 1 port,
2 starboard (width 16). The four maneuvering jets (slots 3-6) are not drives:
the export declares them as geometry RCS anchors ('RCS port forward jet
anchor'), so the kit gives them cold-gas puffs and no art-placed RCS quads.
The Shepherd is LOW_TECH, whose vanilla engine colour is orange
(engine_styles.json, 255,125,25).
"""
import json
from pathlib import Path

NAME = 'Shepherd'
GEOMETRY_JSON = Path(__file__).resolve().parent.parent / 'shepherd-geometry' / 'geometry.json'
_geom = json.loads(GEOMETRY_JSON.read_text(encoding='utf-8'))
_drives = [e for e in _geom['engines'] if e['role'] == 'main drive']

ENGINES = [(e['location'][0], e['location'][1], e['width']) for e in _drives]
ENGINE_INDICES = tuple(range(1, len(ENGINES) + 1))
BELL_R = {16: 4.7}               # sh_engines.MAIN_BELL mouth radius
PORT_ENGINES = (1,)
STARBOARD_ENGINES = (2,)

# One working tender: both drives healthy, lamps lit, the guns live.
STATES = {
    'clean': dict(engines={i: 'clean' for i in ENGINE_INDICES}, powered=True, guns=True,
                  seal_wisps=False, geometry='clean'),
}

# Gun rigs in declared-muzzle order: 1 the WS 007 twin turret, 2 the fixed WS 008
# hardpoint (single barrel). (x, y, angle, scale, twin); scale against the Second
# Wind's small mount.
MOUNTS = [(-3, -23, -75, .9, True), (27, -19, 0, .8, False)]
DECLARED_MUZZLES = True

COLOURS = {
    'clean': dict(core=(1.0, .80, .52), plume=(1.0, .42, .08), warm=(1.0, .50, .12),
                  glow=(1.0, .72, .40), light=(1.0, .62, .30)),
}

RCS_QUADS = {}

# (object name, role, colour or None). The running lenses follow the sprite,
# which draws green to port and red to starboard (sh_hull), so their lights are
# given those colours explicitly rather than the kit's red-to-port default.
WHITE = (1.0, .95, .85)
RED, GREEN = (1.0, .12, .08), (.15, 1.0, .35)
LAMPS = [
    ('Bridge running lamp port green', 'running', GREEN),
    ('Bridge running lamp starboard red', 'running', RED),
    ('Hull stern lamp lens port green', 'running', GREEN),
    ('Hull stern lamp lens starboard red', 'running', RED),
    ('Mast bridge masthead lamp', 'masthead', WHITE),
    ('Mast forward sensor boom nav lamp', 'beacon', WHITE),
    ('Drone bay service block floodlight 1', 'work', WHITE),
    ('Drone bay service block floodlight 2', 'work', WHITE),
]
