"""The Wolf (P)'s weapon slots: wolf.ship less the two the pirate skin removes.

wolf_d_pirates.skin: "removeWeaponSlots": ["WS 002", "WS 003"], so the aft
pair of small energy turrets (-13, +-12) is gone and the sprite paints
crimson armour domes over their rings (wf_hull). What remains:
  WS 001  TURRET     SMALL   ENERGY   (11, 0)    0    arc 150   Turret 1
  WS 004  HARDPOINT  MEDIUM  ENERGY   (45, 0)    0    arc 5     in the bow fork
  WS 005  HARDPOINT  SMALL   MISSILE  (11, 20)   0    arc 5     port pod
  WS 006  HARDPOINT  SMALL   MISSILE  (11, -19)  0    arc 5     starboard pod
Loadout: wolf_d_pirates_Attack.variant, the pirate fleets' goal variant: an
ion cannon on WS 001, a mining blaster on WS 004, Annihilator rocket pods on
WS 005 / WS 006.

Every gun comes from the shared turret kit (../turret-kit, turret_kit.py):
the ion cannon is `tk.build('ioncannon')` on a kit barbette, trained by
`Turret 1 pivot` (an empty at the top of the drum; turn it about hull +Z, it
rests ahead) and elevated by `Turret 1 elevation`; the mining blaster and the
Annihilator pods are `tk.build_hardpoint` on fixed `Weapon <id> mount`
empties: the mining blaster runs in an open trough on the prow's centreline
between its two armoured jaws (the sprite's outer tines), its breech under the
gun hood and buried in the head; the Annihilators are the kit's launcher heads
(round 6), each standing on its swept wing's tip on a fixed collar sunk into the
wing, the five-tube bank facing ahead. Hardpoint size: the game draws them over
the hull at sprite scale and well proud of the bow; here they are scaled
(HARDPOINT_K) to the hull's own prow and wings. The merchant's WS 002 / WS 003 PD
turrets stand in wells sunk into low sponsons (wf_hull), on short drums.

Mounts for the shared VFX kit (../second-wind-vfx), in this order: 1 WS 001,
2 WS 004, 3 WS 005, 4 WS 006. Each gun has `Anchor turret <i> barrel 1
muzzle` (the launchers `Anchor turret <i> tube <k> muzzle`, one per tube), +X
along the line of fire, parented to the kit's own muzzle anchor. Every slot
also has `Weapon <id> anchor` (+X along the slot angle).
"""
import math
import sys
from pathlib import Path
import bpy
from mathutils import Matrix
from wf_kit import boolean

sys.path.insert(0, str(Path(__file__).resolve().parent.parent/'turret-kit'))
import turret_kit as tk  # noqa: E402

SLOTS = [
    {'id': 'WS 001', 'location': (11, 0), 'angle': 0, 'arc': 150, 'mount': 'TURRET', 'size': 'SMALL', 'type': 'ENERGY'},
    {'id': 'WS 004', 'location': (45, 0), 'angle': 0, 'arc': 5, 'mount': 'HARDPOINT', 'size': 'MEDIUM', 'type': 'ENERGY'},
    {'id': 'WS 005', 'location': (11, 20), 'angle': 0, 'arc': 5, 'mount': 'HARDPOINT', 'size': 'SMALL', 'type': 'MISSILE'},
    {'id': 'WS 006', 'location': (11, -19), 'angle': 0, 'arc': 5, 'mount': 'HARDPOINT', 'size': 'SMALL', 'type': 'MISSILE'},
]
REMOVED = [
    {'id': 'WS 002', 'location': (-13, 12), 'angle': 90, 'arc': 200, 'mount': 'TURRET', 'size': 'SMALL', 'type': 'ENERGY'},
    {'id': 'WS 003', 'location': (-13, -11), 'angle': -90, 'arc': 200, 'mount': 'TURRET', 'size': 'SMALL', 'type': 'ENERGY'},
]
LOADOUT = {'WS 001': 'ioncannon', 'WS 004': 'miningblaster', 'WS 005': 'annihilator', 'WS 006': 'annihilator'}
MOUNT_ORDER = ['WS 001', 'WS 004', 'WS 005', 'WS 006']     # VFX mount index i = position + 1
RING = 5.0            # the sprite's turret ring round WS 001 (rows 39-48)
LIFT = 1.6            # barbette drum above the deck (WS 001)
# The merchant's aft PD mounts (owner, round 6: "read like cylinders and are lifted quite
# high"): each stands in a well sunk into a low sponson faired into the flank (wf_hull),
# its drum rising only a little above the sponson deck, so the turret head sits on the hull.
TURRET_LIFT = {'WS 002': 1.25, 'WS 003': 1.25}
TURRET_FOOT = {'WS 002': .5, 'WS 003': .5}
HARDPOINT_K = {'WS 004': .42, 'WS 005': .6, 'WS 006': .6}   # hull units per weapon sprite pixel
AXIS_Z = {'WS 004': -3.6}         # the bow gun's bore axis height
# The Annihilators (owner, round 6: "modeled missile turret heads on the left and right"):
# launcher heads standing on the swept wings' tips, their fixed collars sunk into the
# wing; the bore axis is this many weapon pixels above the wing surface at the slot.
HEAD_ABOVE = {'WS 005': 3.8, 'WS 006': 3.8}
# Bays cut round the hardpoints: ((x0, x1) about the slot, half width, (z0, z1) about the
# axis, hosts). The mining blaster's trough runs through the prow between the jaws,
# open above; its breech is under the gun hood and buried in the head. The launcher
# heads stand on the wings and need no bay.
BAYS = {'WS 004': ((-1.6, 10.5), 3.7, (-2.9, 12.0), ('Hull bow fork base',))}
# The base hull (the merchant variant): every wolf.ship slot, armed as the vanilla
# wolf_Assault variant. The kit has no heavy blaster or Harpoon hardpoint yet: the
# mining blaster and Annihilator pod forms stand in for them (recorded as built_as).
PIRATE = dict(slots=SLOTS, removed=REMOVED, loadout=LOADOUT, order=MOUNT_ORDER, built_as={})
MERCHANT = dict(
    slots=sorted(SLOTS+REMOVED, key=lambda s: s['id']), removed=[],
    loadout={'WS 001': 'ioncannon', 'WS 002': 'pdlaser', 'WS 003': 'pdlaser', 'WS 004': 'heavyblaster',
             'WS 005': 'harpoon', 'WS 006': 'harpoon'},
    order=['WS 001', 'WS 002', 'WS 003', 'WS 004', 'WS 005', 'WS 006'],
    built_as={'WS 004': 'miningblaster', 'WS 005': 'annihilator', 'WS 006': 'annihilator'})
TURRET_RING = {'WS 001': RING, 'WS 002': 4.4, 'WS 003': 4.4}
HARDPOINT_K.update({})
BUILT_AS = {}


def configure(variant):
    """Point the module's slot tables at the pirate skin or the base hull."""
    global SLOTS, REMOVED, LOADOUT, MOUNT_ORDER, BUILT_AS
    v = MERCHANT if variant == 'merchant' else PIRATE
    SLOTS, REMOVED, LOADOUT, MOUNT_ORDER, BUILT_AS = v['slots'], v['removed'], v['loadout'], v['order'], v['built_as']


ANCHOR_Z = {}
TURRETS = []
HARDPOINTS = []
MUZZLES = {}


def mats(kit):
    return {'armour': kit.red, 'plate': kit.steel, 'steel': kit.steel, 'dark': kit.dark}


def vfx_anchor(name, src):
    """The VFX kit's declared muzzle, riding the turret kit's own anchor."""
    a = bpy.data.objects.new(name, None)
    a.empty_display_size = .6
    bpy.context.collection.objects.link(a)
    a.parent = src
    return a


def turret(kit, i, slot, mpu):
    x, y = slot['location']
    sid = slot['id']
    seat = kit.surface(x, y)[0].z
    ring = TURRET_RING[sid]
    lift = TURRET_LIFT.get(sid, LIFT)
    tk.barbette(f'Turret {i} barbette', ring, lift, mats(kit), mpu, foot=TURRET_FOOT.get(sid, 1.2),
                location=(x, y, seat))
    top = seat+lift
    pivot = kit.empty(f'Turret {i} pivot', (x, y, top), 0, size=2.5, kind='SINGLE_ARROW')
    t = tk.build(LOADOUT[sid], f'Turret {i}', pivot, ring, mats(kit), mpu)
    muzzles = [vfx_anchor(f'Anchor turret {MOUNT_ORDER.index(sid)+1} barrel {k} muzzle', m).name
               for k, m in enumerate(t['muzzles'], 1)]
    pivot.rotation_euler = (0, 0, math.radians(slot['angle']))
    pivot['slot'] = sid
    pivot['weapon'] = LOADOUT[sid]
    pivot['rest_angle_deg'] = slot['angle']
    pivot['arc_deg'] = slot['arc']
    pivot['train_axis'] = 'hull +Z'
    kit.empty(f'Weapon {sid} anchor', (x, y, top), slot['angle'], size=2)
    ANCHOR_Z[sid] = top
    MUZZLES[sid] = muzzles
    TURRETS.append({'index': i, 'slot': sid, 'weapon': LOADOUT[sid], 'pivot': pivot.name,
                    'elevation': t['elevation'].name, 'location': [x, y, round(top, 2)],
                    'rest_angle_deg': slot['angle'], 'arc_deg': slot['arc'], 'ring': ring,
                    'fixed': [f'Turret {i} barbette'], 'trained': [o.name for o in t['parts']],
                    'kit_muzzles': [m.name for m in t['muzzles']], 'muzzles': muzzles})


def hardpoint(kit, slot, mpu):
    x, y = slot['location']
    sid = slot['id']
    z = AXIS_Z[sid] if sid in AXIS_Z else kit.surface(x, y)[0].z+HEAD_ABOVE[sid]*HARDPOINT_K[sid]
    (bx0, bx1), hw, (bz0, bz1), hosts = BAYS.get(sid, ((0, 0), 0, (0, 0), ()))
    for host in hosts:
        cutter = kit.box(f'Temporary {sid} bay cutter', (x+(bx0+bx1)/2, y, z+(bz0+bz1)/2),
                         (bx1-bx0, 2*hw, bz1-bz0), kit.dark, 0)
        boolean(bpy.data.objects[host], cutter)
    mount = kit.empty(f'Weapon {sid} mount', (x, y, z), slot['angle'], size=2)
    built = BUILT_AS.get(sid, LOADOUT[sid])
    hp = tk.build_hardpoint(built, f'Weapon {sid}', mount, mats(kit), mpu, k=HARDPOINT_K[sid])
    tag = 'tube' if slot['type'] == 'MISSILE' else 'barrel'
    i = MOUNT_ORDER.index(sid)+1
    muzzles = [vfx_anchor(f'Anchor turret {i} {tag} {k} muzzle', m).name for k, m in enumerate(hp['muzzles'], 1)]
    kit.empty(f'Weapon {sid} anchor', (x, y, z), slot['angle'], size=2)
    ANCHOR_Z[sid] = z
    MUZZLES[sid] = muzzles
    HARDPOINTS.append({'slot': sid, 'weapon': LOADOUT[sid], 'built_as': built, 'mount': mount.name, 'location': [x, y, round(z, 2)],
                       'k': HARDPOINT_K[sid], 'bay': {'x': [x+bx0, x+bx1], 'half_width': hw,
                                                      'z': [z+bz0, z+bz1], 'cut_from': list(hosts)}
                       if hosts else None,
                       'parts': [o.name for o in hp['parts']], 'kit_muzzles': [m.name for m in hp['muzzles']],
                       'muzzles': muzzles})


def build(kit):
    ANCHOR_Z.clear()
    TURRETS.clear()
    HARDPOINTS.clear()
    MUZZLES.clear()
    i = 0
    for slot in SLOTS:
        if slot['mount'] == 'TURRET':
            i += 1
            turret(kit, i, slot, kit.mpu)
        else:
            hardpoint(kit, slot, kit.mpu)


def mounts():
    """VFX mounts in MOUNT_ORDER: (x, y, angle, scale, twin, kind)."""
    out = []
    for sid in MOUNT_ORDER:
        slot = next(s for s in SLOTS if s['id'] == sid)
        kind = 'launcher' if slot['type'] == 'MISSILE' else 'gun'
        scale = .5 if slot['size'] == 'SMALL' else .7
        out.append({'index': MOUNT_ORDER.index(sid)+1, 'slot': sid, 'weapon': LOADOUT[sid],
                    'location': list(slot['location']), 'angle': slot['angle'], 'scale': scale, 'kind': kind,
                    'muzzles': MUZZLES[sid]})
    return out


def clearance(names_a, names_b):
    """Pairs of meshes that overlap (BVH), for the trained turret against the hull."""
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
