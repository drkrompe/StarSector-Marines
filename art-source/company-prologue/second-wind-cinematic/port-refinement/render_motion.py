"""Render genuine half-frame samples from the saved refined-port scene."""
import hashlib
import json
from pathlib import Path
import sys

import bpy

HERE = Path(__file__).resolve().parent
SCENE = HERE / 'port-refinement.blend'
bpy.ops.wm.open_mainfile(filepath=str(SCENE))
scene = bpy.context.scene
scene.camera = bpy.data.objects['04 PORT / side window']
for marker in scene.timeline_markers:
    marker.camera = None
scene.eevee.taa_render_samples = 64
output = HERE / 'build/frames'
output.mkdir(exist_ok=True, parents=True)
indices = [0, 48, 95] if '--preview' in sys.argv else list(range(96))
for index in indices:
    source_frame = 400 + .5 * index
    scene.frame_set(int(source_frame), subframe=source_frame % 1)
    scene.render.filepath = str(output / f'frame-{index:04d}.png')
    bpy.ops.render.render(write_still=True)
    if index % 12 == 0:
        print(f'PORT_MOTION {index + 1}/96', flush=True)
report = {
    'scene_sha256': hashlib.sha256(SCENE.read_bytes()).hexdigest(),
    'indices': indices,
    'source_frame_mapping': '400 + 0.5*i at source 12fps; output 24fps',
    'samples': 64,
    'frames': [
        {'index': index, 'sha256': hashlib.sha256(
            (output / f'frame-{index:04d}.png').read_bytes()).hexdigest()}
        for index in indices
    ],
}
(output.parent / 'render-manifest.json').write_text(
    json.dumps(report, indent=2) + '\n', encoding='utf-8')
print('PORT_FRAMES_RENDERED', flush=True)
