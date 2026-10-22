"""The procedural wreck families of the Asharu kit, as plain Python (wk_geom): every piece is built
from plates, frames and members at real size, never from a primitive.

- **hullfrag**: the bow or stern of a broken frigate or destroyer. A plated hull lofted over ring
  frames, tapering to a closed end, the other end torn off ragged: plates stop short at random,
  the last of them torn or peeled outward, stringers and frames sticking out past the tear. A gash
  or two in the side shows the frames behind; one or two decks; a gutted deckhouse stub.
- **section**: a length of hull torn off at both ends, open, a bulkhead half standing inside.
- **spar**: a truss of angle-iron longerons, battens and diagonals with gussets at the nodes,
  kinked, its far end torn off with the longerons bent; or, for a stubby one, a box girder whose
  webs are cut with lightening holes.
- **plate**: a slab of armour tiles on a ribbed backing, bent across a line, its outline torn,
  tiles missing so the ribs show, a corner peeled back.
- **chunk**: a cargo or machinery module torn out of a hull: a squat, heavily chamfered plated
  body closed by bulkheads, one end half torn away, a shoulder crushed in, drums inside.

Each builder takes the native size (full extents in metres, sorted long, middle, short), a seed
and a livery, and returns a Mesh with its long axis on X, middle on Y, short on Z."""
import math
import random

from wk_geom import (BARE, INTERIOR, PLATING, STRUCTURE, Mesh, Noise, add, bilinear, centroid, cross, dot,
                     field_plate, length, lerp, mul, norm, rotate, shrink, sub, torn_quad)

# Old liveries, as painted (the materials bleach them): (main, accent)
LIVERIES = {
    'oxide': ((.36, .13, .08), (.50, .46, .38)),
    'bone': ((.55, .50, .41), (.42, .25, .12)),
    'hegemony': ((.52, .30, .12), (.45, .42, .36)),
    'bluegrey': ((.26, .30, .34), (.50, .47, .40)),
    'cream': ((.60, .56, .46), (.20, .30, .36)),
    'olive': ((.30, .33, .26), (.45, .40, .30)),
    'umber': ((.27, .19, .13), (.52, .48, .40)),
    'grey': ((.40, .40, .39), (.55, .30, .12)),
}
STEEL = (.30, .29, .27)
PLATE_T = .35          # skin plate thickness, m
GAP = .3               # seam between plates, m (ship-detail-guide: 0.25-0.35)


def _chamfered(w, h, c):
    """A hull cross-section outline: a rectangle of half extents w, h with its corners cut by c (a
    fraction of the smaller), counter-clockwise from the keel centre, as (y, z)."""
    cy, cz = c * min(w, h) * 1.6, c * min(w, h)
    return [(0, -h), (w - cy, -h), (w, -h + cz), (w, h - cz), (w - cy, h), (-w + cy, h), (-w, h - cz),
            (-w, -h + cz), (-w + cy, -h)]


def _resample(poly, n):
    """n points evenly spaced by arc length round the closed polygon."""
    segs = list(zip(poly, poly[1:] + poly[:1]))
    lens = [math.dist(a, b) for a, b in segs]
    total = sum(lens)
    out, k, acc = [], 0, 0.0
    for i in range(n):
        s = total * i / n
        while acc + lens[k] < s:
            acc += lens[k]
            k += 1
        a, b = segs[k]
        t = (s - acc) / lens[k]
        out.append((a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t))
    return out, total


class Body:
    """A lofted, plated hull over ring frames: the shared core of hullfrag and section.

    rings[i][j] is the outer skin point at station i, ring point j; cells (i, j) span stations i..i+1
    and ring points j..j+1."""
    def __init__(self, m, rnd, L, w, h, *, livery, taper=None, chamfer=.35, dent=.05, round_section=False, n_min=12):
        self.m, self.rnd, self.L = m, rnd, L
        self.noise = Noise(rnd.randrange(1 << 30))
        self.main, self.accent = livery
        self.plate_len = max(4.5, min(9.0, L / 12))
        self.nst = max(4, round(L / self.plate_len))
        if round_section:          # a drum: a true circle, enough plates round it that no facet shows
            perim = 2 * math.pi * (w + h) / 2
            self.n = max(n_min, 32, 2 * round(perim / 3.0 / 2))
            base = [(w * math.cos(2 * math.pi * k / self.n - math.pi / 2), h * math.sin(2 * math.pi * k / self.n - math.pi / 2))
                    for k in range(self.n)]
        else:
            outline, perim = _resample(_chamfered(w, h, chamfer), 8)
            self.n = max(n_min, min(44, 2 * round(perim / 6.0 / 2)))
            base, _ = _resample(_chamfered(w, h, chamfer), self.n)
        self.xs = [-L / 2 + L * i / self.nst for i in range(self.nst + 1)]
        self.rings = []
        amp = dent * min(w, h)
        for i, x in enumerate(self.xs):
            s = taper(i / self.nst) if taper else 1.0
            ring = []
            for j, (y, z) in enumerate(base):
                out = norm((0, y, z))
                d = amp * max(0.0, self.noise(x / 18.0, y / 18.0, z / 18.0) * 1.6)
                ring.append((x, y * s - out[1] * d, z * s - out[2] * d))
            self.rings.append(ring)
        self.seg = perim / self.n                      # nominal arc between ring points, m
        self.perim = perim
        self.fronts = []        # (end sign, x of the station end, depth m, noise): tears
        self.holes = []         # (x, s, rx, rs, noise): holes torn in the skin
        self.ends = {}          # station end index -> 'torn'

    def corner(self, i, j):
        return self.rings[i][j % self.n]

    def quad(self, i, j):
        return [self.corner(i, j), self.corner(i, j + 1), self.corner(i + 1, j + 1), self.corner(i + 1, j)]

    def inward(self, quad):
        """Unit normal of the cell pointing into the hull."""
        nrm = norm(cross(sub(quad[1], quad[0]), sub(quad[3], quad[0])))
        c = centroid(quad)
        axis = (c[0], 0.0, 0.0)
        if dot(nrm, sub(axis, c)) < 0:
            nrm = mul(nrm, -1)
        return nrm

    # ------------------------------------------------------------------------------- damage
    # Damage is a field over the skin, f(x, s) in metres (s the arc round the ring): the skin stands
    # where f >= 0. Plates are cut along its contour (wk_geom.field_plate), never along the plate
    # grid, so a tear is jagged and uneven and a hole is ragged, whatever the plating's layout.

    def tear_end(self, end, depth=2):
        """Tears the hull off at station end 0 (aft) or nst (fore), up to depth plate lengths in."""
        self.ends[end] = 'torn'
        d = max(1.0, min(depth, self.nst / 3.5)) * self.plate_len
        x_end = self.xs[0] if end == 0 else self.xs[-1]
        self.fronts.append((1 if end == 0 else -1, x_end, d, Noise(self.rnd.randrange(1 << 30))))

    def gash(self, i0, j0, di, dj):
        """A hole torn in the side, about di plates long and dj plates round, ragged."""
        x = self.xs[min(self.nst, i0)] + di * self.plate_len / 2
        s = (j0 + dj / 2) * self.seg
        self.holes.append((x, s, di * self.plate_len * .55, dj * self.seg * .55, Noise(self.rnd.randrange(1 << 30))))

    def scatter(self, p):
        """Small ragged punctures, about p of the plates' worth of them."""
        for _ in range(int(p * self.nst * self.n / 4) + 1):
            x = self.rnd.uniform(self.xs[0], self.xs[-1])
            s = self.rnd.uniform(0, self.perim)
            r = self.rnd.uniform(.4, .9) * self.plate_len
            self.holes.append((x, s, r, r * self.rnd.uniform(.6, 1.2), Noise(self.rnd.randrange(1 << 30))))

    def front_f(self, x, s):
        """Distance in metres inside the tears (negative past a torn end)."""
        out = 1e9
        for sign, x_end, d, nz in self.fronts:
            a = s / self.perim * 2 * math.pi
            cx, cy = math.cos(a) * 3, math.sin(a) * 3        # round the ring without a seam
            reach = (d * (.55 + .45 * nz(cx, cy)) + .22 * d * nz(cx * 4.1, cy * 4.1, 3.3)
                     + .1 * d * nz(cx * 11, cy * 11, 7.7))
            out = min(out, (x - x_end) * sign - reach)
        return out

    def hole_f(self, x, s):
        out = 1e9
        for hx, hs, rx, rs, nz in self.holes:
            ds = (s - hs + self.perim / 2) % self.perim - self.perim / 2
            dx = x - hx
            ang = math.atan2(ds / rs, dx / rx)
            r = (1 + .3 * nz(math.cos(ang) * 1.5, math.sin(ang) * 1.5) + .2 * nz(math.cos(ang) * 5, math.sin(ang) * 5, 2)
                 + .12 * nz(math.cos(ang) * 13, math.sin(ang) * 13, 6))
            out = min(out, (math.hypot(dx / rx, ds / rs) - r) * min(rx, rs))
        return out

    def field(self, x, s):
        return min(self.front_f(x, s), self.hole_f(x, s))

    # ------------------------------------------------------------------------------- build

    def plates(self, band=None):
        """Every standing skin plate, cut along the damage field; the torn lips curl outward."""
        band_m = self.plate_len * .7

        def lift(fv):
            return 0.0 if fv >= band_m else self.plate_len * .32 * (1 - fv / band_m) ** 2.2
        for i in range(self.nst):
            for j in range(self.n):
                x0, x1 = self.xs[i], self.xs[i + 1]
                s0, s1 = j * self.seg, (j + 1) * self.seg
                samples = [self.field(x0 + (x1 - x0) * u, s0 + (s1 - s0) * v) for u in (0, .5, 1) for v in (0, .5, 1)]
                if max(samples) < -self.plate_len:
                    continue
                q = self.quad(i, j)
                inn = self.inward(q)
                qs = shrink(q, GAP)
                col = self.accent if band and band(j) else self.main
                if self.rnd.random() < .07:
                    col = tuple(min(1, c * self.rnd.uniform(.75, 1.25)) for c in col)      # a replacement plate
                tone = self.rnd.gauss(0, .5)
                if min(samples) > band_m:
                    self.m.prism(qs, mul(inn, PLATE_T), tone=tone, base=col)
                    continue
                f = self._cell_field(x0, x1, s0, s1)
                field_plate(self.m, bilinear(qs), f, inn, PLATE_T, nsub=7, lift=lift, tone=tone, base=col)

    def _cell_field(self, x0, x1, s0, s1):
        return lambda u, v: self.field(x0 + (x1 - x0) * u, s0 + (s1 - s0) * v)

    def frames(self, every=1, depth=1.1, flange=.45):
        """Ring frames inside the skin at every `every`-th station, and stringers between them. Holes
        keep their frames, so the structure shows through them; past a tear, frames stand on for a
        random way beyond the skin, and stringers run on past it and end bent."""
        inset = PLATE_T + depth / 2 + .05
        nst = self.nst
        for i in range(0, nst + 1, every):
            pts = []
            for j in range(self.n + 1):
                p = self.corner(i, j)
                out = norm((0, p[1], p[2]))
                pts.append(sub(p, mul(out, inset)))
            for j in range(self.n):
                beyond = -self.front_f(self.xs[i], (j + .5) * self.seg)
                if beyond > self.rnd.uniform(0, 1.4) * self.plate_len:
                    continue
                a, b = pts[j], pts[j + 1]
                if beyond > 0:                              # standing free past the skin: bent
                    a = add(a, mul(norm((0, a[1], a[2])), self.rnd.uniform(0, .25) * beyond))
                    b = add(b, mul(norm((0, b[1], b[2])), self.rnd.uniform(0, .25) * beyond))
                radial = norm((0, -(a[1] + b[1]) / 2, -(a[2] + b[2]) / 2))
                self.m.member(a, b, radial, flange, depth, 'I')
        step = max(2, self.n // 10)
        for j in range(0, self.n, step):
            s = j * self.seg
            extra = self.rnd.uniform(.3, 1.7) * self.plate_len
            pts = []
            for i in range(nst + 1):
                p = self.corner(i, j)
                out = norm((0, p[1], p[2]))
                pts.append((sub(p, mul(out, PLATE_T + .4)), -self.front_f(self.xs[i], s), out))
            for (a, ba, out), (b, bb, _) in zip(pts, pts[1:]):
                if ba > extra and bb > extra:
                    continue
                if bb > extra or ba > extra:                 # the last length, bent off line
                    bend = (0, self.rnd.uniform(-.25, .25) * self.plate_len, self.rnd.uniform(-.25, .25) * self.plate_len)
                    if bb > extra:
                        b = add(lerp(a, b, .7), bend)
                    else:
                        a = add(lerp(b, a, .7), bend)
                self.m.member(a, b, mul(out, -1), .35, .7, 'L')

    def half_width_at(self, i, z):
        """The ring's inner half width at height z (largest |y| where the ring crosses z)."""
        ring = self.rings[i]
        best = 0.0
        for a, b in zip(ring, ring[1:] + ring[:1]):
            if (a[2] - z) * (b[2] - z) <= 0 and a[2] != b[2]:
                t = (z - a[2]) / (b[2] - a[2])
                best = max(best, abs(a[1] + (b[1] - a[1]) * t))
        return best - PLATE_T - 1.4

    def deck(self, z, miss=.08, torn_reach=2):
        """A deck at height z: plates across the hull on deck beams, gone near torn ends."""
        cw = 5.0
        for i in range(self.nst):
            hw = min(self.half_width_at(i, z), self.half_width_at(i + 1, z))
            if hw < 2:
                continue
            nc = max(2, round(2 * hw / cw))
            near_end = min([abs(i - e) if e == 0 else abs(i - (e - 1)) for e in self.ends] or [99])
            x0, x1 = self.xs[i], self.xs[i + 1]
            for k in range(nc):
                if self.rnd.random() < miss:
                    continue
                y0, y1 = -hw + 2 * hw * k / nc, -hw + 2 * hw * (k + 1) / nc
                q = shrink([(x0, y0, z), (x1, y0, z), (x1, y1, z), (x0, y1, z)], GAP)
                field_plate(self.m, bilinear(q), self._deck_field(x0, x1, y0, y1, hw), (0, 0, -1), .3, nsub=5,
                            top=INTERIOR, bottom=INTERIOR, tone=self.rnd.gauss(0, .4), base=STEEL)
            if near_end >= 1 or self.rnd.random() < .5:
                self.m.member((x0, -hw, z - .65), (x0, hw, z - .65), (0, 0, 1), .35, .7, 'I')

    def _deck_field(self, x0, x1, y0, y1, hw):
        """A deck tears with the hull, a little inboard of the skin's tear."""
        def f(u, v):
            y = y0 + (y1 - y0) * v
            return self.front_f(x0 + (x1 - x0) * u, y / max(hw, 1) * self.perim / 4 + self.perim / 4) - self.plate_len * .4
        return f

    def bulkhead(self, i):
        """A transverse bulkhead at station i: plates fanned in two rings, a third of them gone."""
        ring = self.rings[i]
        x = ring[0][0]
        for j in range(self.n):
            a, b = ring[j], ring[(j + 1) % self.n]
            mid_a, mid_b = lerp((x, 0, 0), a, .5), lerp((x, 0, 0), b, .5)
            in_a, in_b = lerp((x, 0, 0), a, .93), lerp((x, 0, 0), b, .93)
            for q in ([mid_a, in_a, in_b, mid_b], [(x, 0, 0), mid_a, mid_b]):
                if self.rnd.random() < .33:
                    continue
                q = shrink(q, GAP) if len(q) == 4 else q
                self.m.prism(q, (.3, 0, 0), top=INTERIOR, bottom=INTERIOR, tone=self.rnd.gauss(0, .4), base=STEEL)


def _close_bow(body, m):
    """Plates the bow over: the last ring closed with a fan of plates onto a short stem."""
    ring = body.rings[-1]
    x = ring[0][0]
    tip = (x + body.plate_len * .6, 0.0, sum(p[2] for p in ring) / len(ring))
    for j in range(body.n):
        a, b = ring[j], ring[(j + 1) % body.n]
        tri = shrink([a, b, tip, tip], GAP)[:3]
        inn = norm(cross(sub(tri[1], tri[0]), sub(tri[2], tri[0])))
        if dot(inn, sub((x, 0, 0), centroid(tri))) < 0:
            inn = mul(inn, -1)
        m.prism(tri, mul(inn, PLATE_T), tone=body.rnd.gauss(0, .5), base=body.main)


def _deckhouse(m, rnd, x0, x1, hw, z0, z1, livery):
    """A gutted deckhouse: plated walls and roof on corner posts, the roof half torn off, a mast stump."""
    pl = 4.0
    walls = [((x0, -hw), (x1, -hw)), ((x1, -hw), (x1, hw)), ((x1, hw), (x0, hw)), ((x0, hw), (x0, -hw))]
    for (ax, ay), (bx, by) in walls:
        run = math.hypot(bx - ax, by - ay)
        nx = max(1, round(run / pl))
        nz = max(1, round((z1 - z0) / 3.0))
        out = norm((by - ay, -(bx - ax), 0))
        for a in range(nx):
            for b in range(nz):
                if rnd.random() < .18:
                    continue
                p0 = (ax + (bx - ax) * a / nx, ay + (by - ay) * a / nx)
                p1 = (ax + (bx - ax) * (a + 1) / nx, ay + (by - ay) * (a + 1) / nx)
                za, zb = z0 + (z1 - z0) * b / nz, z0 + (z1 - z0) * (b + 1) / nz
                q = shrink([(p0[0], p0[1], za), (p1[0], p1[1], za), (p1[0], p1[1], zb), (p0[0], p0[1], zb)], GAP)
                if b == nz - 1 and rnd.random() < .5:
                    q = torn_quad(q, 2, (zb - za) * .5, rnd)
                m.prism(q, mul(out, -.3), tone=rnd.gauss(0, .5), base=livery[0])
    for cx in (x0, x1):
        for cy in (-hw, hw):
            m.member((cx, cy, z0), (cx, cy, z1 + .3), (1, 0, 0), .6, .6, 'box')
    nx, ny = max(1, round((x1 - x0) / pl)), max(1, round(2 * hw / pl))
    for a in range(nx):
        for b in range(ny):
            if a > nx // 2 and rnd.random() < .7:
                continue
            q = shrink([(x0 + (x1 - x0) * a / nx, -hw + 2 * hw * b / ny, z1),
                        (x0 + (x1 - x0) * (a + 1) / nx, -hw + 2 * hw * b / ny, z1),
                        (x0 + (x1 - x0) * (a + 1) / nx, -hw + 2 * hw * (b + 1) / ny, z1),
                        (x0 + (x1 - x0) * a / nx, -hw + 2 * hw * (b + 1) / ny, z1)], GAP)
            m.prism(q, (0, 0, -.3), tone=rnd.gauss(0, .5), base=livery[0])
    mx = lerp((x0, 0, 0), (x1, 0, 0), .3)[0]
    top = z1 + (z1 - z0) * rnd.uniform(.3, .6)
    m.member((mx, 0, z1), (mx, 0, top), (1, 0, 0), .5, .5, 'box')
    m.member((mx, 0, top), (mx + rnd.uniform(-2, 2), rnd.uniform(-2, 2), top + (z1 - z0) * .15), (1, 0, 0), .35, .35, 'box')


# ------------------------------------------------------------------------------------- families

def hullfrag(size, seed, livery):
    """The bow of a broken hull, torn off aft. size = full (L, W, H)."""
    rnd = random.Random(seed)
    L, W, H = size
    m = Mesh(livery[0])
    hh = H / 2
    # the deckhouse takes the top part of the height; the hull the rest
    house = rnd.random() < .75
    hull_h = hh * (.78 if house else 1.0)
    zc = -hh + hull_h
    tip = rnd.uniform(.28, .45)
    body = Body(m, rnd, L * .9, W / 2, hull_h, livery=livery, chamfer=rnd.uniform(.25, .45),
                taper=lambda f: 1.0 if f < .45 else 1.0 - (1.0 - tip) * ((f - .45) / .55) ** 1.3)
    body.tear_end(0, depth=3)
    for _ in range(rnd.randint(1, 3)):
        body.gash(rnd.randint(1, body.nst - 3), rnd.randrange(body.n), rnd.randint(1, 3), rnd.randint(2, 4))
    body.scatter(.04)
    accent = rnd.randrange(body.n)
    body.plates(band=lambda j: abs(((j - accent + body.n // 2) % body.n) - body.n // 2) <= 1)
    body.frames(every=1)
    body.deck(-hull_h * .25)
    if hull_h > 12:
        body.deck(hull_h * .35)
    _close_bow(body, m)
    m.transform(lambda v: (v[0] - L * .05, v[1], v[2] + zc))
    if house:
        hw = W / 2 * rnd.uniform(.32, .45)
        x0 = rnd.uniform(-.35, -.05) * L
        _deckhouse(m, rnd, x0, x0 + L * rnd.uniform(.14, .22), hw, -hh + hull_h * 2 - .5, hh - 1.5, livery)
    return m


def section(size, seed, livery):
    """A length of hull torn off at both ends."""
    rnd = random.Random(seed)
    L, W, H = size
    m = Mesh(livery[0])
    body = Body(m, rnd, L, W / 2, H / 2, livery=livery, chamfer=rnd.uniform(.25, .5), dent=.07)
    body.tear_end(0, depth=3)
    body.tear_end(body.nst, depth=3)
    for _ in range(rnd.randint(1, 3)):
        body.gash(rnd.randint(1, max(1, body.nst - 3)), rnd.randrange(body.n), rnd.randint(1, 2), rnd.randint(2, 5))
    body.scatter(.05)
    accent = rnd.randrange(body.n)
    body.plates(band=lambda j: j in (accent, (accent + 1) % body.n))
    body.frames(every=1)
    body.deck(-H / 2 * .3)
    if H > 30:
        body.deck(H / 2 * .3)
    body.bulkhead(rnd.randint(max(1, body.nst // 3), max(1, 2 * body.nst // 3)))
    return m


def spar(size, seed, livery, torn=True, kink=True):
    """A truss girder (long, slender) or a box girder (stubby), kinked, torn off at +X."""
    rnd = random.Random(seed)
    L, W, H = size
    if W / L > .22:
        return _box_girder(size, rnd, livery)
    m = Mesh(STEEL)
    hw, hh = W / 2, H / 2
    mem = max(.22, min(W, H) * .11)
    corners = [(hw - mem, hh - mem), (-hw + mem, hh - mem), (-hw + mem, -hh + mem), (hw - mem, -hh + mem)]
    bay = max(2.0, min(W, H) * 1.1)
    nb = max(3, round(L / bay))
    xs = [-L / 2 + L * k / nb for k in range(nb + 1)]
    kink_at = rnd.uniform(.25, .7) * L - L / 2
    kink_ax = norm((0, rnd.uniform(-1, 1), rnd.uniform(-1, 1)))
    # the field gives a kinked arm its own box, so a spar's own kink stays inside its width
    kink_deg = 0.0 if not kink else math.degrees(math.atan(min(W, H) * rnd.uniform(.2, .45) / max(L / 2 - kink_at, 1.0))) * rnd.choice((-1, 1))

    def P(x, c):
        p = (x, c[0], c[1])
        if x > kink_at:
            p = rotate(p, (kink_at, 0, 0), kink_ax, kink_deg)
        return p
    torn = [nb - (rnd.choice((0, 0, 1, 1, 2)) if torn else 0) for _ in corners]
    for k, c in enumerate(corners):
        for b in range(torn[k]):
            a, e = P(xs[b], c), P(xs[b + 1], c)
            if b == torn[k] - 1 and rnd.random() < .7:
                e = add(e, (rnd.uniform(-.5, 1), rnd.uniform(-1, 1) * W * .25, rnd.uniform(-1, 1) * H * .25))
            up = norm((0, -c[0], -c[1]))
            m.member(a, e, up, mem * 1.8, mem * 1.8, 'L', tone=rnd.gauss(0, .3))
    for b in range(1, nb):
        live = [k for k in range(4) if torn[k] >= b]
        for k in range(4):
            k2 = (k + 1) % 4
            if k not in live or k2 not in live or rnd.random() < .12:
                continue
            a, e = P(xs[b], corners[k]), P(xs[b], corners[k2])
            m.member(a, e, (1, 0, 0), mem * .9, mem * .9, 'L')
            if b < nb and k2 in live and torn[k] > b and rnd.random() < .8:
                d0 = P(xs[b], corners[k]) if b % 2 else P(xs[b], corners[k2])
                d1 = P(xs[b + 1], corners[k2]) if b % 2 else P(xs[b + 1], corners[k])
                m.member(d0, d1, (1, 0, 0), mem * .7, mem * .7, 'L')
            if rnd.random() < .5:                     # a gusset plate at the node
                g = mem * 2.2
                cen = P(xs[b], corners[k])
                n_out = norm((0, corners[k][0], corners[k][1]))
                q = [add(cen, (-g, 0, 0)), add(cen, (g, 0, 0)), add(add(cen, (0, 0, 0)), mul(sub(P(xs[b], corners[k2]), cen), .18))]
                m.prism(q, mul(n_out, -.12), top=STRUCTURE, bottom=STRUCTURE, side=BARE)
    # end plate at -X: a mounting flange, torn from whatever it held
    x = xs[0]
    q = shrink([(x, -hw, -hh), (x, hw, -hh), (x, hw, hh), (x, -hw, hh)], .05)
    q = torn_quad(q, rnd.randrange(4), min(W, H) * .3, rnd, n=4)
    m.prism(q, (-.4, 0, 0), top=STRUCTURE, bottom=STRUCTURE, side=BARE)
    return m


def _box_girder(size, rnd, livery):
    """A box girder: four webs of plate cut with lightening holes, diaphragms at the bays, a torn
    end. The holes are the plate left round them: four strips per bay per web."""
    L, W, H = size
    m = Mesh(livery[0])
    hw, hh = W / 2, H / 2
    bay = max(3.0, min(W, H) * .9)
    nb = max(2, round(L / bay))
    xs = [-L / 2 + L * k / nb for k in range(nb + 1)]
    t = .3
    faces = [((0, -1, 0), hh, hw), ((0, 1, 0), hh, hw), ((0, 0, -1), hw, hh), ((0, 0, 1), hw, hh)]
    torn_from = nb - rnd.choice((1, 1, 2))
    for f, (n_out, span, off) in enumerate(faces):
        stop = torn_from + rnd.choice((0, 1)) if f % 2 else torn_from
        for b in range(min(nb, stop + 1)):
            x0, x1 = xs[b] + GAP / 2, xs[b + 1] - GAP / 2
            ring = .28
            for (u0, u1, v0, v1) in ((0, 1, 0, ring), (0, 1, 1 - ring, 1), (0, ring, ring, 1 - ring), (1 - ring, 1, ring, 1 - ring)):
                def pt(u, v):
                    x = x0 + (x1 - x0) * u
                    s = -span + 2 * span * v
                    if n_out[1]:
                        return (x, n_out[1] * off, s)
                    return (x, s, n_out[2] * off)
                q = [pt(u0, v0), pt(u1, v0), pt(u1, v1), pt(u0, v1)]
                if b == stop:
                    q = torn_quad(q, 1, (x1 - x0) * .5, rnd)
                m.prism(q, mul(n_out, -t), tone=rnd.gauss(0, .4))
    for b in range(1, torn_from + 1):
        x = xs[b]
        q = shrink([(x, -hw, -hh), (x, hw, -hh), (x, hw, hh), (x, -hw, hh)], .6)
        if rnd.random() < .5:
            q = torn_quad(q, rnd.randrange(4), min(W, H) * .25, rnd)
        m.prism(q, (.25, 0, 0), top=INTERIOR, bottom=INTERIOR, base=STEEL)
    for cy in (-hw, hw):                               # corner angles running the length
        for cz in (-hh, hh):
            end = xs[min(nb, torn_from + 1)] + rnd.uniform(0, bay * .8)
            m.member((xs[0], cy, cz), (end, cy * 1.0, cz), (0, -cy, -cz), .5, .5, 'L', base=STEEL)
    return m


def plate(size, seed, livery, erode=1.0, holes_n=None, bend=True):
    """A slab of armour torn off a hull: tiles on a ribbed backing, bent. Its outline and its holes
    are a damage field cut along its contour (wk_geom.field_plate), not along the tiles, and the
    torn lips curl up off the slab."""
    rnd = random.Random(seed)
    L, W, H = size
    m = Mesh(livery[0])
    nz = Noise(rnd.randrange(1 << 30))
    tile = max(3.0, min(8.0, L / 9))
    nx, ny = max(3, round(L / tile)), max(2, round(W / tile))
    t_arm = min(.6, max(.25, H * .12))
    rib = min(1.6, max(.5, H * .35))
    edge = min(L, W) * .16 * erode
    holes = []
    for _ in range(rnd.randint(1, 4) if holes_n is None else holes_n):
        r = rnd.uniform(.05, .13) * min(L, W)
        holes.append((rnd.uniform(-.35, .35) * L, rnd.uniform(-.3, .3) * W, r, Noise(rnd.randrange(1 << 30))))

    def field(x, y):
        inside = min(L / 2 - abs(x), W / 2 - abs(y))
        f = inside - edge * (.55 + .45 * nz(x / (L * .18), y / (W * .18))) - edge * .3 * nz(x / 2.2, y / 2.2, 4.4)
        for hx, hy, r, hn in holes:
            ang = math.atan2(y - hy, x - hx)
            rr = r * (1 + .32 * hn(math.cos(ang) * 1.6, math.sin(ang) * 1.6) + .24 * hn(math.cos(ang) * 6, math.sin(ang) * 6, 2)
                      + .14 * hn(math.cos(ang) * 14, math.sin(ang) * 14, 5))
            f = min(f, math.hypot(x - hx, y - hy) - rr)
        return f
    band = tile * .6
    top_lift = max(.0, H - t_arm - rib) * .9

    def lift(fv):
        return 0.0 if fv >= band else top_lift * (1 - fv / band) ** 2      # curled up off the slab
    x0s = [-L / 2 + L * i / nx for i in range(nx + 1)]
    y0s = [-W / 2 + W * j / ny for j in range(ny + 1)]
    for i in range(nx):
        for j in range(ny):
            xa, xb, ya, yb = x0s[i], x0s[i + 1], y0s[j], y0s[j + 1]
            q = shrink([(xa, ya, 0), (xb, ya, 0), (xb, yb, 0), (xa, yb, 0)], GAP)
            f = (lambda xa, xb, ya, yb: lambda u, v: field(xa + (xb - xa) * u, ya + (yb - ya) * v))(xa, xb, ya, yb)
            col = livery[1] if j == ny // 2 and rnd.random() < .5 else livery[0]
            if not field_plate(m, bilinear(q), f, (0, 0, -1), t_arm, nsub=6, lift=lift, tone=rnd.gauss(0, .5), base=col):
                continue
            ym, xm = (ya + yb) / 2, (xa + xb) / 2
            if field(xm, ym) > tile * .3 and rnd.random() < .9:      # ribs under the tile, where it stands
                m.member((xa, ym, -t_arm - rib / 2), (xb, ym, -t_arm - rib / 2), (0, 0, 1), .35, rib, 'I')
            if field(xa, ym) > tile * .3 and rnd.random() < .75:
                m.member((xa, ya, -t_arm - rib / 2), (xa, yb, -t_arm - rib / 2), (0, 0, 1), .3, rib * .8, 'I')
    # bend across a line between tile columns: everything past it turns about it
    bi = rnd.randint(1, nx - 2) if nx > 2 else 1
    xb = x0s[bi]
    target = max(0.0, H - (t_arm + rib) - top_lift - .2)
    run = L / 2 - xb
    ang = math.degrees(math.asin(min(.9, target / max(run, 1)))) * rnd.choice((1, -1)) if target > .2 else 0
    if ang and bend:
        m.transform(lambda p: rotate(p, (xb, 0, 0), (0, 1, 0), ang) if p[0] > xb else p)
    return m


def chunk(size, seed, livery):
    """A cargo or machinery module torn out of a hull: a squat, heavily chamfered plated body with a
    bulkhead at each end, one end half torn away, one shoulder crushed in, drums and racking inside.
    Never a cube: the chamfers take the corners off and the crush and tear break the outline."""
    rnd = random.Random(seed)
    L, W, H = size
    m = Mesh(livery[0])
    body = Body(m, rnd, L, W / 2, H / 2, livery=livery, chamfer=rnd.uniform(.55, .75), dent=.1)
    body.tear_end(body.nst, depth=max(1, body.nst // 3))
    body.gash(rnd.randint(0, max(0, body.nst - 2)), rnd.randrange(body.n), 2, rnd.randint(2, 4))
    body.scatter(.08)
    accent = rnd.randrange(body.n)
    body.plates(band=lambda j: j == accent)
    body.frames(every=1)
    body.bulkhead(0)
    if rnd.random() < .6:
        body.bulkhead(max(1, body.nst // 2))
    hx, hy, hz = L / 2, W / 2, H / 2
    for _ in range(rnd.randint(2, 4)):                 # drums lying along X
        r = min(hy, hz) * rnd.uniform(.2, .32)
        cy, cz = rnd.uniform(-hy * .45, hy * .45), rnd.uniform(-hz * .45, hz * .45)
        x0 = rnd.uniform(-hx + 2, -hx * .2)
        x1 = min(hx * .8, x0 + rnd.uniform(.35, .7) * L)
        n = 28
        circ = [(x0, cy + r * math.cos(2 * math.pi * k / n), cz + r * math.sin(2 * math.pi * k / n)) for k in range(n)]
        m.prism(circ[::-1], (x1 - x0, 0, 0), top=STRUCTURE, bottom=STRUCTURE, side=PLATING, tone=rnd.gauss(0, .5),
                base=livery[1])
        for xb in (x0 + .6, (x0 + x1) / 2, x1 - .6):
            ring = [(xb, cy + (r + .2) * math.cos(2 * math.pi * k / n), cz + (r + .2) * math.sin(2 * math.pi * k / n))
                    for k in range(n)]
            m.prism(ring[::-1], (.5, 0, 0), top=STRUCTURE, bottom=STRUCTURE, side=STRUCTURE)
    # one shoulder crushed in, smoothly
    noise = Noise(rnd.randrange(1 << 30))
    cc = (rnd.uniform(-.3, .5) * hx, rnd.choice((-1, 1)) * hy, rnd.choice((-1, 1)) * hz)
    reach = min(hy, hz) * 1.4

    def squash(p):
        d = math.dist(p, cc)
        if d > reach:
            return p
        f = (1 - d / reach) ** 2
        jitter = noise(p[0] / 6, p[1] / 6, p[2] / 6)
        return (p[0], p[1] - cc[1] / hy * f * reach * (.4 + .15 * jitter), p[2] - cc[2] / hz * f * reach * (.3 + .15 * jitter))
    m.transform(squash)
    return m


BUILDERS = dict(hullfrag=hullfrag, section=section, spar=spar, plate=plate, chunk=chunk)
