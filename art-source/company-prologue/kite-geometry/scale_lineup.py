"""Pass 4 evidence: the Kite (P) beside the Hound (A) and the Colossus at true
relative scale, every hull at its own published metres_per_unit, laid out in
metres. Renders 1920x1080 broadside and top views.

Run (from this folder, after build_kite.py has saved kite-geometry.blend):
  sh ../../blender.sh --background --factory-startup --python scale_lineup.py -- --out DIR
"""
from pathlib import Path
import argparse
import json
import math
import sys
import bpy
from mathutils import Matrix, Vector

HERE = Path(__file__).resolve().parent
PROLOGUE = HERE.parent
ap = argparse.ArgumentParser()
ap.add_argument('--out', default=str(HERE/'renders'))
ap.add_argument('--samples', type=int, default=24)
args = ap.parse_args(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])
OUT = Path(args.out).resolve()
OUT.mkdir(parents=True, exist_ok=True)

# (label, blend, geometry.json); laid out bow to stern along X with 15 m gaps
HULLS = [('Kite (P)', HERE/'kite-geometry.blend', HERE/'geometry.json'),
         ('Hound (A)', PROLOGUE/'hound-geometry/hound-geometry.blend', PROLOGUE/'hound-geometry/geometry.json'),
         ('Colossus', PROLOGUE/'colossus-geometry/colossus-geometry.blend', PROLOGUE/'colossus-geometry/geometry.json')]

bpy.ops.wm.read_factory_settings(use_empty=True)
scene = bpy.context.scene
report = []
x_off = 0.0
for label, blend, gj in HULLS:
    mpu = json.loads(gj.read_text(encoding='utf-8'))['metres_per_unit']
    with bpy.data.libraries.load(str(blend), link=False) as (src, dst):
        dst.objects = list(src.objects)
    root = bpy.data.objects.new(f'{label} root', None)
    scene.collection.objects.link(root)
    lo, hi = Vector((1e9,)*3), Vector((-1e9,)*3)
    for ob in dst.objects:
        if ob is None or ob.type in ('CAMERA', 'LIGHT') or ob.name.startswith('Scale figure'):
            continue
        scene.collection.objects.link(ob)
        if ob.parent is None:
            ob.parent = root
    root.scale = (mpu,)*3
    bpy.context.view_layer.update()
    for ob in root.children_recursive:
        if ob.type == 'MESH':
            for c in ob.bound_box:
                w = ob.matrix_world @ Vector(c)
                lo = Vector(map(min, lo, w)); hi = Vector(map(max, hi, w))
    # Sterns at x_off, keels on Z 0, centred on Y 0.
    root.location = (x_off - lo.x, -(lo.y + hi.y)/2, -lo.z)
    x_off += hi.x - lo.x + 15.0
    report.append({'hull': label, 'metres_per_unit': mpu, 'length_m': round(hi.x - lo.x, 1),
                   'span_m': round(hi.y - lo.y, 1), 'height_m': round(hi.z - lo.z, 1)})
print('LINEUP', json.dumps(report))
(OUT/'scale-lineup.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')

scene.render.engine = 'CYCLES'
scene.cycles.samples = args.samples
scene.cycles.use_denoising = True
scene.render.resolution_x, scene.render.resolution_y = 1920, 1080
scene.render.resolution_percentage = 100
world = bpy.data.worlds.new('Studio')
scene.world = world
world.use_nodes = True
world.node_tree.nodes['Background'].inputs[0].default_value = (.74, .76, .78, 1)
world.node_tree.nodes['Background'].inputs[1].default_value = .7
scene.view_settings.view_transform = 'AgX'
sun = bpy.data.objects.new('Sun', bpy.data.lights.new('Sun', 'SUN'))
sun.data.energy = 3.5
sun.rotation_euler = Vector((.4, .5, .8)).to_track_quat('Z', 'Y').to_euler()
scene.collection.objects.link(sun)

# A 10 m grid strip under the lanes for reading lengths.
mat = bpy.data.materials.new('Grid')
mat.diffuse_color = (.2, .2, .22, 1)
for k in range(0, int(x_off) + 1, 10):
    bpy.ops.mesh.primitive_cube_add(size=1, location=(k, 0, -.3))
    t = bpy.context.object
    t.scale = (.35 if k % 50 else .9, 90, .2)
    t.data.materials.append(mat)

mid = (x_off - 15.0)/2
for name, loc, target, scale in [('broadside', (mid, 600, 20), (mid, 0, 20), x_off + 20),
                                 ('top', (mid, 0, 600), (mid, 0, 0), x_off + 20)]:
    cam = bpy.data.objects.new('Camera ' + name, bpy.data.cameras.new(name))
    scene.collection.objects.link(cam)
    cam.location = loc
    cam.rotation_euler = (Vector(target) - Vector(loc)).to_track_quat('-Z', 'Y').to_euler()
    if name == 'top':
        cam.rotation_euler = (0, 0, 0)
    cam.data.type = 'ORTHO'
    cam.data.ortho_scale = scale
    cam.data.clip_end = 2000
    scene.camera = cam
    scene.render.filepath = str(OUT/f'scale-lineup-{name}.png')
    bpy.ops.render.render(write_still=True)
