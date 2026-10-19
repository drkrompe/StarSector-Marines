"""The Onslaught's (XIV) profile for the shared VFX kit (../second-wind-vfx/swfx_hull.py).

Loaded when SWFX_HULL points at this folder (export_effects.py passes
`swfx.apply(hull=<this folder>)`). Everything the kit does not find here stays
the Second Wind's. Without this profile the kit would take the Onslaught for
the Second Wind: its engine empties are named `Engine <i> exhaust anchor`, the
Second Wind's own naming, so the kit would dress four of them with the Second
Wind's bell radii.

Engines: all six drives in onslaught.ship engineSlots order (geometry.json
`engines`). 1 and 4 are the quarter pods, 2 and 3 the blade drives (width 20),
5 and 6 the two mains (width 30). Port (+Y) is 1, 2, 5 and starboard 3, 4, 6,
by where each slot sits. Bell radii are the geometry's own mouth radii (4.6 and
7.0 hull units). The Onslaught is LOW_TECH, whose vanilla engine colour is
orange (engine_styles.json, 255,125,25); the Legion, the other Hegemony
capital, declares the same style, so this is the Shepherd's LOW_TECH palette.

Guns: the kit takes declared muzzles named `Anchor turret <i> <tag> muzzle`
and keys its knobs by turret and k = 1 (single or the twin's port gun) or 2
(the twin's starboard gun), so it cannot take the turret kit's
`Weapon WS nnn muzzle anchor k` names or a three-barrel mount as one rig.
`RIGS` below maps every ballistic barrel onto kit gun rigs: a single barrel
is one rig (`gun`), a twin one rig (`port gun` / `starboard gun`, by the side of
the line of fire, set by export_effects.py), and a triple two rigs: its outer
barrels as a twin and its centre barrel alone. export_effects.py adds one
`Anchor turret <rig> <tag> muzzle` empty as a child of each turret-kit muzzle
anchor (identity offset), so the effect rides the pivot and the elevation;
nothing is renamed. The four Typhoon missile racks (WS 021-024) are left out:
the kit has gun effects only.

Lamps: the 21 running lights. Red ones blink in the build (2 s, 18% duty),
so they take the kit's `beacon` role, coloured red; white ones (arm edge
lines, stern) take the steady `masthead` role.
"""
import json
from pathlib import Path

NAME = 'Onslaught'
GEOMETRY_JSON = Path(__file__).resolve().parent.parent / 'onslaught-geometry' / 'geometry.json'
_geom = json.loads(GEOMETRY_JSON.read_text(encoding='utf-8'))
_engines = sorted(_geom['engines'], key=lambda e: e['index'])

ENGINES = [(e['location'][0], e['location'][1], e['width']) for e in _engines]
ENGINE_INDICES = tuple(e['index'] for e in _engines)
BELL_R = {e['width']: e['mouth_radius'] for e in _engines}
PORT_ENGINES = tuple(e['index'] for e in _engines if e['location'][1] > 0)
STARBOARD_ENGINES = tuple(e['index'] for e in _engines if e['location'][1] < 0)

# One condition: undamaged, every drive running, lamps lit, the guns live.
STATES = {
    'clean': dict(engines={i: 'clean' for i in ENGINE_INDICES}, powered=True, guns=True,
                  seal_wisps=False, geometry='clean'),
}

# Flash scale against the Second Wind's small mount (hull units at 0.65 m/u; the
# Onslaught's are 0.86 m, so a like-for-like gun is ~0.76 of it in units). The
# turret kit's own kit_scale multiplies in.
SIZE_SCALE = {'SMALL': .75, 'MEDIUM': 1.0, 'LARGE': 1.4}
TPC_SCALE = 2.4


def _rigs():
    rigs = []
    for s in sorted(_geom['weapon_slots'], key=lambda s: s['id']):
        if s['type'] == 'BALLISTIC' and s['mount'] == 'TURRET':
            names = s['muzzle_anchors']
            sc = round(SIZE_SCALE[s['size']] * s.get('kit_scale', 1.0), 3)
            if len(names) == 3:          # outer pair as a twin, the centre barrel alone
                groups = [[names[0], names[2]], [names[1]]]
            else:
                groups = [names]
            for g in groups:
                rigs.append(dict(slot=s['id'], weapon=s['weapon'], size=s['size'], barrels=g,
                                 x=s['location'][0], y=s['location'][1], angle=s['angle'],
                                 scale=sc, twin=len(g) == 2))
        elif s['type'] == 'BUILT_IN' and s.get('muzzle'):       # the two TPC hardpoints
            rigs.append(dict(slot=s['id'], weapon=s['weapon'], size=s['size'], barrels=[s['muzzle']],
                             x=s['location'][0], y=s['location'][1], angle=s['angle'],
                             scale=TPC_SCALE, twin=False))
    for i, r in enumerate(rigs, 1):
        r['rig'] = i
    return rigs


RIGS = _rigs()
MOUNTS = [(r['x'], r['y'], r['angle'], r['scale'], r['twin']) for r in RIGS]
DECLARED_MUZZLES = True

# LOW_TECH orange (vanilla 255,125,25): a white-hot core, an orange plume.
COLOURS = {
    'clean': dict(core=(1.0, .80, .52), plume=(1.0, .42, .08), warm=(1.0, .50, .12),
                  glow=(1.0, .72, .40), light=(1.0, .62, .30)),
}

RCS_QUADS = {}

# (object name, role, colour); one entry per running light, colour from geometry.json.
LAMPS = [(l['name'], 'beacon' if l['colour'] == 'red' else 'masthead', tuple(l['rgb']))
         for l in _geom['running_lights']]
