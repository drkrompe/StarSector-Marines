"""Render-queue driver for the Borer's surfacing guides (rebuild.sh steps 1, 2a and 3a).

  python queue_guides.py <blender> <out_dir>

Builds the clean geometry (no render), strips the review props, then renders the two guide
sets the surfacing needs at the light lane's 16 samples: the finish reference's guide and class
map (render_reference_classes.py, 1920x1080, the harness's registration frame) and every source
part's guide, mask, class and normal tiles (render_parts.py). Copies what the later local steps
read into <out_dir>: guide/, part-list/, build/reference-*.png and build/hull/tiles/.
"""
from pathlib import Path
import os
import shutil
import subprocess
import sys

HERE = Path(__file__).resolve().parent
BLENDER, OUT = sys.argv[1], Path(sys.argv[2])
HARNESS = HERE.parents[1]/'second-wind-surface'
GEOM = HERE.parents[1]/'fighters-geometry'
env = dict(os.environ, SURF_GUIDE_SAMPLES='16')


def run(args, cwd):
    print('QUEUE_GUIDES', ' '.join(map(str, args)), flush=True)
    subprocess.run([BLENDER, '--background', *map(str, args)], cwd=cwd, env=env, check=True)


run(['--factory-startup', '--python-exit-code', '1', '--python', 'build_fighter.py', '--',
     '--fighter', 'borer', '--out', HERE/'build'/'geom', '--views', 'none'], GEOM)
run([HERE/'build'/'geom'/'borer-geometry.blend', '--python-exit-code', '1', '--python', HERE.parent/'strip_review_props.py'],
    HARNESS)
run(['--factory-startup', '--python-exit-code', '1', '--python', 'render_reference_classes.py', '--', '--hull', HERE], HARNESS)
run(['--factory-startup', '--python-exit-code', '1', '--python', 'render_parts.py', '--', '--hull', HERE,
     '--samples', '16'], HARNESS)
OUT.mkdir(parents=True, exist_ok=True)
for rel in ('guide', 'part-list', 'build/hull/tiles'):
    shutil.copytree(HERE/rel, OUT/rel, dirs_exist_ok=True)
for p in (HERE/'build').glob('reference-*.png'):
    (OUT/'build').mkdir(exist_ok=True)
    shutil.copy2(p, OUT/'build'/p.name)
(OUT/'DONE.txt').write_text('borer surfacing guides\n')
print('QUEUE_GUIDES done ->', OUT)
