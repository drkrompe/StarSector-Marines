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
# built state; 1 deployed). The Wolf sits on two fixed keel shoes; four braced
# wedges swing down off the lower hull sides to stop it rolling. Drivers turn
# each wedge hinge and aim its rams (wf_gear.expressions), and a sole anchor rides
# each wedge sole. Every sole anchor's local +X points down.
import wf_gear  # noqa: E402
KNOB = 'gear_down'
gear = geom['landing_gear']
G = gear['ground_z_units']
control = bpy.data.objects.new(f'{COLL} control', None)
control.empty_display_type, control.empty_display_size = 'CUBE', 2
control.location = (0, 0, 16)
bpy.context.scene.collection.objects.link(control)
control[KNOB] = 0.0
control.id_properties_ui(KNOB).update(min=0.0, max=1.0, soft_min=0.0, soft_max=1.0,
                                      description='landing gear: 0 stowed (flight, built), 1 deployed: the keel '
                                                  'shoes and every wedge sole on landing.ground_z_units')
DOWN = Matrix.Rotation(1.5707963267948966, 4, 'Y')
drivers, soles = [], []
for rec in gear['wedges']:
    for nm, path, idx, expr in wf_gear.expressions(rec):
        fc = bpy.data.objects[nm].driver_add(path, idx)
        d = fc.driver
        d.type = 'SCRIPTED'
        v = d.variables.new()
        v.name, v.type = 'k', 'SINGLE_PROP'
        v.targets[0].id_type, v.targets[0].id = 'OBJECT', control
        v.targets[0].data_path = f'["{KNOB}"]'
        d.expression = expr
        drivers.append({'object': nm, 'path': path, 'index': idx})
    # The wedge was modelled deployed (hinge angle 0), so the sole's deployed
    # place is a fixed offset in the hinge's own frame.
    h = bpy.data.objects[rec['hinge']]
    xm = (rec['x'][0] + rec['x'][1]) / 2
    a = bpy.data.objects.new(f"Anchor gear {rec['name'][len('Hull '):]} sole", None)
    a.empty_display_type, a.empty_display_size = 'SINGLE_ARROW', 1.0
    bpy.context.scene.collection.objects.link(a)
    a.parent = h
    a.matrix_parent_inverse = Matrix.Identity(4)
    a.matrix_basis = Matrix.Translation((xm - h.location.x, rec['sole_y'] - rec['H'][0], G - rec['H'][1])) @ DOWN
    soles.append({'part': rec['name'], 'anchor': a.name, 'moves': True, 'sole_z_deployed': G})
for shoe in gear['keel_shoes']:
    a = bpy.data.objects.new(f"Anchor gear {shoe['name'][len('Hull '):]} sole", None)
    a.empty_display_type, a.empty_display_size = 'SINGLE_ARROW', 1.0
    bpy.context.scene.collection.objects.link(a)
    a.matrix_world = Matrix.Translation(((shoe['x'][0] + shoe['x'][1]) / 2, 0, shoe['sole_z'])) @ DOWN
    soles.append({'part': shoe['name'], 'anchor': a.name, 'moves': False, 'sole_z_deployed': G})
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
    'landing': {'ground_z_units': G, 'keel_shoes': gear['keel_shoes'], 'wedges': gear['wedges'],
                'drivers': drivers, 'soles': soles,
                'note': 'the Wolf sits on its keel shoes (fixed); at gear_down 1 every wedge sole anchor '
                        'stands on ground_z_units too, in hull space: place the root that far above the '
                        'ground (times metres_per_unit). Rest is gear stowed.'},
    'lift_thrusters': gear['lift_thrusters'],
    'sources': {'geometry': 'art-source/company-prologue/wolf-geometry/build_wolf.py',
                'surface': 'art-source/company-prologue/' + ('wolf-merchant-surface (shared harness, service wear)'
                                                            if MERCHANT else 'wolf-surface (shared harness, battle wear)'),
                'built_from': Path(src).name if src else None},
}
(OUT / 'manifest.json').write_text(json.dumps(man, indent=1) + '\n', encoding='utf-8')
print('exported', OUT / ASSET, len(meshes), 'meshes', man['polygons'], 'polygons', man['images'], 'images')
