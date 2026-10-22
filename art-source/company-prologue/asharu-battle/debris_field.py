"""The derelict Asharu platform's debris field, as data: plain Python, shared by the blockout's
builder, its --check and the motion preview.

Everything here is in the platform's own metres (battle_blockout.on_platform places it): +X along
the platform's length, +Y its north side (toward the convoy), +Z up. A **piece** is one wreck, a
few boxes (half extents and an attitude, relative to the piece's centre) that turn together, slowly,
about the piece's own axis: a real field tumbles. Nothing is a sphere.

Two kinds of piece:
- a **cover piece**, built against one hiding ship from her pose (`cover_piece`): a hulk, a cargo
  block or a slab of deck, at least her size, set between her and the convoy's line of sight, and
  shifted along her axis by `peek` so her nose (or, negative, her drives) shows past its end;
- **field wreckage** (`field_pieces`): hulks, hull sections, spars and plates, seeded, at scales
  from a few metres to past a Falcon's length, kept out of the ships' launch corridors.

`coverage` asks how much of a ship the convoy can see: rays from points over her bounding box
toward the convoy, the share that a box stops."""
import math
import random

# ship bounding boxes, half extents as fractions of her length: Starsector hulls are broad and flat
SHIP_HALF = (.5, .3, .12)


def rot(hd, pt, rl):
    """motion._rot's convention: Rz(hd) Ry(-pt) Rx(rl); columns are the body's nose, port and up."""
    ch, sh = math.cos(math.radians(hd)), math.sin(math.radians(hd))
    cp, sp = math.cos(math.radians(pt)), math.sin(math.radians(pt))
    cr, sr = math.cos(math.radians(rl)), math.sin(math.radians(rl))
    rz = ((ch, -sh, 0), (sh, ch, 0), (0, 0, 1))
    ry = ((cp, 0, -sp), (0, 1, 0), (sp, 0, cp))
    rx = ((1, 0, 0), (0, cr, -sr), (0, sr, cr))
    return mul(mul(rz, ry), rx)


def mul(a, b):
    return tuple(tuple(sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)) for i in range(3))


def apply(r, v):
    return tuple(sum(r[i][k] * v[k] for k in range(3)) for i in range(3))


def axis_rot(axis, deg):
    x, y, z = axis
    c, s = math.cos(math.radians(deg)), math.sin(math.radians(deg))
    C = 1 - c
    return ((c + x * x * C, x * y * C - z * s, x * z * C + y * s),
            (y * x * C + z * s, c + y * y * C, y * z * C - x * s),
            (z * x * C - y * s, z * y * C + x * s, c + z * z * C))


def unit(v):
    n = math.sqrt(sum(x * x for x in v)) or 1.0
    return tuple(x / n for x in v)


def basis(z, hint):
    """A rotation whose third column is z and whose first is hint made perpendicular to it."""
    z = unit(z)
    d = sum(a * b for a, b in zip(hint, z))
    x = unit(tuple(h - d * zz for h, zz in zip(hint, z)))
    y = (z[1] * x[2] - z[2] * x[1], z[2] * x[0] - z[0] * x[2], z[0] * x[1] - z[1] * x[0])
    return tuple(tuple((x, y, z)[j][i] for j in range(3)) for i in range(3))


class Piece:
    """One wreck: boxes [(centre offset, half extents, rotation)] turning together about axis at
    rate deg/s, posed as built at t0."""
    def __init__(self, name, centre, boxes, axis=(0, 0, 1), rate=0.0, t0=0.0, cover_for=None):
        self.name, self.centre, self.boxes = name, tuple(centre), boxes
        self.axis, self.rate, self.t0, self.cover_for = unit(axis), rate, t0, cover_for

    def spin(self, t):
        return axis_rot(self.axis, self.rate * (t - self.t0))

    def boxes_at(self, t):
        """[(centre, half, rotation)] in the platform's frame at time t."""
        s = self.spin(t)
        return [(tuple(c + o for c, o in zip(self.centre, apply(s, off))), half, mul(s, r))
                for off, half, r in self.boxes]

    def volume(self):
        return sum(8 * h[0] * h[1] * h[2] for _, h, _ in self.boxes)

    def radius(self):
        return max(math.sqrt(sum(x * x for x in off)) + math.sqrt(sum(x * x for x in h)) for off, h, _ in self.boxes)


def half_along(half, r, d):
    """A box's half extent along unit direction d."""
    return sum(abs(sum(r[i][k] * d[i] for i in range(3))) * half[k] for k in range(3))


def cover_piece(name, ship, length, toward, kind, peek=0.0, seed=0, t0=0.0, rate=.15, size=None):
    """A wreck the ship hides behind: between her and `toward` (unit, the convoy's direction), at
    least her size, its long axis along hers, shifted back along her axis by peek x length so her
    nose shows past its end (negative: her drives show past its other end). kind: 'hulk' (a long
    broken hull with a snapped-off end and spars), 'block' (a squat cargo section), 'slab' (a broad
    deck plate face-on to the convoy, with ribs). size: the main box's full (length, width,
    height) in metres, in place of the proportions of her length: a real hull section's size (the
    wreck kit's hulks)."""
    rnd = random.Random(seed)
    x, y, z, hd, pt, rl = ship[:6]
    rs = rot(hd, pt, rl)
    L = length
    nose = tuple(rs[i][0] for i in range(3))
    if kind == 'slab':
        r = basis(toward, nose)
        main = (.68 * L, .55 * L, .07 * L)
    else:
        r = mul(rs, rot(rnd.uniform(-8, 8), rnd.uniform(-6, 6), rnd.uniform(-10, 10)))
        main = (.68 * L, .48 * L, .30 * L) if kind == 'hulk' else (.6 * L, .55 * L, .45 * L)
    if size:
        main = tuple(d / 2 for d in size)
    ship_half = tuple(f * L for f in SHIP_HALF)
    gap = half_along(ship_half, rs, toward) + half_along(main, r, toward) + .04 * L + 6
    c = tuple(p + toward[i] * gap - nose[i] * peek * L for i, p in enumerate((x, y, z)))
    boxes = [((0, 0, 0), main, r)]
    ax = tuple(r[i][0] for i in range(3))
    up = tuple(r[i][2] for i in range(3))
    if kind == 'hulk':           # a snapped-off end hanging askew, and two spars out of the break
        end = tuple(ax[i] * main[0] * 1.15 for i in range(3))
        boxes.append((end, (.18 * L, .36 * L, .22 * L), mul(r, rot(rnd.uniform(15, 30), rnd.uniform(-20, 20), 10))))
        for s in (-1, 1):
            off = tuple(-ax[i] * main[0] * 1.1 + up[i] * s * .12 * L for i in range(3))
            boxes.append((off, (.22 * L, .015 * L + 1, .015 * L + 1), mul(r, rot(s * rnd.uniform(10, 35), 8 * s, 0))))
    elif kind == 'block':        # a torn bulkhead plate leaning off it
        off = tuple(ax[i] * main[0] * .9 + up[i] * main[2] for i in range(3))
        boxes.append((off, (.3 * L, .5 * L, .02 * L + 1), mul(r, rot(0, 25, 12))))
    else:                        # ribs across the back of the slab
        back = tuple(-r[i][2] * (main[2] + .03 * L) for i in range(3))
        for k in (-.5, 0, .5):
            off = tuple(back[i] + ax[i] * k * main[0] for i in range(3))
            boxes.append((off, (.02 * L + 1, .55 * L, .03 * L + 1), r))
    return Piece(name, c, boxes, axis=(rnd.gauss(0, 1), rnd.gauss(0, 1), rnd.gauss(0, 1)), rate=rate, t0=t0,
                 cover_for=None)


def field_pieces(seed, n, extent, keep_out, t0=0.0):
    """Seeded wreckage across the field: n pieces in the ellipsoid of half extents `extent`, denser
    toward the platform, none whose bounds reach a keep-out sphere [(centre, radius)] (launch
    corridors, the cameras). Sizes run from plates of a few metres to hulks past 300 m."""
    rnd = random.Random(seed)
    kinds = (['hulk'] * 7 + ['section'] * 16 + ['spar'] * 16 + ['plate'] * 22)
    out, tries = [], 0
    while len(out) < n and tries < n * 60:
        tries += 1
        kind = rnd.choice(kinds)
        u = [rnd.uniform(-1, 1) for _ in range(3)]
        if sum(v * v for v in u) > 1:
            continue
        k = rnd.uniform(.35, 1.0)
        c = tuple(u[i] * extent[i] * k for i in range(3))
        r = rot(rnd.uniform(0, 360), rnd.uniform(-60, 60), rnd.uniform(0, 360))
        if kind == 'hulk':
            Lh = rnd.uniform(140, 340)
            boxes = [((0, 0, 0), (Lh / 2, Lh * rnd.uniform(.16, .26), Lh * rnd.uniform(.1, .16)), r)]
            ax = tuple(r[i][0] for i in range(3))
            boxes.append((tuple(ax[i] * Lh * .55 for i in range(3)), (Lh * .1, Lh * .2, Lh * .12),
                          mul(r, rot(rnd.uniform(20, 50), rnd.uniform(-25, 25), 0))))
            rate = rnd.uniform(.08, .3)
        elif kind == 'section':
            Ls = rnd.uniform(40, 140)
            boxes = [((0, 0, 0), (Ls / 2, Ls * rnd.uniform(.25, .45), Ls * rnd.uniform(.2, .35)), r)]
            rate = rnd.uniform(.2, .9)
        elif kind == 'spar':
            Lp = rnd.uniform(60, 260)
            w = rnd.uniform(2, 7)
            boxes = [((0, 0, 0), (Lp / 2, w, w), r)]
            if rnd.random() < .5:
                boxes.append((apply(r, (Lp * .3, 0, 0)), (Lp * .15, w * .8, w * .8), mul(r, rot(rnd.uniform(30, 80), 0, 0))))
            rate = rnd.uniform(.4, 2.5)
        else:
            Lq = rnd.uniform(15, 90)
            boxes = [((0, 0, 0), (Lq / 2, Lq * rnd.uniform(.25, .5), rnd.uniform(.6, 1.5)), r)]
            rate = rnd.uniform(.8, 4.0)
        p = Piece('debris %s %d' % (kind, len(out)), c, boxes,
                  axis=(rnd.gauss(0, 1), rnd.gauss(0, 1), rnd.gauss(0, 1)), rate=rate, t0=t0)
        rad = p.radius()
        if any(math.dist(c, kc) < rad + kr for kc, kr in keep_out):
            continue
        out.append(p)
    return out


def free_hulk(name, size, candidates, keep_out, others, seed=0, t0=0.0, rate=.1):
    """One free-standing hulk of full size (length, width, height): at the first candidate centre
    whose bounds clear every keep-out sphere [(centre, radius)] and every other piece, or None."""
    rnd = random.Random(seed)
    half = tuple(d / 2 for d in size)
    for c in candidates:
        r = rot(rnd.uniform(0, 360), rnd.uniform(-30, 30), rnd.uniform(0, 360))
        p = Piece(name, c, [((0, 0, 0), half, r)],
                  axis=(rnd.gauss(0, 1), rnd.gauss(0, 1), rnd.gauss(0, 1)), rate=rate, t0=t0)
        rad = p.radius()
        if any(math.dist(c, kc) < rad + kr for kc, kr in keep_out):
            continue
        if any(math.dist(c, o.centre) < rad + o.radius() for o in others):
            continue
        return p
    return None


def ray_box(o, d, c, half, r):
    """Whether the ray o + s d (s > 0) meets the box."""
    lo, hi = 0.0, 1e12
    rel = tuple(o[i] - c[i] for i in range(3))
    for k in range(3):
        ax = (r[0][k], r[1][k], r[2][k])
        e = sum(rel[i] * ax[i] for i in range(3))
        f = sum(d[i] * ax[i] for i in range(3))
        if abs(f) < 1e-12:
            if abs(e) > half[k]:
                return False
            continue
        t1, t2 = (-half[k] - e) / f, (half[k] - e) / f
        if t1 > t2:
            t1, t2 = t2, t1
        lo, hi = max(lo, t1), min(hi, t2)
        if lo > hi:
            return False
    return True


def ship_points(ship, length, n=5):
    """Points over a ship's bounding box: an n x n x 3 grid, faces and middle."""
    x, y, z, hd, pt, rl = ship[:6]
    r = rot(hd, pt, rl)
    h = tuple(f * length for f in SHIP_HALF)
    pts = []
    for a in range(n):
        for b in range(n):
            for c in (-1, 0, 1):
                v = (h[0] * (2 * a / (n - 1) - 1), h[1] * (2 * b / (n - 1) - 1), h[2] * c)
                w = apply(r, v)
                pts.append((x + w[0], y + w[1], z + w[2]))
    return pts


def coverage(ship, length, toward, boxes):
    """The share of the ship's bounding-box points the convoy cannot see: rays toward it stopped."""
    pts = ship_points(ship, length)
    near = [b for b in boxes if True]
    hid = sum(1 for p in pts if any(ray_box(p, toward, c, h, r) for c, h, r in near))
    return hid / len(pts)
