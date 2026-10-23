"""Build checks for the kit's effects: no orbs (owner, 2026-10-21 and 2026-10-23).

No projectile, blast or impact may read as a round ball of light at any distance: rounds are
streaks along their flight, bolts jagged cores, missiles a body and a motor streak, blasts torn
lobes. Two helpers a scene calls after its kit builds:

    import swfx_lint
    swfx_lint.quiet_lights(objects)                          # every point light off the camera
    rows = swfx_lint.orbs(scene, frames=[120, 180], camera=cam)   # anything that would read as an orb
    for r in rows: print('ORB', r)

quiet_lights: a point light with a radius (shadow_soft_size) is a sphere the camera sees, and the
kit's flash, impact and round lights carry one for their soft shadows; turning off camera
visibility keeps the light it throws and loses the ball. It is a scene step, not a kit change, so
a kit fingerprint (check_kit.py) does not move.

orbs: on each given frame, every emissive mesh drawn (instances included, at their world matrix)
whose name says it is an effect (EFFECT_WORDS) is measured along its own three axes. One whose
axes are all within `ratio` of each other, whose shape is round (a unit box volume, whose shader
draws the round shape inside it, or a mesh whose vertices sit at nearly one distance from its
centre; a torn lobed burst does not), and that spans more than `min_px` pixels at the camera is a
row: {frame, object, axes_m, ratio, px, why}. Emissive means an emission shader, a volume with
emission, or a blackbody.
"""
import math

import bpy
from mathutils import Vector

EFFECT_WORDS = ('round', 'bolt', 'tracer', 'missile', 'motor', 'flare', 'burst', 'impact', 'flash', 'cloud',
                'halo', 'charge', 'blast', 'flak', 'fireball', 'glow', 'shot', 'shell', 'warhead', 'pd ', 'ring',
                'trail')        # not 'plume': an engine's exhaust volume is shaped by its shader, along the jet


def quiet_lights(objects):
    """Point, spot and area lights among `objects` off the camera ray (their light unchanged).
    Returns how many were changed."""
    n = 0
    for ob in objects:
        if isinstance(ob, bpy.types.Object) and ob.type == 'LIGHT' and ob.data.type != 'SUN' and ob.visible_camera:
            ob.visible_camera = False
            n += 1
    return n


_EMISSIVE = {}


def _emissive(mat):
    """True when a material emits light (cached per material name)."""
    if mat is None or mat.node_tree is None:
        return False
    hit = _EMISSIVE.get(mat.name)
    if hit is not None:
        return hit
    hit = False
    for n in mat.node_tree.nodes:
        if n.type in ('EMISSION', 'BLACKBODY'):
            hit = True
        elif n.type in ('PRINCIPLED_VOLUME', 'BSDF_PRINCIPLED'):
            s = n.inputs.get('Emission Strength')
            if s is not None and (s.is_linked or s.default_value > 0):
                hit = True
        if hit:
            break
    _EMISSIVE[mat.name] = hit
    return hit


def _round_shape(me):
    """A mesh that renders round: a box (a volume whose shader draws a round body inside it) or a
    mesh whose vertices sit at nearly one distance from its centre, per axis (a sphere, a capsule
    of equal axes); a spiky lobed burst or a rod does not."""
    vs = me.vertices
    if len(vs) <= 8:
        return True, 'box volume'
    lo = Vector((min(v.co.x for v in vs), min(v.co.y for v in vs), min(v.co.z for v in vs)))
    hi = Vector((max(v.co.x for v in vs), max(v.co.y for v in vs), max(v.co.z for v in vs)))
    c, h = (lo + hi) / 2, (hi - lo) / 2
    h = Vector([max(x, 1e-9) for x in h])
    rs = [Vector(((v.co.x - c.x) / h.x, (v.co.y - c.y) / h.y, (v.co.z - c.z) / h.z)).length for v in vs]
    mean = sum(rs) / len(rs)
    cv = math.sqrt(sum((r - mean) ** 2 for r in rs) / len(rs)) / max(mean, 1e-9)
    return cv < .2, f'vertex radius spread {cv:.2f}'


def _effect(name):
    n = name.lower()
    return any(w in n for w in EFFECT_WORDS)


def _px(cam, scene, world_pos, size):
    """How many pixels across `size` (metres) spans at world_pos, from cam."""
    if cam is None:
        return float('inf')
    M = cam.matrix_world
    d = (M.inverted() @ world_pos)
    depth = -d.z
    if depth <= 0:
        return 0.0
    r = scene.render
    width = r.resolution_x * r.resolution_percentage / 100
    if cam.data.type == 'ORTHO':
        return size / cam.data.ortho_scale * width
    fov = cam.data.angle_x if cam.data.sensor_fit != 'VERTICAL' else cam.data.angle
    return size / (2 * depth * math.tan(fov / 2)) * width


def orbs(scene, frames=None, camera=None, ratio=1.5, min_px=4.0, limit=200):
    """Rows for every emissive effect mesh that would read as an orb (see the module doc). frames:
    the frames to look at (default the current one); camera: default the scene's."""
    cam = camera or scene.camera
    keep = scene.frame_current
    rows, shape = [], {}
    try:
        for f in (frames or [keep]):
            scene.frame_set(int(f))
            dg = bpy.context.evaluated_depsgraph_get()
            for inst in dg.object_instances:
                ob = inst.instance_object if inst.is_instance else inst.object
                if ob is None or ob.type != 'MESH':
                    continue
                orig = ob.original
                if not _effect(orig.name) or orig.hide_render:
                    continue
                if not any(_emissive(s.material) for s in orig.material_slots):
                    continue
                M = inst.matrix_world
                bb = [Vector(c) for c in ob.bound_box]
                lo = Vector([min(c[i] for c in bb) for i in range(3)])
                hi = Vector([max(c[i] for c in bb) for i in range(3)])
                axes = [(hi[i] - lo[i]) * M.col[i].xyz.length for i in range(3)]
                if min(axes) <= 0:
                    continue
                q = max(axes) / min(axes)
                if q >= ratio:
                    continue
                me = orig.data
                if me.name not in shape:
                    shape[me.name] = _round_shape(me)
                rnd, why = shape[me.name]
                if not rnd:
                    continue
                px = _px(cam, scene, M @ ((lo + hi) / 2), max(axes))
                if px < min_px:
                    continue
                rows.append(dict(frame=int(f), object=orig.name, axes_m=[round(a, 2) for a in axes],
                                 ratio=round(q, 2), px=round(px, 1), why=why))
                if len(rows) >= limit:
                    return rows
    finally:
        scene.frame_set(keep)
    return rows
