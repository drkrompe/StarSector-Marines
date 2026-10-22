"""Finish the Asharu backdrop plates (see README.md): raw ImageGen paintings -> <kf>.backdrop.exr.

    python finish_plates.py                 # build/asharu-plates/out/<kf>.backdrop.exr, <kf>.display.png,
                                            # manifest.json
    python finish_plates.py --post COMMIT   # ... and copy them to Drive builds/asharu-plates/<COMMIT>/

1. Resize the painting to 1920x1080 (the key frame's frame; ImageGen returns 1672x941).
2. S12 only: the painting puts its horizon a little high. Rows above the guide's band of exact
   registration are remapped so the painted limb meets the camera's true limb (the ray-cast
   guide's, raised by the air shell's thickness), and nothing below that band moves.
3. Tone-match it to the graded stills (tone_match.json), then invert the stills' grade. The painting is display-referred, painted to look like the
   graded still; the composite grades the backdrop through AgX 'Base Contrast'. agx_lut.py
   tabulates that grade on a lattice in Blender; here each pixel is solved for the scene-linear
   colour the grade maps onto it (Gauss-Newton on the lattice's trilinear interpolation), so the
   plate grades back to the painting.
4. Write scene-linear Rec.709 RGBA float EXR, alpha 1, uncompressed.
"""
import hashlib
import json
import shutil
import struct
import subprocess
import sys
from pathlib import Path

import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[3]
BUILD = REPO / 'build' / 'asharu-plates'
OUT = BUILD / 'out'
W, H = 1920, 1080
KF = {'k0': 'S1a', 'k6': 'S12'}
LUT = BUILD / 'agx_lut.npz'
LO, HI = -12.0, 4.0
REGISTERED_FROM = 300      # S12 rows from here down register to the guide within a pixel


def blender():
    sys.path.insert(0, str(HERE.parents[1]))
    from blender_path import BLENDER
    return BLENDER


def lut():
    if not LUT.exists():
        subprocess.check_call([str(blender()), '-b', '--factory-startup', '--python-exit-code', '1',
                               '--python', str(HERE / 'agx_lut.py'), '--', str(BUILD / 'agx_lut')])
    d = np.load(LUT)
    return d['axis'], d['display'].astype(np.float64)


def grade(z, axis, disp):
    """Display colour of scene-linear 2^z through the tabulated grade (trilinear in lattice index)."""
    n = disp.shape[0]
    t = np.clip(1 + (z - LO) / (HI - LO) * (n - 2), 0, n - 1 - 1e-9)
    i = np.floor(t).astype(int)
    f = t - i
    out = 0.0
    for dr in (0, 1):
        for dg in (0, 1):
            for db in (0, 1):
                wgt = ((f[..., 0] if dr else 1 - f[..., 0]) * (f[..., 1] if dg else 1 - f[..., 1])
                       * (f[..., 2] if db else 1 - f[..., 2]))
                out = out + wgt[..., None] * disp[i[..., 0] + dr, i[..., 1] + dg, i[..., 2] + db]
    return out


def ungrade(y, axis, disp, steps=10):
    """Scene-linear colours whose grade is the display colours y (H x W x 3, 0-1)."""
    n = disp.shape[0]
    grey = disp[np.arange(n), np.arange(n), np.arange(n)]          # the neutral axis
    zs = np.concatenate([[LO - 1], np.linspace(LO, HI, n - 1)])
    z = np.stack([np.interp(y[..., c], grey[:, c], zs) for c in range(3)], -1)
    z = np.clip(z, LO, HI)
    eps = 1e-3
    for _ in range(steps):
        f0 = grade(z, axis, disp)
        jac = np.empty(z.shape + (3,))
        for c in range(3):
            dz = np.zeros(3)
            dz[c] = eps
            jac[..., :, c] = (grade(z + dz, axis, disp) - f0) / eps
        jac += np.eye(3) * 1e-4
        step = np.linalg.solve(jac, (y - f0)[..., None])[..., 0]
        z = np.clip(z + np.clip(step, -1.5, 1.5), LO, HI)
    lin = 2.0 ** z
    return np.where(y.max(-1, keepdims=True) <= grey[0].max() + 1e-4, 0.0, lin), grade(z, axis, disp)


def limb_rows(img, lo=40, hi=REGISTERED_FROM):
    """Per column, the row of the painted limb: the brightest row of the air glow's blue."""
    b = img[lo:hi, :, 2] - .5 * (img[lo:hi, :, 0] + img[lo:hi, :, 1])
    return lo + np.argmax(b, axis=0)


def fix_horizon(img, guide_hit):
    """Rows above REGISTERED_FROM remapped per column so the painted limb lands on the true one."""
    x = np.arange(W)
    # Both limbs are smooth curves: fit each with a parabola so whole-pixel steps leave no stripes.
    true = np.polyval(np.polyfit(x, np.argmax(guide_hit, axis=0).astype(float), 2), x) - 3.0   # air shell
    painted = np.polyval(np.polyfit(x, limb_rows(img).astype(float), 2), x)
    true = np.minimum(true, REGISTERED_FROM - 20)
    ys = np.arange(REGISTERED_FROM, dtype=float)[:, None]
    src = np.where(ys <= true, ys * painted / np.maximum(true, 1),
                   painted + (ys - true) * (REGISTERED_FROM - painted) / np.maximum(REGISTERED_FROM - true, 1))
    out = img.copy()
    y0 = np.clip(np.floor(src).astype(int), 0, H - 2)
    t = (src - y0)[..., None]
    cols = np.arange(W)[None, :]
    out[:REGISTERED_FROM] = img[y0, cols] * (1 - t) + img[y0 + 1, cols] * t
    return out


def tone_match(img):
    """The painting brought to the stills' exposure and palette: the guide lit the ground as if the
    sun were overhead, the scene's sun is 16 degrees up. tone_match.json is a per-channel curve
    fitted once by quantile-matching the S1a painting to the graded r9b S1a still."""
    lut = np.array(json.loads((HERE / 'tone_match.json').read_text())['lut']) / 255.0
    x = np.linspace(0, 1, lut.shape[1])
    return np.stack([np.interp(img[..., c], x, lut[c]) for c in range(3)], -1)


def write_exr(path, rgb):
    """Scanline float32 RGBA OpenEXR, no compression, alpha 1."""
    h, w = rgb.shape[:2]
    chans = {'A': np.ones((h, w), np.float32), 'B': rgb[..., 2], 'G': rgb[..., 1], 'R': rgb[..., 0]}

    def attr(name, kind, data):
        return name.encode() + b'\0' + kind.encode() + b'\0' + struct.pack('<i', len(data)) + data

    chlist = b''.join(c.encode() + b'\0' + struct.pack('<iB3xii', 2, 0, 1, 1) for c in chans) + b'\0'
    box = struct.pack('<iiii', 0, 0, w - 1, h - 1)
    header = (b'\x76\x2f\x31\x01' + struct.pack('<i', 2)
              + attr('channels', 'chlist', chlist) + attr('compression', 'compression', b'\0')
              + attr('dataWindow', 'box2i', box) + attr('displayWindow', 'box2i', box)
              + attr('lineOrder', 'lineOrder', b'\0') + attr('pixelAspectRatio', 'float', struct.pack('<f', 1))
              + attr('screenWindowCenter', 'v2f', struct.pack('<ff', 0, 0))
              + attr('screenWindowWidth', 'float', struct.pack('<f', 1)) + b'\0')
    line = 4 * w * len(chans)
    start = len(header) + 8 * h
    offsets = struct.pack('<%dQ' % h, *[start + y * (8 + line) for y in range(h)])
    with open(path, 'wb') as fh:
        fh.write(header + offsets)
        for y in range(h):
            fh.write(struct.pack('<ii', y, line))
            for c in chans.values():
                fh.write(np.ascontiguousarray(c[y], dtype='<f4').tobytes())


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def main():
    sys.path.insert(0, str(HERE))
    import make_plate_guides as mg
    axis, disp = lut()
    OUT.mkdir(parents=True, exist_ok=True)
    manifest = {'plates': []}
    for kf, shot in KF.items():
        raw = HERE / 'raw' / f'{kf}.png'
        img = np.asarray(Image.open(raw).convert('RGB').resize((W, H), Image.LANCZOS), np.float64) / 255
        if kf == 'k6':
            _, hit = mg.ground(kf, finished={}, base=mg.load(mg.roster.GAME_CORE / 'graphics' / 'planets' / 'desert01.jpg'))
            img = fix_horizon(img, hit)
        img = tone_match(img)
        lin, back = ungrade(img, axis, disp)
        err = np.abs(back - img).max(-1)
        print(f'{kf}: round trip through the grade, mean {err.mean() * 255:.2f}, 99th pct '
              f'{np.percentile(err, 99) * 255:.2f} code values of 255')
        exr = OUT / f'{kf}.backdrop.exr'
        write_exr(exr, lin.astype(np.float32))
        Image.fromarray((np.clip(img, 0, 1) * 255 + .5).astype(np.uint8)).save(OUT / f'{kf}.display.png')
        manifest['plates'].append({'kf': kf, 'shot': shot, 'file': exr.name, 'sha256': sha(exr),
                                   'source': f'art-source/company-prologue/asharu-battle/plates/raw/{kf}.png',
                                   'source_sha256': sha(raw), 'size': [W, H],
                                   'space': 'scene-linear Rec.709, alpha 1',
                                   'graded_by': "AgX, look 'AgX - Base Contrast', sRGB display"})
    (OUT / 'manifest.json').write_text(json.dumps(manifest, indent=1))
    print(OUT)
    if '--post' in sys.argv:
        commit = sys.argv[sys.argv.index('--post') + 1]
        dest = Path('G:/My Drive/starsector/builds/asharu-plates') / commit
        dest.mkdir(parents=True, exist_ok=True)
        for f in OUT.iterdir():
            if f.suffix in ('.exr', '.json'):
                shutil.copy2(f, dest / f.name)
        print('POSTED', dest)


if __name__ == '__main__':
    main()
