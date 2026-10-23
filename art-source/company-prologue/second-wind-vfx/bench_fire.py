"""Fire bench: a breach fire built each way, side by side, on an empty scene, so a flame's look can
be judged and its density checked without building a ship or a battle.

  blender --background --factory-startup --python bench_fire.py -- --out DIR [--samples 32] [--size 6]

Left the round churning flame (swfx_fire.build_fire's default), right the streamed flame
(anchor 'flame' = 'stream'). Both on a breach facing +Z on a grey plate, the hull's aft is -X.
Renders a side view and a view from above at 1.5 s and 3 s after the breach, 960x540, to DIR.
"""
import argparse
import sys
from pathlib import Path

import bpy
from mathutils import Vector

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import swfx_core  # noqa: E402
import swfx_fire  # noqa: E402
import swfx_impacts  # noqa: E402


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument('--out', required=True)
    ap.add_argument('--samples', type=int, default=32)
    ap.add_argument('--size', type=float, default=6.0, help='fire size_m')
    a = ap.parse_args(argv)
    out = Path(a.out)
    out.mkdir(parents=True, exist_ok=True)
    bpy.ops.wm.read_factory_settings(use_empty=True)
    scene = bpy.context.scene
    scene.render.engine = 'CYCLES'
    scene.cycles.samples = a.samples
    scene.render.resolution_x, scene.render.resolution_y = 960, 540
    scene.render.fps = swfx_core.FPS
    world = bpy.data.worlds.new('bench')
    world.use_nodes = True
    world.node_tree.nodes['Background'].inputs['Strength'].default_value = 0.0
    scene.world = world
    ctx = swfx_core.Context(scene, 'patched', None)
    swfx_impacts._knobs(ctx)
    swfx_fire._knobs(ctx)
    u = swfx_core.units
    bpy.ops.mesh.primitive_plane_add(size=u(60))
    bpy.context.object.name = 'bench plate'
    sun = bpy.data.objects.new('bench sun', bpy.data.lights.new('bench sun', 'SUN'))
    sun.data.energy = 2.0
    sun.rotation_euler = (.6, .3, .8)
    scene.collection.objects.link(sun)
    coll = ctx.collection(swfx_impacts.GROUP)
    for x, flame in ((-12.0, None), (12.0, 'stream')):
        e, _ = swfx_impacts.anchor(ctx, 'bench fire', 0, (x, 0.0, 0.0), (0, 0, 1), size_m=a.size, imp_ignite=.15)
        if flame:
            e['flame'] = flame
        rec = swfx_fire.build_fire(ctx, coll, e)
        swfx_impacts._retime([bpy.data.objects[o] for o in rec['objects']], e)
        print('BENCH', flame or 'round', rec, flush=True)
    cam = bpy.data.objects.new('bench cam', bpy.data.cameras.new('bench cam'))
    scene.collection.objects.link(cam)
    scene.camera = cam
    views = {'side': (Vector((0, -70, 14)), Vector((0, 0, 8))), 'above': (Vector((0, -12, 75)), Vector((0, 0, 0)))}
    for name, (loc, tgt) in views.items():
        cam.location = Vector([u(c) for c in loc])
        cam.rotation_euler = (Vector([u(c) for c in tgt]) - cam.location).to_track_quat('-Z', 'Y').to_euler()
        for t in (1.5, 3.0):
            scene.frame_set(int(t * swfx_core.FPS))
            scene.render.filepath = str(out / f'bench-fire-{name}-{t:.1f}s.png')
            bpy.ops.render.render(write_still=True)
            print('BENCH wrote', scene.render.filepath, flush=True)


if __name__ == '__main__':
    main(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])
