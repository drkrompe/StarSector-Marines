"""The Manticore (P)'s one look render: append the published asset and render it.

  blender --background --factory-startup --python look.py -- --blend <manticore-p.blend> --out <dir>
      [--res 960x540] [--samples 16] [--views three-quarter,wing-detail,engine-detail] [--effects]

Appends `MANTICORE (P)` (and `SWFX` with --effects) into an empty scene the way a
shot does, lights it with the geometry build's review studio (key and fill disks,
grey world) and renders the named cameras from geometry.json: so the look is of the
published build, and it also proves the build appends. A light queue job, never a
session's own shell.
"""
import json
import math
import sys
from pathlib import Path
import bpy
from mathutils import Vector

HERE = Path(__file__).resolve().parent
argv = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
arg = lambda k, d=None: argv[argv.index(k) + 1] if k in argv else d
BLEND, OUT = Path(arg('--blend')), Path(arg('--out'))
VIEWS = arg('--views', 'three-quarter,wing-detail,engine-detail').split(',')
GEOM = json.loads((HERE.parent / 'manticore-geometry' / 'geometry.json').read_text(encoding='utf-8'))
TARGET = Vector((10, 0, 0))

bpy.ops.wm.read_factory_settings(use_empty=True)
scene = bpy.context.scene
names = ['MANTICORE (P)'] + (['SWFX'] if '--effects' in argv else [])
with bpy.data.libraries.load(str(BLEND), link=False) as (src, dst):
    dst.collections = [c for c in src.collections if c in names]
for c in dst.collections:
    scene.collection.children.link(c)
assert bpy.data.objects.get('MANTICORE (P) | root'), 'no root after append'

scene.render.engine = 'CYCLES'
scene.cycles.samples = int(arg('--samples', 16))
scene.cycles.use_denoising = True
scene.render.resolution_x, scene.render.resolution_y = map(int, arg('--res', '960x540').split('x'))
scene.render.resolution_percentage = 100
scene.render.image_settings.file_format = 'PNG'
scene.view_settings.view_transform = 'AgX'
scene.world = bpy.data.worlds.new('Look world')
scene.world.use_nodes = True
bg = scene.world.node_tree.nodes['Background']
bg.inputs[0].default_value, bg.inputs[1].default_value = (.74, .76, .78, 1), .65
for name, loc, energy, size in [('Key', (100, 120, 170), 430000, 135), ('Fill', (-85, -110, 95), 230000, 120)]:
    ld = bpy.data.lights.new('Look ' + name, 'AREA')
    ld.energy, ld.shape, ld.size = energy, 'DISK', size
    lt = bpy.data.objects.new('Look ' + name, ld)
    scene.collection.objects.link(lt)
    lt.location = loc
    lt.rotation_euler = (-Vector(loc)).to_track_quat('-Z', 'Y').to_euler()
cams = {c['name']: (c['position'], list(TARGET), c['orthographic_scale']) for c in GEOM['cameras']}
cams |= {c['name']: (c['position'], c['target'], c['orthographic_scale']) for c in GEOM['closeups']}
OUT.mkdir(parents=True, exist_ok=True)
for k, view in enumerate(VIEWS, 1):
    pos, tgt, sc = cams[view]
    cd = bpy.data.cameras.new(view)
    cd.type, cd.ortho_scale, cd.clip_end = 'ORTHO', sc, 1000
    cam = bpy.data.objects.new('Look ' + view, cd)
    scene.collection.objects.link(cam)
    cam.location = pos
    cam.rotation_euler = (Vector(tgt) - Vector(pos)).to_track_quat('-Z', 'Y').to_euler()
    scene.camera = cam
    scene.render.filepath = str(OUT / f'{k:02d}-{view}.png')
    bpy.ops.render.render(write_still=True)
    print('LOOK', scene.render.filepath)
