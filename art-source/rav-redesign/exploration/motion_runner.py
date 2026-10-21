"""Runner motion study: the lilac Runner rigged from ImageGen layers, with jointed IK legs on a
planted-foot gait, showing its four attacks side by side:

  A  one blade impales, reels the victim back to the jaws, and the Runner drags it off
  B  both blades impale, reel in, drag off
  C  chomp: a lunging bite, then mauling the downed Marine
  D  forearm swipes: a clawed front leg lashes across, then the other

  python motion_runner.py <render root> <out.gif> [<key frames.png> [<layers.png>]]

Layers (motion-runner/raw/, generated over hero.png and body-closed.png as geometry authority):
  body-nolegs.png  torso, head and tail with jaws shut and no legs (front-paw stubs masked out here)
  body-gape.png    the jaws gaping (only its head is used)
  blade.png        one arm blade, root at the bottom, pointing up (shortened here)
  leg-front.png, leg-hind.png  one straight leg each, hip at the top, split at the knee here

Legs are two-bone IK chains. Feet follow the planted-foot model of the mechs' `MechGaitState`: a
foot is anchored in world space while it supports, and steps when the body has carried it too far
behind; the run is a bound (front pair, then hind pair, with flight), the drag a backward trot, and
during a strike no foot steps, so the legs brace and stretch as the body lunges.

<render root> holds colony-breach close-ups at the LZ on one focus (commanderEvidence
-Pmission=colony-breach -PmaxTicks=600 -PsnapshotEveryTicks=600 -PsnapshotWidth=1920
-PsnapshotHeight=1080 -PsnapshotAnnotations=false and):
  rav-lz96/  -PsnapshotFocus=197,195,20    96 px per cell
  rav-lz32/  -PsnapshotFocus=197,195,60    32 px per cell
Ground is the tick-0 frame. Marines are cut from the 96 px frame at tick 600 with their cyan review
markers painted out, and scaled down for the 32 px row.
"""
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageOps

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
PADX, PADT, PADB = 260, 420, 60
CW, CH = int((1024 + 2 * PADX) * S), int((1536 + PADT + PADB) * S)
ANCHOR = (512, 760)          # body centre in layer pixels
JAW = (512, 20)              # front of the jaws

T_RUN, T_STRIKE, T_END = 1.0, 0.6, 4.4
RUN_SPEED, DRAG_SPEED = 3.0, 0.7

# ----------------------------------------------------------------------------- layer helpers


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


def poly_mask(pts, blur=0):
    m = Image.new("L", (CW, CH), 0)
    ImageDraw.Draw(m).polygon([L(x, y) for x, y in pts], fill=255)
    return m.filter(ImageFilter.GaussianBlur(blur * S)) if blur else m


def with_alpha(img, alpha):
    out = img.copy()
    out.putalpha(Image.fromarray(alpha.astype(np.uint8)))
    return out


def mirror_pts(pts):
    return [(1024 - x, y) for x, y in pts]


def warp(img, dx, dy):
    """Inverse-map an RGBA image through a displacement field (premultiplied bilinear)."""
    a = np.asarray(img).astype(float)
    a[..., :3] *= a[..., 3:4] / 255
    yy, xx = np.mgrid[0:CH, 0:CW].astype(float)
    sx = np.clip(xx - dx, 0, CW - 1.001)
    sy = np.clip(yy - dy, 0, CH - 1.001)
    x0, y0 = sx.astype(int), sy.astype(int)
    fx, fy = (sx - x0)[..., None], (sy - y0)[..., None]
    out = (a[y0, x0] * (1 - fx) * (1 - fy) + a[y0, x0 + 1] * fx * (1 - fy)
           + a[y0 + 1, x0] * (1 - fx) * fy + a[y0 + 1, x0 + 1] * fx * fy)
    al = out[..., 3:4]
    out[..., :3] = np.where(al > 0, out[..., :3] * 255 / np.maximum(al, 1e-6), 0)
    return Image.fromarray(np.clip(out, 0, 255).astype(np.uint8), "RGBA")


LEG_WIDEN = 1.9  # the generated legs are drawn slender; the body wants muscled limbs


def segment(path, top, joint, bottom, length):
    """Cut a straight limb image into two segments at its joint, scaled so the upper one spans `length`
    layer pixels joint to joint. Returns [(image, (root x, root y), length)] for upper and lower."""
    im = key(path)
    a = np.asarray(im.getchannel("A")) > 128
    xs = np.nonzero(a.any(0))[0]
    cx = (xs.min() + xs.max()) / 2
    k = length / (joint - top) * S
    out = []
    for y0, y1, j0, j1 in ((top - 60, joint + 40, top, joint), (joint - 40, bottom + 120, joint, bottom)):
        c = im.crop((0, max(0, y0), 1024, min(1536, y1)))
        c = c.resize((max(1, int(c.width * k * LEG_WIDEN)), max(1, int(c.height * k))), Image.LANCZOS)
        out.append((c, (cx * k * LEG_WIDEN, (j0 - max(0, y0)) * k), (j1 - j0) * k / S))
    return out


def place_segment(canvas, seg, p0, p1, lift=1.0):
    """Draw a segment whose root sits at p0 (canvas px) and whose far joint points at p1."""
    img, (rx, ry), _ = seg
    if lift != 1.0:
        img = img.resize((max(1, int(img.width * lift)), max(1, int(img.height * lift))), Image.LANCZOS)
        rx, ry = rx * lift, ry * lift
    phi = math.degrees(math.atan2(p1[0] - p0[0], p1[1] - p0[1]))
    layer = Image.new("RGBA", (CW, CH), (0, 0, 0, 0))
    layer.alpha_composite(img, (int(p0[0] - rx), int(p0[1] - ry)))
    canvas.alpha_composite(layer.rotate(phi, center=p0, resample=Image.BICUBIC))


def ik(hip, foot, a, b, outward):
    """Two-bone IK in the plane: the knee for a hip at `hip` reaching `foot`, bent toward `outward` (+1 right)."""
    dx, dy = foot[0] - hip[0], foot[1] - hip[1]
    d = max(abs(a - b) + 1e-3, min(a + b - 1e-3, math.hypot(dx, dy)))
    base = math.atan2(dy, dx)
    off = math.acos(max(-1, min(1, (a * a + d * d - b * b) / (2 * a * d))))
    cands = [(hip[0] + a * math.cos(base + s * off), hip[1] + a * math.sin(base + s * off)) for s in (1, -1)]
    knee = max(cands, key=lambda k: outward * (k[0] - hip[0]))
    reach = (hip[0] + d * math.cos(base), hip[1] + d * math.sin(base))
    return knee, reach


# ----------------------------------------------------------------------------- the rig
# legs, left side in layer pixels (the right side mirrors): hip, rest foot, image spans
FRONT = dict(hip=(322, 600), rest=(190, 700), img="leg-front.png", top=110, joint=760, bottom=1350, a=150)
HIND = dict(hip=(352, 840), rest=(206, 1030), img="leg-hind.png", top=110, joint=740, bottom=1360, a=180)
FEET = ["FL", "FR", "HL", "HR"]
BLADE_IVORY = 330                                  # shortened from 560 in the first study
BLADE_POSES = {  # right blade: angle (deg clockwise from the head) and root
    "rest": (150.0, (650, 300)),
    "coil": (174.0, (688, 350)),
    "ext": (-10.0, (612, 236)),
    "reel": (-10.0, (604, 400)),
}


class Rig:
    def __init__(self):
        body = on_canvas(key(RAW / "body-nolegs.png"))
        a = np.asarray(body.getchannel("A")).astype(float)
        for pts in ([(0, 520), (318, 520), (318, 830), (0, 830)],):  # front-paw stubs the generation kept
            for p in (pts, mirror_pts(pts)):
                a *= 1 - np.asarray(poly_mask(p, 6)).astype(float) / 255
        self.body = with_alpha(body, a)
        gape = on_canvas(key(RAW / "body-gape.png"))
        head = np.zeros((CH, CW))
        y0, y1 = L(0, 250)[1], L(0, 330)[1]
        yy, xx = np.mgrid[0:CH, 0:CW]
        head = np.clip((y1 - yy) / (y1 - y0), 0, 1) * ((xx > L(290, 0)[0]) & (xx < L(734, 0)[0]))
        self.gape_head = with_alpha(gape, np.asarray(gape.getchannel("A")) * head)
        tail = np.asarray(poly_mask([(400, 930), (624, 930), (624, 1536), (400, 1536)], 30)).astype(float) / 255
        self.tail_w = tail * np.clip((yy - L(0, 930)[1]) / (520 * S), 0, 1) ** 1.5
        self.legs = {}
        for name, spec in (("F", FRONT), ("H", HIND)):
            segs = segment(RAW / spec["img"], spec["top"], spec["joint"], spec["bottom"], spec["a"])
            self.legs[name] = (segs, [ImageOps.mirror(s[0]) for s in segs])
        b = key(RAW / "blade.png")
        x0, y0b, x1, y1b = b.getchannel("A").point(lambda v: 255 if v > 60 else 0).getbbox()
        h = y1b - y0b
        b = b.crop((x0, y0b, x1, y0b + int(0.6 * h)))
        k = BLADE_IVORY / (0.45 * h) * S
        b = b.resize((max(1, int(b.width * k)), max(1, int(b.height * k))), Image.LANCZOS)
        rows = np.asarray(b.getchannel("A"))[-12:] > 128
        root_x = float(np.nonzero(rows.any(0))[0].mean())
        self.blade_len = (b.height - 4) / S
        self.blade = (b, (root_x, b.height - 4))
        self.blade_l = (ImageOps.mirror(b), (b.width - root_x, b.height - 4))
        self.hero_area = float((np.asarray(on_canvas(key(RAW / "hero.png")).getchannel("A")) > 128).sum())

    @staticmethod
    def hip(foot):
        spec = FRONT if foot[0] == "F" else HIND
        x, y = spec["hip"]
        return (x, y) if foot[1] == "L" else (1024 - x, y)

    @staticmethod
    def rest(foot):
        spec = FRONT if foot[0] == "F" else HIND
        x, y = spec["rest"]
        return (x, y) if foot[1] == "L" else (1024 - x, y)

    def blade_tip(self, angle, root, side):
        """Tip of a blade in layer pixels; side +1 right, -1 left (mirrored)."""
        x, y = root
        ax = 512 + side * (x - 512)
        r = math.radians(angle)
        return ax + side * self.blade_len * math.sin(r), y - self.blade_len * math.cos(r)

    def pose(self, feet, lifts, tail, gape, blades):
        """feet: name -> foot position in layer px; lifts: name -> scale (>1 while the foot is raised);
        blades: {+1: (angle, root), -1: (angle, root)} for the right and left blade."""
        c = Image.new("RGBA", (CW, CH), (0, 0, 0, 0))
        for name in ("HL", "HR", "FL", "FR"):
            segs, msegs = self.legs[name[0]]
            side = -1 if name[1] == "L" else 1
            upper, lower = segs
            a, b = upper[2], lower[2]
            hip = self.hip(name)
            knee, reach = ik(hip, feet[name], a, b, side)
            lift = lifts.get(name, 1.0)
            for seg, mimg, p0, p1 in ((upper, msegs[0], hip, knee), (lower, msegs[1], knee, reach)):
                img = seg if side < 0 else (mimg, (mimg.width - seg[1][0], seg[1][1]), seg[2])
                place_segment(c, img, L(*p0), L(*p1), lift if seg is lower else 1 + (lift - 1) * 0.5)
        body = self.body.copy()
        if gape > 0.01:
            g = self.gape_head.copy()
            g.putalpha(g.getchannel("A").point(lambda v: int(v * min(1, gape))))
            body.alpha_composite(g)
        if abs(tail) > 0.01:
            body = warp(body, self.tail_w * (tail * 9 * S), np.zeros((CH, CW)))
        c.alpha_composite(body)
        for side, ((img, root), (angle, piv)) in ((1, (self.blade, blades[1])), (-1, (self.blade_l, blades[-1]))):
            pivot = L(512 + side * (piv[0] - 512), piv[1])
            layer = Image.new("RGBA", (CW, CH), (0, 0, 0, 0))
            layer.alpha_composite(img, (int(pivot[0] - root[0]), int(pivot[1] - root[1])))
            c.alpha_composite(layer.rotate(-side * angle, center=pivot, resample=Image.BICUBIC))
        return c


# ----------------------------------------------------------------------------- planted-foot gait
class Gait:
    """World-anchored feet (cells along the approach), stepping when the body leaves one behind."""

    def __init__(self, rig, U, x):
        self.U = U
        self.rest_off = {f: (ANCHOR[1] - Rig.rest(f)[1]) / U for f in FEET}
        self.anchor = {f: x + self.rest_off[f] for f in FEET}
        self.swing = {}
        self.override = {}

    def step(self, x, v, dt, mode, frozen):
        groups = {"bound": [("FL", "FR"), ("HL", "HR")], "trot": [("FL", "HR"), ("FR", "HL")]}[mode]
        th, ahead, dur = {"bound": (0.09, 0.08, 0.26), "trot": (0.07, 0.05, 0.17)}[mode]
        for f, sw in list(self.swing.items()):
            sw["k"] += dt / sw["dur"]
            if sw["k"] >= 1:
                self.anchor[f] = sw["target"](x)
                del self.swing[f]
        if frozen or abs(v) < 0.05:
            return
        sgn = 1 if v > 0 else -1
        for g in groups:
            if any(f in self.swing for f in g):
                continue
            if mode == "trot" and self.swing:
                continue
            behind = max(sgn * ((x + self.rest_off[f]) - self.anchor[f]) for f in g)
            if behind > th:
                for f in g:
                    start = self.anchor[f]
                    off = self.rest_off[f]
                    self.swing[f] = dict(k=0.0, dur=dur, start=start,
                                         target=lambda xx, off=off: xx + off + sgn * ahead)

    def feet(self, x):
        pos, lifts = {}, {}
        for f in FEET:
            rx, ry = Rig.rest(f)
            if f in self.override:
                pos[f], lifts[f] = self.override[f]
                continue
            if f in self.swing:
                sw = self.swing[f]
                k = smooth(min(1, sw["k"]))
                fw = sw["start"] + (sw["target"](x) - sw["start"]) * k
                lifts[f] = 1 + 0.18 * math.sin(math.pi * min(1, sw["k"]))
            else:
                fw = self.anchor[f]
                lifts[f] = 1.0
            pos[f] = (rx, ANCHOR[1] - (fw - x) * self.U)
        return pos, lifts

    def reanchor(self, f, x, local):
        self.anchor[f] = x + (ANCHOR[1] - local[1]) / self.U
        self.override.pop(f, None)


# ----------------------------------------------------------------------------- the four clips
def blade_lerp(p, q, t):
    return (p[0] + (q[0] - p[0]) * t, (p[1][0] + (q[1][0] - p[1][0]) * t, p[1][1] + (q[1][1] - p[1][1]) * t))


def blade_from_e(e):
    rest, coil, ext = BLADE_POSES["rest"], BLADE_POSES["coil"], BLADE_POSES["ext"]
    if e < 0:
        return blade_lerp(rest, coil, -e)
    t = min(e, 1.08)
    a, _ = blade_lerp(rest, ext, t)
    return a, blade_lerp(rest, ext, min(t, 1))[1]


CLIPS = [
    ("A", "one blade impales, reels in, drags off"),
    ("B", "both blades impale, reel in, drag off"),
    ("C", "chomp: lunging bite, then mauls"),
    ("D", "forearm swipes, left then right"),
]


def body_x(clip, t):
    """Body position along the approach (cells) and its speed."""
    p1 = RUN_SPEED * T_RUN
    if t < T_RUN:
        return RUN_SPEED * t, RUN_SPEED
    u = t - T_RUN
    dec = min(u, 0.25)
    x = p1 + RUN_SPEED * dec - RUN_SPEED / 0.5 * dec * dec
    v = RUN_SPEED - RUN_SPEED / 0.25 * dec if u < 0.25 else 0.0
    s = u / T_STRIKE
    lunge = {"A": 0.25, "B": 0.25, "C": 0.42, "D": 0.1}[clip]
    if s >= 0.34:
        x += lunge * ease_out(min(1, (s - 0.34) / 0.1))
    if clip in "AB":
        back0 = T_RUN + T_STRIKE + 0.35  # after the reel-in
        if t > back0:
            x -= DRAG_SPEED * (t - back0)
            v = -DRAG_SPEED
    return x, v


def clip_state(clip, t):
    """Pose controls for a clip at time t (everything but the feet)."""
    u = t - T_RUN
    s = u / T_STRIKE if u >= 0 else None
    st = dict(gape=0.0, blades={1: BLADE_POSES["rest"], -1: BLADE_POSES["rest"]}, phase="approach", hit=None,
              frozen=u >= 0 and (clip not in "AB" or t < T_RUN + T_STRIKE + 0.35), mode="bound")
    if s is None:
        return st
    st["phase"] = "wind-up" if s < 0.34 else ("lash" if s < 0.44 else "strike")
    st["hit"] = s >= 0.44
    if clip in "AB":
        sides = (1,) if clip == "A" else (1, -1)
        if s < 0.58:
            e = strike_e(s)
            for side in sides:
                st["blades"][side] = blade_from_e(e)
            st["gape"] = 0.4 * smooth(min(1, s / 0.34)) if s < 0.44 else 0.6
        else:
            r = smooth(min(1, (u - 0.58 * T_STRIKE) / 0.45))  # reel the victim back to the jaws
            for side in sides:
                st["blades"][side] = blade_lerp(BLADE_POSES["ext"], BLADE_POSES["reel"], r)
            st["gape"] = 1.0 if r < 0.85 else max(0.0, 1 - (r - 0.85) / 0.15)
            st["phase"] = "reel in" if r < 1 else "drag off"
            if r >= 1 and t > T_RUN + T_STRIKE + 0.35:
                st["mode"] = "trot"
    elif clip == "C":
        for side in (1, -1):
            st["blades"][side] = blade_from_e(-0.35 * smooth(min(1, s / 0.34)))  # tucked clear of the bite
        if s < 0.34:
            st["gape"] = smooth(s / 0.34)
        elif s < 0.5:
            st["gape"] = 1.0 - smooth((s - 0.34) / 0.1)
        else:
            w = (u - 0.5 * T_STRIKE) / 0.42  # mauling chomps
            st["gape"] = 0.55 * (0.5 - 0.5 * math.cos(2 * math.pi * w))
            st["phase"] = "maul"
    else:  # D: forearm swipes
        for side in (1, -1):
            st["blades"][side] = blade_from_e(-0.2 * smooth(min(1, s / 0.34)))
        st["gape"] = 0.35 * smooth(min(1, s / 0.34))
        st["phase"] = "swipe"
    return st


def swipe_foot(side, w):
    """A front foot's path through one swipe, w in [0,1]: draw back, lash across in front, recover.
    Layer pixels; side -1 is the left forearm."""
    rx, ry = Rig.rest("FL")
    back = (rx - 30, ry + 80)
    keys = [(0.0, (rx, ry)), (0.3, back), (0.42, (rx - 70, 470)), (0.5, (70, 360)), (0.6, (230, 330)),
            (1.0, (rx, ry))]
    for (t0, p0), (t1, p1) in zip(keys, keys[1:]):
        if w <= t1:
            k = (w - t0) / (t1 - t0)
            k = ease_out(k) if t0 == 0.42 else smooth(k)
            x, y = p0[0] + (p1[0] - p0[0]) * k, p0[1] + (p1[1] - p0[1]) * k
            lift = 1 + 0.28 * math.sin(math.pi * w)
            return ((x, y) if side < 0 else (1024 - x, y)), lift
    return ((rx, ry) if side < 0 else (1024 - rx, ry)), 1.0


SWIPES = [(-1, T_RUN + 0.15, 0.55), (1, T_RUN + 0.85, 0.55)]  # side, start, duration


def simulate(rig, clip, U, n):
    """Run a clip's timeline once: per frame, its pose controls, feet and body position."""
    x, _ = body_x(clip, 0)
    gait = Gait(rig, U, x)
    out = []
    for f in range(n):
        t = f / FPS
        x, v = body_x(clip, t)
        st = clip_state(clip, t)
        if clip == "D":
            for side, t0, dur in SWIPES:
                name = "FL" if side < 0 else "FR"
                if t0 <= t < t0 + dur:
                    gait.override[name] = swipe_foot(side, (t - t0) / dur)
                elif t >= t0 + dur and name in gait.override:
                    gait.reanchor(name, x, Rig.rest(name))
        gait.step(x, v, 1 / FPS, st["mode"], st["frozen"])
        feet, lifts = gait.feet(x)
        out.append((t, x, st, feet, lifts))
    return out


# ----------------------------------------------------------------------------- scenes and drawing
def marine_sprites(root):
    d = Path(root) / "rav-lz96" / "visuals" / "colony-breach" / "frames"
    a = np.asarray(Image.open(d / "frame-0000-tick-000000.png").convert("RGB")).astype(int)
    b = np.asarray(Image.open(d / "frame-0001-tick-000600.png").convert("RGB")).astype(int)
    m = np.abs(a - b).sum(2) > 40
    cyan = (b[..., 2] > 170) & (b[..., 1] > 150) & (b[..., 0] < 120)
    outs = []
    for x0, y0, x1, y1 in ((1440, 180, 1900, 720), (160, 360, 700, 1080)):
        sub = m[y0:y1, x0:x1]
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
                body = ~cy_ & m[by0:by1, bx0:bx1]
                rgb[cy_] = np.median(rgb[body], axis=0) if body.any() else (90, 96, 100)
                alpha = (m[by0:by1, bx0:bx1] * 255).astype(np.uint8)
                outs.append(Image.fromarray(np.dstack([rgb.astype(np.uint8), alpha]), "RGBA"))
    return outs


def ground(root, z):
    d = Path(root) / f"rav-lz{z}" / "visuals" / "colony-breach" / "frames"
    return Image.open(d / "frame-0000-tick-000000.png").convert("RGBA")


START_U = -4.3          # where every clip's Runner starts (cells right of the scene origin)
ORIGIN96 = (1150, 560)  # scene origin in the 96 px frame


class Clip:
    """One attack, simulated once, then drawn at any zoom."""

    def __init__(self, rig, cid, U, n, sc96):
        self.rig, self.cid, self.U = rig, cid, U
        self.frames = simulate(rig, cid, U, n)
        # where the strike lands, in scene cells: the victim stands there
        t_hit = T_RUN + 0.44 * T_STRIKE
        f_hit = int(round(t_hit * FPS))
        _, x, st, feet, _ = self.frames[f_hit]
        if cid in "AB":
            sides = (1,) if cid == "A" else (1, -1)
            tips = [rig.blade_tip(*st["blades"][s], s) for s in sides]
            local = (sum(p[0] for p in tips) / len(tips), sum(p[1] for p in tips) / len(tips))
        elif cid == "C":
            local = JAW
        else:
            _, t0, dur = SWIPES[0]
            local = swipe_foot(-1, 0.5)[0]
            x = body_x(cid, t0 + 0.5 * dur)[0]
        self.victim = self.to_world(local, x)
        if cid == "D":  # the second swipe's mark, on the other side
            _, t1, dur = SWIPES[1]
            self.victim2 = self.to_world(swipe_foot(1, 0.5)[0], body_x(cid, t1 + 0.5 * dur)[0])

    def to_world(self, local, x):
        return (START_U + x + (ANCHOR[1] - local[1]) / self.U, (local[0] - 512) / self.U)

    def draw(self, tile, f, ppc, origin, marines, sc):
        t, x, st, feet, lifts = self.frames[f]
        mk = ppc / 96

        def px(w):
            return origin[0] + w[0] * ppc, origin[1] + w[1] * ppc

        def put(img, w, radius, angle=0.0):
            cx, cy = px(w)
            sh, (ox, oy) = shadow_blob(ppc, radius)
            tile.alpha_composite(sh, (int(cx + ox - sh.width / 2), int(cy + oy - sh.height / 2)))
            if angle:
                img = img.rotate(angle, expand=True, resample=Image.BICUBIC)
            tile.alpha_composite(img, (int(cx - img.width / 2), int(cy - img.height / 2)))

        def small(m, dim=1.0):
            m = m.convert("RGBa").resize((max(1, round(m.width * mk)), max(1, round(m.height * mk))),
                                         Image.LANCZOS).convert("RGBA")
            if dim != 1.0:
                m = Image.fromarray((np.asarray(m).astype(float) * [dim, dim, dim, 1]).astype(np.uint8), "RGBA")
            return m

        vx, vy = self.victim
        for i, (du, dv) in enumerate([(1.3, -0.9), (1.6, 0.8)]):
            put(small(marines[(i + 1) % len(marines)]), (vx + du, vy + dv), 0.25)
        tail = -math.sin(2 * math.pi * x / 1.3) if st["phase"] in ("approach", "drag off") else 0.0
        body = self.rig.pose(feet, lifts, tail, st["gape"], st["blades"])
        body = body.resize((max(1, int(CW * sc)), max(1, int(CH * sc))), Image.LANCZOS).rotate(
            -90, expand=True, resample=Image.BICUBIC)
        Hs = CH * sc
        ax, ay = L(*ANCHOR)
        anc = (Hs - ay * sc, ax * sc)
        bw = (START_U + x, 0.0)
        bx, by = px(bw)
        # the victim
        t_hit = T_RUN + 0.44 * T_STRIKE
        victim = small(marines[0])
        downed = small(marines[0], 0.8)
        if self.cid in "AB" and st["hit"]:
            sides = (1,) if self.cid == "A" else (1, -1)
            tips = [self.rig.blade_tip(*st["blades"][s], s) for s in sides]
            local = (sum(p[0] for p in tips) / len(tips), sum(p[1] for p in tips) / len(tips))
            k = smooth(min(1, (t - t_hit) / 0.08))
            w = self.to_world(local, x)
            w = (vx + (w[0] - vx) * k, vy + (w[1] - vy) * k)
            put(downed, w, 0.22, 90 * k)
        elif self.cid == "C" and st["hit"]:
            k = smooth(min(1, (t - t_hit) / 0.15))
            put(downed, (vx + 0.25 * k, vy), 0.22, 90 * k)
        elif self.cid == "D" and t >= SWIPES[0][1] + 0.5 * SWIPES[0][2]:
            k = smooth(min(1, (t - SWIPES[0][1] - 0.5 * SWIPES[0][2]) / 0.15))
            put(downed, (vx + 0.2 * k, vy - 0.35 * k), 0.22, 90 * k)
        else:
            put(victim, (vx, vy), 0.25)
        if self.cid == "D":  # a second Marine on the other side takes the second swipe
            v2x, v2y = self.victim2
            if t >= SWIPES[1][1] + 0.5 * SWIPES[1][2]:
                k = smooth(min(1, (t - SWIPES[1][1] - 0.5 * SWIPES[1][2]) / 0.15))
                put(small(marines[3 % len(marines)], 0.8), (v2x + 0.2 * k, v2y + 0.35 * k), 0.22, -90 * k)
            else:
                put(small(marines[3 % len(marines)]), (v2x, v2y), 0.25)
        sh, (ox, oy) = shadow_blob(ppc, 0.34)
        tile.alpha_composite(sh, (int(bx + ox - sh.width / 2), int(by + oy - sh.height / 2)))
        tile.alpha_composite(body, (int(bx - anc[0]), int(by - anc[1])))
        return st["phase"]


def build(root, out):
    rig = Rig()
    marines = marine_sprites(root)
    g96, g32 = ground(root, 96), ground(root, 32)
    sc = {p: (RUNNER_AREA_PX * (p / PPC17) ** 2 / rig.hero_area) ** 0.5 for p in (96, 32)}
    U = 96 / (S * sc[96])  # layer pixels per cell
    n = int(T_END * FPS)
    clips = [Clip(rig, cid, U, n, sc[96]) for cid, _ in CLIPS]
    W96, H96, W32, H32 = 944, 404, 470, 150
    o32 = (960 + (ORIGIN96[0] - 960) / 3, 540 + (ORIGIN96[1] - 540) / 3)
    frames = []
    for f in range(n):
        t = f / FPS
        img = Image.new("RGB", (1920, 1080), BG)
        d = ImageDraw.Draw(img)
        d.text((24, 10), "RAV BLOOM RUNNER  /  MOTION STUDY 2  /  four attacks, jointed legs on planted feet",
               font=font(24, True), fill=INK)
        d.text((24, 40), "Shorter barbed blades that strike, impale and reel the victim back to the jaws; a lunging "
                         "bite; forearm claw swipes. Real time. Top: 96 px per cell. Bottom row: 32 px per cell.",
               font=font(14), fill=DIM)
        for i, ((cid, title), clip) in enumerate(zip(CLIPS, clips)):
            gx, gy = 12 + (i % 2) * (W96 + 8), 66 + (i // 2) * (H96 + 34)
            box = (int(ORIGIN96[0] - W96 / 2), int(ORIGIN96[1] - H96 / 2))
            tile = g96.crop((box[0], box[1], box[0] + W96, box[1] + H96)).copy()
            phase = clip.draw(tile, f, 96, (W96 / 2, H96 / 2), marines, sc[96])
            img.paste(tile.convert("RGB"), (gx, gy + 26))
            d.text((gx, gy + 2), f"{cid}  {title}", font=font(16, True), fill=ACC)
            d.text((gx + W96 - 160, gy + 4), phase, font=font(15, True), fill=INK)
            sx = 12 + i * (W32 + 6)
            box32 = (int(o32[0] - W32 / 2), int(o32[1] - H32 / 2))
            t32 = g32.crop((box32[0], box32[1], box32[0] + W32, box32[1] + H32)).copy()
            clip.draw(t32, f, 32, (W32 / 2, H32 / 2), marines, sc[32])
            img.paste(t32.convert("RGB"), (sx, 924))
            d.text((sx, 904), f"{cid} at 32 px per cell", font=font(13), fill=DIM)
        d.text((1700, 14), f"t = {t:4.2f} s", font=font(16, True), fill=DIM)
        frames.append(img)
    save_gif(frames, out, FPS, accents=[(255, 168, 54), (214, 170, 214), (150, 40, 90), (236, 226, 206)])
    return frames, clips


def action_sheet(frames, out):
    """Each clip at six moments, cropped 1:1 around the action."""
    picks = [0.6, 1.12, 1.26, 1.45, 1.9, 3.4]
    sheet = Image.new("RGB", (1920, 1080), BG)
    d = ImageDraw.Draw(sheet)
    d.text((24, 10), "RUNNER MOTION STUDY 2  /  key moments at 96 px per cell, 1:1", font=font(22, True), fill=INK)
    for c, tt in enumerate(picks):
        d.text((150 + c * 296, 44), f"t = {tt:.2f} s", font=font(15, True), fill=ACC)
    for r, (cid, title) in enumerate(CLIPS):
        gx, gy = 12 + (r % 2) * 952, 66 + (r // 2) * 438
        d.text((16, 72 + r * 252 + 100), cid, font=font(28, True), fill=ACC)
        for c, tt in enumerate(picks):
            fr = frames[int(round(tt * FPS))]
            crop = fr.crop((gx + 330, gy + 26 + 82, gx + 330 + 290, gy + 26 + 82 + 240))
            sheet.paste(crop, (60 + c * 296, 70 + r * 252))
    sheet.save(out)


if __name__ == "__main__":
    fr, _ = build(sys.argv[1], sys.argv[2])
    if len(sys.argv) > 3:
        action_sheet(fr, sys.argv[3])
    if len(sys.argv) > 4:
        sheet = Image.new("RGB", (1920, 1080), BG)
        d = ImageDraw.Draw(sheet)
        d.text((24, 16), "RUNNER  /  design and rig layers", font=font(24, True), fill=INK)
        names = [("hero", "combined design"), ("body-nolegs", "layer: body"), ("body-gape", "layer: gaping jaws"),
                 ("blade", "layer: arm blade (shortened in the rig)"), ("leg-front", "layer: front leg"),
                 ("leg-hind", "layer: hind leg")]
        for i, (n_, lab) in enumerate(names):
            im = Image.open(RAW / f"{n_}.png").convert("RGB")
            k = min(310 / im.width, 940 / im.height)
            im = im.resize((int(im.width * k), int(im.height * k)), Image.LANCZOS)
            sheet.paste(im, (16 + i * 316, 64))
            d.text((20 + i * 316, 74 + im.height), lab, font=font(14, True), fill=ACC)
        sheet.save(sys.argv[4])
