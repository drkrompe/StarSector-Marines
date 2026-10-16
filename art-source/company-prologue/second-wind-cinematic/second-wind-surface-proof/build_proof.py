"""Retained-mesh Second Wind arrival surface preparation. Run in background Blender."""
from pathlib import Path
import bpy, json, math, random, hashlib, struct
from mathutils import Vector

OUT=Path(__file__).resolve().parent
BASELINES=OUT/'build'/'baselines'; BASELINES.mkdir(parents=True,exist_ok=True)
BASE=OUT.parent.parent
SOURCE=OUT.parent/'animatic/jangala-blockout.blend'
OUT.mkdir(exist_ok=True)
bpy.ops.wm.open_mainfile(filepath=str(SOURCE))
scene=bpy.context.scene
scene.frame_set(744)
root=bpy.data.objects['SECOND WIND | flight root']
ship=list(root.children_recursive)
meshes=[o for o in ship if o.type=='MESH']
with bpy.data.libraries.load(str(BASE/'second-wind-geometry/second-wind-geometry.blend'),link=False) as (available,loaded):
    canonical_names=tuple(available.objects); loaded.objects=list(canonical_names)
canonical_materials={name:[m.name for m in obj.data.materials if m] for name,obj in zip(canonical_names,loaded.objects) if obj and obj.type=='MESH'}
for imported in loaded.objects:
    if imported: bpy.data.objects.remove(imported,do_unlink=True)
def digest(o):
    h=hashlib.sha256()
    for v in o.data.vertices: h.update(struct.pack('<3f',*v.co))
    for p in o.data.polygons: h.update(struct.pack('<%di'%len(p.vertices),*p.vertices))
    return h.hexdigest()
before={o.name:digest(o) for o in meshes}
# Derive a separate source scene; original saved scenes are read only.
keep=set(ship+[root])
for o in list(bpy.data.objects):
    if o not in keep: bpy.data.objects.remove(o,do_unlink=True)
root.animation_data_clear(); root.location=(0,0,21)
for o in ship: o.animation_data_clear()
root['surface_authority']='V3 identity + retained animatic landing hardware; metre scale provisional'
mapping=bpy.data.objects.new('SURFACE | common rigid hull coordinate system',None)
scene.collection.objects.link(mapping); mapping.parent=root
mapping['contract']='Object coordinates travel with ship. One repeat = six hull units / nine provisional metres.'

def material(name,color,metal=.25):
    m=bpy.data.materials.new('ARRIVAL | '+name); m.use_nodes=True
    n=m.node_tree.nodes; l=m.node_tree.links; p=n.get('Principled BSDF')
    p.inputs['Metallic'].default_value=metal; p.inputs['Roughness'].default_value=.73
    tex=n.new('ShaderNodeTexCoord'); tex.object=mapping
    scale=n.new('ShaderNodeVectorMath'); scale.operation='SCALE'; scale.inputs[3].default_value=1/6
    l.new(tex.outputs['Object'],scale.inputs[0])
    noise=n.new('ShaderNodeTexNoise'); noise.inputs['Scale'].default_value=2.8; noise.inputs['Detail'].default_value=5
    l.new(scale.outputs[0],noise.inputs['Vector'])
    ramp=n.new('ShaderNodeValToRGB'); ramp.color_ramp.elements[0].position=.28; ramp.color_ramp.elements[1].position=.70
    ramp.color_ramp.elements[0].color=(*(c*.21 for c in color),1)
    ramp.color_ramp.elements[1].color=(*color,1)
    l.new(noise.outputs['Fac'],ramp.inputs[0]); l.new(ramp.outputs[0],p.inputs['Base Color'])
    fine=n.new('ShaderNodeTexNoise'); fine.inputs['Scale'].default_value=45; fine.inputs['Detail'].default_value=3
    l.new(scale.outputs[0],fine.inputs['Vector'])
    bump=n.new('ShaderNodeBump'); bump.inputs['Strength'].default_value=.26; bump.inputs['Distance'].default_value=.07
    l.new(fine.outputs['Fac'],bump.inputs['Height']); l.new(bump.outputs['Normal'],p.inputs['Normal'])
    m['atlas_role']=name; m['mapping']='common rigid ship object coordinate; box projection for atlas'
    return m
cream=material('cream',(.61,.55,.41)); oxide=material('oxide',(.40,.135,.045)); steel=material('gunmetal',(.16,.18,.19),.75); backing=material('backing',(.055,.064,.071),.65)
weld=material('exposed weld',(.28,.29,.27),.8)
for o in meshes:
    for i,m in enumerate(o.data.materials):
        source_names=canonical_materials.get(o.name,[])
        name=(source_names[min(i,len(source_names)-1)] if source_names else (m.name if m else '')).lower()
        target=cream if any(s in name for s in ['cream','pod','teal']) else oxide if 'orange' in name else backing if any(s in name for s in ['recess','dark']) else steel
        if 'light' not in name and 'plume' not in name: o.data.materials[i]=target
    o['retained_mesh_sha256']=before[o.name]
    # Explicit per-component UV coverage for later baking; material uses stable object coordinates now.
    bpy.ops.object.select_all(action='DESELECT'); o.select_set(True); bpy.context.view_layer.objects.active=o
    if not o.data.uv_layers:
        bpy.ops.object.mode_set(mode='EDIT'); bpy.ops.mesh.select_all(action='SELECT'); bpy.ops.uv.smart_project(island_margin=.015); bpy.ops.object.mode_set(mode='OBJECT')

damage=bpy.data.collections.new('ARRIVAL | sacrificial armor scars and emergency repairs'); scene.collection.children.link(damage)
hull=next(o for o in meshes if o.name.startswith('Main hull'))
random.seed(810)
def surface(x,z,side=1):
    hit,p,n,idx=hull.ray_cast(Vector((x,side*100,z)),Vector((0,-side,0)))
    return (p,n) if hit else None
def meshpart(name,verts,faces,mat):
    me=bpy.data.meshes.new(name); me.from_pydata(verts,[],faces); me.update()
    o=bpy.data.objects.new(name,me); damage.objects.link(o); o.parent=root; o.data.materials.append(mat)
    o['damage_role']='External sacrificial layer only; retained pressure hull below remains uncut'
    return o
# Large, unique spalled armor overlays. Their center is dark surviving backing;
# torn radial armor petals stand proud, never a painted open pressure breach.
scars=[]
for side in (1,-1):
    for k,(x,z,r) in enumerate([(45,5.6,3.1),(30,8.3,2.1),(-16,8.3,3.2),(-29,2.5,1.8),(45,-4,1.45)]):
        rings=[]; count=18
        for rad,offset in [(0,.10),(.66,.13),(1,.27),(1.23,.16)]:
            row=[]
            for j in range(count):
                a=j*math.tau/count; rr=r*rad*(1+random.uniform(-.12,.12))
                hit=surface(x+math.cos(a)*rr,z+math.sin(a)*rr,side)
                if not hit: row.append(None); continue
                p,n=hit; row.append(tuple(p+n*(offset+(random.uniform(.06,.42) if rad==1 else 0))))
            rings.append(row)
        if any(v is None for row in rings for v in row): continue
        verts=sum(rings,[]); faces=[]
        for a in range(3):
            for j in range(count): faces.append((a*count+j,a*count+(j+1)%count,(a+1)*count+(j+1)%count,(a+1)*count+j))
        ob=meshpart(f'Spall {side:+d} {k:02d} | pressure backing and torn rim',verts,faces,backing)
        ob.data.materials.append(weld); ob.data.materials.append(oxide)
        for p in ob.data.polygons: p.material_index=0 if p.index<count else (1 if p.index<2*count else 2)
        scars.append(ob.name)
# Mismatched, conforming repair patches and actual perimeter fasteners on flank.
patches=[]
for side in (1,-1):
    for k,(x,z,w,h) in enumerate([(51,0,5,3.0),(35,7,5,3),(-11,-4,5,3),(-25,5,5,3),(8,7,4,2.2)]):
        points=[(x-w/2,z-h/2),(x+w/2,z-h/2),(x+w/2,z+h/2),(x-w/2,z+h/2)]
        hits=[surface(a,b,side) for a,b in points]
        if not all(hits):continue
        verts=[tuple(p+n*.24) for p,n in hits]
        ob=meshpart(f'Salvage patch {side:+d} {k:02d}',verts,[(0,1,2,3)],cream if k%2 else steel)
        sol=ob.modifiers.new('External plate thickness','SOLIDIFY'); sol.thickness=.15
        patches.append(ob.name)
        for a,b in points:
            p,n=surface(a,b,side)
            bpy.ops.mesh.primitive_uv_sphere_add(segments=8,ring_count=4,radius=.13,location=(0,0,0))
            bolt=bpy.context.object; bolt.name='Repair plate captive fastener'; bolt.parent=root; bolt.location=p+n*.39; bolt.data.materials.append(weld)
            for c in list(bolt.users_collection): c.objects.unlink(bolt)
            damage.objects.link(bolt)
after={o.name:digest(o) for o in meshes}
assert before==after,'Retained geometry changed'
for o in list(ship):
    if any(s in o.name.lower() for s in ['plume','entry','cloud','glow']): o.hide_render=True
def camera(name,loc,target,lens):
    d=bpy.data.cameras.new(name); c=bpy.data.objects.new(name,d); scene.collection.objects.link(c); c.location=loc
    c.rotation_euler=(Vector(target)-c.location).to_track_quat('-Z','Y').to_euler(); d.lens=lens; return c
sidecam=camera('PROOF | port side landing surface',(12,240,37),(9,0,25),42)
quarter=camera('PROOF | port bow quarter',(168,215,136),(8,0,25),48)
scene.camera=sidecam
world=bpy.data.worlds.new('Open daylight'); scene.world=world; world.use_nodes=True
world.node_tree.nodes['Background'].inputs[0].default_value=(.50,.62,.76,1); world.node_tree.nodes['Background'].inputs[1].default_value=.6
sun_data=bpy.data.lights.new('Daylight sun','SUN'); sun=bpy.data.objects.new('Daylight sun',sun_data); scene.collection.objects.link(sun)
sun.rotation_euler=(math.radians(28),math.radians(-25),math.radians(-35)); sun_data.energy=2.6; sun_data.angle=math.radians(6)
scene.render.engine='CYCLES'; scene.cycles.samples=32; scene.cycles.use_denoising=True
scene.render.resolution_x=1920; scene.render.resolution_y=1080; scene.render.resolution_percentage=100
scene.render.image_settings.file_format='PNG'; scene.render.film_transparent=True
scene.view_settings.view_transform='AgX'; scene.view_settings.look='AgX - Medium High Contrast'; scene.view_settings.exposure=.65
scene.timeline_markers.clear()
scene.frame_start=1;scene.frame_end=49;scene.render.fps=24
# A rigid movement test, separate from landing: identical surface locations travel with root.
for f,loc,ang in [(1,(0,0,21),0),(25,(7,0,25),-.06),(49,(14,0,21),.06)]:
    root.location=loc; root.rotation_euler[2]=ang
    root.keyframe_insert(data_path='location',frame=f); root.keyframe_insert(data_path='rotation_euler',frame=f)
scene.frame_set(1)
report={'status':'Retained geometry plus procedural material / unique sacrificial damage preparation; no final generated texture or landing claim',
        'source':str(SOURCE),'retained_mesh_count':len(meshes),'retained_vertex_count':sum(len(o.data.vertices) for o in meshes),
        'retained_polygon_count':sum(len(o.data.polygons) for o in meshes),'all_retained_mesh_hashes_match':before==after,
        'mesh_hashes':before,'uv_covered_meshes':sum(bool(o.data.uv_layers) for o in meshes),'scar_groups':scars,'repair_patches':patches,
        'axes':{'+X':'bow','+Y':'port','+Z':'up'},'provisional_scale_metres_per_hull_unit':1.5,
        'mapping':'Shared ship-child Empty Object coordinates. Atlas box projection; no stationary world/camera mapping.',
        'landing_camera_source':{'name':'06 LANDING / support review','location':[20,190,4],'target':[10,0,23],'lens':28},
        'proof_cameras':{'side':list(sidecam.location),'quarter':list(quarter.location)},
        'damage_limits':'Added external spall rings and salvage plates only. Pressure hull, bays, engines, pod, mount locations uncut. Not the full arrival-heavy detail density.'}
(BASELINES/'preparation-metrics.json').write_text(json.dumps(report,indent=2))
bpy.ops.wm.save_as_mainfile(filepath=str(BASELINES/'second-wind-surface-proof.blend'))
if '--render' in __import__('sys').argv:
    for name,cam in [('port-side',sidecam),('port-quarter',quarter)]:
        scene.camera=cam; scene.render.filepath=str(OUT/(name+'.png')); bpy.ops.render.render(write_still=True)
