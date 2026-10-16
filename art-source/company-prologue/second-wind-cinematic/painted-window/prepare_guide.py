"""Export retained projection guides in a factory-startup background Blender."""
import json
from pathlib import Path

import bpy
import numpy as np


HERE = Path(__file__).resolve().parent
OUT = HERE / "guides"
SOURCE = HERE.parent / "forest-lookdev" / "jangala-forest-lookdev.blend"


def matrix(value):
    return [list(row) for row in value]


def camera_info(scene, camera):
    graph = bpy.context.evaluated_depsgraph_get()
    evaluated = camera.evaluated_get(graph)
    projection = evaluated.calc_matrix_camera(graph, x=1920, y=1080)
    return {"name": camera.name, "matrix_world": matrix(evaluated.matrix_world),
            "world_to_camera": matrix(evaluated.matrix_world.inverted()),
            "projection_matrix": matrix(projection), "lens_mm": camera.data.lens,
            "sensor_width_mm": camera.data.sensor_width,
            "sensor_height_mm": camera.data.sensor_height,
            "sensor_fit": camera.data.sensor_fit,
            "clip_start": camera.data.clip_start, "clip_end": camera.data.clip_end,
            "intrinsics_pixels": {"fx": 960 * projection[0][0],
                                  "fy": 540 * projection[1][1],
                                  "cx": 960 * (1 - projection[0][2]),
                                  "cy": 540 * (1 + projection[1][2])},
            "convention": "Blender camera local -Z forward,+Y up; pixel origin top-left"}


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    bpy.ops.wm.open_mainfile(filepath=str(SOURCE))
    scene = bpy.context.scene
    scene.frame_set(144)
    original = next(o for o in scene.objects if o.type == "CAMERA" and o.name.startswith("02 REVEAL"))
    # Blender resolves marker camera bindings during rendering, after
    # render_pre. Preserve marker timing while disabling those overrides.
    for marker in scene.timeline_markers:
        marker.camera = None
    scene.camera = original
    scene.render.resolution_x, scene.render.resolution_y = 1920, 1080
    scene.render.resolution_percentage = 100
    scene.render.image_settings.file_format = "PNG"
    scene.render.image_settings.color_mode = "RGBA"
    scene.render.film_transparent = False
    if hasattr(scene, "eevee"):
        scene.eevee.taa_render_samples = 32
    original_info = camera_info(scene, original)
    projection = bpy.data.objects.new("CAM painted projection | immutable frame 144 overscan", original.data.copy())
    scene.collection.objects.link(projection)
    projection.matrix_world = original.evaluated_get(bpy.context.evaluated_depsgraph_get()).matrix_world.copy()
    projection.data.lens /= 1.35
    bpy.context.view_layer.update()
    projection_info = camera_info(scene, projection)
    shiproot = bpy.data.objects.get("SECOND WIND | flight root")
    ship = set(shiproot.children_recursive) | {shiproot}
    hidden = {o: o.hide_render for o in scene.objects}

    def render(name):
        scene.render.filepath = str(OUT / name)
        bpy.ops.render.render(write_still=True)
        print("GUIDE_READY", name, flush=True)

    for obj in ship:
        if obj.type not in {"CAMERA", "LIGHT"}:
            obj.hide_render = True
    scene.camera = projection
    # landscape-guide.png is the immutable 26 mm legacy guide used for v1 art.
    render("landscape-guide-overscan-corrected.png")
    for obj, value in hidden.items():
        obj.hide_render = value
    scene.camera = original
    render("window-guide.png")

    # An emission-only cabin silhouette against white world is independent of
    # lighting and texture alpha. Binarize after antialiasing for an exact mask.
    old_world = scene.world
    mask_world = bpy.data.worlds.new("GUIDE mask white world")
    mask_world.use_nodes = True
    mask_world.node_tree.nodes.get("Background").inputs["Color"].default_value = (1, 1, 1, 1)
    mask_world.node_tree.nodes.get("Background").inputs["Strength"].default_value = 1
    scene.world = mask_world
    black = bpy.data.materials.new("GUIDE mask opaque black cabin")
    black.use_nodes = True
    nodes = black.node_tree.nodes
    nodes.clear()
    output = nodes.new("ShaderNodeOutputMaterial")
    emission = nodes.new("ShaderNodeEmission")
    emission.inputs["Color"].default_value = (0, 0, 0, 1)
    black.node_tree.links.new(emission.outputs[0], output.inputs["Surface"])
    old_override = scene.view_layers[0].material_override
    scene.view_layers[0].material_override = black
    old_transform = scene.view_settings.view_transform
    old_exposure = scene.view_settings.exposure
    scene.view_settings.view_transform = "Standard"
    scene.view_settings.exposure = 0
    for obj in scene.objects:
        if obj.type not in {"CAMERA", "LIGHT"}:
            obj.hide_render = hidden.get(obj, False) or obj not in ship
    render("opening-mask.png")
    mask = bpy.data.images.load(str(OUT / "opening-mask.png"), check_existing=False)
    pixels = np.empty(1920 * 1080 * 4, dtype=np.float32)
    mask.pixels.foreach_get(pixels)
    pixels = pixels.reshape((-1, 4))
    pixels[:, :3] = (pixels[:, :1] > .5).astype(np.float32)
    pixels[:, 3] = 1
    mask.pixels.foreach_set(pixels.ravel())
    mask.filepath_raw = str(OUT / "opening-mask.png")
    mask.file_format = "PNG"
    mask.save()
    scene.world = old_world
    scene.view_layers[0].material_override = old_override
    scene.view_settings.view_transform = old_transform
    scene.view_settings.exposure = old_exposure
    for obj, value in hidden.items():
        obj.hide_render = value
    scene.camera = original

    samples = []
    for frame in (120, 132, 144, 156, 168):
        scene.frame_set(frame)
        info = camera_info(scene, original)
        info["frame"] = frame
        info["delta_translation_from_144"] = [info["matrix_world"][i][3] - original_info["matrix_world"][i][3]
                                                for i in range(3)]
        samples.append(info)
    scene.frame_set(144)
    geometry = []
    for obj in scene.objects:
        if obj.type == "MESH" and (obj.name.startswith("Jangala |") or "river" in obj.name.lower()):
            geometry.append({"name": obj.name, "vertices": len(obj.data.vertices),
                             "polygons": len(obj.data.polygons), "hide_render": obj.hide_render,
                             "matrix_world": matrix(obj.matrix_world),
                             "local_bounds": [list(v) for v in obj.bound_box]})
    manifest = {"source_blend": "../../forest-lookdev/jangala-forest-lookdev.blend",
                "paths_relative_to": "this manifest's guides directory",
                "reference_frame": 144,
                "resolution": [1920, 1080], "overscan_factor": 1.35,
                "original_camera": original_info, "overscan_camera": projection_info,
                "current_landscape_guide": {"path": "landscape-guide-overscan-corrected.png",
                                            "actual_lens_mm": projection.data.lens,
                                            "camera": projection.name,
                                            "marker_camera_bindings_cleared": True},
                "legacy_landscape_guide": {"path": "landscape-guide.png",
                                           "actual_lens_mm": 26.0,
                                           "intended_lens_mm": 26.0 / 1.35,
                                           "immutable": True,
                                           "artwork_dependency": "../assets/landscape-painted-v1.png",
                                           "reason": "Timeline camera bindings overrode overscan during original rendering."},
                "source_camera_samples": samples, "suggested_range": [120, 168],
                "conservative_range": [132, 156], "terrain_geometry": geometry,
                "ship_root": shiproot.name, "ship_descendants": len(ship) - 1,
                "mask": {"path": "opening-mask.png", "white": "opening", "black": "opaque cabin",
                         "binary_threshold": .5},
                "outputs": ["landscape-guide-overscan-corrected.png", "window-guide.png", "opening-mask.png", "projection-base.blend"]}
    (OUT / "manifest.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    bpy.ops.wm.save_as_mainfile(filepath=str(OUT / "projection-base.blend"), compress=True)
    print("GUIDES_COMPLETE", str(OUT), flush=True)


if __name__ == "__main__":
    main()
