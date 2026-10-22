"""Elder treatments for the Rav growth stages: what makes an elder read as older, beyond being bigger
(`rav-growth-stages.md`). One pick-one image per treatment, for the owner's decision page.

  python elder_options_sheet.py <render root> <out dir>

Every treatment is the shipped Runner rig at the elder's 1.22x scale, beside the same whelp and adult
(`growth_stages_sheet.py`), recoloured and reshaped in code with no new art:

  A  ridge   dark plum carapace, pale spine ridge, healed scars, spikes 1.30x (round 2's elder);
  B  heavy   darker, heavier carapace (body 12% broader), spikes 1.55x and thicker;
  C  worn    plates greyed and worn pale at their edges, a hunched carriage (shorter, broader from above);
  D  crowned dark plum with calcified ivory bosses grown over the shoulders and brow, as the Brute's bone
             ram-cap, spikes 1.30x.

Writes 0N-elder-<key>.png (960x540): the three stages on colony paving and on hive creep at 32 px per cell 1:1,
with the elder at 3x beside them, plus options.json for the decision page.
"""
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

import skeleton as sk
from boards import font
from growth_stages_sheet import (HI, RUNNER_RADIUS, STAGES, SlimArt, body_sprite, place, poses, recolour,
                                 scaled_arms, scars)
from render_runner import draw_runner, put_bone
from round2 import PPC32, cut_marines, frames

BG = (16, 20, 26)
INK = (226, 228, 232)
DIM = (150, 156, 166)
ACC = (240, 196, 120)

OPTIONS = [
    ("A", "ridge", "Pale spine ridge, scars",
     "Dark plum with the spine ridge kept pale and healed scars; spikes 1.3x. Round 2's elder."),
    ("B", "heavy", "Darker, heavier, longer spikes",
     "Near-black plum, a body 12% broader, spikes 1.55x and thicker. Reads as more dangerous more than older."),
    ("C", "worn", "Grey, worn, hunched",
     "Plates greyed and worn pale at the edges, carried hunched (shorter and broader from above). "
     "Reads as old, and less as a threat."),
    ("D", "crowned", "Bone crown",
     "Dark plum with ivory calcified bosses grown over the shoulders and brow, like the Brute's ram-cap. "
     "Large pale masses, which carry at 32 px."),
]


def lum(rgb):
    return 0.299 * rgb[..., 0] + 0.587 * rgb[..., 1] + 0.114 * rgb[..., 2]


def edge_band(alpha, px):
    """Pixels inside the body within px of a plate edge (a strong luminance step) or the outline."""
    a = Image.fromarray(alpha)
    inner = np.asarray(a.filter(ImageFilter.MinFilter(px * 2 + 1)))
    return (alpha > 200) & (inner < 200)


def elder(art, s, ppc, key, seed=3):
    if key == "ridge":
        return body_sprite(art, s, "elder", ppc, seed)
    lin = STAGES["elder"][0]
    spike = {"heavy": 1.55, "worn": 1.15, "crowned": 1.30}[key]
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
            put_bone(arms, piece, p0, p1, wk * 1.1 * (1.3 if key == "heavy" else 1.0), piece[2] * wk, i == 1)

    a = np.asarray(body).astype(float)
    rgb, al = a[..., :3], a[..., 3]
    if key == "heavy":
        rgb = rgb * np.array([0.40, 0.32, 0.46])
    elif key == "worn":
        grey = lum(rgb)[..., None]
        rgb = (grey + (rgb - grey) * 0.35) * 0.62 + np.array([18, 16, 20])
        band = edge_band(al.astype(np.uint8), max(2, int(hi * 0.035)))
        bright = lum(a[..., :3]) > 120
        rgb = np.where((band | (bright & (np.random.default_rng(seed).random(bright.shape) < 0.35)))[..., None],
                       rgb * 0.45 + np.array([170, 166, 172]) * 0.55, rgb)
    elif key == "crowned":
        rgb = rgb * np.array([0.50, 0.40, 0.54])
    body = Image.fromarray(np.dstack([np.clip(rgb, 0, 255), al]).astype(np.uint8), "RGBA")

    if key == "crowned":
        crust = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        d = ImageDraw.Draw(crust)
        rnd = np.random.default_rng(seed)
        sx = to_screen((0, s["sh"][0], 0))[0]
        hx = to_screen(s["head"])[0]
        for cx, cy, r in [(sx, H / 2 - 0.14 * hi, 0.15), (sx, H / 2 + 0.14 * hi, 0.15), (sx + 0.12 * hi, H / 2, 0.12),
                          (hx - 0.04 * hi, H / 2, 0.11), (sx - 0.24 * hi, H / 2, 0.09)]:
            for _ in range(5):
                rr = r * hi * rnd.uniform(0.45, 0.8)
                ox, oy = rnd.uniform(-0.5, 0.5) * r * hi, rnd.uniform(-0.5, 0.5) * r * hi
                tone = int(rnd.uniform(200, 236))
                d.ellipse([cx + ox - rr, cy + oy - rr, cx + ox + rr, cy + oy + rr],
                          fill=(tone, tone - 14, tone - 40, 255), outline=(120, 100, 90, 255), width=max(1, int(hi * 0.01)))
        m = np.asarray(body.getchannel("A")).astype(float) / 255
        ca = np.asarray(crust.getchannel("A")).astype(float) * m
        crust.putalpha(Image.fromarray(ca.astype(np.uint8)))
        body.alpha_composite(crust)
    if key in ("heavy", "crowned"):
        body = scars(body, seed)

    arms = recolour(arms, "elder", "arm")
    if key == "worn":
        b = np.asarray(arms).astype(float)
        g = lum(b[..., :3])[..., None]
        b[..., :3] = g + (b[..., :3] - g) * 0.35
        arms = Image.fromarray(b.astype(np.uint8), "RGBA")
    body.alpha_composite(arms)
    k = 1 / HI
    sx_, sy_ = {"heavy": (1.0, 1.12), "worn": (0.92, 1.06)}.get(key, (1.0, 1.0))
    out = body.convert("RGBa").resize((max(1, round(W * k * sx_)), max(1, round(H * k * sy_))),
                                      Image.LANCZOS).convert("RGBA")
    return out


def build(root, out_dir):
    root, out_dir = Path(root), Path(out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    art = SlimArt()
    stand = poses()[int(1.46 * sk.FPS)]
    g0 = Image.open(frames(root, "rav-zoom32-ground")[0]).convert("RGBA")
    creep = Image.open(root / "creep-play-zoom.png").convert("RGBA")
    grounds = [("colony paving", g0.crop((1000, 730, 1000 + 560, 730 + 200))),
               ("hive creep", creep.crop((10, 20, 10 + 560, 20 + 200)))]
    marine = max(cut_marines(root, "rav-zoom32-marines", PPC32), key=lambda c: c.width * c.height)
    whelp = body_sprite(art, stand, "whelp", PPC32, 3)
    adult = body_sprite(art, stand, "adult", PPC32, 3)
    entries = []
    for n, (letter, key, title, note) in enumerate(OPTIONS, 1):
        el = elder(art, stand, PPC32, key)
        el3 = elder(art, stand, PPC32 * 3, key)
        img = Image.new("RGB", (960, 540), BG)
        d = ImageDraw.Draw(img)
        d.text((16, 10), f"{letter}  {title.upper()}", font=font(22, True), fill=ACC)
        d.text((16, 40), "Whelp, adult and this elder beside a Marine: 32 px per cell 1:1, then the elder at 3x.",
               font=font(13), fill=DIM)
        for gi, (gname, gt) in enumerate(grounds):
            tile = gt.copy()
            place(tile, marine, 50, 100, PPC32, 0.25)
            for spr, x, st in ((whelp, 160, "whelp"), (adult, 300, "adult"), (el, 460, "elder")):
                place(tile, spr, x, 100, PPC32, RUNNER_RADIUS * STAGES[st][0])
            y0 = 70 + gi * 230
            img.paste(tile.convert("RGB"), (16, y0))
            d.text((22, y0 + 178), f"{gname}, 32 px/cell", font=font(13, True), fill=INK)
        z = creep.crop((40, 0, 40 + 120, 152)).resize((360, 456), Image.LANCZOS)
        place(z, el3, 180, 228, PPC32 * 3, RUNNER_RADIUS * STAGES["elder"][0])
        img.paste(z.convert("RGB"), (590, 70))
        d.text((596, 506), "elder on creep, 3x (markings only)", font=font(13, True), fill=INK)
        name = f"0{n}-elder-{key}.png"
        img.save(out_dir / name)
        e = np.asarray(el).astype(float)
        m = e[..., 3] > 200
        entries.append({"key": letter, "label": title, "file": name,
                        "note": f"{note} Mean body luminance at 32 px: L {round(lum(e)[m].mean())} (creep L 94)."})
    opts = [{"id": "rav-elder-treatment",
             "question": "What makes an elder rav read as older, beyond being bigger?",
             "options": entries}]
    (out_dir / "options.json").write_text(json.dumps(opts, indent=2), encoding="utf-8")


if __name__ == "__main__":
    build(sys.argv[1], sys.argv[2])
