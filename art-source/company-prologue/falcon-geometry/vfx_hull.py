"""The Falcon (P)'s profile for the shared VFX kit (../second-wind-vfx/swfx_hull.py).

Loaded when SWFX_HULL points at this folder (export_effects.py passes
`swfx.apply(hull=<this folder>)`). Whatever is not set here stays the
Second Wind's.

Engines are falcon.ship's twelve engineSlots in order (fc_engines): 1-6 the
main drives in the two drive blocks (width 16), 7-12 the outrigger pods'
manoeuvring thrusters (width 8). All are MIDLINE, whose vanilla engine colour
is a warm orange-yellow (engine_styles.json); the (P) skin's patchwork drives
burn a little hotter and dirtier, so the plume is pushed toward orange.

Mounts are the manifest's weapon slots in order (geometry.json weapon_slots):
WS 001/002 swarmer and WS 007/008 annihilator pod box launchers, WS 005/006
typhoon launcher turrets, WS 009/010 IR pulse laser turrets (the turret kit's
pdlaser). The export declares one kit muzzle per launch cell row or barrel,
`Anchor turret <i> tube|barrel <k> muzzle` (export_falcon.kit_muzzles),
parented to the mount's own muzzle anchor, so a trained turret carries them.
"""
import json
from pathlib import Path

NAME = 'Falcon (P)'
GEOMETRY_JSON = Path(__file__).resolve().parent / 'geometry.json'
_geom = json.loads(GEOMETRY_JSON.read_text(encoding='utf-8'))

ENGINES = [(e['location'][0], e['location'][1], e['width']) for e in _geom['engines']]
ENGINE_INDICES = tuple(range(1, len(ENGINES) + 1))
BELL_R = {e['width']: e['mouth_radius'] for e in _geom['engines']}
PORT_ENGINES = (1, 2, 3, 7, 8, 9)
STARBOARD_ENGINES = (4, 5, 6, 10, 11, 12)

# The pirate flagship in battle: every drive lit, lamps on, guns live.
STATES = {
    'clean': dict(engines={i: 'clean' for i in ENGINE_INDICES}, powered=True, guns=True,
                  seal_wisps=False, geometry='clean'),
}

LAUNCHERS = ('swarmer', 'annihilatorpod', 'typhoon')
# (x, y, angle, scale, twin, kind) in manifest weapon-slot order.
MOUNTS = [(s['location'][0], s['location'][1], s['angle'], .9 if s['size'] != 'SMALL' else .6, False,
           'launcher' if s['weapon'] in LAUNCHERS else 'gun') for s in _geom['weapon_slots']]
DECLARED_MUZZLES = True

COLOURS = {
    'clean': dict(core=(1.0, .86, .58), plume=(1.0, .52, .12), warm=(1.0, .58, .18),
                  glow=(1.0, .76, .42), light=(1.0, .66, .32)),
}

RCS_QUADS = {}

# (object name, role, colour or None). Running lights take red to port and
# green to starboard by their side of the centreline.
WHITE = (1.0, .95, .85)
LAMPS = [
    ('Light port prong bow 1', 'running', None),
    ('Light starboard prong bow 1', 'running', None),
    ('Light port outrigger tip 1', 'running', None),
    ('Light starboard outrigger tip 1', 'running', None),
    ('Light centre dome masthead 1', 'masthead', WHITE),
    ('Light centre stern 1', 'beacon', WHITE),
]
