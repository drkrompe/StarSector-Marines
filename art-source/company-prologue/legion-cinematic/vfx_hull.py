"""The Legion (XIV)'s profile for the shared VFX kit (../second-wind-vfx/swfx_hull.py).

Loaded when SWFX_HULL points at this folder (export_effects.py passes
`swfx.apply(hull=<this folder>)`). Everything the kit does not find here stays
the Second Wind's.

Engines are the ten drives in legion.ship engineSlots order (geometry.json
`engines`): four small port and starboard cans (width 6), the two big outer
drives (24), the two inner (16) and the two centre (12). The Legion is
LOW_TECH, whose vanilla engine colour is orange (engine_styles.json,
255,125,25), as on the Shepherd.

Mounts are the fifteen turrets in weapon_slots order, read off the export's
manifest.json: their scale (ring radius against the Second Wind's small mount,
or a launcher's missile length against the kit's missile) and their kind. The
squall (WS 000, five tubes) and the hurricane (WS 001, one) are launchers:
launch blooms and missiles, never muzzle flashes. Every barrel is tagged
'barrel <k>' or 'tube <k>' by the export, so a 3-barrel heavy autocannon gets
three rigs (t5_1..t5_3), not two that collide.
"""
import json
from pathlib import Path

NAME = 'Legion'
HERE = Path(__file__).resolve().parent
GEOMETRY_JSON = HERE.parent / 'legion-geometry' / 'geometry.json'
_geom = json.loads(GEOMETRY_JSON.read_text(encoding='utf-8'))
_man = json.loads((HERE / 'manifest.json').read_text(encoding='utf-8'))
_engines = _geom['engines']

ENGINES = [(e['location'][0], e['location'][1], e['width']) for e in _engines]
ENGINE_INDICES = tuple(e['index'] for e in _engines)
BELL_R = {e['width']: e['mouth_radius'] for e in _engines}
PORT_ENGINES = tuple(e['index'] for e in _engines if e['location'][1] > 0)
STARBOARD_ENGINES = tuple(e['index'] for e in _engines if e['location'][1] < 0)

# A well-kept patrol: every drive healthy, lamps lit, the guns live.
STATES = {
    'clean': dict(engines={i: 'clean' for i in ENGINE_INDICES}, powered=True, guns=True,
                  seal_wisps=False, geometry='clean'),
}

# (x, y, angle, scale, twin, kind) in turret index order.
MOUNTS = [(t['pivot_at'][0], t['pivot_at'][1], t['rest_angle_deg'], t['vfx_scale'], False, t['kind'])
          for t in sorted(_man['turrets'], key=lambda t: t['index'])]
DECLARED_MUZZLES = True

COLOURS = {
    'clean': dict(core=(1.0, .80, .52), plume=(1.0, .42, .08), warm=(1.0, .50, .12),
                  glow=(1.0, .72, .40), light=(1.0, .62, .30)),
}

RCS_QUADS = {}

# (object name, role, colour). The red obstruction and extremity lights run on
# lit_running, the white deck-edge and flank lights on lit_masthead; each keeps
# the colour geometry.json gives it. Their blink is on the lamps (export).
LAMPS = [(l['object'], 'running' if l['colour'] == 'red' else 'masthead', tuple(l['rgb']))
         for l in _man['lamps']]


# Shield (../second-wind-vfx/swfx_shield.py): vanilla FRONT, arc 180, radius 190,
# shieldCenter (0, 0) (legion.ship, ship_data.csv), efficiency 1.0. Not in this
# export's groups: a shot asks for it with groups=(..., 'shield'). The arc is
# fitted to the hull ~10 m clear (margin 10.1 units at 0.99 m/u) like the
# Paragon's, centred on the root at the shieldCenter. Colours: the 'low-tech'
# scheme (LOW_TECH hull style); a shot may pass shield_scheme to swfx.apply.
SHIELD = dict(
    type='FRONT', anchor='LEGION | root', center=(0, 0), radius=190.0, arc=180.0, nodes=[],
    scheme='low-tech', efficiency=1.0, fit=dict(margin=10.1), raise_s=1.0, lower_s=1.0,
)
