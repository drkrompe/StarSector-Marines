"""Broken-wreck blockout meshes for the debris field (debris_field), as plain Python: vertices and
faces that fit a box's half extents, so the field's boxes, which --check measures the hiding
against, stay the truth about how big each wreck is, and the picture stops showing boxes.

Owner, 2026-10-22, on preview 12: the cube and slab stand-ins must not read as boxes. They should be
broken hull sections, spars and torn plating with varied silhouettes. Blockout grade is enough.

`wreck(half, seed)` picks a shape from the box's proportions:
- **hull**: long. A lofted, flattened hull that tapers to a bow, with its other end snapped off
  ragged and open, a gash or two torn in its side, and a stub of superstructure;
- **section**: middling. An open hull ring, ragged at both ends, panels missing, a bulkhead inside;
- **plate**: one thin axis. A bent, torn sheet with a ragged outline and holes;
- **spar**: two thin axes. A truss of four longerons and braces, kinked near one end;
- **chunk**: near cubic. A crumpled block with a bite out of one corner.

Each mesh is built in a canonical frame (long axis x, then y, thin axis z) inside [-1, 1]^3, then
its axes are permuted onto the box's own longest, middle and shortest axes and scaled into the unit
cube the builder's matrix stretches by the half extents. Faces are single-sided quads and triangles;
open ends and holes show the hull's inside, which is the point."""
import math
import random


def kind_of(half):
    """The wreck shape for a box of these half extents."""
    a, b, c = sorted(half, reverse=True)
    if b / a < .2:
        return 'spar'
    if c / a < .07:
        return 'plate'
    if a / b > 1.6:
        return 'hull'
    if a / c < 1.35:
        return 'chunk'
    return 'section'


def wreck(half, seed):
    """(kind, vertices, faces) for a box of these half extents: vertices in the box's unit cube
    (the builder's matrix scales them by half), faces as index tuples."""
    rnd = random.Random(seed)
    kind = kind_of(half)
    verts, faces = BUILD[kind](rnd)
    order = sorted(range(3), key=lambda n: -half[n])     # canonical x, y, z onto the box's longest, middle, shortest
    out = []
    for v in verts:
        p = [0.0, 0.0, 0.0]
        for canon, axis in enumerate(order):
            p[axis] = max(-1.0, min(1.0, v[canon]))
        out.append(tuple(p))
    return kind, out, faces


# ------------------------------------------------------------------------------------------ helpers

def _ring(n, wy, wz, rnd, dent=0.0):
    """A flattened octagonal hull cross-section, n points, half widths wy, wz, dented at random."""
    pts = []
    for k in range(n):
        a = 2 * math.pi * k / n
        y, z = math.cos(a), math.sin(a)
        s = 1.0 / max(abs(y) * .78 + abs(z) * .5, abs(y), abs(z))     # an octagon, not a circle
        d = 1.0 - dent * rnd.random()
        pts.append((y * s * wy * d, z * s * wz * d))
    return pts


def _loft(xs, rings, holes=(), close_start=False, close_end=False):
    """Quads joining rings (lists of (y, z)) at stations xs; xs[i] may be a list, one x per ring point
    (a ragged end). holes: (station, point) quads to leave out."""
    verts, faces = [], []
    n = len(rings[0])
    for i, ring in enumerate(rings):
        for k, (y, z) in enumerate(ring):
            x = xs[i][k] if isinstance(xs[i], (list, tuple)) else xs[i]
            verts.append((x, y, z))
    for i in range(len(rings) - 1):
        for k in range(n):
            if (i, k) in holes:
                continue
            a, b = i * n + k, i * n + (k + 1) % n
            faces.append((a, b, b + n, a + n))
    if close_start:
        faces.append(tuple(range(n))[::-1])
    if close_end:
        last = (len(rings) - 1) * n
        faces.append(tuple(range(last, last + n)))
    return verts, faces


def _add(mesh, part):
    verts, faces = mesh
    base = len(verts)
    verts.extend(part[0])
    faces.extend(tuple(base + i for i in f) for f in part[1])
    return mesh


def _beam(p0, p1, r):
    """A square-section member from p0 to p1, half width r."""
    d = [b - a for a, b in zip(p0, p1)]
    ln = math.sqrt(sum(c * c for c in d)) or 1.0
    d = [c / ln for c in d]
    up = (0, 0, 1) if abs(d[2]) < .9 else (0, 1, 0)
    u = (d[1] * up[2] - d[2] * up[1], d[2] * up[0] - d[0] * up[2], d[0] * up[1] - d[1] * up[0])
    un = math.sqrt(sum(c * c for c in u))
    u = [c / un * r for c in u]
    w = (d[1] * u[2] - d[2] * u[1], d[2] * u[0] - d[0] * u[2], d[0] * u[1] - d[1] * u[0])
    corners = [(1, 1), (-1, 1), (-1, -1), (1, -1)]
    verts = []
    for p in (p0, p1):
        for su, sw in corners:
            verts.append(tuple(p[n] + su * u[n] + sw * w[n] for n in range(3)))
    faces = [(k, (k + 1) % 4, (k + 1) % 4 + 4, k + 4) for k in range(4)] + [(3, 2, 1, 0), (4, 5, 6, 7)]
    return verts, faces


# ------------------------------------------------------------------------------------------- shapes

def hull(rnd):
    n, m = 12, 9
    wy, wz = rnd.uniform(.8, 1.0), rnd.uniform(.55, .85)
    taper = rnd.uniform(.25, .55)
    xs, rings = [], []
    for i in range(m):
        f = i / (m - 1)                                   # 0 the broken end, 1 the bow
        x = -1.0 + 2.0 * f
        s = 1.0 if f < .55 else 1.0 - (1.0 - taper) * ((f - .55) / .45) ** 1.4
        if i == 0:
            x = [-1.0 + rnd.uniform(0, .45) for _ in range(n)]      # snapped off ragged
        rings.append(_ring(n, wy * s, wz * s, rnd, dent=.12 if i else .3))
        xs.append(x)
    holes = set()
    for _ in range(rnd.randint(1, 3)):                    # gashes torn in the side
        st, pt = rnd.randint(1, m - 3), rnd.randrange(n)
        for di in range(rnd.randint(1, 2)):
            for dk in range(rnd.randint(1, 3)):
                holes.add((st + di, (pt + dk) % n))
    mesh = _loft(xs, rings, holes, close_end=True)
    # superstructure stub, offset aft of midships on the top
    sx, sw = rnd.uniform(-.4, .2), rnd.uniform(.12, .25)
    top = wz * .9
    box = [(sx - sw, -.3 * wy, top - .05), (sx + sw, .3 * wy, min(1.0, top + rnd.uniform(.15, .3)))]
    _add(mesh, _block(*box))
    return mesh


def section(rnd):
    n, m = 12, 5
    wy, wz = rnd.uniform(.85, 1.0), rnd.uniform(.75, 1.0)
    xs, rings = [], []
    for i in range(m):
        x = -1.0 + 2.0 * i / (m - 1)
        if i == 0:
            x = [-1.0 + rnd.uniform(0, .4) for _ in range(n)]
        elif i == m - 1:
            x = [1.0 - rnd.uniform(0, .4) for _ in range(n)]
        rings.append(_ring(n, wy, wz, rnd, dent=.15))
        xs.append(x)
    holes = {(rnd.randint(0, m - 2), rnd.randrange(n)) for _ in range(rnd.randint(3, 7))}
    mesh = _loft(xs, rings, holes)
    bx = rnd.uniform(-.3, .3)                             # a bulkhead inside, seen through the ends
    bulk = _ring(n, wy * .97, wz * .97, rnd)
    _add(mesh, ([(bx, y, z) for y, z in bulk], [tuple(range(n))]))
    return mesh


def plate(rnd):
    nx, ny = 9, 6
    bend_at = rnd.uniform(-.4, .5)
    bend = rnd.uniform(.25, .7) * rnd.choice((-1, 1))
    verts, idx = [], {}
    for i in range(nx):
        for j in range(ny):
            x, y = -1 + 2 * i / (nx - 1), -1 + 2 * j / (ny - 1)
            edge = i in (0, nx - 1) or j in (0, ny - 1)
            if edge:                                       # a ragged outline
                x *= rnd.uniform(.7, 1.0)
                y *= rnd.uniform(.65, 1.0)
            z = bend * max(0.0, x - bend_at) + rnd.uniform(-.25, .25)
            idx[i, j] = len(verts)
            verts.append((x, y, max(-1.0, min(1.0, z * .9))))
    faces = []
    for i in range(nx - 1):
        for j in range(ny - 1):
            corner = (i in (0, nx - 2)) and (j in (0, ny - 2))
            if (corner and rnd.random() < .6) or rnd.random() < .12:     # torn corners and holes
                continue
            faces.append((idx[i, j], idx[i + 1, j], idx[i + 1, j + 1], idx[i, j + 1]))
    return verts, faces


def spar(rnd):
    mesh = ([], [])
    kink = rnd.uniform(.3, .7)
    ky, kz = rnd.uniform(-.5, .5), rnd.uniform(-.5, .5)
    segs = 6

    def at(x, cy, cz):
        if x > kink:                                       # bent past the kink
            f = (x - kink) / (1 - kink)
            return (x, cy * .6 + ky * f, cz * .6 + kz * f)
        return (x, cy * .6, cz * .6)
    corners = [(1, 1), (-1, 1), (-1, -1), (1, -1)]
    stations = [-1 + 2 * s / segs for s in range(segs + 1)]
    stations[-1] = 1 - rnd.uniform(0, .25)                 # snapped short
    missing = rnd.randrange(4)
    for k, (cy, cz) in enumerate(corners):
        for s in range(segs):
            if k == missing and s >= segs - 2:             # one longeron torn away near the end
                continue
            _add(mesh, _beam(at(stations[s], cy, cz), at(stations[s + 1], cy, cz), .14))
    for s in range(1, segs):
        for k in range(4):
            if rnd.random() < .7:
                c0, c1 = corners[k], corners[(k + 1) % 4]
                _add(mesh, _beam(at(stations[s], *c0), at(stations[s], *c1), .1))
    return mesh


def _block(lo, hi):
    (x0, y0, z0), (x1, y1, z1) = lo, hi
    verts = [(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0),
             (x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)]
    faces = [(3, 2, 1, 0), (4, 5, 6, 7), (0, 1, 5, 4), (1, 2, 6, 5), (2, 3, 7, 6), (3, 0, 4, 7)]
    return verts, faces


def chunk(rnd):
    """A crumpled, torn-open block: a 4x4x4 lattice's surface, each vertex pushed in at random, one
    corner bitten deep and the plating round it torn away, so it shows hollow."""
    n = 4
    bite = [rnd.choice((-1, 1)) for _ in range(3)]
    verts, idx, faces = [], {}, []

    def v(i, j, k):
        if (i, j, k) not in idx:
            p = [-1 + 2 * c / (n - 1) for c in (i, j, k)]
            s = rnd.uniform(.6, 1.0)
            if all(p[a] * bite[a] > .2 for a in range(3)):          # the bitten corner
                s *= .3
            idx[i, j, k] = len(verts)
            verts.append(tuple(c * s for c in p))
        return idx[i, j, k]
    last = n - 1
    for a in range(3):                                     # six faces of the lattice
        for side in (0, last):
            for u in range(last):
                for w in range(last):
                    def g(uu, ww):
                        c = [0, 0, 0]
                        c[a], c[(a + 1) % 3], c[(a + 2) % 3] = side, uu, ww
                        return v(*c)
                    q = (g(u, w), g(u + 1, w), g(u + 1, w + 1), g(u, w + 1))
                    centre = [0.0, 0.0, 0.0]
                    centre[a], centre[(a + 1) % 3], centre[(a + 2) % 3] = side, u + .5, w + .5
                    near = sum((centre[n] / last * 2 - 1) * bite[n] > 0 for n in range(3))
                    if (near == 3 and rnd.random() < .8) or rnd.random() < .1:    # torn away
                        continue
                    faces.append(q if side else q[::-1])
    return verts, faces


BUILD = dict(hull=hull, section=section, plate=plate, spar=spar, chunk=chunk)
