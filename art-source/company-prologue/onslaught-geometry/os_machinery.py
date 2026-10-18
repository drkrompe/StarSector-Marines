"""The exposed machinery in the recessed stern wells, and the tail radiator.

The owner: "note the pipes and machinery in the recessed hull in the back of
the ship". onslaught_hegemony.png shows two wells either side, open to space
below the aft body's rim (os_hull cuts them down to WELL_FLOOR):
  outer well  X -18..-80, outboard of the leg: dense pipe bundles. The
              sprite's strokes run aft and OUTBOARD at about 30 deg from the
              centreline (structure tensor over rows 268-320, cols 62-92:
              29-33 deg), parallel to the leg, from the core's aft face to
              the well's after wall;
  inner well  X -22..-100, between the leg and the tail: pale banks of
              fore-and-aft pipes feeding the Engine 2/3 drives, with pumps
              standing among them;
  tail        |Y| < 12, X -68..-109: a dark red-brown ribbed channel between
              pale edges, read as a bank of radiator fins between coamings.
Every run starts and finishes somewhere: one long two-tier header under the
core's aft face feeds both wells, the outer bundles end in a collector along
the well's after wall (or in a pump), the inner banks in an aft header or the
drive feed manifold ahead of the Engine 2/3 drive can. Pipes lie in three
layers of mixed bore on portal frames with clamp straps, with elbows where
they leave the header, drop between layers and dive into the face or a
collector. Mid-distance detail only: nothing under ~0.5 unit radius.
"""
import math
import bpy
from os_hull import WELL_FLOOR, WELL_PORT, LEG_PORT


class Acc:
    def __init__(self):
        self.verts, self.faces = [], []

    def add(self, verts, faces):
        o = len(self.verts)
        self.verts += verts
        self.faces += [tuple(o+i for i in f) for f in faces]

    def build(self, kit, name, mat, bevel=0):
        return kit.mesh(name, self.verts, self.faces, mat, bevel) if self.faces else None


def _unit(a):
    length = sum(c*c for c in a)**.5
    return tuple(c/length for c in a)


def _cross(a, b):
    return (a[1]*b[2]-a[2]*b[1], a[2]*b[0]-a[0]*b[2], a[0]*b[1]-a[1]*b[0])


def _frame(d):
    d = _unit(d)
    u = _unit(_cross(d, (0, 0, 1) if abs(d[2]) < .9 else (1, 0, 0)))
    return d, u, _cross(d, u)


def tube(p0, p1, r0, r1=None, n=8, phase=.5):
    """Capped cylinder (or truncated cone) from p0 to p1."""
    r1 = r0 if r1 is None else r1
    d, u, v = _frame(tuple(q-p for p, q in zip(p0, p1)))
    verts = [tuple(p[c]+r*(math.cos(2*math.pi*(k+phase)/n)*u[c]+math.sin(2*math.pi*(k+phase)/n)*v[c]) for c in range(3))
             for p, r in ((p0, r0), (p1, r1)) for k in range(n)]
    faces = [tuple(reversed(range(n))), tuple(range(n, 2*n))] + [(k, (k+1) % n, n+(k+1) % n, n+k) for k in range(n)]
    return verts, faces


rod = tube


def ring(c, axis, r_in, r_out, half, n=12):
    """Annulus solid about `axis` through c: a flange, hoop, collar or hand wheel."""
    d, u, v = _frame(axis)
    prof = [(-half, r_in), (-half, r_out), (half, r_out), (half, r_in)]
    verts = [tuple(c[i]+a*d[i]+r*(math.cos(2*math.pi*(k+.5)/n)*u[i]+math.sin(2*math.pi*(k+.5)/n)*v[i]) for i in range(3))
             for a, r in prof for k in range(n)]
    m = len(prof)
    faces = [(j*n+k, j*n+(k+1) % n, ((j+1) % m)*n+(k+1) % n, ((j+1) % m)*n+k) for j in range(m) for k in range(n)]
    return verts, faces


def box(x0, x1, y0, y1, z0, z1):
    x0, x1 = sorted((x0, x1))
    y0, y1 = sorted((y0, y1))
    v = [(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0), (x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)]
    return v, [(0, 3, 2, 1), (4, 5, 6, 7), (0, 1, 5, 4), (1, 2, 6, 5), (2, 3, 7, 6), (3, 0, 4, 7)]


def obox(a, b, width, z0, z1):
    """Plan-oriented box along the segment a-b (plan points), `width` across it."""
    dx, dy = b[0]-a[0], b[1]-a[1]
    length = math.hypot(dx, dy)
    nx, ny = -dy/length*width/2, dx/length*width/2
    p = [(a[0]+nx, a[1]+ny), (b[0]+nx, b[1]+ny), (b[0]-nx, b[1]-ny), (a[0]-nx, a[1]-ny)]
    v = [(x, y, z) for z in (z0, z1) for x, y in p]
    return v, [(0, 3, 2, 1), (4, 5, 6, 7), (0, 1, 5, 4), (1, 2, 6, 5), (2, 3, 7, 6), (3, 0, 4, 7)]


def pipe(acc, pts, r, n=8, fittings=None):
    """A polyline pipe; every bend gets an elbow fitting (a short fat sleeve each way)."""
    for p, q in zip(pts, pts[1:]):
        acc.add(*tube(p, q, r, n=n))
    if fittings is not None:
        for a, p, b in zip(pts, pts[1:], pts[2:]):
            for o in (a, b):
                d = _unit(tuple(oc-pc for oc, pc in zip(o, p)))
                fittings.add(*tube(p, tuple(pc+dc*1.6*r for pc, dc in zip(p, d)), 1.28*r, n=n))


# --- plan tests -------------------------------------------------------------

def inside(poly, x, y):
    c = False
    for (ax, ay), (bx, by) in zip(poly, poly[1:]+poly[:1]):
        if (ay > y) != (by > y) and x < ax+(y-ay)*(bx-ax)/(by-ay):
            c = not c
    return c


def seg_dist(poly, x, y, closed=True):
    best = 1e9
    edges = list(zip(poly, poly[1:]+poly[:1])) if closed else list(zip(poly, poly[1:]))
    for (ax, ay), (bx, by) in edges:
        dx, dy = bx-ax, by-ay
        t = max(0, min(1, ((x-ax)*dx+(y-ay)*dy)/(dx*dx+dy*dy)))
        best = min(best, math.hypot(x-ax-t*dx, y-ay-t*dy))
    return best


def march(start, d, ok, step=.25, limit=120):
    """Distance along plan direction d from start until ok(x, y) first fails."""
    t = 0.0
    while t < limit and ok(start[0]+d[0]*(t+step), start[1]+d[1]*(t+step)):
        t += step
    return t


# --- layout (port coordinates; starboard mirrors Y) --------------------------

F = WELL_FLOOR
HDR_X = -22.6                    # header centreline, under the core's aft face (X -18)
HDR_LO, HDR_HI = F+3.4, F+9.6    # the two header tiers
OUT_DIR = (-math.cos(math.radians(30)), math.sin(math.radians(30)))   # outer bundles: aft and outboard
# The collector's line along the outer well's outer and after walls (inset from WELL_PORT).
COLLECTOR = [(-26, 100.6), (-40, 98.6), (-62, 92.2), (-68.6, 80.6)]
# The Engine 1 quarter pod (os_engines) lies along its exhaust line under the well's
# after corner and its armoured back stands above the floor there: bundles end on it.
POD_MOUTH, POD_AXIS, POD_HALF = (-82.0, 70.0), (math.cos(math.radians(-20)), math.sin(math.radians(-20))), 11.0
COLLECTOR_W = 4.2
OUTER_PUMPS = [(-45.0, 84.5, 3.2), (-52.5, 76.5, 2.9)]          # x, y, drum radius
INNER_PUMPS = [(-44.0, 31.0), (-60.0, 27.5)]                     # horizontal turbopumps over the banks
FEED = (-77.5, -73.0, 30.6, 45.2)                                # Engine 2/3 drive feed manifold (x0, x1, y0, y1)
AFT_HDR = (-99.6, -95.2, 23.4, 30.4)


def _outer_ok(r):
    def ok(x, y):
        if not inside(WELL_PORT, x, y) or seg_dist(WELL_PORT, x, y) < r+.6:
            return False
        if seg_dist(COLLECTOR, x, y, closed=False) < COLLECTOR_W/2+.2 or y < 50:
            return False
        if inside(LEG_PORT, x, y) or seg_dist(LEG_PORT, x, y) < r+1.0:
            return False
        a = (x-POD_MOUTH[0])*POD_AXIS[0]+(y-POD_MOUTH[1])*POD_AXIS[1]
        if -4 < a < 32 and abs((x-POD_MOUTH[0])*POD_AXIS[1]-(y-POD_MOUTH[1])*POD_AXIS[0]) < POD_HALF+r:
            return False
        return all(math.hypot(x-px, y-py) > pr+.3 for px, py, pr in OUTER_PUMPS)
    return ok


def _leg_y(x):
    """Inboard face of the leg (LEG_PORT's inner edge) at X; open forward of the leg."""
    pts = [(-36, 52), (-60, 47), (-104, 46)]
    if x > -36:
        return 100.0
    for (ax, ay), (bx, by) in zip(pts, pts[1:]):
        if bx <= x <= ax:
            return ay+(by-ay)*(x-ax)/(bx-ax)
    return 46.0


def build_side(kit, s, mats):
    P = lambda x, y, z: (x, s*y, z)
    pipes_dark, pipes_pale, pipes_bronze = Acc(), Acc(), Acc()
    fittings, frames, headers, pumps, valves = Acc(), Acc(), Acc(), Acc(), Acc()

    # ---- the header under the core's aft face, feeding both wells ----------
    headers.add(*tube(P(HDR_X, 23.6, HDR_LO), P(HDR_X, 101.0, HDR_LO), 2.5, n=10))
    headers.add(*tube(P(HDR_X, 25.0, HDR_HI), P(HDR_X, 97.0, HDR_HI), 1.9, n=10))
    for y in range(28, 100, 9):
        fittings.add(*ring(P(HDR_X, y, HDR_LO), (0, 1, 0), 2.3, 3.15, .55))
        if y < 96:
            fittings.add(*ring(P(HDR_X, y+4.5, HDR_HI), (0, 1, 0), 1.7, 2.45, .45))
    for y in (24.5, 46.0, 66.0, 86.0, 100.0):     # saddles to the floor and brackets to the face
        frames.add(*box(HDR_X-2.2, HDR_X+2.2, s*(y-1.2), s*(y+1.2), F, HDR_LO))
        frames.add(*box(-18.6, HDR_X+1.4, s*(y-.8), s*(y+.8), HDR_HI-1.2, HDR_HI+1.0))
    # Risers: up out of the upper tier and forward into the core's aft face.
    for y in (30.0, 41.0, 58.0, 69.0, 79.0):
        top = F+17.0
        pipe(pipes_bronze, [P(HDR_X, y, HDR_HI), P(HDR_X, y, top), P(-17.4, y, top)], 1.15, fittings=fittings)
        frames.add(*box(-18.9, -17.6, s*(y-2.0), s*(y+2.0), top-2.0, top+2.0))   # wall flange

    # ---- outer well: diagonal bundles, three layers ---------------------------
    d = OUT_DIR
    zA, zB, zC = F+3.4, F+6.6, HDR_HI
    turn = HDR_X-3.6             # elbow out of the header into the diagonal
    runs = []                    # (start, length) of every layer-A line, for the valve blocks
    for k, y0 in enumerate([55.6+2.9*i for i in range(11)]):
        r = (1.35, 1.0, 1.2, .8)[k % 4]
        t = march((turn, y0), d, _outer_ok(r))
        if t < 3:
            continue
        end = (turn+d[0]*t, y0+d[1]*t)
        pipe(pipes_dark, [P(HDR_X, y0, zA), P(turn, y0, zA), P(end[0]+d[0]*1.2, end[1]+d[1]*1.2, zA)], r,
             fittings=fittings)
        runs.append(((turn, y0), t))
    for k, y0 in enumerate([57.0+2.2*i for i in range(14)]):
        r = (.8, .62, .7)[k % 3]
        p1 = (turn-2.4, y0)
        t = march(p1, d, _outer_ok(r))
        if t < 3:
            continue
        end = (p1[0]+d[0]*t, p1[1]+d[1]*t)
        pipe(pipes_pale, [P(HDR_X, y0, zC), P(turn, y0, zC), P(p1[0], p1[1], zB),
                          P(end[0]+d[0]*1.2, end[1]+d[1]*1.2, zB)], r, fittings=fittings)
    for k, y0 in enumerate([61.2+1.45*i for i in range(7)]):     # small conduits riding on top
        p1 = (turn-1.0, y0)
        t = march(p1, d, _outer_ok(1.0))
        if t < 3:
            continue
        end = (p1[0]+d[0]*(t-1.5), p1[1]+d[1]*(t-1.5))
        drop = (end[0]+d[0]*2.6, end[1]+d[1]*2.6)
        pipe(pipes_bronze, [P(HDR_X, y0, zC+.2), P(*p1, zC+1.6), P(*end, zC+1.6), P(*drop, F+6.5)], .55,
             fittings=fittings)

    # Collector along the after wall, every bundle ending in it.
    for a, b in zip(COLLECTOR, COLLECTOR[1:]):
        headers.add(*obox((a[0], s*a[1]), (b[0], s*b[1]), COLLECTOR_W, F, F+8.6))
        frames.add(*obox((a[0], s*a[1]), (b[0], s*b[1]), COLLECTOR_W+1.0, F+8.6, F+9.6))
    for c in COLLECTOR[1:-1]:
        headers.add(*tube(P(c[0], c[1], F), P(c[0], c[1], F+10.2), 2.9, n=8))   # junction drums at the corners

    # Portal frames across the bundles, with clamp straps over layers A and B.
    nrm = (-d[1], d[0])
    for tk in (6.0, 17.0, 29.0, 41.0):
        base = (turn+d[0]*tk, 66.0+d[1]*tk)
        lo = march(base, (-nrm[0], -nrm[1]), _outer_ok(1.0), limit=60)
        hi = march(base, nrm, _outer_ok(1.0), limit=60)
        if lo+hi < 4:
            continue
        a = (base[0]-nrm[0]*lo, base[1]-nrm[1]*lo)
        b = (base[0]+nrm[0]*hi, base[1]+nrm[1]*hi)
        A, B = (a[0], s*a[1]), (b[0], s*b[1])
        frames.add(*obox(A, B, 1.5, F, F+1.9))                      # sill under layer A
        frames.add(*obox(A, B, 1.0, zA+1.45, zA+1.95))              # strap over A
        frames.add(*obox(A, B, 1.0, zB+.95, zB+1.4))                # strap over B
        frames.add(*obox(A, B, 1.3, F+12.0, F+13.2))                # top beam
        for e in (A, B):
            frames.add(*box(e[0]-.8, e[0]+.8, e[1]-.8, e[1]+.8, F, F+13.2))

    # Pumps standing in the bundle: drums with flanges, a motor and a hand wheel,
    # discharging aft to the collector through one big line.
    for px, py, pr in OUTER_PUMPS:
        pumps.add(*tube(P(px, py, F), P(px, py, F+9.0), pr, n=12))
        for z in (F+2.2, F+7.4):
            fittings.add(*ring(P(px, py, z), (0, 0, 1), pr-.2, pr+.7, .45, n=12))
        pumps.add(*box(px-1.9, px+1.9, s*(py-1.9), s*(py+1.9), F+9.0, F+12.4))      # motor
        pumps.add(*tube(P(px, py, F+12.4), P(px, py, F+13.4), 1.3, n=10))
        valves.add(*tube(P(px+pr*.7, py, F+9.0), P(px+pr*.7, py, F+11.4), .3, n=6))
        valves.add(*ring(P(px+pr*.7, py, F+11.6), (0, 0, 1), 1.0, 1.55, .2, n=12))
        p0 = (px+d[0]*(pr-.4), py+d[1]*(pr-.4))
        t = march(p0, d, lambda x, y: _outer_ok(1.75)(x, y) or math.hypot(x-px, y-py) < pr+.4)
        pipe(pipes_dark, [P(*p0, F+4.6), P(p0[0]+d[0]*(t+1.2), p0[1]+d[1]*(t+1.2), F+4.6)], 1.75, n=10)
        fittings.add(*ring(P(p0[0]+d[0]*(t*.5+.6), p0[1]+d[1]*(t*.5+.6), F+4.6), (d[0], s*d[1], 0), 1.6, 2.35, .5))
    # Valve blocks on the big layer-A lines near the collector.
    for (st, t) in runs[1::3]:
        x, y = st[0]+d[0]*(t-5.5), st[1]+d[1]*(t-5.5)
        valves.add(*obox((x-d[0]*1.4, s*(y-d[1]*1.4)), (x+d[0]*1.4, s*(y+d[1]*1.4)), 3.2, zA-1.6, zA+2.0))
        valves.add(*tube(P(x, y, zA+2.0), P(x, y, zA+4.2), .3, n=6))
        valves.add(*ring(P(x, y, zA+4.3), (0, 0, 1), .95, 1.5, .2, n=12))

    # ---- inner well: pale fore-and-aft banks ---------------------------------
    def inner_end(y, r):
        if y+r > FEED[2]-.3:
            return FEED[1]+.8                     # into the drive feed manifold
        return AFT_HDR[1]+.8                      # into the aft header

    def trimmed(y, r):
        x1 = inner_end(y, r)
        while x1 < -30 and y+r+.8 > min(_leg_y(xx) for xx in (x1, x1+3, x1+6)):
            x1 += .5
        return x1

    for k, y in enumerate([24.9+2.25*i for i in range(9)]):
        r = (.95, .8, 1.05)[k % 3]
        pipe(pipes_pale, [P(HDR_X, y, HDR_LO), P(trimmed(y, r), y, HDR_LO)], r)
    for k, y in enumerate([25.9+1.9*i for i in range(10)]):
        r = (.7, .55)[k % 2]
        pipe(pipes_pale, [P(HDR_X, y, HDR_HI), P(HDR_X-3.0, y, HDR_HI), P(HDR_X-5.4, y, F+6.2),
                          P(trimmed(y, r), y, F+6.2)], r, fittings=fittings)
    for y, x1 in ((27.6, AFT_HDR[1]+2.0), (34.0, FEED[1]+2.0), (40.6, FEED[1]+2.0)):   # trunk mains on top
        pipe(pipes_dark, [P(HDR_X, y, HDR_HI), P(HDR_X-3.5, y, F+9.4), P(x1, y, F+9.4), P(x1-2.6, y, F+5.0)],
             1.45 if y < 40 else 1.6, n=10, fittings=fittings)
    headers.add(*box(AFT_HDR[0], AFT_HDR[1], s*AFT_HDR[2], s*AFT_HDR[3], F, F+10.0))
    headers.add(*box(FEED[0], FEED[1], s*FEED[2], s*FEED[3], F, F+9.0))
    frames.add(*box(FEED[0]-.5, FEED[1]+.5, s*(FEED[2]-.5), s*(FEED[3]+.3), F+9.0, F+10.0))
    frames.add(*box(AFT_HDR[0]-.5, AFT_HDR[1]+.5, s*AFT_HDR[2], s*(AFT_HDR[3]+.5), F+10.0, F+11.0))
    for x in (-33.0, -47.0, -61.0, -86.0):
        y1 = min(_leg_y(x)-.6, FEED[2]-.4 if x < FEED[1] else 99)
        frames.add(*box(x-.7, x+.7, s*23.3, s*y1, F, F+2.1))
        frames.add(*box(x-.5, x+.5, s*23.3, s*y1, HDR_LO+1.15, HDR_LO+1.6))
        frames.add(*box(x-.5, x+.5, s*23.3, s*y1, F+7.0, F+7.5))
        frames.add(*box(x-.7, x+.7, s*23.3, s*y1, F+11.6, F+12.8))
        for y in (23.9, y1-.6):
            frames.add(*box(x-.7, x+.7, s*(y-.7), s*(y+.7), F, F+12.8))
    # Turbopumps lying on saddles over the banks, and a vertical pump aft.
    for px, py in INNER_PUMPS:
        pumps.add(*tube(P(px+4.2, py, F+13.6), P(px-4.2, py, F+13.6), 2.4, n=12))
        pumps.add(*tube(P(px-4.2, py, F+13.6), P(px-6.0, py, F+13.6), 2.4, 1.3, n=12))
        for dx in (3.2, -1.0, -3.6):
            fittings.add(*ring(P(px+dx, py, F+13.6), (1, 0, 0), 2.2, 3.0, .4))
        frames.add(*box(px-3.0, px+3.0, s*(py-2.2), s*(py+2.2), F+10.6, F+11.6))
        for dx in (-2.4, 2.4):
            frames.add(*box(px+dx-.6, px+dx+.6, s*(py-1.8), s*(py+1.8), F+10.6, F+12.6))
        pumps.add(*box(px+4.2, px+7.2, s*(py-1.7), s*(py+1.7), F+11.8, F+15.2))   # drive motor
        pipe(pipes_bronze, [P(px-5.4, py, F+13.6), P(px-8.0, py, F+13.6), P(px-8.0, py, F+9.5)], .9, fittings=fittings)
        valves.add(*tube(P(px, py, F+16.0), P(px, py, F+17.4), .3, n=6))
        valves.add(*ring(P(px, py, F+17.5), (0, 0, 1), 1.0, 1.55, .2, n=12))
    pumps.add(*tube(P(-90.0, 27.0, F), P(-90.0, 27.0, F+6.4), 2.4, n=12))
    for z in (F+2.2, F+5.6):
        fittings.add(*ring(P(-90.0, 27.0, z), (0, 0, 1), 2.2, 3.0, .4, n=12))
    pumps.add(*tube(P(-90.0, 27.0, F+6.4), P(-90.0, 27.0, F+7.6), 2.4, 1.4, n=12))
    # Hand wheels on the lower header, facing aft.
    for y in (35.0, 52.0, 74.0, 93.0):
        valves.add(*box(HDR_X-3.6, HDR_X-1.8, s*(y-1.2), s*(y+1.2), HDR_LO-1.2, HDR_LO+1.2))
        valves.add(*tube(P(HDR_X-3.6, y, HDR_LO), P(HDR_X-5.4, y, HDR_LO), .3, n=6))
        valves.add(*ring(P(HDR_X-5.5, y, HDR_LO), (1, 0, 0), 1.1, 1.65, .2, n=12))

    side = 'port' if s > 0 else 'starboard'
    pipes_dark.build(kit, f'Machinery {side} pipe runs', kit.steel)
    pipes_pale.build(kit, f'Machinery {side} pale pipe banks', mats['pale'])
    pipes_bronze.build(kit, f'Machinery {side} conduits and risers', mats['bronze'])
    fittings.build(kit, f'Machinery {side} flanges and elbows', kit.grey)
    frames.build(kit, f'Machinery {side} well frames', kit.dark)
    headers.build(kit, f'Machinery {side} headers', kit.plate, bevel=.2)
    pumps.build(kit, f'Machinery {side} pumps and valves', kit.grey, bevel=.15)
    valves.build(kit, f'Machinery {side} valve blocks and wheels', mats['pale'])


# ---- tail radiator ------------------------------------------------------------

TAIL_FINS = (-68.0, -109.0)      # X extent of the channel, aft of the bridge spine's end (X -66)
FIN_Y = [-9.6+1.6*i for i in range(13)]
COAMING_Y = 11.4


def _strip(xs, y0, y1, h0, h1, zfun):
    """Surface-following bar along X between y0 and y1, from h0 to h1 above the deck."""
    zs = [zfun(x) for x in xs]
    verts = [(x, y, z+h) for x, z in zip(xs, zs) for y, h in ((y0, h0), (y1, h0), (y1, h1), (y0, h1))]
    n = len(xs)
    faces = [(0, 1, 2, 3), tuple(reversed(range(4*(n-1), 4*n)))]
    for i in range(n-1):
        a, b = 4*i, 4*(i+1)
        faces += [(a+j, b+j, b+(j+1) % 4, a+(j+1) % 4) for j in range(4)]
    return verts, faces


def build_tail_radiator(kit, mats):
    xs = [TAIL_FINS[0]-1.6*i for i in range(int((TAIL_FINS[0]-TAIL_FINS[1])/1.6)+1)]
    cache = {}

    def deck(x):
        if x not in cache:
            hit = kit.surface(x, 0.0, top=150)
            cache[x] = hit[0].z if hit else 18.0
        return cache[x]

    bed, fins, edges, ties = Acc(), Acc(), Acc(), Acc()
    bed.add(*_strip(xs, -COAMING_Y+.4, COAMING_Y-.4, -.4, .45, deck))
    for y in FIN_Y:
        fins.add(*_strip(xs[1:-1], y-.32, y+.32, .3, 2.6, deck))
    for s in (1, -1):
        edges.add(*_strip(xs, *sorted((s*COAMING_Y, s*(COAMING_Y+2.4))), -.4, 3.4, deck))
    # Transverse tie bars across the fin tops, and end caps.
    for x in xs[3:-2:6]:
        z = deck(x)
        ties.add(*box(x-.5, x+.5, -COAMING_Y, COAMING_Y, z+2.4, z+3.0))
    for x in (xs[0], xs[-1]):
        z = deck(x)
        ties.add(*box(x-.7, x+.7, -COAMING_Y, COAMING_Y, z-.3, z+3.0))
    bed.build(kit, 'Machinery tail radiator bed', kit.dark)
    fins.build(kit, 'Machinery tail radiator fins', mats['radiator'])
    edges.build(kit, 'Machinery tail radiator coamings', kit.bone, bevel=.2)
    ties.build(kit, 'Machinery tail radiator tie bars', kit.dark)


def materials(kit):
    from os_kit import material
    get = lambda name, *a: bpy.data.materials.get(name) or material(name, *a)
    return {'pale': get('Pale pipe steel', (.34, .31, .29), .65, .42),
            'bronze': get('Conduit bronze', (.22, .12, .07), .7, .45),
            'radiator': get('Radiator fin red-brown', (.13, .045, .03), .55, .5)}


def build(kit):
    mats = materials(kit)
    for s in (1, -1):
        build_side(kit, s, mats)
    build_tail_radiator(kit, mats)
