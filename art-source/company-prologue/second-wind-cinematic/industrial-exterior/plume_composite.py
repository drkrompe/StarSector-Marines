"""Lay the plume repair pass (`plume_pass.py`) over finished C4/C5 frames, in scene-linear light.

    blender -b --factory-startup --python-exit-code 1 --python plume_composite.py -- \
        --final DIR --plume DIR --out DIR [--mode linear|display] [--only 0,24]

For every frame in --final (one chunk folder of a final, 00000.png ...), writes a frame of the same
name to --out: the final with the plumes laid over where --plume has an EXR for it, and an exact
copy where it has none. The final frames are never written to. A frame already in --out is kept,
so a queued job can be resumed.

The finals are AgX (Medium High Contrast look) display images; the plume pass is scene-linear,
premultiplied RGBA. A plume is emission seen through partial cover, so in the full render its
light adds to the scene's before the view transform compresses the sum: a faint fringe over
bright ground blows out to near white there, which no blend of two display images reproduces.
`linear` (the default) therefore undoes the view transform on the final, lays the plume over it
there (plume + (1 - alpha) * final) and applies the view transform again, through the same OCIO
config and look the render used. Pixels the plume does not touch are copied unchanged, so the
round trip only ever runs under a plume. `display` blends the two display images instead
(the tone-mapped plume over the final, by its alpha), kept for the comparison that chose `linear`.

Needs Blender's bundled Python (numpy, OpenImageIO, PyOpenColorIO), which `blender -b` provides on
both render machines; Blender's own python executable runs it as well.
"""
import shutil
import sys
import time
from pathlib import Path

import numpy as np
import OpenImageIO as oiio
import PyOpenColorIO as OCIO

VIEW, DISPLAY, LOOK, LINEAR = 'AgX', 'sRGB', 'AgX - Medium High Contrast', 'Linear Rec.709'


def args():
    a = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else sys.argv[1:]
    o = {'final': None, 'plume': None, 'out': None, 'mode': 'linear', 'only': None, 'config': None}
    for i, x in enumerate(a):
        if x.startswith('--') and x[2:] in o:
            o[x[2:]] = a[i + 1]
    return o


def config_path(given=None):
    """Blender's own OCIO config: the one the finals were rendered through."""
    if given:
        return given
    try:
        import bpy
        return str(Path(bpy.utils.resource_path('LOCAL')) / 'datafiles' / 'colormanagement' / 'config.ocio')
    except ImportError:
        here = Path(sys.executable).resolve()
        for p in here.parents:
            c = p / 'datafiles' / 'colormanagement' / 'config.ocio'
            if c.exists():
                return str(c)
    raise SystemExit('PLUME_COMPOSITE: no OCIO config found (pass --config)')


class ViewTransform:
    """Scene-linear Rec.709 to the finals' display encoding and back."""

    def __init__(self, path):
        cfg = OCIO.Config.CreateFromFile(path)
        vp = OCIO.LegacyViewingPipeline()
        vp.setDisplayViewTransform(OCIO.DisplayViewTransform(src=LINEAR, display=DISPLAY, view=VIEW))
        vp.setLooksOverrideEnabled(True)
        vp.setLooksOverride(LOOK)
        proc = vp.getProcessor(cfg)
        self._fwd = proc.getDefaultCPUProcessor()
        grp = proc.createGroupTransform()
        grp.setDirection(OCIO.TRANSFORM_DIR_INVERSE)
        self._inv = cfg.getProcessor(grp).getDefaultCPUProcessor()

    def forward(self, rgb):
        x = np.array(rgb, np.float32, order="C", copy=True)
        self._fwd.applyRGB(x)
        return x

    def inverse(self, rgb, iters=3, h=1e-3):
        """Display back to scene-linear. OCIO inverts AgX's 3D LUT approximately (on the finals'
        own pixels, within about 2 levels of 8 bits); a few damped Newton steps on the forward
        transform, in log space, make the round trip exact."""
        d = np.asarray(rgb, np.float64)
        x = np.array(rgb, np.float32, order="C", copy=True)
        self._inv.applyRGB(x)
        u = np.log(np.clip(x, 1e-5, 200.0)).astype(np.float64)
        for _ in range(iters):
            f0 = self.forward(np.exp(u)).astype(np.float64)
            jac = np.empty(d.shape + (3,))
            for c in range(3):
                uc = u.copy()
                uc[:, c] += h
                jac[:, :, c] = (self.forward(np.exp(uc)) - f0) / h
            jt = np.transpose(jac, (0, 2, 1))
            step = np.linalg.solve(jt @ jac + 1e-4 * np.eye(3), (jt @ (d - f0)[..., None]))[..., 0]
            u = np.clip(u + np.clip(step, -2.0, 2.0), np.log(1e-5), np.log(200.0))
        return np.exp(u).astype(np.float32)


def read(path):
    inp = oiio.ImageInput.open(str(path))
    if inp is None:
        raise SystemExit('PLUME_COMPOSITE cannot read %s: %s' % (path, oiio.geterror()))
    px = inp.read_image(format='float')
    inp.close()
    return np.asarray(px, np.float32)


def write_png(path, rgb8):
    h, w, c = rgb8.shape
    out = oiio.ImageOutput.create(str(path))
    out.open(str(path), oiio.ImageSpec(w, h, c, 'uint8'))
    out.write_image(rgb8)
    out.close()


def lay_over(final_png, plume_exr, vt, mode):
    """The final frame (H x W x 3, display, 0..1) with the plume (H x W x 4, linear, premultiplied)
    laid over it. Returns uint8 H x W x 3, untouched outside the plume."""
    b = final_png[..., :3]
    p = plume_exr
    if p.shape[:2] != b.shape[:2]:
        raise SystemExit('PLUME_COMPOSITE size mismatch %s vs %s' % (p.shape, b.shape))
    rgb, a = np.maximum(p[..., :3], 0.0), np.clip(p[..., 3:4], 0.0, 1.0)
    touched = (a[..., 0] > 0) | (rgb.max(axis=-1) > 0)
    out = b.copy()
    if touched.any():
        bs, ps, as_ = b[touched], rgb[touched], a[touched]
        if mode == 'linear':
            out[touched] = vt.forward(ps + (1.0 - as_) * vt.inverse(bs))
        else:
            straight = np.where(as_ > 1e-6, ps / np.maximum(as_, 1e-6), 0.0)
            out[touched] = vt.forward(straight) * as_ + bs * (1.0 - as_)
    q = np.clip(np.round(out * 255.0), 0, 255).astype(np.uint8)
    q[~touched] = np.clip(np.round(b[~touched] * 255.0), 0, 255).astype(np.uint8)
    return q, int(touched.sum())


def main():
    o = args()
    final, plume, out = Path(o['final']), Path(o['plume']), Path(o['out'])
    if out.resolve() == final.resolve():
        raise SystemExit('PLUME_COMPOSITE: --out must not be the final frames folder')
    out.mkdir(parents=True, exist_ok=True)
    vt = ViewTransform(config_path(o['config']))
    frames = sorted(final.glob('[0-9]*.png'))
    if o['only']:
        keep = {'%05d.png' % int(k) for k in o['only'].split(',')}
        frames = [f for f in frames if f.name in keep]
    t0, laid = time.time(), 0
    for f in frames:
        dst = out / f.name
        if dst.exists() and dst.stat().st_size > 0:
            continue
        exr = plume / (f.stem + '.exr')
        if not exr.exists():
            shutil.copyfile(f, dst)
            continue
        rgb8, n = lay_over(read(f), read(exr), vt, o['mode'])
        tmp = dst.with_suffix('.tmp.png')
        write_png(tmp, rgb8)
        tmp.replace(dst)
        laid += 1
        print('PLUME_COMPOSITE %s: %d plume pixels (%s)' % (f.name, n, o['mode']), flush=True)
    print('PLUME_COMPOSITE %d frames, %d with plumes, in %.0fs -> %s' % (len(frames), laid, time.time() - t0, out),
          flush=True)


if __name__ == '__main__':
    main()
