"""Tarsus command module: the dark block that stands forward of the central
body between the two arms (sprite rows 75-81, X 43-50, two orange lamps on
its forward edge), read as the bridge looking out down the gap between the
prongs. Pass 1 is its mass only."""
from ts_kit import prism, section, plane_cut, facet_normal
import ts_hull as H

FRONT, AFT = 51.5, 33.0
HALF = 4.6
KEEL, DECK = -4.0, 13.0


def build(kit):
    cab = prism(kit, 'Bridge command module',
                H.plan([(FRONT, HALF-1.2), (FRONT-1.5, HALF), (AFT, HALF)]),
                section(KEEL, DECK, 2, 2.5), kit.steel)
    # A raked windscreen face over the nose, bounded to the cab's front.
    plane_cut(cab, 'command module windscreen rake', (FRONT-3.5, H.YC, DECK), facet_normal((1, 0), 50))
    kit.hull.append(cab)
