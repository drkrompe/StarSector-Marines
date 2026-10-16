"""Build the Second Wind with its VFX/lighting kit, and render reviews.

Run:
  blender --background --factory-startup --python build_vfx.py -- [options]

Options after a literal "--":
  --state S        derelict | patched (default) | refit | restored | clean
  --blend PATH     open an existing ship build instead of rebuilding it
  --geometry G     build_reference.py --state to build (default: the state's
                   stand-in from swfx_core.STATES)
  --groups a,b     effect groups (default all: thrusters,smoke,guns,lighting)
  --preset P       lighting rig preset (see swfx_lighting.PRESETS)
  --review a,b     render each named group's review (stills + short movie)
  --out DIR        review / save output root (default build/ beside this file)
  --quick          low samples and few frames, for iteration (never shown)
  --save PATH      save the built .blend
  --anchors        print the anchor record as JSON and exit
"""
import argparse
import json
import runpy
import sys
from pathlib import Path

import bpy

HERE = Path(__file__).resolve().parent
GEOMETRY = HERE.parent / 'second-wind-geometry'
for p in (HERE, GEOMETRY):
    if str(p) not in sys.path:
        sys.path.insert(0, str(p))

import swfx  # noqa: E402
import swfx_core  # noqa: E402


def build_ship(geometry_state, scratch):
    """Run the geometry build in this Blender session: no renders, no save."""
    argv = sys.argv
    sys.argv = [argv[0], '--', '--state', geometry_state, '--views', '', '--no-save', '--out', str(scratch)]
    try:
        runpy.run_path(str(GEOMETRY / 'build_reference.py'), run_name='__main__')
    finally:
        sys.argv = argv


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--state', default='patched', choices=sorted(swfx_core.STATES))
    parser.add_argument('--blend')
    parser.add_argument('--geometry', choices=('clean', 'derelict', 'patched'))
    parser.add_argument('--groups', default=','.join(swfx.GROUPS))
    parser.add_argument('--preset')
    parser.add_argument('--review', default='')
    parser.add_argument('--out', default=str(HERE / 'build'))
    parser.add_argument('--quick', action='store_true')
    parser.add_argument('--save')
    parser.add_argument('--anchors', action='store_true')
    args = parser.parse_args(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])
    out = Path(args.out).resolve()
    out.mkdir(parents=True, exist_ok=True)

    if args.blend:
        bpy.ops.wm.open_mainfile(filepath=str(Path(args.blend).resolve()))
    else:
        build_ship(args.geometry or swfx_core.STATES[args.state]['geometry'], out / '_geometry')
    groups = [g for g in args.groups.split(',') if g]
    ctx = swfx.apply(state=args.state, groups=groups, preset=args.preset)
    if args.anchors:
        print(json.dumps(ctx.records['anchors'], indent=1))
        return
    (out / f'record-{args.state}.json').write_text(json.dumps(ctx.records, indent=1, default=str) + '\n',
                                                     encoding='utf-8')
    for group in [g for g in args.review.split(',') if g]:
        swfx.module(group).review(ctx, out / group, quick=args.quick)
    if args.save:
        Path(args.save).resolve().parent.mkdir(parents=True, exist_ok=True)
        bpy.context.preferences.filepaths.save_version = 0
        bpy.ops.wm.save_as_mainfile(filepath=str(Path(args.save).resolve()))


main()
