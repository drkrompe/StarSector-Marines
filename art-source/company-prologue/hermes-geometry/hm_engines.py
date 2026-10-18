"""Hermes drive units, exactly at hermes.ship's engine slots.

Engines 1-3 are the main drives on the aft block's stern face (slots at
X -39, Y +10 / 0 / -10, width 8, angle 180). The sprite shows three
separate rounded nubs there (Y 6..13, -3..3, -13..-6), so each is a whole
drive unit standing on a common thrust plate bolted over the stern face:
a faceted shroud can sunk through the plate into the block, a bolted
flange ring and two pylon gussets tying the can to the plate, a gimbal
ring with side trunnions and two dorsal actuators, a ribbed heat-shield
band, the bell (banded liner, outer skin, throat ring, dark injector face)
and a mouth lip.

Engines 4-5 are the maneuvering jets (system maneuveringjets) at
(-19, +-23), angles 150 / 210 and width 6: compact canted thruster blocks
seated in the notch where the wing body meets the aft block, each with a
square bolted face plate, a swivel collar, its own small bell, a mounting
strap and a valve block.

Every unit is built in a local frame (local -X upstream, mouth plane at
the origin, exhaust along local +X) and then turned to its slot angle and
moved to its slot, so the mouth stays exactly at the slot.

The bell interior ends at the dark injector face aft of every solid it
passes through (block face, thrust plate, jet housing), so nothing flat
shows inside a bell.

Names follow the Second Wind's: 'Engine {i} aft-facing bell', 'Engine {i}
mouth lip', 'Engine {i} dark throat', plus an 'Engine {i} exhaust anchor'
empty at the mouth pointing along the exhaust (its local +Z).
"""
import math
import bmesh
import bpy
from mathutils import Matrix, Vector

import hm_kit

# (x, y, angle, width) from hermes.ship, in slot order.
ENGINES = [(-39, 10, 180, 8), (-39, 0, 180, 8), (-39, -10, 180, 8),
           (-19, 23, 150, 6), (-19, -23, 210, 6)]
ROLES = ['port main drive', 'centre main drive', 'starboard main drive',
         'port maneuvering jet', 'starboard maneuvering jet']
MAIN_Z, JET_Z = 0.0, 0.0
STERN_FACE = -37.0

# Main drive, in units behind (upstream of) the mouth plane, u = 0 at the
# mouth. The block's stern face is u = 2.0 and the thrust plate's aft face
# u = 1.85, so the injector face (u = 1.8) stays aft of both.
M_THROAT_U, M_INJECT_U = 1.6, 1.8
M_PLATE_U = 1.85
PLATE_FWD_X = -35.8        # thrust plate's buried forward face
PLATE_HALF_Y, PLATE_HALF_Z, PLATE_CORNER = 15.3, 6.4, 1.8
# Maneuvering jet: housing aft face at u = 1.45, injector face at 1.3.
J_THROAT_U, J_INJECT_U, J_FACE_U = 1.1, 1.3, 1.45
J_POCKET_U = 1.6           # pocket back wall, just inside the housing face
THROAT_R = .6              # throat radius over mouth radius
WALL = .045                # bell wall over mouth radius
RAM_ANGLES = (60, 120)     # main-drive gimbal actuators, degrees from local +Y towards up


def bell_radius(i, width):
    """Mouth radius: the slot width read as the bell's mouth diameter, less a lip."""
    return width*.45 if i <= 3 else width*.4


def z_of(i):
    return MAIN_Z if i <= 3 else JET_Z


# --- local-frame helpers (axis = local X, upstream = -X) --------------------

def _flat(r, a, sides):
    """Radius of a regular `sides`-gon with apothem r at polar angle a (flats centred on 0)."""
    if not sides:
        return r
    s = math.tau/sides
    return r/math.cos(((a+s/2) % s)-s/2)


def revolve(kit, name, prof, mat, n=36, closed=False, inward=False, sides=None, bevel=0):
    """Surface of revolution about local X through an (x, radius) profile.
    Closed profiles make solids; `sides` makes the section a faceted
    polygon (radius read as apothem). Copied from sw_engines.revolve."""
    v = []
    for x, r_ in prof:
        for t in range(n):
            a = t*math.tau/n
            rr = _flat(r_, a, sides)
            v.append((x, rr*math.cos(a), rr*math.sin(a)))
    m = len(prof)
    rows = m if closed else m-1
    f = [(k*n+t, k*n+(t+1) % n, ((k+1) % m)*n+(t+1) % n, ((k+1) % m)*n+t)
         for k in range(rows) for t in range(n)]
    ob = kit.mesh(name, v, f, mat, bevel=bevel)
    bm = bmesh.new()
    bm.from_mesh(ob.data)
    if closed:
        bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    else:
        out = 0
        for fc in bm.faces:
            c = fc.calc_center_median()
            out += fc.normal.dot(Vector((0, c.y, c.z)))*fc.calc_area()
        if (out > 0) == inward:
            bmesh.ops.reverse_faces(bm, faces=bm.faces)
    bm.to_mesh(ob.data)
    bm.free()
    return ob


def ring(kit, name, x0, x1, r_in, r_out, mat, sides=12, n=48, bevel=.05):
    """Annulus along local X: a faceted outer collar around a round bore
    (after sw_engines.ring); sides=4 gives a square plate with a bore."""
    v = []
    for x in (x0, x1):
        for rr, s in ((r_out, sides), (r_in, None)):
            for k in range(n):
                a = k*math.tau/n
                q = _flat(rr, a, s)
                v.append((x, q*math.cos(a), q*math.sin(a)))
    f = []
    for k in range(n):
        j = (k+1) % n
        f += [(k, j, 2*n+j, 2*n+k), (n+j, n+k, 3*n+k, 3*n+j), (j, k, n+k, n+j), (2*n+k, 2*n+j, 3*n+j, 3*n+k)]
    ob = kit.mesh(name, v, f, mat, bevel)
    bm = bmesh.new()
    bm.from_mesh(ob.data)
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    bm.to_mesh(ob.data)
    bm.free()
    return ob


def radial_box(kit, name, x, ang, r0, r1, length, width, mat, bevel=.04):
    """Box standing radially off the local X axis from r0 to r1 at angle ang
    (degrees, from local +Y towards +Z). After sw_engines.radial_box."""
    a = math.radians(ang)
    rc = (r0+r1)/2
    ob = kit.box(name, (x, rc*math.cos(a), rc*math.sin(a)), (length, width, r1-r0), mat, bevel)
    ob.rotation_euler[0] = math.atan2(-math.cos(a), math.sin(a))
    return ob


def hex_bolts(kit, name, centres, radius, height, mat, axis='X'):
    """Hex bolt heads as one object; each centre is the head's base, the head
    stands along +axis (or -axis with a negative height)."""
    verts, faces = [], []
    for c in centres:
        b = len(verts)
        for h in (0, height):
            for k in range(6):
                a = k*math.tau/6
                p, q = radius*math.cos(a), radius*math.sin(a)
                if axis == 'X':
                    verts.append((c[0]+h, c[1]+p, c[2]+q))
                else:
                    verts.append((c[0]+p, c[1]+q, c[2]+h))
        faces.append(tuple(b+k for k in range(6)))
        faces.append(tuple(b+6+k for k in reversed(range(6))))
        faces += [(b+k, b+(k+1) % 6, b+6+(k+1) % 6, b+6+k) for k in range(6)]
    ob = kit.mesh(name, verts, faces, mat)
    bm = bmesh.new()
    bm.from_mesh(ob.data)
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    bm.to_mesh(ob.data)
    bm.free()
    return ob


def _bell(r, L):
    """Interior radius at depth u behind the mouth: flat at the lip, steepening
    into the throat (a short parabolic bell)."""
    rt = THROAT_R*r
    return lambda u: r-(r-rt)*(min(u, L)/L)**2


def _mats():
    m = bpy.data.materials
    liner = m.get('Drive bell liner') or hm_kit.material('Drive bell liner', (.085, .09, .095), .75, .42)
    rod = m.get('Actuator rod chrome') or hm_kit.material('Actuator rod chrome', (.5, .52, .53), .9, .22)
    frame = m.get('Drive frame plate') or hm_kit.material('Drive frame plate', (.27, .28, .30), .5, .5)
    return liner, rod, frame


def nozzle(kit, i, r, L, ui, lip_minor, band_u, mats):
    """Bell, liner, throat, lip and heat-shield band, in the local frame.
    Returns the bell's interior-radius function."""
    liner, rod, frame = mats
    rho = _bell(r, L)
    rt, t = THROAT_R*r, WALL*r
    # Liner: four film-cooling bands, each joint a backward-facing step.
    bands, step = 4, .03*r
    joints = [L*j/bands for j in range(bands+1)]
    prof = []
    for j, (a0, a1) in enumerate(zip(joints, joints[1:])):
        for q in range(4):
            a = a0+(a1-a0)*q/3
            inset = 0 if j == 0 else step*(a1-a)/(a1-a0)
            prof.append((-a, rho(a)-inset))
    prof += [(-(L+(ui-L)*.5), rt*1.04), (-ui, rt*1.1)]
    revolve(kit, f'Engine {i} aft-facing bell', prof, liner, inward=True)
    kit.cylinder(f'Engine {i} dark throat', (-ui, 0, 0), rt*1.12, .06, kit.dark, 'X', 36, 0)
    kit.cylinder(f'Engine {i} injector pintle', (-ui+.08, 0, 0), .07*r, .16, kit.steel, 'X', 12, .02)
    revolve(kit, f'Engine {i} throat ring', [(-(L-.06*r), rho(L-.06*r)-.01), (-(L-.02*r), rt*.95),
                                             (-(L+.05*r), rt*.95), (-(L+.05*r), rt*1.06),
                                             (-(L-.06*r), rho(L-.06*r)+.02)], kit.steel, 36, True)
    skin = [(-a, rho(a)+t) for a in [ui*q/12 for q in range(13)]]
    revolve(kit, f'Engine {i} bell outer skin', skin, kit.steel, 36)
    bpy.ops.mesh.primitive_torus_add(major_radius=r+t/2, minor_radius=lip_minor, major_segments=48,
                                     minor_segments=8, location=(0, 0, 0), rotation=(0, math.pi/2, 0))
    kit.finish(bpy.context.object, f'Engine {i} mouth lip', kit.steel)
    # Heat-shield band: ribbed refractory wrap on the exposed bell exterior.
    b0, b1 = band_u
    sk = lambda u: rho(u)+t
    ribs = 3
    outer = [(-b0, sk(b0)), (-(b0+.03), sk(b0)+.08)]
    for k in range(ribs):
        c = b0+(b1-b0)*(k+.5)/ribs
        w = (b1-b0)/ribs*.28
        outer += [(-(c-w), sk(c-w)+.08), (-(c-w*.6), sk(c)+.17), (-(c+w*.6), sk(c)+.17), (-(c+w), sk(c+w)+.08)]
    outer += [(-(b1-.03), sk(b1)+.08), (-b1, sk(b1))]
    inner = [(x, max(sk(-x)-.02, .1)) for x, _ in reversed(outer)]
    revolve(kit, f'Engine {i} heat-shield band', outer+inner[1:-1], kit.dark, 36, True)
    return rho


def main_drive(kit, i, r, mats):
    liner, rod, frame = mats
    rho = nozzle(kit, i, r, M_THROAT_U, M_INJECT_U, .15, (.1, .5), mats)
    sk = lambda u: rho(u)+WALL*r
    # Gimbal ring hugging the bell behind the heat band, with side trunnions.
    g0, g1 = .55, .85
    ring_out = 3.95
    ring(kit, f'Engine {i} gimbal ring', -g0, -g1, sk(g0)+.03, ring_out, kit.steel)
    for ang in (0, 180):
        radial_box(kit, f'Engine {i} gimbal trunnion', -(g0+g1)/2, ang, ring_out-.15, 4.4, g1-g0+.06, .8, kit.steel, .05)
        a = math.radians(ang)
        kit.cylinder(f'Engine {i} trunnion pin cap', (-g0+.05, 4.15*math.cos(a), 4.15*math.sin(a)),
                     .22, .1, rod, 'X', 12, .02)
    # Shroud can: a twelve-sided can with a chamfered aft rim, emerging from
    # the thrust plate and running forward into the block.
    can_a = 4.4
    revolve(kit, f'Engine {i} drive can', [(-g1, 3.7), (-g1, 3.95), (-1.1, can_a), (-3.0, can_a), (-3.0, 3.7)],
            kit.steel, 48, True, sides=12, bevel=.04)
    # Dark annulus between the bell skin and the can bore.
    revolve(kit, f'Engine {i} shroud collar', [(-(g1+.01), sk(g1)+.02), (-(g1+.01), 3.72),
                                              (-M_PLATE_U, 3.72), (-M_PLATE_U, sk(M_PLATE_U)+.02)],
            kit.dark, 36, True)
    ring(kit, f'Engine {i} shroud band', -1.2, -1.42, can_a-.1, can_a+.18, kit.steel)
    # Flange ring bolted against the thrust plate, with twelve bolt heads.
    f0 = 1.58
    ring(kit, f'Engine {i} mounting flange', -f0, -M_PLATE_U-.02, can_a-.1, 4.85, frame)
    hex_bolts(kit, f'Engine {i} flange bolts',
              [(-f0, 4.63*math.cos(math.radians(15+30*k)), 4.63*math.sin(math.radians(15+30*k))) for k in range(12)],
              .17, .12, kit.steel)
    # Pylon gussets above and below, tying the can into the plate.
    th = .7
    for s, label in ((1, 'dorsal'), (-1, 'ventral')):
        pts = [(-.95, can_a-.05), (-M_PLATE_U-.05, can_a-.05), (-M_PLATE_U-.05, 5.75), (-1.55, 5.75)]
        v = [(x, yy, s*rr) for yy in (-th/2, th/2) for x, rr in pts]
        f = [(0, 1, 2, 3), (7, 6, 5, 4), (0, 4, 5, 1), (1, 5, 6, 2), (2, 6, 7, 3), (3, 7, 4, 0)]
        ob = kit.mesh(f'Engine {i} {label} pylon gusset', v, f, frame, .04)
        bm = bmesh.new()
        bm.from_mesh(ob.data)
        bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
        bm.to_mesh(ob.data)
        bm.free()
    # Two dorsal actuators along can flats, clevis on the flange ring, rod
    # end on a lever ear of the gimbal ring.
    rr_ = .27
    R = can_a+.05+rr_
    for ang in RAM_ANGLES:
        a = math.radians(ang)
        py, pz = R*math.cos(a), R*math.sin(a)
        radial_box(kit, f'Engine {i} gimbal lever ear', -(g0+g1)/2, ang, ring_out-.2, R+.25, g1-g0, .55, kit.steel)
        radial_box(kit, f'Engine {i} actuator clevis bracket', -1.48, ang, can_a-.1, R+.32, .22, .7, kit.steel)
        kit.cylinder(f'Engine {i} gimbal actuator', (-1.18, py, pz), rr_, .44, kit.dark, 'X', 16, .04)
        kit.cylinder(f'Engine {i} actuator gland', (-.93, py, pz), rr_*1.2, .1, kit.steel, 'X', 16, .02)
        kit.cylinder(f'Engine {i} actuator rod', (-.79, py, pz), rr_*.45, .3, rod, 'X', 12, .02)


def maneuvering_jet(kit, i, r, mats):
    liner, rod, frame = mats
    rho = nozzle(kit, i, r, J_THROAT_U, J_INJECT_U, .12, (.08, .55), mats)
    sk = lambda u: rho(u)+WALL*r
    collar_out = r+.4
    ring(kit, f'Engine {i} jet swivel collar', -.68, -J_FACE_U-.02, sk(.68)+.02, collar_out, kit.steel)
    # Square bolted face plate on the housing's aft face, its bore filled by the collar.
    ring(kit, f'Engine {i} jet face plate', -1.3, -J_FACE_U-.02, collar_out-.08, 2.75, frame, sides=4, n=64)
    hex_bolts(kit, f'Engine {i} jet face bolts', [(-1.3, sy*2.3, sz*2.3) for sy in (-1, 1) for sz in (-1, 1)],
              .17, .12, kit.steel)
    # Compact housing running forward into the wing body / aft block junction.
    h0, h1 = J_FACE_U, 5.0
    kit.box(f'Engine {i} jet housing', (-(h0+h1)/2, 0, 0), (h1-h0, 6.0, 5.4), kit.steel, .25)
    # Mounting lugs above and below the face plate, bolted aft, bearing on
    # the pocket roof and floor cut into the wing body / aft block junction.
    lugs = [(-1.5, -1.05, sy-.45, sy+.45, *((2.2, 3.3) if sz > 0 else (-3.3, -2.2))) for sy in (-1.8, 1.8) for sz in (-1, 1)]
    hm_kit.blocks(kit, f'Engine {i} jet mounting lugs', lugs, frame, .03)
    hex_bolts(kit, f'Engine {i} jet lug bolts', [(-1.05, sy, sz*2.55) for sy in (-1.8, 1.8) for sz in (-1, 1)],
              .13, .1, kit.steel)


def jet_pocket(kit, M):
    """Cut the jet's pocket out of the wing body / aft block junction so the
    bell and its housing face stand clear in a recess (local frame M)."""
    cutter = kit.box('Temporary jet pocket cutter', ((4.0-J_POCKET_U)/2, 0, 0), (4.0+J_POCKET_U, 6.7, 6.4), kit.dark, 0)
    cutter.matrix_basis = M @ cutter.matrix_basis
    names = ('Aft drive block', 'Wing body')
    for k, n in enumerate(names):
        hm_kit.boolean(bpy.data.objects[n], cutter, 'DIFFERENCE', keep=k < len(names)-1)


def plate(kit, mats):
    """Common thrust plate over the stern face carrying the three drives."""
    liner, rod, frame = mats
    hy, hz, c = PLATE_HALF_Y, PLATE_HALF_Z, PLATE_CORNER
    outline = [(hy, hz-c), (hy-c, hz), (-hy+c, hz), (-hy, hz-c), (-hy, -hz+c), (-hy+c, -hz), (hy-c, -hz), (hy, -hz+c)]
    x_aft = -39+M_PLATE_U
    v = [(x, y, z) for x in (PLATE_FWD_X, x_aft) for y, z in outline]
    f = [tuple(range(8)), tuple(reversed(range(8, 16)))] + [(k, (k+1) % 8, 8+(k+1) % 8, 8+k) for k in range(8)]
    ob = kit.mesh('Engines stern thrust plate', v, f, frame, .1)
    bm = bmesh.new()
    bm.from_mesh(ob.data)
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    bm.to_mesh(ob.data)
    bm.free()
    # Perimeter bolt rows, clear of the pylons and flanges.
    pts = []
    for z in (hz-.6, -hz+.6):
        for k in range(-7, 8):
            y = 2.0*k
            if all(abs(y-yc) > .9 for yc in (-10, 0, 10)) and abs(y) < hy-c+.4:
                pts.append((x_aft, y, z))
    for y in (hy-.55, -hy+.55):
        for z in (-3.6, -1.2, 1.2, 3.6):
            pts.append((x_aft, y, z))
    hex_bolts(kit, 'Engines stern plate bolts', pts, .17, -.12, kit.steel)


def build_drive(kit, i, x, y, angle, width, mats):
    r = bell_radius(i, width)
    before = set(o.name for o in bpy.data.objects)
    if i <= 3:
        main_drive(kit, i, r, mats)
    else:
        maneuvering_jet(kit, i, r, mats)
    # Local frame: local +X is the exhaust, the mouth plane at the origin.
    M = Matrix.Translation((x, y, z_of(i))) @ Matrix.Rotation(math.radians(angle), 4, 'Z')
    for o in bpy.data.objects:
        if o.name not in before:
            o.matrix_basis = M @ o.matrix_basis
    if i > 3:
        jet_pocket(kit, M)
    a = math.radians(angle)
    d = Vector((math.cos(a), math.sin(a), 0))
    an = bpy.data.objects.new(f'Engine {i} exhaust anchor', None)
    an.empty_display_type = 'SINGLE_ARROW'
    an.empty_display_size = r
    an.location = (x, y, z_of(i))
    an.rotation_euler = d.to_track_quat('Z', 'Y').to_euler()
    bpy.context.collection.objects.link(an)
    an['engine'] = i
    an['radius'] = r
    inject = M_INJECT_U if i <= 3 else J_INJECT_U
    an['throat_depth'] = inject
    an['throat_radius'] = THROAT_R*r*1.12


def build(kit):
    mats = _mats()
    plate(kit, mats)
    for i, (x, y, angle, width) in enumerate(ENGINES, 1):
        build_drive(kit, i, x, y, angle, width, mats)
