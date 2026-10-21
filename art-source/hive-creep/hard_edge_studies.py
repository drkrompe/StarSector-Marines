"""Hard-edge hive creep look studies (round 1), drawn over real colony ground.

Not shipped code: a look prototype, so the owner can pick a rim and a surface
before the edge set and the relief companion are built. Each of four numbered
variants pairs one rim profile with one surface; the plan shape is the same for
all four so they compare like for like.

Two renderings per variant:
  lit      -- a CPU model of the ground composite: per-pixel normal from the
              creep's height (metres, 1 m per cell), Lambert plus a waxy
              highlight, and a cast shadow marched over the height field
              toward a sun at 38 degrees elevation from the north-west.
  fallback -- what a review frame can afford: flat albedo with the surface's
              detail baked in, a baked rim highlight on the sun side of the lip
              and an offset drop shadow.

Usage: python hard_edge_studies.py <ground-z17.png> <ground-z36.png> <out-dir>
The grounds are colony-breach review frames (creep off) at 17.1 and 36 px/cell.
"""
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont

SUN_EL = math.radians(38.0)
SUN_XY = np.array([-1.0, -1.0]) / math.sqrt(2.0)    # toward the sun, image axes (x right, y down): north-west
SUN = np.array([SUN_XY[0] * math.cos(SUN_EL), SUN_XY[1] * math.cos(SUN_EL), math.sin(SUN_EL)])

BASE = np.array([62, 51, 41], float) / 255
CREAM = np.array([184, 166, 128], float) / 255
CHITIN = np.array([104, 86, 64], float) / 255
VEIN = np.array([74, 40, 32], float) / 255
DEAD = np.array([96, 88, 76], float) / 255


# ---------------------------------------------------------------- noise ----

def _lattice(seed, n=256):
    return np.random.default_rng(seed).random((n, n))


_LAT = {s: _lattice(s) for s in range(12)}


def vnoise(x, y, seed):
    """Smooth value noise, roughly 0..1, one feature per unit."""
    lat = _LAT[seed]
    n = lat.shape[0]
    xi = np.floor(x).astype(int)
    yi = np.floor(y).astype(int)
    fx = x - xi
    fy = y - yi
    fx = fx * fx * (3 - 2 * fx)
    fy = fy * fy * (3 - 2 * fy)
    a = lat[yi % n, xi % n]
    b = lat[yi % n, (xi + 1) % n]
    c = lat[(yi + 1) % n, xi % n]
    d = lat[(yi + 1) % n, (xi + 1) % n]
    return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy


def fbm(x, y, seed, octaves=4):
    v = 0.0
    amp = 0.5
    tot = 0.0
    for o in range(octaves):
        v = v + amp * vnoise(x * (2 ** o), y * (2 ** o), (seed + o) % 12)
        tot += amp
        amp *= 0.5
    return v / tot


def worley(x, y, seed):
    """F1, F2 and a per-cell hash for a jittered grid, unit spacing."""
    rng = np.random.default_rng(100 + seed)
    jit = rng.random((64, 64, 3))
    xi = np.floor(x).astype(int)
    yi = np.floor(y).astype(int)
    f1 = np.full(x.shape, 9.0)
    f2 = np.full(x.shape, 9.0)
    h = np.zeros(x.shape)
    for oy in (-1, 0, 1):
        for ox in (-1, 0, 1):
            cx = xi + ox
            cy = yi + oy
            j = jit[cy % 64, cx % 64]
            px = cx + j[..., 0]
            py = cy + j[..., 1]
            d = np.sqrt((x - px) ** 2 + (y - py) ** 2)
            closer = d < f1
            f2 = np.where(closer, f1, np.minimum(f2, d))
            h = np.where(closer, j[..., 2], h)
            f1 = np.where(closer, d, f1)
    return f1, f2, h


def smooth(e0, e1, v):
    t = np.clip((v - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


def bump(d, centre, width):
    return np.exp(-((d - centre) / width) ** 2)


# ----------------------------------------------------------------- plan ----

def plan_field(cx, cy, src, radius):
    """Signed distance (cells, positive inside) of a lobed, fingered patch."""
    dx = cx - src[0]
    dy = cy - src[1]
    r = np.sqrt(dx * dx + dy * dy)
    wx = (fbm(cx * 0.2, cy * 0.2, 1) - 0.5) * 2.4
    wy = (fbm(cx * 0.2 + 31, cy * 0.2 + 17, 2) - 0.5) * 2.4
    rw = np.sqrt((dx + wx) ** 2 + (dy + wy) ** 2)
    # lobes: broad spatial bulges, so the edge swells and dents without spiking
    lobes = (fbm(cx * 0.32, cy * 0.32, 3, 3) - 0.5) * radius * 0.75
    # fingers: ridged-noise channels the creep runs ahead along, fading past 1.7 R
    ridge = 1 - np.abs(2 * fbm(cx * 0.16 + 5, cy * 0.16 + 9, 4, 3) - 1)
    fingers = smooth(0.62, 0.90, ridge) ** 1.5 * radius * 1.0 * smooth(2.0 * radius, 0.8 * radius, r)
    f = radius + lobes + fingers - rw
    # normalise to a distance: divide by the gradient's magnitude
    gy, gx = np.gradient(f)
    step = cx[0, 1] - cx[0, 0]
    g = np.sqrt(gx * gx + gy * gy) / step
    return f / np.maximum(g, 0.35), r


# ----------------------------------------------------------------- rims ----

PLATEAU = 0.09   # metres
ORGANIC_PLATES = False   # round 3: warped, pillowed chitin plates

def rim_height(v, d, cx, cy):
    if v == 1:   # rolled bead: a fat rounded lip with a sheer outer wall
        return PLATEAU + 0.20 * bump(d, 0.26, 0.17)
    if v == 2:   # thin hard crust: a narrow sharp ridge and a sag behind it
        return PLATEAU + 0.13 * bump(d, 0.07, 0.07) - 0.035 * bump(d, 0.32, 0.14)
    if v == 3:   # terraced: overlapping sheets stepping up inward
        n = fbm(cx * 0.9, cy * 0.9, 5, 2)
        s1 = 0.38 + 0.25 * n
        s2 = 0.95 + 0.4 * n
        return (0.05 + 0.05 * smooth(s1, s1 + 0.06, d) + 0.04 * smooth(s2, s2 + 0.06, d)
                + 0.03 * bump(d, 0.04, 0.05) + 0.025 * bump(d, s1 + 0.06, 0.05))
    # 4: beaded lip: the edge rolls up into a chain of blisters
    f1, _, hh = worley(cx * 2.6, cy * 2.6, 6)
    bead = smooth(0.62, 0.05, f1) * (0.75 + 0.5 * hh)
    return PLATEAU + bump(d, 0.24, 0.2) * (0.06 + 0.17 * bead)


# -------------------------------------------------------------- surfaces ----

def surface(v, d, cx, cy, src):
    """Micro height (metres) and albedo for one surface."""
    shp = d.shape
    veins_n = fbm(cx * 0.55, cy * 0.55, 7, 3)
    vein = smooth(0.035, 0.0, np.abs(veins_n - 0.5)) * smooth(0.25, 0.8, d)
    if v == 1:   # wrinkled membrane: crumpled fine ridges, no direction
        wx = (fbm(cx * 0.9, cy * 0.9, 8, 2) - 0.5) * 1.2
        rg = 1 - np.abs(2 * fbm((cx + wx) * 2.4, (cy - wx) * 2.4, 9, 3) - 1)
        w = rg ** 3
        fine = fbm(cx * 7.0, cy * 7.0, 10, 2) - 0.5
        micro = 0.016 * w + 0.005 * fine
        tone = w
        alb = BASE[None, None] * (0.78 + 0.45 * tone[..., None]) + (CREAM - BASE)[None, None] * (0.22 * smooth(0.55, 1.0, tone))[..., None]
        spec = 0.12
    elif v == 2:   # fibrous striation running out from the source, veins proud of it
        dx = cx - src[0]
        dy = cy - src[1]
        theta = np.arctan2(dy, dx)
        r = np.sqrt(dx * dx + dy * dy)
        fib = fbm(theta * 38.0, r * 0.7, 10, 3)
        fib2 = fbm(theta * 90.0, r * 1.4, 11, 2)
        s = 0.6 * fib + 0.4 * fib2
        micro = 0.014 * (s - 0.5) + 0.02 * vein
        alb = BASE[None, None] * (0.72 + 0.6 * s[..., None])
        alb = alb + (CREAM - BASE)[None, None] * (0.3 * smooth(0.62, 0.85, s))[..., None]
        vein = np.maximum(vein, 0) * 1.0
        spec = 0.08
    elif v == 3:   # nodular: packed small domes with the odd pore
        f1, f2, hh = worley(cx * 4.2, cy * 4.2, 3)
        dome = smooth(0.55, 0.0, f1)
        pore = (hh < 0.07) & (f1 < 0.18)
        micro = 0.018 * dome - 0.03 * pore
        alb = BASE[None, None] * (0.7 + 0.45 * dome[..., None]) + (CREAM - BASE)[None, None] * (0.35 * dome ** 3)[..., None]
        alb = np.where(pore[..., None], VEIN[None, None] * 0.5, alb)
        spec = 0.16
    else:   # chitin plates: grown scales, warped so no seam runs straight, each one pillowed
        if ORGANIC_PLATES:
            wx = (fbm(cx * 1.6, cy * 1.6, 9, 2) - 0.5) * 0.55
            wy = (fbm(cx * 1.6 + 11, cy * 1.6 + 5, 10, 2) - 0.5) * 0.55
            f1, f2, hh = worley((cx + wx) * 3.0, (cy + wy) * 3.0, 4)
            edge = f2 - f1
            seam = smooth(0.10, 0.0, edge)
            pillow = smooth(0.0, 0.30, edge)          # rounds every plate down into its seam
            micro = 0.022 * pillow - 0.006 * f1 + 0.004 * (hh - 0.5)
        else:
            f1, f2, hh = worley(cx * 3.0, cy * 3.0, 4)
            seam = smooth(0.09, 0.0, f2 - f1)
            micro = 0.02 * (1 - seam) - 0.01 * (f1 * 0.6) + 0.004 * (hh - 0.5)
        plate = CHITIN[None, None] * (0.78 + 0.4 * hh[..., None]) + (CREAM - CHITIN)[None, None] * (0.18 * smooth(0.5, 0.0, f1))[..., None]
        alb = plate * (1 - 0.6 * seam[..., None]) + BASE[None, None] * 0.6 * seam[..., None]
        # plates only away from the edge; nearer the lip it is bare membrane
        plated = smooth(0.35, 0.7, d)
        base = BASE[None, None] * (0.85 + 0.25 * fbm(cx * 3, cy * 3, 9, 2)[..., None])
        alb = base * (1 - plated[..., None]) + alb * plated[..., None]
        micro = micro * plated
        spec = 0.22
    alb = alb * (1 - 0.65 * vein[..., None]) + VEIN[None, None] * 0.65 * vein[..., None]
    return micro, np.clip(alb, 0, 1), spec


# --------------------------------------------------------------- render ----

def render(ground, ox, oy, w, h, cell_px, src, radius, variant, lit):
    """Draws one variant's patch over a crop of `ground`. src, radius in cells
    relative to the crop's top-left."""
    crop = np.asarray(ground.crop((ox, oy, ox + w, oy + h)).convert('RGB'), float) / 255
    ys, xs = np.mgrid[0:h, 0:w].astype(float)
    cx = (xs + 0.5) / cell_px
    cy = (ys + 0.5) / cell_px
    d, _ = plan_field(cx, cy, src, radius)
    cover = np.clip(d * cell_px + 0.5, 0, 1)           # one-pixel antialiased hard edge
    inside = d > 0
    dd = np.clip(d, 0, 2.0)          # only the rim reads edge distance; the interior is plateau
    hgt = np.where(inside, rim_height(variant, dd, cx, cy), 0.0)
    micro, alb, spec_k = surface(variant, dd, cx, cy, src)
    hgt = hgt + np.where(inside, micro * smooth(0.0, 0.12, dd), 0)
    # the lip crest is creamier: light catches the rolled membrane
    crest = np.clip((hgt - PLATEAU) / 0.15, 0, 1)
    alb = alb + (CREAM - alb) * (0.35 * crest)[..., None]

    m_per_px = 1.0 / cell_px
    if lit:
        gy, gx = np.gradient(hgt, m_per_px)
        n = np.dstack([-gx, -gy, np.ones_like(hgt)])
        n /= np.linalg.norm(n, axis=2, keepdims=True)
        ndl = np.clip((n * SUN).sum(2), 0, 1)
        flat = SUN[2]
        diffuse = 0.42 + 0.58 * ndl / flat
        half = SUN + np.array([0, 0, 1.0])
        half /= np.linalg.norm(half)
        spec = spec_k * np.clip((n * half).sum(2), 0, 1) ** 28
        col = alb * diffuse[..., None] + spec[..., None]
        # cast shadow: march toward the sun
        rise = math.tan(SUN_EL) * m_per_px
        occl = np.zeros_like(hgt)
        steps = int(0.45 / math.tan(SUN_EL) * cell_px) + 2
        for s in range(1, steps):
            sx = int(round(SUN_XY[0] * s))
            sy = int(round(SUN_XY[1] * s))
            sh = np.roll(np.roll(hgt, -sy, 0), -sx, 1)
            occl = np.maximum(occl, np.clip((sh - (hgt + s * rise)) / 0.02, 0, 1))
        shadow = 1 - 0.55 * occl
        ground_col = crop * shadow[..., None]
        col = col * (1 - 0.45 * occl)[..., None]
    else:
        # baked: emboss of the micro height at half strength, fixed sun
        gy, gx = np.gradient(hgt, m_per_px)
        emb = np.clip(1 + 0.5 * (-(gx * SUN_XY[0] + gy * SUN_XY[1])) * 0.9, 0.7, 1.3)
        lip = bump(dd, 0.12, 0.12) * inside
        # which way the edge faces: the plan's gradient points inward
        pgy, pgx = np.gradient(d)
        pg = np.sqrt(pgx ** 2 + pgy ** 2) + 1e-6
        facing = np.clip(-(pgx * SUN_XY[0] + pgy * SUN_XY[1]) / pg, -1, 1)
        rim_light = 1 + lip * (0.8 * np.clip(-facing, 0, 1) - 0.35 * np.clip(facing, 0, 1))
        rim_light = rim_light * (1 - 0.45 * smooth(0.07, 0.0, dd) * inside)   # baked dark outline: the sheer outer wall
        col = alb * (0.92 * emb * rim_light)[..., None]
        off = int(round(0.16 / math.tan(SUN_EL) * cell_px * 0.7))
        sx = int(round(-SUN_XY[0] * off))
        sy = int(round(-SUN_XY[1] * off))
        drop = np.roll(np.roll(cover, sy, 0), sx, 1)
        drop = (drop + np.roll(drop, 1, 0) + np.roll(drop, 1, 1) + np.roll(drop, -1, 0) + np.roll(drop, -1, 1)) / 5
        ground_col = crop * (1 - 0.6 * drop)[..., None]
    out = ground_col * (1 - cover[..., None]) + np.clip(col, 0, 1) * cover[..., None]
    return Image.fromarray((np.clip(out, 0, 1) * 255).astype(np.uint8))


RIMS = {1: 'rolled bead', 2: 'thin hard crust', 3: 'terraced sheets', 4: 'beaded blisters'}
SURFS = {1: 'wrinkled membrane', 2: 'fibrous striation', 3: 'nodules and pores', 4: 'chitin plates'}


def font(size):
    for f in ('arialbd.ttf', 'DejaVuSans-Bold.ttf'):
        try:
            return ImageFont.truetype(f, size)
        except OSError:
            pass
    return ImageFont.load_default()


def label(img, xy, text, size=18):
    dr = ImageDraw.Draw(img)
    f = font(size)
    x, y = xy
    bb = dr.textbbox((x, y), text, font=f)
    dr.rectangle((bb[0] - 6, bb[1] - 4, bb[2] + 6, bb[3] + 4), fill=(16, 16, 18))
    dr.text((x, y), text, font=f, fill=(236, 230, 214))


def grid_sheet(ground, crop, cell_px, src, radius, lit, title):
    sheet = Image.new('RGB', (1920, 1080), (12, 12, 14))
    pw, ph = 950, 520
    ox, oy = crop
    for i, v in enumerate((1, 2, 3, 4)):
        px = 7 + (i % 2) * (pw + 6)
        py = 34 + (i // 2) * (ph + 6)
        img = render(ground, ox, oy, pw, ph, cell_px, src, radius, v, lit)
        sheet.paste(img, (px, py))
        label(sheet, (px + 12, py + 10), f'#{v}   rim: {RIMS[v]}   surface: {SURFS[v]}')
    label(sheet, (10, 6), title, 16)
    return sheet


def advance_sheet(ground, crop, cell_px, src, radii, lit, title):
    sheet = Image.new('RGB', (1920, 1080), (12, 12, 14))
    pw, ph = 474, 254
    ox, oy = crop
    for row, v in enumerate((1, 2, 3, 4)):
        for col, r in enumerate(radii):
            px = 6 + col * (pw + 4)
            py = 32 + row * (ph + 8)
            sheet.paste(render(ground, ox, oy, pw, ph, cell_px, src, r, v, lit), (px, py))
            label(sheet, (px + 8, py + 6), f'#{v}  t{col + 1}', 14)
    label(sheet, (10, 6), title, 16)
    return sheet


def main():
    g17 = Image.open(sys.argv[1])
    g36 = Image.open(sys.argv[2])
    out = sys.argv[3]
    os.makedirs(out, exist_ok=True)
    c17 = 1920 / 113.0   # the frame's cell size: 1920 px over the focus width
    c36 = 1920 / 53.0
    # battle zoom: a patch over paving and the grass beds east of the plaza
    crop17, src17, r17 = (830, 330), (15.5, 14.0), 6.5
    crop36, src36, r36 = (960, 100), (11.5, 9.5), 6.2
    tag17 = f'{c17:.1f} px/cell'
    tag36 = f'{c36:.1f} px/cell'
    grid_sheet(g17, crop17, c17, src17, r17, True,
               f'Hard-edge creep studies, round 1 -- battle zoom {tag17} -- LIT (CPU model of the relief composite)').save(f'{out}/01-battle-zoom-lit.png')
    grid_sheet(g17, crop17, c17, src17, r17, False,
               f'Hard-edge creep studies, round 1 -- battle zoom {tag17} -- REVIEW-FRAME FALLBACK (flat, baked rim + drop shadow)').save(f'{out}/02-battle-zoom-fallback.png')
    grid_sheet(g36, crop36, c36, src36, r36, True,
               f'Hard-edge creep studies, round 1 -- close zoom {tag36} -- LIT').save(f'{out}/03-close-zoom-lit.png')
    grid_sheet(g36, crop36, c36, src36, r36, False,
               f'Hard-edge creep studies, round 1 -- close zoom {tag36} -- REVIEW-FRAME FALLBACK').save(f'{out}/04-close-zoom-fallback.png')
    adv_crop, adv_src = (830, 360), (13.0, 7.0)
    radii = (2.2, 3.2, 4.2, 5.2)
    advance_sheet(g17, adv_crop, c17, adv_src, radii, True,
                  f'Advancing patch, four steps, battle zoom {tag17} -- LIT').save(f'{out}/05-advance-lit.png')
    advance_sheet(g17, adv_crop, c17, adv_src, radii, False,
                  f'Advancing patch, four steps, battle zoom {tag17} -- FALLBACK').save(f'{out}/06-advance-fallback.png')


if __name__ == '__main__':
    main()
