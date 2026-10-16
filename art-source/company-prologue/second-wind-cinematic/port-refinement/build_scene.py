"""Build the refined working port from its retained textured motion source."""
import argparse
import json
from pathlib import Path
import sys

import bpy

HERE = Path(__file__).resolve().parent
SOURCE = HERE.parent / 'port-approach-realism/port-approach-realism.blend'
sys.path.insert(0, str(HERE))


def road_material(world, road):
    """Asphalt and road paint belong to the same surface material."""
    material = bpy.data.materials.new('PORT ROAD | ' + road['name'])
    material.use_nodes = True
    nodes, links = material.node_tree.nodes, material.node_tree.links
    bsdf = nodes.get('Principled BSDF')
    bsdf.inputs['Roughness'].default_value = .91
    coord = nodes.new('ShaderNodeTexCoord')
    coord.object = world
    separate = nodes.new('ShaderNodeSeparateXYZ')
    links.new(coord.outputs['Object'], separate.inputs[0])

    def math_node(operation, a, b):
        node = nodes.new('ShaderNodeMath')
        node.operation = operation
        for index, value in enumerate((a, b)):
            if isinstance(value, (int, float)):
                node.inputs[index].default_value = value
            else:
                links.new(value, node.inputs[index])
        return node.outputs[0]

    axis = road['axis']
    along = separate.outputs[axis]
    across = separate.outputs['Y' if axis == 'X' else 'X']
    bounds = road['bounds']
    low, high = (bounds[2:4] if axis == 'X' else bounds[0:2])
    offset = math_node('ABSOLUTE', math_node('SUBTRACT', across, (low+high)/2), 0)
    center = math_node('LESS_THAN', offset, .22)
    dash = math_node('LESS_THAN', math_node('PINGPONG', along, 12), 6)
    center = math_node('MULTIPLY', center, dash)
    edge_distance = math_node('ABSOLUTE', math_node('SUBTRACT', offset, (high-low)/2-1), 0)
    edges = math_node('LESS_THAN', edge_distance, .22)
    paint = math_node('MAXIMUM', center, edges)
    noise = nodes.new('ShaderNodeTexNoise')
    noise.inputs['Scale'].default_value = .6
    noise.inputs['Detail'].default_value = 3
    links.new(coord.outputs['Object'], noise.inputs['Vector'])
    asphalt = nodes.new('ShaderNodeMixRGB')
    asphalt.inputs[1].default_value = (.075, .09, .09, 1)
    asphalt.inputs[2].default_value = (.14, .16, .15, 1)
    links.new(noise.outputs['Fac'], asphalt.inputs[0])
    mix = nodes.new('ShaderNodeMixRGB')
    links.new(paint, mix.inputs[0])
    links.new(asphalt.outputs[0], mix.inputs[1])
    mix.inputs[2].default_value = (.63, .59, .37, 1)
    links.new(mix.outputs[0], bsdf.inputs['Base Color'])
    return material


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--preview', action='store_true')
    parser.add_argument('--without-ships', action='store_true')
    parser.add_argument('--geometry-ships', action='store_true', help='Retain the mesh-only guide stage')
    parser.add_argument('--without-architecture-finish', action='store_true', help='Retain neutral material atlas stage')
    args = parser.parse_args(sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else [])
    bpy.ops.wm.open_mainfile(filepath=str(SOURCE))
    # Resolve inherited paths while they still have the source file's base.
    for image in bpy.data.images:
        if image.source == 'FILE' and image.filepath:
            image.filepath = bpy.path.abspath(image.filepath)
    scene = bpy.context.scene
    scene.camera = bpy.data.objects['04 PORT / side window']
    for marker in scene.timeline_markers:
        marker.camera = None
    world = bpy.data.objects['LAYERED | inverse source flight']
    from industrial_district import build_district
    from ground_surfaces import repair_ground
    from verify_ground import verify_ground
    from port_traffic import build_traffic
    layout = build_district(scene, world)
    layout['road_z'] = layout['ground_z']
    layout['obstacle_objects'] = layout['objects']
    # Vegetation/drainage and roads partition one mesh; roads win intersections.
    turf = bpy.data.objects['Jangala | continuous landform'].data.materials[0]
    cells = [{'bounds': bounds, 'material': turf} for bounds in layout['drainage_strips']]
    cells += [dict(road, material=road_material(world, road)) for road in layout['roads']]
    ground = repair_ground(scene, layout['developed_bounds'], cells, z=layout['ground_z'], grade_width=40)
    ground_check = verify_ground(scene)
    traffic = build_traffic(scene, world, layout)
    parked = None
    cards = None
    if not args.without_ships:
        from parked_ships import build_parked_ships
        parked = build_parked_ships(scene, world, layout)
        if not args.geometry_ships:
            from ship_cards import build_ship_cards
            cards = build_ship_cards(scene, world, layout)
    architecture = None
    if not args.without_architecture_finish:
        from architecture_projection import apply_architecture_finish
        architecture = apply_architecture_finish(scene, world, HERE/'assets/port-architecture-finish-v1.png')
    scene.render.engine = 'BLENDER_EEVEE'
    scene.eevee.taa_render_samples = 64
    scene.eevee.shadow_pool_size = '2048'
    scene.render.resolution_x, scene.render.resolution_y = 1920, 1080
    scene.render.resolution_percentage = 100
    scene.render.image_settings.file_format = 'PNG'
    scene.render.image_settings.color_mode = 'RGB'
    scene.render.fps = 12
    scene.frame_set(424)
    for image in bpy.data.images:
        if image.source == 'FILE' and image.filepath:
            absolute = Path(bpy.path.abspath(image.filepath)).resolve()
            if not absolute.exists():
                raise FileNotFoundError(absolute)
            image.filepath = bpy.path.relpath(str(absolute), start=str(HERE))
    bpy.context.preferences.filepaths.save_version = 0
    bpy.ops.wm.save_as_mainfile(filepath=str(HERE/'port-refinement.blend'), compress=True, relative_remap=False)
    manifest = dict(source='../port-approach-realism/port-approach-realism.blend', camera=scene.camera.name,
                    source_frames=[400, 447.5], source_fps=12, output_fps=24, output_frames=96,
                    layout=layout, ground=ground, ground_check=ground_check, traffic=traffic, parked_ships=parked, ship_cards=cards,
                    architecture=architecture, render_samples=64, scope='Refined port study; no source forest/farmland or runtime changes')
    (HERE/'scene-manifest.json').write_text(json.dumps(manifest, indent=2)+'\n', encoding='utf-8')
    if args.preview:
        output = HERE/'build/previews'
        output.mkdir(parents=True, exist_ok=True)
        for index in (0, 48, 95):
            frame = 400 + .5*index
            scene.frame_set(int(frame), subframe=frame%1)
            scene.render.filepath = str(output/f'frame-{index:04d}.png')
            bpy.ops.render.render(write_still=True)
    print('REFINED_PORT_READY', flush=True)


if __name__ == '__main__':
    main()
