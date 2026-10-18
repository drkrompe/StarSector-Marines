"""Stand the Hermes in the Second Wind's port boat bay, at both hulls' own
scales, and measure the fit. Run inside Blender on the Second Wind's blend
(read only; this script never saves it):

  blender --background ../second-wind-geometry/second-wind-geometry.blend \
      --python bay_fit.py -- [--out DIR] [--samples N]

Each hull keeps its own metres_per_unit (geometry.json); the Hermes is
appended from hermes-geometry.blend and scaled by hermes_mpu / sw_mpu so
both are in true relative size. The bay's clear box is measured from the
Second Wind's named bay objects (interior wall, level floor, ceiling,
jambs, coamings), not copied from sw_hull.build_bays, so a change to the
bay shows up here. The Hermes stands on its own gear on the bay floor,
bow forward, centred between the jambs and across the bay's depth.

Writes bay-fit-*.png (1920x1080) and prints a JSON fit record that
compose_review.py folds into geometry.json and the bay-fit sheet.
"""
from pathlib import Path
import argparse
import json
import math
import sys
import bpy
from mathutils import Vector

HERE = Path(__file__).resolve().parent
SW = HERE.parent/'second-wind-geometry'
ap = argparse.ArgumentParser()
ap.add_argument('--out')
ap.add_argument('--samples', type=int, default=32)
ap.add_argument('--gear', choices=('up', 'down'), default='down')
args = ap.parse_args(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])
OUT = Path(args.out).resolve() if args.out else HERE/'renders'
OUT.mkdir(parents=True, exist_ok=True)

hg = json.loads((HERE/'geometry.json').read_text(encoding='utf-8'))
sg = json.loads((SW/'geometry.json').read_text(encoding='utf-8'))
H_MPU, SW_MPU = hg['metres_per_unit'], sg['metres_per_unit']
K = H_MPU/SW_MPU


def bounds(obs):
    bpy.context.view_layer.update()
    lo, hi = Vector((1e9,)*3), Vector((-1e9,)*3)
    for ob in obs:
        if ob.type != 'MESH':
            continue
        for c in ob.bound_box:
            w = ob.matrix_world @ Vector(c)
            lo = Vector(map(min, lo, w)); hi = Vector(map(max, hi, w))
    return lo, hi


def port(prefix):
    """The port-side (y > 0) instance of a mirrored bay part."""
    for ob in bpy.data.objects:
        if ob.name.split('.')[0] == prefix:
            lo, hi = bounds([ob])
            if lo.y > 0:
                yield ob, lo, hi


# --- The bay's clear box, in Second Wind units -------------------------------
wall = bounds([bpy.data.objects['Port bay interior']])[1].y
floor_top = max(hi.z for _, lo, hi in port('Bay level floor'))
ceiling = min(lo.z for _, lo, hi in port('Bay ceiling'))
jambs = sorted(port('Bay jamb'), key=lambda t: t[1].x)
x_aft, x_fore = jambs[0][2].x, jambs[-1][1].x
coamings = sorted(port('Bay coaming'), key=lambda t: t[1].z)
sill, lintel = coamings[0][2].z, coamings[-1][1].z
mouth = max(hi.y for _, lo, hi in coamings)
bay = {'x': [round(x_aft, 2), round(x_fore, 2)], 'y_wall': round(wall, 2), 'y_mouth': round(mouth, 2),
       'z_floor': round(floor_top, 2), 'z_ceiling': round(ceiling, 2), 'z_sill': round(sill, 2), 'z_lintel': round(lintel, 2)}
bay_m = {'length': (x_fore - x_aft)*SW_MPU, 'depth': (mouth - wall)*SW_MPU,
         'clear_height': (ceiling - floor_top)*SW_MPU, 'mouth_height': (lintel - max(sill, floor_top))*SW_MPU}

# --- Append the Hermes and stand it in the bay -------------------------------
with bpy.data.libraries.load(str(HERE/'hermes-geometry.blend'), link=False) as (src, dst):
    dst.objects = [n for n in src.objects]
coll = bpy.data.collections.new('Hermes in port bay')
bpy.context.scene.collection.children.link(coll)
hermes = [ob for ob in dst.objects if ob and ob.type in ('MESH', 'EMPTY')]
for ob in hermes:
    coll.objects.link(ob)
for ob in dst.objects:
    if ob and ob.type not in ('MESH', 'EMPTY'):
        bpy.data.objects.remove(ob, do_unlink=True)
if args.gear == 'up':
    for ob in hermes:
        if ob.name.startswith('Gear ') and ob.name.endswith(' hinge') and 'retract_angle_deg' in ob:
            ob.rotation_euler[1] = math.radians(ob['retract_angle_deg'])
fit = bpy.data.objects.new('Hermes fit (scale hermes_mpu / sw_mpu)', None)
coll.objects.link(fit)
for ob in hermes:
    if ob.parent is None:
        ob.parent = fit
# The starboard bay's sealed sectional door shows where a closed door stands
# inside the mouth; the port bay mirrors it.
door = bpy.data.objects.get('Starboard bay sealed sectional door')
door_y = -bounds([door])[1].y if door else None   # its inner face
inner = door_y if door_y else mouth   # stowed behind a closed door when the bay has one
lo, hi = bounds(hermes)            # Hermes units, at the origin
cx, cy = (lo.x + hi.x)/2, (lo.y + hi.y)/2
# Standing on its lowest point: the pad soles (GROUND) with the gear down.
fit.scale = (K, K, K)
fit.location = ((x_aft + x_fore)/2 - cx*K, (wall + inner)/2 - cy*K, floor_top - lo.z*K)
hlo, hhi = bounds(hermes)
margins_m = {'fore_and_aft_each_end': round(((x_fore - x_aft) - (hhi.x - hlo.x))/2*SW_MPU, 2),
             'inboard_wall': round((hlo.y - wall)*SW_MPU, 2),
             'outboard_mouth': round((mouth - hhi.y)*SW_MPU, 2),
             'door_line': round((inner - hhi.y)*SW_MPU, 2),
             'overhead': round((ceiling - hhi.z)*SW_MPU, 2),
             'under_lintel': round((lintel - hhi.z)*SW_MPU, 2)}
hermes_m = {'length': round((hhi.x - hlo.x)*SW_MPU, 2), 'beam': round((hhi.y - hlo.y)*SW_MPU, 2),
            'height_gear_' + args.gear: round((hhi.z - hlo.z)*SW_MPU, 2)}
cradles = sorted((b for b in (bounds([o]) for o in bpy.data.objects if o.name.startswith('Empty maintenance cradle')) if b[0].y > 0),
                 key=lambda b: b[0].x)
between = [round(c[1].x, 2) for c in cradles[:1]] + [round(c[0].x, 2) for c in cradles[1:]]
record = {
    'metres_per_unit': {'hermes': H_MPU, 'second_wind': SW_MPU, 'hermes_units_per_sw_unit': round(K, 4)},
    'bay_clear_sw_units': bay,
    'bay_clear_m': {k: round(v, 2) for k, v in bay_m.items()},
    'hermes_m': hermes_m,
    'margins_m': margins_m,
    'fits': all(v >= 0 for v in margins_m.values()),
    'stance': 'on its own gear on the bay floor, bow forward, between the two cradle beams '
              f'(their inner faces at SW x {between})' if len(cradles) == 2 else 'on its own gear on the bay floor',
    'door_line_sw_y': round(door_y, 2) if door_y else None,
    'depth_with_door_closed_m': round((door_y - wall)*SW_MPU, 2) if door_y else None,
    'fits_with_door_closed': bool(door_y and hhi.y <= door_y),
    'overhang_past_door_line_m': round(max(0.0, hhi.y - door_y)*SW_MPU, 2) if door_y else None,
    'clear_of_cradles': len(cradles) == 2 and hlo.x > cradles[0][1].x and hhi.x < cradles[1][0].x,
}
print('BAYFIT ' + json.dumps(record))

# --- Review renders ----------------------------------------------------------
scene = bpy.context.scene
for ob in list(scene.objects):
    if ob.type in ('CAMERA', 'LIGHT'):
        bpy.data.objects.remove(ob, do_unlink=True)
scene.render.engine = 'CYCLES'
scene.cycles.samples = args.samples
scene.cycles.use_denoising = True
scene.render.resolution_x, scene.render.resolution_y = 1920, 1080
scene.render.resolution_percentage = 100
scene.render.film_transparent = False
scene.view_settings.view_transform = 'AgX'
if scene.world is None:
    scene.world = bpy.data.worlds.new('Review')
scene.world.use_nodes = True
bg = scene.world.node_tree.nodes['Background']
bg.inputs[0].default_value = (.74, .76, .78, 1)
bg.inputs[1].default_value = .8
bpy.ops.object.light_add(type='SUN', location=(0, 0, 50))
sun = bpy.context.object
sun.data.energy = 3.5
sun.rotation_euler = (math.radians(40), math.radians(-25), math.radians(60))
# A lamp inside the bay, so the interior reads through the mouth.
bpy.ops.object.light_add(type='AREA', location=((x_aft + x_fore)/2, (wall + mouth)/2, ceiling - .3))
inside = bpy.context.object
inside.data.energy = 1500
inside.data.size = (x_fore - x_aft)*.8
cx_bay, cy_bay, cz_bay = (x_aft + x_fore)/2, (wall + mouth)/2, (floor_top + ceiling)/2
VIEWS = [  # name, position, target, ortho scale, clip_start
    ('bay-fit-port', (cx_bay, 120, cz_bay), (cx_bay, 0, cz_bay), 34, .1),
    ('bay-fit-quarter', (cx_bay + 70, 95, 45), (cx_bay, cy_bay, cz_bay), 110, .1),
    ('bay-fit-plan', (cx_bay, cy_bay, 100), None, 34, 100 - (ceiling - .05)),
    ('bay-fit-section', (200, cy_bay, cz_bay), (0, cy_bay, cz_bay), 22, 200 - (x_fore - .05)),
]
for name, pos, target, scale, clip in VIEWS:
    bpy.ops.object.camera_add(location=pos)
    cam = bpy.context.object
    cam.rotation_euler = (0, 0, 0) if target is None else (Vector(target) - cam.location).to_track_quat('-Z', 'Y').to_euler()
    cam.data.type = 'ORTHO'
    cam.data.ortho_scale = scale
    cam.data.clip_start = clip
    cam.data.clip_end = 1000
    scene.camera = cam
    scene.render.filepath = str(OUT/(name + '.png'))
    bpy.ops.render.render(write_still=True)
(OUT/'bay-fit.json').write_text(json.dumps(record, indent=2) + '\n', encoding='utf-8')
