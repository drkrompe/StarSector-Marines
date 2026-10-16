"""Mudskipper dorsal fittings: the cabin hood, stripe panels, the dorsal cargo
hatch and the aft hatch panels either side of the turret.

Everything here stands on the decks ms_hull builds (WAIST_DECK, FORE_DECK,
AFT_DECK). The turret ring at (-9, 0) is ms_turrets.
"""
from ms_kit import prism, facet, plane_cut, crisp
from ms_hull import HOOD_PLAN, STRIPE_BEND, WAIST_DECK, FORE_DECK, AFT_DECK


def build(kit):
    # Dorsal cabin hood: the light raised housing over the centreline.
    hood = prism(kit, 'Dorsal cabin hood', HOOD_PLAN, [(0,WAIST_DECK-1),(0,11.5),(2,13)], kit.plate)
    plane_cut(hood, 'Hood brow', *facet('Hood brow', (22,0,13), (1,0), 45)[1:])
    crisp(hood)
    # Stripe panels either side of the hood, flat on the deck. As in the
    # sprite, each runs straight aft of x 28 and then angles in at 45 degrees
    # to end against the cockpit's flank.
    for s in (1,-1):
        kit.box('Red stripe panel', (11,s*17.75,WAIST_DECK+.15), (16,3.5,.3), kit.stripe, .05)
        kit.box('Red stripe panel', (23.5,s*17.75,FORE_DECK+.15), (9,3.5,.3), kit.stripe, .05)
        bend = prism(kit, 'Red stripe panel angled', [(x,s*y) for x,y in STRIPE_BEND],
                     [(0,FORE_DECK),(0,FORE_DECK+.3)], kit.stripe)
        crisp(bend, .05)
    # Dorsal cargo hatch between the hood and the turret (the sprite's tan
    # grating) and the frame around the turret well.
    kit.box('Dorsal cargo hatch', (2.5,0,WAIST_DECK+.15), (8,13,.3), kit.tan, .08)
    # The two white dorsal hatch panels either side of the turret.
    for s in (1,-1):
        kit.box('Aft dorsal hatch panel', (-7.5,s*13.5,AFT_DECK+.15), (11,7.5,.3), kit.plate, .08)
        kit.box('Aft dorsal hatch seam', (-7.5,s*13.5,AFT_DECK+.18), (.3,7.6,.3), kit.dark, 0)
