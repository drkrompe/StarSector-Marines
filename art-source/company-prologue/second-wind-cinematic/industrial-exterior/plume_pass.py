"""C4/C5 plume repair pass: the drive plumes the finals were rendered without, alone, for compositing.

    blender -b --factory-startup --python-exit-code 1 --python plume_pass.py -- --shot c4|c5 --plan OUT.json
    blender -b --factory-startup --python-exit-code 1 --python plume_pass.py -- --shot c4|c5 \
        --chunk 120-239 --out DIR [--only 24,30]

The C4/C5 finals of 2026-10-21 (content f2323ac8f) were queued before every craft the port flies
had a plume (`thrust_shots.hang_plumes`): only Second Wind, the departing Colossus and the Hermes
lighter on pad 95 showed their drives, and the inbound Shepherd let down through C4's opening
seconds with nothing firing. Rather than re-render, the owner chose to lay the missing plumes over
the finished frames (2026-10-21). They are soft emissive cones that light nothing and cast no
shadow (`port_fleet.far_plume`), so the frames with them and the frames without differ only where
a plume is seen.

The scene is built exactly as the final builds it (same script, CINE_FINAL quality, same camera)
and then everything but the added plumes (`thrust_shots.NEW_PLUME`) is made a holdout, so hulls,
buildings and ground still hide what is behind them, on a transparent film. Transparent effects
(other plumes, the kit's volumes, dust, steam) are hidden rather than held out, so they cut no
holes. Each frame is written as scene-linear, premultiplied RGBA OpenEXR, which is what
`plume_composite.py` lays over the final frame in scene-linear light.

--plan writes which frames of the shot need the pass: those where an added plume has throttle and
is on screen, with each craft's frame ranges. --chunk plans that chunk the same way and renders its
frames, each named by its index in the final's chunk folder (00000 is the chunk's first frame), and
writes the chunk's plan beside them as plan.json; a frame already written is skipped, so a queued
job can be swapped out and resumed. --only renders those chunk indices instead (the evidence
frames). Both need CINE_FINAL=1, as the finals had.
"""
import json
import os
import sys
import time
from pathlib import Path

import bpy
from bpy_extras.object_utils import world_to_camera_view
from mathutils import Vector

BASE = Path(__file__).resolve().parent
sys.path.insert(0, str(BASE))
sys.path.insert(0, str(BASE.parent / 'thrust-markers'))
NEW_PLUME = 'thrust_plume_added'        # thrust_shots.NEW_PLUME
MARGIN = 0.03                           # of the frame: a plume's soft edge just off screen still counts


def args():
    a = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
    o = {'shot': None, 'plan': None, 'chunk': None, 'out': None, 'only': None}
    for i, x in enumerate(a):
        if x in ('--shot', '--plan', '--chunk', '--out', '--only'):
            o[x[2:]] = a[i + 1]
    return o


def build(shot):
    """(scene, first scene frame of the shot, frame count), built as the shot's final builds it."""
    if shot == 'c4':
        import c4x_scene as c4
        return c4.build(), 0, c4.FRAMES
    if shot == 'c5':
        import c5_scene as c5
        return c5.setup('3d'), c5.C4_END, c5.FRAMES
    raise SystemExit('PLUME_PASS unknown --shot %r (c4 or c5)' % shot)


def added_plumes():
    return [o for o in bpy.data.objects if o.get(NEW_PLUME)]


def throttle(ob, frame):
    import thrust_markers as tm
    return tm.throttle_at(ob.parent, frame) if ob.parent is not None else 0.0


def on_screen(scene, ob, depsgraph):
    """Whether the plume's evaluated bounding box overlaps the camera's frame."""
    ev = ob.evaluated_get(depsgraph)
    if ev.hide_render:
        return False
    m = ev.matrix_world
    xs, ys = [], []
    for c in ev.bound_box:
        p = world_to_camera_view(scene, scene.camera, m @ Vector(c))
        if p.z <= 0:
            continue
        xs.append(p.x)
        ys.append(p.y)
    if not xs:
        return False
    return max(xs) > -MARGIN and min(xs) < 1 + MARGIN and max(ys) > -MARGIN and min(ys) < 1 + MARGIN


def plan(scene, first, span, plumes):
    """{'frames': [shot index, ...], 'craft': {craft: [[from, to], ...]}} of the frames to render,
    over the shot indices in span (first, last)."""
    need, by_craft = [], {}
    for i in range(span[0], span[1] + 1):
        f = first + i
        hot = [o for o in plumes if throttle(o, f) > 1e-3]
        if not hot:
            continue
        scene.frame_set(f)
        dg = bpy.context.evaluated_depsgraph_get()
        seen = {o[NEW_PLUME] for o in hot if on_screen(scene, o, dg)}
        if seen:
            need.append(i)
        for c in seen:
            by_craft.setdefault(c, []).append(i)
    return {'frames': need, 'craft': {c: ranges(v) for c, v in sorted(by_craft.items())}}


def ranges(idx):
    out = []
    for i in idx:
        if out and i == out[-1][1] + 1:
            out[-1][1] = i
        else:
            out.append([i, i])
    return out


def transparent_effect(ob):
    """A see-through effect (a plume, the kit's volumes, dust, steam): hidden in the pass rather
    than held out, so it cuts no hole where the final shows the plume through it."""
    mats = [s.material for s in ob.material_slots if s.material is not None]
    if not mats:
        return False
    for m in mats:
        if getattr(m, 'surface_render_method', '') == 'BLENDED':
            continue
        out = m.node_tree and next((n for n in m.node_tree.nodes
                                    if n.type == 'OUTPUT_MATERIAL' and n.is_active_output), None)
        if out is not None and out.inputs['Volume'].links and not out.inputs['Surface'].links:
            continue
        return False
    return True


def isolate(scene, plumes):
    """Only the added plumes render; everything else is a holdout or, if see-through, hidden."""
    keep = {o.name for o in plumes}
    held = hidden = 0
    for ob in bpy.data.objects:
        if ob.name in keep or ob.type in ('LIGHT', 'CAMERA', 'LIGHT_PROBE'):
            continue
        if transparent_effect(ob):
            ad = ob.animation_data
            if ad is not None and ad.action is not None:
                import thrust_markers as tm
                for fc in tm.fcurves(ob):
                    if fc.data_path == 'hide_render':
                        fc.mute = True
            ob.hide_render = True
            hidden += 1
        else:
            ob.is_holdout = True
            held += 1
    scene.render.film_transparent = True
    s = scene.render.image_settings
    s.file_format = 'OPEN_EXR'
    s.color_mode = 'RGBA'
    s.color_depth = '16'
    s.exr_codec = 'ZIP'
    print('PLUME_PASS isolate: %d plumes, %d holdouts, %d see-through effects hidden' % (
        len(plumes), held, hidden), flush=True)


def main():
    o = args()
    t0 = time.time()
    if os.environ.get('CINE_FINAL', '0') != '1':
        raise SystemExit('PLUME_PASS: run with CINE_FINAL=1, the settings the finals rendered with')
    scene, first, count = build(o['shot'])
    plumes = added_plumes()
    print('PLUME_PASS built %s in %.0fs: %d added plumes on %d craft' % (
        o['shot'], time.time() - t0, len(plumes), len({p[NEW_PLUME] for p in plumes})), flush=True)
    if not plumes:
        raise SystemExit('PLUME_PASS: this build adds no plumes (CINE_PLUMES=0?)')
    if o['chunk'] is None:
        p = plan(scene, first, (0, count - 1), plumes)
        p.update(shot=o['shot'], first_scene_frame=first, count=count)
        Path(o['plan']).parent.mkdir(parents=True, exist_ok=True)
        Path(o['plan']).write_text(json.dumps(p, indent=1) + '\n', encoding='utf-8')
        print('PLUME_PASS plan %s: %d of %d frames; %s' % (o['shot'], len(p['frames']), count, p['craft']),
              flush=True)
        return
    a, b = (int(x) for x in o['chunk'].split('-'))
    out = Path(o['out'])
    out.mkdir(parents=True, exist_ok=True)
    if o['only']:
        todo = [int(x) for x in o['only'].split(',')]
    else:
        p = plan(scene, first, (a, b), plumes)
        p.update(shot=o['shot'], first_scene_frame=first, chunk=[a, b])
        (out / 'plan.json').write_text(json.dumps(p, indent=1) + '\n', encoding='utf-8')
        todo = [i - a for i in p['frames']]
        print('PLUME_PASS chunk %s: %d frames to render; %s' % (o['chunk'], len(todo), p['craft']), flush=True)
    isolate(scene, plumes)
    scene.render.use_persistent_data = True
    for k in todo:
        path = out / ('%05d.exr' % k)
        if path.exists() and path.stat().st_size > 0:
            continue
        scene.frame_set(first + a + k)
        scene.render.filepath = str(path)
        bpy.ops.render.render(write_still=True)
        print('PLUME_PASS_FRAME', a + k, flush=True)
    print('PLUME_PASS %s chunk %s: %d frames in %.0fs' % (o['shot'], o['chunk'], len(todo), time.time() - t0),
          flush=True)


if __name__ == '__main__':
    main()
