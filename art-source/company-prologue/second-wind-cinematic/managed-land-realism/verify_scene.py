"""Independent saved-scene checks for shot03; never rebuilds/renders/saves blends."""
import hashlib
import importlib.util
import json
import math
from pathlib import Path

import bpy
import numpy as np
from mathutils import Vector
from mathutils.bvhtree import BVHTree
from mathutils.geometry import barycentric_transform
from bpy_extras.object_utils import world_to_camera_view

HERE = Path(__file__).resolve().parent
CAMERA, ROOT, WORLD = "03 LAND / side window", "SECOND WIND | flight root", "LAYERED | inverse source flight"


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def load(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def frame(scene, t):
    scene.frame_set(int(t), subframe=t % 1)


def delta(a, b):
    return max(abs(a[i][j] - b[i][j]) for i in range(4) for j in range(4))


def hierarchy_bvh(scene):
    vertices, polygons, info = [], [], []
    for obj in scene.objects:
        if obj.type != "MESH" or obj.hide_render:
            continue
        # Coverage traverses retained base topology; modifier-aware contact
        # geometry is measured separately by agriculture_audit.
        data = obj.data
        offset = len(vertices)
        vertices.extend(obj.matrix_world @ v.co for v in data.vertices)
        for polygon in data.polygons:
            polygons.append(tuple(offset + i for i in polygon.vertices))
            info.append((obj, polygon.index))
    return BVHTree.FromPolygons(vertices, polygons), info


def agriculture_audit(scene, world, land, root, times):
    """Measure evaluated render geometry, including modifiers, over the move.

    Sample vertices and triangle barycentres/edge midpoints against actual
    terrain triangles. This detects coarse-face terrain crossings, not merely
    proxy labels or corner clearance. Mesh-mesh assembly overlaps are allowed
    (bolted parts); disjoint-assembly collision uses conservative world AABBs.
    """
    inverse = world.matrix_world.inverted()
    transform = inverse @ land.matrix_world
    reference=json.loads(land.get('ground_reference_geometry_json','null'))
    terrain_vertices=[Vector(v) for v in reference['vertices']] if reference else [v.co for v in land.data.vertices]
    terrain_polygons=reference['polygons'] if reference else [tuple(p.vertices) for p in land.data.polygons]
    tree = BVHTree.FromPolygons([transform @ v for v in terrain_vertices],terrain_polygons)
    ship = set(root.children_recursive)
    stats, collisions, issues, projectors = {}, [], [], {}
    assemblies = [o for o in scene.objects if o.type == 'EMPTY' and o.name.startswith('AGRI | ')
                  and o.parent == world]
    for t in times:
        frame(scene, t)
        depsgraph = bpy.context.evaluated_depsgraph_get()
        inverse = world.matrix_world.inverted()
        bounds = {}
        for obj in scene.objects:
            if obj.type != 'MESH' or obj.hide_render or obj in ship or obj == land:
                continue
            # Sky/canopy plates and the independently authored river are not
            # competing land surfaces. Their intentional depth is checked above.
            if obj.name in ('REALISM | sky','REALISM | distant hills','REALISM | middle woodland',
                            'REALISM | distant Atlas card','Jangala | winding river','Jangala | far estuary'):
                continue
            evaluated = obj.evaluated_get(depsgraph)
            projected = [world_to_camera_view(scene, scene.camera, evaluated.matrix_world @ Vector(corner))
                         for corner in evaluated.bound_box]
            if not obj.name.startswith('AGRI | ') and (max(p.z for p in projected) <= 0 or
                (min(p.z for p in projected) > 0 and
                 (max(p.x for p in projected) < 0 or min(p.x for p in projected) > 1 or
                  max(p.y for p in projected) < 0 or min(p.y for p in projected) > 1))):
                continue
            mesh = evaluated.to_mesh()
            try:
                mesh.calc_loop_triangles()
                matrix = inverse @ evaluated.matrix_world
                vertices = [matrix @ v.co for v in mesh.vertices]
                if not vertices:
                    continue
                if any(not math.isfinite(c) for v in vertices for c in v):
                    issues.append('Nonfinite evaluated prop geometry: '+obj.name)
                    continue
                lo = [min(v[i] for v in vertices) for i in range(3)]
                hi = [max(v[i] for v in vertices) for i in range(3)]
                assembly = next((a for a in assemblies if obj in a.children_recursive),None)
                if assembly:
                    for material in obj.data.materials:
                        if not material or not material.use_nodes:
                            continue
                        for node in material.node_tree.nodes:
                            if node.type == 'TEX_COORD' and node.object and node.object.type == 'CAMERA':
                                if node.object.parent != assembly:
                                    issues.append('Prop texture projector belongs to another frame: '+obj.name)
                    old = bounds.setdefault(assembly.name,[lo.copy(),hi.copy()])
                    for i in range(3):
                        old[0][i],old[1][i] = min(old[0][i],lo[i]),max(old[1][i],hi[i])
                samples = vertices.copy()
                top_samples = []
                for tri in mesh.loop_triangles:
                    a,b,c = (vertices[i] for i in tri.vertices)
                    probes = [(a+b+c)/3,(a+b)/2,(a+c)/2,(b+c)/2]
                    samples.extend(probes)
                    normal = (b-a).cross(c-a)
                    if normal.length and normal.normalized().z > .7:
                        top_samples.extend([a,b,c,*probes])
                def clearances(points):
                    result=[]
                    for point in points:
                        hit,_,_,_=tree.ray_cast(Vector((point.x,point.y,5000)),Vector((0,0,-1)),10000)
                        if hit is not None:
                            result.append(point.z-hit.z)
                    return result
                gaps = clearances(samples)
                tops = clearances(top_samples)
                if not gaps:
                    if obj.name.startswith('AGRI | '):
                        issues.append('No terrain below prop: '+obj.name)
                    continue
                row = stats.setdefault(obj.name,{'minimum_clearance_m':1e9,'maximum_clearance_m':-1e9,
                    'minimum_top_clearance_m':1e9,'sample_count':0,'evaluated_times':0,
                    'thin_ground_surface':False,'terrain_crossing_samples':0})
                row['volumetric_fx'] = obj.pass_index == 63
                if obj.pass_index == 63:
                    material=obj.data.materials[0]
                    nodes=material.node_tree.nodes
                    output=next(n for n in nodes if n.type=='OUTPUT_MATERIAL')
                    mix=output.inputs['Surface'].links[0].from_node
                    factor=mix.inputs[0].links[0].from_node if mix.type=='MIX_SHADER' else None
                    sources=[s.links[0].from_node for s in factor.inputs[:2] if s.is_linked] if factor else []
                    fades=[n for n in sources if n.type=='MAP_RANGE']
                    noise=[n for n in sources if n.type=='TEX_NOISE']
                    upper=max(fades[0].inputs['To Min'].default_value,fades[0].inputs['To Max'].default_value) if len(fades)==1 else 1
                    row['maximum_shader_opacity_bound']=upper
                    if not factor or factor.operation!='MULTIPLY' or len(noise)!=1 or upper>=.5:
                        issues.append('Spray shader no longer satisfies transparent coverage bound: '+obj.name)

                if obj.pass_index == 63 and (not assembly or 'tender' not in assembly.name):
                    issues.append('Spray FX is not attached to moving tender: '+obj.name)
                row['minimum_clearance_m']=min(row['minimum_clearance_m'],min(gaps))
                row['maximum_clearance_m']=max(row['maximum_clearance_m'],max(gaps))
                row['sample_count']+=len(gaps)
                row['evaluated_times']+=1
                if tops:
                    row['minimum_top_clearance_m']=min(row['minimum_top_clearance_m'],min(tops))
                row['terrain_crossing_samples']+=sum(g < -.02 for g in gaps)
                thin = hi[2]-lo[2] < max(1, .04*max(hi[0]-lo[0],hi[1]-lo[1]))
                near = min(gaps) < 1 and max(gaps) > -.1
                if near and tops and (thin or max(hi[0]-lo[0],hi[1]-lo[1]) > 20):
                    row['thin_ground_surface']=True
                    if min(tops) < -.02:
                        issues.append('Competing ground top intersects terrain: '+obj.name)
                    # Fractional-metre separation across broad opaque top faces
                    # recreates the original precision/overlap risk regardless
                    # of pass index. Physical thick foundations may bury bottoms.
                    if min(tops) < .08 and max(hi[0]-lo[0],hi[1]-lo[1]) > 20:
                        issues.append('Broad ground overlay lacks top clearance: '+obj.name)
                if assembly and ('tender' in assembly.name or 'drone' in assembly.name) and min(gaps) < .25:
                    issues.append('Hovering assembly intersects/approaches terrain: '+obj.name)
            finally:
                evaluated.to_mesh_clear()
        for i,a in enumerate(assemblies):
            for b in assemblies[i+1:]:
                if a.name in bounds and b.name in bounds:
                    aa,bb=bounds[a.name],bounds[b.name]
                    if all(min(aa[1][k],bb[1][k])-max(aa[0][k],bb[0][k])>0 for k in range(3)):
                        collisions.append({'frame':t,'assemblies':[a.name,b.name]})
        for obj in scene.objects:
            if obj.type != 'CAMERA' or not obj.name.startswith('AGRI | '):
                continue
            row=projectors.setdefault(obj.name,{'parent':obj.parent.name if obj.parent else None,
                                               'max_local_delta':0,'reference':obj.matrix_basis.copy()})
            row['max_local_delta']=max(row['max_local_delta'],delta(obj.matrix_basis,row['reference']))
            if obj.parent not in assemblies:
                issues.append('Reskin projector is not attached to its rigid assembly: '+obj.name)
    for row in projectors.values():
        row.pop('reference')
        if row['max_local_delta']>1e-6:
            issues.append('Object-attached projector local transform drifts')
    if collisions:
        issues.append('Agriculture assembly AABBs overlap; inspect possible collision')
    return {'objects':stats,'projectors':projectors,'assembly_aabb_collisions':collisions,
            'errors':sorted(set(issues)), 'sample_times':len(times),
            'terrain_clearance_reference':'Retained pre-partition terrain geometry; actual visible ownership audited separately' if reference else 'Actual terrain geometry',
            'limits':['Evaluated vertices, triangle centers and edge midpoints; not exhaustive surface proof.',
                      'Assembly AABB rejection is conservative; touching/overlapping parts inside one assembly are intentional.']}


def main():
    source = HERE.parent / "layered-next-scenes/layered-next-scenes.blend"
    protected = [source, HERE.parent / "animatic/jangala-blockout.blend"]
    source_hashes = {p.relative_to(HERE.parent).as_posix(): digest(p) for p in protected}
    geometry_helper = load("projection_geometry", HERE.parent / "painted-window/verify_projection.py")
    motion = load("cabin_motion", HERE.parent / "parallax-window/diagnose_motion.py")
    guide = json.loads((HERE / "guide-manifest.json").read_text())
    manifest = json.loads((HERE / "scene-manifest.json").read_text())
    times = [240 + i * .5 for i in range(96)]
    bpy.ops.wm.open_mainfile(filepath=str(source))
    source_geometry = geometry_helper.geometry_hashes()
    original = []
    for t in times:
        frame(bpy.context.scene, t)
        original.append({name: bpy.data.objects[name].matrix_world.copy() for name in (CAMERA, ROOT, WORLD)})
        original[-1]["camera_local"] = bpy.data.objects[CAMERA].matrix_basis.copy()
    final = HERE / "managed-land-realism.blend"
    final_hash_before=digest(final)
    bpy.ops.wm.open_mainfile(filepath=str(final))
    scene = bpy.context.scene
    camera, root, world = (bpy.data.objects[n] for n in (CAMERA, ROOT, WORLD))
    errors, warnings = [], []
    geometry = geometry_helper.geometry_hashes()
    changed = [name for name, value in source_geometry.items() if geometry.get(name) != value]
    if changed or len(geometry) != 171:
        errors.append("Expected171 unchanged ship meshes")
    transforms = []
    for t, old in zip(times, original):
        frame(scene, t)
        entry = {"frame": t, "camera_local": delta(camera.matrix_basis, old["camera_local"])}
        entry.update({name: delta(bpy.data.objects[name].matrix_world, old[name]) for name in (CAMERA, ROOT, WORLD)})
        transforms.append(entry)
    if any(value > 1e-5 for entry in transforms for key, value in entry.items() if key != "frame"):
        errors.append("Source camera/rebase transforms changed")
    if scene.render.fps != 12 or scene.render.fps_base != 1 or manifest["output_frames"] != 96:
        errors.append("Source/output time mapping changed")
    if any(m.camera for m in scene.timeline_markers):
        errors.append("Bound timeline camera markers can override shot camera")
    coords = bpy.data.objects["REALISM | source-world coordinates"]
    coordinate_ok = coords.parent == world and delta(coords.matrix_basis, world.matrix_basis.Identity(4)) < 1e-7
    material = bpy.data.materials["REALISM | field ground clipped at actual source river nearbank"]
    reachable=set()
    def upstream(node):
        if node in reachable:return
        reachable.add(node)
        for socket in node.inputs:
            for link in socket.links:upstream(link.from_node)
    upstream(next(n for n in material.node_tree.nodes if n.type=='OUTPUT_MATERIAL'))
    coordinate_nodes = [n for n in reachable if n.type == "TEX_COORD"]
    coordinate_ok = coordinate_ok and bool(coordinate_nodes) and all(n.object == coords or (n.object and n.object.name == 'AGRI | right agricultural service compound' and material.get('surface_cutout_owner')) for n in coordinate_nodes)
    if not coordinate_ok:
        errors.append("FieldXY coordinate object is not rigidly attached to source-world frame")
    missing = [im.filepath for im in bpy.data.images if im.source == "FILE" and im.filepath and
               not im.packed_file and not Path(bpy.path.abspath(im.filepath)).is_file()]
    if missing:
        errors.append("Unresolved image textures")
    packed = [im.name for im in bpy.data.images if im.source == "FILE" and im.packed_file]
    absolute_paths = [im.filepath for im in bpy.data.images if im.source == "FILE" and im.filepath and not im.filepath.startswith("//")]
    if packed or absolute_paths:
        errors.append("Expected external relative textures without packed duplicates")

    # Coverage alone cannot detect intersecting surfaces: the nearest ray hit
    # may alternate between two fully textured objects. Enforce one ground
    # owner and independently measure terrain coverage within every parcel.
    visible_proxies = [obj.name for obj in scene.objects
        if obj.type == 'MESH' and obj.pass_index == 13 and not obj.hide_render]
    if visible_proxies:
        errors.append('Competing plantation ground proxy surfaces remain renderable')
    land = bpy.data.objects['Jangala | continuous landform']
    land_matrix = world.matrix_world.inverted() @ land.matrix_world
    land_tree = BVHTree.FromPolygons([land_matrix @ v.co for v in land.data.vertices],
                                     [tuple(p.vertices) for p in land.data.polygons])
    # Physical slab partitions remove terrain below the compound. Coverage is
    # therefore the union of actual terrain and the two owning slab meshes.
    ground_vertices=[land_matrix @ v.co for v in land.data.vertices]
    ground_polygons=[tuple(p.vertices) for p in land.data.polygons]
    for name in ('AGRI | compound broad apron','AGRI | landing service pad'):
        obj=bpy.data.objects.get(name)
        if obj:
            offset=len(ground_vertices)
            transform=world.matrix_world.inverted() @ obj.matrix_world
            ground_vertices.extend(transform @ v.co for v in obj.data.vertices)
            ground_polygons.extend(tuple(offset+i for i in p.vertices) for p in obj.data.polygons)
    covered_ground_tree=BVHTree.FromPolygons(ground_vertices,ground_polygons)
    footprint_checks = []
    for parcel in json.loads(land.get('field_footprints_json', '[]')):
        xmin, xmax, ymin, ymax = parcel['source_xy_bounds_m']
        hits = []
        for ix in range(21):
            for iy in range(21):
                point = Vector((xmin + (xmax-xmin)*ix/20, ymin + (ymax-ymin)*iy/20, 5000))
                hit, _, _, _ = covered_ground_tree.ray_cast(point, Vector((0,0,-1)), 10000)
                if hit is not None:
                    hits.append(hit.z)
        footprint_checks.append({**parcel, 'sample_count':441, 'terrain_hits':len(hits),
                                  'terrain_z_range_m':[min(hits), max(hits)] if hits else None,
                                  'detail_surface_clearance_m':0,
                                  'detail_surface_owner':'Partitioned terrain/apron/landing surface union' if land.get('ground_geometry_cutout_owner') else land.name})
    if not footprint_checks or any(p['terrain_hits'] != 441 for p in footprint_checks):
        errors.append('Field detail footprint lacks continuous terrain coverage')

    image_cache = {}
    def alpha(image, uv):
        u, v = uv
        if not (0 <= u <= 1 and 0 <= v <= 1):
            return 0.0
        if image.name not in image_cache:
            values = np.empty(len(image.pixels), dtype=np.float32)
            image.pixels.foreach_get(values)
            image_cache[image.name] = values.reshape((image.size[1], image.size[0], 4))[:, :, 3]
        data = image_cache[image.name]
        return float(data[min(data.shape[0]-1, int(v*data.shape[0])), min(data.shape[1]-1, int(u*data.shape[1]))])

    def surface_uv(obj, poly_index, point):
        polygon = obj.data.polygons[poly_index]
        loops = list(polygon.loop_indices)[:3]
        vertices = [obj.matrix_world @ obj.data.vertices[obj.data.loops[i].vertex_index].co for i in loops]
        uvs = [Vector((*obj.data.uv_layers.active.data[i].uv,0)) for i in loops]
        p = barycentric_transform(point, *vertices, *uvs)
        return Vector((p.x,p.y,0))

    def texture_uv(texture, obj, poly_index, point, evaluate_socket=None):
        """Evaluate actual coordinate-node graph, including cropped field UVs."""
        def value(socket):
            if not socket.is_linked:
                default = socket.default_value
                return Vector(default[:3]) if hasattr(default,"__len__") else float(default)
            output = socket.links[0].from_socket
            node = output.node
            if node.type == "TEX_COORD":
                if output.name == "UV":
                    return surface_uv(obj,poly_index,point)
                if output.name == "Object":
                    return (node.object or obj).matrix_world.inverted() @ point
                if output.name == "Generated":
                    local = obj.matrix_world.inverted() @ point
                    bounds = list(zip(*obj.bound_box))
                    return Vector((local[i]-min(bounds[i]))/(max(bounds[i])-min(bounds[i]) or 1) for i in range(3))
            if node.type == "SEPXYZ":
                return value(node.inputs[0])["XYZ".index(output.name)]
            if node.type == "COMBXYZ":
                return Vector(value(node.inputs[i]) for i in range(3))
            if node.type == "MATH":
                a,b = value(node.inputs[0]),value(node.inputs[1])
                if node.operation == "MULTIPLY_ADD": return a*b+value(node.inputs[2])
                if node.operation == "MULTIPLY": return a*b
                if node.operation == "ADD": return a+b
                if node.operation == "SUBTRACT": return a-b
                if node.operation == "DIVIDE": return a/b if abs(b)>1e-12 else 0
                if node.operation == "SINE": return math.sin(a)
                if node.operation == "GREATER_THAN": return float(a>b)
                if node.operation == "LESS_THAN": return float(a<b)
                if node.operation == "MAXIMUM": return max(a,b)
                if node.operation == "MINIMUM": return min(a,b)
                if node.operation == "FRACT": return a-math.floor(a)
                if node.operation == "PINGPONG": return b-abs((a%(2*b))-b) if b>0 else 0
            if node.type == "TEX_IMAGE" and output.name == "Alpha":
                uv=value(node.inputs["Vector"])
                return alpha(node.image,(uv.x,uv.y))
            if node.type == "MAP_RANGE":
                v=value(node.inputs["Value"])
                a,b=value(node.inputs["From Min"]),value(node.inputs["From Max"])
                t=(v-a)/(b-a) if abs(b-a)>1e-12 else 0
                if node.clamp: t=max(0,min(1,t))
                return value(node.inputs["To Min"])+t*(value(node.inputs["To Max"])-value(node.inputs["To Min"]))
            if node.type == "BSDF_TRANSPARENT": return 0.0
            if node.type in {"EMISSION","BSDF_PRINCIPLED"}: return 1.0
            if node.type == "MIX_SHADER":
                f=max(0,min(1,value(node.inputs[0])))
                return (1-f)*value(node.inputs[1])+f*value(node.inputs[2])
            if node.type == "VECT_MATH":
                a,b = value(node.inputs[0]),value(node.inputs[1])
                if node.operation == "LENGTH": return a.length
                if node.operation == "MULTIPLY_ADD": return Vector(a[i]*b[i]+value(node.inputs[2])[i] for i in range(3))
                if node.operation == "MULTIPLY": return Vector(a[i]*b[i] for i in range(3))
                if node.operation == "ADD": return a+b
                if node.operation == "SUBTRACT": return a-b
                if node.operation == "SCALE": return a*value(node.inputs[3])
            raise ValueError("Unsupported coordinate graph node: "+node.name+"/"+node.type)
        if evaluate_socket is not None:
            return value(evaluate_socket)
        vector = value(texture.inputs["Vector"])
        return (vector.x,vector.y)

    pad_ownership = {'tested_rays':0,'failures':[],'surface_contract':None}
    compound = bpy.data.objects.get('AGRI | right agricultural service compound')
    if compound:
        raw_contract = compound.get('ground_surface_partition')
        if not raw_contract:
            errors.append('Compound pad lacks explicit ground surface partition')
        else:
            pad_ownership['surface_contract'] = json.loads(raw_contract)
        ground_objects = [land,bpy.data.objects['AGRI | compound broad apron'],
                           bpy.data.objects['AGRI | landing service pad']]
        for obj in ground_objects[:2]:
            for material in obj.data.materials:
                if material.get('surface_cutout_owner') and material.surface_render_method != 'DITHERED':
                    errors.append('Binary ground cutout lacks depth-buffer participation: '+material.name)
        # Test actual competing intersections along camera rays through the
        # clear apron and landing pad, evaluating each lower-surface cutout.
        for t in times:
            frame(scene,t)
            if int((t-240)*2)%24==0:
                print('PAD_OWNERSHIP_SAMPLE',t,flush=True)
            trees=[]
            for obj in ground_objects:
                tree=BVHTree.FromPolygons([obj.matrix_world @ v.co for v in obj.data.vertices],
                                         [tuple(p.vertices) for p in obj.data.polygons])
                trees.append((obj,tree))
            for ix in range(15):
                for iy in range(11):
                    x,y=-100+ix*200/14,-72+iy*144/10
                    # Avoid building/silo footprints: this check concerns
                    # ground surface ownership beneath their occlusion too.
                    landing=38<x<86 and -61<y<-13
                    target=compound.matrix_world @ Vector((x,y,.9 if landing else .5))
                    origin=camera.matrix_world.translation
                    direction=(target-origin).normalized()
                    intersections=[]
                    for obj,tree in trees:
                        point,_,index,distance=tree.ray_cast(origin,direction,90000)
                        if point is None:continue
                        polygon=obj.data.polygons[index]
                        mat=obj.data.materials[polygon.material_index]
                        output=next(n for n in mat.node_tree.nodes if n.type=='OUTPUT_MATERIAL')
                        opacity=texture_uv(None,obj,index,point,output.inputs['Surface'])
                        intersections.append({'object':obj.name,'distance':distance,'opacity':opacity})
                    visible=sorted((r for r in intersections if r['opacity']>.5),key=lambda r:r['distance'])
                    expected=ground_objects[2 if landing else 1].name
                    pad_ownership['tested_rays']+=1
                    # Each footprint must have one opaque ground owner. Two
                    # physical surfaces separated by .5m are not sufficient
                    # with a .06m near clip and kilometre-scale grazing depth.
                    vertical_visible=[]
                    vertical_origin=compound.matrix_world @ Vector((x,y,100))
                    vertical_direction=(compound.matrix_world.to_3x3() @ Vector((0,0,-1))).normalized()
                    for obj,tree in trees:
                        point,_,index,distance=tree.ray_cast(vertical_origin,vertical_direction,200)
                        if point is None:continue
                        mat=obj.data.materials[obj.data.polygons[index].material_index]
                        output=next(n for n in mat.node_tree.nodes if n.type=='OUTPUT_MATERIAL')
                        opacity=texture_uv(None,obj,index,point,output.inputs['Surface'])
                        if opacity>.5:vertical_visible.append(obj.name)
                    # At a silhouette, an oblique camera ray can legitimately
                    # hit exposed apron behind the landing slab at another XY.
                    # Require one owner at the same plan footprint, and require
                    # the camera's nearest opaque intersection to agree.
                    if vertical_visible!=[expected] or not visible or visible[0]['object']!=expected:
                        if len(pad_ownership['failures'])<100:
                            pad_ownership['failures'].append({'frame':t,'compound_xy':[x,y],
                                'expected':expected,'vertical_owners':vertical_visible,'intersections':intersections})
        if pad_ownership['failures']:
            errors.append('Pad rays encounter duplicate or incorrect visible ground owners')

    coverage = {}
    outside, cabin_holes, misses = [], [], []
    frame_reports = []
    width, height = 1920,1080
    for t in times:
        frame(scene, t)
        tree, owners = hierarchy_bvh(scene)
        corners = camera.data.view_frame(scene=scene)
        xmin,xmax = min(v.x for v in corners),max(v.x for v in corners)
        ymin,ymax = min(v.y for v in corners),max(v.y for v in corners)
        origin = camera.matrix_world.translation
        rotation = camera.matrix_world.to_3x3()
        count = 0
        for py in range(32,height,64):
            for px in range(32,width,64):
                direction = (rotation @ Vector((xmin+(xmax-xmin)*px/width,
                                                ymax-(ymax-ymin)*py/height,corners[0].z))).normalized()
                start = origin.copy()
                for attempt in range(24):
                    point,normal,index,distance = tree.ray_cast(start,direction,90000)
                    if point is None:
                        misses.append({"frame":t,"pixel":[px,py]})
                        break
                    obj, polygon_index = owners[index]
                    if obj.pass_index == 63:
                        # Fine spray has a proven .22 maximum alpha gate; the
                        # .5 coverage decision always traverses it. Its actual
                        # geometry/terrain clearance is measured independently.
                        start = point + direction*.01
                        continue
                    polygon = obj.data.polygons[polygon_index]
                    mat = obj.data.materials[polygon.material_index] if obj.data.materials else None
                    if not mat or not mat.use_nodes:
                        break
                    textures = [n for n in mat.node_tree.nodes if n.type == "TEX_IMAGE"]
                    texture = textures[0] if textures else None
                    # Identify the actual alpha source rather than assuming the
                    # first image also supplies RGB (hill master+mask differs).
                    for node in mat.node_tree.nodes:
                        if node.type == "MAP_RANGE" and node.inputs["Value"].is_linked:
                            upstream = node.inputs["Value"].links[0].from_node
                            if upstream.type == "TEX_IMAGE":
                                texture = upstream
                    if not texture:
                        output=next(n for n in mat.node_tree.nodes if n.type=="OUTPUT_MATERIAL")
                        opacity=texture_uv(None,obj,polygon_index,point,output.inputs["Surface"])
                        if opacity<.5:
                            start = point + direction*.01
                            continue
                        stats=coverage.setdefault(mat.name,{"hits":0,"procedural_no_image_uv":True})
                        stats["hits"]+=1
                        count+=1
                        break
                    uv = texture_uv(texture,obj,polygon_index,point)
                    depth = 1.0
                    texcoords = [n for n in mat.node_tree.nodes if n.type == "TEX_COORD" and n.object and n.object.type == "CAMERA"]
                    if texcoords:
                        depth = -(texcoords[0].object.matrix_world.inverted() @ point).z
                    texture_alpha = alpha(texture.image,uv)
                    output=next(n for n in mat.node_tree.nodes if n.type=="OUTPUT_MATERIAL")
                    opacity=texture_uv(texture,obj,polygon_index,point,output.inputs["Surface"])
                    is_card = any(n.type == "MIX_SHADER" for n in mat.node_tree.nodes)
                    # Transparent card holes expose the next surface. Soft-edge
                    # blend quality is deliberately left for rendered inspection.
                    if is_card and opacity < .5:
                        start = point + direction*.01
                        continue
                    name = mat.name
                    stats = coverage.setdefault(name,{"hits":0,"uv_min":[1e9,1e9],"uv_max":[-1e9,-1e9],"alpha_min":1,
                                                       "texture_images":[n.image.name for n in textures],
                                                       "coverage_alpha_image":texture.image.name})
                    stats["hits"] += 1
                    stats["alpha_min"] = min(stats["alpha_min"],texture_alpha)
                    for axis in range(2):
                        stats["uv_min"][axis] = min(stats["uv_min"][axis],uv[axis])
                        stats["uv_max"][axis] = max(stats["uv_max"][axis],uv[axis])
                    # Prop images are clipped color overlays with an opaque physical
                    # fallback; outside image UV is deliberately valid there.
                    prop_fallback = obj.name.startswith('AGRI | ') and any(n.type == 'BSDF_PRINCIPLED' for n in mat.node_tree.nodes)
                    bad = depth <= 0 or (not prop_fallback and any(value < -.005 or value > 1.005 for value in uv))
                    record = {"frame":t,"pixel":[px,py],"object":obj.name,"material":name,"uv":list(uv),"alpha":texture_alpha,"shader_opacity":opacity}
                    if bad:
                        outside.append(record)
                    if "cabin" in name and texture_alpha < .5:
                        cabin_holes.append(record)
                    count += 1
                    break
        frame_reports.append({"frame":t,"textured_first_hits":count})
        if int((t-240)*2) % 24 == 0:
            print("VERIFY_SAMPLE",t,flush=True)
    if outside:
        errors.append("Visible textured samples outside projection/image bounds")
    if cabin_holes:
        errors.append("Visible cabin geometry projects into transparent artwork")
    if misses:
        errors.append("Visible frame rays have no covered geometry/sky")
    agriculture_checks = agriculture_audit(scene, world, land, root, times)
    errors.extend(agriculture_checks['errors'])
    after = {p.relative_to(HERE.parent).as_posix(): digest(p) for p in protected}
    if after != source_hashes:
        errors.append("Protected source blend changed during verification")
    final_hash_after=digest(final)
    if final_hash_before!=final_hash_after:
        errors.append("Selected final blend changed during verification; rerun against stable save")
    if any(obj.type=="MESH" and not obj.hide_render and "Atlas" in obj.name for obj in scene.objects) and not any("Atlas" in name for name in coverage):
        warnings.append("Atlas cutout was not intercepted by64px coverage grid; rendered traffic visibility is not established by this report")
    far_ground_used=any(not obj.hide_render and obj.type=="MESH" and any(mat and mat.name=="REALISM | registered far ground" for mat in obj.data.materials) for obj in scene.objects)
    if far_ground_used and not any("far ground" in name for name in coverage):
        warnings.append("Far-ground material not encountered by sampled rays; may be masked by woodland")
    report = {"passed":not errors,"errors":errors,"warnings":warnings,
              "source":"../layered-next-scenes/layered-next-scenes.blend","scene":"managed-land-realism.blend",
              "source_file_sha256_before":source_hashes,"source_file_sha256_after":after,
              "scene_sha256_before":final_hash_before,"scene_sha256_after":final_hash_after,
              "ship_mesh_count":len(geometry),"changed_ship_meshes":changed,
              "time_mapping":{"source_fps":12,"output_fps":24,"source_samples":96,"range":[240,287.5]},
              "max_transform_delta":max(value for r in transforms for key,value in r.items() if key!="frame"),
              "field_coordinates_attached_to_scenery":coordinate_ok,"missing_images":missing,
              "packed_file_images":packed,"absolute_image_paths":absolute_paths,
              "selected_assets":manifest.get("assets"),"coverage_by_material":coverage,
              "agriculture_geometry_check":agriculture_checks,
              "compound_pad_ray_ownership":pad_ownership,
              "ground_surface_check":{"visible_competing_proxies":visible_proxies,
                                      "field_footprint_coverage":footprint_checks,
                                      "contract":"Detail shades the terrain surface directly; no offset mesh"},
              "visible_outside_count":len(outside),"visible_outside_samples":outside[:100],
              "cabin_transparent_hit_count":len(cabin_holes),"cabin_transparent_samples":cabin_holes[:100],
              "uncovered_ray_count":len(misses),"uncovered_samples":misses[:100],"frame_samples":frame_reports,
              "limits":["64px image grid at all96half-frame times; not exhaustive every-pixel proof.",
                        "Source-XY parcel masks are bound to the terrain shader; numerical coverage does not establish material quality.",
                        "Shader opacity graph evaluated including alpha remaps, bank sine-curve and nested transparent mixes; traversal uses0.5opacity decision, not full soft-edge compositing.",
                        "Cabin alpha check cannot detect incorrect RGB aperture paint when its alpha is opaque; rendered aperture/trim registration still required.",
                        "Source hashes prove no source modification during this validation, not prior historical immutability.",
                        "No render, scene rebuild or blend save performed."]}
    output = HERE / "review/scene-validation.json"
    output.parent.mkdir(parents=True,exist_ok=True)
    output.write_text(json.dumps(report,indent=2)+"\n",encoding="utf-8")
    print("SCENE_VALIDATION",json.dumps({k:v for k,v in report.items() if k not in {"frame_samples","visible_outside_samples","cabin_transparent_samples","uncovered_samples"}}),flush=True)
    if errors:
        raise RuntimeError("; ".join(errors))


if __name__ == "__main__":
    main()
