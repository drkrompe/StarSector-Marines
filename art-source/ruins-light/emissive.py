"""Emissive overlays for the ruins' machinery and consoles (``ruins-light``): for each ruins doodad sheet, a lit and
an unlit overlay in the sheet's own layout, at half its resolution, so a piece's overlay is the same frame of the
overlay sheet as the piece is of its sheet. Run from the worktree root: ``python art-source/ruins-light/emissive.py``.

Derived, not drawn: the sheets' painters already put screens and status lamps on the kit as small bright cyan, amber,
green and red marks. A pixel is a lamp when it is bright and saturated in one of those hues; dull ochre trim and rust
are darker and are left out, and a lamp must be a small mark (a big bright field such as a lava pool is not a lamp).

- ``<sheet>-lit.png``: the lamps at full glow plus a short soft bloom kept inside the piece, drawn over the piece
  (additive or plain alpha both read) when its circuit is on.
- The forge's molten pieces (great crucible, furnace core, pour spots) are lamps by this test too: a piece that
  stays molten whatever the power should not take the overlay.
- ``<sheet>-unlit.png``: dark dead glass over the same lamps, drawn over the piece when its circuit is off, so a dark
  hall reads as dead rather than idling.
"""
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter

ROOT = Path(__file__).resolve().parents[2]
# The sheets whose painters drew lamps. The works kit, bays and giants were graded grey and carry next to none (a
# white-glass rule caught only frost and metal highlights), so they get no overlay.
SHEETS = ("ruins-themes", "ruins-forge", "ruins-rooms", "ruins-works-plant")
OUT = ROOT / "mod" / "graphics" / "battle" / "lights" / "emissive"
SCALE = 2  # the overlays are half the sheet's resolution: a glow needs no more
# (hue lo, hue hi in degrees, min saturation, min value, glow colour)
HUES = [(165, 215, 0.30, 0.55, (110, 235, 255)),   # cyan screens and status lights
        (28, 52, 0.55, 0.80, (255, 196, 90)),      # amber lamps
        (85, 150, 0.40, 0.55, (130, 255, 140)),    # green lamps
        (345, 375, 0.60, 0.70, (255, 70, 50))]     # red lamps
MAX_BLOB = 0.02  # a lamp is at most this share of its cell's area (128 px cells)


def hsv(rgb):
    r, g, b = [rgb[..., i] / 255.0 for i in range(3)]
    mx, mn = np.maximum(np.maximum(r, g), b), np.minimum(np.minimum(r, g), b)
    d = mx - mn
    h = np.zeros_like(mx)
    nz = d > 1e-6
    rm = nz & (mx == r)
    gm = nz & (mx == g) & ~rm
    bm = nz & ~rm & ~gm
    h[rm] = (60 * ((g - b)[rm] / d[rm])) % 360
    h[gm] = 60 * ((b - r)[gm] / d[gm]) + 120
    h[bm] = 60 * ((r - g)[bm] / d[bm]) + 240
    s = np.where(mx > 0, d / np.maximum(mx, 1e-6), 0)
    return h, s, mx


def lamps(sheet):
    """Per hue, a boolean lamp mask over the sheet."""
    a = np.asarray(sheet.convert("RGBA")).astype(np.float32)
    h, s, v = hsv(a)
    opaque = a[..., 3] > 200
    out = []
    for lo, hi, smin, vmin, colour in HUES:
        hh = np.where(h < lo, h + 360, h) if hi > 360 else h
        m = opaque & (hh >= lo) & (hh <= hi) & (s >= smin) & (v >= vmin)
        out.append((drop_fields(m), colour))
    return out


def drop_fields(m):
    """Clears connected marks bigger than a lamp (a pool, a hot field), by 128 px cell."""
    from collections import deque
    hgt, wid = m.shape
    seen = np.zeros_like(m)
    limit = MAX_BLOB * 128 * 128
    ys, xs = np.nonzero(m)
    for y0, x0 in zip(ys, xs):
        if seen[y0, x0]:
            continue
        comp, q = [], deque([(y0, x0)])
        seen[y0, x0] = True
        while q:
            y, x = q.popleft()
            comp.append((y, x))
            for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                ny, nx = y + dy, x + dx
                if 0 <= ny < hgt and 0 <= nx < wid and m[ny, nx] and not seen[ny, nx]:
                    seen[ny, nx] = True
                    q.append((ny, nx))
        if len(comp) > limit:
            for y, x in comp:
                m[y, x] = False
    return m


def overlays(name):
    sheet = Image.open(ROOT / "mod" / "graphics" / "doodads" / f"{name}.png").convert("RGBA")
    w, h = sheet.width // SCALE, sheet.height // SCALE
    alpha = sheet.getchannel("A").resize((w, h), Image.BILINEAR)
    lit = np.zeros((h, w, 4), np.float32)
    dead = np.zeros((h, w, 4), np.float32)
    for mask, colour in lamps(sheet):
        core = Image.fromarray((mask * 255).astype(np.uint8)).resize((w, h), Image.BILINEAR)
        bloom = core.filter(ImageFilter.MaxFilter(3)).filter(ImageFilter.GaussianBlur(3))
        c = np.asarray(core, np.float32) / 255
        b = np.asarray(bloom, np.float32) / 255
        glow = np.clip(c + 0.6 * b, 0, 1)
        hot = np.clip(np.array(colour, np.float32) + (255 - np.array(colour, np.float32)) * 0.5 * c[..., None], 0, 255)
        k = glow > lit[..., 3] / 255
        lit[k, :3] = hot[k]
        lit[k, 3] = glow[k] * 255
        d = c > dead[..., 3] / 255
        dark = np.array(colour, np.float32) * 0.12 + 18
        dead[d, :3] = dark
        dead[d, 3] = c[d] * 235
    keep = np.asarray(alpha, np.float32) / 255  # the bloom stays on the piece
    lit[..., 3] *= keep
    dead[..., 3] *= keep
    return (Image.fromarray(lit.astype(np.uint8), "RGBA"), Image.fromarray(dead.astype(np.uint8), "RGBA"),
            int((lit[..., 3] > 64).sum()))


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for name in SHEETS:
        lit, dead, n = overlays(name)
        lit.save(OUT / f"{name}-lit.png", optimize=True)
        dead.save(OUT / f"{name}-unlit.png", optimize=True)
        print(name, lit.size, n, "lit px")


if __name__ == "__main__":
    main()
