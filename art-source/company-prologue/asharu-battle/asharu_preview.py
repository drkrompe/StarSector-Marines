"""Asharu battle: the whole sequence (or a range of shots) as a motion preview (motion_preview.py).

    python battle_blockout.py --motion-preview                    # every shot, every frame, half size
    python battle_blockout.py --motion-preview --step 2           # 12 fps (the light-lane default)
    python battle_blockout.py --motion-preview --shots S6:S7      # a range of shots, by the shot list's ids
    python battle_blockout.py --motion-preview --weave-all        # authored poses weave about their pose too
    python battle_blockout.py --motion-preview --dry              # stage and key everything, render nothing
    python battle_blockout.py --motion-preview ... --post windows # post to review/cinematic-asharu-battle/
    python asharu_preview.py --job <dir> --date D [--step 2]      # write the light-lane queue job

The sequence is the shot list in asharu-battle-shot.md, in order and at its lengths. A shot with a
key frame is built by battle_blockout.py's own scene build for that key frame (same hulls, places,
camera and planet) and then played over the shot's whole length:

- every ship flies the motion its key frame gives it, on the cinematic's own clock: a weave about
  her station (motion.fleet_at), a flown run through attitude keys with hits, recoil and blinks
  (motion.keyed), a boat's run, or riding another ship (motion.carried, motion.riding). The key
  frame is one instant of that motion, marked KEY FRAME;
- a ship given only a plain pose holds it: the legend says "(held)". --weave-all weaves it about
  its pose instead;
- the camera flies the key frame's move (motion.camera: a push, a track, a chase, handheld
  drift), passing through the key frame's framing at the key time; a camera with no move holds;
- the planet turns beneath at orbital speed, registered to the still at the key time;
- effects are proxies on placeholder timing (no shot has authored effect timing yet): gunfire
  as dashed lines from the chosen muzzle, missiles as streaks, blasts as wire shells at the key
  moment, fires and smoke as cones, flares and debris as dots, shields as wire shells.

A shot without a key frame is a slate card for its length, so the movie runs at the sequence's
real timing. Run inside Blender, `shot(locals(), kf)` is the builder's mode hook.
"""
import importlib.util
import json
import math
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
PROLOGUE = HERE.parent
ART = PROLOGUE.parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(PROLOGUE / 'cinematic-tools'))
sys.path.insert(0, str(ART))
import motion  # noqa: E402
import motion_preview  # noqa: E402
import roster  # noqa: E402

FPS = 24
NAME = 'asharu-motion-preview'
SHOT_DOC = HERE / 'asharu-battle-shot.md'
PLUME_KIND = {'HIGH_TECH': 'plume-blue', 'MIDLINE': 'plume-white'}


# ------------------------------------------------------------------------------- the sequence

def shot_table():
    """The shot list's rows, in order: id, length, title, caption and start (seconds)."""
    text = SHOT_DOC.read_text(encoding='utf-8')
    text = text[text.index('## Shot list'):]
    text = text[:text.index('\n## ', 4)]
    rows, t = [], 0.0
    for line in text.splitlines():
        # a row may give its span in scene time, '31 s (-21 to 10)' (S0's pre-roll, S13): skipping it
        # put every later shot 10 s early (S7's camera over S6c's action)
        m = re.match(r'\|\s*(S\d+[a-z]?)(?:\s*\*\*K\*\*)?\s*\|\s*([\d.]+)\s*s\s*'
                     r'(?:\(\s*(-?[\d.]+)\s*to\s*-?[\d.]+\s*\)\s*)?\|\s*(.*?)\s*\|', line)
        if not m:
            continue
        if m.group(3) is not None:
            t = float(m.group(3))
        desc = m.group(4)
        b = re.match(r'\*\*(.+?)\*\*\s*(.*)', desc)
        title, caption = (b.group(1).rstrip('.'), b.group(2)) if b else (re.split(r'[:.] ', desc)[0], desc)
        rows.append(dict(id=m.group(1), dur=float(m.group(2)), title=title, caption=caption, start=t))
        t += float(m.group(2))
    return rows


def segments(keyframes):
    """The sequence as preview segments: a key frame's shot (or shot span, 'S8-S9') or a slate."""
    rows = shot_table()
    order = [r['id'] for r in rows]
    span = {}
    for kf, spec in keyframes.items():
        a, _, b = spec['shot'].partition('-')
        ids = order[order.index(a):order.index(b or a) + 1]
        span.setdefault(a, (kf, ids))     # the shot's own key frame, not a still copied from it (k6c)
    covered = {i for _, ids in span.values() for i in ids}
    segs = []
    for r in rows:
        if r['id'] in span:
            kf, ids = span[r['id']]
            dur = sum(x['dur'] for x in rows if x['id'] in ids)
            spec = keyframes[kf]
            segs.append(dict(id='%s %s' % (spec['shot'], kf), first=r['id'], last=ids[-1], kf=kf,
                             start=r['start'], end=r['start'] + dur, title=spec['title'], caption=spec['caption']))
        elif r['id'] not in covered:
            segs.append(dict(id=r['id'], first=r['id'], last=r['id'], kf=None, start=r['start'],
                             end=r['start'] + r['dur'], title=r['title'], caption=r['caption']))
    return segs


def pick(segs, shots):
    """--shots A:B (inclusive, by shot-list id) or A,B,C; None for all."""
    if not shots:
        return segs
    order = [r['id'] for r in shot_table()]
    if ':' in shots:
        a, b = shots.split(':')
        want = set(order[order.index(a):order.index(b) + 1])
    else:
        want = set(shots.split(','))
    return [s for s in segs if s['first'] in want or s['last'] in want or (s['kf'] and s['kf'] in want)]


class Drift(tuple):
    """A pose one of motion's functions made (fleet_at's weave, a boarding_run), still as posed,
    that knows the time t it stills and how to pose the same ship at any other time: at(t)."""
    def __new__(cls, pose, t, at):
        o = tuple.__new__(cls, pose)
        o.t, o.at = t, at
        return o


def traced_keyframes():
    """battle_blockout.KEYFRAMES re-read with boarding_run traced: every pose it makes is a Drift, so
    the preview can play the motion the key frame stills. Fleet ships keep fleet_at's own live() poses,
    the finals' motion: tracing them as drift() let a blockout weave ships the finals crept (owner,
    2026-10-27: an approved blockout plays exactly what the final renders)."""
    real_run = getattr(motion, 'boarding_run', None)

    def boarding_run(*a, **kw):
        return Drift(real_run(*a, **kw), a[-1], lambda tt: real_run(*a[:-1], tt, **kw))
    if real_run is not None:
        motion.boarding_run = boarding_run
    try:
        spec = importlib.util.spec_from_file_location('asharu_traced', HERE / 'battle_blockout.py')
        mod = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(mod)
    finally:
        if real_run is not None:
            motion.boarding_run = real_run
    return mod.SHOTS


# ------------------------------------------------------------------------------- inside Blender

_STATE = {}


def _planet_flat(L, bb):
    """The desert map, unlit and flat: a UV map that repeats the material's direction lookup
    (Workbench reads an image through the mesh's UVs, not through shader maths). The air shell
    and the starfield are left out."""
    import numpy as np
    from mathutils import Euler, Matrix
    me = L['ground'].data
    n = len(me.vertices)
    co = np.empty(n * 3, dtype=np.float64)
    me.vertices.foreach_get('co', co)
    co = co.reshape(-1, 3)
    rot = (Euler((0, math.radians(90 - bb.MAP_LAT), math.radians(bb.MAP_LON)), 'XYZ').to_matrix()
           @ Matrix.Rotation(math.radians(90), 3, 'Z'))
    v = co @ np.array(rot).T
    v /= np.linalg.norm(v, axis=1)[:, None]
    u = np.arctan2(v[:, 1], v[:, 0]) / (2 * math.pi) + .5
    w = np.arcsin(np.clip(v[:, 2], -1, 1)) / math.pi + .5
    lv = np.empty(len(me.loops), dtype=np.int64)
    me.loops.foreach_get('vertex_index', lv)
    lu, lw = u[lv], w[lv]
    tot = np.empty(len(me.polygons), dtype=np.int64)
    me.polygons.foreach_get('loop_total', tot)
    face = np.repeat(np.arange(len(tot)), tot)
    lo = np.full(len(tot), 9.0)
    hi = np.full(len(tot), -9.0)
    np.minimum.at(lo, face, lu)
    np.maximum.at(hi, face, lu)
    wrap = (hi - lo)[face] > .5
    lu = np.where(wrap & (lu < .5), lu + 1.0, lu)
    uvl = me.uv_layers.new(name='Preview')
    uvl.data.foreach_set('uv', np.stack([lu, lw], 1).ravel().astype(np.float32))
    me.uv_layers.active = uvl
    nt = L['gm'].node_tree
    img = next(nd for nd in nt.nodes if nd.type == 'TEX_IMAGE' and nd.image and 'desert01' in nd.image.name)
    nt.nodes.active = img


DETAIL_PX = 4096      # the preview ground's detail image
DETAIL_KNEE = 120e3   # m: the detail mapping's knee; about 60 m a pixel under the battle, coarser outward


def _surface_detail(L, bb):
    """The desert under the battle at a resolution its orbital speed can be seen against. The game's
    map is about 15 km a texel, so 60 km of travel in a shot moves a few blurred texels: the convoy
    looks parked. One image covers the whole cap, radially compressed (g = k r / (r + k) from the
    point under the battle), so it is fine near the battle and coarse at the horizon: the map's
    colour times seeded fractal relief, with scattered bright specks a moving eye can hold on to.
    Judges speed, not the look; the lit stills carry the real surface tiles."""
    import bpy
    import numpy as np
    from mathutils import Euler, Matrix
    me, nt = L['ground'].data, L['gm'].node_tree
    src = next(nd for nd in nt.nodes if nd.type == 'TEX_IMAGE' and nd.image and 'desert01' in nd.image.name).image
    sw, sh = src.size
    sp = np.empty(sw * sh * 4, dtype=np.float32)
    src.pixels.foreach_get(sp)
    sp = sp.reshape(sh, sw, 4)
    rot = np.array(Euler((0, math.radians(90 - bb.MAP_LAT), math.radians(bb.MAP_LON)), 'XYZ').to_matrix()
                   @ Matrix.Rotation(math.radians(90), 3, 'Z'))
    n, r, k = DETAIL_PX, bb.R, DETAIL_KNEE
    rng = np.random.default_rng(7)
    fx, fy = np.fft.rfftfreq(n)[None, :], np.fft.fftfreq(n)[:, None]
    f = np.sqrt(fx * fx + fy * fy)
    f[0, 0] = 1.0
    spec = (rng.standard_normal(f.shape) + 1j * rng.standard_normal(f.shape)) / f ** 1.4
    spec[0, 0] = 0
    relief = np.fft.irfft2(spec, s=(n, n)).astype(np.float32)
    del spec, f
    relief = np.clip(.85 + .32 * relief / (relief.std() + 1e-9), .35, 1.5)
    ys, xs = rng.integers(0, n, (2, 6000))
    relief[ys, xs] = 1.3
    out = np.empty((n, n, 4), dtype=np.float32)
    out[..., 3] = 1.0
    for y0 in range(0, n, 512):
        yy, xx = np.mgrid[y0:y0 + 512, 0:n].astype(np.float64)
        gx, gy = ((xx + .5) / n - .5) * 2 * k, ((yy + .5) / n - .5) * 2 * k
        g = np.minimum(np.hypot(gx, gy), k * .999)
        phi = np.arctan2(gy, gx)
        c = np.minimum(g * k / (k - g) / r, math.radians(24.0))
        p = np.stack([np.sin(c) * np.cos(phi), np.sin(c) * np.sin(phi), np.cos(c)], -1) @ rot.T
        u = (np.arctan2(p[..., 1], p[..., 0]) / (2 * math.pi) + .5) * sw - .5
        w = (np.arcsin(np.clip(p[..., 2], -1, 1)) / math.pi + .5) * sh - .5
        x0, y0_ = np.floor(u).astype(np.int64), np.clip(np.floor(w).astype(np.int64), 0, sh - 2)
        ax, ay = (u - x0)[..., None], np.clip(w - y0_, 0, 1)[..., None]
        x0, x1 = x0 % sw, (x0 + 1) % sw
        col = ((sp[y0_, x0] * (1 - ax) + sp[y0_, x1] * ax) * (1 - ay)
               + (sp[y0_ + 1, x0] * (1 - ax) + sp[y0_ + 1, x1] * ax) * ay)
        out[y0:y0 + 512, :, :3] = col[..., :3] * relief[y0:y0 + 512, :, None]
    img = bpy.data.images.new('Preview surface', n, n, alpha=False)
    img.colorspace_settings.name = src.colorspace_settings.name
    img.pixels.foreach_set(np.clip(out, 0, 1).ravel())
    del out, relief
    node = nt.nodes.new('ShaderNodeTexImage')
    node.image = img
    nt.nodes.active = node
    co = np.empty(len(me.vertices) * 3, dtype=np.float64)
    me.vertices.foreach_get('co', co)
    co = co.reshape(-1, 3)
    c = np.arccos(np.clip(co[:, 2] / np.linalg.norm(co, axis=1), -1, 1))
    phi = np.arctan2(co[:, 1], co[:, 0])
    g = k * (r * c) / (r * c + k)
    uv = np.stack([.5 + g * np.cos(phi) / (2 * k), .5 + g * np.sin(phi) / (2 * k)], 1)
    lv = np.empty(len(me.loops), dtype=np.int64)
    me.loops.foreach_get('vertex_index', lv)
    layer = me.uv_layers.new(name='Preview detail')
    layer.data.foreach_set('uv', uv[lv].ravel().astype(np.float32))
    me.uv_layers.active = layer


def _star_dome(L):
    """Stars for the preview: a seeded field on an inside-out sphere far beyond the fight (inside the
    camera's clip), so a pan or a whip reads against them. The planet hides those below the limb."""
    import bmesh
    import bpy
    import numpy as np
    w, h = 4096, 2048
    rng = np.random.default_rng(11)
    px = np.zeros((h, w, 4), dtype=np.float32)
    px[..., 3] = 1.0
    ys, xs = rng.integers(0, h, 7000), rng.integers(0, w, 7000)
    lum = .35 + .65 * rng.random(7000) ** 3
    px[ys, xs, :3] = lum[:, None]
    big = lum > .8
    px[(ys[big] + 1) % h, xs[big], :3] = lum[big, None] * .7
    px[ys[big], (xs[big] + 1) % w, :3] = lum[big, None] * .7
    img = bpy.data.images.new('Preview stars', w, h, alpha=False)
    img.pixels.foreach_set(px.ravel())
    bm = bmesh.new()
    bm.loops.layers.uv.new('UVMap')        # create_uvsphere fills UVs only into an existing layer
    bmesh.ops.create_uvsphere(bm, u_segments=96, v_segments=48, radius=3.5e6, calc_uvs=True)
    bmesh.ops.reverse_faces(bm, faces=bm.faces[:])
    me = bpy.data.meshes.new('Preview stars')
    bm.to_mesh(me)
    bm.free()
    mat = bpy.data.materials.new('Preview stars')
    mat.use_nodes = True
    node = mat.node_tree.nodes.new('ShaderNodeTexImage')
    node.image = img
    mat.node_tree.nodes.active = node
    me.materials.append(mat)
    ob = bpy.data.objects.new('Preview stars', me)
    L['scene'].collection.objects.link(ob)


def label(members, key):
    """The hull's name, with its key when the convoy or the raiders have more than one of it."""
    name = members[key]['name']
    return name if sum(m['name'] == name for m in members.values()) == 1 else '%s (%s)' % (name, key)


def shot(L, kf):
    """battle_blockout's mode hook: L is blender_main's locals() with key frame kf staged."""
    import time
    print('PREVIEW %s staged by the builder at %s' % (kf, time.strftime('%H:%M:%S')), flush=True)
    import bpy
    from mathutils import Matrix, Vector
    bb = sys.modules['__main__']
    cfg = L['cfg']['motion_preview']
    if not _STATE:
        mp = motion_preview.Preview(L['scene'], Path(L['cfg']['out']) / 'preview', fps=FPS, step=cfg['step'],
                                    scale=cfg['scale'], dry=cfg.get('dry', False))
        mp.sky((0.004, 0.005, 0.008))
        traced = traced_keyframes()
        _STATE.update(mp=mp, traced=traced, segs={s['kf']: s for s in segments(traced) if s['kf']})
        _planet_flat(L, bb)
        _surface_detail(L, bb)
        _star_dome(L)
    mp, seg, spec = _STATE['mp'], _STATE['segs'][kf], _STATE['traced'][kf]
    placed, frame_of, stage = L['placed'], L['frame_of'], L['stage']
    weave_all = cfg.get('weave_all', False)
    pirates = {m['key'] for m in roster.PIRATE}
    ts, te = seg['start'], seg['end']
    dur = te - ts

    # the builder's stand-in effects go; the hulls are re-drawn by the preview, the platform and
    # the entry trails stay (the trails flat orange)
    for o in stage.all_objects:
        if o.name.startswith('Platform'):
            continue
        if o.name.startswith('Plasma'):
            mp._paint(o, mp.flat(motion_preview.FX_RGB['plume']))
            continue
        if not o.name.startswith('Ship | '):
            motion_preview.off(o)

    poses, t_key, held = {}, None, []
    for key, sp in spec['ships'].items():
        if isinstance(sp, Drift) or hasattr(sp, 'at'):      # motion's own poses (flight, keyed, riding) carry at()
            poses[key] = sp.at
            t_key = sp.t
        elif weave_all:
            poses[key] = lambda t, key=key, sp=sp: motion.drift(key, tuple(sp), t)
        else:
            poses[key] = lambda t, sp=sp: tuple(sp)
            held.append(key)

    def M(key, t):
        x, y, z, hd, pt, rl, _ = poses[key](t)
        return frame_of((x, y, z), hd, pt, rl)

    if t_key is not None and not (ts <= t_key < te):
        t_key = None
    tk = t_key if t_key is not None else ts + .4 * dur          # when the shot's effects peak
    notes = []
    if held:
        notes.append('%d of %d ships hold their key-frame pose (no path authored)' % (len(held), len(poses)))
    cam_at = spec['camera'].get('at')
    if cam_at is None:
        notes.append('camera held at the key frame\'s framing')
    if spec.get('fx') or spec.get('shields'):
        notes.append('effects: proxies on placeholder timing')
    if t_key is None and poses and not held:
        notes.append('key frame time outside this shot')
    mp.begin_shot(seg['id'], seg['title'], ts, te, caption=seg['caption'], key_s=t_key, notes=notes)

    for key in spec['ships']:
        pl = placed[key]
        e = next(o for o in stage.all_objects if o.name == 'Ship | ' + key)
        mpu = pl['mpu']
        mp.craft(key, e, label=label(L['members'], key), group='warm' if key in pirates else 'cool',
                 pose=lambda t, key=key, mpu=mpu: M(key, t) @ Matrix.Scale(mpu, 4), held=key in held)
        for i, (pos, dirn, rad, style) in enumerate(pl['lib']['engines']):
            def at(t, key=key, pos=pos, dirn=dirn, rad=rad, mpu=mpu):
                mw = M(key, t)
                return mw @ (pos * mpu), mw.to_3x3() @ dirn, max(rad * mpu, 1.0), poses[key](t)[6]
            mp.plume('%s %d' % (key, i), at, PLUME_KIND.get(style, 'plume'))

    # the debris field tumbles: every wreck turns slowly about its own axis (debris_field)
    if L.get('field_matrix') is not None:
        for o in list(L['scene'].objects):
            if 'field_piece' in o:
                mp.move(o, lambda t, i=int(o['field_piece']), j=int(o['field_box']): L['field_matrix'](i, j, t))

    # Draw only what the preview owns, the planet's surface, the platform and the entry trails:
    # a build's own effects (cook-off volumes, kit copies hidden in the viewport) and the air shell,
    # which the builder turns back on for every key frame, would otherwise draw as grey solids.
    keep, hid = {L['ground'].name, 'Preview stars'}, []
    live = set()                     # collections the render reaches (an excluded library never renders)

    def walk(lc):
        if not lc.exclude:
            live.add(lc.collection.name)
            for c in lc.children:
                walk(c)
    walk(bpy.context.view_layer.layer_collection)
    for o in L['scene'].objects:
        if not any(c.name in live for c in o.users_collection):
            continue
        if o.name in mp.coll.objects or o.name in keep or o.name.startswith(('Platform', 'Plasma')):
            continue
        if ((o.type in ('MESH', 'CURVE', 'SURFACE', 'META', 'FONT', 'VOLUME', 'POINTCLOUD', 'CURVES')
                and not o.hide_render) or o.instance_type == 'COLLECTION'):
            motion_preview.off(o)
            hid.append(o.name)
    print('PREVIEW %s hid %d more objects: %s' % (kf, len(hid), ', '.join(hid[:12])), flush=True)

    def point(key, local=(0, 0, 0)):
        local = Vector(local)
        return lambda t: M(key, t) @ local

    def muzzle(a, b, kind):
        """The muzzle the builder chose for this shot (a bearing gun with a clear line), carried
        with the ship, else her centre."""
        mz = L['muzzles'].get((kind, a, b))
        world = L['muzzle_world'](a, mz)
        return point(a, placed[a]['matrix'].inverted() @ world)

    rnd = __import__('random').Random(kf)
    for i, fx in enumerate(spec.get('fx', ())):
        kind = fx[0]
        name = '%s %d %s' % (kf, i, kind)
        if kind == 'tracer':
            src, dst = muzzle(fx[1], fx[2], kind), point(fx[2], fx[3] if len(fx) > 3 else (0, 0, 0))
            mp.tracer(name, lambda t, s=src, d=dst: (s(t), d(t)), ts + .08 * dur + rnd.uniform(0, .5), te - .2)
        elif kind == 'missiles':
            src, dst = muzzle(fx[1], fx[2], kind), point(fx[2])
            dist = (dst(tk) - src(tk)).length
            for j in range(5):
                launch = ts + .05 * dur + j * .3
                arrive = launch + max(1.5, dist / 1300.0)
                mp.missile('%s %d' % (name, j), lambda t, s=src, d=dst: (s(t), d(t)), launch, arrive)
                mp.burst('%s %d hit' % (name, j), dst, arrive, 12.0, life=.5)
        elif kind in ('blast_on', 'blast'):
            c = point(fx[1], fx[3] if len(fx) > 3 else (0, 0, 0))
            mp.burst(name, c, tk, fx[2] * 1.5)
            mp.burst(name + ' b', c, tk + .25, fx[2] * 2.2, life=1.0)
        elif kind == 'debris':
            c = point(fx[1], fx[3] if len(fx) > 3 else (0, 0, 0))
            dirs = [Vector((rnd.gauss(0, 1), rnd.gauss(0, 1), rnd.gauss(0, 1))).normalized() * rnd.uniform(.4, 1.0)
                    for _ in range(10)]
            mp.dots(name, lambda t, c=c, dirs=dirs, s=fx[2]: None if t < tk else
                    [c(t) + d * s * 1.6 * min(3.0, t - tk) for d in dirs], 'debris', n=10, size_px=3)
        elif kind in ('fire_on', 'smoke'):
            key = fx[1]
            d = Vector(fx[3] if len(fx) > 3 else ((-1, .4, .3) if kind == 'fire_on' else (-1, .2, .5))).normalized()
            c = point(key, fx[2])
            r, ln = (4.0, 28.0) if kind == 'fire_on' else (10.0, 160.0)
            mp.cone(name, lambda t, key=key, c=c, d=d, r=r, ln=ln: (c(t), M(key, t).to_3x3() @ d, r, ln),
                    'fire' if kind == 'fire_on' else 'smoke')
        elif kind == 'plasma':
            # entry plasma, a proxy until the effect exists: a sheath cone from the bow streaming aft,
            # growing from fx[2] to fx[3] seconds
            key, p0, p1 = fx[1], fx[2], fx[3]
            ln_ = placed[key]['length']

            def sheath(t, key=key, p0=p0, p1=p1, ln_=ln_):
                if t < p0:
                    return None
                u = min(1.0, (t - p0) / (p1 - p0))
                mw = M(key, t)
                return (mw @ Vector((ln_ * .55, 0, 0)), mw.to_3x3() @ Vector((-1, 0, 0)),
                        ln_ * (.08 + .17 * u), ln_ * (.25 + .9 * u))
            mp.cone(name, sheath, 'fire')
        elif kind == 'entry_sheath':
            # S12: the wake growing behind a falling hull along the shot's airflow; a proxy for
            # swfx_entry.sheath (the plasma on her windward faces is not drawn here)
            key = fx[1]
            ln_ = placed[key]['length']

            def pos(t, key=key):
                return M(key, t).to_translation()

            import __main__ as bb          # battle_blockout: the shot's authored airflow, K6_WIND
            wind = Vector(bb.K6_WIND)

            mp.line(name + ' trail', lambda t, ts=ts, ln_=ln_: (
                pos(t), pos(t) + wind * ln_ * min(4.0, 1.0 + (t - 126.0) * .8)), 'fire', width_px=6.0)
        elif kind == 'wreckfall':
            # S12: the ships the battle lost, coming down in pieces (battle_blockout.WRECKFALL)
            import __main__ as bb          # battle_blockout, running as Blender's script
            for pc in bb.WRECKFALL:
                def ends(t, pc=pc):
                    if t < pc['t_in']:
                        return None
                    age = min(t - pc['t_in'], 1.5 if pc['kind'] == 'chunk' else .8)
                    head = Vector([pc['at'][i] + pc['vel'][i] * (t - bb.K6_T) for i in range(3)])
                    ln = pc['size'] * (12.0 if pc['kind'] == 'chunk' else 25.0) * min(1.0, .3 + age)
                    return head, head + Vector(bb.K6_WIND) * ln
                mp.line('%s %s' % (name, pc['name']), ends, 'fire' if pc['kind'] == 'chunk' else 'flare',
                        width_px=3.0 if pc['kind'] == 'chunk' else 1.2)
        elif kind == 'flares':
            key = fx[1]
            offs = [Vector((rnd.uniform(-60, 40), rnd.choice((-1, 1)) * rnd.uniform(50, 120), rnd.uniform(-10, 40)))
                    for _ in range(10)]

            def flare(t, key=key, offs=offs):
                age = (t - ts) % 3.0
                if age > 2.2:
                    return None
                mw = M(key, t)
                return [mw @ (o * (.25 + age / 1.4)) for o in offs]
            mp.dots(name, flare, 'flare', n=10, size_px=4)
    for i, (drone, tgt, t0, t1, side, *local) in enumerate(spec.get('rakes', ())):
        # a Borer's mining laser (beam_t_ws001) swept along an attacker's hull as she passes: a beam
        # line from her nose to a point running stern-to-bow down the near side, sparks where it cuts
        if drone not in placed or tgt not in placed:
            continue
        half = placed[tgt]['length'] * .4

        def cut(t, tgt=tgt, t0=t0, t1=t1, side=side, half=half, local=local[0] if local else None):
            if not (t0 <= t <= t1):
                return None
            if local is not None:       # a skimming Borer's cut, given in the target's frame
                return M(tgt, t) @ Vector(local(t))
            u = (t - t0) / max(t1 - t0, 1e-3)
            return M(tgt, t) @ Vector((half * (1 - 2 * u), side * half * .15, 0))
        nose = point(drone, (2.0, 0, 0))
        mp.line('%s rake %d' % (kf, i), lambda t, c=cut, n=nose: None if c(t) is None else (n(t), c(t)),
                'beam', width_px=4.0)

        def sparks(t, c=cut, i=i):
            e = c(t)
            if e is None:
                return None
            r = __import__('random').Random(int(t * 24) * 31 + i)
            return [e + Vector((r.gauss(0, 1), r.gauss(0, 1), r.gauss(0, 1))) * r.uniform(2, 14) for _ in range(10)]
        mp.dots('%s rake %d sparks' % (kf, i), sparks, 'flare', n=10, size_px=5)
    for key, sh in spec.get('shields', {}).items():
        tw = sh.get('toward')
        mp.shield('%s shield %s' % (kf, key), point(key), placed[key]['length'] * .6,
                  toward=point(tw) if tw in placed else None)

    cam, ground = L['scene'].camera, L['ground']
    t_still = spec['camera'].get('t', t_key if t_key is not None else ts)
    t_still = spec.get('ground_t', t_still)     # a take carried over a cut turns the planet from its first shot's time

    def per_frame(f, t):
        """The camera's move (motion.camera) and the planet turning under the co-moving frame."""
        if cam_at is not None:
            c = cam_at(t)
            L['aim'](c['loc'], c['target'], c['lens'], None, c.get('roll', 0.0))
            for path in ('location', 'rotation_quaternion'):
                cam.keyframe_insert(path, frame=f)
            cam.data.keyframe_insert('lens', frame=f)
            if 'focus' in c:            # the optic's focus pull (Workbench depth of field)
                cam.data.dof.use_dof = True
                cam.data.dof.focus_distance = c['focus']
                cam.data.dof.aperture_fstop = c.get('fstop', 2.8)
                cam.data.dof.keyframe_insert('focus_distance', frame=f)
                cam.data.dof.keyframe_insert('aperture_fstop', frame=f)
                L['scene'].display.shading.use_dof = True
        ground.rotation_mode = 'XYZ'
        ground.rotation_euler = (0.0, math.radians(motion.planet_turn_deg(t, t_still, bb.R)), 0.0)
        ground.keyframe_insert('rotation_euler', frame=f)
    mp.render_shot(before=per_frame)
    if cam_at is not None:          # the next shot keys its own camera from scratch
        cam.animation_data_clear()
        cam.data.animation_data_clear()
        cam.data.dof.use_dof = False
        L['scene'].display.shading.use_dof = False
    ground.animation_data_clear()
    ground.rotation_euler = (0.0, 0.0, 0.0)
    mp.end_shot()


# ------------------------------------------------------------------------------- the recording screen

# The playback screen the sequence opens and ends on (the shot doc's "Framing: a comms recording" and
# "On-screen place and time"): S0 opens on it, S11 breaks into its static, and S13 comes back to it for
# the closing traffic and END OF RECORDING (owner, 2026-10-22: "we started by hearing a log and we end
# with hearing the comms log"). The game draws it, localised, with the date computed at run time; the
# preview lays it out and times it against the radio (radio_log) so the sequence plays end to end.
RECORDING_END_S = 124.5          # S11: the picture breaks into the screen's static here


def recording_screen(t, mode):
    """One frame of the playback screen at sequence time t, by callsign (radio_log): 'open' (S0: black,
    then the screen, each channel lighting as its voice speaks, a waveform, the caption), 'static' (S11's
    last second and a half: static over the screen, CARAVEL's and MORROW's channels dropping out) or
    'close' (S13: the screen again, the dropped channels dark, the closing traffic, then END OF
    RECORDING on the log itself)."""
    import random
    import radio_log
    from PIL import Image, ImageDraw
    W, H = motion_preview.W, motion_preview.H
    fb, fm, fs = motion_preview._font(54, True), motion_preview._font(28, True), motion_preview._font(22)
    im = Image.new('RGB', (W, H), (7, 9, 11))
    dr = ImageDraw.Draw(im, 'RGBA')
    rnd = random.Random(int(t * 1000))
    lines = radio_log.OPENING if mode == 'open' else radio_log.CLOSING if mode == 'close' else []
    speaking = [sp for sp in lines if sp[2] <= t <= sp[3]]
    live = {sp[1] for sp in speaking}
    dead = {k for k, t_off in radio_log.DROPS.items() if t >= t_off}
    if mode == 'static':
        live |= {k for k, t_off in radio_log.DROPS.items() if t < t_off and rnd.random() > .3}
    fade = max(0.0, min(1.0, (t - .8) / .8)) if mode == 'open' else 1.0
    a = int(255 * fade)
    amber, grey, dim = (255, 186, 90, a), (190, 196, 204, a), (90, 96, 104, a)
    dr.text((140, 110), 'ASHARU', font=fb, fill=(235, 238, 242, a))
    dr.text((140, 180), 'Corvus  \u00b7  low orbit', font=fm, fill=grey)
    clock = 6 * 3600 + 41 * 60 + 12 + t
    dr.text((1260, 118), '[arrival date \u2212 3 cycles]', font=fm, fill=grey)
    dr.text((1260, 160), '%02d:%02d:%05.2f' % (clock // 3600, clock % 3600 // 60, clock % 60), font=fm, fill=grey)
    dr.text((140, 236), 'RECORDING  \u00b7  CONVOY COMMON', font=fs, fill=dim)
    dr.line((140, 280, 1780, 280), fill=dim, width=1)
    for i, ch in enumerate(radio_log.SCREEN_CHANNELS):
        y = 306 + i * 38
        on = ch in live
        col = amber if on else ((110, 60, 60, a) if ch in dead else dim)
        dr.ellipse((140, y + 6, 156, y + 22), fill=col if on else None, outline=col, width=2)
        dr.text((176, y), ch + ('  \u2014 no carrier' if ch in dead else ''), font=fs, fill=col)
        if on:
            for k in range(12):
                hgt = rnd.uniform(.2, 1.0) * 18
                dr.rectangle((460 + k * 9, y + 22 - hgt, 465 + k * 9, y + 22), fill=amber)
    x0, x1, yc = 640, 1780, 560          # waveform
    env = 1.0 if speaking else .04
    pts = []
    for k in range(0, x1 - x0, 4):
        v = env * rnd.gauss(0, 1) * (90 if speaking else 6) * (.6 + .4 * math.sin(k * .03 + t * 9))
        pts.append((x0 + k, yc + v))
    dr.line(pts, fill=amber if speaking else dim, width=2)
    dr.line((x0, 790, x1, 790), fill=dim, width=2)          # playback position over the whole recording
    pos = x0 + (x1 - x0) * min(1.0, t / radio_log.END_CARD_S)
    dr.rectangle((x0, 786, pos, 794), fill=grey)
    if speaking:
        _, who, _, _, line = speaking[0]
        dr.text((140, 860), who, font=fm, fill=amber)
        dr.text((140, 900), line, font=fm, fill=(235, 238, 242, a))
    if mode == 'close' and t >= radio_log.END_CARD_S:
        dr.text((640, 680), 'END OF RECORDING', font=fb, fill=(235, 238, 242, 255))
    dr.text((140, 1000), 'PREVIEW: layout and timing only. The game draws this screen, localised, with the date '
                         'computed at run time; the lines are voiced.', font=fs, fill=(80, 84, 90, 255))
    if mode == 'static':
        noise = Image.effect_noise((W // 4, H // 4), 90).resize((W, H), Image.NEAREST).convert('RGB')
        k = min(1.0, .55 + (t - RECORDING_END_S) * .3)
        im = Image.blend(im, noise, k * .6)
    return im


def radio_caption(im, f, shot):
    """Burn the radio line on the channel at frame f in as a caption, with its callsign, on every shot
    but the two playback screens (S0, S13), which show it themselves: the owner judges the traffic's
    density and whether the edit follows the radio. A placeholder line is marked as one."""
    import radio_log
    from PIL import ImageDraw
    if shot['id'].split()[0] in ('S0', 'S13'):
        return im
    lines = radio_log.speaking(f / FPS)
    if not lines:
        return im
    _, callsign, _, _, line, ph = lines[0]
    who, role = radio_log.caption(callsign)      # the callsign leads; the designation is a role word after it
    W, H = motion_preview.W, motion_preview.H
    f_who, f_line, f_ph = motion_preview._font(24, True), motion_preview._font(30), motion_preview._font(20)
    dr = ImageDraw.Draw(im, 'RGBA')
    tag = '   '.join(x for x in (role, 'TIMING PLACEHOLDER, NOT DIALOGUE' if ph else '') if x)
    width = max(dr.textlength(line, font=f_line), dr.textlength(who + '   ', font=f_who) + dr.textlength(tag, font=f_ph)) + 60
    x0, y0 = (W - width) / 2, H - 196
    dr.rectangle((x0, y0, x0 + width, y0 + 92), fill=(0, 0, 0, 170))
    dr.text((x0 + 30, y0 + 10), who, font=f_who, fill=(255, 186, 90, 255))
    if tag:
        dr.text((x0 + 30 + dr.textlength(who + '   ', font=f_who), y0 + 13), tag, font=f_ph, fill=(150, 156, 164, 255))
    dr.text((x0 + 30, y0 + 44), line, font=f_line, fill=(150, 156, 164, 255) if ph else (240, 242, 245, 255))
    return im


# ------------------------------------------------------------------------------- driver

def main(argv, run, run_date):
    """battle_blockout.py --motion-preview: build and render the keyed shots in Blender, then the
    overlays, the MP4 and the stills, then (--post) the review folder."""
    def opt(flag, default=None):
        return argv[argv.index(flag) + 1] if flag in argv else default
    step, scale = int(opt('--step', 1)), int(opt('--scale', 50))
    segs = pick(segments(traced_keyframes()), opt('--shots'))
    keyed = [s['kf'] for s in segs if s['kf']]
    out = run / 'preview'
    out.mkdir(parents=True, exist_ok=True)
    if keyed and '--skip-render' not in argv:
        from blender_path import BLENDER
        cfg = dict(out=str(run), frames=keyed, samples=1,
                   motion_preview=dict(step=step, scale=scale, weave_all='--weave-all' in argv, dry='--dry' in argv))
        env = dict(os.environ, ASHARU_CFG=json.dumps(cfg))
        subprocess.check_call([str(BLENDER), '--background', '--factory-startup', '--python-exit-code', '1',
                               '--python', str(HERE / 'battle_blockout.py')], env=env)
    timeline = [dict(id=s['id'], title=s['title'], caption=s['caption'], start=round(s['start'] * FPS),
                     end=round(s['end'] * FPS), rendered=bool(s['kf']) or s['first'] in ('S0', 'S13')) for s in segs]
    for row in timeline:
        first = row['id'].split()[0]
        if first == 'S0':          # the recording screen, over the S1 traffic
            row['screen'] = lambda f: recording_screen(f / FPS, 'open')
        elif first == 'S11':       # the plasma takes the channel: static, the channels drop out
            row['screen'] = lambda f: recording_screen(f / FPS, 'static') if f / FPS >= RECORDING_END_S else None
        elif first == 'S13':       # back on the log: the closing traffic, END OF RECORDING
            row['screen'] = lambda f: recording_screen(f / FPS, 'close')
    summary = motion_preview.finish(out, ffmpeg=opt('--ffmpeg', 'ffmpeg'), name=NAME,
                                    trails=float(opt('--trails', 2.0)), timeline=timeline, sequence='Asharu',
                                    post=radio_caption)
    notes(out, summary, step, scale, argv)
    post = opt('--post')
    if post:
        drive = Path('G:/My Drive/starsector') if os.name == 'nt' else next(
            Path.home().glob('Library/CloudStorage/GoogleDrive-*/My Drive/starsector'))
        dest = drive / 'review' / 'cinematic-asharu-battle' / f"{run_date}_{opt('--what', 'motion-preview')}_{post}"
        dest.mkdir(parents=True, exist_ok=True)
        for f in (NAME + '.mp4', NAME + '-web.mp4', 'NOTES.md', 'preview.json'):
            shutil.copy2(out / f, dest / f)
        shutil.copytree(out / 'jpg', dest / 'jpg', dirs_exist_ok=True)
        print('POSTED', dest, flush=True)


def notes(out, summary, step, scale, argv):
    commit = subprocess.run(['git', 'rev-parse', '--short', 'HEAD'], cwd=HERE, capture_output=True, text=True).stdout.strip()
    lines = ['# Asharu motion preview', '',
             f'Commit `{commit}`. Workbench, {scale}% size, every {"" if step == 1 else str(step) + "nd "}frame '
             f'({FPS / step:g} fps), labelled at 1920x1080. `{NAME}.mp4` plays at real speed.',
             '' if '--weave-all' not in argv else 'Authored poses weave about their pose (--weave-all).', '',
             'Judges motion and composition only. Not lighting, materials, volume look or motion blur.', '',
             '| Shot | Length | Rendered | Notes |', '|---|---|---|---|']
    for s in summary['shots']:
        lines.append(f"| {s['id']} {s['title']} | {s['seconds']:g} s | {'yes' if s['rendered'] else 'slate'} | "
                     f"{'; '.join(s['notes'])} |")
    (out / 'NOTES.md').write_text('\n'.join(lines) + '\n', encoding='utf-8')


def write_job(out_dir, date, step=2, machine='any'):
    """The light-lane queue job for the whole sequence (motion-preview-job.template.json's shape)."""
    import queue_jobs
    run = 'motion-preview'
    job = {
        'id': f'asharu-motion-preview-{date}',
        'machine': machine,
        'lane': 'light',
        'priority': 0,
        'after': [],
        'requires': ['gpu', 'ffmpeg'],
        'cwd': '{checkout}/art-source/company-prologue/asharu-battle',
        'command': ['{python}', 'battle_blockout.py', '--motion-preview', '--step', str(step), '--root', '{work}',
                    '--run', run, '--date', date, '--ffmpeg', '{ffmpeg}', '--post', '{machine}',
                    '--what', 'motion-preview'],
        'env': {},
        'inputs': queue_jobs.inputs(),
        'frames': {'dir': '{work}/' + run + '/preview', 'glob': NAME + '.mp4', 'count': 1},
    }
    path = Path(out_dir) / f"{job['id']}.json"
    path.write_text(json.dumps(job, indent=1))
    print(path)
    return path


if __name__ == '__main__':
    a = sys.argv[1:]
    if '--job' in a:
        write_job(a[a.index('--job') + 1], a[a.index('--date') + 1], int(a[a.index('--step') + 1]) if '--step' in a else 2,
                  a[a.index('--machine') + 1] if '--machine' in a else 'any')
    else:
        for s in segments(traced_keyframes()):
            print(f"{s['id']:14} {s['start']:6.1f}-{s['end']:6.1f} s  {'key' if s['kf'] else 'slate':5} {s['title']}")
