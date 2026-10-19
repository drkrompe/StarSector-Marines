"""Kite (P) drive units, exactly at kite.ship's eight engine slots.

The sprite shows four kinds of drive, each a whole unit rather than a nozzle
in the hull:

- Engines 2-3, the main drives (X -27, Y +-13, width 10): two long pods
  either side of the tail cone, running forward under the aft hump;
- Engines 1 and 4 (X -21, Y +-28, width 8): nacelles slung through the wing's
  trailing edge where it steps in round them;
- Engines 7-8 (X -22, Y -20 / +21, width 6, angles 185 / 175): small cans in
  the tail between the main pods and the nacelles, toed a few degrees out;
- Engines 5-6 (X -16, Y -36 / +36, width 6, angles 210 / 150): the
  maneuvering jets (system maneuveringjets) at the wing tips' trailing
  corners, canted thirty degrees outboard.

Pass 2 (plausible detailing). A Midline civilian shuttle that pirates have
kept flying with whatever was to hand, so the drives read as segmented
industrial cans rather than smooth pods:

- every unit is a twelve-sided can in segments: an aft heat-shield section
  (the sprite's dark/light banding on the main pods), bolted flange joints,
  and a forward section that runs into the hull;
- the bell stands a short skirt aft of the can, inside a gimbal ring with
  side trunnions; the main drives and nacelles carry hydraulic gimbal
  actuators on their dorsal crown (and the nacelles one ventral);
- exposed fuel and coolant lines run along the crowns in clamps to valve
  blocks at the aft flange;
- the pirate refit: crude welded armour collars on the outboard flats and
  bash plates on the ventral crowns, uneven, with weld beads and bolts;
- the maneuvering jets are compact canted thrusters: a square bolted face
  plate, swivel collar, housing can and a valve block with its feed line.

The bell is a banded liner ending at a throat ring and a dark injector face;
the hull is bored out of every bell (and recessed round the exposed skirt),
so no hull plate shows inside a drive.

Every unit is built in a local frame (exhaust along local +X, mouth plane at
the origin), then turned to its slot angle and moved to its slot, so the
mouth stays exactly at the slot.

Names follow the Second Wind's: 'Engine {i} aft-facing bell', 'Engine {i}
mouth lip', 'Engine {i} dark throat', 'Engine {i} drive can', plus an
'Engine {i} exhaust anchor' empty at the mouth pointing along the exhaust
(its local +Z). Further parts are 'Engine {i} <part>'.
"""
import math
import random
import bmesh
import bpy
from mathutils import Matrix, Vector

import kp_kit

# (x, y, angle, width) from kite.ship, in slot order.
ENGINES = [(-21, 28, 180, 8), (-27, 13, 180, 10), (-27, -13, 180, 10), (-21, -28, 180, 8),
           (-16, -36, 210, 6), (-16, 36, 150, 6), (-22, -20, 185, 6), (-22, 21, 175, 6)]
ROLES = ['port wing nacelle', 'port main drive', 'starboard main drive', 'starboard wing nacelle',
         'starboard maneuvering jet', 'port maneuvering jet', 'starboard tail drive', 'port tail drive']
Z = {1: -1.6, 2: -.5, 3: -.5, 4: -1.6, 5: -1.8, 6: -1.8, 7: -1.0, 8: -1.0}
KIND = {1: 'nacelle', 2: 'main', 3: 'main', 4: 'nacelle', 5: 'jet', 6: 'jet', 7: 'tail', 8: 'tail'}
# Can length forward of the mouth, in units: how far each unit runs into the hull.
CAN_LENGTH = {1: 12.0, 2: 15.0, 3: 15.0, 4: 12.0, 5: 6.0, 6: 6.0, 7: 7.0, 8: 7.0}
# Exposed bell skirt aft of the can's rim, by kind.
SKIRT = {'main': 1.3, 'nacelle': 1.05, 'tail': .8, 'jet': .7}
THROAT_R = .6
WALL = .045                # bell wall over mouth radius


def bell_radius(i, width):
    """Mouth radius: the slot width read as the bell's mouth diameter, less a lip."""
    return width*.45


def z_of(i):
    return Z[i]


def bell_depth(r):
    return 1.5*r


def throat_depth(r):
    return bell_depth(r)+.3


# --- local-frame helpers (axis = local X, upstream = -X) --------------------

def _flat(r, a, sides):
    if not sides:
        return r
    s = math.tau/sides
    return r/math.cos(((a+s/2) % s)-s/2)


def _normals(ob):
    bm = bmesh.new()
    bm.from_mesh(ob.data)
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    bm.to_mesh(ob.data)
    bm.free()
    return ob


def revolve(kit, name, prof, mat, n=36, closed=False, inward=False, sides=None, bevel=0):
    """Surface of revolution about local X through an (x, radius) profile
    (copied from hm_engines.revolve)."""
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
    """Annulus along local X: a faceted outer collar round a round bore
    (copied from hm_engines.ring); sides=4 gives a square plate with a bore."""
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
    return _normals(kit.mesh(name, v, f, mat, bevel))


def hex_bolts(kit, name, centres, radius, height, mat):
    """Hex bolt heads as one object, standing from each centre along local
    +X (or -X with a negative height). Copied from hm_engines.hex_bolts."""
    verts, faces = [], []
    for c in centres:
        b = len(verts)
        for h in (0, height):
            for k in range(6):
                a = k*math.tau/6
                verts.append((c[0]+h, c[1]+radius*math.cos(a), c[2]+radius*math.sin(a)))
        faces.append(tuple(b+k for k in range(6)))
        faces.append(tuple(b+6+k for k in reversed(range(6))))
        faces += [(b+k, b+(k+1) % 6, b+6+(k+1) % 6, b+6+k) for k in range(6)]
    return _normals(kit.mesh(name, verts, faces, mat))


def radial_bolts(kit, name, spots, radius, height, mat):
    """Hex bolt heads standing radially off the can: spots are (u, angle deg, r)."""
    verts, faces = [], []
    for u, ang, r0 in spots:
        a = math.radians(ang)
        n = Vector((0, math.cos(a), math.sin(a)))
        t = Vector((0, -math.sin(a), math.cos(a)))
        x = Vector((1, 0, 0))
        c = Vector((-u, 0, 0))+n*r0
        b = len(verts)
        for h in (0, height):
            for k in range(6):
                q = k*math.tau/6
                verts.append(tuple(c+n*h+x*(radius*math.cos(q))+t*(radius*math.sin(q))))
        faces.append(tuple(b+k for k in range(6)))
        faces.append(tuple(b+6+k for k in reversed(range(6))))
        faces += [(b+k, b+(k+1) % 6, b+6+(k+1) % 6, b+6+k) for k in range(6)]
    return _normals(kit.mesh(name, verts, faces, mat))


def tangent_blocks(kit, name, specs, mat, bevel=0):
    """Plates and blocks lying on the can, as one object. Each spec is
    (u0, u1, angle deg, r0, r1, t0, t1, skew): axial span u0..u1 behind the
    mouth, radial r0..r1 along the angle's normal, tangential t0..t1, and a
    tangential drift per unit of axial length (a crooked weld-on)."""
    verts, faces = [], []
    for u0, u1, ang, r0, r1, t0, t1, skew in specs:
        a = math.radians(ang)
        n = (math.cos(a), math.sin(a))
        tv = (-math.sin(a), math.cos(a))
        b = len(verts)
        for rr in (r0, r1):
            for tt in (t0, t1):
                for u in (u0, u1):
                    d = tt+(u-(u0+u1)/2)*skew
                    verts.append((-u, n[0]*rr+tv[0]*d, n[1]*rr+tv[1]*d))
        # vertex index = ((ri*2)+ti)*2+ui
        faces += [(b, b+1, b+3, b+2), (b+4, b+6, b+7, b+5), (b, b+4, b+5, b+1),
                  (b+2, b+3, b+7, b+6), (b, b+2, b+6, b+4), (b+1, b+5, b+7, b+3)]
    return _normals(kit.mesh(name, verts, faces, mat, bevel))


def pipe(kit, name, pts, rad, mat, sides=8):
    """A tube through a polyline of local points (mitred joints, capped ends)."""
    P = [Vector(p) for p in pts]
    verts, faces = [], []
    ref = Vector((1, 0, 0))
    for k, p in enumerate(P):
        d0 = (P[k]-P[k-1]).normalized() if k else None
        d1 = (P[k+1]-P[k]).normalized() if k < len(P)-1 else None
        d = ((d0 or d1)+(d1 or d0)).normalized()
        if abs(d.dot(ref)) > .9:
            ref = Vector((0, 0, 1))
        e1 = d.cross(ref).normalized()
        e2 = d.cross(e1).normalized()
        scale = 1.0
        if d0 is not None and d1 is not None:
            scale = 1/max(.5, math.cos(d0.angle(d1)/2))
        for s in range(sides):
            q = s*math.tau/sides
            verts.append(tuple(p+(e1*math.cos(q)+e2*math.sin(q))*rad*scale))
    for k in range(len(P)-1):
        for s in range(sides):
            faces.append((k*sides+s, k*sides+(s+1) % sides, (k+1)*sides+(s+1) % sides, (k+1)*sides+s))
    faces.append(tuple(range(sides)))
    faces.append(tuple(range((len(P)-1)*sides, len(P)*sides)))
    return _normals(kit.mesh(name, verts, faces, mat))


def polar(u, ang, rr):
    a = math.radians(ang)
    return (-u, rr*math.cos(a), rr*math.sin(a))


def _mats():
    m = bpy.data.materials
    liner = m.get('Drive bell liner') or kp_kit.material('Drive bell liner', (.085, .09, .095), .75, .42)
    rod = m.get('Actuator rod chrome') or kp_kit.material('Actuator rod chrome', (.5, .52, .53), .9, .22)
    frame = m.get('Drive frame plate') or kp_kit.material('Drive frame plate', (.27, .28, .30), .5, .5)
    # The sprite's light drive bands: worn, heat-tinted refractory wrap.
    shield = m.get('Drive heat-shield wrap') or kp_kit.material('Drive heat-shield wrap', (.34, .30, .26), .35, .62)
    # Pirate armour: rough, mismatched plate welded on.
    bash = m.get('Pirate bash plate') or kp_kit.material('Pirate bash plate', (.23, .21, .19), .55, .7)
    return dict(liner=liner, rod=rod, frame=frame, shield=shield, bash=bash)


# --- parts --------------------------------------------------------------------

def nozzle(kit, i, r, e, mats, lip_minor):
    """Banded bell liner, throat ring, dark throat and pintle, outer skin,
    mouth lip, and a ribbed heat band on the exposed skirt (0..e)."""
    L, ui = bell_depth(r), throat_depth(r)
    rt, t = THROAT_R*r, WALL*r
    rho = lambda u: r-(r-rt)*(min(u, L)/L)**2
    bands, step = 4, .03*r
    joints = [L*j/bands for j in range(bands+1)]
    prof = []
    for j, (a0, a1) in enumerate(zip(joints, joints[1:])):
        for q in range(4):
            a = a0+(a1-a0)*q/3
            inset = 0 if j == 0 else step*(a1-a)/(a1-a0)
            prof.append((-a, rho(a)-inset))
    prof += [(-(L+.15), rt*1.04), (-ui, rt*1.1)]
    revolve(kit, f'Engine {i} aft-facing bell', prof, mats['liner'], inward=True)
    kit.cylinder(f'Engine {i} dark throat', (-ui, 0, 0), rt*1.12, .08, kit.dark, 'X', 36, 0)
    kit.cylinder(f'Engine {i} injector pintle', (-ui+.1, 0, 0), .08*r, .2, kit.steel, 'X', 12, .02)
    revolve(kit, f'Engine {i} throat ring', [(-(L-.06*r), rho(L-.06*r)-.01), (-(L-.02*r), rt*.95),
                                             (-(L+.05*r), rt*.95), (-(L+.05*r), rt*1.06),
                                             (-(L-.06*r), rho(L-.06*r)+.02)], kit.steel, 36, True)
    skin = [(-a, rho(a)+t) for a in [ui*q/12 for q in range(13)]]
    revolve(kit, f'Engine {i} bell outer skin', skin, kit.steel, 36)
    bpy.ops.mesh.primitive_torus_add(major_radius=r+t/2, minor_radius=lip_minor, major_segments=48,
                                     minor_segments=8, location=(0, 0, 0), rotation=(0, math.pi/2, 0))
    kit.finish(bpy.context.object, f'Engine {i} mouth lip', kit.steel)
    # Ribbed heat band on the exposed skirt, clear of the lip and the gimbal ring.
    sk = lambda u: rho(u)+t
    b0, b1 = lip_minor+.12, e-.5
    if b1-b0 > .3:
        ribs = 2 if b1-b0 < .7 else 3
        h = .06+.02*r
        outer = [(-b0, sk(b0)), (-(b0+.03), sk(b0)+h*.5)]
        for k in range(ribs):
            c = b0+(b1-b0)*(k+.5)/ribs
            w = (b1-b0)/ribs*.3
            outer += [(-(c-w), sk(c-w)+h*.5), (-(c-w*.6), sk(c)+h), (-(c+w*.6), sk(c)+h), (-(c+w), sk(c+w)+h*.5)]
        outer += [(-(b1-.03), sk(b1)+h*.5), (-b1, sk(b1))]
        inner = [(x, max(sk(-x)-.02, .1)) for x, _ in reversed(outer)]
        revolve(kit, f'Engine {i} heat-shield band', outer+inner[1:-1], kit.dark, 36, True)
    return sk


def gimbal(kit, i, r, e, sk, can_r, mats, actuators):
    """Gimbal ring just aft of the can rim, side trunnions, and hydraulic
    actuators from clevis brackets on the can to lever ears on the ring."""
    g0, g1 = e-.45, e-.05
    ring_out = can_r-.3
    ring(kit, f'Engine {i} gimbal ring', -g0, -g1, sk(g0)+.03, ring_out, kit.steel, bevel=.04)
    tangent_blocks(kit, f'Engine {i} gimbal trunnions',
                   [(g0-.05, g1+.15, ang, ring_out-.25, can_r-.02, -.35*r/4.5-.15, .35*r/4.5+.15, 0) for ang in (0, 180)],
                   kit.steel, .04)
    kit_r = .22*r/4.5+.1           # actuator barrel radius
    R = can_r+.08+kit_r
    for k, ang in enumerate(actuators, 1):
        a = math.radians(ang)
        py, pz = R*math.cos(a), R*math.sin(a)
        # lever ear on the ring, clevis on the can's aft segment
        tangent_blocks(kit, f'Engine {i} gimbal lever ear {k}',
                       [(g0, g1, ang, ring_out-.25, R+kit_r*.9, -.28, .28, 0)], kit.steel, .03)
        c_u = e+.47*r
        tangent_blocks(kit, f'Engine {i} actuator clevis {k}',
                       [(c_u, c_u+.35, ang, can_r-.1, R+kit_r*1.1, -.33, .33, 0)], mats['frame'], .03)
        body0, body1 = e+.55, c_u
        kit.cylinder(f'Engine {i} gimbal actuator {k}', (-(body0+body1)/2, py, pz), kit_r, body1-body0,
                     kit.dark, 'X', 12, .03)
        kit.cylinder(f'Engine {i} actuator gland {k}', (-(body0+.06), py, pz), kit_r*1.25, .14,
                     kit.steel, 'X', 12, .02)
        kit.cylinder(f'Engine {i} actuator rod {k}', (-(g1+body0)/2, py, pz), kit_r*.45, body0-g1+.05,
                     mats['rod'], 'X', 10, .01)


def flange(kit, i, k, u, can_r, mats, bolts=12, proud=.28, width=.38):
    """Bolted flange joint between can segments; bolt heads on its aft face."""
    ring(kit, f'Engine {i} can flange {k}', -u, -(u+width), can_r-.4, can_r+proud, mats['frame'], bevel=.04)
    rb = can_r+proud*.45
    hex_bolts(kit, f'Engine {i} flange {k} bolts',
              [(-u, rb*math.cos(math.radians(15+360/bolts*j)), rb*math.sin(math.radians(15+360/bolts*j)))
               for j in range(bolts)], .1+.02*can_r/5, .1, kit.steel)


def bands(kit, i, u0, u1, n, can_r, mats):
    """The sprite's dark/light banding: proud heat-shield wraps on the aft
    segment, the can's gunmetal showing dark between them."""
    pitch = (u1-u0)/n
    for k in range(n):
        a = u0+pitch*k+pitch*.14
        b = u0+pitch*(k+1)-pitch*.14
        ring(kit, f'Engine {i} heat-shield wrap {k+1}', -a, -b, can_r-.3, can_r+.1, mats['shield'], bevel=.03)


def fuel_lines(kit, i, lines, u_aft, u_fwd, can_r, proud, mats):
    """Exposed fuel/coolant lines along the can: each runs from inside the
    hull (u_fwd) aft above the flanges, then drops into a valve block on the
    aft segment. `lines` are (angle deg, pipe radius)."""
    for k, (ang, pr) in enumerate(lines, 1):
        rl = can_r+proud+pr+.08
        pts = [polar(u_fwd, ang, rl), polar(u_aft+.9, ang, rl), polar(u_aft+.45, ang, can_r+.35)]
        pipe(kit, f'Engine {i} feed line {k}', pts, pr, kit.dark if k % 2 else kit.steel)
        tangent_blocks(kit, f'Engine {i} feed valve {k}',
                       [(u_aft+.05, u_aft+.6, ang, can_r-.05, can_r+.55, -.38, .38, 0),
                        (u_aft+.18, u_aft+.47, ang, can_r+.55, can_r+.72, -.22, .22, 0)], mats['frame'], .03)
        clamps = []
        u = u_aft+1.6
        while u < u_fwd-.6:
            clamps.append((u, u+.22, ang, can_r-.05, rl+pr+.06, -pr-.1, pr+.1, 0))
            u += 2.1
        if clamps:
            tangent_blocks(kit, f'Engine {i} feed clamps {k}', clamps, kit.steel, .02)


def armour_collar(kit, i, rng, u0, u1, angles, can_r, flat, mats):
    """The pirate refit: uneven plates welded onto the can's flats, each
    a little crooked, with weld beads down its long edges and a few bolts."""
    plates, beads, bolts = [], [], []
    for ang in angles:
        a0 = u0+rng.uniform(0, .5)
        a1 = u1-rng.uniform(0, .7)
        th = rng.uniform(.18, .3)
        hw = flat/2*rng.uniform(.85, 1.05)
        sk = rng.uniform(-.06, .06)
        r0 = can_r+.02
        plates.append((a0, a1, ang, r0, r0+th, -hw, hw, sk))
        for s in (-1, 1):
            t = s*hw
            beads.append((a0+.1, a1-.1, ang, r0-.02, r0+th*.55, min(t, t+s*.1), max(t, t+s*.1), sk))
        for u in (a0+.35, a1-.35):
            bolts.append((u, ang+rng.uniform(-6, 6), r0+th))
    tangent_blocks(kit, f'Engine {i} armour collar', plates, mats['bash'], .03)
    tangent_blocks(kit, f'Engine {i} armour weld beads', beads, kit.dark)
    radial_bolts(kit, f'Engine {i} armour collar bolts', bolts, .1+.015*can_r, .08, kit.steel)


def bash_plate(kit, i, rng, u0, u1, ang, can_r, half_w, mats):
    """A crude bash plate welded under the can: a flat skid with a raked
    leading end, on two weld straps, bolted."""
    r0 = can_r+.33              # clear of the flanges it crosses
    th = .3
    ra = math.radians(ang)
    n = Vector((0, math.cos(ra), math.sin(ra)))
    tv = Vector((0, -math.sin(ra), math.cos(ra)))
    x = Vector((-1, 0, 0))
    rake = 1.2
    sec = [(u0, 0), (u1-rake, 0), (u1, -th*1.8)]   # (u, extra radial lift) along the plate
    verts = []
    for s in (-1, 1):
        for rr in (r0, r0+th):
            for u, lift in sec:
                verts.append(tuple(x*u+n*(rr-lift)+tv*(s*half_w)))
    # 12 verts: s(2) x rr(2) x sec(3)
    def idx(si, ri, k):
        return (si*2+ri)*3+k
    faces = []
    for k in range(2):
        faces += [(idx(0, 0, k), idx(0, 0, k+1), idx(1, 0, k+1), idx(1, 0, k)),
                  (idx(0, 1, k), idx(1, 1, k), idx(1, 1, k+1), idx(0, 1, k+1)),
                  (idx(0, 0, k), idx(0, 1, k), idx(0, 1, k+1), idx(0, 0, k+1)),
                  (idx(1, 0, k), idx(1, 0, k+1), idx(1, 1, k+1), idx(1, 1, k))]
    faces += [(idx(0, 0, 0), idx(1, 0, 0), idx(1, 1, 0), idx(0, 1, 0)),
              (idx(0, 0, 2), idx(0, 1, 2), idx(1, 1, 2), idx(1, 0, 2))]
    _normals(kit.mesh(f'Engine {i} bash plate', verts, faces, mats['bash'], .05))
    straps = [(u, u+.5, ang, can_r-.1, r0+.02, -half_w*.8, half_w*.8, 0)
              for u in (u0+.6, (u0+u1-rake)/2)]
    tangent_blocks(kit, f'Engine {i} bash plate straps', straps, kit.dark, .02)
    spots = []
    for u in (u0+.4, (u0+u1-rake)/2+.25, u1-rake-.3):
        for s in (-1, 1):
            q = math.degrees(math.atan2(s*half_w*.75, r0+th))
            spots.append((u+rng.uniform(-.1, .1), ang+q, math.hypot(r0+th, half_w*.75)))
    radial_bolts(kit, f'Engine {i} bash plate bolts', spots, .13, .09, kit.steel)


def can(kit, i, r, e, length, can_r, sk, step=None, neck=None):
    """The can: twelve flats, a chamfered aft rim at the skirt, running
    forward into the hull; its bore follows the bell skin. With `step`, the
    can steps down behind its first flange to a `neck` (the main drives,
    whose buried forward sections would otherwise crowd the tail cans)."""
    ui = throat_depth(r)
    outer = [(-e, can_r-.25), (-(e+.3), can_r)]
    if step:
        outer += [(-step, can_r), (-step, neck), (-length, neck)]
    else:
        outer += [(-length, can_r)]
    inner = [(-length, .5*r), (-ui, sk(ui)+.02)]
    if step:
        inner.append((-step, sk(step)+.02))
    inner.append((-e, sk(e)+.02))
    revolve(kit, f'Engine {i} drive can', outer+inner, kit.steel, 48, True, sides=12, bevel=.05)
    bore = sk(e)+.02
    revolve(kit, f'Engine {i} shroud collar', [(-(e-.02), sk(e)), (-(e-.02), bore+.15), (-(e+.6), bore+.15),
                                              (-(e+.6), sk(e+.6))], kit.dark, 36, True)


def outboard_angle(i):
    """Local polar angle (from local +Y towards +Z) that points outboard in
    the world: local +Y is world -Y at angle 180, +Y near 0."""
    x, y, angle, w = ENGINES[i-1]
    a = math.radians(angle)
    ly = Vector((-math.sin(a), math.cos(a)))      # local +Y in world plan
    return 0 if ly.y*y > 0 else 180


def drive(kit, i, r, mats):
    kind = KIND[i]
    e = SKIRT[kind]
    length = CAN_LENGTH[i]
    can_r = r*1.22
    flat = 2*can_r*math.tan(math.pi/12)
    rng = random.Random(1000+i)
    sk = nozzle(kit, i, r, e, mats, .14*r/4.5+.06)
    neck = r*.91 if kind == 'main' else can_r
    can(kit, i, r, e, length, can_r, sk, *((e+3.0, neck) if kind == 'main' else ()))
    out = outboard_angle(i)
    down = 270
    # ventral-outboard flats, in polar angle, outboard side first
    lo_out = [(out+s*d) % 360 for s, d in ((1 if out == 180 else -1, 30), (1 if out == 180 else -1, 60))]
    if kind == 'main':
        # Only the aft ~5 units stand clear of the tail; the rest runs into it.
        bands(kit, i, e+.05, e+2.6, 2, can_r, mats)
        flange(kit, i, 1, e+2.65, can_r, mats)
        flange(kit, i, 2, e+7.6, neck, mats, proud=.2)
        gimbal(kit, i, r, e, sk, can_r, mats, (60, 120))
        fuel_lines(kit, i, [(90, .2), (35 if out == 0 else 145, .15)], e+3.15, length-.5, neck, .2, mats)
        armour_collar(kit, i, rng, e+.3, e+2.45, lo_out, can_r+.1, flat, mats)
        bash_plate(kit, i, rng, e+.15, e+2.95, down, can_r, flat*.75, mats)
    elif kind == 'nacelle':
        bands(kit, i, e+.05, e+2.2, 2, can_r, mats)
        flange(kit, i, 1, e+2.25, can_r, mats)
        flange(kit, i, 2, e+6.4, can_r, mats, bolts=12, proud=.22)
        gimbal(kit, i, r, e, sk, can_r, mats, (90, 270))
        fuel_lines(kit, i, [(60 if out == 0 else 120, .16)], e+2.7, length-.5, can_r, .22, mats)
        armour_collar(kit, i, rng, e+2.7, e+6.3, [(down-30) % 360, (down+30) % 360], can_r, flat, mats)
        bash_plate(kit, i, rng, e+6.7, length-.4, down, can_r, flat*.7, mats)
    elif kind == 'tail':
        bands(kit, i, e+.05, e+1.5, 1, can_r, mats)
        flange(kit, i, 1, e+1.55, can_r, mats, bolts=10, proud=.2, width=.3)
        ring(kit, f'Engine {i} gimbal ring', -(e-.45), -(e-.05), sk(e-.45)+.03, can_r-.25, kit.steel, bevel=.03)
        fuel_lines(kit, i, [(90, .13)], e+1.95, length-.4, can_r, .2, mats)
        bash_plate(kit, i, rng, e+2.0, length-.3, down, can_r, flat*.7, mats)
    else:
        maneuvering_jet(kit, i, r, e, sk, can_r, length, mats)


def maneuvering_jet(kit, i, r, e, sk, can_r, length, mats):
    """Compact canted thruster: swivel collar, square bolted face plate on
    the housing's aft face, a valve block and feed line on its crown."""
    ring(kit, f'Engine {i} jet swivel collar', -(e-.4), -(e+.05), sk(e-.4)+.02, can_r-.25, kit.steel, bevel=.03)
    hx = can_r-.15
    ring(kit, f'Engine {i} jet face plate', -(e+.05), -(e+.38), can_r-.5, hx, mats['frame'], sides=4, n=64, bevel=.04)
    c = hx*.82
    hex_bolts(kit, f'Engine {i} jet face bolts', [(-(e+.05), sy*c*.72, sz*c*.72) for sy in (-1, 1) for sz in (-1, 1)],
              .14, .1, kit.steel)
    flange(kit, i, 1, e+2.2, can_r, mats, bolts=8, proud=.18, width=.3)
    tangent_blocks(kit, f'Engine {i} jet valve block',
                   [(e+.8, e+1.9, 90, can_r-.05, can_r+.6, -.55, .55, 0),
                    (e+1.1, e+1.6, 90, can_r+.6, can_r+.82, -.3, .3, 0)], mats['frame'], .03)
    rl = can_r+.25+.14
    pipe(kit, f'Engine {i} feed line 1', [polar(length-.3, 90, rl), polar(e+2.6, 90, rl), polar(e+1.9, 90, can_r+.45)],
         .14, kit.dark)


def bore_hull(kit, i, r, M):
    """Cut the hull out of the bell (and a shallow recess round the exposed
    skirt and gimbal ring), so no hull plate passes through a drive."""
    e = SKIRT[KIND[i]]
    can_r = r*1.22/math.cos(math.pi/12)+.12
    ui = throat_depth(r)
    cutters = []
    kit.cylinder(f'Temporary engine {i} bell cutter', (-(ui+.25-1.0)/2, 0, 0), r+.12, ui+1.25, kit.dark, 'X', 36, 0)
    cutters.append(bpy.context.object)
    if KIND[i] == 'jet':
        # A square pocket in the wing tip's trailing corner, so the canted
        # face plate stands clear instead of behind a strip of wing.
        hx = r*1.22+.1
        kit.box(f'Temporary engine {i} pocket cutter', ((4.0-(e+.2))/2, 0, 0), (4.0+e+.2, 2*hx, 2*hx), kit.dark, 0)
    else:
        kit.cylinder(f'Temporary engine {i} skirt cutter', ((1.0-e)/2, 0, 0), can_r, e+1.0, kit.dark, 'X', 36, 0)
    cutters.append(bpy.context.object)
    for cb in cutters:
        cb.matrix_basis = M @ cb.matrix_basis
    bpy.context.view_layer.update()
    for cb in cutters:
        lo, hi = _bounds(cb)
        for ob in [o for o in bpy.data.objects if o.type == 'MESH' and o.get('module') not in (None, __name__)]:
            olo, ohi = _bounds(ob)
            if all(lo[k] < ohi[k] and hi[k] > olo[k] for k in range(3)):
                _carve(ob, cb)
        bpy.data.objects.remove(cb, do_unlink=True)


def _carve(ob, cutter):
    """Boolean-difference the cutter out of ob (after kp_kit.boolean), ahead
    of its chamfer. A part the manifold solver refuses (an open panel) is
    left as it was and reported, rather than failing the build."""
    bpy.context.view_layer.objects.active = ob
    mod = ob.modifiers.new('Engine bore', 'BOOLEAN')
    mod.operation, mod.object, mod.solver = 'DIFFERENCE', cutter, 'MANIFOLD'
    bpy.ops.object.modifier_move_to_index(modifier=mod.name, index=0)
    try:
        bpy.ops.object.modifier_apply(modifier=mod.name)
    except RuntimeError as err:
        print('ENGINE BORE skipped', ob.name, err)
        if mod.name in ob.modifiers:
            ob.modifiers.remove(mod)


def _bounds(ob):
    pts = [ob.matrix_world @ Vector(c) for c in ob.bound_box]
    return [min(p[k] for p in pts) for k in range(3)], [max(p[k] for p in pts) for k in range(3)]


def build_drive(kit, i, x, y, angle, width, mats):
    r = bell_radius(i, width)
    before = set(o.name for o in bpy.data.objects)
    drive(kit, i, r, mats)
    M = Matrix.Translation((x, y, z_of(i))) @ Matrix.Rotation(math.radians(angle), 4, 'Z')
    for o in bpy.data.objects:
        if o.name not in before:
            o.matrix_basis = M @ o.matrix_basis
    bore_hull(kit, i, r, M)
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
    an['throat_depth'] = throat_depth(r)
    an['throat_radius'] = THROAT_R*r*1.12


def build(kit):
    mats = _mats()
    for i, (x, y, angle, width) in enumerate(ENGINES, 1):
        build_drive(kit, i, x, y, angle, width, mats)
