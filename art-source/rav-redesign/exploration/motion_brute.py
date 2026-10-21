"""Brute motion study: the old plum mace-head Brute (round 3 #6) rigged from ImageGen layers, in
three clips side by side:

  A  walk: a heavy four-beat cow/pig walk at its sim speed, the legs under the body
  B  charge: the head drops, it breaks into a gallop and bowls a Marine aside
  C  wall: it walks up to a colony building, rears and rams the wall twice; the first blow cracks it,
     the second breaks a hole, and it walks in through the hole

  python motion_brute.py <render root> <out.gif> [<key frames.png> [<layers.png>]]

Rig: the hero (round 3 #6) itself is cut into a body, the head and four hooves, so at rest the composite
is the chosen design. Under it lies body-nohead.png (generated over the hero: a legless, headless body
with a neck stump), which fills whatever a moved hoof or a thrust head uncovers. head.png, leg-front.png
and leg-hind.png were generated for the rig too; the cut hero parts registered better, so they are kept
for production and not used here.

The legs stand under the body, so from above only the hooves and knees show at the body's edge. Feet
follow the Runner's planted-foot model (motion_runner.Gait, from the mechs' `MechGaitState`): a hoof
stays anchored in the world while it supports and steps when the body has carried it too far behind.
The walk is four-beat (left hind, left fore, right hind, right fore), the charge a rotary gallop.
What a camera overhead cannot see -- the legs swinging under the barrel -- is implied by what it can:
the hooves stepping at the edge, the body rising a little (drawn larger) as it vaults over a planted
leg, the shoulders rolling toward the supporting side, and the head nodding with the stride.

<render root> holds two colony-breach frames:
  brute-wall96/, brute-wall32/  commanderEvidence -Pmission=colony-breach -PmaxTicks=600 -PsnapshotEveryTicks=600
                       -PsnapshotWidth=1920 -PsnapshotHeight=1080 -PsnapshotAnnotations=false
                       -PsnapshotFocus=186,185,20 (and 186,184,60) -PevidenceDir=<root>/brute-wall96 (brute-wall32)
and rav-lz96/ for the Marine sprites (see motion_runner.py).
"""
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

from boards import font
from motion import save_gif
from motion_runner import marine_sprites
from rig import ease_out, smooth
from round2 import shadow_blob
from round_sheet import RUNNER_AREA_PX

RAW = Path(__file__).parent / "motion-brute" / "raw"
BG = (16, 20, 26)
INK = (226, 228, 232)
DIM = (150, 156, 166)
ACC = (240, 196, 120)
PPC17 = 1920 / 112
FPS = 20
T_END = 5.6
S = 0.4                          # working scale of the rig canvas against the 1024x1536 layers
PAD = 200
CW, CH = int((1024 + 2 * PAD) * S), int((1536 + 2 * PAD) * S)
BRUTE_LINEAR = 0.45 / 0.30       # UnitType radius against the Runner's
WALK, CHARGE = 1.8, 4.2          # cells per second: the sim's move speed, and a charge


def key(path):
    a = np.asarray(Image.open(path).convert("RGB")).astype(int)
    corners = np.concatenate([a[:20, :20].reshape(-1, 3), a[:20, -20:].reshape(-1, 3),
                              a[-20:, :20].reshape(-1, 3), a[-20:, -20:].reshape(-1, 3)])
    dist = np.sqrt(((a - np.median(corners, axis=0)) ** 2).sum(2))
    alpha = (np.clip((dist - 12) / 26, 0, 1) * 255).astype(np.uint8)
    return Image.fromarray(np.dstack([a.astype(np.uint8), alpha]), "RGBA")


def L(x, y):
    """Layer pixels to working-canvas pixels."""
    return ((x + PAD) * S, (y + PAD) * S)


def on_canvas(layer):
    c = Image.new("RGBA", (1024 + 2 * PAD, 1536 + 2 * PAD), (0, 0, 0, 0))
    c.alpha_composite(layer, (PAD, PAD))
    return c.resize((CW, CH), Image.LANCZOS)


def with_alpha(img, alpha):
    out = img.copy()
    out.putalpha(Image.fromarray(np.clip(alpha, 0, 255).astype(np.uint8)))
    return out


def scale_about(img, k, pivot):
    """Scale an image by k about a pivot, keeping its size."""
    px, py = pivot
    return img.transform(img.size, Image.AFFINE, (1 / k, 0, px - px / k, 0, 1 / k, py - py / k),
                         resample=Image.BICUBIC)


def largest_blob(img):
    """Keep only the largest opaque component (generations leave specks)."""
    a = np.asarray(img.getchannel("A")) > 60
    from collections import deque
    lab = np.zeros(a.shape, int)
    best, best_n, n = 0, 0, 0
    for y, x in zip(*np.nonzero(a[::4, ::4])):
        y, x = y * 4, x * 4
        if lab[y, x]:
            continue
        n += 1
        q, cnt = deque([(y, x)]), 0
        lab[y, x] = n
        while q:
            cy, cx = q.popleft()
            cnt += 1
            for ny, nx in ((cy + 1, cx), (cy - 1, cx), (cy, cx + 1), (cy, cx - 1)):
                if 0 <= ny < a.shape[0] and 0 <= nx < a.shape[1] and a[ny, nx] and not lab[ny, nx]:
                    lab[ny, nx] = n
                    q.append((ny, nx))
        if cnt > best_n:
            best, best_n = n, cnt
    keep = (lab == best)
    keep = np.asarray(Image.fromarray((keep * 255).astype(np.uint8)).filter(ImageFilter.MaxFilter(5))) > 0
    out = np.asarray(img).copy()
    out[..., 3] = (out[..., 3] * keep).astype(np.uint8)
    return Image.fromarray(out, "RGBA")


# ----------------------------------------------------------------------------- the rig
FEET = ["FL", "FR", "HL", "HR"]
HEAD_POLY = [(240, 0), (784, 0), (784, 300), (630, 330), (566, 384), (458, 384), (394, 330), (240, 300)]
HOOVES = {"FL": ((186, 790), (82, 72)), "HL": ((256, 1158), (76, 66))}   # centre, radii; right side mirrors


def soft_mask(size, draw, blur):
    m = Image.new("L", size, 0)
    draw(ImageDraw.Draw(m))
    return m.filter(ImageFilter.GaussianBlur(blur))


class Rig:
    """The hero, cut into a body, a head and four hooves, over the generated legless, headless body
    (body-nohead.png) as an underlay that fills whatever a moved hoof or head uncovers. At rest the
    composite is the hero."""

    def __init__(self):
        hero = key(RAW / "hero.png")
        size = hero.size
        self.hero_area = float((np.asarray(hero.getchannel("A")) > 128).sum()) * S * S  # on the rig canvas
        head_m = soft_mask(size, lambda d: d.polygon(HEAD_POLY, fill=255), 6)
        hoof_ms = {}
        for name in FEET:
            (cx, cy), (rx, ry) = HOOVES[name[0] + "L"]
            if name[1] == "R":
                cx = 1024 - cx
            hoof_ms[name] = (soft_mask(size, lambda d, cx=cx, cy=cy, rx=rx, ry=ry:
                                       d.ellipse([cx - rx, cy - ry, cx + rx, cy + ry], fill=255), 5), (cx, cy))
        ha = np.asarray(hero.getchannel("A")).astype(float)
        keep = 1 - np.asarray(head_m) / 255
        for m, _ in hoof_ms.values():
            keep *= 1 - np.asarray(m) / 255
        under = largest_blob(key(RAW / "body-nohead.png"))
        body = Image.new("RGBA", size, (0, 0, 0, 0))
        body.alpha_composite(under)
        body.alpha_composite(with_alpha(hero, ha * keep))
        self.body = on_canvas(body)
        self.head = on_canvas(with_alpha(hero, ha * np.asarray(head_m) / 255))
        self.neck = (512, 384)
        self.head_front = (512, 30)
        self.hooves = {}
        for name, (m, c) in hoof_ms.items():
            cut = with_alpha(hero, ha * np.asarray(m) / 255)
            box = m.point(lambda v: 255 if v > 4 else 0).getbbox()
            cut = cut.crop(box)
            cut = cut.resize((max(1, int(cut.width * S)), max(1, int(cut.height * S))), Image.LANCZOS)
            self.hooves[name] = (cut, ((c[0] - box[0]) * S, (c[1] - box[1]) * S), c)

    def rest(self, foot):
        return self.hooves[foot][2]

    def pose(self, feet, lifts, bob, roll, head):
        """feet: name -> hoof centre (layer px); lifts: name -> 0..1 swing height;
        bob: body rise (0 at rest; drawn larger when the body is high); roll: + toward the right side;
        head: dict(dy=offset in layer px along the body, negative forward; k=scale)."""
        c = Image.new("RGBA", (CW, CH), (0, 0, 0, 0))
        hooves = Image.new("RGBA", (CW, CH), (0, 0, 0, 0))
        for name in ("HL", "HR", "FL", "FR"):
            img, (ox, oy), _ = self.hooves[name]
            lift = lifts.get(name, 0.0)
            k = 1 + 0.14 * lift
            if lift > 0.01:
                img = img.resize((max(1, int(img.width * k)), max(1, int(img.height * k))), Image.LANCZOS)
            x, y = feet[name]
            x += (10 if name[1] == "R" else -10) * lift          # a lifted hoof swings a little wide
            px, py = L(x, y)
            hooves.alpha_composite(img, (int(px - ox * k), int(py - oy * k)))
        body = self.body
        k = 1 + 0.025 * bob
        if abs(k - 1) > 1e-3:
            body = scale_about(body, k, L(512, 760))
        sway = (roll * 5 * S, 0)
        if abs(roll) > 0.01:
            body = body.rotate(-1.2 * roll, center=L(512, 760), resample=Image.BICUBIC, translate=sway)
        c.alpha_composite(body)
        c.alpha_composite(hooves)        # hoof cuts sit where the hero drew them: over the flank's edge
        h = self.head
        pivot = L(*self.neck)
        hk = head.get("k", 1.0) * k
        if abs(hk - 1) > 1e-3:
            h = scale_about(h, hk, pivot)
        h = h.rotate(-1.2 * roll, center=pivot, resample=Image.BICUBIC,
                     translate=(sway[0], head.get("dy", 0.0) * S))
        c.alpha_composite(h)
        return c


# ----------------------------------------------------------------------------- planted-foot gait


REACH = 0.16   # cells a hoof may sit ahead of or behind its hip's rest spot


class Gait:
    """World-anchored hooves (cells along the path), stepping when the body leaves one behind."""

    SEQ = {"walk": ["HL", "FL", "HR", "FR"], "gallop": ["HL", "HR", "FL", "FR"]}

    def __init__(self, rig, U, x):
        self.rig, self.U = rig, U
        self.rest_off = {f: (760 - rig.rest(f)[1]) / U for f in FEET}
        self.anchor = {f: x + self.rest_off[f] for f in FEET}
        self.swing = {}
        self.next = 0
        self.mode = "walk"

    def step(self, x, v, dt, mode, frozen):
        if mode != self.mode:
            self.mode, self.next = mode, 0
        th, ahead, dur, overlap = {"walk": (0.08, 0.10, 0.24, 0.25), "gallop": (0.06, 0.14, 0.14, 0.2)}[mode]
        for f, sw in list(self.swing.items()):
            sw["k"] += dt / sw["dur"]
            if sw["k"] >= 1:
                self.anchor[f] = sw["target"](x)
                del self.swing[f]
        if frozen or abs(v) < 0.05:
            return
        # one foot leaves at a time, in sequence; the next may lift once the last is `overlap` through its swing
        if any(sw["k"] < overlap for sw in self.swing.values()):
            return
        f = self.SEQ[mode][self.next % 4]
        if f in self.swing:
            return
        if (x + self.rest_off[f]) - self.anchor[f] > th:
            off = self.rest_off[f]
            self.swing[f] = dict(k=0.0, dur=dur, start=self.anchor[f], target=lambda xx, off=off: xx + off + ahead)
            self.next += 1

    def feet(self, x):
        pos, lifts = {}, {}
        for f in FEET:
            rx, ry = self.rig.rest(f)
            if f in self.swing:
                sw = self.swing[f]
                k = smooth(min(1, sw["k"]))
                fw = sw["start"] + (sw["target"](x) - sw["start"]) * k
                lifts[f] = math.sin(math.pi * min(1, sw["k"]))
            else:
                fw = self.anchor[f]
                lifts[f] = 0.0
            rel = max(-REACH, min(REACH, fw - (x + self.rest_off[f])))   # a hoof stays within a leg's reach
            pos[f] = (rx, ry - rel * self.U)
        return pos, lifts

    def body_motion(self, lifts):
        """Bob, roll and nod read off the swinging feet: the body rises as it vaults over a planted leg,
        rolls toward the side still supporting, and the head nods down as a fore hoof lands."""
        swing = sum(lifts.values())
        bob = 0.5 - 0.5 * math.cos(math.pi * min(1, swing))
        roll = sum(l for f, l in lifts.items() if f[1] == "L") - sum(l for f, l in lifts.items() if f[1] == "R")
        nod = lifts.get("FL", 0) + lifts.get("FR", 0)
        return bob, roll, nod


# ----------------------------------------------------------------------------- the three clips
CLIPS = [
    ("A", "walk: four-beat, legs under the body"),
    ("B", "charge: head down, gallop, bowls a Marine aside"),
    ("C", "wall: rears and rams twice, then walks through"),
]
T_DROP, T_GALLOP, T_HIT_B = 0.9, 1.25, 2.35
WALL_STOP = 2.4                    # clip C arrives at the wall
BLOWS = [(2.55, 0.0), (3.45, 0.0)]  # wind-up start of each ram
WIND, SLAM, HOLD = 0.32, 0.07, 0.25
T_THROUGH = 4.25


def ram_e(t, t0):
    """Body offset (cells along the path) and head pose over one ram starting at t0."""
    u = t - t0
    if u < 0 or u > WIND + SLAM + HOLD + 0.35:
        return 0.0, dict(dy=0.0, k=1.0), False
    if u < WIND:   # rear back, head up
        s = smooth(u / WIND)
        return -0.16 * s, dict(dy=40 * s, k=1 + 0.07 * s), False
    if u < WIND + SLAM:  # slam: head down and through
        s = ease_out((u - WIND) / SLAM)
        return -0.16 + 0.36 * s, dict(dy=40 - 110 * s, k=1.07 - 0.12 * s), False
    if u < WIND + SLAM + HOLD:
        return 0.2, dict(dy=-70, k=0.95), True
    s = smooth((u - WIND - SLAM - HOLD) / 0.35)
    return 0.2 - 0.2 * s, dict(dy=-70 * (1 - s), k=0.95 + 0.05 * s), True


def body_x(clip, t):
    """Position along the path (cells), speed, gait mode, whether feet are frozen, head pose, phase."""
    head = dict(dy=0.0, k=1.0)
    if clip == "A":
        return WALK * t, WALK, "walk", False, head, "walk"
    if clip == "B":
        if t < T_GALLOP:
            x, v, mode, phase = WALK * t, WALK, "walk", "walk"
            if t > T_DROP:
                s = smooth((t - T_DROP) / (T_GALLOP - T_DROP))
                head, phase = dict(dy=-45 * s, k=1 - 0.07 * s), "head down"
            return x, v, mode, False, head, phase
        x0 = WALK * T_GALLOP
        u = t - T_GALLOP
        acc = (CHARGE - WALK) / 0.5
        if u < 0.5:
            x, v = x0 + WALK * u + 0.5 * acc * u * u, WALK + acc * u
        else:
            x, v = x0 + WALK * 0.5 + 0.5 * acc * 0.25 + CHARGE * (u - 0.5), CHARGE
        phase = "charge"
        if t > T_HIT_B + 0.25:   # pull up after bowling through
            uu = t - T_HIT_B - 0.25
            dec = min(uu, 0.6)
            xs = x0 + WALK * 0.5 + 0.5 * acc * 0.25 + CHARGE * (T_HIT_B + 0.25 - T_GALLOP - 0.5)
            x = xs + CHARGE * dec - CHARGE / 1.2 * dec * dec
            v = max(0.0, CHARGE - CHARGE / 0.6 * dec)
            phase = "pull up"
        return x, v, ("gallop" if v > 2.4 else "walk"), False, dict(dy=-45, k=0.93), phase
    # C: walk to the wall, ram twice, walk through
    if t < WALL_STOP:
        return WALK * t, WALK, "walk", False, head, "walk"
    x = WALK * WALL_STOP
    if t < T_THROUGH:
        for i, (t0, _) in enumerate(BLOWS):
            if t0 <= t < t0 + WIND + SLAM + HOLD + 0.35:
                off, head, _ = ram_e(t, t0)
                phase = "rear" if t - t0 < WIND else ("RAM" if t - t0 < WIND + SLAM + HOLD else "recover")
                return x + off, 0.0, "walk", True, head, f"{phase} {i + 1}"
        return x, 0.0, "walk", True, head, "brace"
    return x + WALK * 0.8 * (t - T_THROUGH), WALK * 0.8, "walk", False, head, "through the hole"


def simulate(rig, clip, U, n):
    x, *_ = body_x(clip, 0)
    gait = Gait(rig, U, x)
    out = []
    for f in range(n):
        t = f / FPS
        x, v, mode, frozen, head, phase = body_x(clip, t)
        gait.step(x, v, 1 / FPS, mode, frozen)
        feet, lifts = gait.feet(x)
        bob, roll, nod = gait.body_motion(lifts)
        h = dict(head)
        amp = 18 if mode == "walk" else 10
        h["dy"] = h.get("dy", 0.0) + amp * nod      # a nod forward as a fore hoof reaches
        out.append((t, x, phase, feet, lifts, bob * (0.6 if mode == "walk" else 1.0), roll, h))
    return out


# ----------------------------------------------------------------------------- world, wall and drawing
# World cells are map cells; every scene runs along a row in the -x direction, toward a building's east wall.
WALL_X = 185.0           # outer face of the east wall (cell column 184) of the house at the top left of brute-wall96
PATH_Y = 181.8           # clip C's row, against that wall
SCENE = {"A": (191.6, 183.65),   # start x, row: the dirt strip south of the house
         "B": (192.8, 186.1),   # across the road toward the stone yard
         "C": (None, PATH_Y)}
CENTRE = {"A": 186.6, "B": 188.0, "C": 188.0}   # fixed 96 px windows (cells), so the GIF stays small


def frame_path(root, name):
    d = Path(root) / name / "visuals" / "colony-breach" / "frames"
    return sorted(d.glob("frame-*.png"))[0]


FRAMES = {96: ("brute-wall96", (186, 185)), 32: ("brute-wall32", (186, 184))}


class World:
    def __init__(self, root):
        self.g = {p: Image.open(frame_path(root, name)).convert("RGBA") for p, (name, _) in FRAMES.items()}

    @staticmethod
    def px(ppc, w):
        cx, cy = FRAMES[ppc][1]
        return 960 + (w[0] - cx) * ppc, 540 + (w[1] - cy) * ppc


class Clip:
    def __init__(self, rig, cid, U, n):
        self.rig, self.cid, self.U = rig, cid, U
        self.frames = simulate(rig, cid, U, n)
        x_stop = WALK * WALL_STOP
        nose = (760 - rig.head_front[1]) / U       # cells from the body anchor to the front of the head
        # every clip starts at the same distance; C's path ends with the head just short of the wall
        self.x0, self.y = SCENE[cid]
        if self.x0 is None:
            self.x0 = WALL_X + 0.3 + nose + x_stop
        if cid == "B":
            hx = self.x0 - body_x("B", T_HIT_B)[0] - nose
            self.victim = (hx, self.y + 0.1)

    def world(self, x):
        return (self.x0 - x, self.y)

    def draw_body(self, tile, f, ppc, origin, sc):
        t, x, phase, feet, lifts, bob, roll, head = self.frames[f]
        body = self.rig.pose(feet, lifts, bob, roll, head)
        body = body.resize((max(1, int(CW * sc)), max(1, int(CH * sc))), Image.LANCZOS).rotate(
            90, expand=True, resample=Image.BICUBIC)       # head up -> head toward -x
        ax, ay = L(512, 760)
        anc = (ay * sc, (CW - ax) * sc)
        bx, by = self.world(x)
        cx, cy = origin[0] + bx * ppc, origin[1] + by * ppc
        sh, (ox, oy) = shadow_blob(ppc, 0.45)
        tile.alpha_composite(sh, (int(cx + ox - sh.width / 2), int(cy + oy - sh.height / 2)))
        tile.alpha_composite(body, (int(cx - anc[0]), int(cy - anc[1])))
        return phase


def wall_damage(tile, ppc, origin, t):
    """Cracks after the first ram, a hole and rubble after the second, with dust and flying chunks."""
    hit1 = BLOWS[0][0] + WIND + SLAM
    hit2 = BLOWS[1][0] + WIND + SLAM
    if t < hit1:
        return
    d = ImageDraw.Draw(tile)
    cx, cy = origin[0] + WALL_X * ppc, origin[1] + PATH_Y * ppc
    rng = np.random.default_rng(7)
    w = max(1, int(0.05 * ppc))
    for i in range(7):   # cracks radiating along the wall face
        a = rng.uniform(-1.3, 1.3)
        r = rng.uniform(0.25, 0.6) * ppc
        d.line([(cx, cy), (cx - 0.15 * ppc * rng.uniform(0, 1), cy + r * math.sin(a))], fill=(30, 26, 24, 255), width=w)
    wc = tile.crop((int(cx - 0.6 * ppc), int(cy - 1.2 * ppc), int(cx - 0.4 * ppc), int(cy - 1.0 * ppc))).convert("RGB")
    wall_c = tuple(int(v) for v in np.asarray(wc).reshape(-1, 3).mean(0))
    if t >= hit2:
        # a hole one cell wide through the wall, rubble in front and inside
        x0, x1 = cx - 1.0 * ppc, cx + 0.05 * ppc
        y0, y1 = cy - 0.55 * ppc, cy + 0.55 * ppc
        floor = tile.crop((int(x0 - 0.4 * ppc), int(cy - 0.6 * ppc), int(x0 - 0.2 * ppc), int(cy - 0.5 * ppc))).convert("RGB")
        fc = tuple(int(v) for v in np.asarray(floor).reshape(-1, 3).mean(0))
        d.rectangle([x0, y0, x1, y1], fill=fc + (255,))
        # broken ends of the wall either side of the hole
        for ey in (y0, y1):
            for k in range(6):
                bx_ = x0 + (k + rng.uniform(0, 0.6)) * (x1 - x0) / 6
                d.polygon([(bx_, ey), (bx_ + 0.08 * ppc, ey), (bx_ + 0.04 * ppc, ey + (1 if ey == y0 else -1)
                           * rng.uniform(0.05, 0.16) * ppc)], fill=wall_c + (255,))
        for i in range(34):   # rubble: chunks of the wall's own brick, darker underneath
            rx = cx + rng.uniform(-1.1, 0.6) * ppc
            ry = cy + rng.normal(0, 0.42) * ppc
            s = rng.uniform(0.04, 0.12) * ppc
            sh = rng.uniform(0.6, 1.0)
            pts = [(rx + s * rng.uniform(0.7, 1.2) * math.cos(k * 1.26 + i), ry + s * rng.uniform(0.7, 1.2)
                    * math.sin(k * 1.26 + i)) for k in range(5)]
            d.polygon([(px_ + 0.02 * ppc, py_ + 0.02 * ppc) for px_, py_ in pts], fill=(40, 30, 24, 160))
            d.polygon(pts, fill=tuple(int(c * sh) for c in wall_c) + (255,))
    for hit, big in ((hit1, 0.6), (hit2, 1.2)):  # dust and chunks
        u = t - hit
        if 0 <= u < 0.9:
            dust = Image.new("RGBA", tile.size, (0, 0, 0, 0))
            dd = ImageDraw.Draw(dust)
            for i in range(9):
                a = -math.pi / 2 + (i - 4) * 0.35
                r = (0.2 + 0.9 * ease_out(u / 0.9)) * big * ppc
                px_, py_ = cx + 0.4 * r * math.cos(a) + 0.3 * r, cy + r * math.sin(a) * 1.2
                rad = (0.25 + 0.5 * u) * big * ppc
                al = int(150 * (1 - u / 0.9))
                dd.ellipse([px_ - rad, py_ - rad, px_ + rad, py_ + rad], fill=(176, 168, 156, al))
            tile.alpha_composite(dust.filter(ImageFilter.GaussianBlur(0.15 * ppc)))
            if u < 0.4:
                for i in range(10):
                    a = rng.uniform(-1.4, 1.4)
                    r = ease_out(u / 0.4) * rng.uniform(0.4, 1.3) * big * ppc
                    px_, py_ = cx + r * math.cos(a) * 0.8 + 0.1 * ppc, cy + r * math.sin(a)
                    s = rng.uniform(0.04, 0.1) * ppc
                    d.rectangle([px_ - s, py_ - s, px_ + s, py_ + s], fill=(150, 136, 120, 255))


def draw_victim(tile, clip, t, ppc, origin, marines):
    mk = ppc / 96

    def small(m, dim=1.0):
        m = m.convert("RGBa").resize((max(1, round(m.width * mk)), max(1, round(m.height * mk))),
                                     Image.LANCZOS).convert("RGBA")
        if dim != 1.0:
            m = Image.fromarray((np.asarray(m).astype(float) * [dim, dim, dim, 1]).astype(np.uint8), "RGBA")
        return m

    def put(img, w, angle=0.0):
        cx, cy = origin[0] + w[0] * ppc, origin[1] + w[1] * ppc
        sh, (ox, oy) = shadow_blob(ppc, 0.25)
        tile.alpha_composite(sh, (int(cx + ox - sh.width / 2), int(cy + oy - sh.height / 2)))
        if angle:
            img = img.rotate(angle, expand=True, resample=Image.BICUBIC)
        tile.alpha_composite(img, (int(cx - img.width / 2), int(cy - img.height / 2)))

    vx, vy = clip.victim
    if t < T_HIT_B:
        put(small(marines[0]), (vx, vy))
    else:   # bowled aside: thrown off the line, spinning, then down
        k = ease_out(min(1, (t - T_HIT_B) / 0.45))
        put(small(marines[0], 0.8), (vx - 0.9 * k, vy - 1.4 * k), 200 * k)


def build(root, out):
    rig = Rig()
    world = World(root)
    marines = marine_sprites(root)
    sc = {p: BRUTE_LINEAR * (RUNNER_AREA_PX * (p / PPC17) ** 2 / rig.hero_area) ** 0.5 for p in (96, 32)}
    U = 96 / (S * sc[96])   # layer pixels per cell
    n = int(T_END * FPS)
    clips = [Clip(rig, cid, U, n) for cid, _ in CLIPS]
    W96, H96 = 944, 404
    W32, H32 = 944, 126
    frames = []
    for f in range(n):
        t = f / FPS
        img = Image.new("RGB", (1920, 1080), BG)
        d = ImageDraw.Draw(img)
        d.text((24, 10), "RAV BLOOM BRUTE  /  MOTION STUDY  /  mace head, legs under the body",
               font=font(24, True), fill=INK)
        d.text((24, 40), "Real time. 96 px per cell in the three large panels; 32 px per cell on the right. Hooves "
                         "step at the body's edge; the body rises, rolls and the head nods with the stride.",
               font=font(14), fill=DIM)
        for i, ((cid, title), clip) in enumerate(zip(CLIPS, clips)):
            gx, gy = 12 + (i % 2) * (W96 + 8), 66 + (i // 2) * (H96 + 34)
            ccx, ccy = World.px(96, (CENTRE[cid], clip.y))
            box = (int(ccx - W96 / 2), int(ccy - H96 / 2))
            g = world.g[96].copy()
            origin = (-(FRAMES[96][1][0] * 96) + 960, -(FRAMES[96][1][1] * 96) + 540)
            if cid == "C":
                wall_damage(g, 96, origin, t)
            if cid == "B":
                draw_victim(g, clip, t, 96, origin, marines)
            phase = clip.draw_body(g, f, 96, origin, sc[96])
            img.paste(g.crop((box[0], box[1], box[0] + W96, box[1] + H96)).convert("RGB"), (gx, gy + 26))
            d.text((gx, gy + 2), f"{cid}  {title}", font=font(16, True), fill=ACC)
            d.text((gx + W96 - 190, gy + 4), phase, font=font(15, True), fill=INK)
            # 32 px row, right-hand column under B
            sx, sy = 12 + W96 + 8, 66 + H96 + 34 + i * (H32 + 26)
            g32 = world.g[32].copy()
            o32 = (-(FRAMES[32][1][0] * 32) + 960, -(FRAMES[32][1][1] * 32) + 540)
            if cid == "C":
                wall_damage(g32, 32, o32, t)
            if cid == "B":
                draw_victim(g32, clip, t, 32, o32, marines)
            clip.draw_body(g32, f, 32, o32, sc[32])
            c32 = World.px(32, (WALL_X + 6.5, clip.y))
            b32 = (int(c32[0] - W32 / 2), int(c32[1] - H32 / 2))
            img.paste(g32.crop((b32[0], b32[1], b32[0] + W32, b32[1] + H32)).convert("RGB"), (sx, sy + 20))
            d.text((sx, sy + 2), f"{cid} at 32 px per cell", font=font(13), fill=DIM)
        d.text((1700, 14), f"t = {t:4.2f} s", font=font(16, True), fill=DIM)
        frames.append(img)
    save_gif(frames, out, FPS, accents=[(255, 168, 54), (120, 60, 110), (150, 40, 90), (236, 226, 206)])
    return frames, clips


def key_sheet(frames, clips, out):
    picks = [(0, 1.2, "walk"), (0, 1.4, "walk"), (1, 1.7, "head down, gallop"), (1, 2.4, "bowls through"),
             (2, 2.85, "rear"), (2, 2.95, "ram 1: cracks"), (2, 3.85, "ram 2: wall breaks"), (2, 5.0, "through")]
    sheet = Image.new("RGB", (1920, 1080), BG)
    d = ImageDraw.Draw(sheet)
    d.text((24, 10), "BRUTE MOTION STUDY  /  key moments at 96 px per cell, 1:1", font=font(22, True), fill=INK)
    for i, (clip, tt, lab) in enumerate(picks):
        fi = int(round(tt * FPS))
        fr = frames[fi]
        gx, gy = 12 + (clip % 2) * 952, 66 + (clip // 2) * 438
        c = clips[clip]
        bx = c.world(c.frames[fi][1])[0]
        tx = int((bx - CENTRE[c.cid]) * 96 + 472 - 230 - 30)    # centred a little ahead of the Brute
        tx = max(0, min(944 - 460, tx))
        crop = fr.crop((gx + tx, gy + 26 + 2, gx + tx + 460, gy + 26 + 2 + 400))
        x, y = 12 + (i % 4) * 476, 60 + (i // 4) * 506
        sheet.paste(crop, (x, y + 26))
        d.text((x, y + 2), f"t = {tt:.2f} s  {lab}", font=font(15, True), fill=ACC)
    sheet.save(out)


def layers_sheet(out):
    sheet = Image.new("RGB", (1920, 1080), BG)
    d = ImageDraw.Draw(sheet)
    d.text((24, 16), "BRUTE  /  design and rig layers", font=font(24, True), fill=INK)
    names = [("hero", "design: round 3 #6"), ("body-nohead", "layer: body"), ("head", "layer: mace head"),
             ("leg-front", "layer: front leg (from above)"), ("leg-hind", "layer: hind leg (from above)")]
    for i, (n_, lab) in enumerate(names):
        im = Image.open(RAW / f"{n_}.png").convert("RGB")
        k = min(370 / im.width, 940 / im.height)
        im = im.resize((int(im.width * k), int(im.height * k)), Image.LANCZOS)
        sheet.paste(im, (16 + i * 380, 64))
        d.text((20 + i * 380, 74 + im.height), lab, font=font(14, True), fill=ACC)
    sheet.save(out)


if __name__ == "__main__":
    fr, cl = build(sys.argv[1], sys.argv[2])
    if len(sys.argv) > 3:
        key_sheet(fr, cl, sys.argv[3])
    if len(sys.argv) > 4:
        layers_sheet(sys.argv[4])
