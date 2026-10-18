"""Colossus waist and aft body: the keel spine, the aft machinery body and the open lattice frames.

The keel spine carries the cargo body's loads aft through the narrow waist
(X -18..-33) into the aft machinery body, which stands between the drive pods
and carries the bridge deckhouse (cl_bridge). Open lattice trusses tie the
aft side modules and the drive pods to the spine. Planform from colossus.png;
heights proposed. Symmetric about cl_hull.YC.
"""
from cl_kit import prism, section
from cl_hull import AFT_KEEL, AFT_DECK, SIDES, plan, y


def build(kit):
    for side, s in SIDES:
        # Waist truss: open lattice tying the aft side module to the spine and pod.
        kit.box(f'Frame {side} waist truss', (-27, y(34, s), 0), (16, 28, 10), kit.dark, bevel=.3)
        kit.box(f'Frame {side} pod truss', (-52, y(40, s), 0), (40, 10, 14), kit.dark, bevel=.3)
        kit.box(f'Frame {side} pod collar truss', (-24, y(56, s), -1), (10, 12, 8), kit.dark, bevel=.3)
    spine = prism(kit, 'Hull keel spine', plan([(-12, 20), (-46, 20)]), section(-15, 12, 4, 3), kit.red)
    aft = prism(kit, 'Hull aft machinery body',
                plan([(-30, 34), (-80, 35), (-84, 27), (-87, 16), (-90, 9), (-92, 5)]),
                section(AFT_KEEL, AFT_DECK, 5, 4), kit.red)
    kit.hull += [spine, aft]
