"""Add the shared VFX kit's effects to the built Lasher as an optional collection.

  sh ../../blender.sh --background --factory-startup --python export_effects.py -- [PATH]

Opens lasher.blend (default build/lasher/lasher.blend, written by
build_lasher.py), runs the kit's own `swfx.apply` with this folder's hull
profile (vfx_hull.py: five LOW_TECH drives, three twin turrets, four running
lights; ../second-wind-vfx/swfx_hull.py) for the clean state, without a
lighting-rig preset (that is the shot's), and saves in place. The kit's
`SWFX` collection then sits beside `LASHER`:

- link or append `LASHER` alone for the bare ship;
- load both in one call for the effects, which ride the ship's own anchors
  (parented to them), so moving or instancing the root carries them too.

Writes what the kit built to effects.json beside the .blend.
"""
from pathlib import Path
import json
import sys
import bpy

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / 'second-wind-vfx'))
import swfx  # noqa: E402
import swfx_core  # noqa: E402

argv = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
BLEND = Path(argv[0] if argv else HERE.parents[2] / 'build' / 'lasher' / 'lasher.blend').resolve()
GROUPS = ('thrusters', 'smoke', 'guns', 'lighting')
bpy.ops.wm.open_mainfile(filepath=str(BLEND))
ctx = swfx.apply(state='clean', groups=GROUPS, hull=HERE)
a = ctx.anchors
assert len(a.engines) == 5, [e.index for e in a.engines]
assert len(a.guns) == 6, [(g.turret, g.tag) for g in a.guns]
assert len(a.lamps) == 4, [l.name for l in a.lamps]
coll = bpy.data.collections[swfx_core.ROOT]
objs = list(coll.all_objects)
bpy.context.preferences.filepaths.save_version = 0
bpy.ops.wm.save_as_mainfile(filepath=str(BLEND), compress=True)


def summary(rec):
    if isinstance(rec, dict):
        return {k: (len(v) if isinstance(v, (list, dict)) else v) for k, v in rec.items()
                if isinstance(v, (list, dict, int, float, str, bool))}
    return rec


(BLEND.parent / 'effects.json').write_text(json.dumps({
    'collection': swfx_core.ROOT,
    'kit': 'second-wind-vfx (swfx.apply, hull profile lasher-geometry/vfx_hull.py, state clean, '
           'groups thrusters, smoke, guns, lighting; no rig preset)',
    'objects': len(objs),
    'anchors': ctx.records.get('anchors'),
    'controls': 'SWFX controls (animatable custom properties; see second-wind-vfx/README.md)',
    'groups': {g: summary(ctx.records.get(g)) for g in GROUPS},
    'append': f"load both '{next(c.name for c in bpy.data.collections if c.name.startswith('LASHER') and not c.name.endswith('| anchors'))}' "
              "and 'SWFX' in one call; the ship alone works without it",
}, indent=1, default=str) + '\n', encoding='utf-8')
print('EFFECTS', len(objs), 'objects; engines', len(a.engines), 'guns', len(a.guns), 'lamps', len(a.lamps))
