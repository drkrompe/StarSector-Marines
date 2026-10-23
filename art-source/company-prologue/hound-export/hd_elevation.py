"""WS 002's elevation joint: the gun lifts on its trunnions inside the trained house.

Shared by export_hound.py (every new export) and elevate_export.py (upgrades a published
export in place of a re-skin). The chain is the turret kit's (turret_kit.build):

  root -> `Weapon WS 002 pivot` (traverse, local Z)
       -> `Weapon WS 002 elevation` (local Y; negative lifts the muzzles)
       -> mantlet, mantlet drum, trunnions, barrel(s), jacket(s), muzzle brake(s), bore(s),
          the Hound (P)'s twin cradle, and every `Anchor weapon WS 002 muzzle[ k]`

so swfx_guns.trainers finds pivot and elevation up a muzzle anchor's parent chain with no
special case. The empty sits on the trunnion axis (the mantlet drum's axis, hd_mounts.turret:
x+2.4 ahead of the turret centre at the gun height), local +X along the barrels at rest, +Z
hull-dorsal. The house, cheeks, glacis, roof fittings and bustle stay on the pivot.

Limits are degrees of elevation, positive up, stored as `elevation_min_deg` /
`elevation_max_deg` on the empty as the turret kit stores them. check_elevation.py sweeps the
gun's own parts against every other mesh at every bearing of the arc: on both geometry states
(clean naval gun, the Hound (P)'s twin MG) nothing new is touched from -16 up to +90, the first
contact being the barrel dipping into the traverse gap at -17. The limits keep a margin inside
that and match the Asharu blockout's gun envelope (battle_blockout.GUN_ELEVATION, -10 .. 85).
"""
import math
from mathutils import Matrix, Vector

SLOT = 'WS 002'
# Mesh parts that ride the elevation: everything forward of and on the trunnion axis.
ELEVATING = ('mantlet', 'trunnion', 'barrel', 'muzzle brake', 'muzzle bore', 'twin cradle')
DRUM = f'Weapon {SLOT} mantlet drum'
# (min, max) degrees of elevation by geometry state, inside check_elevation.py's clean range.
LIMITS = {'clean': (-10.0, 85.0), 'pirate': (-10.0, 85.0)}
CONVENTION = (f'Rotate `Weapon {SLOT} pivot` about its local Z to train (rest pose is the slot angle, 145 deg; arc 290); '
              f'rotate `Weapon {SLOT} elevation` (its child, on the trunnion axis) about its local Y to elevate: negative '
              f'lifts the muzzles, limits elevation_min_deg / elevation_max_deg on the empty (positive up). The muzzle '
              f'anchors ride the elevation; the slot anchor rides the pivot.')


def _reparent(ob, parent):
    w = ob.matrix_world.copy()
    ob.parent = parent
    ob.matrix_parent_inverse = parent.matrix_world.inverted()
    ob.matrix_world = w


def _frame(axis):
    x = Vector(axis).normalized()
    up = Vector((0, 0, 1))
    z = (up - x * x.dot(up)).normalized()
    return Matrix((x, z.cross(x), z)).transposed()


def elevating_parts(objects):
    pre = f'Weapon {SLOT} '
    return sorted((o for o in objects if o.type == 'MESH' and o.name.startswith(pre)
                   and any(k in o.name[len(pre):] for k in ELEVATING)), key=lambda o: o.name)


def rig(bpy, state, collection, size=1.5):
    """Add `Weapon WS 002 elevation` under the pivot at the trunnion axis and move the gun
    and its muzzle anchors onto it. The pivot must be at rest (the slot angle). Returns the
    manifest record."""
    pivot = bpy.data.objects[f'Weapon {SLOT} pivot']
    slot_anchor = bpy.data.objects[f'Weapon {SLOT} anchor']
    assert abs(pivot.rotation_euler.z) < 1e-6, 'rig the elevation with the pivot at rest'
    drum = bpy.data.objects[DRUM]
    # The drum is a Y cylinder trained by the slot angle: its centre is the mean of its vertices.
    centre = sum((drum.matrix_world @ v.co for v in drum.data.vertices), Vector()) / len(drum.data.vertices)
    axis = (slot_anchor.matrix_world.to_3x3() @ Vector((1, 0, 0))).normalized()
    elev = bpy.data.objects.new(f'Weapon {SLOT} elevation', None)
    elev.empty_display_type, elev.empty_display_size = 'ARROWS', size
    collection.objects.link(elev)
    # The trunnion frame goes in the parent inverse, so the empty's own channels are zero at
    # rest and rotation_euler.y is the elevation alone (the turret kit's empty gets the same
    # from a pivot that carries the yaw; this pivot is unrotated, the yaw is in the meshes).
    elev.parent = pivot
    elev.matrix_parent_inverse = pivot.matrix_world.inverted() @ Matrix.Translation(centre) @ _frame(axis).to_4x4()
    elev.matrix_basis = Matrix.Identity(4)
    bpy.context.view_layer.update()
    lo, hi = LIMITS[state]
    elev['elevation_min_deg'], elev['elevation_max_deg'] = lo, hi
    elev['elevation_axis'] = 'local Y (the trunnion axis); negative Y rotation lifts the muzzles'
    elev['slot'] = SLOT
    parts = elevating_parts(bpy.data.objects)
    anchors = sorted((o for o in bpy.data.objects if o.type == 'EMPTY'
                      and (o.name == f'Anchor weapon {SLOT} muzzle' or o.name.startswith(f'Anchor weapon {SLOT} muzzle '))),
                     key=lambda o: o.name)
    assert parts and anchors, (parts, anchors)
    for o in parts + anchors:
        _reparent(o, elev)
    bpy.context.view_layer.update()
    return {'name': elev.name, 'at': [round(c, 3) for c in elev.matrix_world.translation],
            'trunnion_axis': [round(c, 3) for c in elev.matrix_world.to_3x3().col[1]],
            'min_deg': lo, 'max_deg': hi, 'elevated_parts': [o.name for o in parts],
            'muzzle_anchors': [o.name for o in anchors]}


def set_elevation(elev, degrees):
    """Pose the gun `degrees` above its rest line (positive up)."""
    elev.rotation_euler = (0, -math.radians(degrees), 0)
