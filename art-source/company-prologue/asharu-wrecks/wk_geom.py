"""Plain-Python mesh building for the Asharu wreck kit: no bpy, so the procedural families can be
built and measured anywhere and the Blender driver only has to hand the result to from_pydata.

A `Mesh` collects vertices and faces, and per face a material slot, a tone offset (plate-to-plate
variety) and a base colour (the wreck's faded livery, or the hull's own colour for a derived hulk).
Every part is a closed solid: plates are slabs with a seam gap round them, members are swept
sections with end caps, so normals can be recalculated per part and every edge can take an
angle-limited bevel.

Material slots (wk_mat builds the materials in this order):
  PLATING    outer face of a plate: the faded livery
  STRUCTURE  frames, stringers, longerons, braces: bare oxidised steel
  INTERIOR   the inner face of a plate, decks' undersides, a hulk's inside
  BARE       a plate's cut or torn edge: scraped bright metal, so a tear catches the sun
"""
import math
import random

PLATING, STRUCTURE, INTERIOR, BARE = range(4)

# ------------------------------------------------------------------------------------- vectors


def add(a, b):
    return (a[0] + b[0], a[1] + b[1], a[2] + b[2])


def sub(a, b):
    return (a[0] - b[0], a[1] - b[1], a[2] - b[2])


def mul(a, s):
    return (a[0] * s, a[1] * s, a[2] * s)


def dot(a, b):
    return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]


def cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def length(a):
    return math.sqrt(dot(a, a))


def norm(a):
    n = length(a) or 1.0
    return (a[0] / n, a[1] / n, a[2] / n)


def lerp(a, b, t):
    return (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t)


def centroid(pts):
    n = float(len(pts))
    return (sum(p[0] for p in pts) / n, sum(p[1] for p in pts) / n, sum(p[2] for p in pts) / n)


def rotate(p, origin, axis, deg):
    """p turned deg about the line through origin along unit axis (Rodrigues)."""
    v = sub(p, origin)
    k = axis
    c, s = math.cos(math.radians(deg)), math.sin(math.radians(deg))
    r = add(add(mul(v, c), mul(cross(k, v), s)), mul(k, dot(k, v) * (1 - c)))
    return add(origin, r)


class Noise:
    """Smooth seeded value noise in 3D, about -1..1, for dents and ragged edges."""
    def __init__(self, seed):
        self.seed = seed

    def _h(self, i, j, k):
        n = (i * 73856093) ^ (j * 19349663) ^ (k * 83492791) ^ (self.seed * 2654435761)
        n &= 0xffffffff
        n = (n ^ (n >> 13)) * 1274126177 & 0xffffffff
        return (n & 0xffff) / 32767.5 - 1.0

    def __call__(self, x, y=0.0, z=0.0):
        i, j, k = math.floor(x), math.floor(y), math.floor(z)
        fx, fy, fz = x - i, y - j, z - k
        sx, sy, sz = (f * f * (3 - 2 * f) for f in (fx, fy, fz))
        out = 0.0
        for di in (0, 1):
            for dj in (0, 1):
                for dk in (0, 1):
                    w = (sx if di else 1 - sx) * (sy if dj else 1 - sy) * (sz if dk else 1 - sz)
                    out += w * self._h(i + di, j + dj, k + dk)
        return out


# ---------------------------------------------------------------------------------------- mesh

class Mesh:
    def __init__(self, base=(.5, .5, .5)):
        self.verts, self.faces, self.mats, self.tones, self.bases = [], [], [], [], []
        self.base = base

    def face(self, idx, mat, tone=0.0, base=None):
        self.faces.append(tuple(idx))
        self.mats.append(mat)
        self.tones.append(tone)
        self.bases.append(base or self.base)

    def extend(self, other, transform=None):
        o = len(self.verts)
        self.verts.extend(other.verts if transform is None else [transform(v) for v in other.verts])
        self.faces.extend(tuple(i + o for i in f) for f in other.faces)
        self.mats.extend(other.mats)
        self.tones.extend(other.tones)
        self.bases.extend(other.bases)

    def bounds(self):
        lo = tuple(min(v[i] for v in self.verts) for i in range(3))
        hi = tuple(max(v[i] for v in self.verts) for i in range(3))
        return lo, hi

    def recentre(self):
        """Moves the mesh so its bounding box is centred on the origin; returns the half extents."""
        lo, hi = self.bounds()
        c = tuple((lo[i] + hi[i]) / 2 for i in range(3))
        self.verts = [sub(v, c) for v in self.verts]
        return tuple((hi[i] - lo[i]) / 2 for i in range(3))

    def transform(self, fn):
        self.verts = [fn(v) for v in self.verts]

    # ------------------------------------------------------------------------------- solids

    def prism(self, outline, offset, top=PLATING, bottom=INTERIOR, side=BARE, tone=0.0, base=None):
        """A slab: the polygon outline (3D, roughly planar) and its copy moved by offset. The outline's
        face is `top`, the moved copy `bottom`, the walls `side`."""
        n = len(outline)
        o = len(self.verts)
        self.verts.extend(outline)
        self.verts.extend(add(p, offset) for p in outline)
        self.face(range(o, o + n), top, tone, base)
        self.face(range(o + 2 * n - 1, o + n - 1, -1), bottom, tone, base)
        for k in range(n):
            a, b = o + k, o + (k + 1) % n
            self.face((b, a, a + n, b + n), side, tone, base)

    def sweep(self, section, p0, p1, up, mat=STRUCTURE, tone=0.0, cap=True, base=None):
        """A straight member from p0 to p1: the 2D section (u along `up` x dir, v along up) swept."""
        d = norm(sub(p1, p0))
        u = cross(d, up)
        if length(u) < 1e-6:
            u = cross(d, (1, 0, 0) if abs(d[0]) < .9 else (0, 1, 0))
        u = norm(u)
        v = cross(u, d)
        n = len(section)
        o = len(self.verts)
        for p in (p0, p1):
            for a, b in section:
                self.verts.append(add(p, add(mul(u, a), mul(v, b))))
        for k in range(n):
            a, b = o + k, o + (k + 1) % n
            self.face((a, b, b + n, a + n), mat, tone, base)
        if cap:
            self.face(range(o + n - 1, o - 1, -1), mat, tone, base)
            self.face(range(o + n, o + 2 * n), mat, tone, base)

    def member(self, p0, p1, up, w, h, profile='I', mat=STRUCTURE, tone=0.0, base=None):
        """A structural member: 'I' (an I-beam, h deep along up, w flange), 'box', 'L' (angle)."""
        if length(sub(p1, p0)) < 1e-3:
            return
        self.sweep(SECTIONS[profile](w, h), p0, p1, up, mat, tone, base=base)

    def polyline_member(self, pts, up_fn, w, h, profile='I', mat=STRUCTURE, tone=0.0, base=None):
        for a, b in zip(pts, pts[1:]):
            self.member(a, b, up_fn(lerp(a, b, .5)), w, h, profile, mat, tone, base)


def _i_section(w, h):
    t, f = max(.12, w * .18), max(.1, h * .12)
    hw, hh = w / 2, h / 2
    return [(-hw, -hh), (hw, -hh), (hw, -hh + f), (t / 2, -hh + f), (t / 2, hh - f), (hw, hh - f), (hw, hh),
            (-hw, hh), (-hw, hh - f), (-t / 2, hh - f), (-t / 2, -hh + f), (-hw, -hh + f)]


def _box_section(w, h):
    c = min(w, h) * .12
    hw, hh = w / 2, h / 2
    return [(-hw + c, -hh), (hw - c, -hh), (hw, -hh + c), (hw, hh - c), (hw - c, hh), (-hw + c, hh),
            (-hw, hh - c), (-hw, -hh + c)]


def _l_section(w, h):
    t = max(.1, min(w, h) * .2)
    return [(0, 0), (w, 0), (w, t), (t, t), (t, h), (0, h)]


SECTIONS = {'I': _i_section, 'box': _box_section, 'L': _l_section}


# ------------------------------------------------------------------------------ plate helpers

def shrink(quad, gap):
    """A quad's corners pulled in towards its centroid so its edges stand gap/2 off the cell's."""
    c = centroid(quad)
    out = []
    for p in quad:
        d = sub(c, p)
        ln = length(d)
        out.append(add(p, mul(d, min(.45, gap * .7071 / max(ln, 1e-6)))))
    return out


def jag(a, b, n, depth, inward, rnd):
    """Points along a torn edge from a to b (a included, b not), each pushed by up to depth along
    inward: a tear, not a cut."""
    pts = [a]
    for k in range(1, n):
        t = k / n + rnd.uniform(-.3, .3) / n
        pts.append(add(lerp(a, b, t), mul(inward, depth * rnd.uniform(.15, 1.0))))
    return pts


def torn_quad(quad, edge, depth, rnd, n=5):
    """quad with its edge-th side (corner edge to edge+1) torn ragged, inward by up to depth."""
    q = list(quad)
    a, b = q[edge], q[(edge + 1) % 4]
    c = centroid(q)
    mid = lerp(a, b, .5)
    inward = norm(sub(c, mid))
    pts = []
    for k in range(4):
        if k == edge:
            pts.extend(jag(a, b, n, depth, inward, rnd))
        else:
            pts.append(q[k])
    return pts
