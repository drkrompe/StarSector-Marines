"""The shared palette: per material class, read off the fixed finish reference.

  python palette.py      -> palette.json

The finish reference is attached to every Codex request, so its paint is the
tone every part is graded back to. It is registered to the clean port-side
view by the spike's measured fit (reference/finish-reference.registration.json)
and sampled on the plates build/reference-classes.png says carry each class,
eroded 3 px so outlines, seams and antialiasing stay out. Stats are CIELAB:
mean and standard deviation per channel, plus percentiles of L.
"""
from pathlib import Path
import json
import numpy as np
import imgops as I
import hull as H

HERE = Path(__file__).resolve().parent
DATA = H.DIR  # the hull's folder: its tables, art and outputs (hull.py)
reg = json.loads((DATA / 'reference' / 'finish-reference.registration.json').read_text())
fit = reg['fits'][reg['best_fit']]['T_guide_to_generated']
T = (*fit['scale'], *fit['offset_px'])
ref = I.load_rgb(DATA / 'reference' / 'finish-reference.png', (1920, 1080))
registered = I.warp_rgb(ref, T, (214, 214, 214))
masks = I.class_masks(I.load_rgb(DATA / 'build' / 'reference-classes.png'))
lab = I.rgb_to_lab(registered)
out = {'source': 'reference/finish-reference.png registered by its own fit (%s, IoU %.3f)' % (reg['best_fit'], reg['fits'][reg['best_fit']]['iou']),
       'space': 'CIELAB D65', 'classes': {}}
for c in H.MEASURED_CLASSES:
    m = I.erode(masks[c], 3)
    v = lab[m]
    out['classes'][c] = {'pixels': int(m.sum()), 'mean': v.mean(0).round(2).tolist(), 'std': v.std(0).round(2).tolist(),
                         'L_p10_p50_p90': np.percentile(v[:, 0], [10, 50, 90]).round(2).tolist(),
                         'mean_srgb': I.lab_to_rgb(v.mean(0)).round(1).tolist()}
    print(c, out['classes'][c])
# Classes not on the reference are owned derivations (surface_hull.derive_palette).
out['classes'].update(H.derive_palette(out['classes']))
# The owned wear colours (wear.py) are authored, not measured: carry them over.
old = DATA / 'palette.json'
if old.exists() and 'wear' in json.loads(old.read_text()):
    out['wear'] = json.loads(old.read_text())['wear']
(DATA / 'palette.json').write_text(json.dumps(out, indent=1) + '\n')
