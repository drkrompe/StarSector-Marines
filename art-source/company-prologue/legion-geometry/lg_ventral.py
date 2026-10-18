"""The underside: what a battlecarrier carries in its belly, modelled in depth.

The ventral keel ('Hull ventral keel', lg_hull) is the deep belly under the fore
and aft body: a flat bottom at z -44 covered by lg_hull's bottom plates, a 45
degree chamfer up to its walls at |Y| 60-62, a forefoot raked up into the bow
and a stern rake up under the engine block. Future shots may look up at it
under unknown light, so everything here is real depth: courses that step,
frames that stand proud of recessed doors, pockets cut into the keel, parts that
hang and overhang. No panel lines, no damage, no weapons (legion.ship has no
ventral slot).

What the belly holds (judged; the sprite shows only the top):
- KEEL GIRDER. The hull's backbone shows on the centreline: a segmented box
  girder over a dark core, running from the forefoot to the stern rake with
  web gussets along its flanks. It parts round the cargo well.
- MAGAZINE HATCHES. Three heavy hatches either side of the girder under the
  central hold, where fighters refit inside the torso: deep coamings, twin
  leaves recessed in them with stiffeners and hinge knuckles, dogs at the
  corners. Ordnance comes aboard here from a lighter alongside.
- CARGO / REPLENISHMENT WELL. Aft of the hatches, astride the keel: a deep
  framed well (cargo 300) whose doors are recessed in it, guide rails either
  side for a supply boat's arms, and six docking clamps hanging open below
  it. A boat docking port either side of the well for shuttles.
- GRAPPLE HARDPOINTS. Four on the flat bottom and two under the bow: tapered
  bases with pads and lifting eyes for yard cradles and tugs.
- ARMOUR COURSE. A stepped orange armour course (two courses, segmented over a
  dark seam underlay) along each bottom edge forward of the hatches, echoing
  the flank belts.
- TANK BLISTERS. Three strapped tank blisters on each forward chamfer (fuel
  300), sunk into it, overhanging the bottom edge.
- HEAT EXCHANGE. The reactors and flux plant are aft (the machinery bed), so
  the aft chamfers carry radiator bays cut into the keel with fin stacks in
  them, and a coolant trunk bundle runs from them aft along the chamfer foot
  and up the stern rake. Radiator skids hang under the aft body (below the
  machinery bed) and under the engine block's outboard wings; the engine
  housings get stepped skid plates.
- SENSORS. The forefoot carries a recessed sensor bay either side of the keel
  line (phased-array tiles in a pocket) and stepped armour shingles on its
  upper courses; the stern rake carries louvred discharge pockets.
- LAMPS. Six only: white at both ends of the girder, amber docking lights at
  the cargo well's corners ('Ventral lamp ...'; lg_fittings owns 'Light ...').

Boundaries (brief): the HANGAR agent owns the arms, the hangar pods and the
transfer zone |Y| 58..82, z -30..-6, X -66..106; nothing here is built there.
Everything stays inside the plan outline seen from above. Only 'Hull ventral
keel' is cut (one boolean of all pocket cutters); every other hull object is
read only and parts are only added onto it. Parts seat by upward ray-casts from
below (Under.up), except under the engine block and housings, which lg_engines
builds after this module: those use lg_engines' own section constants.
"""
import math
import bpy
from mathutils import Vector
from mathutils.bvhtree import BVHTree
from lg_kit import boolean, inset, material
import lg_engines

# --- geometry helpers --------------------------------------------------------------


class Acc:
    def __init__(self):
        self.verts, self.faces = [], []

    def add(self, verts, faces):
        o = len(self.verts)
        self.verts += [tuple(v) for v in verts]
        self.faces += [tuple(o+i for i in f) for f in faces]

    def build(self, kit, name, mat, bevel=0):
        return kit.mesh(name, self.verts, self.faces, mat, bevel) if self.faces else None


def hexa(c):
    return c, [(0, 3, 2, 1), (4, 5, 6, 7), (0, 1, 5, 4), (1, 2, 6, 5), (2, 3, 7, 6), (3, 0, 4, 7)]


def box(x0, x1, y0, y1, z0, z1):
    x0, x1 = sorted((x0, x1))
    y0, y1 = sorted((y0, y1))
    z0, z1 = sorted((z0, z1))
    return hexa([(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0),
                 (x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)])


def cyl(x, y, r, z0, z1, n=16):
    verts = [(x+r*math.cos(2*math.pi*(k+.5)/n), y+r*math.sin(2*math.pi*(k+.5)/n), z) for z in (z0, z1) for k in range(n)]
    faces = [tuple(reversed(range(n))), tuple(range(n, 2*n))] + [(k, (k+1) % n, n+(k+1) % n, n+k) for k in range(n)]
    return verts, faces


def lathe(cx, cy, loop, n=20):
    """Closed solid of revolution about the vertical line (cx, cy) from a closed (r, z) loop, r > 0."""
    verts = [(cx+r*math.cos(2*math.pi*(k+.5)/n), cy+r*math.sin(2*math.pi*(k+.5)/n), z) for r, z in loop for k in range(n)]
    m = len(loop)
    faces = [(j*n+k, j*n+(k+1) % n, ((j+1) % m)*n+(k+1) % n, ((j+1) % m)*n+k) for j in range(m) for k in range(n)]
    return verts, faces


def lathe_x(loop, cy, cz, n=16):
    """Closed solid of revolution about the X-parallel line (cy, cz) from a closed (x, r) loop, r > 0."""
    verts = [(x, cy+r*math.cos(2*math.pi*(k+.5)/n), cz+r*math.sin(2*math.pi*(k+.5)/n)) for x, r in loop for k in range(n)]
    m = len(loop)
    faces = [(j*n+k, j*n+(k+1) % n, ((j+1) % m)*n+(k+1) % n, ((j+1) % m)*n+k) for j in range(m) for k in range(n)]
    return verts, faces


def ring(poly, loop):
    """Closed frame round a CCW plan polygon from a closed (inset, z) section loop."""
    n = len(poly)
    verts = [(x, y, z) for d, z in loop for x, y in inset(poly, d)]
    m = len(loop)
    faces = [(j*n+i, j*n+(i+1) % n, ((j+1) % m)*n+(i+1) % n, ((j+1) % m)*n+i) for j in range(m) for i in range(n)]
    return verts, faces


def rect(x0, x1, y0, y1):
    return [(x0, y0), (x1, y0), (x1, y1), (x0, y1)]


def _sub(a, b):
    return tuple(p-q for p, q in zip(a, b))


def _add(a, b, s=1.0):
    return tuple(p+q*s for p, q in zip(a, b))


def _unit(a):
    length = sum(c*c for c in a)**.5 or 1.0
    return tuple(c/length for c in a)


def _cross(a, b):
    return (a[1]*b[2]-a[2]*b[1], a[2]*b[0]-a[0]*b[2], a[0]*b[1]-a[1]*b[0])


def _frame(t):
    ref = min(((1, 0, 0), (0, 1, 0), (0, 0, 1)), key=lambda e: abs(sum(p*q for p, q in zip(e, t))))
    u = _unit(_cross(t, ref))
    return u, _cross(u, t)


def tube(pts, r, n=10):
    """Closed tube of radius r along a 3D polyline."""
    m = len(pts)
    verts = []
    u0 = None
    for i, p in enumerate(pts):
        t = _unit(_sub(pts[min(i+1, m-1)], pts[max(i-1, 0)]))
        if u0 is None:
            u, v = _frame(t)
        else:   # carry the previous frame along (no twist)
            u = _unit(_sub(u0, tuple(c*sum(a*b for a, b in zip(u0, t)) for c in t)))
            v = _cross(u, t)
        u0 = u
        for k in range(n):
            a = 2*math.pi*(k+.5)/n
            verts.append(tuple(p[c]+r*(math.cos(a)*u[c]+math.sin(a)*v[c]) for c in range(3)))
    faces = [tuple(reversed(range(n))), tuple(range((m-1)*n, m*n))]
    faces += [(j*n+k, j*n+(k+1) % n, (j+1)*n+(k+1) % n, (j+1)*n+k) for j in range(m-1) for k in range(n)]
    return verts, faces


def beam(p0, p1, w, h):
    """Rectangular bar from p0 to p1, w wide (horizontal) and h deep."""
    t = _unit(_sub(p1, p0))
    side = _unit(_cross(t, (0, 0, 1))) if abs(t[2]) < .95 else (0, 1, 0)
    up = _cross(side, t)
    c = []
    for p in (p0, p1):
        for a, b in ((-1, -1), (1, -1), (1, 1), (-1, 1)):
            c.append(tuple(p[k]+side[k]*a*w/2+up[k]*b*h/2 for k in range(3)))
    return c[:4]+c[4:], [(0, 3, 2, 1), (4, 5, 6, 7), (0, 1, 5, 4), (1, 2, 6, 5), (2, 3, 7, 6), (3, 0, 4, 7)]


def sweep_x(pts, prof):
    """Solid swept along a polyline [(x, y, z)] that runs along X; prof is a closed (dy, dz) loop."""
    m = len(prof)
    verts = [(x, y+dy, z+dz) for x, y, z in pts for dy, dz in prof]
    s = len(pts)
    faces = [tuple(range(m)), tuple(range((s-1)*m, s*m))]
    faces += [(j*m+i, j*m+(i+1) % m, (j+1)*m+(i+1) % m, (j+1)*m+i) for j in range(s-1) for i in range(m)]
    return verts, faces


def hang_caps(prof, shrink=.8):
    """A hanging profile shrunk toward its top (the hull side), for chamfered module ends."""
    top = max(dz for _, dz in prof)
    return [(dy*shrink, top+(dz-top)*shrink) for dy, dz in prof]


class Under:
    """Upward ray casts from below against a frozen set of objects (evaluated, world space)."""
    def __init__(self, names):
        bpy.context.view_layer.update()
        dg = bpy.context.evaluated_depsgraph_get()
        self.trees = []
        for n in names:
            ob = bpy.data.objects.get(n)
            if ob is None or ob.type != 'MESH':
                continue
            ev = ob.evaluated_get(dg)
            me = ev.to_mesh()
            mw = ev.matrix_world
            self.trees.append(BVHTree.FromPolygons([mw @ v.co for v in me.vertices], [tuple(p.vertices) for p in me.polygons]))
            ev.to_mesh_clear()

    def up(self, x, y, bottom=-120.0):
        """Height of the lowest surface over (x, y), or None."""
        best = None
        for t in self.trees:
            p, _, _, d = t.ray_cast(Vector((x, y, bottom)), Vector((0, 0, 1)))
            if p is not None and (best is None or p.z < best):
                best = p.z
        return best

    def seat(self, x0, x1, y0, y1, k=3):
        """Lowest surface sampled over a footprint: a flat part hangs from there and sinks 0.3 into it."""
        zs = [self.up(x0+(x1-x0)*i/(k-1), y0+(y1-y0)*j/(k-1)) for i in range(k) for j in range(k)]
        zs = [z for z in zs if z is not None]
        return min(zs) if zs else None


def instance(kit, name, proto, loc, rz=0.0, bevel=0):
    """A linked duplicate of `proto` (sharing its mesh) at loc, turned rz about Z."""
    ob = bpy.data.objects.new(name, proto.data)
    bpy.context.collection.objects.link(ob)
    ob.location = loc
    ob.rotation_euler = (0, 0, rz)
    if bevel:
        b = ob.modifiers.new('Plate edge chamfer', 'BEVEL')
        b.width, b.segments, b.limit_method = bevel, 1, 'ANGLE'
    return ob


def prototype(kit, name, acc, mat, loc, rz=0.0, bevel=0):
    ob = acc.build(kit, name, mat, bevel)
    ob.location = loc
    ob.rotation_euler = (0, 0, rz)
    return ob


def place_all(kit, base, parts, spots, bevel=0):
    """Instance each (suffix, acc, mat) prototype at every (label, loc, rz) spot."""
    for suffix, acc, mat in parts:
        proto = None
        for label, loc, rz in spots:
            name = f'{base} {label}'+(f' {suffix}' if suffix else '')
            if proto is None:
                proto = prototype(kit, name, acc, mat, loc, rz, bevel)
            else:
                instance(kit, name, proto, loc, rz, bevel)


# --- the keel's own planes (lg_hull: forefoot, stern rake, chamfer) --------------------

FORE_N = _unit((0.53, 0, -0.848))        # forefoot plane through (60, 0, -44)
STERN_N = _unit((-0.574, 0, -0.819))     # stern rake plane through (-78, 0, -44)
BOTTOM = -44.0
PLATE = -44.6                            # underside of lg_hull's bottom plates


def fore_z(x):
    return BOTTOM+(x-60)*0.53/0.848


def stern_z(x):
    return BOTTOM+(-78-x)*0.574/0.819


def wall_y(x):
    """The keel wall's |Y| (VENTRAL_PORT between X 70 and -60)."""
    return 60.0+(70.0-x)/130.0*2.0


def chamfer_point(x, sy, s, off):
    """A point on the port/starboard chamfer at X x, s along the slope from its foot, off along its outward normal."""
    yb = wall_y(x)-12.0
    r2 = 2**-.5
    return (x, sy*(yb+s*r2+off*r2), BOTTOM+s*r2-off*r2)


def slope_point(plane, x, y, off):
    n = FORE_N if plane == 'fore' else STERN_N
    z = fore_z(x) if plane == 'fore' else stern_z(x)
    return (x+n[0]*off, y, z+n[2]*off)


def slope_hexa(plane, x0, x1, y0, y1, o0, o1, o0b=None, o1b=None):
    """Box lying on a rake plane: X x0..x1, Y y0..y1, from offset o0 to o1 off the plane
    (o*b give the offsets at x1 when they differ, for wedges)."""
    o0b = o0 if o0b is None else o0b
    o1b = o1 if o1b is None else o1b
    c = [slope_point(plane, x0, y0, o0), slope_point(plane, x1, y0, o0b), slope_point(plane, x1, y1, o0b),
         slope_point(plane, x0, y1, o0), slope_point(plane, x0, y0, o1), slope_point(plane, x1, y0, o1b),
         slope_point(plane, x1, y1, o1b), slope_point(plane, x0, y1, o1)]
    return hexa(c)


def chamfer_hexa(x0, x1, sy, s0, s1, o0, o1):
    c = [chamfer_point(x0, sy, s0, o0), chamfer_point(x1, sy, s0, o0), chamfer_point(x1, sy, s1, o0),
         chamfer_point(x0, sy, s1, o0), chamfer_point(x0, sy, s0, o1), chamfer_point(x1, sy, s0, o1),
         chamfer_point(x1, sy, s1, o1), chamfer_point(x0, sy, s1, o1)]
    return hexa(c)


# Layout (x, |y|) of the belly's fittings.
GIRDER_RUNS = [(66.0, -14.2), (-49.8, -84.0)]           # parted by the cargo well
GIRDER_HW, GIRDER_D = 5.0, 6.0
FRAMES_FORE = [57.0, 44.5, 28.0, 12.0, -4.5]           # transverse belly frames, X
FRAMES_AFT = [-58.5, -70.0]
MAG_HATCHES = [36.0, 20.0, 4.0]                          # X centres, |Y| 21
MAG_Y, MAG_HX, MAG_HY = 21.0, 7.0, 7.5
WELL = (-50.0, -14.0, 15.0)                              # x0, x1, half width
WELL_WALL, WELL_DEPTH = 2.8, 6.4
BOAT_PORTS = [(-32.0, 26.0)]
HARDPOINTS = [(52.0, 27.0), (-66.0, 24.0)]
BOW_HARDPOINTS = [(112.0, 38.0)]
COVERS = [51.0, 36.0, 20.0, 4.0]                         # |Y| 33.5
COURSE = (-12.0, 56.0, 38.5, 48.5)
BLISTERS = [45.5, 27.5, 9.5]
RADIATOR_BAYS = [(-60.0, -47.0), (-45.0, -32.0), (-30.0, -17.0)]
TRUNKS = [(40.5, 1.25), (43.4, 1.0), (46.0, .8)]         # |Y|, radius
FORE_POCKET = (69.6, 78.4, 6.0, 23.0)
STERN_POCKETS = [(-87.4, -96.6, 6.0, 24.0), (-99.4, -108.6, 6.0, 22.0)]


def build(kit):
    lamp_white = material('Ventral lamp white', (.2, .2, .2), 0, .4, emission=(1.0, .95, .88), strength=8.0)
    lamp_amber = material('Ventral lamp amber', (.2, .1, .02), 0, .4, emission=(1.0, .55, .12), strength=8.0)
    keel = bpy.data.objects['Hull ventral keel']
    cut_pockets(kit, keel)
    under = Under(['Hull ventral keel', 'Hull ventral keel bottom plates', 'Hull fore body', 'Hull aft body'])
    keel_only = Under(['Hull ventral keel'])

    build_girder(kit, keel_only)
    build_course(kit, under)
    build_frames(kit, under)
    build_magazine_hatches(kit, under)
    build_cargo_well(kit, under, lamp_amber)
    build_boat_ports(kit, under)
    build_hardpoints(kit, under)
    build_covers(kit, under)
    build_blisters(kit)
    build_radiator_bays(kit)
    build_trunks(kit, keel_only)
    build_aft_radiators(kit, under)
    build_engine_underside(kit)
    build_forefoot(kit, keel_only)
    build_stern_pockets(kit)
    build_girder_lamps(kit, keel_only, lamp_white)


def cut_pockets(kit, keel):
    """One boolean: the forefoot sensor bays, the stern rake louvre pockets and the chamfer radiator bays."""
    cut = Acc()
    for sy in (1, -1):
        x0, x1, y0, y1 = FORE_POCKET
        cut.add(*slope_hexa('fore', x0, x1, sy*y0, sy*y1, -2.4, 3.0))
        for x0, x1, y0, y1 in STERN_POCKETS:
            cut.add(*slope_hexa('stern', x0, x1, sy*y0, sy*y1, -2.2, 3.0))
        for x0, x1 in RADIATOR_BAYS:
            cut.add(*chamfer_hexa(x0, x1, sy, 2.4, 14.6, -2.4, 3.0))
    boolean(keel, cut.build(kit, 'Temporary ventral pocket cutter', kit.dark))


def build_girder(kit, cast):
    """The keel girder: segmented box modules over a dark core, with web gussets on the flat."""
    mods, core = Acc(), Acc()
    prof = [(-GIRDER_HW, .6), (GIRDER_HW, .6), (GIRDER_HW, -4.6), (3.4, -GIRDER_D), (-3.4, -GIRDER_D), (-GIRDER_HW, -4.6)]
    for xa, xb in GIRDER_RUNS:
        pts = []
        x = xa
        while x >= xb-1e-6:
            z = cast.up(x, 0.0)
            if z is not None:
                pts.append((x, 0.0, z))
            x -= 2.0
        if pts[-1][0] > xb+.5:
            pts.append((xb, 0.0, cast.up(xb, 0.0)))
        inner = [p for p in pts if xb+1.0 < p[0] < xa-1.0]
        core.add(*sweep_x(inner, [(-3.8, .5), (3.8, .5), (3.8, -GIRDER_D+.8), (-3.8, -GIRDER_D+.8)]))
        # Modules about 12 long with 1.0 seams, chamfered end caps.
        total = xa-xb
        n = max(1, round(total/12.0))
        step = total/n
        for i in range(n):
            a, b = xa-i*step-(.5 if i else 0), xa-(i+1)*step+(.5 if i < n-1 else 0)
            seg = [p for p in pts if b < p[0] < a]
            st = [(a, 0.0, cast.up(a, 0.0))]+seg+[(b, 0.0, cast.up(b, 0.0))]
            st = [st[0], (st[0][0]-.6, 0.0, cast.up(st[0][0]-.6, 0.0))]+st[1:-1]+[(st[-1][0]+.6, 0.0, cast.up(st[-1][0]+.6, 0.0)), st[-1]]
            st = sorted({p[0]: p for p in st}.values(), key=lambda p: -p[0])
            m = len(prof)
            verts = []
            for j, (x, y, z) in enumerate(st):
                pr = hang_caps(prof, .82) if j in (0, len(st)-1) else prof
                verts += [(x, y+dy, z+dz) for dy, dz in pr]
            s = len(st)
            faces = [tuple(range(m)), tuple(range((s-1)*m, s*m))]
            faces += [(j*m+q, j*m+(q+1) % m, (j+1)*m+(q+1) % m, (j+1)*m+q) for j in range(s-1) for q in range(m)]
            mods.add(verts, faces)
    mods.build(kit, 'Ventral keel girder', kit.plate, bevel=.12)
    core.build(kit, 'Ventral keel girder core', kit.dark)


def build_frames(kit, cast):
    """Transverse belly frames from the girder's flank to the armour course (fore) or the trunks (aft):
    the hull's ribs standing proud of the bottom, coffering the belly round the hatches."""
    acc = Acc()
    for xs, y1 in ((FRAMES_FORE, COURSE[2]+.4), (FRAMES_AFT, TRUNKS[0][0]-1.6)):
        for x in xs:
            for sy in (1, -1):
                z = cast.seat(x-.9, x+.9, sy*4, sy*y1)
                y0 = GIRDER_HW-.4
                prof = [(-.9, .3), (.9, .3), (.9, -1.5), (.4, -2.1), (-.4, -2.1), (-.9, -1.5)]
                pts = [(x, sy*y0, z), (x, sy*(y1-.8), z), (x, sy*y1, z+.6)]
                verts = [(px+dx, py, pz+dz) for px, py, pz in pts for dx, dz in prof]
                m, n = len(prof), len(pts)
                faces = [tuple(range(m)), tuple(range((n-1)*m, n*m))]
                faces += [(j*m+i, j*m+(i+1) % m, (j+1)*m+(i+1) % m, (j+1)*m+i) for j in range(n-1) for i in range(m)]
                acc.add(verts, faces)
    acc.build(kit, 'Ventral belly frames', kit.plate, bevel=.1)


def build_course(kit, cast):
    """Two stepped armour courses along each bottom edge, segmented over a dark seam underlay."""
    x0, x1, y0, y1 = COURSE
    top = cast.seat(x0, x1, y0, y1) + .3
    base = [(-5.0, top), (5.0, top), (5.0, top-.8), (3.8, top-2.0), (-5.0, top-2.0)]         # d outboard +
    step = [(-3.0, top-1.8), (3.6, top-1.8), (3.6, top-2.5), (2.6, top-3.3), (-3.0, top-3.3)]
    crs, und = Acc(), Acc()
    for sy in (1, -1):
        yc = sy*(y0+y1)/2
        n = round((x1-x0)/13.0)
        st = (x1-x0)/n
        for i in range(n):
            a, b = x0+i*st+(.5 if i else 0), x0+(i+1)*st-(.5 if i < n-1 else 0)
            for pr in (base, step):
                p = [(sy*d, z) for d, z in pr]
                if sy < 0:
                    p = list(reversed(p))
                pts = [(a, yc, 0.0), (a+.7, yc, 0.0), (b-.7, yc, 0.0), (b, yc, 0.0)]
                m = len(p)
                verts = []
                for j, (x, y, z) in enumerate(pts):
                    q = hang_caps(p, .85) if j in (0, 3) else p
                    verts += [(x, y+dy, z+dz) for dy, dz in q]
                faces = [tuple(range(m)), tuple(range(3*m, 4*m))]
                faces += [(j*m+k, j*m+(k+1) % m, (j+1)*m+(k+1) % m, (j+1)*m+k) for j in range(3) for k in range(m)]
                crs.add(verts, faces)
        und.add(*box(x0+.2, x1-.2, sy*(y0+.4), sy*(y1-.6), top, top-1.4))
    crs.build(kit, 'Ventral armour course', kit.orange, bevel=.1)
    und.build(kit, 'Ventral armour course seam underlay', kit.dark)


def build_magazine_hatches(kit, cast):
    hx, hy, w = MAG_HX, MAG_HY, 1.8
    frame, doors = Acc(), Acc()
    frame.add(*ring(rect(-hx, hx, -hy, hy), [(0, .3), (0, -1.9), (.7, -2.6), (w, -2.6), (w, .3)]))
    for cx in (-hx+.9, hx-.9):            # dogs at the corners
        for cy in (-hy+.9, hy-.9):
            frame.add(*box(cx-.7, cx+.7, cy-.7, cy+.7, -2.5, -3.3))
    ix, iy = hx-w, hy-w
    for sy in (1, -1):                     # two leaves meeting on the hatch's centreline
        doors.add(*box(-ix, ix, sy*.15, sy*iy, -.6, -1.25))
        for rx in (-ix*.5, ix*.5):         # stiffeners
            doors.add(*box(rx-.4, rx+.4, sy*.6, sy*(iy-.6), -1.25, -1.7))
        for kx in (-ix+.4, -.9, ix-2.2):   # hinge knuckles along the outer edge
            frame.add(*tube([(kx, sy*(iy-.45), -1.35), (kx+1.8, sy*(iy-.45), -1.35)], .45, 8))
    spots = []
    for sy, side in ((1, 'port'), (-1, 'starboard')):
        for k, x in enumerate(MAG_HATCHES, 1):
            z = cast.seat(x-hx, x+hx, sy*MAG_Y-hy, sy*MAG_Y+hy)
            spots.append((f'{side} {k}', (x, sy*MAG_Y, z), 0.0))
    place_all(kit, 'Ventral magazine hatch', [('frame', frame, kit.steel), ('doors', doors, kit.plate)], spots)


def clamp_proto():
    """A docking clamp hanging open: base on the frame's underside, an arm down and inboard, a jaw."""
    acc = Acc()
    acc.add(*box(-1.6, 1.6, -1.4, 1.4, .2, -1.2))
    acc.add(*tube([(0, -1.6, -1.0), (0, 1.6, -1.0)], .7, 8))           # trunnion
    a0, a1 = (0, 0, -1.0), (3.6, 0, -5.2)
    acc.add(*beam(a0, a1, 1.6, 1.2))
    acc.add(*beam((3.0, 0, -5.4), (5.2, 0, -4.6), 1.8, 1.0))             # jaw
    acc.add(*box(4.5, 5.6, -1.0, 1.0, -4.0, -5.3))                       # jaw pad
    acc.add(*beam((-.4, 0, -.6), (2.0, 0, -3.6), .7, .7))               # ram
    return acc


def build_cargo_well(kit, cast, lamp):
    x0, x1, hw = WELL
    z = cast.seat(x0, x1, -hw, hw)
    frame, doors, rails = Acc(), Acc(), Acc()
    W, D = WELL_WALL, WELL_DEPTH
    frame.add(*ring(rect(x0, x1, -hw, hw), [(0, z+.3), (0, z-D+1.0), (1.0, z-D), (W, z-D), (W, z+.3)]))
    ix0, ix1, iy = x0+W, x1-W, hw-W
    for sy in (1, -1):
        doors.add(*box(ix0, ix1, sy*.2, sy*iy, z-1.9, z-2.7))
        xx = ix0+3.0
        while xx < ix1-2:
            doors.add(*box(xx-.45, xx+.45, sy*.8, sy*(iy-.8), z-2.7, z-3.2))
            xx += 5.3
        for xx in (ix0+3, (ix0+ix1)/2-1, ix1-5):       # hinge knuckles on the long edges
            frame.add(*tube([(xx, sy*(iy-.5), z-2.6), (xx+2.2, sy*(iy-.5), z-2.6)], .55, 8))
        # Guide rails for a supply boat's arms, outboard of the long sides.
        for a, b in ((16.6, 17.2), (17.9, 18.5)):
            rails.add(*box(x0-4, x1+4, sy*a, sy*b, z+.3, z-2.1))
        rails.add(*box(x0-4, x1+4, sy*16.6, sy*18.5, z+.3, z-.6))
        for xx in (x0-4.6, x1+4.6):                           # rail end stops
            rails.add(*box(xx-.6, xx+.6, sy*16.2, sy*18.9, z+.3, z-2.6))
    doors.add(*box(ix0, ix1, -.25, .25, z-1.75, z-1.9))         # seam strip over the leaves' meeting line
    frame.build(kit, 'Ventral cargo well frame', kit.steel, bevel=.1)
    doors.build(kit, 'Ventral cargo well doors', kit.plate)
    rails.build(kit, 'Ventral cargo well guide rails', kit.steel)
    zb = z-D
    spots = []
    for k, (cx, cy, rz) in enumerate([(x0+1.6, hw-1.1, -math.pi/4), (x1-1.6, hw-1.1, -3*math.pi/4),
                                      (x0+1.6, -hw+1.1, math.pi/4), (x1-1.6, -hw+1.1, 3*math.pi/4),
                                      ((x0+x1)/2, hw-1.1, -math.pi/2), ((x0+x1)/2, -hw+1.1, math.pi/2)], 1):
        spots.append((str(k), (cx, cy, zb), rz))
    place_all(kit, 'Ventral docking clamp', [('', clamp_proto(), kit.steel)], spots)
    lp = Acc()
    lp.add(*box(-.45, .45, -.45, .45, .1, -.5))
    spots = [(str(k), (cx, cy, zb), 0.0) for k, (cx, cy) in
             enumerate([(x0+.4, hw-.4), (x1-.4, hw-.4), (x0+.4, -hw+.4), (x1-.4, -hw+.4)], 1)]
    place_all(kit, 'Ventral lamp cargo well', [('', lp, lamp)], spots)


def build_boat_ports(kit, cast):
    collar, hatch = Acc(), Acc()
    collar.add(*lathe(0, 0, [(5.6, .3), (5.6, -1.7), (4.9, -2.5), (4.0, -2.5), (4.0, .3)], 24))
    for k in range(6):
        a = 2*math.pi*k/6
        cx, cy = 4.8*math.cos(a), 4.8*math.sin(a)
        collar.add(*box(cx-.6, cx+.6, cy-.6, cy+.6, -2.4, -3.1))
    hatch.add(*cyl(0, 0, 4.1, -1.0, -1.5, 20))
    hatch.add(*cyl(0, 0, 1.3, -1.4, -2.1, 10))
    spots = []
    for sy, side in ((1, 'port'), (-1, 'starboard')):
        for x, y in BOAT_PORTS:
            z = cast.seat(x-5.6, x+5.6, sy*y-5.6, sy*y+5.6)
            spots.append((side, (x, sy*y, z), 0.0))
    place_all(kit, 'Ventral boat port', [('collar', collar, kit.steel), ('hatch', hatch, kit.dark)], spots)


def hardpoint_proto():
    acc = Acc()
    acc.add(*hexa([(-2.5, -2.5, .3), (2.5, -2.5, .3), (2.5, 2.5, .3), (-2.5, 2.5, .3),
                   (-1.7, -1.7, -2.0), (1.7, -1.7, -2.0), (1.7, 1.7, -2.0), (-1.7, 1.7, -2.0)]))
    acc.add(*cyl(0, 0, 1.4, -1.9, -2.8, 12))
    for sx in (-1, 1):
        acc.add(*box(sx*.6, sx*1.0, -.55, .55, -2.7, -4.1))
    acc.add(*tube([(-1.1, 0, -3.6), (1.1, 0, -3.6)], .3, 6))
    return acc


def build_hardpoints(kit, cast):
    spots = []
    for sy, side in ((1, 'port'), (-1, 'starboard')):
        for tag, pts in (('belly', HARDPOINTS), ('bow', BOW_HARDPOINTS)):
            for k, (x, y) in enumerate(pts, 1):
                z = cast.seat(x-2.5, x+2.5, sy*y-2.5, sy*y+2.5)
                spots.append((f'{tag} {side} {k}' if len(pts) > 1 else f'{tag} {side}', (x, sy*y, z), 0.0))
    place_all(kit, 'Ventral grapple hardpoint', [('', hardpoint_proto(), kit.steel)], spots)


def build_covers(kit, cast):
    fr, lid = Acc(), Acc()
    for sy in (1, -1):
        for x in COVERS:
            y = sy*33.5
            z = cast.seat(x-2, x+2, y-2, y+2)
            fr.add(*ring(rect(x-2.1, x+2.1, y-2.1, y+2.1), [(0, z+.3), (0, z-.8), (.6, z-.8), (.6, z+.3)]))
            lid.add(*box(x-1.5, x+1.5, y-1.5, y+1.5, z, z-.45))
            for dx in (-1.1, 1.1):
                for dy in (-1.1, 1.1):
                    lid.add(*box(x+dx-.2, x+dx+.2, y+dy-.2, y+dy+.2, z-.4, z-.65))
    fr.build(kit, 'Ventral service cover frames', kit.steel)
    lid.build(kit, 'Ventral service cover lids', kit.plate)


def build_blisters(kit):
    """Strapped tank blisters on the forward chamfers, sunk into them."""
    r = 3.6
    body, straps = Acc(), Acc()
    body.add(*lathe_x([(-8.5, .2), (-8.5, 1.4), (-6.4, r), (6.4, r), (8.5, 1.4), (8.5, .2)], 0, 0, 16))
    for sx in (-3.6, 0.0, 3.6):
        straps.add(*lathe_x([(sx-.45, r-.15), (sx+.45, r-.15), (sx+.45, r+.35), (sx-.45, r+.35)], 0, 0, 16))
    for sx in (-8.6, 8.6):    # end bosses
        straps.add(*lathe_x([(sx-.5, .2), (sx+.5, .2), (sx+.5, .9), (sx-.5, .9)], 0, 0, 10))
    spots = []
    for sy, side in ((1, 'port'), (-1, 'starboard')):
        for k, x in enumerate(BLISTERS, 1):
            spots.append((f'{side} {k}', chamfer_point(x, sy, 8.5, 1.4), 0.0))
    place_all(kit, 'Ventral tank blister', [('', body, kit.plate), ('straps', straps, kit.steel)], spots)


def build_radiator_bays(kit):
    """Fin stacks and header pipes in the radiator bays cut into the aft chamfers."""
    fins, hdr = Acc(), Acc()
    for sy in (1, -1):
        for x0, x1 in RADIATOR_BAYS:
            x = x0+.8
            while x < x1-.6:
                fins.add(*chamfer_hexa(x-.15, x+.15, sy, 2.6, 14.4, -2.5, -.25))
                x += 1.05
            for s in (3.8, 13.2):
                hdr.add(*tube([chamfer_point(x0+.2, sy, s, -1.2), chamfer_point(x1-.2, sy, s, -1.2)], .75, 8))
            # Transverse frame bars across the bay mouth.
            for xx in (x0+.15, (x0+x1)/2, x1-.15):
                hdr.add(*chamfer_hexa(xx-.35, xx+.35, sy, 2.0, 15.0, -2.5, .3))
    fins.build(kit, 'Ventral radiator fins', kit.steel)
    hdr.build(kit, 'Ventral radiator headers', kit.plate)


def build_trunks(kit, cast):
    """Coolant trunks from the radiator bays aft along the chamfer foot and up the stern rake."""
    pipes, coll, sad = Acc(), Acc(), Acc()
    for sy in (1, -1):
        ends = []
        for y, r in TRUNKS:
            pts = []
            x = -14.0
            last = None
            while x > -130:
                z = cast.up(x, sy*y)
                if z is None:
                    break
                z -= r+1.15
                if last is not None and z < last-.01:
                    z = last          # never step back down
                if z > -23.0:
                    break
                pts.append((x, sy*y, z))
                last = z
                x -= 2.0
            pipes.add(*tube(pts, r, 12))
            ends.append(pts[-1])
            # Flange collars and saddles every few modules.
            for i in range(2, len(pts)-1, 4):
                a, b = pts[i-1], pts[i+1]
                p = pts[i]
                coll.add(*tube([_add(p, _unit(_sub(a, b)), .45), _add(p, _unit(_sub(a, b)), -.45)], r+.4, 12))
                zs = cast.up(p[0], p[1])
                if zs is not None:
                    sad.add(*box(p[0]-.6, p[0]+.6, p[1]-r-.3, p[1]+r+.3, zs+.3, p[2]-.2))
        # Manifold where the bundle leaves the radiator bays, closing the armour course's aft end.
        z = cast.up(-14.0, sy*43.0)
        sad.add(*box(-11.6, -16.4, sy*38.6, sy*48.4, z+.3, z-4.2))
        sad.add(*box(-12.0, -16.0, sy*48.0, sy*52.0, z+2.8, z-2.0))
        # Header box where the bundle enters the hull on the stern rake.
        xe = max(p[0] for p in ends)
        ze = max(p[2] for p in ends)
        sad.add(*box(xe+1.2, xe-3.0, sy*38.4, sy*48.2, ze-2.0, ze+4.0))
    pipes.build(kit, 'Ventral coolant trunks', kit.steel)
    coll.build(kit, 'Ventral coolant trunk collars', kit.plate)
    sad.build(kit, 'Ventral coolant trunk saddles', kit.plate)


def skid(fr, fins, x0, x1, y0, y1, ztop, depth, pitch=1.15):
    """A radiator skid: frame ring and fore-and-aft fins hanging below a flat underside."""
    fr.add(*ring(rect(min(x0, x1), max(x0, x1), min(y0, y1), max(y0, y1)),
                 [(0, ztop+.3), (0, ztop-depth+.7), (.7, ztop-depth), (1.3, ztop-depth), (1.3, ztop+.3)]))
    ya, yb = sorted((y0, y1))
    y = ya+1.3+pitch*.6
    while y < yb-1.3-pitch*.3:
        fins.add(*box(min(x0, x1)+1.4, max(x0, x1)-1.4, y-.17, y+.17, ztop+.2, ztop-depth+.5))
        y += pitch
    for xx in ((x0+x1)/2,):
        fins.add(*box(xx-.4, xx+.4, ya+1.0, yb-1.0, ztop+.2, ztop-depth+.2))


def build_aft_radiators(kit, cast):
    """Radiator skids under the aft body (below the machinery bed), with header stubs into the keel wall."""
    fr, fins, hdr = Acc(), Acc(), Acc()
    for sy in (1, -1):
        z = cast.seat(-94.0, -70.0, sy*64.0, sy*77.0)
        skid(fr, fins, -94.0, -70.0, sy*63.6, sy*77.0, z, 3.4)
        for xx in (-73.5, -90.5):
            hdr.add(*tube([(xx, sy*58.5, z-1.6), (xx, sy*64.4, z-1.6)], .8, 10))
    eb = lg_engines.BLOCK[0][1]     # the engine block's keel (lg_engines builds after this module)
    for sy in (1, -1):
        skid(fr, fins, -111.0, -100.6, sy*56.0, sy*77.0, eb, 3.0)
        hdr.add(*tube([(-100.0, sy*51.0, eb-1.5), (-100.0, sy*77.6, eb-1.5)], .8, 10))
    fr.build(kit, 'Ventral radiator skid frames', kit.steel)
    fins.build(kit, 'Ventral radiator skid fins', kit.steel)
    hdr.build(kit, 'Ventral radiator skid headers', kit.plate)


def build_engine_underside(kit):
    """Stepped skid plates under the outer engine housings (lg_engines' housing keel, built after this)."""
    hk = lg_engines.HOUSING[0][1]
    acc = Acc()
    for sy in (1, -1):
        acc.add(*box(-114.4, -124.2, sy*67.2, sy*76.6, hk+.3, hk-1.1))
        acc.add(*box(-114.6, -123.8, sy*68.0, sy*75.8, hk-1.0, hk-2.0))
        for xx in (-116.5, -121.9):
            acc.add(*box(xx-.5, xx+.5, sy*67.0, sy*76.8, hk-.9, hk-1.6))
        # A grapple eye at the housing's aft corner.
        acc.add(*box(-124.0, -125.6, sy*71.4, sy*72.6, hk-1.0, hk-2.6))
    acc.build(kit, 'Ventral engine housing skids', kit.plate, bevel=.08)


def build_forefoot(kit, cast):
    """The forefoot: phased-array tiles in the sensor bays, stepped armour shingles on its upper courses."""
    tiles, sh = Acc(), Acc()
    for sy in (1, -1):
        x0, x1, y0, y1 = FORE_POCKET
        nx, ny = 2, 4
        for i in range(nx):
            for j in range(ny):
                a = x0+.5+(x1-x0-1.0)*i/nx
                b = x0+.5+(x1-x0-1.0)*(i+1)/nx-.5
                c = y0+.5+(y1-y0-1.0)*j/ny
                d = y0+.5+(y1-y0-1.0)*(j+1)/ny-.5
                tiles.add(*slope_hexa('fore', a, b, sy*c, sy*d, -2.5, -1.2))
        # A comms mast pair standing in the bay's forward end.
        for yy in (y0+3.0, y1-3.0):
            p0 = slope_point('fore', x1-1.2, sy*yy, -2.3)
            tiles.add(*tube([p0, _add(p0, FORE_N, 3.2)], .35, 6))
        # Shingles: each course a wedge, thick at its aft edge, split across the face.
        for xa, xb in ((81.3, 85.9), (86.3, 90.7), (93.3, 97.7)):
            yy = 2.5
            while yy < 60:
                w = 8.4
                ya, yb = yy, yy+w
                ok = True
                for x in (xa, xb):
                    for y in (ya, yb):
                        z = cast.up(x, sy*y)
                        if z is None or abs(z-fore_z(x)) > .3:
                            ok = False
                if ok:
                    sh.add(*slope_hexa('fore', xa, xb, sy*ya, sy*yb, -.3, 1.7, -.3, .5))
                yy += w+.8
    tiles.build(kit, 'Ventral sensor array tiles', kit.steel)
    sh.build(kit, 'Ventral forefoot armour shingles', kit.plate, bevel=.08)


def build_stern_pockets(kit):
    lv = Acc()
    for sy in (1, -1):
        for x0, x1, y0, y1 in STERN_POCKETS:
            hi, lo = max(x0, x1), min(x0, x1)
            x = hi-.6
            while x > lo+.6:
                # Slats leaning aft, from the pocket floor to just short of flush.
                c = [slope_point('stern', x, sy*(y0+.2), -2.1), slope_point('stern', x-.35, sy*(y0+.2), -2.1),
                     slope_point('stern', x-.35, sy*(y1-.2), -2.1), slope_point('stern', x, sy*(y1-.2), -2.1),
                     slope_point('stern', x-1.0, sy*(y0+.2), -.2), slope_point('stern', x-1.35, sy*(y0+.2), -.2),
                     slope_point('stern', x-1.35, sy*(y1-.2), -.2), slope_point('stern', x-1.0, sy*(y1-.2), -.2)]
                lv.add(*hexa(c))
                x -= 1.3
            ym = (y0+y1)/2
            lv.add(*slope_hexa('stern', hi, lo, sy*(ym-.35), sy*(ym+.35), -2.2, -.1))   # centre mullion
    lv.build(kit, 'Ventral stern discharge louvres', kit.steel)


def build_girder_lamps(kit, cast, lamp):
    for tag, x, dx in (('fore', GIRDER_RUNS[0][0], -1.0), ('aft', GIRDER_RUNS[1][1], 1.0)):
        z = cast.up(x+dx*.6, 0.0)
        acc = Acc()
        acc.add(*box(x+dx*.9, x-dx*.25, -.55, .55, z-2.2, z-3.3))
        acc.build(kit, f'Ventral lamp girder {tag}', lamp)
