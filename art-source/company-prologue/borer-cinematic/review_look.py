"""The Borer's look render: four half-size panels for one 2x2 review sheet (render-queue light lane).

  blender --background --factory-startup --python review_look.py --
      --borer DIR --shepherd DIR --out DIR [--samples 16] [--panels a,b] [--device GPU|CPU] [--dry]

DIR for --borer and --shepherd holds the exported build (borer.blend / shepherd.blend with their
manifest.json); the Talon comes from the committed geometry blend. Each panel is appended into a
fresh factory scene under the same neutral sun studio (key, fill, rim; dark world) and rendered
at 960x540, which compose_review.py tiles into one 1920x1080 sheet at 100%:
  hero      port-bow quarter, drives running, the slung mining laser on a rock 2.5 m off the nose
  under     from below the port bow: the sling, the four lift jets puffing, the skids and the keel
  cradle    the Shepherd's drone bay with Borers on cradles 1 and 3 and one lifted out over
            opening 2, all at the Shepherd's scale (root 0.25 / 0.78), launch-point anchors
  sizes     three Borers over the Shepherd at its scale: the 2D ground battle's proportion (1.9 m),
            as built (3.6 m) and the fleet-combat sprites' (11.7 m), left to right
  (stern and talon remain as panels on request: the drives astern; beside a Talon and a figure)
--dry builds every panel's scene and saves it under <out>/dry/ without rendering.
"""
from pathlib import Path
import argparse
import json
import math
import sys
import bpy
from mathutils import Matrix, Vector

HERE = Path(__file__).resolve().parent
TALON = HERE.parent / 'fighters-geometry' / 'talon'
ap = argparse.ArgumentParser()
ap.add_argument('--borer', default=str(HERE))
ap.add_argument('--shepherd', required=True)
ap.add_argument('--out', required=True)
ap.add_argument('--samples', type=int, default=16)
ap.add_argument('--panels', default='hero,under,cradle,sizes')
ap.add_argument('--device', default='GPU', choices=('GPU', 'CPU'))
ap.add_argument('--dry', action='store_true')
args = ap.parse_args(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])
BDIR, SDIR, OUT = Path(args.borer).resolve(), Path(args.shepherd).resolve(), Path(args.out).resolve()
bman = json.loads((BDIR / 'manifest.json').read_text())
sman = json.loads((SDIR / 'manifest.json').read_text())
tgeo = json.loads((TALON / 'geometry.json').read_text())
BMPU, SMPU, TMPU = bman['metres_per_unit'], sman['metres_per_unit'], tgeo['metres_per_unit']


def fresh():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    scene = bpy.context.scene
    scene.render.engine = 'CYCLES'
    prefs = bpy.context.preferences.addons['cycles'].preferences
    for kind in (('OPTIX', 'CUDA', 'METAL') if args.device == 'GPU' else ()):
        try:
            prefs.compute_device_type = kind
            prefs.get_devices()
            if any(d.type == kind for d in prefs.devices):
                for d in prefs.devices:
                    d.use = d.type == kind
                scene.cycles.device = 'GPU'
                break
        except TypeError:
            continue
    scene.cycles.samples = args.samples
    scene.cycles.use_denoising = True
    scene.render.resolution_x, scene.render.resolution_y, scene.render.resolution_percentage = 960, 540, 100
    scene.render.image_settings.file_format = 'PNG'
    scene.render.image_settings.color_mode = 'RGB'
    scene.view_settings.view_transform = 'AgX'
    world = bpy.data.worlds.new('Review studio')
    world.use_nodes = True
    world.node_tree.nodes['Background'].inputs[0].default_value = (.045, .047, .05, 1)
    world.node_tree.nodes['Background'].inputs[1].default_value = .6
    scene.world = world
    rig = bpy.data.collections.new('Review rig')
    scene.collection.children.link(rig)
    # Suns are scale-free, so one studio serves a panel in Borer units and one in metres.
    for name, az, el, energy, angle in (('Key', 40, 50, 3.2, 4), ('Fill', -140, 25, .8, 12), ('Rim', 170, 15, 1.6, 4)):
        ld = bpy.data.lights.new(name, 'SUN')
        ld.energy, ld.angle = energy, math.radians(angle)
        lo = bpy.data.objects.new(name, ld)
        rig.objects.link(lo)
        a, e = math.radians(az), math.radians(el)
        d = Vector((math.sin(a) * math.cos(e), math.cos(a) * math.cos(e), math.sin(e)))
        lo.rotation_euler = (-d).to_track_quat('-Z', 'Y').to_euler()
    cd = bpy.data.cameras.new('Review camera')
    cd.type, cd.clip_start, cd.clip_end = 'ORTHO', .1, 5000
    cam = bpy.data.objects.new('Review camera', cd)
    rig.objects.link(cam)
    scene.camera = cam
    return scene, rig, cam


def append(path, names):
    with bpy.data.libraries.load(str(path), link=False) as (src, dst):
        dst.collections = names
    for c in dst.collections:
        bpy.context.scene.collection.children.link(c)
    return dst.collections


def root_of(coll):
    return next(o for o in coll.all_objects if o.type == 'EMPTY' and o.parent is None)


def aim(cam, az, el, scale, target, dist=600):
    a, e = math.radians(az), math.radians(el)
    t = Vector(target)
    cam.location = t + dist * Vector((math.sin(a) * math.cos(e), math.cos(a) * math.cos(e), math.sin(e)))
    cam.rotation_euler = (t - cam.location).to_track_quat('-Z', 'Y').to_euler()
    cam.data.ortho_scale = scale


def material(name, rgb, rough=.8, metal=0.0):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    b = m.node_tree.nodes['Principled BSDF']
    b.inputs['Base Color'].default_value = (*rgb, 1)
    b.inputs['Roughness'].default_value = rough
    b.inputs['Metallic'].default_value = metal
    return m


def settle():
    for o in bpy.data.objects:
        if o.name == 'SWFX controls':
            o.update_tag()
    bpy.context.scene.frame_set(bpy.context.scene.frame_current)
    bpy.context.view_layer.update()


def borer_fx(beam):
    colls = append(BDIR / bman['asset'], [bman['append_collection'], bman['effects']['collection']])
    ctl = bpy.data.objects['SWFX controls']
    ctl['beam_t_ws001'] = 1.0 if beam else 0.0
    ctl['beam_charge_ws001'] = 1.0 if beam else 0.0
    return colls, ctl


def rock(rig, at, radius):
    bpy.ops.mesh.primitive_ico_sphere_add(subdivisions=4, radius=radius, location=at)
    ob = bpy.context.object
    ob.name = 'Review rock'
    for c in list(ob.users_collection):
        c.objects.unlink(ob)
    rig.objects.link(ob)
    tex = bpy.data.textures.new('Review rock noise', 'CLOUDS')
    tex.noise_scale = radius * .6
    d = ob.modifiers.new('Rough', 'DISPLACE')
    d.texture, d.strength = tex, radius * .35
    ob.data.materials.append(material('Review rock', (.09, .085, .08), .95))
    for p in ob.data.polygons:
        p.use_smooth = False
    return ob


def figure(rig, at, height):
    """A plain 1.8 m mannequin (legs, body, head) standing at `at`, in the panel's units."""
    m = material('Review figure', (.55, .55, .55), .7)
    h = height
    for name, z0, z1, r in (('legs', 0, .47 * h, .07 * h), ('body', .47 * h, .82 * h, .11 * h)):
        bpy.ops.mesh.primitive_cylinder_add(vertices=24, radius=r, depth=z1 - z0,
                                            location=(at[0], at[1], at[2] + (z0 + z1) / 2))
        ob = bpy.context.object
        ob.name = f'Review figure {name}'
        ob.data.materials.append(m)
        for c in list(ob.users_collection):
            c.objects.unlink(ob)
        rig.objects.link(ob)
    bpy.ops.mesh.primitive_uv_sphere_add(radius=.09 * h, location=(at[0], at[1], at[2] + .9 * h))
    ob = bpy.context.object
    ob.name = 'Review figure head'
    ob.data.materials.append(m)
    for c in list(ob.users_collection):
        c.objects.unlink(ob)
    rig.objects.link(ob)


def deck(rig, centre, size, z):
    bpy.ops.mesh.primitive_plane_add(size=size, location=(centre[0], centre[1], z))
    ob = bpy.context.object
    ob.name = 'Review deck'
    for c in list(ob.users_collection):
        c.objects.unlink(ob)
    rig.objects.link(ob)
    ob.data.materials.append(material('Review deck', (.16, .155, .15), .9))


def panel_hero(scene, rig, cam):
    borer_fx(beam=True)
    lens = Vector(bman['tool']['muzzle']['at'])
    reach = 10.0                                     # 2.5 m from the lens to the rock face
    rock(rig, (lens.x + reach + 2.6, 0, lens.z), 3.0)
    bpy.data.objects['SWFX controls']['beam_len_ws001'] = reach
    settle()
    aim(cam, 41, 24, 40, (4.5, 0, -2.6))


def panel_stern(scene, rig, cam):
    borer_fx(beam=False)
    settle()
    aim(cam, -48, 22, 30, (-1.8, 1.3, -.6))


def panel_cradle(scene, rig, cam):
    append(SDIR / sman['asset'], [sman['append_collection']])
    k = BMPU / SMPU
    seat = bman['cradle_seat']['at']
    lps = sman['drone_bay']['openings']
    # One appended Borer, instanced on each station (a second append of the same collection
    # would reuse the first): an instance's matrix is the root's placement.
    coll = append(BDIR / bman['asset'], [bman['append_collection']])[0]
    bpy.context.scene.collection.children.unlink(coll)
    for op in lps:
        at = Vector(op['launch_point_at'])
        if op['index'] == 2:                        # lifted out: pads a metre above the mouth
            at.z = op['mouth_at'][2] + 1.0 / SMPU
        inst = bpy.data.objects.new(f'Review Borer station {op["index"]}', None)
        inst.instance_type, inst.instance_collection = 'COLLECTION', coll
        rig.objects.link(inst)
        inst.matrix_world = Matrix.Translation(at) @ Matrix.Scale(k, 4) @ Matrix.Translation(-Vector(seat))
    bpy.context.view_layer.update()
    aim(cam, 30, 38, 50, (11.5, 18.0, -1.0), dist=900)


def panel_talon(scene, rig, cam):
    # Common scale, in metres: each root scaled by its own metres_per_unit, soles on z = 0.
    b = root_of(append(BDIR / bman['asset'], [bman['append_collection']])[0])
    b.matrix_world = Matrix.Scale(BMPU, 4) @ Matrix.Translation((0, 0, -bman['cradle_seat']['at'][2]))
    tc = append(TALON / 'talon-geometry.blend', [tgeo['collections']['model']])[0]
    t = bpy.data.objects.get(tgeo['root']['name']) or root_of(tc)
    tmin = tgeo['dimensions']['min'][2]
    t.matrix_world = Matrix.Translation((0, -7.5, 0)) @ Matrix.Scale(TMPU, 4) @ Matrix.Translation((0, 0, -tmin))
    figure(rig, (0.0, 3.4, 0.0), 1.8)
    deck(rig, (0, -3, 0), 40, 0.0)
    bpy.context.view_layer.update()
    aim(cam, 62, 18, 19.5, (1.1, -4.7, 1.4), dist=200)


def panel_under(scene, rig, cam):
    # From below the port bow: the slung laser, the four lift jets puffing, the skids and keel.
    borer_fx(beam=False)
    ctl = bpy.data.objects['SWFX controls']
    for k in list(ctl.keys()):
        if k.startswith('thr_rcs_') and k != 'thr_rcs_gain':
            ctl[k] = .35
    ctl['thr_rcs_gain'] = 4.0
    settle()
    aim(cam, 38, -24, 32, (0.5, 0, -2.3))


# The size question on one picture: three Borers over the Shepherd, at the 2D ground battle's
# proportion, as built, and at the fleet-combat sprites' proportion (geometry size_readings_m).
SIZES = [('ground 2D', 1.9), ('as built', 3.6), ('fleet combat', 11.7)]


def panel_sizes(scene, rig, cam):
    append(SDIR / sman['asset'], [sman['append_collection']])
    coll = append(BDIR / bman['asset'], [bman['append_collection']])[0]
    bpy.context.scene.collection.children.unlink(coll)
    seat = Vector(bman['cradle_seat']['at'])
    built = bman['size_metres_rest'][0]
    top = sman['bounds_units_rest'][1][2]
    for (label, length), x in zip(SIZES, (24.0, 6.0, -20.0)):     # left to right in this camera
        k = BMPU / SMPU * length / built
        inst = bpy.data.objects.new(f'Review Borer {label}', None)
        inst.instance_type, inst.instance_collection = 'COLLECTION', coll
        rig.objects.link(inst)
        inst.matrix_world = Matrix.Translation((x, 0, top + 4)) @ Matrix.Scale(k, 4) @ Matrix.Translation(-seat)
    bpy.context.view_layer.update()
    aim(cam, 22, 20, 126, (2.0, 0, 9.5), dist=1200)


PANELS = {'hero': panel_hero, 'under': panel_under, 'stern': panel_stern, 'cradle': panel_cradle,
          'talon': panel_talon, 'sizes': panel_sizes}
OUT.mkdir(parents=True, exist_ok=True)
for name in [p for p in args.panels.split(',') if p]:
    scene, rig, cam = fresh()
    PANELS[name](scene, rig, cam)
    if args.dry:
        (OUT / 'dry').mkdir(exist_ok=True)
        bpy.ops.wm.save_as_mainfile(filepath=str(OUT / 'dry' / f'{name}.blend'))
        print('LOOK dry', name)
        continue
    scene.render.filepath = str(OUT / f'{name}.png')
    bpy.ops.render.render(write_still=True)
    print('LOOK', name)
