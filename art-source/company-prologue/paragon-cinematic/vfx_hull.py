"""The Paragon's profile for the shared VFX kit (../second-wind-vfx/swfx_hull.py).

Loaded when SWFX_HULL points at this folder (export_effects.py passes
`swfx.apply(hull=<this folder>)`). Everything the kit does not find here stays
the Second Wind's.

Engines: the ten drives in paragon.ship engineSlots order (geometry.json
`engines`), all width 12, mouth radius 6.0 hull units. Port (+Y) and starboard
by where each slot sits. The geometry names its mouth empties `Engine <i>
exhaust`; the build also has `Engine <i> mouth lip` parts, which tell the kit
these are Second-Wind-shaped engines, so export_effects.py adds the anchor the
kit reads first, `Engine <i> exhaust anchor`, as a child of each `Engine <i>
exhaust` (identity offset, radius_m from the mouth radius). The Paragon is
HIGH_TECH, whose vanilla engine colour is blue (engine_styles.json, 100,165,255).

Guns: only the two heavy needlers (WS 007, 008) are guns; each is a twin, one
kit rig with a port and a starboard gun. The four Reaper racks are torpedoes
and have no rig (the kit has no missile effects).

Beams: every energy weapon, from the vanilla .wpn and weapon_data.csv
(Starsector 0.98a): colours, width, range, charge-up and charge-down. The bow
tachyon lances (WS 001, 002) are fixed hardpoints; the rest train with their
turrets. Hull units are sprite pixels, the same unit vanilla's width and
range are given in, so they carry over unchanged.

Shield: the OMNI 360-degree shield of radius 270 about shieldCenter (1, 0)
(paragon.ship, ship_data.csv), on the geometry's `Shield emitter` empty, with
the ten `Shield emitter node <k>` empties on the bore lining as its emitter
array. Colours are vanilla: the HIGH_TECH hull style's shieldRingColor
(255,255,255) and shieldInnerColor (125,125,255, alpha 75); the Fortress
Shield system's (fortressshield.system) ring 255,255,255 and inner
255,100,255 (alpha 75), charge up and down 1.5 s (ship_systems.csv).
Raise and lower times are the cinematic's own (the game's unfold rate is not
a data column).

Lamps: none. The Paragon has no running lights; its lamps are emissive
geometry that glows without the kit.
"""
import json
from pathlib import Path

NAME = 'Paragon'
GEOMETRY_JSON = Path(__file__).resolve().parent.parent / 'paragon-geometry' / 'geometry.json'
_geom = json.loads(GEOMETRY_JSON.read_text(encoding='utf-8'))
_engines = sorted(_geom['engines'], key=lambda e: e['index'])

ENGINES = [(e['slot_location'][0], e['slot_location'][1], e['width']) for e in _engines]
ENGINE_INDICES = tuple(e['index'] for e in _engines)
BELL_R = {e['width']: e['mouth_radius'] for e in _engines}
PORT_ENGINES = tuple(e['index'] for e in _engines if e['slot_location'][1] > 0)
STARBOARD_ENGINES = tuple(e['index'] for e in _engines if e['slot_location'][1] < 0)

STATES = {
    'clean': dict(engines={i: 'clean' for i in ENGINE_INDICES}, powered=True, guns=True,
                  seal_wisps=False, geometry='clean'),
}

# HIGH_TECH blue (vanilla 100,165,255): a blue-white core, a blue plume.
COLOURS = {
    'clean': dict(core=(.82, .92, 1.0), plume=(.28, .55, 1.0), warm=(.40, .65, 1.0),
                  glow=(.55, .75, 1.0), light=(.50, .72, 1.0)),
}

RCS_QUADS = {}
LAMPS = []

_slots = sorted(_geom['weapon_slots'], key=lambda s: s['id'])


def _rigs():
    rigs = []
    for s in _slots:
        if s['weapon'] == 'needler':
            rigs.append(dict(slot=s['id'], weapon='heavyneedler', size=s['size'], barrels=s['muzzles'],
                             x=s['location'][0], y=s['location'][1], angle=s['angle_deg'],
                             scale=1.0, twin=len(s['muzzles']) == 2))
    for i, r in enumerate(rigs, 1):
        r['rig'] = i
    return rigs


RIGS = _rigs()
MOUNTS = [(r['x'], r['y'], r['angle'], r['scale'], r['twin']) for r in RIGS]
DECLARED_MUZZLES = True


def _rgb(*c):
    return tuple(round(v / 255, 4) for v in c)


# Vanilla beam data: (width, range, fringe, core, glow, chargeup s, chargedown s, burst s).
# Only the PD laser carries vanilla's PD hint (weapon_data.csv): it engages
# missiles and fighters, never the ship's main target.
WEAPONS = {
    'tachyonlance': (25.0, 1000, _rgb(85, 25, 215), _rgb(255, 255, 255), _rgb(165, 100, 255), .5, 1.0, 1.0),
    'gravitonbeam': (20.0, 1000, _rgb(5, 135, 175), _rgb(200, 225, 255), _rgb(25, 215, 255), .1, .1, None),
    'taclaser': (13.0, 1000, _rgb(0, 255, 0), _rgb(255, 255, 255), _rgb(155, 255, 155), .1, .1, None),
    'pdlaser': (12.0, 500, _rgb(155, 0, 0), _rgb(255, 255, 255), _rgb(255, 100, 100), 0.0, .25, None),
}


PD = ('pdlaser',)


def _beams():
    beams = []
    for s in _slots:
        spec = WEAPONS.get(s['weapon'])
        if spec is None:
            continue
        width, rng, fringe, core, glow, up, down, burst = spec
        for k, m in enumerate(s['muzzles'], 1):
            beams.append(dict(id=s['id'].replace('WS ', 'ws') + ('' if len(s['muzzles']) == 1 else f'_{k}'),
                              slot=s['id'], mount=s['mount'], muzzle=m, weapon=s['weapon'], width=width,
                              range=rng, fringe=fringe, core=core, glow=glow, chargeup=up,
                              chargedown=down, burst=burst, pd=s['weapon'] in PD))
    return beams


BEAMS = _beams()


SHIELD = dict(
    anchor=_geom['shield']['anchor'],
    radius=float(_geom['shield']['radius_units']),
    arc=float(_geom['shield']['arc_deg']),
    nodes=[n['name'] for n in _geom['shield']['emitter_nodes']],
    ring=_rgb(255, 255, 255), inner=_rgb(125, 125, 255), inner_alpha=round(75 / 255, 4),
    fortress_ring=_rgb(255, 255, 255), fortress_inner=_rgb(255, 100, 255), fortress_alpha=round(75 / 255, 4),
    raise_s=1.0, lower_s=1.0, fortress_up_s=1.5, fortress_down_s=1.5,
)
