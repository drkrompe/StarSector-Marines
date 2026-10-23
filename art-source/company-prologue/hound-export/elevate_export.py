"""Add WS 002's elevation joint (hd_elevation.py) to an already published Hound export.

  blender --background --factory-startup --python elevate_export.py -- --src BUILD --out DIR [--commit SHA]

BUILD is a published build folder (manifest.json and its blend, as on Drive builds/<hull>/<commit>/).
The joint is the same one export_hound.py now adds on every export; this upgrades the accepted
builds without replaying their surfacing, so nothing but the joint changes: no mesh, material,
image or other object is touched, and the gun's world pose at rest is unchanged. Writes the blend
and an updated manifest.json (the turret's `elevation`, the conventions, `upgraded_from`) to DIR.
"""
from pathlib import Path
import argparse
import hashlib
import json
import sys
import bpy

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import hd_elevation  # noqa: E402

ap = argparse.ArgumentParser()
ap.add_argument('--src', required=True)
ap.add_argument('--out', required=True)
ap.add_argument('--commit', default='')
args = ap.parse_args(sys.argv[sys.argv.index('--') + 1:])
SRC, OUT = Path(args.src).resolve(), Path(args.out).resolve()
man = json.loads((SRC / 'manifest.json').read_text())
assert not any(t['pivot'].get('elevation') or t.get('elevation') for t in man['turrets']), 'already has an elevation joint'
bpy.ops.wm.open_mainfile(filepath=str(SRC / man['asset']))
rest = {o.name: o.matrix_world.copy() for o in bpy.data.objects}
rec = hd_elevation.rig(bpy, man['state'], bpy.data.collections[man['anchors_collection']])
moved = [n for n, m in rest.items()
         if max(abs(a - b) for ra, rb in zip(bpy.data.objects[n].matrix_world, m) for a, b in zip(ra, rb)) > 1e-5]
assert not moved, moved
for w in man['weapons'] + man['turrets']:
    if w['slot'] == hd_elevation.SLOT and w['mount'] == 'TURRET':
        w['elevation'] = rec
man['conventions']['turrets'] = hd_elevation.CONVENTION
man['upgraded_from'] = {'build': SRC.as_posix(), 'asset_sha256': man.get('asset_sha256'),
                        'by': 'hound-export/elevate_export.py (joint only; meshes, skins and other anchors as published)',
                        'commit': args.commit}
OUT.mkdir(parents=True, exist_ok=True)
bpy.context.preferences.filepaths.save_version = 0
bpy.ops.wm.save_as_mainfile(filepath=str(OUT / man['asset']), compress=True)
man['asset_sha256'] = hashlib.sha256((OUT / man['asset']).read_bytes()).hexdigest()
(OUT / 'manifest.json').write_text(json.dumps(man, indent=1) + '\n', newline='\n')
print('ELEVATE', man['asset'], rec['name'], rec['at'], rec['min_deg'], rec['max_deg'], len(rec['elevated_parts']), 'parts',
      len(rec['muzzle_anchors']), 'muzzles ->', OUT)
