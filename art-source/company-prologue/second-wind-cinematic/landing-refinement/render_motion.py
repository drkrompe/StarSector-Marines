"""Render 192 true scene evaluations with an explicit eight-second editorial retime."""
from pathlib import Path
import sys,math,json,hashlib,time
import bpy
OUT=Path(__file__).resolve().parent;sys.path.insert(0,str(OUT))
from render_config import configure_render_device
KNOTS=[(0,580),(2,624),(2.5,648),(6.5,744),(8,774)]
def source_at(t):
    for (a,x),(b,y) in zip(KNOTS,KNOTS[1:]):
        if t<=b:return x+(y-x)*(t-a)/(b-a)
    return KNOTS[-1][1]
def main():
    path=OUT/'landing-refinement.blend';bpy.ops.wm.open_mainfile(filepath=str(path))
    scene=bpy.context.scene;device=configure_render_device(scene);scene.render.use_persistent_data=True
    frames=OUT/'build/frames';frames.mkdir(parents=True,exist_ok=True)
    records=[];began=time.time()
    for i in range(192):
        sf=source_at(i/24);scene.frame_set(math.floor(sf),subframe=sf%1)
        p=frames/f'{i+1:04d}.png';scene.render.filepath=str(p);bpy.ops.render.render(write_still=True)
        records.append({'output_frame':i+1,'output_seconds':i/24,'source_frame':sf,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()})
        if i%24==23:print('LANDING_PROGRESS',i+1,192,'elapsed',round(time.time()-began,1),flush=True)
    report={'scene':path.name,'scene_sha256':hashlib.sha256(path.read_bytes()).hexdigest(),
      'native_resolution':[1920,1080],'output_fps':24,'duration_seconds':8,'frames':records,
      'source_fps':12,'retime_knots':KNOTS,'render_device':device,'samples':scene.cycles.samples,
      'interpolation':'Original 3D actions evaluated at mapped source times; no synthesized video frames.',
      'elapsed_seconds':round(time.time()-began,3)}
    (OUT/'render-manifest.json').write_text(json.dumps(report,indent=2)+'\n', encoding='utf-8', newline='\n')
if __name__=='__main__':main()
