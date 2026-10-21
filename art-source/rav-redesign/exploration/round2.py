"""Round 2 sheets: concepts judged at a closer, player-like zoom, and value
variants of the leading concept on several grounds.

  python round2.py <round dir> <render root> <out dir> <lead concept number> "<names...>"

<render root> holds three colony-breach close-up renders (regenerate with
commanderEvidence -Pmission=colony-breach -PmaxTicks=600 -PsnapshotEveryTicks=600
-PsnapshotWidth=1920 -PsnapshotHeight=1080 and):
  rav-zoom/            -PsnapshotFocus=199,158,112   17.1 px per cell
  rav-zoom32-marines/  -PsnapshotFocus=199,182,60    32 px per cell, Marines on paving
  rav-zoom32-ground/   -PsnapshotFocus=184,150,60    32 px per cell, dirt and grass

Bodies get the live unit shadow (UnitShadowRenderSystem: a soft ellipse 2.6
body radii wide at 0.55 alpha, lying down-sun of the body), which review frames
leave off by default; the Marines get the same.
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

from boards import font
from chitin_review import creep_tile
from motion import marine_cutouts
from round_sheet import RUNNER_AREA_PX, key_plate

BG = (16, 20, 26)
INK = (226, 228, 232)
DIM = (150, 156, 166)
ACC = (240, 196, 120)
PPC17 = 1920 / 112
PPC32 = 1920 / 60
CREAM = np.array([232, 216, 182])
WALNUT = np.array([44, 32, 23])
AMBER = (255, 168, 54)


def frames(root, name):
    d = Path(root) / name / "visuals" / "colony-breach" / "frames"
    fs = sorted(d.glob("frame-*.png"))
    return fs[0], fs[-1]


def cut_marines(root, name, ppc):
    """Marines standing by the west buildings in the 32 px close-up."""
    first, last = frames(root, name)
    cuts = marine_cutouts(str(last), region=(220, 390, 310, 650))
    return [c for c in cuts if c.width * c.height > (0.45 * ppc) ** 2] or cuts


def scaled(cut, ppc):
    a = np.asarray(cut.getchannel("A")) > 128
    k = (RUNNER_AREA_PX * (ppc / PPC17) ** 2 / max(1, a.sum())) ** 0.5
    size = (max(1, round(cut.width * k)), max(1, round(cut.height * k)))
    return cut.convert("RGBa").resize(size, Image.LANCZOS).convert("RGBA")


def shadow_blob(ppc, radius=0.3, strength=0.55, grow=1.0):
    w = 2.6 * radius * ppc * grow
    L = w * 1.3
    pad = int(L * 1.6)
    im = Image.new("L", (pad, pad), 0)
    d = ImageDraw.Draw(im)
    c = pad / 2
    d.ellipse([c - L / 2, c - w / 2, c + L / 2, c + w / 2], fill=int(255 * strength))
    im = im.rotate(-45, resample=Image.BICUBIC)  # long axis down-sun (sun upper left)
    im = im.filter(ImageFilter.GaussianBlur(max(1, 0.08 * ppc)))
    out = Image.new("RGBA", im.size, (6, 4, 2, 0))
    out.putalpha(im)
    off = 0.5 * L * 0.7071
    return out, (off, off)


def place(tile, body, cx, cy, ppc, radius=0.3, strength=0.55, grow=1.0):
    sh, (ox, oy) = shadow_blob(ppc, radius, strength, grow)
    tile.alpha_composite(sh, (int(cx + ox - sh.width / 2), int(cy + oy - sh.height / 2)))
    tile.alpha_composite(body, (int(cx - body.width / 2), int(cy - body.height / 2)))


# ------------------------------------------------------------ value variants
def recolour(cut, mode):
    a = np.asarray(cut).astype(float)
    rgb, al = a[..., :3], a[..., 3:]
    L = (0.2126 * rgb[..., 0] + 0.7152 * rgb[..., 1] + 0.0722 * rgb[..., 2]) / 255
    if mode == "inverse":  # bone-cream dominant body with dark banding
        t = np.clip(1 - L, 0, 1)[..., None] ** 1.2
        rgb = WALNUT + (CREAM - WALNUT) * t
    elif mode == "lighter":  # a larger cream share
        t = np.clip((L - 0.12) / 0.5, 0, 1)[..., None]
        rgb = rgb + (CREAM - rgb) * (0.55 * t)
    return Image.fromarray(np.dstack([np.clip(rgb, 0, 255), al]).astype(np.uint8), "RGBA")


def rim(body):
    a = body.getchannel("A").point(lambda v: 255 if v > 100 else 0)
    grown = a.filter(ImageFilter.MaxFilter(3))
    ring = Image.fromarray(((np.asarray(grown) > 0) & (np.asarray(a) == 0)).astype(np.uint8) * 230)
    out = Image.new("RGBA", body.size, tuple(CREAM) + (0,))
    out.putalpha(ring)
    out.alpha_composite(body)
    return out


def eyes(body, ppc):
    """Amber eye shine, placed at the head end (the body faces +x after rotation)."""
    out = body.copy()
    g = Image.new("RGBA", body.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(g)
    a = np.asarray(body.getchannel("A")) > 128
    xs = np.where(a.any(0))[0]
    ys = np.where(a.any(1))[0]
    hx = xs.max() - (xs.max() - xs.min()) * 0.2
    cy = (ys.min() + ys.max()) / 2
    r = max(1.2, 0.05 * ppc)
    for s in (-1, 1):
        y = cy + s * (ys.max() - ys.min()) * 0.09
        d.ellipse([hx - r, y - r, hx + r, y + r], fill=AMBER + (255,))
    glow = g.filter(ImageFilter.GaussianBlur(r * 1.2))
    out.alpha_composite(glow)
    out.alpha_composite(g)
    return out


VARIANTS = [
    ("as generated", lambda c, p: c, 0.55, 1.0),
    ("lighter cream share", lambda c, p: recolour(c, "lighter"), 0.55, 1.0),
    ("inverse: bone body", lambda c, p: recolour(c, "inverse"), 0.55, 1.0),
    ("cream rim", lambda c, p: rim(c), 0.55, 1.0),
    ("stronger shadow", lambda c, p: c, 0.85, 1.25),
    ("amber eye shine", lambda c, p: eyes(c, p), 0.55, 1.0),
    ("rim + shadow + eyes", lambda c, p: eyes(rim(c), p), 0.85, 1.25),
]


def grounds(root):
    m0, _ = frames(root, "rav-zoom32-marines")
    g0, _ = frames(root, "rav-zoom32-ground")
    m0 = Image.open(m0).convert("RGBA")
    g0 = Image.open(g0).convert("RGBA")
    return m0, g0


def pick_box(img, test, size):
    a = np.asarray(img.convert("RGB")).astype(int)
    m = test(a)
    w, h = size
    best = None
    for y in range(40, a.shape[0] - h, 16):
        for x in range(0, a.shape[1] - w, 16):
            f = m[y:y + h, x:x + w].mean()
            if best is None or f > best[0]:
                best = (f, x, y)
    return img.crop((best[1], best[2], best[1] + w, best[2] + h)), best[0]


def is_grass(a):
    return (a[..., 1] > a[..., 0] + 5) & (a[..., 1] > a[..., 2] + 10)


def is_dirt(a):
    L = a.mean(2)
    sat = a.max(2) - a.min(2)
    return (sat < 40) & (L > 120) & (L < 200)


def is_paving(a):
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    return (r > g + 8) & (g > b + 4) & (r > 90) & (r < 175)


def ground_tiles(root, size):
    """Hand-picked from the two 32 px close-ups (tick 0, so no units on them)."""
    m0, g0 = grounds(root)
    w, h = size
    pav = g0.crop((1240, 720, 1240 + w, 720 + h))
    strip = m0.crop((1318, 700, 1530, 700 + h))
    grass = Image.new("RGBA", (w, h))
    for x in range(0, w, strip.width):
        grass.paste(strip, (x, 0))
    field = g0.crop((480, 860, 480 + w, 860 + h))
    creep = pav.copy()
    creep.alpha_composite(creep_tile(size, seed=9))
    return [("paving", pav), ("grass", grass), ("field", field), ("creep stand-in", creep)]


def concept_sheet(round_dir, root, out, names, title, nums=None, rim_row=False):
    rd = Path(round_dir)
    raws = sorted((p for p in (rd / "raw").glob("c*.png") if p.stem[1:].isdigit()), key=lambda p: int(p.stem[1:]))
    if nums:
        raws = [p for p in raws if int(p.stem[1:]) in nums]
    m32 = cut_marines(root, "rav-zoom32-marines", PPC32)
    m17 = marine_cutouts(str(frames(root, "rav-zoom")[1]))
    pav32 = ground_tiles(root, (300, 200))[0][1]
    f17 = Image.open(frames(root, "rav-zoom")[0]).convert("RGBA")
    img = Image.new("RGB", (1920, 1080), BG)
    d = ImageDraw.Draw(img)
    d.text((24, 10), title, font=font(24, True), fill=INK)
    d.text((24, 40), "plate | 32 px per cell on paving beside a Marine, with live unit shadows (zoom about 4.7x the opening "
                     "view) | 17.1 px per cell 1:1 and x2 below it", font=font(14), fill=DIM)
    cw, ch = 628, 500
    for i, p in enumerate(raws):
        col, row = i % 3, i // 3
        x0, y0 = 12 + col * (cw + 6), 66 + row * (ch + 6)
        d.rectangle([x0, y0, x0 + cw, y0 + ch], fill=(24, 28, 36))
        num = int(p.stem[1:])
        d.text((x0 + 10, y0 + 6), f"#{num}", font=font(26, True), fill=ACC)
        if num - 1 < len(names):
            d.text((x0 + 62, y0 + 12), names[num - 1], font=font(17, True), fill=INK)
        cut = key_plate(p)
        k = min(300 / cut.width, 440 / cut.height)
        pl = cut.convert("RGBa").resize((int(cut.width * k), int(cut.height * k)), Image.LANCZOS).convert("RGBA")
        pb = Image.new("RGBA", (300, 448), (180, 180, 180, 255))
        pb.alpha_composite(pl, ((300 - pl.width) // 2, (448 - pl.height) // 2))
        img.paste(pb.convert("RGB"), (x0 + 10, y0 + 44))
        rot = cut.rotate(-90, expand=True, resample=Image.BICUBIC)
        t32 = pav32.copy()
        place(t32, scaled(rot, PPC32), 105, 100, PPC32)
        m = m32[i % len(m32)]
        place(t32, m, 230, 100, PPC32)
        img.paste(t32.convert("RGB"), (x0 + 318, y0 + 44))
        t17 = f17.crop((720 + i * 30, 990, 720 + i * 30 + 140, 1060)).copy()
        place(t17, scaled(rot, PPC17), 45, 35, PPC17)
        mm = m17[i % len(m17)]
        place(t17, mm, 105, 35, PPC17)
        img.paste(t17.convert("RGB"), (x0 + 318, y0 + 256))
        img.paste(t17.resize((280, 140), Image.NEAREST).convert("RGB"), (x0 + 318, y0 + 334))
        d.text((x0 + 466, y0 + 262), "17.1 px/cell 1:1", font=font(13), fill=DIM)
        d.text((x0 + 466, y0 + 282), "and x2 below", font=font(13), fill=DIM)
        d.text((x0 + 318, y0 + 478), "32 px/cell above", font=font(13), fill=DIM)
    if rim_row:
        tiles = ground_tiles(root, (300, 205))
        for i, p in enumerate(raws[:3]):
            x0, y0 = 12 + i * (cw + 6), 66 + ch + 6
            d.rectangle([x0, y0, x0 + cw, y0 + ch], fill=(24, 28, 36))
            d.text((x0 + 10, y0 + 6), f"#{int(p.stem[1:])} with cream rim, full shadow and eye shine, 32 px/cell",
                   font=font(16, True), fill=ACC)
            rot = key_plate(p).rotate(-90, expand=True, resample=Image.BICUBIC)
            body = eyes(rim(scaled(rot, PPC32)), PPC32)
            for j, (gname, g) in enumerate(tiles):
                t = g.copy()
                place(t, m32[j % len(m32)], 230, 100, PPC32)
                place(t, body, 100, 100, PPC32, strength=0.85, grow=1.25)
                tx, ty = x0 + 10 + (j % 2) * 308, y0 + 34 + (j // 2) * 230
                img.paste(t.convert("RGB"), (tx, ty))
                d.text((tx + 4, ty + 207), gname, font=font(13), fill=DIM)
    img.save(out)


def value_sheet(round_dir, root, out, lead, title):
    cut = key_plate(Path(round_dir) / "raw" / f"c{lead}.png").rotate(-90, expand=True, resample=Image.BICUBIC)
    m32 = cut_marines(root, "rav-zoom32-marines", PPC32)
    tiles = ground_tiles(root, (400, 130))
    img = Image.new("RGB", (1920, 1080), BG)
    d = ImageDraw.Draw(img)
    d.text((24, 10), title, font=font(24, True), fill=INK)
    d.text((24, 40), "32 px per cell, live unit shadows, beside a Marine. Rows: value treatments of the same concept. "
                     "Columns: grounds.", font=font(14), fill=DIM)
    for j, (gname, _) in enumerate(tiles):
        d.text((250 + j * 412, 62), gname, font=font(17, True), fill=ACC)
    y = 88
    base = scaled(cut, PPC32)
    results = {}
    for label, fn, strength, grow in VARIANTS:
        body = fn(base, PPC32)
        d.text((24, y + 52), label, font=font(17, True), fill=INK)
        for j, (gname, g) in enumerate(tiles):
            t = g.copy()
            m = m32[j % len(m32)]
            place(t, m, 300, 65, PPC32)
            before = np.asarray(t.convert("RGB")).astype(float)
            place(t, body, 150, 65, PPC32, strength=strength, grow=grow)
            after = np.asarray(t.convert("RGB")).astype(float)
            mask = np.zeros(before.shape[:2], bool)
            ba = np.asarray(body.getchannel("A")) > 200
            ox, oy = int(150 - body.width / 2), int(65 - body.height / 2)
            mask[oy:oy + ba.shape[0], ox:ox + ba.shape[1]] = ba
            lum = lambda a: 0.2126 * a[..., 0] + 0.7152 * a[..., 1] + 0.0722 * a[..., 2]
            c = float(np.abs(lum(after) - lum(before))[mask].mean())
            results[(label, gname)] = c
            img.paste(t.convert("RGB"), (250 + j * 412, y))
            d.rectangle([250 + j * 412, y + 112, 250 + j * 412 + 92, y + 130], fill=BG)
            d.text((254 + j * 412, y + 113), f"contrast {c:.0f}", font=font(13), fill=INK)
        y += 140
    img.save(out)
    return results


if __name__ == "__main__":
    rd, root, out_dir, lead = sys.argv[1], sys.argv[2], Path(sys.argv[3]), int(sys.argv[4])
    names = sys.argv[5:]
    concept_sheet(rd, root, out_dir / "01-runner-concepts.png", names,
                  "RAV BLOOM RUNNER  /  ROUND 2  /  #8 x #1, more dog, judged closer")
    r = value_sheet(rd, root, out_dir / "02-value-variants.png", lead,
                    f"ROUND 2  /  can browns, creams and chitin be seen?  value treatments of #{lead}")
    for (label, g), c in r.items():
        print(f"{label:30s} {g:15s} {c:5.1f}")
