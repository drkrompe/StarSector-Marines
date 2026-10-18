"""Integration check: build the whole kit on one ship build and report.

  blender --background --factory-startup --python check_kit.py -- --blend build/_ships/patched.blend --state patched

Prints one JSON line: anchors found, objects per SWFX collection, running
plumes, seal wisps, conduit spark emitters (one per sparking scar anchor),
lights, and every driver that is invalid or not a simple expression
(those would need Python auto-run, which a cinematic file must not depend
on). Exits non-zero if any driver is invalid, a state rule is broken, or a
build is not repeatable (a second apply must give the same counts).
"""
import argparse
import json
import sys
from pathlib import Path

import bpy

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import swfx  # noqa: E402
import swfx_core  # noqa: E402

args = argparse.ArgumentParser()
args.add_argument('--blend', required=True)
args.add_argument('--state', required=True)
args.add_argument('--preset', default='deep-space')
args = args.parse_args(sys.argv[sys.argv.index('--') + 1:])
bpy.ops.wm.open_mainfile(filepath=str(Path(args.blend).resolve()))


def drivers():
    bad = []
    total = 0
    stores = (bpy.data.objects, bpy.data.lights, bpy.data.materials, bpy.data.worlds,
              bpy.data.scenes, bpy.data.node_groups, bpy.data.meshes, bpy.data.cameras)
    for store in stores:
        for idb in store:
            for owner in (idb, getattr(idb, 'node_tree', None)):
                ad = getattr(owner, 'animation_data', None) if owner is not None else None
                if not ad:
                    continue
                for fc in ad.drivers:
                    total += 1
                    d = fc.driver
                    if not d.is_valid or (d.type == 'SCRIPTED' and not d.is_simple_expression):
                        bad.append(f'{idb.name}:{fc.data_path} valid={d.is_valid} simple={d.is_simple_expression}')
    return total, bad


def census():
    root = bpy.data.collections[swfx_core.ROOT]
    return {c.name: len(c.objects) for c in root.children_recursive}


ctx = swfx.apply(state=args.state, preset=args.preset)
first = census()
ctx = swfx.apply(state=args.state, preset=args.preset)
second = census()
bpy.context.scene.frame_set(12)
total, bad = drivers()
names = [o.name for o in bpy.data.objects if o.name.startswith('SWFX')]
plumes = sorted({o.name.split(' e')[-1][0] for o in bpy.data.objects
                 if o.name.startswith('SWFX thrusters plume e')})
problems = list(bad)
if first != second:
    problems.append(f'rebuild changed counts: {first} -> {second}')
mode = swfx_core.STATES[args.state]['engines']
for i in (1, 2, 3, 4):
    running = str(i) in plumes
    should = mode[i] in ('salvaged', 'clean')
    if running != should:
        problems.append(f'engine {i}: plume={running} but mode={mode[i]}')
wisps = len(ctx.records.get('smoke', {}).get('seal_wisps', []))
if bool(wisps) != swfx_core.STATES[args.state]['seal_wisps']:
    problems.append(f'seal wisps: {wisps} built, state allows {swfx_core.STATES[args.state]["seal_wisps"]}')
# Conduit sparks: exactly one emitter per scar spark anchor that is sparking.
spark_anchors = ctx.records['anchors'].get('sparks', [])
live = sorted(s['name'] for s in spark_anchors if s['sparking'])
built = sorted(s['anchor'] for s in ctx.records.get('smoke', {}).get('sparks', []))
if built != live:
    problems.append(f'conduit sparks built at {built}, but sparking anchors are {live}')
lit = [o.name for o in bpy.data.objects if o.type == 'LIGHT' and o.name.startswith('SWFX lighting')
       and 'rig' not in o.name.lower() and o.data.energy > 0]
print('CHECK ' + json.dumps({
    'state': args.state, 'anchors': {k: len(v) for k, v in ctx.records['anchors'].items() if isinstance(v, list)},
    'collections': second, 'plumes': plumes, 'seal_wisps': wisps,
    'spark_anchors': len(spark_anchors), 'spark_emitters': len(built), 'ship_lamps_energised': len(lit),
    'drivers': total, 'problems': problems}))
sys.exit(1 if problems else 0)
