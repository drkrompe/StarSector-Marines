"""Review stills for the Rav Bloom direction pass, 1920x1080 at 100% scale.

  python boards.py <old strain sheet.png> <colony frame-0004.png> <out dir>

01 critique of the shipped strains (measured from the shipped strain sheet),
02-04 one board per direction (brief, ImageGen plate, palette, silhouettes),
08 the battle-zoom comparison on real colony ground with real Marines.
"""
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parents[1]))
from review_font import review_font  # noqa: E402

from creatures import DIRECTIONS, PAL, st  # noqa: E402
from motion import CELL, ground, marine_cutouts  # noqa: E402
from rig import render  # noqa: E402

BG = (16, 20, 26)
INK = (226, 228, 232)
DIM = (150, 156, 166)
ACC = (240, 196, 120)


def font(n, b=False):
    return review_font(n, b)


def para(d, xy, s, width, size=17, fill=INK, bold=False, gap=4):
    """Word-wrapped paragraph; returns the y after it."""
    f = font(size, bold)
    x, y = xy
    line = ""
    for word in s.split():
        trial = (line + " " + word).strip()
        if d.textlength(trial, font=f) > width and line:
            d.text((x, y), line, font=f, fill=fill)
            y += size + gap
            line = word
        else:
            line = trial
    if line:
        d.text((x, y), line, font=f, fill=fill)
        y += size + gap
    return y


# --------------------------------------------------------------- 01 critique
def alpha_from_bg(img, bg, thr=26):
    a = np.asarray(img.convert("RGB")).astype(int)
    diff = np.abs(a - np.array(bg)).sum(2)
    return diff > thr


def critique(old_sheet, out):
    sheet = Image.open(old_sheet).convert("RGB")
    img = Image.new("RGB", (1920, 1080), BG)
    d = ImageDraw.Draw(img)
    d.text((40, 20), "RAV BLOOM REDESIGN  /  what the shipped strains do today", font=font(30, True), fill=INK)
    d.text((40, 58), "measured from the shipped strain sheet (production layered composer, x8) and LayeredUnitComposer / LayeredAppearance",
           font=font(17), fill=DIM)
    # idle vs strike overlay per strain: idle in cyan, strike-only pixels in magenta
    cols = [("Runner", (20, 200, 240, 380), (425, 200, 645, 380)),
            ("Brute", (640, 170, 860, 380), (1040, 170, 1260, 380)),
            ("Spitter", (1270, 200, 1490, 380), (1670, 200, 1890, 380))]
    x = 40
    stats = []
    for name, idle_box, strike_box in cols:
        idle = alpha_from_bg(sheet.crop(idle_box), (17, 25, 32))
        strike0 = alpha_from_bg(sheet.crop(strike_box), (17, 25, 32))
        # register the strike pose on the idle one (the sheet's cells are not pixel-aligned)
        best = None
        for dy in range(-14, 15):
            for dx in range(-14, 15):
                sh = np.roll(np.roll(strike0, dy, 0), dx, 1)
                score = (sh ^ idle).sum()
                if best is None or score < best[0]:
                    best = (score, sh)
        strike = best[1]
        h, w = idle.shape
        ov = np.zeros((h, w, 3), np.uint8)
        ov[...] = BG
        ov[idle] = (60, 170, 190)
        ov[strike & ~idle] = (230, 70, 160)
        ov[idle & ~strike] = (40, 90, 110)
        new = (strike & ~idle).sum() / max(1, idle.sum())
        stats.append((name, new))
        o = Image.fromarray(ov).resize((int(w * 1.6), int(h * 1.6)), Image.NEAREST)
        img.paste(o, (x, 92))
        d.text((x, 92 + int(h * 1.6) + 4), f"{name}: strike pose covers {new * 100:.0f}% new area", font=font(17, True), fill=INK)
        x += int(w * 1.6) + 30
    d.text((40, 470), "cyan = idle silhouette, magenta = area only the strike pose covers, dark = area the strike uncovers (x8 sheet, registered, shown x1.6)",
           font=font(16), fill=DIM)
    # old battle-zoom crop x4
    bz = sheet.crop((1080, 850, 1310, 1010))
    img.paste(bz.resize((bz.width * 3, bz.height * 3), Image.NEAREST), (1200, 500))
    d.text((1200, 500 + bz.height * 3 + 6), "the shipped pack at battle zoom, x3 nearest: Runner / Brute / Spitter rows",
           font=font(16), fill=DIM)
    bullets = [
        ("One body plan, three sizes.", "Runner, Brute and Spitter are the same symmetric bug: body, head, two fore-claws, two feet. The Spitter walks on the Runner's own feet and claws. At battle zoom only size and the Spitter's yellow dot tell them apart."),
        ("The arm is one rigid sprite.", "A strike slides one claw sprite from under the body to just past the head and turns it 28 degrees about a pivot near its tip. No elbow, no wind-up, no follow-through: the strike pose covers 11-12% more area than idle (overlay above)."),
        ("The strike is 0.09 s and starts at the end.", "It plays only during PRIMARY_FLASH_SECONDS as 1 - smoothstep: full extension first, then back. There is no anticipation to read. Which claw strikes is picked by walk phase, not by where the target is."),
        ("Idle, walk and strike look alike.", "Walking shows toe tips 0.1 shoulder-widths past the body. Nothing breathes, pulses or trails. The Spitter's spit replays the claw swipe."),
        ("A speck at 17 px per cell.", "A Runner is about 10 px across. Grey-violet chitin sits at the value of the paving and of a Marine; the swarm owns no colour."),
        ("Nothing says Bloom.", "Chitin plates with amber nodes is the old XENO bug. Nothing reads fungal, red-tide or engineered."),
    ]
    y = 510
    for head, body in bullets:
        y = para(d, (40, y), head, 1100, 19, ACC, True, 2)
        y = para(d, (40, y), body, 1100, 17, INK) + 10
    img.save(out)
    return stats


# --------------------------------------------------------------- 02-04 boards
BRIEFS = {
    "A": dict(
        tag="The Bloom fruits: every body is a fruiting growth of the mycelium the reservoir is.",
        strains=[("Runner", "a small cap on four root stilts, two hyphal whips with frayed tips that fan open on the hit."),
                 ("Brute", "a bracket shelf: wide, flat-fronted, ringed; two trunk arms that slam. Plate = the woody shelf."),
                 ("Spitter", "a puffball on a tripod; the ball inflates, then fires a spore glob from its crimson ostiole."),
                 ("Matriarch", "a fairy ring of caps around a spore core.")],
        palette=["cap", "cap2", "ring", "lip", "hypha", "tip", "spore", "creep"],
        good="One language for the whole hive: creep, spore vents and ground colonies are the same fungus, so static growths and mobile strains belong together. The sporeship rumour reads well.",
        risk="Umber and violet sit at the value of colony paving; pale hyphae vanish below 3 px. Leans 'mushroom crab' when the whips are up.",
        motion="Four-segment whips, gallop on stilts, rhizome trail, cap wobble, spore puff on impact."),
    "B": dict(
        tag="The Bloom turns the ground the way a red tide turns a bay: each body is a colony of fused zooids.",
        strains=[("Runner", "a bead chain rowing on cilia, a dorsal float, trailing filaments, two barbed tentacles curled along its sides that coil tight and whip out a full cell."),
                 ("Brute", "a raft of bladders crusted with calcified plaques (its plate) and a club-tentacle ram."),
                 ("Spitter", "a glowing vermilion float over a siphon; the float swells, then empties into the glob."),
                 ("Matriarch", "a raft ringed with floats around a maw.")],
        palette=["flesh", "core", "float", "sting", "fringe", "plaque", "creep", "creep2"],
        good="It owns a colour: red on every colony surface, never mistaken for a Marine or a colonist. Long streak, spiky disc and glowing ball are three different shapes. Cilia, filaments and float pulse keep it alive even at 10 px. Not an insect.",
        risk="Red is shared with blood and with red UI markers, so creep goes oxblood-black and the bodies stay the brightest red on screen. Tentacle chains need more sprites per body. Must read wet and heavy, not cute.",
        motion="Five-segment tentacles with a 26 ms lag per joint (a whip), body bunching then stretching through the lunge, float swelling in the wind-up."),
    "C": dict(
        tag="Grown to a specification: a biomachine bred from sporeship seeding stock.",
        strains=[("Runner", "a three-lobed husk on a tripod gait; a lance folded flat on its back unfolds through three joints into a bone spike."),
                 ("Brute", "a seed-ram ovoid with a ridged bone cap and two telescoping hammer pistons."),
                 ("Spitter", "a hex husk with a long launch tube and a red ballast sac that squeezes on the shot."),
                 ("Matriarch", "a hexagonal core with six red seed bays, the seeding ship grown into a body.")],
        palette=["husk", "husk2", "bone", "joint", "pod", "whisker", "creep", "creep2"],
        good="The clearest 'engineered weapon' and the sporeship rumour made visible. The switchblade lance is the most dramatic arm. Tripod, ovoid, tube and hex are four distinct shapes.",
        risk="A dark husk is a dark speck at battle zoom, the same value as a Marine; red shows only at joints. It drifts toward bio-robot, which the lore rules out (not AI, not machine). The tripod gait is bespoke work.",
        motion="A three-joint switchblade unfold, a ticking tripod gait, whisker quiver, a pulsing seed pod."),
}


def swatches(d, x, y, key, names):
    P = PAL.get(key + "_swatch", PAL[key])
    for i, n in enumerate(names):
        c = P[n]
        d.rectangle([x + i * 76, y, x + i * 76 + 64, y + 40], fill=c, outline=(0, 0, 0))
        d.text((x + i * 76, y + 44), n, font=font(13), fill=DIM)


def board(key, plate, gnd_frame, out):
    D = DIRECTIONS[key]
    B = BRIEFS[key]
    img = Image.new("RGB", (1920, 1080), BG)
    d = ImageDraw.Draw(img)
    d.text((40, 20), f"DIRECTION {key}  /  {D['name']}", font=font(32, True), fill=INK)
    y = para(d, (40, 64), B["tag"], 600, 18, ACC, True)
    y += 8
    for name, s in B["strains"]:
        y = para(d, (40, y), f"{name}: {s}", 600, 17) + 4
    y += 6
    y = para(d, (40, y), "Strong", 600, 17, ACC, True, 2)
    y = para(d, (40, y), B["good"], 600, 16) + 6
    y = para(d, (40, y), "Risk", 600, 17, ACC, True, 2)
    y = para(d, (40, y), B["risk"], 600, 16) + 6
    y = para(d, (40, y), "Motion", 600, 17, ACC, True, 2)
    y = para(d, (40, y), B["motion"], 600, 16) + 10
    swatches(d, 40, max(y, 860), key, B["palette"])
    # plate
    pl = Image.open(plate).convert("RGB").resize((1200, 675), Image.LANCZOS)
    img.paste(pl, (680, 30))
    d.text((680, 710), "ImageGen concept plate over the rig's guide (geometry kept): Runner at rest, Runner striking, Brute, Spitter, Matriarch",
           font=font(15), fill=DIM)
    # battle-zoom silhouettes: true size on colony ground, then x4 nearest, then black silhouette x4
    g = ground(gnd_frame, (600, 1000, 1240, 1080))  # quiet paving
    patch = g.crop((0, 0, 60, 60))
    x = 680
    for kind, cells in (("runner", 3), ("brute", 3), ("spitter", 3), ("matriarch", 4)):
        size = int(cells * CELL)
        im = render(D[kind](st(tau=0.3)), CELL, (cells, cells), (0.5, 0.5), ss=6)
        tile = Image.new("RGBA", (size, size))
        for ty in range(0, size, patch.height):
            for tx in range(0, size, patch.width):
                tile.paste(patch, (tx, ty))
        tile.alpha_composite(im)
        img.paste(tile.convert("RGB"), (x, 745))
        big = tile.resize((size * 4, size * 4), Image.NEAREST)
        img.paste(big.convert("RGB"), (x + size + 10, 745))
        d.text((x, 745 + size + 4), kind, font=font(14), fill=DIM)
        x += size * 5 + 34
    d.text((680, 1050), "true size on colony paving (17.1 px per cell), the same pixels x4", font=font(15), fill=DIM)
    img.save(out)


# --------------------------------------------------------------- 08 zoom comparison
def old_pack(sheet):
    """The shipped Runner/Brute/Spitter at battle zoom, keyed off the sheet's flat ground."""
    sh = Image.open(sheet).convert("RGBA")
    outs = {}
    for name, box in (("runner", (1148, 860, 1172, 882)), ("brute", (1084, 914, 1116, 946)),
                      ("spitter", (1088, 978, 1114, 1002))):
        c = sh.crop(box)
        m = alpha_from_bg(c, (74, 70, 56), 30)
        a = Image.fromarray((m * 255).astype(np.uint8))
        c.putalpha(a)
        outs[name] = c
    return outs


def creep_patch(size, key, seed=3):
    P = PAL[key]
    rng = np.random.default_rng(seed)
    w, h = size
    m = Image.new("L", size, 0)
    d = ImageDraw.Draw(m)
    for _ in range(18):
        cx, cy = rng.uniform(0.15, 0.85) * w, rng.uniform(0.2, 0.8) * h
        r = rng.uniform(0.08, 0.2) * h
        d.ellipse([cx - r * 1.6, cy - r, cx + r * 1.6, cy + r], fill=255)
    m = m.filter(ImageFilter.GaussianBlur(5)).point(lambda v: 230 if v > 110 else int(v * 1.6))
    col = Image.new("RGBA", size, P["creep"] + (0,))
    col.putalpha(m)
    veins = Image.new("RGBA", size, (0, 0, 0, 0))
    vd = ImageDraw.Draw(veins)
    for _ in range(40):
        x, y = rng.uniform(0, w), rng.uniform(0, h)
        pts = [(x, y)]
        a = rng.uniform(0, 6.28)
        for _ in range(6):
            a += rng.uniform(-0.6, 0.6)
            x, y = x + 9 * math.cos(a), y + 9 * math.sin(a)
            pts.append((x, y))
        vd.line(pts, fill=P["creep2"] + (200,), width=1)
    va = np.asarray(veins).copy()
    va[..., 3] = (va[..., 3].astype(int) * (np.asarray(m).astype(int)) // 255).astype(np.uint8)
    col.alpha_composite(Image.fromarray(va))
    return col


def zoom_compare(sheet, frame, out):
    img = Image.new("RGB", (1920, 1080), BG)
    d = ImageDraw.Draw(img)
    d.text((40, 16), "BATTLE ZOOM  /  17.1 px per cell, 100% scale, real colony ground and Marines; right half of each strip is proposed creep",
           font=font(22, True), fill=INK)
    marines = marine_cutouts(frame)
    gbox = (600, 960, 1240, 1080)  # paving rows with no buildings
    olds = old_pack(sheet)
    rows = [("shipped", None), ("A  FRUITING", "A"), ("B  RED TIDE", "B"), ("C  SEEDCASE", "C")]
    y = 60
    for label, key in rows:
        g = ground(frame, gbox).crop((0, 0, 640, 120)).copy()
        if key:
            cp = creep_patch((300, 120), key)
            g.alpha_composite(cp, (330, 0))
        # marines on the left and in the creep
        for k, (mx, my) in enumerate(((60, 40), (84, 78), (420, 64), (460, 30))):
            m = marines[k % len(marines)]
            g.alpha_composite(m, (mx - m.width // 2, my - m.height // 2))
        spots = [("runner", (150, 40), None), ("runner", (178, 86), 0.5), ("brute", (240, 50), None),
                 ("spitter", (290, 92), None), ("runner", (500, 50), 0.5), ("runner", (540, 90), None),
                 ("brute", (590, 40), None), ("spitter", (600, 96), None)]
        for kind, (x, yy), s in spots:
            if key is None:
                im = olds[kind].rotate(90, expand=True)
            else:
                im = render(DIRECTIONS[key][kind](st(s=s, tau=0.3 + x)), CELL, (5, 5), (0.5, 0.5), yaw=-90, ss=6)
            g.alpha_composite(im, (int(x - im.width / 2), int(yy - im.height / 2)))
        img.paste(g.convert("RGB"), (40, y))
        inset = g.crop((30, 14, 340, 110)).resize((310 * 2, 96 * 2), Image.NEAREST)
        img.paste(inset.convert("RGB"), (720, y))
        inset2 = g.crop((360, 14, 640, 110)).resize((280 * 2, 96 * 2), Image.NEAREST)
        img.paste(inset2.convert("RGB"), (1350, y))
        d.text((40, y + 124), label, font=font(18, True), fill=ACC)
        d.text((720, y + 196), "x2: on paving", font=font(14), fill=DIM)
        d.text((1350, y + 196), "x2: on creep", font=font(14), fill=DIM)
        y += 252
    img.save(out)


if __name__ == "__main__":
    old_sheet, frame, out_dir = sys.argv[1], sys.argv[2], Path(sys.argv[3])
    print(critique(old_sheet, out_dir / "01-critique.png"))
    for i, k in enumerate("ABC"):
        board(k, HERE / "raw" / f"plate-{k}.png", frame, out_dir / f"0{i + 2}-direction-{k}.png")
    zoom_compare(old_sheet, frame, out_dir / "08-battle-zoom.png")


def silhouettes_sheet(out):
    """Black silhouettes, x4 of true battle-zoom size, every direction and strain;
    plus the area a Runner strike adds over its idle pose, measured on the rig."""
    img = Image.new("RGB", (1920, 1080), (206, 200, 190))
    d = ImageDraw.Draw(img)
    d.text((40, 16), "SILHOUETTES  /  true battle-zoom pixels x4, nearest: can you tell the strains apart?", font=font(26, True), fill=(20, 20, 24))
    y = 80
    stats = {}
    for key in "ABC":
        D = DIRECTIONS[key]
        d.text((40, y + 90), f"{key}  {D['name']}", font=font(22, True), fill=(20, 20, 24))
        x = 280
        for kind, s, cells in (("runner", None, 3), ("runner", 0.48, 3), ("brute", None, 3), ("spitter", None, 3), ("matriarch", None, 4)):
            im = render(D[kind](st(s=s, tau=0.3)), CELL, (cells, cells), (0.5, 0.62 if kind == "runner" else 0.5), ss=6, shadow=False)
            a = np.asarray(im.getchannel("A")) > 90
            sil = Image.fromarray(np.where(a, 20, 206).astype(np.uint8)).convert("RGB")
            big = sil.resize((sil.width * 4, sil.height * 4), Image.NEAREST)
            img.paste(big, (x, y))
            if kind == "runner":
                stats.setdefault(key, []).append(a)
            label = "runner strike" if s else kind
            d.text((x, y + big.height + 2), label, font=font(14), fill=(60, 60, 66))
            x += big.width + 30
        y += 330
    for key, (idle, strike) in stats.items():
        stats[key] = (strike & ~idle).sum() / max(1, idle.sum())
    img.save(out)
    return stats
