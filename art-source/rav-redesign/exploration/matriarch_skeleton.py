"""The Matriarch's skeleton: brute_skeleton.py's technique (legs under the body solved in their own
side-view plane, feet on a phase gait that keeps a supporting foot planted, shoulder and hip sway read
off the load) with an elephant's build, plus the two scythes and the crown.

Body frame: y forward, x to the right, z up, in cells, measured off the chosen hero (round 3 #5) at the
proposed sim size (3.9x the Runner's length: U_LAYER hero pixels per cell, about 4.5 cells nose to tail).
Legs are long, thick pillars: the forelimb two bones with the elbow back, the hind three.

Clips (each a body track along the path and a gait schedule):
  A  walk    a slow four-beat walk at 0.7 cells/s
  B  reap    walking into Marines; the right scythe sweeps across the front, then the left; the feet
             stay planted through each strike, so the legs brace while the shoulders lunge
  C  breach  walks up to a wall, plants, lowers the crown and shoves twice (the second breaks it),
             roars, and walks through
"""
import math

from rig import ease_out, smooth
from skeleton import ik2

FPS = 20
T_END = 6.4
# ----------------------------------------------------------------------------- anatomy
U_LAYER = 327.4                 # hero layer pixels per cell at the proposed sim size
ANCHOR_Y = 900                  # hero layer row of the body anchor
SH_ROW, HP_ROW = 860, 1270      # hero rows of the front and hind feet
FOOT_X = {"FL": (185 - 512) / U_LAYER, "FR": (850 - 512) / U_LAYER,
          "HL": (255 - 512) / U_LAYER, "HR": (775 - 512) / U_LAYER}
SHOULDER_Y, HIP_Y = (ANCHOR_Y - SH_ROW) / U_LAYER, (ANCHOR_Y - HP_ROW) / U_LAYER
SHOULDER_Z, HIP_Z = 0.78, 0.74
FRONT = dict(a=0.39, b=0.39)
HIND = dict(a=0.31, b=0.31, c=0.19)
GAITS = {  # duty factor, stride (cells), phase offsets, lift (cells)
    "walk": (0.74, 0.75, {"HL": 0.0, "FL": 0.25, "HR": 0.5, "FR": 0.75}, 0.13),
}
FEET = ("FL", "FR", "HL", "HR")
WALK = 0.7

# ----------------------------------------------------------------------------- clip timing
STRIKES = {"R": 1.7, "L": 3.2}          # clip B: wind-up start of each scythe
S_WIND, S_LASH, S_HOLD, S_BACK = 0.30, 0.13, 0.15, 0.45
WALL_STOP = 2.0                          # clip C plants here
SHOVES = [2.15, 3.15]
WIND, SHOVE, HOLD, BACK = 0.45, 0.12, 0.30, 0.40
ROAR = (4.15, 0.75)
T_THROUGH = 5.0


def strike(t, t0):
    """One scythe: angle in degrees (+ swings forward and inward across the front), outward reach in
    cells, lunge of the shoulders (cells forward), and a label."""
    u = t - t0
    if u < 0 or u > S_WIND + S_LASH + S_HOLD + S_BACK:
        return 0.0, 0.0, 0.0, None
    if u < S_WIND:            # cocked back and out
        s = smooth(u / S_WIND)
        return -22 * s, 0.10 * s, -0.06 * s, "wind-up"
    if u < S_WIND + S_LASH:   # the sweep
        s = ease_out((u - S_WIND) / S_LASH)
        return -22 + 227 * s, 0.10 + 0.12 * s, -0.06 + 0.22 * s, "REAP"
    if u < S_WIND + S_LASH + S_HOLD:
        return 205.0, 0.22, 0.16, "REAP"
    s = smooth((u - S_WIND - S_LASH - S_HOLD) / S_BACK)
    return 205 * (1 - s), 0.22 * (1 - s), 0.16 * (1 - s), "recover"


def shove(t, t0):
    """One crown shove: body offset along the path, crouch, head pose, label."""
    u = t - t0
    if u < 0 or u > WIND + SHOVE + HOLD + BACK:
        return 0.0, 0.0, None, None
    if u < WIND:      # rock back onto the haunches, crown up
        s = smooth(u / WIND)
        return -0.25 * s, 0.05 * s, dict(dy=60 * s, k=1 + 0.06 * s), "rock back"
    if u < WIND + SHOVE:
        s = ease_out((u - WIND) / SHOVE)
        return -0.25 + 0.55 * s, 0.05 - 0.13 * s, dict(dy=60 - 150 * s, k=1.06 - 0.12 * s), "SHOVE"
    if u < WIND + SHOVE + HOLD:
        return 0.30, -0.08, dict(dy=-90, k=0.94), "SHOVE"
    s = smooth((u - WIND - SHOVE - HOLD) / BACK)
    return 0.30 * (1 - s), -0.08 * (1 - s), dict(dy=-90 * (1 - s), k=0.94 + 0.06 * s), "recover"


def track(clip, t):
    """Body position along the path, gait ('plant' pins the feet), crouch, head, scythes, label."""
    head = dict(dy=0.0, k=1.0, gape=0.0)
    blades = {"L": (0.0, 0.0), "R": (0.0, 0.0)}
    if clip == "A":
        return WALK * t, "walk", 0.0, head, blades, 0.0, "walk"
    if clip == "B":
        x, lunge, label, plant = WALK * t, 0.0, "walk", False
        # the body stops while a scythe is out: the walk distance lost to each strike window
        for side, t0 in STRIKES.items():
            dur = S_WIND + S_LASH + S_HOLD + S_BACK
            if t > t0:
                x -= WALK * min(t - t0, dur)
            a, reach, lg, ph = strike(t, t0)
            if ph:
                blades[side] = (a, reach)
                lunge, label, plant = lg, f"{ph} ({'right' if side == 'R' else 'left'})", True
                head = dict(dy=-20 * min(1, max(0, lg) / 0.16), k=1.0, gape=0.6 * (ph == "REAP"))
        return x + lunge, "plant" if plant else "walk", 0.0, head, blades, lunge, label
    # clip C
    if t < WALL_STOP:
        return WALK * t, "walk", 0.0, head, blades, 0.0, "walk"
    x = WALK * WALL_STOP
    if t < T_THROUGH:
        for i, t0 in enumerate(SHOVES):
            off, crouch, h, ph = shove(t, t0)
            if ph:
                h["gape"] = 0.0
                return x + off, "plant", crouch, h, blades, off, f"{ph} {i + 1}"
        r0, rd = ROAR
        if r0 <= t < r0 + rd:
            g = math.sin(math.pi * (t - r0) / rd)
            return x, "plant", 0.0, dict(dy=-30 * g, k=1 + 0.05 * g, gape=min(1.0, 1.6 * g)), blades, 0.0, "ROAR"
        return x, "plant", 0.0, head, blades, 0.0, "brace"
    return x + WALK * (t - T_THROUGH), "walk", 0.0, head, blades, 0.0, "through the breach"


# ----------------------------------------------------------------------------- the skeleton
def gait_foot(name, phi, gait):
    duty, stride, offs, lift = GAITS[gait]
    u = (phi + offs[name]) % 1.0
    half = duty * stride / 2
    if u < duty:
        return half - (u / duty) * 2 * half, 0.0, u / duty
    k = (u - duty) / (1 - duty)
    return -half + 2 * half * smooth(k), lift * math.sin(math.pi * k), None


class MatriarchSkeleton:
    def __init__(self, clip):
        self.clip = clip
        self.phi = 0.0
        self.last_y = None
        self.pinned = {}
        self.prev = None
        self.load = {f: 1.0 for f in FEET}

    def frame(self, t, dt):
        y, gait, crouch, head, blades, lunge, phase = track(self.clip, t)
        walk_y = y - lunge
        dy = 0.0 if self.last_y is None else walk_y - self.last_y
        self.last_y = walk_y
        if gait != "plant":
            self.phi += dy / GAITS[gait][1]
        feet, stance = {}, {}
        for n in FEET:
            root_y = walk_y + (SHOULDER_Y if n[0] == "F" else HIP_Y)
            x = FOOT_X[n]
            if gait == "plant":
                if n not in self.pinned:   # a foot in the air sets down where it is
                    p = self.prev[n] if self.prev else (x, root_y, 0.0)
                    self.pinned[n] = (p, t)
                p, t0 = self.pinned[n]
                k = smooth(min(1, (t - t0) / 0.15))
                feet[n] = (x, p[1], p[2] * (1 - k))
                stance[n] = 0.5
            else:
                fy, fz, st = gait_foot(n, self.phi, gait)
                feet[n] = (x, root_y + fy, fz)
                stance[n] = st
        if gait != "plant" and self.pinned:   # leave the plant by blending feet back onto the gait
            self.blend = ({n: (self.prev[n][1] - walk_y, self.prev[n][2]) for n in FEET}, 0.0)
            self.pinned = {}
        blend = getattr(self, "blend", None)
        if blend is not None and gait != "plant":
            frm, bt = blend
            bt += dt
            k = smooth(min(1, bt / 0.35))
            for n in FEET:
                fx, fy, fz = feet[n]
                by, bz = frm[n]
                feet[n] = (fx, walk_y + by + (fy - walk_y - by) * k, bz + (fz - bz) * k + 0.06 * math.sin(math.pi * k))
            self.blend = None if k >= 1 else (frm, bt)
        self.prev = feet
        for n in FEET:
            target = 1.0 if feet[n][2] < 0.01 else 0.0
            self.load[n] += (target - self.load[n]) * (1 - math.exp(-dt / 0.08))
        run = 0.0 if gait == "plant" else 0.6
        push = {n: (smooth(max(0.0, (stance[n] - 0.6) / 0.4)) if stance[n] is not None else 0.0) for n in FEET}
        sway_f = 0.07 * (self.load["FR"] - self.load["FL"]) * run
        sway_h = 0.09 * (self.load["HR"] - self.load["HL"]) * run + 0.04 * run * (push["HL"] - push["HR"])
        surge = 0.05 * run * (push["HL"] + push["HR"])
        # a strike throws the shoulders toward the striking side's opposite: the body winds against the blade
        twist = 0.0
        for side, (a, reach) in blades.items():
            twist += (1 if side == "R" else -1) * 0.12 * max(-0.2, min(1.0, a / 205))
        sh = (sway_f + twist, y + SHOULDER_Y + surge * 0.4, SHOULDER_Z - crouch)
        hp = (sway_h - 0.3 * twist, walk_y + HIP_Y + surge, HIP_Z - crouch * 1.5)
        legs = {}
        for n in FEET:
            root = sh if n[0] == "F" else hp
            fx, fy, fz = feet[n]
            rx = fx + root[0]
            if n[0] == "F":
                elbow, foot = ik2((root[1], root[2]), (fy, fz), FRONT["a"], FRONT["b"], -1)
                legs[n] = [(rx, root[1], root[2]), (rx, *elbow), (fx, *foot)]
            else:
                stifle, hock = ik2((root[1], root[2]), (fy - 0.04, fz + HIND["c"]), HIND["a"], HIND["b"], 1)
                legs[n] = [(rx, root[1], root[2]), (rx, *stifle), (rx, *hock), (fx, fy, fz)]
        return dict(t=t, y=walk_y, gait=gait, phase=phase, feet=feet, legs=legs, load=dict(self.load), sh=sh, hp=hp,
                    crouch=crouch, head=head, blades=blades, run=run)


def simulate(clip, n=None):
    sk = MatriarchSkeleton(clip)
    n = n or int(T_END * FPS)
    return [sk.frame(f / FPS, 1 / FPS) for f in range(n)]


def body_offsets(s):
    """Lateral (x) and along-body (y, + forward) offsets, in cells, of the shoulders and the hips."""
    sh, hp, y = s["sh"], s["hp"], s["y"]
    return (sh[0], sh[1] - (y + SHOULDER_Y)), (hp[0], hp[1] - (y + HIP_Y))
