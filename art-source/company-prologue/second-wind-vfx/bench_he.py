"""HE blast bench: the shell hit built the old way and the torn way, side by side, frame by frame,
so how long the white core holds and how solid it reads can be judged without a battle.

  blender --background --factory-startup --python bench_he.py -- --out DIR [--samples 16] [--frames 8]

Top row an HE shell (swfx_impacts.he), bottom row a Hellbore slug (swfx_impacts.hellbore); left the
whole flash held four frames (tear=False), right the flash torn after its first frame (the
default). All thrown up and a little toward the camera off a grey plate. Writes one 960x540 PNG
per frame from the hit (frame 0) on, at 24 fps, to DIR.
"""
import argparse
import sys
from pathlib import Path

import bpy
from mathutils import Vector

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import swfx_core  # noqa: E402
import swfx_impacts  # noqa: E402

HIT = 4
THROW = (0.0, -.35, .94)


def main(argv):
    ap = argparse.ArgumentParser()
    ap.add_argument('--out', required=True)
    ap.add_argument('--samples', type=int, default=16)
    ap.add_argument('--frames', type=int, default=8)
    a = ap.parse_args(argv)
    out = Path(a.out)
    out.mkdir(parents=True, exist_ok=True)
    bpy.ops.wm.read_factory_settings(use_empty=True)
    scene = bpy.context.scene
    scene.render.engine = 'CYCLES'
    scene.cycles.samples = a.samples
    scene.render.resolution_x, scene.render.resolution_y = 960, 540
    scene.render.fps = swfx_core.FPS
    scene.view_settings.view_transform = 'AgX'
    world = bpy.data.worlds.new('bench')
    world.use_nodes = True
    world.node_tree.nodes['Background'].inputs['Strength'].default_value = 0.0
    scene.world = world
    ctx = swfx_core.Context(scene, 'patched', None)
    swfx_impacts._knobs(ctx)
    u = swfx_core.units
    bpy.ops.mesh.primitive_plane_add(size=u(120))
    bpy.context.object.name = 'bench plate'
    bpy.context.object.location.z = -u(2)
    sun = bpy.data.objects.new('bench sun', bpy.data.lights.new('bench sun', 'SUN'))
    sun.data.energy = 2.0
    sun.rotation_euler = (.6, .3, .8)
    scene.collection.objects.link(sun)
    throw = Vector(THROW).normalized()
    for x, tear in ((-16.0, False), (16.0, True)):
        swfx_impacts.he(ctx, HIT, (x, 0.0, 12.0), throw, tear=tear)
        swfx_impacts.hellbore(ctx, HIT, (x, 0.0, -14.0), throw, tear=tear)
    cam = bpy.data.objects.new('bench cam', bpy.data.cameras.new('bench cam'))
    scene.collection.objects.link(cam)
    scene.camera = cam
    cam.location = Vector([u(c) for c in (0, -95, 8)])
    cam.rotation_euler = (Vector([u(c) for c in (0, 0, 0)]) - cam.location).to_track_quat('-Z', 'Y').to_euler()
    for j in range(a.frames):
        scene.frame_set(HIT + j)
        scene.render.filepath = str(out / f'bench-he-f{j:02d}.png')
        bpy.ops.render.render(write_still=True)
        print('BENCH wrote', scene.render.filepath, flush=True)


if __name__ == '__main__':
    main(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])
