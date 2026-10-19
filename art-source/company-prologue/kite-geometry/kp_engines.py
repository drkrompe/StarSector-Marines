"""Kite (P) drive units, exactly at kite.ship's eight engine slots.

The sprite shows four kinds of drive, each a whole unit rather than a nozzle
in the hull:

- Engines 2-3, the main drives (X -27, Y +-13, width 10): two long pods
  either side of the tail cone, running forward under the aft hump;
- Engines 1 and 4 (X -21, Y +-28, width 8): nacelles slung through the wing's
  trailing edge where it steps in round them;
- Engines 7-8 (X -22, Y -20 / +21, width 6, angles 185 / 175): small cans in
  the tail between the main pods and the nacelles, toed a few degrees out;
- Engines 5-6 (X -16, Y -36 / +36, width 6, angles 210 / 150): the
  maneuvering jets (system maneuveringjets) at the wing tips' trailing
  corners, canted thirty degrees outboard.

Every unit is built in a local frame (exhaust along local +X, mouth plane at
the origin), then turned to its slot angle and moved to its slot, so the
mouth stays exactly at the slot. The bell interior ends at a dark injector
face inside the can, so nothing flat shows inside a bell.

Names follow the Second Wind's: 'Engine {i} aft-facing bell', 'Engine {i}
mouth lip', 'Engine {i} dark throat', plus an 'Engine {i} exhaust anchor'
empty at the mouth pointing along the exhaust (its local +Z).
"""
import math
import bmesh
import bpy
from mathutils import Matrix, Vector

import kp_kit

# (x, y, angle, width) from kite.ship, in slot order.
ENGINES = [(-21, 28, 180, 8), (-27, 13, 180, 10), (-27, -13, 180, 10), (-21, -28, 180, 8),
           (-16, -36, 210, 6), (-16, 36, 150, 6), (-22, -20, 185, 6), (-22, 21, 175, 6)]
ROLES = ['port wing nacelle', 'port main drive', 'starboard main drive', 'starboard wing nacelle',
         'starboard maneuvering jet', 'port maneuvering jet', 'starboard tail drive', 'port tail drive']
Z = {1: -1.6, 2: -.5, 3: -.5, 4: -1.6, 5: -1.8, 6: -1.8, 7: -1.0, 8: -1.0}
# Can length forward of the mouth, in units: how far each unit runs into the hull.
CAN_LENGTH = {1: 12.0, 2: 15.0, 3: 15.0, 4: 12.0, 5: 6.0, 6: 6.0, 7: 7.0, 8: 7.0}
THROAT_R = .6


def bell_radius(i, width):
    """Mouth radius: the slot width read as the bell's mouth diameter, less a lip."""
    return width*.45


def z_of(i):
    return Z[i]


def _flat(r, a, sides):
    if not sides:
        return r
    s = math.tau/sides
    return r/math.cos(((a+s/2) % s)-s/2)


def revolve(kit, name, prof, mat, n=36, closed=False, inward=False, sides=None, bevel=0):
    """Surface of revolution about local X through an (x, radius) profile
    (copied from hm_engines.revolve)."""
    v = []
    for x, r_ in prof:
        for t in range(n):
            a = t*math.tau/n
            rr = _flat(r_, a, sides)
            v.append((x, rr*math.cos(a), rr*math.sin(a)))
    m = len(prof)
    rows = m if closed else m-1
    f = [(k*n+t, k*n+(t+1) % n, ((k+1) % m)*n+(t+1) % n, ((k+1) % m)*n+t)
         for k in range(rows) for t in range(n)]
    ob = kit.mesh(name, v, f, mat, bevel=bevel)
    bm = bmesh.new()
    bm.from_mesh(ob.data)
    if closed:
        bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    else:
        out = 0
        for fc in bm.faces:
            c = fc.calc_center_median()
            out += fc.normal.dot(Vector((0, c.y, c.z)))*fc.calc_area()
        if (out > 0) == inward:
            bmesh.ops.reverse_faces(bm, faces=bm.faces)
    bm.to_mesh(ob.data)
    bm.free()
    return ob


def _mats():
    m = bpy.data.materials
    liner = m.get('Drive bell liner') or kp_kit.material('Drive bell liner', (.085, .09, .095), .75, .42)
    return liner


def drive(kit, i, r):
    """One drive unit in the local frame: faceted can, bell, throat, lip."""
    liner = _mats()
    L = 1.5*r                       # bell depth
    rt = THROAT_R*r
    rho = lambda u: r-(r-rt)*(min(u, L)/L)**2
    can_r = r*1.22
    length = CAN_LENGTH[i]
    # The can: twelve flats, a chamfered aft rim round the mouth, running forward into the hull.
    revolve(kit, f'Engine {i} drive can', [(.15, r+.12), (.15, can_r-.25), (-.35, can_r), (-length, can_r),
                                           (-length, r*.5)], kit.steel, 48, True, sides=12, bevel=.06)
    revolve(kit, f'Engine {i} aft-facing bell', [(-u, rho(u)) for u in [L*q/8 for q in range(9)]] + [(-(L+.3), rt*1.05)],
            liner, inward=True)
    kit.cylinder(f'Engine {i} dark throat', (-(L+.3), 0, 0), rt*1.1, .08, kit.dark, 'X', 36, 0)
    bpy.ops.mesh.primitive_torus_add(major_radius=r+.08, minor_radius=.14*r/4.5+.06, major_segments=48,
                                     minor_segments=8, location=(0, 0, 0), rotation=(0, math.pi/2, 0))
    kit.finish(bpy.context.object, f'Engine {i} mouth lip', kit.steel)
    # Two banding rings on the can.
    for k, x in enumerate((-.3*length, -.6*length)):
        revolve(kit, f'Engine {i} can band {k+1}', [(x+.35, can_r-.05), (x+.35, can_r+.18), (x-.35, can_r+.18),
                                                    (x-.35, can_r-.05)], kit.steel, 48, True, sides=12)


def build_drive(kit, i, x, y, angle, width):
    r = bell_radius(i, width)
    before = set(o.name for o in bpy.data.objects)
    drive(kit, i, r)
    M = Matrix.Translation((x, y, z_of(i))) @ Matrix.Rotation(math.radians(angle), 4, 'Z')
    for o in bpy.data.objects:
        if o.name not in before:
            o.matrix_basis = M @ o.matrix_basis
    a = math.radians(angle)
    d = Vector((math.cos(a), math.sin(a), 0))
    an = bpy.data.objects.new(f'Engine {i} exhaust anchor', None)
    an.empty_display_type = 'SINGLE_ARROW'
    an.empty_display_size = r
    an.location = (x, y, z_of(i))
    an.rotation_euler = d.to_track_quat('Z', 'Y').to_euler()
    bpy.context.collection.objects.link(an)
    an['engine'] = i
    an['radius'] = r
    an['throat_depth'] = 1.5*r+.3
    an['throat_radius'] = THROAT_R*r*1.1


def build(kit):
    for i, (x, y, angle, width) in enumerate(ENGINES, 1):
        build_drive(kit, i, x, y, angle, width)
