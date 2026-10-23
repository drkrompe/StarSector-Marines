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
from mathutils import Matrix, Vector

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
    name = '%s %.2f' % (name, strength)          # one per strength: a dimmer trail is not the first one's
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


def _wake_material(name, strength, cell_m):
    """The wake as it cools (coordinator on S12 lit span 3, 2026-10-22: "turbulent and broken, widening
    and cooling: white-yellow at the head, then orange, red, and grey smoke", gone before the black of
    space). entry_u drives the colour, the glow (bright at the head, none once it is smoke), a lit grey
    smoke taking over from the glow, and the opacity; a 3D noise at about cell_m metres breaks it into
    patches, more so toward the tail, so it never reads as a cone with straight edges."""
    name = '%s %.2f %.0f' % (name, strength, cell_m)
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
    col = _ramp(nt, [(0, (1.0, .95, .8)), (.07, (1.0, .78, .4)), (.18, PLASMA), (.34, (.62, .15, .05)),
                     (.5, (.3, .27, .25)), (1, (.26, .25, .24))], u)
    glow = _ramp(nt, [(0, (1, 1, 1)), (.1, (.45, .45, .45)), (.3, (.1, .1, .1)), (.48, (0, 0, 0))], u)
    smoke = _ramp(nt, [(.32, (0, 0, 0)), (.55, (1, 1, 1))], u)
    op = _ramp(nt, [(0, (1, 1, 1)), (.12, (.9, .9, .9)), (.5, (.5, .5, .5)), (.72, (.22, .22, .22)),
                    (.88, (0, 0, 0))], u)
    tc = nt.nodes.new('ShaderNodeTexCoord')
    sc = nt.nodes.new('ShaderNodeVectorMath')
    sc.operation = 'SCALE'
    sc.inputs['Scale'].default_value = 1.0 / cell_m
    nt.links.new(tc.outputs['Object'], sc.inputs[0])
    nz = nt.nodes.new('ShaderNodeTexNoise')
    nz.inputs['Scale'].default_value = 1.0
    nz.inputs['Detail'].default_value = 6.0
    nz.inputs['Roughness'].default_value = .62
    nt.links.new(sc.outputs['Vector'], nz.inputs['Vector'])
    patch = _math(nt, 'MINIMUM', _math(nt, 'MAXIMUM', _math(nt, 'MULTIPLY',
                                                            _math(nt, 'SUBTRACT', nz.outputs['Fac'], .36), 3.2), 0.0), 1.0)
    how = _math(nt, 'ADD', .45, _math(nt, 'MULTIPLY', u, .5))          # how broken: more toward the tail
    brk = _math(nt, 'ADD', _math(nt, 'MULTIPLY', patch, how), _math(nt, 'SUBTRACT', 1.0, how))
    lw = nt.nodes.new('ShaderNodeLayerWeight')
    lw.inputs['Blend'].default_value = .5
    soft = _math(nt, 'POWER', _math(nt, 'SUBTRACT', 1.0, lw.outputs['Facing']), 1.2)
    fac = _math(nt, 'MINIMUM', _math(nt, 'MULTIPLY', _math(nt, 'MULTIPLY', op.outputs['Color'], soft), brk), 1.0)
    em = nt.nodes.new('ShaderNodeEmission')
    nt.links.new(col.outputs['Color'], em.inputs['Color'])
    nt.links.new(_math(nt, 'MULTIPLY', glow.outputs['Color'], strength), em.inputs['Strength'])
    dif = nt.nodes.new('ShaderNodeBsdfDiffuse')
    nt.links.new(col.outputs['Color'], dif.inputs['Color'])
    body = nt.nodes.new('ShaderNodeMixShader')
    nt.links.new(smoke.outputs['Color'], body.inputs['Fac'])
    nt.links.new(em.outputs[0], body.inputs[1])
    nt.links.new(dif.outputs[0], body.inputs[2])
    clear = nt.nodes.new('ShaderNodeBsdfTransparent')
    mix = nt.nodes.new('ShaderNodeMixShader')
    nt.links.new(fac, mix.inputs['Fac'])
    nt.links.new(clear.outputs[0], mix.inputs[1])
    nt.links.new(body.outputs[0], mix.inputs[2])
    nt.links.new(mix.outputs[0], nt.nodes['Material Output'].inputs['Surface'])
    return mat


def _resample(pts, n):
    """n points evenly along the polyline pts."""
    out = []
    for k in range(n):
        j = k * (len(pts) - 1) / (n - 1)
        i = min(int(j), len(pts) - 2)
        out.append(pts[i].lerp(pts[i + 1], j - i))
    return out


def _ragged_tube(name, pts, radii, us, coll, mat, rnd, segments=20):
    """_tube with its centre line wandering and every vertex's radius jittered, both growing toward
    the tail: a wake whose edges are torn, not a cone's straight sides."""
    me = bpy.data.meshes.new(name)
    bm = bmesh.new()
    rings = []
    phase = rnd.uniform(0, 6.3)
    for i, (p, r, (t, s, up)) in enumerate(zip(pts, radii, _frames(pts))):
        w = us[i]
        c = p + (s * math.sin(phase + 5.0 * w) + up * math.cos(phase * 1.7 + 4.0 * w)) * r * .35 * w
        ring = []
        for k in range(segments):
            a = 2 * math.pi * k / segments
            rr = r * (1 + w * rnd.uniform(-.35, .35))
            ring.append(bm.verts.new(c + (s * math.cos(a) + up * math.sin(a)) * rr))
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


def trail(name, pts, coll, width_m=1500.0, glow=1.0, sparks=0, seed=0, ion=True, shock=None, spark_len=None,
          ragged=0):
    """An entry trail along pts (tail first, the hull last). The hottest light is ahead of and around
    her, never behind (coordinator, S12 lit span 3: a glow cone behind each hull read as engine
    exhaust): a bow shock stands off her nose and wraps back over the hull, with a white-hot cap at its
    front; the wake streams back from it, torn and cooling to smoke. width_m: the wake's width at its
    widest; glow scales the emission. shock: (length, radius) of the bow shock in metres, in place of
    its size from width_m. sparks: that many burning flecks, spark_len the longest, in the wake's
    orange. ion=False leaves out the faint ionisation trail. ragged: that many extra small shocks
    jittered around the head, so a tumbling fragment's head is torn, not a tidy comet's.
    Returns the head's position."""
    import random
    rnd = random.Random(seed)
    pts = _resample([Vector(p) for p in pts], 48)          # enough rings for the wake to wander
    n = len(pts)
    us = [1 - i / (n - 1) for i in range(n)]               # 0 at the head, 1 at the tail
    head, axis = pts[-1], (pts[-1] - pts[-2]).normalized()
    sl, sr = shock if shock else (width_m * 2.2, width_m * .55)
    wake = _wake_material(f'{PREFIX} wake', 9.0 * glow, max(width_m * .7, 1.0))
    core = _material(f'{PREFIX} core', [(0, (1, 1, 1)), (.3, HOT), (1, PLASMA)], 45.0 * glow,
                     [(0, 1.0), (.5, .8), (1, 0.0)], edge=1.4)
    shock_mat = _material(f'{PREFIX} shock', [(0, HOT), (.35, PLASMA), (1, EMBER)], 22.0 * glow,
                          [(0, 1.0), (.45, .75), (1, 0.0)], edge=1.6)
    # the wake: from the shock's back edge, narrow and hot, widening and tearing as it cools
    _ragged_tube(f'{name} wake', pts, [max(sr * .8, width_m * (.18 + .82 * u ** .6)) for u in us], us, coll,
                 wake, rnd)
    if ion:
        ion_mat = _material(f'{PREFIX} ionisation', [(0, ION), (1, ION)], .35 * glow,
                            [(0, 0.0), (.15, .28), (.6, .18), (1, 0.0)], edge=2.2, streaks=.3)
        tail_dir = (pts[0] - pts[1]).normalized()
        ext = [pts[0] + tail_dir * (pts[0] - pts[1]).length * k for k in (4, 3, 2, 1)]
        ipts = ext + pts
        ius = [1 - i / (len(ipts) - 1) for i in range(len(ipts))]
        _tube(f'{name} ionisation', ipts, [width_m * (.5 + 2.5 * u) for u in ius], ius, coll, ion_mat)
    # the bow shock, standing off her nose and wrapping back over her; a white-hot cap at its front
    _cone(f'{name} shock', head + axis * sl * .4, axis, sl, sr, coll, shock_mat)
    _cone(f'{name} cap', head + axis * sl * .42, axis, sl * .35, sr * .55, coll, core)
    for j in range(ragged):
        off = Vector((rnd.gauss(0, 1), rnd.gauss(0, 1), rnd.gauss(0, 1)))
        off -= axis * off.dot(axis)
        tilt = (axis + Vector((rnd.gauss(0, .3), rnd.gauss(0, .3), rnd.gauss(0, .3)))).normalized()
        k = rnd.uniform(.35, .75)
        _cone(f'{name} shard {j}', head + off * sr * .7 + tilt * sl * .3 * k, tilt, sl * k, sr * k, coll,
              shock_mat if j % 2 else core)
    if sparks:
        fr = _frames(pts)
        for k in range(sparks):
            i = max(1, int((n - 1) * (1 - rnd.random() ** 2 * .35)))       # most near the head
            t, s_, u_ = fr[i]
            a = rnd.uniform(0, 2 * math.pi)
            off = (s_ * math.cos(a) + u_ * math.sin(a)) * max(width_m, sr) * rnd.uniform(.3, 1.4)
            p0 = pts[i] + off
            ln = (spark_len or width_m * 1.6) * rnd.uniform(.25, 1.0)
            p1 = p0 - t * ln
            _tube(f'{name} spark {k}', [p1, p0], [ln * .008, ln * .015], [1.0, .2], coll, wake, segments=6)
    return head


# --- plasma shaped by the hull -------------------------------------------------------------------
# Owner on S12 lit span 3 (2026-10-22): "Reads like hard shapes / arrowhead. The burn up doesn't take
# into account the shape of the ship." So the entry is built from the hull's own mesh: an envelope (a
# voxel-remeshed skin over the hull, as KSP2 gives every part), a sheath on it lit by how squarely each
# point meets the airflow, a shock layer standing off the windward side, and flames peeling off the
# hull's silhouette as the air sees it (Lindner's edge extrusion) into one wake. See asharu-effects.md,
# "Atmospheric entry".
#
# Coordinator on the first close still (2026-10-22): it read as "white foam, cotton or snow, and
# cream-coloured noodles". Plasma is self-lit: every layer here is emission over full transparency
# (additive, no surface response, so the hull and the planet show through), coloured by blackbody
# temperature, and only the wake's far end is smoke, a sun-lit grey volume.

def hull_soup(instancer, skip=('SWFX',)):
    """The world-space polygons of everything a collection-instance empty draws: (verts, faces), read
    from whichever view layer draws it. Hidden objects and kit objects are left out."""
    for vl in bpy.context.scene.view_layers:
        vl.update()
        verts, faces = _soup(vl.depsgraph, instancer, skip)
        if verts:
            return verts, faces
    return [], []


def _soup(dg, instancer, skip):
    verts, faces = [], []
    for inst in dg.object_instances:
        if not inst.is_instance or inst.parent is None or inst.parent.original != instancer:
            continue
        ob = inst.object
        if ob.type != 'MESH' or ob.original.hide_render or any(ob.name.startswith(s) for s in skip):
            continue
        me = ob.to_mesh()
        mw, o = inst.matrix_world.copy(), len(verts)
        verts += [mw @ v.co for v in me.vertices]
        faces += [tuple(o + i for i in p.vertices) for p in me.polygons]
        ob.to_mesh_clear()
    return verts, faces


def _skin(name, verts, faces, voxel_m):
    """The envelope: verts/faces joined and voxel-remeshed into one clean skin (greebles and
    concavities filled), keeping the outer shells: a small piece wholly inside the largest one's
    bounds is an inner wall the remesher left, and is dropped. Returns a bmesh with normals."""
    src = bpy.data.meshes.new(name + ' src')
    src.from_pydata([tuple(v) for v in verts], [], faces)
    tmp = bpy.data.objects.new(name + ' src', src)
    bpy.context.scene.collection.objects.link(tmp)
    mod = tmp.modifiers.new('remesh', 'REMESH')
    mod.mode, mod.voxel_size, mod.adaptivity = 'VOXEL', voxel_m, 0.0
    dg = bpy.context.evaluated_depsgraph_get()
    ev = tmp.evaluated_get(dg)
    bm = bmesh.new()
    bm.from_mesh(ev.to_mesh())
    ev.to_mesh_clear()
    bpy.data.objects.remove(tmp)
    bpy.data.meshes.remove(src)
    seen, pieces = set(), []
    for v in bm.verts:
        if v in seen:
            continue
        stack, piece = [v], []
        seen.add(v)
        while stack:
            a = stack.pop()
            piece.append(a)
            for e in a.link_edges:
                b = e.other_vert(a)
                if b not in seen:
                    seen.add(b)
                    stack.append(b)
        pieces.append(piece)
    if len(pieces) > 1:
        def box(p):
            xs, ys, zs = [v.co.x for v in p], [v.co.y for v in p], [v.co.z for v in p]
            return (min(xs), min(ys), min(zs)), (max(xs), max(ys), max(zs))
        big = max(pieces, key=len)
        lo, hi = box(big)
        drop = []
        for p in pieces:
            if p is big:
                continue
            a, b = box(p)
            if all(lo[i] <= a[i] and b[i] <= hi[i] for i in range(3)) and len(p) < len(big) * .2:
                drop += p
        if drop:
            bmesh.ops.delete(bm, geom=drop, context='VERTS')
    bm.normal_update()
    return bm


def _shell(name, skin, hot, length, coll, mat, base, windward, power, rough, seed):
    """A shell offset from the skin along its normals: base everywhere, plus windward times the point's
    share of the heat (how squarely it faces hot, to that power), roughened by noise; all in fractions
    of the hull's length. Carries entry_wind (that share, 0 to 1) for its material."""
    from mathutils import noise
    bm = skin.copy()
    into = Vector(hot).normalized()
    wl = bm.verts.layers.float.new('entry_wind')
    off = Vector((seed * 13.1, seed * 7.7, seed * 3.3))
    moves = []
    for v in bm.verts:
        n = v.normal
        f = max(0.0, n.dot(into))
        v[wl] = f
        nz = noise.noise(v.co / (length * .09) + off)
        moves.append((v, n * length * (base + windward * f ** power) * (1 + rough * nz)))
    for v, d in moves:
        v.co += d
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    for p in me.polygons:
        p.use_smooth = True
    me.materials.append(mat)
    o = bpy.data.objects.new(name, me)
    coll.objects.link(o)
    o.visible_shadow = False
    return o


def _new_material(name):
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    nt = mat.node_tree
    nt.nodes.remove(nt.nodes['Principled BSDF'])
    return mat, nt


def _blackbody(nt, heat, cold=1300.0, hot=6500.0):
    """A blackbody colour for heat 0..1: deep red at cold kelvin, white-yellow at hot."""
    bb = nt.nodes.new('ShaderNodeBlackbody')
    nt.links.new(_math(nt, 'ADD', cold, _math(nt, 'MULTIPLY', heat, hot - cold)), bb.inputs['Temperature'])
    return bb.outputs['Color']


def _additive(nt, colour, strength):
    """Emission added over full transparency: light, and nothing else."""
    em = nt.nodes.new('ShaderNodeEmission')
    nt.links.new(colour, em.inputs['Color'])
    if isinstance(strength, (int, float)):
        em.inputs['Strength'].default_value = strength
    else:
        nt.links.new(strength, em.inputs['Strength'])
    clear = nt.nodes.new('ShaderNodeBsdfTransparent')
    add = nt.nodes.new('ShaderNodeAddShader')
    nt.links.new(clear.outputs[0], add.inputs[0])
    nt.links.new(em.outputs[0], add.inputs[1])
    nt.links.new(add.outputs[0], nt.nodes['Material Output'].inputs['Surface'])


def _noise(nt, cell_m, detail=5.0, coords='Object'):
    tc = nt.nodes.new('ShaderNodeTexCoord')
    sc = nt.nodes.new('ShaderNodeVectorMath')
    sc.operation = 'SCALE'
    sc.inputs['Scale'].default_value = 1.0 / cell_m
    nt.links.new(tc.outputs[coords], sc.inputs[0])
    nz = nt.nodes.new('ShaderNodeTexNoise')
    nz.inputs['Detail'].default_value = detail
    nz.inputs['Roughness'].default_value = .6
    nt.links.new(sc.outputs['Vector'], nz.inputs['Vector'])
    return nz.outputs['Fac'], tc


def _sheath_material(name, strength, cell_m):
    """The sheath on the hull, self-lit: by entry_wind, deep red at the flanks through orange to white
    on the faces square to the airflow (hot enough there to clip), nothing on the lee; mottled."""
    name = '%s %.2f %.1f' % (name, strength, cell_m)
    mat = bpy.data.materials.get(name)
    if mat:
        return mat
    mat, nt = _new_material(name)
    at = nt.nodes.new('ShaderNodeAttribute')
    at.attribute_name = 'entry_wind'
    w = at.outputs['Fac']
    nz, _ = _noise(nt, cell_m)
    # close still 6: the edge still read as one neon line. Hot spots, not a band: below a threshold the
    # noise leaves only a faint glow, above it the patch flares
    mottle = _math(nt, 'ADD', .12, _math(nt, 'MAXIMUM', _math(nt, 'MULTIPLY', _math(nt, 'SUBTRACT', nz, .4), 3.0), 0.0))
    # owner, 2026-10-22: "bright in places", not white all over. The colour's heat is mottled as the
    # glow is, so only the squarest, hottest patches reach white; the rest of the face burns orange-yellow
    heat = _math(nt, 'MINIMUM', _math(nt, 'MULTIPLY', _math(nt, 'MULTIPLY', _math(nt, 'POWER', w, 1.8), mottle), .5), 1.0)
    # coordinator on close still 6: "a uniform tube" along the box edges. The glow spreads further across
    # the hot faces (a softer power on the heat share), and is damped where the shell is seen edge-on,
    # which is what stacked it into a neon line at every corner
    lw = nt.nodes.new('ShaderNodeLayerWeight')
    lw.inputs['Blend'].default_value = .5
    face_on = _math(nt, 'ADD', .25, _math(nt, 'MULTIPLY', _math(nt, 'POWER', lw.outputs['Facing'], .7), .75))
    glow = _math(nt, 'MULTIPLY', _math(nt, 'MULTIPLY', _math(nt, 'MULTIPLY', _math(nt, 'POWER', w, 1.7), mottle),
                                       face_on), strength)
    _additive(nt, _blackbody(nt, heat), glow)
    return mat


def _shock_material(name, strength):
    """The shock layer standing off the windward side, self-lit: faint where it faces the camera,
    brightest at its rim, hot, with a faint magenta at its edge; nothing on the lee."""
    name = '%s %.2f' % (name, strength)
    mat = bpy.data.materials.get(name)
    if mat:
        return mat
    mat, nt = _new_material(name)
    at = nt.nodes.new('ShaderNodeAttribute')
    at.attribute_name = 'entry_wind'
    w = at.outputs['Fac']
    lw = nt.nodes.new('ShaderNodeLayerWeight')
    lw.inputs['Blend'].default_value = .5
    rim = _math(nt, 'SUBTRACT', 1.0, lw.outputs['Facing'])
    hot = _blackbody(nt, _math(nt, 'ADD', .28, _math(nt, 'MULTIPLY', w, .42)))
    tint = nt.nodes.new('ShaderNodeMix')
    tint.data_type = 'RGBA'
    nt.links.new(_math(nt, 'MULTIPLY', _math(nt, 'POWER', rim, 3.0), .4), tint.inputs['Factor'])
    nt.links.new(hot, tint.inputs['A'])
    tint.inputs['B'].default_value = (1.0, .5, .9, 1)
    glow = _math(nt, 'MULTIPLY', _math(nt, 'MULTIPLY', _math(nt, 'POWER', w, 1.8),
                                       _math(nt, 'ADD', .08, _math(nt, 'POWER', rim, 2.5))), strength)
    _additive(nt, tint.outputs['Result'], glow)
    return mat


def _flame_material(name, strength, cell_m):
    """A flame tongue peeling off the hull, self-lit: hot where it leaves the edge (entry_u 0), cooling
    to deep red and gone along its length, soft at its sides, torn by noise along it."""
    name = '%s %.2f %.1f' % (name, strength, cell_m)
    mat = bpy.data.materials.get(name)
    if mat:
        return mat
    mat, nt = _new_material(name)
    at = nt.nodes.new('ShaderNodeAttribute')
    at.attribute_name = 'entry_u'
    u = at.outputs['Fac']
    life = _math(nt, 'POWER', _math(nt, 'MAXIMUM', _math(nt, 'SUBTRACT', 1.0, u), 0.0), 1.4)
    nz, _ = _noise(nt, cell_m, detail=6.0)
    torn = _math(nt, 'MINIMUM', _math(nt, 'MAXIMUM', _math(nt, 'MULTIPLY', _math(nt, 'SUBTRACT', nz, .45), 4.5), 0.0), 1.0)
    lw = nt.nodes.new('ShaderNodeLayerWeight')
    lw.inputs['Blend'].default_value = .5
    soft = _math(nt, 'POWER', lw.outputs['Facing'], 1.5)       # bright through the middle, gone at the sides
    glow = _math(nt, 'MULTIPLY', _math(nt, 'MULTIPLY', _math(nt, 'MULTIPLY', _math(nt, 'POWER', life, 1.8), torn),
                                       soft), strength)
    _additive(nt, _blackbody(nt, _math(nt, 'POWER', life, 1.2), hot=5200.0), glow)
    return mat


def _wake_volume(name, start, wind, span, r0, r1, coll, strength, cell_m, seed):
    """The merged wake behind a hull as a volume, not a shell (coordinator: the faceted wake): a capped
    tube in its own frame (x along the wind from 0 to span, so the shader knows where along the wake a
    point is), filled with noise-broken density that widens and cools: blackbody glow near the hull,
    gone by mid-wake, then a thin sun-lit grey smoke that fades out."""
    import random
    rnd = random.Random(seed)
    W = Vector(wind).normalized()
    ref = Vector((0, 0, 1)) if abs(W.z) < .9 else Vector((0, 1, 0))
    Y = W.cross(ref).normalized()
    Z = W.cross(Y).normalized()
    rot = Matrix((W, Y, Z)).transposed().to_4x4()
    bm = bmesh.new()
    seg, n = 20, 32
    rings = []
    for i in range(n + 1):
        u = i / n
        r = (r0 + (r1 - r0) * u ** .7) * 1.5 + r1 * .6 * (.25 + u)     # room for the wander
        rings.append([bm.verts.new((u * span, r * math.cos(2 * math.pi * k / seg), r * math.sin(2 * math.pi * k / seg)))
                      for k in range(seg)])
    for i in range(n):
        for k in range(seg):
            bm.faces.new((rings[i][k], rings[i][(k + 1) % seg], rings[i + 1][(k + 1) % seg], rings[i + 1][k]))
    bm.faces.new(list(reversed(rings[0])))
    bm.faces.new(rings[-1])
    bm.normal_update()
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    mname = '%s %.2f %.0f %.0f %.0f %.0f' % (f'{PREFIX} wake volume', strength, span, r0, r1, cell_m)
    mat = bpy.data.materials.get(mname)
    if mat is None:
        mat, nt = _new_material(mname)
        tc = nt.nodes.new('ShaderNodeTexCoord')
        sep = nt.nodes.new('ShaderNodeSeparateXYZ')
        nt.links.new(tc.outputs['Object'], sep.inputs[0])
        u = _math(nt, 'MINIMUM', _math(nt, 'MAXIMUM', _math(nt, 'DIVIDE', sep.outputs['X'], span), 0.0), 1.0)
        # coordinator on close still 3: "a solid beam, like a lightsaber". Its centre line wanders and its
        # edge eddies: the radial measure is taken from a noise-warped line, more so further back
        warp = nt.nodes.new('ShaderNodeTexNoise')
        warp.inputs['Scale'].default_value = 1.0
        warp.inputs['Detail'].default_value = 3.0
        wsc = nt.nodes.new('ShaderNodeVectorMath')
        wsc.operation = 'SCALE'
        wsc.inputs['Scale'].default_value = 1.0 / (cell_m * 2.5)
        nt.links.new(tc.outputs['Object'], wsc.inputs[0])
        nt.links.new(wsc.outputs['Vector'], warp.inputs['Vector'])
        wc = nt.nodes.new('ShaderNodeSeparateColor')
        nt.links.new(warp.outputs['Color'], wc.inputs[0])
        amp = _math(nt, 'MULTIPLY', _math(nt, 'ADD', .25, u), r1 * 1.1)
        y = _math(nt, 'ADD', sep.outputs['Y'], _math(nt, 'MULTIPLY', _math(nt, 'SUBTRACT', wc.outputs[1], .5), amp))
        z = _math(nt, 'ADD', sep.outputs['Z'], _math(nt, 'MULTIPLY', _math(nt, 'SUBTRACT', wc.outputs[2], .5), amp))
        rad = _math(nt, 'SQRT', _math(nt, 'ADD', _math(nt, 'MULTIPLY', y, y), _math(nt, 'MULTIPLY', z, z)))
        rn = _math(nt, 'ADD', r0, _math(nt, 'MULTIPLY', _math(nt, 'POWER', u, .7), r1 - r0))
        radial = _math(nt, 'MAXIMUM', _math(nt, 'SUBTRACT', 1.0, _math(nt, 'POWER', _math(nt, 'DIVIDE', rad, rn), 2.0)), 0.0)
        nz, _ = _noise(nt, cell_m, detail=6.0)
        patch = _math(nt, 'MINIMUM', _math(nt, 'MAXIMUM', _math(nt, 'MULTIPLY', _math(nt, 'SUBTRACT', nz, .42), 3.4), 0.0), 1.0)
        along = _ramp(nt, [(0, (.7, .7, .7)), (.08, (1, 1, 1)), (.6, (.55, .55, .55)), (1, (0, 0, 0))], u)
        dens = _math(nt, 'MULTIPLY', _math(nt, 'MULTIPLY', radial, patch), along.outputs['Color'])
        heat = _ramp(nt, [(0, (1, 1, 1)), (.06, (.45, .45, .45)), (.2, (.12, .12, .12)), (.38, (0, 0, 0))], u)
        hcol = heat.outputs['Color']
        em = nt.nodes.new('ShaderNodeEmission')
        nt.links.new(_blackbody(nt, hcol, hot=4200.0), em.inputs['Color'])
        nt.links.new(_math(nt, 'MULTIPLY', _math(nt, 'MULTIPLY', dens, hcol), strength / max(r1, 1.0)),
                     em.inputs['Strength'])
        smoke = _ramp(nt, [(.3, (0, 0, 0)), (.6, (1, 1, 1))], u)
        sd = _math(nt, 'MULTIPLY', _math(nt, 'MULTIPLY', dens, smoke.outputs['Color']), 1.2 / max(r1, 1.0))
        sc = nt.nodes.new('ShaderNodeVolumeScatter')
        sc.inputs['Color'].default_value = (.42, .4, .38, 1)
        nt.links.new(sd, sc.inputs['Density'])
        ab = nt.nodes.new('ShaderNodeVolumeAbsorption')
        ab.inputs['Color'].default_value = (.5, .48, .46, 1)
        nt.links.new(_math(nt, 'MULTIPLY', sd, .5), ab.inputs['Density'])
        a1 = nt.nodes.new('ShaderNodeAddShader')
        nt.links.new(em.outputs[0], a1.inputs[0])
        nt.links.new(sc.outputs[0], a1.inputs[1])
        a2 = nt.nodes.new('ShaderNodeAddShader')
        nt.links.new(a1.outputs[0], a2.inputs[0])
        nt.links.new(ab.outputs[0], a2.inputs[1])
        nt.links.new(a2.outputs[0], nt.nodes['Material Output'].inputs['Volume'])
    me.materials.append(mat)
    o = bpy.data.objects.new(name, me)
    o.matrix_world = Matrix.Translation(start) @ rot
    coll.objects.link(o)
    o.visible_shadow = False
    return o


def _glow_lights(name, skin, c, into, length, glow, coll, count=3):
    """The sheath's light on the hull's own windward plates: a few warm soft lights just off her
    windward-most points, spread along her."""
    pts = sorted(skin.verts, key=lambda v: -(v.co - c).dot(into))[:max(60, len(skin.verts) // 40)]
    chosen = [pts[0].co.copy()]
    while len(chosen) < count and len(chosen) < len(pts):
        chosen.append(max((v.co for v in pts), key=lambda p: min((p - q).length for q in chosen)).copy())
    for k, p in enumerate(chosen):
        ld = bpy.data.lights.new(f'{name} glow {k}', 'POINT')
        ld.energy = 2.4e5 * glow * (length / 108.0) ** 2 / count
        ld.color = (1.0, .5, .2)
        ld.shadow_soft_size = length * .2
        lo = bpy.data.objects.new(f'{name} glow {k}', ld)
        lo.location = p + into * length * .18
        coll.objects.link(lo)


def sheath(name, verts, faces, coll, wind, length, glow=1.0, streamers=22, seed=0, grow=1.0, voxel=None,
           lights=None, wake=True, belly=None):
    """A hull burning into the air, its plasma shaped by the hull (verts/faces: its world-space mesh,
    hull_soup). wind: the direction the plasma streams off it (the airflow over it); length: its length
    in metres; grow: 0 to 1, how far its flames have grown (they lengthen as it goes deeper). Builds the
    envelope, the windward sheath, the shock layer, thin flame tongues peeling off its silhouette as the
    air sees it and merging into a volume wake, and (lights: a collection) the sheath's warm light on its
    windward plates. belly: the world direction her underside faces; the heat leans to it.
    Returns the hull's centre."""
    import random
    from mathutils import noise
    rnd = random.Random(seed)
    W = Vector(wind).normalized()
    into = -W
    skin = _skin(name + ' skin', verts, faces, voxel or length / 70.0)
    if not skin.verts:
        skin.free()
        return None
    c = sum((v.co for v in skin.verts), Vector()) / len(skin.verts)
    # owner, 2026-10-22: "focus the friction and fires along the front and underside the most". The heat
    # follows a hot direction, into the air and leaning to her belly: the front and the belly burn
    # brightest and shed the flames, the flanks glow faintly, the top and the lee stay dark.
    hot = (into + Vector(belly).normalized() * .6).normalized() if belly is not None else into
    _shell(name + ' sheath', skin, hot, length, coll,
           _sheath_material(f'{PREFIX} sheath', 22.0 * glow, length * .07), .003, .012, 1.2, .6, seed)
    _shell(name + ' shock', skin, hot, length, coll, _shock_material(f'{PREFIX} shock layer', 5.0 * glow),
           .008, .05, 1.6, .35, seed + 1)
    # the flames leave the edge of her hot side (front and belly), never the deck in her lee
    rim = [v for v in skin.verts if -.05 < v.normal.dot(hot) < .3 and v.normal.dot(into) > -.1]
    rim = rim or list(skin.verts)
    down = Vector(belly).normalized() if belly is not None else hot
    weights = [max(0.0, v.normal.dot(down)) + .12 for v in rim]       # most of them off the belly's edge
    for k in range(min(streamers, len(rim))):
        long_ = k < 5           # coordinator: a few visibly longer, streaming back along her flanks
        v = rnd.choice(rim) if long_ else rnd.choices(rim, weights)[0]
        p0 = v.co + v.normal * length * .01
        ln = length * (rnd.uniform(.8, 1.1) if long_ else rnd.uniform(.3, .7)) * (.3 + .7 * grow)   # gone in a hull length
        rel = p0 - c
        perp = rel - W * rel.dot(W)
        r0 = length * (rnd.uniform(.004, .007) if long_ else rnd.uniform(.0015, .004))
        hug = .3 if long_ else .8          # a long flame keeps to her flank, then closes
        pts, us, radii = [], [], []
        for j in range(16):
            s = 1 - j / 15                                   # tail first: s 1 at the tail, 0 at the edge
            wob = noise.noise_vector(p0 / (length * .2) + Vector((s * (6.0 if long_ else 2.6), k * .37, 0)))                 * (ln * .14 if long_ else length * .06) * s
            pts.append(p0 + W * ln * s - perp * hug * (1 - math.exp(-5 * s)) + wob)
            us.append(s)
            radii.append(r0 * 2.2 * (1 - .75 * s))        # wide at the root, tapering out
        tube = _ragged_tube(f'{name} flame {k}', pts, radii, us, coll,
                            _flame_material(f'{PREFIX} flame', glow * (rnd.uniform(5, 9) if long_ else rnd.uniform(8, 18)),
                                            length * .05),
                            rnd, segments=10)
        for p in tube.data.polygons:
            p.use_smooth = True
    if wake:
        # owner, 2026-10-22: the burn-up gas behind her was not the size of her flames or her structure.
        # It is sized from her own cross-section across the airflow: it starts as wide as the flames
        # converge (they close to a fifth of her half-width) and opens to a little more than her width.
        ext = max((v.co - c).dot(W) for v in skin.verts)
        half = max(((v.co - c) - W * (v.co - c).dot(W)).length for v in skin.verts)
        span = length * (.8 + 2.6 * grow)
        _wake_volume(f'{name} wake', c + W * (ext - length * .05), W, span, half * .35, half * 1.15, coll,
                     10.0 * glow, half * .25, seed)
    if lights is not None:
        _glow_lights(name, skin, c, hot, length, glow, lights)
    skin.free()
    return c


def rock(name, centre, size, seed, coll, turn=0.0):
    """A charred fragment for the wreckfall: a lumpy, flattened rock of about size metres, dark. Returns
    its (verts, faces) in world space, for its sheath."""
    import random
    from mathutils import Euler, noise
    rnd = random.Random(seed)
    bm = bmesh.new()
    bmesh.ops.create_icosphere(bm, subdivisions=2, radius=size * .5)
    sq = Vector((rnd.uniform(.6, 1.0), rnd.uniform(.35, .8), rnd.uniform(.25, .6)))
    rot = Vector((rnd.uniform(0, 6.3), rnd.uniform(0, 6.3), rnd.uniform(0, 6.3)))
    R = Euler(rot + Vector((turn, turn * .6, 0))).to_matrix()
    for v in bm.verts:
        d = v.co.normalized()
        v.co = R @ Vector((v.co.x * sq.x, v.co.y * sq.y, v.co.z * sq.z)) * (1 + .45 * noise.noise(d * 1.7 + rot)) \
            + Vector(centre)
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    mat = bpy.data.materials.get(f'{PREFIX} char')
    if mat is None:
        mat = bpy.data.materials.new(f'{PREFIX} char')
        mat.use_nodes = True
        b = mat.node_tree.nodes['Principled BSDF']
        b.inputs['Base Color'].default_value = (.03, .025, .022, 1)
        b.inputs['Roughness'].default_value = .9
    me.materials.append(mat)
    o = bpy.data.objects.new(name, me)
    coll.objects.link(o)
    return [v.co.copy() for v in me.vertices], [tuple(p.vertices) for p in me.polygons]
