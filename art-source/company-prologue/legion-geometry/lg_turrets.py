"""The sixteen turrets at legion.ship's weapon slots, as the XIV skin types them.

Every turret is a fixed barbette seated on the hull with `surface`, and a
trainable part (house and barrels or launcher) parented to an empty named
'Weapon <slot> pivot'. The pivot sits at the slot on top of the barbette and
rests at the slot angle; rotating it about Z trains the turret, so any turret
can be keyed (the cinematic's scrutiny beat uses WS 007). Ring radii are read
off the sprite's turret rings. Fittings follow the legion_xiv_Elite variant
for flavour: Squall and Hurricane launchers, heavy autocannon, flak, Vulcans.
"""
import math
import bpy
from mathutils import Matrix, Vector
from mathutils.bvhtree import BVHTree

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
PIVOT_Z = {}                 # slot id -> pivot height, filled by build()


def _tree(ob, dg):
    ev = ob.evaluated_get(dg)
    me = ev.to_mesh()
    t = BVHTree.FromPolygons([ev.matrix_world @ v.co for v in me.vertices], [tuple(p.vertices) for p in me.polygons])
    ev.to_mesh_clear()
    return t


def _part(kit, name, pivot, loc, dims, mat, bevel=.12, cyl=None):
    if cyl:
        radius, depth, axis = cyl
        ob = kit.cylinder(name, loc, radius, depth, mat, axis=axis, vertices=16, bevel=bevel)
    else:
        ob = kit.box(name, loc, dims, mat, bevel=bevel)
    ob.parent = pivot     # loc is in the pivot's frame: +X along the barrels
    return ob


def _trainable(kit, slot, pivot, r):
    sid = slot['id']
    if slot['type'] == 'MISSILE':
        house = [_part(kit, f'Weapon {sid} launcher house', pivot, (0, 0, .32*r), (1.55*r, 1.25*r, .64*r), kit.plate, .3)]
        house.append(_part(kit, f'Weapon {sid} launcher cell face', pivot, (.78*r, 0, .34*r), (.08*r, 1.05*r, .48*r), kit.dark, 0))
        return house, []
    house = [_part(kit, f'Weapon {sid} turret house', pivot, (0, 0, .25*r), None, kit.orange if slot['size'] != 'SMALL' else kit.plate,
                   .15, cyl=(.8*r, .5*r, 'Z')),
             _part(kit, f'Weapon {sid} turret shield', pivot, (.45*r, 0, .3*r), (.55*r, 1.1*r, .44*r), kit.plate, .1)]
    n, length, rad = {'heavyac': (2, 1.6, .09), 'flak': (2, 1.1, .1), 'vulcan': (1, 1.25, .16)}.get(slot['weapon'], (1, 1.2, .14))
    barrels = []
    for k in range(n):
        off = (k-(n-1)/2)*.32*r
        barrels.append(_part(kit, f'Weapon {sid} barrel {k+1}', pivot, (.6*r+length*r/2, off, .32*r), None, kit.steel,
                             .03, cyl=(rad*r, length*r, 'X')))
    return house, barrels


def build(kit):
    dg = bpy.context.evaluated_depsgraph_get()
    for slot in SLOTS:
        x, y = slot['location']
        r = RING[slot['size']]
        hit = kit.surface(x, y, top=120)
        seat = hit[0].z if hit else 10.0
        lift = .22*r
        for _ in range(8):
            barbette = kit.cylinder(f"Weapon {slot['id']} barbette", (x, y, seat-2+(lift+2)/2), r, lift+2,
                                    kit.steel, vertices=28, bevel=.15)
            pivot = kit.empty(f"Weapon {slot['id']} pivot", (x, y, seat+lift), slot['angle'])
            house, barrels = _trainable(kit, slot, pivot, r)
            bpy.context.view_layer.update()
            dg = bpy.context.evaluated_depsgraph_get()
            mine = {barbette.name, pivot.name} | {o.name for o in house + barrels}
            others = [_tree(o, dg) for o in kit.hull if o.name not in mine]
            moving = [_tree(o, dg) for o in house + barrels]
            if not any(t.overlap(o) for t in moving for o in others):
                break
            for ob in [barbette, pivot] + house + barrels:   # clash: lift the barbette, never move the slot
                bpy.data.objects.remove(ob, do_unlink=True)
            lift += 1.5
        PIVOT_Z[slot['id']] = seat+lift
        kit.hull.append(barbette)


def records():
    return [dict({k: v for k, v in s.items() if k != 'location'}, location=[*s['location'], round(PIVOT_Z[s['id']], 2)],
                 mount='TURRET', ring_radius=RING[s['size']], pivot=f"Weapon {s['id']} pivot",
                 objects=f"Weapon {s['id']} ...", trains='rotate the pivot about its local Z; rest is the slot angle',
                 keyable_for_c2=s['id'] == KEYABLE)
            for s in SLOTS]
