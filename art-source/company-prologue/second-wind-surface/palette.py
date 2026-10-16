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

HERE = Path(__file__).resolve().parent
reg = json.loads((HERE / 'reference' / 'finish-reference.registration.json').read_text())
fit = reg['fits'][reg['best_fit']]['T_guide_to_generated']
T = (*fit['scale'], *fit['offset_px'])
ref = I.load_rgb(HERE / 'reference' / 'finish-reference.png', (1920, 1080))
registered = I.warp_rgb(ref, T, (214, 214, 214))
masks = I.class_masks(I.load_rgb(HERE / 'build' / 'reference-classes.png'))
lab = I.rgb_to_lab(registered)
out = {'source': 'reference/finish-reference.png registered by its own fit (%s, IoU %.3f)' % (reg['best_fit'], reg['fits'][reg['best_fit']]['iou']),
       'space': 'CIELAB D65', 'classes': {}}
for c in ('cream', 'oxide', 'gunmetal', 'dark'):
    m = I.erode(masks[c], 3)
    v = lab[m]
    out['classes'][c] = {'pixels': int(m.sum()), 'mean': v.mean(0).round(2).tolist(), 'std': v.std(0).round(2).tolist(),
                         'L_p10_p50_p90': np.percentile(v[:, 0], [10, 50, 90]).round(2).tolist(),
                         'mean_srgb': I.lab_to_rgb(v.mean(0)).round(1).tolist()}
    print(c, out['classes'][c])
# The grapple arm's pale grey is not on the reference (it is the arm's only
# material and the reference's arm is a few pixels): an owned derivation.
c, g = out['classes']['cream'], out['classes']['gunmetal']
out['classes']['pale'] = {'derived': 'not on the finish reference (the grapple arm is its only material): cream lightness, gunmetal chroma and spread',
                          'mean': [round(c['mean'][0] - 4, 2), g['mean'][1], g['mean'][2]],
                          'std': [round(c['std'][0] * 0.8, 2), g['std'][1], g['std'][2]]}
(HERE / 'palette.json').write_text(json.dumps(out, indent=1) + '\n')
