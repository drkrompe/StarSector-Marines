"""Compare retained geometry/motion and check exterior depth and texture paths."""
import hashlib
import json
from pathlib import Path
import bpy

HERE=Path(__file__).resolve().parent
source=HERE.parent/'forest-reveal-realism/forest-reveal-realism.blend'
names=['02 REVEAL / near glass','SECOND WIND | flight root','PARALLAX | Atlas industrial transit']
def snapshot(path):
    bpy.ops.wm.open_mainfile(filepath=str(path))
    scene=bpy.context.scene
    root=bpy.data.objects[names[1]]
    meshes={o.name:hashlib.sha256(str(([list(v.co) for v in o.data.vertices],[list(p.vertices) for p in o.data.polygons])).encode()).hexdigest() for o in root.children_recursive if o.type=='MESH'}
    samples=[]
    for i in range(96):
        time=73+47*i/95
        scene.frame_set(int(time),subframe=time%1)
        bpy.context.view_layer.update()
        samples.append({n:[list(row) for row in bpy.data.objects[n].matrix_world] for n in names})
    return meshes,samples
before=snapshot(source)
after=snapshot(HERE/'entry-window.blend')
assert before==after,'Retained geometry or motion changed'
missing=[bpy.path.abspath(im.filepath) for im in bpy.data.images if im.source=='FILE' and im.filepath and not Path(bpy.path.abspath(im.filepath)).exists()]
assert not missing,missing
depths={o.name:o['depth_m'] for o in bpy.data.objects if o.name.startswith('ENTRY |')}
assert sorted(depths.values())==[800,900,1600,2300]
report={'all96_source_camera_root_atlas_matrices_match':True,'unchanged_ship_mesh_count':len(after[0]),'all_ship_vertex_topology_hashes_match':True,'missing_textures':missing,'exterior_depths_m':depths,'inspected_1080p_previews':[0,48,95],'movie_playback_review':'Coordinating reviewer owns normal-speed full-cut review; see media-validation.json for encode facts'}
report['scene_sha256']=hashlib.sha256((HERE/'entry-window.blend').read_bytes()).hexdigest()
(HERE/'review').mkdir(exist_ok=True)
(HERE/'review/scene-validation.json').write_text(json.dumps(report,indent=2)+'\n',newline='\n')
print(json.dumps(report,indent=2))
