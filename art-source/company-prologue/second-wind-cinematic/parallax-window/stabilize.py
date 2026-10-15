"""Rebase the derived window shot without changing the retained source blend.

Builder contract: create world cards at (source_world_position - origin_offset),
then parent them to returned scenery_root with identity parent inverse. Their
reference-frame positions are unchanged. Independent traffic animation belongs
on children beneath scenery_root. No handlers or application-global edits.
"""
import json
from pathlib import Path


def _curves(action):
    """Yield mutable curve collections for legacy and layered Blender actions."""
    if getattr(action, "is_action_layered", False):
        for layer in action.layers:
            for strip in layer.strips:
                for bag in getattr(strip, "channelbags", ()):
                    yield bag.fcurves
    elif hasattr(action, "fcurves"):
        yield action.fcurves


def stabilize(scene, root, reference_frame=144):
    """Return (scenery_root, origin_offset, report), baking source frames73..216.

    Ship rotation, scale, camera and camera-local animation remain intact.
    Source root translation is removed from a private copy of its action;
    the scenery receives its inverse displacement with linear source-frame keys.
    source_position_samples contain unmodified world translations in metres.
    """
    import bpy
    from mathutils import Vector

    if root.parent is not None:
        raise ValueError("Stabilization requires the top-level flight root")
    if root.constraints:
        raise ValueError("Constrained flight root needs explicit transform baking")
    if root.animation_data and any(d.data_path == "location" for d in root.animation_data.drivers):
        raise ValueError("Driven root translation cannot be stabilized by removing keys")
    if root.animation_data and any(not track.mute for track in root.animation_data.nla_tracks):
        raise ValueError("Active NLA tracks require explicit root translation isolation")
    old_frame, old_subframe = scene.frame_current, scene.frame_subframe
    samples, rotations, scales = [], [], []
    for frame in range(73, 217):
        scene.frame_set(frame)
        samples.append({"frame": frame, "position": list(root.matrix_world.translation)})
        rotations.append(tuple(root.rotation_euler))
        scales.append(tuple(root.scale))
    scene.frame_set(int(reference_frame), subframe=reference_frame % 1)
    origin = root.matrix_world.translation.copy()
    rotation_delta = max(abs(v[i] - rotations[0][i]) for v in rotations for i in range(3))
    scale_delta = max(abs(v[i] - scales[0][i]) for v in scales for i in range(3))

    animation = root.animation_data
    removed = 0
    if animation and animation.action:
        slot = getattr(animation, "action_slot", None)
        slot_identifier = slot.identifier if slot else None
        private_action = animation.action.copy()
        private_action.name = "PARALLAX | preserved source root rotation and scale"
        animation.action = private_action
        if slot_identifier:
            matching = next((s for s in private_action.slots if s.identifier == slot_identifier), None)
            if matching:
                animation.action_slot = matching
        for collection in _curves(private_action):
            for curve in list(collection):
                if curve.data_path == "location":
                    collection.remove(curve)
                    removed += 1
    root.location = (0, 0, 0)

    scenery = bpy.data.objects.new("PARALLAX | inverse source flight displacement", None)
    scene.collection.objects.link(scenery)
    scenery.empty_display_type = "PLAIN_AXES"
    for sample in samples:
        scenery.location = origin - Vector(sample["position"])
        scenery.keyframe_insert(data_path="location", frame=sample["frame"])
    if scenery.animation_data and scenery.animation_data.action:
        for collection in _curves(scenery.animation_data.action):
            for curve in collection:
                for key in curve.keyframe_points:
                    key.interpolation = "LINEAR"
    report = {"source_frame_range": [73, 216], "reference_frame": reference_frame,
              "source_fps": scene.render.fps / scene.render.fps_base,
              "origin_offset_world_m": list(origin), "source_position_samples": samples,
              "rotation_max_component_delta": rotation_delta,
              "scale_max_component_delta": scale_delta,
              "rotation_constant": rotation_delta < 1e-7, "scale_constant": scale_delta < 1e-7,
              "removed_root_translation_curves": removed,
              "root_rotation_scale_action_preserved": True,
              "scenery_interpolation": "LINEAR", "root_translation": [0, 0, 0],
              "coordinate_contract": "card coordinates = source world coordinates minus origin_offset; parent to scenery_root with identity parent inverse"}
    scenery["reference_frame"] = reference_frame
    scene.frame_set(old_frame, subframe=old_subframe)
    return scenery, origin, report


def _diagnose():
    """Disposable in-memory derivation; writes numeric report, never a blend."""
    import bpy
    import importlib.util
    import math

    here = Path(__file__).resolve().parent
    spec = importlib.util.spec_from_file_location("motion_diagnosis", here / "diagnose_motion.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    bpy.ops.wm.open_mainfile(filepath=str(here.parent / "painted-window/jangala-painted-window.blend"))
    scene = bpy.context.scene
    root = bpy.data.objects["SECOND WIND | flight root"]
    camera = bpy.data.objects["02 REVEAL / near glass"]
    scenery, origin, report = stabilize(scene, root)
    errors, accel, tracks = [], [], []
    features = []
    scene.frame_set(144)
    for obj in root.children_recursive:
        if obj.type != "MESH" or not (obj.name.startswith("Viewport") or obj.name == "Crew hand proxy"):
            continue
        transform = module.local_to_root(camera, root).inverted() @ module.local_to_root(obj, root)
        for vertex in obj.data.vertices:
            v = transform @ vertex.co
            if v.z < -.02:
                p = module.pixel(v, camera)
                if 0 <= p[0] <= 1920 and 0 <= p[1] <= 1080:
                    features.append((obj, vertex.index))
    features = features[::max(1, len(features) // 80)]
    for k in range(96):
        f = 120 + k / 2
        scene.frame_set(int(f), subframe=f % 1)
        inverse = camera.matrix_world.inverted()
        local_inverse = module.local_to_root(camera, root).inverted()
        frame_tracks = []
        for obj, index in features:
            co = obj.data.vertices[index].co
            world = module.pixel(inverse @ (obj.matrix_world @ co), camera)
            local = module.pixel(local_inverse @ (module.local_to_root(obj, root) @ co), camera)
            errors.extend(abs(world[i] - local[i]) for i in range(2))
            frame_tracks.append(world)
        tracks.append(frame_tracks)
    for k in range(1, 95):
        for j in range(len(features)):
            for axis in range(2):
                accel.append(abs(tracks[k + 1][j][axis] - 2 * tracks[k][j][axis] + tracks[k - 1][j][axis]))
    report.update({"feature_count": len(features), "tested_half_frames": 96,
                   "postfix_max_world_vs_ship_local_projection_error_px": max(errors),
                   "postfix_rms_world_vs_ship_local_projection_error_px": math.sqrt(sum(v*v for v in errors)/len(errors)),
                   "postfix_max_world_projection_second_difference_px": max(accel),
                   "source_blend_modified": False})
    target = here / "review/jitter-diagnosis-stabilized.json"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(report, indent=2), encoding="utf-8")
    print("STABILIZED_DIAGNOSIS", json.dumps({k:v for k,v in report.items() if k != "source_position_samples"}), flush=True)


if __name__ == "__main__":
    _diagnose()
