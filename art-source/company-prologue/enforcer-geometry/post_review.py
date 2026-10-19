"""Copy a set of finished 1920x1080 renders into a new review run on the shared Drive.

  python3 post_review.py RUN NOTES.md SRC:NAME [SRC:NAME ...]
RUN is the run folder name (`<YYYY-MM-DD>_<what>_mac`), created under
<Drive>/starsector/review/enforcer/; it must not exist yet (one run, one
writer). SRC is a render path relative to build/enforcer/ (for example
merchant/three-quarter.png); NAME is the review name without its order
prefix. Images are numbered in the order given. A transparent render (top
views, shot checks) is flattened onto black for a shot and onto the review
grey otherwise. Every image must be 1920x1080. NOTES.md is copied in as is.
Then prunes the asset with tools/review_prune.py.
"""
from pathlib import Path
import shutil
import subprocess
import sys
from PIL import Image

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[2]
BUILD = REPO/'build'/'enforcer'
DRIVE = next(Path.home().glob('Library/CloudStorage/GoogleDrive-*/My Drive/starsector'), None) \
    or Path(r'G:\My Drive\starsector')

run, notes, *pairs = sys.argv[1:]
asset = DRIVE/'review'/'enforcer'
asset.mkdir(parents=True, exist_ok=True)
dest = asset/run
dest.mkdir()
for k, pair in enumerate(pairs, 1):
    src, name = pair.split(':')
    im = Image.open(BUILD/src).convert('RGBA')
    assert im.size == (1920, 1080), (src, im.size)
    shot = Path(src).name.startswith('shot-')
    bg = Image.new('RGBA', im.size, (0, 0, 0, 255) if shot else (60, 62, 68, 255))
    bg.alpha_composite(im)
    bg.convert('RGB').save(dest/f'{k:02d}-{name}.png')
shutil.copy(notes, dest/'NOTES.md')
print('posted', dest)
subprocess.run([sys.executable, str(REPO/'tools'/'review_prune.py'), '--asset', 'enforcer', '--apply'], check=False)
