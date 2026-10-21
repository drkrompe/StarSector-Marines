"""Copy the exported Borer to the shared Drive: builds/borer/<commit>/.

  python publish_build.py [--drive DIR]

<commit> is the short id of HEAD, so publish after committing the scripts that made the asset;
it refuses while this folder or the surfacing inputs have uncommitted changes. Copies
borer.blend, manifest.json, validation.json, cradle-check.json, README.md and the surfacing's
skin-manifest.json, and refuses unless validation passed and every cradle station fits.
"""
from pathlib import Path
import argparse
import hashlib
import json
import os
import shutil
import subprocess

HERE = Path(__file__).resolve().parent
SURFACE = HERE.parent / 'fighters-surface' / 'borer'
DEFAULT = Path('G:/My Drive/starsector') if os.name == 'nt' else \
    next(Path.home().glob('Library/CloudStorage/GoogleDrive-*/My Drive/starsector'), None)
ap = argparse.ArgumentParser()
ap.add_argument('--drive', default=str(DEFAULT))
a = ap.parse_args()


def git(*args):
    return subprocess.run(['git', *args], cwd=HERE, check=True, capture_output=True, text=True).stdout.strip()


dirty = git('status', '--porcelain', '--', str(HERE), str(SURFACE))
assert not dirty, f'commit first:\n{dirty}'
assert json.loads((HERE / 'validation.json').read_text())['passed'], 'validation failed'
assert all(s['ok'] for s in json.loads((HERE / 'cradle-check.json').read_text())['stations']), 'cradle check failed'
man = json.loads((HERE / 'manifest.json').read_text())
assert man['asset_sha256'] == hashlib.sha256((HERE / 'borer.blend').read_bytes()).hexdigest(), 'manifest is stale'
out = Path(a.drive) / 'builds' / 'borer' / git('rev-parse', '--short=9', 'HEAD')
out.mkdir(parents=True, exist_ok=True)
for p in (HERE / 'borer.blend', HERE / 'manifest.json', HERE / 'validation.json', HERE / 'cradle-check.json',
          HERE / 'README.md', SURFACE / 'skin-manifest.json'):
    shutil.copy2(p, out / p.name)
print('PUBLISH', out)
