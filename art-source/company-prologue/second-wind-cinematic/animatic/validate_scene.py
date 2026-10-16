"""Read-only saved-scene evidence. Run Blender --background <blend> --python this.py.

Writes JSON and two review JPEGs; never rebuilds or saves the loaded blend.
"""
from pathlib import Path
import json
import bpy
from mathutils import Vector

HERE = Path(__file__).resolve().parent
OUT = HERE / "review"
OUT.mkdir(exist_ok=True)
scene = bpy.context.scene
root = bpy.data.objects.get("SECOND WIND | flight root")
if root is None:
    raise RuntimeError("Load jangala-blockout.blend before running this script")
errors = []
report = {"source_blend": Path(bpy.data.filepath).name, "frames": [], "checks": {}}
TOL = 0.025

def require(ok, message):
    if not ok:
        errors.append(message)

def objects(prefix):
    return sorted((o for o in scene.objects if o.name.startswith(prefix)), key=lambda o: o.name)

def bounds(obj):
    evaluated = obj.evaluated_get(bpy.context.evaluated_depsgraph_get())
    mesh = evaluated.to_mesh()
    try:
        points = [evaluated.matrix_world @ v.co for v in mesh.vertices]
        return [[min(p[i] for p in points), max(p[i] for p in points)] for i in range(3)]
    finally:
        evaluated.to_mesh_clear()

def overlap_xy(a, b):
    return all(min(a[i][1], b[i][1]) - max(a[i][0], b[i][0]) > TOL for i in (0, 1))

shoes = objects("PROPOSAL keel shoe")
pads = objects("YARD_contact_pad_")
clamps = objects("YARD_retaining_wedge_")
lifts = objects("PROPOSAL ventral lift collar")
for label, group in (("shoes", shoes), ("pads", pads), ("clamps", clamps), ("lifts", lifts)):
    require(len(group) == 4, f"Expected four {label}; got {len(group)}")

for frame in (624, 648, 720, 744, 751, 768, 774, 780):
    scene.frame_set(frame)
    bpy.context.view_layer.update()
    evaluated = root.evaluated_get(bpy.context.evaluated_depsgraph_get())
    location, rotation, scale = evaluated.matrix_world.decompose()
    level_error = (rotation @ Vector((0, 0, 1)) - Vector((0, 0, 1))).length
    require(abs(location.x) < TOL and abs(location.y) < TOL, f"Frame {frame}: landing XY not zero")
    require(level_error < 1e-5, f"Frame {frame}: hull is not level")
    entry = {"frame": frame, "root_xyz": list(location), "level_error": level_error,
             "contacts": [], "clamps": []}
    for shoe in shoes:
        sb = bounds(shoe)
        pad = min(pads, key=lambda p: (p.matrix_world.translation.x - (sb[0][0]+sb[0][1])/2)**2
                  + (p.matrix_world.translation.y - (sb[1][0]+sb[1][1])/2)**2)
        pb = bounds(pad)
        gap = sb[2][0] - pb[2][1]
        entry["contacts"].append({"shoe": shoe.name, "pad": pad.name,
                                  "shoe_bottom_z": sb[2][0], "pad_top_z": pb[2][1], "gap_m": gap})
        require(gap >= -TOL, f"Frame {frame}: {shoe.name} penetrates pad")
        if frame >= 744:
            require(abs(gap) < TOL and abs(pb[2][1]-5) < TOL,
                    f"Frame {frame}: {shoe.name} does not contact pad at Z=5")
            require(all(sb[i][0] >= pb[i][0]-TOL and sb[i][1] <= pb[i][1]+TOL for i in (0,1)),
                    f"Frame {frame}: shoe footprint outside pad")
    for clamp in clamps:
        cb = bounds(clamp)
        inner = min(abs(cb[1][0]), abs(cb[1][1]))
        nearest_shoe = min(shoes, key=lambda shoe: sum(((bounds(shoe)[i][0]+bounds(shoe)[i][1])/2
                          -(cb[i][0]+cb[i][1])/2)**2 for i in (0,1)))
        shoe_bounds = bounds(nearest_shoe)
        shoe_outer = max(abs(shoe_bounds[1][0]), abs(shoe_bounds[1][1]))
        entry["clamps"].append({"name": clamp.name, "inner_abs_y": inner,
                                 "shoe": nearest_shoe.name, "shoe_outer_abs_y": shoe_outer,
                                 "lateral_clearance_m": inner-shoe_outer})
        require(inner >= 18-TOL, f"Frame {frame}: clamp penetrates shoe edge")
        if frame >= 774:
            require(abs(inner-18) < TOL, f"Frame {frame}: clamp does not meet shoe edge Y=18")
            require(abs(inner-shoe_outer) < TOL, f"Frame {frame}: clamp does not meet actual shoe edge")
    for support in pads + shoes:
        for lift in lifts:
            require(not overlap_xy(bounds(support), bounds(lift)),
                    f"Frame {frame}: support/lift footprint overlap: {support.name}, {lift.name}")
    report["frames"].append(entry)

# Compare actual source vertices in ship-local coordinates with retained V3 mesh.
baseline_path = HERE.parent.parent / "second-wind-geometry" / "second-wind-geometry.blend"
engine_prefixes = tuple(f"Engine {i} " for i in range(1,5))
engine_objects = [o for o in scene.objects if o.type == "MESH" and o.name.startswith(engine_prefixes)]
with bpy.data.libraries.load(str(baseline_path), link=False) as (available, loaded):
    source_names = tuple(available.objects)
    names = tuple(n for n in source_names if n.startswith(engine_prefixes))
    loaded.objects = list(names)
originals = [o for o in scene.objects if o.type == "MESH" and o.name in source_names]
def combined_bounds(group):
    boxes = [bounds(o) for o in group]
    return [[min(b[i][0] for b in boxes), max(b[i][1] for b in boxes)] for i in range(3)]
for label, group in (("original_ship", originals), ("main_hull", [o for o in originals if o.name.startswith("Main hull")])):
    box = combined_bounds(group)
    report["checks"][label] = {"mesh_count": len(group), "world_bounds_m": box,
                               "dimensions_m": [hi-lo for lo,hi in box]}
require(len(originals) == 135, f"Expected 135 original ship meshes; got {len(originals)}")
engine_checks = []
for name, baseline in zip(names, loaded.objects):
    current = next((o for o in engine_objects if o.name == name), None)
    require(current is not None, f"Missing original engine object {name}")
    if current is None:
        continue
    equal_count = len(current.data.vertices) == len(baseline.data.vertices)
    require(equal_count, f"Engine vertex count changed: {name}")
    transform = root.matrix_world.inverted() @ current.matrix_world
    # Library-only objects are unlinked and have no evaluated world matrix;
    # retained V3 engine objects are unparented, so basis owns their transform.
    require(baseline.parent is None, f"Unexpected parent on V3 engine {name}")
    delta = max(((transform @ a.co) - (baseline.matrix_basis @ b.co)).length
                for a,b in zip(current.data.vertices, baseline.data.vertices)) if equal_count else None
    engine_checks.append({"name": name, "maximum_local_vertex_delta": delta})
    require(delta is not None and delta < 1e-4, f"Original engine geometry/placement changed: {name}")
require(len(engine_checks) >= 4, "Could not verify four original aft engines")
report["checks"]["original_engine_geometry"] = engine_checks
for baseline in loaded.objects:
    if baseline is not None:
        bpy.data.objects.remove(baseline, do_unlink=True)

# Temporary review cameras and visibility changes affect only this background process.
scene.frame_set(780)
# Timeline-bound cameras otherwise override the explicit review camera at render.
for marker in list(scene.timeline_markers):
    scene.timeline_markers.remove(marker)
scene.render.resolution_x = 1920
scene.render.resolution_y = 1080
scene.render.resolution_percentage = 100
scene.render.image_settings.file_format = "JPEG"
scene.render.image_settings.quality = 92
for obj in scene.objects:
    if obj.name.startswith(("YARD_shed", "YARD_crane", "Port district", "Port communications", "Port tower")):
        obj.hide_render = True

def render_review(name, position, target, ortho, hide_ship=False):
    if hide_ship:
        for obj in scene.objects:
            if obj.parent == root:
                obj.hide_render = True
    data = bpy.data.cameras.new(name)
    cam = bpy.data.objects.new(name, data)
    scene.collection.objects.link(cam)
    cam.location = position
    cam.rotation_euler = (Vector(target)-cam.location).to_track_quat("-Z", "Y").to_euler()
    data.type = "ORTHO"
    data.ortho_scale = ortho
    data.clip_end = 10000
    scene.camera = cam
    scene.render.filepath = str(OUT / (name + ".jpg"))
    bpy.ops.render.render(write_still=True)

report["review_render_notes"] = "Separate orthographic evidence cameras; frontage hidden for clearance review; ship hidden in yard plan. Loaded blend is never saved."
report["errors"] = errors
report["passed"] = not errors
(OUT / "geometry-review.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
render_review("contact-detail", (-31.5, 110, 8), (-31.5, 15, 5), 27)
render_review("yard-top-plan", (10, 0, 400), (10, 0, 0), 270, hide_ship=True)
print("GEOMETRY_REVIEW", "PASS" if not errors else "FAIL", str(OUT / "geometry-review.json"))
if errors:
    raise RuntimeError("; ".join(errors))
