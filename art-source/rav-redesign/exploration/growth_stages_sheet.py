"""Rav growth stages, look check: a whelp, an adult and an elder Runner at play zoom, on colony ground and on
hive creep, beside Marines (`rav-growth-stages.md`).

  python growth_stages_sheet.py <render root> <out dir>

No new art. Each stage is the in-game Runner rig (`render_runner.draw_runner` with the round-6 slim body the
game ships, `export_runner_rig.py`) scaled and recoloured:

  whelp  0.80x linear, plates lifted toward a pale lilac and a little desaturated, spike arms cut to nubs (0.55x);
  adult  1.00x, the shipped Runner as it is;
  elder  1.22x, plates darkened toward the Brute's old plum, healed scars across the carapace, arms 1.30x.

Each body is drawn at four times play zoom, recoloured, then resampled once to its size at 32 px per cell, with the
live unit shadow (`round2.shadow_blob`) scaled by the stage's body radius.

Writes 01-stages.png (1920x1080): the three stages and a pack, on colony paving and on creep, at 32 px per cell
1:1, and a 3x close-up row for the markings.

<render root> holds `rav-zoom32-ground`, `rav-zoom32-marines` (see round2.py) and `creep-play-zoom.png` (the
hive-creep GL play-zoom frame the Matriarch sheets use).
"""
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

import skeleton as sk
from boards import font
from motion_runner import key, marine_sprites
from render_runner import Art, BODY_ROWS, draw_runner, put_bone
from round2 import PPC32, cut_marines, frames, shadow_blob

HERE = Path(__file__).resolve().parent
SLIM_BODY = HERE / "rounds" / "round-6" / "layers" / "body-slim-b.png"

BG = (16, 20, 26)
INK = (226, 228, 232)
DIM = (150, 156, 166)
ACC = (240, 196, 120)
HI = 4  # draw at HI x play zoom, then resample once

# stage: linear scale, spike scale
STAGES = {
    "whelp": (0.80, 0.55),
    "adult": (1.00, 1.00),
    "elder": (1.22, 1.30),
}
RUNNER_RADIUS = 0.30


class SlimArt(Art):
    """The rig art with the slim body the game ships in place of round 5's."""

    def __init__(self):
        super().__init__()
        body = key(SLIM_BODY)
        n = BODY_ROWS["neck"]
        self.fore = body.crop((0, 0, 1024, n + 40))
        a = np.asarray(body.getchannel("A")).astype(float)
        ramp = np.clip((np.arange(body.height) - (n - 30)) / 40, 0, 1)[:, None]
        trunk = body.copy()
        trunk.putalpha(Image.fromarray((a * ramp).astype(np.uint8)))
        self.trunk = trunk


def poses():
    skel = sk.Skeleton()
    n = int(sk.T_END * sk.FPS)
    return [skel.frame(f / sk.FPS, 1 / sk.FPS) for f in range(n)]


def scaled_arms(s, k):
    out = []
    for pts in s["arms"]:
        r = pts[0]
        out.append([tuple(r[i] + (p[i] - r[i]) * k for i in range(3)) for p in pts])
    return out


def recolour(img, stage, part):
    a = np.asarray(img).astype(float)
    rgb, al = a[..., :3], a[..., 3:]
    if stage == "whelp":
        rgb = rgb + (255 - rgb) * (0.34 if part == "body" else 0.22)
        grey = rgb.mean(2, keepdims=True)
        rgb = grey + (rgb - grey) * 0.78
    elif stage == "elder":
        if part == "body":
            rgb = rgb * np.array([0.50, 0.40, 0.54])
        else:  # old bone: a touch yellowed, not darkened
            rgb = rgb * np.array([1.0, 0.96, 0.84])
    return Image.fromarray(np.dstack([np.clip(rgb, 0, 255), al]).astype(np.uint8), "RGBA")


def scars(img, seed):
    """Healed scars: pale raised seams across the carapace, kept inside the body."""
    rnd = np.random.default_rng(seed)
    a = np.asarray(img.getchannel("A")) > 200
    ys, xs = np.nonzero(a)
    cx, cy = xs.mean(), ys.mean()
    w = xs.max() - xs.min()
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    for _ in range(4):
        x0 = cx + rnd.uniform(-0.35, 0.25) * w
        y0 = cy + rnd.uniform(-0.25, 0.25) * (ys.max() - ys.min())
        ang = rnd.uniform(-0.9, 0.9) + math.pi / 2
        ln = rnd.uniform(0.10, 0.20) * w
        pts = [(x0 + math.cos(ang) * t * ln + rnd.uniform(-2, 2), y0 + math.sin(ang) * t * ln) for t in
               np.linspace(-0.5, 0.5, 6)]
        d.line(pts, fill=(196, 150, 176, 210), width=max(2, int(w * 0.011)))
    m = Image.fromarray((a * 255).astype(np.uint8))
    layer.putalpha(Image.fromarray((np.asarray(layer.getchannel("A")).astype(float) *
                                    np.asarray(m).astype(float) / 255).astype(np.uint8)))
    out = img.copy()
    out.alpha_composite(layer)
    return out


def body_sprite(art, s, stage, ppc, seed=0):
    """One Runner at its stage, facing right, at ppc px per cell; returns (sprite, centre offset)."""
    lin, spike = STAGES[stage]
    hi = ppc * lin * HI
    W, H = int(4.2 * hi), int(3.4 * hi)
    yc = (s["sh"][0] + s["hp"][0]) / 2

    def to_screen(p):
        return W / 2 + (p[1] - yc) * hi, H / 2 + p[0] * hi

    body = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    draw_runner(body, art, dict(s, arms=[]), hi, to_screen)
    arms = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    for i, pts in enumerate(scaled_arms(s, spike)):
        for j, piece in enumerate(art.arm):
            p0, p1 = to_screen(pts[j]), to_screen(pts[j + 1])
            wk = math.dist(pts[j], pts[j + 1]) * hi / piece[2]
            put_bone(arms, piece, p0, p1, wk * 1.1 * (1.15 if stage == "whelp" else 1.0), piece[2] * wk, i == 1)
    body = recolour(body, stage, "body")
    if stage == "elder":
        body = scars(body, seed)
    body.alpha_composite(recolour(arms, stage, "arm"))
    k = 1 / HI
    out = body.convert("RGBa").resize((max(1, round(W * k)), max(1, round(H * k))), Image.LANCZOS).convert("RGBA")
    return out


def place(tile, spr, cx, cy, ppc, radius, angle=0.0):
    sh, (ox, oy) = shadow_blob(ppc, radius)
    tile.alpha_composite(sh, (int(cx + ox - sh.width / 2), int(cy + oy - sh.height / 2)))
    if angle:
        spr = spr.rotate(angle, expand=True, resample=Image.BICUBIC)
    tile.alpha_composite(spr, (int(cx - spr.width / 2), int(cy - spr.height / 2)))


def label(d, xy, s, size=15, fill=INK, bold=True):
    d.text(xy, s, font=font(size, bold), fill=fill)


def build(root, out_dir):
    root = Path(root)
    out_dir = Path(out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    art = SlimArt()
    raw = poses()
    stand = raw[int(1.46 * sk.FPS)]
    gallop = [raw[int(t * sk.FPS)] for t in (0.24, 0.40, 0.56, 0.72, 0.88, 1.04)]

    g0 = Image.open(frames(root, "rav-zoom32-ground")[0]).convert("RGBA")
    creep = Image.open(root / "creep-play-zoom.png").convert("RGBA")
    grounds = [("COLONY PAVING", g0.crop((990, 700, 990 + 920, 700 + 250))),
               ("HIVE CREEP", creep.crop((10, 0, 10 + 920, 250)))]
    marines = cut_marines(root, "rav-zoom32-marines", PPC32)
    m = max(marines, key=lambda c: c.width * c.height)

    ppc = PPC32
    stand_spr = {st: body_sprite(art, stand, st, ppc, seed=3) for st in STAGES}

    img = Image.new("RGB", (1920, 1080), BG)
    d = ImageDraw.Draw(img)
    label(d, (24, 12), "RAV GROWTH STAGES  /  RUNNER  /  whelp - adult - elder at play zoom", 24)
    label(d, (24, 44), "The shipped Runner rig, scaled and recoloured; no new art.  Rows 1-2 are 32 px per cell 1:1, "
                       "row 3 is 3x for the markings.", 14, DIM, False)

    # row 1: Marine + whelp, adult, elder, standing
    for gi, (gname, gtile) in enumerate(grounds):
        x0, y0 = 20 + gi * 950, 96
        tile = gtile.copy()
        cy = 130
        place(tile, m, 80, cy, ppc, 0.25)
        xs = {"whelp": 250, "adult": 470, "elder": 720}
        for st, x in xs.items():
            place(tile, stand_spr[st], x, cy, ppc, RUNNER_RADIUS * STAGES[st][0])
        img.paste(tile.convert("RGB"), (x0, y0))
        label(d, (x0, y0 - 22), f"{gname}  -  32 px per cell, 1:1", 14, ACC)
        for name, x in (("Marine", 80), *[(f"{st} {STAGES[st][0]:.2f}x", v) for st, v in xs.items()]):
            label(d, (x0 + x - 40, y0 + 228), name, 13, INK)

    # row 2: a pack running in, an elder at its head, toward three Marines
    pack = [("elder", 640, 125, 0), ("adult", 520, 70, 4), ("adult", 520, 185, -5), ("whelp", 420, 40, 6),
            ("whelp", 420, 120, -3), ("whelp", 400, 205, 2), ("adult", 330, 100, 0), ("whelp", 300, 170, -6),
            ("whelp", 260, 60, 3)]
    for gi, (gname, gtile) in enumerate(grounds):
        x0, y0 = 20 + gi * 950, 400
        tile = gtile.copy()
        for i, (x, y) in enumerate(((840, 70), (870, 150), (835, 200))):
            place(tile, marines[i % len(marines)], x, y, ppc, 0.25)
        for i, (st, x, y, ang) in enumerate(pack):
            spr = body_sprite(art, gallop[i % len(gallop)], st, ppc, seed=11 + i)
            place(tile, spr, x, y, ppc, RUNNER_RADIUS * STAGES[st][0], ang)
        img.paste(tile.convert("RGB"), (x0, y0))
        label(d, (x0, y0 - 22), f"A PACK, ELDER LEADING  -  {gname.lower()}, 32 px per cell, 1:1", 14, ACC)

    # row 3: close-up, 3x
    zoom = 3
    for gi, (gname, gtile) in enumerate(grounds):
        x0, y0 = 20 + gi * 950, 700
        W, H = 920, 360
        src = gtile.crop((0, 0, W // zoom + 1, H // zoom + 1)).resize((W + zoom, H + zoom), Image.LANCZOS)
        tile = src.crop((0, 0, W, H))
        p = ppc * zoom
        spr = {st: body_sprite(art, stand, st, p, seed=3) for st in STAGES}
        m96 = max(marine_sprites(root), key=lambda c: c.width * c.height)
        mm = m96.convert("RGBa").resize((round(m96.width * zoom / 3), round(m96.height * zoom / 3)), Image.LANCZOS).convert("RGBA")
        place(tile, mm, 90, 170, p, 0.25)
        for st, x in (("whelp", 250), ("adult", 480), ("elder", 740)):
            place(tile, spr[st], x, 170, p, RUNNER_RADIUS * STAGES[st][0])
        img.paste(tile.convert("RGB"), (x0, y0))
        label(d, (x0, y0 - 22), f"CLOSE-UP 3x  -  {gname.lower()}  (markings only: not a play zoom)", 14, ACC)
        for name, x in (("Marine", 90), ("whelp: pale, nub spikes", 250), ("adult", 480),
                        ("elder: dark plum, scars, long spikes", 740)):
            label(d, (x0 + x - 60, y0 + H - 26), name, 13, INK)

    img.save(out_dir / "01-stages.png")
    return img


if __name__ == "__main__":
    build(sys.argv[1], sys.argv[2])
