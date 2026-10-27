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

Owner on the S8-S9 final (2026-10-27): the beam was "too thick and doesn't read as a pulsing
transfer of energy", and should rake across the hull. So, behind new parameters (the old call is
unchanged):

- **halo:** the halo's radius as a multiple of the core's (4 as it was);
- **pulse:** packets of energy running down the beam from emitter to contact, the beam swelling
  and brightening as each one passes, and the flash, sparks and both lights throbbing as they
  arrive and leave;
- **trail:** the hot seam a raking beam leaves on the plating behind its contact.

Plain Blender: the caller passes world-space points and its collections; nothing is keyed. A pulsed
beam is built for one instant (pulse['t']), so a caller that rebuilds it each frame from the
frame's own time gets the same picture on that frame whatever it built before.
"""
import math
import random

import bpy
from mathutils import Matrix, Vector

import swfx_burst
import swfx_entry as se

PREFIX = 'SWFX mining'
CORE, FRINGE = (255, 155, 155), (255, 0, 0)     # mininglaser.wpn coreColor, fringeColor
# a pulsed beam's packets: their spacing and speed, how narrow (sharp), the glow between them (floor,
# of the peak) and how much they swell the core (of its width); the halo's glow between them
# (halo_floor) and its swell. Owner on r2 (2026-10-27): "pulse is good, but the pulse is too thick",
# at a core swell of 1.6 with the halo swelling and flaring alongside: now the packet is a brighter
# bead in a core that barely widens, inside a halo that holds its width and only brightens a little
PULSE = dict(wavelength_m=8.0, speed_mps=40.0, sharp=4.0, floor=.15, swell=.35, halo_floor=.6, halo_swell=0.0)


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


def pulse_packet(x, sharp=4.0):
    """The packet at x along the beam (wavelengths from the emitter less the phase): 1 on a packet, 0
    between. _packet draws the same in a shader."""
    return ((1 + math.cos(2 * math.pi * x)) * .5) ** sharp


def _packet(nt, x, sharp):
    c = se._math(nt, 'COSINE', se._math(nt, 'MULTIPLY', x, 2 * math.pi))
    return se._math(nt, 'POWER', se._math(nt, 'MULTIPLY', se._math(nt, 'ADD', c, 1.0), .5), sharp)


def _pulse_material(name, rgb, strength, soft, floor, sharp, cell_m):
    """_beam_material with packets running along it: entry_u carries the packet phase, so a packet
    is brightest where it is a whole number; floor is the glow between packets, of the peak."""
    name = '%s %.1f %.1f %.2f %.1f' % (name, strength, soft, floor, sharp)
    mat = bpy.data.materials.get(name)
    if mat:
        return mat
    mat, nt = se._new_material(name)
    lw = nt.nodes.new('ShaderNodeLayerWeight')
    lw.inputs['Blend'].default_value = .5
    at = nt.nodes.new('ShaderNodeAttribute')
    at.attribute_name = 'entry_u'
    pk = se._math(nt, 'ADD', floor, se._math(nt, 'MULTIPLY', _packet(nt, at.outputs['Fac'], sharp), 1.0 - floor))
    nz, _ = se._noise(nt, cell_m, detail=3.0)
    flick = se._math(nt, 'ADD', .8, se._math(nt, 'MULTIPLY', nz, .4))
    glow = se._math(nt, 'MULTIPLY', se._math(nt, 'MULTIPLY', se._math(nt, 'POWER', lw.outputs['Facing'], soft),
                                             se._math(nt, 'MULTIPLY', pk, flick)), strength)
    rgb_node = nt.nodes.new('ShaderNodeRGB')
    rgb_node.outputs[0].default_value = (*rgb, 1)
    se._additive(nt, rgb_node.outputs[0], glow)
    return mat


def _seam_material(name, strength):
    """The cut a raking beam leaves: additive blackbody along entry_u, orange-hot at the contact (0)
    cooling to dull red and out at the seam's old end (1)."""
    name = '%s %.1f' % (name, strength)
    mat = bpy.data.materials.get(name)
    if mat:
        return mat
    mat, nt = se._new_material(name)
    at = nt.nodes.new('ShaderNodeAttribute')
    at.attribute_name = 'entry_u'
    hot = se._math(nt, 'SUBTRACT', 1.0, at.outputs['Fac'])
    lw = nt.nodes.new('ShaderNodeLayerWeight')
    lw.inputs['Blend'].default_value = .5
    glow = se._math(nt, 'MULTIPLY', se._math(nt, 'MULTIPLY', se._math(nt, 'POWER', lw.outputs['Facing'], 1.2),
                                             se._math(nt, 'POWER', hot, 2.0)), strength)
    se._additive(nt, se._blackbody(nt, se._math(nt, 'MULTIPLY', hot, .55)), glow)
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


def laser(name, emitter, contact, normal, coll, lights=None, seed=0, width_m=.3, glow=1.0, sparks=22, halo=4.0,
          pulse=None, trail=None, cut_m=None):
    """A mining laser from emitter to contact (world space, metres), cutting a surface whose outward
    normal at the contact is `normal`. width_m: the beam's core radius; halo: the halo's radius as a
    multiple of it; cut_m: the radius the cut (flash, scorch, sparks) is sized from, width_m unless
    given. lights: the collection its two lights go in (the one that lights hulls), or None.

    pulse: None for the steady beam, or {'t': seconds} (PULSE's keys override its defaults): packets
    every wavelength_m running emitter to contact at speed_mps, the core swelling a little on each; the flash,
    sparks and lights throb with the packets at their end of it, and the flash and sparks are drawn
    afresh for each instant. trail: world points on the plating, oldest first, ending at the contact:
    the seam the beam has cut raking along them."""
    rnd = random.Random(seed)
    a, b = Vector(emitter), Vector(contact)
    n = Vector(normal).normalized()
    d = b - a
    length = d.length
    if length < 1e-3:
        return
    cell = max(length / 6.0, 2.0)
    cw = width_m if cut_m is None else cut_m
    if pulse is None:
        pts = [a.lerp(b, i / 15) for i in range(16)]
        us = [1 - i / 15 for i in range(16)]
        radii = [1.0] * 16
        at_emitter = at_cut = 1.0
        # the game's own Mining Laser (mininglaser.wpn): a pink core (255,155,155) in a red fringe
        # (255,0,0), not the white-hot rod it was, which at quarter size clipped to a solid white line
        # and read as some other weapon's beam (owner, 2026-10-23, S8-S9: "wonky laser effects")
        core_mat = _beam_material(f'{PREFIX} core', swfx_burst.srgb(CORE), 14.0 * glow, .6, cell)
        halo_mat = _beam_material(f'{PREFIX} halo', swfx_burst.srgb(FRINGE), 4.0 * glow, 2.2, cell)
    else:
        pp = {**PULSE, **pulse}
        lam = pp['wavelength_m']
        phase = pp['t'] * pp['speed_mps'] / lam
        steps = max(16, min(400, int(math.ceil(length / lam * 8))))     # eight rings a packet: a round swell
        pts = [a.lerp(b, i / steps) for i in range(steps + 1)]
        us = [length * i / steps / lam - phase for i in range(steps + 1)]
        radii = [1.0 + pp['swell'] * pulse_packet(u, pp['sharp']) for u in us]
        halo_radii = [1.0 + pp['halo_swell'] * pulse_packet(u, pp['sharp']) for u in us]
        at_emitter, at_cut = pulse_packet(us[0], pp['sharp']), pulse_packet(us[-1], pp['sharp'])
        rnd = random.Random(seed * 7919 + int(round(pp['t'] * 240)))     # this instant's flash and sparks
        # packets brighter than the steady beam's 14, but short: only the packet clips, never a long
        # white core (blob_lint stops a final on 600 px of it); r1 at 12 never read as a pulse at range
        core_mat = _pulse_material(f'{PREFIX} core pulse', swfx_burst.srgb(CORE), 18.0 * glow, .6, pp['floor'],
                                   pp['sharp'], cell)
        halo_mat = _pulse_material(f'{PREFIX} halo pulse', swfx_burst.srgb(FRINGE), 4.0 * glow, 2.2,
                                   pp['halo_floor'], pp['sharp'], cell)
    throb_cut = .55 + .45 * at_cut if pulse is not None else 1.0
    throb_emitter = .55 + .45 * at_emitter if pulse is not None else 1.0
    se._tube(f'{name} core', pts, [width_m * r for r in radii], us, coll, core_mat, segments=10)
    se._tube(f'{name} halo', pts, [width_m * halo * r for r in (radii if pulse is None else halo_radii)], us, coll,
             halo_mat, segments=14)
    # the cut: a small jagged flash leaning out of the plating, flattened against it
    flash_me, _ = swfx_burst.lobed_mesh(f'{name} flash', _hot_material(f'{PREFIX} flash', 30.0 * glow, .92), rnd,
                                        lobes=(3, 5), spikes=(3, 6), lean=1.0)
    fl = bpy.data.objects.new(f'{name} flash', flash_me)
    coll.objects.link(fl)
    s = cw * 6.0 * throb_cut
    _placed(fl, b + n * s * .15, n, (s * .6, s, s))
    fl.visible_shadow = False
    # the scorch: a dull red, irregular glow pressed onto the plating round the cut (a pulsed beam's
    # keeps its shape from instant to instant: it is the hot metal, not the flash)
    sc_rnd = random.Random(seed) if pulse is not None else rnd
    sc_me, _ = swfx_burst.lobed_mesh(f'{name} scorch', _hot_material(f'{PREFIX} scorch', 3.0 * glow, .12), sc_rnd,
                                     lobes=(5, 8), spikes=(0, 1), lean=0.0)
    sc = bpy.data.objects.new(f'{name} scorch', sc_me)
    coll.objects.link(sc)
    s = cw * 13.0
    _placed(sc, b + n * cw * .3, n, (s * .05, s, s * sc_rnd.uniform(.6, 1.0)))
    sc.visible_shadow = False
    if trail is not None and len(trail) > 1:
        # the seam behind the contact, a thin hot line in the plating, cooling back along it (bench r1: at
        # 1.2x the cut's width and strength 8 it read as a glowing plank)
        seam = [Vector(p) + n * cw * .3 for p in reversed(trail)]
        k = len(seam)
        se._tube(f'{name} seam', seam, [cw * (.45 - .3 * i / (k - 1)) for i in range(k)],
                 [i / (k - 1) for i in range(k)], coll, _seam_material(f'{PREFIX} seam', 4.0 * glow), segments=8)
    # molten sparks: thrown off the surface, most of them low along it, cooling as they fly
    ref = Vector((0, 0, 1)) if abs(n.z) < .9 else Vector((0, 1, 0))
    t1 = n.cross(ref).normalized()
    t2 = n.cross(t1)
    spark_mat = se._flame_material(f'{PREFIX} spark', 22.0 * glow, max(cw * 4, .5))
    for k in range(sparks if pulse is None else int(round(sparks * (.4 + .8 * at_cut)))):
        ang = rnd.uniform(0, 2 * math.pi)
        out = (t1 * math.cos(ang) + t2 * math.sin(ang)) * rnd.uniform(.6, 1.0) + n * rnd.uniform(.15, .9)
        ln = cw * rnd.uniform(4.0, 18.0)
        bend = (t1 * rnd.gauss(0, 1) + t2 * rnd.gauss(0, 1)) * ln * .12
        p0 = b + n * cw * .5
        p1 = p0 + out.normalized() * ln * .5 + bend * .3
        p2 = p0 + out.normalized() * ln + bend
        r = cw * rnd.uniform(.05, .12)
        se._tube(f'{name} spark {k}', [p2, p1, p0], [r * .4, r * .8, r], [1.0, .5, 0.0], coll, spark_mat,
                 segments=6)
    if lights is not None:
        _light(f'{name} cut light', lights, b + n * cw * 4, 6.0e3 * glow * throb_cut, (1.0, .55, .25),
               cw * 2)
        _light(f'{name} emitter light', lights, a + d.normalized() * width_m * 3, 1.5e3 * glow * throb_emitter,
               (1.0, .6, .3), cw * 2)
