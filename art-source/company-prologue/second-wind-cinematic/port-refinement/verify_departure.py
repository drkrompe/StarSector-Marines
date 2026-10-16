"""Measure actual saved-scene departure; run with background --factory-startup.

Thresholds express this shot's artistic motion acceptance, not flight physics.
Full-canvas projection is measured; painted alpha silhouette and occlusion need
rendered review. This script never writes the source scene.
"""
from pathlib import Path
import hashlib
import json
import math

import bpy
from bpy_extras.object_utils import world_to_camera_view
from mathutils import Vector

H = Path(__file__).resolve().parent
FRAMES = [400 + .5 * i for i in range(96)]


def verify():
    scene = bpy.context.scene
    camera = bpy.data.objects['04 PORT / side window']
    world = bpy.data.objects['LAYERED | inverse source flight']
    scene_path = Path(bpy.data.filepath)
    rows = []
    for obj in sorted((o for o in scene.objects if 'ship_card_id' in o), key=lambda o: o.name):
        scene.frame_set(424)
        midpoint = world.matrix_world.inverted() @ obj.matrix_world.translation
        samples = []
        directions = []
        for frame in FRAMES:
            scene.frame_set(int(frame), subframe=frame % 1)
            source_matrix = world.matrix_world.inverted() @ obj.matrix_world
            center = source_matrix.translation.copy()
            source_camera = world.matrix_world.inverted() @ camera.matrix_world.translation
            # Express line of sight in the retained card's own fixed coordinates.
            direction = source_matrix.to_3x3().inverted() @ (source_camera - center)
            directions.append(direction.normalized())
            def project(point):
                p = world_to_camera_view(scene, camera, point)
                return [1920 * p.x, 1080 * (1 - p.y), p.z]
            projected = [project(obj.matrix_world @ v.co) for v in obj.data.vertices]
            samples.append(dict(source_frame=frame, source_center=list(center),
                                center_px=project(obj.matrix_world.translation),
                                stationary_midpoint_px=project(world.matrix_world @ midpoint),
                                full_canvas_width_px=max(p[0] for p in projected)-min(p[0] for p in projected),
                                min_camera_depth=min(p[2] for p in projected)))
        first, last = samples[0], samples[-1]
        elapsed = (FRAMES[-1] - FRAMES[0]) / 12
        start = Vector(first['source_center'])
        delta = Vector(last['source_center']) - start
        errors = [(Vector(r['source_center']) - (start + delta * i / 95)).length for i, r in enumerate(samples)]
        dx = last['center_px'][0] - first['center_px'][0]
        dy = last['center_px'][1] - first['center_px'][1]
        ratio = last['full_canvas_width_px'] / first['full_canvas_width_px']
        stationary_dx = last['stationary_midpoint_px'][0] - first['stationary_midpoint_px'][0]
        angles = [math.degrees(a.angle(b)) for a in directions for b in directions]
        monotonic = all(b['center_px'][0] > a['center_px'][0] for a, b in zip(samples, samples[1:]))
        passed = dx > 100 and ratio < .85 and max(errors) < .002 and monotonic and min(r['min_camera_depth'] for r in samples) > 0
        rows.append(dict(ship=obj['ship_card_id'], passed=passed, elapsed_seconds=elapsed,
                         source_delta=list(delta), source_speed_m_per_second=delta.length/elapsed,
                         source_climb_m_per_second=delta.z/elapsed, screen_displacement_px=[dx, dy],
                         stationary_counterfactual_displacement_x_px=stationary_dx,
                         independent_motion_contribution_x_px=dx-stationary_dx,
                         apparent_full_canvas_width_end_over_start=ratio,
                         maximum_viewpoint_angle_variation_degrees=max(angles),
                         maximum_linear_path_residual_metres=max(errors), monotonically_rightward=monotonic,
                         samples=samples))
    report = dict(passed=len(rows)==2 and all(r['passed'] for r in rows),
                  scene=str(scene_path), scene_sha256=hashlib.sha256(scene_path.read_bytes()).hexdigest(),
                  source_fps=12, output_fps=24, source_frames=[400,447.5], sample_count=96,
                  acceptance='Artistic shot acceptance: >100px rightward travel, end/start full-canvas width <0.85, linear source path within 0.002m, monotonic rightward motion, positive camera depth.',
                  limits='Full quad canvas projection, not alpha silhouette. Does not prove painted bow geometry, occlusion, terrain clearance, aircraft performance, or convincing rendered motion. Existing scene verifier owns clearance.', ships=rows)
    target=H/'review/departure-motion.json'
    target.parent.mkdir(parents=True,exist_ok=True)
    target.write_text(json.dumps(report,indent=2),encoding='utf-8')
    print(json.dumps({k:v for k,v in report.items() if k!='ships'}))
    for row in rows:
        print(json.dumps({k:v for k,v in row.items() if k!='samples'}))
    if not report['passed']:
        raise RuntimeError('Departure motion acceptance failed; inspect measured report.')


if __name__ == '__main__':
    bpy.ops.wm.open_mainfile(filepath=str(H / 'port-refinement.blend'))
    verify()
