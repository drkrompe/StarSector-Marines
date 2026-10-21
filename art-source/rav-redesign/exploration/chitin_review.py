"""Review stills for the chitin pass, 1920x1080 at 100% scale.

  python chitin_review.py <old strain sheet.png> <colony frame-0004.png> <out dir>

01 palette and value structure, 02/03 boards for D1/D2, 06 battle zoom on
paving, grass and creep beside Marines (with a no-rim control row), and the
contrast each body makes against the ground it covers.
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

import chitin  # noqa: F401  (registers D1, D2)
from boards import BRIEFS, alpha_from_bg, board, font, old_pack, para
from chitin import CHITIN, chitinize
from creatures import B_brute, B_runner, B_spitter, DIRECTIONS, PAL, st
from motion import CELL, ground, marine_cutouts
from rig import render

BG = (16, 20, 26)
INK = (226, 228, 232)
DIM = (150, 156, 166)
ACC = (240, 196, 120)

GROUNDS = [("paving", (700, 960, 900, 1080)), ("grass", (1072, 206, 1272, 326)), ("creep", (700, 960, 900, 1080))]

BRIEFS["D1"] = dict(
    tag="B's silhouettes armoured in lacquered chitin: dark plates, cream sutures and rims, one small amber node.",
    strains=[("Runner", "a streak of ring segments on jointed paddle legs; two segmented whip-arms curled along its sides that coil and lash a full cell."),
             ("Brute", "a wide plated disc studded with cream calcified bosses (its plate; they chip off as it is spent), a segmented club ram."),
             ("Spitter", "a round armoured sac with cream meridian sutures over a siphon; the sac swells and squeezes."),
             ("Matriarch", "a plated disc ringed with amber nodes around a maw.")],
    palette=None,
    good="Keeps the three most distinct shapes we have and the most alive motion: a near-black streak with a cream head plate, a disc studded with cream bosses, a sutured ball. Big cream masses, not thin rims, carry it at 10 px.",
    risk="The Spitter is the weakest read: at zoom its sutured sac can look like a pale dish. The segmented whips need the most sprites.",
    motion="Five-segment whips with a 26 ms lag per joint; body bunches then stretches; paddle legs row in a wave.")
BRIEFS["D2"] = dict(
    tag="C's articulated limbs in the same chitin: hard jointed levers instead of whips.",
    strains=[("Runner", "a three-lobed husk on a tripod; a lance folded flat on its back unfolds through three cream joints into a bone spike."),
             ("Brute", "a seed-ram ovoid with a ridged cream ram-cap and two telescoping hammer pistons."),
             ("Spitter", "a hex husk with a cream-rimmed launch tube and a small amber node."),
             ("Matriarch", "a hexagonal plated core with six amber seed bays.")],
    palette=None,
    good="The most mechanical, most legible strike: one long lever snapping straight. Reads as engineered.",
    risk="Compact bodies: at 10 px the Runner is a small knot until the lance is out, and Runner and Spitter are close in size and shape. Its Brute (dark ovoid, cream ram-cap) is the strongest single read of either set.",
    motion="A three-joint switchblade unfold, a ticking tripod gait.")

PALETTE_NAMES = ["plate", "plate2", "deep", "cream", "bone", "leg", "amber", "creep", "creep2"]


def flat(builder, key):
    """Control: the chitin palette with no cream rims, no sheen."""
    P = PAL[key]

    def build(S):
        parts = builder(S, P)
        return parts

    return build


CONTROL = dict(name="D1 no rims", runner=flat(B_runner, "D1"), brute=flat(B_brute, "D1"), spitter=flat(B_spitter, "D1"))


def creep_tile(size, seed=5):
    rng = np.random.default_rng(seed)
    w, h = size
    m = Image.new("L", size, 0)
    d = ImageDraw.Draw(m)
    for _ in range(14):
        cx, cy = rng.uniform(0, w), rng.uniform(0, h)
        r = rng.uniform(0.2, 0.45) * h
        d.ellipse([cx - r * 1.5, cy - r, cx + r * 1.5, cy + r], fill=255)
    m = m.filter(ImageFilter.GaussianBlur(4)).point(lambda v: 235 if v > 100 else int(v * 2.2))
    col = Image.new("RGBA", size, CHITIN["creep"] + (0,))
    col.putalpha(m)
    ridges = Image.new("RGBA", size, (0, 0, 0, 0))
    rd = ImageDraw.Draw(ridges)
    for _ in range(60):
        x, y = rng.uniform(0, w), rng.uniform(0, h)
        a = rng.uniform(0, 6.28)
        pts = [(x, y)]
        for _ in range(5):
            a += rng.uniform(-0.7, 0.7)
            x, y = x + 6 * np.cos(a), y + 6 * np.sin(a)
            pts.append((x, y))
        rd.line(pts, fill=CHITIN["creep2"] + (170,), width=1)
    for _ in range(6):
        x, y = rng.uniform(0, w), rng.uniform(0, h)
        rd.ellipse([x - 1, y - 1, x + 1, y + 1], fill=CHITIN["creep_node"] + (200,))
    ra = np.asarray(ridges).copy()
    ra[..., 3] = (ra[..., 3].astype(int) * np.asarray(m).astype(int) // 255).astype(np.uint8)
    col.alpha_composite(Image.fromarray(ra))
    return col


def lum(a):
    a = a.astype(float)
    return 0.2126 * a[..., 0] + 0.7152 * a[..., 1] + 0.0722 * a[..., 2]


def tile_for(frame, gname, box, D, marines, olds=None):
    g = ground(frame, box).copy()
    if gname == "creep":
        g.alpha_composite(creep_tile(g.size))
    base = np.asarray(g.convert("RGB")).copy()
    for k, (mx, my) in enumerate(((168, 42), (176, 86))):
        m = marines[k % len(marines)]
        g.alpha_composite(m, (mx - m.width // 2, my - m.height // 2))
    spots = [("runner", (130, 44), 0.47), ("runner", (104, 88), None), ("brute", (60, 42), None),
             ("spitter", (38, 94), None)]
    masks = []
    for kind, (x, y), s in spots:
        if olds is not None:
            im = olds[kind].rotate(90, expand=True)
        else:
            im = render(D[kind](st(s=s, w=0.3, move=0.0 if s else 1.0, tau=0.3 + x)), CELL, (5, 5), (0.5, 0.5),
                        yaw=90, ss=6, shadow=True)
        ox, oy = int(x - im.width / 2), int(y - im.height / 2)
        g.alpha_composite(im, (ox, oy))
        a = np.zeros(base.shape[:2], bool)
        am = np.asarray(im.getchannel("A")) > 200
        y0, x0 = max(0, oy), max(0, ox)
        y1, x1 = min(base.shape[0], oy + am.shape[0]), min(base.shape[1], ox + am.shape[1])
        a[y0:y1, x0:x1] = am[y0 - oy:y1 - oy, x0 - ox:x1 - ox]
        masks.append(a)
    comp = np.asarray(g.convert("RGB"))
    body = np.any(masks, axis=0)
    contrast = float(np.abs(lum(comp) - lum(base))[body].mean()) if body.any() else 0.0
    return g, contrast


def zoom_rows(sheet, frame, out):
    img = Image.new("RGB", (1920, 1080), BG)
    d = ImageDraw.Draw(img)
    d.text((40, 14), "BATTLE ZOOM  /  17.1 px per cell, 100% scale, beside Marines (right): paving, grass, proposed creep",
           font=font(22, True), fill=INK)
    marines = marine_cutouts(frame)
    rows = [("shipped", None, old_pack(sheet)), ("D1 CHITIN TIDE", DIRECTIONS["D1"], None),
            ("D2 CHITIN LANCE", DIRECTIONS["D2"], None), ("control: D1 with no rims or sheen", CONTROL, None)]
    y = 54
    table = {}
    for label, D, olds in rows:
        x1 = 40
        xi = 700
        for gname, box in GROUNDS:
            t, c = tile_for(frame, gname, box, D, marines, olds)
            table[(label, gname)] = c
            img.paste(t.convert("RGB"), (x1, y + 24))
            big = t.resize((t.width * 2, t.height * 2), Image.NEAREST)
            img.paste(big.convert("RGB"), (xi, y))
            d.rectangle([xi, y + 220, xi + 190, y + 240], fill=BG)
            d.text((xi + 4, y + 222), f"x2 {gname}  ·  contrast {c:.0f}", font=font(14), fill=INK)
            x1 += 206
            xi += 406
        d.text((40, y + 150), label, font=font(19, True), fill=ACC)
        d.text((40, y + 176), "1:1 tiles left, x2 nearest right", font=font(14), fill=DIM)
        y += 246
    img.save(out)
    return table


def palette_sheet(frame, out):
    img = Image.new("RGB", (1920, 1080), BG)
    d = ImageDraw.Draw(img)
    d.text((40, 20), "CHITIN PALETTE  /  readability from value structure, not hue", font=font(30, True), fill=INK)
    y = para(d, (40, 70), "The owner's direction: browns, creams and chitin. Readability comes from values, not hue. The plates are near-black "
             "walnut (L 33), far below every colony ground (paving L 122, grass L 100). A few large cream masses sit far above "
             "them: the Runner's head plate, the Brute's bosses, the Spitter's sutures, the joints and the barbs. Add one small "
             "lacquer sheen streak and one small amber node. Thin rims barely survive at 17 px per cell: the control row in "
             "06 loses only a few points without them. Creep is the dullest thing on the map, a grey-umber that sits between the "
             "plates and the paving, so near-black bodies still cut out of it.",
             1800, 18) + 20
    for i, n in enumerate(PALETTE_NAMES):
        c = CHITIN[n]
        x = 40 + i * 205
        d.rectangle([x, y, x + 180, y + 90], fill=c, outline=(0, 0, 0))
        L = 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]
        d.text((x, y + 96), n, font=font(16, True), fill=INK)
        d.text((x, y + 118), "#%02x%02x%02x  L %d" % (c + (int(L),)), font=font(14), fill=DIM)
    y += 160
    # ground values for reference
    d.text((40, y), "Ground values in the colony render (mean luminance):", font=font(18, True), fill=ACC)
    y += 30
    for i, (gname, box) in enumerate(GROUNDS[:2] + [("concrete", (1150, 600, 1230, 640))]):
        a = np.asarray(ground(frame, box).convert("RGB"))
        L = lum(a).mean()
        mean = tuple(int(v) for v in a.reshape(-1, 3).mean(0))
        x = 40 + i * 300
        d.rectangle([x, y, x + 120, y + 60], fill=mean)
        d.text((x + 130, y + 8), f"{gname}", font=font(16, True), fill=INK)
        d.text((x + 130, y + 30), f"L {L:.0f}", font=font(14), fill=DIM)
    y += 90
    # value-structure study: Runner rendered big, then the same in greyscale
    D = DIRECTIONS["D1"]
    for k, (kind, s) in enumerate((("runner", None), ("runner", 0.48), ("brute", None), ("spitter", None))):
        im = render(D[kind](st(s=s, tau=0.3)), 130, (2.4, 2.6), (0.5, 0.62 if kind == "runner" else 0.5))
        bg = Image.new("RGBA", im.size, (126, 100, 80, 255))
        bg.alpha_composite(im)
        img.paste(bg.convert("RGB"), (40 + k * 470, y))
        gray = bg.convert("L").convert("RGB")
        img.paste(gray.resize((gray.width // 2, gray.height // 2)), (40 + k * 470 + bg.width - 150, y + bg.height - 170))
    d.text((40, 1050), "D1 on a paving-value field; inset: the same in greyscale. The plate must stay dark and the rims light with the colour removed.",
           font=font(15), fill=DIM)
    img.save(out)


if __name__ == "__main__":
    old_sheet, frame, out_dir = sys.argv[1], sys.argv[2], Path(sys.argv[3])
    here = Path(__file__).resolve().parent
    palette_sheet(frame, out_dir / "01-chitin-palette.png")
    for k, n in (("D1", "02"), ("D2", "03")):
        BRIEFS[k]["palette"] = PALETTE_NAMES[:7]
        PAL[k + "_swatch"] = CHITIN
        board(k, here / "raw" / f"plate-{k}.png", frame, out_dir / f"{n}-direction-{k}.png")
    t = zoom_rows(old_sheet, frame, out_dir / "06-battle-zoom.png")
    for (label, g), c in t.items():
        print(f"{label:38s} {g:7s} {c:5.1f}")
