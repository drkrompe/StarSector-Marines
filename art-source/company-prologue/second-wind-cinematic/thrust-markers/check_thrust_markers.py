"""Build a shot's scene and check that every craft in it carries thruster markers.

    blender --background --factory-startup --python-exit-code 1 --python check_thrust_markers.py \
        -- --shot c1|c2|c4 [--still SECONDS[,SECONDS...]] [--samples N] [--out DIR]

Builds the scene the way its own script does (c1v2_scene.build, c2_bridge_render's Blender half
up to its render loop, c4x_scene.build, which C5 also builds on), then lists every craft the view
layer shows with its markers by kind, what its engines are doing, and the nozzle sources. Fails
(exit 1) when a craft has no markers, when a marker breaks the convention, or when an object that
looks like a ship (by name) is neither a marked craft nor declared scenery below.

Writes <out>/<shot>.json: per marker its craft, kind, radius, source, throttle keys and its place
on the craft, plus each craft's world position once a second, for the VFX and sound passes.
--still renders a 1920x1080 frame at each time, <out>/<shot>-<t>s-plain.png, and beside it
<shot>-<t>s-glyphs.json with each marker's place in the picture; draw_glyphs.py then draws the
markers over it. Default out:
build/thrust-markers/ under the repository root (ignored, never committed).
"""
import json
import re
import sys
import time
from pathlib import Path

import bpy
from bpy_extras.object_utils import world_to_camera_view
from mathutils import Quaternion, Vector

HERE = Path(__file__).resolve().parent
CINE = HERE.parent
REPO = CINE.parents[2]
sys.path.insert(0, str(HERE))
import thrust_markers as tm  # noqa: E402

# Names that suggest a flying craft; any shown object matching one must be a marked craft.
SHIP_WORDS = re.compile(r'second wind|onslaught|lasher|legion|hound|mudskipper|broadsword|hermes|lighter|'
                        r'colossus|shepherd|atlas|fighter|flotilla|shuttle|lifter|valkyrie|buffalo|tarsus|'
                        r'mule|nebula|mercury|dominator|freighter|tanker|ferry|wasp|talon|gunship', re.I)
# Parts and props that carry a ship's name without being a ship.
NOT_CRAFT = re.compile(r'plume|glow|dust|light|lamp|shadow|beacon|core|haze|smoke|steam|flame|bead|'
                       r'pad|berth|stand|shed|crane|truck|tug|forklift|carrier \d|bowser|crate|'
                       r'thrust \||camera|sun|station|planet|ring|trail|apron|road|gate|fence', re.I)


def args():
    a = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
    o = {'shot': None, 'still': None, 'samples': 16, 'out': REPO / 'build' / 'thrust-markers'}
    for i, x in enumerate(a):
        if x == '--shot':
            o['shot'] = a[i + 1]
        elif x == '--still':
            o['still'] = [float(v) for v in a[i + 1].split(',')]
        elif x == '--samples':
            o['samples'] = int(a[i + 1])
        elif x == '--out':
            o['out'] = Path(a[i + 1])
    return o


def build(shot):
    """(scene, fps, end frame) built by the shot's own script."""
    if shot == 'c1':
        sys.path.insert(0, str(CINE / 'corvus-approach'))
        import c1v2_scene as c1
        return c1.build(), c1.FPS, c1.FRAMES - 1
    if shot == 'c4':
        sys.path.insert(0, str(CINE / 'industrial-exterior'))
        import c4x_scene as c4
        scene = c4.build()
        try:
            import port_life
            end = port_life.end_frame()
        except Exception:
            end = c4.FRAMES - 1
        return scene, c4.FPS, end
    if shot == 'c2':
        sys.path.insert(0, str(CINE / 'patrol-abeam'))
        import c2_bridge_render as c2
        import c2_core as C
        fps = 24.0
        n = int(round(C.C2_LEN * fps))
        p = c2.plan(list(range(n + 1)), fps, 16)
        out = REPO / 'build' / 'thrust-markers' / 'c2-plan'
        out.mkdir(parents=True, exist_ok=True)
        p.update(out=str(out), resume=False, render=[], build_only=True)
        pp = out / 'plan.json'
        pp.write_text(json.dumps(p), encoding='utf-8')
        scene = c2.blender_main(['--plan', str(pp)])
        return scene, int(fps), n
    raise SystemExit('unknown --shot %r (c1, c2 or c4)' % shot)


def craft_world_path(ob, fps, end, scene):
    out = []
    for f in range(0, end + 1, int(fps)):
        scene.frame_set(f)
        out.append([round(f / fps, 2)] + [round(v, 2) for v in ob.matrix_world.translation])
    return out


def check(scene, fps, end):
    rows = tm.report(scene, fps)
    bad = []
    craft_names = {r[0] for r in rows}
    marked = {m.parent.name for m in tm.markers() if m.parent is not None}
    for name, act, kinds, flying in rows:
        if not sum(kinds.values()):
            bad.append('%s: no markers' % name)
    for m in tm.markers():
        for prop in ('thrust_craft', 'thrust_kind', 'thrust_index', 'radius_m', 'thrust_source',
                     'thrust_working', tm.PROP):
            if prop not in m:
                bad.append('%s: no %s' % (m.name, prop))
        if m.get('thrust_kind') not in tm.KINDS:
            bad.append('%s: kind %r' % (m.name, m.get('thrust_kind')))
        if m.parent is None:
            bad.append('%s: not parented to its craft' % m.name)
        if not 0.0 <= tm.throttle_at(m, scene.frame_current) <= 1.0:
            bad.append('%s: throttle out of 0..1' % m.name)
        if m.get('thrust_vectored'):
            bad += vectored_faults(m, fps, end)
    suspects = []
    for ob in bpy.context.view_layer.objects:
        if ob.name.startswith(tm.PREFIX) or ob.type in ('LIGHT', 'CAMERA'):
            continue
        if not SHIP_WORDS.search(ob.name) or NOT_CRAFT.search(ob.name):
            continue
        if any(a.name in craft_names for a in [ob] + tm.ancestors(ob)):
            continue
        if ob.parent is not None and SHIP_WORDS.search(tm.top(ob).name):
            continue                     # a part of some top-level object already judged
        suspects.append(ob.name)
        bad.append('%s: looks like a ship but has no markers' % ob.name)
    return rows, bad, suspects, marked


def vectored_faults(m, fps, end):
    """A drive turned down for lift must point hull-down whenever it carries thrust with no way
    on, and must never carry more than the drive's one throttle together with its main."""
    out = []
    main = bpy.data.objects.get(m['thrust_vectored'])
    if main is None:
        return ['%s: vectored from missing %s' % (m.name, m['thrust_vectored'])]
    craft = m.parent
    for f in range(0, end + 1, max(1, int(fps) // 2)):
        a, b = tm.path(craft, [max(0, f - 3), min(end, f + 3)])
        vh = (b - a).xy.length / (max(1, min(end, f + 3) - max(0, f - 3)) / fps)
        ax = rotation_at(m, f) @ Vector((1, 0, 0))
        if tm.throttle_at(m, f) > 0.05 and vh < 0.3 and ax.z > -0.95:
            out.append('%s: carries thrust at %.1f s with no way on but is not pointing down' % (m.name, f / fps))
        if tm.throttle_at(m, f) + tm.throttle_at(main, f) > 1.001:
            out.append('%s: with its main over one throttle at %.1f s' % (m.name, f / fps))
    return out[:3]


def rotation_at(m, frame):
    q = [None] * 4
    for fc in tm.fcurves(m):
        if fc.data_path == 'rotation_quaternion':
            q[fc.array_index] = fc.evaluate(frame)
    if None in q:
        return m.matrix_basis.to_quaternion()
    return Quaternion(q).normalized()


def summary(scene, fps, end, shot):
    data = {'shot': shot, 'fps': fps, 'frames': [0, end], 'convention': 'thrust-markers/README.md',
            'craft': {}}
    for m in tm.markers():
        c = m.parent
        rec = data['craft'].setdefault(c.name, {'label': c.get('thrust_craft', c.name),
                                                'activity': c.get('thrust_activity', ''),
                                                'markers': []})
        loc, rot, _ = m.matrix_basis.decompose()
        rec['markers'].append({
            'name': m.name, 'kind': m['thrust_kind'], 'index': m['thrust_index'],
            'radius_m': m['radius_m'], 'working': m['thrust_working'], 'source': m['thrust_source'],
            'throttle': tm.throttle_keys(m, fps),
            'local_location': [round(v, 4) for v in loc],
            'local_exhaust_axis': [round(v, 4) for v in rot @ Vector((1, 0, 0))],
        })
        if m.get('thrust_vectored'):
            rec['markers'][-1]['vectored_from'] = m['thrust_vectored']
            rec['markers'][-1]['local_exhaust_axis_1s'] = [
                [round(f / fps, 2)] + [round(v, 3) for v in rotation_at(m, f) @ Vector((1, 0, 0))]
                for f in range(0, end + 1, int(fps))]
    for name, rec in data['craft'].items():
        rec['world_path_1s'] = craft_world_path(bpy.data.objects[name], fps, end, scene)
    return data


def glyphs(scene, frame):
    scene.frame_set(frame)
    cam = scene.camera
    w, h = scene.render.resolution_x, scene.render.resolution_y
    out = []
    for m in tm.markers():
        mw = m.matrix_world
        p = mw.translation
        axis = (mw.to_3x3() @ Vector((1, 0, 0))).normalized()
        dist = (p - cam.matrix_world.translation).length
        length = max(m['radius_m'] * 5.0, dist * 0.02)
        a = world_to_camera_view(scene, cam, p)
        b = world_to_camera_view(scene, cam, p + axis * length)
        e = world_to_camera_view(scene, cam, p + mw.to_3x3().col[1].normalized() * m['radius_m'])
        if a.z <= 0:
            continue
        out.append({'craft': m['thrust_craft'], 'kind': m['thrust_kind'], 'name': m.name,
                    'throttle': round(tm.throttle_at(m, frame), 3), 'working': m['thrust_working'],
                    'xy': [a.x * w, (1 - a.y) * h], 'tip': [b.x * w, (1 - b.y) * h] if b.z > 0 else None,
                    'r_px': ((e.x - a.x) ** 2 * w * w + (e.y - a.y) ** 2 * h * h) ** 0.5})
    return out


def render(scene, path, samples):
    r = scene.render
    r.resolution_x, r.resolution_y, r.resolution_percentage = 1920, 1080, 100
    r.image_settings.file_format = 'PNG'
    r.image_settings.color_mode = 'RGB'
    r.use_motion_blur = False
    if r.engine == 'CYCLES':
        scene.cycles.samples = samples
        scene.cycles.use_denoising = True
    elif hasattr(scene, 'eevee'):
        scene.eevee.taa_render_samples = samples
    r.filepath = str(path)
    bpy.ops.render.render(write_still=True)


def main():
    o = args()
    t0 = time.time()
    scene, fps, end = build(o['shot'])
    print('THRUST built %s in %.0fs' % (o['shot'], time.time() - t0), flush=True)
    rows, bad, suspects, _ = check(scene, fps, end)
    print('THRUST %s: %d craft, %d markers' % (o['shot'], len(rows), len(tm.markers())))
    for name, act, kinds, flying in rows:
        print('THRUST   %-44s %-12s %-60s %s' % (name[:44], ' '.join('%s:%d' % kv for kv in sorted(kinds.items())),
                                                  act[:60], 'burning' if flying else 'off'))
    o['out'].mkdir(parents=True, exist_ok=True)
    data = summary(scene, fps, end, o['shot'])
    data['problems'] = bad
    (o['out'] / ('%s.json' % o['shot'])).write_text(json.dumps(data, indent=1) + '\n', encoding='utf-8')
    for t in o['still'] or ():
        frame = round(t * fps)
        stem = '%s-%04.1fs' % (o['shot'], t)
        g = glyphs(scene, frame)
        (o['out'] / ('%s-glyphs.json' % stem)).write_text(json.dumps({
            'shot': o['shot'], 't': t, 'frame': frame, 'markers': g}, indent=1), encoding='utf-8')
        render(scene, o['out'] / ('%s-plain.png' % stem), o['samples'])
    for b in bad:
        print('THRUST FAIL', b)
    print('THRUST %s %s' % (o['shot'], 'FAIL' if bad else 'PASS'), flush=True)
    if bad:
        raise SystemExit(1)


if __name__ == '__main__':
    main()
