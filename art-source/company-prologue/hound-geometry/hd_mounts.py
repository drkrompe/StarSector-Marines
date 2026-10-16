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

Pass 2: WS 001 is a naval medium gun lying in the fork's trough (the
sprite's light-grey body, cols 35-37 rows 13-30): a breech on a recoil
slide in a cradle on the trough rails, recoil cylinders, a jacketed barrel
and a ported muzzle brake level with the tine points, fed by a belt chute
out of the shielded hold. WS 002 is a small naval turret on a thick bolted
barbette (the sprite's ring, about ten pixels across): turret house with a
sloped glacis and cheek armour, a mantlet on trunnions, an armour skirt
over the traverse gap, a cupola with vision blocks, a gunner's sight and an
external feed. WS 003 is a flush flare dispenser in the stern notch: an
armoured shutter frame in hazard stripes round a grid of aft-facing tubes,
some cells capped as spares.
"""
import math
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


# --- WS 001: fixed medium gun in the fork ---

def hardpoint(kit, slot):
    x, y = slot['location']
    z = .4
    t = slot['id']
    light = material('Worn light gunmetal', (.36, .37, .38), .7, .45)
    floor = -1.4+.44  # top of the fork's trough cradle rails
    # Cradle: the sled riding the trough rails, with cheeks the breech slides in.
    kit.box(f'Weapon {t} hardpoint housing', (x-8.8, y, floor+.45), (8.4, 3.3, .9), kit.steel, .08)
    boxes(kit, f'Weapon {t} cradle cheeks', [((x-8.0, y+s*1.5, floor+1.55), (5.6, .3, 1.5)) for s in (-1, 1)], kit.steel, .04)
    # Recoil slide rails on the cradle, the breech block riding them.
    boxes(kit, f'Weapon {t} recoil slide', [((x-8.6, y+s*.85, floor+1.0), (7.4, .35, .2)) for s in (-1, 1)], light, .02)
    kit.box(f'Weapon {t} breech block', (x-8.2, y, z+.35), (3.8, 2.6, 2.3), kit.rust, .12)
    kit.box(f'Weapon {t} breech ring', (x-6.1, y, z+.2), (.6, 2.2, 2.1), light, .06)
    kit.box(f'Weapon {t} breech rammer', (x-9.7, y, z+1.75), (2.4, .9, .7), light, .06)
    # Armoured mantlet block closing the front of the cradle between the
    # tine roots (the sprite's dark block at rows 12-13), the barrel collar
    # ahead of it, recoil cylinders above and below.
    kit.box(f'Weapon {t} gun mantlet', (x-4.0, y, z+.1), (2.8, 4.5, 2.2), kit.steel, .14)
    bolts(kit, f'Weapon {t} gun mantlet bolts', [(x-4.0+dx, y+dy, z+1.2) for dx in (-.9, .9) for dy in (-1.8, 1.8)],
          (0, 0, 1), kit.steel, .11, .08)
    kit.cylinder(f'Weapon {t} recoil sleeve', (x-2.25, y, z), .95, .9, kit.steel, 'X', 12, .06)
    for name, cx, ln, dz in (('upper', x-4.2, 4.6, 1.25), ('lower', x-3.0, 3.0, -1.15)):
        kit.cylinder(f'Weapon {t} recoil cylinder {name}', (cx, y, z+dz), .34, ln, light, 'X', 8, .03)
    # Thick barrel with a jacket and a ported brake; muzzle at the tine points.
    kit.cylinder(f'Weapon {t} barrel', (x+.6, y, z), .58, 8.4, light, 'X', 12, .03)
    kit.cylinder(f'Weapon {t} barrel jacket', (x-1.6, y, z), .8, 3.6, kit.steel, 'X', 12, .04)
    for k, bx in enumerate((x-3.35, x+.15), 1):
        kit.cylinder(f'Weapon {t} jacket band {k}', (bx, y, z), .9, .3, kit.steel, 'X', 12, .02)
    brake = kit.box(f'Weapon {t} muzzle brake', (x+4.85, y, z), (1.1, 1.5, 1.25), kit.steel, 0)
    for px in (x+4.65, x+5.05):
        boolean(brake, kit.box('Temporary brake port', (px, y, z), (.2, 3, .7), kit.dark, 0))
    kit.cylinder(f'Weapon {t} muzzle bore', (x+5.41, y, z), .32, .06, kit.dark, 'X', 12, 0)
    # Belt chute out of the shielded hold into the top of the breech.
    house = bpy.data.objects.get('Hull shielded hold deckhouse')
    xa = 18.4
    if house is not None:
        bpy.context.view_layer.update()
        ev = house.evaluated_get(bpy.context.evaluated_depsgraph_get())
        inv = ev.matrix_world.inverted()
        hit, p, _, _ = ev.ray_cast(inv @ Vector((40, y, 8.8)), (inv.to_3x3() @ Vector((-1, 0, 0))).normalized())
        if hit:
            xa = (ev.matrix_world @ p).x
    # Leaves the deckhouse high so it passes over the hull's coolant loop.
    a, b = Vector((xa-.9, y, 8.8)), Vector((x-8.6, y, z+1.6))
    # An elbow: level over the coolant loop at the deckhouse face, then down.
    m = Vector((20.4, y, 8.1))

    def run(name, a, b):
        d = (b-a).normalized()
        n = Vector((d.z, 0, -d.x))  # in the XZ plane, normal to the run
        sec = lambda c: [c+Vector((0, -.6, 0))-n*.45, c+Vector((0, .6, 0))-n*.45, c+Vector((0, .6, 0))+n*.45, c+Vector((0, -.6, 0))+n*.45]
        loft(kit, name, sec(a), sec(b), kit.steel, .04)
        return d, sec
    run(f'Weapon {t} feed chute', a, m+(m-a).normalized()*.3)
    d, sec = run(f'Weapon {t} feed chute drop', m, b)
    kit.box(f'Weapon {t} feed chute elbow', m, (1.1, 1.3, 1.1), light, .06)
    for k, f in enumerate((.3, .65), 1):
        c = m.lerp(b, f)
        loft(kit, f'Weapon {t} feed chute band {k}', [v-d*.15 for v in sec(c)], [v+d*.15 for v in sec(c)], light, 0)
    ANCHOR_Z[t] = z
    kit.empty(f'Weapon {t} anchor', (x, y, z), slot['angle'])
    gun = [o for o in bpy.data.objects if o.type == 'MESH' and o.name.startswith(f'Weapon {t}') and 'feed chute' not in o.name]
    fork = [o for o in bpy.data.objects if o.type == 'MESH' and o.name.startswith('Fork') and
            ('tine' in o.name or 'cross bar' in o.name or 'gusset' in o.name or 'sensor' in o.name)]
    CLASHES[t] = overlaps(gun, fork)


# --- WS 002: small naval turret on an armoured barbette ---

def turret(kit, slot):
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
    kit.cylinder(f'Weapon {t} barrel', (x+5.6, y, gz), .42, 4.9, kit.steel, 'X', 12, .03)
    kit.cylinder(f'Weapon {t} barrel jacket', (x+4.2, y, gz), .58, 1.6, kit.steel, 'X', 12, .03)
    brake = kit.box(f'Weapon {t} muzzle brake', (x+8.35, y, gz), (.8, 1.05, .9), kit.steel, 0)
    boolean(brake, kit.box('Temporary brake port', (x+8.35, y, gz), (.22, 2, .42), kit.dark, 0))
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


def build(kit):
    for slot in SLOTS:
        {'HARDPOINT': hardpoint, 'TURRET': turret, 'HIDDEN': flare_launcher}[slot['mount']](kit, slot)
    for sid, hits in CLASHES.items():
        print(f'CHECK {sid} gun overlap: {hits if hits else "clear"}')
    for slot in SLOTS:
        a = bpy.data.objects[f"Weapon {slot['id']} anchor"]
        print(f"SLOT {slot['id']} anchor at ({a.location.x:.2f}, {a.location.y:.2f}, {a.location.z:.2f}) "
              f"rotZ {math.degrees(a.rotation_euler.z):.1f}")
