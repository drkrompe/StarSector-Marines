"""The pirate refit (enforcer_pirates.png, skin enforcer_d_pirates): welded
armour over the same hull.

The pirate sprite keeps every slot and engine of the base hull and the same
outline; what changes is the surface. The carapace's smooth armour is stripped
back to its frames in places and red slabs are welded over it: over the tank
bays, on the aft quarters and the bow shoulders, at the beam extremities and
along the aft edge, and a red cruciform plate on the prow face. Girders are
welded across the diamond wells and the tank bays.

Built only for the pirate variant, as its own named objects (`Armour P ...`),
so the base hull's objects and names are the same in both builds and the
merchant's damage scars and the effects key off identical parts.
"""
import math
import bpy
from ef_kit import parent  # noqa: F401  (kept for later passes)

# (x, y, length along its own X, width, angle deg, thickness): port side; mirrored.
SLABS = [
    (-29.0, 31.0, 13.0, 10.0, 0, 1.2),     # over the outer tank
    (-30.0, 54.0, 13.0, 7.0, -35, 1.0),    # aft quarter
    (9.0, 56.0, 12.0, 6.0, 20, 1.0),       # beam, ahead of WS 001
    (30.5, 22.0, 9.0, 5.0, 62, 1.0),       # bow shoulder
    (-17.0, 12.5, 7.0, 5.0, 0, .9),        # beside the bridge
    (-47.5, 40.0, 7.0, 3.5, 90, 1.0),      # aft edge
    (-47.5, 22.0, 9.0, 3.5, 90, 1.0),      # aft edge
    (-4.0, 45.0, 6.0, 5.0, 30, .9),        # between WS 001 and the diamond well
]
# (x0, y0, x1, y1): girders welded across the sunk areas, port side.
GIRDERS = [
    (-12.5, 21.5, 2.5, 34.5), (-12.5, 34.5, 2.5, 21.5),    # X across the diamond well
    (-40.0, 15.0, -40.0, 36.5), (-31.0, 15.0, -31.0, 36.5), (-22.0, 15.0, -22.0, 36.0),  # tank bay frames
]


def slab(kit, name, x, y, length, width, angle, thick):
    hit = kit.surface(x, y)
    z = hit[0].z if hit else 6.0
    ob = kit.box(name, (x, y, z+thick/2-.25), (length, width, thick), kit.accent, .2)
    ob.rotation_euler = (0, 0, math.radians(angle))
    return ob


def girder(kit, name, x0, y0, x1, y1, z):
    dx, dy = x1-x0, y1-y0
    length = math.hypot(dx, dy)
    ob = kit.box(name, ((x0+x1)/2, (y0+y1)/2, z), (length, 1.0, 1.4), kit.steel, .1)
    ob.rotation_euler = (0, 0, math.atan2(dy, dx))
    return ob


def build(kit):
    made = []
    for side, f in (('port', 1), ('starboard', -1)):
        for k, (x, y, length, width, angle, thick) in enumerate(SLABS, 1):
            made.append(slab(kit, f'Armour P {side} slab {k}', x, f*y, length, width, f*angle, thick))
        for k, (x0, y0, x1, y1) in enumerate(GIRDERS, 1):
            made.append(girder(kit, f'Armour P {side} girder {k}', x0, f*y0, x1, f*y1, 5.6))
    # The prow's red cross: a vertical and a horizontal plate on the ram's face.
    made.append(kit.box('Armour P prow plate vertical', (62.0, 0, -.5), (2.0, 4.0, 9.0), kit.accent, .2))
    made.append(kit.box('Armour P prow plate horizontal', (61.2, 0, 1.0), (2.0, 30.0, 3.2), kit.accent, .2))
    # The neck's sensor eye the pirate sprite draws at (42, 0).
    made.append(kit.cylinder('Armour P neck sensor ring', (42.0, 0, 3.4), 2.4, .9, kit.accent, 'Z', 16, .1))
    made.append(kit.cylinder('Armour P neck sensor lens', (42.0, 0, 3.9), 1.4, .4, kit.dark, 'Z', 16, 0))
    bpy.context.view_layer.update()
    return made
