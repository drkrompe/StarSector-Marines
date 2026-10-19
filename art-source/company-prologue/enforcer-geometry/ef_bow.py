"""The Enforcer's bow: the neck, the anvil prow and the four missile pods.

A narrow gunmetal neck runs forward from the carapace to the anvil prow, a
broad armoured ram the width of the inner missile pods (41 units). The pods
are gunmetal blocks standing proud of the carapace's forward edge, each
holding a small missile hardpoint (the launchers are ef_mounts').
"""
from ef_kit import (facet_normal, mirror_y, plane_cut, prism, section, sym)

NECK = [(48, 0), (48, 14), (37, 14), (35, 9), (30, 9), (30, 0)]
PROW = [(68.5, 0), (68.5, 9), (66.5, 15), (66, 18), (64, 20.5), (48, 20.5), (46, 18), (46, 0)]
POD_OUTER = [(32, 41), (36, 44), (36.5, 54), (34.5, 58), (31, 60), (18, 60), (17, 41)]          # WS 006 (29, 49)
POD_INNER = [(42.5, 22.5), (46, 26), (46, 35), (42.5, 38.5), (28, 38.5), (27, 22.5)]            # WS 007 (38, 30)


def build(kit):
    neck = prism(kit, 'Hull neck', sym(NECK), section(-5.0, 3.0, 1.5, 1.5), kit.steel, .15)
    kit.hull.append(neck)
    prow = prism(kit, 'Hull prow', sym(PROW), section(-6.5, 4.5, 2.5, 2.0), kit.armour, .2)
    plane_cut(prow, 'prow brow', (63, 0, 3.0), facet_normal((1, 0), 55))
    plane_cut(prow, 'prow chin', (63, 0, -4.5), (.6, 0, -.8))
    kit.hull.append(prow)
    for side, f in (('port', 1), ('starboard', -1)):
        def m(plan):
            return plan if f > 0 else mirror_y(plan)
        for name, plan in (('outer', POD_OUTER), ('inner', POD_INNER)):
            pod = prism(kit, f'Pod {side} {name} missile', m(plan), section(-4.5, 4.5, 1.5, 1.5), kit.steel, .15)
            kit.hull.append(pod)
