"""The Onslaught's hull masses: prow, core, the two gun arms, aft body, tail and aft legs.

Plans are read off onslaught_hegemony.png (identical silhouette to
onslaught_base.png) in model units: X = 244 - row, Y = 144 - col about the
.ship centre (144, 140 from the bottom). Heights are proposed art: the
sprite gives none. The sprite reads as five masses, and each is one prism
with one keel chamfer, one wall and one shoulder chamfer:

  prow       the long central wedge, X 96..225, carrying WS 007/008/009/019
             in a dark trench between two orange armour shoulders;
  core       the arrowhead behind it, X -70..104: its orange glacis bands run
             from the prow's shoulders aft and outboard to the broadside
             sponsons; the bone spine and the two domes stand on it (os_bridge);
  arms       the two gun arms, X -16..214, each a long armoured block with the
             thermal pulse cannon hardpoints at the tip, a row of turrets along
             it and the large Devastator barbette at its root; the notch
             between arm and prow is open to space forward of X 104;
  aft body   the stern quarters aft of the arms: a thick outer rim round two
             recessed machinery wells open to space, one outboard of each leg
             (diagonal pipe bundles) and one inboard (fore-and-aft pipe banks
             beside the tail); os_machinery fills them;
  tail       the central main-engine housing, X -50..-113;
  legs       the two orange-striped aft legs either side of the tail notch.

Every mass is its own object so a plane cut on one never clips another.
"""
import math
from mathutils import Vector
from os_kit import boolean, ccw, curved, mirror_y, plane_cut, prism, section, facet_normal, inset

# --- plans (port halves, listed bow to stern along the outer edge) ---------

PROW_HALF = [(225, 7.5), (220, 10.5), (216, 14.5), (212, 16.5), (200, 18.5), (196, 22.5), (192, 25.5),
             (172, 29.5), (148, 34.5), (120, 39.5), (104, 41.5), (96, 41.5)]
# The core ends in a flat aft face at X -18: behind it the deck drops into the
# machinery wells (os_machinery) and only the tail carries on aft.
CORE_HALF = [(104, 41.5), (60, 58), (20, 74), (0, 82), (-18, 84), (-18, 22)]
# The sprite's small outer steps (X 146-150 and the notch at X 68) are left to
# the armour blocks of pass 2: the curved bilge insets the plan by up to 10
# units, more than those steps are deep.
ARM_OUTER = [(214, 79.5), (204, 85), (176, 95), (146, 102), (104, 127.5), (80, 128.5), (44, 128.5),
             (20, 134.5), (8, 125), (-16, 118.5)]
ARM_INNER = [(-16, 90), (0, 88), (40, 70), (104, 56.5), (140, 51.5), (176, 48.5), (196, 54.5), (208, 62),
             (214, 64.5)]          # stern to bow
AFT_OUTER = [(-4, 124), (-16, 118.5), (-24, 114.5), (-40, 109.5), (-60, 103.5), (-70, 100.5), (-72, 84),
             (-80, 74.5), (-88, 65.5), (-96, 68.5), (-101, 69), (-102, 42)]
TAIL_HALF = [(-10, 22), (-50, 20.5), (-104, 20.5), (-108, 18.5), (-112, 17.5), (-113.5, 14), (-113.5, 0)]
LEG_PORT = [(-36, 60), (-56, 63), (-90, 66), (-100, 69.5), (-112, 63.5), (-120, 60.5), (-124, 56.5), (-126, 52.5),
            (-121, 48.5), (-112, 47), (-104, 46), (-60, 47), (-36, 52)]
# The machinery well (port; mirrored), cut into the aft body's deck down to WELL_FLOOR.
WELL_PORT = [(-18, 23), (-18, 104), (-40, 101), (-64, 94), (-71, 81), (-82, 66), (-96, 62), (-100, 44),
             (-100, 23)]
WELL_FLOOR = -10.0
# The low deck between the glacis and the arm (the sprite's dark valley, with
# amber bay lamps at its forward end where it opens into the notch).
WAIST_PORT = [(106, 40), (106, 58), (40, 72), (2, 89), (2, 79), (60, 55)]

# --- sections ---------------------------------------------------------------
# The owner: "thick, with large curving hull depth". Every mass is deep, and
# its lower hull turns under on a rounded bilge rather than one keel chamfer;
# the core is the deepest, a keel 62 units down under a 30-unit bilge. In
# profile the keel line curves up toward the stem and the stern as well
# (keel_curve: a run of facets on a circular arc), so the side view curves too.
PROW = curved(-38.0, 16.0, 16.0, 32.0, 6.0, 10.0)
CORE = curved(-62.0, 22.0, 30.0, 50.0, 8.0, 14.0)
ARM = curved(-46.0, 14.0, 12.0, 36.0, 5.0, 12.0)
AFT = curved(-50.0, 10.0, 18.0, 36.0, 4.0, 8.0)
TAIL = curved(-54.0, 18.0, 10.0, 40.0, 4.0, 8.0)
LEG = curved(-30.0, 12.0, 6.0, 20.0, 2.5, 5.0)
WAIST = section(-20.0, 6.0, 3.0, 1.5)

DECK = {'prow': 16.0, 'core': 22.0, 'arm': 14.0, 'aft': 10.0, 'tail': 18.0, 'leg': 12.0, 'waist': 6.0}


def full(half):
    """A symmetric CCW plan from a port half listed bow to stern."""
    return ccw(list(half) + [(x, -y) for x, y in reversed(half) if abs(y) > 1e-6])


def port_plan(outer, inner):
    return ccw(list(outer) + list(inner))


def keel_cut(target, name, point, back, angle, pad=0.0):
    """Remove everything below a plane rising toward the plan direction `back` at `angle` deg.
    `pad` moves the plane into the kept side, so a part replaying the cut stops just short of it."""
    a = math.radians(angle)
    dx, dy = back
    length = (dx*dx+dy*dy)**.5
    n = (dx/length*math.sin(a), dy/length*math.sin(a), -math.cos(a))
    plane_cut(target, name, tuple(p-c*pad for p, c in zip(point, n)), n)


def pad_cut(target, name, point, normal, pad=0.0):
    """plane_cut, with the plane moved `pad` into the kept side."""
    length = sum(c*c for c in normal)**.5
    plane_cut(target, name, tuple(p-c/length*pad for p, c in zip(point, normal)), normal)


def keel_curve(target, name, x0, x1, keel, rise, back, n=4, pad=0.0):
    """Curve the keel line up by `rise` between X x0 (tangent, at depth `keel`) and x1,
    along a circular arc cut as n facets; `back` is (+-1, 0), the way the curve runs."""
    span = abs(x1-x0)
    # Circle tangent to the keel at x0, passing `rise` above it at x1.
    radius = (span*span+rise*rise)/(2*rise)
    end = math.asin(span/radius)
    pts = [(x0+back[0]*radius*math.sin(end*k/n), keel+radius*(1-math.cos(end*k/n))) for k in range(n+1)]
    for (ax, az), (bx, bz) in zip(pts, pts[1:]):
        angle = math.degrees(math.atan2(bz-az, abs(bx-ax)))
        if angle > .5:
            keel_cut(target, name, (ax, 0, az), back, angle, pad)


# --- the cuts each mass takes after its prism, replayable on parts that follow its skin ---

def prow_cuts(ob, pad=0.0):
    pad_cut(ob, 'bow upper', (176, 0, 16.0), facet_normal((1, 0), 14), pad)
    keel_curve(ob, 'bow keel', 96, 225, -38.0, 30.0, (1, 0), pad=pad)
    for s in (1, -1):
        pad_cut(ob, 'bow chine', (214, s*13.5, 4.0), (0, s*math.sin(math.radians(38)), math.cos(math.radians(38))), pad)


def arm_cuts(ob, pad=0.0):
    pad_cut(ob, 'arm nose', (184, 0, 14.0), facet_normal((1, 0), 15), pad)
    keel_curve(ob, 'arm keel', 80, 214, -46.0, 38.0, (1, 0), pad=pad)
    keel_curve(ob, 'arm heel', -2, -16, -46.0, 8.0, (-1, 0), n=2, pad=pad)


def aft_cuts(ob, pad=0.0):
    keel_curve(ob, 'aft keel', -20, -102, -50.0, 34.0, (-1, 0), pad=pad)


def tail_cuts(ob, pad=0.0):
    pad_cut(ob, 'tail upper', (-92, 0, 18.0), facet_normal((-1, 0), 18), pad)
    keel_curve(ob, 'tail keel', -40, -114, -54.0, 34.0, (-1, 0), pad=pad)


def leg_cuts(ob, pad=0.0):
    pad_cut(ob, 'leg aft', (-104, 0, 12.0), facet_normal((-1, 0), 20), pad)
    keel_curve(ob, 'leg keel', -40, -127, -30.0, 22.0, (-1, 0), pad=pad)


def no_cuts(ob, pad=0.0):
    pass


# --- pass 2: the plated skin -------------------------------------------------
# The flanks are plated in courses that follow the bilge: rows of slabs laid
# like brickwork (each course offset half a plate), with real seam gaps. Where
# a course lies far enough inboard of the plan edge for a raised slab to stay
# inside the top silhouette (the lower bilge), the plates stand proud of the
# hull as their own object; on the wall and the tumblehome, where anything
# proud would widen the sprite outline, the seams are cut into the hull
# instead, so the same rhythm carries up the side without moving the plan.

class Skin:
    """A mass's flank as a surface: s = arc length round its plan at the wall
    (CCW), q = arc length up its section from the keel. point() returns the
    hull surface there lifted `lift` along the section's outward normal."""

    def __init__(self, plan, profile):
        self.plan, self.profile = plan, profile
        n = len(plan)
        self.cum = [0.0]
        for i in range(n):
            (ax, ay), (bx, by) = plan[i], plan[(i+1) % n]
            self.cum.append(self.cum[-1]+math.hypot(bx-ax, by-ay))
        self.perim = self.cum[-1]
        self.q = [0.0]
        for (d0, z0), (d1, z1) in zip(profile, profile[1:]):
            self.q.append(self.q[-1]+math.hypot(d1-d0, z1-z0))
        self.height = self.q[-1]
        self._polys = {}

    def poly(self, d):
        key = round(d, 4)
        if key not in self._polys:
            self._polys[key] = inset(self.plan, d)
        return self._polys[key]

    def _seg_normal(self, k):
        (d0, z0), (d1, z1) = self.profile[k], self.profile[k+1]
        length = math.hypot(d1-d0, z1-z0) or 1.0
        return (-(z1-z0)/length, (d1-d0)/length)

    def row(self, q):
        """(inset, z, normal_inset, normal_z) at section arc length q."""
        q = min(max(q, 0.0), self.height)
        k = 0
        while k < len(self.q)-2 and self.q[k+1] < q:
            k += 1
        span = (self.q[k+1]-self.q[k]) or 1.0
        f = (q-self.q[k])/span
        (d0, z0), (d1, z1) = self.profile[k], self.profile[k+1]
        nd, nz = self._seg_normal(k)
        # Average with the neighbouring facet at a section vertex, so a slab
        # spanning two facets keeps an even thickness across the knuckle.
        if f < 1e-6 and k > 0:
            pd, pz = self._seg_normal(k-1)
            nd, nz = (nd+pd)/2, (nz+pz)/2
        elif f > 1-1e-6 and k+1 < len(self.profile)-1:
            pd, pz = self._seg_normal(k+1)
            nd, nz = (nd+pd)/2, (nz+pz)/2
        return d0+(d1-d0)*f, z0+(z1-z0)*f, nd, nz

    def at_s(self, s):
        s %= self.perim
        i = 0
        while i < len(self.plan)-1 and self.cum[i+1] < s:
            i += 1
        span = (self.cum[i+1]-self.cum[i]) or 1.0
        return i, (s-self.cum[i])/span

    def point(self, s, q, lift):
        d, z, nd, nz = self.row(q)
        p = self.poly(d+nd*lift)
        i, f = self.at_s(s)
        (ax, ay), (bx, by) = p[i], p[(i+1) % len(p)]
        return (ax+(bx-ax)*f, ay+(by-ay)*f, z+nz*lift)

    def clear(self, q, lift):
        """How far inboard of the plan edge a slab lifted `lift` at q stands."""
        d, _, nd, _ = self.row(q)
        return d+nd*lift

    def ranges(self, keep):
        """Contiguous s ranges over plan edges for which keep(a, b, outward normal) holds."""
        out = []
        n = len(self.plan)
        for i in range(n):
            a, b = self.plan[i], self.plan[(i+1) % n]
            length = math.hypot(b[0]-a[0], b[1]-a[1])
            if length < 1e-6:
                continue
            normal = ((b[1]-a[1])/length, -(b[0]-a[0])/length)
            if keep(a, b, normal):
                if out and abs(out[-1][1]-self.cum[i]) < 1e-6:
                    out[-1][1] = self.cum[i+1]
                else:
                    out.append([self.cum[i], self.cum[i+1]])
        return [tuple(r) for r in out]

    def samples(self, a, b, marks):
        """a, b and every mark strictly between them (in arc terms)."""
        return [a]+[m for m in marks if a+1e-4 < m < b-1e-4]+[b]

    def patch(self, s0, s1, q0, q1, lift_lo, lift_hi, sink):
        """Closed slab over [s0,s1] x [q0,q1]: outer face lifted lift_lo at q0
        rising linearly to lift_hi at q1 (a shingle), inner face sunk `sink`."""
        ss = self.samples(s0, s1, [c+k*self.perim for k in (0, 1) for c in self.cum])
        qs = self.samples(q0, q1, self.q)
        nu, nr = len(ss), len(qs)
        verts = []
        for lift in ('out', 'in'):
            for q in qs:
                t = (q-q0)/((q1-q0) or 1.0)
                h = lift_lo+(lift_hi-lift_lo)*t if lift == 'out' else -sink
                verts += [self.point(s, q, h) for s in ss]
        o = lambda i, j: j*nu+i
        m = lambda i, j: nu*nr+j*nu+i
        faces = []
        for j in range(nr-1):
            for i in range(nu-1):
                faces.append((o(i, j), o(i+1, j), o(i+1, j+1), o(i, j+1)))
                faces.append((m(i, j), m(i, j+1), m(i+1, j+1), m(i+1, j)))
        for i in range(nu-1):
            faces.append((o(i, 0), m(i, 0), m(i+1, 0), o(i+1, 0)))
            faces.append((o(i, nr-1), o(i+1, nr-1), m(i+1, nr-1), m(i, nr-1)))
        for j in range(nr-1):
            faces.append((o(0, j), o(0, j+1), m(0, j+1), m(0, j)))
            faces.append((o(nu-1, j), m(nu-1, j), m(nu-1, j+1), o(nu-1, j+1)))
        return verts, faces


class Shells:
    """Many closed slabs accumulated into one mesh, optionally mirrored to starboard."""

    def __init__(self):
        self.verts, self.faces = [], []

    def add(self, verts, faces, mirror=False):
        for vs in ([verts, [(x, -y, z) for x, y, z in verts]] if mirror else [verts]):
            o = len(self.verts)
            self.verts += vs
            self.faces += [tuple(o+i for i in f) for f in faces]

    def build(self, kit, name, mat, bevel=0):
        return kit.mesh(name, self.verts, self.faces, mat, bevel) if self.faces else None


def _hash(*v):
    h = 0
    for x in v:
        h = (h*1103515245+int(round(x*7))+12345) & 0x7fffffff
    return (h % 1000)/1000.0


# Plate layout per mass: course height (q), plate length (s), and the plan
# edges that are open to view (both sides of a symmetric plan are listed by
# their port half and mirrored).
SEAM = 1.3            # seam gap between plates
PLATE_LIFT = 1.5      # how proud a bilge plate stands
SEAM_DEPTH = 1.4      # how deep a cut seam goes into the wall
MIN_CLEAR = 0.35      # a raised slab keeps at least this inboard of the plan edge


def plate_courses(kit, name, skin, edges, course_h, plate_l, cuts, mirror, host, mirror_seams=None, q_lo=1.5,
                  q_top=3.0):
    """Lay the courses on one mass: raised slabs where they clear the plan
    edge, seams cut into `host` where they do not. Returns the raised object
    and the q at which raised plating stops (where a strake can run)."""
    q_hi = skin.height-q_top
    # Courses break at the section's knuckles (bilge to wall, wall to
    # tumblehome), so the wall is one tall belt of plates and the bilge is
    # laid in courses that follow its curve.
    half = len(skin.profile)//2
    knuckles = [q for q in (skin.q[half-1], skin.q[half]) if q_lo+3 < q < q_hi-3]
    bounds = [q_lo]
    for a, b in zip([q_lo]+knuckles, knuckles+[q_hi]):
        n = max(1, round((b-a)/course_h))
        bounds += [a+(b-a)*k/n for k in range(1, n+1)]
    ms = mirror if mirror_seams is None else mirror_seams
    raised, horiz, vert = Shells(), Shells(), Shells()
    top_raised = None
    for c, (qa, qb) in enumerate(zip(bounds, bounds[1:])):
        lift = PLATE_LIFT+.6
        ok = all(skin.clear(qa+(qb-qa)*k/6, lift) >= MIN_CLEAR for k in range(7))
        if ok:
            top_raised = qb
        for s0, s1 in edges:
            # Plates of uneven length, each course started half a plate on from
            # the one below, so the joints never line up between courses.
            # The wall and tumblehome are hung with longer belt plates than the bilge.
            pl = plate_l if ok else 1.5*plate_l
            joints, x = [s0], s0+pl*(.25+.5*(c % 2)+.2*_hash(s0, c))
            while x < s1-.45*pl:
                joints.append(x)
                x += pl*(.75+.5*_hash(x, c, qa))
            joints.append(s1)
            cuts_s = joints[1:-1]
            if ok:
                for a, b in zip(joints, joints[1:]):
                    if b-a < 3:
                        continue
                    j = _hash(a, qa, c)
                    lo = PLATE_LIFT+.5*j
                    raised.add(*skin.patch(a+SEAM/2, b-SEAM/2, qa+SEAM/2, qb-SEAM/2, lo+.35, lo, .6), mirror)
            else:
                for x in cuts_s:
                    vert.add(*skin.patch(x-.4*SEAM, x+.4*SEAM, qa-.45*SEAM, qb+.45*SEAM, 3.0, 3.0, SEAM_DEPTH),
                             ms)
                if c > 0 or qa > q_lo:
                    horiz.add(*skin.patch(s0, s1, qa-SEAM/2, qa+SEAM/2, 3.0, 3.0, SEAM_DEPTH), ms)
    # The top seam of the plating, under the deck edge.
    for s0, s1 in edges:
        horiz.add(*skin.patch(s0, s1, q_hi-SEAM/2, q_hi+SEAM/2, 3.0, 3.0, SEAM_DEPTH), ms)
    for shells in (horiz, vert):
        cutter = shells.build(kit, 'Temporary hull seam cutter', kit.dark)
        if cutter:
            # A seam stops short of the mass's own cut facets (the falling bow
            # deck, the rising keel) rather than notching across them.
            cuts(cutter, SEAM_DEPTH+.6)
            boolean(host, cutter)
    ob = raised.build(kit, name, PLATING, bevel=.25) if name else None
    if ob:
        cuts(ob, .3)
    return ob, top_raised


# Filled by build(): {mass: (Skin, port edges, cuts, q where raised plating stops)} so os_armour can lay strakes on the same skin.
SKINS = {}
PLATING = None


def _plating_material():
    from os_kit import material
    # A shade off the structure colour, so the plate courses read as separate
    # slabs against the hull they are bolted to.
    return material('Hegemony hull plating', (.17, .108, .088), .35, .58)


def _port(skin, x_lo=-1e9, x_hi=1e9, outward=None):
    """Edges of a symmetric plan on the port side (not touching the centreline) within an X window."""
    def keep(a, b, n):
        if min(a[1], b[1]) < .5:
            return False
        mx = (a[0]+b[0])/2
        return x_lo <= mx <= x_hi and (outward is None or outward(n))
    return skin.ranges(keep)


def build(kit):
    global PLATING
    PLATING = _plating_material()
    prow = prism(kit, 'Hull prow', full(PROW_HALF), PROW, kit.plate)
    # Bow: the deck falls toward the stem and the keel rises to meet it; the
    # sides close in with a chine either side of the stem.
    prow_cuts(prow)

    core = prism(kit, 'Hull core', full(CORE_HALF), CORE, kit.plate)

    arms = {}
    for side, s in (('port', 1), ('starboard', -1)):
        plan = port_plan(ARM_OUTER, ARM_INNER)
        if s < 0:
            plan = mirror_y(plan)
        arm = prism(kit, f'Hull {side} arm', plan, ARM, kit.plate)
        # The arm's nose falls toward the hardpoint cheeks, and its keel rises.
        arm_cuts(arm)
        arms[side] = arm

    aft = prism(kit, 'Hull aft body', full(AFT_OUTER + [(-102, 21)]), AFT, kit.plate)
    aft_cuts(aft)
    for s in (1, -1):
        plan = ccw(WELL_PORT) if s > 0 else mirror_y(ccw(WELL_PORT))
        cutter = prism(kit, 'Temporary machinery well cutter', plan, [(0.0, WELL_FLOOR), (0.0, 30.0)], kit.dark, bevel=0)
        boolean(aft, cutter)
    tail = prism(kit, 'Hull tail', full(TAIL_HALF), TAIL, kit.plate)
    tail_cuts(tail)

    legs, waists = {}, {}
    for side, s in (('port', 1), ('starboard', -1)):
        plan = ccw(LEG_PORT) if s > 0 else mirror_y(ccw(LEG_PORT))
        leg = prism(kit, f'Hull {side} aft leg', plan, LEG, kit.plate)
        leg_cuts(leg)
        legs[side] = leg
        waists[side] = prism(kit, f'Hull {side} waist', ccw(WAIST_PORT) if s > 0 else mirror_y(ccw(WAIST_PORT)),
                             WAIST, kit.plate)

    # --- pass 2: plate courses and cut seams on every open flank -------------
    plating = lambda name, skin, edges, h, l, cuts, host, **kw: plate_courses(
        kit, name, skin, edges, h, l, cuts, True, host, **kw)
    sk = Skin(full(PROW_HALF), PROW)
    e = _port(sk, 98, 222)
    _, top = plating('Hull prow plating', sk, e, 11.0, 30.0, prow_cuts, prow)
    SKINS['prow'] = (sk, e, prow_cuts, top)
    sk = Skin(full(CORE_HALF), CORE)
    e = _port(sk, -17, 104)
    _, top = plating('Hull core plating', sk, e, 14.0, 36.0, no_cuts, core)
    SKINS['core'] = (sk, e, no_cuts, top)
    # The arms are separate objects; the port arm's plating is mirrored, and
    # each host takes its own seams.
    sk = Skin(ccw(port_plan(ARM_OUTER, ARM_INNER)), ARM)
    # Outer flank and nose all along; the inner flank only where it faces the open notch.
    e = sk.ranges(lambda a, b, n: n[1] > -.2 or (min(a[0], b[0]) >= 100))
    for side in ('port', 'starboard'):
        host = arms[side]
        if side == 'port':
            _, top = plate_courses(kit, 'Hull arm plating', sk, e, 11.0, 32.0, arm_cuts, True, host, False)
        else:
            mirrored = Skin(mirror_y(ccw(port_plan(ARM_OUTER, ARM_INNER))), ARM)
            me = mirrored.ranges(lambda a, b, n: n[1] < .2 or (min(a[0], b[0]) >= 100))
            plate_courses(kit, None, mirrored, me, 11.0, 32.0, arm_cuts, False, host)
    SKINS['arm'] = (sk, e, arm_cuts, top)
    sk = Skin(full(AFT_OUTER + [(-102, 21)]), AFT)
    e = _port(sk, -110, -10, lambda n: n[0] < .9)
    _, top = plating('Hull aft body plating', sk, e, 12.0, 28.0, aft_cuts, aft)
    SKINS['aft'] = (sk, e, aft_cuts, top)
    sk = Skin(full(TAIL_HALF), TAIL)
    e = _port(sk, -120, -12)
    _, top = plating('Hull tail plating', sk, e, 12.0, 28.0, tail_cuts, tail)
    SKINS['tail'] = (sk, e, tail_cuts, top)
    sk = Skin(ccw(LEG_PORT), LEG)
    e = sk.ranges(lambda a, b, n: (a[0]+b[0])/2 < -40)
    for side in ('port', 'starboard'):
        if side == 'port':
            _, top = plate_courses(kit, 'Hull aft leg plating', sk, e, 10.0, 24.0, leg_cuts, True, legs[side], False)
        else:
            mirrored = Skin(mirror_y(ccw(LEG_PORT)), LEG)
            plate_courses(kit, None, mirrored, mirrored.ranges(lambda a, b, n: (a[0]+b[0])/2 < -40), 10.0, 24.0,
                          leg_cuts, False, legs[side])
    SKINS['leg'] = (sk, e, leg_cuts, top)

    kit.hull += [prow, core, *arms.values(), aft, tail, *legs.values(), *waists.values()]
    return {'prow': prow, 'core': core, 'arms': arms, 'aft': aft, 'tail': tail, 'legs': legs}
