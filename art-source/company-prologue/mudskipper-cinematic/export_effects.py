"""Add the shared VFX kit's effects to the exported Mudskipper as an optional collection.

  blender --background --factory-startup --python export_effects.py

Opens mudskipper.blend, runs the kit's own `swfx.apply` with this folder's
hull profile (vfx_hull.py: three LOW_TECH drives, two maneuvering jets as RCS,
the twin MG, her lamps; ../second-wind-vfx/swfx_hull.py) for the one clean
state, without a lighting-rig preset (that is the shot's), and saves in place.
The kit's `SWFX` collection then sits beside `MUDSKIPPER`:

- append `MUDSKIPPER` alone for the bare ship;
- append both in one load for the effects, which ride the ship's own anchors
  (parented to them), so moving the root moves them too.

Records what the kit built in manifest.json under `effects`.
"""
from pathlib import Path
import hashlib
import json
import sys
import bpy

HERE = Path(__file__).resolve().parent
VFX = HERE.parent / 'second-wind-vfx'
sys.path.insert(0, str(VFX))
import swfx  # noqa: E402
import swfx_core  # noqa: E402

BLEND = HERE / 'mudskipper.blend'
GROUPS = ('thrusters', 'smoke', 'guns', 'lighting')
bpy.ops.wm.open_mainfile(filepath=str(BLEND))
ctx = swfx.apply(state='clean', groups=GROUPS, hull=HERE)
coll = bpy.data.collections[swfx_core.ROOT]
objs = list(coll.all_objects)
for img in bpy.data.images:
    if img.source == 'FILE' and not img.packed_file:
        img.pack()
bpy.context.preferences.filepaths.save_version = 0
bpy.ops.wm.save_as_mainfile(filepath=str(BLEND), compress=True)


def summary(rec):
    if isinstance(rec, dict):
        return {k: (len(v) if isinstance(v, (list, dict)) else v) for k, v in rec.items()
                if isinstance(v, (list, dict, int, float, str, bool))}
    return rec


man_path = HERE / 'manifest.json'
man = json.loads(man_path.read_text())
man['effects'] = {
    'collection': swfx_core.ROOT,
    'kit': 'second-wind-vfx (swfx.apply, hull profile mudskipper-cinematic/vfx_hull.py, state clean, '
           'groups thrusters, smoke, guns, lighting; no rig preset)',
    'objects': len(objs),
    'anchors': ctx.records.get('anchors'),
    'controls': 'SWFX controls (animatable custom properties; see second-wind-vfx/README.md)',
    'groups': {g: summary(ctx.records.get(g)) for g in GROUPS},
    'append': f"load both '{man['append_collection']}' and '{swfx_core.ROOT}' in one call; the ship alone works without it",
}
man['asset_sha256'] = hashlib.sha256(BLEND.read_bytes()).hexdigest()
man_path.write_text(json.dumps(man, indent=1) + '\n')
print('EFFECTS', len(objs), 'objects; engines', len(ctx.anchors.engines), 'guns', len(ctx.anchors.guns),
      'lamps', len(ctx.anchors.lamps))
