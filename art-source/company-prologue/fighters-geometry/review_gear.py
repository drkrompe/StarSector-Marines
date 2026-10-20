"""Review renders of the fighters' retractable gear: flying, mid-stow, landed, and a landed close-up.

  blender -b --factory-startup --python review_gear.py -- --blend a.blend [--blend b.blend ...]
          --out DIR [--half] [--samples 16] [--views flight,landed]

Each --blend is a textured fighter build (the surfacing build, ../fighters-surface/<id>/build/
skinned.blend, or a copy) carrying the "<Name> control" empty and its gear_down knob. Per fighter
it renders, into DIR, <id>-1-flight.png (knob 0, gear stowed, from below the bow quarter),
<id>-2-mid-stow.png (knob 0.55: doors open, legs on the way), <id>-3-landed.png (knob 1, standing
on a deck) and <id>-4-gear.png (knob 1, close on the gear). Frames already in DIR are skipped,
so a swapped-out queue job resumes. --views picks views by name (default flight,landed: the owner
looks at gear up and gear down; mid-stow and gear are there to ask for). --half renders at 960x540 (the render queue's light lane).
"""
from pathlib import Path
import argparse
import math
import sys
import bpy
from mathutils import Vector

ap = argparse.ArgumentParser()
ap.add_argument('--blend', action='append', required=True)
ap.add_argument('--out', required=True)
ap.add_argument('--half', action='store_true')
ap.add_argument('--samples', type=int, default=16)
ap.add_argument('--views', default='flight,landed')
args = ap.parse_args(sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else [])
OUT = Path(args.out)
OUT.mkdir(parents=True, exist_ok=True)

GEAR_WORDS = ('gear', ' leg ', ' leg', 'runner', 'skid fairing')
VIEWS = [  # (suffix, knob, view direction from the target, deck, framing)
    ('1-flight', 0.0, Vector((1.0, 1.15, -.42)), False, 'whole'),
    ('2-mid-stow', .55, Vector((.75, 1.0, -.85)), False, 'whole'),
    ('3-landed', 1.0, Vector((1.0, 1.35, .22)), True, 'whole'),
    ('4-gear', 1.0, Vector((.85, 1.25, .16)), True, 'gear'),
]


def gpu():
    prefs = bpy.context.preferences.addons['cycles'].preferences
    for kind in ('OPTIX', 'CUDA', 'METAL', 'HIP'):
        try:
            prefs.compute_device_type = kind
        except TypeError:
            continue
        prefs.refresh_devices()
        devs = [d for d in prefs.devices if d.type == kind]
        if devs:
            for d in prefs.devices:
                d.use = d.type == kind
            return kind
    return 'CPU'


for path in args.blend:
    bpy.ops.wm.open_mainfile(filepath=str(Path(path).resolve()))
    scene = bpy.context.scene
    control = next(o for o in bpy.data.objects if o.name.endswith(' control') and 'gear_down' in o)
    fighter = control.name[:-len(' control')].lower()
    jobs = [v for v in VIEWS if v[0].split('-', 1)[1] in args.views.split(',')
            and not (OUT/f'{fighter}-{v[0]}.png').exists()]
    if not jobs:
        continue
    for o in list(bpy.data.objects):
        if o.type in ('CAMERA', 'LIGHT') or o.name.startswith(('Pilot', 'Scale figure', 'Parked deck')):
            bpy.data.objects.remove(o)
    meshes = [o for o in bpy.data.objects if o.type == 'MESH' and not o.hide_render]
    scene.render.engine = 'CYCLES'
    scene.cycles.device = 'GPU' if gpu() != 'CPU' else 'CPU'
    scene.cycles.samples = args.samples
    scene.cycles.use_denoising = True
    scene.render.resolution_x, scene.render.resolution_y = 1920, 1080
    scene.render.resolution_percentage = 50 if args.half else 100
    scene.render.image_settings.file_format = 'PNG'
    scene.view_settings.view_transform = 'AgX'
    scene.world = scene.world or bpy.data.worlds.new('World')
    scene.world.use_nodes = True
    bg = scene.world.node_tree.nodes['Background']
    bg.inputs[0].default_value = (.62, .64, .67, 1)
    bg.inputs[1].default_value = .6

    def pose(k):
        control['gear_down'] = k
        control.update_tag()
        bpy.context.view_layer.update()

    def bounds(objs):
        dg = bpy.context.evaluated_depsgraph_get()
        lo, hi = Vector((1e9,)*3), Vector((-1e9,)*3)
        for o in objs:
            ev = o.evaluated_get(dg)
            for c in ev.bound_box:
                w = ev.matrix_world @ Vector(c)
                lo, hi = Vector(map(min, lo, w)), Vector(map(max, hi, w))
        return lo, hi

    pose(1.0)
    lo, hi = bounds(meshes)
    deck_z = lo.z
    span = (hi-lo).length
    gear = [o for o in meshes if any(w in o.name for w in GEAR_WORDS) and ' door' not in o.name
            and 'liner' not in o.name and 'fairing' not in o.name]
    glo, ghi = bounds(gear)
    bpy.ops.mesh.primitive_plane_add(size=span*30, location=(0, 0, deck_z))
    deck = bpy.context.object
    deck.name = 'Review deck'
    dm = bpy.data.materials.new('Review deck grey')
    dm.use_nodes = True
    dm.node_tree.nodes['Principled BSDF'].inputs['Base Color'].default_value = (.32, .33, .34, 1)
    dm.node_tree.nodes['Principled BSDF'].inputs['Roughness'].default_value = .85
    deck.data.materials.append(dm)
    for name, d, energy, size in (('Key', Vector((.6, 1.0, 1.4)), 2.2, .4), ('Fill', Vector((-.8, -.6, .5)), .9, 1.5),
                                  ('Under', Vector((.2, .4, -1.0)), .5, 2.0)):
        bpy.ops.object.light_add(type='SUN', location=(0, 0, 0))
        sun = bpy.context.object
        sun.name = f'Review {name}'
        sun.data.energy = energy
        sun.data.angle = math.radians(size*10)
        sun.rotation_euler = (-d).to_track_quat('-Z', 'Y').to_euler()
    bpy.ops.object.camera_add()
    cam = bpy.context.object
    scene.camera = cam
    cam.data.lens = 50
    cam.data.clip_start = span*.01
    cam.data.clip_end = span*50
    for suffix, k, d, show_deck, framing in jobs:
        pose(k)
        if framing == 'gear':
            target, radius = (glo+ghi)/2, (ghi-glo).length*.62
        else:
            blo, bhi = bounds(meshes)
            target, radius = (blo+bhi)/2, (bhi-blo).length*.5
        dist = radius/math.tan(cam.data.angle/2)*1.05
        cam.location = target+d.normalized()*dist
        cam.rotation_euler = (target-cam.location).to_track_quat('-Z', 'Y').to_euler()
        deck.hide_render = not show_deck
        bpy.data.objects['Review Under'].hide_render = show_deck
        scene.render.filepath = str(OUT/f'{fighter}-{suffix}.png')
        bpy.ops.render.render(write_still=True)
        print('REVIEW', scene.render.filepath)
print('REVIEW done')
