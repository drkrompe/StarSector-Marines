"""Read-only validation of stabilized multilayer window scene; background Blender."""
import importlib.util
import json
import math
from pathlib import Path

import bpy
from mathutils import Vector

HERE = Path(__file__).resolve().parent
FRAMES = [120 + i * .5 for i in range(96)]
CAMERA = "02 REVEAL / near glass"
ROOT = "SECOND WIND | flight root"
LAYERS = ["near trees", "medium trees and river", "far trees", "distant hills"]


def load_module(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def setframe(scene, frame):
    scene.frame_set(int(frame), subframe=frame % 1)


def matrix_delta(a, b):
    return max(abs(a[i][j] - b[i][j]) for i in range(4) for j in range(4))


def main():
    motion = load_module("motion_diagnosis", HERE / "diagnose_motion.py")
    previous = load_module("projection_validation", HERE.parent / "painted-window/verify_projection.py")
    bpy.ops.wm.open_mainfile(filepath=str(HERE.parent / "painted-window/jangala-painted-window.blend"))
    scene = bpy.context.scene
    camera, root = bpy.data.objects[CAMERA], bpy.data.objects[ROOT]
    source_geometry = previous.geometry_hashes()
    source = []
    for frame in FRAMES:
        setframe(scene, frame)
        source.append({"position": root.matrix_world.translation.copy(),
                       "camera_local": camera.matrix_basis.copy(),
                       "rotation": root.rotation_euler.copy(), "scale": root.scale.copy()})
    scene.frame_set(144)
    reference_origin = root.matrix_world.translation.copy()
    features = []
    for obj in root.children_recursive:
        if obj.type != "MESH" or not (obj.name.startswith("Viewport") or obj.name == "Crew hand proxy"):
            continue
        transform = motion.local_to_root(camera, root).inverted() @ motion.local_to_root(obj, root)
        for vertex in obj.data.vertices:
            v = transform @ vertex.co
            if v.z < -.02:
                p = motion.pixel(v, camera)
                if 0 <= p[0] <= 1920 and 0 <= p[1] <= 1080:
                    features.append((obj.name, vertex.index))
    features = features[::max(1, len(features) // 80)]

    final = HERE / "jangala-parallax-window.blend"
    bpy.ops.wm.open_mainfile(filepath=str(final))
    scene = bpy.context.scene
    camera, root = bpy.data.objects[CAMERA], bpy.data.objects[ROOT]
    scenery = bpy.data.objects["PARALLAX | inverse source flight displacement"]
    errors = []
    geometry = previous.geometry_hashes()
    changed = sorted(name for name, value in source_geometry.items() if geometry.get(name) != value)
    if changed or len(geometry) != len(source_geometry):
        errors.append("Ship geometry changed")
    camera_delta, rotation_delta, scale_delta, root_position, inverse_delta = [], [], [], [], []
    residuals, tracks = [], []
    for frame, old in zip(FRAMES, source):
        setframe(scene, frame)
        camera_delta.append(matrix_delta(camera.matrix_basis, old["camera_local"]))
        rotation_delta.extend(abs(root.rotation_euler[i] - old["rotation"][i]) for i in range(3))
        scale_delta.extend(abs(root.scale[i] - old["scale"][i]) for i in range(3))
        root_position.append(root.location.length)
        inverse_delta.append((scenery.location - (reference_origin - old["position"])).length)
        world_inverse = camera.matrix_world.inverted()
        local_inverse = motion.local_to_root(camera, root).inverted()
        frame_tracks = []
        for name, index in features:
            obj = bpy.data.objects[name]
            co = obj.data.vertices[index].co
            world = motion.pixel(world_inverse @ (obj.matrix_world @ co), camera)
            local = motion.pixel(local_inverse @ (motion.local_to_root(obj, root) @ co), camera)
            residuals.extend(abs(world[i] - local[i]) for i in range(2))
            frame_tracks.append(world)
        tracks.append(frame_tracks)
    if max(camera_delta) > 1e-6 or max(rotation_delta) > 1e-6 or max(scale_delta) > 1e-6:
        errors.append("Camera local matrix or root rotation/scale changed")
    if max(root_position) > 1e-7:
        errors.append("Root is not stabilized at origin")
    if max(inverse_delta) > 1e-3:
        errors.append("Inverse scenery displacement differs from source flight by over1mm")
    if max(residuals) >= .1:
        errors.append("Cabin numeric precision residual exceeds0.1px")
    missing = []
    for image in bpy.data.images:
        if image.source == "FILE" and image.filepath and not image.packed_file:
            if not Path(bpy.path.abspath(image.filepath)).is_file():
                missing.append(image.filepath)
    if missing:
        errors.append("Unresolved texture paths")

    # Compare a common reference-image centre on each plane, independent of
    # painted mask registration. This measures physical parallax, not alpha.
    scene.frame_set(144)
    reference = bpy.data.objects["PAINT | fixed landscape projector"].matrix_world.copy()
    layer_reports = []
    for suffix in LAYERS:
        obj = bpy.data.objects["PARALLAX | " + suffix]
        declared = float(obj["depth_m"])
        point = reference @ Vector((0, 0, -declared)) - reference_origin
        local_point = obj.matrix_world.inverted() @ point
        plane_world = obj.matrix_world @ obj.data.vertices[0].co
        actual_depth = -(camera.matrix_world.normalized().inverted() @ plane_world).z
        positions = []
        for frame in (120, 168):
            scene.frame_set(frame)
            positions.append(motion.pixel(camera.matrix_world.inverted() @ (obj.matrix_world @ local_point), camera))
        movement = math.hypot(*(positions[1][i] - positions[0][i] for i in range(2)))
        layer_reports.append({"name": obj.name, "declared_depth_m": declared,
                              "actual_reference_depth_m": actual_depth,
                              "screen_positions_120_168": positions, "movement_px": movement})
        scene.frame_set(144)
    depth_order = all(a["actual_reference_depth_m"] < b["actual_reference_depth_m"]
                      for a, b in zip(layer_reports, layer_reports[1:]))
    movement_order = all(a["movement_px"] > b["movement_px"]
                         for a, b in zip(layer_reports, layer_reports[1:]))
    if not depth_order or not movement_order:
        errors.append("Depth/parallax ordering is not near>medium>far>hills")
    accel = [abs(tracks[k + 1][j][a] - 2 * tracks[k][j][a] + tracks[k - 1][j][a])
             for k in range(1, 95) for j in range(len(features)) for a in range(2)]
    report = {"passed": not errors, "errors": errors,
              "paths_relative_to": "parallax-window directory",
              "source": "../painted-window/jangala-painted-window.blend", "scene": final.name,
              "half_frame_samples": 96, "tested_frames": [120, 167.5],
              "ship_mesh_count": len(geometry), "changed_meshes": changed,
              "max_camera_local_matrix_delta": max(camera_delta),
              "max_root_rotation_delta": max(rotation_delta), "max_root_scale_delta": max(scale_delta),
              "max_root_location_m": max(root_position), "max_inverse_scenery_error_m": max(inverse_delta),
              "feature_count": len(features), "max_cabin_precision_residual_px": max(residuals),
              "rms_cabin_precision_residual_px": math.sqrt(sum(v*v for v in residuals)/len(residuals)),
              "max_cabin_screen_second_difference_px": max(accel),
              "missing_texture_paths": missing, "layer_depth_order_correct": depth_order,
              "layer_movement_order_correct": movement_order, "parallax_layers": layer_reports,
              "limits": "Numeric transform/geometry/asset checks; alpha gaps, matte edges and visual composition require rendered review. No blend saved."}
    target = HERE / "review/scene-validation.json"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print("SCENE_VALIDATION", json.dumps(report), flush=True)
    if errors:
        raise RuntimeError("; ".join(errors))


if __name__ == "__main__":
    main()
