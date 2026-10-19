"""Grey review renders of the bridge interior (sw_bridge_interior), 1920x1080, Workbench.

    blender --background --factory-startup <model>.blend --python bridge_interior_review.py --
            --out DIR [--figures] [--views a,b] [--exterior-only] [--no-shadows]

Interior views stand where C2's bridge camera stands (forward and port stations), plus an
overview of the room, a cutaway from above with the roof lifted off, and a plan. The exterior
views (port quarter and a bridge close-up, orthographic, the driver's own framings) exist so
two builds can be pixel-diffed to show the outside of the ship did not move.

Glazing is hidden in the interior views: Workbench draws it opaque. --figures adds the 1.8 m
mannequins (sw_bridge_interior.figures); they are never saved.
"""
import math
import sys
from pathlib import Path

import bpy
from mathutils import Vector

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import sw_bridge_interior as BI  # noqa: E402

argv = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
opts = {'out': None, 'figures': False, 'views': None, 'exterior_only': False, 'no_shadows': False}
i = 0
while i < len(argv):
    k = argv[i].lstrip('-').replace('-', '_')
    if k in ('figures', 'exterior_only', 'no_shadows'):
        opts[k] = True
        i += 1
    else:
        opts[k] = argv[i + 1]
        i += 2
out = Path(opts['out'])
out.mkdir(parents=True, exist_ok=True)
scene = bpy.context.scene
for o in list(bpy.data.objects):
    if o.type in ('CAMERA', 'LIGHT'):
        bpy.data.objects.remove(o, do_unlink=True)
if opts['figures']:
    BI.figures(0.65)

scene.render.engine = 'BLENDER_WORKBENCH'
sh = scene.display.shading
sh.light, sh.color_type = 'STUDIO', 'MATERIAL'
sh.show_shadows, sh.show_cavity = True, True
sh.shadow_intensity = .6
if opts['no_shadows']:      # for exterior diffs: Workbench stencil shadows react to inner walls
    sh.show_shadows = sh.show_cavity = False
scene.display.light_direction = (-.4, .5, .75)
sh.background_type, sh.background_color = 'VIEWPORT', (.03, .03, .035)
scene.render.resolution_x, scene.render.resolution_y = 1920, 1080
scene.render.resolution_percentage = 100
scene.render.image_settings.file_format = 'PNG'
scene.view_settings.view_transform = 'Standard'

cam = bpy.data.objects.new('Review camera', bpy.data.cameras.new('Review camera'))
scene.collection.objects.link(cam)
scene.camera = cam
cam.data.sensor_width, cam.data.sensor_fit = 36.0, 'HORIZONTAL'
cam.data.clip_start, cam.data.clip_end = .05, 2000


def aim(pos, target=None, yaw=None, pitch=0.0, lens=22.0, ortho=None):
    cam.location = pos
    if target is not None:
        d = Vector(target) - Vector(pos)
    else:
        y, p = math.radians(yaw), math.radians(pitch)
        d = Vector((math.cos(p) * math.cos(y), math.cos(p) * math.sin(y), math.sin(p)))
    cam.rotation_euler = d.to_track_quat('-Z', 'Y').to_euler()
    cam.data.type = 'ORTHO' if ortho else 'PERSP'
    if ortho:
        cam.data.ortho_scale = ortho
    cam.data.lens = lens


def show(hide):
    for o in bpy.data.objects:
        if o.type == 'MESH' and o.name.split('.')[0] != 'Bridge interior cavity':
            o.hide_render = hide(o)


def glazing(o):
    return o.name.startswith('Command deck viewport slits')


def above_room(o):
    if glazing(o) or o.name.startswith(('Command deck armoured roof', 'Command deck visor brow')):
        return True
    bb = [o.matrix_world @ Vector(c) for c in o.bound_box]
    lo = Vector([min(v[i] for v in bb) for i in range(3)])
    hi = Vector([max(v[i] for v in bb) for i in range(3)])
    over = hi.x > 8 and lo.x < 19 and abs(lo.y + hi.y) / 2 < 6
    return over and lo.z > BI.CEILING - .45


VIEWS = {
    # name: (hide rule, aim kwargs)
    '01-forward-station': (glazing, dict(pos=(15.5, .2, 19.2), yaw=6.5, pitch=-8)),
    '02-port-station': (glazing, dict(pos=(13.0, 3.25, 19.2), yaw=90, pitch=2)),
    '03-room-from-helm': (glazing, dict(pos=(16.5, -2.9, 19.5), target=(10.0, 2.2, 17.4), lens=15)),
    '04-room-from-hatch': (glazing, dict(pos=(9.9, -1.2, 19.4), target=(17.5, 1.5, 18.0), lens=15)),
    '05-cutaway': (above_room, dict(pos=(8.0, -11.0, 30.0), target=(13.4, 0.5, 17.4), lens=28)),
    '06-plan': (above_room, dict(pos=(13.45, 0, 40.0), target=(13.45, 0, 0), ortho=17.5)),
    '07-exterior-port-quarter': (lambda o: False, dict(pos=(100, 130, 100), target=(5, 0, 1), ortho=163)),
    '08-exterior-bridge': (lambda o: False, dict(pos=(60, 60, 50), target=(12, 0, 14), ortho=55)),
}
wanted = opts['views'].split(',') if opts['views'] else [v for v in VIEWS if not opts['exterior_only']
                                                       or v.startswith('07') or v.startswith('08')]
for name in wanted:
    hide, kw = VIEWS[name]
    show(hide)
    if name == '06-plan':
        cam.location = kw['pos']
        cam.rotation_euler = (0, 0, -math.pi / 2)        # bow up the frame
        cam.data.type, cam.data.ortho_scale = 'ORTHO', kw['ortho']
    else:
        aim(**kw)
    scene.render.filepath = str(out / (name + '.png'))
    bpy.ops.render.render(write_still=True)
    print('REVIEW', name, flush=True)
