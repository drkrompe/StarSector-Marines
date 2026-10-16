"""Read-only background validation of the saved next-scene blocking.

Reads scene-manifest.json, opens source and derived scenes, writes review evidence.
Never renders, rebuilds or saves a Blender scene. Bounds checks are conservative;
visual framing, painted appearance and pixel-perfect collision remain review work.
"""
import hashlib
import json
from pathlib import Path
import struct

import bpy
from mathutils import Matrix, Vector

HERE = Path(__file__).resolve().parent
TOL = 0.025


def frame(scene, time):
    whole = int(time)
    scene.frame_set(whole, subframe=time-whole)
    bpy.context.view_layer.update()


def geometry(obj):
    digest = hashlib.sha256()
    for v in obj.data.vertices:
        digest.update(struct.pack('<3f', *v.co))
    for face in obj.data.polygons:
        digest.update(struct.pack('<I', len(face.vertices)))
        for index in face.vertices:
            digest.update(struct.pack('<I', index))
    return {'sha256': digest.hexdigest(), 'vertices': len(obj.data.vertices),
            'polygons': len(obj.data.polygons)}


def matrix_delta(a, b):
    return max(abs(a[i][j]-b[i][j]) for i in range(4) for j in range(4))


def box(obj):
    graph = bpy.context.evaluated_depsgraph_get()
    evaluated = obj.evaluated_get(graph)
    mesh = evaluated.to_mesh()
    try:
        points = [evaluated.matrix_world @ v.co for v in mesh.vertices]
        return [(min(v[i] for v in points), max(v[i] for v in points)) for i in range(3)]
    finally:
        evaluated.to_mesh_clear()


def overlap(a, b, axes=(0, 1, 2)):
    return all(min(a[i][1], b[i][1])-max(a[i][0], b[i][0]) > TOL for i in axes)


def main():
    manifest = json.loads((HERE/'scene-manifest.json').read_text(encoding='utf-8-sig'))
    source_path = (HERE/manifest['source_blend']).resolve()
    final_path = (HERE/manifest['scene']).resolve()
    root_name = manifest.get('root', 'SECOND WIND | flight root')
    scenery_name = manifest.get('scenery_root', 'LAYERED | inverse source flight')
    shots = manifest['shots']
    mapped = []
    for shot in shots:
        step = shot.get('source_step', .5)
        for index in range(shot['output_start'], shot['output_end']+1):
            mapped.append((index, shot['source_start']+(index-shot['output_start'])*step, shot['camera']))
    errors = []
    def require(ok, message):
        if not ok:
            errors.append(message)
    require(manifest['source_fps'] == 12 and manifest['output_fps'] == 24, 'Expected source12/output24 FPS')
    require(len(mapped) == 576 and [i for i,_,_ in mapped] == list(range(1,577)), 'Shot map must cover576 contiguous output frames')
    require(all(abs(shot.get('source_step', .5)-.5)<1e-9 for shot in shots), 'Expected half-source-frame sampling')
    sample_times = sorted(set([s['source_start'] for s in shots] +
                             [t for _,t,_ in mapped[::24]] +
                             [624,648,649,720,744,750,751,768,774,778.5]))
    bpy.ops.wm.open_mainfile(filepath=str(source_path))
    scene = bpy.context.scene
    require(scene.render.fps/scene.render.fps_base == 12, 'Source FPS differs from manifest')
    source_names = {o.name for o in scene.objects}
    root = bpy.data.objects[root_name]
    ship_names = sorted(o.name for o in root.children_recursive if o.type=='MESH')
    hashes = {name:geometry(bpy.data.objects[name]) for name in ship_names}
    hardware_names = sorted(o.name for o in scene.objects if o.type=='MESH' and
                            o.name.startswith(('YARD_contact_pad_', 'YARD_retaining_wedge_',
                                               'PROPOSAL keel shoe', 'PROPOSAL ventral lift collar')))
    camera_names = sorted({s['camera'] for s in shots})
    engine_names = sorted(name for name in ship_names if name.startswith(tuple(f'Engine {i} ' for i in range(1,5))))
    for i in range(1,5):
        require(any(n.startswith(f'Engine {i} ') for n in engine_names), f'Missing aft engine group{i}')
    tracked = sorted(set(ship_names+hardware_names+camera_names))
    source = {}
    for time in sorted(set(sample_times+[t for _,t,_ in mapped])):
        frame(scene,time)
        source[time] = {'position':root.matrix_world.translation.copy(),
                        'rotation':root.rotation_euler.copy(), 'scale':root.scale.copy()}
        if time in sample_times:
            source[time]['matrices'] = {n:bpy.data.objects[n].matrix_world.copy() for n in tracked}
    bpy.ops.wm.open_mainfile(filepath=str(final_path))
    scene=bpy.context.scene
    root=bpy.data.objects[root_name]
    scenery=bpy.data.objects[scenery_name]
    final_ship=sorted(o.name for o in root.children_recursive if o.type=='MESH')
    require(final_ship==ship_names,'Ship mesh names/count changed')
    changed=[n for n in ship_names if bpy.data.objects.get(n) is None or geometry(bpy.data.objects[n])!=hashes[n]]
    require(not changed, 'Ship local mesh geometry changed: '+', '.join(changed))
    maxima={'root_location_m':0.0,'inverse_flight_error_m':0.0,'attitude_scale_delta':0.0,'world_relative_matrix_delta':0.0}
    for _,time,_ in mapped:
        frame(scene,time)
        old=source[time]
        maxima['root_location_m']=max(maxima['root_location_m'],root.location.length)
        maxima['inverse_flight_error_m']=max(maxima['inverse_flight_error_m'],(scenery.location+old['position']).length)
        maxima['attitude_scale_delta']=max(maxima['attitude_scale_delta'],*(abs(root.rotation_euler[i]-old['rotation'][i]) for i in range(3)),*(abs(root.scale[i]-old['scale'][i]) for i in range(3)))
        up=root.matrix_world.to_quaternion() @ Vector((0,0,1))
        require((up-Vector((0,0,1))).length<1e-5, f'Source{time}: ship not level')
    for time in sample_times:
        frame(scene,time)
        old=source[time]
        shift=Matrix.Translation(-old['position'])
        for name in tracked:
            ob=bpy.data.objects.get(name)
            if ob is None:
                errors.append('Missing original tracked object: '+name)
                continue
            delta=matrix_delta(ob.matrix_world,shift @ old['matrices'][name])
            maxima['world_relative_matrix_delta']=max(maxima['world_relative_matrix_delta'],delta)
    require(maxima['root_location_m']<1e-6,'Ship root not at origin')
    require(maxima['inverse_flight_error_m']<.002,'Inverse flight differs by over2mm')
    require(maxima['attitude_scale_delta']<1e-6,'Root attitude/scale changed')
    require(maxima['world_relative_matrix_delta']<.005,'Original hardware/engine/camera relative transforms differ by over5mm')
    shoes=sorted([o for o in scene.objects if o.name.startswith('PROPOSAL keel shoe')],key=lambda o:o.name)
    pads=sorted([o for o in scene.objects if o.name.startswith('YARD_contact_pad_')],key=lambda o:o.name)
    clamps=sorted([o for o in scene.objects if o.name.startswith('YARD_retaining_wedge_')],key=lambda o:o.name)
    lifts=[o for o in scene.objects if o.name.startswith('PROPOSAL ventral lift collar')]
    for label,group in (('shoes',shoes),('pads',pads),('clamps',clamps),('lift collars',lifts)):
        require(len(group)==4,f'Expected four {label}')
    landing=[]
    for time in (648,649,720,744,750,751,768,774,778.5):
        frame(scene,time)
        row={'source_frame':time,'contacts':[],'clamps':[]}
        cached={o.name:box(o) for o in shoes+pads+clamps+lifts}
        for shoe in shoes:
            sb=cached[shoe.name]
            pad=min(pads,key=lambda p:sum(((cached[p.name][i][0]+cached[p.name][i][1])-(sb[i][0]+sb[i][1]))**2 for i in (0,1)))
            pb=cached[pad.name]
            gap=sb[2][0]-pb[2][1]
            require(gap>=-TOL,f'Frame{time}: shoe penetrates support')
            if time>=744:
                require(abs(gap)<TOL,f'Frame{time}: missing shoe/pad contact')
                require(all(sb[i][0]>=pb[i][0]-TOL and sb[i][1]<=pb[i][1]+TOL for i in (0,1)),f'Frame{time}: shoe outside support')
            else:
                require(gap>0,f'Frame{time}: premature contact')
            row['contacts'].append({'shoe':shoe.name,'pad':pad.name,'gap_m':gap})
        for clamp in clamps:
            cb=cached[clamp.name]
            shoe=min(shoes,key=lambda o:sum(((cached[o.name][i][0]+cached[o.name][i][1])-(cb[i][0]+cb[i][1]))**2 for i in (0,1)))
            sb=cached[shoe.name]
            clearance=max(sb[1][0]-cb[1][1],cb[1][0]-sb[1][1])
            require(clearance>=-TOL,f'Frame{time}: clamp penetrates shoe')
            if time<=750:
                require(clearance>.5,f'Frame{time}: clamp engages before supported hold')
            if time>=774:
                require(abs(clearance)<TOL,f'Frame{time}: clamp not against shoe')
            row['clamps'].append({'clamp':clamp.name,'shoe':shoe.name,'lateral_clearance_m':clearance})
        for support in shoes+pads:
            for lift in lifts:
                require(not overlap(cached[support.name],cached[lift.name],(0,1)),f'Frame{time}: support/lift footprint overlap')
        landing.append(row)
    # New render-visible geometry may not enter the original low landing volume.
    # Source geometry already owns pads, rail and apron; only additions are tested.
    lane=((-700,150),(-80,80),(.10,65))
    lane_checks=[]
    for time in (624,744,778.5):
        frame(scene,time)
        offset=source[time]['position']
        for ob in scene.objects:
            if ob.type!='MESH' or ob.name in source_names or ob.hide_render:
                continue
            bb=box(ob)
            restored=[(lo+offset[i],hi+offset[i]) for i,(lo,hi) in enumerate(bb)]
            intersects=overlap(restored,lane)
            lane_checks.append({'source_frame':time,'object':ob.name,'source_world_bounds':restored,'lane_overlap':intersects})
            require(not intersects,f'Frame{time}: new geometry bounds enter landing volume: {ob.name}')
    report={'passed':not errors,'errors':errors,'scene':manifest['scene'],'source':manifest['source_blend'],
            'output_samples':len(mapped),'source_times':[mapped[0][1],mapped[-1][1]],
            'ship_mesh_count':len(ship_names),'changed_ship_meshes':changed,'engine_objects':engine_names,
            'maxima':maxima,'transform_sample_frames':sample_times,'landing':landing,'new_geometry_lane_checks':lane_checks,
            'limits':'Mesh hashes and sampled transforms/bounds establish retained blocking geometry only. New-geometry AABB lane checks are conservative, not triangle collision or exhaust dynamics. No rendering, visual acceptance, damage skin or final-art acceptance is implied.'}
    out=HERE/'review/scene-validation.json'
    out.parent.mkdir(parents=True,exist_ok=True)
    out.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    print('LAYERED_SCENE_VALIDATION', 'PASS' if not errors else 'FAIL', str(out),flush=True)
    if errors:
        raise RuntimeError('; '.join(errors))


if __name__=='__main__':
    main()
