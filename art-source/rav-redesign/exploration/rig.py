"""Tiny 2D top-down rig renderer for the Rav Bloom redesign exploration.

Creatures are described in cell units (1 cell = one battle grid cell), facing
up (-y). Angles: 0 points forward (up), positive turns clockwise. A builder
returns a list of Part objects; render() shades and composites them with a
soft ground shadow, supersampled, at any pixels-per-cell.

This is exploration tooling for motion studies, not production art: the
production path is layered sprites through unit-layer-layouts.
"""
import math
from dataclasses import dataclass, field

from PIL import Image, ImageDraw, ImageFilter

LIGHT = (-0.55, -0.85)  # light from upper-left (screen space)


def d2v(a_deg):
    a = math.radians(a_deg)
    return math.sin(a), -math.cos(a)


def add(p, q, k=1.0):
    return p[0] + q[0] * k, p[1] + q[1] * k


def lerp(a, b, t):
    return a + (b - a) * t


def lerpc(c1, c2, t):
    return tuple(int(round(lerp(a, b, t))) for a, b in zip(c1, c2))


def clamp(x, lo=0.0, hi=1.0):
    return max(lo, min(hi, x))


def smooth(t):
    t = clamp(t)
    return t * t * (3 - 2 * t)


def ease_out(t):
    t = clamp(t)
    return 1 - (1 - t) ** 3


# ---------------------------------------------------------------- strike curve
def strike_e(s):
    """Blend parameter over a strike in [0,1): -1 coil, 0 rest, 1 extend.

    0.00-0.34 anticipation (slow wind-up), 0.34-0.44 lash (fast, overshoots),
    0.44-0.58 impact hold settling, 0.58-1.0 recovery with a damped wobble.
    """
    if s is None:
        return 0.0
    if s < 0:
        return 0.0
    if s < 0.34:
        return -smooth(s / 0.34)
    if s < 0.44:
        return -1 + 2.2 * ease_out((s - 0.34) / 0.10)
    if s < 0.58:
        return lerp(1.2, 1.0, smooth((s - 0.44) / 0.14))
    if s < 1.0:
        u = (s - 0.58) / 0.42
        return (1 - smooth(u)) + 0.18 * math.sin(u * 3 * math.pi) * (1 - u)
    return 0.0


def blend_pose(rest, coil, ext, e):
    if e >= 0:
        return [r + (x - r) * e for r, x in zip(rest, ext)]
    return [r + (c - r) * (-e) for r, c in zip(rest, coil)]


def lagged_pose(rest, coil, ext, s, lag=0.022, wobble=None):
    out = []
    for j in range(len(rest)):
        sj = None if s is None else s - j * lag
        e = strike_e(sj) if sj is not None else 0.0
        a = blend_pose([rest[j]], [coil[j]], [ext[j]], e)[0]
        if wobble:
            a += wobble[j]
        out.append(a)
    return out


# ---------------------------------------------------------------- geometry
def chain(root, base_abs, rels, lens):
    """Joint positions for a chain; rels[0] is added to base_abs."""
    pts = [root]
    a = base_abs
    angs = []
    for r, L in zip(rels, lens):
        a += r
        angs.append(a)
        pts.append(add(pts[-1], d2v(a), L))
    return pts, angs


def capsule(p0, p1, r0, r1, n=10):
    dx, dy = p1[0] - p0[0], p1[1] - p0[1]
    ang = math.atan2(dy, dx)
    pts = []
    for i in range(n + 1):
        t = ang - math.pi / 2 + math.pi * i / n
        pts.append((p1[0] + r1 * math.cos(t), p1[1] + r1 * math.sin(t)))
    for i in range(n + 1):
        t = ang + math.pi / 2 + math.pi * i / n
        pts.append((p0[0] + r0 * math.cos(t), p0[1] + r0 * math.sin(t)))
    return pts


def ellipse(c, rx, ry, rot=0.0, n=40, wob=None):
    pts = []
    cr, sr = math.cos(math.radians(rot)), math.sin(math.radians(rot))
    for i in range(n):
        t = 2 * math.pi * i / n
        k = 1.0
        if wob:
            for amp, freq, ph in wob:
                k += amp * math.sin(freq * t + ph)
        x, y = rx * k * math.cos(t), ry * k * math.sin(t)
        pts.append((c[0] + x * cr - y * sr, c[1] + x * sr + y * cr))
    return pts


def poly_xform(pts, scale=1.0, shift=(0, 0), about=None):
    if about is None:
        about = (sum(p[0] for p in pts) / len(pts), sum(p[1] for p in pts) / len(pts))
    return [(about[0] + (p[0] - about[0]) * scale + shift[0],
             about[1] + (p[1] - about[1]) * scale + shift[1]) for p in pts]


@dataclass
class Part:
    pts: list
    color: tuple
    z: float = 0.0
    kind: str = "solid"  # solid | line | glow | dots
    width: float = 0.0  # for lines, in cells
    outline: float = 0.018  # outline width in cells
    shade: bool = True
    glow: float = 0.0  # 0..1 extra additive glow
    alpha: int = 255
    shadow: bool = True
    hi: float = 1.0  # highlight strength
    edge: tuple = None  # outline colour override (light chitin sutures and rims)
    sheen: float = 0.0  # 0..1 lacquer streak along the light side


def seg_parts(pts, radii, color, z, **kw):
    out = []
    for i in range(len(pts) - 1):
        out.append(Part(capsule(pts[i], pts[i + 1], radii[i], radii[i + 1]), color, z + i * 0.01, **kw))
    return out


def taper(r0, r1, n):
    return [lerp(r0, r1, i / n) for i in range(n + 1)]


# ---------------------------------------------------------------- render
def _darken(c, k):
    return tuple(int(v * k) for v in c[:3])


def _lighten(c, k):
    return tuple(int(v + (255 - v) * k) for v in c[:3])


def render(parts, ppc, size_cells=(4, 4), center=(0.5, 0.5), ss=4, yaw=0.0, offset=(0, 0),
           shadow=True, shadow_offset=(0.05, 0.07), shadow_alpha=110):
    """Render parts to an RGBA image size_cells*ppc pixels.

    center is the fraction of the canvas where creature origin sits.
    """
    W, H = int(round(size_cells[0] * ppc)), int(round(size_cells[1] * ppc))
    S = ppc * ss
    ox, oy = W * ss * center[0], H * ss * center[1]
    cy, sy = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))

    def tx(p):
        x, y = p[0] + offset[0], p[1] + offset[1]
        x, y = x * cy - y * sy, x * sy + y * cy
        return (ox + x * S, oy + y * S)

    big = Image.new("RGBA", (W * ss, H * ss), (0, 0, 0, 0))
    d = ImageDraw.Draw(big)
    glow = Image.new("RGBA", big.size, (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    parts = sorted(parts, key=lambda p: p.z)

    if shadow:
        sh = Image.new("L", big.size, 0)
        sd = ImageDraw.Draw(sh)
        so = (shadow_offset[0] * S, shadow_offset[1] * S)
        for p in parts:
            if not p.shadow:
                continue
            pts = [(q[0] + so[0], q[1] + so[1]) for q in map(tx, p.pts)]
            if p.kind == "line":
                sd.line(pts, fill=255, width=max(1, int(p.width * S)), joint="curve")
            elif len(pts) >= 3:
                sd.polygon(pts, fill=255)
        sh = sh.filter(ImageFilter.GaussianBlur(max(1, 0.035 * S)))
        shade = Image.new("RGBA", big.size, (8, 4, 6, 0))
        shade.putalpha(sh.point(lambda v: v * shadow_alpha // 255))
        big = Image.alpha_composite(big, shade)
        d = ImageDraw.Draw(big)

    lx, ly = LIGHT
    for p in parts:
        pts = [tx(q) for q in p.pts]
        col = tuple(p.color[:3]) + (p.alpha,)
        if p.kind == "line":
            if p.outline > 0:
                d.line(pts, fill=_darken(col, 0.35) + (p.alpha,), width=max(1, int((p.width + p.outline) * S)), joint="curve")
            d.line(pts, fill=col, width=max(1, int(p.width * S)), joint="curve")
            if p.glow:
                gd.line(pts, fill=col[:3] + (int(255 * p.glow),), width=max(1, int(p.width * S * 2.5)), joint="curve")
            continue
        if p.kind == "dots":
            r = p.width * S
            for q in pts:
                d.ellipse([q[0] - r, q[1] - r, q[0] + r, q[1] + r], fill=col)
            continue
        if len(pts) < 3:
            continue
        if p.outline > 0:
            oc = tuple(p.edge[:3]) if p.edge else _darken(col, 0.3)
            d.line(pts + [pts[0]], fill=oc + (p.alpha,), width=max(1, int(p.outline * S)), joint="curve")
        d.polygon(pts, fill=col)
        if p.shade:
            xs = [q[0] for q in pts]
            ys = [q[1] for q in pts]
            ext = max(max(xs) - min(xs), max(ys) - min(ys))
            mid = poly_xform(pts, 0.74, (lx * ext * 0.07, ly * ext * 0.07))
            d.polygon(mid, fill=_lighten(col, 0.16 * p.hi) + (p.alpha,))
            hi = poly_xform(pts, 0.42, (lx * ext * 0.16, ly * ext * 0.16))
            d.polygon(hi, fill=_lighten(col, 0.36 * p.hi) + (p.alpha,))
        if p.sheen and p.shade:
            sh = poly_xform(pts, 0.22, (lx * ext * 0.24, ly * ext * 0.24))
            d.polygon(sh, fill=_lighten(col, 0.55 + 0.35 * p.sheen) + (p.alpha,))
        if p.glow:
            gd.polygon(pts, fill=col[:3] + (int(255 * p.glow),))

    if any(p.glow for p in parts):
        g = glow.filter(ImageFilter.GaussianBlur(max(1, 0.06 * S)))
        big = Image.alpha_composite(big, g)
    out = big.convert("RGBa").resize((W, H), Image.LANCZOS).convert("RGBA")
    return out


def silhouette(img, color=(16, 14, 16)):
    a = img.getchannel("A").point(lambda v: 255 if v > 90 else 0)
    s = Image.new("RGBA", img.size, color + (0,))
    s.putalpha(a)
    return s
