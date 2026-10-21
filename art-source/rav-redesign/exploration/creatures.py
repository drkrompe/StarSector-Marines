"""The three Rav Bloom design directions as procedural rigs.

Every builder takes a state dict:
  w      walk phase in [0,1) (one gait cycle)
  move   0..1 how much the gait is applied
  s      strike/spit progress in [0,1) or None
  tau    seconds, for idle secondary motion
and returns rig.Part lists in cell units, facing up.

A  FRUITING  - the Bloom as a walking fungal fruiting body
B  TIDE      - the Bloom as a red-tide colonial organism (siphonophore-like)
C  SEEDCASE  - the Bloom as an engineered sporeship seed-machine
"""
import math

from rig import (Part, add, capsule, chain, clamp, d2v, ellipse, lagged_pose, lerp, lerpc,
                 seg_parts, smooth, strike_e, taper)

TAU = 2 * math.pi


def st(w=0.0, move=0.0, s=None, tau=0.0):
    return dict(w=w, move=move, s=s, tau=tau)


def _mirror(p):
    return (-p[0], p[1])


# =====================================================================
# Palettes
# =====================================================================
PAL = {
    "A": dict(  # FRUITING: bruised umber caps, crimson gill lips, pale hyphae
        cap=(96, 58, 60), cap2=(120, 70, 86), ring=(62, 34, 42), lip=(214, 48, 58),
        hypha=(206, 176, 168), tip=(228, 70, 74), spore=(244, 214, 196), dark=(40, 22, 28),
        creep=(70, 44, 52), creep2=(120, 52, 66)),
    "B": dict(  # TIDE: wet carmine flesh, oxblood cores, vermilion floats, pale stingers
        flesh=(178, 36, 44), core=(104, 14, 28), float=(255, 92, 48), sting=(255, 206, 196),
        fringe=(232, 110, 112), dark=(46, 6, 14), plaque=(226, 206, 196),
        creep=(54, 16, 24), creep2=(132, 26, 38)),
    "C": dict(  # SEEDCASE: green-black lacquered husk, seed-bone ridges, red joint tissue
        husk=(46, 58, 50), husk2=(64, 80, 66), bone=(220, 208, 178), joint=(200, 36, 46),
        pod=(214, 54, 60), dark=(20, 26, 22), whisker=(206, 196, 160),
        creep=(48, 58, 44), creep2=(150, 40, 46)),
}


# =====================================================================
# Shared pieces
# =====================================================================
def trailing(root, base_abs, n, seg, r0, r1, col, z, w, tau, amp=16, phase=0.0, width=False):
    rels = []
    for j in range(n):
        rels.append((0 if j == 0 else 0) + amp * math.sin(TAU * (w + tau * 0.35) - j * 1.1 + phase))
    pts, _ = chain(root, base_abs, rels, [seg] * n)
    return seg_parts(pts, taper(r0, r1, n), col, z, outline=0.012, hi=0.6)


def gait_leg(root, rest_abs, swing, phase, l1, l2, bend, r0, r1, col, z, tipcol=None, lift=0.85):
    """Two-segment leg stepping around rest_abs; phase in [0,1)."""
    sw = math.sin(TAU * phase)
    a = rest_abs + swing * sw
    k = lift if math.cos(TAU * phase) > 0 else 1.0  # lifted on the forward swing
    pts, _ = chain(root, a, [0, bend], [l1 * k, l2 * k])
    parts = seg_parts(pts, [r0, (r0 + r1) / 2, r1], col, z, outline=0.012, hi=0.5)
    if tipcol:
        parts.append(Part(ellipse(pts[-1], r1 * 1.3, r1 * 1.3), tipcol, z + 0.05, outline=0.008, shade=False))
    return parts


# =====================================================================
# A  FRUITING
# =====================================================================
def A_runner(S):
    P = PAL["A"]
    w, mv, s, tau = S["w"], S["move"], S["s"], S["tau"]
    e = strike_e(s)
    lunge = 0.13 * clamp(e) - 0.06 * clamp(-e)
    bob = 0.015 * math.sin(TAU * 2 * w) * mv
    parts = []
    body_c = (0, 0.02 - lunge + bob)
    # four root stilts, gallop (front pair together, rear pair together)
    for side in (-1, 1):
        for front, ph in ((True, 0.0), (False, 0.5)):
            root = (side * 0.11, body_c[1] + (-0.08 if front else 0.12))
            rest = side * (55 if front else 128)
            parts += gait_leg(root, rest, 26 * mv * side, w + ph + (0.08 if side > 0 else 0),
                              0.15, 0.17, side * (35 if front else -35), 0.03, 0.014, P["hypha"], 1)
    # rhizome trail
    for k, a in enumerate((168, 180, 192)):
        parts += trailing((0.04 * (k - 1), body_c[1] + 0.2), a, 4, 0.08, 0.022, 0.006, P["hypha"], 2,
                          w * mv, tau, amp=14 + 10 * mv, phase=k)
    # cap
    sw = 1 + 0.04 * math.sin(TAU * tau * 0.9) - 0.05 * clamp(-e) + 0.03 * clamp(e)
    cap = ellipse(body_c, 0.20 * sw, 0.27 * sw, wob=[(0.05, 3, 0.4), (0.03, 5, 1.0)])
    parts.append(Part(cap, P["cap"], 5))
    for r in (0.7, 0.45):
        parts.append(Part(ellipse(body_c, 0.2 * r * sw, 0.27 * r * sw), P["ring"], 5.1, kind="line",
                          width=0.012, outline=0, shadow=False))
    # crimson gill lip along the front of the cap
    lip = [(body_c[0] + 0.19 * math.cos(t), body_c[1] - 0.25 * abs(math.sin(t)) * sw)
           for t in [math.pi * (0.15 + 0.7 * i / 10) for i in range(11)]]
    parts.append(Part(lip, P["lip"], 5.3, kind="line", width=0.035, outline=0.01, glow=0.25))
    # two hyphal whips with frayed tips
    lens = [0.17, 0.16, 0.15, 0.13]
    for side, lag in ((-1, 0.0), (1, 0.05)):
        rest = [-38, -18, 26, 34]
        coil = [-105, -55, -45, -40]
        ext = [-16, 3, 2, 1]
        wob = [5 * math.sin(TAU * tau * 0.7 - j * 0.9 + side) for j in range(4)]
        ss = None if s is None else s - lag
        rels = lagged_pose(rest, coil, ext, ss, wobble=wob)
        rels = [r * (-side) for r in rels]
        root = (side * 0.07, body_c[1] - 0.22)
        pts, angs = chain(root, 0, rels, lens)
        parts += seg_parts(pts, taper(0.034, 0.014, 4), P["hypha"], 6, outline=0.012, hi=0.6)
        es = strike_e(ss) if ss is not None else 0
        spread = 10 + 32 * clamp(es - 0.7, 0, 0.5) * 2
        for f in (-2, -1, 0, 1, 2):
            fp, _ = chain(pts[-1], angs[-1], [f * spread * 0.5, f * 6], [0.06, 0.04])
            parts.append(Part(fp, P["tip"], 6.5, kind="line", width=0.01, outline=0.004))
        # spore puff on impact
        if ss is not None and 0.42 < ss < 0.75:
            u = (ss - 0.42) / 0.33
            for i in range(7):
                a = angs[-1] + (i - 3) * 22
                q = add(pts[-1], d2v(a), 0.05 + 0.16 * u)
                parts.append(Part([q], P["spore"], 9, kind="dots", width=0.02 * (1 - u) + 0.006,
                                  shadow=False, alpha=int(230 * (1 - u))))
    return parts


def A_brute(S):
    P = PAL["A"]
    s, tau, w, mv = S["s"], S["tau"], S["w"], S["move"]
    e = strike_e(s)
    parts = []
    c = (0, 0.06 - 0.1 * clamp(e))
    for side in (-1, 1):
        for i, (dy, rest) in enumerate(((-0.12, 70), (0.05, 100), (0.2, 130))):
            parts += gait_leg((side * 0.36, c[1] + dy), side * rest, 14 * mv * side, w + i / 3 + (side > 0) * 0.5,
                              0.12, 0.12, side * 20, 0.05, 0.03, P["hypha"], 1)
    # bracket shelf: a half-disc with a flat, thick front lip
    shelf = []
    for i in range(31):
        t = math.pi * i / 30
        shelf.append((c[0] - 0.55 * math.cos(t), c[1] - 0.12 + 0.42 * math.sin(t)))
    shelf.append((c[0] + 0.5, c[1] - 0.2))
    shelf.append((c[0] - 0.5, c[1] - 0.2))
    parts.append(Part(shelf, P["cap"], 5, outline=0.03))
    for k in (0.75, 0.52, 0.3):
        arc = [(c[0] - 0.55 * k * math.cos(math.pi * i / 20), c[1] - 0.12 + 0.42 * k * math.sin(math.pi * i / 20))
               for i in range(21)]
        parts.append(Part(arc, P["ring"], 5.1, kind="line", width=0.018, outline=0, shadow=False))
    lip = [(c[0] - 0.52 + 1.04 * i / 12, c[1] - 0.2 + 0.03 * math.sin(i * 1.7)) for i in range(13)]
    parts.append(Part(lip, P["lip"], 5.4, kind="line", width=0.07, outline=0.015, glow=0.2))
    # two trunk arms that slam forward
    for side, lag in ((-1, 0), (1, 0.04)):
        rest = [-20, 18]
        coil = [-55, 60]
        ext = [-4, 2]
        ss = None if s is None else s - lag
        rels = [r * (-side) for r in lagged_pose(rest, coil, ext, ss)]
        pts, _ = chain((side * 0.3, c[1] - 0.22), 0, rels, [0.24, 0.22])
        parts += seg_parts(pts, [0.09, 0.075, 0.1], P["cap2"], 6, outline=0.02)
        parts.append(Part(ellipse(pts[-1], 0.1, 0.1), P["lip"], 6.2, glow=0.15))
    return parts


def A_spitter(S):
    P = PAL["A"]
    s, tau, w, mv = S["s"], S["tau"], S["w"], S["move"]
    e = strike_e(s)
    parts = []
    for i, a in enumerate((0, 120, 240)):
        root = add((0, 0.04), d2v(a + 60), 0.12)
        parts += gait_leg(root, a + 60, 20 * mv, w + i / 3, 0.2, 0.18, 30, 0.028, 0.012, P["hypha"], 1)
    puff = 1 + 0.05 * math.sin(TAU * tau * 1.2) + 0.12 * clamp(-e) - 0.2 * clamp(e)
    parts.append(Part(ellipse((0, 0.04), 0.24 * puff, 0.24 * puff, wob=[(0.04, 6, 0.0)]), P["cap2"], 5,
                      outline=0.02))
    parts.append(Part(ellipse((0, 0.04), 0.24 * puff, 0.24 * puff, wob=[(0.04, 6, 0.0)]), P["cap2"], 5.05,
                      kind="line", width=0.03, outline=0, shadow=False))
    for i in range(12):
        a = i * 30
        parts.append(Part([add((0, 0.04), d2v(a), 0.06 * puff), add((0, 0.04), d2v(a), 0.2 * puff)], P["spore"], 5.2,
                          kind="line", width=0.008, outline=0, shadow=False))
    parts.append(Part(ellipse((0, -0.17 * puff + 0.04), 0.06, 0.05), P["lip"], 5.5, glow=0.5))
    return parts


def A_matriarch(S):
    P = PAL["A"]
    parts = []
    for i in range(9):
        a = i * 40
        c = d2v(a)
        c = (c[0] * 0.75, c[1] * 0.75)
        parts.append(Part(ellipse(c, 0.26, 0.3, rot=a, wob=[(0.06, 3, i)]), P["cap"] if i % 2 else P["cap2"], 5))
        parts.append(Part([add(c, d2v(a), -0.2), add(c, d2v(a), 0.27)], P["lip"], 5.2, kind="line", width=0.04,
                          glow=0.2))
    parts.append(Part(ellipse((0, 0), 0.42, 0.42), P["dark"], 4))
    parts.append(Part(ellipse((0, 0), 0.22, 0.22), P["spore"], 6, glow=0.2))
    return parts


# =====================================================================
# B  TIDE
# =====================================================================
def B_runner(S):
    P = PAL["B"]
    w, mv, s, tau = S["w"], S["move"], S["s"], S["tau"]
    e = strike_e(s)
    lunge = 0.15 * clamp(e) - 0.07 * clamp(-e)
    stretch = 1 + 0.16 * clamp(e) - 0.14 * clamp(-e)
    sway_amp = 0.05 * mv * (1 - 0.7 * abs(e)) + 0.008
    radii = [0.13, 0.15, 0.135, 0.11, 0.085, 0.06]
    beads = []
    y = -0.2 - lunge
    for i, r in enumerate(radii):
        x = sway_amp * math.sin(TAU * (w + tau * 0.25) - i * 0.95) * (0.2 + i * 0.25)
        beads.append((x, y))
        y += (r + (radii[i + 1] if i + 1 < len(radii) else r)) * 0.62 * stretch
    parts = []
    # metachronal fringe rowers along both sides
    for i in range(1, 5):
        bx, by = beads[i]
        for side in (-1, 1):
            for k in range(2):
                ph = TAU * (2 * w + tau * 0.6) - i * 1.3 - k * 0.6
                a = side * (100 + 22 * k) + side * 28 * math.sin(ph) * (0.4 + mv)
                root = (bx + side * radii[i] * 0.8, by + (k - 0.5) * 0.05)
                tip = add(root, d2v(a), 0.075)
                parts.append(Part([root, tip], P["fringe"], 1, kind="line", width=0.014, outline=0.006))
    # trailing filaments
    tail = beads[-1]
    for k, a in enumerate((165, 180, 195)):
        parts += trailing(tail, a + 30 * (beads[-1][0] - beads[-3][0]) * 10, 5, 0.075, 0.016, 0.005, P["fringe"], 2,
                          w * mv, tau, amp=10 + 14 * mv, phase=k * 0.8)
    # bead chain body
    for i in range(len(beads) - 1, -1, -1):
        parts.append(Part(ellipse(beads[i], radii[i], radii[i] * 0.92), P["flesh"], 5 + (len(beads) - i) * 0.01,
                          outline=0.02))
        parts.append(Part(ellipse(beads[i], radii[i] * 0.45, radii[i] * 0.4), P["core"], 5.05 + (len(beads) - i) * 0.01,
                          outline=0, shade=False, shadow=False))
    # dorsal float (pulses, swells in anticipation, empties on the lash)
    fl = 1 + 0.08 * math.sin(TAU * tau * 1.4) + 0.22 * clamp(-e) - 0.15 * clamp(e)
    fc = ((beads[1][0] + beads[2][0]) / 2, (beads[1][1] + beads[2][1]) / 2)
    parts.append(Part(ellipse(fc, 0.085 * fl, 0.12 * fl), P["float"], 5.6, outline=0.012, glow=0.55))
    # mouth with palps
    hx, hy = beads[0]
    parts.append(Part(ellipse((hx, hy - 0.07), 0.05, 0.03), P["dark"], 5.7, outline=0, shade=False))
    # two long grasping tentacles: rest curled along the sides, coil tight, then whip
    lens = [0.19, 0.18, 0.17, 0.15, 0.12]
    for side, lag in ((-1, 0.0), (1, 0.06)):
        rest = [-112, -26, -28, -26, -22]
        coil = [-150, -42, -44, -40, -30]
        ext = [-22, 3, 2, 2, 1]
        wob = [7 * math.sin(TAU * tau * 0.8 - j * 0.9 + side * 1.3) * (1 if s is None else 0.4) for j in range(5)]
        ss = None if s is None else s - lag
        rels = [r * (-side) for r in lagged_pose(rest, coil, ext, ss, lag=0.026, wobble=wob)]
        root = (hx + side * 0.09, hy - 0.04)
        pts, angs = chain(root, 0, rels, lens)
        parts += seg_parts(pts, taper(0.04, 0.014, 5), P["flesh"], 6, outline=0.014, hi=0.7)
        # stinger stipple on the last two segments
        dots = []
        for j in (3, 4):
            for u in (0.25, 0.6, 0.9):
                dots.append((lerp(pts[j][0], pts[j + 1][0], u), lerp(pts[j][1], pts[j + 1][1], u)))
        parts.append(Part(dots, P["sting"], 6.6, kind="dots", width=0.008, shadow=False))
        # barbed hook tip
        hp, _ = chain(pts[-1], angs[-1], [side * 50], [0.05])
        parts.append(Part(hp, P["sting"], 6.7, kind="line", width=0.012, outline=0.005))
    return parts


def B_brute(S):
    P = PAL["B"]
    s, tau, w, mv = S["s"], S["tau"], S["w"], S["move"]
    e = strike_e(s)
    parts = []
    c = (0, 0.05 - 0.1 * clamp(e) + 0.04 * clamp(-e))
    # short thick pseudopod fringe all round the raft
    for i in range(14):
        a = 360 * i / 14 + 6 * math.sin(TAU * (w * 2 + tau * 0.3) - i)
        root = add(c, d2v(a), 0.42)
        parts.append(Part(capsule(root, add(root, d2v(a + 10 * math.sin(TAU * w * 2 - i) * (0.3 + mv)), 0.1), 0.05, 0.03),
                          P["fringe"], 1, outline=0.012, hi=0.5))
    # raft: overlapping bladders
    blobs = [((0, 0), 0.34, 0.3), ((-0.24, 0.08), 0.2, 0.2), ((0.24, 0.08), 0.2, 0.2), ((-0.15, -0.18), 0.17, 0.15),
             ((0.15, -0.18), 0.17, 0.15), ((0, 0.24), 0.22, 0.16), ((-0.3, -0.06), 0.13, 0.13), ((0.3, -0.06), 0.13, 0.13)]
    for k, (o, rx, ry) in enumerate(sorted(blobs, key=lambda b: -b[0][1])):
        pul = 1 + 0.04 * math.sin(TAU * tau * 0.7 + k)
        parts.append(Part(ellipse(add(c, o), rx * pul, ry * pul), P["flesh"] if k % 2 else P["core"], 5 + k * 0.01,
                          outline=0.022))
    # calcified plaques (the plate)
    for o, r in (((-0.12, -0.02), 0.11), ((0.13, 0.0), 0.1), ((0, 0.16), 0.09), ((0, -0.15), 0.08)):
        parts.append(Part(ellipse(add(c, o), r, r * 0.8, wob=[(0.12, 5, o[0] * 9)]), P["plaque"], 5.5, outline=0.014,
                          hi=0.4))
    # two club tentacles fused into a ram
    for side, lag in ((-1, 0.0), (1, 0.035)):
        rest = [-30, 40, 40]
        coil = [-70, 70, 60]
        ext = [-6, 3, 2]
        ss = None if s is None else s - lag
        rels = [r * (-side) for r in lagged_pose(rest, coil, ext, ss)]
        pts, angs = chain(add(c, (side * 0.18, -0.3)), 0, rels, [0.2, 0.18, 0.15])
        parts += seg_parts(pts, [0.085, 0.075, 0.07, 0.1], P["flesh"], 6, outline=0.02)
        parts.append(Part(ellipse(pts[-1], 0.09, 0.09, wob=[(0.2, 4, 0)]), P["plaque"], 6.3, outline=0.014))
    return parts


def B_spitter(S):
    P = PAL["B"]
    s, tau, w, mv = S["s"], S["tau"], S["w"], S["move"]
    e = strike_e(s)
    parts = []
    # skirt of trailing tentacles
    for k in range(7):
        a = 180 + (k - 3) * 17
        parts += trailing(add((0, 0.12), d2v(a), 0.14), a, 6, 0.07, 0.018, 0.005, P["fringe"], 1,
                          w * mv, tau, amp=10 + 10 * mv, phase=k * 0.7)
    # small body under the float with a forward siphon
    recoil = 0.06 * clamp(e)
    parts.append(Part(ellipse((0, 0.0 + recoil), 0.13, 0.17), P["core"], 4, outline=0.02))
    sip = [(0, -0.12 + recoil), (0, -0.27 + recoil + 0.05 * clamp(-e))]
    parts += seg_parts(sip, [0.05, 0.035], P["flesh"], 4.5, outline=0.014)
    # float: big, glowing, inflates in anticipation and empties on the spit
    fl = 1 + 0.06 * math.sin(TAU * tau * 1.1) + 0.2 * clamp(-e) - 0.24 * clamp(e)
    fc = (0, 0.07 + recoil)
    parts.append(Part(ellipse(fc, 0.24 * fl, 0.28 * fl, wob=[(0.03, 3, 0.5)]), P["float"], 5, outline=0.02, glow=0.75,
                      alpha=235))
    crest = [(0.0, fc[1] - 0.24 * fl), (0.03 * math.sin(TAU * tau), fc[1]), (0.0, fc[1] + 0.25 * fl)]
    parts.append(Part(crest, P["sting"], 5.3, kind="line", width=0.03, outline=0.008, shadow=False))
    if s is not None and 0.38 < s < 0.6:
        u = (s - 0.38) / 0.22
        parts.append(Part(ellipse((0, -0.3 - 0.5 * u), 0.06, 0.08), (255, 140, 60), 9, glow=0.9, shadow=False))
    return parts


def B_matriarch(S):
    P = PAL["B"]
    tau, w = S["tau"], S["w"]
    parts = []
    for k in range(16):
        a = k * 22.5
        parts += trailing(add((0, 0), d2v(a), 0.8), a, 6, 0.12, 0.03, 0.008, P["fringe"], 1, w, tau, amp=12, phase=k)
    for k in range(8):
        a = k * 45 + 22
        parts.append(Part(ellipse(add((0, 0), d2v(a), 0.62), 0.24, 0.24), P["float"], 5.5, glow=0.6, outline=0.02))
    parts.append(Part(ellipse((0, 0), 0.8, 0.75, wob=[(0.05, 5, 0)]), P["flesh"], 5, outline=0.03))
    parts.append(Part(ellipse((0, 0), 0.32, 0.3), P["core"], 5.2, outline=0.02))
    parts.append(Part(ellipse((0, 0), 0.14, 0.1), P["dark"], 5.3, outline=0, shade=False))
    for o in ((-0.45, 0.1), (0.45, 0.1), (0, 0.5), (0, -0.48)):
        parts.append(Part(ellipse(o, 0.13, 0.1), P["plaque"], 5.4, outline=0.014))
    return parts


# =====================================================================
# C  SEEDCASE
# =====================================================================
def C_runner(S):
    P = PAL["C"]
    w, mv, s, tau = S["w"], S["move"], S["s"], S["tau"]
    e = strike_e(s)
    lunge = 0.1 * clamp(e) - 0.05 * clamp(-e)
    tick = 4 * math.sin(TAU * 3 * w) * mv  # the tripod gait ticks the body
    c = (0, 0.05 - lunge)
    parts = []
    # tripod legs, stepping in sequence
    for i, a in enumerate((60, 180, 300)):
        root = add(c, d2v(a + tick), 0.13)
        parts += gait_leg(root, a + tick, 24 * mv, w + i / 3, 0.17, 0.19, 38 if a != 180 else 0, 0.034, 0.018,
                          P["husk2"], 1, tipcol=P["bone"])
        parts.append(Part(ellipse(root, 0.035, 0.035), P["joint"], 1.5, outline=0.008, shade=False))
    # rear seed pod
    pod = 1 + 0.07 * math.sin(TAU * tau * 1.3) + 0.12 * clamp(-e)
    parts.append(Part(ellipse(add(c, (0, 0.19)), 0.09 * pod, 0.11 * pod), P["pod"], 4, outline=0.016, glow=0.35))
    # three-lobed husk
    for i, a in enumerate((0 + tick, 120 + tick, 240 + tick)):
        o = add(c, d2v(a), 0.095)
        parts.append(Part(ellipse(o, 0.12, 0.15, rot=a), P["husk"], 5 + i * 0.01, outline=0.022))
        parts.append(Part([add(o, d2v(a), -0.06), add(o, d2v(a), 0.12)], P["bone"], 5.3, kind="line", width=0.022,
                          outline=0.006, shadow=False))
    for a in (60 + tick, 180 + tick, 300 + tick):
        parts.append(Part([c, add(c, d2v(a), 0.17)], P["joint"], 5.2, kind="line", width=0.018, outline=0,
                          shadow=False, glow=0.3))
    parts.append(Part(ellipse(c, 0.06, 0.06, n=6), P["bone"], 5.4, outline=0.01))
    # sensor whiskers
    for side in (-1, 1):
        a = side * (28 + 6 * math.sin(TAU * tau * 2.2 + side))
        parts.append(Part([add(c, (side * 0.05, -0.2)), add(c, add((side * 0.05, -0.2), d2v(a), 0.14))], P["whisker"],
                          6.5, kind="line", width=0.008, outline=0.003))
    # switchblade lance: folded Z on the back, unfolds through the side and stabs
    rest = [150, -165, 165]
    coil = [168, -175, 172]
    ext = [-4, 2, 2]
    wob = [3 * math.sin(TAU * tau * 0.6 - j) for j in range(3)]
    rels = lagged_pose(rest, coil, ext, s, lag=0.03, wobble=wob)
    pts, angs = chain(add(c, (0.05, -0.2)), 0, rels, [0.22, 0.24, 0.2])
    parts += seg_parts(pts, [0.042, 0.036, 0.03, 0.02], P["husk2"], 6, outline=0.014)
    for q in pts[1:-1]:
        parts.append(Part(ellipse(q, 0.03, 0.03), P["joint"], 6.3, outline=0.006, shade=False, glow=0.3))
    spike, _ = chain(pts[-1], angs[-1], [0], [0.11])
    parts.append(Part(capsule(spike[0], spike[1], 0.022, 0.004), P["bone"], 6.4, outline=0.008))
    return parts


def C_brute(S):
    P = PAL["C"]
    s, tau, w, mv = S["s"], S["tau"], S["w"], S["move"]
    e = strike_e(s)
    parts = []
    c = (0, 0.04 - 0.06 * clamp(e))
    for i, (o, a) in enumerate((((-0.3, -0.1), -70), ((0.3, -0.1), 70), ((-0.3, 0.2), -115), ((0.3, 0.2), 115))):
        parts += gait_leg(add(c, o), a, 14 * mv, w + i * 0.25, 0.13, 0.12, 0, 0.06, 0.04, P["husk2"], 1,
                          tipcol=P["bone"])
    parts.append(Part(ellipse(c, 0.38, 0.5, wob=[(0.03, 2, 1.5)]), P["husk"], 5, outline=0.03))
    for k in range(5):
        x = -0.24 + 0.12 * k
        parts.append(Part([add(c, (x, -0.36 + abs(x) * 0.5)), add(c, (x * 1.1, 0.36 - abs(x) * 0.4))], P["husk2"], 5.1,
                          kind="line", width=0.02, outline=0, shadow=False))
    cap = ellipse(add(c, (0, -0.32)), 0.3, 0.17)
    parts.append(Part(cap, P["bone"], 5.4, outline=0.02))
    for k in range(5):
        parts.append(Part([add(c, (-0.2 + 0.1 * k, -0.42)), add(c, (-0.2 + 0.1 * k, -0.25))], P["husk"], 5.5,
                          kind="line", width=0.014, outline=0, shadow=False))
    parts.append(Part(ellipse(add(c, (0, 0.34)), 0.12, 0.08), P["pod"], 5.6, glow=0.3))
    # hammer pistons telescope out
    for side, lag in ((-1, 0), (1, 0.05)):
        ss = None if s is None else s - lag
        es = strike_e(ss) if ss is not None else 0
        ext = 0.12 + 0.28 * clamp(es) - 0.05 * clamp(-es)
        root = add(c, (side * 0.3, -0.25))
        p1 = add(root, d2v(side * 10), 0.14)
        p2 = add(p1, d2v(side * 4), ext)
        parts += seg_parts([root, p1], [0.07, 0.065], P["husk2"], 6, outline=0.02)
        parts += seg_parts([p1, p2], [0.045, 0.045], P["joint"], 5.9, outline=0.012)
        parts.append(Part(ellipse(p2, 0.075, 0.06), P["bone"], 6.2, outline=0.016))
    return parts


def C_spitter(S):
    P = PAL["C"]
    s, tau, w, mv = S["s"], S["tau"], S["w"], S["move"]
    e = strike_e(s)
    parts = []
    for i, a in enumerate((60, 180, 300)):
        parts += gait_leg(add((0, 0.05), d2v(a), 0.15), a, 18 * mv, w + i / 3, 0.2, 0.2, 30 if a != 180 else 0, 0.03,
                          0.016, P["husk2"], 1, tipcol=P["bone"])
    sac = 1 + 0.06 * math.sin(TAU * tau) + 0.25 * clamp(-e) - 0.3 * clamp(e)
    parts.append(Part(ellipse((0, 0.18), 0.17 * sac, 0.15 * sac), P["pod"], 4, outline=0.018, glow=0.5))
    parts.append(Part(ellipse((0, 0.03), 0.19, 0.19, n=6, rot=30), P["husk"], 5, outline=0.024))
    rec = 0.07 * clamp(e)
    barrel = [(0, 0.0 + rec), (0, -0.34 + rec)]
    parts += seg_parts(barrel, [0.07, 0.06], P["husk2"], 6, outline=0.02)
    parts.append(Part(ellipse((0, -0.34 + rec), 0.065, 0.065), P["bone"], 6.1, outline=0.014))
    parts.append(Part(ellipse((0, -0.34 + rec), 0.03, 0.03), P["dark"], 6.2, outline=0, shade=False))
    for a in (0, 120, 240):
        parts.append(Part([(0, 0.03), add((0, 0.03), d2v(a + 60), 0.16)], P["bone"], 5.2, kind="line", width=0.02,
                          outline=0.005, shadow=False))
    if s is not None and 0.38 < s < 0.6:
        u = (s - 0.38) / 0.22
        parts.append(Part(ellipse((0, -0.4 - 0.5 * u), 0.05, 0.07), (230, 70, 60), 9, glow=0.8, shadow=False))
    return parts


def C_matriarch(S):
    P = PAL["C"]
    parts = []
    for i in range(6):
        a = i * 60 + 30
        parts += gait_leg(add((0, 0), d2v(a), 0.6), a, 0, 0, 0.35, 0.3, 25, 0.07, 0.04, P["husk2"], 1,
                          tipcol=P["bone"])
    parts.append(Part(ellipse((0, 0), 0.75, 0.75, n=6), P["husk"], 5, outline=0.03))
    for i in range(6):
        a = i * 60
        o = add((0, 0), d2v(a), 0.45)
        parts.append(Part(ellipse(o, 0.14, 0.14, n=6), P["pod"], 5.3, glow=0.4, outline=0.016))
        parts.append(Part([(0, 0), add((0, 0), d2v(a + 30), 0.72)], P["bone"], 5.2, kind="line", width=0.03,
                          shadow=False))
    parts.append(Part(ellipse((0, 0), 0.2, 0.2, n=6), P["bone"], 5.5, outline=0.02))
    return parts


DIRECTIONS = {
    "A": dict(name="FRUITING", runner=A_runner, brute=A_brute, spitter=A_spitter, matriarch=A_matriarch),
    "B": dict(name="RED TIDE", runner=B_runner, brute=B_brute, spitter=B_spitter, matriarch=B_matriarch),
    "C": dict(name="SEEDCASE", runner=C_runner, brute=C_brute, spitter=C_spitter, matriarch=C_matriarch),
}
