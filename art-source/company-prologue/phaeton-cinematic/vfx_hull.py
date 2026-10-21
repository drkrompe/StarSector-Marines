"""The Phaeton's profile for the shared VFX kit (../second-wind-vfx/swfx_hull.py).

Loaded when SWFX_HULL points at this folder (export_effects.py passes
`swfx.apply(hull=<this folder>)`). Everything the kit does not find here stays
the Second Wind's.

Engines are the six main drives in phaeton.ship engineSlots order, across the
stern from port to starboard: 1 (Y +36, width 12), 2 (+24), 3 (+9), 4 (-9),
5 (-24) (width 16) and 6 (-36, width 12). The Phaeton is LOW_TECH, whose
vanilla engine colour is orange (engine_styles.json, 255,125,25).

Two states. 'clean' is the working tanker in the column (S1, and S6 until the
missiles land): every drive running, lamps lit, guns live. 'destroyed' is the
S6 wreck (../phaeton-geometry/ph_damage.py): drives dead, lamps dark, no guns;
its fires, vents and the explosion come from the kit's opt-in 'fire' group,
which finds the wreck's 'Scar <id> fire / vent anchor' empties and the
export's explosion anchors.
"""
import json
from pathlib import Path

NAME = 'Phaeton'
GEOMETRY_JSON = Path(__file__).resolve().parent.parent / 'phaeton-geometry' / 'geometry.json'
_geom = json.loads(GEOMETRY_JSON.read_text(encoding='utf-8'))

ENGINES = [(e['location'][0], e['location'][1], e['width']) for e in _geom['engines']]
ENGINE_INDICES = tuple(range(1, len(ENGINES) + 1))
BELL_R = {e['width']: e.get('bell_radius', .29 * e['width']) for e in _geom['engines']}
PORT_ENGINES = tuple(i for i, (x, y, w) in enumerate(ENGINES, 1) if y > 0)
STARBOARD_ENGINES = tuple(i for i, (x, y, w) in enumerate(ENGINES, 1) if y < 0)

STATES = {
    'clean': dict(engines={i: 'clean' for i in ENGINE_INDICES}, powered=True, guns=True,
                  seal_wisps=False, geometry='clean'),
    'destroyed': dict(engines={i: 'dead' for i in ENGINE_INDICES}, powered=False, guns=False,
                      seal_wisps=False, geometry='destroyed'),
}

# Gun rigs in declared-muzzle order: the four WS 001-004 small twin turrets
# (the Shepherd's mount at 1.5x in hull units). (x, y, angle, scale, twin).
MOUNTS = [(s['location'][0], s['location'][1], s['angle'], 1.3, True)
          for s in _geom['weapon_slots'] if s['mount'] == 'TURRET']
DECLARED_MUZZLES = True

COLOURS = {
    'clean': dict(core=(1.0, .80, .52), plume=(1.0, .42, .08), warm=(1.0, .50, .12),
                  glow=(1.0, .72, .40), light=(1.0, .62, .30)),
}

RCS_QUADS = {}

# (object name prefix, role, colour). Navigation lights by side (red port, green
# starboard), the masthead white, amber beacons on the three dome crowns and the
# vent masts, and the deck floodlights as warm work lights over the cargo deck.
WHITE = (1.0, .95, .85)
AMBER = (1.0, .62, .25)
LAMPS = [
    ('Bridge masthead lamp', 'masthead', WHITE),
    ('Bow port sidelight', 'masthead', (1.0, .12, .08)),
    ('Bridge starboard sidelight', 'masthead', (.15, 1.0, .3)),
    ('Deck aft dome crown lamp port lens', 'beacon', AMBER),
    ('Deck mid dome crown lamp starboard lens', 'beacon', AMBER),
    ('Deck fore dome crown lamp port lens', 'beacon', AMBER),
    ('Deck vent mast 1 lamp lens', 'beacon', AMBER),
    ('Deck vent mast 4 lamp lens', 'beacon', AMBER),
    ('Deck trunk gap A lamp lens', 'work', AMBER),
    ('Deck trunk gap B lamp lens', 'work', AMBER),
    ('Deck port manifold lamp lens', 'work', AMBER),
    ('Deck starboard manifold lamp lens', 'work', AMBER),
]


# Shield (../second-wind-vfx/swfx_shield.py): vanilla OMNI, arc 90, radius 160,
# shieldCenter (21, 0) (phaeton.ship, ship_data.csv), efficiency 1.2. Colour by
# technology, not faction: the LOW_TECH hull style, so the 'low-tech' scheme.
# Not in this export's groups: a shot builds it per ship with the film
# (swfx_shield.build(ctx, film=True, hull_collection=...), asharu-effects-plan.md W1).
# Fitted ~8 m clear of the hull (24 units at 0.33 m/u). vents: the hull parts
# that glow and shed vapour when she vents (shield_vent).
SHIELD = dict(
    type='OMNI', anchor='PHAETON | root', center=(21, 0), radius=160.0, arc=90.0, nodes=[],
    scheme='low-tech', efficiency=1.2, fit=dict(margin=24.0), raise_s=1.0, lower_s=1.0,
    vents=['Hull aft port radiator fins', 'Hull aft starboard radiator fins'],
)
