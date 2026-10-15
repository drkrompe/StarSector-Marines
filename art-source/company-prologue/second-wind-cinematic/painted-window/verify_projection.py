"""Read-only background validation of the retained painted-window projection."""
import hashlib
import json
import os
from pathlib import Path
import struct

import bpy
from mathutils import Vector
from mathutils.bvhtree import BVHTree

HERE = Path(__file__).resolve().parent
FRAMES = (120, 132, 144, 156, 168)
CAMERA = "02 REVEAL / near glass"
ROOT = "SECOND WIND | flight root"
LAND = {"Jangala | continuous landform", "Jangala | winding river",
        "Jangala | far estuary", "Jangala | distant folded ridgeline",
        "PAINT | distant sky backing", "PAINT | far ground continuation"}


def shader_projection(projector, point):
    """Match build_painted's per-pixel perspective divide exactly."""
    local = projector.matrix_world.inverted() @ point
    depth = -local.z
    if abs(depth) < 1e-9:
        return (1e30, 1e30, depth)
    scale = projector.data.lens / projector.data.sensor_width
    return (.5 + local.x / depth * scale,
            .5 + local.y / depth * scale * 16 / 9, depth)


def bounds_report(hits):
    outside = [h for h in hits if h["depth"] <= 0 or
               any(v < -.005 or v > 1.005 for v in h["uv"])]
    return {"hits": len(hits),
            "uv_min": [min((h["uv"][i] for h in hits), default=None) for i in range(2)],
            "uv_max": [max((h["uv"][i] for h in hits), default=None) for i in range(2)],
            "behind_projector": sum(h["depth"] <= 0 for h in hits),
            "outside_tolerance": .005, "outside_count": len(outside),
            "outside_samples": outside, "passed": bool(hits) and not outside}


def rows(value):
    return [list(row) for row in value]


def matrices(scene):
    result = {}
    for frame in FRAMES:
        scene.frame_set(frame)
        graph = bpy.context.evaluated_depsgraph_get()
        result[frame] = {name: rows(bpy.data.objects[name].evaluated_get(graph).matrix_world)
                         for name in (CAMERA, ROOT)}
    return result


def geometry_hashes():
    result = {}
    for obj in bpy.data.objects[ROOT].children_recursive:
        if obj.type != "MESH":
            continue
        digest = hashlib.sha256()
        for vertex in obj.data.vertices:
            digest.update(struct.pack("<3f", *vertex.co))
        for polygon in obj.data.polygons:
            digest.update(struct.pack("<I", len(polygon.vertices)))
            for index in polygon.vertices:
                digest.update(struct.pack("<I", index))
        result[obj.name] = {"sha256": digest.hexdigest(),
                            "vertices": len(obj.data.vertices), "polygons": len(obj.data.polygons)}
    return result


def visible_bvh(scene):
    """Use render visibility explicitly; scene.ray_cast also hits hidden meshes."""
    graph = bpy.context.evaluated_depsgraph_get()
    vertices, polygons, owners = [], [], []
    for obj in scene.objects:
        if obj.type != "MESH" or obj.hide_render:
            continue
        evaluated = obj.evaluated_get(graph)
        data = evaluated.to_mesh()
        offset = len(vertices)
        transform = evaluated.matrix_world
        vertices.extend(transform @ v.co for v in data.vertices)
        polygons.extend(tuple(offset + i for i in p.vertices) for p in data.polygons)
        owners.extend([obj.name] * len(data.polygons))
        evaluated.to_mesh_clear()
    return BVHTree.FromPolygons(vertices, polygons, all_triangles=False), owners


def coverage(scene, frame):
    scene.frame_set(frame)
    graph = bpy.context.evaluated_depsgraph_get()
    camera = bpy.data.objects[CAMERA].evaluated_get(graph)
    projector = bpy.data.objects["PAINT | fixed landscape projector"]
    cabin_projector = bpy.data.objects["PAINT | ship-local cabin projector"]
    ship_names = {o.name for o in bpy.data.objects[ROOT].children_recursive if o.type == "MESH"}
    corners = camera.data.view_frame(scene=scene)
    xmin, xmax = min(v.x for v in corners), max(v.x for v in corners)
    ymin, ymax = min(v.y for v in corners), max(v.y for v in corners)
    z = corners[0].z
    origin = camera.matrix_world.translation
    orientation = camera.matrix_world.to_3x3()
    tree, owners = visible_bvh(scene)
    hits, cabin_hits, skipped, missed = [], [], 0, 0
    counts = {}
    # Half-cell offset samples the whole image, including its edge strips.
    for py in range(32, 1080, 64):
        for px in range(32, 1920, 64):
            direction = (orientation @ Vector((xmin + (xmax - xmin) * px / 1920,
                                               ymax - (ymax - ymin) * py / 1080, z))).normalized()
            point, normal, index, distance = tree.ray_cast(origin, direction, 60000)
            if point is None:
                missed += 1
                continue
            name = owners[index]
            counts[name] = counts.get(name, 0) + 1
            if name not in LAND:
                skipped += 1
                if name in ship_names:
                    u, v, depth = shader_projection(cabin_projector, point)
                    cabin_hits.append({"pixel": [px, py], "object": name,
                                       "uv": [u, v], "depth": depth, "distance_m": distance})
                continue
            u, v, depth = shader_projection(projector, point)
            record = {"pixel": [px, py], "object": name, "uv": [u, v],
                      "depth": depth, "distance_m": distance}
            hits.append(record)
    land_report = bounds_report(hits)
    cabin_report = bounds_report(cabin_hits)
    return {"frame": frame, "land_hits": len(hits), "cabin_or_other_hits": skipped,
            "missed": missed, "first_hit_counts": counts,
            "uv_min": land_report["uv_min"], "uv_max": land_report["uv_max"],
            "outside_tolerance": .005, "outside_count": land_report["outside_count"],
            "outside_samples": land_report["outside_samples"],
            "landscape_projection": land_report, "cabin_projection": cabin_report,
            "passed": land_report["passed"] and cabin_report["passed"] and missed == 0}


def main():
    source = HERE.parent / "animatic" / "jangala-blockout.blend"
    final = HERE / "jangala-painted-window.blend"
    if not final.is_file():
        raise FileNotFoundError("Build final painted scene before running verification: " + str(final))
    bpy.ops.wm.open_mainfile(filepath=str(source))
    original_matrices = matrices(bpy.context.scene)
    original_geometry = geometry_hashes()
    bpy.ops.wm.open_mainfile(filepath=str(final))
    scene = bpy.context.scene
    final_matrices = matrices(scene)
    final_geometry = geometry_hashes()
    drift, errors = [], []
    for frame in FRAMES:
        for name in (CAMERA, ROOT):
            delta = max(abs(original_matrices[frame][name][i][j] - final_matrices[frame][name][i][j])
                        for i in range(4) for j in range(4))
            drift.append({"frame": frame, "object": name, "max_matrix_delta": delta})
            if delta > .0001:
                errors.append("Camera/root drift at frame %d: %s" % (frame, name))
    changed = [name for name, value in original_geometry.items() if final_geometry.get(name) != value]
    added = sorted(set(final_geometry) - set(original_geometry))
    if changed or added:
        errors.append("Original ship mesh geometry changed")
    missing_textures = []
    for image in bpy.data.images:
        if image.source == "FILE" and image.filepath and not image.packed_file:
            resolved = Path(bpy.path.abspath(image.filepath))
            if not resolved.is_file():
                missing_textures.append(Path(os.path.relpath(resolved, HERE)).as_posix())
    if missing_textures:
        errors.append("Unresolved file textures")
    samples = [coverage(scene, frame) for frame in FRAMES]
    for sample in samples:
        if not sample["passed"]:
            errors.append("Projection coverage failed at frame %d (land=%s,cabin=%s,misses=%d)" %
                          (sample["frame"], sample["landscape_projection"]["passed"],
                           sample["cabin_projection"]["passed"], sample["missed"]))
    scene.frame_set(144)
    projector = bpy.data.objects["PAINT | fixed landscape projector"]
    report = {"passed": not errors, "errors": errors,
              "paths_relative_to": "painted-window directory",
              "source": "../animatic/jangala-blockout.blend",
              "painted_scene": "jangala-painted-window.blend",
              "matrix_comparison": drift, "source_matrices": original_matrices,
              "painted_matrices": final_matrices,
              "ship_geometry": {"source_mesh_count": len(original_geometry),
                                "painted_mesh_count": len(final_geometry),
                                "changed_or_missing": changed, "added": added,
                                "source_hashes": original_geometry},
              "missing_textures": missing_textures,
              "projector_frame144": {"matrix_world": rows(projector.matrix_world),
                                      "lens_mm": projector.data.lens},
              "coverage": samples,
              "validated_sample_frames": [s["frame"] for s in samples if s["passed"]],
              "method": "64px grid first-hit raycast of evaluated render-visible meshes; landscape and visible ship hits checked against their respective per-pixel material perspective divide, including depth sign; world-normalized rays,60000m limit; UV tolerance0.005. Misses fail. Discrete samples do not prove every pixel/frame."}
    output = HERE / "review" / "projection-validation.json"
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print("PROJECTION_VALIDATION", output, "PASS" if not errors else "FAIL", flush=True)
    if errors:
        raise RuntimeError("; ".join(errors))


if __name__ == "__main__":
    main()
