"""Add the shared VFX kit's effects to an exported Phaeton as an optional collection.

  blender --background --factory-startup --python export_effects.py -- --state clean|destroyed

Opens phaeton.blend (clean) or phaeton-destroyed.blend, runs the kit's own
`swfx.apply` with this folder's hull profile (vfx_hull.py) and saves in place.
The kit's `SWFX` collection then sits beside the ship's:

- clean: the six drives, guns and lamps (groups thrusters, smoke, guns,
  lighting): the working tanker in the column;
- destroyed: the opt-in 'fire' group on the wreck's scar anchors (22 fires,
  6 vents) and its three explosion anchors. The kit's `fir_exp_frame` is
  driven from `PHAETON WRECK | controls`'s `break_frame`, so one knob stages
  the detonation, the fireball, the break-up and the fires coming up.

Append the ship's collection alone for the bare ship, or both in one load for
the effects (they ride the ship's anchors). Records what the kit built in the
state's manifest under `effects`.
"""
from pathlib import Path
import argparse
import hashlib
import json
import sys
import bpy

HERE = Path(__file__).resolve().parent
VFX = HERE.parent / 'second-wind-vfx'
sys.path.insert(0, str(VFX))
import swfx  # noqa: E402
import swfx_core  # noqa: E402

ap = argparse.ArgumentParser()
ap.add_argument('--state', default='clean', choices=('clean', 'destroyed'))
args = ap.parse_args(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])
WRECK = args.state == 'destroyed'
BLEND = HERE / ('phaeton-destroyed.blend' if WRECK else 'phaeton.blend')
MANIFEST = HERE / ('manifest-destroyed.json' if WRECK else 'manifest.json')
GROUPS = ('fire',) if WRECK else ('thrusters', 'smoke', 'guns', 'lighting')

bpy.ops.wm.open_mainfile(filepath=str(BLEND))
ctx = swfx.apply(state=args.state, groups=GROUPS, hull=HERE)
coll = bpy.data.collections[swfx_core.ROOT]
if WRECK:
    # One knob: the kit's detonation frame follows the wreck's break_frame.
    ctrl = bpy.data.objects['PHAETON WRECK | controls']
    knobs = ctx.controls
    d = knobs.driver_add('["fir_exp_frame"]').driver
    d.type = 'SCRIPTED'
    v = d.variables.new()
    v.name, v.type = 'bf', 'SINGLE_PROP'
    v.targets[0].id, v.targets[0].data_path = ctrl, '["break_frame"]'
    d.expression = 'bf'
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


man = json.loads(MANIFEST.read_text())
man['effects'] = {
    'collection': swfx_core.ROOT,
    'kit': f"second-wind-vfx (swfx.apply, hull profile phaeton-cinematic/vfx_hull.py, state {args.state}, "
           f"groups {', '.join(GROUPS)}; no rig preset)",
    'objects': len(objs),
    'anchors': ctx.records.get('anchors'),
    'controls': 'SWFX controls (animatable custom properties; see second-wind-vfx/README.md)',
    'groups': {g: summary(ctx.records.get(g)) for g in GROUPS},
    'append': f"load both '{man['append_collection']}' and '{swfx_core.ROOT}' in one call; the ship alone works without it",
}
if WRECK:
    man['effects']['detonation'] = ("SWFX controls fir_exp_frame is driven from PHAETON WRECK | controls "
                                    "break_frame: set break_frame only. fir_fire / fir_vent / fir_smoke scale "
                                    "the wreck's fires; fir_exp 0 suppresses the blast for a wreck already burning.")
man['asset_sha256'] = hashlib.sha256(BLEND.read_bytes()).hexdigest()
MANIFEST.write_text(json.dumps(man, indent=1) + '\n')
print('EFFECTS', args.state, len(objs), 'objects; engines', len(ctx.anchors.engines), 'guns', len(ctx.anchors.guns),
      'lamps', len(ctx.anchors.lamps), {g: summary(ctx.records.get(g)) for g in GROUPS})
