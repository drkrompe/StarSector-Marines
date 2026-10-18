"""The fifteen turrets at legion.ship's weapon slots, as the XIV skin types them.

Every turret is a fixed barbette seated on the hull with `surface`, and a
trainable part parented to an empty named 'Weapon <slot> pivot'. The pivot
sits at the slot on top of the barbette and rests at the slot angle; rotating
it about its local Z trains the whole turret, so any turret can be keyed (the
cinematic's scrutiny beat uses WS 007). Only what stands above the traverse
gap trains: the barbette, with its foot flange and chamfered lip, is fixed.

Ring radii are read off the sprite's turret rings. Weapons follow the
legion_xiv_Elite variant: squall (WS 000), hurricane (WS 001), flak
(WS 002/003), heavyac (WS 004/005/007), vulcan on the small slots; WS 014/015
are empty in Elite and carry the plain small mount. The geometry is the shared
turret kit's (turret-kit/turret_kit.py) at near detail. Each gun or launcher
elevates on 'Weapon <slot> elevation'; 'Weapon <slot> muzzle anchor <k>'
marks each bore, +X along the line of fire.
"""
import math
import sys
from pathlib import Path
import bpy
import lg_kit
from mathutils import Vector
from mathutils.bvhtree import BVHTree

sys.path.insert(0, str(Path(__file__).resolve().parent.parent/'turret-kit'))
import turret_kit as tk

SLOTS = [  # legion.ship, with the legion_xiv.skin type changes
    {'id': 'WS 000', 'location': (42, 56), 'angle': 30, 'arc': 160, 'size': 'LARGE', 'type': 'MISSILE', 'weapon': 'squall'},
    {'id': 'WS 001', 'location': (42, -56), 'angle': -30, 'arc': 160, 'size': 'LARGE', 'type': 'MISSILE', 'weapon': 'hurricane'},
    {'id': 'WS 002', 'location': (13, 107), 'angle': 55, 'arc': 180, 'size': 'MEDIUM', 'type': 'BALLISTIC', 'weapon': 'flak'},
    {'id': 'WS 003', 'location': (13, -107), 'angle': -55, 'arc': 180, 'size': 'MEDIUM', 'type': 'BALLISTIC', 'weapon': 'flak'},
    {'id': 'WS 004', 'location': (80, 34), 'angle': 20, 'arc': 160, 'size': 'MEDIUM', 'type': 'BALLISTIC', 'weapon': 'heavyac'},
    {'id': 'WS 005', 'location': (104, 0), 'angle': 0, 'arc': 180, 'size': 'MEDIUM', 'type': 'BALLISTIC', 'weapon': 'heavyac'},
    {'id': 'WS 007', 'location': (80, -34), 'angle': -20, 'arc': 160, 'size': 'MEDIUM', 'type': 'BALLISTIC', 'weapon': 'heavyac'},
    {'id': 'WS 008', 'location': (-58, 130), 'angle': 90, 'arc': 180, 'size': 'SMALL', 'type': 'BALLISTIC', 'weapon': 'vulcan'},
    {'id': 'WS 009', 'location': (-58, -130), 'angle': -90, 'arc': 180, 'size': 'SMALL', 'type': 'BALLISTIC', 'weapon': 'vulcan'},
    {'id': 'WS 014', 'location': (-8, 118), 'angle': 82, 'arc': 156, 'size': 'SMALL', 'type': 'BALLISTIC', 'weapon': 'none in Elite'},
    {'id': 'WS 015', 'location': (-8, -118), 'angle': -82, 'arc': 156, 'size': 'SMALL', 'type': 'BALLISTIC', 'weapon': 'none in Elite'},
    {'id': 'WS 018', 'location': (36, 91), 'angle': 24, 'arc': 100, 'size': 'SMALL', 'type': 'BALLISTIC', 'weapon': 'vulcan'},
    {'id': 'WS 019', 'location': (36, -91), 'angle': -24, 'arc': 100, 'size': 'SMALL', 'type': 'BALLISTIC', 'weapon': 'vulcan'},
    {'id': 'WS 020', 'location': (76, 60), 'angle': 39, 'arc': 160, 'size': 'SMALL', 'type': 'BALLISTIC', 'weapon': 'vulcan'},
    {'id': 'WS 021', 'location': (76, -60), 'angle': -39, 'arc': 160, 'size': 'SMALL', 'type': 'BALLISTIC', 'weapon': 'vulcan'},
]
# Slot types before the skin, for checking against legion.ship.
BASE_TYPE = {'WS 000': 'BALLISTIC', 'WS 001': 'BALLISTIC', 'WS 002': 'COMPOSITE', 'WS 003': 'COMPOSITE',
             'WS 004': 'COMPOSITE', 'WS 005': 'COMPOSITE', 'WS 007': 'COMPOSITE'}
RING = {'LARGE': 12.5, 'MEDIUM': 9.5, 'SMALL': 5.5}
KEYABLE = 'WS 007'           # starboard dorsal heavy autocannon: trains on Second Wind in C2
KEY_TEST_ANGLES = (-90, -100, 60)   # laid on Second Wind abeam to starboard, and both arc limits
MIRRORED = {'WS 004': KEYABLE}      # its port twin is tested at the mirrored angles, so the pair matches
SPONSONS = {}                # slot id -> rim rays over open space (the ring overhangs the hull edge)
PIVOT_Z = {}                 # slot id -> pivot height, filled by build()
ARC_REPORT = {}              # slot id -> lift and the swept angles that clash (report only)


# --- the shared turret kit ---------------------------------------------------
# Turret geometry comes from turret-kit/turret_kit.py, shared by every hull:
# silhouettes proportioned to the sprite ring, fittings in true metres, guns
# on an elevation empty. This module only seats them and checks clashes.

def _mats(kit):
    return {'armour': kit.orange, 'plate': kit.plate, 'steel': kit.steel, 'dark': kit.dark}


def _trainable(kit, slot, pivot, r):
    """The turret's moving objects, and the empties (elevation, muzzle anchors) that go with them."""
    weapon = 'none' if slot['weapon'].startswith('none') else slot['weapon']
    t = tk.build(weapon, f"Weapon {slot['id']}", pivot, r, _mats(kit), lg_kit.METRES_PER_UNIT)
    return t['parts'], [t['elevation']]+t['muzzles']


def _barbette(kit, name, x, y, seat, lift, r, foot, sponson=0.0, sponson_dir=0.0):
    return tk.barbette(name, r, lift, _mats(kit), lg_kit.METRES_PER_UNIT, foot=foot, location=(x, y, seat),
                       sponson=sponson, sponson_dir=sponson_dir)


# --- clash checking ---------------------------------------------------------

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
        t, lo, hi = _tree(p, dg)
        for name, (o, olo, ohi) in others.items():
            if all(lo[i] <= ohi[i] and olo[i] <= hi[i] for i in range(3)) and t.overlap(o):
                hits.add(name)
    return hits


def build(kit):
    cache = {}
    # The keyed pair goes last, so its training test also sees the small mounts beside it
    # (WS 007 laid abeam passes its muzzles over WS 021).
    for slot in sorted(SLOTS, key=lambda s: s['id'] == KEYABLE or s['id'] in MIRRORED):
        sid = slot['id']
        x, y = slot['location']
        r = RING[slot['size']]
        # Seat on the highest surface under the ring: the sprite puts several small
        # mounts on the orange belts, whose barrels lie across the belt at rest, so
        # seating them on the deck only made the clash test lift them back up. The
        # part of the barbette below the seat is a belt-coloured bastion (plinth),
        # so the belt carries the mount instead of a tall grey stalk.
        hits = [kit.surface(x+.6*r*math.cos(k*math.pi/4), y+.6*r*math.sin(k*math.pi/4), top=120) for k in range(8)]
        hits = [h for h in hits+[kit.surface(x, y, top=120)] if h]
        seat = max(h[0].z for h in hits) if hits else 10.0
        # The foot runs down to the lowest surface under the whole flange, so a
        # mount seated on a belt still stands on the deck beside it, never floats.
        under = [kit.surface(x+f*r*math.cos(k*math.pi/8), y+f*r*math.sin(k*math.pi/8), top=120)
                 for f in (.6, .9, 1.12, 1.4, 1.68) for k in range(16)]
        low = min([h[0].z for h in under if h] or [seat])
        foot = max(2+.35*r, seat-low+.6)
        # Where the ring overhangs the hull's edge (rays that meet nothing), the
        # barbette stands on a sponson tub that tapers into the side wall.
        edge = [(k, h) for k, h in enumerate(under[16:48]) if not h]
        sponson, sponson_dir = 0.0, 0.0
        if edge:
            sponson = 1.1*r
            sponson_dir = math.atan2(sum(math.sin(k % 16*math.pi/8) for k, _ in edge),
                                     sum(math.cos(k % 16*math.pi/8) for k, _ in edge))
            SPONSONS[sid] = len(edge)
        lift = .22*r
        tests = [slot['angle']] + (list(KEY_TEST_ANGLES) if sid == KEYABLE else
                                   [-a for a in KEY_TEST_ANGLES] if sid in MIRRORED else [])
        bpy.context.view_layer.update()
        dg = bpy.context.evaluated_depsgraph_get()
        mine = f'Weapon {sid} '
        for ob in bpy.context.scene.objects:   # every other solid: hull, other modules, earlier turrets
            if ob.type == 'MESH' and not ob.name.startswith(mine) and ob.name not in cache:
                cache[ob.name] = _tree(ob, dg)
        reach = 3.2*r
        others = {n: v for n, v in cache.items() if not n.startswith(mine)
                  and v[1].x <= x+reach and v[2].x >= x-reach and v[1].y <= y+reach and v[2].y >= y-reach}
        for _ in range(40):
            barbette = _barbette(kit, f'Weapon {sid} barbette', x, y, seat, lift, r, foot, sponson, sponson_dir)
            pivot = kit.empty(f'Weapon {sid} pivot', (x, y, seat+lift), slot['angle'])
            parts, extras = _trainable(kit, slot, pivot, r)
            bad = {a: h for a in tests if (h := _clashes(parts, pivot, a, others))}
            pivot.rotation_euler = (0, 0, math.radians(slot['angle']))
            if not bad:
                break
            print(f'turret {sid}: lift {lift:.2f} clashes {bad}')
            for ob in [barbette, pivot] + list(barbette.children) + parts + extras:   # clash: lift the barbette, never move the slot
                bpy.data.objects.remove(ob, do_unlink=True)
            lift += .4
        # Arc sweep (report only): which trained angles would put the turret into something.
        a0 = slot['angle']-slot['arc']/2
        sweep = [a0+slot['arc']*i/16 for i in range(17)]
        ARC_REPORT[sid] = {'lift': round(lift, 2), 'tests': tests,
                           'clash_angles': {round(a): sorted(h) for a in sweep if (h := _clashes(parts, pivot, a, others))}}
        pivot.rotation_euler = (0, 0, math.radians(slot['angle']))
        bpy.context.view_layer.update()
        PIVOT_Z[sid] = seat+lift
        kit.hull.append(barbette)
        kit.hull.extend(ch for ch in barbette.children if ch.type == 'MESH')
        print(f"turret {sid}: seat {seat:.2f} lift {lift:.2f} foot {foot:.2f} sponson {sponson:.2f} parts {len(parts)} "
              f"arc clashes {ARC_REPORT[sid]['clash_angles']}")


def records():
    return [dict({k: v for k, v in s.items() if k != 'location'}, location=[*s['location'], round(PIVOT_Z[s['id']], 2)],
                 mount='TURRET', ring_radius=RING[s['size']], pivot=f"Weapon {s['id']} pivot",
                 objects=f"Weapon {s['id']} ...", trains='rotate the pivot about its local Z; rest is the slot angle',
                 elevation=f"Weapon {s['id']} elevation", elevates='rotate the elevation empty about its local Y; negative lifts the muzzles',
                 muzzle_anchors=f"Weapon {s['id']} muzzle anchor <k>", built_by='turret-kit/turret_kit.py',
                 keyable_for_c2=s['id'] == KEYABLE)
            for s in SLOTS]
