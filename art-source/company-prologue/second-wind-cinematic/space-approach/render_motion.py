"""Render only after preview acceptance and shared GPU coordination."""
from pathlib import Path
import bpy,sys,json,hashlib,time
OUT=Path(__file__).resolve().parent
sys.path.append(str(OUT.parent/'landing-refinement'))
from render_config import configure_render_device
scene_path=OUT/'space-approach.blend';scene_hash=hashlib.sha256(scene_path.read_bytes()).hexdigest()
bpy.ops.wm.open_mainfile(filepath=str(scene_path))
s=bpy.context.scene;device=configure_render_device(s);s.render.use_persistent_data=True
frames=OUT/'build/frames';frames.mkdir(parents=True,exist_ok=True);records=[];start=time.time()
for f in range(1,97):
    s.frame_set(f);p=frames/f'{f:04d}.png';s.render.filepath=str(p);bpy.ops.render.render(write_still=True)
    records.append({'frame':f,'seconds':(f-1)/24,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()})
assert hashlib.sha256(scene_path.read_bytes()).hexdigest()==scene_hash
(OUT/'render-manifest.json').write_text(json.dumps({'scene_sha256':scene_hash,'fps':24,'duration':4,'size':[1920,1080],'silent':True,'device':device,'frames':records,'elapsed_seconds':time.time()-start},indent=2)+'\n',newline='\n')
