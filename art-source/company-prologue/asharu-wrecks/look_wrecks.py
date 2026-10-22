"""The look still for the wreck kit: the real Asharu debris field (battle_blockout.FIELD, posed at S4's
time) drawn with the kit swapped into every box through wreck_kit.pick, under the battle's sun,
from S4's camera position, with the kit's derelict platform in place of the box blockout. The
pirates are left out: this judges the wrecks, not the shot.

  sh ../../blender.sh --background --factory-startup --python look_wrecks.py -- --kit <asharu-wrecks.blend>
      --out DIR [--views wide,close] [--samples 16] [--half] [--layout]

--layout writes no render: it prints the swap table and what each view frames (for --check-style
review without a GPU). --workbench renders the views in Workbench, a framing check for the
session, never an owner review image. Views:
  wide   S4's opening: 28 mm from K2_CAM onto K2_AIM, the whole field with the desert below
  close     110 mm from the same place onto the Falcon (P)'s cover and the wrecks round it
The derelict platform is the kit's station piece (wreck_kit.station), placed in the platform's frame."""
import argparse
import math
import sys
from pathlib import Path

import bpy
from mathutils import Matrix, Vector

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE.parent / 'asharu-battle'))
import wreck_kit  # noqa: E402

argv = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
ap = argparse.ArgumentParser()
ap.add_argument('--kit', required=True)
ap.add_argument('--out', required=True)
ap.add_argument('--views', default='wide,close')
ap.add_argument('--samples', type=int, default=16)
ap.add_argument('--half', action='store_true')
ap.add_argument('--percent', type=int, default=0, help='resolution percentage override (internal checks)')
ap.add_argument('--cpu', action='store_true')
ap.add_argument('--layout', action='store_true')
ap.add_argument('--workbench', action='store_true', help='internal framing check: Workbench, never an owner image')
a = ap.parse_args(argv)
a.kit, a.out = str(Path(a.kit).resolve()), str(Path(a.out).resolve())   # before the kit is opened: Blender resolves relative to the .blend

_argv, sys.argv = sys.argv, ['look_wrecks']
import battle_blockout as B  # noqa: E402
sys.argv = _argv

T = B.K2_T
VIEWS = {
    'wide': dict(cam=B.K2_CAM, aim=B.K2_AIM, lens=28.0),
    'close': dict(cam=B.K2_CAM, aim=None, lens=110.0),
}


def platform_matrix(c, r):
    rz = Matrix.Rotation(math.radians(B.PLATFORM_HD), 3, 'Z')
    return Matrix.Translation(Vector(B.on_platform(c))) @ (rz @ Matrix(r)).to_4x4()


def main():
    bpy.ops.wm.open_mainfile(filepath=a.kit)
    sc = bpy.context.scene
    kit = {o.name: o for o in bpy.data.objects if o.get('wk_name')}
    for o in kit.values():
        for c in list(o.users_collection):
            c.objects.unlink(o)
    st_name, st_src = wreck_kit.station()
    print('STATION', st_name, 'current' if wreck_kit.station_current(B.platform_parts()) else 'STALE', st_src['counts'])
    station = bpy.data.objects.new('Platform station', kit[st_name].data)
    station.matrix_world = (Matrix.Translation(Vector(B.PLATFORM))
                            @ Matrix.Rotation(math.radians(B.PLATFORM_HD), 4, 'Z'))
    sc.collection.objects.link(station)
    field = bpy.data.collections.new('Field')
    sc.collection.children.link(field)
    close_aim = None
    for i, piece in enumerate(B.FIELD):
        for j, (c, half, r) in enumerate(piece.boxes_at(T)):
            p = wreck_kit.pick(half, i * 31 + j)
            ob = bpy.data.objects.new('Field %d.%d %s' % (i, j, p.name), kit[p.object].data)
            ob.matrix_world = platform_matrix(c, r) @ Matrix(p.local).to_4x4()
            field.objects.link(ob)
            print('SWAP %-20s %d -> %-22s fill %s' % (piece.name, j, p.name, p.fill))
            if piece.name == 'cover falcon-p' and j == 0:
                close_aim = B.on_platform(c)
    VIEWS['close']['aim'] = close_aim or B.K2_AIM
    # light: the battle's sun, and the desert's bounce from below
    el, az = math.radians(B.SUN_EL), math.radians(B.SUN_AZ)
    sun_dir = Vector((math.cos(el) * math.cos(az), math.cos(el) * math.sin(az), math.sin(el)))
    sun = bpy.data.objects.new('Asharu | Corvus', bpy.data.lights.new('Asharu | Corvus', 'SUN'))
    sun.data.energy, sun.data.color, sun.data.angle = 4.6, (1.0, .96, .88), math.radians(.5)
    sun.rotation_euler = sun_dir.to_track_quat('Z', 'Y').to_euler()
    sc.collection.objects.link(sun)
    bounce = bpy.data.objects.new('Asharu | bounce', bpy.data.lights.new('Asharu | bounce', 'SUN'))
    bounce.data.energy, bounce.data.color, bounce.data.angle = .45, (.78, .6, .42), math.radians(60)
    bounce.rotation_euler = Vector((0, 0, -1)).to_track_quat('Z', 'Y').to_euler()
    bounce.data.use_shadow = False
    sc.collection.objects.link(bounce)
    # world: black sky, the desert filling everything below the horizon
    w = bpy.data.worlds.new('Asharu')
    sc.world = w
    w.use_nodes = True
    nt = w.node_tree
    for nd in list(nt.nodes):
        nt.nodes.remove(nd)
    tc = nt.nodes.new('ShaderNodeTexCoord')
    sep = nt.nodes.new('ShaderNodeSeparateXYZ')
    nt.links.new(tc.outputs['Generated'], sep.inputs['Vector'])
    ramp = nt.nodes.new('ShaderNodeValToRGB')
    horizon = -math.sin(math.radians(17.6))
    ramp.color_ramp.elements[0].position = 0.0
    ramp.color_ramp.elements[0].color = (.30, .21, .13, 1)
    ramp.color_ramp.elements[1].position = 1.0
    ramp.color_ramp.elements[1].color = (0, 0, 0, 1)
    mid = ramp.color_ramp.elements.new(horizon * -0.0 + .5 * (1 + horizon) - .002)
    mid.color = (.42, .33, .24, 1)
    top = ramp.color_ramp.elements.new(.5 * (1 + horizon) + .004)
    top.color = (.0008, .0009, .0012, 1)
    mr = nt.nodes.new('ShaderNodeMapRange')
    mr.inputs['From Min'].default_value, mr.inputs['From Max'].default_value = -1, 1
    nt.links.new(sep.outputs['Z'], mr.inputs['Value'])
    nt.links.new(mr.outputs['Result'], ramp.inputs['Fac'])
    bg = nt.nodes.new('ShaderNodeBackground')
    bg.inputs['Strength'].default_value = 1.0
    nt.links.new(ramp.outputs['Color'], bg.inputs['Color'])
    out = nt.nodes.new('ShaderNodeOutputWorld')
    nt.links.new(bg.outputs['Background'], out.inputs['Surface'])
    # render
    sc.render.engine = 'CYCLES'
    sc.cycles.samples = a.samples
    sc.cycles.use_denoising = True
    try:
        prefs = bpy.context.preferences.addons['cycles'].preferences
        for kind in ('OPTIX', 'CUDA', 'METAL', 'HIP'):
            try:
                prefs.compute_device_type = kind
                prefs.get_devices()
                if any(d.type == kind for d in prefs.devices):
                    for d in prefs.devices:
                        d.use = d.type == kind
                    sc.cycles.device = 'GPU'
                    break
            except TypeError:
                continue
    except KeyError:
        pass
    if a.workbench:
        sc.render.engine = 'BLENDER_WORKBENCH'
        sc.display.shading.light = 'STUDIO'
        sc.display.shading.color_type = 'MATERIAL'
    sc.render.resolution_x, sc.render.resolution_y = 1920, 1080
    sc.render.resolution_percentage = a.percent or (50 if a.half else 100)
    if a.cpu:
        sc.cycles.device = 'CPU'
    sc.view_settings.view_transform = 'AgX'
    sc.view_settings.look = 'AgX - Base Contrast'
    cam = bpy.data.objects.new('Look camera', bpy.data.cameras.new('Look camera'))
    sc.collection.objects.link(cam)
    sc.camera = cam
    cam.data.sensor_width, cam.data.clip_start, cam.data.clip_end = 36.0, 1.0, 4.0e6
    outdir = Path(a.out)
    outdir.mkdir(parents=True, exist_ok=True)
    for n, view in enumerate(filter(None, a.views.split(',')), 1):
        v = VIEWS[view]
        cam.location = Vector(v['cam'])
        d = (Vector(v['aim']) - cam.location).normalized()
        cam.rotation_euler = d.to_track_quat('-Z', 'Y').to_euler()
        cam.data.lens = v['lens']
        print('VIEW', view, 'cam', tuple(round(x) for x in v['cam']), 'aim', tuple(round(x) for x in v['aim']),
              'lens', v['lens'], 'range %.0f m' % (Vector(v['aim']) - cam.location).length)
        if a.layout:
            continue
        sc.render.filepath = str(outdir / ('%02d-%s.png' % (n, view)))
        bpy.ops.render.render(write_still=True)


main()
