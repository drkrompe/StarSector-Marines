"""Engine block and the ten drive units at legion.ship's engine slots.

The sprite's stern (X -96 to -137) is one engine block carrying the drives in
a row: a 24-wide main drive either side (Y +-44), 16-wide drives inboard of
them (Y +-26), 12-wide drives either side of the tail spike (Y +-13) and a
pair of 6-wide drives in each outer housing (Y +-57/62).

Each is a whole drive unit set into the block, not a nozzle on a wall. The
sprite shows every drive's chamber from above, lying in its own bay in the
block deck between ribbed machinery, and its bell running on aft past the
block face. So each drive sits in a U-shaped bay cut into the block (open on
top and aft, a bulkhead ahead of it), carried on cradle saddles, with:
  drive can       the combustion chamber, banded with cooling-jacket hoops;
  gimbal ring     a heavy ring with trunnion bosses at the throat;
  actuators       gimbal rams from the chamber collar to the bell's first
                  hoop (the three larger drive sizes);
  aft-facing bell a flaring (parabolic) bell, ribbed and hooped outside;
  mouth lip       a thick rolled rim at the slot X, where exhaust starts;
  dark throat     the dark core at the throat.
The dividers left between the bays carry fore-and-aft radiator fins (the
sprite's ribbed lines); the block face carries frames and service ledges,
and the block and outer housings carry raised armour plates with real seam
gaps and an upward-facing strake for the high sun to catch.

Bell mouth radius is MOUTH_R x width, except the 24-wide mains: the sprite
draws their bells about 17-18 px across, and at 0.47 x 24 they overlapped the
16-wide drives' bells beside them, so mains use MAIN_MOUTH_R.
"""
import math
from lg_kit import boolean, ccw, mirror_y, prism, section

# (x, y, width, contrailSize, length) in legion.ship order; index i is position i+1.
ENGINES = [(-118, 62, 6, 26, 24), (-118, 57, 6, 26, 24), (-117, -56, 6, 26, 24), (-118, -61, 6, 26, 24),
           (-123, 44, 24, 64, 100), (-124, -44, 24, 64, 100), (-128, 26, 16, 40, 64), (-128, -26, 16, 40, 64),
           (-123, 13, 12, 34, 32), (-123, -12, 12, 34, 32)]
ENGINE_Z = -4.0
MOUTH_R = .47                # mouth radius / slot width
MAIN_MOUTH_R = .37           # 24-wide mains, read off the sprite (see module doc)
THROAT_OF_MOUTH = .36        # throat radius / mouth radius
CAN_OF_THROAT = 1.55         # chamber (drive can) radius / throat radius
BELL_DEPTH = {24: .80, 16: .85, 12: .80, 6: .75}   # bell length / slot width, read off the sprite
CAN_FRONT = -97.5            # drive cans run forward into the bay bulkhead to here
BAY_FRONT = -98.5            # forward bulkhead of every drive bay
BLOCK_AFT = -112.0           # the block's aft face

BLOCK_PORT = [(-96, 0), (-96, 84), (-104, 82), (-112, 81), (-112, 0)]
BLOCK = section(-16.0, 8.0, 5.0, 2.0)
BLOCK_DECK = 8.0
HOUSING_PORT = [(-112, 64), (-112, 80), (-124, 79), (-127, 76), (-127, 66), (-124, 64)]
HOUSING = section(-12.0, 4.0, 3.0, 1.5)


# --- geometry accumulation -------------------------------------------------

class Acc:
    """Vertices and faces of several closed shells merged into one object."""
    def __init__(self):
        self.verts, self.faces = [], []

    def add(self, verts, faces):
        o = len(self.verts)
        self.verts += verts
        self.faces += [tuple(o+i for i in f) for f in faces]

    def build(self, kit, name, mat):
        return kit.mesh(name, self.verts, self.faces, mat) if self.faces else None


def revolve(poly, y, z, n, phase=0.0):
    """Closed solid of revolution about the X-parallel line (y, z) from a closed (x, r) loop, r > 0."""
    verts = [(x, y+r*math.cos(2*math.pi*(k+phase)/n), z+r*math.sin(2*math.pi*(k+phase)/n)) for x, r in poly
             for k in range(n)]
    m = len(poly)
    faces = [(j*n+k, j*n+(k+1) % n, ((j+1) % m)*n+(k+1) % n, ((j+1) % m)*n+k) for j in range(m) for k in range(n)]
    return verts, faces


def hexa(corners):
    """Six faces of a hexahedron from 8 corners ordered (bottom loop, top loop)."""
    return corners, [(0, 3, 2, 1), (4, 5, 6, 7), (0, 1, 5, 4), (1, 2, 6, 5), (2, 3, 7, 6), (3, 0, 4, 7)]


def box(x0, x1, y0, y1, z0, z1):
    return hexa([(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0),
                 (x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)])


def _unit(a):
    length = sum(c*c for c in a)**.5
    return tuple(c/length for c in a)


def _cross(a, b):
    return (a[1]*b[2]-a[2]*b[1], a[2]*b[0]-a[0]*b[2], a[0]*b[1]-a[1]*b[0])


def rod(p0, p1, r, n=8):
    """An n-sided prism of radius r from p0 to p1."""
    d = _unit(tuple(q-p for p, q in zip(p0, p1)))
    u = _unit(_cross(d, (0, 0, 1) if abs(d[2]) < .9 else (1, 0, 0)))
    v = _cross(d, u)
    verts = []
    for p in (p0, p1):
        for k in range(n):
            a = 2*math.pi*(k+.5)/n
            verts.append(tuple(p[c]+r*(math.cos(a)*u[c]+math.sin(a)*v[c]) for c in range(3)))
    faces = [tuple(reversed(range(n))), tuple(range(n, 2*n))] + [(k, (k+1) % n, n+(k+1) % n, n+k) for k in range(n)]
    return verts, faces


def disc(kit, name, x, r, mat, y, z, n=24, t=.2):
    verts = [(x+dx, y+r*math.cos(2*math.pi*k/n), z+r*math.sin(2*math.pi*k/n)) for dx in (-t/2, t/2) for k in range(n)]
    faces = [tuple(range(n)), tuple(range(2*n-1, n-1, -1))] + [(k, (k+1) % n, n+(k+1) % n, n+k) for k in range(n)]
    return kit.mesh(name, verts, faces, mat)


def band(x, r, half_len, height, y, z, n):
    """A rectangular-section hoop round the X-parallel line (y, z) at X x, sitting on radius r."""
    return revolve([(x+half_len, r-.1), (x+half_len, r+height), (x-half_len, r+height), (x-half_len, r-.1)], y, z, n)


# --- one drive unit ----------------------------------------------------------

def mouth_r(w):
    return (MAIN_MOUTH_R if w >= 24 else MOUTH_R)*w


class Drive:
    """Dimensions of one drive unit, all derived from its slot."""
    def __init__(self, x, y, w):
        self.x, self.y, self.w = x, y, w
        self.rm = mouth_r(w)
        self.rt = THROAT_OF_MOUTH*self.rm
        self.can_r = CAN_OF_THROAT*self.rt
        self.tx = x+BELL_DEPTH[w]*w          # throat X (forward of the mouth)
        self.wall = max(.04*w, .35)
        self.rib_h = max(.025*w, .4)
        self.n = 36 if w >= 24 else 28 if w >= 12 else 18

    def bell_x(self, t):
        return self.tx+(self.x-self.tx)*t

    def bell_r(self, t):
        """Outer radius along the bell, t = 0 at the throat, 1 at the mouth: a flaring contour."""
        return self.rt+self.wall+(self.rm-self.rt-self.wall)*(.55*(1-(1-t)**2)+.45*t)

    def t_at(self, x):
        return min(max((self.tx-x)/(self.tx-self.x), 0.0), 1.0)

    def bay_r(self):
        """Radius of the U-shaped bay round the drive where it passes the block face."""
        if self.tx > BLOCK_AFT:
            return self.bell_r(self.t_at(BLOCK_AFT))+self.rib_h+.9
        return self.can_r+max(.03*self.w, .4)+.7


def build_drive(kit, i, d):
    x, y, w, z = d.x, d.y, d.w, ENGINE_Z
    n = d.n
    nc = max(n-8, 16)
    stations = [k/10 for k in range(11)]
    # Chamber: the can runs from the bay bulkhead back to the throat.
    kit.cylinder(f'Engine {i} drive can', ((CAN_FRONT+d.tx)/2, y, z), d.can_r, CAN_FRONT-d.tx, kit.steel,
                 axis='X', vertices=nc, bevel=.1)
    # The bell: outer contour throat->mouth, inner contour back, closed at both ends.
    outer = [(d.bell_x(t), d.bell_r(t)) for t in stations]
    inner = [(px, pr-d.wall) for px, pr in reversed(outer)]
    kit.mesh(f'Engine {i} aft-facing bell', *revolve(outer+inner, y, z, n), kit.steel)
    # Thick rolled lip at the slot X.
    rm = d.bell_r(1)
    lip = [(x+.08*w, rm-.01*w), (x+.08*w, rm+.03*w), (x+.04*w, rm+.06*w), (x+.005*w, rm+.055*w),
           (x, rm+.01*w), (x+.005*w, rm-d.wall-.02*w), (x+.04*w, rm-d.wall-.025*w)]
    kit.mesh(f'Engine {i} mouth lip', *revolve(lip, y, z, n), kit.plate)
    disc(kit, f'Engine {i} dark throat', d.tx-.05*w, d.rt+.02*w, kit.dark, y, z, n=nc)
    kit.empty(f'Engine {i} exhaust anchor', (x, y, z), 180)

    # Bell stiffeners: circumferential hoops and longitudinal ribs on the outside.
    stiff = Acc()
    hoop_t = (.45, .8) if w >= 12 else (.55,)
    for t in hoop_t:
        stiff.add(*band(d.bell_x(t), d.bell_r(t), max(.035*w, .45), d.rib_h+.25, y, z, n))
    ribs = 12 if w >= 24 else 10 if w >= 16 else 8 if w >= 12 else 0
    half = max(.012*w, .2)
    sts = stations[1:-1]
    for k in range(ribs):
        a = 2*math.pi*(k+.5)/ribs
        ca, sa = math.cos(a), math.sin(a)
        verts = []
        for t in sts:
            px, pr = d.bell_x(t), d.bell_r(t)
            for rr, s in ((pr-.1, -1), (pr-.1, 1), (pr+d.rib_h, 1), (pr+d.rib_h, -1)):
                verts.append((px, y+rr*ca-s*half*sa, z+rr*sa+s*half*ca))
        m = len(sts)
        faces = [(0, 1, 2, 3), tuple(reversed(range(4*(m-1), 4*m)))]
        faces += [(4*j+q, 4*j+(q+1) % 4, 4*(j+1)+(q+1) % 4, 4*(j+1)+q) for j in range(m-1) for q in range(4)]
        stiff.add(verts, faces)
    stiff.build(kit, f'Engine {i} bell stiffeners', kit.steel)

    # Cooling-jacket hoops round the chamber.
    jacket = Acc()
    can_len = CAN_FRONT-d.tx
    for f in ((.3, .72) if can_len > 8 else (.5,)):
        jacket.add(*band(CAN_FRONT-can_len*f, d.can_r, max(.03*w, .4), max(.03*w, .4), y, z, nc))
    jacket.build(kit, f'Engine {i} cooling jacket', kit.plate)

    # Gimbal ring at the throat, with trunnion bosses port and starboard.
    gim = Acc()
    g0, g1 = d.rt+d.wall-.1, d.can_r+.05*w
    gim.add(*revolve([(d.tx+.1*w, g0), (d.tx+.1*w, g1), (d.tx-.04*w, g1), (d.tx-.04*w, g0)], y, z, nc))
    b = max(.06*w, .45)
    gim.add(*box(d.tx-b, d.tx+b, y+g1-.2, y+g1+.07*w, z-b, z+b))
    gim.add(*box(d.tx-b, d.tx+b, y-g1-.07*w, y-g1+.2, z-b, z+b))
    gim.build(kit, f'Engine {i} gimbal ring', kit.steel)

    # Gimbal actuators: ram body from the chamber collar, piston rod to the bell's first hoop.
    if w >= 12:
        act = Acc()
        ax0 = min(d.tx+.22*w, BAY_FRONT-1.0)
        hx, hr = d.bell_x(hoop_t[0]), d.bell_r(hoop_t[0])+d.rib_h+.25
        for a in (math.radians(v) for v in ((45, 135, 225, 315) if w >= 16 else (60, 300))):
            ca, sa = math.cos(a), math.sin(a)
            r0 = d.can_r+max(.03*w, .4)+max(.045*w, .5)
            p0 = (ax0, y+r0*ca, z+r0*sa)
            p1 = (hx+.04*w, y+hr*ca, z+hr*sa)
            mid = tuple(p+(q-p)*.55 for p, q in zip(p0, p1))
            act.add(*rod(p0, mid, max(.045*w, .5), 8))
            act.add(*rod(mid, p1, max(.022*w, .3), 6))
        act.build(kit, f'Engine {i} actuators', kit.plate)

    # Propellant feed lines down the bay to a manifold ring at the gimbal (larger drives).
    if w >= 16 and d.tx > BLOCK_AFT-4:
        feed = Acc()
        fr = max(.045*w, .7)
        mx = d.tx+.13*w
        rr = d.can_r+max(.03*w, .4)+fr+.2
        feed.add(*band(mx, d.can_r+.05*w, .05*w, fr*1.6, y, z, nc))
        for deg in (70, 110):
            a = math.radians(deg)
            ca, sa = math.cos(a), math.sin(a)
            p0 = (BAY_FRONT+.5, y+rr*ca, z+rr*sa)
            p1 = (mx+.05*w, y+rr*ca, z+rr*sa)
            feed.add(*rod(p0, p1, fr, 8))
        feed.build(kit, f'Engine {i} feed lines', kit.steel)

    # Cradle saddles under the chamber, standing on the bay floor.
    cr = Acc()
    floor = z-d.bay_r()-.6
    for cx in (BAY_FRONT-1.5, (BAY_FRONT+BLOCK_AFT)/2-1.5):
        if cx > d.tx+.8:
            cr.add(*box(cx-.8, cx+.8, y-d.can_r*.55, y+d.can_r*.55, floor, z-d.can_r*.8))
    cr.build(kit, f'Engine {i} cradle', kit.dark)


# --- engine block and housings -------------------------------------------------

def bay_groups(drives):
    """Drives sharing one bay: each drive its own, the paired 6-wide drives one per side."""
    groups = {}
    for d in drives:
        groups.setdefault(('pair', d.y > 0) if d.w == 6 else (d.x, d.y), []).append(d)
    out = []
    for ds in groups.values():
        r = max(d.bay_r() for d in ds)
        out.append((ds, r, min(d.y for d in ds)-r, max(d.y for d in ds)+r))
    return out


def wall_panels(acc, edge, z0, z1, proud, depth, gaps=.6, segs=None):
    """Raised plates along a plan polyline whose outward side is the right of travel.

    Each edge segment becomes `segs` plates (about 7 units each by default) with real seam gaps."""
    for (ax, ay), (bx, by) in zip(edge, edge[1:]):
        dx, dy = bx-ax, by-ay
        length = (dx*dx+dy*dy)**.5
        ux, uy = dx/length, dy/length
        nx, ny = uy, -ux
        k = segs or max(1, round(length/7))
        step = length/k
        for s in range(k):
            t0, t1 = s*step+gaps/2, (s+1)*step-gaps/2
            p0, p1 = (ax+ux*t0, ay+uy*t0), (ax+ux*t1, ay+uy*t1)
            corners = []
            for zz in (z0, z1):
                for (px, py), off in ((p0, -depth), (p1, -depth), (p1, proud), (p0, proud)):
                    corners.append((px+nx*off, py+ny*off, zz))
            acc.add(*hexa(corners))


def build(kit):
    block = prism(kit, 'Hull engine block', ccw(BLOCK_PORT + [(x, -y) for x, y in reversed(BLOCK_PORT) if y > 0]),
                  BLOCK, kit.plate)
    housings = {}
    for side, plan in (('port', ccw(HOUSING_PORT)), ('starboard', mirror_y(ccw(HOUSING_PORT)))):
        housings[side] = prism(kit, f'Hull {side} engine housing', plan, HOUSING, kit.steel)

    drives = [Drive(x, y, w) for x, y, w, _, _ in ENGINES]
    groups = bay_groups(drives)
    # U-shaped drive bays: open to the deck above the drive axis, a half-cylinder below it.
    for ds, r, y0, y1 in groups:
        boolean(block, kit.mesh('Temporary engine bay cutter',
                                *box(BLOCK_AFT-2, BAY_FRONT, y0, y1, ENGINE_Z, BLOCK_DECK+5), kit.steel))
        for d in ds:
            boolean(block, kit.cylinder('Temporary engine bay floor cutter', ((BAY_FRONT+BLOCK_AFT-2)/2, d.y, ENGINE_Z),
                                        r, BAY_FRONT-BLOCK_AFT+2, kit.steel, axis='X', vertices=32, bevel=0))
    # Aft vents in the outer housings: a dark recess behind louvres.
    for side, s in (('port', 1), ('starboard', -1)):
        y0, y1 = sorted((s*67.5, s*74.5))
        boolean(housings[side], kit.mesh('Temporary housing vent cutter', *box(-130, -125.6, y0, y1, -7, 0), kit.dark))
    kit.hull.append(block)
    kit.hull += list(housings.values())

    for i, d in enumerate(drives, 1):
        build_drive(kit, i, d)
        if d.w >= 12:
            # Armoured octagonal shroud framing the drive where it leaves the block face.
            r = d.bay_r()/math.cos(math.pi/8)
            t = max(.07*d.w, 1.2)
            kit.mesh(f'Engine {i} shroud', *revolve([(BLOCK_AFT+.6, r), (BLOCK_AFT+.6, r+t), (BLOCK_AFT-2.0, r+t),
                                                     (BLOCK_AFT-2.8, r+t*.4), (BLOCK_AFT-2.8, r)],
                                                    d.y, ENGINE_Z, 8, phase=.5), kit.plate)

    fins, frames, ledges = Acc(), Acc(), Acc()
    for side in (1, -1):
        spans = sorted((y0, y1) for ds, r, y0, y1 in groups if (ds[0].y > 0) == (side > 0))
        # Radiator fins along the dividers between bays (the sprite's fore-and-aft ribbing),
        # and a frame down the block face at each divider.
        for (_, y0), (y1, _) in zip(spans, spans[1:]):
            width = y1-y0
            k = max(1, int((width-.4)/.9))
            for j in range(k):
                fy = y0+(j+.5)*width/k
                hit = kit.surface(-104, fy, top=120)
                top = hit[0].z if hit else BLOCK_DECK
                fins.add(*box(-110.6, BAY_FRONT-.6, fy-.18, fy+.18, top-.3, top+1.0))
            yc, fw = (y0+y1)/2, min(1.0, width/2-.3)
            frames.add(*box(BLOCK_AFT-1.0, BLOCK_AFT+.5, yc-fw, yc+fw, -10.5, 6.0))
        # Service ledges along the block face between the bays: a gallery under the
        # shoulder and a sill above the keel chamfer.
        lo, hi = (9.0, 79.0) if side > 0 else (-79.0, -9.0)
        cur, segs = lo, []
        for a, b in spans:
            if a > cur+.8:
                segs.append((cur, a))
            cur = max(cur, b)
        if hi > cur+.8:
            segs.append((cur, hi))
        for a, b in segs:
            ledges.add(*box(BLOCK_AFT-1.4, BLOCK_AFT+.5, a, b, 4.2, 5.0))
            ledges.add(*box(BLOCK_AFT-.9, BLOCK_AFT+.5, a, b, -10.6, -9.7))
    fins.build(kit, 'Hull engine block radiator fins', kit.steel)
    frames.build(kit, 'Hull engine block face frames', kit.plate)
    ledges.build(kit, 'Hull engine block service ledges', kit.plate)

    # Raised armour plates with seam gaps on the block's outboard walls, and a strake between them.
    plates, strake = Acc(), Acc()
    port_edge = [(-96, 84), (-104, 82), (-112, 81)]     # travelling aft: outboard (+Y) is the right
    for edge in (port_edge, [(x, -y) for x, y in reversed(port_edge)]):
        wall_panels(plates, edge, -9.5, -.4, .55, .4, segs=1)
        wall_panels(plates, edge, .6, 5.0, .45, .4, segs=1)
        wall_panels(strake, edge, -.4, .6, 1.1, .4, gaps=0, segs=1)
    plates.build(kit, 'Hull engine block armour plates', kit.plate)
    strake.build(kit, 'Hull engine block strake', kit.plate)

    for side, s in (('port', 1), ('starboard', -1)):
        acc, st = Acc(), Acc()
        edge = [(-112, 80), (-124, 79), (-127, 76)]
        if s < 0:
            edge = [(x, -y) for x, y in reversed(edge)]
        wall_panels(acc, edge, -8.0, -1.9, .5, .4)
        wall_panels(acc, edge, -1.1, 2.2, .4, .4)
        wall_panels(st, edge, -1.9, -1.1, 1.0, .4, gaps=0, segs=1)
        # Plates on the housing deck with seams between them.
        for x0, x1 in ((-113.0, -118.6), (-119.2, -124.2)):
            for y0, y1 in ((66.0, 71.6), (72.2, 77.4)):
                yy0, yy1 = sorted((s*y0, s*y1))
                hit = kit.surface((x0+x1)/2, (yy0+yy1)/2, top=120)
                top = hit[0].z if hit else 4.0
                acc.add(*box(x1, x0, yy0, yy1, top-.4, top+.45))
        # Louvres across the aft vent.
        yy0, yy1 = sorted((s*67.3, s*74.7))
        for k in range(4):
            vz = -6.4+k*1.65
            acc.add(*hexa([(-126.4, yy0, vz), (-125.6, yy0, vz), (-125.6, yy1, vz), (-126.4, yy1, vz),
                           (-127.4, yy0, vz+.9), (-126.6, yy0, vz+.9), (-126.6, yy1, vz+.9), (-127.4, yy1, vz+.9)]))
        # The cowl framing the paired small drives where they leave the block.
        ds, r, py0, py1 = next(g for g in groups if g[0][0].w == 6 and (g[0][0].y > 0) == (s > 0))
        cz0, cz1 = ENGINE_Z-r-.6, ENGINE_Z+r+.6
        for b in (box(BLOCK_AFT-2.2, BLOCK_AFT+.5, py0-.9, py0, cz0, cz1),
                  box(BLOCK_AFT-2.2, BLOCK_AFT+.5, py1, py1+.9, cz0, cz1),
                  box(BLOCK_AFT-2.2, BLOCK_AFT+.5, py0-.9, py1+.9, cz0-.9, cz0),
                  box(BLOCK_AFT-2.2, BLOCK_AFT+.5, py0-.9, py1+.9, cz1, cz1+.9)):
            acc.add(*b)
        acc.build(kit, f'Hull {side} engine housing plates', kit.steel)
        st.build(kit, f'Hull {side} engine housing strake', kit.plate)


def records():
    return [{'index': i, 'objects': f'Engine {i} ...', 'anchor': f'Engine {i} exhaust anchor',
             'location': [x, y, ENGINE_Z], 'width': w, 'length': length, 'contrailSize': c, 'style': 'LOW_TECH',
             'angle': 180, 'exhaust_direction': [-1, 0, 0], 'mouth_radius': round(mouth_r(w), 2),
             'bell': f'Engine {i} aft-facing bell', 'lip': f'Engine {i} mouth lip', 'throat': f'Engine {i} dark throat'}
            for i, (x, y, w, c, length) in enumerate(ENGINES, 1)]
