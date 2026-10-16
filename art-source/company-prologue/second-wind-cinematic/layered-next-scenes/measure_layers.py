"""Read-only depth-aligned motion diagnostics; not artistic acceptance."""
import importlib.util
import json
import math
from pathlib import Path

import bpy
from bpy_extras.object_utils import world_to_camera_view
from mathutils import Vector

HERE = Path(__file__).resolve().parent


def frame(scene, value):
    scene.frame_set(int(value), subframe=value % 1)


def screen(scene, camera, point):
    p = world_to_camera_view(scene, camera, point)
    return {"pixel": [p.x * 1920, (1 - p.y) * 1080], "depth_camera_units": p.z,
            "inside_image": p.z > 0 and 0 <= p.x <= 1 and 0 <= p.y <= 1}


def main():
    manifest = json.loads((HERE / "scene-manifest.json").read_text(encoding="utf-8-sig"))
    spec = importlib.util.spec_from_file_location("projection_checks", HERE.parent / "painted-window/verify_projection.py")
    helper = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(helper)
    bpy.ops.wm.open_mainfile(filepath=str(HERE / manifest["scene"]))
    scene = bpy.context.scene
    scenery = bpy.data.objects[manifest["scenery_root"]]
    reports = []
    for shot in manifest["shots"]:
        if shot["id"] not in ("03", "04"):
            continue
        start = shot["source_start"]
        end = start + (shot["output_end"] - shot["output_start"]) * shot["source_step"]
        middle = (start + end) / 2
        camera = bpy.data.objects[shot["camera"]]
        frame(scene, middle)
        origin = camera.matrix_world.translation.copy()
        forward = (camera.matrix_world.to_3x3() @ Vector((0, 0, -1))).normalized()
        selections = [("near", "managed land near broken verge" if shot["id"] == "03" else "port district backdrop trees"),
                      ("middle", "middle woodland continuous belt"), ("far", "far forest belt"),
                      ("hills", "far asymmetric hills")]
        layers = []
        for role, suffix in selections:
            frame(scene, middle)
            obj = bpy.data.objects["LAYER BLOCK | " + suffix]
            mean_y = sum((obj.matrix_world @ v.co).y for v in obj.data.vertices) / len(obj.data.vertices)
            distance = (mean_y - origin.y) / forward.y
            aligned_world = origin + forward * distance
            source_point = scenery.matrix_world.inverted() @ aligned_world
            # Pick an actual crest vertex nearest image centre at the midpoint.
            candidates = []
            for vertex in obj.data.vertices:
                if vertex.index % 5 != 2:
                    continue
                point = obj.matrix_world @ vertex.co
                p = screen(scene, camera, point)
                score = math.hypot(p["pixel"][0] - 960, p["pixel"][1] - 540)
                if p["depth_camera_units"] > 0:
                    candidates.append((not p["inside_image"], score, vertex.index))
            selected = min(candidates)[2]
            layers.append({"role": role, "object": obj.name,
                           "mean_source_y_m": mean_y - scenery.location.y,
                           "aligned_source_world_point": list(source_point),
                           "midpoint_camera_depth_m": distance,
                           "actual_crest_vertex_index": selected, "aligned_samples": [], "crest_samples": []})
        for t in (start, middle, end):
            frame(scene, t)
            tree, owners = helper.visible_bvh(scene)
            eye = camera.matrix_world.translation
            for layer in layers:
                obj = bpy.data.objects[layer["object"]]
                point = scenery.matrix_world @ Vector(layer["aligned_source_world_point"])
                sample = screen(scene, camera, point)
                sample["source_frame"] = t
                layer["aligned_samples"].append(sample)
                point = obj.matrix_world @ obj.data.vertices[layer["actual_crest_vertex_index"]].co
                sample = screen(scene, camera, point)
                vector = point - eye
                hit, normal, index, distance = tree.ray_cast(eye, vector.normalized(), vector.length + 1)
                first = owners[index] if hit is not None else None
                sample.update({"source_frame": t, "first_hit_object": first,
                               "visible_surface": sample["inside_image"] and first == obj.name and
                                                  abs(distance - vector.length) < .5,
                               "status": "outside_image" if not sample["inside_image"] else
                                         "visible" if first == obj.name and abs(distance - vector.length) < .5 else "occluded"})
                layer["crest_samples"].append(sample)
        for layer in layers:
            a, b = layer["aligned_samples"][0]["pixel"], layer["aligned_samples"][-1]["pixel"]
            layer["aligned_start_to_end_displacement_px"] = math.hypot(b[0] - a[0], b[1] - a[1])
        ordered = all(a["aligned_start_to_end_displacement_px"] > b["aligned_start_to_end_displacement_px"]
                      for a, b in zip(layers, layers[1:]))
        reports.append({"shot": shot["id"], "camera": camera.name, "source_frames": [start, middle, end],
                        "source_fps": manifest["source_fps"], "near_to_far_motion_order_correct": ordered,
                        "layers": layers})
    result = {"scene": manifest["scene"], "paths_relative_to": "layered-next-scenes directory",
              "interior_shots": reports,
              "method": "At each shot midpoint intersect one common camera-centre ray with each selected ribbon's mean world-Y plane, then hold that source-world point fixed through scenery rebasing. This isolates depth from image registration. These hypothetical depth-plane points are not actual surface features and their screen travel is a mathematical diagnostic, not visible-art acceptance. Actual crest vertices are separately projected and first-hit raycast against render-visible meshes; offscreen/occluded results reported explicitly.",
              "exterior": {"05": "Ordering intentionally not asserted: source exterior camera pans; source camera preservation belongs to verify_scene.py.",
                           "06": "Ordering intentionally not asserted: fixed source camera relative yard; source camera preservation belongs to verify_scene.py."}}
    output = HERE / "review/layer-motion.json"
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    for shot in reports:
        print("LAYER_MOTION", shot["shot"], "ORDER", shot["near_to_far_motion_order_correct"],
              [(l["role"], round(l["aligned_start_to_end_displacement_px"], 3),
                [s["status"] for s in l["crest_samples"]]) for l in shot["layers"]], flush=True)


if __name__ == "__main__":
    main()
