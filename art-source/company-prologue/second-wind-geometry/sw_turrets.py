"""The four weapon mounts: two Vulcans from the shared turret kit, and two empty housings.

warhound.ship's slots, fitted as the owner chose (2026-10-20): a stripped fit,
the same in every condition state, lighter than the vanilla cerberus_Standard
variant (Vulcans on 1-2, a railgun on 3, a heavy mauler on 4):
  WS 001  TURRET  SMALL   BALLISTIC  (-11, 20)   90   arc 210   Turret 1  vulcan (port, aft)
  WS 002  TURRET  SMALL   BALLISTIC  (-11, -20)  -90  arc 210   Turret 2  empty housing
  WS 003  TURRET  SMALL   BALLISTIC  (41, 11)    30   arc 210   Turret 3  empty housing
  WS 004  TURRET  MEDIUM  BALLISTIC  (36, -16)   -30  arc 290   Turret 4  vulcan (starboard, bow)
One gun a side, one fore and one aft. The derelict's stripped turret 4 and
wrecked 1 and 3 are its scars (sw_scars_spine), not its loadout.

A gun is `tk.build(weapon)` from ../turret-kit on a kit barbette, as the
Legion, Paragon and Wolf mount theirs. `Turret <i> pivot` is an empty at the
top of the barbette: turn it about hull +Z to train (it rests at the slot
angle, `rest_angle_deg`); `Turret <i> elevation` lifts the guns with a
negative rotation about its local Y; each barrel recoils on `Turret <i>
recoil <k>`. `Anchor turret <i> barrel <k> muzzle` rides the kit's own muzzle
anchor, +X along the line of fire, for the VFX kit (DECLARED_MUZZLES) and the
cinematic export. Everything the turret trains is named `Turret <i> house ...`,
`Turret <i> gun ...` or `Turret <i> recoil <k> ...`; the fixed part is
`Turret <i> barbette` (with its `... plinth` where the foot is deep). Turret
4's small Vulcan stands on `Turret 4 reducer plate`, a fixed adapter on the
medium drum.

An empty housing is the same kit barbette closed by `Turret <i> blanking
cap` (a bolted plate) and `Turret <i> blanking cap boss` (the plugged feed
trunk at its centre); its pivot is kept, empty, so every slot has one.

Rings are in hull units: the small mounts on 4.2, so the kit's flange (1.1
ring) lands inside the pods' octagonal seats for 1 and 2 (4.62 to the flats),
a little inside the sprite's own small ring; turret 4's drum on 7.0, the
sprite's, with its Vulcan at the small ring.
"""
import math
import sys
from pathlib import Path
import bpy

sys.path.insert(0, str(Path(__file__).resolve().parent.parent/'turret-kit'))
import turret_kit as tk  # noqa: E402

# warhound.ship WS 001..004: XY, resting angle (degrees CCW from the bow, so +90
# faces port), mount scale (the VFX kit's flash scale and the scars' strike
# scale: 1.0 small, 1.3 medium), twin (no mount fires a paired port/starboard
# gun any more: each gun declares its barrels).
# The scars' keep-out reads the housing's scale, so turret 4 stays 1.3.
MOUNTS = [(-11, 20, 90, 1.0, False), (-11, -20, -90, 1.0, False), (41, 11, 30, 1.0, False), (36, -16, -30, 1.3, False)]
SLOTS = ['WS 001', 'WS 002', 'WS 003', 'WS 004']
ARC = [210, 210, 210, 290]
SIZE = ['SMALL', 'SMALL', 'SMALL', 'MEDIUM']
LOADOUT = ['vulcan', None, None, 'vulcan']   # None: an empty housing
RING = [4.2, 4.2, 4.2, 7.0]                   # the barbette
GUN_RING = 4.2                                # every gun is a small Vulcan
REDUCER_H = .3                                # turret 4's adapter plate under its Vulcan
# Barbette drum above its seat. Turret 3 carries no gun, so it no longer stands
# superfiring over the stowed port grapple arm.
LIFT = [1.6, 1.6, 1.6, 1.8]
# The VFX kit's mounts (swfx_anchors), flash scale 1.0: both guns are small.
VFX_MOUNTS = [(x, y, a, 1.0, False, 'gun') for x, y, a, _, _ in MOUNTS]
ENERGY_COLOURS = {}

BUILT = {}   # index -> record, filled by build(); the scars and geometry.json read it


def mats(kit):
    return {'armour': kit.orange, 'plate': kit.steel, 'steel': kit.steel, 'dark': kit.dark}


def seat(kit, i, x, y, r):
    """Highest and lowest hull Z under the barbette (sampled just inside its ring: the
    pods' seats for 1 and 2 end under the flange's rim, and the side below is not its seat)."""
    zs = []
    for k in range(17):
        a = k*math.tau/16
        px, py = (x, y) if k == 16 else (x+.95*r*math.cos(a), y+.95*r*math.sin(a))
        hit = kit.surface(px, py)
        if hit is not None:
            zs.append(hit[0].z)
    if kit.surface(x, y) is None:
        raise RuntimeError(f'Turret {i} has no supporting hull surface')
    return max(zs), min(zs)


def empty(name, loc, rot_z=0.0, size=1.0, kind='PLAIN_AXES', parent=None):
    ob = bpy.data.objects.new(name, None)
    ob.empty_display_type, ob.empty_display_size = kind, size
    bpy.context.collection.objects.link(ob)
    ob.parent = parent
    ob.location = loc
    ob.rotation_euler = (0, 0, rot_z)
    return ob


def build(kit):
    BUILT.clear()
    mpu = getattr(kit, 'mpu', .65)
    for i, ((x, y, angle, s, _), sid, arc, size, weapon, ring, lift) in enumerate(
            zip(MOUNTS, SLOTS, ARC, SIZE, LOADOUT, RING, LIFT), 1):
        top_hull, low = seat(kit, i, x, y, ring)
        tk.barbette(f'Turret {i} barbette', ring, lift, mats(kit), mpu, foot=top_hull-low+.5,
                    location=(x, y, top_hull))
        top = top_hull+lift
        pivot = empty(f'Turret {i} pivot', (x, y, top), math.radians(angle), 2.5, 'SINGLE_ARROW')
        pivot['slot'], pivot['weapon'], pivot['rest_angle_deg'] = sid, weapon or 'empty', float(angle)
        pivot['arc_deg'], pivot['train_axis'] = float(arc), 'hull +Z'
        rec = {'index': i, 'slot': sid, 'size': size, 'weapon': weapon or 'empty', 'ring': ring, 'lift': lift,
               'seat': top_hull, 'top': top, 'location': [x, y, round(top, 3)], 'rest_angle_deg': angle,
               'arc_deg': arc, 'pivot': pivot.name, 'fixed': [f'Turret {i} barbette']}
        if weapon is None:
            # Blanking cap: a bolted plate over the drum's crown and the plugged feed trunk.
            cap = kit.cylinder(f'Turret {i} blanking cap', (x, y, top+.15), ring-.45, .3, kit.steel, 'Z', 32, .05)
            boss = kit.cylinder(f'Turret {i} blanking cap boss', (x, y, top+.45), .32*ring, .35, kit.steel, 'Z', 16, .05)
            rec.update(k=GUN_RING/tk.NOMINAL_RING['SMALL'], elevation=None, trunnion=[x, y, top],
                       elevation_limits=None, fixed=rec['fixed']+[cap.name, boss.name], trained=[], recoil=[],
                       kit_muzzles=[], muzzles=[])
            BUILT[i] = rec
            continue
        if ring > GUN_RING:
            plate = kit.cylinder(f'Turret {i} reducer plate', (x, y, top+REDUCER_H/2), GUN_RING*1.1, REDUCER_H,
                                 kit.steel, 'Z', 32, .05)
            pivot.location.z = top+REDUCER_H
            rec['fixed'].append(plate.name)
        t = tk.build(weapon, f'Turret {i}', pivot, GUN_RING, mats(kit), mpu)
        muzzles = []
        for k, m in enumerate(t['muzzles'], 1):
            a = empty(f'Anchor turret {i} barrel {k} muzzle', (0, 0, 0), 0, .6, 'SINGLE_ARROW', m)
            muzzles.append(a.name)
        bpy.context.view_layer.update()
        elev = t['elevation']
        rec.update(k=GUN_RING/tk.NOMINAL_RING['SMALL'], elevation=elev.name,
                   trunnion=list(elev.matrix_world.translation),
                   elevation_limits=[elev.get('elevation_min_deg'), elev.get('elevation_max_deg')],
                   trained=[o.name for o in t['parts']], recoil=[e.name for e in t['moving']],
                   kit_muzzles=[m.name for m in t['muzzles']], muzzles=muzzles)
        BUILT[i] = rec


def records():
    """geometry.json's `turrets`: what build() placed, JSON-safe."""
    out = []
    for i in sorted(BUILT):
        r = dict(BUILT[i])
        r['trunnion'] = [round(c, 3) for c in r['trunnion']]
        r['seat'], r['top'], r['k'] = round(r['seat'], 3), round(r['top'], 3), round(r['k'], 4)
        out.append(r)
    return out


def clearance(names_a, names_b):
    """Pairs of meshes that overlap (BVH), to check a trained turret against the hull."""
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
    trees = {n: tree(n) for n in set(names_a) | set(names_b)}
    return [(a, b) for a in names_a for b in names_b if a != b and trees[a].overlap(trees[b])]
