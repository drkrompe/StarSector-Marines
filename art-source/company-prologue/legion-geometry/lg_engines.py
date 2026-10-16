"""Engine block and the ten drive units at legion.ship's engine slots.

The sprite's stern (X -96 to -137) is one engine block carrying the drives in
a row: a 24-wide main drive either side (Y +-44), 16-wide drives inboard of
them (Y +-26), 12-wide drives either side of the tail spike (Y +-13) and a
pair of 6-wide drives in each outer housing (Y +-57/62). Each is a whole drive
unit: a can running forward into the block, an aft-facing bell, a mouth lip
and a dark throat. Bell sizes follow the slot widths.
"""
import math
from lg_kit import ccw, mirror_y, prism, section

# (x, y, width, contrailSize, length) in legion.ship order; index i is position i+1.
ENGINES = [(-118, 62, 6, 26, 24), (-118, 57, 6, 26, 24), (-117, -56, 6, 26, 24), (-118, -61, 6, 26, 24),
           (-123, 44, 24, 64, 100), (-124, -44, 24, 64, 100), (-128, 26, 16, 40, 64), (-128, -26, 16, 40, 64),
           (-123, 13, 12, 34, 32), (-123, -12, 12, 34, 32)]
ENGINE_Z = -4.0
MOUTH_R = .47                # mouth radius / slot width
THROAT_R = .30
BELL_DEPTH = .62             # bell length / slot width
CAN_FRONT = -100.0           # drive cans run forward into the block to here

BLOCK_PORT = [(-96, 0), (-96, 84), (-104, 82), (-112, 81), (-112, 0)]
BLOCK = section(-16.0, 8.0, 5.0, 2.0)
HOUSING_PORT = [(-112, 64), (-112, 80), (-124, 79), (-127, 76), (-127, 66), (-124, 64)]
HOUSING = section(-12.0, 4.0, 3.0, 1.5)


def ring(kit, name, x0, x1, r0, r1, thick, mat, y, z, n=28):
    """A thin-walled frustum along X from (x0, radius r0) to (x1, radius r1)."""
    verts, faces = [], []
    for x, r in ((x0, r0), (x1, r1)):
        for rr in (r, r-thick):
            for k in range(n):
                a = 2*math.pi*k/n
                verts.append((x, y+rr*math.cos(a), z+rr*math.sin(a)))
    o0, i0, o1, i1 = 0, n, 2*n, 3*n
    for k in range(n):
        j = (k+1) % n
        faces += [(o0+k, o0+j, o1+j, o1+k), (i0+j, i0+k, i1+k, i1+j),
                  (o0+j, o0+k, i0+k, i0+j), (o1+k, o1+j, i1+j, i1+k)]
    return kit.mesh(name, verts, faces, mat)


def disc(kit, name, x, r, mat, y, z, n=24, t=.2):
    verts = [(x+dx, y+r*math.cos(2*math.pi*k/n), z+r*math.sin(2*math.pi*k/n)) for dx in (-t/2, t/2) for k in range(n)]
    faces = [tuple(range(n)), tuple(range(2*n-1, n-1, -1))] + [(k, (k+1) % n, n+(k+1) % n, n+k) for k in range(n)]
    return kit.mesh(name, verts, faces, mat)


def build(kit):
    block = prism(kit, 'Hull engine block', ccw(BLOCK_PORT + [(x, -y) for x, y in reversed(BLOCK_PORT) if y > 0]),
                  BLOCK, kit.plate)
    kit.hull.append(block)
    for side, plan in (('port', ccw(HOUSING_PORT)), ('starboard', mirror_y(ccw(HOUSING_PORT)))):
        kit.hull.append(prism(kit, f'Hull {side} engine housing', plan, HOUSING, kit.steel))
    for i, (x, y, w, _, _) in enumerate(ENGINES, 1):
        rm, rt, depth = MOUTH_R*w, THROAT_R*w, BELL_DEPTH*w
        throat_x = x+depth
        can_r = rt+.12*w
        kit.cylinder(f'Engine {i} drive can', ((CAN_FRONT+throat_x)/2, y, ENGINE_Z), can_r, CAN_FRONT-throat_x,
                     kit.steel, axis='X', vertices=24, bevel=.1)
        ring(kit, f'Engine {i} aft-facing bell', throat_x, x, rt+.06*w, rm, .05*w, kit.steel, y, ENGINE_Z)
        ring(kit, f'Engine {i} mouth lip', x+.06*w, x-.02*w, rm+.04*w, rm+.03*w, .1*w, kit.plate, y, ENGINE_Z)
        disc(kit, f'Engine {i} dark throat', throat_x-.05*w, rt+.05*w, kit.dark, y, ENGINE_Z)
        kit.empty(f'Engine {i} exhaust anchor', (x, y, ENGINE_Z), 180)


def records():
    return [{'index': i, 'objects': f'Engine {i} ...', 'anchor': f'Engine {i} exhaust anchor',
             'location': [x, y, ENGINE_Z], 'width': w, 'length': length, 'contrailSize': c, 'style': 'LOW_TECH',
             'angle': 180, 'exhaust_direction': [-1, 0, 0], 'mouth_radius': round(MOUTH_R*w, 2),
             'bell': f'Engine {i} aft-facing bell', 'lip': f'Engine {i} mouth lip', 'throat': f'Engine {i} dark throat'}
            for i, (x, y, w, c, length) in enumerate(ENGINES, 1)]
