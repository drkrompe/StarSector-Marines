"""Round 7 detail (owner: "next it needs plausible detail pass"): the fittings a
working 72 m frigate carries, laid on the round 6 forms without moving them.

Read off both sprites where they show it, inferred where they do not, and sized
for the ship (0.76 m per unit: a person is 2.4 units, a door 1.3 x 2.9, a deck
hatch 1.2 square), so nothing here is a greeble:
- plate seams: dark joint lines that follow the lofted forms, along the decks,
  down the flanks and round the flank belt; across the prow and the bridge roof;
- crew access: deck hatches on the neck and engine decks and the bridge roof, a
  crew door in each neck flank, bolted maintenance panels on the main-body, aft
  and engine-block flanks;
- the bridge: a visor over the glazed face, frame lips along the side glazing,
  a comms dish, whip antennas, a sensor box and a roof hatch;
- the drive section's heat exchangers: finned radiator wells on the engine
  deck and louvred exchanger panels on its flanks;
- RCS thruster clusters at the jaws, the wing tips and the stern corners;
- sensors and comms: a ventral sensor blister, a neck antenna mast;
- a ventral docking collar with its two docking lights, a masthead light, and
  tie-down cleats along the deck edges;
- conduits along both wing roots, bracketed to the hull;
- on the pirate only, more mismatched plates welded on (jaw, neck flank,
  engine block) with beads round them: "heavily modified - and poorly maintained".
Every object is 'Hull ...' or 'Pod ...' named and in the hull's own materials,
so the surfacing gives it the class swatch; none of it is a surfacing member,
so no part tile moves.
"""
import math
import bmesh
import bpy
from mathutils import Matrix, Vector
from mathutils.bvhtree import BVHTree

SIDES = (('port', 1), ('starboard', -1))
_TREE = []


def _tree(kit):
    """One BVH over the hull objects, for side and upward rays (kit.surface casts down only)."""
    if not _TREE:
        dg = bpy.context.evaluated_depsgraph_get()
        verts, polys = [], []
        for ob in kit.hull:
            ev = ob.evaluated_get(dg)
            me = ev.to_mesh()
            base = len(verts)
            verts += [ob.matrix_world @ v.co for v in me.vertices]
            polys += [[base+i for i in p.vertices] for p in me.polygons]
            ev.to_mesh_clear()
        _TREE.append(BVHTree.FromPolygons(verts, polys))
    return _TREE[0]


def down(kit, x, y):
    hit = kit.surface(x, y)
    return (hit[0], hit[1]) if hit else (None, None)


def side(kit, x, z, s):
    """Outermost flank point at (x, z) on the s (+1 port, -1 starboard) side, and its normal."""
    p, n, _, _ = _tree(kit).ray_cast(Vector((x, s*80.0, z)), Vector((0, -s, 0)))
    return (p, n if n.y*s > 0 else -n) if p is not None else (None, None)


def up(kit, x, y):
    p, n, _, _ = _tree(kit).ray_cast(Vector((x, y, -80.0)), Vector((0, 0, 1)))
    return (p, n if n.z < 0 else -n) if p is not None else (None, None)


def frame(p, n, yaw=0.0):
    """World matrix at p with local +Z along the normal n and local +X as near hull +X as it can be."""
    z = Vector(n).normalized()
    ref = Vector((1, 0, 0)) if abs(z.x) < .9 else Vector((0, 0, 1))
    x = (ref-z*ref.dot(z)).normalized()
    y = z.cross(x)
    m = Matrix((x, y, z)).transposed().to_4x4()
    m.translation = p
    return m @ Matrix.Rotation(math.radians(yaw), 4, 'Z')


def put(ob, m):
    ob.matrix_world = m @ ob.matrix_world
    return ob


def join(objs, name):
    objs = [o for o in objs if o]
    if len(objs) == 1:
        objs[0].name = name
        return objs[0]
    with bpy.context.temp_override(active_object=objs[0], selected_editable_objects=objs):
        bpy.ops.object.join()
    objs[0].name = objs[0].data.name = name
    return objs[0]


def plate(kit, name, m, w, h, mat, t=.25, gap=.18, bolts=True):
    """An access panel on the surface frame m: a dark joint round a bolted plate."""
    seam = kit.box(name+' joint', (0, 0, t*.3), (w+2*gap, h+2*gap, t*.6), kit.dark, 0)
    lid = kit.box(name, (0, 0, t*.5+.04), (w, h, t), mat, .06)
    parts = [put(lid, m)]
    if bolts:
        for bx in (-w/2+.3, w/2-.3):
            for by in (-h/2+.3, h/2-.3):
                parts.append(put(kit.cylinder(name+' bolt', (bx, by, t+.06), .1, .12, kit.steel, 'Z', 6, 0), m))
    join(parts, name)
    return put(seam, m)


def strip(kit, name, pts, width, height, mat):
    """A strip laid along surface points [(p, n), ...]: a seam line or a bead."""
    verts, faces = [], []
    pts = [(p, n) for p, n in pts if p is not None]
    if len(pts) < 2:
        return None
    for i, (p, n) in enumerate(pts):
        a = pts[max(i-1, 0)][0]
        b = pts[min(i+1, len(pts)-1)][0]
        t = (b-a).normalized()
        nn = Vector(n).normalized()
        across = t.cross(nn).normalized()*width/2
        base = p-nn*.05
        top = p+nn*height
        verts += [base+across, base-across, top-across, top+across]
    for i in range(len(pts)-1):
        a, b = 4*i, 4*(i+1)
        faces += [(a, a+1, b+1, b), (a+1, a+2, b+2, b+1), (a+2, a+3, b+3, b+2), (a+3, a, b, b+3)]
    faces += [(0, 1, 2, 3), tuple(reversed(range(4*(len(pts)-1), 4*len(pts))))]
    return kit.mesh(name, [tuple(v) for v in verts], faces, mat)


def pipe(kit, name, a, b, r, mat):
    a, b = Vector(a), Vector(b)
    ob = kit.cylinder(name, (0, 0, 0), r, (b-a).length, mat, 'Z', 10, 0)
    q = Vector((0, 0, 1)).rotation_difference((b-a).normalized())
    ob.matrix_world = Matrix.Translation((a+b)/2) @ q.to_matrix().to_4x4()
    return ob


def seams(kit):
    d = kit.dark
    # Decks: two longitudinal joints a side, following the crowned deck.
    for side_, s in SIDES:
        for k, (y, x0, x1) in enumerate(((6.6, -17.5, 29.0), (12.2, -5.6, 16.0)), 1):
            pts = [down(kit, x0+(x1-x0)*i/40, s*y) for i in range(41)]
            strip(kit, f'Hull deck seam {side_} {k}', pts, .22, .07, d)
        # Flanks: the belt joint along the widest line, and frame joints down it.
        pts = [side(kit, x, -1.0, s) for x in [-18.0+i*1.2 for i in range(41)]]
        strip(kit, f'Hull flank belt seam {side_}', pts, .22, .07, d)
        for k, x in enumerate((24.0, 8.0, -4.6, -14.0), 1):
            pts = [side(kit, x, z, s) for z in [-9.0+i*.6 for i in range(25)]]
            strip(kit, f'Hull flank frame seam {side_} {k}', pts, .22, .07, d)
    # Across the decks, clear of the spine and the bridge.
    for k, x in enumerate((8.0, 24.0, -12.0, 38.0), 4):
        clear = 6.2 if x > 30 else 4.4
        for side_, s in SIDES:
            pts = [down(kit, x, s*(clear+(13.8-clear)*i/14)) for i in range(15)]
            strip(kit, f'Hull deck seam {k} {side_}', pts, .22, .07, d)
    # The prow and the bridge roof.
    pts = [down(kit, 48.2, y) for y in [3.9+.2*i for i in range(7)]]
    strip(kit, 'Hull prow seam port', pts, .22, .07, d)
    strip(kit, 'Hull prow seam starboard', [down(kit, 48.2, -y) for y in [3.9+.2*i for i in range(7)]], .22, .07, d)
    for k, x in enumerate((31.6, 35.0), 1):
        strip(kit, f'Hull bridge roof seam {k}', [down(kit, x, y) for y in [-4.0+.5*i for i in range(17)]], .2, .06, d)


def access(kit):
    steel, red = kit.steel, kit.red
    for side_, s in SIDES:
        # Deck hatches: neck and engine decks.
        for k, (x, y) in enumerate(((22.0, 8.6), (-25.5, 7.4)), 1):
            p, n = down(kit, x, s*y)
            plate(kit, f'Hull {side_} deck hatch {k}', frame(p, n), 1.3, 1.3, steel)
            put(kit.box(f'Hull {side_} deck hatch {k} handle', (.3, 0, .38), (.12, .7, .1), steel, 0), frame(p, n))
        # A crew door in each neck flank: 1.3 x 2.9 units (1.0 x 2.2 m).
        p, n = side(kit, 23.0, -2.0, s)
        m = frame(p, n, 0)                 # local X along the hull, local Y vertical
        put(kit.box(f'Hull {side_} crew door frame', (0, 0, .1), (2.0, 3.6, .2), kit.dark, 0), m)
        put(kit.box(f'Hull {side_} crew door', (0, 0, .22), (1.3, 2.9, .2), steel, .05), m)
        put(kit.box(f'Hull {side_} crew door handle', (.4, 0, .38), (.1, .5, .1), steel, 0), m)
        # Maintenance panels on the flanks: main body, aft body, engine block.
        for k, (x, z, w, h) in enumerate(((10.0, -5.0, 3.4, 2.2), (1.0, -5.0, 3.4, 2.2), (-11.0, -4.0, 3.0, 2.4),
                                          (-24.5, -1.5, 3.6, 2.6)), 1):
            p, n = side(kit, x, z, s)
            if p is not None:
                plate(kit, f'Hull {side_} access panel {k}', frame(p, n, 0), w, h, steel if x < -20 else red)


def bridge(kit):
    steel, glass = kit.steel, kit.glass
    # Visor over the glazed face; frame lips along the side glazing.
    kit.box('Hull bridge visor', (39.0, 0, 10.0), (1.7, 10.4, .26), steel, .06)
    for side_, s in SIDES:
        for k, z in enumerate((9.15, 7.35), 1):
            kit.box(f'Hull bridge {side_} glazing lip {k}', (33.6, s*5.9, z), (8.6, .22, .2), steel, 0)
    zr = 10.2
    # Comms dish on a short pedestal, aft on the roof.
    kit.cylinder('Hull bridge dish pedestal', (28.4, -2.2, zr+.5), .35, 1.2, steel, 'Z', 10, 0)
    bpy.ops.mesh.primitive_cone_add(vertices=16, radius1=1.5, radius2=.25, depth=.6, location=(0, 0, 0))
    dish = bpy.context.object
    dish.matrix_world = Matrix.Translation((28.0, -2.2, zr+1.4)) @ Matrix.Rotation(math.radians(-115), 4, 'Y')
    bpy.ops.object.transform_apply(location=False, rotation=True, scale=False)
    kit.finish(dish, 'Hull bridge comms dish', steel)
    for side_, s in SIDES:
        kit.cylinder(f'Hull bridge {side_} whip antenna', (36.2, s*4.4, zr+1.5), .07, 3.0, steel, 'Z', 6, 0)
        kit.cylinder(f'Hull bridge {side_} whip antenna base', (36.2, s*4.4, zr+.1), .25, .3, steel, 'Z', 8, 0)
    kit.box('Hull bridge sensor box', (28.6, 2.4, zr+.35), (1.6, 1.2, .8), steel, .08)
    kit.box('Hull bridge sensor window', (29.42, 2.4, zr+.45), (.06, .8, .35), glass, 0)
    p, n = down(kit, 34.8, -2.8)
    plate(kit, 'Hull bridge roof hatch', frame(p, n), 1.2, 1.2, steel)


def drive_section(kit):
    steel, dark = kit.steel, kit.dark
    # Radiator wells on the engine deck, finned.
    for side_, s in SIDES:
        p, n = down(kit, -23.2, s*7.2)
        m = frame(p, n)
        put(kit.box(f'Hull engine {side_} radiator well', (0, 0, .05), (5.4, 3.0, .2), dark, 0), m)
        fins = [put(kit.box(f'Hull engine {side_} radiator fin', (-2.2+i*.55, 0, .25), (.14, 2.7, .45), steel, 0), m)
                for i in range(9)]
        join(fins, f'Hull engine {side_} radiator fins')
        put(kit.box(f'Hull engine {side_} radiator frame', (0, 0, .3), (5.8, 3.4, .12), steel, 0), m)
        # Louvred exchanger panels on the flank, behind the aft body.
        for k, x in enumerate((-22.0, -26.0), 1):
            p, n = side(kit, x, 1.0, s)
            if p is None:
                continue
            m = frame(p, n)
            put(kit.box(f'Hull engine {side_} exchanger {k} well', (0, 0, .05), (3.2, 3.0, .2), dark, 0), m)
            louvres = [put(kit.box(f'Hull engine {side_} exchanger louvre', (0, -1.1+j*.45, .2), (2.9, .16, .3),
                                   steel, 0), m) for j in range(6)]
            join(louvres, f'Hull engine {side_} exchanger {k} louvres')


def rcs(kit):
    """Quad thruster blocks: two nozzles outboard along the surface normal, one fore or aft."""
    steel, dark = kit.steel, kit.dark
    spots = [('bow', 51.0, -1.0, 1), ('wing', 8.0, -1.4, 1), ('stern', -33.8, 2.0, -1)]
    for side_, s in SIDES:
        for where, x, z, fore in spots:
            p, n = side(kit, x, z, s)
            if p is None:
                continue
            m = frame(p, n)
            parts = [kit.box('rcs', (0, 0, .35), (1.5, 1.2, .8), steel, .08)]
            for dy in (-.3, .3):
                parts.append(kit.cylinder('rcs n', (0, dy, .85), .2, .4, steel, 'Z', 10, 0))
                parts.append(kit.cylinder('rcs b', (0, dy, 1.04), .13, .05, dark, 'Z', 10, 0))
            parts.append(kit.cylinder('rcs n', (fore*.9, 0, .4), .2, .4, steel, 'X', 10, 0))
            for o in parts:
                put(o, m)
            join(parts, f'Hull {side_} {where} RCS cluster')


def sensors_and_docking(kit):
    from wf_lights import lamp
    steel, dark, glass = kit.steel, kit.dark, kit.glass
    # Ventral sensor blister under the neck.
    p, n = up(kit, 22.0, 0.0)
    if p is not None:
        bpy.ops.mesh.primitive_uv_sphere_add(segments=14, ring_count=7, radius=1.0, location=p)
        ob = bpy.context.object
        ob.scale = (2.4, 1.8, 1.1)
        bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
        kit.finish(ob, 'Hull ventral sensor blister', glass)
        kit.cylinder('Hull ventral sensor blister ring', (p.x, 0, p.z-.15), 2.0, .35, steel, 'Z', 16, 0)
    # Neck antenna mast on the spine.
    p, n = down(kit, 18.6, 0)
    kit.cylinder('Hull neck antenna mast', (18.6, 0, p.z+1.6), .14, 3.2, steel, 'Z', 8, 0)
    kit.box('Hull neck antenna yard', (18.6, 0, p.z+2.6), (.25, 2.6, .2), steel, 0)
    # Ventral docking collar under the main body, with its two docking lights.
    p, n = up(kit, 2.0, 0.0)
    if p is not None:
        z = p.z
        bpy.ops.mesh.primitive_cylinder_add(vertices=20, radius=2.3, depth=.7, location=(2.0, 0, z-.25))
        collar = bpy.context.object
        kit.finish(collar, 'Hull ventral docking collar', steel, .06)
        kit.cylinder('Hull ventral docking hatch', (2.0, 0, z-.62), 1.5, .1, dark, 'Z', 20, 0)
        white = bpy.data.materials['Wolf running light white']
        mats = {'white': white}
        for k, y in enumerate((3.0, -3.0), 1):
            lamp(kit, f'Light ventral docking {k}', (2.0, y, z-.3), 'white', 'docking light', 0, 0, mats, .3)
    # Masthead light on the bridge mast.
    lamp(kit, 'Light centre masthead 1', (30.4, 0, 10.2+2.75), 'white', 'masthead light', 0, 0,
         {'white': bpy.data.materials['Wolf running light white']}, .3)
    # Tie-down cleats along the deck edges.
    cleats = []
    for side_, s in SIDES:
        for x in (27.0, 20.0, 12.0, 4.0, -3.0, -9.5):
            p, n = down(kit, x, s*13.2)
            if p is None:
                continue
            m = frame(p, n)
            cleats += [put(kit.box('cleat', (0, 0, .15), (.9, .35, .3), steel, .04), m),
                       put(kit.box('cleat', (0, 0, .35), (.3, .2, .3), steel, 0), m)]
    join(cleats, 'Hull deck tie-down cleats')


def wing_conduits(kit):
    steel = kit.steel
    for side_, s in SIDES:
        path = [(-14.0, 16.4), (-8.0, 16.6), (-2.0, 16.9), (4.0, 17.3)]
        for k, (r, dy, dz) in enumerate(((.32, 0.0, .45), (.22, .75, .35)), 1):
            pts = []
            for x, y in path:
                p, n = down(kit, x, s*(y+dy))
                pts.append(Vector((x, s*(y+dy), p.z+dz)))
            segs = [pipe(kit, 'c', a, b, r, steel) for a, b in zip(pts, pts[1:])]
            join(segs, f'Pod {side_} wing root conduit {k}')
        brackets = []
        for x, y in path[:-1]:
            p, n = down(kit, x+1.0, s*(y+.4))
            brackets.append(put(kit.box('b', (0, 0, .3), (.35, 1.6, .55), steel, 0), frame(p, n)))
        join(brackets, f'Pod {side_} wing root conduit brackets')


def pirate_plates(kit):
    red, steel = kit.red, kit.steel

    def welded(name, m, w, h, mat):
        lid = put(kit.box(name, (0, 0, .2), (w, h, .3), mat, .05), m)
        bead = [put(kit.box('w', (0, sy*(h/2+.08), .12), (w+.3, .16, .16), steel, 0), m) for sy in (1, -1)]
        bead += [put(kit.box('w', (sx*(w/2+.08), 0, .12), (.16, h+.3, .16), steel, 0), m) for sx in (1, -1)]
        join(bead, name+' weld bead')
        return lid
    for side_, s in SIDES:
        p, n = down(kit, 47.6, s*8.0)
        welded(f'Hull bow {side_} jaw patch plate', frame(p, n, 12*s), 3.0, 2.2, red)
        p, n = side(kit, 28.0, 1.0, s)
        if p is not None:
            welded(f'Hull neck {side_} flank patch plate', frame(p, n, 6*s), 3.6, 2.4, steel)
        p, n = side(kit, -29.0, -3.5, s)
        if p is not None:
            welded(f'Hull engine {side_} patch plate', frame(p, n, -8*s), 3.0, 2.6, red)


def build(kit):
    import wf_kit
    _TREE.clear()
    seams(kit)
    access(kit)
    bridge(kit)
    drive_section(kit)
    rcs(kit)
    sensors_and_docking(kit)
    wing_conduits(kit)
    if wf_kit.VARIANT == 'pirate':
        pirate_plates(kit)
