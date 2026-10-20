"""Shepherd main structure: the central crew body, the spine, the aft
crossbeam and the aft machinery block between the drive pods.

The sprite (drone_tender.png) is almost a square block: a sand-tan central
body running the whole length, the drone bay down the port side, the repair
gantry down the starboard side, and one wide crossbeam across the stern that
carries both drive pods. The central body is where the crew lives ("internal
volume given to crew comfort"), so it is the deepest section.

Pass 3 (owner review): the bow read too boxy for the sprite's rounded
capsule, so the nose is a superellipse in plan with tangent facets over and
under it, the section has two facets at each shoulder, and the bow fittings
are seated on the rounded nose by horizontal ray-casts.

Pass 2 detail, read off the sprite and the hull's yard life:
  - the dorsal deck aft of the bridge hull (X -9..+4) is laid in bolted
    armour modules over a dark seam underlay, with the two small dark
    hatches the sprite shows near X -5.5 and an access trunk up to the spine
    walkway at its forward port corner (the dark notch at X 3..4);
  - the bright transverse band at X -9..-11 is a structural frame band
    wrapped over the deck, the starboard flank and the spine;
  - aft of the tan crossbeam band (X -14..-16) the sprite goes dark: the
    machinery block's top is exposed in a well (X -27.8..-16.5, cut through
    the body and the crossbeam down to the machinery top) holding the paired
    intake stacks, a reactor access hatch, a pump, piping and a ladder;
  - the port spine carries a grated walkway between handrails, conduits
    along its inboard wall and the dark service notches on its outboard face;
  - the crossbeam carries seams and dark slots on what the pods leave
    visible; the machinery's aft face carries a bolted reactor access hatch,
    louvred intakes and piping between the stern lamp housings (where the
    sprite has them; red to port, green to starboard by convention);
  - two crew airlocks: a vestibule onto the turret deck, and a door on the
    starboard bow quarter stepping down to the gantry base deck;
  - ventral: she rests on her keel on a yard service cradle, so the keel
    carries two skids and eight load-bearing cradle shoes, a ventral cargo
    hatch (cargo 100), the surveying sensor blister and bolted covers.

Names: 'Hull ...'. Pressure hull for the scale is the central body, the
spine and the aft machinery block (build_shepherd.PRESSURE_HULL). Only the
four section volumes are ray-cast seats (kit.hull); every fitting stays off
that list so other modules keep seating on the pass-1 envelopes.
"""
import math
import bmesh
import bpy
from mathutils import Vector
from sh_kit import prism, section, boolean, plane_cut, loft_x, material, seg

# Planform of the central body (CCW, +X bow, +Y port), read off the sprite:
# the tan body is Y -10..+5 forward of the crossbeam, widening aft into the
# machinery between the pods; the long bridge hull rides on it (sh_bridge).
# Pass 3 (owner: the bow read too boxy): the sprite's bow is a rounded capsule,
# so the nose is a superellipse in plan (BOW_*) and a run of tangent facets in
# elevation (BOW_FACETS), and the section carries two facets at each shoulder.
BOW_C, BOW_A, BOW_N = (26.0, -2.5), (9.5, 8.5), 2.6    # plan centre, semi-axes, superellipse exponent
BOW_EL_C, BOW_EL_A = (25.0, -2.0), (10.5, 13.0)        # elevation ellipse (x, z) centre and semi-axes
BOW_FACETS = (22, -22, 48, -46, -68)                   # tangent facet angles on it, deg


def _bow_plan(steps=12):
    (cx, cy), (ax, ay), e = BOW_C, BOW_A, 2/BOW_N
    out = []
    for k in range(steps+1):
        t = math.pi*(-.5+k/steps)
        c, s = math.cos(t), math.sin(t)
        out.append((round(cx+ax*math.copysign(abs(c)**e, c), 3), round(cy+ay*math.copysign(abs(s)**e, s), 3)))
    return out


BODY_PLAN = [(-31, -11), (-12, -11), (6, -11)]+_bow_plan()+[(6, 6), (-12, 9), (-31, 9)]
BODY_KEEL, BODY_DECK = -15.0, 11.0
# Keel chamfer, a second keel facet, the wall, and two shoulder facets (inset, z).
BODY_SECTION = [(4.0, BODY_KEEL), (1.2, BODY_KEEL+2.2), (0, BODY_KEEL+4.6), (0, BODY_DECK-3.6),
                (1.0, BODY_DECK-1.3), (3.0, BODY_DECK)]
SPINE_PLAN = [(-14, 5), (36, 5), (37, 6.5), (37, 11), (-14, 11)]
# Its outer ends run aft past the pods to carry the jet clusters (sprite X -24..-29).
CROSSBEAM_PLAN = [(-29, -30.5), (-23, -31.5), (-9, -31.5), (-9, 31.5), (-23, 31.5), (-29, 30.5)]
MACHINERY_PLAN = [(-31.6, -13), (-14, -13), (-14, 13), (-31.6, 13)]  # proud of the body's aft face

# The machinery well opened in the aft deck (sprite: dark from X -16.5 aft).
WELL = (-27.8, -16.5, -7.2, 5.2)          # x0, x1, y0, y1; floor is the machinery top
MACH_TOP = 6.0
BEAM_TOP = 7.0
SF_Z = -3.5                                # stern-face hatch centre
LAMP_Z = 2.5                               # lamp post centre, on the quarter block top
SPINE_TOP = 13.0
INTAKES = ((-20.0, 1.9), (-20.0, -4.1))
INTAKE_R = 2.3   # the sprite's paired grey intake rings
LAMPS = (('port red', 8.5), ('starboard green', -9.5))
SHOE_X = (-21.0, -7.0, 7.0, 21.0)
SKID_Y = (0.5, -5.5)                       # port, starboard keel skids
KEEL_FLAT = BODY_KEEL


class _Parts:
    """Many small solids gathered into one flat-shaded mesh object."""

    def __init__(self):
        self.v, self.f = [], []

    def _add(self, verts, faces):
        o = len(self.v)
        self.v += verts
        self.f += [tuple(i+o for i in face) for face in faces]

    def box(self, c, d, rz=0.0):
        cx, cy, cz = c
        hx, hy, hz = d[0]/2, d[1]/2, d[2]/2
        cs, sn = math.cos(rz), math.sin(rz)
        verts = []
        for z in (-hz, hz):
            for x, y in ((-hx, -hy), (hx, -hy), (hx, hy), (-hx, hy)):
                verts.append((cx+x*cs-y*sn, cy+x*sn+y*cs, cz+z))
        self._add(verts, [(0, 3, 2, 1), (4, 5, 6, 7), (0, 1, 5, 4), (1, 2, 6, 5), (2, 3, 7, 6), (3, 0, 4, 7)])
        return self

    def cyl(self, c, r, length, axis=(0, 0, 1), n=10, r2=None):
        n = seg(max(r, r if r2 is None else r2), n)
        a = Vector(axis).normalized()
        u = a.orthogonal().normalized()
        w = a.cross(u)
        c = Vector(c)
        r2 = r if r2 is None else r2
        verts = []
        for s, rad in ((-length/2, r), (length/2, r2)):
            for i in range(n):
                t = 2*math.pi*(i+.5)/n
                verts.append(tuple(c+a*s+u*(rad*math.cos(t))+w*(rad*math.sin(t))))
        faces = [tuple(range(n)), tuple(range(n, 2*n))]
        faces += [(i, (i+1) % n, n+(i+1) % n, n+i) for i in range(n)]
        self._add(verts, faces)
        return self

    def ring(self, c, r_in, r_out, z0, z1, n=20):
        n = seg(r_out, n)
        cx, cy = c
        verts = []
        for z in (z0, z1):
            for r in (r_in, r_out):
                for i in range(n):
                    t = 2*math.pi*(i+.5)/n
                    verts.append((cx+r*math.cos(t), cy+r*math.sin(t), z))
        bi, bo, ti, to = 0, n, 2*n, 3*n
        faces = []
        for i in range(n):
            j = (i+1) % n
            faces += [(bi+i, bi+j, bo+j, bo+i), (ti+i, to+i, to+j, ti+j),
                      (bo+i, bo+j, to+j, to+i), (bi+i, ti+i, ti+j, bi+j)]
        self._add(verts, faces)
        return self

    def emit(self, kit, name, mat, bevel=0):
        return kit.mesh(name, self.v, self.f, mat, bevel) if self.v else None


def _convex(ob):
    """Split concave n-gons a boolean leaves (a deck with a well bridged into it), so
    the renderer cannot triangulate one across the opening."""
    bm = bmesh.new()
    bm.from_mesh(ob.data)
    bmesh.ops.connect_verts_concave(bm, faces=bm.faces[:])
    bm.to_mesh(ob.data)
    bm.free()
    ob.data.update()


def _bolts(parts, x0, x1, y0, y1, z, per_side=3, size=.24):
    """Bolt heads round a rectangle's rim, lying on the plane z."""
    for i in range(per_side):
        t = (i+.5)/per_side
        for x, y in ((x0+(x1-x0)*t, y0), (x0+(x1-x0)*t, y1), (x0, y0+(y1-y0)*t), (x1, y0+(y1-y0)*t)):
            parts.box((x, y, z+.05), (size, size, .12))


def _deck_hatch(kit, name, cx, cy, z, size, mats):
    """A dorsal hatch: dark coaming frame proud of the deck, a recessed steel lid with dogs."""
    frame, lid, dogs = _Parts(), _Parts(), _Parts()
    h, t = size/2, .28
    for dx, dy, lx, ly in ((0, h-t/2, size, t), (0, -h+t/2, size, t), (h-t/2, 0, t, size), (-h+t/2, 0, t, size)):
        frame.box((cx+dx, cy+dy, z+.22), (lx, ly, .44))
    lid.box((cx, cy, z+.12), (size-2*t-.12, size-2*t-.12, .24))
    lid.cyl((cx, cy, z+.3), .22, .14, n=8)
    for dx, dy in ((h-t-.25, 0), (-h+t+.25, 0), (0, h-t-.25), (0, -h+t+.25)):
        dogs.box((cx+dx, cy+dy, z+.27), (.22, .22, .1))
    frame.emit(kit, name+' coaming', mats['dark'], .04)
    lid.emit(kit, name+' lid', mats['steel'], .04)
    dogs.emit(kit, name+' dogs', mats['cream'])


def _door_panel(kit, name, centre, normal, width, z0, z1, mats, leaf_mat):
    """A crew door set on a flat face: dark reveal, steel frame, leaf, dogs and a sill.
    normal is the face's outward plan direction (unit 2-vector)."""
    nx, ny = normal
    rz = math.atan2(ny, nx)               # local X = outward normal, local Y = across the door
    tx, ty = -ny, nx
    cx, cy = centre
    zc, ht = (z0+z1)/2, z1-z0

    def at(off_n, off_t=0.0):
        return (cx+nx*off_n+tx*off_t, cy+ny*off_n+ty*off_t)

    reveal, frame, leaf, dogs = _Parts(), _Parts(), _Parts(), _Parts()
    reveal.box((*at(.04), zc), (.16, width+.3, ht+.3), rz)
    fw = .26
    for off_t, w in ((width/2+fw/2, fw), (-width/2-fw/2, fw)):
        frame.box((*at(.14, off_t), zc), (.2, w, ht+2*fw), rz)
    for z in (z1+fw/2, z0-fw/2):
        frame.box((*at(.14), z), (.2, width+2*fw, fw), rz)
    leaf.box((*at(.1), zc), (.12, width-.06, ht-.06), rz)
    # Horizontal stiffener and a small dark viewport in the leaf.
    leaf.box((*at(.18), zc-.25*ht), (.06, width-.3, .14), rz)
    for f in (.32, .62):
        dogs.box((*at(.2, width/2-.2), z0+f*ht), (.08, .16, .3), rz)
    dogs.cyl((*at(.2, -width/4), z0+.55*ht), .12, .1, axis=(nx, ny, 0), n=8)
    reveal.box((*at(.15), z0+.78*ht), (.08, .45, .3), rz)
    reveal.emit(kit, name+' reveal', mats['dark'])
    frame.emit(kit, name+' frame', mats['steel'], .03)
    leaf.emit(kit, name+' leaf', leaf_mat, .02)
    dogs.emit(kit, name+' dogs', mats['cream'])


def _deck(kit, mats):
    """Armour modules, hatches, trunk and frame band on the dorsal deck."""
    z = BODY_DECK
    under = _Parts().box((-2.4, -2.33, z+.04), (12.7, 11.25, .1))   # dark seam underlay X -8.75..3.95
    under.box((14.4, 1.7, z+.04), (20.6, 2.6, .1))                 # port strip X 4.1..24.7
    under.emit(kit, 'Hull dorsal deck seam underlay', mats['dark'])
    a, b, bolts = _Parts(), _Parts(), _Parts()
    rows = ((-8.6, -4.45), (-4.25, -0.1), (0.1, 3.85))
    cols = ((-7.9, -4.6), (-4.4, -0.15), (0.05, 3.25))
    for i, (x0, x1) in enumerate(rows):
        for j, (y0, y1) in enumerate(cols):
            if (i, j) == (2, 2):
                continue  # the access trunk stands there
            (a if (i+j) % 2 == 0 else b).box(((x0+x1)/2, (y0+y1)/2, z+.22), (x1-x0, y1-y0, .28))
            _bolts(bolts, x0+.3, x1-.3, y0+.3, y1-.3, z+.36, 2, .2)
    for k, (x0, x1) in enumerate(((4.3, 9.5), (9.7, 14.9), (15.1, 20.3), (20.5, 24.6))):
        (a if k % 2 else b).box(((x0+x1)/2, 1.75, z+.22), (x1-x0, 2.4, .28))
        _bolts(bolts, x0+.3, x1-.3, .85, 2.65, z+.36, 2, .2)
    a.emit(kit, 'Hull dorsal armour modules', mats['plate_a'], .06)
    b.emit(kit, 'Hull dorsal armour modules worn', mats['plate_b'], .06)
    bolts.emit(kit, 'Hull dorsal armour module bolts', mats['cream'])
    top = z+.36
    # The two small dark hatches the sprite shows near X -5.5, and a crew deck hatch in the port strip.
    _deck_hatch(kit, 'Hull dorsal hatch 1', -6.5, 1.7, top, 2.0, mats)
    _deck_hatch(kit, 'Hull dorsal hatch 2', -6.5, -2.3, top, 2.0, mats)
    _deck_hatch(kit, 'Hull dorsal hatch 3', 12.3, 1.75, top, 2.0, mats)
    # Bolted service cover over the starboard deck module.
    cover, cb = _Parts(), _Parts()
    cover.box((-1.4, -6.25, top+.08), (2.6, 2.6, .16))
    _bolts(cb, -2.5, -.3, -7.35, -5.15, top+.16, 3, .2)
    cover.emit(kit, 'Hull dorsal service cover', mats['steel'], .04)
    cb.emit(kit, 'Hull dorsal service cover bolts', mats['cream'])

    # Access trunk from the crew deck up to the spine walkway (the sprite's dark notch at X 3..4).
    kit.box('Hull dorsal access trunk', (2.0, 2.35, z+.6), (3.2, 3.9, 4.0), mats['tan'], .15)
    kit.box('Hull dorsal access trunk roof plate', (2.0, 2.35, z+2.66), (2.8, 3.4, .14), mats['steel'], .04)
    _door_panel(kit, 'Hull dorsal access trunk door', (0.4, 2.35), (-1, 0), 1.25, z+.25, z+2.25, mats,
                mats['cream'])

    # Structural frame band (the sprite's bright transverse band, X -9..-11) wrapped over
    # the deck and down the starboard flank to the turret deck.
    band = [(5.0, z+.5), (-8.2, z+.5), (-11.28, z-2.6), (-11.28, 3.0), (-10.85, 3.0), (-10.85, z-3.1),
            (-7.9, z-.05), (5.0, z-.05)]
    loft_x(kit, 'Hull frame band', [(-11.3, band), (-9.1, band)], mats['cream'], .05)
    kit.box('Hull frame band spine cap', (-10.2, 8.0, SPINE_TOP+.2), (2.2, 3.6, .5), mats['cream'], .05)
    welds = _Parts()
    for x in (-11.45, -8.95):
        welds.box((x, -2.0, z+.04), (.12, 14.0, .1))
    welds.box((-13.3, -1.0, z+.04), (.14, 16.0, .1))   # transverse weld across the aft deck rim
    welds.emit(kit, 'Hull dorsal weld beads', mats['weld'])


def _bow_hit(y, z, d=(-1.0, 0.0, 0.0)):
    """Where a horizontal ray at height z meets the rounded bow: (point, plan normal (nx, ny)).
    The default ray runs aft along Y = y; any other direction d is aimed at the nose centre."""
    bpy.context.view_layer.update()
    body = bpy.data.objects['Hull central body']
    ev = body.evaluated_get(bpy.context.evaluated_depsgraph_get())
    d = Vector(d).normalized()
    o = Vector((60.0, y, z)) if d.x == -1 else Vector((BOW_C[0], BOW_C[1], z))-d*40
    hit, p, n, _ = ev.ray_cast(o, d)
    assert hit, (y, z)
    h = math.hypot(n.x, n.y)
    return p, (n.x/h, n.y/h)


def _flanks(kit, mats):
    """Seams and strata on the visible starboard flank, the bow and the airlocks."""
    seams = _Parts()
    y = -11.02
    for z in (6.0, 0.0, -6.5):
        seams.box((8.5, y, z), (35.0, .12, .12))     # strata lines, X -9..26 (the flank, aft of the bow's turn)
    for x in (-2.0, 4.5, 13.0, 22.0):
        seams.box((x, y, -1.6), (.12, .12, 17.2))
    seams.emit(kit, 'Hull flank seam gaps', mats['dark'])
    weld = _Parts().box((8.5, -11.03, 7.2), (35.0, .1, .1))
    weld.emit(kit, 'Hull flank weld bead', mats['weld'])
    # Bow fittings, each set on the rounded nose where a ray finds it and turned to its face.
    cover, cb = _Parts(), _Parts()
    for yy, zz, sz in ((-5.4, -2.6, 2.0), (3.4, -2.6, 1.6)):
        p, (nx, ny) = _bow_hit(yy, zz)
        rz = math.atan2(ny, nx)
        cover.box((p.x+nx*.06, p.y+ny*.06, zz), (.18, sz, sz), rz)
        tx, ty = -ny, nx
        for dy, dz in ((-1, -1), (-1, 1), (1, -1), (1, 1)):
            u = dy*(sz/2-.25)
            cb.box((p.x+nx*.18+tx*u, p.y+ny*.18+ty*u, zz+dz*(sz/2-.25)), (.1, .2, .2), rz)
    cover.emit(kit, 'Hull bow service covers', mats['steel'], .03)
    cb.emit(kit, 'Hull bow service cover bolts', mats['cream'])
    p, (nx, ny) = _bow_hit(-1.0, -3.0)
    fair = _Parts().box((p.x+nx*.2, p.y+ny*.2, -3.0), (.6, 1.6, 1.0), math.atan2(ny, nx))
    fair.emit(kit, 'Hull bow mooring fairlead', mats['dark'], .1)
    kit.cylinder('Hull bow mooring fairlead bollard', (p.x+nx*.45, p.y+ny*.45, -3.0), .35, .7, mats['steel'],
                 'X', 10, .04)
    # Plating strakes round the nose: the rounded bow is plated in horizontal courses.
    strakes = _Parts()
    for zz in (-8.0, 0.6, 5.0):
        for k in range(15):
            a = math.radians(-80+k*(160/14))
            try:
                p, (nx, ny) = _bow_hit(0, zz, d=(-math.cos(a), -math.sin(a), 0))
            except AssertionError:
                continue
            if p.x > 26.5:
                strakes.box((p.x+nx*.02, p.y+ny*.02, zz), (.12, 1.3, .12), math.atan2(ny, nx))
    strakes.emit(kit, 'Hull bow plating strakes', mats['dark'])

    # Airlock 1: a vestibule on the starboard flank opening onto the turret deck.
    kit.box('Hull starboard airlock vestibule', (-5.0, -11.55, 4.65), (3.2, 1.7, 3.3), mats['tan'], .15)
    kit.box('Hull starboard airlock vestibule roof', (-5.0, -11.6, 6.35), (3.4, 1.9, .2), mats['steel'], .05)
    _door_panel(kit, 'Hull starboard airlock door', (-5.0, -12.4), (0, -1), 1.3, 3.15, 5.45, mats, mats['cream'])
    # Airlock 2: on the starboard bow quarter, stepping down to the gantry base deck.
    p, n = _bow_hit(0, -6.35, d=(-1, 1, 0))
    _door_panel(kit, 'Hull starboard bow airlock door', (p.x, p.y), n, 1.3, -7.5, -5.2, mats, mats['cream'])
    rz = math.atan2(n[1], n[0])
    step = _Parts().box((p.x+n[0]*.8, p.y+n[1]*.8, -7.85), (1.6, 1.7, .2), rz)
    step.emit(kit, 'Hull starboard bow airlock step', mats['steel'], .03)
    # A ladder down from the step to the gantry base deck (top about -11.4).
    lad, (lx, ly) = _Parts(), (p.x+n[0]*1.55, p.y+n[1]*1.55)
    for t in (-.55, .55):
        lad.box((lx-n[1]*t, ly+n[0]*t, -9.65), (.12, .12, 3.7), rz)
    for k in range(5):
        lad.box((lx, ly, -11.0+k*.75), (.1, 1.1, .1), rz)
    lad.emit(kit, 'Hull starboard bow airlock ladder', mats['cream'])


def _spine(kit, mats):
    """Walkway, handrails, conduits and service notches on the port spine."""
    z = SPINE_TOP
    kit.box('Hull port spine walkway bed', (11.0, 8.0, z+.04), (46.0, 2.0, .1), mats['dark'], 0)
    treads = _Parts()
    x = -11.5
    while x < 33.8:
        if not -11.4 < x < -8.9:
            treads.box((x, 8.0, z+.13), (.18, 2.0, .1))
        x += .8
    treads.box((11.0, 7.05, z+.13), (46.0, .14, .12))
    treads.box((11.0, 8.95, z+.13), (46.0, .14, .12))
    treads.emit(kit, 'Hull port spine walkway grating', mats['steel'])
    rails = _Parts()
    for y in (6.7, 9.3):
        for px in (-13.0, -7.0, -2.0, 3.0, 8.0, 13.0, 18.0, 23.0, 28.0, 33.5):
            rails.box((px, y, z+.6), (.14, .14, 1.2))
        rails.box((10.25, y, z+1.2), (46.5, .14, .14))
        rails.box((10.25, y, z+.62), (46.5, .1, .1))
    rails.emit(kit, 'Hull port spine handrails', mats['cream'])
    # Conduits along the inboard wall, on brackets; they end at the bow.
    pipes, brackets = _Parts(), _Parts()
    for zz, yy, r in ((BODY_DECK+.45, 4.72, .28), (BODY_DECK+1.05, 5.05, .22)):
        pipes.cyl((5.25, yy, zz), r, 37.5, axis=(1, 0, 0), n=8)
    for bx in (-12.0, -7.5, -1.0, 6.0, 12.0, 18.0, 23.5):
        brackets.box((bx, 4.95, BODY_DECK+.7), (.3, .5, 1.3))
    pipes.emit(kit, 'Hull port spine conduits', mats['steel'])
    brackets.emit(kit, 'Hull port spine conduit brackets', mats['dark'])
    # The dark notches the sprite shows along the spine: service ports in its outboard face.
    notches, lids = _Parts(), _Parts()
    for nx in (-12.0, -6.0, -1.5, 3.0, 7.5, 12.0, 16.5, 21.0, 25.5, 30.0):
        notches.box((nx, 11.04, 9.6), (1.3, .14, 1.6))
        lids.box((nx, 11.12, 9.95), (1.0, .08, .55))
    notches.emit(kit, 'Hull port spine service notches', mats['dark'])
    lids.emit(kit, 'Hull port spine service notch lids', mats['steel'])
    seams = _Parts()
    for sx in (-4.0, 9.5, 21.5):
        seams.box((sx, 11.03, 7.5), (.12, .1, 7.0))
    seams.box((11.0, 11.03, 6.5), (49.0, .1, .1))
    seams.emit(kit, 'Hull port spine seam gaps', mats['dark'])


def _crossbeam(kit, mats):
    """Seams, slots and welds on the visible parts of the aft crossbeam."""
    seams, slots = _Parts(), _Parts()
    for y in (-28.4, -11.6, 9.6, 28.6):
        seams.box((-19.0, y, BEAM_TOP-.01), (17.0, .14, .1))
    seams.box((-11.0, -21.0, BEAM_TOP-.01), (.14, 19.0, .1))
    # Dark slots in the starboard forward face above the turret deck.
    for y in (-13.0, -16.0, -19.0, -22.0, -25.0):
        slots.box((-8.97, y, 4.0), (.14, 1.8, .7))
    # Vent slots on the outboard tips, beyond the pods.
    for s in (1, -1):
        for x in (-12.0, -14.2, -16.4, -18.6, -20.8):
            slots.box((x, s*28.9, BEAM_TOP-.01), (1.0, 1.0, .12))
        slots.box((-15.0, s*31.55, 0.0), (3.0, .14, 1.4))    # outboard face vent
    seams.emit(kit, 'Hull aft crossbeam seam gaps', mats['dark'])
    slots.emit(kit, 'Hull aft crossbeam slots', mats['dark'])
    weld = _Parts().box((-9.05, -19.5, 4.95), (.1, 15.0, .1))
    weld.emit(kit, 'Hull aft crossbeam weld bead', mats['weld'])


def _machinery(kit, mats, body, mach, beam):
    """Open the machinery well and fit its intakes, hatch, pump and piping; stern face; lamps."""
    x0, x1, y0, y1 = WELL
    # The crossbeam runs through the body here, so the well opens both.
    for host in (beam, body):
        cut = kit.box('Temporary machinery well cutter', ((x0+x1)/2, (y0+y1)/2, MACH_TOP-.4+4),
                      (x1-x0, y1-y0, 8), mats['tan'], 0)
        boolean(host, cut)
    # The paired dark intakes the sprite shows on the machinery deck.
    for i, (x, y) in enumerate(INTAKES, 1):
        for host in (mach, body, beam):
            c = kit.cylinder(f'Temporary intake {i} cutter', (x, y, MACH_TOP), INTAKE_R, 3, mats['dark'], 'Z', 16, 0)
            boolean(host, c)
        kit.cylinder(f'Hull aft machinery intake {i} grille', (x, y, MACH_TOP-1.25), INTAKE_R, .4, mats['dark'], 'Z', 16, 0)
        bars = _Parts()
        for k in range(-2, 3):
            half = math.sqrt(max(INTAKE_R**2-(k*.9)**2, 0))
            bars.box((x+k*.9, y, MACH_TOP+2.6), (.16, 2*half, .14))
        bars.box((x, y, MACH_TOP+2.75), (2*INTAKE_R, .2, .16))
        bars.emit(kit, f'Hull aft machinery intake {i} grille bars', mats['steel'])
        collar = _Parts().ring((x, y), INTAKE_R, INTAKE_R+.4, MACH_TOP-.1, MACH_TOP+2.9, 20)
        collar.emit(kit, f'Hull aft machinery intake {i} collar', mats['steel'], .04)
        lip = _Parts().ring((x, y), INTAKE_R-.05, INTAKE_R+.55, MACH_TOP+2.9, MACH_TOP+3.25, 20)
        for k in range(4):                         # stiffening bands round the stack
            lip.ring((x, y), INTAKE_R+.35, INTAKE_R+.5, MACH_TOP+.5+k*.6, MACH_TOP+.7+k*.6, 20)
        lip.emit(kit, f'Hull aft machinery intake {i} stack lip', mats['grey'], .03)
    for host in (mach, body, beam):
        _convex(host)
    # Dorsal reactor access hatch and a coolant pump at the aft end of the well.
    _deck_hatch(kit, 'Hull aft machinery dorsal reactor hatch', -25.9, -4.6, MACH_TOP, 2.6, mats)
    kit.box('Hull aft machinery coolant pump', (-26.4, 3.4, MACH_TOP+1.2), (2.6, 2.2, 2.4), mats['grey'], .12)
    kit.cylinder('Hull aft machinery coolant pump motor', (-26.6, 1.8, MACH_TOP+1.2), .7, 1.2, mats['dark'], 'Y', 10, .04)
    pipes, flanges = _Parts(), _Parts()
    pipes.cyl((-23.9, -.9, MACH_TOP+.4), .32, 14.0, axis=(0, 1, 0), n=8)          # transverse header
    pipes.cyl((-26.0, 4.75, MACH_TOP+.35), .26, 4.4, axis=(1, 0, 0), n=8)         # pump to header, port
    pipes.cyl((-27.6, -.6, MACH_TOP+.35), .26, 6.4, axis=(0, 1, 0), n=8)          # pump to hatch
    for fy in (-6.0, -3.0, 2.0, 5.2):
        flanges.cyl((-23.9, fy, MACH_TOP+.4), .46, .2, axis=(0, 1, 0), n=8)
    for fx in (-25.0,):
        flanges.cyl((fx, 4.75, MACH_TOP+.35), .4, .2, axis=(1, 0, 0), n=8)
    pipes.emit(kit, 'Hull aft machinery well piping', mats['steel'])
    flanges.emit(kit, 'Hull aft machinery well pipe flanges', mats['grey'])
    kit.cylinder('Hull aft machinery well valve stem', (-23.9, -1.2, MACH_TOP+1.0), .1, 1.0, mats['steel'], 'Z', 6, 0)
    wheel = _Parts().ring((-23.9, -1.2), .38, .55, MACH_TOP+1.45, MACH_TOP+1.6, 12)
    wheel.emit(kit, 'Hull aft machinery well valve wheel', mats['orange'])
    # Ladder down the well's forward wall, from the deck to the machinery top.
    lad = _Parts()
    for t in (-.55, .55):
        lad.box((x1-.18, 4.4+t, (MACH_TOP+BODY_DECK)/2+.4), (.12, .12, BODY_DECK-MACH_TOP+.8))
    for k in range(6):
        lad.box((x1-.25, 4.4, MACH_TOP+.6+k*.8), (.1, 1.1, .1))
    lad.emit(kit, 'Hull aft machinery well ladder', mats['cream'])

    # Stern face of the machinery block (X -31.6, Y -8..8, z -6..1.5).
    xf = MACHINERY_PLAN[0][0]
    frame, cover, bolts = _Parts(), _Parts(), _Parts()
    hz = SF_Z
    for dy, dz, ly, lz in ((0, 1.65, 4.4, .3), (0, -1.65, 4.4, .3), (2.05, 0, .3, 3.6), (-2.05, 0, .3, 3.6)):
        frame.box((xf-.12, dy, hz+dz), (.3, ly, lz))
    cover.box((xf-.07, 0, hz), (.16, 3.7, 3.0))
    cover.box((xf-.2, 0, hz), (.1, 3.0, .2))
    for i in range(4):
        t = (i+.5)/4
        for yy, zz in ((-1.6+3.2*t, hz+1.3), (-1.6+3.2*t, hz-1.3), (-1.6, hz-1.3+2.6*t), (1.6, hz-1.3+2.6*t)):
            bolts.box((xf-.2, yy, zz), (.12, .2, .2))
    frame.emit(kit, 'Hull aft machinery reactor access hatch frame', mats['dark'], .03)
    cover.emit(kit, 'Hull aft machinery reactor access hatch cover', mats['grey'], .03)
    bolts.emit(kit, 'Hull aft machinery reactor access hatch bolts', mats['cream'])
    louvre_back, slats = _Parts(), _Parts()
    for s in (1, -1):
        louvre_back.box((xf-.05, s*5.5, hz), (.14, 2.6, 4.2))
        for k in range(6):
            slats.box((xf-.2, s*5.5, hz-1.7+k*.68), (.14, 2.4, .2))
    louvre_back.emit(kit, 'Hull aft machinery stern intakes', mats['dark'])
    slats.emit(kit, 'Hull aft machinery stern intake louvres', mats['steel'])
    sp, sf = _Parts(), _Parts()
    for zz in (hz-3.0, hz+2.9):
        sp.cyl((xf-.38, 0, zz), .32, 16.2, axis=(0, 1, 0), n=8)
        for fy in (-6.9, -3.0, 3.0, 6.9):
            sf.cyl((xf-.38, fy, zz), .46, .22, axis=(0, 1, 0), n=8)
        for fy in (-7.5, 0.0, 7.5):
            sf.box((xf-.2, fy, zz), (.4, .3, .9))
    sp.emit(kit, 'Hull aft machinery stern piping', mats['steel'])
    sf.emit(kit, 'Hull aft machinery stern pipe flanges', mats['grey'])
    seams = _Parts()
    seams.box((xf-.02, 0, -9.5), (.1, 16.0, .12))
    for yy in (-3.4, 3.4):
        seams.box((xf-.02, yy, 3.3), (.1, .1, 1.4))
    seams.emit(kit, 'Hull aft machinery seam gaps', mats['dark'])

    # Stern running lamps where the sprite has them, coloured to the navigation
    # convention (owner): red to port, green to starboard.
    for name, y in LAMPS:
        lens_mat = mats['green'] if 'green' in name else mats['red']
        kit.box(f'Hull stern lamp post {name}', (-31.9, y, LAMP_Z), (1.2, 1.6, 2.2), mats['cream'], .1)
        kit.box(f'Hull stern lamp housing {name}', (-32.2, y, LAMP_Z+1.75), (1.8, 1.7, 1.3), mats['steel'], .12)
        kit.box(f'Hull stern lamp lens {name}', (-33.12, y, LAMP_Z+1.75), (.16, 1.2, .75), lens_mat, .03)
        kit.box(f'Hull stern lamp hood {name}', (-33.1, y, LAMP_Z+2.48), (.6, 1.8, .16), mats['dark'], .03)
        guard = _Parts()
        for dy in (-.45, 0.0, .45):
            guard.box((-33.32, y+dy, LAMP_Z+1.75), (.08, .08, .9))
        guard.emit(kit, f'Hull stern lamp guard {name}', mats['dark'])


def _ventral(kit, mats):
    """Keel skids and cradle shoes, the ventral cargo hatch, sensor blister and covers."""
    z = KEEL_FLAT
    for side, y in zip(('port', 'starboard'), SKID_Y):
        kit.box(f'Hull keel skid {side}', (0.0, y, z-.35), (54.0, 1.3, .8), mats['steel'], .12)
    i = 0
    for y in SKID_Y:
        for x in SHOE_X:
            i += 1
            kit.box(f'Hull keel cradle shoe {i}', (x, y, z-1.15), (3.4, 2.7, .9), mats['steel'], .14)
            pad = _Parts().box((x, y, z-1.68), (3.0, 2.3, .16))
            for dx in (-1.25, 1.25):        # side gussets into the skid
                pad.box((x+dx, y, z-.9), (.24, 2.2, .6))
            pad.emit(kit, f'Hull keel cradle shoe {i} bearing pad', mats['dark'], .02)
    # Ventral cargo hatch between the skids (cargo 100): two leaves on hinge lines.
    hx0, hx1, hy0, hy1 = 9.0, 19.0, -4.1, -0.9
    frame, leaves, hinge = _Parts(), _Parts(), _Parts()
    t = .35
    for c, d in ((((hx0+hx1)/2, hy0+t/2), (hx1-hx0, t)), (((hx0+hx1)/2, hy1-t/2), (hx1-hx0, t)),
                 ((hx0+t/2, (hy0+hy1)/2), (t, hy1-hy0)), ((hx1-t/2, (hy0+hy1)/2), (t, hy1-hy0))):
        frame.box((c[0], c[1], z-.15), (d[0], d[1], .3))
    frame.box(((hx0+hx1)/2, (hy0+hy1)/2, z-.02), (hx1-hx0-.2, hy1-hy0-.2, .06))   # dark gap behind leaves
    mid = (hy0+hy1)/2
    for y0, y1 in ((hy0+t+.05, mid-.07), (mid+.07, hy1-t-.05)):
        leaves.box(((hx0+hx1)/2, (y0+y1)/2, z-.1), (hx1-hx0-2*t-.1, y1-y0, .14))
        for k in range(4):
            leaves.box((hx0+1.4+k*2.4, (y0+y1)/2, z-.2), (.18, y1-y0-.3, .08))
    for y in (hy0+t+.15, hy1-t-.15):
        for k in range(5):
            hinge.cyl((hx0+1.0+k*2.0, y, z-.22), .16, .8, axis=(1, 0, 0), n=8)
    frame.emit(kit, 'Hull ventral cargo hatch frame', mats['dark'], .03)
    leaves.emit(kit, 'Hull ventral cargo hatch leaves', mats['plate_b'], .03)
    hinge.emit(kit, 'Hull ventral cargo hatch hinges', mats['steel'])
    # Surveying equipment: the ventral sensor blister under the bow.
    blister = _Parts().cyl((24.5, -2.5, z-.3), 2.0, .6, n=8, r2=2.0)
    blister.cyl((24.5, -2.5, z-.75), 1.75, .3, n=8, r2=1.2)
    blister.emit(kit, 'Hull ventral sensor blister', mats['grey'], .06)
    kit.cylinder('Hull ventral sensor blister window', (24.5, -2.5, z-.95), 1.0, .14, mats['dark'], 'Z', 8, 0)
    # Bolted ventral covers and the keel seams.
    covers, bolts = _Parts(), _Parts()
    for cx, cy, s in ((0.0, -2.5, 2.4), (-14.0, -2.5, 2.4), (-24.0, -2.5, 3.0), (-3.5, -2.5, 1.6)):
        covers.box((cx, cy, z-.07), (s, s, .14))
        _bolts(bolts, cx-s/2+.25, cx+s/2-.25, cy-s/2+.25, cy+s/2-.25, z-.25, 2, .2)
    covers.emit(kit, 'Hull ventral service covers', mats['steel'], .03)
    bolts.emit(kit, 'Hull ventral service cover bolts', mats['cream'])
    seams = _Parts()
    for sx in (-17.0, -10.5, 3.5, 19.0, 27.0):
        seams.box((sx, -2.5, z-.02), (.16, 8.6, .08))
    seams.box((0.0, -2.5, z-.02), (55.0, .16, .08))
    seams.emit(kit, 'Hull ventral seam gaps', mats['dark'])
    drains = _Parts()
    for dx in (-26.5, -6.0, 4.5, 26.5):
        drains.cyl((dx, 3.5 if dx < -12 else 1.6, z-.08), .3, .2, n=8)
    drains.emit(kit, 'Hull ventral drain ports', mats['dark'])


def build(kit):
    body = prism(kit, 'Hull central body', BODY_PLAN, BODY_SECTION, kit.tan)
    # Bow facets: tangent planes to an XZ ellipse, so the nose rounds over and under.
    plane_cut(body, 'bow brow', (30.5, 0, 11), (1, 0, 1))
    (ex, ez), (ax, az) = BOW_EL_C, BOW_EL_A
    for a in BOW_FACETS:
        t = math.radians(a)
        plane_cut(body, f'bow facet {a}', (ex+ax*math.cos(t), 0, ez+az*math.sin(t)),
                  (math.cos(t)/ax, 0, math.sin(t)/az))
    kit.hull.append(body)

    spine = prism(kit, 'Hull port spine', SPINE_PLAN, section(-8, 13, 2.0, 1.5), kit.tan)
    kit.hull.append(spine)

    beam = prism(kit, 'Hull aft crossbeam', CROSSBEAM_PLAN, section(-8, 7, 2.0, 2.0), kit.tan)
    kit.hull.append(beam)

    mach = prism(kit, 'Hull aft machinery block', MACHINERY_PLAN, section(-14, 6, 3.0, 2.0), kit.steel)
    kit.hull.append(mach)
    # Its aft corners step back beside the pods (sprite rows X -31..-33).
    for side, s in (('port', 1), ('starboard', -1)):
        kit.box(f'Hull aft machinery {side} quarter block', (-32, s*10.5, -4), (3, 5, 12), kit.steel, .15)

    mats = {'tan': kit.tan, 'steel': kit.steel, 'grey': kit.grey, 'dark': kit.dark, 'cream': kit.cream,
            'orange': kit.orange,
            'plate_a': material('Ko sand armour module', (.49, .35, .18), .2, .58),
            'plate_b': material('Ko sand armour module worn', (.40, .29, .15), .25, .66),
            'weld': material('Weld bead', (.30, .27, .22), .5, .5),
            'red': material('Running lamp red', (.30, .02, .02), .1, .3, emission=(1.0, .08, .05), strength=6.0),
            'green': material('Running lamp green', (.02, .25, .05), .1, .3, emission=(.1, 1.0, .25), strength=6.0)}
    _machinery(kit, mats, body, mach, beam)
    _deck(kit, mats)
    _flanks(kit, mats)
    _spine(kit, mats)
    _crossbeam(kit, mats)
    _ventral(kit, mats)
