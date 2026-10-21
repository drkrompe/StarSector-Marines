"""Asharu effects bench: an isolated rig the effects workstreams test on (asharu-effects-plan.md, W0).

One to three real builds against a black sky under a fixed key light and a fixed camera, each
ship with its own SWFX copy (ship_fx.ShipFx), and N frames rendered at half size and at most
16 samples: a light-lane job.

    python effects_bench.py --list                                   # every test, its builds and frames
    python effects_bench.py --tests w0.two-lashers --check           # build and key the rig in Blender, no render
    python effects_bench.py --tests w0.two-lashers --root DIR --post windows    # render (a queue job does this)
    python effects_bench.py --write-job --tests w0.two-lashers --machine windows --date 2026-10-21 --out DIR

Tests live in bench_<module>.py beside this file, one module per workstream (bench_w0.py,
bench_shields.py, bench_missiles.py, ...), so parallel sessions never edit one file. A test id is
'<module>.<name>'. A module defines

    TESTS = {'<name>': dict(builds=['lasher'], frames=[1, 2, 3, 4], note='what to look at',
                            setup=<function(bench)>)}

`builds` are roster.FINISHED keys; `setup(bench)` places ships with `bench.ship(...)`, keys their
knobs (`ship.key`, or the kit's own helpers through `with ship.kit() as (ctx, kit)`), and may
move the camera with `bench.camera(...)`. It runs inside Blender, so a module imports bpy only
inside its functions; --list and --write-job read the module in plain Python.

Frames render to <root>/<run>/raw/<module>.<name>__f####.png and a finished frame is skipped,
so a swapped-out job resumes. --post lays the frames out four to a 1920x1080 sheet (2x2 at half
size) and posts each module's sheets to review/asharu-fx-<module>/<date>_<what>_<machine>/
with a NOTES.md, then prunes that asset (tools/review_prune.py). Each workstream is its own
review asset so one workstream's posts never prune another's.
"""
import importlib
import json
import math
import os
import subprocess
import sys
import time
from datetime import date
from pathlib import Path

HERE = Path(__file__).resolve().parent
PROLOGUE = HERE.parent
ART = PROLOGUE.parent
REPO = ART.parent
for p in (HERE, PROLOGUE, ART):
    if str(p) not in sys.path:
        sys.path.insert(0, str(p))
import roster  # noqa: E402

BUILDS = Path(os.environ.get('ASHARU_BUILDS') or REPO / 'build' / 'asharu-battle' / 'builds')
W, H = 1920, 1080
MAX_SAMPLES = 16          # the light lane's ceiling (owner rule)


# ======================================================================================= tests

def modules():
    return sorted(p.stem[len('bench_'):] for p in HERE.glob('bench_*.py'))


def test(tid):
    mod, _, name = tid.partition('.')
    m = importlib.import_module(f'bench_{mod}')
    if name not in m.TESTS:
        raise SystemExit(f'no test {name!r} in bench_{mod}.py; it has {sorted(m.TESTS)}')
    return dict(m.TESTS[name], id=tid, module=mod, name=name)


def all_tests():
    out = []
    for mod in modules():
        m = importlib.import_module(f'bench_{mod}')
        out += [f'{mod}.{n}' for n in m.TESTS]
    return out


def frame_name(t, f):
    return f"{t['id']}__f{f:04d}.png"


# ======================================================================================= Blender

class Bench:
    """The rig one test builds on. `scene` is the Blender scene; ships placed with ship() are
    ship_fx.ShipFx objects, each with `.matrix` (its world frame, metres)."""

    def __init__(self, samples, half):
        import bpy
        self.bpy = bpy
        bpy.ops.wm.read_factory_settings(use_empty=True)
        scene = self.scene = bpy.context.scene
        scene.render.resolution_x, scene.render.resolution_y = W, H
        scene.render.resolution_percentage = 50 if half else 100
        scene.render.fps = 24
        scene.render.engine = 'CYCLES'
        scene.cycles.samples = samples
        scene.cycles.use_denoising = True
        scene.cycles.max_bounces = 4
        scene.cycles.transparent_max_bounces = 16
        scene.view_settings.view_transform = 'AgX'
        scene.view_settings.look = 'AgX - Base Contrast'
        self._gpu()
        world = bpy.data.worlds.new('Bench black')
        scene.world = world
        world.color = (0, 0, 0)
        bg = world.node_tree.nodes.get('Background') if world.node_tree else None
        if bg is not None:          # a new world's node tree carries its own 0.05 grey
            bg.inputs['Color'].default_value = (0, 0, 0, 1)
            bg.inputs['Strength'].default_value = 0.0
        self.libraries = {}
        self.ships = []
        # A fixed key from above-left and a faint cool fill: enough to read the hull, dark
        # enough that every flash, plume and shield reads against it.
        self.key_light = self._sun('Bench key', 3.2, (math.radians(50), 0, math.radians(-35)), (1.0, .96, .9))
        self.fill_light = self._sun('Bench fill', .25, (math.radians(115), 0, math.radians(150)), (.6, .7, 1.0))
        cam = bpy.data.objects.new('Bench camera', bpy.data.cameras.new('Bench camera'))
        scene.collection.objects.link(cam)
        scene.camera = cam
        cam.data.sensor_fit = 'HORIZONTAL'
        cam.data.sensor_width = 36.0
        cam.data.clip_start = .5
        cam.data.clip_end = 1.0e5
        self.cam = cam
        self.camera((0, -300, 60), (0, 0, 0), 50)

    def _gpu(self):
        bpy, scene = self.bpy, self.scene
        try:
            prefs = bpy.context.preferences.addons['cycles'].preferences
        except KeyError:
            return
        for kind in ('OPTIX', 'CUDA', 'METAL'):
            try:
                prefs.compute_device_type = kind
                prefs.get_devices()
            except TypeError:
                continue
            if any(d.type == kind for d in prefs.devices):
                for d in prefs.devices:
                    d.use = d.type == kind
                scene.cycles.device = 'GPU'
                return

    def _sun(self, name, energy, rot, rgb):
        bpy = self.bpy
        light = bpy.data.lights.new(name, 'SUN')
        light.energy, light.color, light.angle = energy, rgb, math.radians(.5)
        ob = bpy.data.objects.new(name, light)
        ob.rotation_euler = rot
        self.scene.collection.objects.link(ob)
        return ob

    def camera(self, loc, target, lens=50):
        """The fixed camera: at loc, looking at target (metres), with this focal length."""
        from mathutils import Vector
        self.cam.location = loc
        self.cam.rotation_euler = (Vector(target) - Vector(loc)).to_track_quat('-Z', 'Y').to_euler()
        self.cam.data.lens = lens

    def library(self, build):
        """A build's hull and SWFX collections, appended once and excluded (never drawn as is)."""
        if build in self.libraries:
            return self.libraries[build]
        bpy = self.bpy
        f = roster.FINISHED[build]
        m = json.loads((BUILDS / f['dir'] / f.get('manifest', 'manifest.json')).read_text())
        names = [m['append_collection']] + ([m['effects']['collection']] if (m.get('effects') or {}).get('collection')
                                            else [])
        with bpy.data.libraries.load(str(BUILDS / f['dir'] / f['file']), link=False) as (src, dst):
            dst.collections = names
        colls = list(dst.collections)
        for c in colls:
            self._exclude(c)
        ctl = (m.get('control') or {}).get('object') or (m.get('break_up') or {}).get('controls')
        import ship_fx
        hull_ctl = next((o for o in colls[0].all_objects if ctl and ship_fx.base_name(o.name) == ctl), None)
        lib = self.libraries[build] = dict(hull=colls[0], fx=colls[1] if len(colls) > 1 else None, manifest=m,
                                           hull_ctl=hull_ctl)
        return lib

    def _exclude(self, coll):
        self.scene.collection.children.link(coll)
        self.bpy.context.view_layer.layer_collection.children[coll.name].exclude = True

    def ship(self, key, build, at=(0, 0, 0), heading=0.0, pitch=0.0, roll=0.0, throttle=None):
        """Place one ship of `build` (a roster.FINISHED key) with its own effects copy. Heading,
        pitch and roll in degrees, the scene's order (+X forward). Returns its ShipFx."""
        import ship_fx
        from mathutils import Matrix, Quaternion, Vector
        bpy = self.bpy
        lib = self.library(build)
        s = ship_fx.ShipFx(key, lib['hull'], lib['fx'], lib['manifest'], hull_ctl=lib['hull_ctl'])
        for c in (s.hull if s.hull is not lib['hull'] else None, s.fx):
            if c is not None:
                self._exclude(c)
        q = (Quaternion((0, 0, 1), math.radians(heading)) @ Quaternion((0, 1, 0), -math.radians(pitch))
             @ Quaternion((1, 0, 0), math.radians(roll)))
        s.matrix = Matrix.Translation(Vector(at)) @ q.to_matrix().to_4x4()
        for coll, label in ((s.hull, 'Ship'), (s.fx, 'Ship fx')):
            if coll is None:
                continue
            e = bpy.data.objects.new(f'{label} | {key}', None)
            e.instance_type = 'COLLECTION'
            e.instance_collection = coll
            e.matrix_world = s.matrix @ Matrix.Scale(s.mpu, 4)
            self.scene.collection.objects.link(e)
        if throttle is not None and s.controls is not None:
            s.set('thr_throttle', throttle)
        self.ships.append(s)
        return s

    def at(self, ship, local=(0, 0, 0)):
        """A point on a placed ship, local metres in its own frame."""
        from mathutils import Vector
        return ship.matrix @ Vector(local)


def blender_main(cfg):
    import ship_fx
    out = Path(cfg['out'])
    raw = out / 'raw'
    raw.mkdir(parents=True, exist_ok=True)
    report = {}
    for tid in cfg['tests']:
        t = test(tid)
        todo = [f for f in t['frames'] if cfg.get('check') or not finished(raw / frame_name(t, f))]
        if not todo:
            print('BENCH already finished', tid, flush=True)
            continue
        t0 = time.time()
        bench = Bench(min(cfg['samples'], MAX_SAMPLES), cfg['half'])
        t['setup'](bench)
        ship_fx.refresh(bench.scene)
        audit = ship_fx.audit(bench.ships, [lib['fx'] for lib in bench.libraries.values()])
        foreign = {k: v['foreign'] for k, v in audit.items() if v['foreign']}
        rows = {}
        for f in t['frames']:
            bench.scene.frame_set(f)
            dg = bench.bpy.context.evaluated_depsgraph_get()
            rows[f] = {s.key: s.reading(dg) for s in bench.ships}
            for k, r in rows[f].items():
                print(f'BENCH {tid} f{f} {k:12} {json.dumps(r)}', flush=True)
        report[tid] = dict(setup_s=round(time.time() - t0, 1), foreign=foreign, readings=rows, frames={})
        if foreign:
            print('BENCH FOREIGN references', json.dumps(foreign), flush=True)
            raise SystemExit(1)
        if cfg.get('check'):
            continue
        scene = bench.scene
        scene.render.use_file_extension = False
        for f in todo:
            t1 = time.time()
            scene.frame_set(f)
            partial = raw / (frame_name(t, f) + '.partial')     # not *.png, so the queue never counts it
            scene.render.filepath = str(partial)
            bench.bpy.ops.render.render(write_still=True)
            os.replace(partial, raw / frame_name(t, f))
            report[tid]['frames'][f] = round(time.time() - t1, 1)
            print(f'BENCH rendered {tid} f{f} in {time.time() - t1:.1f}s', flush=True)
    store = out / 'bench-report.json'
    old = json.loads(store.read_text()) if store.exists() else {}
    store.write_text(json.dumps({**old, **report}, indent=1))


def finished(png):
    try:
        with open(png, 'rb') as fh:
            fh.seek(-12, 2)
            return fh.read(12)[4:8] == b'IEND'
    except OSError:
        return False


# ======================================================================================= driver

def drive_dir():
    return Path('G:/My Drive/starsector') if os.name == 'nt' else next(
        Path.home().glob('Library/CloudStorage/GoogleDrive-*/My Drive/starsector'))


def git_commit():
    try:
        return subprocess.check_output(['git', 'rev-parse', '--short', 'HEAD'], cwd=REPO, text=True).strip()
    except Exception:
        return 'unknown'


def sheets(run, tests):
    """Each test's frames, four to a 1920x1080 sheet (2x2 at half size), labelled. Returns
    {module: [sheet paths]}."""
    from PIL import Image, ImageDraw
    from review_font import review_font
    font = review_font(22, bold=True)
    small = review_font(18)
    out = {}
    n = 0
    for tid in tests:
        t = test(tid)
        frames = [f for f in t['frames'] if finished(run / 'raw' / frame_name(t, f))]
        for i in range(0, len(frames), 4):
            n += 1
            sheet = Image.new('RGB', (W, H), (0, 0, 0))
            dr = ImageDraw.Draw(sheet)
            for j, f in enumerate(frames[i:i + 4]):
                im = Image.open(run / 'raw' / frame_name(t, f)).convert('RGB').resize((W // 2, H // 2))
                x, y = (j % 2) * W // 2, (j // 2) * H // 2
                sheet.paste(im, (x, y))
                dr.text((x + 14, y + 10), f"{t['id']}  frame {f}", font=font, fill=(235, 235, 235))
            if t.get('note'):
                dr.text((14, H - 30), t['note'][:180], font=small, fill=(200, 200, 200))
            if len(frames) > 1:
                dr.line([(W // 2, 0), (W // 2, H)], fill=(60, 60, 60), width=2)
                dr.line([(0, H // 2), (W, H // 2)], fill=(60, 60, 60), width=2)
            name = f"{n:02d}-{t['id'].replace('.', '-')}" + (f'-{i // 4 + 1}' if len(frames) > 4 else '') + '.png'
            sheet.save(run / name)
            out.setdefault(t['module'], []).append(name)
    return out


def notes(run, tests, samples, half, module):
    rep = json.loads((run / 'bench-report.json').read_text()) if (run / 'bench-report.json').exists() else {}
    lines = [f'# Asharu effects bench: {module}', '',
             f'- Commit `{git_commit()}`, `effects_bench.py`, {"half size" if half else "full size"}, '
             f'{samples} samples, light lane.', '']
    for tid in tests:
        t = test(tid)
        if t['module'] != module:
            continue
        r = rep.get(tid, {})
        lines += [f"## {tid}", '', t.get('note', ''), '',
                  f"- Builds: {', '.join(t['builds'])}. Frames {t['frames']}.",
                  f"- Setup {r.get('setup_s', '?')} s; render s/frame: {r.get('frames', {})}.", '']
        for f, rows in (r.get('readings') or {}).items():
            lines.append(f'- frame {f}: ' + '; '.join(f'{k} {json.dumps(v)}' for k, v in rows.items()))
        lines.append('')
    (run / f'NOTES-{module}.md').write_text('\n'.join(lines))
    return f'NOTES-{module}.md'


def write_job(tests, machine, day, out, samples, what):
    """One light-lane render-queue job for these tests (see bench-job.template.json)."""
    import queue_jobs
    if samples > MAX_SAMPLES:
        raise SystemExit(f'the light lane allows at most {MAX_SAMPLES} samples')
    ts = [test(t) for t in tests]
    builds = sorted({b for t in ts for b in t['builds']})
    inputs = [{'kind': 'git', 'commit': '<filled in by --commit>'}]
    for b in builds:
        f = roster.FINISHED[b]
        src = queue_jobs.DRIVE / f['drive']
        inputs.append({'kind': 'drive', 'from': '{drive}/' + f['drive'],
                       'to': '{checkout}/build/asharu-battle/builds/' + f['dir'],
                       'sha256': {n: queue_jobs.sha256(src / n) for n in (f['file'], f.get('manifest', 'manifest.json'))}})
    slug = what or 'bench-' + '-'.join(t['name'] for t in ts)
    job = {
        'id': f'asharu-fx-{slug}-{day}',
        'machine': machine,
        'lane': 'light',
        'priority': 0,
        'after': [],
        'requires': ['gpu'],
        'cwd': '{checkout}/art-source/company-prologue/asharu-battle',
        'command': ['{python}', 'effects_bench.py', '--tests', ','.join(tests), '--samples', str(samples), '--half',
                    '--root', '{work}', '--run', 'bench', '--date', day, '--post', '{machine}', '--what', slug],
        'env': {},
        'inputs': inputs,
        'frames': {'dir': '{work}/bench/raw', 'glob': '*.png', 'count': sum(len(t['frames']) for t in ts)},
    }
    path = Path(out) / f"{job['id']}.json"
    path.write_text(json.dumps(job, indent=1))
    print(path)
    return path


def main():
    argv = sys.argv[1:]

    def opt(flag, default=None):
        return argv[argv.index(flag) + 1] if flag in argv else default

    if '--list' in argv:
        for tid in all_tests():
            t = test(tid)
            print(f"{tid:28} builds {','.join(t['builds']):24} frames {t['frames']}  {t.get('note', '')}")
        return
    tests = opt('--tests', '').split(',') if opt('--tests') else []
    if not tests:
        raise SystemExit('--tests <module.name>[,...] (see --list)')
    for tid in tests:
        test(tid)
    samples = int(opt('--samples', MAX_SAMPLES))
    if '--write-job' in argv:
        write_job(tests, opt('--machine', 'any'), opt('--date', date.today().isoformat()), opt('--out', '.'),
                  samples, opt('--what'))
        return
    half = '--full' not in argv
    if samples > MAX_SAMPLES:
        raise SystemExit(f'the bench renders at most {MAX_SAMPLES} samples (light lane)')
    run = Path(opt('--root', REPO / 'build' / 'asharu-battle' / 'bench')) / opt('--run', date.today().isoformat())
    run.mkdir(parents=True, exist_ok=True)
    started = run / 'run-date.txt'
    if not started.exists():
        started.write_text(opt('--date', date.today().isoformat()))
    from blender_path import BLENDER
    cfg = dict(out=str(run), tests=tests, samples=samples, half=half, check='--check' in argv)
    env = dict(os.environ, BENCH_CFG=json.dumps(cfg))
    subprocess.check_call([str(BLENDER), '--background', '--factory-startup', '--python-exit-code', '1',
                           '--python', __file__], env=env)
    if '--check' in argv:
        print('BENCH check done', run / 'bench-report.json')
        return
    made = sheets(run, tests)
    print('\n'.join(str(run / n) for ns in made.values() for n in ns))
    post = opt('--post')
    if post:
        import shutil
        drive = drive_dir()
        what = opt('--what', 'bench')
        for module, names in made.items():
            asset = f'asharu-fx-{module}'
            dest = drive / 'review' / asset / f'{started.read_text().strip()}_{what}_{post}'
            marker = dest / '.bench-run'            # one run, one writer
            if dest.exists() and not (marker.exists() and marker.read_text().strip() == str(run)):
                raise SystemExit(f'{dest} exists and belongs to another run')
            dest.mkdir(parents=True, exist_ok=True)
            marker.write_text(str(run))
            for n in names:
                shutil.copy2(run / n, dest / n)
            shutil.copy2(run / notes(run, tests, samples, half, module), dest / 'NOTES.md')
            print('POSTED', dest, flush=True)
            prune = REPO / 'tools' / 'review_prune.py'
            if prune.exists():
                subprocess.call([sys.executable, str(prune), '--asset', asset, '--apply'])


if __name__ == '__main__':
    try:
        import bpy  # noqa: F401
    except ImportError:
        bpy = None
    if bpy is not None and os.environ.get('BENCH_CFG'):
        blender_main(json.loads(os.environ['BENCH_CFG']))
    else:
        main()
