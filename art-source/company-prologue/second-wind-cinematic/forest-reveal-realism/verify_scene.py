"""Reopen saved source/derived scenes and compare full sampled motion."""
import json
import hashlib
import struct
from pathlib import Path
import bpy
HERE=Path(__file__).resolve().parent
def read_scene(path):
    bpy.ops.wm.open_mainfile(filepath=str(path))
    scene=bpy.context.scene
    names=['02 REVEAL / near glass','SECOND WIND | flight root','PARALLAX | Atlas industrial transit']
    ship=sorted([o for o in bpy.data.objects[names[1]].children_recursive if o.type=='MESH'],key=lambda o:o.name)
    geometry={o.name:hashlib.sha256(json.dumps({'vertices':[list(v.co) for v in o.data.vertices],
        'polygons':[list(p.vertices) for p in o.data.polygons]},sort_keys=True).encode('utf-8')).hexdigest() for o in ship}
    ship_motion=hashlib.sha256()
    samples=[]
    for i in range(96):
        t=120+.5*i
        scene.frame_set(int(t),subframe=t%1)
        bpy.context.view_layer.update()
        samples.append([[float(v) for row in bpy.data.objects[n].matrix_world for v in row] for n in names])
        for o in ship:
            ship_motion.update(struct.pack('<16f',*[float(v) for row in o.matrix_world for v in row]))
    return samples,geometry,ship_motion.hexdigest()
source,source_geo,source_motion=read_scene(HERE.parent/'parallax-window/jangala-parallax-atlas-lift.blend')
derived,derived_geo,derived_motion=read_scene(HERE/'forest-reveal-realism.blend')
assert source_geo==derived_geo
assert source_motion==derived_motion
error=max(abs(a-b) for s,d in zip(source,derived) for sm,dm in zip(s,d) for a,b in zip(sm,dm))
assert error<1e-7, error
scene=bpy.context.scene
assert scene.render.fps==12
assert (scene.render.resolution_x,scene.render.resolution_y,scene.render.resolution_percentage)==(1920,1080,100)
missing=[]
for image in bpy.data.images:
    if image.source=='FILE' and image.filepath and not Path(bpy.path.abspath(image.filepath)).exists():
        missing.append(image.filepath)
assert not missing, missing
report={'saved_source_derived_motion_max_matrix_error':error,'sample_count':96,
    'unchanged_ship_mesh_count':len(source_geo),'ship_mesh_vertex_topology_hashes_match':True,
    'all96_ship_mesh_world_transforms_match':True,'ship_motion_sha256':source_motion,
    'missing_image_paths':missing,'camera_root_atlas_unchanged':True,
    'format':'source12fps; 96half-frame evaluations delivered24fps',
    'limits':'Checks retained transform equality and texture availability, not artistic acceptance.'}
(HERE/'review').mkdir(exist_ok=True)
(HERE/'review/scene-validation.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
print(json.dumps(report,indent=2))
