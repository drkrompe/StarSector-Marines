"""Background geometric regression and before/after rendered boundary proof."""
import bpy,sys,json,math
from pathlib import Path
from mathutils import Vector
from mathutils.bvhtree import BVHTree
H=Path(__file__).resolve().parent
sys.path.insert(0,str(H))
from ground_surfaces import repair_ground

def verify_ground(scene):
    w=bpy.data.objects['LAYERED | inverse source flight']
    land=bpy.data.objects['Jangala | continuous landform']
    top=bpy.data.objects['PORT GROUND | continuous precinct and roads']
    contract=json.loads(top['surface_contract']);a,b,c,d=contract['bounds'];z=contract['top_z']
    t=w.matrix_world.inverted()@land.matrix_world
    boundary=[];overlaps=[];source_vertices=[]
    for v in land.data.vertices:
        p=t@v.co
        source_vertices.append(p)
        on_x=(abs(p.x-a)<.001 or abs(p.x-b)<.001) and c-.001<=p.y<=d+.001
        on_y=(abs(p.y-c)<.001 or abs(p.y-d)<.001) and a-.001<=p.x<=b+.001
        if on_x or on_y:boundary.append(abs(p.z-z))
    for p in land.data.polygons:
        q=t@p.center
        if a+.001<q.x<b-.001 and c+.001<q.y<d-.001:overlaps.append(p.index)
    # Every rectangular top cell must tile the requested area exactly once.
    area=sum(p.area for p in top.data.polygons)
    expected=(b-a)*(d-c)
    # Probe actual mesh interiors rather than trusting only vertex/face-centre
    # heights. Other visible road/precinct decks must count as duplicate owners.
    surfaces=[]
    for o in scene.objects:
        if o.type!='MESH' or o.hide_render:continue
        if o not in (land,top) and 'surface_contract' not in o and not any(word in o.name.lower() for word in ('precinct','freight road')):continue
        transform=w.matrix_world.inverted()@o.matrix_world
        tree=BVHTree.FromPolygons([transform@v.co for v in o.data.vertices],[list(p.vertices) for p in o.data.polygons],all_triangles=False)
        surfaces.append((o.name,tree))
    failures=[]
    for j in range(17):
        for i in range(19):
            x=a+(b-a)*(i+.37)/19;y=c+(d-c)*(j+.43)/17
            owners=[]
            for name,tree in surfaces:
                hit,normal,index,distance=tree.ray_cast(Vector((x,y,1000)),Vector((0,0,-1)),2000)
                if hit is not None:owners.append((name,hit.z))
            if len(owners)!=1 or abs(owners[0][1]-z)>.001:failures.append(dict(x=x,y=y,owners=owners))
    grade_samples=contract.get('grade_return_source_vertices',[])
    grade_errors=[]
    for original in grade_samples:
        candidates=[p for p in source_vertices if abs(p.x-original[0])<.001 and abs(p.y-original[1])<.001]
        grade_errors.append(min((abs(p.z-original[2]) for p in candidates),default=float('inf')))
    checks=dict(terrain_no_covered_faces=not overlaps,terrain_boundary_matches_top=bool(boundary) and max(boundary)<.001,top_area_matches_footprint=abs(area-expected)<.1,one_visible_ground_owner=not failures)
    if grade_samples:checks['grade_returns_to_original_elevation']=max(grade_errors)<.001
    return dict(passed=all(checks.values()),checks=checks,boundary_samples=len(boundary),max_boundary_height_error=max(boundary,default=None),top_area=area,expected_area=expected,interior_ray_samples=323,ownership_failures=failures,grade_return_samples=len(grade_samples),max_grade_return_error=max(grade_errors,default=None))

if __name__=='__main__':
    bpy.ops.wm.open_mainfile(filepath=str(H.parent/'port-approach-realism/port-approach-realism.blend'))
    s=bpy.context.scene;s.frame_set(423,subframe=.75)
    # Old boundary retained in this controlled A/B; only ground repair changes.
    repair_ground(s,precinct_bounds=(-1745,-195,65,495))
    result=verify_ground(s)
    (H/'build/ground/verification.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
    print(json.dumps(result),flush=True)
    if not result['passed']:raise RuntimeError('Ground ownership regression')
    s.eevee.taa_render_samples=16
    for frame,label in ((400,'start'),(423.75,'midpoint'),(447.5,'end')):
        s.frame_set(int(frame),subframe=frame%1)
        s.render.filepath=str(H/('build/ground/repaired-'+label+'.png'))
        bpy.ops.render.render(write_still=True)
