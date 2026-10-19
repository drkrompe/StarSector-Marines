"""Phaeton bow: the bridge cab on the starboard bow and the hose-handling
crane on the port bow.

Read off tanker_medium.png:
- the tan cab at X 100..119, Y -13..-31 with lit window slits is the
  bridge, set on the starboard bow shoulder where it sees past the tanks;
- the grey lattice on the port bow is a cargo-hose crane: a post at about
  (96, 25) with an outboard jib running out to Y 55 and a forward jib to
  X 118 at Y 47, braced between them, the hose reel at its foot.
"""
import bpy
from mathutils import Vector
from ph_kit import prism, section

BRIDGE = {'x0': 100.0, 'x1': 119.0, 'y0': -41.0, 'y1': -14.0}
CRANE_POST = (96.0, 25.0)


def strut(kit, name, a, b, r, mat, vertices=8):
    a, b = Vector(a), Vector(b)
    d = b-a
    ob = kit.cylinder(name, (a+b)/2, r, d.length, mat, 'Z', vertices, 0)
    ob.rotation_euler = d.to_track_quat('Z', 'Y').to_euler()
    return ob


def build(kit):
    b = BRIDGE
    deck = kit.surface((b['x0']+b['x1'])/2, (b['y0']+b['y1'])/2)[0].z
    cab = prism(kit, 'Bridge cab', [(b['x0']-3, b['y0']+1), (b['x0']-1, b['y0']-3), (b['x0']+6, b['y0']-3),
                                    (b['x0']+10, b['y0']+1), (b['x1']-3, b['y0']+4), (b['x1'], b['y0']+7), (b['x1'], b['y1']-2),
                                    (b['x1']-3, b['y1']), (b['x0'], b['y1'])],
                section(deck-6, deck+9, 1, 2.5), kit.tan)
    kit.hull.append(cab)
    # Glazing: a forward band and the starboard wing windows.
    kit.box('Bridge forward glazing', (b['x1']+.05, -22, deck+6.3), (.3, 12, 1.6), kit.glass, 0)
    kit.box('Bridge starboard glazing', (110, b['y0']-.05, deck+6.3), (12, .3, 1.6), kit.glass, 0)

    x, y = CRANE_POST
    base = kit.surface(x, y)[0].z
    kit.cylinder('Crane pedestal', (x, y, base+3), 3.0, 6.0, kit.grey, 'Z', 16, .2)
    top = base+12
    kit.box('Crane post', (x, y, base+8), (2.6, 2.6, 10), kit.grey, .15)
    # Box-girder jibs: an outboard jib along +Y to the sprite's tip at Y 56,
    # a forward jib along X at Y 47, a lower chord under each and diagonals.
    strut(kit, 'Crane outboard jib', (x, y, top), (x+1, 56, top+2), 1.6, kit.grey, 4)
    strut(kit, 'Crane outboard jib outer chord', (x+5, y+4, top-1), (x+5, 52, top+1), .8, kit.grey, 4)
    strut(kit, 'Crane outboard jib lower chord', (x+2, y+2, top-3), (x+2, 50, top-1), .7, kit.grey, 4)
    strut(kit, 'Crane forward jib', (x+1, 47.5, top+2), (121, 47.5, top+1), 1.1, kit.grey, 4)
    strut(kit, 'Crane forward jib inner chord', (x+3, 40, top), (122, 41, top), .7, kit.grey, 4)
    strut(kit, 'Crane jib brace', (x+1, y+2, top-1), (116, 46, top+1), .8, kit.grey, 4)
    strut(kit, 'Crane jib brace aft', (x+2, 36, top-1), (108, 30, top-2), .7, kit.grey, 4)
    for i, xi in enumerate((104, 111, 118), 1):
        strut(kit, f'Crane jib diagonal {i}', (xi-5, 47.5, top+2), (xi, 41, top), .45, kit.grey, 4)
    kit.box('Crane jib head', (121.5, 47, top+.5), (3, 3, 2.5), kit.steel, .1)
    kit.cylinder('Crane hose reel', (x-6, y-4, base+2), 2.5, 3.5, kit.dark, 'Y', 16, .1)
