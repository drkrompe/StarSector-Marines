"""Compare saved foliage study with base; background Blender, no scene writes.

blender --background --factory-startup --python-exit-code 1 --python verify_scene.py
Optional: -- --base path.blend --scene path.blend
"""
from pathlib import Path
import argparse
import hashlib
import json
import sys
import bpy
from mathutils import Vector

HERE = Path(__file__).resolve().parent
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--base", type=Path, default=HERE.parent / "animatic/jangala-blockout.blend")
parser.add_argument("--scene", type=Path, default=HERE / "jangala-forest-lookdev.blend")
args = parser.parse_args(sys.argv[sys.argv.index("--")+1:] if "--" in sys.argv else [])
errors = []
def require(ok, message):
    if not ok:
        errors.append(message)

def digest(value):
    return hashlib.sha256(json.dumps(value, separators=(",", ":"), sort_keys=True).encode()).hexdigest()

def animation(owner):
    ad = owner.animation_data
    if not ad or not ad.action:
        return []
    action = ad.action
    curves = []
    # Blender 5.2 layered actions: select only this owner's action slot.
    slot = getattr(ad, "action_slot", None)
    for layer in action.layers:
        for strip in layer.strips:
            bags = list(getattr(strip, "channelbags", []))
            for bag in bags:
                if slot is not None and bag.slot_handle != slot.handle:
                    continue
                for fc in bag.fcurves:
                    curves.append([fc.data_path, fc.array_index, fc.extrapolation,
                                   [[list(k.co), list(k.handle_left), list(k.handle_right),
                                     k.interpolation, k.handle_left_type, k.handle_right_type]
                                    for k in fc.keyframe_points]])
    return sorted(curves, key=lambda row: (row[0], row[1]))

def matrix(m):
    return [list(row) for row in m]

def mesh_snapshot(ob):
    return {"geometry": digest([[list(v.co) for v in ob.data.vertices],
                                [list(p.vertices) for p in ob.data.polygons]]),
            "basis": matrix(ob.matrix_basis), "parent_inverse": matrix(ob.matrix_parent_inverse),
            "parent": ob.parent.name if ob.parent else None,
            "animation": digest(animation(ob))}

def descendant(ob, root):
    while ob.parent:
        ob = ob.parent
        if ob == root:
            return True
    return False

bpy.ops.wm.open_mainfile(filepath=str(args.base.resolve()))
base_scene = bpy.context.scene
base_scene.frame_set(432)
bpy.context.view_layer.update()
root = bpy.data.objects["SECOND WIND | flight root"]
base_cameras = {o.name: {"object_keys": digest(animation(o)),
                       "data_keys": digest(animation(o.data)),
                       "lens": o.data.lens, "sensor_width": o.data.sensor_width,
                       "sensor_fit": o.data.sensor_fit, "type": o.data.type,
                       "basis": matrix(o.matrix_basis), "parent": o.parent.name if o.parent else None}
                for o in base_scene.objects if o.type == "CAMERA"}
base_meshes = {o.name: mesh_snapshot(o) for o in base_scene.objects
               if o.type == "MESH" and (descendant(o, root) or o.name.startswith("YARD_retaining_wedge_"))}
# build_forest explicitly disables the illustrative entry-cloud animation.
# Keep their mesh/parent checks, but report this authorized animation exception.
atmosphere_exceptions = [name for name in base_meshes if name.startswith("Cloud breakup proxy")]
sample_names = list(base_cameras)+[root.name]+[name for name in base_meshes if name not in atmosphere_exceptions]
base_root_keys = digest(animation(root))
sample_frames = (1,120,144,168,192,396,420,444,468,624,648,720,744,751,768,774,780)
base_samples = {}
for frame in sample_frames:
    base_scene.frame_set(frame)
    bpy.context.view_layer.update()
    base_samples[frame] = {name: matrix(bpy.data.objects[name].matrix_world)
                           for name in sample_names}

bpy.ops.wm.open_mainfile(filepath=str(args.scene.resolve()))
scene = bpy.context.scene
scene.frame_set(432)
bpy.context.view_layer.update()
root = bpy.data.objects.get("SECOND WIND | flight root")
require(root is not None, "Original flight root missing")
require(root is not None and digest(animation(root)) == base_root_keys, "Flight root animation differs")
camera_results = []
for name, original in base_cameras.items():
    ob = bpy.data.objects.get(name)
    if ob is None:
        errors.append("Missing original camera: " + name)
        continue
    current = {"object_keys": digest(animation(ob)), "data_keys": digest(animation(ob.data)),
               "lens": ob.data.lens, "sensor_width": ob.data.sensor_width,
               "sensor_fit": ob.data.sensor_fit, "type": ob.data.type,
               "basis": matrix(ob.matrix_basis), "parent": ob.parent.name if ob.parent else None}
    # matrix_basis depends on saved playhead; sampled transforms verify it below.
    changed = [key for key in original if key != "basis" and original[key] != current[key]]
    require(not changed, name + " changed: " + ", ".join(changed))
    camera_results.append({"name": name, "changed_fields": changed})
mesh_changes = []
for name, original in base_meshes.items():
    ob = bpy.data.objects.get(name)
    if ob is None:
        mesh_changes.append({"name": name, "changed_fields": ["missing"]})
        continue
    current = mesh_snapshot(ob)
    changed = [key for key in original if original[key] != current[key]
               and not (name in atmosphere_exceptions and key == "animation")]
    if changed:
        mesh_changes.append({"name": name, "changed_fields": changed})
require(not mesh_changes, "Ship/cabin/support descendant meshes or transforms changed")
max_transform_delta = 0.0
for frame in sample_frames:
    scene.frame_set(frame)
    bpy.context.view_layer.update()
    for name, original in base_samples[frame].items():
        ob = bpy.data.objects.get(name)
        if ob is None:
            continue
        delta = max(abs(ob.matrix_world[i][j]-original[i][j]) for i in range(4) for j in range(4))
        max_transform_delta = max(max_transform_delta, delta)
require(max_transform_delta < 1e-5, "Sampled original camera/root transforms differ")

textures = []
for image in bpy.data.images:
    if image.source != "FILE":
        continue
    resolved = Path(bpy.path.abspath(image.filepath, library=image.library)) if image.filepath else None
    exists = resolved is not None and resolved.is_file()
    textures.append({"image": image.name, "stored_path": image.filepath, "exists": exists,
                     "packed": bool(image.packed_file)})
    require(exists, "Unresolved FILE texture: " + image.name + " / " + image.filepath)

terrain = bpy.data.objects["Jangala | continuous landform"]
evaluated = terrain.evaluated_get(bpy.context.evaluated_depsgraph_get())
inverse = evaluated.matrix_world.inverted()
trees = [o for o in scene.objects if o.name.startswith(("Forest edge ", "Forest understory "))]
root_failures = []
max_root_error = 0.0
minimum_y = min((o.matrix_world.translation.y for o in trees), default=None)
for ob in trees:
    point = ob.matrix_world.translation
    start = inverse @ Vector((point.x, point.y, 10000))
    direction = (inverse.to_3x3() @ Vector((0,0,-1))).normalized()
    hit, p, _, _ = evaluated.ray_cast(start, direction, distance=30000)
    error = abs(point.z-(evaluated.matrix_world @ p).z) if hit else None
    if error is not None:
        max_root_error = max(max_root_error, error)
    if not hit or error > .05 or point.y < 470:
        root_failures.append({"name": ob.name, "ground_hit": hit,
                              "root_height_error_m": error, "root_y": point.y})
require(bool(trees), "No linked near-forest objects found")
require(not root_failures, "Forest roots unseated or root Y<470")
report = {"base_scene": "../animatic/"+args.base.name, "lookdev_scene": args.scene.name,
          "camera_comparison": camera_results, "sample_frames": sample_frames,
          "maximum_camera_root_matrix_delta": max_transform_delta,
          "ship_descendant_mesh_count": len(base_meshes), "ship_mesh_changes": mesh_changes,
          "authorized_atmosphere_animation_exceptions": atmosphere_exceptions,
          "file_textures": textures,
          "near_forest": {"count": len(trees), "minimum_root_y": minimum_y,
                          "maximum_ground_error_m": max_root_error, "failures": root_failures},
          "scope_limits": ["Far forest is a batched mesh; individual far roots are not independently verified.",
                           "Y>=470 verifies near root exclusion, not foliage crown clearance.",
                           "Mesh comparison excludes materials, which intentionally change in lookdev."],
          "passed": not errors, "errors": errors}
out = HERE / "review/verification.json"
out.parent.mkdir(exist_ok=True)
out.write_text(json.dumps(report, indent=2)+"\n", encoding="utf-8")
print("FOREST_VERIFICATION", "PASS" if not errors else "FAIL", str(out))
if errors:
    raise RuntimeError("; ".join(errors))
