"""The Manticore (P)'s guide renders as one render-queue light job (rebuild.sh steps 1-3).

  python queue_guides.py <blender> <out dir>

Builds the clean geometry headless (no render), then renders the reference
camera's lit guide and class map (render_reference_classes.py) and every source
part's guide, mask, class and normal passes (render_parts.py) at
SURF_GUIDE_SAMPLES (16 unless set; the class and normal passes are one sample),
and copies what the local steps need into <out dir>: build/reference-*.png,
guide/, part-list/ and build/<group>/tiles/. The coordinator's rule: guide renders
go through the queue, never a session's own shell (both GPUs render finals).
"""
import os
import shutil
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
HARNESS = HERE.parent / 'second-wind-surface'
blender, out = sys.argv[1], Path(sys.argv[2])
env = dict(os.environ, SURF_GUIDE_SAMPLES=os.environ.get('SURF_GUIDE_SAMPLES', '16'))


def blend(script, *args, cwd=HARNESS):
    cmd = [blender, '--background', '--factory-startup', '--python-exit-code', '1', '--python', str(script), '--', *args]
    print('RUN', ' '.join(cmd), flush=True)
    subprocess.run(cmd, cwd=cwd, env=env, check=True)


blend(HERE.parent / 'manticore-geometry' / 'build_manticore.py', '--views', 'none', '--out', str(HERE / 'build' / 'geom'),
      cwd=HERE.parent / 'manticore-geometry')
blend(HARNESS / 'render_reference_classes.py', '--hull', str(HERE))
blend(HARNESS / 'render_parts.py', '--hull', str(HERE))
out.mkdir(parents=True, exist_ok=True)
for name in ('reference-guide-rgba.png', 'reference-classes.png'):
    shutil.copy2(HERE / 'build' / name, out / name)
shutil.copytree(HERE / 'guide', out / 'guide', dirs_exist_ok=True)
shutil.copytree(HERE / 'part-list', out / 'part-list', dirs_exist_ok=True)
for tiles in (HERE / 'build').glob('*/tiles'):
    shutil.copytree(tiles, out / 'tiles' / tiles.parent.name, dirs_exist_ok=True)
print('GUIDES done', sum(1 for _ in out.rglob('*.png')), 'png')
