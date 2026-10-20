"""Package a textured fighter build for shots: verify its gear knob, save it flying, write a manifest.

  blender -b <surface>/build/skinned.blend --python package_build.py -- --fighter talon --out DIR

Opens the surfacing build (textured, images packed, built gear down), and checks what a shot relies
on, after it is appended: the "<Name> control" empty drives the gear (at gear_down 1 the feet stand
on the parked deck the geometry recorded, within 1 cm; at 0 every hinge sits at its stowed angle),
and no image is external. It then sets the knob to 0 (flying), drops the review furniture and saves
DIR/<id>.blend, with DIR/<id>-manifest.json: the root, control, knob, hinges, deck z, feet, the
blend's sha256 and the geometry.json it was built from.
"""
from pathlib import Path
import argparse
import hashlib
import json
import math
import sys
import bpy
from mathutils import Vector

HERE = Path(__file__).resolve().parent
ap = argparse.ArgumentParser()
ap.add_argument('--fighter', required=True)
ap.add_argument('--out', required=True)
args = ap.parse_args(sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else [])
OUT = Path(args.out)
GEOM = json.loads((HERE/args.fighter/'geometry.json').read_text(encoding='utf-8'))
gear = GEOM['landing_gear']
mpu = GEOM['metres_per_unit']
control = bpy.data.objects[gear['control']]


def pose(k):
    control['gear_down'] = k
    control.update_tag()
    bpy.context.view_layer.update()
    return bpy.context.evaluated_depsgraph_get()


def lowest(ob, dg):
    ev = ob.evaluated_get(dg)
    me = ev.to_mesh()
    z = min((ev.matrix_world @ v.co).z for v in me.vertices)
    ev.to_mesh_clear()
    return z


report = {}
dg = pose(1.0)
deck = GEOM['parked_check']['deck_plane_units']['z0']
feet = {n: round((lowest(bpy.data.objects[n], dg)-deck)*mpu, 4) for n in GEOM['parked_check']['feet']}
report['feet_above_deck_m_at_1'] = feet
if max(abs(v) for v in feet.values()) > .01:
    raise SystemExit(f'feet off the deck at gear_down 1: {feet}')
dg = pose(0.0)
drift = max(abs(bpy.data.objects[h['name']].evaluated_get(dg).rotation_euler[1]-math.radians(h['stowed_deg']))
            for h in gear['hinges'])
report['hinge_drift_rad_at_0'] = round(drift, 6)
if drift > 1e-4:
    raise SystemExit(f'hinges not at their stowed angles at gear_down 0 (drift {drift})')
for o in list(bpy.data.objects):
    if o.name.startswith(('Pilot proxy', 'Scale figure', 'Parked deck')) or o.type in ('CAMERA', 'LIGHT'):
        bpy.data.objects.remove(o)
for img in bpy.data.images:
    if img.source == 'FILE' and not img.packed_file:
        img.pack()
external = [i.name for i in bpy.data.images if i.source == 'FILE' and not i.packed_file]
if external:
    raise SystemExit(f'external images: {external}')
OUT.mkdir(parents=True, exist_ok=True)
blend = OUT/f'{args.fighter}.blend'
bpy.context.preferences.filepaths.save_version = 0
bpy.ops.wm.save_as_mainfile(filepath=str(blend), compress=True)
manifest = {
    'asset': blend.name,
    'asset_sha256': hashlib.sha256(blend.read_bytes()).hexdigest(),
    'collection': GEOM['collections']['model'],
    'root': GEOM['root']['name'],
    'metres_per_unit': mpu,
    'control': gear['control'], 'knob': gear['knob'], 'saved_knob': 0.0,
    'knob_meaning': gear['knob_meaning'],
    'note': 'Animate gear_down on the control empty (0..1); doors open over the first 30%, legs swing over the '
            'rest. At 1 the feet stand on deck_z in hull space: place the root that far above a deck. A pose set '
            'from Python needs control.update_tag() before the frame is evaluated.',
    'deck_z_units': deck,
    'feet': list(feet),
    'hinges': gear['hinges'],
    'checks': report,
    'geometry_json_sha256': hashlib.sha256((HERE/args.fighter/'geometry.json').read_bytes()).hexdigest(),
}
(OUT/f'{args.fighter}-manifest.json').write_text(json.dumps(manifest, indent=1)+'\n')
print('PACKAGE', blend, json.dumps(report))
