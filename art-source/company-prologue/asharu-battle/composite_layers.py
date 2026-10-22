"""Asharu battle: compose a key frame's layered render into its still (see asharu-battle-shot.md, "Layers").

battle_blockout.py --layers renders each key frame as three view layers into one multilayer EXR,
raw/<kf>.layers.exr, and calls compose_frame() to write raw/<kf>.png from it. The same compose runs
on its own, in seconds, so a new plate or a regraded backdrop never re-renders a hull:

    blender -b --factory-startup --python composite_layers.py -- EXR OUT.png
            [--plates DIR] [--kf k0] [--backdrop CACHED.exr] [--diff REF.png [--diff-out D.png]]

Layers, back to front, all scene-linear Rec.709 with premultiplied alpha:
    backdrop  the planet and the starfield: its Combined over its Environment (the stars, which film
              transparency takes out of Combined). A painted plate replaces it wholesale.
    ships     the hulls, lit by everything (effects and planet indirect-only), alpha over the backdrop
    effects   volumes, shields, rounds, bursts, with ships and planet held out, alpha over the rest
The result goes through the scene's own view (AgX, 'AgX - Base Contrast'), the transform a classic
render writes its PNG with, so a layered still and a combined one are the same picture.

Plates (agreed with the planet session): <plates>/<kf>.backdrop.exr, 1920x1080, scene-linear Rec.709,
alpha 1; an sRGB <kf>.backdrop.png is accepted and linearised. --diff compares the still with a
reference PNG (a combined render of the same frame) and writes a heat map of the difference.

Runs in Blender's Python: OpenImageIO and numpy ship with it.
"""
import json
import sys
from pathlib import Path

import numpy as np

LAYERS = ('backdrop', 'ships', 'effects')     # back to front; the EXR's view layer names
BACKDROP_SAMPLES = 16                         # the planet and the stars: smooth, cheap, rendered once per camera


def plate_for(plates, kf):
    """The planet session's painted backdrop for this key frame, or None."""
    if not plates:
        return None
    for ext in ('exr', 'png'):
        p = Path(plates) / f'{kf}.backdrop.{ext}'
        if p.exists():
            return p
    return None


def _oiio():
    import OpenImageIO as oiio
    return oiio


def read_layers(path):
    """{layer: {pass: HxWxC float32}} from a Blender multilayer EXR, top row first."""
    oiio = _oiio()
    out = {}
    inp = oiio.ImageInput.open(str(path))
    if inp is None:
        raise SystemExit(f'cannot open {path}: {oiio.geterror()}')
    try:
        sub = 0
        while inp.seek_subimage(sub, 0):
            spec = inp.spec()
            px = inp.read_image(0, spec.nchannels, oiio.FLOAT)
            px = np.asarray(px, dtype=np.float32).reshape(spec.height, spec.width, spec.nchannels)
            for i, name in enumerate(spec.channelnames):
                head, chan = name.rsplit('.', 1) if '.' in name else ('', name)
                layer, pass_ = head.split('.', 1) if '.' in head else ('', head)
                out.setdefault(layer, {}).setdefault(pass_, {})[chan] = px[..., i]
            sub += 1
    finally:
        inp.close()
    order = {'R': 0, 'G': 1, 'B': 2, 'A': 3, 'X': 0, 'Y': 1, 'Z': 2, 'V': 0}
    return {lay: {p: np.stack([ch[c] for c in sorted(ch, key=lambda c: order.get(c, 9))], -1)
                  for p, ch in passes.items()}
            for lay, passes in out.items()}


def read_rgba(path, size):
    """A plate or a cached backdrop as HxWx4 scene-linear, alpha 1 where it has none."""
    oiio = _oiio()
    buf = oiio.ImageBuf(str(path))
    if Path(path).suffix.lower() == '.png':
        buf = oiio.ImageBufAlgo.colorconvert(buf, 'sRGB', 'linear')
    spec = buf.spec()
    if (spec.width, spec.height) != size:
        buf = oiio.ImageBufAlgo.resize(buf, roi=oiio.ROI(0, size[0], 0, size[1], 0, 1, 0, spec.nchannels))
        spec = buf.spec()
    px = np.asarray(buf.get_pixels(oiio.FLOAT), dtype=np.float32).reshape(spec.height, spec.width, spec.nchannels)
    rgb = px[..., :3]
    a = px[..., 3:4] if spec.nchannels > 3 else np.ones_like(rgb[..., :1])
    return np.concatenate([rgb, a], -1)


def write_rgba(path, rgba):
    oiio = _oiio()
    h, w, _ = rgba.shape
    spec = oiio.ImageSpec(w, h, 4, oiio.HALF)
    spec.attribute('compression', 'zip')
    out = oiio.ImageOutput.create(str(path))
    out.open(str(path), spec)
    out.write_image(np.ascontiguousarray(rgba, dtype=np.float32))
    out.close()


def over(top, under):
    """Premultiplied alpha-over."""
    return top + (1.0 - top[..., 3:4]) * under


def backdrop_of(layers):
    """The rendered backdrop: its Combined (the planet, the air) over its Environment (the stars)."""
    b = layers['backdrop']
    c = b['Combined']
    env = b.get('Env')
    if env is None:
        return c
    env = np.concatenate([env[..., :3], np.ones_like(env[..., :1])], -1)
    return over(c, env)


def compose(layers, plate=None, backdrop=None):
    """HxWx4 scene-linear: plate, else the cached backdrop, else the EXR's own backdrop layer; then the
    ships over it and the effects over them. Returns (rgba, which backdrop was used)."""
    h, w = layers['ships']['Combined'].shape[:2]
    if plate is not None:
        bg, used = read_rgba(plate, (w, h)), f'plate {Path(plate).name}'
    elif 'backdrop' in layers:
        bg, used = backdrop_of(layers), 'rendered'
    elif backdrop is not None and Path(backdrop).exists():
        bg, used = read_rgba(backdrop, (w, h)), f'cached {Path(backdrop).name}'
    else:
        raise SystemExit('no backdrop: the EXR has no backdrop layer, and there is no plate or cached backdrop')
    out = over(layers['ships']['Combined'], bg)
    out = over(layers['effects']['Combined'], out)
    return out, used


def save_png(rgba, path, scene):
    """Through the scene's view transform, as a render's PNG is written."""
    import bpy
    h, w, _ = rgba.shape
    img = bpy.data.images.new('Asharu composite', w, h, alpha=True, float_buffer=True)
    try:
        flat = np.ascontiguousarray(rgba[::-1].astype(np.float32))     # Blender's pixels start at the bottom row
        flat[..., 3] = np.clip(flat[..., 3], 0.0, 1.0)
        img.pixels.foreach_set(flat.ravel())
        st = scene.render.image_settings
        saved = (st.file_format, st.color_mode, st.color_depth)
        st.file_format, st.color_mode, st.color_depth = 'PNG', 'RGBA', '8'
        try:
            img.save_render(str(path), scene=scene)
        finally:
            st.file_format, st.color_mode, st.color_depth = saved
    finally:
        bpy.data.images.remove(img)


def compose_frame(exr, png, scene, plate=None, backdrop=None, log=print):
    """raw/<kf>.layers.exr -> the still. A backdrop the EXR rendered is cached at `backdrop` for the next
    frame with the same camera; one it did not render is read from there."""
    layers = read_layers(exr)
    missing = [n for n in ('ships', 'effects') if n not in layers]
    if missing:
        raise SystemExit(f'{exr}: no {", ".join(missing)} layer (has {", ".join(layers)})')
    rgba, used = compose(layers, plate, backdrop)
    if used == 'rendered' and backdrop is not None:
        Path(backdrop).parent.mkdir(parents=True, exist_ok=True)
        write_rgba(backdrop, backdrop_of(layers))
    save_png(rgba, png, scene)
    log(f'ASHARU composite {Path(exr).name} -> {Path(png).name}: backdrop {used}; '
        f'layers {", ".join(n for n in LAYERS if n in layers)}')
    return used


def read_png(path):
    oiio = _oiio()
    buf = oiio.ImageBuf(str(path))
    spec = buf.spec()
    px = np.asarray(buf.get_pixels(oiio.UINT8), dtype=np.uint8).reshape(spec.height, spec.width, spec.nchannels)
    return px[..., :3].astype(np.float32)


def diff(png, ref, out=None):
    """The still against a reference still of the same frame, in 8-bit display values. A reference at
    twice the size (a full-size combined render against a --half layered check) is box-filtered down."""
    a, b = read_png(png), read_png(ref)
    if b.shape[0] == 2 * a.shape[0] and b.shape[1] == 2 * a.shape[1]:
        b = b.reshape(a.shape[0], 2, a.shape[1], 2, 3).mean((1, 3))
    if a.shape != b.shape:
        raise SystemExit(f'size mismatch: {png} {a.shape[:2]} against {ref} {b.shape[:2]}')
    d = np.abs(a - b).max(-1)
    # noise is per pixel; a layering fault is a shape: so also compare at a blur that averages noise away
    k = 8
    hh, ww = (d.shape[0] // k) * k, (d.shape[1] // k) * k
    blur = np.abs(a[:hh, :ww].reshape(hh // k, k, ww // k, k, 3).mean((1, 3))
                  - b[:hh, :ww].reshape(hh // k, k, ww // k, k, 3).mean((1, 3))).max(-1)
    row = dict(mean=float(d.mean()), p99=float(np.percentile(d, 99)), max=float(d.max()),
               over_8=float((d > 8).mean()), over_32=float((d > 32).mean()),
               blur8_mean=float(blur.mean()), blur8_p99=float(np.percentile(blur, 99)), blur8_max=float(blur.max()),
               mean_level=dict(still=float(a.mean()), reference=float(b.mean())))
    print('ASHARU diff', Path(png).name, 'against', Path(ref).name, json.dumps(row), flush=True)
    if out is not None:
        oiio = _oiio()
        heat = np.clip(d * 4, 0, 255).astype(np.uint8)
        img = np.stack([heat, heat, heat], -1)
        buf = oiio.ImageBuf(oiio.ImageSpec(img.shape[1], img.shape[0], 3, oiio.UINT8))
        buf.set_pixels(oiio.ROI(0, img.shape[1], 0, img.shape[0], 0, 1, 0, 3), np.ascontiguousarray(img))
        buf.write(str(out))
    return row


def main(argv):
    import bpy

    def opt(flag, default=None):
        return argv[argv.index(flag) + 1] if flag in argv else default

    exr, png = argv[0], argv[1]
    scene = bpy.context.scene
    scene.view_settings.view_transform = 'AgX'
    scene.view_settings.look = 'AgX - Base Contrast'
    kf = opt('--kf') or Path(exr).name.split('.')[0]
    compose_frame(exr, png, scene, plate=plate_for(opt('--plates'), kf), backdrop=opt('--backdrop'))
    if opt('--diff'):
        diff(png, opt('--diff'), opt('--diff-out'))


if __name__ == '__main__':
    main(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])
