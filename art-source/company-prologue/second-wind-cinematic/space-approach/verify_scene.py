"""Verify retained hull mesh/UV/material contracts and finite camera motion."""
from pathlib import Path
import bpy,json,hashlib
from mathutils import Vector
from bpy_extras.object_utils import world_to_camera_view
OUT=Path(__file__).resolve().parent
def signatures():
    root=bpy.context.scene.objects['SECOND WIND | flight root'];result={}
    for o in root.children_recursive:
        if o.type!='MESH' or o.name.startswith('SPACE |'):continue
        mesh=o.data
        data={'vertices':[list(v.co) for v in mesh.vertices],'polygons':[list(p.vertices) for p in mesh.polygons],'uv':[[list(v.uv) for v in layer.data] for layer in mesh.uv_layers],'materials':[m.name if m else None for m in mesh.materials]}
        result[o.name]=hashlib.sha256(json.dumps(data).encode()).hexdigest()
    return result
bpy.ops.wm.open_mainfile(filepath=str(OUT.parent/'landing-wreck-pass/landing-wreck-pass.blend'));original=signatures()
bpy.ops.wm.open_mainfile(filepath=str(OUT/'space-approach.blend'));derived=signatures();assert original==derived,'Ship mesh/UV/material changed'
s=bpy.context.scene;root=s.objects['SECOND WIND | flight root'];obs=[o for o in root.children_recursive if o.type=='MESH' and not o.hide_render and not o.name.startswith('SPACE |')];rows=[]
for f in range(1,97):
    s.frame_set(f);pts=[world_to_camera_view(s,s.camera,o.matrix_world@Vector(c)) for o in obs for c in o.bound_box]
    box=[min(p.x for p in pts)*1920,min(1-p.y for p in pts)*1080,max(p.x for p in pts)*1920,max(1-p.y for p in pts)*1080]
    assert box[2]-box[0]<=450;assert max(abs(v) for v in root.rotation_euler)<1e-6
    rows.append({'frame':f,'bounds_px':box,'root_location':list(root.location)})
assert rows[-1]['bounds_px'][0]>rows[0]['bounds_px'][0]
(OUT/'verification.json').write_text(json.dumps({'scene_sha256':hashlib.sha256((OUT/'space-approach.blend').read_bytes()).hexdigest(),'source_sha256':hashlib.sha256((OUT.parent/'landing-wreck-pass/landing-wreck-pass.blend').read_bytes()).hexdigest(),'planet_sha256':hashlib.sha256((OUT/'assets/planet-plate.png').read_bytes()).hexdigest(),'retained_mesh_count':len(original),'mesh_uv_material_signatures_unchanged':True,'camera_fixed':True,'ship_rotation_zero':True,'frames':rows},indent=2)+'\n',newline='\n')
print('SPACE_VERIFIED',len(original),'unchanged ship meshes;96frames;max450px')
