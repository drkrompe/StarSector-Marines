"""Render numeric object-mask group colors without changing the saved scene."""
import argparse
import colorsys
import json
from pathlib import Path
import sys
import bpy

HERE = Path(__file__).resolve().parent


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--blend', type=Path, default=HERE / 'layered-next-scenes.blend')
    parser.add_argument('--manifest', type=Path, default=HERE / 'scene-manifest.json')
    parser.add_argument('--output', type=Path, default=HERE / 'review')
    args = parser.parse_args(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])
    manifest = json.loads(args.manifest.read_text(encoding='utf-8'))
    bpy.ops.wm.open_mainfile(filepath=str(args.blend))
    scene = bpy.context.scene
    roles = {group['mask_id']: group['role'] for group in manifest['layers']['groups']}
    roles.update({group['id']: name + ': ' + group['role']
                  for name, group in manifest['original_mask_groups'].items()})
    roles.setdefault(0, 'Other retained geometry')
    ids = sorted({ob.pass_index for ob in scene.objects if ob.type == 'MESH'})
    palette = {}
    materials = {}
    for index, group_id in enumerate(ids):
        rgb = colorsys.hsv_to_rgb((index * .61803398875 + .04) % 1, .65, .9)
        # Store intended display sRGB colors; feed their linear equivalents to emission.
        linear = tuple(c / 12.92 if c <= .04045 else ((c + .055) / 1.055) ** 2.4 for c in rgb)
        palette[str(group_id)] = {'role': roles.get(group_id, 'Unlabelled retained geometry'),
                                  'srgb_0_to255': [round(c * 255) for c in rgb]}
        mat = bpy.data.materials.new(f'REVIEW MASK {group_id}')
        mat.use_nodes = True
        mat.node_tree.nodes.clear()
        emission = mat.node_tree.nodes.new('ShaderNodeEmission')
        emission.inputs['Color'].default_value = (*linear, 1)
        emission.inputs['Strength'].default_value = 1
        output = mat.node_tree.nodes.new('ShaderNodeOutputMaterial')
        mat.node_tree.links.new(emission.outputs[0], output.inputs['Surface'])
        materials[group_id] = mat
    for ob in scene.objects:
        if ob.type != 'MESH':
            continue
        # Make shared mesh data single-user so distinct object IDs stay distinct.
        ob.data = ob.data.copy()
        ob.data.materials.clear()
        ob.data.materials.append(materials[ob.pass_index])
        for polygon in ob.data.polygons:
            polygon.material_index = 0
        # hide_render and collection visibility deliberately remain untouched.
    scene.world = bpy.data.worlds.new('REVIEW MASK black background')
    scene.world.use_nodes = True
    scene.world.node_tree.nodes.get('Background').inputs['Color'].default_value = (0, 0, 0, 1)
    scene.world.node_tree.nodes.get('Background').inputs['Strength'].default_value = 0
    scene.render.film_transparent = False
    scene.view_settings.view_transform = 'Standard'
    scene.view_settings.look = 'None'
    scene.view_settings.exposure = 0
    scene.view_settings.gamma = 1
    scene.render.resolution_x, scene.render.resolution_y = 1920, 1080
    scene.render.resolution_percentage = 100
    scene.render.image_settings.file_format = 'PNG'
    scene.render.image_settings.compression = 90
    scene.render.dither_intensity = 0
    scene.render.use_compositing = False
    args.output.mkdir(parents=True, exist_ok=True)
    for shot in manifest['shots']:
        midpoint = (shot['output_start'] + shot['output_end']) // 2
        source = shot['source_start'] + (midpoint - shot['output_start']) * shot['source_step']
        scene.frame_set(int(source), subframe=source - int(source))
        scene.camera = bpy.data.objects[shot['camera']]
        scene.render.filepath = str(args.output / f"shot-{shot['id']}-layers.png")
        bpy.ops.render.render(write_still=True)
        print(f"LAYER_MAP shot={shot['id']} source={source}", flush=True)
    (args.output / 'layer-map-legend.json').write_text(
        json.dumps({'background': [0, 0, 0], 'groups': palette,
                    'note': 'Flat object-ID role visualization; original hidden meshes remain hidden.'},
                   indent=2) + '\n', encoding='utf-8')


if __name__ == '__main__':
    main()
