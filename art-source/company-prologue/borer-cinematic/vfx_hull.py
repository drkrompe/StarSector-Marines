"""The Borer's profile for the shared VFX kit (../second-wind-vfx/swfx_hull.py).

Loaded when SWFX_HULL points at this folder (export_effects.py passes
`swfx.apply(hull=<this folder>)`). Everything the kit does not find here stays the Second
Wind's.

Engines are drone_borer.ship's three engineSlots in order: 1 the main drive on the spine's
tail (width 6), 2 port and 3 starboard (width 4), splayed 20 degrees outboard (angles 160 and
200). The export declares `Engine <i> exhaust anchor` on each mouth with +X down that slot's
exhaust heading, so the plumes follow the splay. All three are LOW_TECH, whose vanilla engine
colour is orange (engine_styles.json, 255,125,25).

The tool is the variant's mining laser (WS 001, mininglaser), a continuous beam from the
cutting head's lens: the kit's `beams` group, dark until `beam_t_ws001` is raised, so it is
opt-in in every shot. Its colours, charge-down and range are the vanilla .wpn and
weapon_data.csv values; its width is not. Vanilla draws the beam 17 sprite pixels wide, which at
the Borer's 0.25 m/unit would be a 4 m beam from a 0.24 m emitter; it is sized to the emitter
instead (0.6 units fringe, 0.15 m). The drone has no guns, so the kit's `guns` group is not used.

The lift jets puff short and faint (RCS_PUFF_M, RCS_PUFF_DENSITY). The kit's smoke group (drive
haze, landing dust) is not used: on a drone this size it read as a smear (owner, round 2).

Lamps are the geometry's two work-lamp lenses (warm white, point lights at the masthead
role's small power) and the amber beacon on the spine, which blinks.
"""
import json
from pathlib import Path

NAME = 'Borer'
GEOMETRY_JSON = Path(__file__).resolve().parent.parent / 'fighters-geometry' / 'borer' / 'geometry.json'
_geom = json.loads(GEOMETRY_JSON.read_text(encoding='utf-8'))
_mpu = _geom['metres_per_unit']

ENGINES = [(*e['slot_location'], e['width']) for e in _geom['engines']]
ENGINE_INDICES = tuple(range(1, len(ENGINES) + 1))
# Bell mouth radii (hull units) by slot width, as ft_borer.ENGINE_SET builds them.
BELL_R = {6: .22 / _mpu, 4: .175 / _mpu}
PORT_ENGINES = (2,)
STARBOARD_ENGINES = (3,)

# One working drone: all three drives healthy, lamps lit. `guns` is the state's weapons-live
# switch, which the beams group reads too; the Borer has no guns (MOUNTS is empty), so True
# only lets the mining laser's rig be built (still dark until beam_t_ws001 is raised).
STATES = {
    'clean': dict(engines={i: 'clean' for i in ENGINE_INDICES}, powered=True, guns=True,
                  seal_wisps=False, geometry='clean'),
}

MOUNTS = []
DECLARED_MUZZLES = True

COLOURS = {
    'clean': dict(core=(1.0, .80, .52), plume=(1.0, .42, .08), warm=(1.0, .50, .12),
                  glow=(1.0, .72, .40), light=(1.0, .62, .30)),
}


def _rgb(r, g, b):
    return (r / 255, g / 255, b / 255)


# mininglaser.wpn: fringe 255,0,0; core 255,155,155; glow 255,155,155; ROUGH texture.
# weapon_data.csv: range 600, chargeup 0, chargedown 0.25, continuous (no burst).
BEAMS = [dict(id='ws001', slot='WS 001', mount='HIDDEN', muzzle='Anchor weapon WS 001 muzzle',
              weapon='mininglaser', width=.6, range=600, fringe=_rgb(255, 0, 0), core=_rgb(255, 155, 155),
              glow=_rgb(255, 155, 155), chargeup=0.0, chargedown=.25, burst=None, pd=True)]

RCS_QUADS = {}
# The four ventral lift jets (the export's 'RCS <side> <fore|aft> lift jet anchor'): a short,
# faint cold-gas pulse off a 0.2 m ring, not the kit's 4 m frigate plume (owner, round 2:
# "too much smoke in the vertical booster").
RCS_NOZZLE_M = .1
RCS_PUFF_M = .7
RCS_PUFF_DENSITY = .25

WARM = (1.0, .88, .70)
AMBER = (1.0, .45, .08)
LAMPS = [
    ('Pod port work lamp lens', 'masthead', WARM),
    ('Pod starboard work lamp lens', 'masthead', WARM),
    ('Hull spine beacon lamp lens', 'beacon', AMBER),
]
