"""Rebuild the editable Second Wind geometry guide with Blender's Python.

Run: blender --background --factory-startup --python build_reference.py
Output is source art only. Coordinates are hull-local, +X bow, +Y port, +Z dorsal.
The vanilla engine XY positions and relative widths are retained; Z and hull
depth are proposed art geometry, not canonical game dimensions.

Detail lives in one module per area so separate passes do not collide:
sw_hull (forebody shell, bow facets, bays, armor), sw_aft_deck (centre aft
deck and stern notch), sw_engines (aft engine pods), sw_bridge (dorsal casings, command deck, spine), sw_bridge_interior (the bridge
room cut into the command deck, and its condition dressing), sw_turrets; sw_kit holds the
shared materials and primitives; sw_port_module builds the port grapple.

Review options, after a literal "--":
  --out DIR        write renders, .blend and geometry.json to DIR instead
  --views a,b      render only these cameras (port-quarter, top, port-side,
                   stern, plus the close-ups bow-detail, bridge-detail,
                   turret-detail)
  --samples N      Cycles samples (default 24)
  --res WxH        render size (default 960x440, the guide panels)
  --no-save        skip the .blend and geometry.json
  --cam NAME:PX,PY,PZ:TX,TY,TZ:SCALE   add an ad-hoc ortho review camera
                   (repeatable) and render it; written as NAME.png
  --state S        clean (default), derelict, patched, refit or restored:
                   sw_damage.apply layers that condition state onto the finished clean ship. A damaged
                   state writes to states/<S>/ unless --out is given, and by
                   default also renders starboard-quarter and the damage
                   close-ups; the committed guide is always the clean ship.
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
# The one shared real-world scale (second-wind-pipeline.md, Scale): about
# 66 m overall. Everything metric reads this; nothing hard-codes metres.
METRES_PER_UNIT = 0.65
sys.path.insert(0, str(HERE))
import sw_kit, sw_hull, sw_aft_deck, sw_engines, sw_bridge, sw_bridge_interior, sw_turrets, sw_port_module, sw_rcs, sw_damage
for module in (sw_kit, sw_hull, sw_aft_deck, sw_engines, sw_bridge, sw_bridge_interior, sw_turrets, sw_port_module, sw_rcs, sw_damage):
    importlib.reload(module)

parser = argparse.ArgumentParser()
parser.add_argument('--out')
parser.add_argument('--views')
parser.add_argument('--res', default='960x440')
parser.add_argument('--state', choices=('clean','derelict','patched','refit','restored'), default='clean')
parser.add_argument('--samples', type=int, default=24)
parser.add_argument('--no-save', action='store_true')
parser.add_argument('--cam', action='append', default=[])
args = parser.parse_args(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])
DAMAGE_VIEWS = ('port-quarter,top,port-side,stern,starboard-quarter,'
                'port-flank-detail,port-aft-detail,starboard-aft-detail,stern-detail,bridge-detail')
OUT = Path(args.out or (HERE if args.state == 'clean' else HERE/'states'/args.state)).resolve()
if args.views is None:
    args.views = 'port-quarter,top,port-side,stern' if args.state == 'clean' else DAMAGE_VIEWS
OUT.mkdir(parents=True, exist_ok=True)

bpy.ops.object.select_all(action='SELECT')
bpy.ops.object.delete(use_global=False)
kit = sw_kit.Kit()
kit.mpu = METRES_PER_UNIT

# Order matters: bow facets are cut before the engine pods join the hull,
# and the bay apertures after, so neither cut reaches the wrong part.
sw_hull.build_shell(kit)
sw_aft_deck.build_body(kit)
sw_engines.build_pods(kit)
sw_hull.build_bays(kit)
sw_hull.build_details(kit)
sw_engines.build_details(kit)
sw_aft_deck.build_details(kit)
sw_bridge.build(kit)
sw_bridge_interior.build(kit)
sw_port_module.build(kit)

sw_engines.build_nozzles(kit)
sw_turrets.build(kit)
sw_rcs.build(kit)
# Scars are the last phase: everything above is the clean ship.
damage = sw_damage.apply(kit, args.state) if args.state != 'clean' else None
sw_bridge_interior.dress(kit, args.state)

scene=bpy.context.scene
scene.render.engine='CYCLES'
scene.cycles.samples=args.samples
scene.cycles.use_denoising=True
scene.render.resolution_x,scene.render.resolution_y=map(int,args.res.split('x'))
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
          ('turret-detail',(70,70,60),(30,-5,12),60),
          # Damage review views.
          ('starboard-quarter',(100,-130,100),(5,0,1),163),
          ('port-flank-detail',(75,95,45),(34,24,6),52),
          ('port-aft-detail',(15,85,55),(-22,28,6),52),
          ('starboard-aft-detail',(-80,-75,60),(-24,-26,6),55),
          ('stern-detail',(-120,40,30),(-36,6,4),62),
          # C1's angle (ortho stand-in): starboard stern quarter, 30 deg above the deck.
          ('c1-stern-quarter',(-118,-123,101),(-5,0,1),125),
          ('drive-detail',(-110,-60,35),(-38,-28,1),30)]
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
        'units':'Vanilla hull-space units (1 sprite px = 1 unit); multiply by metres_per_unit for metres',
        'metres_per_unit':METRES_PER_UNIT,
        'revision':'V3 detail: segmented sprite-measured hull, whole engine pods, spine command deck, port grapple; turret-kit guns of the cerberus_Standard loadout',
        'thickness_profile':{'method':'Flat deck plateaus (x0, x1, deck z) over single-chamfer sections; level keel', 'stations':sw_hull.THICKNESS_STATIONS, 'deck_datum':0, 'attachments':'Engine centres unchanged from V2'},
        'engine_source':'starsector-core/data/hulls/warhound.ship engineSlots',
        'engines':[{'location':[x,y,0],'width':w,'exhaust_direction':[-1,0,0]} for x,y,w in sw_engines.ENGINES],
        'port_fitting':{'extent_x':[28.5,48],'extent_y':[24,34.3],'function':'Proposed boarding/cargo grapple sponson and folded arm, read from the sprite; not canonical lore'},
        'cameras':[{'name':name,'position':list(pos),'orthographic_scale':scale} for name,pos,scale in cameras],
        'turret_source':'starsector-core/data/variants/cerberus/cerberus_Standard.variant on warhound.ship weaponSlots; guns from ../turret-kit',
        'turrets':sw_turrets.records(),
        'bay_state':{'port':'open, empty cradle','starboard':'sealed, awaits repair'},
        'mesh_authority':'All views render the same objects; generated surface art is subordinate to this geometry.'
    }
    # Effect anchors, found by name (second-wind-pipeline.md): local +X is the
    # exhaust direction. 'running'/'working' are this state's condition.
    def anchor(ob):
        p=ob.matrix_world.translation
        e=(ob.matrix_world.to_3x3()@Vector((1,0,0))).normalized()
        return {'name':ob.name,'position':[round(c,4) for c in p],
                'position_m':[round(c*METRES_PER_UNIT,4) for c in p],
                'exhaust_dir':[round(c,5) for c in e]}
    manifest['engine_anchors']=[dict(anchor(ob),engine=int(ob['engine']),radius=round(float(ob['radius']),4),
                                     radius_m=round(float(ob['radius'])*METRES_PER_UNIT,4),running=bool(ob.get('running',True)),
                                     **{k:(round(float(v),4) if isinstance(v,float) else bool(v) if isinstance(v,bool) else [round(float(c),4) for c in v])
                                        for k,v in ob.items() if k.startswith('mouth_')})
                                for ob in sorted(bpy.data.objects,key=lambda o:o.name)
                                if ob.name.startswith('Engine ') and ob.name.endswith(' exhaust anchor')]
    manifest['rcs_anchors']=[dict(anchor(ob),cluster=ob['cluster'],axis=ob['axis'],
                                  radius_m=round(float(ob['radius_m']),4),working=bool(ob.get('working',True)))
                             for ob in sorted(bpy.data.objects,key=lambda o:o.name)
                             if ob.name.startswith('RCS ') and ob.name.endswith(' anchor')]
    manifest['spark_anchors']=[dict(((k if k!='exhaust_dir' else 'spray_dir'),v) for k,v in anchor(ob).items())|dict(scar=ob['scar'],sparking=bool(ob.get('sparking',False)),
                                    **({'radius_m':round(float(ob['radius_m']),4)} if 'radius_m' in ob else {}))
                               for ob in sorted(bpy.data.objects,key=lambda o:o.name)
                               if ob.name.startswith('Scar ') and ' spark' in ob.name and ob.name.endswith(' anchor')]
    if damage:
        manifest['status']='Proposed damage-state review geometry; the committed guide remains the clean ship'
        manifest['damage']=damage
    (OUT/'geometry.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
