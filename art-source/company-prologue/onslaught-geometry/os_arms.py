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
ARM = (-30.0, 8.0, 20.0, 24.0, 16.0, 18.0, 3.0, 10.0)
DECK_Z = 8.0          # the arm's deck (inboard and shoulder chines sit just under it)
KEEL_Z = -30.0        # the keel line amidships

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
    """The keel line: level amidships, a raked forefoot sweeping up to the nose, a heel aft."""
    if x > 104:
        f = min(1.0, (x-104)/(X_NOSE-104))
        return KEEL_Z+(DECK_Z-9.0-KEEL_Z)*f**1.5
    if x < 14:
        f = min(1.0, (14-x)/(14-X_HEEL))
        return KEEL_Z+16.0*f**1.4
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


def _course_section(x, path, u0, u1, t, sink=.6):
    sec = section(x)
    us = [u0]+[k for k in range(5) if u0 < k < u1]+[u1]
    inner, outer = [], []
    for u in us:
        (py, pz), (ny, nz) = _path_point(sec, path, u), _path_normal(sec, path, u)
        inner.append((py-ny*sink, pz-nz*sink))
        outer.append((py+ny*t, pz+nz*t))
    return inner+list(reversed(outer))


def course(kit, name, s, x_aft, x_fore, path, u0, u1, t_aft, t_fore, mat, chamfer=2.5):
    """A plate course on the arm's faces from x_aft to x_fore, thickness t_aft at its aft end
    tapering to t_fore; its forward end is chamfered down to a thin edge (a sloped face)."""
    st = []
    span = x_fore-x_aft
    for x in _stations(x_aft, x_fore-chamfer):
        t = t_aft+(t_fore-t_aft)*(x-x_aft)/span
        st.append((x, _y(_course_section(x, path, u0, u1, t), s)))
    st.append((x_fore, _y(_course_section(x_fore, path, u0, u1, .5), s)))
    return loft_x(kit, name, st, mat, bevel=.2)


# --- the solids --------------------------------------------------------------

def arm_mass(kit, name, s):
    xs = _stations(X_HEEL, X_NOSE, 4.0, [p[0] for p in ARM_OUTER+ARM_INNER]+[g[0] for g in ARM_GALLERY])
    st = [(x, _y([section(x)[c] for c in ORDER], s)) for x in xs]
    ob = loft_x(kit, name, st, kit.plate, bevel=0)
    # A raked face on the nose under the casemate, so the nose ends in a sloped prow.
    plane_cut(ob, 'arm forefoot', (X_NOSE-2.0, 0, DECK_Z-6.0), (math.sin(math.radians(35)), 0,
                                                                  -math.cos(math.radians(35))))
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


def gallery(kit, name, s):
    """The gun gallery: a chamfered trapezoid along the turret line, its inboard side a long
    glacis toward the valley, its outboard side a steeper face onto the upper course."""
    xs = _stations(ARM_GALLERY[-1][0], ARM_GALLERY[0][0], 4.0, [g[0] for g in ARM_GALLERY])
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
        sec = [(yi, base), (yo, base), (yc+w-1.0, top-2.5), (yc+.35*w, top),
               (yc-.45*w, top), (yc-w+2.0, top-2.6)]
        st.append((x, _y(sec, s)))
    return loft_x(kit, name, st, kit.plate, bevel=0)


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
        # Upper course (orange): along the shoulder and sloped upper face, segmented with seams.
        for k, (xa, xf, u0, u1, ta, tf) in enumerate(((152, 192, .72, 2.9, 2.8, 2.2),
                                                       (106, 150, .72, 2.05, 2.6, 2.0),
                                                       (72, 104, .72, 2.05, 2.6, 2.0),
                                                       (22, 70, .62, 2.25, 2.8, 2.0)), 1):
            course(kit, f'Armour {side} arm belt {k}', s, xa, xf, OUTBOARD, u0, u1, ta, tf, kit.orange)
        course(kit, f'Armour {side} arm belt 5', s, -12, 20, OUTBOARD, .9, 2.1, 2.4, 2.0, kit.grey)
        # Lower course (dark grey): stepped blocks on the outboard rim lapping over the orange.
        for k, (xa, xf, ta, tf) in enumerate(((98, 150, 4.0, 2.8), (76, 96, 3.8, 2.6), (56, 74, 3.8, 2.6),
                                              (40, 54, 3.6, 2.5), (20, 38, 3.6, 2.5), (2, 18, 3.4, 2.4)), 1):
            course(kit, f'Armour {side} arm step {k}', s, xa, xf, OUTBOARD, 1.75, 3.25, ta, tf, kit.grey,
                   chamfer=4.0)
        # Inboard strake on the top chine facing the notch.
        course(kit, f'Armour {side} arm coaming', s, 106, 204, INBOARD, .85, 1.7, 1.8, 1.6, kit.grey)
        housings(kit, side, s)
        # TPC nose cheeks either side of the barrel.
        for k, poly in enumerate(([(213.5, 64.8), (213.5, 66.4), (205.5, 66.2), (205.5, 63.6)],
                                  [(213.5, 73.6), (213.5, 78.6), (205.5, 80.6), (205.5, 74.2)]), 1):
            ch = prism(kit, f'Armour {side} nose cheek {k}', _side(poly, s), [(0.0, -2.0), (0.0, 9.0), (.8, 9.8)],
                       kit.grey, bevel=.25)
            plane_cut(ch, 'cheek nose', (190, 0, 12.0), facet_normal((1, 0), 16))
        shoulder(kit, side, s, arm, g)
        ARM_PARTS[side] = [o for o in bpy.data.objects if o not in before]
        tilt(kit, side, s)


def _cap(kit, name, plan, sources, lift, floor, mat):
    """A plate following the top of `sources` `lift` proud, clipped to `plan`, from `floor` up."""
    import bpy
    copies = []
    for src in sources:
        c = src.copy()
        c.data = src.data.copy()
        c.modifiers.clear()
        bpy.context.collection.objects.link(c)
        c.location.z += lift
        copies.append(c)
    base = copies[0]
    for c in copies[1:]:
        boolean(base, c, 'UNION')
    clip = prism(kit, 'Temporary cap clip', plan, [(0.0, floor), (0.0, 90.0)], mat, bevel=0)
    boolean(base, clip, 'INTERSECT')
    base.data.materials.clear()
    return kit.finish(base, name, mat, .2)


# The shoulder: the sprite's orange broadside fin at the arm root, a plate laid on
# the arm and gallery and posed with them, with a crest tier on its inboard part.
def shoulder(kit, side, s, arm, g):
    fin = [(48, 78), (30, 97), (-8, 106), (-14, 92), (-6, 68), (20, 72)]
    _cap(kit, f'Armour {side} broadside fin', _side(fin, s), [arm, g], 3.4, DECK_Z-6.0, kit.orange)
    crest = [(38, 83), (26, 95), (-2, 101), (-7, 91), (0, 75), (18, 78)]
    _cap(kit, f'Armour {side} broadside fin crest', _side(crest, s), [arm, g], 6.2, DECK_Z-2.0, kit.orange)


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
