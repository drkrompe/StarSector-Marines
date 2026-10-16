"""Derived painted forest continuity proof: preserve retained scene, lift Atlas only.

Run in background Blender. Default prepares private blend and 96-point diagnostics;
--preview renders five frames, --render renders all96 genuine half-frame samples.
The retained painted scene/movie and all raw artwork stay unchanged.
"""
import argparse
import json
from pathlib import Path
import sys
import bpy
from mathutils import Vector
from bpy_extras.object_utils import world_to_camera_view

HERE = Path(__file__).resolve().parent
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--preview', action='store_true')
parser.add_argument('--render', action='store_true')
args = parser.parse_args(sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else [])
bpy.ops.wm.open_mainfile(filepath=str(HERE/'jangala-parallax-window.blend'))
scene = bpy.context.scene
camera = bpy.data.objects['02 REVEAL / near glass']
atlas = bpy.data.objects['PARALLAX | Atlas industrial transit']
scene.camera = camera
scene.frame_set(144)
base = atlas.location.copy()
right = (camera.matrix_world.to_3x3() @ Vector((1,0,0))).normalized()
up = (camera.matrix_world.to_3x3() @ Vector((0,1,0))).normalized()

def project(point):
    p = world_to_camera_view(scene,camera,point)
    return [p.x*1920,(1-p.y)*1080]

# Give the rising silhouette enough sky clearance without changing camera/art.
p0 = project(atlas.parent.matrix_world@base)
p1 = project(atlas.parent.matrix_world@(base+up))
lower = up * (50/(p1[1]-p0[1]))
atlas.animation_data_clear()
for t in (120,167.5):
    atlas.location = base+lower+right*(265*(t-144)/12)+Vector((0,0,25*(t-120)/12))
    atlas.keyframe_insert(data_path='location',frame=t)
for layer in atlas.animation_data.action.layers:
    for strip in layer.strips:
        for bag in strip.channelbags:
            for curve in bag.fcurves:
                for key in curve.keyframe_points:
                    key.interpolation='LINEAR'
path=[]
for index in range(96):
    t=120+index*.5
    scene.frame_set(int(t),subframe=t%1)
    bpy.context.view_layer.update()
    corners=[project(atlas.matrix_world@v.co) for v in atlas.data.vertices]
    path.append({'output_frame':index,'source_frame':t,'centre_px':project(atlas.matrix_world.translation),
                 'canvas_bounds_px':[min(p[0] for p in corners),min(p[1] for p in corners),max(p[0] for p in corners),max(p[1] for p in corners)]})
report={'scope':'Earlier PAINTED forest continuity proof; not approved realism replacement',
        'source':'jangala-parallax-window.blend','derived_scene':'jangala-parallax-atlas-lift.blend',
        'mapping':'source120+0.5*i, i=0..95; source12fps, delivery24fps, duration4s',
        'source_independent_velocity_m_s':list(right*265+Vector((0,0,25))),
        'source_motion_components':{'reference_camera_right_m_s':265,'world_vertical_m_s':25},
        'source_velocity_is_provisional_compositing_choice':True,
        'base_lowering_px_at_reference':50,'unmodified':'all cameras, scenery, cloud animation, cabin and raw artwork',
        'screen_delta_px':[path[-1]['centre_px'][i]-path[0]['centre_px'][i] for i in (0,1)],
        'all96_centres_within_frame':all(0<p['centre_px'][0]<1920 and 0<p['centre_px'][1]<1080 for p in path),
        'all96_canvas_bounds_within_frame':all(0<p['canvas_bounds_px'][0]<p['canvas_bounds_px'][2]<1920 and 0<p['canvas_bounds_px'][1]<p['canvas_bounds_px'][3]<1080 for p in path),
        'monotonic_right':all(path[i+1]['centre_px'][0]>path[i]['centre_px'][0] for i in range(95)),
        'monotonic_up':all(path[i+1]['centre_px'][1]<path[i]['centre_px'][1] for i in range(95)),
        'path':path}
review=HERE/'review/atlas-lift';review.mkdir(parents=True,exist_ok=True)
(review/'motion-validation.json').write_text(json.dumps(report,indent=2)+'\n')
scene.render.resolution_x,scene.render.resolution_y,scene.render.resolution_percentage=1920,1080,100
assert scene.render.fps==12
scene.frame_set(144)
bpy.context.preferences.filepaths.save_version=0
bpy.ops.wm.save_as_mainfile(filepath=str(HERE/'jangala-parallax-atlas-lift.blend'),relative_remap=True,compress=True)
print('ATLAS_LIFT_READY',json.dumps({k:v for k,v in report.items() if k!='path'}),flush=True)
if args.preview or args.render:
    output=HERE/'build/atlas-lift-frames';output.mkdir(parents=True,exist_ok=True)
    for index in (range(96) if args.render else (0,24,48,72,95)):
        t=120+index*.5;scene.frame_set(int(t),subframe=t%1)
        scene.render.filepath=str(output/f'frame_{index:04d}.png')
        bpy.ops.render.render(write_still=True)
        print('ATLAS_LIFT_FRAME',index,'SOURCE',t,flush=True)
