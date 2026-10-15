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
        # Builder retains original topology and has no new mesh modifiers.
        data = obj.data
        offset = len(vertices)
        vertices.extend(obj.matrix_world @ v.co for v in data.vertices)
        for polygon in data.polygons:
            polygons.append(tuple(offset + i for i in polygon.vertices))
            info.append((obj, polygon.index))
    return BVHTree.FromPolygons(vertices, polygons), info


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
    material = bpy.data.materials["REALISM | source-world orthographic fields"]
    coordinate_nodes = [n for n in material.node_tree.nodes if n.type == "TEX_COORD"]
    coordinate_ok = coordinate_ok and len(coordinate_nodes) == 1 and coordinate_nodes[0].object == coords
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
                    bad = depth <= 0 or any(value < -.005 or value > 1.005 for value in uv)
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
              "visible_outside_count":len(outside),"visible_outside_samples":outside[:100],
              "cabin_transparent_hit_count":len(cabin_holes),"cabin_transparent_samples":cabin_holes[:100],
              "uncovered_ray_count":len(misses),"uncovered_samples":misses[:100],"frame_samples":frame_reports,
              "limits":["64px image grid at all96half-frame times; not exhaustive every-pixel proof.",
                        "Local generated-field UVs and crop mapping are evaluated from actual node graph; color/top-versus-side selection is not a visual material-quality check.",
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
