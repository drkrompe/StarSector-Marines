"""Broadsword side structure, port and starboard: the gun pod housings, the
armoured wing blades outboard of them, the aft strakes that sweep in to the
engine nacelles, and the low root fairing between pod and fuselage.

From the sprite: the gun housings are Y 8..11 from X 5 aft; the blades
fill |Y| 12..14 from X 2.5 to -4 and step in to 12 by X -5; the trailing
edge runs diagonally from (-8, 12) toward (-12, 8); the strake stops
short of the exhaust lip, which carries the last of that edge. The gap at Y 7..8,
X 3..5 between the housing and the shoulders is real and kept open.
"""
import bw_kit as K
from bw_kit import section_loft, strip, prism, section, mirror_y, corner_s

POD_Y = 9.2  # gun pod centre line: the sprite's housing, Y 8..10 forward of X 1
POD = [  # (x, half width, keel z, deck z, top chamfer, keel chamfer)
    (5.1, .85, -1.0, 1.0, .45, .45),
    (4.4, 1.05, -1.4, 1.3, .5, .5),
    (1.2, 1.15, -1.4, 1.3, .55, .55),
    (-.4, 1.7, -1.4, 1.3, .7, .7),
    (-4.0, 1.7, -1.4, 1.3, .7, .7),
    (-7.0, 1.35, -1.15, 1.0, .55, .55),
]
BLADE = [(3.0, 12.1), (2.3, 14.0), (-3.7, 14.05), (-4.7, 13.0), (-5.8, 12.0), (-6.9, 11.9), (-6.9, 10.8), (0.0, 10.8), (0.0, 12.1)]
STRAKE = [(-.5, 8.2), (-.5, 11.5), (-4.6, 12.1), (-7.8, 12.0), (-10.9, 8.95), (-10.3, 7.6), (-3.0, 7.5)]
# Khaki top plates, drawn rather than inset: an inset of the blade's notch
# or the strake's sharp tail folds over itself.
BLADE_PLATE = [(2.7, 12.5), (2.1, 13.6), (-3.5, 13.6), (-4.4, 12.6), (-5.5, 12.45), (-5.5, 11.2), (.4, 11.2), (.4, 12.5)]
STRAKE_PLATE = [(-1.0, 8.6), (-1.0, 11.1), (-4.6, 11.6), (-7.6, 11.5), (-10.2, 8.9), (-8.0, 8.0)]
FAIRING = [(1.0, 6.0), (1.0, 7.7), (-1.0, 8.2), (-1.0, 5.5)]


def side(s):
    return 'port' if s > 0 else 'starboard'


def plan(poly, s):
    return poly if s > 0 else mirror_y(poly)


def build(kit):
    for s in (1, -1):
        n = side(s)
        pod = section_loft(kit, f'Wing {n} gun pod housing', POD, kit.steel if kit.detail else kit.khaki,
                           cy=s*POD_Y, bevel=.06)
        blade = prism(kit, f'Wing {n} blade', plan(BLADE, s), section(-.6, .6, .3, .35),
                      kit.steel if kit.detail else kit.khaki)
        strake = prism(kit, f'Wing {n} aft strake', plan(STRAKE, s), section(-1.0, .85, .4, .4),
                       kit.steel if kit.detail else kit.khaki)
        prism(kit, f'Wing {n} root fairing', plan(FAIRING, s), section(-1.3, .5, .4, .4), kit.dark)
        kit.hull += [pod, blade, strake]
    if kit.detail:
        detail(kit)


def detail(kit):
    for s in (1, -1):
        n = side(s)
        # Khaki armour over each steel structure, leaving a dark-steel rim
        # at the outer edges as the master paints it.
        strip(kit, f'Wing {n} gun pod armour plate', POD, -6.4, 4.5, .0,
              lambda w, ct: corner_s(w, ct, 2)+.3, kit.khaki, cy=s*POD_Y, side=1, stations=4)
        strip(kit, f'Wing {n} gun pod armour plate inboard', POD, -6.4, 4.5, .0,
              lambda w, ct: corner_s(w, ct, 2)+.3, kit.khaki, cy=s*POD_Y, side=-1, stations=4)
        blade_top = plan(BLADE_PLATE, s)
        prism(kit, f'Wing {n} blade armour plate', blade_top, section(.5, .78, 0, .1), kit.khaki, .03)
        strake_top = plan(STRAKE_PLATE, s)
        prism(kit, f'Wing {n} aft strake armour plate', strake_top, section(.75, 1.02, 0, .12), kit.khaki, .03)
        # Edge rail down the blade's outer edge.
        kit.box(f'Wing {n} blade edge rail', (-.8, s*13.85, .1), (5.6, .45, .9), kit.dark, .05)
        for i, (x, y) in enumerate(((1.0, 12.6), (-3.2, 12.9), (-2.0, 10.0), (-6.0, 10.9), (-8.7, 9.2)), 1):
            kit.bolt(f'Wing {n} bolt {i}', x, s*y)
