"""Masts, sensors, antennas and running lights.

Fittings stand only where legion_xiv.png shows a small light protrusion:
- forward mast pod (sprite X 64-77, Y +-115..123): a pod at the forward
  hangar box's outer corner with two pale prongs at X 75-76. The forward
  flight deck well runs out to Y 120 under open sky there, so the pod is on the
  rim outboard of the well and the two masts stand on it (X 73.5 and 69.5)
  rather than over the well;
- aft sensor stub (X -92..-96, Y +-125..131): a nub on the sponson's tail with
  a pale probe pointing aft;
- flank sensor array (X -2..-8, Y +-124.5..131): a low slatted block outboard
  of WS 014/015, kept below their barrels;
- sensor dome (X -1..-15, Y +-59..73): the white grilled disc in the machinery
  bay beside the inner belt, a radome on a drum.

Running lights (c2-layout.md: red and white pinpoints on the flank and deck,
18% blink duty as C1's). Convention used here, stated as Hegemony practice:
RED marks extremities and obstructions (bow-blade tips, sponson tail corners,
mast heads, the forward pod); WHITE is the edge line, a row of position lights
along each sponson's deck edge and along its flank strake, plus a white stern
light on the tail spike. Both colours therefore appear on the starboard flank
C2 sees. Lights are small emissive bodies seated by ray casts against every
mesh already built (so other modules' strakes and greebles cannot bury them);
LIGHTS holds name, position, colour, role and blink for the manifest.
"""
import math
import bpy
from mathutils import Vector
from mathutils.bvhtree import BVHTree
from lg_kit import material

LIGHTS = []                   # filled by build(); see records()
DUTY = .18
RED, WHITE = (1.0, .12, .06), (1.0, .95, .88)


_TREES = []


def _scene_trees():
    """World-space BVH trees of every mesh built so far (running lights excluded), built once."""
    bpy.context.view_layer.update()
    dg = bpy.context.evaluated_depsgraph_get()
    _TREES.clear()
    for ob in bpy.context.scene.objects:
        if ob.type != 'MESH' or ob.name.startswith('Light '):
            continue
        ev = ob.evaluated_get(dg)
        me = ev.to_mesh()
        _TREES.append(BVHTree.FromPolygons([ev.matrix_world @ v.co for v in me.vertices],
                                           [tuple(p.vertices) for p in me.polygons]))
        ev.to_mesh_clear()


def _cast(origin, direction):
    """Nearest hit of a ray against the meshes in _TREES."""
    o_w, d_w = Vector(origin), Vector(direction).normalized()
    best = None
    for t in _TREES:
        p, n, _, dist = t.ray_cast(o_w, d_w)
        if p is not None and (best is None or dist < best[0]):
            if n.dot(d_w) > 0:
                n = -n
            best = (dist, p, n)
    return best


def _seat(kit, x, y, fallback=10.0):
    hit = kit.surface(x, y, top=120)
    return hit[0].z if hit else fallback


def _light(kit, name, point, normal, colour, role, period, phase, mats):
    loc = Vector(point)+Vector(normal)*.2
    bpy.ops.mesh.primitive_ico_sphere_add(subdivisions=1, radius=.42, location=loc)
    ob = bpy.context.object
    kit.finish(ob, name, mats['red' if colour == RED else 'white'])
    LIGHTS.append({'name': name, 'position': [round(v, 2) for v in loc],
                   'colour': 'red' if colour == RED else 'white', 'rgb': list(colour), 'role': role,
                   'blink': {'period_s': period, 'phase_s': round(phase, 2), 'duty': DUTY}})
    return ob


def _drop_light(kit, name, x, y, colour, role, period, phase, mats):
    hit = _cast((x, y, 120), (0, 0, -1))
    if hit:
        _light(kit, name, hit[1], (0, 0, 1), colour, role, period, phase, mats)


def _flank_light(kit, name, x, sy, z, colour, role, period, phase, mats):
    hit = _cast((x, sy*400, z), (0, -sy, 0))
    if hit:
        _light(kit, name, hit[1], hit[2], colour, role, period, phase, mats)


def _mast(kit, name, x, y, z0, height, radius, mat):
    return kit.cylinder(name, (x, y, z0+height/2), radius, height, mat, vertices=8, bevel=0)


def build(kit):
    LIGHTS.clear()
    mats = {'red': bpy.data.materials.get('Running light red') or
            material('Running light red', (.1, .01, .01), 0, .4, emission=RED, strength=12.0),
            'white': bpy.data.materials.get('Running light white') or
            material('Running light white', (.2, .2, .2), 0, .4, emission=WHITE, strength=10.0)}
    for side, sy in (('port', 1), ('starboard', -1)):
        # Forward mast pod on the rim outboard of the forward flight-deck well.
        rim = _seat(kit, 69, sy*121.5)
        top = max(rim, _seat(kit, 66, sy*121))+2.5
        kit.box(f'Fitting {side} forward mast pod', (68.5, sy*121.7, (rim-3.5+top)/2), (11, 2.6, top-rim+3.5),
                kit.plate, bevel=.2)
        _mast(kit, f'Fitting {side} forward mast 1', 73.5, sy*121.2, top, 11.0, .45, kit.steel)
        kit.box(f'Fitting {side} forward mast 1 yard', (73.5, sy*121.2, top+8.6), (.4, 4.2, .4), kit.steel, bevel=0)
        kit.cylinder(f'Fitting {side} forward mast 1 sensor head', (73.5, sy*121.2, top+11.3), .9, 1.0, kit.bone,
                     vertices=10, bevel=.1)
        _mast(kit, f'Fitting {side} forward mast 2', 69.5, sy*122.4, top, 8.0, .3, kit.steel)
        _light(kit, f'Light {side} mast head 1', (73.5, sy*121.2, top+11.8), (0, 0, 1), RED, 'mast head (obstruction)',
               2.4, 0.0 if sy > 0 else 1.2, mats)

        # Aft sensor stub on the sponson's tail nub, probe pointing aft.
        z = _seat(kit, -94, sy*128.5)
        kit.box(f'Fitting {side} aft sensor stub', (-94.0, sy*128.5, z+.4), (3.2, 3.6, 2.0), kit.steel, bevel=.15)
        kit.cylinder(f'Fitting {side} aft sensor probe', (-97.5, sy*128.5, z+.9), .3, 5.0, kit.bone, axis='X',
                     vertices=8, bevel=0)

        # Flank sensor array outboard of WS 014/015: a low block of transverse slats.
        z = _seat(kit, -5, sy*127.8)
        kit.box(f'Fitting {side} flank sensor array', (-5.0, sy*127.8, z+.1), (6.4, 6.4, 1.4), kit.steel, bevel=.12)
        for k, x in enumerate((-7.0, -5.0, -3.0), 1):
            kit.box(f'Fitting {side} flank sensor array slat {k}', (x, sy*127.8, z+.9), (.8, 5.6, .9), kit.bone,
                    bevel=.08)

        # Sensor dome in the machinery bay beside the inner belt.
        z = _seat(kit, -8, sy*66)
        kit.cylinder(f'Fitting {side} sensor dome drum', (-8.0, sy*66.0, z+.4), 7.0, 2.2, kit.steel, vertices=24,
                     bevel=.15)
        bpy.ops.mesh.primitive_uv_sphere_add(segments=24, ring_count=10, radius=6.2, location=(-8.0, sy*66.0, z+1.5))
        dome = bpy.context.object
        dome.scale = (1, 1, .5)
        bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
        kit.finish(dome, f'Fitting {side} sensor dome', kit.bone)
        for k, dx in enumerate((4.6, 5.2, 5.8), 1):   # the grille the sprite shows on its aft face
            zs = z+1.5+.5*(6.2**2-dx**2)**.5
            kit.box(f'Fitting {side} sensor dome grille {k}', (-8.0-dx, sy*66.0, zs), (.45, 5.0-1.2*k, .8), kit.steel,
                    bevel=0)

    # Running lights, after every structure they sit on (masts done above).
    _scene_trees()
    for side, sy in (('port', 1), ('starboard', -1)):
        ph = 0.0 if sy > 0 else 1.2
        _drop_light(kit, f'Light {side} bow blade tip 1', 145.5, sy*33.5, RED, 'bow-blade tip (extremity)', 2.4, ph+.3, mats)
        _drop_light(kit, f'Light {side} sponson tail 1', -69.0, sy*144.5, RED, 'sponson tail corner (extremity)', 2.4,
                    ph+.6, mats)
        _drop_light(kit, f'Light {side} forward pod 1', 64.8, sy*122.0, RED, 'forward hangar corner (obstruction)', 2.4,
                    ph+.9, mats)
        for k, (x, y) in enumerate([(46, 126.5), (20, 135.5), (-20, 136.5), (-45, 139.5)], 1):
            _drop_light(kit, f'Light {side} deck edge {k}', x, sy*y, WHITE, 'deck-edge position light', 1.6,
                        (k-1)*.4, mats)
        for k, x in enumerate((40, 10, -25, -48), 1):
            _flank_light(kit, f'Light {side} flank {k}', x, sy, 4.0, WHITE, 'flank strake position light', 1.6,
                         .2+(k-1)*.4, mats)
        _flank_light(kit, f'Light {side} flank tail 1', -70.0, sy, 4.0, RED, 'sponson flank extremity', 2.4, ph+.6, mats)
    _drop_light(kit, 'Light centre tail spike 1', -133.0, 0.0, WHITE, 'stern light', 2.4, 0.0, mats)


def records():
    """Running lights for geometry.json: name, model position, colour, role and blink (18% duty)."""
    return [dict(l) for l in LIGHTS]
