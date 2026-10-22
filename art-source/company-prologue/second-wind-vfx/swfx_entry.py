"""Atmospheric entry: the trail a hull leaves as it falls into the air, seen from orbit (W7 of
asharu-effects-plan.md; S12 of the Asharu battle, and S11's far end).

Coordinator, 2026-10-22, on S12's first lit frame: the stand-in trails "read as flat white dashed
lines, like a map annotation". An entry trail is three things, and none of them is an orb:

- **The head:** a bow-shock glow at the falling hull, orange-white at its core: a short cone of hot
  plasma opening behind her, soft at its edges, and a narrow white-hot core in its throat.
- **The wake:** the plasma she leaves, streaming back up her track. It cools from orange through
  dull red to nothing and widens as it cools, broken into streaks along its length.
- **The ionisation trail:** a faint, pale blue-grey wake, wider still, that lingers higher up the
  track toward orbit and fades out there.

The track runs **tail first, head last**: `pts[0]` is where the heating began (high, near orbit),
`pts[-1]` is the hull (far and low). Everything is one continuous mesh per layer, with the position
along the track carried as the face-corner attribute `entry_u` (0 at the head, 1 at the tail), so
nothing breaks into dashes. Plain Blender: the caller passes its collection; nothing is keyed.
"""
import math

import bmesh
import bpy
from mathutils import Vector

PREFIX = 'SWFX entry'
HOT = (1.0, .82, .55)          # orange-white: the shock's core
PLASMA = (1.0, .45, .14)       # the sheath and the young wake
EMBER = (.55, .12, .04)        # the wake cooling
ION = (.58, .68, .86)          # the ionisation trail: pale, faintly blue


def _frames(pts):
    """A (tangent, side, up) frame at each point, parallel-transported along the track."""
    out = []
    t0 = (pts[1] - pts[0]).normalized()
    ref = Vector((0, 0, 1)) if abs(t0.z) < .9 else Vector((0, 1, 0))
    side = t0.cross(ref).normalized()
    for i, p in enumerate(pts):
        t = (pts[min(i + 1, len(pts) - 1)] - pts[max(i - 1, 0)]).normalized()
        side = (side - t * side.dot(t)).normalized()
        out.append((t, side, t.cross(side).normalized()))
    return out


def _tube(name, pts, radii, us, coll, mat, segments=16):
    """One continuous tube through pts with these radii, entry_u = us along it, open at both ends."""
    me = bpy.data.meshes.new(name)
    bm = bmesh.new()
    rings = []
    for p, r, (t, s, u) in zip(pts, radii, _frames(pts)):
        ring = []
        for k in range(segments):
            a = 2 * math.pi * k / segments
            ring.append(bm.verts.new(p + (s * math.cos(a) + u * math.sin(a)) * r))
        rings.append(ring)
    layer = bm.loops.layers.float.new('entry_u')
    for i in range(len(rings) - 1):
        for k in range(segments):
            a, b = rings[i][k], rings[i][(k + 1) % segments]
            c, d = rings[i + 1][(k + 1) % segments], rings[i + 1][k]
            f = bm.faces.new((a, b, c, d))
            for loop in f.loops:
                loop[layer] = us[i] if loop.vert in (a, b) else us[i + 1]
    bm.to_mesh(me)
    bm.free()
    me.materials.append(mat)
    o = bpy.data.objects.new(name, me)
    coll.objects.link(o)
    o.visible_shadow = False
    return o


def _cone(name, apex, axis, length, radius, coll, mat, segments=24, rings=8):
    """The bow shock: a paraboloid cap opening back along -axis from apex, entry_u 0 at the apex."""
    t = axis.normalized()
    ref = Vector((0, 0, 1)) if abs(t.z) < .9 else Vector((0, 1, 0))
    s = t.cross(ref).normalized()
    u = t.cross(s).normalized()
    me = bpy.data.meshes.new(name)
    bm = bmesh.new()
    tip = bm.verts.new(apex)
    grid = []
    for j in range(1, rings + 1):
        f = j / rings
        back, r = length * f, radius * math.sqrt(f)
        grid.append([bm.verts.new(apex - t * back + (s * math.cos(2 * math.pi * k / segments)
                                                       + u * math.sin(2 * math.pi * k / segments)) * r)
                     for k in range(segments)])
    layer = bm.loops.layers.float.new('entry_u')
    for k in range(segments):
        f = bm.faces.new((tip, grid[0][k], grid[0][(k + 1) % segments]))
        for loop in f.loops:
            loop[layer] = 0.0 if loop.vert is tip else 1 / rings
    for j in range(rings - 1):
        for k in range(segments):
            q = (grid[j][k], grid[j + 1][k], grid[j + 1][(k + 1) % segments], grid[j][(k + 1) % segments])
            f = bm.faces.new(q)
            for loop in f.loops:
                loop[layer] = (j + 1) / rings if loop.vert in (q[0], q[3]) else (j + 2) / rings
    bm.to_mesh(me)
    bm.free()
    me.materials.append(mat)
    o = bpy.data.objects.new(name, me)
    coll.objects.link(o)
    o.visible_shadow = False
    return o


def _ramp(nt, stops, fac):
    r = nt.nodes.new('ShaderNodeValToRGB')
    els = r.color_ramp.elements
    while len(els) > len(stops):
        els.remove(els[-1])
    while len(els) < len(stops):
        els.new(0.5)
    for e, (pos, rgb) in zip(els, stops):
        e.position = pos
        e.color = (*rgb, 1) if len(rgb) == 3 else rgb
    nt.links.new(fac, r.inputs['Fac'])
    return r


def _math(nt, op, a, b=None):
    m = nt.nodes.new('ShaderNodeMath')
    m.operation = op
    for i, v in enumerate((a, b)):
        if v is None:
            continue
        if isinstance(v, (int, float)):
            m.inputs[i].default_value = v
        else:
            nt.links.new(v, m.inputs[i])
    return m.outputs[0]


def _material(name, colours, strength, opacity, edge=1.6, streaks=0.0):
    """Emission over transparency, both driven by entry_u: colours and opacity are ramp stops
    [(u, rgb)] / [(u, value)]; strength scales the emission. The edges of the tube fall away
    softly (edge: the power on how squarely the surface faces the camera), so it reads as glowing
    gas, not a pipe; streaks breaks the opacity up along the track."""
    mat = bpy.data.materials.get(name)
    if mat:
        return mat
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    nt = mat.node_tree
    nt.nodes.remove(nt.nodes['Principled BSDF'])
    at = nt.nodes.new('ShaderNodeAttribute')
    at.attribute_name = 'entry_u'
    u = at.outputs['Fac']
    col = _ramp(nt, colours, u)
    op = _ramp(nt, [(p, (v, v, v)) for p, v in opacity], u)
    lw = nt.nodes.new('ShaderNodeLayerWeight')
    lw.inputs['Blend'].default_value = .5
    soft = _math(nt, 'POWER', _math(nt, 'SUBTRACT', 1.0, lw.outputs['Facing']), edge)
    fac = _math(nt, 'MULTIPLY', op.outputs['Color'], soft)
    if streaks:
        sx = nt.nodes.new('ShaderNodeCombineXYZ')
        nt.links.new(_math(nt, 'MULTIPLY', u, 60.0), sx.inputs['X'])
        nz = nt.nodes.new('ShaderNodeTexNoise')
        nz.inputs['Scale'].default_value = 1.0
        nz.inputs['Detail'].default_value = 4.0
        nt.links.new(sx.outputs[0], nz.inputs['Vector'])
        brk = _math(nt, 'ADD', 1 - streaks, _math(nt, 'MULTIPLY', nz.outputs['Fac'], streaks * 1.6))
        fac = _math(nt, 'MULTIPLY', fac, brk)
    fac = _math(nt, 'MINIMUM', fac, 1.0)
    em = nt.nodes.new('ShaderNodeEmission')
    nt.links.new(col.outputs['Color'], em.inputs['Color'])
    em.inputs['Strength'].default_value = strength
    clear = nt.nodes.new('ShaderNodeBsdfTransparent')
    mix = nt.nodes.new('ShaderNodeMixShader')
    nt.links.new(fac, mix.inputs['Fac'])
    nt.links.new(clear.outputs[0], mix.inputs[1])
    nt.links.new(em.outputs[0], mix.inputs[2])
    nt.links.new(mix.outputs[0], nt.nodes['Material Output'].inputs['Surface'])
    return mat


def trail(name, pts, coll, width_m=1500.0, glow=1.0):
    """An entry trail along pts (tail first, the hull last): the head's bow shock and core, the wake
    cooling and widening back up the track, and the ionisation trail beyond it. width_m: the wake's
    width at its widest; glow scales the emission (a smaller hull burns smaller and dimmer).
    Returns the head's position."""
    pts = [Vector(p) for p in pts]
    n = len(pts)
    us = [1 - i / (n - 1) for i in range(n)]               # 0 at the head, 1 at the tail
    head, axis = pts[-1], (pts[-1] - pts[-2]).normalized()
    wake = _material(f'{PREFIX} wake', [(0, HOT), (.06, PLASMA), (.35, EMBER), (1, (.12, .03, .01))],
                     7.0 * glow, [(0, 1.0), (.1, .85), (.5, .35), (1, 0.0)], edge=1.4, streaks=.45)
    ion = _material(f'{PREFIX} ionisation', [(0, ION), (1, ION)], .35 * glow,
                    [(0, 0.0), (.15, .28), (.6, .18), (1, 0.0)], edge=2.2, streaks=.3)
    core = _material(f'{PREFIX} core', [(0, (1, 1, 1)), (.3, HOT), (1, PLASMA)], 40.0 * glow,
                     [(0, 1.0), (.5, .9), (1, 0.0)], edge=1.0)
    shock = _material(f'{PREFIX} shock', [(0, HOT), (.4, PLASMA), (1, EMBER)], 14.0 * glow,
                      [(0, 1.0), (.5, .7), (1, 0.0)], edge=2.0)
    # the wake: narrow and hot at the head, widening as it cools
    _tube(f'{name} wake', pts, [width_m * (.12 + .88 * u ** .7) for u in us], us, coll, wake)
    # the ionisation trail: wider, and on past the wake's tail toward orbit
    tail_dir = (pts[0] - pts[1]).normalized()
    ext = [pts[0] + tail_dir * pts[0].__sub__(pts[1]).length * k for k in (4, 3, 2, 1)]
    ipts = ext + pts
    ius = [1 - i / (len(ipts) - 1) for i in range(len(ipts))]
    _tube(f'{name} ionisation', ipts, [width_m * (.5 + 2.5 * u) for u in ius], ius, coll, ion)
    # the head: a white-hot core a few hull lengths long, and the bow shock opening back over it
    k = max(2, n // 12)
    cpts = pts[-k - 1:]
    cus = [1 - i / (len(cpts) - 1) for i in range(len(cpts))]
    _tube(f'{name} core', cpts, [width_m * (.05 + .1 * u) for u in cus], cus, coll, core)
    _cone(f'{name} shock', head + axis * width_m * .15, axis, width_m * 2.2, width_m * .55, coll, shock)
    return head
