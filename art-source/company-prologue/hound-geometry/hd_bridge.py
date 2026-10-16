"""The bridge capsule on the port bow, inside its crash cage.

The sprite's port forward pod (cols 13-27, rows 1-24) is a cylinder with a
lit, glazed nose (the cream lamps at rows 2-8), wrapped in a cage: proud
ring frames at rows 10-11, 14-15 and 18-19 reaching col 13, and fore-aft
bars at cols 14-15 between them. Inboard it leans on the gun fork's port
tine. The capsule is twelve-sided and flat-shaded; the cage is elliptical
so it stays within the hull's depth.
"""
import math
from hd_kit import loft_x

CY, CZ = 9.2, 1.5
CORE = [(20.0, 5.2), (37.4, 5.2)]
NOSE = [(37.4, 5.2), (40.0, 4.7), (42.0, 3.8), (43.1, 2.3)]
# Ring frames at the sprite's rows 23, 18-19, 14-15 and 10-11.
RINGS = [21.5, 26.0, 30.0, 34.0]
RING_OUT, RING_IN, RING_W = (9.7, 7.1), (6.3, 5.4), 1.4
BAR_ANGLES = [90, 45, 0, -45, -90]
BAR_R = (7.9, 6.25)
GLAZING = [(39.0, 39.9), (40.6, 41.5)]


def circle(r, n=12, ry=None, rz=None, phase=math.pi/12):
    ry = r if ry is None else ry
    rz = r if rz is None else rz
    return [(CY+ry*math.cos(phase+k*math.tau/n), CZ+rz*math.sin(phase+k*math.tau/n)) for k in range(n)]


def ring(kit, name, x, mat):
    # An elliptical frame: outer and inner twelve-gons joined front and back.
    n = 16
    outer = circle(0, n, *RING_OUT, phase=0)
    inner = circle(0, n, *RING_IN, phase=0)
    verts = []
    for dx in (-RING_W/2, RING_W/2):
        verts += [(x+dx, y, z) for y, z in outer] + [(x+dx, y, z) for y, z in inner]
    faces = []
    for k in range(n):
        a, b = k, (k+1) % n
        o0, i0, o1, i1 = 0, n, 2*n, 3*n
        faces += [(o0+a, o0+b, o1+b, o1+a), (i0+b, i0+a, i1+a, i1+b),
                  (o0+b, o0+a, i0+a, i0+b), (o1+a, o1+b, i1+b, i1+a)]
    return kit.mesh(name, verts, faces, mat, .08)


def build(kit):
    loft_x(kit, 'Bridge capsule hull', [(x, circle(r)) for x, r in CORE], kit.orange)
    loft_x(kit, 'Bridge capsule nose', [(x, circle(r)) for x, r in NOSE], kit.cream)
    for k, (x0, x1) in enumerate(GLAZING, 1):
        r0 = 4.7 - (x0-40)/2*.9 + .12
        r1 = 4.7 - (x1-40)/2*.9 + .12
        loft_x(kit, f'Bridge glazing band {k}', [(x0, circle(r0)), (x1, circle(r1))], kit.glass, 0)
    for k, x in enumerate(RINGS, 1):
        ring(kit, f'Bridge cage ring {k}', x, kit.steel)
    for k, a in enumerate(BAR_ANGLES, 1):
        t = math.radians(a)
        y, z = CY+BAR_R[0]*math.cos(t), CZ+BAR_R[1]*math.sin(t)
        kit.box(f'Bridge cage bar {k}', ((RINGS[0]+RINGS[-1]+2)/2, y, z), (RINGS[-1]-RINGS[0]+2, 1.0, 1.0), kit.steel, .08)
    # Cradle tying the capsule to the hull's forward face.
    kit.box('Bridge capsule cradle', (19.6, CY-.4, -1.2), (4.2, 10, 6.5), kit.steel, .15)
