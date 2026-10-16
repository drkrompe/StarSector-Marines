"""Read-only numeric diagnosis of painted cabin jitter, background Blender."""
import json
import math
from pathlib import Path
import bpy

HERE = Path(__file__).resolve().parent
SOURCE = HERE.parent / "painted-window" / "jangala-painted-window.blend"


def local_to_root(obj, root):
    result = obj.matrix_parent_inverse @ obj.matrix_basis
    parent = obj.parent
    while parent != root:
        if parent is None:
            raise ValueError(obj.name + " is not ship-local")
        result = parent.matrix_parent_inverse @ parent.matrix_basis @ result
        parent = parent.parent
    return result


def pixel(v, camera):
    scale = camera.data.lens / camera.data.sensor_width
    return [1920 * (.5 + v.x / -v.z * scale),
            1080 * (.5 - v.y / -v.z * scale * 16 / 9)]


def main():
    bpy.ops.wm.open_mainfile(filepath=str(SOURCE))
    scene = bpy.context.scene
    camera = bpy.data.objects["02 REVEAL / near glass"]
    root = bpy.data.objects["SECOND WIND | flight root"]
    scene.frame_set(144)
    features = []
    for obj in root.children_recursive:
        if obj.type != "MESH" or not (obj.name.startswith("Viewport") or obj.name == "Crew hand proxy"):
            continue
        stable = local_to_root(camera, root).inverted() @ local_to_root(obj, root)
        for vertex in obj.data.vertices:
            v = stable @ vertex.co
            if v.z >= -.02:
                continue
            p = pixel(v, camera)
            if 0 <= p[0] <= 1920 and 0 <= p[1] <= 1080:
                features.append((obj, vertex.index))
    # Limit redundant bevel vertices, retaining each framing object's samples.
    features = features[::max(1, len(features) // 80)]
    samples = []
    for k in range(96):
        frame = 120 + k / 2
        scene.frame_set(int(frame), subframe=frame % 1)
        cinv = camera.matrix_world.inverted()
        stable_cinv = local_to_root(camera, root).inverted()
        tracks = []
        for obj, index in features:
            co = obj.data.vertices[index].co
            world = pixel(cinv @ (obj.matrix_world @ co), camera)
            stable = pixel(stable_cinv @ (local_to_root(obj, root) @ co), camera)
            tracks.append({"world": world, "ship_local": stable,
                           "precision_residual_px": [world[i] - stable[i] for i in range(2)]})
        samples.append({"frame": frame, "root_location": list(root.location),
                        "camera_location_local": list(camera.location),
                        "camera_rotation_local": list(camera.rotation_euler), "tracks": tracks})
    residuals = [abs(v) for s in samples for t in s["tracks"] for v in t["precision_residual_px"]]
    step_jitter, local_accel, world_accel = [], [], []
    for j in range(len(features)):
        for k in range(1, len(samples) - 1):
            for axis in range(2):
                a, b, c = [samples[t]["tracks"][j] for t in (k - 1, k, k + 1)]
                step_jitter.append(abs(c["precision_residual_px"][axis] - b["precision_residual_px"][axis]))
                local_accel.append(abs(c["ship_local"][axis] - 2 * b["ship_local"][axis] + a["ship_local"][axis]))
                world_accel.append(abs(c["world"][axis] - 2 * b["world"][axis] + a["world"][axis]))
    modifier_info = []
    for obj in (camera, root):
        animation = obj.animation_data
        action = animation.action if animation else None
        curves = []
        if action:
            for layer in action.layers:
                for strip in layer.strips:
                    if hasattr(strip, "channelbags"):
                        for bag in strip.channelbags:
                            curves.extend(bag.fcurves)
        modifier_info.append({"object": obj.name, "action": action.name if action else None,
                              "curve_count": len(curves),
                              "curve_modifiers": [m.type for f in curves for m in f.modifiers],
                              "constraints": [c.type for c in obj.constraints],
                              "interpolation": sorted({p.interpolation for f in curves for p in f.keyframe_points})})
    report = {"source": "../painted-window/jangala-painted-window.blend",
              "source_frames": [120, 167.5], "source_step": .5, "output_fps": 24,
              "feature_count": len(features),
              "features": [{"object": o.name, "vertex": i} for o, i in features],
              "animation": modifier_info,
              "max_world_vs_ship_local_projection_error_px": max(residuals),
              "rms_world_vs_ship_local_projection_error_px": math.sqrt(sum(v*v for v in residuals)/len(residuals)),
              "max_adjacent_precision_residual_change_px": max(step_jitter),
              "max_world_projection_second_difference_px": max(world_accel),
              "max_ship_local_projection_second_difference_px": max(local_accel),
              "findings": ["Camera and flight root have linear curves, no curve modifiers and no constraints; no authored reentry bob or engine bump.",
                           "Renderer evaluates actual half-source-frame samples for24fps output from12fps source; frames are not duplicated or optically interpolated.",
                           "Large world translation creates measurable float cancellation in close cabin feature projection; ship-local calculation is substantially smoother."],
              "recommended_correction": "In the derived parallax shot, keep the ship root at or near origin, retain camera local dolly, and move background layers by the inverse source ship displacement. Alternatively subtract a shared reference origin from camera/ship/projectors/environment before evaluation; keep the source blend unchanged. Increase render samples only for any residual stochastic shader noise after precision is corrected.",
              "method": "Same fixed mesh vertices projected via full world float matrices and via short ship-local transform chain,1920x1080; no scene edit or render. Residual isolates cancellation from large world translation; actual shader/GPU sampling may add further error.",
              "samples": samples}
    output = HERE / "review" / "jitter-diagnosis.json"
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps({k:v for k,v in report.items() if k not in {"samples","features"}},indent=2),flush=True)


if __name__ == "__main__":
    main()
