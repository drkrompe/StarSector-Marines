"""Render actual source half-frames: 240..287.5 at24fps,96samples. No encode."""
import argparse
from pathlib import Path
import sys
import bpy

HERE = Path(__file__).resolve().parent
parser = argparse.ArgumentParser()
parser.add_argument('--preview', action='store_true')
parser.add_argument('--frame',type=int,choices=range(1,97),help='Render one output frame1..96')
args = parser.parse_args(sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else [])
bpy.ops.wm.open_mainfile(filepath=str(HERE / 'managed-land-realism.blend'))
scene = bpy.context.scene
scene.camera = bpy.data.objects['03 LAND / side window']
for marker in scene.timeline_markers:
    marker.camera = None
assert scene.render.fps == 12
assert scene.eevee.taa_render_samples == 96
assert (scene.render.resolution_x,scene.render.resolution_y,scene.render.resolution_percentage) == (1920,1080,100)
output = HERE / 'build/frames'
output.mkdir(parents=True,exist_ok=True)
for index in ([args.frame-1] if args.frame else [0,24,48,72,95] if args.preview else range(96)):
    source = 240 + index*.5
    scene.frame_set(int(source),subframe=source%1)
    scene.render.filepath=str(output/f'frame_{index+1:04d}.png')
    bpy.ops.render.render(write_still=True)
    print('SHOT03_FRAME',index+1,'SOURCE',source,flush=True)
