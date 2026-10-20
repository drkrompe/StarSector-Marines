"""The eight weapon slots the Falcon (P) keeps: four turrets, four hardpoints.

falcon.ship has ten slots; falcon_p.skin removes WS 003 and WS 004 (the small
energy turrets on the drive blocks) and retypes WS 005-008 (WS 005/006
COMPOSITE, WS 007/008 MISSILE). Weapons follow falcon_p_Strike.variant, the
only (P) variant in the install:

  WS 001 / 002  (-17, +-69)  small missile hardpoints, swarmer
  WS 005 / 006  (14, +-38)   medium turrets, typhoon (angle +-60, arc 170)
  WS 007 / 008  (119, 22/-21) medium hardpoints, annihilatorpod
  WS 009 / 010  (55, +-29)   small turrets, irpulse (angle +-65, arc 180)

Turrets are the shared turret kit's (turret-kit/turret_kit.py) at near
detail: a fixed barbette seated with `surface`, and a trainable part on an
empty `Weapon <slot> pivot` at the top of the traverse gap, resting at the
slot angle. The kit has no IR pulse laser; WS 009/010 carry its small energy
mount (`pdlaser`) as the nearest family and say so in geometry.json. A clash
lifts the barbette, never moves the slot.

Hardpoints are the kit's too (tk.build_hardpoint) on a fixed `Weapon <slot>
mount`: the Swarmer's four-tube pack (sprite size, k=1) sunk in a bay cut out
of its outrigger pod (open through the pod's inboard wall where the wall would
be a skin; WS 002's fuse box sits in a closed pocket in the prong's flank),
and the Annihilator's launcher head (k=0.6, the Wolf (P)'s) between each bow
fork's jaws, in a bay notched into the jaws. Each slot has `Weapon <slot>
anchor` (+X along the slot angle, at the bore axis) and the kit's `Weapon
<slot> muzzle anchor <k>` per tube mouth, +X along the line of fire.
"""
import math
import sys
from pathlib import Path
import bpy
from mathutils import Vector
from mathutils.bvhtree import BVHTree

sys.path.insert(0, str(Path(__file__).resolve().parent.parent/'turret-kit'))
import turret_kit as tk

SLOTS = [  # falcon.ship with falcon_p.skin's changes; WS 003/004 removed by the skin
    {'id': 'WS 001', 'location': (-17, 69), 'angle': 0, 'arc': 5, 'mount': 'HARDPOINT', 'size': 'SMALL', 'type': 'MISSILE', 'weapon': 'swarmer'},
    {'id': 'WS 002', 'location': (-17, -68), 'angle': 0, 'arc': 5, 'mount': 'HARDPOINT', 'size': 'SMALL', 'type': 'MISSILE', 'weapon': 'swarmer'},
    {'id': 'WS 005', 'location': (14, 38), 'angle': 60, 'arc': 170, 'mount': 'TURRET', 'size': 'MEDIUM', 'type': 'COMPOSITE', 'weapon': 'typhoon'},
    {'id': 'WS 006', 'location': (14, -38), 'angle': -60, 'arc': 170, 'mount': 'TURRET', 'size': 'MEDIUM', 'type': 'COMPOSITE', 'weapon': 'typhoon'},
    {'id': 'WS 007', 'location': (119, 22), 'angle': 0, 'arc': 5, 'mount': 'HARDPOINT', 'size': 'MEDIUM', 'type': 'MISSILE', 'weapon': 'annihilatorpod'},
    {'id': 'WS 008', 'location': (119, -21), 'angle': 0, 'arc': 5, 'mount': 'HARDPOINT', 'size': 'MEDIUM', 'type': 'MISSILE', 'weapon': 'annihilatorpod'},
    {'id': 'WS 009', 'location': (55, 29), 'angle': 65, 'arc': 180, 'mount': 'TURRET', 'size': 'SMALL', 'type': 'ENERGY', 'weapon': 'irpulse'},
    {'id': 'WS 010', 'location': (55, -29), 'angle': -65, 'arc': 180, 'mount': 'TURRET', 'size': 'SMALL', 'type': 'ENERGY', 'weapon': 'irpulse'},
]
REMOVED = ('WS 003', 'WS 004')
# Slot types before the skin, for checking against falcon.ship.
BASE_TYPE = {'WS 005': 'ENERGY', 'WS 006': 'ENERGY', 'WS 007': 'BALLISTIC', 'WS 008': 'BALLISTIC'}
KIT_WEAPON = {'typhoon': 'typhoon', 'irpulse': 'pdlaser'}
RING = {'MEDIUM': 9.5, 'SMALL': 5.5}
PIVOT_Z = {}
ANCHOR_Z = {}
MUZZLES = {}
ARC_REPORT = {}


def _mats(kit):
    return {'armour': kit.armour, 'plate': kit.plate, 'steel': kit.steel, 'dark': kit.dark}


def _tree(ob, dg):
    ev = ob.evaluated_get(dg)
    me = ev.to_mesh()
    pts = [ev.matrix_world @ v.co for v in me.vertices]
    t = BVHTree.FromPolygons(pts, [tuple(p.vertices) for p in me.polygons])
    ev.to_mesh_clear()
    lo = Vector(map(min, *pts)) if len(pts) > 1 else pts[0]
    hi = Vector(map(max, *pts)) if len(pts) > 1 else pts[0]
    return t, lo, hi


def _clashes(parts, pivot, angle, others):
    pivot.rotation_euler = (0, 0, math.radians(angle))
    bpy.context.view_layer.update()
    dg = bpy.context.evaluated_depsgraph_get()
    hits = set()
    for p in parts:
        if p.type != 'MESH':
            continue
        t, lo, hi = _tree(p, dg)
        for name, (o, olo, ohi) in others.items():
            if all(lo[i] <= ohi[i] and olo[i] <= hi[i] for i in range(3)) and t.overlap(o):
                hits.add(name)
    return hits


def turret(kit, slot, cache):
    sid = slot['id']
    x, y = slot['location']
    r = RING[slot['size']]
    hits = [kit.surface(x+.6*r*math.cos(k*math.pi/4), y+.6*r*math.sin(k*math.pi/4), top=80) for k in range(8)]
    hits = [h for h in hits+[kit.surface(x, y, top=80)] if h]
    seat = max(h[0].z for h in hits)
    under = [kit.surface(x+f*r*math.cos(k*math.pi/8), y+f*r*math.sin(k*math.pi/8), top=80)
             for f in (.6, .9, 1.12) for k in range(16)]
    low = min([h[0].z for h in under if h] or [seat])
    foot = max(2+.35*r, seat-low+.6)
    lift = .22*r
    bpy.context.view_layer.update()
    dg = bpy.context.evaluated_depsgraph_get()
    mine = f'Weapon {sid} '
    for ob in bpy.context.scene.objects:
        if ob.type == 'MESH' and not ob.name.startswith(mine) and ob.name not in cache:
            cache[ob.name] = _tree(ob, dg)
    reach = 3.2*r
    others = {n: v for n, v in cache.items() if not n.startswith(mine)
              and v[1].x <= x+reach and v[2].x >= x-reach and v[1].y <= y+reach and v[2].y >= y-reach}
    weapon = KIT_WEAPON[slot['weapon']]
    for _ in range(40):
        barbette = tk.barbette(f'Weapon {sid} barbette', r, lift, _mats(kit), kit.mpu, foot=foot, location=(x, y, seat))
        pivot = kit.empty(f'Weapon {sid} pivot', (x, y, seat+lift), slot['angle'])
        t = tk.build(weapon, f'Weapon {sid}', pivot, r, _mats(kit), kit.mpu)
        parts, extras = t['parts'], [t['elevation']]+t['muzzles']
        bad = _clashes(parts, pivot, slot['angle'], others)
        pivot.rotation_euler = (0, 0, math.radians(slot['angle']))
        if not bad:
            break
        print(f'turret {sid}: lift {lift:.2f} clashes {bad}')
        for ob in [barbette, pivot]+list(barbette.children)+parts+extras:
            bpy.data.objects.remove(ob, do_unlink=True)
        lift += .4
    a0 = slot['angle']-slot['arc']/2
    sweep = [a0+slot['arc']*i/12 for i in range(13)]
    ARC_REPORT[sid] = {'lift': round(lift, 2),
                       'clash_angles': {round(a): sorted(h) for a in sweep if (h := _clashes(parts, pivot, a, others))}}
    pivot.rotation_euler = (0, 0, math.radians(slot['angle']))
    pivot['slot'] = sid
    pivot['rest_angle_deg'] = slot['angle']
    pivot['arc_deg'] = slot['arc']
    pivot['weapon'] = slot['weapon']
    pivot['kit_weapon'] = weapon
    bpy.context.view_layer.update()
    PIVOT_Z[sid] = ANCHOR_Z[sid] = seat+lift
    MUZZLES[sid] = [m.name for m in t['muzzles']]
    kit.empty(f'Weapon {sid} anchor', (x, y, seat+lift), slot['angle'], size=2)
    kit.hull.append(barbette)
    print(f"turret {sid}: seat {seat:.2f} lift {lift:.2f} foot {foot:.2f} parts {len(parts)} "
          f"arc clashes {ARC_REPORT[sid]['clash_angles']}")


# The kit's hardpoints (turret-kit build_hardpoint) for the two fits, at k hull units
# per weapon sprite pixel (one Falcon unit is one ship sprite pixel, so k=1 is the game's
# own size). The Swarmer packs stand on the outrigger pods' forward decks at the sprite's
# size; the Annihilator heads stand between each bow fork's jaws, a 9.5-unit gap, so they
# are drawn at the Wolf (P)'s 0.6.
HARDPOINT_KIT = {'swarmer': 'swarmer', 'annihilatorpod': 'annihilator'}
HARDPOINT_K = {'WS 001': 1.0, 'WS 002': 1.0, 'WS 007': .6, 'WS 008': .6}
# Bore axis below the deck at the slot (swarmer: the breech housing's roof stands ~1.5
# units proud, the tubes half sunk) or, between the open jaws, the axis height itself.
AXIS_BELOW_DECK = {'swarmer': .8}
AXIS_Z = {'annihilatorpod': .6}
# The Annihilator head has no `bay` in the kit: its own pocket between the jaws, from
# behind the magazine cap to past the cheeks' front, the cheeks' half-width and below
# the collar's foot, in weapon pixels (scaled by k), with this much clearance in units.
ANNIHILATOR_BAY = (-7.4, 11.4, 8.4, -9.0)
BAY_CLEAR = .4
# Hull parts each bay may cut: the pod and its plating, the fork jaws and their probes.
BAY_HOSTS = {'swarmer': ('Hull {side} outrigger pod',), 'annihilatorpod': ('Hull {side} bow fork',)}
BAY_WALL = .5     # pod wall left beside a bay that would cut the pod to a skin
POD_PLAN_Y = {'WS 001': (60.75, 79.6), 'WS 002': (-79.6, -60.75)}   # pod's y extent at the slot x
HARDPOINT_RECORDS = {}


def _cut_bay(kit, sid, mount, box, keep, hosts, top=None, dissolve=True):
    """Sink the hardpoint: cut a box (x0, x1 along the fire line, y0..y1 across it, the
    floor below the axis; open to the deck, or closed at `top` above the axis for a pocket)
    out of every hull part named by `hosts` that it reaches. Returns the cut parts. After
    lasher-geometry/ls_mounts._cut_bay. dissolve=False leaves the rest of a big lofted part's
    faces exactly as they were (its seat heights and surfacing face selections)."""
    import bmesh
    x0, x1, y0, y1, floor = box
    height = 30 if top is None else top-floor
    bpy.ops.mesh.primitive_cube_add(size=1)
    cutter = bpy.context.object
    cutter.name = f'Temporary bay cutter {sid}'
    cutter.parent = mount
    cutter.scale = (x1-x0, y1-y0, height)
    cutter.location = ((x0+x1)/2, (y0+y1)/2, floor+height/2)
    bpy.context.view_layer.update()
    corners = [cutter.matrix_world @ Vector(c) for c in cutter.bound_box]
    lo = Vector(map(min, *corners))
    hi = Vector(map(max, *corners))
    cut = []
    for ob in list(bpy.context.scene.objects):
        if ob.type != 'MESH' or ob == cutter or ob in keep or not ob.name.startswith(hosts):
            continue
        bb = [ob.matrix_world @ Vector(c) for c in ob.bound_box]
        if any(max(c[i] for c in bb) < lo[i] or min(c[i] for c in bb) > hi[i] for i in range(3)):
            continue
        for bm_ in ob.modifiers:          # its own chamfer, held to hard edges
            if bm_.type == 'BEVEL' and bm_.limit_method == 'ANGLE':
                bm_.angle_limit = max(bm_.angle_limit, math.radians(65))
        mod = ob.modifiers.new('bay', 'BOOLEAN')
        mod.operation, mod.solver, mod.object = 'DIFFERENCE', 'EXACT', cutter
        bpy.context.view_layer.update()
        dg = bpy.context.evaluated_depsgraph_get()
        me = bpy.data.meshes.new_from_object(ob.evaluated_get(dg))
        if dissolve:
            bm = bmesh.new()             # merge the boolean's near-coplanar slivers back into faces
            bm.from_mesh(me)
            bmesh.ops.dissolve_limit(bm, angle_limit=math.radians(1.5), verts=bm.verts, edges=bm.edges)
            bm.to_mesh(me)
            bm.free()
        own = next((m for m in me.materials if m is not None), None)
        for i, m in enumerate(me.materials):     # the cut faces take the part's own finish
            if m is None:
                me.materials[i] = own
        old = ob.data
        ob.modifiers.clear()
        ob.data = me
        me.name = old.name
        bpy.data.meshes.remove(old)
        if not me.polygons:            # a part wholly inside the bay
            if ob in kit.hull:
                kit.hull.remove(ob)
            bpy.data.objects.remove(ob, do_unlink=True)
            continue
        cut.append(ob.name)
    cm = cutter.data
    bpy.data.objects.remove(cutter, do_unlink=True)
    bpy.data.meshes.remove(cm)
    return cut


def hardpoint(kit, slot):
    """The turret kit's hardpoint for the slot's weapon on `Weapon <id> mount` at the
    slot, sunk in a bay cut from the hull: the Swarmer in its outrigger pod, the
    Annihilator head between its bow fork's jaws. The kit names the muzzles
    `Weapon <id> muzzle anchor <k>`, the hull's own tags."""
    sid = slot['id']
    x, y = slot['location']
    weapon = slot['weapon']
    built = HARDPOINT_KIT[weapon]
    k = HARDPOINT_K[sid]
    if weapon in AXIS_Z:
        z = AXIS_Z[weapon]
    else:
        hit = kit.surface(x, y)
        z = (hit[0].z if hit else 4.0)-AXIS_BELOW_DECK[weapon]
    mount = kit.empty(f'Weapon {sid} mount', (x, y, z), slot['angle'], size=2)
    hp = tk.build_hardpoint(built, f'Weapon {sid}', mount, _mats(kit), kit.mpu, k=k)
    side = 'port' if y > 0 else 'starboard'
    hosts = tuple(h.format(side=side) for h in BAY_HOSTS[weapon])
    if hp['bay']:
        x0, x1, hw, floor = hp['bay']
    else:
        x0, x1, hw, floor = (v*k for v in ANNIHILATOR_BAY)
        x0, x1, hw, floor = x0-BAY_CLEAR, x1+BAY_CLEAR, hw+BAY_CLEAR, floor-BAY_CLEAR
    ext = {1: hw, -1: hw}
    if sid in POD_PLAN_Y:
        # Across the bay: where the pod wall left beside the housing would be a skin, the
        # cut runs on out through it (ls_mounts' rule), so the flank shows in an open side.
        ylo, yhi = POD_PLAN_Y[sid]
        room = {1: yhi-y, -1: y-ylo}      # +local y is +world y (slot angle 0)
        ext = {sy: (hw if room[sy]-hw >= BAY_WALL else room[sy]+2.0) for sy in (1, -1)}
    cut = _cut_bay(kit, sid, mount, (x0, x1, -ext[-1], ext[1], floor), set(hp['parts']), hosts)
    pocket = None
    if sid in POD_PLAN_Y:
        # The pod abuts the prong's flank: whatever of the pack reaches inboard past the
        # pod (WS 002, a unit further in than WS 001, puts its fuse box there) sits in a
        # closed pocket in the prong's flank rather than through it.
        bpy.context.view_layer.update()
        inner = min(abs(v) for v in POD_PLAN_Y[sid])+.6
        pts = [o.matrix_world @ v.co for o in hp['parts'] if o.type == 'MESH' for v in o.data.vertices]
        pts = [mount.matrix_world.inverted() @ p for p in pts if abs(p.y) < inner]
        if pts:
            lo = Vector(map(min, *pts)) if len(pts) > 1 else pts[0]
            hi = Vector(map(max, *pts)) if len(pts) > 1 else pts[0]
            c = BAY_CLEAR
            sy = 1 if y < 0 else -1          # inboard is +local y to starboard, -local y to port
            ya, yb = (lo.y-c, ext[1]+.01) if sy == 1 else (-ext[-1]-.01, hi.y+c)
            pocket = (lo.x-c, hi.x+c, ya, yb, lo.z-c, hi.z+c)
            cut += _cut_bay(kit, sid, mount, (lo.x-c, hi.x+c, ya, yb, lo.z-c), set(hp['parts']),
                            (f'Hull {side} prong',), top=hi.z+c, dissolve=False)
    kit.empty(f'Weapon {sid} anchor', (x, y, z), slot['angle'], size=2)
    ANCHOR_Z[sid] = z
    MUZZLES[sid] = [m.name for m in hp['muzzles']]
    HARDPOINT_RECORDS[sid] = {
        'built_by': 'turret-kit/turret_kit.py build_hardpoint', 'kit_weapon': built, 'k': k,
        'mount_empty': mount.name, 'mount_location': [x, y, round(z, 2)],
        'bay': {'x': [round(x+x0, 2), round(x+x1, 2)], 'y': [round(y-ext[-1], 2), round(y+ext[1], 2)],
                'floor_z': round(z+floor, 2), 'cut_from': sorted(cut),
                'prong_pocket': [round(v, 2) for v in pocket] if pocket else None},
        'moving': [e.name for e in hp['moving']],
        'muzzle_locations': [[round(v, 2) for v in (mount.matrix_world @ m.location)] for m in hp['muzzles']]}
    print(f'hardpoint {sid}: {built} k {k} axis z {z:.2f} bay cut from {sorted(cut)}')


def build(kit):
    PIVOT_Z.clear()
    ANCHOR_Z.clear()
    MUZZLES.clear()
    ARC_REPORT.clear()
    HARDPOINT_RECORDS.clear()
    for slot in SLOTS:
        if slot['mount'] == 'HARDPOINT':
            hardpoint(kit, slot)
    cache = {}
    for slot in SLOTS:
        if slot['mount'] == 'TURRET':
            turret(kit, slot, cache)


def records():
    out = []
    for s in SLOTS:
        rec = dict({k: v for k, v in s.items() if k != 'location'},
                   location=[*s['location'], round(ANCHOR_Z[s['id']], 2)], anchor=f"Weapon {s['id']} anchor",
                   muzzle_anchors=MUZZLES[s['id']])
        if s['mount'] == 'TURRET':
            rec.update(ring_radius=RING[s['size']], pivot=f"Weapon {s['id']} pivot",
                       trains='rotate the pivot about its local Z; rest is the slot angle',
                       elevation=f"Weapon {s['id']} elevation",
                       elevates='rotate the elevation empty about its local Y; negative lifts the muzzles',
                       built_by='turret-kit/turret_kit.py', kit_weapon=KIT_WEAPON[s['weapon']],
                       arc_report=ARC_REPORT[s['id']])
            if s['weapon'] == 'irpulse':
                rec['note'] = 'the turret kit has no IR pulse laser; its small energy mount (pdlaser) stands in'
        else:
            rec.update(HARDPOINT_RECORDS[s['id']])
        out.append(rec)
    return out
