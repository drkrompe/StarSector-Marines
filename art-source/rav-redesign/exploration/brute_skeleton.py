"""The Brute's skeleton: skeleton.py's technique (legs under the body, solved in their own side-view
plane, feet on a phase gait that keeps a supporting hoof planted) with a pig/cow build, and the forces
that a camera overhead can read off it.

  python brute_skeleton.py <out.gif>      (writes <out>-A.gif, -B, -C: one per clip)

Body frame: y forward, x to the right, z up, in cells, measured off the chosen hero (round 3 #6) at the
Brute's sim size. Legs are short and thick and end in hooves; the forelimb is two bones with the elbow
back, the hind three (stifle forward, hock back), as in skeleton.py.

Each clip is a body track (position along the path over time) and a gait schedule:
  A  walk    0.8 cells/s, four-beat lateral sequence (left hind, left fore, right hind, right fore)
  B  charge  a trot at the sim's 1.8 cells/s, then a transverse gallop at 4 cells/s
  C  ram     trot in, plant all four hooves, rear and slam twice, trot on through the hole
At 1.8 cells/s a body with legs this short cannot walk: it trots, as a pig does at that pace.

What the overhead view gets from the skeleton, besides the hooves:
  load     how much weight each hoof carries (0 in the air, rising to 1 a little after it lands)
  sway     the shoulders and the hips each shift toward the side of their pair that carries more
           weight, and push away from a hind hoof driving off at the end of its stance
  surge    the hips drive forward and the shoulders reach as a hind pair pushes off (spine flex)
"""
import math
import sys

import numpy as np
from PIL import Image, ImageDraw

from boards import font
from rig import ease_out, smooth
from motion_runner import key
from skeleton import ik2

FPS = 20
T_END = 5.6
# ----------------------------------------------------------------------------- anatomy (cells)
U_LAYER = 776.4                 # hero layer pixels per cell at sim size (motion_brute's U)
SHOULDER_Y, HIP_Y = (760 - 790) / U_LAYER, (760 - 1158) / U_LAYER   # leg roots, from the body anchor
FRONT_X, HIND_X = (512 - 186) / U_LAYER, (512 - 256) / U_LAYER      # leg planes: the hooves' rest x
SHOULDER_Z, HIP_Z = 0.30, 0.29
FRONT = dict(a=0.15, b=0.15)
HIND = dict(a=0.12, b=0.12, c=0.075)
GAITS = {  # duty factor, stride (cells), phase offsets, lift (cells)
    "walk": (0.72, 0.36, {"HL": 0.0, "FL": 0.25, "HR": 0.5, "FR": 0.75}, 0.05),
    "trot": (0.52, 0.46, {"FL": 0.0, "HR": 0.0, "FR": 0.5, "HL": 0.5}, 0.055),
    "gallop": (0.36, 0.90, {"HL": 0.0, "HR": 0.12, "FL": 0.5, "FR": 0.62}, 0.07),
}
FEET = ("FL", "FR", "HL", "HR")
WALK, TROT, CHARGE = 0.8, 1.8, 4.0

# ----------------------------------------------------------------------------- clip tracks
T_TROT_B, T_GALLOP_B, T_HIT_B = 1.2, 1.6, 2.6
WALL_STOP = 2.0                  # clip C plants at the wall
BLOWS = [2.2, 3.15]              # wind-up start of each ram
WIND, SLAM, HOLD, BACK = 0.32, 0.07, 0.25, 0.35
T_THROUGH = 4.15


def ram(t, t0):
    """Body offset along the path, crouch (cells) and head pose over one ram."""
    u = t - t0
    if u < 0 or u > WIND + SLAM + HOLD + BACK:
        return 0.0, 0.0, dict(dy=0.0, k=1.0), None
    if u < WIND:   # rear back onto the haunches, head up
        s = smooth(u / WIND)
        return -0.14 * s, 0.03 * s, dict(dy=40 * s, k=1 + 0.08 * s), "rear"
    if u < WIND + SLAM:
        s = ease_out((u - WIND) / SLAM)
        return -0.14 + 0.32 * s, 0.03 - 0.05 * s, dict(dy=40 - 110 * s, k=1.08 - 0.13 * s), "RAM"
    if u < WIND + SLAM + HOLD:
        return 0.18, -0.02, dict(dy=-70, k=0.95), "RAM"
    s = smooth((u - WIND - SLAM - HOLD) / BACK)
    return 0.18 * (1 - s), -0.02 * (1 - s), dict(dy=-70 * (1 - s), k=0.95 + 0.05 * s), "recover"


def track(clip, t):
    """Body position along the path, gait ('plant' pins the hooves), crouch, head pose, phase label."""
    head = dict(dy=0.0, k=1.0)
    if clip == "A":
        return WALK * t, "walk", 0.0, head, "walk"
    if clip == "B":
        if t < T_TROT_B:
            return TROT * t, "trot", 0.0, head, "trot (sim speed)"
        x0 = TROT * T_TROT_B
        u = t - T_TROT_B
        s = smooth(min(1, u / 0.35))
        head = dict(dy=-45 * s, k=1 - 0.07 * s)
        acc = (CHARGE - TROT) / 0.5
        if u < 0.5:
            x = x0 + TROT * u + 0.5 * acc * u * u
        else:
            x = x0 + TROT * 0.5 + 0.5 * acc * 0.25 + CHARGE * (u - 0.5)
        gait = "trot" if t < T_GALLOP_B else "gallop"
        phase = "head down" if t < T_GALLOP_B else "charge"
        if t > T_HIT_B + 0.3:
            uu = min(t - T_HIT_B - 0.3, 0.8)
            xs = x0 + TROT * 0.5 + 0.5 * acc * 0.25 + CHARGE * (T_HIT_B + 0.3 - T_TROT_B - 0.5)
            x = xs + CHARGE * uu - CHARGE / 1.6 * uu * uu
            gait = "gallop" if uu < 0.35 else "trot"
            phase = "pull up"
        return x, gait, 0.0, head, phase
    if t < WALL_STOP:
        return TROT * t, "trot", 0.0, head, "trot"
    x = TROT * WALL_STOP
    if t < T_THROUGH:
        for i, t0 in enumerate(BLOWS):
            off, crouch, h, ph = ram(t, t0)
            if ph:
                return x + off, "plant", crouch, h, f"{ph} {i + 1}"
        return x, "plant", 0.0, head, "brace"
    return x + 0.9 * (t - T_THROUGH), "walk", 0.0, head, "through the hole"


# ----------------------------------------------------------------------------- the skeleton
def gait_foot(name, phi, gait):
    duty, stride, offs, lift = GAITS[gait]
    u = (phi + offs[name]) % 1.0
    half = duty * stride / 2
    if u < duty:
        return half - (u / duty) * 2 * half, 0.0, u / duty
    k = (u - duty) / (1 - duty)
    return -half + 2 * half * smooth(k), lift * math.sin(math.pi * k), None


class BruteSkeleton:
    def __init__(self, clip):
        self.clip = clip
        self.phi = 0.0
        self.last_y = None
        self.gait = None
        self.blend = None
        self.pinned = {}
        self.prev = None
        self.load = {f: 1.0 for f in FEET}

    def frame(self, t, dt):
        y, gait, crouch, head, phase = track(self.clip, t)
        dy = 0.0 if self.last_y is None else y - self.last_y
        self.last_y = y
        if gait != "plant":
            self.phi += dy / GAITS[gait][1]
        feet, stance = {}, {}
        for n in FEET:
            root_y = y + (SHOULDER_Y if n[0] == "F" else HIP_Y)
            x = (-1 if n[1] == "L" else 1) * (FRONT_X if n[0] == "F" else HIND_X)
            if gait == "plant":
                if n not in self.pinned:
                    p = self.prev[n]
                    land = p[1] if p[2] < 0.005 else root_y + 0.03
                    self.pinned[n] = (p, land, t)
                p, land, t0 = self.pinned[n]
                k = smooth(min(1, (t - t0) / 0.12))
                feet[n] = (x, p[1] + (land - p[1]) * k, p[2] * (1 - k))
                stance[n] = 0.5
            else:
                fy, fz, st = gait_foot(n, self.phi, gait)
                feet[n] = (x, root_y + fy, fz)
                stance[n] = st
        if gait != "plant":
            self.pinned = {}
        if self.gait is not None and gait != self.gait and gait != "plant" and self.prev:
            self.blend = ({n: (f[1] - y, f[2]) for n, f in self.prev.items()}, 0.0)
        self.gait = gait
        if self.blend is not None:
            frm, bt = self.blend
            bt += dt
            k = smooth(min(1, bt / 0.25))
            for n in FEET:
                fx, fy, fz = feet[n]
                by, bz = frm[n]
                feet[n] = (fx, y + by + (fy - y - by) * k, bz + (fz - bz) * k + 0.03 * math.sin(math.pi * k))
            self.blend = None if k >= 1 else (frm, bt)
        self.prev = feet
        # load: weight a hoof carries, eased in after landing so the shift reads as a transfer
        for n in FEET:
            target = 1.0 if feet[n][2] < 0.004 else 0.0
            rate = 1 - math.exp(-dt / 0.06)
            self.load[n] += (target - self.load[n]) * rate
        run = {"walk": 0.4, "trot": 0.7, "gallop": 1.0, "plant": 0.0}[gait]
        # push-off: a hind hoof late in its stance drives the hips away from its own side and forward
        push = {n: (smooth(max(0.0, (stance[n] - 0.6) / 0.4)) if stance[n] is not None else 0.0) for n in FEET}
        sway_f = 0.035 * (self.load["FR"] - self.load["FL"]) * run
        sway_h = 0.045 * (self.load["HR"] - self.load["HL"]) * run + 0.02 * run * (push["HL"] - push["HR"])
        surge = 0.03 * run * (push["HL"] + push["HR"]) - 0.012 * run * (self.load["FL"] + self.load["FR"] - 1)
        rise_f = 0.012 * run * (1 - 0.5 * (self.load["FL"] + self.load["FR"]))
        rise_h = 0.012 * run * (1 - 0.5 * (self.load["HL"] + self.load["HR"]))
        sh = (sway_f, y + SHOULDER_Y + surge * 0.4, SHOULDER_Z - crouch + rise_f)
        hp = (sway_h, y + HIP_Y + surge, HIP_Z - crouch * 1.6 + rise_h)
        legs = {}
        for n in FEET:
            root = sh if n[0] == "F" else hp
            fx, fy, fz = feet[n]
            rx = fx + root[0]
            if n[0] == "F":
                elbow, hoof = ik2((root[1], root[2] - 0.02), (fy, fz), FRONT["a"], FRONT["b"], -1)
                legs[n] = [(rx, root[1], root[2] - 0.02), (rx, *elbow), (fx, *hoof)]
            else:
                hock_t = (fy - 0.02, fz + HIND["c"])
                stifle, hock = ik2((root[1], root[2] - 0.02), hock_t, HIND["a"], HIND["b"], 1)
                legs[n] = [(rx, root[1], root[2] - 0.02), (rx, *stifle), (rx, *hock), (fx, fy, fz)]
        return dict(t=t, y=y, gait=gait, phase=phase, feet=feet, legs=legs, load=dict(self.load), sh=sh, hp=hp,
                    crouch=crouch, head=head, run=run)


def simulate(clip, n=None):
    sk = BruteSkeleton(clip)
    n = n or int(T_END * FPS)
    return [sk.frame(f / FPS, 1 / FPS) for f in range(n)]


# ----------------------------------------------------------------------------- body warp
ANCHOR_Y = 760          # hero layer row of the body anchor
SH_ROW, HP_ROW = 790, 1158


def body_offsets(s):
    """Lateral (x) and along-body (y, + forward) offsets, in cells, of the shoulders and the hips."""
    sh, hp, y = s["sh"], s["hp"], s["y"]
    return (sh[0], sh[1] - (y + SHOULDER_Y)), (hp[0], hp[1] - (y + HIP_Y))


def sway_warp(img, s, U, pivot_row=ANCHOR_Y, row0=0, scale=1.0):
    """Bend a head-up body image: each row shifts sideways and along the body by the shoulder offset at the
    shoulder row and the hip offset at the hip row, interpolated between and carried on past them (the head
    rides on the shoulders; the tail swings on past the hips). `row0`, `scale` map image rows to hero rows."""
    (sx, sy), (hx, hy) = body_offsets(s)
    a = np.asarray(img).astype(float)
    h, w = a.shape[:2]
    rows = row0 + np.arange(h) / scale
    k = (rows - SH_ROW) / (HP_ROW - SH_ROW)
    k = np.clip(k, -0.6, 1.8)
    dx = (sx + (hx - sx) * k) * U * scale
    dy = -(sy + (hy - sy) * k) * U * scale             # + forward is up the image
    a[..., :3] *= a[..., 3:4] / 255
    yy, xx = np.mgrid[0:h, 0:w].astype(float)
    sxm = np.clip(xx - dx[:, None], 0, w - 1.001)
    sym = np.clip(yy - dy[:, None], 0, h - 1.001)
    x0, y0 = sxm.astype(int), sym.astype(int)
    fx, fy = (sxm - x0)[..., None], (sym - y0)[..., None]
    out = (a[y0, x0] * (1 - fx) * (1 - fy) + a[y0, x0 + 1] * fx * (1 - fy)
           + a[y0 + 1, x0] * (1 - fx) * fy + a[y0 + 1, x0 + 1] * fx * fy)
    al = out[..., 3:4]
    out[..., :3] = np.where(al > 0, out[..., :3] * 255 / np.maximum(al, 1e-6), 0)
    return Image.fromarray(np.clip(out, 0, 255).astype(np.uint8), "RGBA")


# ----------------------------------------------------------------------------- the stick GIF
BG = (16, 20, 26)
INK = (226, 228, 232)
DIM = (150, 156, 166)
ACC = (240, 196, 120)
NEAR, FAR = (226, 176, 226), (128, 84, 132)
BODY, EDGE = (110, 50, 96), (200, 150, 196)
HOOF, HOOF_AIR = (240, 230, 206), (176, 150, 168)
GROUND = (60, 66, 76)
CLIPS = [("A", "walk 0.8 cells/s, four-beat"), ("B", "trot at sim speed 1.8, then charge gallop 4.0"),
         ("C", "trot in, plant, rear and ram twice")]


def draw(frames_by_clip, f, scale=2, rows=None):
    rows = rows or CLIPS
    W, H = 1920 * scale, 1080 * scale
    img = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(img)
    ppc = 330 * scale
    ppc_top = 200 * scale

    def line(pts, col, w):
        d.line(pts, fill=col, width=int(w * scale), joint="curve")
        for p in pts:
            r = w * scale / 2
            d.ellipse([p[0] - r, p[1] - r, p[0] + r, p[1] + r], fill=col)

    top_row = (336 * (3 - len(rows))) // 2
    for r, (cid, _) in enumerate(rows):
        s = frames_by_clip[cid][f]
        cam = s["y"]
        oy = (70 + top_row + r * 336) * scale

        def side(p, ox=430 * scale, gy=oy + 250 * scale):
            return ox + (p[1] - cam) * ppc, gy - p[2] * ppc

        def top(p, ox=1440 * scale, cy=oy + 150 * scale):
            return ox + (p[1] - cam) * ppc_top, cy + p[0] * ppc_top

        gy = oy + 250 * scale
        d.line([(30 * scale, gy), (930 * scale, gy)], fill=GROUND, width=2 * scale)
        k0 = math.floor((cam - 2) * 8)
        for k in range(k0, k0 + 34):
            x = 480 * scale + (k / 8 - cam) * ppc
            if 30 * scale < x < 930 * scale:
                d.line([(x, gy), (x, gy + 10 * scale)], fill=GROUND, width=2 * scale)
                if k % 2:
                    continue
                for j in (-2, 0, 2):
                    yy = oy + 150 * scale + j * 0.3 * ppc_top
                    tx = 1440 * scale + (k / 8 - cam) * ppc_top
                    d.ellipse([tx - 3 * scale, yy - 3 * scale, tx + 3 * scale, yy + 3 * scale], fill=GROUND)
        # side view: far legs, body, near legs
        for n in ("FL", "HL"):
            line([side(p) for p in s["legs"][n]], FAR, 15)
        sh, hp = s["sh"], s["hp"]
        # proportions off the hero: rump 0.1 behind the hips, chest 0.45 ahead of the shoulders, head 0.75
        a, b = side((0, hp[1] - 0.1, hp[2] + 0.02)), side((0, sh[1] + 0.45, sh[2] + 0.04))
        line([a, b], BODY, 0.3 * ppc / scale)
        head = (0, sh[1] + 0.72 - s["head"]["dy"] / U_LAYER, sh[2] - 0.02 - 0.08 * (1 - s["head"]["k"]) / 0.07)
        line([b, side(head)], BODY, 0.22 * ppc / scale)
        d.ellipse([side(head)[0] - 0.13 * ppc, side(head)[1] - 0.11 * ppc, side(head)[0] + 0.13 * ppc,
                   side(head)[1] + 0.11 * ppc], fill=HOOF)
        for n in ("FR", "HR"):
            pts = [side(p) for p in s["legs"][n]]
            line(pts, NEAR, 17)
            hx, hy = pts[-1]
            d.rectangle([hx - 0.035 * ppc, hy - 0.03 * ppc, hx + 0.045 * ppc, hy], fill=HOOF)
        # top view: legs under the body, then the Brute itself at half opacity over them
        for n in FEET:
            pts = [top(p) for p in s["legs"][n]]
            line(pts, NEAR if n[1] == "R" else FAR, 12)
        for n in FEET:
            fx, fy, fz = s["feet"][n]
            p = top((fx, fy, fz))
            rad = (0.06 + 0.6 * fz) * ppc_top
            col = tuple(int(HOOF_AIR[i] + (HOOF[i] - HOOF_AIR[i]) * s["load"][n]) for i in range(3))
            d.ellipse([p[0] - rad, p[1] - rad, p[0] + rad, p[1] + rad], fill=col, outline=BG, width=scale)
        k = ppc_top / U_LAYER
        body = sway_warp(HERO_SMALL[scale], s, U_LAYER * k, row0=0, scale=k)
        body = body.rotate(-90, expand=True, resample=Image.BICUBIC)
        al = np.asarray(body).copy()
        al[..., 3] = (al[..., 3] * 0.55).astype(np.uint8)
        body = Image.fromarray(al, "RGBA")
        cx, cy = top((0, s["y"], 0))
        layer = img.convert("RGBA")
        layer.alpha_composite(body, (int(cx - body.width + ANCHOR_Y * k), int(cy - body.height / 2)))
        img.paste(layer.convert("RGB"))
        d = ImageDraw.Draw(img)
    img = img.resize((1920, 1080), Image.LANCZOS)
    d = ImageDraw.Draw(img)
    d.text((24, 10), "RAV BLOOM BRUTE  /  SKELETON  /  pig-cow legs under the body", font=font(24, True), fill=INK)
    d.text((24, 40), "Side view (left) and top view (right) of one skeleton per clip; the camera follows the body, so a "
                     "planted hoof holds still against the ground ticks. Top view: hooves bright when loaded, grey in "
                     "the air; the shoulders and hips sway toward the loaded side.", font=font(14), fill=DIM)
    for r, (cid, title) in enumerate(rows):
        s = frames_by_clip[cid][f]
        d.text((24, 76 + top_row + r * 336), f"{cid}  {title}", font=font(16, True), fill=ACC)
        d.text((990, 76 + top_row + r * 336), s["phase"], font=font(16, True), fill=INK)
    d.text((1760, 14), f"t = {f / FPS:4.2f} s", font=font(16, True), fill=DIM)
    return img


HERO_SMALL = {}


if __name__ == "__main__":
    from pathlib import Path
    from motion import save_gif
    hero = key(Path(__file__).parent / "motion-brute" / "raw" / "hero.png")
    k = 200 * 2 / U_LAYER
    HERO_SMALL[2] = hero.resize((int(hero.width * k), int(hero.height * k)), Image.LANCZOS)
    sims = {cid: simulate(cid) for cid, _ in CLIPS}
    stem = sys.argv[1][:-4] if sys.argv[1].endswith(".gif") else sys.argv[1]
    for row in CLIPS:   # one GIF per clip, each under the 8 MB preview limit
        frames = [draw(sims, f, rows=[row]) for f in range(int(T_END * FPS))]
        save_gif(frames, f"{stem}-{row[0]}.gif", FPS, accents=[ACC, HOOF, NEAR, FAR, BODY])
