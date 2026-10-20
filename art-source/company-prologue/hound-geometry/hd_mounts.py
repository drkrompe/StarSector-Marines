"""The three weapon slots of hound.ship, at their exact locations and angles.

WS 001  HARDPOINT MEDIUM BALLISTIC  (35, -6)  angle 0    arc 5
WS 002  TURRET    SMALL  BALLISTIC  (-4,  4)  angle 145  arc 290
WS 003  HIDDEN    SMALL  SYSTEM     (-19, 0)  angle 180  arc 90  (flare launcher)

Hound (A) carries Armored Weapon Mounts as a built-in hullmod, so the turret
barbette has a heavier armour collar than the Second Wind's small mounts.
No weapon positions are invented, and the hull has no built-in weapons:
the guns are generic ballistic fittings sized by slot, resting at the slot
angle. Every object is named 'Weapon <slot id> ...' and each slot has an
anchor empty whose +X points along the slot angle.

WS 001 is the shared turret kit's Assault Chaingun hardpoint
(tk.build_hardpoint('chaingun'), k 0.5) on the empty 'Weapon WS 001 mount',
sunk in a bay cut out of the fork base and the tine roots: a breech housing,
drive motor, feed chute and ammunition box, a fixed cooling jacket and the
six-barrel cluster on 'Weapon WS 001 rotor' inside 'Weapon WS 001 recoil 1';
its one muzzle ('Weapon WS 001 muzzle anchor 1') is on the cluster axis, level
just short of the tine points. Both states carry it (it replaced the hull's own naval
gun and the Hound (P)'s own chaingun, 2026-10-20). WS 002 is a small naval turret on a thick bolted
barbette (the sprite's ring, about ten pixels across): turret house with a
sloped glacis and cheek armour, a mantlet on trunnions, an armour skirt
over the traverse gap, a cupola with vision blocks, a gunner's sight and an
external feed. WS 003 is a flush flare dispenser in the stern notch: an
armoured shutter frame in hazard stripes round a grid of aft-facing tubes,
some cells capped as spares.
"""
import math
import sys
from pathlib import Path
import bpy
from mathutils import Matrix, Vector
from mathutils.bvhtree import BVHTree
from hd_kit import boolean, material
from hd_fork import bolts, boxes, loft


SLOTS = [
    {'id': 'WS 001', 'mount': 'HARDPOINT', 'size': 'MEDIUM', 'type': 'BALLISTIC', 'location': (35, -6), 'angle': 0, 'arc': 5},
    {'id': 'WS 002', 'mount': 'TURRET', 'size': 'SMALL', 'type': 'BALLISTIC', 'location': (-4, 4), 'angle': 145, 'arc': 290},
    {'id': 'WS 003', 'mount': 'HIDDEN', 'size': 'SMALL', 'type': 'SYSTEM', 'location': (-19, 0), 'angle': 180, 'arc': 90},
]
ANCHOR_Z = {}
CLASHES = {}  # slot id -> names of parts its gun overlaps (filled by build)
METRES_PER_UNIT = .88   # build_hound.METRES_PER_UNIT (the kit sizes its detail in metres)

HOUSE_BASE = [(3.2, -1.5), (3.2, 1.5), (1.9, 2.6), (-2.6, 2.6), (-2.6, -2.6), (1.9, -2.6)]
HOUSE_ROOF = [(1.0, -1.0), (1.0, 1.0), (.3, 2.0), (-2.3, 2.0), (-2.3, -2.0), (.3, -2.0)]
HOUSE_H = 2.0


def loft_z(kit, name, base, roof, z0, z1, mat, bevel=.1):
    n = len(base)
    verts = [(x, y, z0) for x, y in base] + [(x, y, z1) for x, y in roof]
    faces = [(i, (i+1) % n, n+(i+1) % n, n+i) for i in range(n)]
    faces += [tuple(reversed(range(n))), tuple(range(n, 2*n))]
    return kit.mesh(name, verts, faces, mat, bevel)


def _bvh(ob):
    dg = bpy.context.evaluated_depsgraph_get()
    ev = ob.evaluated_get(dg)
    me = ev.to_mesh()
    verts = [ev.matrix_world @ v.co for v in me.vertices]
    polys = [tuple(p.vertices) for p in me.polygons]
    ev.to_mesh_clear()
    return BVHTree.FromPolygons(verts, polys)


def overlaps(parts, others):
    """Names of `others` whose surfaces intersect any of `parts`."""
    bpy.context.view_layer.update()
    trees = [_bvh(p) for p in parts]
    hits = []
    for o in others:
        t = _bvh(o)
        if any(p.overlap(t) for p in trees):
            hits.append(o.name)
    return hits


def _face_plate(kit, name, quad, out, t, inset, mat, bevel=.04):
    """A plate lying on a planar quad face, shrunk by `inset`, standing `t` proud along `out`."""
    c = sum((Vector(p) for p in quad), Vector())/4
    lower = [Vector(p)+(c-Vector(p)).normalized()*inset-out*.05 for p in quad]
    upper = [p+out*(t+.05) for p in lower]
    return loft(kit, name, lower, upper, mat, bevel)


# --- WS 001: the turret kit's chaingun, sunk in a bay in the fork ---

# The kit's Assault Chaingun hardpoint (turret-kit/turret_hardpoints.py, 'chaingun'),
# at K hull units per sprite pixel: at k 1 its cooling jacket is 10 units across and
# the fork's gun gap is 6, so it is built at 0.5, where the jacket (r 2.6) and the
# barrel clamps (r 2.0) clear the tines, the gussets and the cross bar. Its mount
# empty sits at MOUNT_X with the axis at AXIS_Z: the housing fills the fork base
# from the cargo body's front face (X 20.5) forward, and the muzzle (29 px ahead of
# the mount) is just short of the tine points.
K = .5
MOUNT_X = 24.95
AXIS_Z = .2
# The bay, in the mount's frame (x0, x1 along the fire line, y0, y1 across it, the
# floor below the axis; open to the deck): the housing's pit, the drive motor's
# pocket into the port tine root, and the starboard pocket the feed chute and
# ammunition box sit in (the starboard tine's root). Margins about 0.2 units.
BAY = [(-4.45, 5.1, -4.2, 3.8, -2.5),
       (-3.1, 3.7, 3.6, 5.45, -1.8),
       (1.55, 4.45, -7.45, -3.9, -.75)]


def _cut_bay(kit, sid, mount, box):
    """Cut a box (mount frame; open to the deck) out of every fork part it reaches,
    baking the result with its chamfer and merging the boolean's slivers (as
    lasher-geometry's _cut_bay). Returns the names of the cut parts."""
    import bmesh
    x0, x1, y0, y1, floor = box
    bpy.ops.mesh.primitive_cube_add(size=1)
    cutter = bpy.context.object
    cutter.name = f'Temporary bay cutter {sid}'
    cutter.parent = mount
    cutter.scale = (x1-x0, y1-y0, 30)
    cutter.location = ((x0+x1)/2, (y0+y1)/2, floor+15)
    bpy.context.view_layer.update()
    corners = [cutter.matrix_world @ Vector(c) for c in cutter.bound_box]
    lo, hi = Vector(map(min, *corners)), Vector(map(max, *corners))
    cut = []
    for ob in list(bpy.context.scene.objects):
        if ob.type != 'MESH' or ob == cutter or not ob.name.startswith('Fork '):
            continue
        bb = [ob.matrix_world @ Vector(c) for c in ob.bound_box]
        if any(max(c[i] for c in bb) < lo[i] or min(c[i] for c in bb) > hi[i] for i in range(3)):
            continue
        for bm_ in ob.modifiers:
            if bm_.type == 'BEVEL' and bm_.limit_method == 'ANGLE':
                bm_.angle_limit = max(bm_.angle_limit, math.radians(65))
        mod = ob.modifiers.new('bay', 'BOOLEAN')
        mod.operation, mod.solver, mod.object = 'DIFFERENCE', 'EXACT', cutter
        bpy.context.view_layer.update()
        dg = bpy.context.evaluated_depsgraph_get()
        me = bpy.data.meshes.new_from_object(ob.evaluated_get(dg))
        bm = bmesh.new()
        bm.from_mesh(me)
        bmesh.ops.dissolve_limit(bm, angle_limit=math.radians(1.5), verts=bm.verts, edges=bm.edges)
        bm.to_mesh(me)
        bm.free()
        own = next((m for m in me.materials if m is not None), None)
        for i, m in enumerate(me.materials):
            if m is None:
                me.materials[i] = own
        old = ob.data
        ob.modifiers.clear()
        ob.data = me
        me.name = old.name
        if old.users == 0:
            bpy.data.meshes.remove(old)
        if not me.polygons:            # wholly inside the bay
            name = ob.name
            if ob in kit.hull:
                kit.hull.remove(ob)
            bpy.data.objects.remove(ob, do_unlink=True)
            cut.append(f'{name} (removed)')
            continue
        cut.append(ob.name)
    cm = cutter.data
    bpy.data.objects.remove(cutter, do_unlink=True)
    bpy.data.meshes.remove(cm)
    return cut


HARDPOINT_RECORD = {}


def hardpoint(kit, slot):
    """WS 001: the kit's chaingun (tk.build_hardpoint) on 'Weapon WS 001 mount', sunk in
    a bay cut out of the fork base and the tine roots. The old naval gun's trough
    fittings ('Fork trough ...') are removed first: the kit housing fills the trough."""
    sys.path.insert(0, str(Path(__file__).resolve().parent.parent/'turret-kit'))
    import turret_kit as tk
    x, y = slot['location']
    t = slot['id']
    for ob in [o for o in bpy.data.objects if o.name.startswith('Fork trough ')]:
        bpy.data.objects.remove(ob, do_unlink=True)
    mount = kit.empty(f'Weapon {t} mount', (MOUNT_X, y, AXIS_Z), slot['angle'])
    mount.empty_display_size = 2
    mats = {'armour': kit.orange, 'plate': kit.steel, 'steel': kit.steel, 'dark': kit.dark}
    hp = tk.build_hardpoint('chaingun', f'Weapon {t}', mount, mats, METRES_PER_UNIT, k=K)
    cut = []
    for box in BAY:
        cut += _cut_bay(kit, t, mount, box)
    bpy.context.view_layer.update()
    ANCHOR_Z[t] = AXIS_Z
    kit.empty(f'Weapon {t} anchor', (x, y, AXIS_Z), slot['angle'])
    muzzle = hp['muzzles'][0]
    HARDPOINT_RECORD.clear()
    HARDPOINT_RECORD.update({
        'slot': t, 'weapon': 'chaingun', 'built_by': 'turret-kit/turret_kit.py build_hardpoint', 'k': K,
        'mount': mount.name, 'mount_location': [MOUNT_X, y, AXIS_Z],
        'bay': [list(b) for b in BAY], 'bay_cut_from': sorted(set(cut)),
        'parts': [o.name for o in hp['parts']], 'moving': [e.name for e in hp['moving']],
        'kit_muzzles': [m.name for m in hp['muzzles']],
        'muzzle_location': [round(v, 2) for v in muzzle.matrix_world.translation]})
    gun = [o for o in hp['parts'] if o.type == 'MESH']
    fork = [o for o in bpy.data.objects if o.type == 'MESH' and o.name.startswith('Fork') and
            ('tine' in o.name or 'cross bar' in o.name or 'gusset' in o.name or 'sensor' in o.name)]
    CLASHES[t] = overlaps(gun, fork)


# --- WS 002: small naval turret on an armoured barbette ---

def turret(kit, slot, gun=None):
    """gun: None for the single naval gun, else fn(kit, slot, x, y, gz) that builds
    a different weapon in front of the mantlet, at rest along +X, before training."""
    x, y = slot['location']
    t = slot['id']
    glass = material('Vision block glass', (.03, .05, .06), .3, .1)
    R = 4.3
    zs = []
    for k in range(13):
        a = k*math.tau/12
        px, py = (x, y) if k == 12 else (x+R*math.cos(a), y+R*math.sin(a))
        hit = kit.surface(px, py)
        if hit:
            zs.append(hit[0].z)
    if kit.surface(x, y) is None:
        raise RuntimeError(f'{t} has no supporting hull surface')
    zmax, zmin = max(zs), min(zs)
    lift = .25  # raised barbette: clears the barrel over the deck at rest
    # Armoured barbette (Armored Weapon Mounts): foot sunk into the deck,
    # a thick octagonal collar faced with bolted plates, the ring, the gap.
    kit.cylinder(f'Weapon {t} barbette foot', (x, y, (zmin-.4+zmax+.3)/2), R+.5, zmax+.7-zmin, kit.steel, 'Z', 12, .1)
    zc0, zc1 = zmax+.3, zmax+1.9+lift
    col = kit.cylinder(f'Weapon {t} armoured collar', (x, y, (zc0+zc1)/2), R+.1, zc1-zc0, kit.orange, 'Z', 8, .1)
    col.rotation_euler[2] = math.pi/8
    ap = (R+.1)*math.cos(math.pi/8)
    side = 2*(R+.1)*math.sin(math.pi/8)
    bolt_pts, bolt_ax = [], []
    for k in range(8):
        a = k*math.pi/4
        u, v = Vector((math.cos(a), math.sin(a), 0)), Vector((-math.sin(a), math.cos(a), 0))
        c = Vector((x, y, (zc0+zc1)/2+.05))+u*ap
        h, w = (zc1-zc0)-.35, side-.45
        quad = [c-v*w/2-Vector((0, 0, h/2)), c+v*w/2-Vector((0, 0, h/2)), c+v*w/2+Vector((0, 0, h/2)), c-v*w/2+Vector((0, 0, h/2))]
        _face_plate(kit, f'Weapon {t} barbette armour plate {k+1}', quad, u, .2, 0, kit.orange, .04)
        bolt_pts += [c+u*.2+v*s*(w/2-.3)+Vector((0, 0, dz)) for s in (-1, 1) for dz in (-h/2+.25, h/2-.25)]
        bolt_ax += [tuple(u)]*4
    bolts(kit, f'Weapon {t} barbette bolts', [tuple(p) for p in bolt_pts], bolt_ax, kit.steel, .1, .06)
    kit.cylinder(f'Weapon {t} turret ring', (x, y, zc1+.25), R-.3, .5, kit.steel, 'Z', 16, .06)
    kit.cylinder(f'Weapon {t} traverse gap', (x, y, zc1+.6), R-1.4, .3, kit.dark, 'Z', 16, 0)
    zb = zc1+.7
    fixed = set(bpy.data.objects)

    # Everything below rotates with the turret; built pointing +X, then trained.
    P = lambda pts: [(x+px, y+py) for px, py in pts]
    loft_z(kit, f'Weapon {t} gun house', P(HOUSE_BASE), P(HOUSE_ROOF), zb, zb+HOUSE_H, kit.steel)
    bb = [(-2.4, -2.1), (-2.4, 2.1), (-3.4, 1.9), (-4.0, 1.3), (-4.0, -1.3), (-3.4, -1.9)]
    loft_z(kit, f'Weapon {t} rear bustle', P(bb), P([(px+.1, py*.86) for px, py in bb]), zb+.15, zb+1.6, kit.steel)
    W = lambda px, py, pz: Vector((x+px, y+py, zb+pz))
    # Glacis plate and cheek armour, each a separate proud plate.
    g = [W(3.2, -1.5, 0), W(3.2, 1.5, 0), W(1.0, 1.0, HOUSE_H), W(1.0, -1.0, HOUSE_H)]
    gn = (g[1]-g[0]).cross(g[3]-g[0]).normalized()
    gn = gn if gn.x > 0 else -gn
    _face_plate(kit, f'Weapon {t} glacis plate', g, gn, .18, .22, kit.rust)
    for name, s in (('port', 1), ('starboard', -1)):
        q = [W(1.9, s*2.6, 0), W(-2.6, s*2.6, 0), W(-2.3, s*2.0, HOUSE_H), W(.3, s*2.0, HOUSE_H)]
        qn = (q[1]-q[0]).cross(q[3]-q[0]).normalized()
        qn = qn if qn.y*s > 0 else -qn
        _face_plate(kit, f'Weapon {t} cheek plate {name}', q, qn, .16, .2, kit.rust)
    # Armour skirt: a sloped apron round the house foot, hanging out over
    # the traverse gap.
    SK = [(3.45, -2.0), (3.45, 2.0), (2.1, 3.3), (-3.3, 3.3), (-3.3, -3.3), (2.1, -3.3)]
    loft_z(kit, f'Weapon {t} armour skirt', P(SK), P(HOUSE_BASE), zb-.3, zb+.45, kit.steel, .05)
    # Mantlet: armoured face on a drum between trunnions.
    gz = zb+.95
    kit.cylinder(f'Weapon {t} mantlet drum', (x+2.4, y, gz), .8, 2.0, kit.rust, 'Y', 12, .08)
    kit.box(f'Weapon {t} mantlet', (x+3.15, y, gz), (.55, 1.7, 1.45), kit.rust, .1)
    for name, s in (('port', 1), ('starboard', -1)):
        kit.cylinder(f'Weapon {t} trunnion {name}', (x+2.4, y+s*1.12, gz), .5, .26, kit.steel, 'Y', 8, .03)
    if gun is None:
        kit.cylinder(f'Weapon {t} barrel', (x+5.6, y, gz), .42, 4.9, kit.steel, 'X', 12, .03)
        kit.cylinder(f'Weapon {t} barrel jacket', (x+4.2, y, gz), .58, 1.6, kit.steel, 'X', 12, .03)
        brake = kit.box(f'Weapon {t} muzzle brake', (x+8.35, y, gz), (.8, 1.05, .9), kit.steel, 0)
        boolean(brake, kit.box('Temporary brake port', (x+8.35, y, gz), (.22, 2, .42), kit.dark, 0))
    else:  # a different weapon in the same house, built at rest along +X (hd_pirate)
        gun(kit, slot, x, y, gz)
    # Roof: loader's hatch, commander's cupola with vision blocks, gunner's sight.
    zr = zb+HOUSE_H
    kit.cylinder(f'Weapon {t} roof hatch', (x-1.4, y-.85, zr+.08), .55, .16, kit.steel, 'Z', 12, .04)
    kit.cylinder(f'Weapon {t} cupola', (x-1.1, y+.95, zr+.25), .62, .5, kit.steel, 'Z', 8, .04)
    kit.cylinder(f'Weapon {t} cupola lid', (x-1.1, y+.95, zr+.56), .5, .12, kit.steel, 'Z', 8, .03)
    vb = []
    for k in range(5):
        a = math.radians(-60+k*45)
        vb.append(((x-1.1+.6*math.cos(a), y+.95+.6*math.sin(a), zr+.32), (.22, .22, .18)))
    boxes(kit, f'Weapon {t} vision blocks', vb, glass)
    kit.box(f'Weapon {t} gunner sight', (x+.15, y-1.15, zr+.25), (.9, .55, .5), kit.steel, .05)
    kit.cylinder(f'Weapon {t} gunner sight lens', (x+.62, y-1.15, zr+.27), .17, .06, glass, 'X', 8, 0)
    # Ready-round box on the bustle roof, its feed elbow entering the house's
    # rear face.
    kit.box(f'Weapon {t} ammunition box', (x-3.2, y, zb+1.95), (1.2, 2.4, .8), kit.steel, .06)
    boxes(kit, f'Weapon {t} ammunition box straps', [((x-3.2, y+dy, zb+1.95), (1.3, .14, .88)) for dy in (-.7, .7)], kit.dark)
    kit.box(f'Weapon {t} ammunition feed', (x-2.45, y+.5, zb+2.05), (.7, .6, .5), kit.steel, .04)
    house_bolts = [W(px, py, HOUSE_H) for px in (-1.9, .0) for py in (-1.7, 1.7)]
    bolts(kit, f'Weapon {t} roof bolts', [tuple(p) for p in house_bolts], (0, 0, 1), kit.steel, .1, .07)

    train = Matrix.Translation((x, y, 0)) @ Matrix.Rotation(math.radians(slot['angle']), 4, 'Z') @ Matrix.Translation((-x, -y, 0))
    turning = set(bpy.data.objects)-fixed
    for obj in turning:
        obj.matrix_world = train @ obj.matrix_world
    ANCHOR_Z[t] = gz
    kit.empty(f'Weapon {t} anchor', (x, y, gz), slot['angle'])
    gun = [o for o in turning if any(k in o.name for k in ('barrel', 'muzzle', 'mantlet'))]
    others = [o for o in bpy.data.objects if o.type == 'MESH' and not o.name.startswith(f'Weapon {t}')]
    CLASHES[t] = overlaps(gun, others)


# --- WS 003: flush flare dispenser in the stern notch ---

def flare_launcher(kit, slot):
    x, y = slot['location']
    t = slot['id']
    z = -.5
    hazard = material('Hazard ochre', (.55, .36, .04), .2, .55)
    # Launcher body bedded on the aft bulkhead; its aft face at X -19.3.
    kit.box(f'Weapon {t} flare dispenser', (x-.25+.9/2+.05, y, z), (1.2, 4.8, 3.0), kit.steel, .06)
    face = x-.3
    # Armoured shutter frame round the tube bed, the top bar housing the
    # rolled-up shutter.
    fr = {'top': ((x-.15, y, 1.25), (1.6, 6.0, .5)), 'bottom': ((x-.15, y, -2.25), (1.6, 6.0, .5)),
          'port': ((x-.15, y+2.7, z), (1.6, .6, 3.0)), 'starboard': ((x-.15, y-2.7, z), (1.6, .6, 3.0))}
    for name, (c, d) in fr.items():
        kit.box(f'Weapon {t} shutter frame {name}', c, d, kit.steel, .05)
    kit.box(f'Weapon {t} shutter housing', (x-.55, y, 1.55), (.7, 5.4, .2), kit.steel, .04)
    # Hazard stripes on the frame's aft face: diagonal ochre bars.
    xf = x-.95
    verts, faces = [], []

    def bar(y0, y1, z0, z1, slope):
        b = len(verts)
        q = [(y0, z0), (y0+.32, z0), (y1+.32, z1), (y1, z1)]
        for xx in (xf-.04, xf+.06):
            verts.extend((xx, yy, zz) for yy, zz in q)
        faces.extend([(b, b+1, b+2, b+3), (b+4, b+7, b+6, b+5), (b, b+4, b+5, b+1),
                      (b+1, b+5, b+6, b+2), (b+2, b+6, b+7, b+3), (b+3, b+7, b+4, b)])
    for k in range(9):
        yy = y-2.75+k*.62
        bar(yy, yy+.22, 1.04, 1.46, 1)
        bar(yy, yy+.22, -2.46, -2.04, 1)
    for k in range(4):
        zz = -1.95+k*.75
        for s in (-1, 1):
            bar(y+s*2.7-.3, y+s*2.7-.3, zz, zz+.32, 0)
    kit.mesh(f'Weapon {t} hazard frame', verts, faces, hazard)
    # Tube grid: four columns by three rows facing aft; three cells capped.
    spare = {(0, 2), (3, 0), (2, 1)}
    cells = [(c, r) for r in range(3) for c in range(4)]
    bores, n, m = [], 1, 1
    for c, r in cells:
        ty, tz = y+(c-1.5)*1.1, z+(r-1)*.95
        if (c, r) in spare:
            kit.box(f'Weapon {t} spare cell cover {m}', (face-.12, ty, tz), (.24, .92, .82), kit.steel, .04)
            m += 1
            continue
        kit.cylinder(f'Weapon {t} flare tube {n}', (face-.2, ty, tz), .42, .4, kit.steel, 'X', 8, .03)
        bores.append((ty, tz))
        n += 1
    bv, bf = [], []
    for ty, tz in bores:
        b = len(bv)
        ring = [(ty+.3*math.cos(k*math.pi/4), tz+.3*math.sin(k*math.pi/4)) for k in range(8)]
        bv += [(face-.43, yy, zz) for yy, zz in ring]+[(face-.37, yy, zz) for yy, zz in ring]
        bf += [tuple(b+k for k in range(8)), tuple(b+8+k for k in reversed(range(8)))]
        bf += [(b+k, b+8+k, b+8+(k+1) % 8, b+(k+1) % 8) for k in range(8)]
    kit.mesh(f'Weapon {t} flare tube bores', bv, bf, kit.dark)
    ANCHOR_Z[t] = z
    kit.empty(f'Weapon {t} anchor', (x, y, z), slot['angle'])


def build(kit, guns=None):
    """guns: optional {slot id: gun builder} replacing a slot's weapon on its mount
    (the Hound (P) state passes hd_pirate.GUNS); None builds the clean ship."""
    for slot in SLOTS:
        fn = {'HARDPOINT': hardpoint, 'TURRET': turret, 'HIDDEN': flare_launcher}[slot['mount']]
        gun = (guns or {}).get(slot['id']) if slot['mount'] == 'TURRET' else None
        fn(kit, slot) if gun is None else fn(kit, slot, gun)
    for sid, hits in CLASHES.items():
        print(f'CHECK {sid} gun overlap: {hits if hits else "clear"}')
    for slot in SLOTS:
        a = bpy.data.objects[f"Weapon {slot['id']} anchor"]
        print(f"SLOT {slot['id']} anchor at ({a.location.x:.2f}, {a.location.y:.2f}, {a.location.z:.2f}) "
              f"rotZ {math.degrees(a.rotation_euler.z):.1f}")
