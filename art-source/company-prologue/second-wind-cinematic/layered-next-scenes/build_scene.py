"""Build the next four arrival beats with explicit compositing depth groups.

Run in background Blender. The original animatic and painted studies are inputs;
only this directory receives a derived scene and metadata. No ImageGen is used.
"""
import json
from pathlib import Path
import sys

import bpy
from mathutils import Matrix, Vector

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE.parent / 'parallax-window'))
from environment_layers import build_layers
from stabilize import _curves

SOURCE = HERE.parent / 'animatic/jangala-blockout.blend'
SHOTS = [
    dict(id='03', title='A world people live on', output_start=1, output_end=96,
         source_start=240, source_step=.5, camera='03 LAND / side window'),
    dict(id='04', title='The working port', output_start=97, output_end=192,
         source_start=400, source_step=.5, camera='04 PORT / side window'),
    dict(id='05', title='Aligned with the berth', output_start=193, output_end=312,
         source_start=565, source_step=.5, camera='05 EXTERIOR / approach'),
    dict(id='06', title='The yard takes the weight', output_start=313, output_end=576,
         source_start=647, source_step=.5, camera='06 LANDING / support review'),
]


def stabilize_world(scene, root):
    samples = []
    for frame in range(1, 781):
        scene.frame_set(frame)
        samples.append(dict(frame=frame, position=list(root.matrix_world.translation)))
    animation = root.animation_data
    identifier = animation.action_slot.identifier
    action = animation.action.copy()
    animation.action = action
    animation.action_slot = next(s for s in action.slots if s.identifier == identifier)
    for curves in _curves(action):
        for curve in list(curves):
            if curve.data_path == 'location':
                curves.remove(curve)
    root.location = (0, 0, 0)
    world = bpy.data.objects.new('LAYERED | inverse source flight', None)
    scene.collection.objects.link(world)
    # Keep source world coordinates as local coordinates. Rebase every top-level
    # world object, including the exterior cameras, by the same inverse flight.
    for obj in list(scene.objects):
        if obj not in (root, world) and obj.parent is None:
            obj.parent = world
            obj.matrix_parent_inverse = Matrix.Identity(4)
    for sample in samples:
        world.location = -Vector(sample['position'])
        world.keyframe_insert(data_path='location', frame=sample['frame'])
    for curves in _curves(world.animation_data.action):
        for curve in curves:
            for key in curve.keyframe_points:
                key.interpolation = 'LINEAR'
    return world, dict(source_positions=samples, root_translation=[0, 0, 0],
                       source_frame_range=[1, 780],
                       coordinate_contract='scenery world = original world - source ship translation')


def main():
    bpy.ops.wm.open_mainfile(filepath=str(SOURCE))
    scene = bpy.context.scene
    root = bpy.data.objects['SECOND WIND | flight root']
    original_names = set(bpy.data.objects.keys())
    layers = build_layers(scene)
    # Explicit isolation IDs also cover the actual foreground mechanics. These
    # are future paint/mask boundaries, not a reason to flatten their geometry.
    masks = {
        'ship and cabin': dict(id=10, role='Retain geometry; shot-local paint projection'),
        'cradle and clamps': dict(id=11, role='Retain geometry, contact and occlusion'),
        'near port structures': dict(id=12, role='Retain geometry; independent painted surfaces'),
        'managed land and roads': dict(id=13, role='Retain ground perspective; paint field surfaces'),
        'river and terrain': dict(id=14, role='Terrain relief / broad distant painted plates'),
    }
    ship = set(root.children_recursive)
    for obj in scene.objects:
        if obj.type != 'MESH' or obj.name not in original_names:
            continue
        group = ('ship and cabin' if obj in ship else
                 'cradle and clamps' if obj.name.startswith(('YARD_contact', 'YARD_pad_', 'YARD_cradle', 'YARD_retaining', 'YARD_clamp')) else
                 'near port structures' if obj.name.startswith(('YARD_', 'Port ', 'Freight ')) else
                 'managed land and roads' if obj.name.startswith('Plantation') else 'river and terrain')
        obj.pass_index = masks[group]['id']
        obj['composite_role'] = group
        obj['future_paint_role'] = masks[group]['role']
    world, stabilization = stabilize_world(scene, root)
    # The source matte geometry is deliberately retained. Keep this review a
    # blockout, with neutral illumination and visible depth/structural edges.
    scene.render.engine = 'BLENDER_EEVEE'
    scene.eevee.taa_render_samples = 8
    scene.render.resolution_x, scene.render.resolution_y = 1920, 1080
    scene.render.resolution_percentage = 100
    scene.render.fps = 12
    scene.render.image_settings.file_format = 'PNG'
    scene.render.image_settings.color_mode = 'RGB'
    scene.render.image_settings.compression = 15
    scene.frame_start, scene.frame_end = 217, 780
    scene.view_layers[0].use_pass_z = True
    scene.view_layers[0].use_pass_object_index = True
    scene.frame_set(264)
    scene.camera = bpy.data.objects[SHOTS[0]['camera']]
    for screen in bpy.data.screens:
        for area in screen.areas:
            if area.type == 'VIEW_3D':
                area.spaces.active.region_3d.view_perspective = 'CAMERA'
    bpy.context.preferences.filepaths.save_version = 0
    bpy.ops.wm.save_as_mainfile(filepath=str(HERE / 'layered-next-scenes.blend'), compress=True)
    manifest = dict(
        source_blend='../animatic/jangala-blockout.blend', scene='layered-next-scenes.blend',
        root=root.name, scenery_root=world.name, source_fps=12, output_fps=24,
        output_frames=576, duration_seconds=24, shots=SHOTS,
        stabilization=stabilization, layers=layers, original_mask_groups=masks,
        landing=dict(fore_aft_stopped=624, hold_end=648, descent_start=649,
                     contact=744, clamp_start=751, clamp_end=774, lift_off=768),
        scale='Original provisional 1.5 m per hull unit; no canonical dimension claim',
        limitations=['Untextured geometry; arrival damage is a later surface pass',
                     '24 seconds of native-speed excerpts; cuts omit travel, not time acceleration',
                     'No audio, runtime playback, repairs or campaign-state changes'],
    )
    (HERE / 'scene-manifest.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
    print('LAYERED_SCENE_READY', HERE, flush=True)


if __name__ == '__main__':
    main()
