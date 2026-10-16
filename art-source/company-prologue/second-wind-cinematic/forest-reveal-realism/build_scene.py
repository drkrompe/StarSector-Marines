"""Forest realism conversion; exact retained Atlas lift/camera animation."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import sys
import bpy
from bpy_extras.object_utils import world_to_camera_view

HERE=Path(__file__).resolve().parent
parser=argparse.ArgumentParser()
parser.add_argument('--preview',action='store_true')
parser.add_argument('--render',action='store_true')
args=parser.parse_args(sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else [])
source=HERE.parent/'parallax-window/jangala-parallax-atlas-lift.blend'
bpy.ops.wm.open_mainfile(filepath=str(source))
scene=bpy.context.scene
camera=bpy.data.objects['02 REVEAL / near glass']
atlas=bpy.data.objects['PARALLAX | Atlas industrial transit']
root=bpy.data.objects['SECOND WIND | flight root']
scene.camera=camera

def samples():
    result=[]
    for i in range(96):
        t=120+.5*i
        scene.frame_set(int(t),subframe=t%1)
        bpy.context.view_layer.update()
        p=world_to_camera_view(scene,camera,atlas.matrix_world.translation)
        result.append({'i':i,'source_frame':t,'camera':[list(row) for row in camera.matrix_world],
            'root':[list(row) for row in root.matrix_world],
            'atlas':[list(row) for row in atlas.matrix_world],
            'centre_px':[p.x*1920,(1-p.y)*1080]})
    return result

before=samples()
scene.frame_set(144)
records=[]
def replace_material_image(name,path):
    obj=bpy.data.objects[name]
    mat=obj.active_material.copy()
    obj.data=obj.data.copy()
    obj.data.materials.clear()
    obj.data.materials.append(mat)
    tex=next(node for node in mat.node_tree.nodes if node.type=='TEX_IMAGE')
    tex.image=bpy.data.images.load(str(path),check_existing=True)
    tex.image.alpha_mode='STRAIGHT'
    records.append({'object':name,'image':os.path.relpath(path,HERE).replace(os.sep,'/'),
        'dimensions':list(tex.image.size),'sha256':hashlib.sha256(path.read_bytes()).hexdigest()})
    return tex

def master_rgb(name, preserve_skyline=False):
    mat=bpy.data.objects[name].active_material
    nodes,links=mat.node_tree.nodes,mat.node_tree.links
    extracted=next(n for n in nodes if n.type=='TEX_IMAGE')
    emission=next(n for n in nodes if n.type=='EMISSION')
    original=nodes.new('ShaderNodeTexImage')
    original.image=bpy.data.images.load(str(HERE.parent/'realism-lookdev/landscape-realism-v1.png'),check_existing=True)
    original.extension='CLIP'
    original.interpolation='Linear'
    if preserve_skyline:
        # Preserve generated skyline pixels, use detailed original canopy below.
        uv=nodes.new('ShaderNodeTexCoord')
        split=nodes.new('ShaderNodeSeparateXYZ')
        links.new(uv.outputs['UV'],split.inputs[0])
        ramp=nodes.new('ShaderNodeMapRange')
        ramp.clamp=True
        ramp.inputs['From Min'].default_value=.76
        ramp.inputs['From Max'].default_value=.80
        links.new(split.outputs['Y'],ramp.inputs['Value'])
        mix=nodes.new('ShaderNodeMixRGB')
        links.new(ramp.outputs[0],mix.inputs[0])
        links.new(original.outputs['Color'],mix.inputs[1])
        links.new(extracted.outputs['Color'],mix.inputs[2])
        links.new(mix.outputs[0],emission.inputs['Color'])
    else:
        links.new(original.outputs['Color'],emission.inputs['Color'])

replace_material_image('PARALLAX | sky',HERE.parent/'managed-land-realism/assets/sky-realism-v1.png')
replace_material_image('PARALLAX | distant hills',HERE/'assets/far-realism-v1.png')
bpy.data.objects['PARALLAX | far trees'].hide_render=True
replace_material_image('PARALLAX | medium trees and river',HERE/'assets/mid-realism-v1.png')
replace_material_image('PARALLAX | near trees',HERE/'assets/near-realism-v1.png')
master_rgb('PARALLAX | distant hills',preserve_skyline=True)
master_rgb('PARALLAX | medium trees and river')
replace_material_image('PARALLAX | cloud bank',HERE.parent/'managed-land-realism/assets/clouds-realism-v1.png')
for mat in bpy.data.materials:
    if mat.name.startswith('PAINT | fixed cabin artwork'):
        for tex in mat.node_tree.nodes:
            if tex.type=='TEX_IMAGE':
                tex.image=bpy.data.images.load(str(HERE/'assets/cabin-realism-v1.png'),check_existing=True)

after=samples()
assert before==after, 'Camera/root/Atlas transforms changed'
manifest={'source':'../parallax-window/jangala-parallax-atlas-lift.blend',
    'mapping':'source120+0.5*i, i=0..95; source12fps, output24fps,4s',
    'unchanged_all96_camera_root_atlas_matrices':before==after,
    'layers':records,'motion_samples':after,
    'representation':'Three independent forest depth cards (34000,10500,5000m); sky/clouds independent; original Atlas lift unmodified',
    'limits':'New ImageGen layers preserve broad composition rather than pixel registration. Individual trees within each plane have no separate depth. Cabin is guided projection, not UV artwork for other viewpoints.'}
for image in bpy.data.images:
    if image.source=='FILE' and image.filepath:
        image.filepath=str(Path(bpy.path.abspath(image.filepath)).resolve())
scene.render.resolution_x=1920
scene.render.resolution_y=1080
scene.render.resolution_percentage=100
scene.eevee.taa_render_samples=64
scene.frame_set(144)
bpy.context.preferences.filepaths.save_version=0
bpy.ops.wm.save_as_mainfile(filepath=str(HERE/'forest-reveal-realism.blend'),relative_remap=True,compress=True)
(HERE/'scene-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
out=HERE/'build/frames'
out.mkdir(parents=True,exist_ok=True)
if args.preview or args.render:
    for i in (range(96) if args.render else (0,24,48,72,95)):
        t=120+.5*i
        scene.frame_set(int(t),subframe=t%1)
        scene.render.filepath=str(out/f'frame_{i:04d}.png')
        bpy.ops.render.render(write_still=True)
        print('FOREST_FRAME',i,flush=True)
