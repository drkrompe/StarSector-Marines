"""Derive a damaged arrival and continuous yard style from the retained landing."""
from pathlib import Path
import hashlib
import json
import os
import sys
import bpy

OUT = Path(__file__).resolve().parent
PREVIOUS = OUT.parent / 'landing-refinement'
sys.path.insert(0, str(OUT))
sys.path.append(str(PREVIOUS))
from render_config import configure_render_device
from damage_geometry import apply_damage
from impact_surface import apply_impact_surface
from unstable_arrival import apply_unstable_arrival
from yard_style import apply_yard_style

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    source = PREVIOUS / 'landing-refinement.blend'
    bpy.ops.wm.open_mainfile(filepath=str(source))
    scene = bpy.context.scene
    yard = apply_yard_style(scene)
    damage = apply_damage(scene)
    surface = apply_impact_surface(scene, OUT / 'assets/impact-surfaces-v1.png')
    motion = apply_unstable_arrival(scene)
    device = configure_render_device(scene)
    scene.render.engine = 'CYCLES'
    scene.cycles.samples = 32
    scene.cycles.use_denoising = True
    scene.render.use_persistent_data = True
    scene.render.resolution_x = 1920
    scene.render.resolution_y = 1080
    scene.render.resolution_percentage = 100
    scene.render.image_settings.file_format = 'PNG'
    scene.frame_set(744)
    for folder in ('build', 'review'):
        (OUT / folder).mkdir(exist_ok=True)
    for image in bpy.data.images:
        if image.source == 'FILE' and image.filepath and not image.packed_file:
            resolved = Path(bpy.path.abspath(image.filepath))
            if resolved.is_file():
                image.filepath = '//' + Path(os.path.relpath(resolved, OUT)).as_posix()
    target = OUT / 'landing-wreck-pass.blend'
    bpy.ops.wm.save_as_mainfile(filepath=str(target), relative_remap=False)
    report = {
        'source': '../landing-refinement/landing-refinement.blend',
        'source_sha256': sha(source), 'scene': target.name, 'scene_sha256': sha(target),
        'camera': scene.camera.name, 'device': device,
        'yard': yard, 'damage': damage, 'surface': surface, 'motion': motion,
        'review_scope': 'Eight-second revised landing; prior four-second port excerpt prepended for continuity review.'
    }
    (OUT / 'scene-manifest.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8', newline='\n')
    if '--preview' in sys.argv:
        for name, frame in [('start',580), ('correction',686), ('contact',744), ('settled',774)]:
            scene.frame_set(frame)
            scene.render.filepath = str(OUT / 'review' / (name + '.png'))
            bpy.ops.render.render(write_still=True)

if __name__ == '__main__':
    main()
