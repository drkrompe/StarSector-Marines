"""Retain isolated orthographic guides and exact source-world card transforms."""
from pathlib import Path
import json,bpy
from mathutils import Vector
H=Path(__file__).resolve().parent
bpy.ops.wm.open_mainfile(filepath=str(H/'port-refinement.blend'))
s=bpy.context.scene;w=bpy.data.objects['LAYERED | inverse source flight'];s.frame_set(423,subframe=.75)
view=bpy.data.objects['04 PORT / side window'];basis=view.matrix_world.to_3x3();right=(basis@Vector((1,0,0))).normalized();up=(basis@Vector((0,1,0))).normalized();forward=(basis@Vector((0,0,-1))).normalized()
original={o.name:o.hide_render for o in s.objects};records=[]
G=H/'guides';G.mkdir(exist_ok=True)
for ident in ('hound','venture'):
 objs=[o for o in s.objects if o.type=='MESH' and o.name.startswith('PARKED | '+ident+' | ')]
 points=[o.matrix_world@Vector(v) for o in objs for v in o.bound_box]
 values=[[p.dot(a) for p in points] for a in (right,up,forward)]
 ranges=[(min(a),max(a)) for a in values]
 center=sum((a*((lo+hi)/2) for a,(lo,hi) in zip((right,up,forward),ranges)),Vector())
 feet=[o for o in objs if 'service support foot' in o.name or 'maintenance platform grounded foot' in o.name]
 footpoints=[o.matrix_world@v.co for o in feet for v in o.data.vertices if abs(v.co.z-.4)<1e-4]
 selected_contact=min(footpoints,key=lambda p:p.dot(up))
 center+=forward*(selected_contact.dot(forward)-center.dot(forward))
 width=(ranges[0][1]-ranges[0][0])*1.12;height=max((ranges[1][1]-ranges[1][0])*1.15,width*384/1024)
 width=height*1024/384
 camera=bpy.data.objects.new('SHIP CARD GUIDE | '+ident,bpy.data.cameras.new(ident+' isolated guide'));s.collection.objects.link(camera)
 camera.rotation_euler=view.matrix_world.to_euler();camera.location=center-forward*600;camera.data.type='ORTHO';camera.data.ortho_scale=width;s.camera=camera
 for o in s.objects:
  if o.type=='MESH':o.hide_render=o not in objs
 for mark in s.timeline_markers:mark.camera=None
 s.render.resolution_x=1024;s.render.resolution_y=384;s.render.resolution_percentage=100
 s.render.image_settings.file_format='PNG';s.render.image_settings.color_mode='RGBA';s.render.film_transparent=True;s.render.use_compositing=False
 s.render.engine='BLENDER_EEVEE';s.eevee.taa_render_samples=32
 s.render.filepath=str(G/(ident+'-card-guide.png'));bpy.ops.render.render(write_still=True)
 inv=w.matrix_world.inverted();source_center=inv@center;source_right=inv.to_3x3()@right;source_up=inv.to_3x3()@up
 source_root=bpy.data.objects['PARKED | '+ident+' source-plan root']
 contact=inv@selected_contact
 anchor=[.5+(contact-source_center).dot(source_right)/(width*source_right.length_squared),.5+(contact-source_center).dot(source_up)/(height*source_up.length_squared)]
 records.append(dict(id=ident,guide='guides/'+ident+'-card-guide.png',asset='assets/'+ident+'-card-v1.png',canvas=[1024,384],width=width,height=height,source_center=list(source_center),source_right=list(source_right),source_up=list(source_up),contact_source=list(contact),contact_uv=anchor,guide_meshes=[o.name for o in objs],reference_source_frame=423.75,anchor_selection='lowest projected visible maintenance/support foot corner at source z.4; other feet are fixed-view depiction'))
(H/'ship-card-layout.json').write_text(json.dumps(dict(ships=records,representation='Fixed midpoint-view RGBA parallax card; retained models are guide-only',shadow_strategy='Generated RGBA must retain grounded feet and a small soft contact shadow within canvas; no second terrain overlay.'),indent=2),encoding='utf-8')
print('CARD_GUIDES_READY')
