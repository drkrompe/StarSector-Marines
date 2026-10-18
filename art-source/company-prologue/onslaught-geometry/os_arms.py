"""The two gun arms: base mass, gun gallery and the armour that belongs to them.

Split out of os_hull/os_armour so the arms can be shaped on their own. Every
object built here is collected in ARM_PARTS[side] and then posed by tilt():
the arms need not share the core's plane (owner, round 2), and tilting them
lets more of their guns bear (owner).

Round 3 (owner: "still needs a lot of silhouette work"; "sections of hull that
don't line up"; "look closely at the pixel art to infer the armoring on the
curved arms"). The arm was a round-bellied heightfield, which read from ahead
and astern as a balloon on a pontoon. It is now an armoured forearm lofted
along X through an angular section (hard chines, sloped plate faces, a keel
line), and its armour is read off onslaught_hegemony.png at 8x as courses of
plate that follow that section rather than bricks stuck on a pod:

  section     (arm frame, port, before the pose) inboard top chine -> a low
              crown under the gun gallery -> outboard shoulder chine -> a
              sloped upper plate face falling outboard to the max-beam chine
              -> a lower face tucking in to the lower chine -> a V bottom to a
              keel line set outboard of centre (under the heavy side) -> the
              inboard bilge chine -> the inboard face, which runs down and in
              under the waist so the join is buried, not an overhang. The keel
              sweeps up into a raked forefoot under the TPC nose (the nose
              leads the side and front silhouette as a wedge) and up into the
              heel, which runs into the aft body.
  gallery     the raised gun spine along the turret line, lofted as a
              trapezoid with chamfered shoulders (no round crown), its inboard
              side a long glacis down toward the valley; it climbs aft and
              falls at the nose.
  upper course   (orange, belts 1-4) the sprite's orange plate along the
              gallery's outboard edge: a plate course lying on the shoulder and
              the sloped upper face, in segments with seams at X 151, 105 and
              71. Forward (X 152-192) it is the broad orange nose plate that
              covers the whole outboard flank; amidships a narrow strip beside
              the grey blocks; aft of the Devastator the wide orange chevron.
              Belt 5 (grey) carries the course on aft under the shoulder.
  lower course   (dark grey, steps 1-6) the sprite's stepped dark grey blocks on
              the outboard rim from X 150 aft: thick plates on the max-beam
              chine and lower face that lap over the orange course's lower
              edge, each thickest at its aft end and chamfered down forward
              (the sprite lights their forward faces), so the rim steps up at
              every seam like overlapping scales.
  coaming     a grey strake on the inboard top chine facing the notch.
  housings    lozenge plinths under WS 001/002/005 (the sprite draws each in
              its own outline), and the Devastator's drum with a tapered aft
              fairing (the sprite's ring and converging struts, X 44-92).
  nose        grey cheeks either side of the TPC barrel (os_turrets builds the
              casemate in the nose by ray casts).
  terrace     (round 5) the gallery's outboard side steps down by a short riser to a
              terrace shelf ending in a lip a plate's step above the orange, so from
              above the arm reads gun deck | terrace | orange, not a split.
  rim blocks  (round 5) grey blocks at each turret housing crossing the terrace and the
              orange course's inner half, as the sprite's grey rims break the orange.
  after end   (round 5) the keel runs level to a steep raked after face that breaks to a
              ramp into the aft body, framed by a grey collar; the upper tier runs on aft.
  forward plates (round 8) from ahead the forward arm read as a crumple of small facets; the
              sprite's is three plates. Forward of the Devastator (SHOULDER_X) the gallery's
              outboard side is one shoulder plate raked off the shoulder chine C, the orange
              courses start on that chine, the terrace and rim blocks 1-3 are gone (rim block 4
              stays at the Devastator), and the arm's lofts are shaded smooth within
              PLATE_ANGLE so each face reads as one plate with hard chines.
  shoulder    the orange broadside fin, now a cap posed with the arm that
              follows the arm's and gallery's top a plate's thickness proud,
              with a crest tier, so it reads as a shoulder plate on the arm
              root rather than a slab standing above it.
"""
import math
from os_kit import plane_cut, prism, facet_normal, loft_x, boolean
from os_hull import ARM_INNER, ARM_OUTER, GALLERY_TOP
from os_armour import _side

ARM_PARTS = {'port': [], 'starboard': []}

# Kept for importers: (keel, deck, ...) in the arm's own frame.
ARM = (-31.0, 8.0, 20.0, 24.0, 16.0, 18.0, 3.0, 10.0)
DECK_Z = 8.0          # the arm's deck (inboard and shoulder chines sit just under it)
KEEL_Z = -31.0        # the keel line amidships (arm frame)
# The keel's knuckles (arm frame): the forefoot rakes straight up from X_KNUCKLE_FORE to the
# chin under the casemate at X_CHIN; the heel rakes straight up aft of X_KNUCKLE_AFT.
X_KNUCKLE_FORE, X_KNUCKLE_AFT = 166.0, 9.0
X_CHIN, CHIN_Z = 200.0, -6.5
HEEL_Z = -14.0

# The gun nose (round 4, owner: forward aggression). The Thermal Pulse Cannon is the arm's
# nose: os_turrets builds its casemate in WORLD space on the line of fire at TPC_AXIS_Z, from
# TPC_AFT_X (buried in the arm) to its face at TPC_FACE_X (the sprite's ladder runs X 196-216),
# with the barrel and prongs standing out ahead of it. The arm's hull ends in a raked nose face
# (NOSE_FACE, leaning aft NOSE_RAKE deg from vertical) a few units behind the casemate's face,
# its forefoot raking down and aft from the casemate's chin and its gallery and deck raking up
# and aft from the casemate's roof, so the cannon leads the arm in every view. The axis is set
# under the prow's keel so in side view the two gun noses jut out beneath and beside the ram.
TPC_AXIS_Z = -26.0
TPC_FACE_X, TPC_AFT_X = 216.5, 182.0
TPC_HALF_W = 6.0
NOSE_FACE = (200.5, -7.0)    # arm frame (x, z): the foot of the hull's nose face at the chin
NOSE_RAKE = 30.0

# The pose (owner notes 5, 8, 9). The arm guns' slot angles all point outboard
# and forward (WS 001-006 at 65-75 deg, the Devastators at 80, WS 010/011 to the
# beam), with arcs reaching from dead ahead round to the quarter. So each arm is
# rolled outboard-down about its own long axis and pitched nose-down about a
# horizontal axis, both through its root at the core: the gun deck then faces
# forward and outboard, toward the middle of those arcs, every turret can
# depress below the horizon on its own side, and the deck steps up from the nose
# aft, so the guns aft fire over the ones ahead of them and over the TPC noses.
# The drop puts the whole forearm below the core's terrace and glacis, a level
# of its own hanging off the shoulder. The plan is not yawed: the sprite fixes it.
ROLL = 16.0      # deg, outboard edge down
PITCH = 6.0      # deg, nose down
DROP = 7.0       # units, straight down after the rotation
ROOT = (40.0, 78.0, -10.0)   # port; the pivot: the arm's root against the core, at mid-depth
AXIS = (222.0, -36.0)        # port; the arm's long axis in plan (along its gun gallery)

X_NOSE, X_HEEL = 214.0, -16.0
SHOULDER_SLOPE = .8           # its rake: inboard per unit of rise (arm frame)
SHOULDER_X = (84.0, 92.0)     # round 8: the shoulder plate starts here (under the Devastator drum)

# The gun gallery's stations (os_hull.ARM_GALLERY copied): (x, y, half-width) along the
# turret line, bow to stern. Aft of the Devastator it narrows, so the shoulder plate and
# the outboard courses, not a tall gallery wall, make the arm's aft top.
ARM_GALLERY = [(210, 70.0, 8.0), (196, 70.5, 11.0), (158, 71.0, 13.0), (134, 74.0, 13.0), (114, 78.0, 13.5),
               (92, 84.0, 14.0), (60, 92.0, 13.0), (30, 98.0, 10.5), (4, 103.0, 8.5), (-12, 104.0, 7.0)]


# --- the plan as functions of X ---------------------------------------------

def _lerp(pts, x):
    """y on a polyline [(x, y), ...] at x (any X order), clamped at its ends."""
    p = sorted(pts)
    if x <= p[0][0]:
        return p[0][1]
    for (ax, ay), (bx, by) in zip(p, p[1:]):
        if ax <= x <= bx:
            return ay+(by-ay)*(x-ax)/(bx-ax) if bx > ax else ay
    return p[-1][1]


def y_in(x):
    return _lerp(ARM_INNER, x)


def y_out(x):
    return _lerp(ARM_OUTER, x)


def gallery_at(x):
    """(centre y, half-width) of the gun gallery at x."""
    return _lerp([(g[0], g[1]) for g in ARM_GALLERY], x), _lerp([(g[0], g[2]) for g in ARM_GALLERY], x)


def keel_z(x):
    """The keel line: a chined keel of straight runs with hard knuckles, so in side view it reads
    as the arm's own line rather than a continuation of the core's belly - level amidships, a
    straight raked forefoot climbing to the chin under the TPC casemate, a steep heel aft."""
    if x >= X_KNUCKLE_FORE:
        if x <= X_CHIN:
            return KEEL_Z+(CHIN_Z-KEEL_Z)*(x-X_KNUCKLE_FORE)/(X_CHIN-X_KNUCKLE_FORE)
        return CHIN_Z+(x-X_CHIN)*.6           # ahead of the nose face: cut away
    if x <= X_KNUCKLE_AFT:
        f = min(1.0, (X_KNUCKLE_AFT-x)/(X_KNUCKLE_AFT-X_HEEL))
        return KEEL_Z+(HEEL_Z-KEEL_Z)*f
    return KEEL_Z


def deck_z(x):
    """The arm's deck: level, rising into the heel so the posed heel meets the aft body's deck."""
    return DECK_Z+max(0.0, 6.0-x)*.22


def tuck(x):
    """How far the inboard face runs in under the waist (none forward of it, at the open notch)."""
    return 4.0 if x < 98 else max(2.0, 4.0*(110-x)/12)


def armour_inset(x):
    """How far the hull's outboard chines sit inside the plan edge, so the armour courses on
    them, not the hull under them, make the sprite's outline."""
    # The roll carries a chine outboard by its height above the pivot times sin(ROLL); the
    # max-beam chine climbs toward the nose as the keel sweeps up, so it is set further in there.
    zd = deck_z(x)-.34*(deck_z(x)-keel_z(x))
    return 3.3+1.3*math.sin(math.radians(ROLL))*max(0.0, zd+4.9)


def section(x):
    """The arm's angular cross-section at x (port, arm frame): named (y, z) points."""
    yi, yo = y_in(x), y_out(x)
    w = yo-yi
    yg, _ = gallery_at(x)
    yg = min(max(yg, yi+.3*w), yo-.38*w)
    d, k = deck_z(x), keel_z(x)
    h = d-k
    t = tuck(x)
    a = armour_inset(x)
    # The roll carries the inboard top chine about 5 outboard, so it is set that far inboard of
    # the plan edge: posed, it lands on the plan edge, on the waist's deck edge (aft of X 98)
    # and under the coaming (forward).
    ci = -5.0+1.5*max(0.0, min(1.0, (x-98)/10))
    # Along the waist the inboard chine stands up to the waist's deck (the roll lifts it no
    # further than that), so arm deck and valley floor meet; forward, at the open notch, it
    # drops to a chine under the coaming.
    za = d+2.0-3.0*max(0.0, min(1.0, (x-104)/10))
    return {
        'A': (yi+ci, za),                      # inboard top chine (inside the coaming forward)
        'B': (yg, d+2.5),                      # low crown under the gallery
        'C': (yo-.3*w-.5*a, d-.5),             # outboard shoulder chine
        'D': (yo-a, d-.34*h),                  # max-beam chine: the upper face falls ~45 deg to it
        'E': (yo-.13*w-.8*a, d-.74*h),         # lower outboard chine
        'F': (yi+.6*w, k),                     # keel, under the heavy outboard side
        'G': (yi+.16*w-t, d-.66*h),            # inboard bilge chine
        'H': (yi-t, d-.24*h),                  # inboard lower chine (under the waist)
    }


ORDER = 'ABCDEFGH'
OUTBOARD = 'BCDEF'      # the path the outboard courses follow, u = 0 at B .. 4 at F
INBOARD = 'BAHG'        # the inboard strake's path


def _stations(x0, x1, step=4.0, extra=()):
    xs = {round(x0, 3), round(x1, 3)}
    x = x0
    while x < x1:
        xs.add(round(x, 3))
        x += step
    xs |= {round(e, 3) for e in extra if x0 < e < x1}
    out = []
    for x in sorted(xs):
        if not out or x-out[-1] > .4:
            out.append(x)
    return out


def _y(points, s):
    return [(s*y, z) for y, z in points]


# --- courses: plates lying on the section ------------------------------------

def _path_point(sec, path, u):
    pts = [sec[c] for c in path]
    i = max(0, min(len(pts)-2, int(math.floor(u))))
    f = u-i
    (ay, az), (by, bz) = pts[i], pts[i+1]
    return (ay+(by-ay)*f, az+(bz-az)*f)


def _seg_normal(a, b, centre):
    dy, dz = b[0]-a[0], b[1]-a[1]
    L = (dy*dy+dz*dz)**.5 or 1.0
    n = (-dz/L, dy/L)
    m = ((a[0]+b[0])/2-centre[0], (a[1]+b[1])/2-centre[1])
    return n if n[0]*m[0]+n[1]*m[1] > 0 else (-n[0], -n[1])


def _path_normal(sec, path, u):
    pts = [sec[c] for c in path]
    cy = sum(p[0] for p in sec.values())/len(sec)
    cz = sum(p[1] for p in sec.values())/len(sec)
    i = int(round(u))
    if abs(u-i) < 1e-6 and 0 < i < len(pts)-1:          # a chine: mitre the two faces
        n0 = _seg_normal(pts[i-1], pts[i], (cy, cz))
        n1 = _seg_normal(pts[i], pts[i+1], (cy, cz))
        ny, nz = n0[0]+n1[0], n0[1]+n1[1]
        L = (ny*ny+nz*nz)**.5 or 1.0
        c = max(.5, (ny*n0[0]+nz*n0[1])/L)
        return (ny/L/c, nz/L/c)
    j = max(0, min(len(pts)-2, int(math.floor(u))))
    return _seg_normal(pts[j], pts[j+1], (cy, cz))


def shoulder_f(x):
    """0 aft of the forward shoulder plate, 1 on it (round 8), ramping across SHOULDER_X."""
    return max(0.0, min(1.0, (x-SHOULDER_X[0])/(SHOULDER_X[1]-SHOULDER_X[0])))


def shoulder_u0(x, u0=.72):
    """An outboard course's top edge: u0 aft, the shoulder chine C (u 1) on the shoulder plate,
    where the gallery's outboard face comes down to it."""
    return u0+(.995-u0)*shoulder_f(x)


def _course_section(x, path, u0, u1, t, sink=.6):
    sec = section(x)
    if callable(u0):
        u0 = u0(x)
    us = [u0]+[k for k in range(5) if u0 < k < u1]+[u1]
    inner, outer = [], []
    for u in us:
        (py, pz), (ny, nz) = _path_point(sec, path, u), _path_normal(sec, path, u)
        inner.append((py-ny*sink, pz-nz*sink))
        outer.append((py+ny*t, pz+nz*t))
    # Square-cut edges overhang a sloping face: a plate's edge stands out over the face beside
    # it with a wedge of daylight under it (seam audit: steps over the arm, the keel bar). So
    # each long edge of a plate is cut along the world's vertical - which, in the arm's frame,
    # leans inboard by the roll - by sliding its outer corner along the outer surface.
    vy, vz = -math.sin(math.radians(ROLL)), math.cos(math.radians(ROLL))
    for k, u in ((0, u0), (-1, u1)):
        ny, nz = _path_normal(sec, path, u)
        dy, dz = -nz, ny
        det = vy*(-dz)-vz*(-dy)
        if abs(ny*vy+nz*vz) > .35 and abs(det) > 1e-3:
            ry, rz = outer[k][0]-inner[k][0], outer[k][1]-inner[k][1]
            mu = (vy*rz-vz*ry)/det          # outer + d*mu lies on the vertical through inner
            if abs(mu) <= 2.5*t:
                outer[k] = (outer[k][0]+dy*mu, outer[k][1]+dz*mu)
    return inner+list(reversed(outer))


def course(kit, name, s, x_aft, x_fore, path, u0, u1, t_aft, t_fore, mat, chamfer=2.5, lip=1.2):
    """A plate course on the arm's faces from x_aft to x_fore, thickness t_aft at its aft end
    tapering to t_fore; its forward end is chamfered down to a thin edge (a sloped face)."""
    st = []
    span = x_fore-x_aft
    # The aft end is cut with a short lip leaning forward: under the arm the pose's 6 deg pitch
    # would otherwise lean a square aft end out over the face behind it (seam audit: steps).
    st.append((x_aft, _y(_course_section(x_aft, path, u0, u1, min(t_aft, .8)), s)))
    for x in _stations(x_aft+lip, x_fore-chamfer):
        t = t_aft+(t_fore-t_aft)*(x-x_aft)/span
        st.append((x, _y(_course_section(x, path, u0, u1, t), s)))
    st.append((x_fore, _y(_course_section(x_fore, path, u0, u1, .5), s)))
    return loft_x(kit, name, st, mat, bevel=.2)


def scale_course(kit, name, s, x_aft, x_fore, u0, u1, mat, t_aft=1.2, t_body=2.6, t_fore=3.9, kick=8.0,
                 rake=40.0, lean=.3):
    """An orange course segment laid like a scale pointing at the bow (round 6, the sprite's
    overlapping orange plates): thin at its aft end, where the next segment aft laps over it,
    t_body along its run, and kicked up and out to t_fore over its last `kick` units. Its forward
    end is a step angled toward the bow: one face, plumb in the world across the arm and leaning
    back by `lean` along it, running aft `rake` deg off square from the course's outboard edge
    to its inner edge, so the outboard (lowest) corner is the segment's point. (Two raked faces
    meeting at a mid-course apex cut a sliver notch out of the plate where it wraps the max-beam
    chine; plates converging on the faces hung ledges over daylight on the steep face.)"""
    st = []
    for x in _stations(x_aft, x_fore, 2.0, [x_aft+3.0, x_fore-kick]+list(SHOULDER_X)):
        if x <= x_aft+3.0:
            t = t_aft+(t_body-t_aft)*(x-x_aft)/3.0
        elif x < x_fore-kick:
            t = t_body
        else:
            f = (x-(x_fore-kick))/kick
            t = t_body+(t_fore-t_body)*(f*(2-f))
        st.append((x, _y(_course_section(x, OUTBOARD, u0, u1, t, .6 if x > x_aft+.5 else .3), s)))
    ob = loft_x(kit, name, st, mat, bevel=.2)
    sec = section(x_fore)
    py, pz = _path_point(sec, OUTBOARD, u1)
    r = math.radians(ROLL)
    tp = math.tan(math.radians(rake))
    n = (1.0, -tp, (lean-tp*math.sin(r))/math.cos(r))
    plane_cut(ob, name+' step', (x_fore, s*(py+t_fore), pz), (n[0], s*n[1], n[2]))
    return ob


def _plate(x, us, t, sink):
    """_course_section with an explicit u list (one vertex per entry), so a plate whose edges
    move across the chines keeps one vertex count along its loft."""
    sec = section(x)
    inner, outer = [], []
    for u in us:
        (py, pz) = _path_point(sec, OUTBOARD, u)
        ui = int(round(u))
        (ny, nz) = _path_normal(sec, OUTBOARD, ui if abs(u-ui) < .02 else u)
        inner.append((py-ny*sink, pz-nz*sink))
        outer.append((py+ny*t, pz+nz*t))
    vy, vz = -math.sin(math.radians(ROLL)), math.cos(math.radians(ROLL))
    for k, u in ((0, us[0]), (-1, us[-1])):
        ny, nz = _path_normal(sec, OUTBOARD, u)
        dy, dz = -nz, ny
        det = vy*(-dz)-vz*(-dy)
        if abs(ny*vy+nz*vz) > .35 and abs(det) > 1e-3:
            ry, rz = outer[k][0]-inner[k][0], outer[k][1]-inner[k][1]
            mu = (vy*rz-vz*ry)/det
            if abs(mu) <= 2.5*t:
                outer[k] = (outer[k][0]+dy*mu, outer[k][1]+dz*mu)
    return inner+list(reversed(outer))


# --- the solids --------------------------------------------------------------

def nose_face(ob):
    """The arm's raked nose face: everything ahead of a plane through the chin leaning aft
    NOSE_RAKE deg, so the hull ends a few units behind the casemate's face at the chin and
    further behind it up at the deck, and the casemate stands out of it."""
    r = math.radians(NOSE_RAKE)
    plane_cut(ob, 'arm nose face', (NOSE_FACE[0], 0, NOSE_FACE[1]), (math.cos(r), 0, math.sin(r)))


def tpc_arm(s=1):
    """The TPC's line of fire in the arm frame (before the pose): (y of its centre line, z of
    its axis) at the nose, so the nose plates can be laid against the casemate's sides."""
    from mathutils import Vector
    p = pose(s).inverted() @ Vector((206.0, s*70.0, TPC_AXIS_Z))
    return s*p.y, p.z


def arm_mass(kit, name, s):
    xs = _stations(X_HEEL, X_NOSE, 4.0, [p[0] for p in ARM_OUTER+ARM_INNER]+[g[0] for g in ARM_GALLERY]
                   + [X_KNUCKLE_AFT, X_KNUCKLE_FORE, X_CHIN])
    st = [(x, _y([section(x)[c] for c in ORDER], s)) for x in xs]
    ob = loft_x(kit, name, st, kit.plate, bevel=0)
    nose_face(ob)
    return ob


def gallery_top(x):
    """The gallery's top: GALLERY_TOP, climbing aft of X 80 and falling over the nose."""
    z = GALLERY_TOP
    if x < 80:
        z += (80-x)*math.tan(math.radians(3.0))
    if x > 190:
        z -= (x-190)*math.tan(math.radians(24.0))
    if x < 4:
        z -= (4-x)*.25         # the heel eases down aft onto the aft body
    return z


def hull_top(x, y):
    """Height of the arm's top surface (section chines A-B-C-D) at y, arm frame, port."""
    sec = section(x)
    return _lerp([sec[c] for c in 'ABCD'], y)


def terrace(x):
    """(height, outboard edge y) of the gallery's outboard terrace at x (arm frame, port): one
    step above the upper course's top at its inner edge, reaching 2 over that edge so the
    course runs in under the lip."""
    sec = section(x)
    py, pz = _path_point(sec, OUTBOARD, .72)
    ny, nz = _path_normal(sec, OUTBOARD, .72)
    return pz+nz*2.6+1.1, py+ny*2.6+2.0


def gallery(kit, name, s):
    """The gun gallery: a chamfered trapezoid along the turret line, its inboard side a long
    glacis toward the valley, its outboard side a riser and terrace stepping down onto the
    upper course."""
    xs = _stations(ARM_GALLERY[-1][0], ARM_GALLERY[0][0], 4.0, [g[0] for g in ARM_GALLERY]+list(SHOULDER_X))
    st = []
    for x in xs:
        yc, w = gallery_at(x)
        # the gallery runs diagonally: its width across a constant-X cut is w / cos(heading)
        dy = gallery_at(x+2)[0]-gallery_at(x-2)[0]
        w = w*(1+(dy/4)**2)**.5
        top = gallery_top(x)
        base = DECK_Z-3.0
        yi = max(y_in(x)+1.0, yc-w-5.0)
        yo = max(yc+w-.5, min(yc+w+7.0, y_out(x)-armour_inset(x)-3.0))
        # the base follows the hull's top down at its edges, so the gallery never stands over
        # the arm's sloping upper face with daylight under it (seam audit: belt 1 / gallery)
        bi, bo = min(base, hull_top(x, yi)-1.0), min(base, hull_top(x, yo)-1.0)
        # Round 8 (from ahead the forward arm was many small crumpled facets; the sprite's
        # forward arm is three plates): forward of the first turret the gallery's outboard side
        # is one plane - the shoulder plate - running from the gun deck straight down to the
        # arm's shoulder chine C, where the orange nose plate takes over on the chine. The
        # change is made under turret housing 1, and the terrace fades out with it.
        fwd = shoulder_f(x)
        if fwd > 0.0:
            sc = section(x)['C']
            yo += (sc[0]+.3-yo)*fwd
            bo += (sc[1]-1.0-bo)*fwd
        # Round 5 (owner: the gallery/forearm seam read from above as a split down each arm):
        # the outboard side is a stepped terrace, not one long dark face falling into the
        # orange. The gun deck's outboard chamfer drops by a short riser to a terrace shelf that
        # runs out level with the upper course's inner edge and ends in an armour lip a plate's
        # step above the orange, so from above the arm reads gun deck | terrace | orange course.
        # Every face leans inboard by more than the roll, so posed none overhangs.
        zt, ylip = terrace(x)
        y0 = min(yc+w-1.0, yo-.48*(top-2.5-bo))         # the plain outboard face's top edge
        # the shoulder plate: one sloped face at a constant rake off the shoulder chine, so its top
        # edge runs parallel to the chine instead of wandering with the gallery's own line
        y0 += (yo-SHOULDER_SLOPE*(top-2.5-bo)-y0)*fwd
        # (its chamfer up to the gun deck leans in 1.4 over its 2.5 rise there: steeper, the roll
        # leaned it out over the plate - a fold in the seam audit)
        yt = min(y0, max(yc+.5*w, ylip-7.0))
        yr = yt+.42*max(0.0, top-2.5-zt)
        yl = min(ylip, yo-.48*(zt-bo)-.6)
        # The terrace fades out where there is no room for it (over the nose, where the gallery
        # falls below the course, and at the heel), blending into the plain sloped face, so the
        # loft never jumps between two section shapes.
        f = max(0.0, min(1.0, (yl-yr-2.0)/3.0))*max(0.0, min(1.0, (top-3.0-zt)/2.0))*(1.0-fwd)

        def mix(face_t, p):
            q = (yo+(y0-yo)*face_t, bo+(top-2.5-bo)*face_t)
            return (q[0]+(p[0]-q[0])*f, q[1]+(p[1]-q[1])*f)
        # Round 7 (critique 1.1: from the C1 shot's high view each arm still split lengthwise into
        # gallery and forearm, the gallery's inboard glacis falling into a valley in shadow beside
        # the coaming - "five parallel fingers"). The gallery's inboard side is now the arm's own
        # deck: from the gun deck's shoulder it falls in one plane to the inboard top chine and ends
        # flush with the coaming's top (aft of the coaming, a plate's lip over the waist's deck),
        # so from above the arm reads as one mass edged by the coaming, with no channel in it.
        sa = section(x)['A']
        lip = 1.9 if x > 104.0 else .6+1.3*max(0.0, min(1.0, (x-92.0)/12.0))
        yi = sa[0]+2.6
        bi = min(base, hull_top(x, yi)-1.0)
        sec = [(yi, bi), (yo, bo), mix(.25, (yl, zt-.9)), mix(.4, (yl-.9, zt)), mix(.75, (yr, zt)),
               mix(1.0, (yt, top-2.5)), (max(yc-.45*w+1.0, min(yc+.35*w, mix(1.0, (yt, 0))[0]-.5-.9*fwd)), top),
               (yc-.45*w, top), (yc-w+1.0, top-1.5), (yi, sa[1]+lip)]
        st.append((x, _y(sec, s)))
    ob = loft_x(kit, name, st, kit.plate, bevel=0)
    nose_face(ob)
    return ob


def arm_xy(x, y, z):
    """The arm-frame (port) plan point that the pose carries over the world point (x, y) at height z,
    so a housing built before the pose lands under its slot after it."""
    from mathutils import Vector
    m = pose(1)
    p = Vector((x, y, z))
    for _ in range(6):
        q = m @ p
        p.x -= q.x-x
        p.y -= q.y-y
    return p.x, p.y


def housings(kit, side, s):
    """Lozenge plinths under the arm's turrets and the Devastator's drum and aft fairing."""
    for k, (x, y, r) in enumerate(((158, 71, 7.0), (134, 74, 4.2), (114, 78, 4.2)), 1):
        top = gallery_top(x)
        x, y = arm_xy(x, y, top)
        L, W = r+4.5, r+1.8
        poly = [(x+L, y+.6), (x+.35*L, y+W), (x-.6*L, y+W+.8), (x-L, y+1.4), (x-.6*L, y-W+1.0), (x+.35*L, y-W)]
        prism(kit, f'Armour {side} arm turret housing {k}', _side(poly, s),
              [(0.0, DECK_Z-1.0), (0.0, top+.3), (1.2, top+1.3)], kit.plate, bevel=.2)
    # Devastator (WS 018 at 77, 88; ring r 11): a raised drum with a sloped batter, and the
    # fairing that runs aft from it into the gallery (the sprite's converging struts).
    top = gallery_top(77.0)
    x, y = arm_xy(77.0, 88.0, top)
    drum = [(x+15.5*math.cos(2*math.pi*(k+.5)/16), y+15.5*math.sin(2*math.pi*(k+.5)/16)) for k in range(16)]
    prism(kit, f'Armour {side} arm devastator housing', _side(drum, s),
          [(0.0, DECK_Z-3.0), (0.0, top-4.0), (3.4, top+1.4), (4.6, top+2.6)], kit.plate, bevel=.2)
    fair = [(x-11, y-7), (x-13, y+8), (x-29, y+7), (x-33, y+3)]
    f = prism(kit, f'Armour {side} arm devastator fairing', _side(fair, s),
              [(0.0, DECK_Z-1.0), (0.0, top+1.6), (1.4, top+2.4)], kit.grey, bevel=.2)
    plane_cut(f, 'fairing aft', (x-13, 0, top+2.4), facet_normal((-1, 0), 6.0))


def build(kit):
    import bpy
    for side, s in (('port', 1), ('starboard', -1)):
        before = set(bpy.data.objects)
        arm = arm_mass(kit, f'Hull {side} arm', s)
        g = gallery(kit, f'Hull {side} arm gallery', s)
        kit.hull += [arm, g]
        # Upper course (orange). Round 6 (owner note 3; critique C, the sprite at 6x): the course is
        # not one strip but overlapping plate segments laid like scales pointing at the bow - the
        # nose plate, then four segments whose forward ends stand at the sprite's breaks (X ~166,
        # 142, 100 and 60, each just ahead of or aft of a turret housing), every one thin at its aft
        # end, where the next segment's forward end laps over it, and kicked up and out at its
        # forward end into a step whose face is a forward-pointing chevron. The lower edge is
        # irregular (each segment reaches a different depth), as the sprite's orange is.
        course(kit, f'Armour {side} arm belt 1', s, 157, 192, OUTBOARD, 1.0, 2.9, 2.8, 2.2, kit.orange)
        for k, (xa, xf, u0, u1) in ((2, (135, 169, shoulder_u0, 2.05)), (3, (88, 147, shoulder_u0, 1.95)),
                                    (4, (48, 100, shoulder_u0, 2.15)), (6, (22, 60, .62, 2.3))):
            scale_course(kit, f'Armour {side} arm belt {k}', s, xa, xf, u0, u1, kit.orange)
        # Each block is centred on its housing as the pose carries it (arm_xy), as long as the
        # housing's lozenge (the Devastator: its drum): the turret's own armour interrupts the
        # course's inner half as the sprite's grey rims break the orange.
        for k, (wx, wy, half, u1) in ((4, (77, 88, 11.5, 1.6)),):
            xc = arm_xy(wx, wy, gallery_top(wx))[0]
            xa, xf = xc-half, xc+half
            course(kit, f'Armour {side} arm rim block {k}', s, xa, xf, OUTBOARD, .45, u1, 4.6, 4.4, kit.grey,
                   chamfer=1.6, lip=1.6)
        belt5 = course(kit, f'Armour {side} arm belt 5', s, 0, 20, OUTBOARD, .9, 2.1, 2.4, 2.0, kit.grey)
        # Lower course (dark grey). Round 6 (critique C: abeam, five evenly spaced blocks read as the
        # teeth of a comb): the sprite has three large grey blocks on the outboard rim (X 100-150,
        # 62-98, 18-58), of unequal length and depth, each with a doubler of its own at a different
        # place, so the rim reads as a few irregular plates rather than a row of teeth.
        steps = []
        for k, (xa, xf, u0, u1, ta, tf, ch) in enumerate(((101, 144, 1.75, 3.25, 4.0, 2.8, 4.0),
                                                          (61, 93, 1.5, 3.3, 3.8, 3.0, 4.0),
                                                          (19, 52, 1.95, 3.25, 3.4, 2.6, 3.0)), 1):
            steps.append(course(kit, f'Armour {side} arm step {k}', s, xa, xf, OUTBOARD, u0, u1, ta, tf,
                                kit.grey, chamfer=ch, lip=2.6))
        for k, (xa, xf, u0, u1, ta, tf) in ((4, (116, 138, 2.3, 3.2, 5.0, 4.6)), (5, (66, 82, 2.05, 3.15, 4.7, 4.2)),
                                            (7, (33, 49, 2.45, 3.2, 4.4, 4.0))):
            steps.append(course(kit, f'Armour {side} arm step {k}', s, xa, xf, OUTBOARD, u0, u1, ta, tf,
                                kit.grey, chamfer=3.0, lip=2.6))
        # Inboard strake on the top chine facing the notch.
        course(kit, f'Armour {side} arm coaming', s, 106, 198, INBOARD, .85, 1.7, 1.8, 1.6, kit.grey)
        # Keel bar: a grey strake along the chined keel, heel to chin, so the keel reads as a line.
        keel = course(kit, f'Armour {side} arm keel bar', s, X_HEEL+4, X_CHIN-1, 'EFG', .62, 1.38, 1.7, 1.5,
                      kit.grey, chamfer=5.0)
        after_end(kit, side, s, arm, [belt5, keel, steps[2], steps[5]])
        housings(kit, side, s)
        nose_cheeks(kit, side, s)
        shoulder(kit, side, s, arm, g)
        ARM_PARTS[side] = [o for o in bpy.data.objects if o not in before]
        tilt(kit, side, s)
        after_planes(kit, side, s)
        plates(side)


# Round 8: the arm's lofts are ruled between stations a few units apart whose chines kink at
# different X (inner and outer plan, gallery line), so each big face was a field of slightly
# twisted quads - flat-shaded, a crumple of thin triangles, worst from ahead where the light
# grazes them. Shaded smooth within PLATE_ANGLE, each face reads as the one plate it is; every
# chine and step (well over PLATE_ANGLE) stays a hard edge.
PLATE_ANGLE = 18.0


def plates(side):
    for ob in ARM_PARTS[side]:
        if ob.type != 'MESH' or not (ob.name in (f'Hull {side} arm', f'Hull {side} arm gallery')
                                     or ob.name.startswith(f'Armour {side} arm belt')):
            continue
        for p in ob.data.polygons:
            p.use_smooth = True
        ob.data.set_sharp_from_angle(angle=math.radians(PLATE_ANGLE))


# The after end (round 5; critic: from astern and on the stern quarters the arms' after ends
# were lumpy sloped masses - the heel sweeping up under the aft body with the rim blocks and
# keel bar trailing off it). Each arm now ends like a gun platform: the keel runs level to
# AFTER_KEEL_X and the lower hull is cut off there by a steep raked after face (AFTER_RAKE deg
# from horizontal), the full depth of the forearm, that breaks on a chine at AFTER_CHINE_Z to a
# shallow ramp (RAMP_RAKE) rising aft into the aft body's flared quarter. A grey armour collar
# runs round the hull just forward of the steep face, its after face flush with the hull's, so
# it frames the face as a lip and the courses on the arm end against it. The upper tier - deck,
# gallery terrace and broadside fin - carries on aft above the ramp into the aft body's deck, so
# from astern the arm reads fin, deck, ramp, lip, face.
AFTER_KEEL_X, AFTER_RAKE, AFTER_CHINE_Z, RAMP_RAKE, COLLAR_W = 12.0, 68.0, -13.0, 30.0, 4.0


def _after_profile(shift=0.0):
    """The after end's profile in the arm frame (x, z), keel to ramp, moved `shift` forward."""
    k = (AFTER_KEEL_X+shift, KEEL_Z)
    c = (k[0]-(AFTER_CHINE_Z-KEEL_Z)/math.tan(math.radians(AFTER_RAKE)), AFTER_CHINE_Z)
    return k, c


def _after_cutter(kit, s, shift=0.0, steep_only=False, rake=AFTER_RAKE):
    """A solid filling everything aft of and under the after face (a convex wedge: aft of the
    steep face and under the ramp), extruded right across the arm."""
    (kx, kz), (cx, cz) = _after_profile(shift)
    cx = kx-(cz-kz)/math.tan(math.radians(rake))
    low = (kx+(kz+90.0)/math.tan(math.radians(rake)), -90.0)
    if steep_only:
        prof = [low, (cx, cz), (cx-80.0, cz), (-80.0, -90.0)]
    else:
        prof = [low, (cx, cz), (-80.0, cz+(cx+80.0)*math.tan(math.radians(RAMP_RAKE))), (-80.0, -90.0)]
    verts = [(x, s*y, z) for y in (20.0, 170.0) for x, z in prof]
    n = len(prof)
    faces = [tuple(range(n)), tuple(range(n, 2*n))]+[(i, (i+1) % n, n+(i+1) % n, n+i) for i in range(n)]
    return kit.mesh('Temporary arm after cutter', verts, faces, kit.dark)


def after_end(kit, side, s, arm, courses):
    import bpy
    boolean(arm, _after_cutter(kit, s))
    for c in courses:
        boolean(c, _after_cutter(kit, s, COLLAR_W))
        if not c.data.polygons:
            bpy.data.objects.remove(c, do_unlink=True)
    # The collar: one band round the hull below the deck (outboard faces and keel, into the
    # inboard bilge, whose upper part the aft body buries), a plate thicker than the rim blocks
    # it frames, trimmed to the steep face aft and
    # to a parallel face COLLAR_W forward of it, and under the ramp's chine.
    (kx, _), _ = _after_profile()
    col = course(kit, f'Armour {side} arm after collar', s, kx-24.0, kx+COLLAR_W+14.0, 'CDEFG', 1.6, 3.35,
                 3.9, 3.9, kit.grey, chamfer=.6)
    boolean(col, _after_cutter(kit, s))
    # Its forward edge stands nearer the vertical than the face, so on the arm's under-faces the
    # plate's outer skin never runs forward of its root (no lip hanging over daylight).
    keep = _after_cutter(kit, s, COLLAR_W, steep_only=True, rake=84.0)
    boolean(col, keep, 'INTERSECT')


# Round 7 (critique 1.3: from astern and on the stern quarter the arm's after end still read as
# a crumpled lobe - the loft's octagonal end cap, the ramp, the collar and the sponson glacis
# each at their own angle). The posed after end is now two flat planes cut square across the arm
# in the WORLD: one raked face from the keel's after knuckle up to the gun deck, square across the
# arm and leaning aft AFTER_LEAN deg from the vertical at the top (so the deck runs aft over it to
# carry the flak sponson), and one step - the sponson standing on the deck at its top. Every arm
# part (hull, gallery, courses, collar, fin) is cut by the same plane, so the plates end flush on
# the face. (A second, set-back face hung the upper hull over a ledge with air under it: a floating
# edge in the seam audit; the sponson is the step instead.)
AFTER_LEAN, AFTER_FOOT = 35.0, (19.0, -38.0)


def _after_x(z):
    """World X of the posed after end's raked face at height z."""
    return AFTER_FOOT[0]-(z-AFTER_FOOT[1])*math.tan(math.radians(AFTER_LEAN))


# The flak sponson (WS 010/011 at (-3, +-112)): the turret stood where the arm's heel, the fin
# crest and the aft body's quarter meet, so its glacis was laid over four surfaces at once and
# crumpled into a lobe. It now stands on one flat-topped armoured sponson, level with the highest
# deck under its ring, its sides plumb planes with a chamfered crown, so the barbette sits flush and the sponson is the after end's one step, standing on the deck at
# the top of the raked face.
SPONSON = (-3.0, 112.0)
SPONSON_PLAN = [(10.5, -5.0), (10.5, 3.5), (3.0, 7.8), (-6.0, 7.8), (-11.0, 2.5), (-11.0, -6.0), (-8.0, -10.5),
                (5.0, -10.5)]


def _deck_top(x, y, r):
    """Highest built surface (not weapons/lights/fittings) under a ring of radius r at (x, y)."""
    import bpy
    from mathutils import Vector
    bpy.context.view_layer.update()
    dg = bpy.context.evaluated_depsgraph_get()
    best = None
    pts = [(x, y)]+[(x+r*math.cos(k*math.pi/6), y+r*math.sin(k*math.pi/6)) for k in range(12)]
    for ob in bpy.context.scene.objects:
        if ob.type != 'MESH' or ob.name.split(' ')[0] in ('Weapon', 'Light', 'Fitting', 'Temporary'):
            continue
        ev = ob.evaluated_get(dg)
        inv = ev.matrix_world.inverted()
        for px, py in pts:
            ok, p, _, _ = ev.ray_cast(inv @ Vector((px, py, 150)), (inv.to_3x3() @ Vector((0, 0, -1))).normalized())
            if ok:
                z = (ev.matrix_world @ p).z
                best = z if best is None else max(best, z)
    return best


def sponson(kit, side, s):
    cx, cy = SPONSON[0], s*SPONSON[1]
    top = _deck_top(cx, cy, 8.4)+.2
    plan = [(cx+dx, cy+s*dy) for dx, dy in SPONSON_PLAN]
    if s < 0:
        plan = list(reversed(plan))
    # Plumb sides with a chamfered crown, carried down until the arm's raked after face cuts them
    # off (after_planes), so its outboard side stands into the arm's rolled face, not over it.
    ob = prism(kit, f'Armour {side} arm flak sponson', plan, [(0.0, top-40.0), (0.0, top-1.0), (1.0, top)],
               kit.plate, bevel=.2)
    return ob


def after_planes(kit, side, s):
    import bpy
    # the sponson is cut with the arm: its underside ends on the same raked face, so it never
    # hangs below the hull it stands on
    ARM_PARTS[side].append(sponson(kit, side, s))
    prof = [(_after_x(-90.0), -90.0), (_after_x(70.0), 70.0), (-160.0, 70.0), (-160.0, -90.0)]
    n = len(prof)
    for ob in list(ARM_PARTS[side]):
        if ob.type != 'MESH':
            continue
        verts = [(x, s*y, z) for y in (30.0, 180.0) for x, z in prof]
        faces = [tuple(range(n)), tuple(range(n, 2*n))]+[(i, (i+1) % n, n+(i+1) % n, n+i) for i in range(n)]
        # the cut faces take the part's own material (a cutter's would paint the face dark)
        cutter = kit.mesh('Temporary arm after planes', verts, faces, ob.active_material or kit.plate)
        boolean(ob, cutter)
        if not ob.data.polygons:
            ARM_PARTS[side].remove(ob)
            bpy.data.objects.remove(ob, do_unlink=True)


def _on(p, q, z):
    """The point on segment p-q at height z, clamped to the segment."""
    if abs(q[1]-p[1]) < 1e-6:
        return p
    f = max(0.0, min(1.0, (z-p[1])/(q[1]-p[1])))
    return (p[0]+(q[0]-p[0])*f, p[1]+(q[1]-p[1])*f)


def _cheek(kit, name, s, x0, x1, inner, path, t, brow, chin, mat):
    """A nose plate from x0 to its point at x1: its inner side flat against the casemate (y =
    inner), its outer side the arm's own faces along `path` (chines at u = 1, 2, 3) offset t
    outward, between a brow line and a chin line (functions of x). Aft of the nose face it is a
    plate lying on the hull; ahead of it, a solid wedge flanking the gun."""
    st = []
    for x in _stations(x0, x1, 2.0):
        sec = section(x)
        p1, p2, p3 = [(lambda u: (_path_point(sec, path, u)[0]+_path_normal(sec, path, u)[0]*t,
                                  _path_point(sec, path, u)[1]+_path_normal(sec, path, u)[1]*t))(u)
                      for u in (1, 2, 3)]
        zt = min(brow(x), p1[1])
        zb = max(chin(x), p3[1])
        zt, zb = max(zt, zb+1.0), min(zb, zt-1.0)
        top = _on(p1, p2, zt) if zt < p1[1] else p1
        bot = _on(p2, p3, zb) if zb > p3[1] else p3
        mid = p2 if zb < p2[1] < zt else ((top[0]+bot[0])/2, (zt+zb)/2)
        f = 1.0-.55*max(0.0, (x-(x1-9.0))/9.0)          # the point narrows in plan
        pull = lambda p: (inner+(p[0]-inner)*f, p[1])
        st.append((x, _y([(inner, zt), pull(top), pull(mid), pull(bot), (inner, zb)], s)))
    return loft_x(kit, name, st, mat, bevel=.25)


def nose_cheeks(kit, side, s):
    """The sprite's stepped cheeks either side of the TPC ladder (X 184-212): nose plates laid
    against the casemate's sides and over the arm's own faces, each an arrowhead pointing along
    the line of fire - its brow raking up and aft to the deck, its chin raking down and aft to
    the keel - so the nose reads as a wedge with the cannon at its point and the casemate
    standing clear of it above and below. Cheek 2 (outboard) runs to a point at X 212 on the plan
    edge; cheek 1 (inboard) to X 206 inboard of the casemate, as the sprite draws them."""
    yc, zc = tpc_arm(s)
    hw = TPC_HALF_W-.15

    def line(x0, z0, x1, z1):
        return lambda x: z0+(z1-z0)*(x-x0)/(x1-x0)
    _cheek(kit, f'Armour {side} nose cheek 1', s, 184.0, 206.5, yc-hw, INBOARD, 1.6,
           line(184.0, DECK_Z+4.0, 206.5, zc+4.0), line(184.0, keel_z(184.0), 206.5, zc-4.0), kit.grey)
    _cheek(kit, f'Armour {side} nose cheek 2', s, 180.0, 212.5, yc+hw, OUTBOARD, 1.6,
           line(180.0, DECK_Z+3.0, 212.5, zc+3.0), line(180.0, keel_z(180.0), 212.5, zc-2.5), kit.grey)


def _cap(kit, name, plan, sources, lift, floor, mat, s=1):
    """A plate following the top of `sources` `lift` proud, clipped to `plan`, from `floor` up.
    The lift is along the WORLD vertical (in the arm frame it leans inboard by the roll), so the
    posed plate stands straight over what it covers instead of leaning out over its edges."""
    import bpy
    copies = []
    for src in sources:
        c = src.copy()
        c.data = src.data.copy()
        c.modifiers.clear()
        bpy.context.collection.objects.link(c)
        c.location.y -= s*math.sin(math.radians(ROLL))*lift
        c.location.z += math.cos(math.radians(ROLL))*lift
        copies.append(c)
    base = copies[0]
    for c in copies[1:]:
        boolean(base, c, 'UNION')
    clip = prism(kit, 'Temporary cap clip', plan, [(0.0, floor), (0.0, 90.0)], mat, bevel=0)
    for v in clip.data.vertices:          # walls plumb in the world, not in the rolled arm frame
        v.co.y -= s*math.tan(math.radians(ROLL))*(v.co.z-GALLERY_TOP)
    boolean(base, clip, 'INTERSECT')
    base.data.materials.clear()
    return kit.finish(base, name, mat, .2)


# The shoulder: the sprite's orange broadside fin at the arm root, a plate laid on
# the arm and gallery and posed with them, with a crest tier on its inboard part.
def shoulder(kit, side, s, arm, g):
    fin = [(48, 78), (30, 97), (-8, 106), (-14, 92), (-6, 68), (20, 72)]
    _cap(kit, f'Armour {side} broadside fin', _side(fin, s), [arm, g], 3.4, DECK_Z-6.0, kit.orange, s)
    crest = [(38, 83), (26, 95), (-2, 101), (-7, 91), (0, 75), (18, 78)]
    _cap(kit, f'Armour {side} broadside fin crest', _side(crest, s), [arm, g], 6.2, DECK_Z-2.0, kit.orange, s)


def pose(s):
    """The arm's rigid pose as a 4x4 world matrix (s = 1 port, -1 starboard)."""
    from mathutils import Matrix, Vector
    u = Vector((AXIS[0], s*AXIS[1], 0.0)).normalized()
    w = Vector((0, 0, 1)).cross(u)
    p = Vector((ROOT[0], s*ROOT[1], ROOT[2]))
    r = Matrix.Rotation(math.radians(PITCH), 4, w) @ Matrix.Rotation(math.radians(-s*ROLL), 4, u)
    return Matrix.Translation(p+Vector((0, 0, -DROP))) @ r @ Matrix.Translation(-p)


def tilt(kit, side, s):
    """Pose one arm's parts: bake the rigid pose into every mesh (so the later ray
    casts against kit.hull see it) and move any empty."""
    import bpy
    m = pose(s)
    for ob in ARM_PARTS[side]:
        if ob.type == 'MESH':
            ob.data.transform(m @ ob.matrix_world)
            ob.matrix_world = ob.matrix_world.__class__.Identity(4)
            ob.data.update()
        else:
            ob.matrix_world = m @ ob.matrix_world
    bpy.context.view_layer.update()
