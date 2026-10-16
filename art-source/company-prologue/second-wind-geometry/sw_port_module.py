"""Port forward service module: the one asymmetric fitting on the hull.

Placeholder carried over from the V2/V3 blockout: a low teal pod on a neck.
"""


def build(kit):
    # Long axis +X, centred below the top of the pressure hull, PORT only.
    kit.box('Port service pod neck', (37,28,1), (12,8,4), kit.steel, .6)
    kit.box('PORT SERVICE POD - fixed low horizontal body', (37,33,1), (19,8,6), kit.podmat, 2)
    kit.cylinder('Pod outward access hatch', (37,37.1,1), 2.2, .4, kit.steel, 'Y')
    kit.box('Pod dorsal inspection plate', (37,33,4.1), (10,4,.25), kit.cream, .3)
