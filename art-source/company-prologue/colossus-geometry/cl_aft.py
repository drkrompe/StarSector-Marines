"""Colossus waist and aft body: the keel spine, the aft machinery body and the open lattice frames.

The keel spine carries the cargo body's loads aft through the narrow waist
(X -18..-33) into the aft machinery body, which stands between the drive pods
and carries the bridge deckhouse (cl_bridge). Open lattice trusses tie the
aft side modules and the drive pods to the spine. Planform from colossus.png;
heights proposed. Symmetric about cl_hull.YC.

Pass 2 detail, all read off colossus.png at 8-13x:
- the spine's top is a ladder: two pale rails at Y rel +-7 over a dark
  recessed well, bridged by rungs, with dark slot pockets in the outer channels;
- each truss is an open space frame (chords, verticals, Warren lacing) with a
  dark walkway grating at mid-depth and a pipe run on it, so the plan stays
  as opaque as the sprite's dark lattice while every oblique view sees through;
- the aft body is segmented armour: three transverse seam rings, a strata
  groove round the walls, two longitudinal deck seams, bolted service hatches
  and louvred vents on the outboard shelves;
- pale ribs across the striped band forward of the bridge (X -33..-41);
- the stern below the bridge: two pale equipment housings, the dark radiator
  grille (fore-aft fins in a recessed well, continued as vertical slats down
  the aft faces), a bolted centre hatch and the small tail block (X -86..-93.5,
  Y rel +-5.5) with the two stern lamps the sprite's amber dots suggest.

The scale solids ('Hull keel spine', 'Hull aft machinery body') stay closed:
their seams and wells are shallow MANIFOLD grooves, nothing is hollowed.
"""
import math
import bmesh
import bpy
from mathutils import Vector
from cl_kit import prism, section, inset, boolean, material
from cl_hull import AFT_KEEL, AFT_DECK, SIDES, YC, plan, y

AFT_HALF = [(-30, 34), (-80, 35), (-84, 27), (-87, 16), (-90, 9), (-92, 5)]
SEAM = .6       # seam groove width and depth


# --- small geometry helpers (used by cl_bridge too) ---

def beam(verts, faces, a, b, w, h=None, side=None):
    """Append a rectangular bar from a to b: w across `side`, h across the third axis."""
    h = w if h is None else h
    a, b = Vector(a), Vector(b)
    d = (b-a).normalized()
    s = Vector(side) if side is not None else (Vector((0, 0, 1)) if abs(d.z) < .9 else Vector((1, 0, 0)))
    s = (s-d*s.dot(d)).normalized()
    t = d.cross(s).normalized()
    n = len(verts)
    for p in (a, b):
        for i, j in ((-1, -1), (1, -1), (1, 1), (-1, 1)):
            verts.append(tuple(p+s*(i*w/2)+t*(j*h/2)))
    faces += [(n, n+1, n+2, n+3), (n+4, n+7, n+6, n+5),
              (n, n+4, n+5, n+1), (n+1, n+5, n+6, n+2), (n+2, n+6, n+7, n+3), (n+3, n+7, n+4, n)]


def block(verts, faces, lo, hi):
    """Append an axis-aligned box from corner lo to corner hi."""
    (x0, y0, z0), (x1, y1, z1) = lo, hi
    beam(verts, faces, (x0, (y0+y1)/2, (z0+z1)/2), (x1, (y0+y1)/2, (z0+z1)/2), y1-y0, z1-z0, (0, 1, 0))


def solid(kit, name, verts, faces, mat, bevel=0):
    """One object holding every bar appended to verts/faces, normals recalculated outward."""
    me = bpy.data.meshes.new(name)
    me.from_pydata(verts, [], faces)
    bm = bmesh.new()
    bm.from_mesh(me)
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    bm.to_mesh(me)
    bm.free()
    me.update()
    ob = bpy.data.objects.new(name, me)
    bpy.context.collection.objects.link(ob)
    return kit.finish(ob, name, mat, bevel)


def seat(kit, x, yy, fallback):
    hit = kit.surface(x, yy)
    return hit[0].z if hit else fallback


def lamp(kit, name, loc, r=.4):
    return kit.cylinder(name, loc, r, r*1.1, kit.lamp, vertices=10, bevel=.06)


def bolted_plate(kit, name, cx, cy, z, sx, sy, mat, pitch=1.4, t=.35, down=False):
    """A service cover laid on a deck at height z (or under a keel, down=True), with a ring of bolt heads."""
    k = -1 if down else 1
    kit.box(name, (cx, cy, z+k*t/2), (sx, sy, t), mat, bevel=.08)
    v, f = [], []
    nx, ny = max(2, round(sx/pitch)), max(2, round(sy/pitch))
    pts = set()
    for i in range(nx+1):
        for yy in (-1, 1):
            pts.add((cx-sx/2+.45+(sx-.9)*i/nx, cy+yy*(sy/2-.45)))
    for j in range(ny+1):
        for xx in (-1, 1):
            pts.add((cx+xx*(sx/2-.45), cy-sy/2+.45+(sy-.9)*j/ny))
    for px, py in pts:
        block(v, f, (px-.15, py-.15, z+k*t-(.18 if down else 0)), (px+.15, py+.15, z+k*t+(0 if down else .18)))
    solid(kit, name+' bolts', v, f, kit.steel)


def louvre_vent(kit, name, cx, cy, z, sx, sy, n=5):
    """A dark vent box with pale louvre blades across X."""
    kit.box(name, (cx, cy, z+.5), (sx, sy, 1.0), kit.dark, bevel=.08)
    v, f = [], []
    for i in range(n):
        px = cx-sx/2+.5+(sx-1)*i/(n-1)
        beam(v, f, (px, cy-sy/2+.3, z+1.15), (px, cy+sy/2-.3, z+1.15), .35, .3, (1, 0, 0))
    block(v, f, (cx-sx/2-.15, cy-sy/2-.15, z), (cx+sx/2+.15, cy+sy/2+.15, z+.25))
    solid(kit, name+' louvres', v, f, kit.pale)


def space_frame(kit, name, x0, x1, y0, y1, z0, z1, bx, by, chord=.9, lace=.5):
    """Open lattice box: top and bottom chord grids, perimeter verticals and Warren lacing in every
    vertical panel, plus a dark walkway grating at mid-depth that keeps the plan opaque."""
    xs = [x0+(x1-x0)*i/bx for i in range(bx+1)]
    ys = [y0+(y1-y0)*j/by for j in range(by+1)]
    v, f = [], []
    for z in (z0, z1):
        for yy in ys:
            beam(v, f, (x0, yy, z), (x1, yy, z), chord)
        for xx in xs:
            beam(v, f, (xx, y0, z), (xx, y1, z), chord)
    for xx in xs:
        for yy in ys:
            if xx in (x0, x1) or yy in (y0, y1):
                beam(v, f, (xx, yy, z0), (xx, yy, z1), chord)
    for yy in ys:      # lacing in the X-running panels
        for i in range(bx):
            a, b = (xs[i], z0), (xs[i+1], z1)
            if i % 2:
                a, b = (xs[i], z1), (xs[i+1], z0)
            beam(v, f, (a[0], yy, a[1]), (b[0], yy, b[1]), lace)
    for xx in xs:      # lacing in the Y-running panels
        for j in range(by):
            a, b = (ys[j], z0), (ys[j+1], z1)
            if j % 2:
                a, b = (ys[j], z1), (ys[j+1], z0)
            beam(v, f, (xx, a[0], a[1]), (xx, b[0], b[1]), lace)
    frame = solid(kit, name, v, f, kit.steel)
    zm = (z0+z1)/2
    v, f = [], []
    block(v, f, (x0, y0, zm-.2), (x1, y1, zm+.2))
    solid(kit, name+' walkway grating', v, f, kit.dark)
    return frame, zm


def pipes(kit, name, runs, r, mat):
    """Pipe runs [(a, b), ...] joined into one object (octagonal section)."""
    v, f = [], []
    for a, b in runs:
        a, b = Vector(a), Vector(b)
        d = (b-a).normalized()
        s = Vector((0, 0, 1)) if abs(d.z) < .9 else Vector((1, 0, 0))
        s = (s-d*s.dot(d)).normalized()
        t = d.cross(s)
        n = len(v)
        for p in (a, b):
            for k in range(8):
                ang = 2*math.pi*k/8+math.pi/8
                v.append(tuple(p+s*(r*math.cos(ang))+t*(r*math.sin(ang))))
        f += [tuple(range(n+7, n-1, -1)), tuple(range(n+8, n+16))]
        f += [(n+k, n+(k+1) % 8, n+8+(k+1) % 8, n+8+k) for k in range(8)]
    return solid(kit, name, v, f, mat)


# --- shallow grooves in the closed scale solids ---

def _groove_box(kit, target, lo, hi, groove_mat):
    v, f = [], []
    block(v, f, lo, hi)
    cutter = solid(kit, 'Temporary groove cutter', v, f, groove_mat)
    boolean(target, cutter)


def _seam_shell(kit, slab_lo, slab_hi, half, keel, deck, keel_c, shoulder_c, groove_mat):
    """A thin cutter: the slab minus the solid offset SEAM inward, so only a skin-deep band remains."""
    v, f = [], []
    block(v, f, slab_lo, slab_hi)
    shell = solid(kit, 'Temporary seam shell', v, f, groove_mat)
    inner = prism(kit, 'Temporary seam core', inset(plan(half), SEAM),
                  section(keel+SEAM, deck-SEAM, keel_c, shoulder_c), groove_mat, bevel=0)
    boolean(shell, inner)
    return shell


def build(kit):
    groove = material('Seam groove shadow', (.05, .045, .045), .4, .8)
    for side, s in SIDES:
        # Waist truss: open space frame tying the aft side module to the spine and the pod.
        frame, zm = space_frame(kit, f'Frame {side} waist truss', -35, -19, *sorted((y(20, s), y(48, s))), -5, 5, 4, 6)
        lo, hi = sorted((y(20.5, s), y(47.5, s)))
        pipes(kit, f'Frame {side} waist truss pipe run',
              [((-22.5, lo, zm+1.1), (-22.5, hi, zm+1.1)), ((-25, lo, zm+1.1), (-25, hi, zm+1.1)),
               ((-28.5, lo, zm+.9), (-28.5, hi, zm+.9))], .8, kit.pale)
        # Pod truss: a long lattice girder between the aft body's flank and the drive pod (Y rel 35..45).
        frame, zm = space_frame(kit, f'Frame {side} pod truss', -72, -32, *sorted((y(34.5, s), y(45, s))), -7, 7, 8, 2)
        lo, hi = sorted((y(37, s), y(42.5, s)))
        pipes(kit, f'Frame {side} pod truss pipe run',
              [((-72, y(38.2, s), zm+1.1), (-32, y(38.2, s), zm+1.1)),
               ((-72, y(40.6, s), zm+1.1), (-32, y(40.6, s), zm+1.1)),
               ((-72, y(42.4, s), zm+.8), (-32, y(42.4, s), zm+.8))], .75, kit.pale)
        # Pod collar truss: the short frame between the aft side module's tail and the pod's nose.
        space_frame(kit, f'Frame {side} pod collar truss', -29, -19, *sorted((y(50, s), y(62, s))), -5, 3, 2, 3)
        # Thrust yoke (owner, pass 3 round 1: the pods push the cargo block like tugs). Heavy
        # struts run from the pod's nose and keel into a bolted thrust pad low on the aft face
        # of the deep side module, so the push visibly lands on the block's mass.
        v, f = [], []
        for r in (53.0, 61.0):
            beam(v, f, (-33.5, y(r, s), -4), (-21.6, y(r, s), -12), 2.2, 2.2, (0, 1, 0))      # nose struts
            beam(v, f, (-38.0, y(r, s), -11), (-21.6, y(r, s), -30), 2.0, 2.0, (0, 1, 0))     # keel struts
        beam(v, f, (-26.0, y(52.0, s), -8.5), (-26.0, y(62.0, s), -8.5), 1.4, 1.4, (1, 0, 0))   # cross ties
        beam(v, f, (-27.5, y(52.0, s), -22.0), (-27.5, y(62.0, s), -22.0), 1.4, 1.4, (1, 0, 0))
        solid(kit, f'Frame {side} thrust yoke', v, f, kit.steel, bevel=.15)
        v, f = [], []
        block(v, f, (-22.2, min(y(50.5, s), y(63.5, s)), -34), (-20.6, max(y(50.5, s), y(63.5, s)), -6))
        solid(kit, f'Frame {side} thrust pad', v, f, kit.dark, bevel=.3)
        v, f = [], []
        for r in (51.6, 62.4):
            for zb in (-32.0, -26.0, -20.0, -14.0, -8.0):
                block(v, f, (-22.6, y(r, s)-.3, zb-.3), (-22.2, y(r, s)+.3, zb+.3))
        solid(kit, f'Frame {side} thrust pad bolts', v, f, kit.pale)

    # --- keel spine: the waist's ladder ---
    spine = prism(kit, 'Hull keel spine', plan([(-12, 20), (-46, 20)]), section(-15, 12, 4, 3), kit.red)
    _groove_box(kit, spine, (-29.6, y(-5.2, 1), 10.4), (-19.4, y(5.2, 1), 13), groove)      # central well
    for side, s in SIDES:
        a, b = sorted((y(10.6, s), y(14.4, s)))
        for x0, x1 in ((-28.6, -24.6), (-23.6, -19.6)):
            _groove_box(kit, spine, (x0, a, 11), (x1, b, 13), groove)                    # slot pockets
    v, f = [], []
    for side, s in SIDES:
        a, b = sorted((y(6, s), y(8.6, s)))
        block(v, f, (-33.5, a, 11.6), (-19.2, b, 13.4))                                  # ladder rails
    for k in range(5):
        x = -20.6-k*2.2
        block(v, f, (x-.45, y(-6.2, 1), 11.3), (x+.45, y(6.2, 1), 12.2))                 # rungs
    solid(kit, 'Hull keel spine ladder rails', v, f, kit.pale, bevel=.08)
    v, f = [], []
    block(v, f, (-29.5, y(-5.1, 1), 10.4), (-19.5, y(5.1, 1), 10.6))
    solid(kit, 'Hull keel spine well floor', v, f, kit.dark)
    v, f = [], []
    for side, s in SIDES:                                                                 # cross struts to the knees
        for x in (-21.5, -27.5):
            beam(v, f, (x, y(19.5, s), 6), (x, y(27, s), 2), .9, .9, (1, 0, 0))
    solid(kit, 'Hull keel spine cross struts', v, f, kit.steel)

    # --- aft machinery body: segmented armour ---
    aft = prism(kit, 'Hull aft machinery body', plan(AFT_HALF), section(AFT_KEEL, AFT_DECK, 5, 4), kit.red)
    for xs in (-41.5, -58.0, -77.5):                                                       # transverse seam rings
        boolean(aft, _seam_shell(kit, (xs-SEAM/2, -100, -40), (xs+SEAM/2, 100, 40), AFT_HALF,
                                 AFT_KEEL, AFT_DECK, 5, 4, groove))
    boolean(aft, _seam_shell(kit, (-120, -100, -3.3), (0, 100, -2.7), AFT_HALF,
                             AFT_KEEL, AFT_DECK, 5, 4, groove))                            # strata groove
    for side, s in SIDES:
        a, b = sorted((y(26.2, s), y(26.8, s)))
        _groove_box(kit, aft, (-75.5, a, AFT_DECK-SEAM), (-35, b, AFT_DECK+1), groove)     # deck seams
        a, b = sorted((y(4.8, s), y(13.6, s)))
        _groove_box(kit, aft, (-86.5, a, AFT_DECK-1.6), (-78.2, b, AFT_DECK+2), groove)     # grille well
        a, b = sorted((y(13.2, s), y(13.8, s)))
        _groove_box(kit, aft, (-84, a, AFT_KEEL-1), (-34, b, AFT_KEEL+SEAM), groove)       # keel seams
    kit.hull += [spine, aft]

    # Striped band forward of the bridge: pale ribs on the red deck (X -33..-41).
    v, f = [], []
    for x in (-33.2, -35.2, -37.2, -39.2, -41.2):
        z = seat(kit, x, YC+9, AFT_DECK)
        block(v, f, (x-.55, y(-20, 1), z-1.2), (x+.55, y(20, 1), z+.45))
    solid(kit, 'Hull aft striped band ribs', v, f, kit.pale, bevel=.06)

    for side, s in SIDES:
        # Edge lamps along the striped band's outboard ends, where the sprite has pale dots.
        for k, x in enumerate((-34.5, -37.5, -40.5)):
            yy = y(21.6, s)
            lamp(kit, f'Hull aft {side} band edge lamp {k+1}', (x, yy, seat(kit, x, yy, AFT_DECK)+.2), .35)
        # Outboard machinery shelves: bolted service hatches and louvred vents.
        for k, x in enumerate((-45, -54.5)):
            bolted_plate(kit, f'Hull aft {side} service hatch {k+1}', x, y(30, s), AFT_DECK, 5.0, 4.2, kit.maroon)
        louvre_vent(kit, f'Hull aft {side} shelf vent', -66.5, y(30.2, s), AFT_DECK, 5.5, 3.6)
        # Stern below the bridge: pale equipment housing either side of the door landing.
        lo, hi = sorted((y(5.2, s), y(11.8, s)))
        kit.box(f'Hull aft {side} stern housing', (-75, (lo+hi)/2, AFT_DECK+1.3), (5, hi-lo, 2.6), kit.pale, bevel=.25)
        # Radiator grille: fore-aft fins standing in the recessed well.
        v, f = [], []
        for k in range(7):
            yy = y(5.6+k*1.25, s)
            block(v, f, (-85.6, yy-.17, AFT_DECK-1.6), (-78.6, yy+.17, AFT_DECK-.1))
        solid(kit, f'Hull aft {side} stern grille fins', v, f, kit.steel)
        # ... continued down the aft faces as vertical slats on a dark backing.
        v, f, bv, bf = [], [], [], []
        for (xa, ra), (xb, rb), n in (((-87, 16), (-90, 9), 6), ((-90, 9), (-92, 5), 3)):
            pa, pb = Vector((xa, y(ra, s), 0)), Vector((xb, y(rb, s), 0))
            e = (pb-pa).normalized()
            out = Vector((-e.y, e.x, 0))
            if out.dot(Vector((-1, 0, 0))) < 0:
                out = -out
            beam(bv, bf, pa+out*.15-e*.2, pb+out*.15+e*.2, .3, 16, out)   # backing plate, Z -8..8
            for i in range(n):
                p = pa+(pb-pa)*((i+.5)/n)+out*.6
                beam(v, f, (p.x, p.y, -7.5), (p.x, p.y, 7.5), 1.0, .4, out)
        solid(kit, f'Hull aft {side} stern face grille', v, f, kit.steel)
        solid(kit, f'Hull aft {side} stern face grille backing', bv, bf, kit.dark)

    # The pale-pink bolted cover between the two grilles.
    bolted_plate(kit, 'Hull aft stern centre hatch', -81.5, YC, AFT_DECK, 6.0, 7.4, kit.pale)
    # The small tail block on the stern centreline, with the two stern lamps the sprite's amber dots suggest.
    kit.box('Hull aft tail block', (-90.2, YC, 1.5), (7, 11, 12), kit.steel, bevel=.3)
    v, f = [], []
    block(v, f, (-94.0, YC-5.8, 6.4), (-88.5, YC+5.8, 7.9))          # pale cap band (the sprite's light corners)
    for z in (-1.5, 1.5):
        block(v, f, (-94.0, YC-5.2, z-.2), (-93.5, YC+5.2, z+.2))   # guard ribs across the aft face
    solid(kit, 'Hull aft tail block cap', v, f, kit.pale, bevel=.05)
    for side, s in SIDES:
        lamp(kit, f'Hull aft {side} stern lamp', (-93.75, y(3.2, s), 5.2), .45).rotation_euler = (0, math.pi/2, 0)
    # Ventral access hatches on the aft body's flat keel.
    for k, x in enumerate((-50, -66)):
        bolted_plate(kit, f'Hull aft ventral hatch {k+1}', x, YC, AFT_KEEL, 7, 9, kit.maroon, down=True)
