"""Hermes cockpit: the glazed nose and its lamps.

The lights layer (graphics/ships/hermes/hermes_lights.png) places the
glazing: a band across the nose top at X 38..41.5, |Y| < 3, two small
lamps at X 36.5, Y +-1.5 just aft of it, and a lamp either side at
X 34..35, |Y| 7..8. The cockpit sits behind the glazing in the spine,
two seats abreast for the boat's crew of two; the troop bay is aft of it.
Glazing panels are seated on the glacis by ray casts.
"""
import bpy
from hm_kit import material

# Glazing patches (x0, x1, y0, y1) on the glacis, port to starboard.
GLAZING = [(37.5, 41.2, -3.0, 3.0), (36.5, 39.5, 3.4, 6.2), (36.5, 39.5, -6.2, -3.4)]
LAMPS = [(36.5, 1.5), (36.5, -1.5), (34.5, 7.5), (34.5, -7.5)]


def patch(kit, name, x0, x1, y0, y1, mat, offset=.08, count=4, thickness=.18):
    """A surface-hugging plate over a hull rectangle, by ray casts."""
    verts = []
    for i in range(count):
        for j in range(count):
            x = x0 + (x1 - x0)*i/(count - 1)
            y = y0 + (y1 - y0)*j/(count - 1)
            hit = kit.surface_any(x, y)
            if hit is None:
                return None
            p, n = hit
            verts.append(tuple(p + n*offset))
    faces = [(i*count + j, (i+1)*count + j, (i+1)*count + j + 1, i*count + j + 1)
             for i in range(count - 1) for j in range(count - 1)]
    ob = kit.mesh(name, verts, faces, mat)
    sol = ob.modifiers.new('Plate thickness', 'SOLIDIFY')
    sol.thickness, sol.offset = thickness, 1
    return ob


def build(kit):
    names = ['Cockpit centre glazing', 'Cockpit port glazing', 'Cockpit starboard glazing']
    for name, rect in zip(names, GLAZING):
        patch(kit, name, *rect, kit.glass)
    for k, (x, y) in enumerate(LAMPS, 1):
        hit = kit.surface_any(x, y)
        if hit:
            p, n = hit
            kit.cylinder(f'Nose lamp {k}', tuple(p + n*.1), .45, .3, kit.lamp, 'Z', 12, 0)
