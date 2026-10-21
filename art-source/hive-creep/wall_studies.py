"""Hard-edge hive creep against walls (round 2): variant #4 of
`hard_edge_studies.py` (beaded-blister rim, chitin-plate surface) meeting one
real building, in four numbered wall treatments.

  W1 banked     -- the creep stops at the wall foot and heaps up against it
  W2 crusted    -- a crust climbs over the wall top from the face it touches,
                   with its own beaded edge
  W3 held off   -- the creep stops a hand's width short, its lip rolled up
                   along a dark gap, as if the wall repels it
  W4 tendrils   -- a narrow crust plus raised veins running on over the wall top

The building's wall is measured off the review frame (a one-cell band around a
box) rather than read from the simulation; walls stand 3 m, so in the lit
rendering they cast the composite's long shadows onto creep and ground.

Usage: python wall_studies.py <ground-z17.png> <ground-z36.png> <out-dir>
"""
import math
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import hard_edge_studies as he  # noqa: E402

WALL_M = 3.0
CENTRE = (960.0, 540.0)
C36 = 1920 / 53.0
C17 = 1920 / 113.0
# the green-walled building in the paved courtyard, in 36.2 px/cell frame pixels
OUTER36 = (708, 521, 924, 880)

NAMES = {1: 'banked against the wall', 2: 'crust climbs the wall top', 3: 'held off a hand short',
         4: 'narrow crust + veins over the wall'}


def to17(x, y):
    k = C17 / C36
    return CENTRE[0] + (x - CENTRE[0]) * k, CENTRE[1] + (y - CENTRE[1]) * k


def box_sdf(cx, cy, box):
    """Signed distance (cells) to an axis-aligned box, negative inside."""
    x0, y0, x1, y1 = box
    hx = (x1 - x0) / 2
    hy = (y1 - y0) / 2
    qx = np.abs(cx - (x0 + hx)) - hx
    qy = np.abs(cy - (y0 + hy)) - hy
    out = np.sqrt(np.maximum(qx, 0) ** 2 + np.maximum(qy, 0) ** 2)
    return out + np.minimum(np.maximum(qx, qy), 0)


def render(ground, ox, oy, w, h, cell_px, src, radius, outer_px, treatment, lit):
    crop = np.asarray(ground.crop((ox, oy, ox + w, oy + h)).convert('RGB'), float) / 255
    ys, xs = np.mgrid[0:h, 0:w].astype(float)
    cx = (xs + 0.5) / cell_px
    cy = (ys + 0.5) / cell_px
    box = tuple((v - o) / cell_px for v, o in zip(outer_px, (ox, oy, ox, oy)))
    sd = box_sdf(cx, cy, box)                 # > 0 outside the building
    wall = (sd <= 0) & (sd > -1.0)            # the one-cell outer wall band
    depth = -sd                               # how far onto the wall top
    plan, _ = he.plan_field(cx, cy, src, radius)

    gap = 0.0
    if treatment == 3:
        gap = 0.14 + 0.06 * he.fbm(cx * 1.3, cy * 1.3, 5, 2)
    # the creep's own edge distance; held off, the wall is an edge as well
    d = plan if treatment != 3 else np.minimum(plan, sd - gap)
    on_ground = (d > 0) & (sd > gap)
    cover = np.clip(d * cell_px + 0.5, 0, 1) * (sd > 0)
    dd = np.clip(d, 0, 2.0)

    hgt = np.where(on_ground, he.rim_height(4, dd, cx, cy), 0.0)
    micro, alb, spec_k = he.surface(4, dd, cx, cy, src)
    hgt = hgt + np.where(on_ground, micro * he.smooth(0.0, 0.12, dd), 0)
    if treatment == 1:   # heaped bank at the wall foot, blistered like the rim
        f1, _, hh = he.worley(cx * 2.6, cy * 2.6, 6)
        bead = he.smooth(0.62, 0.05, f1) * (0.7 + 0.6 * hh)
        bank = he.bump(np.maximum(sd, 0), 0.0, 0.45) * (0.16 + 0.18 * bead) * he.smooth(0.0, 0.4, dd)
        hgt = hgt + np.where(on_ground, bank, 0)

    # crust on the wall top: only where the creep reaches the wall's foot
    crust_cover = np.zeros_like(hgt)
    crust_h = np.zeros_like(hgt)
    if treatment in (2, 4):
        reach = 0.55 + 0.3 * he.fbm(cx * 1.1, cy * 1.1, 8, 2) if treatment == 2 else 0.22 + 0.1 * he.fbm(cx * 1.6, cy * 1.6, 8, 2)
        dc = np.minimum(reach - depth, plan - 0.35)     # crust edge distance
        crust_cover = np.clip(dc * cell_px + 0.5, 0, 1) * wall
        dcc = np.clip(dc, 0, 2.0)
        crust_h = WALL_M + he.rim_height(4, dcc, cx, cy) * 0.6
    veins = np.zeros_like(hgt)
    if treatment == 4:
        rg = 1 - np.abs(2 * he.fbm(cx * 1.2 + 3, cy * 1.2 + 8, 9, 3) - 1)
        veins = he.smooth(0.90, 0.97, rg) * wall * (plan > 0.2) * he.smooth(1.6, 0.4, depth - 0.0)
        veins = np.maximum(veins, 0)

    # height field for shading: walls stand 3 m, the building inside its roof line too
    full_h = np.where(wall, WALL_M, np.where(sd <= 0, 0.3, hgt))   # wall band 3 m, floor inside 0.3 m
    full_h = np.where(crust_cover > 0.5, crust_h, full_h)
    full_h = full_h + veins * 0.05

    m_per_px = 1.0 / cell_px
    crest = np.clip((hgt - he.PLATEAU) / 0.15, 0, 1)
    alb = alb + (he.CREAM - alb) * (0.35 * crest)[..., None]
    crust_alb = alb * 0.92
    if lit:
        gy, gx = np.gradient(full_h, m_per_px)
        # the wall's sheer sides are not seen from above: flatten their slope
        steep = np.abs(gx) + np.abs(gy) > 4.0
        gx = np.where(steep, 0, gx)
        gy = np.where(steep, 0, gy)
        n = np.dstack([-gx, -gy, np.ones_like(full_h)])
        n /= np.linalg.norm(n, axis=2, keepdims=True)
        ndl = np.clip((n * he.SUN).sum(2), 0, 1)
        diffuse = 0.42 + 0.58 * ndl / he.SUN[2]
        half = he.SUN + np.array([0, 0, 1.0])
        half /= np.linalg.norm(half)
        spec = spec_k * np.clip((n * half).sum(2), 0, 1) ** 28
        rise = math.tan(he.SUN_EL) * m_per_px
        occl = np.zeros_like(full_h)
        steps = int((WALL_M + 0.5) / math.tan(he.SUN_EL) * cell_px) + 2
        for s in range(1, steps):
            sx = int(round(he.SUN_XY[0] * s))
            sy = int(round(he.SUN_XY[1] * s))
            sh = np.roll(np.roll(full_h, -sy, 0), -sx, 1)
            occl = np.maximum(occl, np.clip((sh - (full_h + s * rise)) / 0.02, 0, 1))
        shade = 1 - 0.5 * occl
        creep_col = (alb * diffuse[..., None] + spec[..., None]) * shade[..., None]
        crust_col = (crust_alb * diffuse[..., None] + spec[..., None]) * shade[..., None]
        ground_col = crop * shade[..., None]
        wall_col = crop * shade[..., None]
    else:
        gy, gx = np.gradient(np.where(sd <= 0, 0, hgt), m_per_px)
        emb = np.clip(1 + 0.45 * (-(gx * he.SUN_XY[0] + gy * he.SUN_XY[1])), 0.7, 1.3)
        pgy, pgx = np.gradient(d)
        pg = np.sqrt(pgx ** 2 + pgy ** 2) + 1e-6
        facing = np.clip(-(pgx * he.SUN_XY[0] + pgy * he.SUN_XY[1]) / pg, -1, 1)
        lip = he.bump(dd, 0.12, 0.12)
        rim_light = 1 + lip * (0.8 * np.clip(-facing, 0, 1) - 0.35 * np.clip(facing, 0, 1))
        rim_light = rim_light * (1 - 0.45 * he.smooth(0.07, 0.0, dd))
        creep_col = alb * (0.92 * emb * rim_light)[..., None]
        cgy, cgx = np.gradient(crust_h * (crust_cover > 0), m_per_px)
        cemb = np.clip(1 + 0.45 * (-(cgx * he.SUN_XY[0] + cgy * he.SUN_XY[1])), 0.7, 1.3)
        dcl = np.clip(np.where(crust_cover > 0, (crust_cover), 0), 0, 1)
        crust_col = crust_alb * (0.9 * cemb)[..., None]
        off = int(round(0.16 / math.tan(he.SUN_EL) * cell_px * 0.7))
        sx = int(round(-he.SUN_XY[0] * off))
        sy = int(round(-he.SUN_XY[1] * off))
        drop = np.roll(np.roll(np.maximum(cover, crust_cover), sy, 0), sx, 1)
        ground_col = crop * (1 - 0.6 * drop)[..., None]
        wall_col = crop * (1 - 0.45 * drop)[..., None]
        del dcl

    out = np.where((sd <= 0)[..., None], wall_col, ground_col)
    out = out * (1 - cover[..., None]) + np.clip(creep_col, 0, 1) * cover[..., None]
    out = out * (1 - crust_cover[..., None]) + np.clip(crust_col, 0, 1) * crust_cover[..., None]
    if treatment == 4:
        vcol = (he.VEIN * 0.5 + he.BASE * 0.5)[None, None]
        out = out * (1 - veins[..., None] * 0.85) + vcol * veins[..., None] * 0.85
    if treatment == 3:   # the gap reads dark: a shadowed seam at the wall foot
        seam = (sd > 0) & (sd < gap) & (plan > 0)
        out = np.where(seam[..., None], out * 0.55, out)
    return Image.fromarray((np.clip(out, 0, 1) * 255).astype(np.uint8))


def grid(ground, crop, cell_px, src, radius, outer, lit, title):
    sheet = Image.new('RGB', (1920, 1080), (12, 12, 14))
    pw, ph = 950, 520
    for i, t in enumerate((1, 2, 3, 4)):
        px = 7 + (i % 2) * (pw + 6)
        py = 34 + (i // 2) * (ph + 6)
        sheet.paste(render(ground, crop[0], crop[1], pw, ph, cell_px, src, radius, outer, t, lit), (px, py))
        he.label(sheet, (px + 12, py + 10), f'W{t}   {NAMES[t]}')
    he.label(sheet, (10, 6), title, 16)
    return sheet


def advance(ground, crop, cell_px, src, radii, outer, treatment, lit, title):
    sheet = Image.new('RGB', (1920, 1080), (12, 12, 14))
    pw, ph = 950, 520
    for i, r in enumerate(radii):
        px = 7 + (i % 2) * (pw + 6)
        py = 34 + (i // 2) * (ph + 6)
        sheet.paste(render(ground, crop[0], crop[1], pw, ph, cell_px, src, r, outer, treatment, lit), (px, py))
        he.label(sheet, (px + 12, py + 10), f'W{treatment}  t{i + 1}')
    he.label(sheet, (10, 6), title, 16)
    return sheet


def main():
    g17 = Image.open(sys.argv[1])
    g36 = Image.open(sys.argv[2])
    out = sys.argv[3]
    os.makedirs(out, exist_ok=True)
    crop36, src36, r = (330, 440), (7.4, 8.0), 5.0
    world = (crop36[0] + src36[0] * C36, crop36[1] + src36[1] * C36)
    w17 = to17(*world)
    crop17 = (600, 330)
    src17 = ((w17[0] - crop17[0]) / C17, (w17[1] - crop17[1]) / C17)
    o0 = to17(OUTER36[0], OUTER36[1])
    o1 = to17(OUTER36[2], OUTER36[3])
    outer17 = (o0[0], o0[1], o1[0], o1[1])
    tag = 'creep #4 (beaded blisters, chitin plates) meeting a wall'
    grid(g36, crop36, C36, src36, r, OUTER36, True, f'Round 2 -- {tag} -- close zoom {C36:.1f} px/cell -- LIT').save(f'{out}/01-walls-close-lit.png')
    grid(g36, crop36, C36, src36, r, OUTER36, False, f'Round 2 -- {tag} -- close zoom -- REVIEW-FRAME FALLBACK').save(f'{out}/02-walls-close-fallback.png')
    grid(g17, crop17, C17, src17, r, outer17, True, f'Round 2 -- {tag} -- battle zoom {C17:.1f} px/cell -- LIT').save(f'{out}/03-walls-battle-lit.png')
    grid(g17, crop17, C17, src17, r, outer17, False, f'Round 2 -- {tag} -- battle zoom -- REVIEW-FRAME FALLBACK').save(f'{out}/04-walls-battle-fallback.png')
    advance(g36, crop36, C36, src36, (2.4, 3.3, 4.2, 5.2), OUTER36, 2, True,
            f'Round 2 -- W2 crust reaching the wall in four steps -- close zoom -- LIT').save(f'{out}/05-walls-advance-w2-lit.png')


if __name__ == '__main__':
    main()
