"""Guide for, and registration of, a hull's finish reference.

  python register_reference.py guide [--hull DIR]  -> guide/reference-guide.png
  python register_reference.py [--hull DIR]        -> reference/finish-reference.registration.json

The finish reference is one Codex reskin of the whole clean ship from the build
camera hull.REFERENCE_CAMERA, attached to every later request as the material
to match and read by palette.py for the class palette. `guide` lays
render_reference_classes.py's lit transparent render on parts.BACKGROUND: that
is the image Codex repaints. The default mode registers the generated
reference/finish-reference.png to it by silhouette fit (imgops.fit, as
register.py does per tile) and writes the record palette.py reads, in the
projection spike's schema: T maps guide pixels to generated pixels, both at
1920x1080. (The Second Wind's record came from the spike itself.)
"""
from pathlib import Path
import json
import sys
import numpy as np
from PIL import Image
import imgops as I
import parts as P
import hull as H

DATA = H.DIR
SIZE = (1920, 1080)
GUIDE = DATA / 'guide' / 'reference-guide.png'


def guide():
    rgba = np.asarray(Image.open(DATA / 'build' / 'reference-guide-rgba.png').convert('RGBA'), np.float32) / 255
    a = rgba[..., 3:4]
    out = rgba[..., :3] * 255 * a + np.array(P.BACKGROUND, np.float32) * (1 - a)
    GUIDE.parent.mkdir(exist_ok=True)
    I.save_rgb(out, GUIDE)
    I.save_rgb(np.repeat(a * 255, 3, 2), DATA / 'guide' / 'reference-mask.png')
    print('guide ->', GUIDE)


def register():
    src = DATA / 'reference' / 'finish-reference.png'
    native = Image.open(src).size
    gmask = I.load_rgb(DATA / 'guide' / 'reference-mask.png')[..., 0] > 127
    gen = I.load_rgb(src, SIZE)
    nmask, bg = I.segment(gen)
    fits, scores, best = I.fit(gmask, nmask)
    rep = {'source': 'reference/finish-reference.png', 'view': H.REFERENCE_CAMERA, 'native_size': list(native),
           'guide_size': list(SIZE), 'segmentation_selfcheck_iou_on_guide': round(I.iou(I.segment(I.load_rgb(GUIDE))[0], gmask), 4),
           'generated_background_rgb': [round(float(v), 1) for v in bg], 'best_fit': best, 'fits': {}}
    for k, T in fits.items():
        b = I.boundary_distance(gmask, I.warp_mask(nmask, T))
        rep['fits'][k] = {'T_guide_to_generated': {'scale': [T[0], T[1]], 'offset_px': [T[2], T[3]]}, 'iou': scores[k],
                          'boundary_mean_px': b['mean_px'], 'boundary_p95_px': b['p95_px']}
    (DATA / 'reference' / 'finish-reference.registration.json').write_text(json.dumps(rep, indent=1) + '\n')
    I.save_rgb(I.warp_rgb(gen, fits[best], bg), DATA / 'build' / 'finish-reference-registered.png')
    print(best, {k: round(v, 4) for k, v in scores.items()})


if __name__ == '__main__':
    guide() if len(sys.argv) > 1 and sys.argv[1] == 'guide' else register()
