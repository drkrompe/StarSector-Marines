"""Masts, sensors and the running lights.

The sensor mast stands on WS 025, the decorative hardpoint on the command
dome's crown (X 17). Running lights follow the Legion's stated Hegemony
practice so a formation reads as one navy: RED marks extremities and
obstructions (stem, arm noses, leg tips, mast head); WHITE is the edge
line along each arm's outer deck edge, plus a white stern light on the
tail. Blink: 18% duty, as C1's. Lights are small emissive bodies seated by
ray casts against every mesh built so far; LIGHTS holds name, position,
colour, role and blink for the manifest.
"""
import math
import bpy
from mathutils import Vector
from mathutils.bvhtree import BVHTree
from os_kit import material

LIGHTS = []
DUTY = .18
RED, WHITE = (1.0, .12, .06), (1.0, .95, .88)
_TREES = []


def _scene_trees():
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
    o, d = Vector(origin), Vector(direction).normalized()
    best = None
    for t in _TREES:
        p, n, _, dist = t.ray_cast(o, d)
        if p is not None and (best is None or dist < best[0]):
            best = (dist, p, -n if n.dot(d) > 0 else n)
    return best


def _light(kit, name, point, normal, colour, role, period, phase, mats):
    loc = Vector(point)+Vector(normal)*.25
    bpy.ops.mesh.primitive_ico_sphere_add(subdivisions=1, radius=.55, location=loc)
    ob = bpy.context.object
    kit.finish(ob, name, mats['red' if colour == RED else 'white'])
    LIGHTS.append({'name': name, 'position': [round(v, 2) for v in loc],
                   'colour': 'red' if colour == RED else 'white', 'rgb': list(colour), 'role': role,
                   'blink': {'period_s': period, 'phase_s': round(phase, 2), 'duty': DUTY}})


def _drop(kit, name, x, y, colour, role, period, phase, mats):
    hit = _cast((x, y, 150), (0, 0, -1))
    if hit:
        _light(kit, name, hit[1], (0, 0, 1), colour, role, period, phase, mats)


def _from(kit, name, origin, direction, colour, role, period, phase, mats):
    hit = _cast(origin, direction)
    if hit:
        _light(kit, name, hit[1], hit[2], colour, role, period, phase, mats)


def _strut(kit, name, p0, p1, radius):
    """A round bar from p0 to p1."""
    a, b = Vector(p0), Vector(p1)
    ob = kit.cylinder(name, tuple((a+b)/2), radius, (b-a).length, kit.steel, vertices=6, bevel=0)
    ob.rotation_euler = (b-a).to_track_quat('Z', 'Y').to_euler()
    return ob


def build(kit):
    LIGHTS.clear()
    mats = {'red': material('Running light red', RED, 0, .4, emission=RED, strength=40.0),
            'white': material('Running light white', WHITE, 0, .4, emission=WHITE, strength=30.0)}
    _scene_trees()
    # Sensor mast on WS 025: a drum, a tripod, a platform with the search
    # radar's flat array, the mast proper with its crossbar, and a cap that
    # carries the mast-head light.
    hit = kit.surface(17, 0, top=150)
    z0 = hit[0].z if hit else 38.0
    kit.cylinder('Fitting command dome sensor drum', (17, 0, z0+1.0), 2.4, 2.4, kit.steel, vertices=10)
    kit.cylinder('Fitting command dome sensor mast', (17, 0, z0+7.0), .55, 11.0, kit.steel, vertices=8, bevel=0)
    kit.box('Fitting command dome sensor yard', (17, 0, z0+10.5), (1.0, 9.0, .6), kit.steel, bevel=.1)
    for k in range(3):
        a = math.radians(180+120*k)
        _strut(kit, f'Fitting command dome sensor tripod leg {k+1}',
               (17+2.6*math.cos(a), 2.6*math.sin(a), z0+2.0), (17+.5*math.cos(a), .5*math.sin(a), z0+6.4), .32)
    kit.cylinder('Fitting command dome sensor platform', (17, 0, z0+6.4), 2.0, .5, kit.steel, vertices=10, bevel=.05)
    kit.box('Fitting command dome radar array', (16.0, 0, z0+8.2), (.6, 7.0, 2.2), kit.steel, bevel=.08)
    kit.cylinder('Fitting command dome sensor mast cap', (17, 0, z0+12.7), .8, .8, kit.steel, vertices=8, bevel=.05)
    # Sensor pods on the arch's legs (the sprite's two pale circles at X 98, Y +-9.5), each with a whip.
    for side, s in (('port', 1), ('starboard', -1)):
        top = _cast((98, s*9.5, 150), (0, 0, -1))
        zp = top[1].z if top else 31.0
        kit.cylinder(f'Fitting arch sensor pod {side}', (98, s*9.5, zp+.7), 2.3, 1.6, kit.bone, vertices=14,
                     bevel=.1)
        bpy.ops.mesh.primitive_uv_sphere_add(segments=14, ring_count=6, radius=1.7, location=(98, s*9.5, zp+1.5))
        dome = bpy.context.object
        dome.scale.z = .7
        kit.finish(dome, f'Fitting arch sensor pod {side} dome', kit.bone)
        kit.cylinder(f'Fitting arch antenna {side}', (98, s*11.2, zp+5.0), .22, 8.0, kit.steel, vertices=6, bevel=0)
    # Aft dome: a squat radome and two comm dishes on short pedestals, facing outboard.
    hit = kit.surface(-31, 0, top=150)
    za = hit[0].z if hit else 33.0
    kit.cylinder('Fitting aft dome radome pedestal', (-31, 0, za+.8), 1.6, 1.8, kit.steel, vertices=10)
    bpy.ops.mesh.primitive_uv_sphere_add(segments=16, ring_count=8, radius=3.2, location=(-31, 0, za+3.4))
    kit.finish(bpy.context.object, 'Fitting aft dome radome', kit.bone)
    for side, s in (('port', 1), ('starboard', -1)):
        top = _cast((-40, s*8.5, 150), (0, 0, -1))
        zd = top[1].z if top else 30.0
        kit.cylinder(f'Fitting aft dome comm dish {side} pedestal', (-40, s*8.5, zd+1.0), .5, 2.6, kit.steel,
                     vertices=8, bevel=0)
        bpy.ops.mesh.primitive_cone_add(vertices=14, radius1=.6, radius2=2.3, depth=1.0,
                                        location=(-40, s*8.9, zd+2.6))
        dish = bpy.context.object
        dish.rotation_euler = (s*math.radians(-70), 0, 0)
        kit.finish(dish, f'Fitting aft dome comm dish {side}', kit.bone)

    _scene_trees()
    _from(kit, 'Light stem 1', (260, 0, 2), (-1, 0, 0), RED, 'stem', 2.0, 0.0, mats)
    _drop(kit, 'Light mast head 1', 17, 0, RED, 'mast head', 2.0, 1.0, mats)
    for side, s in (('port', 1), ('starboard', -1)):
        _drop(kit, f'Light {side} arm nose 1', 210, s*82, RED, 'arm nose', 2.0, 0.0 if s > 0 else .3, mats)
        _drop(kit, f'Light {side} leg tip 1', -122, s*56, RED, 'leg tip', 2.0, 1.0, mats)
        _drop(kit, f'Light {side} quarter 1', -66, s*100, RED, 'stern quarter', 2.0, 1.0, mats)
        for k, (x, y) in enumerate(((180, 92), (140, 104), (100, 124), (60, 126), (20, 130), (-10, 120)), 1):
            _drop(kit, f'Light {side} arm edge {k}', x, s*(y-2.5), WHITE, 'edge line', 3.0, .35*k, mats)
    _from(kit, 'Light stern 1', (-200, 0, 8), (1, 0, 0), WHITE, 'stern', 3.0, 1.5, mats)


def records():
    return [dict(l) for l in LIGHTS]
