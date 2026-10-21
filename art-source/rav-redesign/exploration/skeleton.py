"""A 3D skeleton for a four-legged Rav strain, seen from the side and from above: the biology test that
comes before any art goes on a rig.

  python skeleton.py <out.gif> [<key frames.png>]

Body frame: y forward, x to the right, z up, in cells. Legs carry the body from underneath, as a
hound's do, and are solved in their own side-view plane: the front leg is two bones with the elbow
pointing back; the hind leg is three, a forward stifle and a backward hock. Feet follow a phase gait
in which a supporting foot moves back exactly as fast as the body moves forward, so it stays put on
the ground. When the body has to move without stepping (the lunge) the feet are pinned to the
ground instead. The spine stretches and gathers with the gallop. The spike arms sit on top of the
shoulders, folded in a Z; a strike drives the wrist along a straight line, so the spike thrusts
forward instead of swinging.

The ground carries ticks every quarter cell, and the camera follows the body, so a planted foot is
one that holds still against the ticks.
"""
import math
import sys

from PIL import Image, ImageDraw

from boards import font
from rig import ease_out, smooth

FPS = 25
T_END = 5.0
SS = 2                   # supersampling
BG = (16, 20, 26)
INK = (226, 228, 232)
DIM = (150, 156, 166)
ACC = (240, 196, 120)
NEAR, FAR = (226, 176, 226), (128, 84, 132)
ARM, SPIKE = (210, 160, 220), (240, 230, 206)
BODY, BODY_EDGE = (150, 70, 120), (220, 170, 210)
GROUND = (60, 66, 76)
VICTIM = (120, 128, 136)

# ----------------------------------------------------------------------------- anatomy (cells)
SPINE = 0.50                    # shoulder to hip
SHOULDER_Z, HIP_Z = 0.40, 0.38
FRONT = dict(x=0.085, a=0.22, b=0.22)            # upper arm, forearm (elbow points back)
HIND = dict(x=0.08, a=0.19, b=0.20, c=0.12)      # thigh, shin, metatarsus (stifle forward, hock back)
ARMS = dict(x=0.07, up=0.07, a=0.17, b=0.17, spike=0.11)
GAITS = {  # duty factor, stride (cells), phase offsets
    "gallop": (0.30, 1.05, {"HL": 0.0, "HR": 0.1, "FR": 0.48, "FL": 0.58}),
    "trot": (0.55, 0.36, {"FL": 0.0, "HR": 0.0, "FR": 0.5, "HL": 0.5}),
}
LIFT = {"gallop": 0.10, "trot": 0.05}

# ----------------------------------------------------------------------------- timeline
T_RUN, T_STOP = 1.2, 1.42           # galloping, then braking into a planted stance
T_SWIPE, SWIPE_WIND, SWIPE_CUT, SWIPE_BACK = 1.42, 0.15, 0.10, 0.30   # right arm: a side swipe
T_WIND, T_THRUST, T_HOLD, T_REEL = 1.95, 2.15, 2.23, 2.45                # left arm: a straight stab
T_DRAG = 2.9
T_SWIPE_HIT = T_SWIPE + SWIPE_WIND + 0.55 * SWIPE_CUT
RUN_V, DRAG_V, LUNGE = 3.0, -0.6, 0.14


def body_y(t):
    if t < T_RUN:
        return RUN_V * t
    y = RUN_V * T_RUN
    u = min(t, T_STOP) - T_RUN
    d = T_STOP - T_RUN
    y += RUN_V * u - RUN_V / (2 * d) * u * u
    if t > T_THRUST:
        y += LUNGE * ease_out(min(1, (t - T_THRUST) / (T_HOLD - T_THRUST)))
    if t > T_REEL:
        y -= LUNGE * smooth(min(1, (t - T_REEL) / 0.3))  # settles back as it reels in
    if t > T_DRAG:
        y += DRAG_V * (t - T_DRAG)
    return y


def mode(t):
    if t < T_RUN + 0.08:
        return "gallop"
    if t < T_DRAG:
        return "plant"
    return "trot"


# ----------------------------------------------------------------------------- solvers
def ik2(root, target, a, b, bend):
    """Two bones in a vertical plane: root and target are (y, z); bend +1 puts the joint forward of the line."""
    dy, dz = target[0] - root[0], target[1] - root[1]
    d = max(abs(a - b) + 1e-4, min(a + b - 1e-4, math.hypot(dy, dz)))
    base = math.atan2(dz, dy)
    off = math.acos(max(-1, min(1, (a * a + d * d - b * b) / (2 * a * d))))
    cands = [(root[0] + a * math.cos(base + s * off), root[1] + a * math.sin(base + s * off)) for s in (1, -1)]
    joint = max(cands, key=lambda j: bend * j[0])
    end = (root[0] + d * math.cos(base), root[1] + d * math.sin(base))
    return joint, end


def gait_foot(name, phi, gait):
    """Foot (y offset from the leg root, z) for a gait phase."""
    duty, stride, offs = GAITS[gait]
    u = (phi + offs[name]) % 1.0
    half = duty * stride / 2
    if u < duty:
        return half - (u / duty) * 2 * half, 0.0
    k = (u - duty) / (1 - duty)
    return -half + 2 * half * smooth(k), LIFT[gait] * math.sin(math.pi * k)


class Skeleton:
    def __init__(self):
        self.phi = 0.0
        self.last_y = 0.0
        self.last_mode = "gallop"
        self.pinned = {}
        self.blend_from = None
        self.blend_t = 0.0
        self.prev_feet = None

    def roots(self, y, flex, crouch):
        sh_y, hp_y = y + SPINE / 2 * (1 + flex), y - SPINE / 2 * (1 + flex)
        sz, hz = SHOULDER_Z - crouch, HIP_Z - crouch * 0.6
        r = {}
        for n in ("FL", "FR"):
            r[n] = ((-1 if n[1] == "L" else 1) * FRONT["x"], sh_y, sz - 0.02)
        for n in ("HL", "HR"):
            r[n] = ((-1 if n[1] == "L" else 1) * HIND["x"], hp_y, hz - 0.02)
        return r, (sh_y, sz), (hp_y, hz)

    def frame(self, t, dt):
        y = body_y(t)
        m = mode(t)
        dy = y - self.last_y
        self.last_y = y
        gait = "gallop" if m == "gallop" else "trot"
        if m != "plant":
            self.phi += dy / GAITS[gait][1]
        # spine flex: the gallop gathers and stretches once a stride
        run = 1.0 if m == "gallop" else (0.25 if m == "trot" else 0.0)
        flex = 0.10 * run * math.sin(2 * math.pi * (self.phi + 0.25))
        crouch = 0.0
        if T_WIND <= t < T_THRUST:
            crouch = 0.035 * smooth((t - T_WIND) / (T_THRUST - T_WIND))
        elif T_THRUST <= t < T_HOLD + 0.2:
            crouch = 0.035 * (1 - smooth((t - T_THRUST) / 0.28))
        roots, sh, hp = self.roots(y, flex, crouch)
        feet = {}
        for n, root in roots.items():
            neutral = y + (SPINE / 2 if n[0] == "F" else -SPINE / 2)  # feet hang off the unflexed frame
            if m == "plant":
                if n not in self.pinned:
                    prev = self.prev_feet[n]
                    # a foot caught in the air finishes its step and lands under its root
                    land = prev[1] if prev[2] < 0.005 else neutral + 0.02
                    self.pinned[n] = dict(start=prev, land=land, t=t)
                pin = self.pinned[n]
                k = smooth(min(1, (t - pin["t"]) / 0.1))
                sy, sz = pin["start"][1], pin["start"][2]
                feet[n] = (root[0], sy + (pin["land"] - sy) * k, sz * (1 - k))
            else:
                fy, fz = gait_foot(n, self.phi, gait)
                feet[n] = (root[0], neutral + fy, fz)
        if m != self.last_mode:
            if m == "trot":
                self.blend_from = {n: (f[0], f[1] - y, f[2]) for n, f in self.prev_feet.items()}
                self.blend_t = 0.0
            if m != "plant":
                self.pinned = {}
            self.last_mode = m
        if self.blend_from is not None:
            self.blend_t += dt
            k = smooth(min(1, self.blend_t / 0.25))
            for n in feet:
                bx, by, bz = self.blend_from[n]
                fx, fy, fz = feet[n]
                feet[n] = (fx, y + by + (fy - y - by) * k, fz + 0.04 * math.sin(math.pi * k))
            if k >= 1:
                self.blend_from = None
        self.prev_feet = feet
        legs = {}
        for n, root in roots.items():
            foot = feet[n]
            if n[0] == "F":
                elbow, paw = ik2((root[1], root[2]), (foot[1], foot[2]), FRONT["a"], FRONT["b"], -1)
                legs[n] = [root, (root[0], *elbow), (root[0], *paw)]
            else:
                hock_t = (foot[1] - 0.03, foot[2] + HIND["c"])  # metatarsus stands near-vertical, leaning back
                stifle, hock = ik2((root[1], root[2]), hock_t, HIND["a"], HIND["b"], 1)
                legs[n] = [root, (root[0], *stifle), (root[0], *hock), (root[0], foot[1], foot[2])]
        arms, tips = self.arms(t, sh)
        neck = (0.0, sh[0] + 0.12, sh[1] + 0.03)
        head = (0.0, sh[0] + 0.25 + 0.03 * math.sin(2 * math.pi * self.phi) * run, sh[1] - 0.01)
        tail = [(0.0, hp[0] - 0.08 * i, hp[1] + 0.02 - 0.03 * i) for i in range(5)]
        sway = 0.04 * run * math.sin(2 * math.pi * self.phi)
        tail = [(sway * i * i / 16, p[1], p[2]) for i, p in enumerate(tail)]
        return dict(y=y, legs=legs, arms=arms, tips=tips, sh=sh, hp=hp, neck=neck, head=head, tail=tail,
                    phase=self.phase(t))

    def arms(self, t, sh):
        """One arm per strike. Each wrist is (turn at the shoulder, reach, height) from its base: the left arm
        stabs straight along a line; the right arm swipes, turning from out wide across the front."""
        rest, coil, ext = (0.15, 0.10), (0.05, 0.14), (0.31, -0.08)

        def lerp(p, q, k):
            return tuple(a + (b - a) * k for a, b in zip(p, q))

        # left: (yaw deg, reach, height)
        r0 = (0.0, *rest)
        if t < T_WIND:
            left = r0
        elif t < T_THRUST:
            left = lerp(r0, (0.0, *coil), smooth((t - T_WIND) / (T_THRUST - T_WIND)))
        elif t < T_HOLD:
            left = lerp((0.0, *coil), (0.0, *ext), ease_out((t - T_THRUST) / (T_HOLD - T_THRUST)))
        elif t < T_REEL:
            left = (0.0, *ext)
        else:
            left = lerp((0.0, *ext), r0, smooth(min(1, (t - T_REEL) / 0.4)))
        # right: the swipe
        out, cut = (80.0, 0.22, 0.07), (-30.0, 0.30, -0.02)
        u = t - T_SWIPE
        if u < 0:
            right = r0
        elif u < SWIPE_WIND:
            right = lerp(r0, out, smooth(u / SWIPE_WIND))
        elif u < SWIPE_WIND + SWIPE_CUT:
            right = lerp(out, cut, ease_out((u - SWIPE_WIND) / SWIPE_CUT))
        else:
            right = lerp(cut, r0, smooth(min(1, (u - SWIPE_WIND - SWIPE_CUT) / SWIPE_BACK)))
        arms, tips = [], []
        for side, (yaw, r, h) in ((-1, left), (1, right)):
            base = (side * ARMS["x"], sh[0] - 0.02, sh[1] + ARMS["up"])
            (er, eh), (wr, wh) = ik2((0.0, 0.0), (r, h), ARMS["a"], ARMS["b"], -1)
            ang = math.atan2(wh - eh, wr - er)
            tr, th = wr + ARMS["spike"] * math.cos(ang), wh + ARMS["spike"] * math.sin(ang)
            sy, cy = math.sin(math.radians(yaw)), math.cos(math.radians(yaw))

            def at(rr, hh):
                return (base[0] + side * rr * sy, base[1] + rr * cy, base[2] + hh)

            pts = [base, at(er, eh), at(wr, wh), at(tr, th)]
            arms.append(pts)
            tips.append(pts[-1])
        return arms, tips

    @staticmethod
    def phase(t):
        if t < T_RUN:
            return "gallop"
        if t < T_SWIPE:
            return "brake"
        if t < T_WIND:
            return "side swipe"
        if t < T_THRUST:
            return "stab wind-up"
        if t < T_HOLD:
            return "stab"
        if t < T_REEL:
            return "impale"
        if t < T_DRAG:
            return "reel in"
        return "drag off"


# ----------------------------------------------------------------------------- drawing
def draw_frame(sk, t, victim):
    W, H = 1920 * SS, 1080 * SS
    img = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(img)
    ppc = 520 * SS
    cam = sk["y"]

    def side(p, ox, oy):
        return ox + (p[1] - cam) * ppc, oy - p[2] * ppc

    def top(p, ox, oy):
        return ox + (p[1] - cam) * ppc, oy + p[0] * ppc

    def line(pts, col, w):
        d.line(pts, fill=col, width=int(w * SS), joint="curve")
        for p in pts:
            r = w * SS / 2
            d.ellipse([p[0] - r, p[1] - r, p[0] + r, p[1] + r], fill=col)

    # --- side view (left)
    ox, oy = 480 * SS, 700 * SS
    d.line([(20 * SS, oy), (940 * SS, oy)], fill=GROUND, width=2 * SS)
    k0 = math.floor((cam - 1) * 4)
    for k in range(k0, k0 + 9):
        x = ox + (k / 4 - cam) * ppc
        if 20 * SS < x < 940 * SS:
            d.line([(x, oy), (x, oy + 14 * SS)], fill=GROUND, width=2 * SS)
    for n in ("FL", "HL"):  # far side
        line([side(p, ox, oy) for p in sk["legs"][n]], FAR, 9)
    line([side(p, ox, oy) for p in sk["arms"][0]], FAR, 7)
    torso = [side(p, ox, oy) for p in [(0, sk["hp"][0], sk["hp"][1]), (0, sk["sh"][0], sk["sh"][1])]]
    line([side(p, ox, oy) for p in sk["tail"]], BODY, 12)
    line(torso, BODY, 46)
    line([torso[1], side(sk["neck"], ox, oy), side(sk["head"], ox, oy)], BODY, 30)
    for n in ("FR", "HR"):
        line([side(p, ox, oy) for p in sk["legs"][n]], NEAR, 11)
    arm = [side(p, ox, oy) for p in sk["arms"][1]]
    line(arm[:3], ARM, 9)
    line(arm[2:], SPIKE, 6)
    if victim is not None:
        vx, vy = side((0, victim[0], 0.12), ox, oy)
        r = 40 * SS
        d.rounded_rectangle([vx - r * 0.5, vy - r * 1.4, vx + r * 0.5, vy + r * 1.4], radius=r * 0.4, fill=VICTIM)
    # --- top view (right)
    ox, oy = 1440 * SS, 420 * SS
    for k in range(k0, k0 + 9):
        x = ox + (k / 4 - cam) * ppc
        if 980 * SS < x < 1900 * SS:
            for j in range(-3, 4):
                yy = oy + j * 0.12 * ppc
                d.ellipse([x - 3 * SS, yy - 3 * SS, x + 3 * SS, yy + 3 * SS], fill=GROUND)
    for n in ("FL", "FR", "HL", "HR"):
        pts = [top(p, ox, oy) for p in sk["legs"][n]]
        line(pts, NEAR if n[1] == "R" else FAR, 9)
        fz = sk["legs"][n][-1][2]
        r = (9 + 40 * fz) * SS
        px, py = pts[-1]
        d.ellipse([px - r, py - r, px + r, py + r], fill=SPIKE if fz < 0.005 else (180, 150, 170))
    tail = [top(p, ox, oy) for p in sk["tail"]]
    line(tail, BODY, 14)
    hp, sh = top((0, sk["hp"][0], 0), ox, oy), top((0, sk["sh"][0], 0), ox, oy)
    d.ellipse([hp[0] - 0.13 * ppc, hp[1] - 0.12 * ppc, hp[0] + 0.13 * ppc, hp[1] + 0.12 * ppc], fill=BODY,
              outline=BODY_EDGE, width=2 * SS)
    d.ellipse([sh[0] - 0.15 * ppc, sh[1] - 0.13 * ppc, sh[0] + 0.15 * ppc, sh[1] + 0.13 * ppc], fill=BODY,
              outline=BODY_EDGE, width=2 * SS)
    d.polygon([(hp[0], hp[1] - 0.09 * ppc), (sh[0], sh[1] - 0.1 * ppc), (sh[0], sh[1] + 0.1 * ppc),
               (hp[0], hp[1] + 0.09 * ppc)], fill=BODY)
    hd = top(sk["head"], ox, oy)
    line([sh, top(sk["neck"], ox, oy), hd], BODY, 34)
    for a in sk["arms"]:
        pts = [top(p, ox, oy) for p in a]
        line(pts[:3], ARM, 9)
        line(pts[2:], SPIKE, 6)
    if victim is not None:
        vx, vy = top((victim[1], victim[0], 0), ox, oy)
        r = 34 * SS
        d.ellipse([vx - r, vy - r, vx + r, vy + r], fill=VICTIM)
    img = img.resize((1920, 1080), Image.LANCZOS)
    d = ImageDraw.Draw(img)
    d.text((24, 12), "RAV BLOOM RUNNER  /  SKELETON TEST 2  /  one arm per strike: side swipe, then straight stab",
           font=font(24, True), fill=INK)
    d.text((24, 44), "Side view (left) and top view (right) of one 3D skeleton. Camera follows the body; a planted "
                     "foot holds still against the ground ticks. Far-side limbs darker. Real time.",
           font=font(14), fill=DIM)
    d.text((24, 1000), "Legs: front elbow back; hind stifle forward, hock back. Gallop: rotary, spine gathers and stretches. "
                       "One arm per strike: the right swipes across from out wide, the left stabs straight and "
                       "impales. Drag: backward trot.", font=font(14), fill=DIM)
    for i, ph in enumerate(["gallop", "brake", "side swipe", "stab wind-up", "stab", "impale", "reel in", "drag off"]):
        x = 24 + i * 118
        on = ph == sk["phase"]
        d.rectangle([x, 940, x + 114, 972], fill=ACC if on else (34, 38, 46))
        d.text((x + 6, 947), ph, font=font(13, True), fill=BG if on else DIM)
    d.text((990, 946), f"t = {t:4.2f} s", font=font(15, True), fill=DIM)
    return img


def build(out):
    sk = Skeleton()
    frames, raw = [], []
    for f in range(int(T_END * FPS)):
        raw.append(sk.frame(f / FPS, 1 / FPS))
    # the Marine stands where the left arm's stab lands; the swipe staggers it there first
    hit = next(s for f, s in enumerate(raw) if f / FPS >= T_HOLD)
    vy, vx = hit["tips"][0][1], hit["tips"][0][0]
    for f, s in enumerate(raw):
        t = f / FPS
        if t < T_HOLD:
            k = smooth(min(1, max(0.0, t - T_SWIPE_HIT) / 0.15))
            victim = (vy + 0.04 * k, vx + 0.1 * (1 - k))
        else:
            victim = (s["tips"][0][1], s["tips"][0][0])
        frames.append(draw_frame(s, t, victim))
    from motion import save_gif
    save_gif(frames, out, FPS, accents=[ACC, SPIKE, NEAR, FAR, BODY])
    return frames


if __name__ == "__main__":
    fr = build(sys.argv[1])
    if len(sys.argv) > 2:
        picks = [0.3, 1.5, 1.63, 2.1, 2.24, 3.6]
        sheet = Image.new("RGB", (1920, 1080), BG)
        for i, tt in enumerate(picks):
            im = fr[int(tt * FPS)].crop((0, 70, 1920, 930)).resize((640, 287))
            sheet.paste(im, ((i % 3) * 640, 60 + (i // 3) * 500))
            ImageDraw.Draw(sheet).text(((i % 3) * 640 + 8, 60 + (i // 3) * 500 + 292), f"t = {tt:.2f} s",
                                       font=font(16, True), fill=ACC)
        ImageDraw.Draw(sheet).text((24, 16), "SKELETON TEST  /  key frames", font=font(22, True), fill=INK)
        sheet.save(sys.argv[2])
