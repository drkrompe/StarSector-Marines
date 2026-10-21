"""Builds the shipped creep material from an accepted ImageGen master.

The master is a neutral value study (see creep-material-v1.prompt.txt) painted
over creep_material_guide.py's guide. Colour is not in the art: the shader maps
this luminance through the active CreepPalette (dark = veins, mid = film,
bright = nodules), so one material serves every palette.

Steps: luminance, a narrow mirrored blend across the wrap so the tile has no
seam, an auto-level to the full range, downsample to 512.

    python art-source/hive-creep/build_creep_material.py [master.png]
"""
import os, sys
import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..'))
master = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, 'creep-material-v1.png')
OUT = os.path.join(ROOT, 'mod', 'graphics', 'creep', 'creep_material.png')
SIZE, BAND = 512, 20

m = np.asarray(Image.open(master).convert('L').resize((1024, 1024), Image.LANCZOS)).astype(float)
for axis in (0, 1):
    a = np.moveaxis(m, axis, 1).copy()
    for i in range(BAND):
        w = 0.5 * (1 - i / BAND)
        left, right = a[:, i].copy(), a[:, -1 - i].copy()
        a[:, i] = (1 - w) * left + w * right
        a[:, -1 - i] = (1 - w) * right + w * left
    m = np.moveaxis(a, 1, axis)
lo, hi = np.percentile(m, 1), np.percentile(m, 99.5)
m = np.clip((m - lo) / (hi - lo), 0, 1)
img = Image.fromarray((m * 255).astype(np.uint8), 'L').resize((SIZE, SIZE), Image.LANCZOS)
img.save(OUT)
h = np.asarray(img) / 255.0
print('wrote', OUT, 'quantiles 10/50/90:', [round(float(np.percentile(h, q)), 3) for q in (10, 50, 90)])
