"""One heavy sag and delayed lift recovery, evaluated in the retained source clock.

Call apply_unstable_arrival(scene) after loading landing-refinement.blend.
Run this file in background Blender for the finite construction report. No blend
is saved, no camera is animated, and no fluid simulation is claimed.
"""
from pathlib import Path
import hashlib
import json
import math
import bpy
from mathutils import Vector

HERE=Path(__file__).resolve().parent
SOURCE=HERE.parent/'landing-refinement/landing-refinement.blend'
ROOT='SECOND WIND | flight root'
KNOTS=((0,580),(2,624),(2.5,648),(6.5,744),(8,774))

def _frame(scene,f):scene.frame_set(int(f),subframe=f%1)
def _smooth(u):return max(0,min(1,u))**2*(3-2*max(0,min(1,u)))
def _curve(knots,x):
    """Monotone cubic Hermite: varying descent speed without extra bounces."""
    if x<=knots[0][0]:return knots[0][1]
    if x>=knots[-1][0]:return knots[-1][1]
    slopes=[(b[1]-a[1])/(b[0]-a[0]) for a,b in zip(knots,knots[1:])]
    tangents=[slopes[0]]
    for a,b in zip(slopes,slopes[1:]):
        tangents.append(0 if a*b<=0 else 2*a*b/(a+b))
    tangents.append(0)
    for i,(a,b) in enumerate(zip(knots,knots[1:])):
        if x<=b[0]:
            h=b[0]-a[0];u=(x-a[0])/h
            return (2*u**3-3*u*u+1)*a[1]+(u**3-2*u*u+u)*h*tangents[i]+(-2*u**3+3*u*u)*b[1]+(u**3-u*u)*h*tangents[i+1]

def _angle(f,onset,peak,end,amplitude):
    if f<=onset or f>=end:return 0
    return amplitude*(_smooth((f-onset)/(peak-onset)) if f<=peak else 1-_smooth((f-peak)/(end-peak)))
def _fcurves(action):
    return [c for layer in action.layers for strip in layer.strips for bag in strip.channelbags for c in bag.fcurves]

def apply_unstable_arrival(scene):
    """Apply ship root motion and four independent bounded exhaust responses.

    The heavy hull has one starboard-low/bow-low departure from level, then one
    long correction. Its COM descends monotonically and all rotation is gone
    before final shoe contact. Original support and clamp actions are retained.
    """
    root=scene.objects[ROOT]
    if '_wreck_original_start' not in root:
        _frame(scene,580);root['_wreck_original_start']=list(root.location)
        _frame(scene,744);root['_wreck_original_final']=list(root.location)
        root['_wreck_original_rotation']=list(root.rotation_euler)
    first=list(root['_wreck_original_start']);final=list(root['_wreck_original_final']);base_rot=list(root['_wreck_original_rotation'])
    # Copy action before changing any original data block. Dense quarter-frame
    # keys keep the analytic motion reproducible under arbitrary half/subframes.
    if root.animation_data and root.animation_data.action:
        root.animation_data.action=root.animation_data.action.copy()
        root.animation_data.action.name='WRECK ARRIVAL | single sag and recovery'
        for c in _fcurves(root.animation_data.action):
            if c.data_path in ('location','rotation_euler'):
                for k in list(c.keyframe_points):
                    if 580<=k.co.x<=774:c.keyframe_points.remove(k,fast=True)
    zknots=((580,first[2]),(608,33.4),(624,32),(648,31.5),(672,27.2),(690,25.6),(708,24.4),(726,22.6),(744,final[2]),(774,final[2]))
    # Delayed braking: retain a little extra travel through the midpoint, then
    # reduce to a crawl. Both position and velocity reach zero before lowering.
    xknots=((580,first[0]),(594,first[0]*.71),(606,first[0]*.36),(617,first[0]*.07),(624,final[0]),(774,final[0]))
    for tick in range(777):
        f=580+tick*.25
        root.location=(_curve(xknots,f),final[1],_curve(zknots,f))
        roll=math.radians(_angle(f,604,664,730,3.0))
        pitch=math.radians(_angle(f,612,670,732,1.25))
        root.rotation_euler=(base_rot[0]+roll,base_rot[1]+pitch,base_rot[2])
        root.keyframe_insert('location',frame=f);root.keyframe_insert('rotation_euler',frame=f)
    for c in _fcurves(root.animation_data.action):
        if c.data_path in ('location','rotation_euler'):
            for k in c.keyframe_points:
                if 580<=k.co.x<=774:k.interpolation='LINEAR'
    _frame(scene,744)
    controls=sorted((o for o in scene.objects if o.name.startswith('LIFT VECTOR | placeholder')),key=lambda o:o.name)
    mouths=[o for o in root.children_recursive if o.name.startswith('PROPOSAL lift mouth')]
    if len(controls)!=4 or len(mouths)!=4:raise ValueError('Expected four retained lift mouths/controls')
    response=[]
    for i,control in enumerate(controls):
        anchor=scene.objects['LANDING FX | nozzle anchor %02d'%i]
        mouth=min(mouths,key=lambda o:(o.matrix_world.translation-control.matrix_world.translation).length)
        point=root.matrix_world.inverted()@mouth.matrix_world.translation
        for c in list(anchor.constraints):anchor.constraints.remove(c)
        anchor.parent=root;anchor.matrix_parent_inverse.identity();anchor.location=point
        anchor.rotation_euler=(0,0,0);anchor.scale=tuple(1/v for v in root.scale)
        # Existing container dimensions are metres; their zero now sits at the
        # actual mouth centre and their -Z axes travel with the banking hull.
        scene.objects['LANDING FX | core %02d'%i].location=(0,0,-1.95)
        scene.objects['LANDING FX | mixing %02d'%i].location=(0,0,-2.75)
        ground=scene.objects['LANDING FX | ground anchor %02d'%i]
        for c in ground.constraints:
            if c.type=='COPY_LOCATION':c.target=anchor
        starboard=point.y<0;forward=point.x>0
        lag=.68 if starboard else .94
        peak=(1.55 if starboard else 1.04)+(.12 if forward else 0)
        delay=0 if forward else 3
        keys=((580,.84),(624,.92),(648,lag),(664,lag*.80),(678+delay,peak),(690+delay,1.13 if starboard else .98),(708,.90),(732,.76),(744,.48),(750,.15),(758,0),(774,0))
        for label in ('core','mixing','ground wash'):
            ob=scene.objects['LANDING FX | %s %02d'%(label,i)]
            old=ob.data.materials[0]
            desired='WRECK ARRIVAL | %s nozzle %02d'%(label,i)
            mat=old if old.name==desired else old.copy();mat.name=desired
            ob.data.materials[0]=mat
            mat.node_tree.animation_data_clear()
            socket=mat.node_tree.nodes['Thrust envelope'].outputs[0]
            for f,v in keys:
                socket.default_value=v;socket.keyframe_insert('default_value',frame=f)
            for c in _fcurves(mat.node_tree.animation_data.action):
                for k in c.keyframe_points:k.interpolation='BEZIER';k.handle_left_type='AUTO_CLAMPED';k.handle_right_type='AUTO_CLAMPED'
        response.append(dict(nozzle=i,mouth=mouth.name,root_local_mouth=list(point),starboard=starboard,forward=forward,envelope_keys=keys))
    scene['wreck_arrival_motion']='One slow 3deg starboard-low bank / 1.25deg bow-low sag; delayed asymmetric lift; level by732, original contact744.'
    _frame(scene,744)
    return dict(root=ROOT,source_clock_knots=KNOTS,rotation_degrees={'roll_peak':3,'roll_peak_frame':664,'pitch_peak':1.25,'pitch_peak_frame':670,'level_frame':732},horizontal_knots=xknots,altitude_knots=zknots,nozzles=response,contact_frame=744,exhaust_off_frame=758)

def _bounds(ob):
    ev=ob.evaluated_get(bpy.context.evaluated_depsgraph_get());mesh=ev.to_mesh()
    pts=[ev.matrix_world@v.co for v in mesh.vertices];ev.to_mesh_clear()
    return [(min(p[i] for p in pts),max(p[i] for p in pts)) for i in range(3)]
def _mapped(t):
    for (a,x),(b,y) in zip(KNOTS,KNOTS[1:]):
        if t<=b:return x+(y-x)*(t-a)/(b-a)
    return 774

def main():
    bpy.ops.wm.open_mainfile(filepath=str(SOURCE));scene=bpy.context.scene
    _frame(scene,744);original=scene.objects[ROOT].matrix_world.copy();camera=scene.camera.matrix_world.copy()
    design=apply_unstable_arrival(scene)
    shoes=[o for o in scene.objects if o.name.startswith('PROPOSAL keel shoe')]
    pads=[o for o in scene.objects if o.name.startswith('YARD_contact_pad_')]
    samples=[];minimum_gap=1e9;minimum_precontact=1e9;contact_ok=True;anchor_error=0;monotone=True;previous_z=None;settled_error=0
    for index in range(192):
        f=_mapped(index/24);_frame(scene,f);root=scene.objects[ROOT]
        p=root.matrix_world.translation
        if previous_z is not None and p.z>previous_z+1e-5:monotone=False
        previous_z=p.z
        contacts=[]
        for shoe in shoes:
            sb=_bounds(shoe);pad=min(pads,key=lambda o:sum((o.matrix_world.translation[j]-(sb[j][0]+sb[j][1])/2)**2 for j in (0,1)))
            pb=_bounds(pad);gap=sb[2][0]-pb[2][1]
            xy=all(sb[j][0]>=pb[j][0]-.025 and sb[j][1]<=pb[j][1]+.025 for j in (0,1))
            # Height clearance is deliberately conservative even while outside
            # the pad footprint on approach.
            minimum_gap=min(minimum_gap,gap)
            if f<744:minimum_precontact=min(minimum_precontact,gap)
            if f>=744:contact_ok=contact_ok and abs(gap)<.025 and xy
            contacts.append(dict(shoe=shoe.name,gap_m=gap,footprint_inside=xy))
        for item in design['nozzles']:
            anchor=scene.objects['LANDING FX | nozzle anchor %02d'%item['nozzle']]
            mouth=scene.objects[item['mouth']]
            anchor_error=max(anchor_error,(anchor.matrix_world.translation-mouth.matrix_world.translation).length)
        if f>=744:settled_error=max(settled_error,max(abs(root.matrix_world[r][c]-original[r][c]) for r in range(4) for c in range(4)))
        if index%12==0 or index==191:samples.append(dict(output_frame=index,source_frame=f,position=list(p),rotation_degrees=[math.degrees(v) for v in root.rotation_euler],contacts=contacts))
    _frame(scene,744);contact_exact=max(abs(scene.objects[ROOT].matrix_world[r][c]-original[r][c]) for r in range(4) for c in range(4))
    _frame(scene,774)
    envelopes=[scene.objects['LANDING FX | core %02d'%i].data.materials[0].node_tree.nodes['Thrust envelope'].outputs[0].default_value for i in range(4)]
    checks=dict(four_contact_pairs=len(shoes)==len(pads)==4,monotone_COM_descent=monotone,no_sampled_shoe_pad_penetration=minimum_gap>=-.025,settled_contact_and_footprints=contact_ok,exact_original_contact_pose=contact_exact<1e-6,no_post_contact_slide=settled_error<1e-6,nozzle_attachment_all_192_poses=anchor_error<1e-5,camera_unchanged=scene.camera.matrix_world==camera,exhaust_zero_after_settle=all(v==0 for v in envelopes))
    report=dict(passed=all(checks.values()),checks=checks,source_blend_sha256=hashlib.sha256(SOURCE.read_bytes()).hexdigest(),module_sha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),design=design,sampled_output_poses=192,minimum_shoe_pad_vertical_gap_m=minimum_gap,minimum_precontact_shoe_pad_vertical_gap_m=minimum_precontact,maximum_nozzle_attachment_error_m=anchor_error,maximum_settled_matrix_error=settled_error,trajectory_samples=samples,limits=['Authored motion/visual force response; not a rigid-body, atmospheric-flight or structural-load simulation.','Finite shoe/pad geometry and nozzle attachment checks; full swept-hull/environment collision and rendered motion review remain integration work.','One visible bank and correction, no camera shake or random noise. Source-clock retime retained.'])
    (HERE/'motion-contract.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8',newline='\n')
    print(json.dumps(dict(passed=report['passed'],checks=checks,minimum_gap=minimum_gap,anchor_error=anchor_error)))
    if not report['passed']:raise AssertionError('Motion contract failed')
if __name__=='__main__':main()
