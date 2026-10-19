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

Hardpoints are fixed box launchers built here (the kit has no missile
hardpoints yet): a launcher body on a saddle with its tube faces, the
swarmer's 3 x 4 small cells, the annihilator pod's 2 x 3. Each slot has
`Weapon <slot> anchor` (+X along the slot angle) and `Weapon <slot> muzzle
anchor <k>` per cell row, +X along the line of fire.
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


def hardpoint(kit, slot):
    """A fixed box launcher on a saddle, its cells facing +X."""
    sid = slot['id']
    x, y = slot['location']
    small = slot['size'] == 'SMALL'
    if small:   # swarmer: on the outrigger pod's forward deck
        hit = kit.surface(x, y)
        z0 = hit[0].z if hit else 4.0
        L, W, H = 9.0, 5.0, 3.6
        rows, cols, cell = 3, 4, .55
        cx = x+1.0
    else:       # annihilator pod: between the bow fork's jaws, on the prong's bow face
        z0 = -3.2
        L, W, H = 16.0, 8.0, 6.4
        rows, cols, cell = 2, 3, 1.1
        cx = x-4.0
    zc = z0+H/2
    kit.box(f'Weapon {sid} saddle', (cx-L*.15, y, z0+.4), (L*.7, W*1.1, 1.0), kit.steel, .12)
    kit.box(f'Weapon {sid} launcher', (cx, y, zc+.3), (L, W, H), kit.plate, .2)
    kit.box(f'Weapon {sid} tube face', (cx+L/2+.15, y, zc+.3), (.3, W*.86, H*.82), kit.steel, .06)
    muzzles = []
    for rr in range(rows):
        dz = (rr-(rows-1)/2)*H*.78/rows
        for cc in range(cols):
            dy = (cc-(cols-1)/2)*W*.84/cols
            kit.cylinder(f'Weapon {sid} cell {rr*cols+cc+1}', (cx+L/2+.32, y+dy, zc+.3+dz), cell, .3,
                         kit.dark, 'X', 10, 0)
        m = kit.empty(f'Weapon {sid} muzzle anchor {rr+1}', (cx+L/2+.6, y, zc+.3+dz), slot['angle'], size=1)
        muzzles.append(m.name)
    kit.empty(f'Weapon {sid} anchor', (x, y, zc+.3), slot['angle'], size=2)
    ANCHOR_Z[sid] = zc+.3
    MUZZLES[sid] = muzzles


def build(kit):
    PIVOT_Z.clear()
    ANCHOR_Z.clear()
    MUZZLES.clear()
    ARC_REPORT.clear()
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
            rec.update(built_by='fc_mounts.hardpoint (box launcher)')
        out.append(rec)
    return out
