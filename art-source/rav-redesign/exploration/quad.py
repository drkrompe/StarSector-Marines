"""Insect crossed with a quadruped monster (owner, 2026-10-21).

A predator's four-legged stance and gait read from above (shoulders,
haunches, a striding leg cycle) with insect anatomy on top: a segmented chitin
carapace, mandibles, compound-eye clusters and an extra pair of jointed
forelimbs carried off the shoulders as the arms. Browns, creams and chitin:
near-black plates, cream plate edges, sutures and joint membranes, a lacquer
sheen, and amber only in the eye clusters and the Spitter's vent.

Two variants of the one direction:
  Q1 HOUND-MANTIS  lean, long-legged, long scythe arms, a bounding lope
  Q2 BEETLE-BEAST  broad domed carapace, shorter legs, heavy hooked arms

Every builder takes the same state dict as creatures.st(); S["s"] drives the
strain's action: Runner strike, Brute charge, Spitter rear-and-lob. S may also
carry "chip" (0..1) for the Brute's spent plate.
"""
import math

from chitin import CHITIN
from creatures import DIRECTIONS, PAL
from rig import Part, add, capsule, chain, clamp, d2v, ellipse, lagged_pose, lerp, poly_xform, seg_parts, strike_e

TAU = 2 * math.pi
C = CHITIN
EDGE = C["cream"]


def plate(pts, col, z, outline=0.016, sheen=0.6, edge=EDGE, hi=0.5):
    return Part(pts, col, z, outline=outline, edge=edge, sheen=sheen, hi=hi)


def blade(p0, ang, length, width, side, z, curve=40, col=None):
    """A scythe: a tapering crescent curving toward the body's midline, its
    cutting (inner) edge picked out in cream."""
    col = col or C["plate2"]
    n = 8
    inward = -side
    outer, inner = [], []
    p = p0
    for i in range(n + 1):
        t = i / n
        a = ang + inward * curve * t
        w = width * (1 - t) ** 0.8
        nx, ny = d2v(a + inward * 90)
        outer.append((p[0] - nx * w * 0.35, p[1] - ny * w * 0.35))
        inner.append((p[0] + nx * w * 0.65, p[1] + ny * w * 0.65))
        p = add(p, d2v(a), length / n)
    parts = [Part(outer + inner[::-1], col, z, outline=0.012, edge=C["deep"], sheen=0.5, hi=0.5)]
    parts.append(Part(inner, EDGE, z + 0.01, kind="line", width=0.014, outline=0, shadow=False))
    return parts


def leg(root, rest, swing, ph, l1, l2, r0, bend, z, lift=0.82, stance=True):
    """Two-segment leg with a cream knee membrane and a cream claw.
    ph in [0,1): the first half swings forward (lifted), the second half is the
    planted stance sweeping back."""
    u = ph % 1.0
    if u < 0.5:  # swing: forward and lifted
        k = u / 0.5
        a = rest - swing + 2 * swing * (0.5 - 0.5 * math.cos(math.pi * k))
        lf = lift
    else:  # stance: planted, sweeping back
        k = (u - 0.5) / 0.5
        a = rest + swing - 2 * swing * k
        lf = 1.0
    if not stance:
        a, lf = rest, 1.0
    pts, angs = chain(root, a, [0, bend], [l1 * lf, l2 * lf])
    out = seg_parts(pts, [r0, r0 * 0.7, r0 * 0.35], C["plate2"], z, outline=0.012, hi=0.4)
    out.append(Part(ellipse(pts[1], r0 * 0.62, r0 * 0.62), EDGE, z + 0.05, outline=0, shade=False, shadow=False))
    claw, _ = chain(pts[2], angs[1], [bend * 0.4], [l2 * 0.22])
    out.append(Part(claw, EDGE, z + 0.04, kind="line", width=r0 * 0.5, outline=0.004))
    return out


GAITS = {
    # phase offsets for FL, FR, HL, HR
    "bound": (0.0, 0.07, 0.5, 0.57),
    "walk": (0.25, 0.75, 0.0, 0.5),
    "trot": (0.0, 0.5, 0.5, 0.0),
}


def quad(S, spec):
    w, mv, s, tau = S["w"], S["move"], S["s"], S["tau"]
    act = spec["action"]
    e = strike_e(s)
    parts = []

    # ---- whole-body motion from the action
    lunge = 0.0
    rear = 0.0
    if act == "strike":
        lunge = 0.08 * clamp(e) - 0.05 * clamp(-e)
    elif act == "charge":
        lunge = 0.42 * clamp(e) - 0.1 * clamp(-e)
    elif act == "rear":
        rear = clamp(-e) * 1.0 + clamp(e) * 0.3 * (1 - clamp(e))
    if act == "charge" and s is not None:
        mv = max(mv, 1.0)
        w = w + 2.2 * s

    # ---- spine
    tl, tw = spec["thorax"]
    hl, hw = spec["head"]
    gait = GAITS[spec["gait"]]
    flex = spec.get("flex", 0.06) * mv * math.sin(TAU * w * 1.0) if spec["gait"] == "bound" else 0.0
    sway = spec.get("sway", 0.03) * mv * math.sin(TAU * w) if spec["gait"] != "bound" else 0.0
    bob = 1 + 0.025 * mv * math.cos(TAU * 2 * w)
    ty = -lunge
    hy = ty - tl * 0.5 - hl * 0.42
    front = (0, ty)

    # ---- legs (drawn first, under the body)
    lf, lh = spec["leg_front"], spec["leg_hind"]
    abd = spec["abdomen"]
    hip_y = ty + tl * 0.5 + abd[0][0] * 0.35 + flex
    for side, (phF, phH) in ((-1, (gait[0], gait[2])), (1, (gait[1], gait[3]))):
        rootF = (side * tw * 0.42, ty - tl * 0.12)
        rootH = (side * abd[0][1] * 0.45 + sway, hip_y)
        parts += leg(rootF, side * lf["rest"], lf["swing"] * mv * side, w + phF, lf["l1"], lf["l2"], lf["r"],
                     side * lf["bend"], 1, stance=mv > 0)
        parts += leg(rootH, side * lh["rest"], lh["swing"] * mv * side, w + phH, lh["l1"], lh["l2"], lh["r"],
                     side * lh["bend"], 1, stance=mv > 0)
        # haunch and shoulder masses: what makes it read as a quadruped from above
        parts.append(plate(ellipse(add(rootH, (side * 0.02, 0.02)), lh["r"] * 2.1, lh["r"] * 2.6), C["plate2"], 2,
                           sheen=0.4))
        parts.append(plate(ellipse(add(rootF, (side * 0.01, 0.0)), lf["r"] * 1.8, lf["r"] * 2.0), C["plate2"], 2,
                           sheen=0.4))

    # ---- abdomen: overlapping banded segments, cream rims read as stripes
    y = ty + tl * 0.5 - 0.02 + flex
    for i, (L, W) in enumerate(abd):
        cy = y + L * 0.5
        x = sway * (1 + i * 0.6)
        pts = ellipse((x, cy), W * 0.5 * bob, L * 0.62, wob=[(0.03, 2, 0)])
        parts.append(plate(pts, C["plate"], 4 - i * 0.01, outline=0.02))
        y += L * 0.72

    # ---- dorsal acid sac (Spitter)
    if spec.get("sac"):
        sr = spec["sac"]
        sq = 1 + 0.06 * math.sin(TAU * tau * 1.1) + 0.25 * clamp(-e) - 0.3 * clamp(e)
        sc = (sway, ty + tl * 0.5 + abd[0][0] * 0.55 + flex)
        parts.append(plate(ellipse(sc, sr * 0.9 * sq, sr * sq), C["plate"], 4.5, outline=0.02))
        for k in (-0.55, 0.0, 0.55):
            arc = [(sc[0] + k * sr * 0.9 * sq * math.sin(math.pi * i / 10), sc[1] - sr * sq * math.cos(math.pi * i / 10))
                   for i in range(11)]
            parts.append(Part(arc, EDGE, 4.6, kind="line", width=0.024, outline=0, shadow=False))
        parts.append(Part(ellipse((sc[0], sc[1] - sr * sq * 0.92), 0.04, 0.03), C["amber"], 4.7, outline=0,
                          shade=False, glow=0.4, shadow=False))

    # ---- thorax: the shoulder carapace with a cream mid-suture
    thor = ellipse(front, tw * 0.5 * bob, tl * 0.55, wob=[(0.04, 3, 1.2)])
    parts.append(plate(thor, C["plate"], 5, outline=0.022))
    parts.append(Part([(0, ty - tl * 0.45), (0, ty + tl * 0.45)], EDGE, 5.1, kind="line", width=0.016, outline=0,
                      shadow=False))
    # armour plates on the shoulders (the Brute's chip as its plate is spent)
    for k, (ox, oy, r) in enumerate(spec.get("plates", [])):
        if S.get("chip", 0.0) * len(spec["plates"]) > k + 0.5:
            parts.append(Part(ellipse((ox, ty + oy), r * 0.8, r * 0.6, wob=[(0.25, 5, k)]), (86, 70, 56), 5.2,
                              outline=0.01, hi=0.2))
            continue
        parts.append(plate(ellipse((ox, ty + oy), r, r * 0.78, wob=[(0.08, 5, k)]), C["bone"], 5.2, outline=0.014,
                           edge=C["deep"], hi=0.4, sheen=0.3))

    # ---- head, mandibles, eye clusters, crest
    hpts = ellipse((0, hy), hw * 0.5, hl * 0.5, wob=[(0.05, 2, 0.0)])
    parts.append(plate(hpts, C["plate2"] if spec.get("pale_head") is None else spec["pale_head"], 6, outline=0.018))
    if spec.get("crest"):
        cw, ch = spec["crest"]
        dip = 0.05 * clamp(e) if act == "charge" else 0.0
        crest = [(-cw * 0.5, hy + hl * 0.1 - dip), (-cw * 0.42, hy - hl * 0.5 - ch - dip),
                 (0, hy - hl * 0.5 - ch * 1.35 - dip), (cw * 0.42, hy - hl * 0.5 - ch - dip), (cw * 0.5, hy + hl * 0.1 - dip)]
        parts.append(plate(crest, C["bone"], 6.3, outline=0.02, edge=C["deep"], hi=0.4, sheen=0.4))
        for k in (-1, 0, 1):
            parts.append(Part([(k * cw * 0.22, hy - hl * 0.4 - dip), (k * cw * 0.28, hy - hl * 0.5 - ch * 1.1 - dip)],
                              C["plate2"], 6.4, kind="line", width=0.014, outline=0, shadow=False))
    snap = clamp(e)
    gape = 30 + 25 * clamp(-e) - 30 * snap + 6 * math.sin(TAU * tau * 1.7)
    for side in (-1, 1):
        root = (side * hw * 0.22, hy - hl * 0.4)
        m, ma = chain(root, side * gape, [0, -side * 55], [spec["mand"] * 0.55, spec["mand"] * 0.5])
        parts += seg_parts(m, [0.022, 0.016, 0.006], C["plate2"], 6.6, outline=0.01, hi=0.4)
        parts.append(Part([m[1], m[2]], EDGE, 6.65, kind="line", width=0.008, outline=0, shadow=False))
        # compound-eye cluster: the one accent, and it says which way the thing faces
        for k, (ex, ey) in enumerate(((0.36, -0.12), (0.44, 0.02), (0.3, 0.06))):
            parts.append(Part(ellipse((side * hw * ex, hy + hl * ey), 0.022, 0.022), C["amber"], 6.7, outline=0.004,
                              edge=C["deep"], shade=False, glow=0.35, shadow=False))

    # ---- arms off the shoulders
    arm = spec.get("arms")
    if arm:
        for side, lag in ((-1, 0.0), (1, arm.get("lag", 0.045))):
            ss = None if (s is None or act != "strike" and act != "charge") else s - lag
            wob = [3 * math.sin(TAU * tau * 0.6 - j + side) for j in range(3)]
            rels = [r * (-side) for r in lagged_pose(arm["rest"], arm["coil"], arm["ext"], ss, lag=0.03, wobble=wob)]
            root = (side * tw * 0.36, ty - tl * 0.42)
            pts, angs = chain(root, 0, rels[:2], arm["lens"][:2])
            parts += seg_parts(pts, [arm["r"], arm["r"] * 0.8, arm["r"] * 0.6], C["plate2"], 7, outline=0.012, hi=0.4)
            for q in pts[1:]:
                parts.append(Part(ellipse(q, arm["r"] * 0.6, arm["r"] * 0.6), EDGE, 7.05, outline=0, shade=False,
                                  shadow=False))
            ba = angs[-1] + rels[2]
            if arm["kind"] == "scythe":
                parts += blade(pts[-1], ba, arm["lens"][2], arm["r"] * 2.0, side, 7.1, curve=arm.get("curve", 40))
            else:  # hook: a heavy grasping hand
                hk, _ = chain(pts[-1], ba, [0, -side * 70], [arm["lens"][2] * 0.6, arm["lens"][2] * 0.45])
                parts += seg_parts(hk, [arm["r"] * 1.2, arm["r"] * 0.8, arm["r"] * 0.3], C["plate2"], 7.1,
                                   outline=0.014, hi=0.4)
                parts.append(Part(hk[1:], EDGE, 7.15, kind="line", width=0.014, outline=0, shadow=False))

    # ---- spitter's glob
    if act == "rear" and s is not None and 0.38 < s < 0.62:
        u = (s - 0.38) / 0.24
        parts.append(Part(ellipse((0, hy - 0.2 - 0.7 * u), 0.05, 0.065), C["amber"], 9, glow=0.7, shadow=False,
                          outline=0.006, edge=C["deep"]))

    # ---- rearing: the front half rises toward the camera
    if rear > 0:
        k = 1 + 0.18 * rear
        piv = (0, ty + tl * 0.3)
        for p in parts:
            if p.z >= 5 and p.kind != "dots" and p.z < 9 and not (4.4 < p.z < 4.8):
                cy = sum(q[1] for q in p.pts) / len(p.pts)
                if cy < piv[1] + 0.02:
                    p.pts = [(piv[0] + (q[0] - piv[0]) * k, piv[1] + (q[1] - piv[1]) * k + 0.05 * rear) for q in p.pts]
    return parts


# =====================================================================
# Q1 HOUND-MANTIS
# =====================================================================
SCYTHE = dict(kind="scythe", lens=[0.2, 0.22, 0.26], r=0.032, curve=42,
              rest=[-30, 150, -158], coil=[-118, 70, 40], ext=[-14, 6, 6])
Q1 = dict(
    runner=dict(action="strike", gait="bound", head=(0.17, 0.16), thorax=(0.24, 0.26), mand=0.08,
                abdomen=[(0.16, 0.24), (0.13, 0.19), (0.1, 0.13)],
                leg_front=dict(l1=0.17, l2=0.2, r=0.032, rest=62, swing=30, bend=-40),
                leg_hind=dict(l1=0.19, l2=0.22, r=0.036, rest=125, swing=30, bend=40),
                arms=SCYTHE, flex=0.05),
    brute=dict(action="charge", gait="walk", head=(0.2, 0.26), thorax=(0.34, 0.5), mand=0.1,
               abdomen=[(0.22, 0.44), (0.17, 0.34), (0.12, 0.22)],
               leg_front=dict(l1=0.17, l2=0.17, r=0.055, rest=70, swing=20, bend=-30),
               leg_hind=dict(l1=0.17, l2=0.18, r=0.06, rest=118, swing=20, bend=30),
               crest=(0.36, 0.08),
               plates=[(-0.15, -0.06, 0.08), (0.15, -0.06, 0.08), (-0.12, 0.09, 0.07), (0.12, 0.09, 0.07),
                       (0.0, 0.0, 0.07)],
               arms=dict(kind="hook", lens=[0.15, 0.15, 0.14], r=0.045, rest=[-28, 120, 0], coil=[-60, 140, 0],
                         ext=[-10, 20, 0], lag=0.02)),
    spitter=dict(action="rear", gait="walk", head=(0.15, 0.15), thorax=(0.2, 0.24), mand=0.06,
                 abdomen=[(0.2, 0.3), (0.14, 0.24), (0.1, 0.16)],
                 leg_front=dict(l1=0.16, l2=0.19, r=0.03, rest=66, swing=24, bend=-40),
                 leg_hind=dict(l1=0.18, l2=0.2, r=0.034, rest=122, swing=24, bend=40),
                 sac=0.17),
)

# =====================================================================
# Q2 BEETLE-BEAST
# =====================================================================
HOOK = dict(kind="hook", lens=[0.17, 0.17, 0.17], r=0.04, rest=[-40, 130, 0], coil=[-110, 60, 0],
            ext=[-12, 10, 0])
Q2 = dict(
    runner=dict(action="strike", gait="trot", head=(0.16, 0.2), thorax=(0.24, 0.32), mand=0.11,
                abdomen=[(0.24, 0.34), (0.12, 0.24)],
                leg_front=dict(l1=0.15, l2=0.15, r=0.036, rest=65, swing=26, bend=-35),
                leg_hind=dict(l1=0.16, l2=0.16, r=0.04, rest=120, swing=26, bend=35),
                arms=HOOK),
    brute=dict(action="charge", gait="walk", head=(0.22, 0.32), thorax=(0.36, 0.56), mand=0.13,
               abdomen=[(0.32, 0.56), (0.18, 0.4)],
               leg_front=dict(l1=0.14, l2=0.14, r=0.06, rest=72, swing=16, bend=-25),
               leg_hind=dict(l1=0.14, l2=0.15, r=0.065, rest=115, swing=16, bend=25),
               crest=(0.44, 0.12),
               plates=[(-0.17, -0.05, 0.09), (0.17, -0.05, 0.09), (-0.14, 0.11, 0.08), (0.14, 0.11, 0.08),
                       (0.0, 0.03, 0.08), (0.0, -0.12, 0.06)],
               arms=dict(kind="hook", lens=[0.13, 0.13, 0.15], r=0.05, rest=[-30, 120, 0], coil=[-55, 140, 0],
                         ext=[-8, 20, 0], lag=0.02)),
    spitter=dict(action="rear", gait="walk", head=(0.14, 0.17), thorax=(0.2, 0.28), mand=0.07,
                 abdomen=[(0.3, 0.36), (0.12, 0.2)],
                 leg_front=dict(l1=0.14, l2=0.16, r=0.032, rest=66, swing=22, bend=-35),
                 leg_hind=dict(l1=0.15, l2=0.17, r=0.036, rest=120, swing=22, bend=35),
                 sac=0.2),
)


def matriarch(S, broad=False):
    """Six-limbed heavy stage, thumbnail only: two quadruped leg pairs plus a
    middle pair, a crowned carapace and two great arms."""
    spec = dict(action="strike", gait="walk", head=(0.34, 0.5), thorax=(0.6, 1.0 if broad else 0.86), mand=0.2,
                abdomen=[(0.5, 0.9 if broad else 0.8), (0.4, 0.66), (0.28, 0.44)],
                leg_front=dict(l1=0.32, l2=0.34, r=0.08, rest=62, swing=0, bend=-35),
                leg_hind=dict(l1=0.32, l2=0.36, r=0.09, rest=125, swing=0, bend=35),
                crest=(0.6, 0.16), plates=[(-0.28, -0.1, 0.13), (0.28, -0.1, 0.13), (0, 0.1, 0.12)],
                arms=dict(SCYTHE, lens=[0.36, 0.4, 0.46], r=0.06) if not broad else
                dict(HOOK, lens=[0.32, 0.32, 0.32], r=0.075))
    parts = quad(S, spec)
    for side in (-1, 1):
        parts += leg((side * 0.46, 0.42), side * 95, 0, 0, 0.32, 0.36, 0.08, side * 20, 1, stance=False)
    return parts


def builders(table, broad):
    return dict(runner=lambda S: quad(S, table["runner"]), brute=lambda S: quad(S, table["brute"]),
                spitter=lambda S: quad(S, table["spitter"]), matriarch=lambda S: matriarch(S, broad))


DIRECTIONS["Q1"] = dict(name="HOUND-MANTIS", **builders(Q1, False))
DIRECTIONS["Q2"] = dict(name="BEETLE-BEAST", **builders(Q2, True))
for k in ("Q1", "Q2"):
    PAL[k] = dict(C)
    PAL[k + "_swatch"] = C
    PAL[k + "_accents"] = [C["amber"], (255, 206, 120), C["cream"], C["bone"]]
