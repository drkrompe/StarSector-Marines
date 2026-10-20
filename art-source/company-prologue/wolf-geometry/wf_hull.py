"""The Wolf (P)'s structure: bow fork, head and outriggers, neck, main body
with its missile pods, aft body with the armour domes, engine block.

Read off wolf_pirates.png (52x96, .ship centre (26, 42) from the bottom edge;
sprite column c, row r covers X 53-r..54-r and Y 25-c..26-c), checked
against wolf_base.png, which shows the same hull unpainted:
- Bow fork (rows 1-10): a gunmetal claw of five tines, the outer pair
  heaviest (Y +-6.5..10), an inner pair (Y +-2.5..4.5) and the WS 004
  hardpoint channel on the centreline, all springing from a solid base
  across Y +-10 at rows 8-10.
- Head (rows 11-23): a grey block Y +-10 with crimson cheek plates
  (Y +-5..9.5), a white bridge canopy on the centreline (rows 13-16), and a
  grey outrigger either side out to Y +-16 (cols 10-15, 36-41). The
  starboard outrigger carries an orange heat glow (rows 16-18, cols 38-40;
  the port one does not: the sprite is asymmetric there).
- Neck (rows 24-36): crimson flanks to Y +-14, notched at row 25, a grey
  spine Y +-4.5 with a domed sensor housing (rows 26-30) and a second
  canopy (rows 33-34), and amber hull lamps at rows 28 (cols 14, 37).
- Main body (rows 37-58): the widest section. A crimson core Y +-17 round
  the WS 001 turret ring (rows 39-48, radius 5 about X 11), two round
  emitter wells at X 6, Y +-9 (the same in the base sprite), grey missile
  pods Y +-13.5..25 (rows 39-49) round the WS 005 / WS 006 hardpoints, and
  crimson wing-root lobes behind them (rows 49-57) out to Y +-23.
- Waist and aft body (rows 58-73): pinched to Y +-15.5 at row 61, then two
  crimson armour domes (rows 63-72, centre X -13.5, Y +-12.5) where the
  base hull's WS 002 / WS 003 turrets stand: the pirate skin removes those
  slots and paints armour over the rings. A grey centre spine with a dark
  hatch well (rows 66-68).
- Engine block (rows 74-94): grey machinery Y +-14..18 carrying the three
  HIGH_TECH drives on its aft face.
Round 6 (owner: the nose "still feels weak"): the prow is the head's depth
carried forward with the fork base's breadth, two armoured jaws (the outer
tines) and a gun hood; the bridge is a raised command deck over the head
canopy and the neck oval; the merchant's aft turrets sit in faired sponsons.
"""
import math
import bmesh
import bpy
from wf_kit import boolean, facet_normal, plane_cut, prism, section, sym, revolve_x, loft_x

FORK = sym([(47.5, 9.4), (43, 9.4), (43, 9.0)])
OUTER_TINE = [(53.8, 6.4), (53.8, 8.6), (51, 10), (44, 10), (44, 5.3), (51, 5.3)]
HEAD = sym([(43.6, 8.5), (42.5, 10), (31, 10), (30.5, 9)])
OUTRIGGER = [(43.5, 12.4), (43.5, 13.9), (42.0, 15.3), (40.6, 16.2), (32.5, 16.2), (30.6, 15.8), (30.6, 10.4),
             (42, 10.4)]
NECK = sym([(31.5, 14.2), (30.4, 13.0), (29, 13.0), (27.8, 14.6), (19, 14.9), (17, 16.4), (15.5, 16.4)])
MAIN = sym([(18.6, 14.6), (17.3, 16.8), (6.0, 17.2), (-4.0, 16.6), (-6.4, 16.0), (-8.5, 15.6)])
# (round 5: the body keeps to its core; the swept missile wings carry the span to Y +-25)
# The missile wing (owner, round 5): the fixed missile hookups sit at the tips of
# shallow swept wings that leave the hull amidships, rooted ahead of the drive
# section and angling forward and outboard to the WS 005 / WS 006 launchers.
WING = [(15.0, 18.6), (15.0, 24.2), (13.8, 25.2), (6.0, 25.2), (-3.0, 21.8), (-12.0, 17.4), (-19.0, 15.4),
        (-19.0, 11.0), (-6.0, 12.2), (5.0, 14.0)]
POD = [(14.6, 18.0), (14.6, 23.8), (13.6, 24.7), (5.4, 24.7), (4.4, 23.8), (4.4, 15.0), (6.0, 13.6),
       (13.0, 13.6)]
AFT = sym([(-5, 11), (-9, 15.5), (-19.5, 15.6), (-21, 13)])
ENGINE_BLOCK = sym([(-18.5, 16.3), (-21, 15.0), (-23.5, 13.9), (-27, 14.6), (-30, 15.4), (-33.6, 16.6),
                    (-35, 16.0), (-37.3, 13), (-38.6, 10.5), (-38.8, 1)])
DOME = (-13.6, 12.4, 5.2)            # centre X, |Y|, plan radius of the armour domes
# The prow (round 6): half-width, deck and keel along X (cosine-eased, as the hull lines).
PROW_HW = [(42.6, 10.0), (50.5, 9.6), (54.4, 7.4)]
PROW_TOP = [(42.6, 4.4), (54.4, 1.2)]
PROW_KEEL = [(42.6, -10.6), (46.5, -10.0), (54.4, -6.4)]
JAW = [(54.7, 4.6), (54.7, 7.6), (52.0, 10.1), (44.2, 10.4), (44.2, 6.4), (51.0, 5.3)]   # port jaw plan
# The bridge (round 6): the command deck on the head, its glazed face over the bow gun
# (the sprite's white canopy, rows 13-16) and its roof running aft into the neck spine.
# (x, half-width, roof) stations; it stands on the deck from BRIDGE_FLOOR up.
BRIDGE = [(26.6, 3.4, 9.4), (29.6, 5.2, 10.2), (36.6, 5.8, 10.2), (38.6, 5.6, 9.9), (42.4, 4.4, 6.2)]
BRIDGE_FLOOR = 2.0

# Heights (proposed art; the game gives none). One keel chamfer, one wall,
# one shoulder chamfer per section.
FORK_Z = (-9.5, 1.0)
HEAD_Z = (-12.5, 5.0)
OUTRIGGER_Z = (-9.5, 3.5)
NECK_Z = (-13.0, 7.0)
MAIN_Z = (-14.5, 9.0)
POD_Z = (-10.5, 4.0)
AFT_Z = (-14.0, 7.5)
ENGINE_Z = (-12.5, 5.5)
SPINE_TOP = 9.5


def ccw(poly):
    area = sum(x0*y1-x1*y0 for (x0, y0), (x1, y1) in zip(poly, poly[1:]+poly[:1]))
    return poly if area > 0 else list(reversed(poly))


def mirror(poly):
    return ccw([(x, -y) for x, y in poly])


def sides(plan):
    return (('port', 1, ccw(plan)), ('starboard', -1, mirror(plan)))


def dome(kit, name, x, y, r, z0, h, mat, n=16, rings=4):
    """A flattened spherical cap of plan radius r rising h above z0, faceted."""
    verts, faces = [], []
    for k in range(rings+1):
        a = (math.pi/2)*k/rings
        rr, zz = r*math.cos(a), z0+h*math.sin(a)
        for j in range(n if k < rings else 1):
            t = 2*math.pi*j/n
            verts.append((x+rr*math.cos(t), y+rr*math.sin(t), zz))
    for k in range(rings-1):
        for j in range(n):
            j2 = (j+1) % n
            faces.append((k*n+j, k*n+j2, (k+1)*n+j2, (k+1)*n+j))
    top = rings*n
    faces += [((rings-1)*n+j, (rings-1)*n+(j+1) % n, top) for j in range(n)]
    # Skirt down to the floor so the cap is a closed solid seated in the deck.
    base = len(verts)
    verts += [(x+r*math.cos(2*math.pi*j/n), y+r*math.sin(2*math.pi*j/n), z0-2.0) for j in range(n)]
    faces += [(base+j, base+(j+1) % n, (j+1) % n, j) for j in range(n)]
    faces.append(tuple(reversed(range(base, base+n))))
    return kit.mesh(name, verts, faces, mat)


def shear(ob, y0, k, top_only=True):
    """Lower vertices outboard of |Y| y0 by k per unit, so a deck crowns over the
    spine and falls away to the flanks (a crease at |Y| = y0 keeps faces planar)."""
    bm = bmesh.new()
    bm.from_mesh(ob.data)
    for s in (1, -1):
        bmesh.ops.bisect_plane(bm, geom=bm.verts[:]+bm.edges[:]+bm.faces[:], plane_co=(0, s*y0, 0),
                               plane_no=(0, 1, 0))
    for v in bm.verts:
        if (not top_only or v.co.z > 0) and abs(v.co.y) > y0:
            v.co.z -= k*(abs(v.co.y)-y0)
    bm.to_mesh(ob.data)
    bm.free()
    ob.data.update()
    return ob


def lobe(kit, name, centre, radii, mat, yaw=0.0, segments=16, rings=8):
    """A faceted ellipsoid, mostly buried in the hull it rounds out: the curved
    crimson forms the sprite's highlights show (a high-tech hull)."""
    bpy.ops.mesh.primitive_uv_sphere_add(segments=segments, ring_count=rings, radius=1, location=centre)
    ob = bpy.context.object
    ob.scale = radii
    ob.rotation_euler = (0, 0, math.radians(yaw))
    bpy.ops.object.transform_apply(location=False, rotation=True, scale=True)
    return kit.finish(ob, name, mat)


def seat(kit, x, y, fallback):
    hit = kit.surface(x, y)
    return hit[0].z if hit else fallback


# --- One hull, not a stack of blocks (owner, round 4: "the modules don't feel
# cohesive"). The central hull - head, neck, main body, aft body, engine block -
# is lofted through one plan (the union of the sprite-read outlines), one top
# line and one keel line, with rounded superellipse sections, so each module
# meets the next in the same section and the ship reads as one form. Each
# module keeps its own object (and name) over its own stretch of X.
TOP_LINE = [(46, 4.0), (43.6, 4.4), (38, 5.2), (31, 6.6), (24, 7.6), (14, 8.8), (4, 9.0), (-6, 8.4),
            (-14, 7.8), (-21, 7.0), (-30, 5.8), (-39, 4.6)]
KEEL_LINE = [(46, -10.2), (43.6, -10.6), (38, -11.0), (31, -12.6), (20, -14.0), (6, -14.6), (-8, -14.4),
             (-18, -13.8), (-28, -12.8), (-39, -11.2)]
MODULES = [('Hull head', 31.0, 43.6, 'steel'), ('Hull neck', 16.5, 31.0, 'red'), ('Hull main body', -6.0, 16.5, 'red'),
           ('Hull aft body', -19.5, -6.0, 'red'), ('Hull engine block', -27.0, -19.5, 'steel')]
# The drive section (owner, round 5): an octagon, short ahead of three long
# octagonal drive cans that run exposed out the back (wf_engines).
P_TOP, P_BOTTOM = 3.4, 3.8     # superellipse exponents: a rounded crown, a fuller belly


def line_at(ctrl, x):
    """Cosine-eased interpolation through (x, value) control points (descending x)."""
    pts = sorted(ctrl)
    if x <= pts[0][0]:
        return pts[0][1]
    for (x0, v0), (x1, v1) in zip(pts, pts[1:]):
        if x0 <= x <= x1:
            t = (x-x0)/(x1-x0)
            t = .5-.5*math.cos(math.pi*t)
            return v0+(v1-v0)*t
    return pts[-1][1]


def plan_hw(polys, x):
    """Half-width of the union of symmetric plan outlines at X (0 if none covers it)."""
    best = 0.0
    for poly in polys:
        for (x0, y0), (x1, y1) in zip(poly, poly[1:]+poly[:1]):
            if min(x0, x1) <= x <= max(x0, x1) and x0 != x1:
                y = y0+(y1-y0)*(x-x0)/(x1-x0)
                best = max(best, abs(y))
    return best


def side_span(poly, x):
    """(centre y, half-width) of a one-sided plan outline at X."""
    ys = []
    for (x0, y0), (x1, y1) in zip(poly, poly[1:]+poly[:1]):
        if min(x0, x1) <= x <= max(x0, x1) and x0 != x1:
            ys.append(y0+(y1-y0)*(x-x0)/(x1-x0))
    if not ys:
        return 0.0, 0.0
    return (max(ys)+min(ys))/2, (max(ys)-min(ys))/2


def sponson(kit, name, poly, top, keel, mat, sign, step=.8):
    top_f = top if callable(top) else (lambda x, v=top: v)
    keel_f = keel if callable(keel) else (lambda x, v=keel: v)
    """A side module (missile pod, outrigger) faired like the hull: superellipse
    sections that follow its sprite outline, port outline mirrored by sign."""
    xs0 = sorted({x for x, _ in poly})
    x0, x1 = xs0[0]+.05, xs0[-1]-.05
    k = max(3, int((x1-x0)/step))
    xs = [x0+(x1-x0)*i/k for i in range(k+1)]
    stations_ = []
    for x in xs:
        yc, w = side_span(poly, x)
        stations_.append((x, yc*sign, max(w, .3)))
    n = 20
    out = []
    for x, yc, w in stations_:
        ring = []
        top, keel = top_f(x), keel_f(x)
        zm = keel+.45*(top-keel)
        for j in range(n):
            a = 2*math.pi*j/n
            c, sn = math.cos(a), math.sin(a)
            pp = 3.4
            y = yc+w*math.copysign(abs(c)**(2/pp), c)
            z = zm+(top-zm)*abs(sn)**(2/pp) if sn >= 0 else zm-(zm-keel)*abs(sn)**(2/pp)
            ring.append((y, z))
        out.append((x, ring))
    return loft_x(kit, name, out, mat, 0)


def superloft(kit, name, xs, hw, top, keel, mat, yc=0.0, n=24, mid=.42, pt=P_TOP, pb=P_BOTTOM):
    """A closed loft through superellipse sections at stations xs: half-width hw(x)
    about yc, from keel(x) up to top(x), widest at mid of the way up."""
    stations = []
    for x in xs:
        w, zt, zk = max(hw(x), .3), top(x), keel(x)
        zm = zk+mid*(zt-zk)
        ring = []
        for k in range(n):
            a = 2*math.pi*k/n
            c, sn = math.cos(a), math.sin(a)
            p = pt if sn >= 0 else pb
            y = yc+w*math.copysign(abs(c)**(2/p), c)
            z = zm+(zt-zm)*abs(sn)**(2/p) if sn >= 0 else zm-(zm-zk)*abs(sn)**(2/p)
            ring.append((y, z))
        stations.append((x, ring))
    return loft_x(kit, name, stations, mat, 0)


def stations(x0, x1, polys, step=1.2):
    xs = {x0, x1}
    k = max(2, int((x1-x0)/step))
    xs.update(x0+(x1-x0)*i/k for i in range(k+1))
    xs.update(x for poly in polys for x, _ in poly if x0 < x < x1)
    return sorted(xs)


def octagon_yz(hw, z0, z1, top, bot):
    """A chamfered YZ section about the centreline: flat floor and roof, vertical walls."""
    return [(-hw+bot, z0), (hw-bot, z0), (hw, z0+bot), (hw, z1-top), (hw-top, z1), (-hw+top, z1),
            (-hw, z1-top), (-hw, z0+bot)][::-1]


def build(kit):
    red, steel, dark = kit.red, kit.steel, kit.dark
    # --- Prow (owner, round 6: "that nose - still feels weak"). ---
    # The head's full depth carried to the bow: a deep faceted prow that keeps the
    # sprite's breadth (the fork base, Y +-10, rows 8-10) to the outer tines' tips,
    # its deck raking down and its keel up into a chin, so in profile it is still the
    # round 5 wedge but about 8 units (6 m) deep at the face rather than 3.6. The
    # WS 004 gun runs in an open trough on the centreline (wf_mounts), its breech
    # under an armoured hood, and the sprite's two heavy outer tines are armoured
    # jaws either side of it, standing proud of the prow deck.
    fork = superloft(kit, 'Hull bow fork base', [42.6+i*(54.4-42.6)/12 for i in range(13)],
                     lambda x: line_at(PROW_HW, x), lambda x: line_at(PROW_TOP, x),
                     lambda x: line_at(PROW_KEEL, x), steel, n=8, pt=2.0, pb=2.0, mid=.5)
    plane_cut(fork, 'prow stem rake', (54.4, 0, -4.0), (math.cos(math.radians(24)), 0, math.sin(math.radians(24))))
    tines = []
    for side, s in (('port', 1), ('starboard', -1)):
        jaw = prism(kit, f'Hull bow {side} jaw', ccw([(x, s*y) for x, y in JAW]), section(-9.4, 4.8, 1.0, .9),
                    steel)
        plane_cut(jaw, f'{side} jaw rake', (44.0, 0, 4.8), facet_normal((1, 0), 15))
        plane_cut(jaw, f'{side} jaw chin', (47.0, 0, -9.4), (math.sin(math.radians(28)), 0,
                                                                -math.cos(math.radians(28))))
        plane_cut(jaw, f'{side} jaw bevel', (50.0, s*9.0, 3.4), (0, s*.6, .8))
        plane_cut(jaw, f'{side} jaw face', (54.7, 0, -3.0), (math.cos(math.radians(20)), 0, math.sin(math.radians(20))))
        tines.append(jaw)
    # The gun's hood: an armoured roof over the WS 004 breech where the trough
    # leaves the head, its face raked, the trough running on out under it.
    hood = prism(kit, 'Hull bow gun hood', ccw(sym([(47.0, 3.2), (46.0, 4.8), (40.6, 4.8), (40.0, 3.6)])),
                 section(.8, 6.0, .2, 1.0), steel)
    plane_cut(hood, 'gun hood face', (44.0, 0, 6.0), facet_normal((1, 0), 58))
    tines.append(hood)
    # --- Head, outriggers. ---
    plans = [ccw(HEAD), ccw(NECK), ccw(MAIN), ccw(AFT), ccw(ENGINE_BLOCK)]
    top = lambda x: line_at(TOP_LINE, x)
    keel_z = lambda x: line_at(KEEL_LINE, x)
    hull_hw = lambda x: plan_hw(plans, x)
    body = {}
    for name, x0, x1, mat in MODULES:
        octagon = name == 'Hull engine block'
        body[name] = superloft(kit, name, stations(x0, x1, plans), hull_hw, top, keel_z,
                               steel if mat == 'steel' else red, **(dict(n=8, pt=2.0, pb=2.0, mid=.5) if octagon else {}))
    head = body['Hull head']
    outriggers = {}
    for side, s, plan in (('port', 1, OUTRIGGER), ('starboard', -1, OUTRIGGER)):
        o = sponson(kit, f'Hull {side} outrigger', plan, OUTRIGGER_Z[1], OUTRIGGER_Z[0], steel, s)
        outriggers[side] = o

    neck, main = body['Hull neck'], body['Hull main body']
    pods = {}
    for side, s, plan in (('port', 1, WING), ('starboard', -1, WING)):
        # Shallow: about 9 units at the root, thinning to 6 at the launcher.
        p = sponson(kit, f'Pod {side} missile body', plan, lambda x: 1.2+.04*(x+19), lambda x: -7.8+.1*(x+19),
                    steel, s)
        pods[side] = p

    # --- Aft body, armour domes, engine block. ---
    aft = body['Hull aft body']
    kit.hull.append(aft)
    domes = []
    x, y, r = DOME
    import wf_kit
    for side, s in (('port', 1), ('starboard', -1)) if wf_kit.VARIANT == 'pirate' else ():
        # The pirate skin's armour over the removed WS 002 / WS 003 rings; the base
        # hull keeps its turrets there (wf_mounts).
        z0 = seat(kit, x, s*y, AFT_Z[1])
        domes.append(dome(kit, f'Hull {side} armour dome', x, s*y, r, z0-.6, 3.4, red))
    if wf_kit.VARIANT == 'merchant':
        # The base hull's aft turret wells (wolf_base.png rows 63-72: round rings
        # reaching Y +-18, the silhouette the pirate domes keep). Round 6 (owner: the
        # PD turrets "read like cylinders and are lifted quite high on the hull"): no
        # full-depth drums. Each turret stands in a well sunk into a low sponson, a
        # blister faired into the flank like the hull's own sections, its flat deck level
        # with the hull deck inboard of the ring, so the turret sits on the hull.
        for side, s in (('port', 1), ('starboard', -1)):
            deck = seat(kit, x, s*7.6, AFT_Z[1])
            R = r+.9
            xs = [x-R*1.25+2*R*1.25*i/16 for i in range(17)]
            tub = superloft(kit, f'Hull {side} turret foundation', xs,
                            lambda xx, R=R: R*max(.25, 1-((xx-x)/(R*1.25))**2)**.5, lambda xx: deck,
                            lambda xx: deck-9.0, red, yc=s*y, n=28, mid=.78, pt=6.0, pb=2.0)
            well = kit.cylinder(f'Temporary {side} turret well', (x, s*y, deck), 5.0, 2.0, kit.dark, 'Z', 32, 0)
            boolean(tub, well)
            domes.append(tub)
    eng = body['Hull engine block']

    # --- Spine: the grey centre structure from the engine block to the head. ---
    spine = prism(kit, 'Hull dorsal spine fore', ccw(sym([(32, 4.2), (17.5, 4.6), (16.2, 3.4)])),
                  section(0, SPINE_TOP, 0, 1.4), steel)
    plane_cut(spine, 'spine fore rake', (29, 0, SPINE_TOP), facet_normal((1, 0), 35))
    plane_cut(spine, 'spine fore aft rake', (25.0, 0, SPINE_TOP), facet_normal((-1, 0), 40))
    spine_aft = prism(kit, 'Hull dorsal spine aft', ccw(sym([(-2.5, 3.0), (-6, 3.6), (-33, 3.6)])),
                      section(0, SPINE_TOP-.6, 0, 1.3), steel)
    plane_cut(spine_aft, 'spine aft rake', (-29, 0, SPINE_TOP-.6), facet_normal((-1, 0), 30))
    plane_cut(spine_aft, 'spine aft fore rake', (-4, 0, SPINE_TOP-.6), facet_normal((1, 0), 40))
    # The bridge (owner, round 6: "maybe we can work out bridge being around there").
    # Both sprites put the ship's glazing forward: the white canopy on the head's
    # centreline (rows 13-16, X 38-41) right behind the bow gun, and the lit oval on
    # the neck spine (rows 24-34). The command deck is one raised, faceted block over
    # that stretch: its face raked forward over the WS 004 hood, a glazed band across
    # the face and down both flanks, its roof running aft onto the neck spine, where
    # the sprite's oval is its domed sensor housing. It stays below the WS 001 ion
    # cannon's line of fire.
    rows = []
    for x, hw, roof in BRIDGE:
        rows.append((x, [(y, z) for y, z in octagon_yz(hw, BRIDGE_FLOOR, roof, 1.5, .6)]))
    bridge = loft_x(kit, 'Hull bridge', rows, steel, 0)
    band = loft_x(kit, 'Hull bridge glazing', [(x, [(y*(1+.1/max(abs(y), .1)) if abs(y) > .1 else y, z+.08)
                                                    for y, z in sec]) for x, sec in rows], kit.glass, 0)
    slab = kit.box('Temporary bridge glazing slab', (36.4, 0, 8.25), (13.0, 30, 1.5), kit.glass, 0)
    boolean(band, slab, 'INTERSECT')
    for y in (-3.0, -1.0, 1.0, 3.0):                       # mullions: the band split into panes
        cutter = kit.box('Temporary bridge mullion', (41.0, y, 8.2), (6.0, .35, 3.0), kit.glass, 0)
        boolean(band, cutter)
    for x in (31.4, 33.8, 36.2):
        cutter = kit.box('Temporary bridge side mullion', (x, 0, 8.2), (.35, 30, 3.0), kit.glass, 0)
        boolean(band, cutter)
    zs = seat(kit, 25.0, 0, SPINE_TOP)
    sensor = dome(kit, 'Hull neck sensor dome', 25.0, 0, 2.8, zs-.4, 2.0, steel, 14, 3)
    zr = 10.2
    roof = dome(kit, 'Hull bridge roof sensor dome', 32.6, 0, 1.9, zr-.3, 1.3, kit.glass, 12, 3)
    kit.box('Hull bridge roof mast', (30.4, 0, zr+1.2), (.5, .5, 2.6), steel, .05)
    kit.box('Hull bridge roof mast yard', (30.4, 0, zr+2.1), (.4, 3.6, .3), steel, .05)
    kit.hull += [bridge, sensor]
    keel_plan = [ccw(sym([(27, 3.2), (24, 5.0), (-16, 5.0), (-20, 3.6)]))]
    keel = superloft(kit, 'Hull ventral keel', stations(-19.8, 26.8, keel_plan, 1.5), lambda x: plan_hw(keel_plan, x),
                     lambda x: line_at(KEEL_LINE, x)+3.0, lambda x: line_at(KEEL_LINE, x)-3.4, steel, n=16, mid=.6)
    kit.hull += [fork, *tines, head, *outriggers.values(), neck, main, *pods.values(), aft, *domes, eng, spine, spine_aft]
    return keel
