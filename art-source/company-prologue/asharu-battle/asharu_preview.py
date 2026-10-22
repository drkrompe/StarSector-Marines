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

- a ship the key frame places with motion.fleet_at moves the way motion.drift says, and a boat
  on a motion.boarding_run flies its run, on the cinematic's own clock (the key frame is one
  instant of that motion, marked KEY FRAME);
- a ship with an authored pose (an attack run, a ship aboard another) has no path yet and holds
  that pose: the legend says "(held)". --weave-all weaves it about its pose instead;
- the camera holds the key frame's framing;
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
        m = re.match(r'\|\s*(S\d+[a-z]?)(?:\s*\*\*K\*\*)?\s*\|\s*([\d.]+)\s*s\s*\|\s*(.*?)\s*\|', line)
        if not m:
            continue
        desc = m.group(3)
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
        span[a] = (kf, ids)
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
    """battle_blockout.KEYFRAMES re-read with motion's functions traced: every pose they make is a
    Drift, so the preview can play the motion the key frame stills."""
    real_fleet, real_run = motion.fleet_at, getattr(motion, 'boarding_run', None)

    def fleet_at(ships, t, scale=1.0, keep=()):
        return {k: (v if k in keep else Drift(v, t, lambda tt, k=k, st=tuple(ships[k]): motion.drift(k, st, tt, scale)))
                for k, v in real_fleet(ships, t, scale, keep).items()}

    def boarding_run(*a):
        return Drift(real_run(*a), a[-1], lambda tt: real_run(*a[:-1], tt))
    motion.fleet_at = fleet_at
    if real_run is not None:
        motion.boarding_run = boarding_run
    try:
        spec = importlib.util.spec_from_file_location('asharu_traced', HERE / 'battle_blockout.py')
        mod = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(mod)
    finally:
        motion.fleet_at = real_fleet
        if real_run is not None:
            motion.boarding_run = real_run
    return mod.KEYFRAMES


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
        if isinstance(sp, Drift):
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

    # Draw only what the preview owns, the planet's surface, the platform and the entry trails:
    # a build's own effects (cook-off volumes, kit copies hidden in the viewport) and the air shell,
    # which the builder turns back on for every key frame, would otherwise draw as grey solids.
    keep, hid = {L['ground'].name}, []
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
    for key, sh in spec.get('shields', {}).items():
        tw = sh.get('toward')
        mp.shield('%s shield %s' % (kf, key), point(key), placed[key]['length'] * .6,
                  toward=point(tw) if tw in placed else None)

    mp.render_shot()
    mp.end_shot()


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
                     end=round(s['end'] * FPS), rendered=bool(s['kf'])) for s in segs]
    summary = motion_preview.finish(out, ffmpeg=opt('--ffmpeg', 'ffmpeg'), name=NAME,
                                    trails=float(opt('--trails', 2.0)), timeline=timeline, sequence='Asharu')
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
