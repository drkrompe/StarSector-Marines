"""Opt-in, background-Blender fault injection; never writes a .blend.

Run with Blender --background --factory-startup --python this_file.py.
This checks the geometric instrument, not cinematic appearance or playback.
All faults affect temporary datablocks and are restored before the next case.
"""
from pathlib import Path
import hashlib
import json
import sys
import time

import bmesh
import bpy
from mathutils import Vector
from mathutils.bvhtree import BVHTree

H=Path(__file__).resolve().parent
sys.path.insert(0,str(H))
from verify_ground import verify_ground
from ground_surfaces import repair_ground

def compact(result):
    return dict(passed=result['passed'],failed_checks=[name for name,passed in result['checks'].items() if not passed],
                max_boundary_height_error=result['max_boundary_height_error'],ownership_failure_samples=len(result['ownership_failures']),
                expected_area=result['expected_area'],measured_area=result['top_area'],grade_return_samples=result['grade_return_samples'])

def main():
    started=time.perf_counter()
    path=H/'port-refinement.blend'
    scene_sha256=hashlib.sha256(path.read_bytes()).hexdigest()
    bpy.ops.wm.open_mainfile(filepath=str(path))
    scene=bpy.context.scene
    scene.frame_set(423,subframe=.75)
    bpy.context.view_layer.update()
    top=bpy.data.objects['PORT GROUND | continuous precinct and roads']
    land=bpy.data.objects['Jangala | continuous landform']
    w=bpy.data.objects['LAYERED | inverse source flight']
    contract=json.loads(top['surface_contract'])
    a,b,c,d=contract['bounds']
    baseline=verify_ground(scene)
    cases={}
    assert baseline['passed'], 'Saved scene must pass before injecting faults'

    # Change one genuine adjoining terrain vertex, preserving all XY positions.
    original=land.data
    land.data=original.copy()
    transform=w.matrix_world.inverted()@land.matrix_world
    inverse=transform.inverted()
    try:
        for v in land.data.vertices:
            p=transform@v.co
            if ((abs(p.x-a)<.001 or abs(p.x-b)<.001) and c-.001<=p.y<=d+.001) or ((abs(p.y-c)<.001 or abs(p.y-d)<.001) and a-.001<=p.x<=b+.001):
                p.z+=5
                v.co=inverse@p
                break
        else:raise RuntimeError('No joined boundary vertex in fixture')
        land.data.update()
        cases['raised_join_vertex_5m']=compact(verify_ground(scene))
        assert 'terrain_boundary_matches_top' in cases['raised_join_vertex_5m']['failed_checks']
    finally:
        temporary=land.data;land.data=original;bpy.data.meshes.remove(temporary)

    # Contract tagging must find this duplicate even with an unrelated name.
    duplicate=top.copy()
    duplicate.data=top.data.copy()
    duplicate.name='REGRESSION | duplicate owner'
    scene.collection.objects.link(duplicate)
    bpy.context.view_layer.update()
    try:
        cases['duplicate_ground_owner']=compact(verify_ground(scene))
        assert 'one_visible_ground_owner' in cases['duplicate_ground_owner']['failed_checks']
        assert cases['duplicate_ground_owner']['ownership_failure_samples']==323
    finally:
        temporary=duplicate.data;bpy.data.objects.remove(duplicate,do_unlink=True);bpy.data.meshes.remove(temporary)

    # Remove the cell hit by an actual validator probe, ensuring this fault asks
    # both coverage and sampled ownership rather than relying only on total area.
    transform=w.matrix_world.inverted()@top.matrix_world
    tree=BVHTree.FromPolygons([transform@v.co for v in top.data.vertices],[list(p.vertices) for p in top.data.polygons])
    x=a+(b-a)*(9+.37)/19;y=c+(d-c)*(8+.43)/17
    hit,normal,index,distance=tree.ray_cast(Vector((x,y,1000)),Vector((0,0,-1)),2000)
    assert index is not None
    original=top.data;top.data=original.copy()
    try:
        bm=bmesh.new();bm.from_mesh(top.data);bm.faces.ensure_lookup_table()
        bmesh.ops.delete(bm,geom=[bm.faces[index]],context='FACES')
        bm.to_mesh(top.data);bm.free();top.data.update()
        cases['removed_owner_cell']=compact(verify_ground(scene))
        assert 'top_area_matches_footprint' in cases['removed_owner_cell']['failed_checks']
        assert 'one_visible_ground_owner' in cases['removed_owner_cell']['failed_checks']
        assert cases['removed_owner_cell']['ownership_failure_samples']>0
    finally:
        temporary=top.data;top.data=original;bpy.data.meshes.remove(temporary)
    restored=verify_ground(scene)
    assert restored['passed'], 'Temporary fixture restoration failed'

    # Independently exercise the helper's original-elevation return contract.
    # This new temporary fixture starts from the unexpanded retained source,
    # not from already graded land, so its reference elevations are meaningful.
    source=H.parent/'port-approach-realism/port-approach-realism.blend'
    bpy.ops.wm.open_mainfile(filepath=str(source))
    grade_scene=bpy.context.scene;grade_scene.frame_set(423,subframe=.75)
    grade_contract=repair_ground(grade_scene,precinct_bounds=contract['bounds'])
    grade_baseline=verify_ground(grade_scene)
    assert grade_baseline['passed'] and grade_baseline['grade_return_samples']>0
    grade_land=bpy.data.objects['Jangala | continuous landform']
    grade_world=bpy.data.objects['LAYERED | inverse source flight']
    transform=grade_world.matrix_world.inverted()@grade_land.matrix_world
    inverse=transform.inverted();sample=grade_contract['grade_return_source_vertices'][0]
    for v in grade_land.data.vertices:
        p=transform@v.co
        if abs(p.x-sample[0])<.001 and abs(p.y-sample[1])<.001:
            p.z+=5;v.co=inverse@p
            break
    else:raise RuntimeError('Outer-grade return vertex not found')
    grade_land.data.update()
    grade_fault=verify_ground(grade_scene)
    assert not grade_fault['checks']['grade_returns_to_original_elevation']
    cases['raised_outer_grade_return_5m']=compact(grade_fault)

    report=dict(fixture='Controlled temporary faults injected into the saved expanded rectangular port ground; no scene saved',scene_sha256=scene_sha256,
        passed=True,bounds=contract['bounds'],baseline=compact(baseline),negative_controls=cases,restored=compact(restored),
        grade_fixture=dict(label='Independent temporary helper build from original unexpanded source',baseline=compact(grade_baseline),max_return_height_error_after_fault=grade_fault['max_grade_return_error']),
        elapsed_seconds=round(time.perf_counter()-started,3),
        limits=['One axis-aligned rectangular precinct with nested rectangular material partitions; expanded footprint must contain existing cutout.',
                '323 deterministic interior rays sample visible tagged/named ground owners; finite sampling cannot prove arbitrary sub-pixel holes or unrelated untagged overlays.',
                'Area equality supplements ray coverage but does not alone prove an arbitrary overlapping polygon union.',
                'Boundary fitting checks all terrain vertices on the rectangle; visual motion review remains separate.',
                'Outer-grade return is checked only when retained original-grade samples are present in the build contract.'])
    review=H/'review';review.mkdir(exist_ok=True)
    (review/'ground-regressions.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
    print(json.dumps(report),flush=True)

if __name__=='__main__':main()
