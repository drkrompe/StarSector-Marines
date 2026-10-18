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
        _TREES.append((ob.name, BVHTree.FromPolygons([ev.matrix_world @ v.co for v in me.vertices],
                                                     [tuple(p.vertices) for p in me.polygons])))
        ev.to_mesh_clear()


def _cast(origin, direction, hull_only=False, only=None):
    """Nearest hit along a ray: (distance, point, normal facing the ray). hull_only skips
    trainable weapons and fittings, so a light is never seated on a barrel or a mast."""
    o, d = Vector(origin), Vector(direction).normalized()
    best = None
    for name, t in _TREES:
        if hull_only and name.split(' ')[0] not in ('Hull', 'Armour', 'Bridge', 'Engine'):
            continue
        if only is not None and not only(name):
            continue
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
        # Arm nose: the arm's foremost point (the TPC casemate's cheek, or the nose itself),
        # found by horizontal casts aft over the nose on a grid mirrored port/starboard.
        _foremost(kit, f'Light {side} arm nose 1', s, RED, 'arm nose', 2.0, 0.0 if s > 0 else .3, mats)
        _extreme(kit, f'Light {side} leg tip 1', (-150, s*56), (1, 0), 50, RED, 'leg tip', 2.0, 1.0, mats)
        _extreme(kit, f'Light {side} quarter 1', (-66, s*125), (0, -s), 40, RED, 'stern quarter', 2.0, 1.0, mats)
        for k, x in enumerate(EDGE_X, 1):
            _beam(kit, f'Light {side} arm edge {k}', x, s, WHITE, 'edge line', 3.0, .35*k, mats)
    _from(kit, 'Light stern 1', (-200, 0, 8), (1, 0, 0), WHITE, 'stern', 3.0, 1.5, mats)


EDGE_X = (180, 140, 100, 60, 20, -6)     # stations of the white edge line along each arm (the last
                                        # aft of the WS 010/011 flak sponson, not under it)
EDGE_DEG = 35.0                         # the deck edge is the arm's extreme point this far off vertical


def _support(u, c, span_a, span_b, step=.5, only=None):
    """The hull's extreme point in direction u near c: rays cast along -u from a grid
    (span_a x span_b, either may be 0) on a plane perpendicular to u, keeping the hit that
    lies furthest along u. That point is on the silhouette seen from u, so a light seated
    there shows from that side, from above and abeam."""
    u = Vector(u).normalized()
    e1 = u.cross(Vector((1, 0, 0)) if abs(u.x) < .9 else Vector((0, 0, 1))).normalized()
    e2 = u.cross(e1).normalized()
    best = None
    na, nb = int(span_a/step), int(span_b/step)
    for i in range(-na, na+1):
        for j in range(-nb, nb+1):
            o = Vector(c)+u*300.0+e1*(i*step)+e2*(j*step)
            hit = _cast(o, -u, hull_only=True, only=only)
            if hit and (best is None or hit[1].dot(u) > best[1].dot(u)+1e-3):
                best = hit
    return best


def _beam(kit, name, x, s, colour, role, period, phase, mats):
    """A light on the arm's outer deck edge at station x: the extreme point of the arm's own
    section (its gallery, forearm and their belts; never the broadside fin, a turret or the
    stern) outboard-and-up at EDGE_DEG from vertical, i.e. the chine where the gun deck meets
    the outer side. One rule at every station, so the six lights make one clean line along
    the arm whatever its roll."""
    side = 'port' if s > 0 else 'starboard'
    mine = lambda n: n in (f'Hull {side} arm gallery', f'Hull {side} arm') or n.startswith(f'Armour {side} arm belt')
    a = math.radians(EDGE_DEG)
    u = Vector((0, s*math.sin(a), math.cos(a)))
    e1 = u.cross(Vector((1, 0, 0))).normalized()
    best = None
    for k in range(-160, 161):
        o = Vector((x, s*95.0, -5.0))+u*300.0+e1*(k*.5)
        hit = _cast(o, -u, only=mine)
        if hit and (best is None or hit[1].dot(u) > best[1].dot(u)+1e-3):
            best = hit
    if best:
        _light(kit, name, best[1], best[2], colour, role, period, phase, mats)


def _foremost(kit, name, s, colour, role, period, phase, mats):
    """A light at the arm's nose: the extreme point of the arm's own bodies forward-and-up
    (from ahead, 30 deg above), so it marks the nose's top from ahead, abeam and above."""
    side = 'port' if s > 0 else 'starboard'
    mine = lambda n: n.startswith((f'Hull {side} arm', f'Armour {side} arm', f'Armour {side} nose'))
    best = _support((1, 0, .58), (205, s*80.0, -5.0), 24, 24, step=1.0, only=mine)
    if best:
        _light(kit, name, best[1], best[2], colour, role, period, phase, mats)


def _edge(kit, name, start, step_dir, reach, colour, role, period, phase, mats):
    """A light on a deck's outer edge: walk outward from `start` (on the deck) off the hull,
    and set the light on the outermost point that still faces up (not over the side, not on the
    bilge below the deck edge). Survives the arm being tilted or reshaped."""
    x, y = start
    dx, dy = step_dir
    hits = []
    t = 0.0
    while t <= reach:
        hit = _cast((x+dx*t, y+dy*t, 150), (0, 0, -1))
        if hit:
            hits.append(hit)
        t += .5
    if not hits:
        return
    floor = max(h[1].z for h in hits)-6.0
    up = [h for h in hits if h[2].z >= .45 and h[1].z >= floor]
    best = up[-1] if up else hits[0]
    _light(kit, name, best[1], best[2], colour, role, period, phase, mats)


def _extreme(kit, name, start, step_dir, reach, colour, role, period, phase, mats, inset=1.2):
    """A light at the hull's extremity: drop rays from `start` (off the hull) stepping along
    `step_dir` until one lands on the hull, then set the light `inset` further in, on the deck
    edge's top rather than over the side. Survives the arms or legs being moved or reshaped."""
    x, y = start
    dx, dy = step_dir
    t = 0.0
    while t <= reach:
        if _cast((x+dx*t, y+dy*t, 150), (0, 0, -1)):
            hit = _cast((x+dx*(t+inset), y+dy*(t+inset), 150), (0, 0, -1)) or \
                _cast((x+dx*t, y+dy*t, 150), (0, 0, -1))
            _light(kit, name, hit[1], hit[2], colour, role, period, phase, mats)
            return
        t += .5


def records():
    return [dict(l) for l in LIGHTS]
