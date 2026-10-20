"""Kite (P) weapon slots, exactly as kite.ship declares them, fitted as the
vanilla kite_pirates_Raider variant arms them.

WS 001  HARDPOINT  SMALL MISSILE    at (6, 22),  angle 0, arc 6    Hammer torpedo
WS 002  HARDPOINT  SMALL MISSILE    at (6, -22), angle 0, arc 6    Swarmer SRM
WS 003  TURRET     SMALL BALLISTIC  at (-7, 0),  angle 0, arc 200  light dual machine gun

Every weapon is the shared turret kit's (../turret-kit), on its close-view detail
rule.

The hardpoints are the kit's fixed mounts (tk.build_hardpoint: 'hammer', the
low-tech twin torpedo cradle, two troughs with a loaded Hammer on 'missile 1-2';
'swarmer', the four-tube SRM pack, a round on 'missile 1-4'), each on its own
'Weapon <id> mount' empty and sunk in a bay cut out of the hull: the kit's `bay`
pocket (open to the deck) cut from the wing deck, the sponson's inner wall and
anything standing in it, run on forward over the muzzles. They sit in the slot
between each missile sponson and the fuselage (the sprite's gap at Y 20..23)
and fire forward through it, at K hull units per sprite pixel: the sprites are
14 px across and the gap is not, so the mounts are scaled to fill it, pushed
outboard into the sponson so the bays stop short of the fuselage flank. The
slot itself keeps its 'Weapon <id> anchor' at the .ship location on the deck.

Muzzle anchors 'Weapon WS 00x muzzle anchor k' are the kit's, one per tube at
its mouth, local +X along the line of fire; they ride the slot's anchor rather
than the mount, so effects hung on them are not part of the mount.

The turret stands on the aft hump inside the sprite's dark ring (radius
about 5): a barbette and the shared turret kit's light machine gun, twinned
here (the kit has no Light Dual Machine Gun, and its lightmg builder ignores
barrels=): the gun group is duplicated either side of the mount's centre at
lightdualmg.wpn's turretOffsets (y -2/+2 sprite px), the two receivers
bridged by a welded saddle and top strap, on a raised pedestal so both
barrels clear the frame. The kit's gimbal (turntable, yoke, trunnions, recoil
sleeves) is fitted after the twinning, so the yoke spans both guns. Only the
parts above the traverse gap train, on the empty 'Weapon WS 003 pivot', which
rests at the slot angle.
"""
import math
import sys
from pathlib import Path
import bmesh
import bpy
from mathutils import Vector
from mathutils.bvhtree import BVHTree
from kp_kit import blocks, parent_to

sys.path.insert(0, str(Path(__file__).resolve().parent.parent/'turret-kit'))
import turret_kit as tk  # noqa: E402
import turret_gimbal  # noqa: E402
import check_hardpoints  # noqa: E402

SLOTS = [
    {'id': 'WS 001', 'mount': 'HARDPOINT', 'size': 'SMALL', 'type': 'MISSILE', 'location': [6, 22], 'angle': 0,
     'arc': 6, 'weapon': 'hammer'},
    {'id': 'WS 002', 'mount': 'HARDPOINT', 'size': 'SMALL', 'type': 'MISSILE', 'location': [6, -22], 'angle': 0,
     'arc': 6, 'weapon': 'swarmer'},
    {'id': 'WS 003', 'mount': 'TURRET', 'size': 'SMALL', 'type': 'BALLISTIC', 'location': [-7, 0], 'angle': 0,
     'arc': 200, 'weapon': 'lightdualmg'},
]
RING = 4.2            # turret ring radius, units (sprite ring about 5 with its coaming)
LIFT = 1.2            # barbette drum above the hump deck
MPU = .80             # the hull's metres_per_unit (build_kite.METRES_PER_UNIT): the kit sizes its fittings from it
TWIN = 2.0            # lightdualmg.wpn turretOffsets y, sprite px either side of the mount's centre
GUN_RAISE = .8        # the twin cradle rides this many sprite px higher than the single gun
CLASH_ANGLES = (0, 100, -100)
BARBETTE_FOOT = 1.0   # units the barbette runs down into the hump deck
WING_DECK = .8        # kp_hull.WING_DECK: the deck in the slot
# Per slot: k (hull units per sprite px), the mount's offset outboard of the slot
# (into the sponson, so the bay stops short of the fuselage flank at |Y| 19.5),
# and how far the mount's floor sinks below the wing deck.
HARDPOINT_FIT = {'WS 001': {'k': .4, 'outboard': .6, 'sink': .6},
                 'WS 002': {'k': .4, 'outboard': .9, 'sink': .6}}
BAY_MARGIN = .3       # the bay runs on this far past the muzzles, so a nose never meets the sponson prong


def _empty(name, loc, angle=0.0, size=1.0, parent=None):
    e = bpy.data.objects.new(name, None)
    e.empty_display_type, e.empty_display_size = 'ARROWS', size
    bpy.context.collection.objects.link(e)
    if parent is not None:
        e.parent = parent
    e.location = loc
    e.rotation_euler = (0, 0, math.radians(angle))
    return e


def _cut_bay(sid, mount, box, keep):
    """Sink a hardpoint: cut a box (x0, x1 along the fire line, y0..y1 across it, the
    floor below the axis; open to the deck) out of every hull part it reaches, so the
    mount sits in the hull rather than on it (after lasher-geometry/ls_mounts._cut_bay).
    Only the base mesh is cut; the part's own modifiers (its chamfer) stay live, so
    the bay's rim takes the part's own edge treatment. Returns the cut parts."""
    x0, x1, y0, y1, floor = box
    bpy.ops.mesh.primitive_cube_add(size=1)
    cutter = bpy.context.object
    cutter.name = f'Temporary bay cutter {sid}'
    cutter.parent = mount
    cutter.scale = (x1-x0, y1-y0, 30)
    cutter.location = ((x0+x1)/2, (y0+y1)/2, floor+15)
    bpy.context.view_layer.update()
    corners = [cutter.matrix_world @ Vector(c) for c in cutter.bound_box]
    lo = Vector(map(min, *corners))
    hi = Vector(map(max, *corners))
    cut = []
    for ob in list(bpy.context.scene.objects):
        if ob.type != 'MESH' or ob == cutter or ob in keep or ob.name.startswith(('Weapon ', 'Temporary ')):
            continue
        bb = [ob.matrix_world @ Vector(c) for c in ob.bound_box]
        if any(max(c[i] for c in bb) < lo[i] or min(c[i] for c in bb) > hi[i] for i in range(3)):
            continue
        live = [(m, m.show_viewport) for m in ob.modifiers]
        for m, _ in live:
            m.show_viewport = False
        mod = ob.modifiers.new('bay', 'BOOLEAN')
        mod.operation, mod.solver, mod.object = 'DIFFERENCE', 'EXACT', cutter
        bpy.context.view_layer.update()
        dg = bpy.context.evaluated_depsgraph_get()
        me = bpy.data.meshes.new_from_object(ob.evaluated_get(dg))
        ob.modifiers.remove(mod)
        for m, v in live:
            m.show_viewport = v
        if len(me.polygons) == len(ob.data.polygons) and len(me.vertices) == len(ob.data.vertices):
            bpy.data.meshes.remove(me)         # the box only grazed it
            continue
        bm = bmesh.new()                       # merge the boolean's near-coplanar slivers back into faces
        bm.from_mesh(me)
        bmesh.ops.dissolve_limit(bm, angle_limit=math.radians(1.5), verts=bm.verts, edges=bm.edges)
        bm.to_mesh(me)
        bm.free()
        own = next((m for m in me.materials if m is not None), None)
        for i, m in enumerate(me.materials):   # the cut faces take the part's own finish
            if m is None:
                me.materials[i] = own
        old = ob.data
        ob.data = me
        me.name = old.name
        bpy.data.meshes.remove(old)
        if not me.polygons:                    # a part wholly inside the bay
            bpy.data.objects.remove(ob, do_unlink=True)
            continue
        cut.append(ob.name)
    cm = cutter.data
    bpy.data.objects.remove(cutter, do_unlink=True)
    bpy.data.meshes.remove(cm)
    return cut


def _hardpoint(kit, slot):
    """The kit's fixed mount for the slot's weapon on 'Weapon <id> mount', sunk in its bay."""
    sid, (x, y) = slot['id'], slot['location']
    fit = HARDPOINT_FIT[sid]
    k = fit['k']
    s = 1 if y > 0 else -1
    spec = tk.HARDPOINT_WEAPONS[slot['weapon']]
    bx0, bx1, bhw, bfloor = (v*k for v in spec['bay'])
    # The mount's axis: its floor (the bay's) sinks fit['sink'] below the deck... measured
    # from the lowest part the builder makes, which the bay floor sits just under.
    zaxis = WING_DECK-fit['sink']-bfloor-.1*k
    my = y+s*fit['outboard']
    mount = _empty(f'Weapon {sid} mount', (x, my, zaxis), slot['angle'], size=2)
    mats = {'armour': kit.cream, 'plate': kit.panel, 'steel': kit.steel, 'dark': kit.dark}
    hp = tk.build_hardpoint(slot['weapon'], f'Weapon {sid}', mount, mats, MPU, k=k)
    reach = max(m.location.x for m in hp['muzzles'])+BAY_MARGIN
    box = (bx0, max(bx1, reach), -bhw, bhw, bfloor)
    cut = _cut_bay(sid, mount, box, set(hp['parts']))
    anchor = _empty(f'Weapon {sid} anchor', (x, y, WING_DECK), slot['angle'], size=2)
    anchor['slot'], anchor['arc_deg'], anchor['rest_angle_deg'] = sid, slot['arc'], slot['angle']
    # The muzzles ride the slot's anchor, not the mount: a fixed mount never moves them,
    # and what a shot hangs on a muzzle (launch flash, smoke) is then not taken for part
    # of the mount by check_hardpoints.
    bpy.context.view_layer.update()
    for m in hp['muzzles']:
        w = m.matrix_world.copy()
        m.parent = anchor
        m.matrix_world = w
    slot['muzzles'] = [m.name for m in hp['muzzles']]
    slot['built_by'] = 'turret-kit/turret_kit.py build_hardpoint'
    slot['k'] = k
    slot['anchor'] = anchor.name
    slot['mount_object'] = mount.name
    slot['mount_location'] = [round(v, 3) for v in mount.location]
    slot['bay'] = {'x': [round(x+box[0], 2), round(x+box[1], 2)], 'y': [round(my-bhw, 2), round(my+bhw, 2)],
                   'floor_z': round(zaxis+bfloor, 2), 'cut_from': sorted(cut)}
    slot['moving'] = [e.name for e in hp['moving']]
    bpy.context.view_layer.update()
    slot['muzzle_locations'] = [[round(v, 3) for v in m.matrix_world.translation] for m in hp['muzzles']]
    return hp


def _twin(kit, t, prefix, k, mats):
    """Make the kit's single light MG a twin: shift its gun group to one side,
    duplicate it on the other at lightdualmg's offsets, bridge the two."""
    elev = t['elevation']
    off = TWIN*k
    raise_ = GUN_RAISE*k
    elev.location.z += raise_
    children = [c for c in elev.children]
    anchor = next(c for c in children if c.name.endswith('muzzle anchor 1'))
    for c in children:
        c.location.y -= off
    for c in children:
        if c is anchor:
            a2 = c.copy()
            a2.name = f'{prefix} muzzle anchor 2'
            bpy.context.collection.objects.link(a2)
            a2.parent = elev
            a2.location.y += 2*off
            continue
        dup = c.copy()
        if c.type == 'MESH':
            dup.name = c.name.replace(f'{prefix} gun', f'{prefix} gun 2')
        else:
            dup.name = c.name.replace('recoil 1', 'recoil 2')
        bpy.context.collection.objects.link(dup)
        dup.parent = elev
        dup.location.y += 2*off
        for g in c.children:
            gd = g.copy()
            gd.name = g.name.replace('recoil 1', 'recoil 2')
            bpy.context.collection.objects.link(gd)
            gd.parent = dup
    bpy.context.view_layer.update()
    # Saddle under both pintles and a strap over both receivers, welded: in
    # elevation-empty space (its origin is the trunnion: x 1.0k, z za).
    w = elev.matrix_world

    def at(lx, ly, lz):
        return w @ Vector((lx, ly, lz))
    parts = []
    c = at(-2.0*k, 0, -1.65*k)
    parts.append(kit.box(f'{prefix} twin saddle', c, (2.4*k, 2*off+1.8*k, .5*k), mats['steel'], .06))
    c = at(-2.4*k, 0, 1.0*k)
    parts.append(kit.box(f'{prefix} twin top strap', c, (1.0*k, 2*off+2.4*k, .2*k), mats['steel'], .04))
    c = at(.6*k, 0, -.15*k)
    parts.append(kit.box(f'{prefix} twin spacer', c, (2.6*k, 2*off-2.0*k, 1.0*k), mats['dark'], .03))
    welds = []
    for sy in (1, -1):
        p = at(-2.4*k, sy*(off+1.3*k), .95*k)
        welds += [(p.x-.4*k, p.x+.4*k, p.y-.1, p.y+.1, p.z-.12, p.z+.05)]
    parts.append(blocks(kit, f'{prefix} twin welds', welds, mats['steel'], 0))
    parent_to(parts, elev)
    # Pedestal on the house deck under the raised saddle (trains, does not elevate).
    pivot = elev.parent
    pw = pivot.matrix_world
    z_deck = tk.GAP*RING*.8+.6*k
    z_top = (elev.location.z-1.9*k)
    ped = kit.cylinder(f'{prefix} twin pedestal', pw @ Vector((-2.0*k, 0, (z_deck+z_top)/2)), .75*k,
                       z_top-z_deck, mats['steel'], 'Z', 20, .03)   # 20: the segment rule at its radius
    parent_to([ped], pivot)


def _gimbal(t, prefix, pivot, ring, hull_mats):
    """The kit's gimbal (turret_gimbal.fit, as tk.build fits it) on the twinned mount:
    the trunnion is the raised elevation empty, the barrels both guns' 'recoil k'."""
    bpy.context.view_layer.update()
    elev = t['elevation']
    parts = [o for o in pivot.children_recursive if o.type == 'MESH']
    moving = sorted((o for o in elev.children_recursive if o.type == 'EMPTY' and ' recoil ' in o.name),
                    key=lambda o: o.name)
    f = tk.Fit(MPU, 'near')
    tk._CAP[0] = tk.CHAMFER_M*f.u
    mats = tk._sprite_mats(t['spec'], hull_mats)       # as tk.build dresses the gun (reused by name)
    # The yoke stands outside the widest part of either gun (the gun shields ride ahead of
    # the trunnions, where fit() does not measure the body): a temporary bar across the
    # trunnion at that width makes fit() put its cheeks clear of both shields.
    inv = pivot.matrix_world.inverted()
    half = max(abs((inv @ (o.matrix_world @ v.co)).y) for o in elev.children_recursive if o.type == 'MESH'
               for v in o.data.vertices)
    me = bpy.data.meshes.new('Temporary yoke gauge')
    bar = bpy.data.objects.new('Temporary yoke gauge', me)
    bpy.context.collection.objects.link(bar)
    bm = bmesh.new()
    bmesh.ops.create_cube(bm, size=1.0)
    bm.to_mesh(me)
    bm.free()
    bar.parent = elev
    bar.scale = (.02, 2*half, .02)
    bpy.context.view_layer.update()
    added = turret_gimbal.fit(tk, prefix, pivot, elev, t['spec'], f, ring, tuple(elev.location), parts+[bar], moving,
                              mats)
    bpy.data.objects.remove(bar, do_unlink=True)
    bpy.data.meshes.remove(me)
    t['parts'] = list(t['parts'])+added
    return added


def _turret(kit, slot):
    sid, (x, y) = slot['id'], slot['location']
    hit = kit.surface_any(x, y)
    seat = hit[0].z if hit else 7.5
    mats = {'armour': kit.cream, 'plate': kit.panel, 'steel': kit.steel, 'dark': kit.dark}
    # The hump deck is flat under the whole flange, so a short foot meets it: no bastion
    # plinth (the kit builds one for a foot deeper than half the ring).
    tk.barbette(f'Weapon {sid} barbette', RING, LIFT, mats, MPU, location=(x, y, seat), foot=BARBETTE_FOOT)
    pivot = bpy.data.objects.new(f'Weapon {sid} pivot', None)
    pivot.empty_display_type = 'ARROWS'
    pivot.location = (x, y, seat + LIFT)
    pivot.rotation_euler = (0, 0, math.radians(slot['angle']))
    pivot['slot'], pivot['arc_deg'], pivot['rest_angle_deg'] = sid, slot['arc'], slot['angle']
    bpy.context.collection.objects.link(pivot)
    ring = RING*.8
    # The gimbal waits for the twinning, so its yoke and sleeves are fitted to both guns.
    t = tk.build('lightmg', f'Weapon {sid}', pivot, ring, mats, MPU, gimbal=False)
    k = ring/tk.NOMINAL_RING['SMALL']
    _twin(kit, t, f'Weapon {sid}', k, mats)
    _gimbal(t, f'Weapon {sid}', pivot, ring, mats)
    slot['muzzles'] = sorted(a.name for a in pivot.children_recursive
                             if a.type == 'EMPTY' and ' muzzle anchor ' in a.name)
    slot['pivot'] = pivot.name
    slot['seat_z'] = round(seat, 2)
    _clash_check(slot, pivot)


def _tree(ob):
    dg = bpy.context.evaluated_depsgraph_get()
    ev = ob.evaluated_get(dg)
    me = ev.to_mesh()
    vs = [ev.matrix_world @ v.co for v in me.vertices]
    tree = BVHTree.FromPolygons(vs, [p.vertices[:] for p in me.polygons])
    ev.to_mesh_clear()
    return tree


def _clash_check(slot, pivot):
    """BVH overlap of every trained part against everything that does not
    train (the barbette reported apart: the roller rides on its lip), at the
    rest angle and at +-100 degrees, the edges of the 200-degree arc."""
    trained = [o for o in pivot.children_recursive if o.type == 'MESH']
    tset = set(trained)
    fixed = [o for o in bpy.data.objects if o.type == 'MESH' and o not in tset]
    rest = pivot.rotation_euler.z
    fixed_trees = [(o.name, _tree(o)) for o in fixed]
    for a in CLASH_ANGLES:
        pivot.rotation_euler.z = rest+math.radians(a)
        bpy.context.view_layer.update()
        hits = []
        for o in trained:
            to = _tree(o)
            for n, tf in fixed_trees:
                if to.overlap(tf):
                    hits.append((o.name, n))
        bar = [h for h in hits if 'barbette' in h[1]]
        hull = [h for h in hits if 'barbette' not in h[1]]
        print(f'CLASH {slot["id"]} at {a:+d} deg: {len(hull)} against hull/other, {len(bar)} against barbette')
        for h in hull+bar:
            print('   ', h[0], 'x', h[1])
    pivot.rotation_euler.z = rest
    bpy.context.view_layer.update()


def build(kit):
    for slot in SLOTS:
        if slot['mount'] == 'HARDPOINT':
            _hardpoint(kit, slot)
        else:
            _turret(kit, slot)
    # A fixed mount must not clip its hull (check_hardpoints, as the Lasher's build).
    clips = {m: h['clips'] for m, h in check_hardpoints.clashes().items() if h['clips']}
    for m, h in clips.items():
        print('HARDPOINT CLIP', m, h)
    if clips:
        raise SystemExit(f'Kite (P): hardpoints clip the hull: {sorted(clips)}')
    print('HARDPOINTS no clips')
