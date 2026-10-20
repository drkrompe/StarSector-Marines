"""The nine weapon slots of lasher.ship: three turrets, four hardpoints, two
hidden system mounts.

Turrets (WS 005, WS 006 on the wing body either side of the pinched spine,
WS 007 on the bow ring) come from the shared turret kit (../turret-kit),
armed as lasher_Standard.variant: a light machine gun each. A kit barbette
is seated on the highest hull under its ring with its foot run down to the
lowest, and the trainable kit turret is parented to `Turret <i> pivot`, an
empty at the drum's top, so a shot trains a turret by turning its pivot
about hull +Z and elevates it by turning `Turret <i> elevation` about its
local Y (negative lifts). Each barbette is lifted until the turret trains
clear of everything round it across its whole arc (SWEEP angles); the waist
pair stand taller than the bow mount because their arcs cross the spine. Each rests at its slot
angle (WS 005 60 deg to port, WS 006 60 deg to starboard, WS 007 ahead).
Turret i counts the turret slots in .ship order: 1 = WS 005, 2 = WS 006,
3 = WS 007; the slot id is a custom property on the pivot.

The hardpoints are fixed: the ballistic pair WS 002 / WS 003 sit in the gun
pods' forward gaps, the missile pair WS 001 / WS 004 in the missile pods'.
WS 008 / WS 009 are HIDDEN SYSTEM slots (the ammo-feed system) and carry
only an anchor. Every slot has `Weapon <id> anchor` (+X along the slot
angle); every gun has a muzzle empty, +X along the line of fire: `Anchor
turret <i> barrel 1 muzzle` on the turrets (riding the turret kit's own
`Turret <i> muzzle anchor 1`) and `Anchor weapon <id> muzzle` on the
hardpoints, the names the shared VFX kit (../second-wind-vfx) looks for. The
hardpoint guns are this package's own placeholders sized by slot: the kit has
no light assault gun or heatseeker rack yet.
"""
import math
import sys
from pathlib import Path
import bpy
from mathutils import Vector

sys.path.insert(0, str(Path(__file__).resolve().parent.parent/'turret-kit'))
import turret_kit as tk  # noqa: E402

SLOTS = [
    {'id': 'WS 001', 'location': (11, 27), 'angle': 0, 'arc': 5, 'mount': 'HARDPOINT', 'size': 'SMALL', 'type': 'MISSILE'},
    {'id': 'WS 002', 'location': (24, 16), 'angle': 0, 'arc': 5, 'mount': 'HARDPOINT', 'size': 'SMALL', 'type': 'BALLISTIC'},
    {'id': 'WS 003', 'location': (24, -15), 'angle': 0, 'arc': 5, 'mount': 'HARDPOINT', 'size': 'SMALL', 'type': 'BALLISTIC'},
    {'id': 'WS 004', 'location': (11, -27), 'angle': 0, 'arc': 5, 'mount': 'HARDPOINT', 'size': 'SMALL', 'type': 'MISSILE'},
    {'id': 'WS 005', 'location': (4, 10), 'angle': 60, 'arc': 270, 'mount': 'TURRET', 'size': 'SMALL', 'type': 'BALLISTIC'},
    {'id': 'WS 006', 'location': (4, -10), 'angle': -60, 'arc': 270, 'mount': 'TURRET', 'size': 'SMALL', 'type': 'BALLISTIC'},
    {'id': 'WS 007', 'location': (37, 0), 'angle': 0, 'arc': 270, 'mount': 'TURRET', 'size': 'SMALL', 'type': 'BALLISTIC'},
    {'id': 'WS 008', 'location': (-15, 29), 'angle': 160, 'arc': 60, 'mount': 'HIDDEN', 'size': 'SMALL', 'type': 'SYSTEM'},
    {'id': 'WS 009', 'location': (-15, -29), 'angle': 200, 'arc': 60, 'mount': 'HIDDEN', 'size': 'SMALL', 'type': 'SYSTEM'},
]
# The turrets are the shared turret kit's (../turret-kit, owner 2026-10-20: "not using
# approved kit turrets"), armed as lasher_Standard.variant, the stock fit: a light
# machine gun on each of WS 005-007. The hardpoints keep this package's own guns (the
# kit has no light assault gun or Annihilator-less heatseeker rack yet).
LOADOUT = {'WS 005': 'lightmg', 'WS 006': 'lightmg', 'WS 007': 'lightmg'}
RING = 4.6        # the sprite's turret ring round each small mount (the old barbette)
LIFT0 = 1.0       # barbette drum above its seat before the arc sweep asks for more
LIFT_STEP = .4
SWEEP = 24        # trained angles tested across each turret's arc
ANCHOR_Z = {}   # slot id -> Z of its anchor, for geometry.json
TURRETS = []    # records for geometry.json
MUZZLES = {}    # slot id -> list of muzzle anchor names


def mats(kit):
    return {'armour': kit.orange, 'plate': kit.steel, 'steel': kit.steel, 'dark': kit.dark}


def _tree(ob, dg):
    from mathutils.bvhtree import BVHTree
    ev = ob.evaluated_get(dg)
    me = ev.to_mesh()
    verts = [ob.matrix_world @ v.co for v in me.vertices]
    polys = [tuple(p.vertices) for p in me.polygons]
    ev.to_mesh_clear()
    return BVHTree.FromPolygons(verts, polys) if polys else None


def _clashes(parts, pivot, angle, others):
    """Names of the other solids the trained turret meets with its pivot at `angle`."""
    pivot.rotation_euler = (0, 0, math.radians(angle))
    bpy.context.view_layer.update()
    dg = bpy.context.evaluated_depsgraph_get()
    hits = set()
    for ob in parts:
        if ob.type != 'MESH':
            continue
        t = _tree(ob, dg)
        if t is None:
            continue
        for name, o in others.items():
            if t.overlap(o):
                hits.add(name)
    return hits


def turret(kit, i, slot):
    """A kit turret on a kit barbette, seated on the highest hull under its ring, its
    foot run down to the lowest, and lifted until its whole arc trains clear."""
    x, y = slot['location']
    sid = slot['id']
    r = RING
    under = [kit.surface(x+f*r*math.cos(k*math.pi/8), y+f*r*math.sin(k*math.pi/8))
             for f in (0, .6, .95) for k in range(16 if f else 1)]
    zs = [h[0].z for h in under if h]
    seat, low = max(zs), min(zs)
    foot = max(1.2, seat-low+.5)
    bpy.context.view_layer.update()
    dg = bpy.context.evaluated_depsgraph_get()
    mine = f'Turret {i} '
    reach = 4*r
    others = {}
    for ob in bpy.context.scene.objects:   # hull, detail, pods, hardpoints, earlier turrets
        if ob.type != 'MESH' or ob.name.startswith(mine):
            continue
        lo = ob.matrix_world @ Vector(ob.bound_box[0])
        hi = ob.matrix_world @ Vector(ob.bound_box[6])
        if min(lo.x, hi.x) > x+reach or max(lo.x, hi.x) < x-reach or min(lo.y, hi.y) > y+reach \
                or max(lo.y, hi.y) < y-reach:
            continue
        tr = _tree(ob, dg)
        if tr is not None:
            others[ob.name] = tr
    a0 = slot['angle']-slot['arc']/2
    sweep = [a0+slot['arc']*k/SWEEP for k in range(SWEEP+1)]
    lift = LIFT0
    for _ in range(30):
        barbette = tk.barbette(f'Turret {i} barbette', r, lift, mats(kit), kit.mpu, foot=foot,
                               location=(x, y, seat))
        top = seat+lift
        pivot = kit.empty(f'Turret {i} pivot', (x, y, top), 0, size=2.5, kind='SINGLE_ARROW')
        t = tk.build(LOADOUT[sid], f'Turret {i}', pivot, r, mats(kit), kit.mpu)
        bad = {round(a): sorted(h) for a in sweep if (h := _clashes(t['parts'], pivot, a, others))}
        if not bad:
            break
        print(f'turret {i} ({sid}): lift {lift:.2f} clashes {bad}')
        for ob in [barbette, pivot, *barbette.children_recursive, *pivot.children_recursive]:
            if ob.name in bpy.data.objects:
                bpy.data.objects.remove(ob, do_unlink=True)
        lift += LIFT_STEP
    else:
        raise RuntimeError(f'turret {i} ({sid}) still clashes across its arc: {bad}')
    pivot.rotation_euler = (0, 0, math.radians(slot['angle']))
    bpy.context.view_layer.update()
    muzzles = []
    for k, m in enumerate(t['muzzles'], 1):
        # Named for the shared VFX kit's declared-muzzle rule, riding the turret kit's anchor.
        a = bpy.data.objects.new(f'Anchor turret {i} barrel {k} muzzle', None)
        a.empty_display_size = .6
        bpy.context.collection.objects.link(a)
        a.parent = m
        muzzles.append(a.name)
    pivot['slot'] = sid
    pivot['weapon'] = LOADOUT[sid]
    pivot['rest_angle_deg'] = slot['angle']
    pivot['arc_deg'] = slot['arc']
    pivot['train_axis'] = 'hull +Z'
    elev = t['elevation']
    kit.empty(f'Weapon {sid} anchor', (x, y, top), slot['angle'], size=2)
    ANCHOR_Z[sid] = top
    MUZZLES[sid] = muzzles
    TURRETS.append({'index': i, 'slot': sid, 'weapon': LOADOUT[sid], 'built_by': 'turret-kit/turret_kit.py',
                    'pivot': pivot.name, 'location': [x, y, round(top, 2)],
                    'rest_angle_deg': slot['angle'], 'arc_deg': slot['arc'], 'ring': r,
                    'seat': round(seat, 2), 'lift': round(lift, 2), 'foot': round(foot, 2),
                    'arc_sweep': {'angles': len(sweep), 'clashes': 0},
                    'elevation': elev.name, 'elevates': 'rotate about its local Y; negative lifts the muzzles',
                    'elevation_limits_deg': [elev.get('elevation_min_deg'), elev.get('elevation_max_deg')],
                    'fixed': [barbette.name]+[c.name for c in barbette.children_recursive],
                    'trained': [o.name for o in t['parts']], 'recoil': [e.name for e in t['moving']],
                    'kit_muzzles': [m.name for m in t['muzzles']], 'muzzles': muzzles})


def hardpoint(kit, slot):
    x, y = slot['location']
    sid = slot['id']
    if slot['type'] == 'BALLISTIC':
        z = 1.2
        kit.box(f'Weapon {sid} breech', (x+2.5, y, z), (5, 2.0, 1.8), kit.steel, .1)
        kit.cylinder(f'Weapon {sid} barrel', (x+6.5, y, z), .4, 6.0, kit.steel, 'X', 8, 0)
        kit.cylinder(f'Weapon {sid} muzzle brake', (x+9.3, y, z), .55, .9, kit.dark, 'X', 8, 0)
        m = kit.empty(f'Anchor weapon {sid} muzzle', (x+9.8, y, z), 0, size=1)
    else:
        z = 1.4
        kit.box(f'Weapon {sid} launcher', (x+1.6, y, z), (5.6, 2.8, 2.6), kit.steel, .1)
        for k, (dy, dz) in enumerate(((.65, .6), (-.65, .6), (.65, -.6), (-.65, -.6)), 1):
            kit.cylinder(f'Weapon {sid} tube {k}', (x+4.45, y+dy, z+dz), .42, .2, kit.dark, 'X', 8, 0)
        m = kit.empty(f'Anchor weapon {sid} muzzle', (x+4.6, y, z), 0, size=1)
    kit.empty(f'Weapon {sid} anchor', (x, y, z), slot['angle'], size=2)
    ANCHOR_Z[sid] = z
    MUZZLES[sid] = [m.name]


def build(kit):
    ANCHOR_Z.clear()
    TURRETS.clear()
    MUZZLES.clear()
    i = 0
    for slot in SLOTS:
        if slot['mount'] == 'TURRET':
            i += 1
            turret(kit, i, slot)
        elif slot['mount'] == 'HARDPOINT':
            hardpoint(kit, slot)
        else:
            x, y = slot['location']
            kit.empty(f'Weapon {slot["id"]} anchor', (x, y, 0), slot['angle'], size=2)
            ANCHOR_Z[slot['id']] = 0.0


def clearance(names_a, names_b):
    """Pairs of meshes that overlap (BVH), for the trained barrels against the hull."""
    from mathutils.bvhtree import BVHTree
    dg = bpy.context.evaluated_depsgraph_get()

    def tree(name):
        ob = bpy.data.objects[name]
        ev = ob.evaluated_get(dg)
        me = ev.to_mesh()
        verts = [ob.matrix_world @ v.co for v in me.vertices]
        polys = [tuple(p.vertices) for p in me.polygons]
        ev.to_mesh_clear()
        return BVHTree.FromPolygons(verts, polys)
    hits = []
    for a in names_a:
        ta = tree(a)
        for b in names_b:
            if ta.overlap(tree(b)):
                hits.append((a, b))
    return hits
