"""Add the VFX kit's effects to an exported state as an optional collection.

  blender --background --factory-startup --python export_effects.py -- --state patched

Opens <state>/second-wind-<state>.blend, runs the kit's own `swfx.apply`
(../../second-wind-vfx/swfx.py: anchors, thrusters, smoke, guns and ship
lamps for that state; no lighting-rig preset, which is the shot's) and saves
the file in place. The kit's `SWFX` collection then sits beside
`SECOND WIND | <state>`:

- append `SECOND WIND | <state>` alone for the bare ship;
- append both in one load for the effects, which ride the ship's own parts
  (their anchors are parented to them), so moving the root moves them too.

Nothing of the kit is copied or changed here; re-running the kit in a shot
(`swfx.apply`) replaces this collection the same way. Records what the kit
built in <state>/manifest.json under `effects`.
"""
from pathlib import Path
import json
import sys
import bpy

HERE = Path(__file__).resolve().parent
VFX = HERE.parent.parent / 'second-wind-vfx'
sys.path.insert(0, str(VFX))
import swfx  # noqa: E402
import swfx_core  # noqa: E402

argv = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
STATE = argv[argv.index('--state') + 1]
DIR = HERE / STATE
BLEND = DIR / f'second-wind-{STATE}.blend'
bpy.ops.wm.open_mainfile(filepath=str(BLEND))
ctx = swfx.apply(state=STATE, groups=('thrusters', 'smoke', 'guns', 'lighting'))
coll = bpy.data.collections[swfx_core.ROOT]
objs = list(coll.all_objects)
for img in bpy.data.images:
    if img.source == 'FILE' and not img.packed_file:
        img.pack()
bpy.context.preferences.filepaths.save_version = 0
bpy.ops.wm.save_as_mainfile(filepath=str(BLEND), compress=True)

man_path = DIR / 'manifest.json'
man = json.loads(man_path.read_text())


def summary(rec):
    if isinstance(rec, dict):
        return {k: (len(v) if isinstance(v, (list, dict)) else v) for k, v in rec.items()
                if isinstance(v, (list, dict, int, float, str, bool))}
    return rec


man['effects'] = {
    'collection': swfx_core.ROOT,
    'kit': 'second-wind-vfx (swfx.apply, groups thrusters, smoke, guns, lighting; no rig preset)',
    'objects': len(objs),
    'controls': 'SWFX controls (animatable custom properties; see second-wind-vfx/README.md)',
    'groups': {g: summary(ctx.records.get(g)) for g in ('thrusters', 'smoke', 'guns', 'lighting')},
    'append': f"load both '{man['append_collection']}' and '{swfx_core.ROOT}' in one call; the ship alone works without it",
}
import hashlib  # noqa: E402
man['asset_sha256'] = hashlib.sha256(BLEND.read_bytes()).hexdigest()
man_path.write_text(json.dumps(man, indent=1) + '\n')
print('EFFECTS', STATE, len(objs), 'objects')
