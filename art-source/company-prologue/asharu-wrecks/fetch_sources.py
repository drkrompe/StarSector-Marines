"""Copy the accepted hull builds the hulks derive from (wk_catalogue.SOURCES) off the shared Drive
into build/asharu-wrecks/src/<key>/<commit>/, where build_wrecks.py reads them.

  python fetch_sources.py [--drive DIR]"""
from pathlib import Path
import argparse
import os
import shutil
import sys

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import wk_catalogue as C  # noqa: E402

REPO = HERE.parents[2]
DEFAULT = Path('G:/My Drive/starsector') if os.name == 'nt' else \
    next(Path.home().glob('Library/CloudStorage/GoogleDrive-*/My Drive/starsector'), None)
ap = argparse.ArgumentParser()
ap.add_argument('--drive', default=str(DEFAULT))
a = ap.parse_args()
for key, s in C.SOURCES.items():
    src = Path(a.drive) / s['drive']
    dst = REPO / 'build' / 'asharu-wrecks' / 'src' / key / s['drive'].rstrip('/').split('/')[-1]
    dst.mkdir(parents=True, exist_ok=True)
    for name in (s['file'], 'manifest.json'):
        if not (dst / name).exists() or (dst / name).stat().st_size != (src / name).stat().st_size:
            shutil.copy2(src / name, dst / name)
    print('SOURCE', key, dst)
