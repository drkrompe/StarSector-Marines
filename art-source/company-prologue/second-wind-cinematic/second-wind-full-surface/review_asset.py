"""Independent studio evidence. Run in background Blender with --factory-startup.

Default: six 1080p stills. --motion: 192 frames / 24fps. --preview: only
starboard and bow-quarter stills, or motion frames 1/65/129 (still 1080p).
Never saves over the source asset. The manifest is written during rendering.
"""
import argparse
import hashlib
import json
import math
from pathlib import Path
import sys
import time
import uuid

import bpy
from mathutils import Matrix, Vector
from bpy_extras.object_utils import world_to_camera_view

BASE = Path(__file__).resolve().parent
ASSET = BASE / 'second-wind-full-surface.blend'
VIEWS = {'starboard': (0, -1, .12), 'port': (0, 1, .12),
         'bow-quarter': (1, -.9, .45), 'stern-quarter': (-1, -.9, .4),
         'top': (0, 0, 1), 'underside': (0, 0, -1)}


def sha(path):
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(chunk)
    return digest.hexdigest()


def write_json(path, value):
    temporary = path.with_suffix('.tmp')
    temporary.write_text(json.dumps(value, indent=2), encoding='utf-8', newline='\n')
    temporary.replace(path)


def point_at(obj, point):
    obj.rotation_euler = (Vector(point) - obj.location).to_track_quat('-Z', 'Y').to_euler()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    modes = parser.add_mutually_exclusive_group()
    modes.add_argument('--motion', action='store_true')
    modes.add_argument('--stills', action='store_true')
    parser.add_argument('--preview', action='store_true')
    parser.add_argument('--views', help='comma-separated subset of named still views')
    args = parser.parse_args(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])
    if not bpy.app.background:
        raise RuntimeError('Use background Blender; this tool never changes a live UI scene')
    source_hash = sha(ASSET)
    bpy.ops.wm.open_mainfile(filepath=str(ASSET))
    scene = bpy.context.scene
    root = bpy.data.objects.get('SECOND WIND | flight root')
    if root is None:
        raise RuntimeError('Missing SECOND WIND | flight root')
    ship = {root, *root.children_recursive}
    # Render only the asset hierarchy; preserve every authored child hide flag.
    for obj in list(bpy.data.objects):
        if obj not in ship:
            bpy.data.objects.remove(obj, do_unlink=True)
    for obj in ship:
        obj.animation_data_clear()
    root.matrix_world = Matrix.Diagonal((*root.scale, 1))
    bpy.context.view_layer.update()
    visible = [o for o in ship if o.type == 'MESH' and not o.hide_render]
    points = [o.matrix_world @ Vector(c) for o in visible for c in o.bound_box]
    if not points:
        raise RuntimeError('No renderable hull geometry')
    lo = Vector(tuple(min(p[i] for p in points) for i in range(3)))
    hi = Vector(tuple(max(p[i] for p in points) for i in range(3)))
    center = (lo + hi) / 2
    span = (hi - lo).length
    # Rotate the complete ship about its own center without applying scale.
    pivot = bpy.data.objects.new('REVIEW | physical ship turn', None)
    scene.collection.objects.link(pivot)
    pivot.location = center
    bpy.context.view_layer.update()
    matrix = root.matrix_world.copy()
    root.parent = pivot
    root.matrix_world = matrix
    scene.world = bpy.data.worlds.new('REVIEW | neutral studio')
    scene.world.use_nodes = True
    bg = scene.world.node_tree.nodes.get('Background')
    bg.inputs['Color'].default_value = (.025, .025, .025, 1)
    bg.inputs['Strength'].default_value = .35
    camera_data = bpy.data.cameras.new('REVIEW | camera')
    camera = bpy.data.objects.new('REVIEW | camera', camera_data)
    scene.collection.objects.link(camera)
    scene.camera = camera
    camera_data.type = 'ORTHO'
    camera_data.clip_end = span * 100
    camera_data.clip_start = .01
    for name, direction, power, size in [
        ('key', (0.4, -0.8, 1), 14, .9),
        ('fill', (-.4, .8, .35), 7, 1.1),
        ('rim', (-1, -.1, .8), 11, .8),
        ('belly', (.3, .3, -1), 6, 1.0)]:
        lamp_data = bpy.data.lights.new('REVIEW | ' + name, 'AREA')
        lamp_data.energy = power * span * span
        lamp_data.shape = 'DISK'
        lamp_data.size = span * size
        lamp = bpy.data.objects.new(lamp_data.name, lamp_data)
        scene.collection.objects.link(lamp)
        lamp.location = center + Vector(direction).normalized() * span * 1.4
        point_at(lamp, center)
    scene.render.engine = 'CYCLES'
    scene.cycles.device = 'GPU'
    scene.cycles.samples = 24
    scene.cycles.use_denoising = True
    prefs = bpy.context.preferences.addons['cycles'].preferences
    prefs.compute_device_type = 'OPTIX'
    prefs.get_devices()
    gpu_devices = [d for d in prefs.devices if d.type == 'OPTIX']
    if not gpu_devices:
        raise RuntimeError('OPTIX GPU required; no silent CPU fallback')
    for device in prefs.devices:
        device.use = device.type == 'OPTIX'
    scene.render.resolution_x = 1920
    scene.render.resolution_y = 1080
    scene.render.resolution_percentage = 100
    scene.render.fps = 24
    scene.render.fps_base = 1
    scene.render.image_settings.file_format = 'PNG'
    scene.render.image_settings.color_mode = 'RGB'
    scene.render.film_transparent = False
    scene.render.use_compositing = False
    scene.render.use_sequencer = False
    scene.view_settings.view_transform = 'AgX'
    scene.view_settings.exposure = 0
    scene.view_settings.gamma = 1
    scene.frame_start, scene.frame_end = 1, 192
    # Frame 193 completes the continuous turn; 1..192 are distinct samples.
    for frame in range(1, 194):
        phase = (frame - 1) / 192 * 2 * math.pi
        pivot.rotation_euler = (math.radians(4) * math.sin(phase),
                                math.radians(15) * math.sin(phase), phase)
        pivot.keyframe_insert(data_path='rotation_euler', frame=frame)
    camera.location = center + Vector((.15, -1, .32)).normalized() * span * 3
    point_at(camera, center)
    # Bound every pose, keeping the camera/lights static during motion.
    def fit(frames):
        max_x = max_y = 0
        camera_data.ortho_scale = 1
        for frame in frames:
            scene.frame_set(frame)
            bpy.context.view_layer.update()
            for obj in visible:
                for corner in obj.bound_box:
                    p = world_to_camera_view(scene, camera, obj.matrix_world @ Vector(corner))
                    max_x = max(max_x, abs(p.x - .5))
                    max_y = max(max_y, abs(p.y - .5))
        camera_data.ortho_scale = max(max_x * 2 / .76, max_y * 2 / .8)
    fit(range(1, 193))
    scene.frame_set(1)
    build = BASE / 'build'
    review = BASE / 'review'
    build.mkdir(exist_ok=True)
    review.mkdir(exist_ok=True)
    mode = 'motion' if args.motion else 'stills'
    tag = ('preview-' if args.preview else '') + mode
    rig = build / ('review-scene-' + tag + '.blend')
    # Save once, before rendering. This exact file is bound to every frame.
    bpy.ops.wm.save_as_mainfile(filepath=str(rig))
    rig_hash = sha(rig)
    manifest_path = review / (tag + '-manifest.json')
    manifest = {'schema': 1, 'run_id': str(uuid.uuid4()), 'mode': mode,
                'preview': args.preview, 'complete': False,
                'source_asset': ASSET.name, 'source_sha256_before': source_hash,
                'review_scene': rig.relative_to(BASE).as_posix(),
                'review_scene_sha256_before': rig_hash,
                'dimensions': [1920, 1080], 'fps': 24, 'samples': 24,
                'devices': [d.name for d in gpu_devices], 'frames': []}
    write_json(manifest_path, manifest)
    if args.motion:
        selected = [1, 65, 129] if args.preview else list(range(1, 193))
        jobs = [(str(f), f, None) for f in selected]
    else:
        selected = args.views.split(',') if args.views else (['starboard', 'bow-quarter'] if args.preview else list(VIEWS))
        if any(v not in VIEWS for v in selected):
            raise ValueError('Unknown view; choose ' + ', '.join(VIEWS))
        jobs = [(v, 1, VIEWS[v]) for v in selected]
    frame_dir = build / (tag + '-frames')
    frame_dir.mkdir(exist_ok=True)
    for name, frame, direction in jobs:
        scene.frame_set(frame)
        if direction is not None:
            pivot.animation_data_clear()
            pivot.rotation_euler = (0, 0, 0)
            camera.location = center + Vector(direction).normalized() * span * 3
            point_at(camera, center)
            fit([1])
        path = frame_dir / (f'{frame:04d}.png' if args.motion else name + '.png')
        scene.render.filepath = str(path)
        before = time.time_ns()
        source_before = sha(ASSET)
        scene_before = sha(rig)
        if source_before != source_hash or scene_before != rig_hash:
            raise RuntimeError('Source/review scene changed before render')
        bpy.ops.render.render(write_still=True)
        # Bind immediately, rather than reconstructing provenance at encode time.
        record = {'name': name, 'frame': frame, 'path': path.relative_to(BASE).as_posix(),
                  'sha256': sha(path), 'bytes': path.stat().st_size,
                  'mtime_ns': path.stat().st_mtime_ns, 'render_started_ns': before,
                  'source_sha256_before': source_before,
                  'review_scene_sha256_before': scene_before,
                  'source_sha256_after': sha(ASSET),
                  'review_scene_sha256_after': sha(rig),
                  'camera_matrix_world': [list(row) for row in camera.matrix_world],
                  'camera_ortho_scale': camera_data.ortho_scale,
                  'turn_matrix_world': [list(row) for row in pivot.matrix_world]}
        manifest['frames'].append(record)
        manifest['source_sha256_after'] = sha(ASSET)
        manifest['review_scene_sha256_after'] = sha(rig)
        if manifest['source_sha256_after'] != source_hash or manifest['review_scene_sha256_after'] != rig_hash:
            raise RuntimeError('Source/review scene changed during rendering')
        write_json(manifest_path, manifest)
        if not args.motion:
            import shutil
            shutil.copyfile(path, review / (('preview-' if args.preview else '') + name + '.png'))
    manifest['unique_frame_hashes'] = len({r['sha256'] for r in manifest['frames']})
    if manifest['unique_frame_hashes'] != len(jobs):
        raise RuntimeError('Duplicate rendered frames')
    manifest['complete'] = True
    write_json(manifest_path, manifest)
    print('REVIEW_MANIFEST ' + str(manifest_path), flush=True)


if __name__ == '__main__':
    main()
