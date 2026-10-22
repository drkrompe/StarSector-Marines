"""Write the ImageGen guides for the Asharu surface tiles (see README.md).

    python make_guides.py            # build/asharu-surface/guides/<tile>.png for every tile whose
                                     # parent is finished, from the install's desert01 and raw/
    python make_guides.py --finish   # ... and build/asharu-surface/finished/<tile>.png, to look at

A guide is the game's own map (or the finished parent tile) resampled onto a tile, so guides are
regenerated from the install and raw/, never committed. The generations are committed in raw/.
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE.parent))
import roster  # noqa: E402
import surface_detail as sd  # noqa: E402
import settlement_guides  # noqa: E402

REPO = HERE.parents[3]
OUT = REPO / 'build' / 'asharu-surface'


def load(path):
    return np.asarray(Image.open(path).convert('RGB'), dtype=np.float64) / 255.0


def save(img, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray((np.clip(img, 0, 1) * 255 + .5).astype(np.uint8)).save(path)
    print(path)


def raw(name):
    p = HERE / 'raw' / f'{name}.png'
    return load(p) if p.exists() else None


def main():
    base = load(roster.GAME_CORE / 'graphics' / 'planets' / 'desert01.jpg')
    finished = sd.finish_all(base, raw)
    for name in sd.TILES:
        parent = sd.PARENT[name]
        if parent is None or parent in finished:
            save(sd.guide_for(name, base, finished), OUT / 'guides' / f'{name}.png')
        if name in finished:
            g = settlement_guides.draw(name, finished[name])
            if g is not None:
                save(g, OUT / 'guides' / f'{name}-settled.png')
    if '--finish' in sys.argv:
        for name, img in finished.items():
            save(img, OUT / 'finished' / f'{name}.png')


if __name__ == '__main__':
    main()
