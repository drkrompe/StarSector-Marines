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
from mathutils import Vector

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import wf_export  # noqa: E402
importlib.reload(wf_export)

ap = argparse.ArgumentParser()
ap.add_argument('--out', required=True)
args = ap.parse_args(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])
OUT = Path(args.out).resolve()
OUT.mkdir(parents=True, exist_ok=True)
geom = json.loads((HERE / 'geometry.json').read_text(encoding='utf-8'))
mpu = geom['metres_per_unit']
src = bpy.data.filepath
wf_export.package(OUT / 'wolf-p.blend', mpu, pack_images=True,
                  extra={'built_from': Path(src).name if src else 'unsaved'})
coll = bpy.data.collections[wf_export.COLL]
meshes = [o for o in coll.all_objects if o.type == 'MESH']
lo, hi = Vector((1e9,) * 3), Vector((-1e9,) * 3)
for o in meshes:
    for c in o.bound_box:
        w = o.matrix_world @ Vector(c)
        lo, hi = Vector(map(min, lo, w)), Vector(map(max, hi, w))
man = {
    'asset': 'wolf-p.blend', 'name': 'Wolf (P)',
    'append_collection': wf_export.COLL, 'root': wf_export.ROOT, 'anchors_collection': f'{wf_export.COLL} | anchors',
    'metres_per_unit': mpu, 'axes': geom['axes'],
    'bounds_units': [[round(v, 3) for v in lo], [round(v, 3) for v in hi]],
    'size_m': [round((b - a) * mpu, 1) for a, b in zip(lo, hi)],
    'meshes': len(meshes), 'polygons': sum(len(o.data.polygons) for o in meshes),
    'images': len([i for i in bpy.data.images if i.source == 'FILE']),
    'engines': geom['engines'], 'turrets': geom['turrets'], 'hardpoints': geom['hardpoints'],
    'vfx_mounts': geom['vfx_mounts'], 'weapon_slots': geom['weapon_slots'],
    'removed_weapon_slots': geom['hull']['removed_weapon_slots'], 'loadout': geom['hull']['loadout'],
    'running_lights': geom['running_lights'],
    'sources': {'geometry': 'art-source/company-prologue/wolf-geometry/build_wolf.py',
                'surface': 'art-source/company-prologue/wolf-surface (shared harness, battle wear)',
                'built_from': Path(src).name if src else None},
}
(OUT / 'manifest.json').write_text(json.dumps(man, indent=1) + '\n', encoding='utf-8')
print('exported', OUT / 'wolf-p.blend', len(meshes), 'meshes', man['polygons'], 'polygons', man['images'], 'images')
