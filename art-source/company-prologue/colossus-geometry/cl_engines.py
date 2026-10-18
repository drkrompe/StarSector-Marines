"""Colossus drives: two drive pods aft, port and starboard, three LOW_TECH drives each.

Engine slots from colossus.ship, in file order (index i = 1..6), all angle 180,
width 15, length 56: port 1 (-124, 64), 2 (-124, 56), 3 (-124, 49); starboard
4 (-124, -61), 5 (-124, -54), 6 (-124, -46). Read off colossus.png, each pod is
one long armoured drive unit (X -31..-125) that widens past a diagonal shoulder
at X -56..-66 and ends in a row of three nozzles on its inboard half: the pod is
the drive, and the bells are its mouths, not nozzles on the hull.

Names: 'Drive pod <side> casing' for the pod, and per slot the Second Wind's
'Engine <i> aft-facing bell', 'Engine <i> mouth lip', 'Engine <i> dark throat'
and 'Engine <i> exhaust anchor' (an empty on the mouth whose +X is the exhaust
direction).
"""
from cl_kit import loft_x, octagon, revolve_x, material
import cl_hull

# (x, y, width, angle) exactly as colossus.ship lists them.
ENGINES = [(-124, 64, 15, 180), (-124, 56, 15, 180), (-124, 49, 15, 180),
           (-124, -61, 15, 180), (-124, -54, 15, 180), (-124, -46, 15, 180)]
POD_Z = 0.0
# Pod envelope fore to aft: (x, inboard rel y, outboard rel y, half-height).
ENVELOPE = [(-30, 47, 71, 10), (-33, 44, 73.5, 14), (-55, 44, 75, 15), (-60, 43, 79.5, 15.5), (-67, 42, 84, 16),
            (-108, 40, 83, 16), (-111, 40, 80, 15.5), (-116, 40, 74.5, 14.5), (-125, 43.5, 69, 11)]
BELL = {'mouth_r': 3.6, 'throat_r': 2.2, 'length': 4.0}
ANCHORS = {}


def build(kit):
    ANCHORS.clear()
    liner = material('Drive bell liner', (.085, .09, .095), .75, .42)
    for side, s in cl_hull.SIDES:
        stations = []
        for x, a, b, h in reversed(ENVELOPE):
            y0, y1 = sorted((cl_hull.y(a, s), cl_hull.y(b, s)))
            stations.append((x, octagon(y0, y1, POD_Z-h, POD_Z+h, min(6, h*.45))))
        pod = loft_x(kit, f'Drive pod {side} casing', stations, kit.maroon)
        kit.hull.append(pod)
    for i, (x, y, w, angle) in enumerate(ENGINES, 1):
        m, t, length = BELL['mouth_r'], BELL['throat_r'], BELL['length']
        x0 = x-1.5          # bell sinks 2.5 units into the pod's aft face
        x1 = x0+length
        # Outer skin of the bell, closed profile mouth -> throat -> back out.
        revolve_x(kit, f'Engine {i} aft-facing bell',
                  [(x0, m), (x1, t+.8), (x1, t), (x0+.3, m-.5)], y, POD_Z, kit.steel, n=20)
        revolve_x(kit, f'Engine {i} mouth lip', [(x0-.2, m+.35), (x0+.5, m+.35), (x0+.5, m-.2), (x0-.2, m-.2)],
                  y, POD_Z, kit.pale, n=20)
        revolve_x(kit, f'Engine {i} dark throat', [(x0+.4, m-.6), (x1-.2, t-.2), (x1-.2, 0.01), (x0+.4, 0.01)],
                  y, POD_Z, liner, n=20)
        loc = (x0, y, POD_Z)
        kit.empty(f'Engine {i} exhaust anchor', loc, angle)
        ANCHORS[i] = (loc, (-1.0, 0.0, 0.0))
