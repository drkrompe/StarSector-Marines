"""Colossus bridge: the modest deckhouse on the aft machinery body.

colossus.png puts the bridge at the aft centre: a pale rounded shell (X -42..-71,
Y +-25) with a dark band of ports, standing on the aft body and looking forward
over the cargo body along the spine. A freighter's bridge, not a warship's: one
low deckhouse, a windscreen band across its forward face, wing ports, and the
short mast the sprite's notch suggests.
"""
from cl_kit import prism, section
import cl_hull

X0, X1 = -71.0, -42.0   # aft / forward faces
HALF = 24.0
DECK = cl_hull.AFT_DECK
ROOF = DECK+11.0


def build(kit):
    half = [(X1, HALF-8), (X1-4, HALF-1.5), (X1-10, HALF), (X0+6, HALF), (X0, HALF-6)]
    house = prism(kit, 'Bridge deckhouse', cl_hull.plan(half), section(DECK-1, ROOF, .5, 4.5), kit.pale)
    kit.hull.append(house)
    # Windscreen: a glazed band across the forward face, under the shoulder chamfer.
    kit.box('Bridge windscreen', (X1+.1, cl_hull.YC, ROOF-6.2), (.8, 2*(HALF-9), 1.9), kit.glass, bevel=0)
    for side, s in cl_hull.SIDES:
        kit.box(f'Bridge {side} wing ports', (X1-14, cl_hull.y(HALF+.1, s), ROOF-6.2), (14, .8, 1.5), kit.glass, bevel=0)
    kit.box('Bridge mast base', (X0+8, cl_hull.YC, ROOF+1), (5, 5, 2), kit.steel, bevel=.2)
    kit.cylinder('Bridge mast', (X0+8, cl_hull.YC, ROOF+5), .6, 8, kit.steel, vertices=8)
