"""The Enforcer's aft spine, bridge deckhouse and the tanks in the tank bays.

The gunmetal spine runs on the centreline between the tank bays to the stern
notch and carries the bridge: the octagonal deckhouse the sprite draws as a
pale ring at (-15, 0), with a glazing band round it. Each tank bay (cut into
the carapace by ef_hull) holds two pale tanks lying fore and aft.
"""
import math
from ef_kit import (DECK, WELL, prism, section, sym)

SPINE = [(-3, 0), (-3, 10.5), (-42, 10.5), (-36, 5.5), (-32, 0)]
TANKS = [  # (name, y, x aft, x fore, radius): the pale tanks in each bay
    ('outer', 30.0, -41.0, -22.5, 4.2),
    ('inner', 20.0, -41.0, -16.0, 4.0),
]
BRIDGE = (-15.0, 0.0, 6.6)   # deckhouse centre x, y, outer radius


def build(kit):
    spine = prism(kit, 'Hull aft spine', sym(SPINE), section(-6.0, DECK+1.5, 2.0, 1.5), kit.steel, .15)
    kit.hull.append(spine)
    x, y, r = BRIDGE
    octo = [(x+r*math.cos(math.pi/8+k*math.pi/4), y+r*math.sin(math.pi/8+k*math.pi/4)) for k in range(8)]
    house = prism(kit, 'Bridge deckhouse', octo, [(0, DECK+1), (0, DECK+4.6), (1.2, DECK+5.6)], kit.armour, .12)
    kit.hull.append(house)
    band = [(x+(r+.08)*math.cos(math.pi/8+k*math.pi/4), y+(r+.08)*math.sin(math.pi/8+k*math.pi/4)) for k in range(8)]
    prism(kit, 'Bridge glazing band', band, [(0, DECK+3.0), (0, DECK+4.0)], kit.glass, 0)
    for side, f in (('port', 1), ('starboard', -1)):
        for name, ty, xa, xf, rr in TANKS:
            t = kit.cylinder(f'Hull {side} {name} tank', ((xa+xf)/2, f*ty, WELL-1.5+rr+.3), rr, xf-xa,
                             kit.tank, 'X', 16, .25)
            kit.hull.append(t)
