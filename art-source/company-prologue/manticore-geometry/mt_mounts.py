"""The ten weapon slots of manticore.ship: four turrets, two missile
hardpoints, four hidden system mounts.

Weapons follow the manticore_pirates_Support variant (the pirate long-range
fit), except the large WS 000, which carries a Hellbore (owner, round 1: the
kit's Mark IX read as comical on this mount), Harpoon pods on the medium
missile hardpoints WS 004 / WS 005, light assault guns on WS 001 / WS 003 and
a light needler on the bow WS 002. The shared turret kit
(../turret-kit/turret_kit.py) builds them; it has no light assault gun or
light needler, so those slots carry its nearest small guns, the light
autocannon and the needler, sized to their ring.

Every gun stands on a fixed barbette seated with `surface`; what trains is
parented to `Weapon <slot> pivot` (an empty on top of the barbette, resting at
the slot angle; turn it about its local Z). Guns and launchers hang on
`Weapon <slot> elevation` (local Y, negative lifts). The hardpoint launchers
are the kit's harpoon rack on a pivot that is never trained (arc 5).

Mount index i counts the turret and hardpoint slots in .ship order (1 WS 000,
2 WS 001, 3 WS 002, 4 WS 003, 5 WS 004, 6 WS 005). Each muzzle has the VFX
kit's declared anchor, `Anchor turret <i> barrel <k> muzzle` on a gun and
`Anchor turret <i> tube <k> muzzle` on a launcher, +X along the line of fire,
with `scale` and `kind` custom properties. WS 006-009 are HIDDEN SYSTEM slots
(the canister flak system) and carry only an anchor, `Weapon <slot> anchor`.
"""
import math
import sys
from pathlib import Path
import bpy
from mathutils import Matrix, Vector
from mathutils.bvhtree import BVHTree

sys.path.insert(0, str(Path(__file__).resolve().parent.parent/'turret-kit'))
import turret_kit as tk  # noqa: E402

SLOTS = [
    {'id': 'WS 000', 'location': (21, 0), 'angle': 0, 'arc': 90, 'mount': 'TURRET', 'size': 'LARGE', 'type': 'BALLISTIC'},
    {'id': 'WS 001', 'location': (23, 65), 'angle': 80, 'arc': 235, 'mount': 'TURRET', 'size': 'SMALL', 'type': 'BALLISTIC'},
    {'id': 'WS 002', 'location': (73, 0), 'angle': 0, 'arc': 235, 'mount': 'TURRET', 'size': 'SMALL', 'type': 'BALLISTIC'},
    {'id': 'WS 003', 'location': (23, -65), 'angle': -80, 'arc': 235, 'mount': 'TURRET', 'size': 'SMALL', 'type': 'BALLISTIC'},
    {'id': 'WS 004', 'location': (32, 43), 'angle': 0, 'arc': 5, 'mount': 'HARDPOINT', 'size': 'MEDIUM', 'type': 'MISSILE'},
    {'id': 'WS 005', 'location': (32, -43), 'angle': 0, 'arc': 5, 'mount': 'HARDPOINT', 'size': 'MEDIUM', 'type': 'MISSILE'},
    {'id': 'WS 006', 'location': (36, -20), 'angle': -45, 'arc': 5, 'mount': 'HIDDEN', 'size': 'SMALL', 'type': 'SYSTEM'},
    {'id': 'WS 007', 'location': (36, 21), 'angle': 45, 'arc': 5, 'mount': 'HIDDEN', 'size': 'SMALL', 'type': 'SYSTEM'},
    {'id': 'WS 008', 'location': (-19, 40), 'angle': 135, 'arc': 5, 'mount': 'HIDDEN', 'size': 'SMALL', 'type': 'SYSTEM'},
    {'id': 'WS 009', 'location': (-19, -40), 'angle': 225, 'arc': 5, 'mount': 'HIDDEN', 'size': 'SMALL', 'type': 'SYSTEM'},
]
# manticore_pirates_Support, with the kit's stand-ins named; WS 000 by the owner's choice.
WEAPON = {'WS 000': 'hellbore', 'WS 001': 'lightac', 'WS 002': 'needler', 'WS 003': 'lightac',
          'WS 004': 'harpoonpod', 'WS 005': 'harpoonpod'}
VARIANT_WEAPON = {'WS 000': 'mark9', 'WS 001': 'lightag', 'WS 002': 'lightneedler', 'WS 003': 'lightag',
                  'WS 004': 'harpoonpod', 'WS 005': 'harpoonpod'}
# Ring radii read off the sprite's turret rings (hull units).
RING = {'WS 000': 13.0, 'WS 001': 7.0, 'WS 002': 6.0, 'WS 003': 7.0, 'WS 004': 7.5, 'WS 005': 7.5}
LAUNCHERS = ('harpoonpod',)
SW_HOUSE = 2.5     # the VFX kit's reference: the Second Wind's small mount radius, units
GUN_FLASH = {'hellbore': .7, 'mark9': .7, 'lightac': .55, 'needler': .45}   # share of the ring a barrel's flash spans
MISSILE_BODY = 2.2  # the VFX kit's missile body length at scale 1, units (swfx_guns; as legion-cinematic)
TURRETS = []       # records for geometry.json
ANCHOR_Z = {}


def _mats(kit):
    return {'armour': kit.red, 'plate': kit.plate, 'steel': kit.steel, 'dark': kit.dark}


def _tree(ob, dg):
    ev = ob.evaluated_get(dg)
    me = ev.to_mesh()
    pts = [ev.matrix_world @ v.co for v in me.vertices]
    t = BVHTree.FromPolygons(pts, [tuple(p.vertices) for p in me.polygons])
    ev.to_mesh_clear()
    return t


def _clashes(parts, others):
    bpy.context.view_layer.update()
    dg = bpy.context.evaluated_depsgraph_get()
    hits = set()
    for p in parts:
        if p.type != 'MESH':
            continue
        t = _tree(p, dg)
        for name, o in others.items():
            if t.overlap(o):
                hits.add(name)
    return hits


def _frame(fire):
    x = fire.normalized()
    up = Vector((0, 0, 1))
    y = up.cross(x)
    if y.length < 1e-6:
        y = Vector((0, 1, 0))
    y.normalize()
    z = x.cross(y)
    return Matrix((x, y, z)).transposed()


def mount(kit, i, slot):
    sid = slot['id']
    x, y = slot['location']
    r = RING[sid]
    weapon = WEAPON[sid]
    kind = 'launcher' if weapon in LAUNCHERS else 'gun'
    hits = [kit.surface(x+.6*r*math.cos(k*math.pi/4), y+.6*r*math.sin(k*math.pi/4)) for k in range(8)]
    hits = [h for h in hits+[kit.surface(x, y)] if h]
    seat = max(h[0].z for h in hits)
    under = [kit.surface(x+f*r*math.cos(k*math.pi/8), y+f*r*math.sin(k*math.pi/8)) for f in (.6, .9, 1.1)
             for k in range(16)]
    low = min([h[0].z for h in under if h] or [seat])
    foot = max(1.2+.25*r, seat-low+.5)
    bpy.context.view_layer.update()
    dg = bpy.context.evaluated_depsgraph_get()
    others = {o.name: _tree(o, dg) for o in bpy.context.scene.objects
              if o.type == 'MESH' and not o.name.startswith(f'Weapon {sid} ')
              and abs(o.location.x) < 1e6 and (Vector((x, y, 0))-o.matrix_world.translation).length < 200}
    lift = .2*r
    for _ in range(30):
        barb = tk.barbette(f'Weapon {sid} barbette', r, lift, _mats(kit), kit.mpu, foot=foot,
                           location=(x, y, seat))
        pivot = kit.empty(f'Weapon {sid} pivot', (x, y, seat+lift), slot['angle'], size=.6*r, kind='SINGLE_ARROW')
        t = tk.build(weapon, f'Weapon {sid}', pivot, r, _mats(kit), kit.mpu)
        bad = _clashes(t['parts'], {n: v for n, v in others.items() if n != barb.name and not n.startswith(barb.name)})
        if not bad:
            break
        print(f'mount {sid}: lift {lift:.2f} clashes {sorted(bad)}')
        for ob in [barb, pivot]+list(barb.children_recursive)+list(pivot.children_recursive):
            if ob.name in bpy.data.objects:
                bpy.data.objects.remove(ob, do_unlink=True)
        lift += .4
    kit.hull.append(barb)
    pivot['slot'] = sid
    pivot['turret'] = i
    pivot['rest_angle_deg'] = slot['angle']
    pivot['arc_deg'] = slot['arc']
    pivot['train_axis'] = 'local Z'
    pivot['weapon'] = weapon
    elev = t['elevation']
    elev['elevation_axis'] = 'local Y (negative lifts the muzzles)'
    bpy.context.view_layer.update()
    if kind == 'launcher':
        missile = next((o for o in bpy.data.objects if o.name.startswith(f'Weapon {sid} missile')
                        and o.type == 'MESH'), None)
        length = max(missile.dimensions) if missile else 1.2*r
        scale = round(length/MISSILE_BODY, 3)
    else:
        scale = round(r/SW_HOUSE*GUN_FLASH.get(weapon, .6), 3)
    muzzles = []
    for k, gm in enumerate(sorted(t['muzzles'], key=lambda o: o.name), 1):
        tag = f"{'tube' if kind == 'launcher' else 'barrel'} {k}"
        mw = gm.matrix_world
        fire = mw.to_3x3().col[0].normalized()
        a = bpy.data.objects.new(f'Anchor turret {i} {tag} muzzle', None)
        bpy.context.collection.objects.link(a)
        a.empty_display_type, a.empty_display_size = 'SINGLE_ARROW', max(1, .3*r)
        a.matrix_world = Matrix.Translation(mw.translation) @ _frame(fire).to_4x4()
        wm = a.matrix_world.copy()
        a.parent = gm
        a.matrix_parent_inverse = gm.matrix_world.inverted()
        a.matrix_world = wm
        a['scale'] = scale
        a['kind'] = kind
        muzzles.append({'tag': tag, 'anchor': a.name, 'geometry_anchor': gm.name,
                        'at': [round(v, 3) for v in mw.translation], 'axis': [round(v, 3) for v in fire]})
    ANCHOR_Z[sid] = seat+lift
    TURRETS.append({'index': i, 'slot': sid, 'mount': slot['mount'], 'weapon': weapon,
                    'variant_weapon': VARIANT_WEAPON[sid], 'kind': kind, 'size': slot['size'], 'type': slot['type'],
                    'ring_radius': r, 'pivot': pivot.name, 'elevation': elev.name,
                    'location': [x, y, round(seat+lift, 2)], 'seat_z': round(seat, 2), 'lift': round(lift, 2),
                    'rest_angle_deg': slot['angle'], 'arc_deg': slot['arc'], 'vfx_scale': scale,
                    'trainable': slot['mount'] == 'TURRET', 'muzzles': muzzles,
                    'elevation_limits_deg': [elev.get('elevation_min_deg'), elev.get('elevation_max_deg')]
                    if 'elevation_min_deg' in elev.keys() else None})
    print(f'mount {sid} ({weapon}): seat {seat:.2f} lift {lift:.2f} muzzles {len(muzzles)} scale {scale}')


def build(kit):
    TURRETS.clear()
    ANCHOR_Z.clear()
    i = 0
    for slot in SLOTS:
        if slot['mount'] in ('TURRET', 'HARDPOINT'):
            i += 1
            mount(kit, i, slot)
        else:
            x, y = slot['location']
            hit = kit.surface(x, y)
            z = hit[0].z if hit else 0.0
            kit.empty(f'Weapon {slot["id"]} anchor', (x, y, z), slot['angle'], size=2)
            ANCHOR_Z[slot['id']] = z
