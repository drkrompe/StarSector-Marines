"""Stern: everything aft of X ~ -50 of the Onslaught (XIV).

The stern is two blades (the aft legs, Hull port/starboard aft leg, with the
orange leg stripe and the blade plate families), the central tail with its
radiator, the drive keels under the outer drive housings, the quarter pods and
the aft quarters' lower slopes. Almost every big surface here is ONE polygon
of a long loft (the leg's top, glacis and slopes are 60-85 units each), so a
box cannot split it and density is capped by its own length (8-13 t/u, as the
Second Wind's keel plate); the plates, hatches and vents on them come along at
no extra cost. Port sources, starboard mirrors (skin_from + mirror_u); the tail
is on the centreline.

Left on the graded swatches: the machinery wells and everything in them (pipe
banks, headers, flanges, wheels, the recessed well floors), the drive cans,
bells, hoops, collars, shrouds, mouth lips and heat-shield bands, the aft-body
underside (one 44 x 172-unit polygon) and its stern strip, the leg's inboard
walls and the small fittings.

Sheets stern-blade and stern-tail were generated twice (-v2): the first blade
sheet painted the bone-brown plates cream (41% read as bone, a swap), the first
tail sheet painted the radiator fins rust-red (78% read as weathered orange).
The v2 prompts name those tones; the v1 masters stay in generated/.
"""
from math import sqrt


def _n(v):
    s = sqrt(sum(c * c for c in v))
    return tuple(c / s for c in v)


UP_Z = (0.0, 0.0, 1.0)
PORT = (0.0, 1.0, 0.0)
BOW = (1.0, 0.0, 0.0)
STERN = (-1.0, 0.0, 0.0)
BIG = 1e4

# The blade (port aft leg) runs fore-inboard: in plan about (53, -20) from its aft tip.
AX = _n((53.0, -20.0, 0.0))
BLADE_UP = (-AX[1], AX[0], 0.0)          # plan normal to the axis, outboard

BLADE_TOP = _n((0.22, 0.26, 0.94))       # leg face 743
LEG_OUT = _n((0.30, 0.81, 0.50))         # 745
LEG_IN = _n((-0.08, -0.71, 0.70))        # 744 and the orange stripe's top
LEG_AFT = _n((-0.48, 0.86, 0.14))        # 748
LEG_UNDER = _n((-0.55, 0.0, -0.83))      # between 746 (-.37,0,-.93) and 747 (-.73,0,-.68)
QUARTER_UNDER = _n((-0.27, 0.82, -0.50))  # aft body 4584
KEEL_SIDE = _n((0.0, 0.96, -0.29))       # drive keel outboard wall and its lower slope
KEEL_AFT = _n((-0.91, 0.0, -0.41))       # drive keel 522
TAIL_UNDER = _n((-0.44, 0.0, -0.90))     # tail 2113
TAIL_SLOPE = _n((0.0, 0.89, 0.45))
POD = _n((0.25, 1.0, 0.0))
LEG_INNER = _n((-0.26, -0.97, 0.0))

BLADE_PLATES = ['Detail port blade bone plates', 'Detail port blade dark plates', 'Detail port blade hatches',
                'Detail port blade hatch trim', 'Detail port blade vent frames', 'Detail port blade vent beds',
                'Detail port blade heat shields', 'Detail port blade plate seams']
SLOPE_PLATES = ['Detail port blade bone plates', 'Detail port blade dark plates', 'Detail port blade plate seams']
LEG = 'Hull port aft leg'
STRIPE = 'Armour port leg stripe'


def box(x0, x1, y0=-BIG, y1=BIG, z0=-BIG, z1=BIG):
    return {'box': ((x0, y0, z0), (x1, y1, z1))}


def _all(objs, seed=None):
    return [(o, seed) for o in objs]


PARTS = {
    # ---- the blade (port leg); starboard mirrors
    'blade-top-port': dict(members=[(LEG, (-80.3, 48.7, 16.6))] + _all(BLADE_PLATES), dir=BLADE_TOP, up=BLADE_UP, cone=.85,
                           what='port blade top: plates, hatches, vents'),
    'leg-out-port': dict(members=[(LEG, (-67.0, 57.0, 2.1))] + _all(SLOPE_PLATES), dir=LEG_OUT, up=UP_Z, cone=.9,
                         what='port leg outboard upper slope'),
    'leg-in-port': dict(members=[(LEG, (-81.6, 38.2, 12.6)), (STRIPE, (-102.3, 45.7, 18.4))], dir=LEG_IN, up=UP_Z, cone=.9,
                        what='port leg inboard glacis with the orange stripe'),
    'leg-aft-port': dict(members=[(LEG, (-109.5, 60.8, 8.8))], dir=LEG_AFT, up=UP_Z, cone=.9, what='port leg aft outboard facet'),
    'leg-under-port': dict(members=[(LEG, (-77.7, 49.6, -19.7)), (LEG, (-112.5, 48.3, 1.4)), (STRIPE, (-116.7, 49.8, 6.6))],
                           dir=LEG_UNDER, up=PORT, cone=.85, what='port leg underside and aft lower facet'),
    'quarter-under-port': dict(members=[('Hull aft body', (-32.8, 102.6, -19.0))] + _all(
        ['Detail port aft quarter hatches', 'Detail port aft quarter hatch trim', 'Detail port aft quarter heat shields']),
        dir=QUARTER_UNDER, up=UP_Z, cone=.85, what='port aft quarter outboard lower slope'),
    'keel-side-port': dict(members=[('Engine 2 drive keel', None)], dir=KEEL_SIDE, up=UP_Z, cone=.85, what='port drive keel outboard side'),
    'keel-aft-port': dict(members=[('Engine 2 drive keel', (-97.5, 41.6, -19.0)), ('Detail port drive keel drain frame', None),
                                   ('Detail port drive keel drain bed', None)], dir=KEEL_AFT, up=UP_Z, cone=.85,
                          what='port drive keel aft face'),
    'pod-port': dict(members=[('Engine 1 drive pod', None), ('Engine 1 pod armour bands', None), (LEG, box(-130, -40, 50, 80, -12, 0))],
                     dir=POD, up=UP_Z, cone=.85, what='port quarter pod and the leg band behind it'),
    'leg-inner-port': dict(members=[(LEG, box(-130, -40, 0, 60, -6, 15)), (STRIPE, None)], dir=LEG_INNER, up=UP_Z, cone=.9,
                           what='port leg inboard wall with the orange stripe face'),
    'aft-stern-port': dict(members=[('Hull aft body', box(-130, -60, 0, 125))], dir=STERN, up=UP_Z, cone=.85,
                           what='aft body stern faces, port half'),
    'pod-top-port': dict(members=[('Engine 1 drive pod', None), ('Engine 1 pod armour bands', None)],
                         dir=UP_Z, up=PORT, cone=.85, what='port quarter pod from above'),
    'tail-slope-port': dict(members=[('Hull tail', box(-130, -48, 0, 30))], dir=TAIL_SLOPE, up=UP_Z, cone=.9, what='tail port upper slope'),
    # ---- the tail (centreline)
    'tail-top': dict(members=[('Hull tail', box(-130, -48))] + _all(['Machinery tail radiator coamings', 'Machinery tail radiator bed',
                                                                    'Machinery tail radiator fins', 'Machinery tail radiator tie bars']),
                     dir=UP_Z, up=PORT, cone=.85, what='tail top with the radiator'),
    'tail-stern': dict(members=[('Hull tail', None)] + _all(['Detail tail face hatches', 'Detail tail face hatch trim',
                                                            'Detail tail face vent frames', 'Detail tail face vent beds']),
                       dir=STERN, up=UP_Z, cone=.85, what='tail stern face'),
    'tail-under': dict(members=[('Hull tail', (-83.8, -2.8, -26.3))], dir=TAIL_UNDER, up=PORT, cone=.85, what='tail underside'),
}



def _mirror_name(name):
    return (name.replace(' port ', ' starboard ').replace('Engine 1 ', 'Engine 4 ').replace('Engine 2 ', 'Engine 3 '))


def _mirror_seed(seed):
    if seed is None:
        return None
    if isinstance(seed, dict):
        (x0, y0, z0), (x1, y1, z1) = seed['box']
        return {'box': ((x0, -y1, z0), (x1, -y0, z1))}
    return (seed[0], -seed[1], seed[2])


def _flip(v):
    return (v[0], -v[1], v[2])


for _p in [p for p in PARTS if p.endswith('-port')]:
    _s = PARTS[_p]
    PARTS[_p[:-5] + '-stbd'] = dict(members=[(_mirror_name(o), _mirror_seed(s)) for o, s in _s['members']],
                                    dir=_flip(_s['dir']), up=_flip(_s['up']), cone=_s['cone'], skin_from=_p, mirror_u=True,
                                    what=_s['what'].replace('port', 'starboard') + ' (mirror of port)')

SHEETS = {
    'stern-blade-v2': ['blade-top-port', 'leg-out-port', 'leg-in-port', 'leg-aft-port'],
    'stern-under': ['leg-under-port', 'quarter-under-port', 'keel-side-port', 'keel-aft-port'],
    'stern-tail-v2': ['tail-top', 'tail-stern', 'tail-under', 'tail-slope-port'],
    'stern-drive': ['pod-port', 'leg-inner-port', 'aft-stern-port', 'pod-top-port'],
}
