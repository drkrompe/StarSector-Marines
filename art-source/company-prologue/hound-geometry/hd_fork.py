"""The gun fork on the starboard bow: the structure around hardpoint WS 001.

The sprite's starboard forward block (cols 28-47, rows 5-24) is an H: a
solid base (rows 15-24), two tall tines running forward with twin points
(cols 29-34 and 41-47), and a cross bar (row 11) over the gap they leave
(cols 35-40) for the medium hardpoint. The tines rake down to their points
and their chins rake up, so the fork reads as armour, not a slab.
"""
from hd_kit import facet_normal, plane_cut, prism, section

BASE_PLAN = [(19.5, -16), (28.4, -16), (29.4, -15), (29.4, 2), (28.4, 3), (19.5, 3)]
BASE = section(-4.6, 4.4, 1.6, 1.0)
TINES = {'port': (-3, 3), 'starboard': (-16, -9)}
TINE = section(-4.2, 4.8, 1.2, .9)


def tine_plan(y0, y1):
    # Twin points at the tip (sprite rows 5-6), a notch between them.
    ym = (y0+y1)/2
    return [(26, y0), (38, y0), (40, y0+.5), (40, ym-1.1), (38.6, ym-.3), (38.6, ym+.3),
            (40, ym+1.1), (40, y1-.5), (38, y1), (26, y1)]


def build(kit):
    base = prism(kit, 'Fork base', BASE_PLAN, BASE, kit.orange)
    plane_cut(base, 'Fork base chin rake', (29.4, 0, -2.6), facet_normal((1, 0), 135))
    for side, (y0, y1) in TINES.items():
        tine = prism(kit, f'Fork {side} tine', tine_plan(y0, y1), TINE, kit.steel)
        plane_cut(tine, f'Fork {side} tine top rake', (33, 0, 4.8), facet_normal((1, 0), 24))
        plane_cut(tine, f'Fork {side} tine chin rake', (33, 0, -4.2), facet_normal((1, 0), 150))
    kit.box('Fork cross bar', (33.6, -6, 3.6), (1.6, 6.4, 1.6), kit.steel, .1)
    kit.hull.append(base)
