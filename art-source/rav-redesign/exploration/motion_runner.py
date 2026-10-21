"""Runner motion study: the round-4 lilac Runner rigged from ImageGen layers, running in, winding up,
lashing its barbed arm blades forward with a gaping bite, impaling a Marine and dragging it off.

  python motion_runner.py <render root> <out.gif>

Layers (motion-runner/raw/, generated over hero.png as the geometry authority):
  body-closed.png  body without arm blades, jaws shut
  body-gape.png    the same with the jaws gaping (only its head is used)
  blade.png        one isolated arm blade, root at the bottom, pointing up

<render root> holds colony-breach close-ups at the LZ, all on one focus so they share a centre
(commanderEvidence -Pmission=colony-breach -PmaxTicks=600 -PsnapshotEveryTicks=600
-PsnapshotWidth=1920 -PsnapshotHeight=1080 -PsnapshotAnnotations=false and):
  rav-lz96/  -PsnapshotFocus=197,195,20    96 px per cell
  rav-lz32/  -PsnapshotFocus=197,195,60    32 px per cell
  rav-lz17/  -PsnapshotFocus=197,195,112   17.1 px per cell
Ground is the tick-0 frame. Marines are cut from the 96 px frame at tick 600 (their cyan review
markers painted out) and scaled down for the closer-out panels, because the review markers swamp
the sprites below 96 px per cell.
"""
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageOps

from boards import font
from motion import save_gif
from rig import ease_out, smooth, strike_e
from round2 import shadow_blob
from round_sheet import RUNNER_AREA_PX

RAW = Path(__file__).parent / "motion-runner" / "raw"
BG = (16, 20, 26)
INK = (226, 228, 232)
DIM = (150, 156, 166)
ACC = (240, 196, 120)
PPC17 = 1920 / 112
FPS = 20
S = 0.4                       # working scale of the rig canvas against the 1024x1536 layers
PADX, PADT, PADB = 200, 560, 60
CW, CH = int((1024 + 2 * PADX) * S), int((1536 + PADT + PADB) * S)
ANCHOR = (512, 760)          # body centre in layer pixels
HOLD = (512, -270)           # where an impaled victim is held: between the crossing blade tips

# timeline (seconds)
T_RUN, T_STRIKE, T_DRAG, T_END = 1.0, 0.6, 2.0, 4.3
RUN_SPEED, DRAG_SPEED, STRIDE, LUNGE = 4.0, 1.2, 0.95, 0.35


def key(path):
    a = np.asarray(Image.open(path).convert("RGB")).astype(int)
    corners = np.concatenate([a[:20, :20].reshape(-1, 3), a[:20, -20:].reshape(-1, 3),
                              a[-20:, :20].reshape(-1, 3), a[-20:, -20:].reshape(-1, 3)])
    dist = np.sqrt(((a - np.median(corners, axis=0)) ** 2).sum(2))
    alpha = (np.clip((dist - 12) / 26, 0, 1) * 255).astype(np.uint8)
    return Image.fromarray(np.dstack([a.astype(np.uint8), alpha]), "RGBA")


def L(x, y):
    """Layer pixels to working-canvas pixels."""
    return ((x + PADX) * S, (y + PADT) * S)


def on_canvas(layer):
    c = Image.new("RGBA", (1024 + 2 * PADX, 1536 + PADT + PADB), (0, 0, 0, 0))
    c.alpha_composite(layer, (PADX, PADT))
    return c.resize((CW, CH), Image.LANCZOS)


def poly_mask(pts):
    m = Image.new("L", (CW, CH), 0)
    ImageDraw.Draw(m).polygon([L(x, y) for x, y in pts], fill=255)
    return m


def cut(img, mask):
    out = img.copy()
    a = np.minimum(np.asarray(img.getchannel("A")), np.asarray(mask))
    out.putalpha(Image.fromarray(a))
    return out


def erase(img, mask):
    out = img.copy()
    a = np.asarray(img.getchannel("A")).astype(int) * (255 - np.asarray(mask).astype(int)) // 255
    out.putalpha(Image.fromarray(a.astype(np.uint8)))
    return out


def mirror_pts(pts):
    return [(1024 - x, y) for x, y in pts]


# leg regions below the torso plates, each turned about its hip (layer pixels, left side)
LEGS = {
    "FL": ([(80, 630), (322, 630), (322, 840), (80, 840)], (330, 610), 16, 0.0),
    "HL": ([(90, 975), (322, 975), (322, 1310), (90, 1310)], (305, 905), 13, math.pi),
}
TAIL = ([(420, 1030), (604, 1030), (604, 1520), (420, 1520)], (512, 1010))


class Rig:
    def __init__(self):
        closed = on_canvas(key(RAW / "body-closed.png"))
        gape = on_canvas(key(RAW / "body-gape.png"))
        head = Image.new("L", (CW, CH), 0)
        hd = ImageDraw.Draw(head)
        for i in range(12):  # feathered head window: the gape layer is used above the neck only
            hd.rectangle([*L(300, -200), *L(724, 300 + i * 6)], fill=int(255 * (i + 1) / 12))
        self.gape_head = cut(gape, head)
        self.legs = []
        base = closed
        for name, (pts, hip, amp, ph) in LEGS.items():
            for side, p, h in (("L", pts, hip), ("R", mirror_pts(pts), (1024 - hip[0], hip[1]))):
                m = poly_mask(p)
                self.legs.append((cut(closed, m), L(*h), amp, ph + (math.pi if side == "R" else 0.0)))
                base = erase(base, m)
        m = poly_mask(TAIL[0])
        self.tail = (cut(closed, m), L(*TAIL[1]))
        self.base = erase(base, m)
        self.closed_head = cut(closed, head)
        # blade: ivory plus a short forearm, root at the bottom
        b = key(RAW / "blade.png")
        x0, y0, x1, y1 = b.getchannel("A").point(lambda v: 255 if v > 60 else 0).getbbox()
        h = y1 - y0
        b = b.crop((x0, y0, x1, y0 + int(0.6 * h)))
        k = 560 / (0.45 * h) * S
        b = b.resize((max(1, int(b.width * k)), max(1, int(b.height * k))), Image.LANCZOS)
        rows = np.asarray(b.getchannel("A"))[-12:] > 128
        root_x = float(np.nonzero(rows.any(0))[0].mean())
        self.blade = (b, (root_x, b.height - 4))
        self.blade_l = (ImageOps.mirror(b), (b.width - root_x, b.height - 4))
        self.hero_area = float((np.asarray(on_canvas(key(RAW / "hero.png")).getchannel("A")) > 128).sum())

    def pose(self, gait, sway, tail, crouch, gape, blade_a, blade_piv):
        c = Image.new("RGBA", (CW, CH), (0, 0, 0, 0))
        for img, hip, amp, ph in self.legs:
            a = amp * math.sin(2 * math.pi * gait + ph)
            c.alpha_composite(img.rotate(a if hip[0] < L(512, 0)[0] else -a, center=hip, resample=Image.BICUBIC))
        ti, tp = self.tail
        c.alpha_composite(ti.rotate(tail, center=tp, resample=Image.BICUBIC))
        c.alpha_composite(self.base)
        if gape > 0.01:
            g = self.gape_head.copy()
            g.putalpha(g.getchannel("A").point(lambda v: int(v * min(1, gape))))
            c.alpha_composite(g)
        for (img, root), sgn in ((self.blade, 1), (self.blade_l, -1)):
            px, py = blade_piv
            pivot = L(512 + sgn * (px - 512), py)
            layer = Image.new("RGBA", (CW, CH), (0, 0, 0, 0))
            layer.alpha_composite(img, (int(pivot[0] - root[0]), int(pivot[1] - root[1])))
            c.alpha_composite(layer.rotate(-sgn * blade_a, center=pivot, resample=Image.BICUBIC))
        about = L(*ANCHOR)
        if abs(sway) > 0.01:
            c = c.rotate(sway, center=about, resample=Image.BICUBIC)
        if crouch < 0.999:
            w, h = int(CW * crouch), int(CH * crouch)
            small = c.resize((w, h), Image.LANCZOS)
            c = Image.new("RGBA", (CW, CH), (0, 0, 0, 0))
            c.alpha_composite(small, (int(about[0] * (1 - crouch)), int(about[1] * (1 - crouch))))
        return c


# ----------------------------------------------------------------------------- timeline
def blade_pose(e):
    """Right-blade angle (degrees clockwise from the head) and root, from strike_e's -1 coil .. 1 extend."""
    rest, coil, ext = 150.0, 182.0, -14.0
    rp, cp, xp = (650, 300), (700, 360), (602, 236)
    if e < 0:
        t = -e
        return rest + (coil - rest) * t, (rp[0] + (cp[0] - rp[0]) * t, rp[1] + (cp[1] - rp[1]) * t)
    t = min(e, 1.1)
    return rest + (ext - rest) * t, (rp[0] + (xp[0] - rp[0]) * min(t, 1), rp[1] + (xp[1] - rp[1]) * min(t, 1))


def state(t):
    """Everything about the Runner at time t, positions in cells along the approach (x grows toward the Marine)."""
    p1 = RUN_SPEED * T_RUN
    if t < T_RUN:
        return dict(x=RUN_SPEED * t, phase="approach", s=None, gait=RUN_SPEED * t / STRIDE, gape=0.0, held=False)
    if t < T_RUN + T_STRIKE:
        u = t - T_RUN
        s = u / T_STRIKE
        dec = min(u, 0.2)
        x = p1 + RUN_SPEED * dec - 10 * dec * dec
        if s >= 0.34:
            x += LUNGE * ease_out(min(1, (s - 0.34) / 0.1))
        name = "wind-up" if s < 0.34 else ("lash" if s < 0.44 else "impale")
        gape = 0.5 * smooth(s / 0.34) if s < 0.34 else 1.0
        return dict(x=x, phase=name, s=s, gait=(p1 + RUN_SPEED * dec - 10 * dec * dec) / STRIDE, gape=gape,
                    held=s >= 0.44)
    x_hold = p1 + 0.4 + LUNGE
    u = t - T_RUN - T_STRIKE
    if u < T_DRAG:
        back = DRAG_SPEED * max(0.0, u - 0.15)
        return dict(x=x_hold - back, phase="drag-off", s=1.0, gait=(x_hold - back) / STRIDE,
                    gape=max(0.0, 1 - u / 0.12), held=True, clamp=min(1, u / 0.12))
    return dict(x=x_hold - DRAG_SPEED * (T_DRAG - 0.15), phase="drag-off", s=1.0,
                gait=(x_hold - DRAG_SPEED * (T_DRAG - 0.15)) / STRIDE, gape=0.0, held=True, clamp=1.0, still=True)


def runner_pose(rig, st):
    s = st["s"]
    if s is None:
        e = 0.0
    elif s < 0.58:
        e = strike_e(s)
    else:
        e = 0.96  # blades stay forward and crossed, carrying
    a, piv = blade_pose(e)
    g = st["gait"]
    still = st.get("still", False)
    crouch = 1.0
    if s is not None and s < 0.34:
        crouch = 1 - 0.05 * smooth(s / 0.34)
    elif s is not None and s < 0.5:
        crouch = 0.95 + 0.05 * smooth((s - 0.34) / 0.16)
    sway = 0.0 if still else 2.0 * math.sin(2 * math.pi * g)
    tail = 0.0 if still else -6.0 * math.sin(2 * math.pi * g * 0.5)
    return rig.pose(g, sway, tail, crouch, st["gape"], a, piv)


# ----------------------------------------------------------------------------- scenes
def marine_sprites(root):
    d = Path(root) / "rav-lz96" / "visuals" / "colony-breach" / "frames"
    a = np.asarray(Image.open(d / "frame-0000-tick-000000.png").convert("RGB")).astype(int)
    im = Image.open(d / "frame-0001-tick-000600.png").convert("RGB")
    b = np.asarray(im).astype(int)
    m = np.abs(a - b).sum(2) > 40
    cyan = (b[..., 2] > 170) & (b[..., 1] > 150) & (b[..., 0] < 120)
    outs = []
    for box in ((1440, 180, 1900, 720), (160, 360, 700, 1080)):
        x0, y0, x1, y1 = box
        sub = m[y0:y1, x0:x1].copy()
        seen = np.zeros_like(sub)
        for y in range(sub.shape[0]):
            for x in range(sub.shape[1]):
                if not sub[y, x] or seen[y, x]:
                    continue
                stack, pts = [(y, x)], []
                seen[y, x] = 1
                while stack:
                    cy, cx = stack.pop()
                    pts.append((cy, cx))
                    for dy in (-2, -1, 0, 1, 2):
                        for dx in (-2, -1, 0, 1, 2):
                            ny, nx = cy + dy, cx + dx
                            if 0 <= ny < sub.shape[0] and 0 <= nx < sub.shape[1] and sub[ny, nx] and not seen[ny, nx]:
                                seen[ny, nx] = 1
                                stack.append((ny, nx))
                if not 400 < len(pts) < 4000:
                    continue
                ys, xs = zip(*pts)
                bx0, by0, bx1, by1 = min(xs) + x0, min(ys) + y0, max(xs) + x0 + 1, max(ys) + y0 + 1
                rgb = b[by0:by1, bx0:bx1].copy()
                cy_ = cyan[by0:by1, bx0:bx1]
                grey = np.median(rgb[~cy_ & m[by0:by1, bx0:bx1]], axis=0) if (~cy_).any() else (90, 96, 100)
                rgb[cy_] = grey
                alpha = (m[by0:by1, bx0:bx1] * 255).astype(np.uint8)
                outs.append(Image.fromarray(np.dstack([rgb.astype(np.uint8), alpha]), "RGBA"))
    return outs


def ground(root, z):
    d = Path(root) / f"rav-lz{z}" / "visuals" / "colony-breach" / "frames"
    return Image.open(d / "frame-0000-tick-000000.png").convert("RGBA")


SCENE_U = 1.8                      # scene centre, cells right of the frame centre
VICTIM = (4.2, 0.1)
SQUAD = [(5.0, -0.7), (5.5, 0.5), (4.9, 1.1), (5.8, -0.1)]
LANE_V = 0.1


def to_px(u, v, ppc, box):
    return 960 + u * ppc - box[0], 540 + v * ppc - box[1]


def panel(rig, st, t, gnd, marines, ppc, box, start_u):
    tile = gnd.crop(box).copy()
    sc = (RUNNER_AREA_PX * (ppc / PPC17) ** 2 / rig.hero_area) ** 0.5
    mk = ppc / 96

    def put(img, cx, cy, radius, angle=0.0):
        sh, (ox, oy) = shadow_blob(ppc, radius)
        tile.alpha_composite(sh, (int(cx + ox - sh.width / 2), int(cy + oy - sh.height / 2)))
        if angle:
            img = img.rotate(angle, expand=True, resample=Image.BICUBIC)
        tile.alpha_composite(img, (int(cx - img.width / 2), int(cy - img.height / 2)))

    def small(m):
        return m.convert("RGBa").resize((max(1, round(m.width * mk)), max(1, round(m.height * mk))),
                                        Image.LANCZOS).convert("RGBA")

    for i, (u, v) in enumerate(SQUAD):
        put(small(marines[(i + 1) % len(marines)]), *to_px(u, v, ppc, box), 0.25)
    body = runner_pose(rig, st)
    body = body.resize((max(1, int(CW * sc)), max(1, int(CH * sc))), Image.LANCZOS).rotate(-90, expand=True,
                                                                                         resample=Image.BICUBIC)
    # anchor of the rotated, scaled canvas: (x, y) -> (H - y, x)
    ax, ay = L(*ANCHOR)
    hx, hy = L(*HOLD)
    Hs = CH * sc
    anc = (Hs - ay * sc, ax * sc)
    hold = (Hs - hy * sc, hx * sc)
    bx, by = to_px(start_u + st["x"], LANE_V, ppc, box)
    victim = small(marines[0])
    vx, vy = to_px(*VICTIM, ppc, box)
    if st["held"]:
        k = smooth(min(1, (t - (T_RUN + 0.44 * T_STRIKE)) / 0.1))
        tx, ty = bx + hold[0] - anc[0], by + hold[1] - anc[1]
        vx, vy = vx + (tx - vx) * k, vy + (ty - vy) * k
        dim = victim.copy()
        dim = Image.fromarray((np.asarray(dim).astype(float) * [0.8, 0.8, 0.8, 1]).astype(np.uint8), "RGBA")
        put(dim, vx, vy, 0.22, angle=90 * k)
    sh, (ox, oy) = shadow_blob(ppc, 0.34)
    tile.alpha_composite(sh, (int(bx + ox - sh.width / 2), int(by + oy - sh.height / 2)))
    tile.alpha_composite(body, (int(bx - anc[0]), int(by - anc[1])))
    if not st["held"]:
        put(victim, vx, vy, 0.25)
    return tile


def build(root, out):
    rig = Rig()
    marines = marine_sprites(root)
    g96, g32, g17 = ground(root, 96), ground(root, 32), ground(root, 17)
    # where the Runner starts, so the lunge ends with the victim at the hold point
    sc96 = (RUNNER_AREA_PX * (96 / PPC17) ** 2 / rig.hero_area) ** 0.5
    reach = (L(*ANCHOR)[1] - L(*HOLD)[1]) * sc96 / 96
    x_hold = RUN_SPEED * T_RUN + 0.4 + LUNGE
    start_u = VICTIM[0] - reach - x_hold
    cx = 960 + SCENE_U * 96
    b96 = (int(cx - 620), 90, int(cx + 620), 990)
    b32 = (int(960 + SCENE_U * 32 - 316), 330, int(960 + SCENE_U * 32 + 316), 750)
    b17 = (int(960 + SCENE_U * PPC17 - 158), 450, int(960 + SCENE_U * PPC17 + 158), 630)
    frames = []
    n = int(T_END * FPS)
    for f in range(n):
        t = f / FPS
        st = state(t)
        img = Image.new("RGB", (1920, 1080), BG)
        d = ImageDraw.Draw(img)
        d.text((24, 12), "RAV BLOOM RUNNER  /  MOTION STUDY  /  run in, wind up, lash, impale, drag off",
               font=font(24, True), fill=INK)
        d.text((24, 44), "Round-4 lilac Runner (#2 pose and jaw, #3 barbed gaffs) rigged from ImageGen layers. "
                         "Colony LZ ground, real Marine sprites, live unit shadows. Played at real time.",
               font=font(14), fill=DIM)
        img.paste(panel(rig, st, t, g96, marines, 96, b96, start_u).convert("RGB"), (16, 74))
        d.text((24, 980), "96 px per cell (zoomed right in)", font=font(14), fill=DIM)
        img.paste(panel(rig, st, t, g32, marines, 32, b32, start_u).convert("RGB"), (1272, 74))
        d.text((1272, 500), "32 px per cell", font=font(14), fill=DIM)
        p17 = panel(rig, st, t, g17, marines, PPC17, b17, start_u).convert("RGB")
        img.paste(p17.resize((632, 360), Image.NEAREST), (1272, 530))
        d.text((1272, 896), "17.1 px per cell, shown x2", font=font(14), fill=DIM)
        # phase strip
        phases = ["approach", "wind-up", "lash", "impale", "drag-off"]
        for i, ph in enumerate(phases):
            x = 1272 + i * 128
            on = ph == st["phase"]
            d.rectangle([x, 930, x + 122, 966], fill=(240, 196, 120) if on else (34, 38, 46))
            d.text((x + 10, 938), ph, font=font(16, True), fill=BG if on else DIM)
        d.text((1272, 980), f"t = {t:4.2f} s   wind-up 0.20 s, lash 0.06 s", font=font(14), fill=DIM)
        d.text((1272, 1000), "the strike lands on the damage tick; the Runner backs off at a third of its run speed",
               font=font(13), fill=DIM)
        frames.append(img)
    save_gif(frames, out, FPS, accents=[(255, 168, 54), (214, 170, 214), (150, 40, 90), (236, 226, 206)])
    return frames


if __name__ == "__main__":
    fr = build(sys.argv[1], sys.argv[2])
    if len(sys.argv) > 3:  # optional contact strip of key frames
        picks = [int(x * FPS) for x in (0.85, 1.1, 1.2, 1.25, 1.4, 2.9)]
        strip = Image.new("RGB", (1920, 1080), BG)
        for i, p in enumerate(picks):
            strip.paste(fr[p].crop((16 + 540, 74 + 200, 16 + 1160, 74 + 650)), ((i % 3) * 640 + 10, (i // 3) * 520 + 80))
            ImageDraw.Draw(strip).text(((i % 3) * 640 + 14, (i // 3) * 520 + 540), f"t = {p / FPS:.2f} s",
                                       font=font(16, True), fill=ACC)
        ImageDraw.Draw(strip).text((24, 20), "RUNNER MOTION STUDY  /  key frames at 96 px per cell, 1:1",
                                   font=font(24, True), fill=INK)
        strip.save(sys.argv[3])
    if len(sys.argv) > 4:  # optional sheet of the combined design and its rig layers
        sheet = Image.new("RGB", (1920, 1080), BG)
        d = ImageDraw.Draw(sheet)
        d.text((24, 16), "RUNNER  /  combined design and its rig layers", font=font(24, True), fill=INK)
        labels = ["combined: #2 pose and jaw, #3 barbed gaffs", "layer: body, jaws shut", "layer: body, jaws gaping",
                  "layer: one arm blade"]
        for i, (n, lab) in enumerate(zip(["hero", "body-closed", "body-gape", "blade"], labels)):
            im = Image.open(RAW / f"{n}.png").convert("RGB")
            k = min(464 / im.width, 940 / im.height)
            im = im.resize((int(im.width * k), int(im.height * k)), Image.LANCZOS)
            sheet.paste(im, (16 + i * 476, 64))
            d.text((20 + i * 476, 74 + im.height), lab, font=font(15, True), fill=ACC)
        sheet.save(sys.argv[4])
