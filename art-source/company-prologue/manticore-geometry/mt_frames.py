"""Round 10: the exposed hull framing and machinery manticore_pirate.png implies.

Owner, round 9: "read the implied exposed hull framing and machinery from the
sprite and add this detail into the model". Every piece here is real geometry
let into a recess cut in the long round-9 panels, never a greeble floating on
them, and every recess stays inside the plan so the silhouette holds:
- Wing armour and framework (rounds 11-12, owner: the armour "spent angled
  ahead", then "less AND thicker framing ... light interspaced plating and
  machinery / tubing", with the armour wrapping further back): a red plate
  over the solid forward wing whose raked aft edge follows the sprite's red
  boundary; behind it a heavy trailing-edge spar girder, two deep I-girder
  ribs with lightening holes (the outer one red-capped, as drawn), heavy
  bracing, partial plating, two big conduits to the nacelle, a banded tank, a
  pump with feed pipes, two heavy rams and the sensor dome's platform.
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
from mathutils import Matrix, Vector
from mt_kit import boolean, facet_normal, loft_x, plane_cut, prism
from mt_hull import POD, POD_STATIONS, LAUNCHER, WING_DECK, ccw, droop, nose_drop, shear, wing_drop
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
# Round 12 (owner: "less AND thicker framing ... light interspaced plating and
# machinery / tubing", and the armour should "wrap a bit further back", its
# outline visible as in the sprite). The armour edge is read off the sprite's
# red: from the leading edge the plate runs back to X 15.5 across the arm and
# wraps aft along the core's flank to X -1.5; behind that line two deep ribs
# and the trailing-edge spar are the arm's structure.
ARMOUR_EDGE = [(-1.5, 21.0), (-1.5, 25.5), (15.5, 36.5), (15.5, 56.0)]   # port, root to nacelle
EDGE_LIP = 2.0                  # the solid wing stops this far ahead of the plate's aft edge
PLATE = (WING_DECK-2.0, WING_DECK+2.0)
TOP, BOTTOM = 7.5, -2.0         # framework top and bottom
SPAR_B = (1.0, 4.4)             # trailing-edge spar longerons, s inboard of the trailing edge
RIB_A, RIB_B = 45.0, 31.0       # the red-capped rib (sprite, Y 42-48) and the root rib
ROOT_Y = 19.5                   # members run into the core's flank
AFT_X = -16.5                   # ... and into the engine block's forward face


def spar_b_x(y):
    """Where the trailing-edge spar's centre line crosses y (port)."""
    tv = (TE_ROOT[1]+SWEEP_N[1]*sum(SPAR_B)/2-y)/-SWEEP_T[1]
    return TE_ROOT[0]+SWEEP_N[0]*sum(SPAR_B)/2+SWEEP_T[0]*tv


def edge_x(y):
    """The armour edge's x at port y."""
    (x1, y1), (x2, y2) = ARMOUR_EDGE[1], ARMOUR_EDGE[2]
    if y <= y1:
        return x1
    if y >= y2:
        return x2
    return x1+(x2-x1)*(y-y1)/(y2-y1)


def offset_edge(d):
    """The armour edge's polyline moved d units forward, each run parallel to itself."""
    (x1, y1), (x2, y2) = ARMOUR_EDGE[1], ARMOUR_EDGE[2]
    ln = math.hypot(x2-x1, y2-y1)
    nx, ny = (y2-y1)/ln, -(x2-x1)/ln       # the diagonal's forward-inboard normal
    # The diagonal moved by d meets the root run at x1+d and the straight run at x2+d.
    k = (x1+d-(x1+nx*d))/(x2-x1)
    ya = y1+ny*d+k*(y2-y1)
    k = (x2+d-(x1+nx*d))/(x2-x1)
    yb = y1+ny*d+k*(y2-y1)
    return [(x1+d, ARMOUR_EDGE[0][1]), (x1+d, ya), (x2+d, yb), (x2+d, ARMOUR_EDGE[3][1])]


def baked(ob):
    """Move an object's placement into its mesh, so `shear` and `droop` (which read
    mesh coordinates) bend it where it stands."""
    for mod in list(ob.modifiers):
        bpy.context.view_layer.objects.active = ob
        bpy.ops.object.modifier_apply(modifier=mod.name)
    ob.data.transform(ob.matrix_world)
    ob.matrix_world = Matrix.Identity(4)
    return ob


def slab(kit, name, plan, z0, z1, mat):
    return prism(kit, name, ccw(plan), [(0, z0), (0, z1)], mat, 0)


def wing_armour(kit, side, s, wing):
    m = lambda pts: [(x, s*y) for x, y in pts]
    lip = offset_edge(EDGE_LIP)
    cut = m(lip+[(lip[-1][0], 90.0), (-70.0, 90.0), (-70.0, lip[0][1])])
    boolean(wing, slab(kit, f'Temporary {side} wing armour-edge cutter', cut, -40, 40, kit.steel))
    # The armour plate over the solid forward wing, its aft edge raked so the
    # armour's outline reads as one deliberate line from above and from the side.
    # Its front stays under the glacis band's flat top; every edge is chamfered
    # 45 deg, so the aft edge is one raked line (front, root and outboard ends
    # are buried in the band, the core and the nacelle).
    plan = [(22.0, 21.0), (22.0, 56.0)]+list(reversed(ARMOUR_EDGE))
    z0, z1 = PLATE
    plate = prism(kit, f'Hull wing {side} armour plate 1', ccw(m(plan)), [(0, z0), (0, z1-2.5), (2.5, z1)], kit.red, .12)
    kit.hull.append(droop(shear(plate)))


def i_rib(kit, name, y, x0, x1, holes, segs, s):
    """A deep I-girder rib along x at port y: flanges as bars, a plate web with lightening holes."""
    web = slab(kit, name+' web', [(x0, s*(y-.3)), (x1, s*(y-.3)), (x1, s*(y+.3)), (x0, s*(y+.3))],
               BOTTOM+.5, TOP-.5, kit.steel)
    for k, (hx, hw) in enumerate(holes):
        boolean(web, slab(kit, f'Temporary {name} hole {k}', [(hx-hw, s*(y-1)), (hx+hw, s*(y-1)), (hx+hw, s*(y+1)),
                                                            (hx-hw, s*(y+1))], BOTTOM+2.0, TOP-2.0, kit.steel))
    segs += [((x0, s*y, TOP), (x1, s*y, TOP), .9, .5), ((x0, s*y, BOTTOM), (x1, s*y, BOTTOM), .9, .5)]
    droop(shear(web))


def wing_framework(kit, side, s, wing):
    wing_armour(kit, side, s, wing)
    m = lambda pts: [(x, s*y) for x, y in pts]
    segs = []
    # Trailing-edge spar: a heavy box girder from the nacelle's tail into the engine block.
    t0, t1 = 5.0, 40.0
    step = (t1-t0-2.0)/8
    for sv in SPAR_B:
        a, b = te(sv, t0, s), te(sv, t1, s)
        segs += [((*a, TOP-1.0), (*b, TOP-1.0), .7, .7), ((*a, BOTTOM+1.0), (*b, BOTTOM+1.0), .7, .7)]
    for k in range(9):  # a few heavy posts and diagonals across the spar's faces
        tv = t0+1.0+k*step
        for sv in SPAR_B:
            segs.append(((*te(sv, tv, s), BOTTOM+1.0), (*te(sv, tv, s), TOP-1.0), .45, .45))
        if k < 8:
            segs.append(((*te(SPAR_B[1], tv, s), BOTTOM+1.0), (*te(SPAR_B[1], tv+step, s), TOP-1.0), .35, .35))
    cap = [te(SPAR_B[0]-.6, t0, s), te(SPAR_B[1]+.6, t0, s), te(SPAR_B[1]+.6, t1, s), te(SPAR_B[0]-.6, t1, s)]
    droop(shear(slab(kit, f'Hull wing {side} trailing spar cap', cap, TOP-.3, TOP+.4, kit.red)))
    # Interspaced plating on the spar's outer face: every other panel closed.
    for k in range(0, 8, 2):
        ta, tb = t0+1.0+k*step, t0+1.0+(k+1)*step
        quad = [te(SPAR_B[0]-.1, ta, s), te(SPAR_B[0]-.1, tb, s), te(SPAR_B[0]+.4, tb, s), te(SPAR_B[0]+.4, ta, s)]
        droop(shear(slab(kit, f'Hull wing {side} spar panel {k//2+1}', quad, BOTTOM+1.0, TOP-1.0, kit.plate)))
    # Two deep ribs: the red-capped rib out at Y 45 and the root rib at Y 31.
    xa0, xa1 = edge_x(RIB_A)+EDGE_LIP+.5, spar_b_x(RIB_A)
    i_rib(kit, f'Hull wing {side} rib A', RIB_A, xa0, xa1, [(xa0-5.5, 2.0), (xa0-13.0, 2.2), (xa0-20.5, 2.0)], segs, s)
    droop(shear(slab(kit, f'Hull wing {side} rib A cap', m([(xa0, RIB_A-1.6), (xa1, RIB_A-1.6), (xa1, RIB_A+1.6),
                                                            (xa0, RIB_A+1.6)]), TOP+.5, TOP+1.1, kit.red)))
    xb0, xb1 = edge_x(RIB_B)+EDGE_LIP+.5, AFT_X
    i_rib(kit, f'Hull wing {side} rib B', RIB_B, xb0, xb1, [(xb0-5.0, 2.0), (xb0-12.5, 2.2), (xb0-19.0, 1.6)], segs, s)
    # Diagonal bracing in plan between the ribs, top and bottom: one heavy member a bay.
    segs += [((xb0-2.0, s*RIB_B, TOP), (xa1+3.0, s*RIB_A, TOP), .5, .5),
             ((xb1+3.0, s*RIB_B, BOTTOM), (xa0-3.0, s*RIB_A, BOTTOM), .5, .5),
             ((xa0-1.0, s*RIB_A, TOP), (8.0, s*56.5, TOP-3.0), .5, .5)]
    droop(shear(struts(kit, f'Hull wing {side} framework', segs, kit.steel)))
    # Light interspaced plating: partial panels, never a closed skin.
    for k, plan, z in ((1, [(-13.5, 32.5), (-5.0, 32.5), (-5.0, 43.5), (-13.5, 43.5)], TOP+.5),
                       (2, [(11.0, 46.0), (3.0, 46.0), (3.0, 53.0), (9.5, 53.0)], TOP+.5),
                       (3, [(2.0, 32.0), (-12.0, 32.0), (-12.0, 44.0), (2.0, 44.0)], BOTTOM-.5)):
        droop(shear(slab(kit, f'Hull wing {side} panel {k}', m(plan), z-.25, z+.25, kit.plate)))
    # Machinery and tubing, sized to read at the three-quarter distance: two
    # big conduits from the core out to the nacelle with collars where they
    # pass the ribs, a banded tank on cradles, a pump, feed pipes and two rams.
    runs = ((8.5, 3.0), (6.4, 1.6))
    pipes = [((x, s*ROOT_Y, z), (x, s*60.0, z), 1.0, 1.0, 10) for x, z in runs]
    for y in (RIB_B+.8, RIB_A-.8, RIB_A+.8, 57.5):
        for x, z in runs:
            pipes.append(((x, s*(y-.5), z), (x, s*(y+.5), z), 1.35, 1.35, 10))
    droop(shear(struts(kit, f'Hull wing {side} conduits', pipes, kit.plate)))
    tank_y, tank_x, tank_z = 38.0, -4.5, BOTTOM+3.2
    shear(baked(kit.cylinder(f'Hull wing {side} tank', (tank_x, s*tank_y, tank_z), 2.7, 11.0, kit.plate, axis='X',
                             vertices=16, bevel=0)))
    bands = [((tank_x+d, s*tank_y, tank_z), (tank_x+d+.7, s*tank_y, tank_z), 2.95, 2.95, 16) for d in (-3.5, 2.8)]
    bands += [((tank_x+d, s*tank_y, BOTTOM-.3), (tank_x+d, s*tank_y, BOTTOM+1.2), 2.2, .5) for d in (-3.2, 3.2)]
    shear(struts(kit, f'Hull wing {side} tank bands', bands, kit.steel))
    shear(slab(kit, f'Hull wing {side} pump', m([(-1.0, 48.5), (3.5, 48.5), (3.5, 53.0), (-1.0, 53.0)]), BOTTOM+.5, 4.5,
               kit.steel))
    feed = [((1.2, s*50.7, 3.0), (6.4, s*50.7, 1.6), .6, .6, 8),
            ((tank_x+5.5, s*tank_y, tank_z), (6.4, s*tank_y, 1.6), .6, .6, 8),
            ((1.2, s*50.7, 1.0), (spar_b_x(50.7)+2.5, s*50.7, 1.0), .6, .6, 8)]
    shear(struts(kit, f'Hull wing {side} feed pipes', feed, kit.plate))
    rams = []
    for x in (13.0, 8.5):
        a, b = (x, s*(RIB_A+.6), 1.0), (x-1.0, s*59.0, 2.5)
        mid = tuple(a[i]+(b[i]-a[i])*.55 for i in range(3))
        rams += [(a, mid, 1.0, 1.0, 10), (mid, b, .5, .5, 10)]
    droop(shear(struts(kit, f'Hull wing {side} pod rams', rams, kit.plate)))
    # The sensor dome (the tan bulb at (5, +-35)) on a platform over the root bay.
    plat = slab(kit, f'Hull wing {side} sensor platform', m([(0, 31.5), (10, 31.5), (10, 40), (0, 40)]), TOP+.35,
                TOP+.85, kit.steel)
    plinth = kit.cylinder(f'Hull wing {side} sensor plinth', (5, s*35, TOP+1.4), 4.6, 1.2, kit.steel, vertices=12, bevel=0)
    bpy.ops.mesh.primitive_uv_sphere_add(segments=16, ring_count=8, radius=3.8, location=(5, s*35, TOP+2.0))
    dome = bpy.context.object
    dome.scale.z = .8
    bpy.ops.object.transform_apply(scale=True)
    kit.finish(dome, f'Hull wing {side} sensor dome', kit.pale)
    for ob in (plat, plinth, dome):
        shear(baked(ob))


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
