"""Render the turret catalogue: every weapon on its barbette, studio-lit, 1920x1080.

  blender --background --factory-startup --python catalog.py -- --out DIR [--mpu 0.99] [--detail near]
         [--only heavyac,vulcan] [--samples 32]

Writes catalog-overview.png (every weapon, three-quarter from above), and one
close three-quarter per weapon as <weapon>.png. Ring radii match the Legion's
sprite rings (12.5 / 9.5 / 5.5 units); the scene is in hull units at --mpu.
"""
import argparse
import math
import sys
from pathlib import Path
import bpy
from mathutils import Vector

sys.path.insert(0, str(Path(__file__).resolve().parent))
import turret_kit as tk

argv = sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else []
ap = argparse.ArgumentParser()
ap.add_argument('--out', required=True)
ap.add_argument('--mpu', type=float, default=.99)
ap.add_argument('--detail', default='near')
ap.add_argument('--only', default='')
ap.add_argument('--samples', type=int, default=32)
ap.add_argument('--no-singles', action='store_true')
ap.add_argument('--top', action='store_true', help='also orthographic top and side views of each, trained dead ahead, for review_sheet.py')
args = ap.parse_args(argv)
OUT = Path(args.out)
OUT.mkdir(parents=True, exist_ok=True)

bpy.ops.wm.read_factory_settings(use_empty=True)
scene = bpy.context.scene
scene.render.engine = 'CYCLES'
scene.cycles.samples = args.samples
scene.cycles.use_denoising = True
scene.render.resolution_x, scene.render.resolution_y, scene.render.resolution_percentage = 1920, 1080, 100
scene.view_settings.view_transform = 'AgX'


def material(name, color, metallic=0.0, roughness=.6):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    b = m.node_tree.nodes['Principled BSDF']
    b.inputs['Base Color'].default_value = (*color, 1)
    b.inputs['Metallic'].default_value = metallic
    b.inputs['Roughness'].default_value = roughness
    return m


MATS = {'armour': material('XIV orange armour', (.62, .19, .055), .25, .55),
        'plate': material('XIV grey hull plating', (.30, .27, .25), .35, .6),
        'steel': material('Gunmetal machinery', (.16, .16, .17), .7, .5),
        'dark': material('Recess and machinery', (.035, .035, .04), .5),
        'glass': material('Sight glass', (.03, .05, .06), .2, .1)}
DECK = material('Deck', (.22, .21, .2), .3, .7)

world = bpy.data.worlds.new('w')
scene.world = world
world.use_nodes = True
world.node_tree.nodes['Background'].inputs[0].default_value = (.74, .76, .78, 1)
world.node_tree.nodes['Background'].inputs[1].default_value = .55
for name, rot, energy in (('Key', (50, 0, -35), 4.0), ('Fill', (65, 0, 140), 1.2), ('Rim', (20, 0, 200), 2.0)):
    d = bpy.data.lights.new(name, 'SUN')
    d.energy = energy
    d.angle = math.radians(3)
    ob = bpy.data.objects.new(name, d)
    scene.collection.objects.link(ob)
    ob.rotation_euler = [math.radians(a) for a in rot]

RING = {'LARGE': 12.5, 'MEDIUM': 9.5, 'SMALL': 5.5}
SIZE = {'vulcan': 'SMALL', 'lightac': 'SMALL', 'lightmg': 'SMALL', 'none': 'SMALL',
        'squall': 'LARGE', 'hurricane': 'LARGE', 'devastator': 'LARGE', 'mark9': 'LARGE', 'hellbore': 'LARGE',
        'hephaestus': 'LARGE', 'needler': 'LARGE', 'typhoon': 'LARGE',
        'harpoonpod': 'MEDIUM', 'sabotpod': 'MEDIUM', 'salamanderpod': 'SMALL', 'annihilatorpod': 'MEDIUM'}
names = [n for n in tk.WEAPONS if not args.only or n in args.only.split(',')]
cols = 6
pitch = 64 if args.top else 34
placed = []
for i, w in enumerate(names):
    r = RING[tk.sprite(w)['size'] if tk.WEAPONS[w]['house'] in ('sprite', 'hand') else SIZE.get(w, 'MEDIUM')]
    x, y = (i % cols)*pitch, -(i//cols)*pitch
    lift = .22*r
    tk.barbette(f'Weapon {w} barbette', r, lift, MATS, args.mpu, args.detail, foot=1.0, location=(x, y, 0))
    piv = bpy.data.objects.new(f'Weapon {w} pivot', None)
    scene.collection.objects.link(piv)
    piv.location = (x, y, lift)
    piv.rotation_euler = (0, 0, 0 if args.top else math.radians(-25))
    t = tk.build(w, f'Weapon {w}', piv, r, MATS, args.mpu, args.detail)
    t['elevation'].rotation_euler = (0, 0 if args.top else math.radians(-8), 0)
    placed.append((w, Vector((x, y, lift)), r, t))
    print(f'{w}: {len(t["parts"])} objects, '
          f'{sum(len(p.data.polygons) for p in t["parts"])} faces, {len(t["muzzles"])} muzzles')

# A deck under everything.
bpy.ops.mesh.primitive_plane_add(size=1, location=((cols-1)*pitch/2, -(len(names)//cols)*pitch/2, -.5))
deck = bpy.context.object
deck.scale = (cols*pitch+60, (len(names)//cols+1)*pitch+60, 1)
deck.data.materials.append(DECK)


def shoot(name, target, dist, lens=50, elev=32, azim=-55):
    cam_data = bpy.data.cameras.new(name)
    cam_data.lens = lens
    cam = bpy.data.objects.new(name, cam_data)
    scene.collection.objects.link(cam)
    a, e = math.radians(azim), math.radians(elev)
    cam.location = target+Vector((dist*math.cos(e)*math.cos(a), dist*math.cos(e)*math.sin(a), dist*math.sin(e)))
    cam.rotation_euler = (target-cam.location).to_track_quat('-Z', 'Y').to_euler()
    cam_data.clip_end = 5000
    scene.camera = cam
    scene.render.filepath = str(OUT/(name+'.png'))
    bpy.ops.render.render(write_still=True)


rows = (len(names)+cols-1)//cols
centre = Vector(((cols-1)*pitch/2, -(rows-1)*pitch/2, 0))
shoot('catalog-overview', centre, 2.2*max(cols*pitch*.62, rows*pitch), lens=50, elev=38)
if not args.no_singles:
    for w, p, r, t in placed:
        shoot(w, p+Vector((.8*r, 0, .3*r)), 8.4*r, lens=50, elev=26, azim=-60)
if args.top:
    scene.render.resolution_x = scene.render.resolution_y = 1080
    # The top and side views show the turret alone on a transparent film, so
    # review_sheet.py can score the top silhouette against the sprite's alpha.
    scene.render.film_transparent = True
    for ob in scene.objects:
        if ob.name.endswith(' barbette') or ob.name.endswith(' plinth') or ob == deck:
            ob.hide_render = True
    d = bpy.data.lights.new('Side fill', 'SUN')
    d.energy = 3.0
    side_fill = bpy.data.objects.new('Side fill', d)
    scene.collection.objects.link(side_fill)
    side_fill.rotation_euler = (math.radians(70), 0, math.radians(-20))
    side_fill.hide_render = True
    meshes = [ob for ob in scene.objects if ob.type == 'MESH' and not ob.hide_render]

    def centre(ob):
        c = sum((ob.matrix_world @ Vector(v) for v in ob.bound_box), Vector()) / 8
        return c
    centres = {ob.name: centre(ob) for ob in meshes}
    for w, p, r, t in placed:
        # This turret only: a neighbour in the catalogue grid would otherwise
        # reach into the frame and into the silhouette score.
        for ob in meshes:
            c = centres[ob.name]
            ob.hide_render = math.hypot(c.x-p.x, c.y-p.y) > 2.6*r
        cam_data = bpy.data.cameras.new(w+'-top')
        cam_data.type = 'ORTHO'
        cam_data.ortho_scale = 2*r*3.2/1.0
        cam = bpy.data.objects.new(w+'-top', cam_data)
        scene.collection.objects.link(cam)
        cam.location = p+Vector((0, 0, 60))
        cam.rotation_euler = (0, 0, -math.pi/2)       # +X (line of fire) up the frame
        scene.camera = cam
        scene.render.filepath = str(OUT/(w+'-top.png'))
        bpy.ops.render.render(write_still=True)
        side = bpy.data.cameras.new(w+'-side')
        side.type = 'ORTHO'
        side.ortho_scale = cam_data.ortho_scale
        cs = bpy.data.objects.new(w+'-side', side)
        scene.collection.objects.link(cs)
        cs.location = p+Vector((0, -60, .2*r))
        cs.rotation_euler = (math.pi/2, 0, 0)          # from starboard, the line of fire to the right
        side.clip_start, side.clip_end = 60-1.7*r, 60+1.7*r   # this turret only, not the row behind it
        scene.camera = cs
        scene.render.filepath = str(OUT/(w+'-side.png'))
        side_fill.hide_render = False                  # the key light is on the far side
        bpy.ops.render.render(write_still=True)
        side_fill.hide_render = True
    scene.render.resolution_x, scene.render.resolution_y = 1920, 1080
