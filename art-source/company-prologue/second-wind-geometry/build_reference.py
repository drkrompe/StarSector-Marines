"""Rebuild the editable Second Wind geometry guide with Blender's Python.

Run: blender --background --factory-startup --python build_reference.py
Output is source art only. Coordinates are hull-local, +X bow, +Y port, +Z dorsal.
The vanilla engine XY positions and relative widths are retained; Z and hull
depth are proposed art geometry, not canonical game dimensions.

Detail lives in one module per area so separate passes do not collide:
sw_hull (forebody shell, bow facets, bays, armor), sw_engines (aft engine pods
and stern), sw_bridge (dorsal casings, command deck, spine), sw_turrets; sw_kit holds the
shared materials and primitives. Engines and the port pod are locked here.

Review options, after a literal "--":
  --out DIR        write renders, .blend and geometry.json to DIR instead
  --views a,b      render only these cameras (port-quarter, top, port-side,
                   stern, plus the close-ups bow-detail, bridge-detail,
                   turret-detail)
  --samples N      Cycles samples (default 24)
  --no-save        skip the .blend and geometry.json
  --cam NAME:PX,PY,PZ:TX,TY,TZ:SCALE   add an ad-hoc ortho review camera
                   (repeatable) and render it; written as NAME.png
Close-up cameras are rendered only when named, never by the default run.
"""
from pathlib import Path
import argparse
import importlib
import json
import math
import sys
import bpy
from mathutils import Vector

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import sw_kit, sw_hull, sw_engines, sw_bridge, sw_turrets, sw_port_module
for module in (sw_kit, sw_hull, sw_engines, sw_bridge, sw_turrets, sw_port_module):
    importlib.reload(module)

parser = argparse.ArgumentParser()
parser.add_argument('--out', default=str(HERE))
parser.add_argument('--views', default='port-quarter,top,port-side,stern')
parser.add_argument('--samples', type=int, default=24)
parser.add_argument('--no-save', action='store_true')
parser.add_argument('--cam', action='append', default=[])
args = parser.parse_args(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])
OUT = Path(args.out).resolve()
OUT.mkdir(parents=True, exist_ok=True)

bpy.ops.object.select_all(action='SELECT')
bpy.ops.object.delete(use_global=False)
kit = sw_kit.Kit()

# Order matters: bow facets are cut before the engine pods join the hull,
# and the bay apertures after, so neither cut reaches the wrong part.
sw_hull.build_shell(kit)
sw_engines.build_pods(kit)
sw_hull.build_bays(kit)
sw_hull.build_details(kit)
sw_engines.build_details(kit)
sw_bridge.build(kit)
sw_port_module.build(kit)

sw_engines.build_nozzles(kit)
sw_turrets.build(kit)

scene=bpy.context.scene
scene.render.engine='CYCLES'
scene.cycles.samples=args.samples
scene.cycles.use_denoising=True
scene.render.resolution_x=960
scene.render.resolution_y=440
scene.render.resolution_percentage=100
scene.render.image_settings.file_format='PNG'
scene.world.color=(.7,.7,.7)
scene.world.use_nodes=True
scene.world.node_tree.nodes['Background'].inputs[0].default_value=(.74,.76,.78,1)
scene.world.node_tree.nodes['Background'].inputs[1].default_value=.65
scene.view_settings.view_transform='AgX'
scene.render.film_transparent=False
for name,loc,energy,size in [('Key',(60,70,120),240000,90),('Fill',(-40,-60,70),160000,80)]:
    bpy.ops.object.light_add(type='AREA',location=loc)
    obj=bpy.context.object
    obj.name=name
    obj.data.energy=energy
    obj.data.shape='DISK'
    obj.data.size=size
    obj.rotation_euler=(-obj.location).to_track_quat('-Z','Y').to_euler()

# Each camera observes the SAME objects. No per-view geometry transforms.
cameras=[('port-quarter',(100,130,100),163),('top',(0,0,180),205),
         ('port-side',(0,180,0),132),('stern',(-180,0,0),130)]
# Review close-ups: (name, position, target, orthographic scale).
closeups=[('bow-detail',(110,60,55),(40,0,8),70),
          ('bridge-detail',(60,60,50),(12,0,14),55),
          ('turret-detail',(70,70,60),(30,-5,12),60)]
wanted=[v for v in args.views.split(',') if v]
for spec in args.cam:
    name,pos,target,scale=spec.split(':')
    closeups.append((name,tuple(map(float,pos.split(','))),tuple(map(float,target.split(','))),float(scale)))
    wanted.append(name)
for name,pos,scale in cameras:
    bpy.ops.object.camera_add(location=pos)
    camera=bpy.context.object
    camera.name='Camera '+name
    target=Vector((5,0,1))
    camera.rotation_euler=(target-camera.location).to_track_quat('-Z','Y').to_euler()
    if name=='top':
        # Bow points left, as in the port-side view.
        camera.rotation_euler=(0,0,math.pi)
        camera.location=(5,0,180)
    camera.data.type='ORTHO'
    camera.data.ortho_scale=scale
for name,pos,target,scale in closeups:
    bpy.ops.object.camera_add(location=pos)
    camera=bpy.context.object
    camera.name='Camera '+name
    camera.rotation_euler=(Vector(target)-camera.location).to_track_quat('-Z','Y').to_euler()
    camera.data.type='ORTHO'
    camera.data.ortho_scale=scale
for name in wanted:
    scene.camera=bpy.data.objects['Camera '+name]
    scene.render.filepath=str(OUT/(name+'.png'))
    bpy.ops.render.render(write_still=True)

if not args.no_save:
    scene.camera=bpy.data.objects['Camera port-quarter']
    bpy.context.preferences.filepaths.save_version=0
    bpy.ops.wm.save_as_mainfile(filepath=str(OUT/'second-wind-geometry.blend'))
    manifest={
        'status':'Proposed geometry reference; no scene art or gameplay changes',
        'axes':{'+X':'bow','+Y':'port','+Z':'dorsal'},
        'units':'Vanilla hull-space units; no canonical metre scale asserted',
        'revision':'V3 detail: sprite-measured faceted bow, separate engine nacelles, spine command deck, naval turrets',
        'thickness_profile':{'method':'Upper Z uses plateaus and linear chamfer transitions between stations; lower hull has uniform 1.25 depth multiplier for level keel', 'stations':sw_hull.THICKNESS_STATIONS, 'deck_datum':0, 'attachments':'Engine and port-pod coordinates unchanged from V2'},
        'engine_source':'starsector-core/data/hulls/warhound.ship engineSlots',
        'engines':[{'location':[x,y,0],'width':w,'exhaust_direction':[-1,0,0]} for x,y,w in sw_engines.ENGINES],
        'pod':{'center':[37,33,1],'dimensions':[19,8,6],'long_axis':'+X','function':'Proposed service/airlock pod; not canonical lore'},
        'cameras':[{'name':name,'position':list(pos),'orthographic_scale':scale} for name,pos,scale in cameras],
        'bay_state':{'port':'open, empty cradle','starboard':'sealed, awaits repair'},
        'mesh_authority':'All views render the same objects; generated surface art is subordinate to this geometry.'
    }
    (OUT/'geometry.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
