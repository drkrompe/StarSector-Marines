"""Render genuine Blender evaluations against the revised source clock."""
from pathlib import Path
import hashlib
import json
import math
import sys
import time
import bpy

OUT = Path(__file__).resolve().parent
sys.path.append(str(OUT.parent / 'landing-refinement'))
from render_config import configure_render_device
KNOTS = ((0,580),(2,624),(2.5,648),(6.5,744),(8,774))

def source_at(t):
    for (a,x),(b,y) in zip(KNOTS,KNOTS[1:]):
        if t <= b:
            return x + (y-x)*(t-a)/(b-a)
    return KNOTS[-1][1]

def main():
    scene_path = OUT / 'landing-wreck-pass.blend'
    scene_hash = hashlib.sha256(scene_path.read_bytes()).hexdigest()
    bpy.ops.wm.open_mainfile(filepath=str(scene_path))
    scene = bpy.context.scene
    device = configure_render_device(scene)
    scene.render.use_persistent_data = True
    frames = OUT / 'build/frames'
    frames.mkdir(parents=True, exist_ok=True)
    records = []
    started = time.time()
    for i in range(192):
        source = source_at(i/24)
        scene.frame_set(math.floor(source), subframe=source%1)
        target = frames / f'{i+1:04d}.png'
        scene.render.filepath = str(target)
        bpy.ops.render.render(write_still=True)
        records.append({'output_frame':i+1,'seconds':i/24,'source_frame':source,
                        'sha256':hashlib.sha256(target.read_bytes()).hexdigest()})
        if i%24 == 23:
            print('WRECK_PROGRESS',i+1,192,round(time.time()-started,1),flush=True)
    assert hashlib.sha256(scene_path.read_bytes()).hexdigest() == scene_hash
    report = {'scene':scene_path.name,'scene_sha256':scene_hash,'fps':24,'duration_seconds':8,
              'size':[1920,1080],'samples':scene.cycles.samples,'device':device,
              'retime_knots':KNOTS,'frames':records,'elapsed_seconds':round(time.time()-started,2)}
    (OUT/'render-manifest.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8',newline='\n')

if __name__ == '__main__':
    main()
