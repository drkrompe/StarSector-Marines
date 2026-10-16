"""Shot04 geometry-owned port realism. Run background Blender; no live session."""
from pathlib import Path
import bpy, math, json, sys, argparse
from mathutils import Vector
H=Path(__file__).resolve().parent
A=H/'assets';A.mkdir(exist_ok=True)
G=H/'guides';G.mkdir(exist_ok=True)
parser=argparse.ArgumentParser();parser.add_argument('--guides',action='store_true');args=parser.parse_args(sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else [])
bpy.ops.wm.open_mainfile(filepath=str(H.parent/'layered-next-scenes/layered-next-scenes.blend'))
s=bpy.context.scene;w=bpy.data.objects['LAYERED | inverse source flight'];r=bpy.data.objects['SECOND WIND | flight root'];c=bpy.data.objects['04 PORT / side window']
for m in s.timeline_markers:m.camera=None
s.frame_set(423,subframe=.75);s.camera=c
original_camera=c.matrix_world.copy();source_camera=w.matrix_world.inverted()@original_camera
# Accepted cabin surface and rigid local projector are reused intact.
with bpy.data.libraries.load(str(H.parent/'managed-land-realism/managed-land-realism.blend'),link=False) as (src,dst):
 dst.objects=['REALISM | ship-local cabin projector'];dst.materials=['REALISM | ship-local registered cabin']
projector=dst.objects[0];s.collection.objects.link(projector)
# Appended projector retains source local basis; substitute this scene's root.
projector.parent=r
cabin=dst.materials[0]
for node in cabin.node_tree.nodes:
 if node.type=='TEX_COORD' and node.object:node.object=projector
for im in bpy.data.images:
 if im.source=='FILE' and im.filepath and im.name.startswith('cabin-realism'):
  im.filepath=str(H.parent/'managed-land-realism/assets'/im.name)
ship=set(r.children_recursive)
for o in ship:
 if o.type=='MESH':o.data=o.data.copy();o.data.materials.clear();o.data.materials.append(cabin)
# An industrial precinct occupies the already accepted flat shed site.
# All added details inherit inverse source motion, preserving the locked camera.
def mat(name,color,cell=None):
 m=bpy.data.materials.new('PORT REALISM | '+name);m.use_nodes=True;n=m.node_tree.nodes;l=m.node_tree.links;p=n.get('Principled BSDF');p.inputs['Base Color'].default_value=(*color,1);p.inputs['Roughness'].default_value=.82
 atlas=A/'industrial-materials-v1.png'
 if cell is not None and atlas.is_file():
  tex=n.new('ShaderNodeTexImage');tex.image=bpy.data.images.load(str(atlas),check_existing=True);tex.extension='REPEAT'
  coord=n.new('ShaderNodeTexCoord');sep=n.new('ShaderNodeSeparateXYZ');l.new(coord.outputs['Generated'],sep.inputs[0]);combine=n.new('ShaderNodeCombineXYZ')
  # Each face has explicit UVs; sample within one atlas cell with 2% guardband.
  mapping=n.new('ShaderNodeVectorMath');mapping.operation='MULTIPLY_ADD';mapping.inputs[1].default_value=(.46,.46,1);mapping.inputs[2].default_value=(cell[0]*.5+.02,cell[1]*.5+.02,0);wrap=n.new('ShaderNodeVectorMath');wrap.operation='FRACTION';l.new(coord.outputs['UV'],wrap.inputs[0]);l.new(wrap.outputs[0],mapping.inputs[0]);l.new(mapping.outputs[0],tex.inputs['Vector']);l.new(tex.outputs['Color'],p.inputs['Base Color'])
 noise=n.new('ShaderNodeTexNoise');noise.inputs['Scale'].default_value=65;noise.inputs['Detail'].default_value=3;bump=n.new('ShaderNodeBump');bump.inputs['Strength'].default_value=.2;bump.inputs['Distance'].default_value=.06;l.new(noise.outputs['Fac'],bump.inputs['Height']);l.new(bump.outputs[0],p.inputs['Normal'])
 return m
wall=mat('offwhite ribbed cladding',(.48,.51,.49),(1,1));roof=mat('weathered grey standing seam',(.31,.35,.35),(0,1));concrete=mat('freight concrete',(.35,.36,.32),(0,0));steel=mat('oxide structural steel',(.36,.15,.065),(1,0));dark=mat('recesses and rubber',(.07,.09,.09));trim=mat('galvanized service hardware',(.3,.34,.34));cargo=mat('faded muted cargo',(.24,.32,.3))
def paint(o,m):
 o.data=o.data.copy();o.data.materials.clear();o.data.materials.append(m)
 for p in o.data.polygons:p.material_index=0
 if True:
  uv=o.data.uv_layers.active or o.data.uv_layers.new(name='face-local surface coordinates')
  for p in o.data.polygons:
   axis=max(range(3),key=lambda i:abs(p.normal[i]));axes=[i for i in range(3) if i!=axis];verts=[o.data.vertices[o.data.loops[i].vertex_index].co.copy() for i in p.loop_indices]
   for v in verts:
    for ax in range(3):v[ax]*=o.scale[ax]
   bounds=[(min(v[a] for v in verts),max(v[a] for v in verts)) for a in axes]
   for li,v in zip(p.loop_indices,verts):uv.data[li].uv=tuple((v[a]-lo)/20 for a,(lo,hi) in zip(axes,bounds))
def box(name,loc,dim,m):
 bpy.ops.mesh.primitive_cube_add(size=1);o=bpy.context.object;o.name='PORT DETAIL | '+name;o.parent=w;o.location=loc;o.scale=dim;bpy.ops.object.transform_apply(location=False,rotation=False,scale=True);paint(o,m);o.pass_index=12
 return o
def beam(name,a,b,width,m):
 mid=(Vector(a)+Vector(b))/2;o=box(name,mid,(width,width,(Vector(b)-Vector(a)).length),m);o.rotation_euler=(Vector(b)-Vector(a)).to_track_quat('Z','Y').to_euler();return o
sheds=[o for o in s.objects if o.name.startswith('Port district shed')]
for i,o in enumerate(sheds):
 paint(o,wall);x,y,z=o.location;ww,dd,hh=o.dimensions;top=z+hh/2;bottom=z-hh/2
 # Recessed loading portals/frames and dock aprons. Physical ribs are restrained.
 for dx in (-ww*.31,0,ww*.31):
  box('shed%02d dark loading portal'%i,(x+dx,y-dd/2-.11,bottom+5.5),(16,.45,11),dark)
  for side in (-1,1):box('shed%02d portal jamb'%i,(x+dx+side*8.3,y-dd/2-.5,bottom+5.6),(.6,.8,11.2),trim)
  box('shed%02d portal canopy'%i,(x+dx,y-dd/2-3,bottom+12),(19,6,.45),roof)
 for dx in range(-40,41,10):box('shed%02d wall rib'%i,(x+dx,y-dd/2-.3,z),(.35,.6,hh),trim)
 for dx in (-ww*.27,ww*.27):
  box('shed%02d roof service plinth'%i,(x+dx,y+dd*.2,top+2),(9,7,4),trim)
  box('shed%02d vent dark top'%i,(x+dx,y+dd*.2,top+4.15),(7.8,5.8,.4),dark)
for o in list(s.objects):
 if o.name.startswith('Port district roof'):paint(o,roof)
 elif o.name.startswith(('Port communications','Port tower')):paint(o,trim)
 elif o.name.startswith('Port district freight road'):o.hide_render=True # Road is painted into owning precinct slab.
# Top owner at z=.4, terrain lies below this flat district. No transparent overlays.
pad=box('single industrial precinct surface',(-970,280,.2),(1550,430,.4),concrete)
for i in range(18):
 x=-1670+i*77;y=130
 # Small cargo stacks and clear road lane preserve believable 12m freight scale.
 for j in range(2):box('cargo%02d_%d'%(i,j),(x,410+j*7,2.5),(12,5,5),cargo if i%3 else steel)
for i,x in enumerate((-1390,-1080,-770,-460)):
 y=460
 # Open gantry structures retain genuine disocclusion.
 for dx in (-30,30):
  beam('gantry upright%d'%i,(x+dx,y,.4),(x+dx,y,46),3,steel)
  beam('gantry brace%d'%i,(x+dx,y-10,.4),(x+dx,y,36),1.4,steel)
 beam('gantry crossbeam%d'%i,(x-36,y,46),(x+36,y,46),4,steel)
 beam('gantry diagonal%d'%i,(x-30,y,28),(x+30,y,44),1.2,trim)
 box('gantry trolley%d'%i,(x-8,y,41),(9,8,6),trim)
 beam('gantry cable%d'%i,(x-8,y,39),(x-8,y,14),.25,dark)
# Reuse raw accepted distant assets at independent camera-aligned depths.
for o in s.objects:
 if o.pass_index in (31,32,33,34,35,36,37,38,39):o.hide_render=True
layers=[]
def card(name,filename,depth_y,alpha=False):
 path=H.parent/'managed-land-realism/assets'/filename
 forward=source_camera.to_3x3()@Vector((0,0,-1));depth=(depth_y-source_camera.translation.y)/forward.y
 hw=depth*c.data.sensor_width/c.data.lens/2*1.65;hh=hw*1080/2560
 verts=[source_camera@Vector((x,y,-depth)) for x,y in ((-hw,-hh),(hw,-hh),(hw,hh),(-hw,hh))];center=sum(verts,Vector())/4
 mesh=bpy.data.meshes.new(name);mesh.from_pydata([v-center for v in verts],[],[(0,1,2,3)]);uv=mesh.uv_layers.new()
 for loop,pair in zip(uv.data,((0,0),(1,0),(1,1),(0,1))):loop.uv=pair
 o=bpy.data.objects.new('PORT LAYER | '+name,mesh);s.collection.objects.link(o);o.parent=w;o.location=center;o.pass_index=41 if alpha else 40
 m=bpy.data.materials.new(name);m.use_nodes=True;n=m.node_tree.nodes;l=m.node_tree.links;n.clear();t=n.new('ShaderNodeTexImage');t.image=bpy.data.images.load(str(path),check_existing=True);t.image.alpha_mode='STRAIGHT';t.extension='CLIP';e=n.new('ShaderNodeEmission');l.new(t.outputs['Color'],e.inputs[0]);out=n.new('ShaderNodeOutputMaterial')
 if alpha:
  trans=n.new('ShaderNodeBsdfTransparent');mix=n.new('ShaderNodeMixShader');l.new(t.outputs['Alpha'],mix.inputs[0]);l.new(trans.outputs[0],mix.inputs[1]);l.new(e.outputs[0],mix.inputs[2]);l.new(mix.outputs[0],out.inputs[0]);m.surface_render_method='BLENDED'
 else:l.new(e.outputs[0],out.inputs[0])
 mesh.materials.append(m);layers.append(dict(object=o.name,asset=str(path),depth=depth,source_y=depth_y))
card('accepted daylight sky','sky-realism-v1.png',35000)
card('accepted hills','hills-realism-v2.png',11000,True)
card('accepted woodland bank','woodland-realism-v1.png',3000,True)
# Reuse accepted source-coordinate land and river materials, with original behavior.
with bpy.data.libraries.load(str(H.parent/'managed-land-realism/managed-land-realism.blend'),link=False) as (src,dst):
 dst.materials=['REALISM | field ground clipped at actual source river nearbank','REALISM | source-world river ripples without projected foliage']
coords=bpy.data.objects.new('PORT REALISM | source world texture coordinates',None);s.collection.objects.link(coords);coords.parent=w
for material in dst.materials:
 for node in material.node_tree.nodes:
  if node.type=='TEX_COORD' and node.object:node.object=coords
for im in bpy.data.images:
 if im.source=='FILE' and im.filepath:
  candidate=H.parent/'managed-land-realism/assets'/Path(im.filepath).name
  if candidate.exists():im.filepath=str(candidate)
land=bpy.data.objects['Jangala | continuous landform'];paint(land,dst.materials[0])
for name in ('Jangala | winding river','Jangala | far estuary'):paint(bpy.data.objects[name],dst.materials[1])
# Accepted layered material logic uses original master RGB with alpha masks.
with bpy.data.libraries.load(str(H.parent/'managed-land-realism/managed-land-realism.blend'),link=False) as (src,dst):dst.materials=['REALISM | distant hills','REALISM | middle woodland']
for name,material in zip(('PORT LAYER | accepted hills','PORT LAYER | accepted woodland bank'),dst.materials):
 if material:
  o=bpy.data.objects[name];o.data.materials.clear();o.data.materials.append(material)
# Ground outside the agricultural texture footprint uses a small clean vegetation
# patch from retained source artwork, tiled on the terrain in source coordinates.
m=bpy.data.materials.new('PORT REALISM | textured native low vegetation');m.use_nodes=True;n=m.node_tree.nodes;l=m.node_tree.links;p=n.get('Principled BSDF');p.inputs['Roughness'].default_value=.94
tex=n.new('ShaderNodeTexImage');tex.image=bpy.data.images.load(str(H.parent/'managed-land-realism/assets/fields-realism-v1.png'),check_existing=True);tex.extension='REPEAT'
co=n.new('ShaderNodeTexCoord');co.object=coords;se=n.new('ShaderNodeSeparateXYZ');l.new(co.outputs['Object'],se.inputs[0]);vec=n.new('ShaderNodeCombineXYZ')
for axis,period,scale,offset in [('X',180,.08,.80),('Y',180,.08,.28)]:
 div=n.new('ShaderNodeMath');div.operation='DIVIDE';div.inputs[1].default_value=period;l.new(se.outputs[axis],div.inputs[0]);wrap=n.new('ShaderNodeMath');wrap.operation='PINGPONG';wrap.inputs[1].default_value=1;l.new(div.outputs[0],wrap.inputs[0]);rem=n.new('ShaderNodeMath');rem.operation='MULTIPLY_ADD';rem.inputs[1].default_value=scale;rem.inputs[2].default_value=offset;l.new(wrap.outputs[0],rem.inputs[0]);l.new(rem.outputs[0],vec.inputs[axis])
l.new(vec.outputs[0],tex.inputs[0]);l.new(tex.outputs[0],p.inputs['Base Color'])
# Keep the original bank masking material farther away; replace only near port faces.
land.data.materials.append(m)
for polygon in land.data.polygons:
 point=(w.matrix_world.inverted()@land.matrix_world)@polygon.center
 if point.y<2300:polygon.material_index=len(land.data.materials)-1

# Freeze original terrain triangles and physically remove ground beneath concrete.
import bmesh
land.data.calc_loop_triangles();mesh=bpy.data.meshes.new('PORT ground exact triangle partition');mesh.from_pydata([v.co[:] for v in land.data.vertices],[],[tuple(t.vertices) for t in land.data.loop_triangles]);
for material in land.data.materials:mesh.materials.append(material)
for polygon,triangle in zip(mesh.polygons,land.data.loop_triangles):polygon.material_index=triangle.material_index
land.data=mesh
bm=bmesh.new();bm.from_mesh(mesh);transform=w.matrix_world.inverted()@land.matrix_world;inverse=transform.inverted()
for v in bm.verts:v.co=transform@v.co
rect=(-1745,-195,65,495)
for pt,no in (((rect[0],0,0),(1,0,0)),((rect[1],0,0),(1,0,0)),((0,rect[2],0),(0,1,0)),((0,rect[3],0),(0,1,0))):bmesh.ops.bisect_plane(bm,geom=list(bm.verts)+list(bm.edges)+list(bm.faces),dist=1e-5,plane_co=Vector(pt),plane_no=Vector(no))
faces=[f for f in bm.faces if rect[0]<f.calc_center_median().x<rect[1] and rect[2]<f.calc_center_median().y<rect[3]];bmesh.ops.delete(bm,geom=faces,context='FACES')
for v in bm.verts:v.co=inverse@v.co
bm.to_mesh(mesh);bm.free();mesh.update();land['port_pad_cutout']=json.dumps(rect)

s.render.engine='BLENDER_EEVEE';s.eevee.taa_render_samples=32;s.eevee.shadow_pool_size='2048';s.render.resolution_x=1920;s.render.resolution_y=1080;s.render.resolution_percentage=100;s.render.fps=12;s.render.image_settings.file_format='PNG';s.render.image_settings.color_mode='RGB';s.render.use_compositing=False
s.view_settings.view_transform='Standard';s.view_settings.look='None';s.frame_start=400;s.frame_end=448
for obj in s.objects:
 if obj.type=='LIGHT':obj.data.use_shadow=obj.data.type=='SUN'
bpy.context.preferences.filepaths.save_version=0
sys.path.insert(0,str(H))
from finish_scene import finish_scene
finish_scene(s)
from finalize_environment import finalize_environment
finalize_environment(s)
bpy.ops.wm.save_as_mainfile(filepath=str(H/'port-approach-realism.blend'),compress=True,relative_remap=False)
manifest=dict(camera=c.name,source_frames=[400,447.5],source_fps=12,output_fps=24,output_frames=96,layers=layers,added_meshes=[o.name for o in s.objects if o.name.startswith('PORT DETAIL')],surface_atlas=str(A/'industrial-materials-v1.png'),atlas_present=(A/'industrial-materials-v1.png').exists(),scope='Geometry-owned close port materials; separate distant plates; retained camera/model; no integration')
(H/'scene-manifest.json').write_text(json.dumps(manifest,indent=2),encoding='utf-8')
if args.guides:
 # Geometry-only masks, no generated scene paint. Emission white/black override.
 for o in s.objects:
  if o.name.startswith('PORT LAYER'):o.hide_render=True
 for frame,label in ((400,'start'),(423.75,'mid'),(447.5,'end')):
  s.frame_set(int(frame),subframe=frame%1);s.render.filepath=str(G/('geometry-'+label+'.png'));bpy.ops.render.render(write_still=True)
 s.frame_set(423,subframe=.75);guide=bpy.data.objects.new('PORT GUIDE | exact midpoint overscan',c.data.copy());s.collection.objects.link(guide);guide.matrix_world=c.matrix_world.copy();guide.data.lens=c.data.lens/1.65;s.camera=guide;s.render.resolution_x=2560;s.render.resolution_y=1440
 s.render.filepath=str(G/'port-overscan-geometry.png');bpy.ops.render.render(write_still=True)
 for group,predicate in [('near-port',lambda o:o.pass_index==12),('terrain',lambda o:o.name in ('Jangala | continuous landform','Jangala | winding river')),('cabin',lambda o:o in ship)]:
  for o in s.objects:
   if o.type!='MESH':continue
   selected=predicate(o);o.hide_render=False if selected else o.hide_render
   m=bpy.data.materials.new('mask');m.use_nodes=True;n=m.node_tree.nodes;n.clear();e=n.new('ShaderNodeEmission');e.inputs[0].default_value=(1,1,1,1) if selected else (0,0,0,1);out=n.new('ShaderNodeOutputMaterial');m.node_tree.links.new(e.outputs[0],out.inputs[0]);paint(o,m)
  s.world.color=(0,0,0);s.render.filepath=str(G/('mask-'+group+'.png'));bpy.ops.render.render(write_still=True)
print('PORT_BUILD_READY',flush=True)
