"""Render saved entry scene using independent source-time sampling."""
import argparse
import hashlib
import json
from pathlib import Path
import sys
import bpy

HERE=Path(__file__).resolve().parent
p=argparse.ArgumentParser()
p.add_argument('--preview',action='store_true')
args=p.parse_args(sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else [])
blend=HERE/'entry-window.blend'
scene_hash=hashlib.sha256(blend.read_bytes()).hexdigest()
bpy.ops.wm.open_mainfile(filepath=str(blend))
scene=bpy.context.scene
out=HERE/'build'/('previews' if args.preview else 'frames')
out.mkdir(parents=True,exist_ok=True)
records=[]
for i in ((0,48,95) if args.preview else range(96)):
    time=73.0+47.0*i/95
    scene.frame_set(int(time),subframe=time%1)
    scene.render.filepath=str(out/f'frame_{i:04d}.png')
    bpy.ops.render.render(write_still=True)
    frame=out/f'frame_{i:04d}.png'
    records.append({'index':i,'source_frame':time,'sha256':hashlib.sha256(frame.read_bytes()).hexdigest()})
    print('ENTRY_FRAME',i,flush=True)
end_hash=hashlib.sha256(blend.read_bytes()).hexdigest()
assert end_hash==scene_hash,'Scene file changed during render'
(out/'render-manifest.json').write_text(json.dumps({'provenance':'Measured in render process',
    'scene_sha256_before':scene_hash,'scene_sha256_after':end_hash,'frames':records},indent=2)+'\n',newline='\n')
