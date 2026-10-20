"""Round 10: the exposed hull framing and machinery manticore_pirate.png implies.

Owner, round 9: "read the implied exposed hull framing and machinery from the
sprite and add this detail into the model". Every piece here is real geometry
let into a recess cut in the long round-9 panels, never a greeble floating on
them, and every recess stays inside the plan so the silhouette holds:
- Wing framework (round 11, owner: "the wings besides the front sections are
  exposed structural framework ... the armor being spent angled ahead"): the
  solid wing ends behind the leading-edge glacis; aft of it each arm is an
  open lattice read off the sprite's dark framework areas: a main spar girder
  out to the gun nacelle, a trailing-edge spar from the nacelle's tail to the
  engine block, five chordwise truss ribs, X-bracing top and bottom and a mid
  stringer, with conduits threaded through to the nacelle, two rams at its
  root and the sensor dome on a platform across two ribs.
- Aft machinery bays: the dark round drum at (-24, +-32) (rows 108-115) is a
  tank lying fore and aft in a bay cut into the engine block's shoulder, on
  cradles, banded, with feed pipes running aft to the outer drive.
- Heat-vent bays: the slatted blocks either side of the centreline aft of the
  bridge (rows 117-135) are recessed bays in the engine deck, framed across and
  along, with the vent glow on their floors.
- Pod tail machinery: the dark mechanism aft of each gun ring (rows 74-81) is
  a bay in the nacelle's tail deck with frames and two actuator rams.
- Launcher coamings: the light rails flanking each Harpoon rack (rows 45-56)
  are frame members along the recess lips with a cross beam at its back.
Recesses in the measured solids (wing body, aft body, core) are kept shallow:
open bays are not enclosed volume, and the scale's volume check allows 2%.
"""
import math
import bpy
from mathutils import Vector
from mt_kit import boolean, loft_x, plane_cut, prism
from mt_hull import GLACIS, POD, POD_STATIONS, LAUNCHER, WING_DECK, ccw, droop, nose_drop, shear, wing_drop
from mt_detail import SWEEP_N, SWEEP_T, TE_ROOT

TANK_BAY = (-17.5, -30.5, 26.0, 38.0, 2.5)   # x fore, x aft, |y| in, |y| out, depth
VENT_BAY = (-33.0, -50.0, 3.5, 11.0, 1.6)    # x fore, x aft, |y| in, |y| out, depth
POD_BAY = (6.5, 12.5, 3.6, 1.5)              # x aft, x fore, half-width about the pod axis, depth


def struts(kit, name, segs, mat):
    """One mesh of straight members: each (p0, p1, half_w, half_h[, sides]) is a
    square bar (sides 4) or a round rod (sides 8) between two 3D points."""
    verts, faces = [], []
    for seg in segs:
        p0, p1, hw, hh = (Vector(seg[0]), Vector(seg[1]), seg[2], seg[3])
        sides = seg[4] if len(seg) > 4 else 4
        u = (p1-p0).normalized()
        side = Vector((-u.y, u.x, 0))
        side = side.normalized() if side.length > 1e-6 else Vector((0, 1, 0))
        up = side.cross(u).normalized()
        base = len(verts)
        for p in (p0, p1):
            for k in range(sides):
                a = 2*math.pi*(k+.5)/sides
                verts.append(tuple(p+side*(hw*math.cos(a)*(2**.5 if sides == 4 else 1))
                                   + up*(hh*math.sin(a)*(2**.5 if sides == 4 else 1))))
        faces.append(tuple(base+k for k in range(sides)))
        faces.append(tuple(base+sides+k for k in range(sides)))
        faces += [(base+k, base+(k+1) % sides, base+sides+(k+1) % sides, base+sides+k) for k in range(sides)]
    return kit.mesh(name, verts, faces, mat, 0)


def te(sv, tv, s):
    """Plan point s inboard of the port trailing edge and t along it (mirrored by s)."""
    return (TE_ROOT[0]+SWEEP_N[0]*sv+SWEEP_T[0]*tv, s*(TE_ROOT[1]+SWEEP_N[1]*sv+SWEEP_T[1]*tv))


def seat_z(kit, x, y, fallback):
    hit = kit.surface(x, y)
    return hit[0].z if hit else fallback


def floor_plate(kit, name, plan, z, mat):
    return prism(kit, name, ccw(plan), [(0, z), (0, z+.06)], mat, 0)


def tank_bay(kit, side, s, hosts):
    xf, xa, yi, yo, depth = TANK_BAY
    zi, zo = seat_z(kit, (xf+xa)/2, s*yi, 8), seat_z(kit, (xf+xa)/2, s*yo, 4)

    def zb(y):  # the bay floor, parallel to the shoulder it is cut into
        return zi+(zo-zi)*(abs(y)-yi)/(yo-yi)-depth
    plan = [(xf, s*yi), (xf, s*yo), (xa, s*yo), (xa, s*yi)]
    verts = [(x, y, zb(y)) for x, y in plan]+[(x, y, 40) for x, y in plan]
    faces = [(0, 1, 2, 3), (4, 5, 6, 7), (0, 1, 5, 4), (1, 2, 6, 5), (2, 3, 7, 6), (3, 0, 4, 7)]
    for host in hosts:
        boolean(host, kit.mesh(f'Temporary {side} tank bay cutter', verts, faces, kit.steel, 0))
    floor = kit.mesh(f'Hull aft {side} machinery bay floor', [(x, y, z+.05) for x, y, z in verts[:4]]
                     + [(x, y, z+.11) for x, y, z in verts[:4]], faces, kit.dark, 0)
    # The tank: a drum lying fore and aft on two cradles, banded, its ends domed.
    yc = s*(yi+yo)/2
    r = 3.0
    zc = zb(yc)+r-.5
    length = (xf-xa)-1.6
    kit.cylinder(f'Hull aft {side} machinery tank', ((xf+xa)/2, yc, zc), r, length, kit.plate, axis='X',
                 vertices=16, bevel=.25)
    for k, x in enumerate((xf-3.0, xa+3.0), 1):
        kit.cylinder(f'Hull aft {side} machinery tank band {k}', (x, yc, zc), r+.22, .7, kit.steel, axis='X',
                     vertices=16, bevel=.05)
        kit.box(f'Hull aft {side} machinery tank cradle {k}', (x, yc, zb(yc)+.6), (1.2, 2*r-.6, 1.4), kit.steel, .08)
    for k, x in enumerate((xf-.8, xa+.8), 1):
        kit.cylinder(f'Hull aft {side} machinery tank head {k}', (x, yc, zc), r-.6, .9, kit.steel, axis='X',
                     vertices=16, bevel=.2)
    # Two feed pipes from the tank's aft head along the engine block's shoulder
    # to the outer drive, each seated at its bends and clamped where it lies.
    for k, dy in enumerate((-1.4, 1.4), 1):
        y0 = yc+s*dy
        pts = [(xa+.4, y0, zc-.8)]
        for x, y in ((-34.0, y0), (-40.0, s*(29.0+dy*.6)), (-47.0, s*(29.0+dy*.6))):
            pts.append((x, y, seat_z(kit, x, y, 4)+.6))
        segs = [(a, b, .5, .5, 8) for a, b in zip(pts, pts[1:])]
        for (x, y, z) in pts[1:]:
            segs.append(((x+.4, y, z-.65), (x-.4, y, z-.65), .55, .75))  # clamp block under each bend
        struts(kit, f'Hull aft {side} feed pipe {k}', segs, kit.plate)
    return floor


def core_deck(x):
    """Hull core deck height at x aft of the gun step (mt_hull.CORE_STATIONS)."""
    return 15.0 if x >= -40 else 15.0-(15.0-11.0)*(-40-x)/17.5


def vent_bay(kit, side, s, core):
    xf, xa, yi, yo, depth = VENT_BAY
    xs = [xf, -40.0, xa]
    secs = [(x, [(s*yi, core_deck(x)-depth), (s*yo, core_deck(x)-depth), (s*yo, 40), (s*yi, 40)]) for x in xs]
    boolean(core, loft_x(kit, f'Temporary {side} vent bay cutter', secs, kit.steel, 0))
    loft_x(kit, f'Hull aft {side} vent bay floor',
           [(x, [(s*yi, z+.04), (s*yo, z+.04), (s*yo, z+.1), (s*yi, z+.1)]) for x, z in
            ((x, core_deck(x)-depth) for x in xs)], kit.dark, 0)
    segs = []
    for k in range(8):  # transverse frames, top a little under the deck
        x = xf-1.0-2.2*k
        top = core_deck(x)-.2
        segs.append(((x, s*(yi+.1), top-.55), (x, s*(yo-.1), top-.55), .3, .55))
    for y in (yi+.35, yo-.35):  # stringers along the walls, following the deck's fall aft of -40
        segs.append(((xf-.3, s*y, core_deck(xf)-.9), (-40.0, s*y, core_deck(-40)-.9), .3, .5))
        segs.append(((-40.0, s*y, core_deck(-40)-.9), (xa+.3, s*y, core_deck(xa)-.9), .3, .5))
    struts(kit, f'Hull aft {side} vent bay frames', segs, kit.steel)
    for k, y in enumerate((5.6, 7.25, 8.9), 1):  # the glowing slats between the frames
        kit_slat = [((xf-.6, s*y, core_deck(xf)-depth+.25), (-40.0, s*y, core_deck(-40)-depth+.25), .25, .12),
                    ((-40.0, s*y, core_deck(-40)-depth+.25), (xa+.6, s*y, core_deck(xa)-depth+.25), .25, .12)]
        struts(kit, f'Hull aft {side} heat vent slat {k}', kit_slat, kit.vent)


def pod_deck(x):
    """Gun nacelle deck height along its tail (POD_STATIONS, before droop and the wing's fall)."""
    (xa, _, za, _), (xb, _, zb_, _) = POD_STATIONS[3], POD_STATIONS[2]
    return za+(zb_-za)*(x-xa)/(xb-xa)


def pod_bay(kit, side, s, hosts):
    x0, x1, hw, depth = POD_BAY
    _, y, _ = POD
    drop = wing_drop(y)
    xs = [x0, 10.0, x1]

    def deck(x):
        return pod_deck(x)-drop-nose_drop(x)
    secs = [(x, [(s*(y-hw), deck(x)-depth), (s*(y+hw), deck(x)-depth), (s*(y+hw), 40), (s*(y-hw), 40)]) for x in xs]
    for host in hosts:  # the wing body fills the nacelle's tail where they meet
        boolean(host, loft_x(kit, f'Temporary {side} pod bay cutter', secs, kit.steel, 0))
    loft_x(kit, f'Hull {side} pod machinery bay floor',
           [(x, [(s*(y-hw), deck(x)-depth+.04), (s*(y+hw), deck(x)-depth+.04), (s*(y+hw), deck(x)-depth+.1),
                 (s*(y-hw), deck(x)-depth+.1)]) for x in xs], kit.dark, 0)
    segs = []
    for x in (x0+.6, (x0+x1)/2, x1-.6):   # frames across the bay
        segs.append(((x, s*(y-hw+.2), deck(x)-.75), (x, s*(y+hw-.2), deck(x)-.75), .28, .55))
    for dy in (-1.6, 1.6):                 # two actuator rams lying fore and aft
        segs.append(((x0+.9, s*(y+dy), deck(x0)-depth+.75), (x1-.9, s*(y+dy), deck(x1)-depth+.75), .5, .5, 8))
        segs.append(((x0+.9, s*(y+dy), deck(x0)-depth+.75), ((x0+x1)/2, s*(y+dy), deck((x0+x1)/2)-depth+.75),
                     .7, .7, 8))         # the ram's cylinder over its rear half
    struts(kit, f'Hull {side} pod machinery', segs, kit.steel)


def launcher_coamings(kit, side, s):
    lx, ly = LAUNCHER
    segs = []
    for dy in (-8.1, 8.1):
        y = s*(ly+dy)
        za, zb = seat_z(kit, lx-6.5, y, 4), seat_z(kit, lx-.8, y, 4)
        segs.append(((lx-6.8, y, za+.35), (lx-.6, y, zb+.35), .55, .4))
    zl, zr = seat_z(kit, lx-7.3, s*(ly-8.1), 4), seat_z(kit, lx-7.3, s*(ly+8.1), 4)
    segs.append(((lx-7.0, s*(ly-8.4), zl+.35), (lx-7.0, s*(ly+8.4), zr+.35), .5, .4))  # cross beam at its back
    struts(kit, f'Hull {side} launcher coamings', segs, kit.plate)


# Wing framework, port side, before the wing's fall (`shear`) and the nose bend (`droop`).
WING_CUT = 31.3-GLACIS+1.5      # the solid wing ends just inside the glacis band's back wall
TOP, BOTTOM = 7.5, -1.0         # top and bottom chord heights
SPAR_A = (18.3, 14.8)           # main spar girder: front and rear longerons (x)
SPAR_B = (1.0, 3.5)             # trailing-edge spar longerons, s inboard of the trailing edge
RIBS = (26.0, 33.0, 40.0, 47.0, 53.5)
ROOT_Y = 19.5                   # members run into the core's flank
AFT_X = -16.5                   # ... and into the engine block's forward face


def spar_b_x(y):
    """Where the trailing-edge spar's centre line crosses y (port)."""
    tv = (TE_ROOT[1]+SWEEP_N[1]*sum(SPAR_B)/2-y)/-SWEEP_T[1]
    return TE_ROOT[0]+SWEEP_N[0]*sum(SPAR_B)/2+SWEEP_T[0]*tv


def truss(segs, a, b, top, bottom, panel, hw=.22):
    """A flat truss between plan points a and b: chords at top/bottom, verticals and alternating diagonals."""
    (ax, ay), (bx, by) = a, b
    n = max(1, round(math.hypot(bx-ax, by-ay)/panel))
    pts = [(ax+(bx-ax)*k/n, ay+(by-ay)*k/n) for k in range(n+1)]
    segs += [((*a, top), (*b, top), .35, .35), ((*a, bottom), (*b, bottom), .35, .35)]
    for k, (x, y) in enumerate(pts):
        segs.append(((x, y, bottom), (x, y, top), hw, hw))
        if k < n:
            x1, y1 = pts[k+1]
            lo, hi = ((x, y), (x1, y1)) if k % 2 == 0 else ((x1, y1), (x, y))
            segs.append(((*lo, bottom), (*hi, top), hw, hw))


def wing_framework(kit, side, s, wing):
    plane_cut(wing, f'{side} wing framework cut', (WING_CUT, s*40, 0), (-1, 0, 0))
    segs = []
    m = lambda pts: [(x, s*y) for x, y in pts]
    # Main spar: a box girder of two trusses, braced across top and bottom.
    fa, ra = SPAR_A
    y0, y1 = ROOT_Y, 56.0
    for x in SPAR_A:
        truss(segs, *m([(x, y0), (x, y1)]), TOP, BOTTOM, 4.5)
    for k in range(int((y1-y0)/4.5)):
        ya, yb = y0+4.5*k, y0+4.5*(k+1)
        for z in (TOP, BOTTOM):
            segs.append(((fa, s*ya, z), (ra, s*yb, z), .2, .2))
    # Trailing-edge spar: the same girder, lighter, from the nacelle's tail into the engine block.
    lines = [[te(sv, 3.0, s), te(sv, 40.0, s)] for sv in SPAR_B]
    for a, b in lines:
        truss(segs, a, b, TOP-1.5, BOTTOM+1.0, 4.5, .2)
    # Chordwise ribs from the main spar's rear longeron to the trailing-edge spar or the engine block.
    for k, y in enumerate(RIBS):
        x_end = max(spar_b_x(y), AFT_X)
        truss(segs, *m([(ra, y), (x_end, y)]), TOP, BOTTOM, 5.0)
        if k+1 < len(RIBS):  # plan X-bracing between neighbouring ribs, top and bottom
            yn = RIBS[k+1]
            xn = max(spar_b_x(yn), AFT_X)
            segs.append(((ra, s*y, TOP), ((x_end+xn)/2+4, s*yn, TOP), .2, .2))
            segs.append(((ra, s*yn, BOTTOM), ((x_end+xn)/2+4, s*y, BOTTOM), .2, .2))
    for z in (TOP, BOTTOM):  # a mid-chord stringer along the span
        segs.append(((1.5, s*ROOT_Y, z), (1.5, s*RIBS[-1], z), .3, .3))
    droop(shear(struts(kit, f'Hull wing {side} framework', segs, kit.steel)))
    # Conduits threaded through the ribs out to the nacelle, and two rams at its root.
    pipes = [((x, s*ROOT_Y, z), (x, s*59.0, z), .55, .55, 8) for x, z in ((11.0, 3.4), (9.4, 2.4), (7.8, 3.4))]
    droop(shear(struts(kit, f'Hull wing {side} conduits', pipes, kit.plate)))
    rams = []
    for x in (16.0, 12.0):
        a, b = (x, s*46.0, 1.0), (x-2.0, s*57.0, 2.5)
        mid = tuple(a[i]+(b[i]-a[i])*.55 for i in range(3))
        rams += [(a, mid, .7, .7, 8), (mid, b, .35, .35, 8)]
    droop(shear(struts(kit, f'Hull wing {side} pod rams', rams, kit.plate)))
    # The sensor dome (the tan bulb at (5, +-35)) on a platform laid across two ribs.
    plat = kit.box(f'Hull wing {side} sensor platform', (5, s*36.5, TOP+.6), (10, 9.5, .5), kit.steel, .08)
    plinth = kit.cylinder(f'Hull wing {side} sensor plinth', (5, s*35, TOP+1.4), 4.6, 1.2, kit.steel, vertices=12, bevel=.1)
    bpy.ops.mesh.primitive_uv_sphere_add(segments=16, ring_count=8, radius=3.8, location=(5, s*35, TOP+2.0))
    dome = bpy.context.object
    dome.scale.z = .8
    bpy.ops.object.transform_apply(scale=True)
    kit.finish(dome, f'Hull wing {side} sensor dome', kit.pale)
    for ob in (plat, plinth, dome):
        for mod in list(ob.modifiers):
            bpy.context.view_layer.objects.active = ob
            bpy.ops.object.modifier_apply(modifier=mod.name)
        shear(ob)


def build(kit):
    wing = bpy.data.objects['Hull wing body']
    aft = bpy.data.objects['Hull aft body']
    core = bpy.data.objects['Hull core']
    for side, s in (('port', 1), ('starboard', -1)):
        wing_framework(kit, side, s, wing)
        launcher_coamings(kit, side, s)
        tank_bay(kit, side, s, (aft,))
        vent_bay(kit, side, s, core)
        pod_bay(kit, side, s, (bpy.data.objects[f'Hull {side} turret pod'],))
