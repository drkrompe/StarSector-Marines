"""The exposed machinery in the recessed stern wells.

The owner: "note the pipes and machinery in the recessed hull in the back of
the ship". onslaught_hegemony.png shows two wells either side, open to space
below the aft body's rim (os_hull cuts them down to WELL_FLOOR):
  outer well  X -18..-72, outboard of the leg: dense pipe bundles running
              diagonally aft and inboard, from a header under the core's aft
              face into the leg's flank;
  inner well  X -22..-100, between the leg and the tail: banks of
              fore-and-aft pipes (the pale ribbing beside the tail) feeding
              the drives, with pumps between them.
Pipes stand in two layers on frames across the well floor and end in
headers, so every run starts and finishes somewhere.
"""
import math
from os_hull import WELL_FLOOR

LEG_EDGE = 64.0         # outer-well pipes run into the leg's outboard flank here


class Acc:
    def __init__(self):
        self.verts, self.faces = [], []

    def add(self, verts, faces):
        o = len(self.verts)
        self.verts += verts
        self.faces += [tuple(o+i for i in f) for f in faces]

    def build(self, kit, name, mat):
        return kit.mesh(name, self.verts, self.faces, mat) if self.faces else None


def _unit(a):
    length = sum(c*c for c in a)**.5
    return tuple(c/length for c in a)


def _cross(a, b):
    return (a[1]*b[2]-a[2]*b[1], a[2]*b[0]-a[0]*b[2], a[0]*b[1]-a[1]*b[0])


def rod(p0, p1, r, n=8):
    d = _unit(tuple(q-p for p, q in zip(p0, p1)))
    u = _unit(_cross(d, (0, 0, 1) if abs(d[2]) < .9 else (1, 0, 0)))
    v = _cross(d, u)
    verts = [tuple(p[c]+r*(math.cos(2*math.pi*(k+.5)/n)*u[c]+math.sin(2*math.pi*(k+.5)/n)*v[c]) for c in range(3))
             for p in (p0, p1) for k in range(n)]
    faces = [tuple(reversed(range(n))), tuple(range(n, 2*n))] + [(k, (k+1) % n, n+(k+1) % n, n+k) for k in range(n)]
    return verts, faces


def box(x0, x1, y0, y1, z0, z1):
    v = [(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0), (x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)]
    return v, [(0, 3, 2, 1), (4, 5, 6, 7), (0, 1, 5, 4), (1, 2, 6, 5), (2, 3, 7, 6), (3, 0, 4, 7)]


def build(kit):
    f = WELL_FLOOR
    for side, s in (('port', 1), ('starboard', -1)):
        pipes, frames, headers, pumps = Acc(), Acc(), Acc(), Acc()
        # Outer well: diagonal bundles, aft and inboard at 30 degrees, two layers.
        d = (-math.cos(math.radians(30)), -math.sin(math.radians(30)))
        for layer, (z, r, step, off) in enumerate(((f+2.0, .95, 2.5, 0.0), (f+4.6, .8, 3.0, 1.2))):
            y = 68.0+off
            while y < 101:
                x = -21.0
                t = (y-LEG_EDGE)/-d[1]                       # run until the leg's flank
                t = min(t, (x+70.0)/-d[0])                   # or the well's aft wall
                if t > 4:
                    p0, p1 = (x, s*y, z), (x+d[0]*t, s*(y+d[1]*t), z)
                    pipes.add(*rod(p0, p1, r))
                y += step
        headers.add(*box(-23.5, -19.0, *sorted((s*66, s*103)), f, f+7.5))
        # Frames across the outer well floor that the bundles sit on.
        for fx in (-30, -44, -58):
            frames.add(*box(fx-.7, fx+.7, *sorted((s*LEG_EDGE, s*99)), f, f+1.2))
        # Inner well: fore-and-aft banks beside the tail, two layers.
        for z, r, y0 in ((f+1.8, .9, 24.5), (f+4.2, .75, 25.8)):
            y = y0
            while y < 44:
                pipes.add(*rod((-24.0, s*y, z), (-97.0, s*y, z), r))
                y += 2.6
        headers.add(*box(-26.0, -21.0, *sorted((s*23.5, s*46)), f, f+7.0))
        headers.add(*box(-99.5, -96.0, *sorted((s*23.5, s*44)), f, f+7.0))
        for fx in (-40, -58, -76, -90):
            frames.add(*box(fx-.7, fx+.7, *sorted((s*23.5, s*45.5)), f, f+1.0))
        # Pumps and valve blocks standing among the inner banks.
        for px, py in ((-48, 34), (-68, 30), (-84, 38)):
            pumps.add(*box(px-3, px+3, *sorted((s*(py-2.6), s*(py+2.6))), f+5.4, f+9.0))
            pumps.add(*rod((px, s*py, f+9.0), (px, s*py, f+11.0), 1.6, 10))
        # Valve blocks in the outer well.
        for px, py in ((-34, 92), (-50, 86)):
            pumps.add(*box(px-2.5, px+2.5, *sorted((s*(py-2.2), s*(py+2.2))), f+5.6, f+9.4))
        pipes.build(kit, f'Machinery {side} pipe runs', kit.steel)
        frames.build(kit, f'Machinery {side} well frames', kit.dark)
        headers.build(kit, f'Machinery {side} headers', kit.plate)
        pumps.build(kit, f'Machinery {side} pumps and valves', kit.grey)
