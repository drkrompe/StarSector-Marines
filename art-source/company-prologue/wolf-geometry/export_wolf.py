"""Package an open Wolf (P) build (normally the textured one) as the appendable asset.

  blender --background ../wolf-surface/build/skinned.blend --python export_wolf.py -- --out DIR

Runs wf_export.package on the open file with every image packed and writes
DIR/wolf-p.blend and DIR/manifest.json: the collection, root and anchors
collection names, metres_per_unit, bounds, mesh and polygon counts, and the
engine, mount and lamp records from geometry.json, which a shot and the
cinematic package (../wolf-cinematic) read.
"""
from pathlib import Path
import argparse
import importlib
import json
import sys
import bpy
from mathutils import Matrix, Vector

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import wf_export  # noqa: E402
importlib.reload(wf_export)

ap = argparse.ArgumentParser()
ap.add_argument('--out', required=True)
ap.add_argument('--variant', default='pirate', choices=('pirate', 'merchant'))
args = ap.parse_args(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])
OUT = Path(args.out).resolve()
OUT.mkdir(parents=True, exist_ok=True)
MERCHANT = args.variant == 'merchant'
COLL, ASSET = ('WOLF', 'wolf.blend') if MERCHANT else (wf_export.COLL, 'wolf-p.blend')
geom = json.loads((HERE / ('geometry-merchant.json' if MERCHANT else 'geometry.json')).read_text(encoding='utf-8'))
mpu = geom['metres_per_unit']
src = bpy.data.filepath

# Landing gear (round 8): the control empty and its gear_down knob (0 stowed, the
# built state; 1 deployed), driving every leg stage's slide straight down, and a
# sole anchor riding each pad. Local +X of a sole anchor points down.
KNOB = 'gear_down'
gear = geom['landing_gear']
control = bpy.data.objects.new(f'{COLL} control', None)
control.empty_display_type, control.empty_display_size = 'CUBE', 2
control.location = (0, 0, 16)
bpy.context.scene.collection.objects.link(control)
control[KNOB] = 0.0
control.id_properties_ui(KNOB).update(min=0.0, max=1.0, soft_min=0.0, soft_max=1.0,
                                      description='landing gear: 0 stowed (flight, built), 1 deployed: every '
                                                  'pad sole on landing.ground_z_units in hull space')
slides, soles = [], []
for leg in gear['legs']:
    for nm in leg['slides']:
        h = bpy.data.objects[nm]
        seat, units = h.location.z, h['slide_units']
        fc = h.driver_add('location', 2)
        d = fc.driver
        d.type = 'SCRIPTED'
        v = d.variables.new()
        v.name, v.type = 'k', 'SINGLE_PROP'
        v.targets[0].id_type, v.targets[0].id = 'OBJECT', control
        v.targets[0].data_path = f'["{KNOB}"]'
        d.expression = f'{seat!r} - k * {units!r}'
        slides.append({'slide': nm, 'axis': 'hull -Z', 'units_at_1': units})
    carrier = bpy.data.objects[leg['slides'][-1]]
    x, y = leg['xy']
    a = bpy.data.objects.new(f"Anchor gear {leg['name'][len('Hull '):]} sole", None)
    a.empty_display_type, a.empty_display_size = 'SINGLE_ARROW', 1.0
    bpy.context.scene.collection.objects.link(a)
    a.matrix_world = Matrix.Translation((x, y, leg['seat_z'])) @ Matrix.Rotation(1.5707963267948966, 4, 'Y')
    bpy.context.view_layer.update()
    inv = carrier.matrix_world.inverted()
    mw = a.matrix_world.copy()
    a.parent = carrier
    a.matrix_parent_inverse = inv
    a.matrix_world = mw
    soles.append({'leg': leg['name'], 'anchor': a.name, 'sole_z_stowed': leg['seat_z'],
                  'sole_z_deployed': gear['ground_z_units']})
bpy.context.view_layer.update()
wf_export.package(OUT / ASSET, mpu, pack_images=True, coll_name=COLL,
                  extra={'built_from': Path(src).name if src else 'unsaved', 'variant': args.variant})
coll = bpy.data.collections[COLL]
meshes = [o for o in coll.all_objects if o.type == 'MESH']
lo, hi = Vector((1e9,) * 3), Vector((-1e9,) * 3)
for o in meshes:
    for c in o.bound_box:
        w = o.matrix_world @ Vector(c)
        lo, hi = Vector(map(min, lo, w)), Vector(map(max, hi, w))
man = {
    'asset': ASSET, 'name': geom['hull']['name'], 'variant': args.variant,
    'append_collection': COLL, 'root': f'{COLL} | root', 'anchors_collection': f'{COLL} | anchors',
    'metres_per_unit': mpu, 'axes': geom['axes'],
    'bounds_units': [[round(v, 3) for v in lo], [round(v, 3) for v in hi]],
    'size_m': [round((b - a) * mpu, 1) for a, b in zip(lo, hi)],
    'meshes': len(meshes), 'polygons': sum(len(o.data.polygons) for o in meshes),
    'images': len([i for i in bpy.data.images if i.source == 'FILE']),
    'engines': geom['engines'], 'turrets': geom['turrets'], 'hardpoints': geom['hardpoints'],
    'vfx_mounts': geom['vfx_mounts'], 'weapon_slots': geom['weapon_slots'],
    'removed_weapon_slots': geom['hull']['removed_weapon_slots'], 'loadout': geom['hull']['loadout'],
    'built_as': geom['hull'].get('built_as', {}),
    'running_lights': geom['running_lights'],
    'control': {'object': f'{COLL} control', 'knobs': {KNOB: '0 stowed (flight, built) .. 1 deployed'}},
    'landing': {'ground_z_units': gear['ground_z_units'], 'legs': gear['legs'], 'slides': slides, 'soles': soles,
                'note': 'rest is gear stowed; at gear_down 1 every sole anchor stands on ground_z_units in hull '
                        'space: place the root that far above the ground (times metres_per_unit)'},
    'lift_thrusters': gear['lift_thrusters'],
    'sources': {'geometry': 'art-source/company-prologue/wolf-geometry/build_wolf.py',
                'surface': 'art-source/company-prologue/' + ('wolf-merchant-surface (shared harness, service wear)'
                                                            if MERCHANT else 'wolf-surface (shared harness, battle wear)'),
                'built_from': Path(src).name if src else None},
}
(OUT / 'manifest.json').write_text(json.dumps(man, indent=1) + '\n', encoding='utf-8')
print('exported', OUT / ASSET, len(meshes), 'meshes', man['polygons'], 'polygons', man['images'], 'images')
