"""Surface proof inserted into unchanged source landing transforms and mechanics."""
from pathlib import Path
import bpy,json,sys
OUT=Path(__file__).resolve().parent
STAGES=OUT/'build'/'stages';STAGES.mkdir(parents=True,exist_ok=True)
bpy.ops.wm.open_mainfile(filepath=str(OUT.parent/'animatic/jangala-blockout.blend'))
scene=bpy.context.scene;root=bpy.data.objects['SECOND WIND | flight root']
original={o.name:o for o in root.children_recursive if o.type=='MESH'}
with bpy.data.libraries.load(str(STAGES/'second-wind-side-projection-proof.blend'),link=False) as (available,loaded):
    names=tuple(available.objects);loaded.objects=list(names)
imports=dict(zip(names,loaded.objects))
collection=bpy.data.collections.new('SURFACE PROOF | attached preparation');scene.collection.children.link(collection)
retained=[]
for name,obj in imports.items():
    if name in original and obj.type=='MESH':
        original[name].data=obj.data
    if name.startswith(('SURFACE |','Physical armor |','Armor captive','Emergency backing','Service panel')):
        obj.parent=root;collection.objects.link(obj);retained.append(obj)
for obj in loaded.objects:
    if obj not in retained:bpy.data.objects.remove(obj,do_unlink=True)
# Original camera and support/shoe/clamp animation remain exact source mechanisms.
scene.camera=bpy.data.objects['06 LANDING / support review']
scene.render.engine='CYCLES';scene.cycles.samples=24;scene.cycles.use_denoising=True
scene.render.resolution_x=1920;scene.render.resolution_y=1080;scene.render.resolution_percentage=100
scene.render.image_settings.file_format='PNG';scene.render.film_transparent=False
scene.frame_set(696)
bpy.ops.wm.save_as_mainfile(filepath=str(STAGES/'second-wind-projected-landing-proof.blend'))
report={'status':'Material and damage-preparation transfer to original greybox landing. Environment still greybox; not final cinematic.',
 'source_frame_events':{'hold':647,'mid_descent':696,'contact':744,'clamp_secured':774},
 'camera':'06 LANDING / support review','original_ship_meshes_with_transferred_materials':len(original),
 'added_attached_objects':len(retained),'mechanics':'Original flight root action, shoes, pads, clamps, camera and world retained unchanged.'}
(STAGES/'landing-preparation.json').write_text(json.dumps(report,indent=2))
if '--render' in sys.argv:
    for f in (696,746):
        scene.frame_set(f);scene.camera=bpy.data.objects['06 LANDING / support review']
        scene.render.filepath=str(OUT/f'projected-landing-{f}.png');bpy.ops.render.render(write_still=True)
