"""Round 10: the exposed hull framing and machinery manticore_pirate.png implies.

Owner, round 9: "read the implied exposed hull framing and machinery from the
sprite and add this detail into the model". Every piece here is real geometry
let into a recess cut in the long round-9 panels, never a greeble floating on
them, and every recess stays inside the plan so the silhouette holds:
- Trailing-edge truss bays: the dark hatched band just inboard of each swept
  trailing edge (sprite rows 75-100) is an open channel in the wing with a
  Warren truss in it: two chords, zigzag diagonals and transverse frames.
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
from mt_kit import boolean, loft_x, prism
from mt_hull import POD, POD_STATIONS, LAUNCHER, WING_DECK, ccw, droop, nose_drop, shear, wing_drop
from mt_detail import SWEEP_N, SWEEP_T, TE_ROOT

TRUSS = (3.4, 6.4, 10.0, 33.0, 2.2)      # s from, s to, t from, t to, depth (trailing-edge channel)
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


def trailing_truss(kit, side, s, wing):
    s0, s1, t0, t1, depth = TRUSS
    zf = WING_DECK-depth
    plan = [te(s0, t0, s), te(s1, t0, s), te(s1, t1, s), te(s0, t1, s)]
    cutter = shear(prism(kit, f'Temporary {side} truss channel cutter', ccw(plan), [(0, zf), (0, WING_DECK+6)],
                         kit.steel, 0))
    boolean(wing, cutter)
    shear(floor_plate(kit, f'Hull wing {side} truss bay floor', plan, zf, kit.dark))
    ca, cb = s0+.35, s1-.35          # the two chords, just inside the channel walls
    segs = [((*te(ca, t0+.3, s), zf+.55), (*te(ca, t1-.3, s), zf+.55), .3, .45),
            ((*te(cb, t0+.3, s), zf+.55), (*te(cb, t1-.3, s), zf+.55), .3, .45)]
    n = int((t1-t0-1)/2.5)
    for k in range(n):               # zigzag diagonals, rising to just under the deck
        ta, tb = t0+.6+2.5*k, t0+.6+2.5*(k+1)
        a, b = (ca, cb) if k % 2 == 0 else (cb, ca)
        segs.append(((*te(a, ta, s), zf+.9), (*te(b, tb, s), zf+1.6), .2, .3))
    for k in range(int((t1-t0)/5)+1):  # transverse frames every five units
        tv = min(t0+.5+5*k, t1-.5)
        segs.append(((*te(s0, tv, s), zf+1.0), (*te(s1, tv, s), zf+1.0), .3, .9))
    shear(struts(kit, f'Hull wing {side} trailing truss', segs, kit.plate))


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


def build(kit):
    wing = bpy.data.objects['Hull wing body']
    aft = bpy.data.objects['Hull aft body']
    core = bpy.data.objects['Hull core']
    for side, s in (('port', 1), ('starboard', -1)):
        trailing_truss(kit, side, s, wing)
        launcher_coamings(kit, side, s)
        tank_bay(kit, side, s, (aft, wing))
        vent_bay(kit, side, s, core)
        pod_bay(kit, side, s, (bpy.data.objects[f'Hull {side} turret pod'], wing))
