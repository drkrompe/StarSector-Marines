"""Hermes drive units, exactly at hermes.ship's engine slots.

Engines 1-3 are the main drives on the aft block's stern face (slots at
X -39, Y +10 / 0 / -10, width 8, angle 180). The sprite shows three
separate nubs there (Y 6..13, -3..3, -13..-6), so each is a whole drive
unit: a can sunk into the block, a bell, a mouth lip and a dark throat.
Engines 4-5 are the maneuvering jets (system maneuveringjets) at
(-19, +-23), angles 150 / 210 and width 6: canted nozzles in the notch
where the wing body meets the aft block.

Names follow the Second Wind's: 'Engine {i} aft-facing bell', 'Engine {i}
mouth lip', 'Engine {i} dark throat', plus an 'Engine {i} exhaust anchor'
empty at the mouth pointing along the exhaust.
"""
import math
import bpy
from mathutils import Vector

# (x, y, angle, width) from hermes.ship, in slot order.
ENGINES = [(-39, 10, 180, 8), (-39, 0, 180, 8), (-39, -10, 180, 8),
           (-19, 23, 150, 6), (-19, -23, 210, 6)]
ROLES = ['port main drive', 'centre main drive', 'starboard main drive',
         'port maneuvering jet', 'starboard maneuvering jet']
MAIN_Z, JET_Z = 0.0, 0.0
STERN_FACE = -37.0


def bell_radius(i, width):
    """Mouth radius: the slot width read as the bell's mouth diameter, less a lip."""
    return width*.45 if i <= 3 else width*.4


def z_of(i):
    return MAIN_Z if i <= 3 else JET_Z


def cone(kit, name, start, direction, r0, r1, length, mat, verts=32, cap=True):
    """Truncated cone from `start` along `direction` (radius r0 -> r1)."""
    bpy.ops.mesh.primitive_cone_add(vertices=verts, radius1=r0, radius2=r1, depth=length,
                                    end_fill_type='NGON' if cap else 'NOTHING')
    ob = bpy.context.object
    d = Vector(direction).normalized()
    ob.location = Vector(start) + d*length/2
    ob.rotation_euler = d.to_track_quat('Z', 'Y').to_euler()
    return kit.finish(ob, name, mat, 0)


def build_drive(kit, i, x, y, angle, width):
    a = math.radians(angle)
    d = Vector((math.cos(a), math.sin(a), 0))
    z = z_of(i)
    r = bell_radius(i, width)
    mouth = Vector((x, y, z))
    if i <= 3:
        # Can from inside the block to the stern face, then the bell.
        can_len = 8.0
        cone(kit, f'Engine {i} drive can', mouth - d*(r*1.2+can_len) + d*0, d, r*1.12, r*1.12, can_len, kit.steel)
        bell_start = mouth - d*r*1.25
        bell_len = r*1.25
    else:
        bell_start = mouth - d*r*1.6
        bell_len = r*1.6
        cone(kit, f'Engine {i} jet housing', bell_start - d*3.0, d, r*1.25, r*1.15, 3.0, kit.steel)
    cone(kit, f'Engine {i} aft-facing bell', bell_start, d, r*.8, r, bell_len, kit.steel, cap=False)
    bpy.ops.mesh.primitive_torus_add(major_radius=r, minor_radius=r*.09, major_segments=32, minor_segments=8)
    lip = bpy.context.object
    lip.location = mouth
    lip.rotation_euler = d.to_track_quat('Z', 'Y').to_euler()
    kit.finish(lip, f'Engine {i} mouth lip', kit.steel)
    cone(kit, f'Engine {i} dark throat', bell_start + d*.15, d, r*.78, r*.78, .1, kit.dark)
    an = bpy.data.objects.new(f'Engine {i} exhaust anchor', None)
    an.empty_display_type = 'SINGLE_ARROW'
    an.location = mouth
    an.rotation_euler = d.to_track_quat('Z', 'Y').to_euler()
    bpy.context.collection.objects.link(an)


def build(kit):
    for i, (x, y, angle, width) in enumerate(ENGINES, 1):
        build_drive(kit, i, x, y, angle, width)
