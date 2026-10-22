"""Copy the built wreck kit to the shared Drive: builds/asharu-wrecks/<commit>/.

  python publish_build.py [--kit DIR] [--drive DIR]

<commit> is the short id of HEAD, so publish after committing the scripts that made the kit; it
refuses while this folder has uncommitted changes, when validation failed, when the manifest's
hash does not match the blend, or when the committed catalogue.json is not the one the build
wrote. Copies asharu-wrecks.blend, manifest.json, validation.json, catalogue.json, README.md and
wreck_kit.py (the contract, so a consumer can read it beside the blend)."""
from pathlib import Path
import argparse
import hashlib
import json
import os
import shutil
import subprocess

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[2]
DEFAULT = Path('G:/My Drive/starsector') if os.name == 'nt' else \
    next(Path.home().glob('Library/CloudStorage/GoogleDrive-*/My Drive/starsector'), None)
ap = argparse.ArgumentParser()
ap.add_argument('--kit', default=str(REPO / 'build' / 'asharu-wrecks' / 'kit'))
ap.add_argument('--drive', default=str(DEFAULT))
a = ap.parse_args()
kit = Path(a.kit)


def git(*args):
    return subprocess.run(['git', *args], cwd=HERE, check=True, capture_output=True, text=True).stdout.strip()


dirty = git('status', '--porcelain', '--', str(HERE))
assert not dirty, f'commit first:\n{dirty}'
assert json.loads((kit / 'validation.json').read_text())['passed'], 'validation failed'
man = json.loads((kit / 'manifest.json').read_text())
assert man['asset_sha256'] == hashlib.sha256((kit / 'asharu-wrecks.blend').read_bytes()).hexdigest(), 'manifest is stale'
cat = json.loads((HERE / 'catalogue.json').read_text())
assert sorted(v['name'] for v in cat['variants']) == sorted(v['name'] for v in man['variants']), \
    'catalogue.json does not match the build'
out = Path(a.drive) / 'builds' / 'asharu-wrecks' / git('rev-parse', '--short=9', 'HEAD')
out.mkdir(parents=True, exist_ok=True)
for p in (kit / 'asharu-wrecks.blend', kit / 'manifest.json', kit / 'validation.json', HERE / 'catalogue.json',
          HERE / 'README.md', HERE / 'wreck_kit.py'):
    shutil.copy2(p, out / p.name)
print('PUBLISH', out)
