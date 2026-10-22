"""Asharu battle: compose a key frame's layered render into its still (see asharu-battle-shot.md, "Layers").

battle_blockout.py --layers renders each key frame as three view layers into one multilayer EXR,
raw/<kf>.layers.exr, and calls compose_frame() to write raw/<kf>.png from it. The same compose runs
on its own, in seconds, so a new plate or a regraded backdrop never re-renders a hull:

    blender -b --factory-startup --python composite_layers.py -- EXR OUT.png
            [--plates DIR | --no-plates] [--kf k0] [--backdrop CACHED.exr] [--diff REF.png [--diff-out D.png]]

Layers, back to front, all scene-linear Rec.709 with premultiplied alpha:
    backdrop  the planet and the starfield: its Combined over its Environment (the stars, which film
              transparency takes out of Combined). A painted plate replaces it wholesale.
    ships     the hulls, lit by everything (effects and planet indirect-only), alpha over the backdrop
    effects   volumes, shields, rounds, bursts, with ships and planet held out, alpha over the rest
The result goes through the scene's own view (AgX, 'AgX - Base Contrast'), the transform a classic
render writes its PNG with, so a layered still and a combined one are the same picture.

Plates (agreed with the planet session, 2026-10-22) are painted on exactly the key frame's camera at
1920x1080 (sources and prompts in plates/; the finished plates on Drive builds/asharu-plates/<commit>/,
roster.PLATES, copied to build/asharu-battle/plates/). A display-referred <plates>/<kf>/plate.png is (painted over a guide graded like the stills), and AgX does not invert cleanly, so
it is swapped in after the view transform: out = full + (1 - a) * (plate - backdrop), with full the
still as rendered, backdrop the rendered backdrop through the same view and a the coverage of the
ships and effects over it. Where nothing covers the plate the still is the plate; under a hull it is
the hull; a drive's glow keeps its own tone over the painting. A scene-linear plate
(<plates>/<kf>.backdrop.exr, delivered on Drive builds/asharu-plates/<commit>/) replaces the backdrop
before the view instead; where it carries a premultiplied matte, the rendered backdrop (the stars)
shows through. The backdrop is rendered (16 samples, once per camera) whatever the plate.
--diff compares the still with a reference PNG (a combined render of the same frame) and writes a
heat map of the difference.

Runs in Blender's Python: OpenImageIO and numpy ship with it.
"""
import json
import sys
from pathlib import Path

import numpy as np

LAYERS = ('backdrop', 'ships', 'effects')     # back to front; the EXR's view layer names
BACKDROP_SAMPLES = 16                         # the planet and the stars: smooth, cheap, rendered once per camera


PLATES = Path(__file__).resolve().parents[3] / 'build' / 'asharu-battle' / 'plates'   # roster.PLATES, copied from Drive


def plate_for(plates, kf):
    """The painted backdrop for this key frame, or None: a scene-linear <plates>/<kf>.backdrop.exr, else a
    display-referred <plates>/<kf>/plate.png. plates=None reads PLATES; '' means no plates at all."""
    root = PLATES if plates is None else (Path(plates) if plates else None)
    if root is None:
        return None
    for p in (root / f'{kf}.backdrop.exr', root / kf / 'plate.png'):
        if p.exists():
            return p
    return None


def is_linear(plate):
    return plate is not None and Path(plate).suffix.lower() == '.exr'


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


def _read(path, size, kind):
    oiio = _oiio()
    buf = oiio.ImageBuf(str(path))
    spec = buf.spec()
    if (spec.width, spec.height) != size:
        buf = oiio.ImageBufAlgo.resize(buf, roi=oiio.ROI(0, size[0], 0, size[1], 0, 1, 0, spec.nchannels))
        spec = buf.spec()
    return np.asarray(buf.get_pixels(kind), dtype=np.float32).reshape(spec.height, spec.width, spec.nchannels)


def read_rgba(path, size):
    """A scene-linear plate or a cached backdrop as HxWx4, alpha 1 where it has none."""
    px = _read(path, size, _oiio().FLOAT)
    rgb = px[..., :3]
    a = px[..., 3:4] if px.shape[-1] > 3 else np.ones_like(rgb[..., :1])
    return np.concatenate([rgb, a], -1)


def read_display(path, size):
    """A display-referred image (a painted plate, a 16-bit intermediate) as HxWx3 in 0..1."""
    return _read(path, size, _oiio().FLOAT)[..., :3]


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
    """HxWx4 scene-linear and its backdrop: a scene-linear plate, else the EXR's own backdrop layer, else
    the camera's cached one; then the ships over it and the effects over them.
    Returns (rgba, backdrop, foreground coverage, which backdrop was used)."""
    h, w = layers['ships']['Combined'].shape[:2]
    if 'backdrop' in layers:
        bg, used = backdrop_of(layers), 'rendered'
    elif backdrop is not None and Path(backdrop).exists():
        bg, used = read_rgba(backdrop, (w, h)), f'cached {Path(backdrop).name}'
    else:
        bg, used = None, None
    if is_linear(plate):
        p = read_rgba(plate, (w, h))
        if (p[..., 3] < 1.0).any():      # a matted plate (premultiplied): the rendered stars show through its sky
            if bg is None:
                raise SystemExit(f'{plate} has a matte, and there is no rendered or cached backdrop under it')
            bg, used = over(p, bg), f'plate {Path(plate).name} over {used}'
        else:
            bg, used = p, f'plate {Path(plate).name}'
    if bg is None:
        raise SystemExit('no backdrop: the EXR has no backdrop layer, and there is no plate or cached backdrop')
    fg = over(layers['effects']['Combined'], layers['ships']['Combined'])
    return over(fg, bg), bg, np.clip(fg[..., 3:4], 0.0, 1.0), used


def save_png(rgba, path, scene, depth='8'):
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
        st.file_format, st.color_mode, st.color_depth = 'PNG', 'RGBA', depth
        try:
            img.save_render(str(path), scene=scene)
        finally:
            st.file_format, st.color_mode, st.color_depth = saved
    finally:
        bpy.data.images.remove(img)


def swap_plate(rgba, bg, cover, plate, png, scene):
    """A display-referred plate in place of the rendered backdrop, after the view transform:
    out = full + (1 - cover) * (plate - backdrop), all through the scene's view."""
    import tempfile
    oiio = _oiio()
    h, w = rgba.shape[:2]
    with tempfile.TemporaryDirectory() as tmp:
        full_p, bg_p = Path(tmp) / 'full.png', Path(tmp) / 'backdrop.png'
        save_png(rgba, full_p, scene, depth='16')
        save_png(bg, bg_p, scene, depth='16')
        full, back = read_display(full_p, (w, h)), read_display(bg_p, (w, h))
    out = np.clip(full + (1.0 - cover) * (read_display(plate, (w, h)) - back), 0.0, 1.0)
    out8 = np.concatenate([np.round(out * 255.0), np.full((h, w, 1), 255.0)], -1).astype(np.uint8)
    buf = oiio.ImageBuf(oiio.ImageSpec(w, h, 4, oiio.UINT8))
    buf.set_pixels(oiio.ROI(0, w, 0, h, 0, 1, 0, 4), np.ascontiguousarray(out8))
    if not buf.write(str(png), fileformat='png'):
        raise SystemExit(f'cannot write {png}: {buf.geterror()}')


def compose_frame(exr, png, scene, plate=None, backdrop=None, log=print):
    """raw/<kf>.layers.exr -> the still. A backdrop the EXR rendered is cached at `backdrop` for the next
    frame with the same camera; one it did not render is read from there."""
    layers = read_layers(exr)
    missing = [n for n in ('ships', 'effects') if n not in layers]
    if missing:
        raise SystemExit(f'{exr}: no {", ".join(missing)} layer (has {", ".join(layers)})')
    rgba, bg, cover, used = compose(layers, plate, backdrop)
    if 'backdrop' in layers and backdrop is not None:
        Path(backdrop).parent.mkdir(parents=True, exist_ok=True)
        write_rgba(backdrop, backdrop_of(layers))
    if plate is not None and not is_linear(plate):
        swap_plate(rgba, bg, cover, plate, png, scene)
        used += f', plate {Path(plate).parent.name}/{Path(plate).name} swapped in after the view'
    else:
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
    plate = plate_for(opt('--plates'), kf) if '--no-plates' not in argv else None
    compose_frame(exr, png, scene, plate=plate, backdrop=opt('--backdrop'))
    if opt('--diff'):
        diff(png, opt('--diff'), opt('--diff-out'))


if __name__ == '__main__':
    main(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else [])
