"""Internal QA: a Workbench contact sheet of the kit, one tile per variant (no Cycles, no GPU
render; never shown to the owner as a review image).

  sh ../../blender.sh --background <kit.blend> --python qa_sheet.py -- --out DIR [--only a,b] [--size 480]
"""
import argparse
import math
import sys
from pathlib import Path

import bpy
from mathutils import Vector

argv = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
ap = argparse.ArgumentParser()
ap.add_argument('--out', required=True)
ap.add_argument('--only', default='')
ap.add_argument('--size', type=int, default=480)
ap.add_argument('--color', default='MATERIAL')
a = ap.parse_args(argv)
out = Path(a.out)
out.mkdir(parents=True, exist_ok=True)
only = set(filter(None, a.only.split(',')))
sc = bpy.context.scene
sc.render.engine = 'BLENDER_WORKBENCH'
sc.display.shading.light = 'STUDIO'
sc.display.shading.color_type = a.color
sc.display.shading.show_cavity = True
sc.display.shading.show_shadows = True
sc.render.resolution_x = sc.render.resolution_y = a.size
sc.render.film_transparent = False
cam = bpy.data.objects.new('qa cam', bpy.data.cameras.new('qa cam'))
sc.collection.objects.link(cam)
sc.camera = cam
wrecks = [o for o in bpy.data.objects if o.get('wk_name')]
for o in wrecks:
    o.hide_render = True
for o in wrecks:
    if only and o['wk_name'] not in only:
        continue
    o.hide_render = False
    h = Vector(o['wk_half'])
    r = h.length
    c = o.location
    d = Vector((-.62, -.55, .5)).normalized()
    cam.location = c + d * r * 2.6
    cam.rotation_euler = (-d).to_track_quat('-Z', 'Y').to_euler()
    cam.data.lens = 50
    sc.render.filepath = str(out / ('%s.png' % o['wk_name']))
    bpy.ops.render.render(write_still=True)
    o.hide_render = True
