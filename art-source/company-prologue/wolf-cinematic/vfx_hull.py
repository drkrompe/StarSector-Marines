"""The Wolf (P)'s profile for the shared VFX kit (../second-wind-vfx/swfx_hull.py).

Loaded when SWFX_HULL points at this folder (export_effects.py passes
`swfx.apply(hull=<this folder>)`). Whatever is not set here stays the Second
Wind's.

Engines are wolf.ship's three engineSlots in order (geometry.json `engines`):
1 port (-36, 12), 2 centre (-40, 0), 3 starboard (-36, -11), width 16, bell
mouth radius 3.2 units. The Wolf is HIGH_TECH, whose vanilla engine colour is
blue (engine_styles.json, 100,165,255), as on the Paragon.

Mounts, in the build's VFX order (geometry.json `vfx_mounts`), each with the
build's declared muzzles (`Anchor turret <i> barrel|tube <k> muzzle`):
1 WS 001 ion cannon and 2 WS 004 mining blaster are 'energy' mounts (the
kit's opt-in kind: flash, light and bolt in the weapon's own colour, no
propellant puff or brass), 3 WS 005 and 4 WS 006 the Annihilator pods,
'launcher' mounts (five tubes each, launch bloom and rockets).
ENERGY_COLOURS are the vanilla glow colours in linear light: ion cannon
(100, 200, 255), mining blaster (255, 100, 100).

Lamps: the two amber neck lamps the sprite draws (steady, masthead role), the
red and green pod-tip lights, the white stern light.
"""
import json
import os
from pathlib import Path

# WOLF_VARIANT=merchant (export_effects.py / review_ship.py --variant merchant) dresses the
# convoy's own Wolf, the base hull with every slot armed (geometry-merchant.json).
MERCHANT = os.environ.get('WOLF_VARIANT') == 'merchant'
NAME = 'Wolf' if MERCHANT else 'Wolf (P)'
GEOMETRY_JSON = Path(__file__).resolve().parent.parent / 'wolf-geometry' / (
    'geometry-merchant.json' if MERCHANT else 'geometry.json')
_geom = json.loads(GEOMETRY_JSON.read_text(encoding='utf-8'))
_engines = sorted(_geom['engines'], key=lambda e: e['index'])

ENGINES = [(e['location'][0], e['location'][1], e['width']) for e in _engines]
ENGINE_INDICES = tuple(e['index'] for e in _engines)
BELL_R = {e['width']: e['mouth_radius'] for e in _engines}
PORT_ENGINES = tuple(e['index'] for e in _engines if e['location'][1] > 0)
STARBOARD_ENGINES = tuple(e['index'] for e in _engines if e['location'][1] < 0)

# One condition: battle-worn but working, every drive running, lamps lit, guns live.
STATES = {
    'clean': dict(engines={i: 'clean' for i in ENGINE_INDICES}, powered=True, guns=True,
                  seal_wisps=False, geometry='clean'),
}

# Flash scale against the Second Wind's small mount (0.65 m/u): the Wolf's units
# are 0.76 m, so a like-for-like small gun is ~0.86 of it in units.
SIZE_SCALE = {'SMALL': .7, 'MEDIUM': .95}
KIND = {'ENERGY': 'energy', 'MISSILE': 'launcher'}
_slots = {s['id']: s for s in _geom['weapon_slots']}
MOUNTS = [(m['location'][0], m['location'][1], m['angle'], SIZE_SCALE[_slots[m['slot']]['size']], False,
           KIND[_slots[m['slot']]['type']]) for m in sorted(_geom['vfx_mounts'], key=lambda m: m['index'])]
DECLARED_MUZZLES = True
ENERGY_COLOURS = {1: (.127, .578, 1.0), 2: (1.0, .127, .127)}
if MERCHANT:   # ion cannon; PD lasers (255,100,100); heavy blaster (100,100,255)
    ENERGY_COLOURS = {1: (.127, .578, 1.0), 2: (1.0, .127, .127), 3: (1.0, .127, .127), 4: (.127, .127, 1.0)}

# HIGH_TECH blue (vanilla 100,165,255): a blue-white core, a blue plume.
COLOURS = {
    'clean': dict(core=(.82, .92, 1.0), plume=(.28, .55, 1.0), warm=(.40, .65, 1.0),
                  glow=(.55, .75, 1.0), light=(.45, .68, 1.0)),
}

RCS_QUADS = {}

AMBER, WHITE = (1.0, .78, .38), (1.0, .95, .85)
LAMPS = [(l['name'], 'running' if l['colour'] in ('red', 'green') else 'masthead',
          None if l['colour'] in ('red', 'green') else (AMBER if l['colour'] == 'amber' else WHITE))
         for l in _geom['running_lights']]
