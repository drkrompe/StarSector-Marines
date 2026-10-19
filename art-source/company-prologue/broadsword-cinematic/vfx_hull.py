"""The Broadsword's profile for the shared VFX kit (../second-wind-vfx/swfx_hull.py).

Loaded when SWFX_HULL points at this folder (export_effects.py passes
`swfx.apply(hull=<this folder>)`). Everything the kit does not find here stays
the Second Wind's.

Engines are broadsword.ship's two engineSlots in order: 1 port, 2 starboard
(width 12). The geometry names its bells, lips and throats the Second Wind way
and the export declares `Engine <i> exhaust anchor` at each mouth, so the kit
reads them through this table. Both are LOW_TECH, whose vanilla engine colour
is orange (engine_styles.json, 255,125,25). The guns are the variant's two
linked light machine guns in the hidden WS 001/002 hardpoints: fixed, not
turrets, but the kit takes them through its declared-muzzle path
(`Anchor turret <i> gun muzzle`, single barrel). Lamps are the geometry's four
running lights (bw_lights.py): red and green blade tips, a white tail light
and a white anti-collision strobe.
"""
import json
from pathlib import Path

NAME = 'Broadsword'
GEOMETRY_JSON = Path(__file__).resolve().parent.parent / 'broadsword-geometry' / 'geometry.json'
_geom = json.loads(GEOMETRY_JSON.read_text(encoding='utf-8'))

ENGINES = [(*e['slot_location'], e['width']) for e in _geom['engines']]
ENGINE_INDICES = tuple(range(1, len(ENGINES) + 1))
BELL_R = {e['width']: e['bell_radius'] for e in _geom['engines']}
PORT_ENGINES = (1,)
STARBOARD_ENGINES = (2,)

# One front-line fighter: both drives healthy, lamps lit, guns live.
STATES = {
    'clean': dict(engines={i: 'clean' for i in ENGINE_INDICES}, powered=True, guns=True,
                  seal_wisps=False, geometry='clean'),
}

# (x, y, angle, scale, twin) in gun index order: WS 001 (port), WS 002 (starboard).
# Scale against the Second Wind's small mount: a light machine gun's flash on a
# 0.5 m/unit fighter, about the size of its 0.55-unit muzzle brake.
MOUNTS = [(*_geom['anchors'][s['muzzle']][:2], s['angle'], .75, False)
          for s in _geom['weapon_slots'] if s.get('fitted')]
DECLARED_MUZZLES = True

COLOURS = {
    'clean': dict(core=(1.0, .80, .52), plume=(1.0, .42, .08), warm=(1.0, .50, .12),
                  glow=(1.0, .72, .40), light=(1.0, .62, .30)),
}

RCS_QUADS = {}

# (object name, role, colour or None). Blade-tip lights take red to port and
# green to starboard by their side of the centreline.
WHITE = (1.0, .95, .85)
LAMPS = [
    ('Light port blade tip 1', 'running', None),
    ('Light starboard blade tip 1', 'running', None),
    ('Light centre tail 1', 'masthead', WHITE),
    ('Light centre dorsal 1', 'beacon', WHITE),
]
