"""Bursts: the kit's one explosion language, irregular and short-lived, never an orb.

Owner rule (2026-10-21): "In general I don't like orb explosions — they don't make sense." In
vacuum nothing billows into a round fireball, so every detonation the Asharu work keys (canister
flak, a missile hit or kill, a torpedo, an HE shell or a breach on armour, a cook-off blast that
opts in) is built here, in the flak's language:

- **Flash:** a jagged, lobed mesh (a sphere pushed out into a few broad lobes and a few sharp
  spikes in seeded directions), white-hot for a frame and gone in about three. Given an `axis`
  (a surface normal, a missile's heading reversed) the lobes lean along it, so a blast on a hull
  throws out of the face rather than round it.
- **Shrapnel:** fragments along the spikes and a few loose, leaving white-hot and cooling
  through orange and dull red to dark within about a third of their flight, then flying on as
  dark fragments (vacuum: straight, no drag).
- **Smoke:** an uneven puff on a lumpier mesh of its own, its density broken up by noise so it
  reads torn and patchy, thinning out in half a second (longer for a big blast); no billow.

Size, lobes, spikes, turn, stretch and fragment pattern are all seeded per burst (`rnd`), so no
two match. Everything is keyed from the burst's frame at build time, in the caller's collection
space (`here`, hull units; `mpu` metres per unit), so a burst belongs to the ship that keys it.

Callers: `swfx_pd` (flak), `swfx_missile_paths` (hits, kills, decoys, ignition), `swfx_impacts`
(kinetic and HE flashes, breaches), `swfx_fire` (explosion anchors with style 'jagged').
"""
import math

from mathutils import Matrix, Quaternion, Vector

PREFIX = 'SWFX burst'
SMOKE_RGB = (.07, .052, .034)           # soot: vanilla flak cloud 155,125,75 darkened, so a lit puff
                                        # reads as smoke and not as a sunlit rock (first sweep render)


def _bpy():
    import bpy
    return bpy


def srgb(c):
    import swfx_missile_paths as mp
    return mp.srgb(c)


def _rod_mesh(name, mat, sides=8):
    """A unit rod from x=-1 to 0 (radius 1): scaled to a fragment."""
    bpy = _bpy()
    me = bpy.data.meshes.get(name)
    if me:
        return me
    import swfx_guns
    return swfx_guns._cylinder_mesh(name, 1.0, -1.0, 0.0, sides, mat)


def lobed_mesh(name, mat, rnd, lobes=(4, 7), spikes=(2, 5), lean=0.0, segs=20, rings=10):
    """An irregular burst shape: a sphere pushed out into a few broad lobes and a few sharp
    spikes in seeded directions, leaning toward local +X by `lean` (0 all round, 1.5 mostly
    forward). Returns (mesh, [(direction, amount, sharpness)]); a spike's sharpness is over 15."""
    bpy = _bpy()

    def pick():
        return (Vector((rnd.gauss(0, 1), rnd.gauss(0, 1), rnd.gauss(0, 1))).normalized()
                + Vector((lean, 0, 0))).normalized()

    dirs = [(pick(), rnd.uniform(.35, .8), rnd.uniform(3.0, 6.0)) for _ in range(rnd.randint(*lobes))]
    dirs += [(pick(), rnd.uniform(.8, 1.5), rnd.uniform(18.0, 40.0)) for _ in range(rnd.randint(*spikes))]

    def radius(v):
        r = .55 + sum(a * max(0.0, v.dot(d)) ** p for d, a, p in dirs)
        return r * (1 + .12 * math.sin(7.3 * v.x + 3.1 * v.y) * math.cos(5.7 * v.z + 1.3 * v.x))

    verts = []
    for r in range(rings + 1):
        th = math.pi * r / rings
        for k in range(segs if 0 < r < rings else 1):
            ph = 2 * math.pi * k / segs
            v = Vector((math.sin(th) * math.cos(ph), math.sin(th) * math.sin(ph), math.cos(th)))
            verts.append(v * radius(v))
    faces = [(0, 1 + (k + 1) % segs, 1 + k) for k in range(segs)]
    for r in range(rings - 2):
        a0, b0 = 1 + r * segs, 1 + (r + 1) * segs
        for k in range(segs):
            k1 = (k + 1) % segs
            faces.append((a0 + k, a0 + k1, b0 + k1, b0 + k))
    last = len(verts) - 1
    base = 1 + (rings - 2) * segs
    faces += [(base + k, base + (k + 1) % segs, last) for k in range(segs)]
    me = bpy.data.meshes.new(name)
    me.from_pydata([tuple(v) for v in verts], [], faces)
    for poly in me.polygons:
        poly.use_smooth = True
    me.update()
    me.materials.append(mat)
    return me, dirs


def smoke_material(ctx, rgb=SMOKE_RGB):
    """The burst's smoke: dull, its density broken up by noise so the puff is patchy and torn
    rather than a ball. Object colour R is the opacity (keyed), B a seed that moves the noise."""
    bpy = _bpy()
    import swfx_core
    import swfx_guns
    name = f'{PREFIX} smoke ' + '-'.join(f'{c:.3f}' for c in rgb)
    m = bpy.data.materials.get(name)
    if m:
        return m
    m, nodes, links = swfx_core.node_material(name)
    g = swfx_guns._G(nodes, links)
    out = nodes.new('ShaderNodeOutputMaterial')
    tc = nodes.new('ShaderNodeTexCoord')
    info = nodes.new('ShaderNodeObjectInfo')
    sep = nodes.new('ShaderNodeSeparateColor')
    links.new(info.outputs['Color'], sep.inputs[0])
    lw = nodes.new('ShaderNodeLayerWeight')
    lw.inputs['Blend'].default_value = .5
    c = g.clamp01(g.sub(1.0, lw.outputs['Facing']))
    n = g.noise(tc.outputs['Object'], g.mul(sep.outputs[2], 17.0), 2.6, 8.0, .75)
    holes = g.clamp01(g.mul(g.sub(n, .47), 2.6))
    # Soft to the silhouette and torn through: no hard edge, so the mesh never reads as a solid.
    dens = g.clamp01(g.mul(g.mul(g.mul(g.pow(c, 2.6), holes), sep.outputs[0]), .75))
    smoke = nodes.new('ShaderNodeBsdfDiffuse')
    smoke.inputs['Color'].default_value = (*rgb, 1)
    tr = nodes.new('ShaderNodeBsdfTransparent')
    mix = nodes.new('ShaderNodeMixShader')
    links.new(dens, mix.inputs['Fac'])
    links.new(tr.outputs[0], mix.inputs[1])
    links.new(smoke.outputs[0], mix.inputs[2])
    links.new(mix.outputs[0], out.inputs['Surface'])
    try:
        m.surface_render_method = 'BLENDED'
    except Exception:
        pass
    return m


def flash_material(ctx, rgb):
    """The burst's opening flash (owner, 2026-10-21): a near-white core grading out through hot
    yellow to orange, the outer band leaning half toward the blast's own colour (a Harpoon's
    blue, a torpedo's red), feathered to nothing at the silhouette with no rim. The jagged lobes
    give the shape; the emission stays modest, so the extra brightness is left to the camera's
    glare and bloom rather than to a large bright solid. Object colour R is the intensity
    (keyed), G pushes the grade toward the core while the flash is young, and B is how whole the
    flash still is, stored as (whole + 1) / 2: 1 (the default, unkeyed) a whole lobed body; below 1
    it tears into radial tongues and loses its white heart (owner, 2026-10-23: an HE blast is a short bright flash
    that breaks into ragged, directional debris and fire, no white body that holds)."""
    bpy = _bpy()
    import swfx_core
    import swfx_guns
    name = f'{PREFIX} flash ' + '-'.join(f'{c:.3f}' for c in rgb)
    m = bpy.data.materials.get(name)
    if m:
        return m
    m, nodes, links = swfx_core.node_material(name)
    g = swfx_guns._G(nodes, links)
    out = nodes.new('ShaderNodeOutputMaterial')
    info = nodes.new('ShaderNodeObjectInfo')
    sep = nodes.new('ShaderNodeSeparateColor')
    links.new(info.outputs['Color'], sep.inputs[0])
    lw = nodes.new('ShaderNodeLayerWeight')
    lw.inputs['Blend'].default_value = .35
    c = g.clamp01(g.sub(1.0, lw.outputs['Facing']))          # 1 facing the camera, 0 at the silhouette
    feather = g.pow(c, 2.4)                                  # soft to the edge, no rim
    whole = g.sub(g.mul(sep.outputs[2], 2.0), 1.0)           # B 1 is 1; B below .5 tears past 1
    tear = g.sub(1.0, whole)
    grade = g.clamp01(g.add(g.mul(g.pow(c, 1.6), g.add(g.mul(whole, .55), .45)), g.mul(sep.outputs[1], .35)))
    # Tongues: noise over the direction from the centre, so the holes run out radially and what
    # is left of the body is rays thrown outward rather than a shrinking ball. Whole (B 1): 1.
    tc = nodes.new('ShaderNodeTexCoord')
    nrm = nodes.new('ShaderNodeVectorMath')
    nrm.operation = 'NORMALIZE'
    links.new(tc.outputs['Object'], nrm.inputs[0])
    ray = nodes.new('ShaderNodeVectorMath')
    ray.operation = 'MULTIPLY_ADD'
    links.new(nrm.outputs[0], ray.inputs[0])
    ray.inputs[1].default_value = (2.0, 2.0, 2.0)
    links.new(tc.outputs['Object'], ray.inputs[2])
    n = g.noise(ray.outputs[0], g.mul(info.outputs['Random'], 40.0), 1.7, 4.0, .65)
    # Torn (tear near 1) the cut is clean: what the noise drops goes to nothing, not to a dim glow
    # that keeps the body's outline (bench, 2026-10-23: a big burst's +1 read as a brown plate).
    keep = g.clamp01(g.mul(g.sub(n, g.add(g.mul(tear, .3), .2)), 5.0))
    holes = g.clamp01(g.sub(1.0, g.mul(g.mul(tear, 1.6), g.sub(1.0, keep))))
    ramp = nodes.new('ShaderNodeValToRGB')
    els = ramp.color_ramp.elements
    rim = tuple(.5 * a + .5 * b for a, b in zip((1.0, .30, .05), rgb))   # orange leaning to the blast colour
    els[0].position, els[0].color = 0.0, (*rim, 1)
    els[1].position, els[1].color = 1.0, (1.0, .97, .9, 1)            # near-white core
    for pos, col in ((.35, (1.0, .42, .08)), (.68, (1.0, .74, .28))):  # orange, hot yellow
        e = els.new(pos)
        e.color = (*col, 1)
    links.new(grade, ramp.inputs['Fac'])
    em = nodes.new('ShaderNodeEmission')
    links.new(ramp.outputs['Color'], em.inputs['Color'])
    links.new(g.mul(g.mul(g.mul(feather, sep.outputs[0]), holes), 4.0), em.inputs['Strength'])
    tr = nodes.new('ShaderNodeBsdfTransparent')
    add = nodes.new('ShaderNodeAddShader')
    links.new(tr.outputs[0], add.inputs[0])
    links.new(em.outputs[0], add.inputs[1])
    links.new(add.outputs[0], out.inputs['Surface'])
    try:
        m.surface_render_method = 'BLENDED'
    except Exception:
        pass
    return m


def retime(objects, frame):
    """Move bursts keyed for one frame onto another: every key of each object (and of a light's
    data) shifts by the difference from its 'swfx_burst_t0', which then reads `frame`. For a
    burst whose frame is a knob the scene sets later (a wreck's break_frame). Returns the shift."""
    import swfx_guns
    shift = 0
    for ob in objects:
        t0 = ob.get('swfx_burst_t0')
        if t0 is None:
            continue
        base = ob.get('swfx_burst_base', t0)          # a cluster's own frame, its pieces keyed after it
        shift = int(frame) - int(base)
        ob['swfx_burst_base'] = int(frame)
        frame_now = int(t0) + shift
        if shift:
            for idb in (ob, getattr(ob, 'data', None)):
                for fc in swfx_guns._fcurves(idb) if idb is not None else ():
                    for kp in fc.keyframe_points:
                        kp.co.x += shift
                        kp.handle_left.x += shift
                        kp.handle_right.x += shift
                    fc.update()
        ob['swfx_burst_t0'] = frame_now
    return shift


def shrapnel_material(ctx):
    """A fragment that cools in flight. Object colour R is its heat (keyed, 1 to 0): white-hot,
    then orange, dull red and dark, the glow falling off with the heat. Under the glow the
    fragment is a dark solid, so it flies on unlit once the glow has gone."""
    bpy = _bpy()
    import swfx_core
    import swfx_guns
    name = f'{PREFIX} shrapnel'
    m = bpy.data.materials.get(name)
    if m:
        return m
    m, nodes, links = swfx_core.node_material(name)
    g = swfx_guns._G(nodes, links)
    out = nodes.new('ShaderNodeOutputMaterial')
    info = nodes.new('ShaderNodeObjectInfo')
    sep = nodes.new('ShaderNodeSeparateColor')
    links.new(info.outputs['Color'], sep.inputs[0])
    heat = g.clamp01(sep.outputs[0])
    ramp = nodes.new('ShaderNodeValToRGB')
    els = ramp.color_ramp.elements
    els[0].position, els[0].color = 0.0, (0, 0, 0, 1)
    els[1].position, els[1].color = 1.0, (1.0, .95, .85, 1)
    for pos, rgb in ((.22, (90, 18, 8)), (.45, (200, 60, 15)), (.72, (255, 160, 60))):
        e = els.new(pos)
        e.color = (*srgb(rgb), 1)
    links.new(heat, ramp.inputs['Fac'])
    em = nodes.new('ShaderNodeEmission')
    links.new(ramp.outputs['Color'], em.inputs['Color'])
    links.new(g.mul(g.pow(heat, 1.5), 40.0), em.inputs['Strength'])
    body = nodes.new('ShaderNodeBsdfDiffuse')
    body.inputs['Color'].default_value = (.05, .045, .04, 1)
    add = nodes.new('ShaderNodeAddShader')
    links.new(body.outputs[0], add.inputs[0])
    links.new(em.outputs[0], add.inputs[1])
    links.new(add.outputs[0], out.inputs['Surface'])
    return m


def burst(ctx, coll, tag, here, f, mpu, rnd, flash_rgb, size_m=(5.0, 9.5), axis=None, lean=1.1, light_w=0.0,
          shrapnel=(2, 4), smoke=.55, smoke_life=(10, 14), smoke_rgb=SMOKE_RGB, flash_frames=3, parent=None,
          prefix=PREFIX, tear=False, smoke_drift=0.0, white=True):
    """One burst at `here` (collection space, hull units) on frame f. size_m: the flash's
    radius range in metres (the scale everything else follows). axis: a collection-space
    direction the burst throws along (a surface normal; None all round), with `lean` how far.
    shrapnel: loose fragments added to the spikes' own (None or (0, 0) for none). smoke: the
    puff's peak opacity (0 none) over smoke_life frames. light_w: a flash light (W, hull-unit
    file). parent: an object to hang everything from (it then rides with it). tear: the flash is
    whole and white-hot for its first frame only, then tears into orange tongues thrown outward
    over the rest of flash_frames (an HE blast); False keeps it whole as it fades. white: with tear,
    False opens already torn, with no white frame. smoke_drift: the
    smoke fades in over three frames and flies out along the throw by that many flash radii over its
    life, so it is torn off the blast rather than a body left where the flash was. Returns objects."""
    bpy = _bpy()
    import swfx_core
    import swfx_guns
    import swfx_missile_paths as mp
    here = Vector(here)
    size = rnd.uniform(*size_m) / mpu
    k = (sum(size_m) / 2) / 7.25                  # 1 for a flak burst
    stretch = (1.0, rnd.uniform(.55, .9), rnd.uniform(.45, .8))
    if axis is not None:
        q = Vector(axis).normalized().to_track_quat('X', 'Z') @ Quaternion((1, 0, 0), rnd.uniform(0, 2 * math.pi))
        rot = q.to_euler()
        lean_now = lean
    else:
        rot = (rnd.uniform(0, 2 * math.pi), rnd.uniform(0, 2 * math.pi), rnd.uniform(0, 2 * math.pi))
        lean_now = 0.0
    out = []
    # The flash: hot, jagged, gone in flash_frames.
    # Its own material: hot yellow-white heart, the blast's colour out to a feathered edge (the
    # kit's glow ball rendered the lobed mesh as flat white cut-outs, first sweep render).
    glow = flash_material(ctx, flash_rgb)
    me, dirs = lobed_mesh(f'{prefix} {tag} flash mesh', glow, rnd, lean=lean_now)
    fl = bpy.data.objects.new(f'{prefix} {tag}', me)
    coll.objects.link(fl)
    fl.location = here
    fl.rotation_euler = rot
    fl.visible_shadow = False
    n_fl = max(2, int(flash_frames))
    fr = [f + j for j in range(n_fl + 1)]
    if tear:
        # One whole white-hot frame, then torn orange tongues flying out and fading.
        # Whole goes below zero by the third frame so the tear keeps eating the body: a big
        # burst (a Harpoon's, a cook-off) left at a part-torn .4 read as a solid mottled plate for
        # two frames (bench, 2026-10-23). white=False skips the white frame (a cook-off's
        # satellites, which otherwise pop white one after another).
        grow = [.6 + 1.0 * (1 - math.exp(-2.0 * j / n_fl)) for j in range(n_fl + 1)]
        heat = [1.7 if white else 1.1] + [1.0 * (1 - j / n_fl) ** 1.2 for j in range(1, n_fl + 1)]
        young = [.35 if white else 0.0] + [0.0] * n_fl
        whole = [1.0 if white else .1] + [.1 - .2 * (j - 1) for j in range(1, n_fl + 1)]
        swfx_guns._bake(fl, 'color', 2, fr, [(w + 1) / 2 for w in whole])     # stored as (whole + 1) / 2
    else:
        grow = [.55 + .65 * (1 - math.exp(-2.5 * j / n_fl)) for j in range(n_fl + 1)]
        heat = [1.7 * (1 - j / n_fl) ** 1.3 for j in range(n_fl + 1)]
        young = [.35 * (1 - j / n_fl) for j in range(n_fl + 1)]
    for i in range(3):
        swfx_guns._bake(fl, 'scale', i, fr, [size * v * stretch[i] for v in grow])
    swfx_guns._bake(fl, 'color', 0, fr, heat)
    swfx_guns._bake(fl, 'color', 1, fr, young)
    swfx_guns._visible_between(fl, f, f + n_fl - 1)
    out.append(fl)
    # Shrapnel along the spikes and a few loose: short and white-hot at the core, dark within
    # about a third of its flight, then flying on as dark fragments.
    if shrapnel is not None:
        turn = fl.rotation_euler.to_matrix()
        spikes = [turn @ Vector((d.x * stretch[0], d.y * stretch[1], d.z * stretch[2])) for d, a, p in dirs if p > 15]
        lo, hi = shrapnel
        loose = [turn @ (Vector((rnd.gauss(0, 1), rnd.gauss(0, 1), rnd.gauss(0, 1))).normalized()
                         + Vector((lean_now * .7, 0, 0))) for _ in range(rnd.randint(lo, hi))] if hi > 0 else []
        rod = _rod_mesh(f'{prefix} shrapnel mesh', shrapnel_material(ctx))
        for j, d in enumerate(spikes + loose):
            d = d.normalized()
            speed = rnd.uniform(9.0, 18.0) * k ** .7 / mpu          # outward, per frame
            hot_len = rnd.uniform(2.0, 4.0) * k ** .8 / mpu
            frag = rnd.uniform(.5, 1.2) * k ** .6 / mpu
            width = .2 * k ** .5 / mpu
            n = rnd.randint(9, 12)
            cool = n / 3.0 * rnd.uniform(.8, 1.1)
            heat = [max(0.0, 1 - i / cool) ** 1.6 for i in range(n + 1)]
            st = bpy.data.objects.new(f'{prefix} {tag} shrapnel {j}', rod)
            coll.objects.link(st)
            st.visible_shadow = False
            st.rotation_mode = 'QUATERNION'
            st.rotation_quaternion = d.to_track_quat('X', 'Z')
            frames = list(range(f, f + n + 1))
            tips = [here + d * (size * .5 + speed * i) for i in range(n + 1)]
            for i in range(3):
                swfx_guns._bake(st, 'location', i, frames, [p[i] for p in tips])
            swfx_guns._bake(st, 'scale', 0, frames,
                            [min(size * .3 + speed * i, frag + (hot_len - frag) * h) for i, h in enumerate(heat)])
            swfx_guns._bake(st, 'scale', 1, frames, [width] * len(frames))
            swfx_guns._bake(st, 'scale', 2, frames, [width] * len(frames))
            swfx_guns._bake(st, 'color', 0, frames, heat)
            st.color = (1, 0, 0, 1)
            swfx_guns._visible_between(st, f, f + n)
            out.append(st)
    if smoke:
        sm_me, _ = lobed_mesh(f'{prefix} {tag} smoke mesh', smoke_material(ctx, smoke_rgb), rnd, lobes=(5, 8),
                              spikes=(0, 1), lean=lean_now * .6)
        sm = bpy.data.objects.new(f'{prefix} {tag} smoke', sm_me)
        coll.objects.link(sm)
        sm.location = here
        sm.rotation_euler = (rot[0] + rnd.uniform(-.4, .4), rot[1] + rnd.uniform(-.2, .2), rot[2] + rnd.uniform(-.4, .4))
        sm.visible_shadow = False
        life = rnd.randint(*smoke_life)
        fr = [f + 1 + i for i in range(life + 1)]
        grow = [size * 1.15 * (.6 + .6 * (1 - math.exp(-3.0 * i / life))) for i in range(life + 1)]
        for i in range(3):
            swfx_guns._bake(sm, 'scale', i, fr, [v * (.8 + .2 * stretch[i]) for v in grow])
        fade = [min(1.0, (i + 1) / 3.0) if smoke_drift else 1.0 for i in range(life + 1)]
        swfx_guns._bake(sm, 'color', 0, fr, [smoke * a * (1 - i / life) ** 1.5 for i, a in zip(range(life + 1), fade)])
        if smoke_drift:
            out_dir = (Vector(axis).normalized() if axis is not None else Vector((0, 0, 0)))
            path = [here + out_dir * size * smoke_drift * (1 - math.exp(-2.5 * i / life)) for i in range(life + 1)]
            for i in range(3):
                swfx_guns._bake(sm, 'location', i, fr, [p[i] for p in path])
        swfx_guns._bake(sm, 'color', 2, [f + 1], [rnd.random()], 'CONSTANT')
        swfx_guns._visible_between(sm, f + 1, f + life)
        out.append(sm)
    if light_w:
        lt = swfx_core.point_light(f'{prefix} {tag} light', coll, flash_rgb, 0.0, radius=size * .3)
        lt.location = here
        for fi, e in ((f - 1, 0.0), (f, light_w), (f + 1, light_w * .45), (f + 2, light_w * .1), (f + 3, 0.0)):
            lt.data.energy = e
            lt.data.keyframe_insert('energy', frame=fi)
        out.append(lt)
    for ob in out:
        ob['swfx_burst_t0'] = int(f)
    if parent is not None:
        inv = mp.anchor_matrix(parent).inverted()      # its parent chain: excluded libraries never evaluate
        for ob in out:
            ob.parent = parent
            ob.matrix_parent_inverse = inv
    return out


def warhead(ctx, coll, tag, here, f, mpu, rnd, flash_rgb, core_m, axis=None, tongues=(5, 7), reach=(.8, 1.6),
            tongue_k=(.35, .5), late=0, late_s=1.0, shrapnel=(4, 7), smoke=.6, smoke_life=(24, 32),
            smoke_rgb=SMOKE_RGB, light_w=0.0, spall=0, glow_w=0.0, parent=None, prefix=PREFIX):
    """A big blast built the way the approved HE and Hellbore hits are, scaled by adding pieces
    rather than by inflating one (coordinator, 2026-10-23: a Harpoon hit torn out of one big flash
    read as a hard polygon plate, then a leopard-print solid, then nothing). A small hot core
    (core_m, a Hellbore-sized burst with its one white frame), torn fire tongues thrown outward round
    it (no white frame of their own, opening over the core's first two frames, each with a puff),
    `late` flickers of burning debris out to late_s seconds, and the core's smoke held smoke_life
    frames and drifting off along the throw. What holds an HE hit on screen for a second is its
    glowing crater lighting the smoke warm from below and its slow glowing spall; a warhead burst in
    the open has no crater, so glow_w puts a warm light in the smoke fading out over late_s, and
    spall throws that many slow fragments that glow orange and cool over half a second to a second.
    here: collection space; returns the objects."""
    ax = Vector(axis).normalized() if axis is not None else None
    out = burst(ctx, coll, f'{tag} core', here, f, mpu, rnd, flash_rgb, size_m=core_m, axis=axis, lean=1.2,
                shrapnel=shrapnel, smoke=smoke, smoke_life=smoke_life, smoke_rgb=smoke_rgb, flash_frames=5,
                light_w=light_w, parent=parent, prefix=prefix, tear=True, smoke_drift=1.2)
    R = sum(core_m) / 2 / mpu                       # the core's radius, hull units

    def outward(spread):
        d = Vector((rnd.gauss(0, 1), rnd.gauss(0, 1), rnd.gauss(0, 1))).normalized()
        return (d * spread + ax).normalized() if ax is not None else d

    tongue_life = (max(6, int(smoke_life[0] * .5)), max(8, int(smoke_life[1] * .6)))
    for k in range(rnd.randint(*tongues)):
        d = outward(.9)
        s = sum(core_m) / 2 * rnd.uniform(*tongue_k)
        out += burst(ctx, coll, f'{tag} tongue {k}', here + d * R * rnd.uniform(*reach), f + rnd.randint(0, 2), mpu,
                     rnd, flash_rgb, size_m=(s * .85, s * 1.15), axis=d, lean=1.0, shrapnel=(0, 1),
                     smoke=smoke * .5, smoke_life=tongue_life, smoke_rgb=smoke_rgb, flash_frames=3, parent=parent,
                     prefix=prefix, tear=True, smoke_drift=1.0, white=False)
    fps = ctx.scene.render.fps or 24
    for k in range(late):
        d = outward(1.4)
        s = sum(core_m) / 2 * rnd.uniform(.16, .26)
        at = f + 3 + int(round((k + rnd.random()) / max(1, late) * (late_s * fps - 3)))
        out += burst(ctx, coll, f'{tag} fire {k}', here + d * R * rnd.uniform(.4, 1.3), at, mpu, rnd,
                     (1.0, .45, .12), size_m=(s * .85, s * 1.15), axis=d, lean=.8, shrapnel=None, smoke=smoke * .35,
                     smoke_life=(8, 12), smoke_rgb=smoke_rgb, flash_frames=3, parent=parent, prefix=prefix,
                     tear=True, smoke_drift=.8, white=False)
    bpy = _bpy()
    import swfx_core
    import swfx_guns
    extra = []
    if spall:
        rod = _rod_mesh(f'{prefix} shrapnel mesh', shrapnel_material(ctx))
        k = sum(core_m) / 2 / 4.5                       # 1 for a Hellbore-sized core
        for j in range(spall):
            d = outward(1.2)
            speed = rnd.uniform(6.0, 24.0) * k ** .5 / mpu / fps          # per frame (an HE hit's spall)
            n = rnd.randint(int(.4 * fps), int(1.2 * fps))
            st = bpy.data.objects.new(f'{prefix} {tag} spall {j}', rod)
            coll.objects.link(st)
            st.visible_shadow = False
            st.rotation_mode = 'QUATERNION'
            st.rotation_quaternion = d.to_track_quat('X', 'Z')
            frames = list(range(f, f + n + 1))
            start = here + d * R * rnd.uniform(.1, .5)
            for i in range(3):
                swfx_guns._bake(st, 'location', i, frames, [(start + d * speed * t)[i] for t in range(n + 1)])
            # a thin streak the length of a frame and a half of its travel, as an HE hit's spall is drawn
            # (bench7: fat 0.5 m rods read as a heap of pink blocks)
            width = .07 * k ** .5 / mpu
            swfx_guns._bake(st, 'scale', 0, frames, [max(speed * 1.5, width * 4)] * len(frames))
            for i in (1, 2):
                swfx_guns._bake(st, 'scale', i, frames, [width] * len(frames))
            swfx_guns._bake(st, 'color', 0, frames, [.75 * (1 - t / n) ** 1.3 for t in range(n + 1)])
            st.color = (.75, 0, 0, 1)
            swfx_guns._visible_between(st, f, f + n)
            extra.append(st)
    if glow_w:
        lt = swfx_core.point_light(f'{prefix} {tag} glow', coll, (1.0, .5, .18), 0.0, radius=R * .6)
        lt.location = here + (ax * R * .6 if ax is not None else Vector())
        span = max(4, int(late_s * fps))
        keys = [(f - 1, 0.0)] + [(f + i, glow_w * math.exp(-2.6 * i / span) * (1 - i / span))
                                 for i in range(0, span + 1, 2)]
        for fi, e in keys:
            lt.data.energy = e
            lt.data.keyframe_insert('energy', frame=fi)
        # shown only while it burns (dark either side): a light shown from the shot's first frame
        # read to the motion audit as born there, wherever its ship then was
        swfx_guns._visible_between(lt, f, f + span)
        extra.append(lt)
    for ob in extra:
        ob['swfx_burst_t0'] = int(f)
    if parent is not None:
        import swfx_missile_paths as mp
        inv = mp.anchor_matrix(parent).inverted()
        for ob in extra:
            ob.parent = parent
            ob.matrix_parent_inverse = inv
    return out + extra
