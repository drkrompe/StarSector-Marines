"""A mining laser at work: a thin beam from a drone's emitter to its contact point, and the cut where it
touches (S8-S9 of the Asharu battle, the Borers).

Owner and coordinator on r10's k4 (2026-10-22): the kit beam (`swfx_beams`, built for turret weapons)
drew "stubby orange cones that end in orbs". A mining laser is:

- **the beam:** thin and intense, the same width along its length: a white-hot core inside a soft
  orange halo, both additive, flickering along the line;
- **the cut:** where it touches, a small jagged flash (`swfx_burst.lobed_mesh`, flattened against
  the hull and leaning out of it), molten sparks spraying off the surface, and a scorch glow pressed
  onto the plating, irregular and dull red. Nothing round;
- **light:** a warm light at the cut, and one at the emitter, so the drone's own hull shows lit by
  its laser.

Plain Blender: the caller passes world-space points and its collections; nothing is keyed.
"""
import math
import random

import bpy
from mathutils import Matrix, Vector

import swfx_burst
import swfx_entry as se

PREFIX = 'SWFX mining'
CORE, FRINGE = (255, 155, 155), (255, 0, 0)     # mininglaser.wpn coreColor, fringeColor


def _beam_material(name, rgb, strength, soft, cell_m):
    """Additive, brightest through the beam's middle and soft at its sides (soft: the power on how
    squarely the surface faces the camera), flickering along it."""
    name = '%s %.1f %.1f' % (name, strength, soft)
    mat = bpy.data.materials.get(name)
    if mat:
        return mat
    mat, nt = se._new_material(name)
    lw = nt.nodes.new('ShaderNodeLayerWeight')
    lw.inputs['Blend'].default_value = .5
    nz, _ = se._noise(nt, cell_m, detail=3.0)
    flick = se._math(nt, 'ADD', .55, se._math(nt, 'MULTIPLY', nz, .9))
    glow = se._math(nt, 'MULTIPLY', se._math(nt, 'MULTIPLY', se._math(nt, 'POWER', lw.outputs['Facing'], soft), flick),
                    strength)
    rgb_node = nt.nodes.new('ShaderNodeRGB')
    rgb_node.outputs[0].default_value = (*rgb, 1)
    se._additive(nt, rgb_node.outputs[0], glow)
    return mat


def _hot_material(name, strength, heat):
    """Additive blackbody: the flash (heat near 1, white-yellow) or the scorch (low, dull red)."""
    name = '%s %.1f %.2f' % (name, strength, heat)
    mat = bpy.data.materials.get(name)
    if mat:
        return mat
    mat, nt = se._new_material(name)
    lw = nt.nodes.new('ShaderNodeLayerWeight')
    lw.inputs['Blend'].default_value = .5
    glow = se._math(nt, 'MULTIPLY', se._math(nt, 'POWER', lw.outputs['Facing'], 1.2), strength)
    se._additive(nt, se._blackbody(nt, heat), glow)
    return mat


def _placed(obj, at, axis, scale):
    """obj at `at`, its local +X along axis, scaled (x along axis, y and z across)."""
    x = Vector(axis).normalized()
    ref = Vector((0, 0, 1)) if abs(x.z) < .9 else Vector((0, 1, 0))
    y = x.cross(ref).normalized()
    z = x.cross(y)
    obj.matrix_world = Matrix.Translation(at) @ Matrix((x, y, z)).transposed().to_4x4() @ Matrix.Diagonal((*scale, 1))
    return obj


def _light(name, coll, at, energy, rgb, radius):
    ld = bpy.data.lights.new(name, 'POINT')
    ld.energy, ld.color, ld.shadow_soft_size = energy, rgb, radius
    lo = bpy.data.objects.new(name, ld)
    lo.location = at
    coll.objects.link(lo)
    return lo


def laser(name, emitter, contact, normal, coll, lights=None, seed=0, width_m=.3, glow=1.0, sparks=22):
    """A mining laser from emitter to contact (world space, metres), cutting a surface whose outward
    normal at the contact is `normal`. width_m: the beam's core radius; the halo is four times it.
    lights: the collection its two lights go in (the one that lights hulls), or None for none."""
    rnd = random.Random(seed)
    a, b = Vector(emitter), Vector(contact)
    n = Vector(normal).normalized()
    d = b - a
    length = d.length
    if length < 1e-3:
        return
    pts = [a.lerp(b, i / 15) for i in range(16)]
    us = [1 - i / 15 for i in range(16)]
    cell = max(length / 6.0, 2.0)
    # the game's own Mining Laser (mininglaser.wpn): a pink core (255,155,155) in a red fringe
    # (255,0,0), not the white-hot rod it was, which at quarter size clipped to a solid white line
    # and read as some other weapon's beam (owner, 2026-10-23, S8-S9: "wonky laser effects")
    se._tube(f'{name} core', pts, [width_m] * 16, us, coll,
             _beam_material(f'{PREFIX} core', swfx_burst.srgb(CORE), 14.0 * glow, .6, cell), segments=10)
    se._tube(f'{name} halo', pts, [width_m * 4.0] * 16, us, coll,
             _beam_material(f'{PREFIX} halo', swfx_burst.srgb(FRINGE), 4.0 * glow, 2.2, cell), segments=14)
    # the cut: a small jagged flash leaning out of the plating, flattened against it
    flash_me, _ = swfx_burst.lobed_mesh(f'{name} flash', _hot_material(f'{PREFIX} flash', 30.0 * glow, .92), rnd,
                                        lobes=(3, 5), spikes=(3, 6), lean=1.0)
    fl = bpy.data.objects.new(f'{name} flash', flash_me)
    coll.objects.link(fl)
    s = width_m * 6.0
    _placed(fl, b + n * s * .15, n, (s * .6, s, s))
    fl.visible_shadow = False
    # the scorch: a dull red, irregular glow pressed onto the plating round the cut
    sc_me, _ = swfx_burst.lobed_mesh(f'{name} scorch', _hot_material(f'{PREFIX} scorch', 3.0 * glow, .12), rnd,
                                     lobes=(5, 8), spikes=(0, 1), lean=0.0)
    sc = bpy.data.objects.new(f'{name} scorch', sc_me)
    coll.objects.link(sc)
    s = width_m * 13.0
    _placed(sc, b + n * width_m * .3, n, (s * .05, s, s * rnd.uniform(.6, 1.0)))
    sc.visible_shadow = False
    # molten sparks: thrown off the surface, most of them low along it, cooling as they fly
    ref = Vector((0, 0, 1)) if abs(n.z) < .9 else Vector((0, 1, 0))
    t1 = n.cross(ref).normalized()
    t2 = n.cross(t1)
    spark_mat = se._flame_material(f'{PREFIX} spark', 22.0 * glow, max(width_m * 4, .5))
    for k in range(sparks):
        ang = rnd.uniform(0, 2 * math.pi)
        out = (t1 * math.cos(ang) + t2 * math.sin(ang)) * rnd.uniform(.6, 1.0) + n * rnd.uniform(.15, .9)
        ln = width_m * rnd.uniform(4.0, 18.0)
        bend = (t1 * rnd.gauss(0, 1) + t2 * rnd.gauss(0, 1)) * ln * .12
        p0 = b + n * width_m * .5
        p1 = p0 + out.normalized() * ln * .5 + bend * .3
        p2 = p0 + out.normalized() * ln + bend
        r = width_m * rnd.uniform(.05, .12)
        se._tube(f'{name} spark {k}', [p2, p1, p0], [r * .4, r * .8, r], [1.0, .5, 0.0], coll, spark_mat,
                 segments=6)
    if lights is not None:
        _light(f'{name} cut light', lights, b + n * width_m * 4, 6.0e3 * glow, (1.0, .55, .25), width_m * 2)
        _light(f'{name} emitter light', lights, a + d.normalized() * width_m * 3, 1.5e3 * glow, (1.0, .6, .3),
               width_m * 2)
