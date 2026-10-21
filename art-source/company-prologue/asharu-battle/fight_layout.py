"""Asharu battle: the fight laid out as diagrams, with no render (see asharu-battle-shot.md).

    python fight_layout.py            # every key frame with ships, plus the overview sheet

Plain Python and Pillow, read straight from battle_blockout's KEYFRAMES, camera maths and gun
check, so the diagrams and the blockout cannot disagree. Each key frame gets one 1920x1080 sheet:

- the camera's frame as a schematic: every hull a true-length diamond seen through the shot's own
  lens, with a tick on its deck side, so a roll reads at a glance;
- a plan view (prograde right, north up) and a side view (prograde right, up up), both true scale,
  with the camera and its field of view;
- every tracer and missile as a line, green where the shooter's guns bear and red where they
  cannot (the same verdict as --check).

Output goes to <repo>/build/asharu-battle/layout/ (ignored); never commit it.
"""
import math
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE.parent))
import battle_blockout as bb  # noqa: E402
import roster  # noqa: E402
from PIL import Image, ImageDraw  # noqa: E402
from review_font import review_font  # noqa: E402

W, H = bb.W, bb.H
OUT = bb.REPO / 'build' / 'asharu-battle' / 'layout'
BG, PANEL, GRID, EDGE = (8, 9, 14), (14, 16, 24), (36, 40, 52), (90, 96, 110)
MERCHANT_C, PIRATE_C = (120, 170, 230), (235, 120, 90)
BEARS_C, CANNOT_C = (120, 220, 130), (255, 70, 70)
FX_C = (255, 200, 110)

MEMBERS = {m['key']: m for m in roster.MERCHANT + roster.PIRATE}
LENGTH = {k: roster.size_row(m)['length_m'] for k, m in MEMBERS.items()}
PIRATES = {m['key'] for m in roster.PIRATE}
BEAM = 0.28            # drawn beam as a share of length; a schematic, not the hull's real plan


def colour(key):
    return PIRATE_C if key in PIRATES else MERCHANT_C


def name(key):
    """The hull's name, told apart from its sisters by the key's suffix (Lasher lead, Wolf (P) b)."""
    if key == 'platform':
        return 'derelict platform'
    if key not in MEMBERS:
        return key
    n = MEMBERS[key]['name'].replace('Shepherd "Morrow"', 'Morrow')
    tail = key.rsplit('-', 1)[-1]
    if tail in ('a', 'b'):
        return f'{n} {tail.upper()}'
    if tail == 'lead':
        return f'{n} (lead)'
    return n


class Labels:
    """Places labels under their points without overlapping one another."""

    def __init__(self, dr, font, box):
        self.dr, self.font, self.box, self.placed = dr, font, box, []

    def put(self, xy, txt, fill, dy=10):
        x0, y0, x1, y1 = self.box
        tw = self.dr.textlength(txt, font=self.font)
        lx, ly = min(max(xy[0] - tw / 2, x0 + 4), x1 - tw - 4), xy[1] + dy
        for _ in range(40):
            if not any(lx < px + pw + 4 and lx + tw + 4 > px and abs(ly - py) < 17 for px, py, pw in self.placed):
                break
            ly += 17
        if ly > y1 - 40:
            return
        self.placed.append((lx, ly, tw))
        self.dr.rectangle((lx - 3, ly - 1, lx + tw + 3, ly + 17), fill=(0, 0, 0, 150))
        self.dr.text((lx, ly), txt, font=self.font, fill=fill)


def world(sp, local):
    """A point in a ship's own frame (metres; +X bow, +Y port, +Z deck) in world metres."""
    R = bb._rot(*sp[3:6])
    return [sp[i] + sum(R[i][j] * local[j] for j in range(3)) for i in range(3)]


PLATFORM_HALF = (380, 260, 35)   # the blockout platform's rough extent, metres


def platform_faces(p):
    """The platform as two quads (top and bottom face) in world metres."""
    hx, hy, hz = PLATFORM_HALF
    return [[(p[0] + sx * hx, p[1] + sy * hy, p[2] + z) for sx, sy in ((-1, -1), (1, -1), (1, 1), (-1, 1))]
            for z in (-hz, hz)]


def hull_points(key, sp):
    L = LENGTH[key]
    return dict(bow=world(sp, (L / 2, 0, 0)), stern=world(sp, (-L / 2, 0, 0)),
                port=world(sp, (0, L * BEAM / 2, 0)), stbd=world(sp, (0, -L * BEAM / 2, 0)),
                centre=list(sp[:3]), deck=world(sp, (0, 0, L * 0.22)))


class Camera:
    """The shot's camera, the same maths as battle_blockout.check: Blender's track ('-Z', 'Y')."""

    def __init__(self, c):
        self.loc = c['loc']
        f = [b - a for a, b in zip(self.loc, c['target'])] if c.get('target') else list(c['look'])
        n = math.sqrt(sum(x * x for x in f))
        self.f = [x / n for x in f]
        r = [self.f[1], -self.f[0], 0.0]
        n = math.sqrt(sum(x * x for x in r))
        self.r = [x / n for x in r]
        f, r = self.f, self.r
        self.u = [r[1] * f[2] - r[2] * f[1], r[2] * f[0] - r[0] * f[2], r[0] * f[1] - r[1] * f[0]]
        self.fpx = c['lens'] / 36.0 * W
        self.lens = c['lens']
        self.pitch = math.degrees(math.asin(f[2]))
        dip = math.degrees(math.acos(bb.R / (bb.R + bb.ALT + self.loc[2])))
        self.horizon_y = H / 2 - self.fpx * math.tan(math.radians(-dip - self.pitch))

    def project(self, p):
        """Frame pixel (1920x1080) and depth, or None behind the camera."""
        d = [b - a for a, b in zip(self.loc, p)]
        z = sum(a * b for a, b in zip(d, self.f))
        if z <= 1:
            return None
        return (W / 2 + self.fpx * sum(a * b for a, b in zip(d, self.r)) / z,
                H / 2 - self.fpx * sum(a * b for a, b in zip(d, self.u)) / z, z)


def fonts():
    return dict(title=review_font(30, True), head=review_font(20, True), text=review_font(18),
                small=review_font(15), label=review_font(16, True))


def verdicts(kf):
    """(kind, shooter, target) -> True/False/None from the blockout's own bearing check."""
    out = {}
    for kind, a, b, best, _ in bb.bearing(kf):
        out[(kind, a, b)] = None if best is None else bool(best[0])
    return out


def shots(spec):
    return [fx for fx in spec.get('fx', []) if fx[0] in ('tracer', 'missiles')
            and fx[1] in spec['ships'] and fx[2] in spec['ships']]


def fx_marks(spec):
    """Blasts, fires, smoke, flares, debris: (kind, world point)."""
    marks = []
    for fx in spec.get('fx', []):
        if fx[0] in ('tracer', 'missiles') or len(fx) < 2 or fx[1] not in spec['ships']:
            continue
        sp = spec['ships'][fx[1]]
        local = next((a for a in fx[2:] if isinstance(a, tuple)), (0, 0, 0))
        marks.append((fx[0], world(sp, local)))
    return marks


def arrow(dr, a, b, fill, width=3, head=12):
    dr.line((a, b), fill=fill, width=width)
    ang = math.atan2(b[1] - a[1], b[0] - a[0])
    for s in (-1, 1):
        dr.line((b, (b[0] - head * math.cos(ang + s * .45), b[1] - head * math.sin(ang + s * .45))),
                fill=fill, width=width)


def draw_hull(dr, pts, fill, outline, deck=True):
    """A hull diamond from projected points; a short bar to its deck side and a bow dot."""
    if any(pts[k] is None for k in ('bow', 'port', 'stern', 'stbd')):
        return
    poly = [pts[k][:2] for k in ('bow', 'port', 'stern', 'stbd')]
    dr.polygon(poly, fill=fill, outline=outline)
    dr.line((pts['stern'][:2], pts['bow'][:2]), fill=outline, width=1)
    bx, by = pts['bow'][:2]
    dr.ellipse((bx - 3, by - 3, bx + 3, by + 3), fill=(255, 255, 255))
    if deck and pts['deck'] is not None and pts['centre'] is not None:
        dr.line((pts['centre'][:2], pts['deck'][:2]), fill=(255, 255, 255, 200), width=2)


def camera_panel(im, dr, F, kf, spec, box, labelled=True):
    """The shot as its camera frames it, scaled into box."""
    x0, y0, x1, y1 = box
    s = (x1 - x0) / W
    cam = Camera(spec['camera'])

    def P(p):
        q = cam.project(p)
        return None if q is None else (x0 + q[0] * s, y0 + q[1] * s, q[2])

    dr.rectangle(box, fill=(18, 20, 30), outline=EDGE, width=2)
    hy = y0 + cam.horizon_y * s
    if y0 < hy < y1:
        dr.rectangle((x0 + 2, hy, x1 - 2, y1 - 2), fill=(52, 42, 32))
        dr.line((x0, hy, x1, hy), fill=(196, 160, 116), width=2)
        dr.text((x1 - 140, hy - 22), 'Asharu limb', font=F['small'], fill=(196, 160, 116))
    elif hy <= y0:
        dr.rectangle((x0 + 2, y0 + 2, x1 - 2, y1 - 2), fill=(52, 42, 32))
        dr.text((x0 + 10, y0 + 8), 'desert fills the frame (limb above it)', font=F['small'], fill=(196, 160, 116))
    # thirds, faint
    for k in (1, 2):
        dr.line((x0 + (x1 - x0) * k / 3, y0, x0 + (x1 - x0) * k / 3, y1), fill=(255, 255, 255, 18))
        dr.line((x0, y0 + (y1 - y0) * k / 3, x1, y0 + (y1 - y0) * k / 3), fill=(255, 255, 255, 18))
    ships = spec['ships']
    ok = verdicts(kf)
    order = sorted(ships, key=lambda k: -(cam.project(ships[k][:3]) or (0, 0, -1))[2])   # far first
    dr2 = ImageDraw.Draw(im, 'RGBA')
    for kind, a, b, *_ in shots(spec):
        pa, pb = P(ships[a][:3]), P(ships[b][:3])
        if pa and pb:
            v = ok.get((kind, a, b))
            c = BEARS_C if v else CANNOT_C if v is False else (200, 200, 200)
            dr2.line((pa[:2], pb[:2]), fill=(*c, 150), width=2 if kind == 'tracer' else 1)
            if kind == 'missiles':
                for t in (.3, .5, .7):
                    mx, my = pa[0] + (pb[0] - pa[0]) * t, pa[1] + (pb[1] - pa[1]) * t
                    dr2.ellipse((mx - 3, my - 3, mx + 3, my + 3), fill=(*c, 220))
    labels = []
    if spec.get('platform'):
        for face in platform_faces(spec['platform']):
            q = [P(v) for v in face]
            if all(q):
                dr2.polygon([v[:2] for v in q], fill=(120, 112, 100, 110), outline=(170, 160, 145, 220))
        c = P(spec['platform'])
        if c:
            labels.append(('platform', (c[0], c[1] + 60 * s * 8, c[2])))
    for key in order:
        pts = {k: P(v) for k, v in hull_points(key, ships[key]).items()}
        c = colour(key)
        draw_hull(dr2, pts, (*c, 150), (*c, 255))
        if pts['centre'] is not None:
            labels.append((key, pts['centre']))
    for kind, p in fx_marks(spec):
        q = P(p)
        if q:
            r = 9 if kind in ('blast_on', 'debris') else 6
            dr2.ellipse((q[0] - r, q[1] - r, q[0] + r, q[1] + r), outline=FX_C, width=2)
    if not labelled:
        return
    lab = Labels(dr2, F['label'], (x0, y0, x1, y1 + 40))
    for key, c in sorted(labels, key=lambda kv: kv[1][1]):
        if x0 <= c[0] <= x1 and y0 <= c[1] <= y1:
            lab.put(c, name(key), (200, 190, 175) if key == 'platform' else colour(key), dy=12)
    off = [name(k) for k in ships if (lambda q: q is None or not (0 <= q[0] <= W and 0 <= q[1] <= H))(cam.project(ships[k][:3]))]
    return (x0, y1 + 6), (f"Camera frame ({cam.lens} mm, looking {abs(cam.pitch):.0f} deg {'down' if cam.pitch < 0 else 'up'})"
                          + ('' if not off else f"; out of frame: {', '.join(off)}"))


def map_panel(dr, F, spec, box, axes, title):
    """True-scale map of the ships: axes ('x','y') plan or ('x','z') side."""
    x0, y0, x1, y1 = box
    ia, ib = {'x': 0, 'y': 1, 'z': 2}[axes[0]], {'x': 0, 'y': 1, 'z': 2}[axes[1]]
    ships = spec['ships']
    cam = spec['camera']
    pts = [s[:3] for s in ships.values()]
    if spec.get('platform'):
        pts += [v for face in platform_faces(spec['platform']) for v in face]
    cen = [sum(p[i] for p in pts) / len(pts) for i in range(3)]
    extent = max(max(abs(p[i] - cen[i]) for p in pts) for i in range(3))
    cam_dist = math.dist(cam['loc'], cen)
    cam_inside = cam_dist < 2.5 * max(extent, 150)      # a far camera is an arrow at the edge, not a fit
    if cam_inside:
        pts = pts + [list(cam['loc'])]
    lo_a, hi_a = min(p[ia] for p in pts), max(p[ia] for p in pts)
    lo_b, hi_b = min(p[ib] for p in pts), max(p[ib] for p in pts)
    pad = 60
    sc = min((x1 - x0 - 2 * pad) / max(hi_a - lo_a, 200), (y1 - y0 - 2 * pad - 20) / max(hi_b - lo_b, 200))
    ca, cb = (lo_a + hi_a) / 2, (lo_b + hi_b) / 2
    mx, my = (x0 + x1) / 2, (y0 + y1) / 2 + 10

    def Q(p):
        return mx + (p[ia] - ca) * sc, my - (p[ib] - cb) * sc

    dr.rectangle(box, fill=PANEL, outline=EDGE, width=2)
    step = next(s for s in (100, 250, 500, 1000, 2000, 5000) if s * sc >= 40)
    a = math.floor((ca - (x1 - x0) / 2 / sc) / step) * step
    while a < ca + (x1 - x0) / 2 / sc:
        gx = mx + (a - ca) * sc
        if x0 < gx < x1:
            dr.line((gx, y0 + 30, gx, y1 - 2), fill=GRID)
        a += step
    b = math.floor((cb - (y1 - y0) / 2 / sc) / step) * step
    while b < cb + (y1 - y0) / 2 / sc:
        gy = my - (b - cb) * sc
        if y0 + 30 < gy < y1:
            dr.line((x0 + 2, gy, x1 - 2, gy), fill=GRID)
        b += step
    dr.text((x0 + 10, y0 + 6), title, font=F['head'], fill=(230, 230, 230))
    dr.text((x1 - 170, y0 + 8), f'grid {step:,} m', font=F['small'], fill=(150, 150, 160))
    if spec.get('platform'):
        face = platform_faces(spec['platform'])[1]
        qa = [Q(v) for v in face]
        xs, ys = [v[0] for v in qa], [v[1] for v in qa]
        hz = PLATFORM_HALF[2] * sc if axes == ('x', 'z') else 0
        dr.rectangle((min(xs), min(ys), max(xs), max(ys) + 2 * hz), fill=(120, 112, 100, 90), outline=(170, 160, 145))
        dr.text((min(xs), max(ys) + 2 * hz + 4), 'derelict platform', font=F['small'], fill=(200, 190, 175))
    # camera and its field of view
    c = Camera(cam)
    cp = Q(cam['loc'])
    if not cam_inside:     # clamp to the panel edge along the line from the subject
        sx_, sy_ = Q(cen)
        dx, dy = cp[0] - sx_, cp[1] - sy_
        t = min(((x1 - 30 - sx_) / dx if dx > 0 else (x0 + 30 - sx_) / dx if dx < 0 else 9e9),
                ((y1 - 40 - sy_) / dy if dy > 0 else (y0 + 50 - sy_) / dy if dy < 0 else 9e9))
        cp = (sx_ + dx * t, sy_ + dy * t)
        arrow(dr, (sx_ + dx * t * .8, sy_ + dy * t * .8), cp, (220, 220, 220), 2, 9)
        dr.text((cp[0] - 120 if cp[0] > (x0 + x1) / 2 else cp[0] + 12, cp[1] + 8),
                f'camera, {cam_dist / 1e3:.1f} km', font=F['small'], fill=(220, 220, 220))
    half = math.atan(18.0 / cam['lens']) if axes == ('x', 'y') else math.atan(10.125 / cam['lens'])
    fa, fb = c.f[ia], c.f[ib]
    n = math.hypot(fa, fb) or 1
    ang = math.atan2(fb, fa)
    reach = 0.18 * max(x1 - x0, y1 - y0)
    if cam_inside:
        for s in (-1, 1):
            e = (cp[0] + reach * math.cos(ang + s * half), cp[1] - reach * math.sin(ang + s * half))
            dr.line((cp, e), fill=(255, 255, 255, 60), width=1)
        dr.rectangle((cp[0] - 6, cp[1] - 6, cp[0] + 6, cp[1] + 6), outline=(255, 255, 255), width=2)
        dr.text((cp[0] + 9, cp[1] - 9), 'camera', font=F['small'], fill=(220, 220, 220))
    ok = {}
    for fx in shots(spec):
        pa, pb = Q(ships[fx[1]][:3]), Q(ships[fx[2]][:3])
        dr.line((pa, pb), fill=(200, 200, 200, 70), width=1)
    pend = []
    for key, sp in ships.items():
        hp = {k: Q(v) + (0,) for k, v in hull_points(key, sp).items()}
        col = colour(key)
        hp_true = dict(hp)
        L = LENGTH[key] * sc
        if L < 10:      # too small at this scale: draw a marker with the heading
            cxy = hp['centre'][:2]
            dr.ellipse((cxy[0] - 4, cxy[1] - 4, cxy[0] + 4, cxy[1] + 4), fill=col)
            bx, by = hp['bow'][:2]
            d = math.hypot(bx - cxy[0], by - cxy[1]) or 1
            arrow(dr, cxy, (cxy[0] + (bx - cxy[0]) / d * 14, cxy[1] + (by - cxy[1]) / d * 14), col, 2, 5)
        else:
            draw_hull(dr, hp_true, (*col, 120), (*col, 255), deck=axes == ('x', 'z'))
        pend.append((hp['centre'][:2], key))
    for kind, p in fx_marks(spec):
        q = Q(p)
        dr.ellipse((q[0] - 5, q[1] - 5, q[0] + 5, q[1] + 5), outline=FX_C, width=2)
    lab = Labels(dr, F['small'], (x0, y0 + 30, x1, y1))
    for c, key in sorted(pend, key=lambda v: v[0][1]):
        lab.put(c, name(key), colour(key), dy=8)
    la = {'x': 'prograde', 'y': 'north', 'z': 'up'}
    dr.text((x0 + 10, y1 - 24), f'{la[axes[0]]} to the right, {la[axes[1]]} up', font=F['small'], fill=(150, 150, 160))


def wrap(dr, text, font, width):
    words, lines, cur = text.split(), [], ''
    for w in words:
        t = (cur + ' ' + w).strip()
        if dr.textlength(t, font=font) > width and cur:
            lines.append(cur)
            cur = w
        else:
            cur = t
    return lines + [cur]


def sheet(kf, n):
    spec = bb.KEYFRAMES[kf]
    F = fonts()
    im = Image.new('RGB', (W, H), BG)
    dr = ImageDraw.Draw(im, 'RGBA')
    dr.text((24, 16), f"{spec['shot']}  {spec['title']}", font=F['title'], fill=(255, 255, 255))
    dr.text((W - 560, 24), 'Layout diagram, not a render', font=F['head'], fill=(170, 170, 180))
    box = (24, 66, 24 + 1120, 66 + 630)
    layer = im.copy()      # drawn whole, then clipped to its box: a hull off the frame's edge stays off it
    at, footer = camera_panel(layer, ImageDraw.Draw(layer, 'RGBA'), F, kf, spec, box)
    im.paste(layer.crop((box[0], box[1], box[2] + 1, box[3] + 1)), box[:2])
    dr.text(at, footer, font=F['small'], fill=(170, 170, 180))
    map_panel(dr, F, spec, (1168, 66, W - 24, 66 + 446), ('x', 'y'), 'From above')
    map_panel(dr, F, spec, (1168, 524, W - 24, 1056), ('x', 'z'), 'From the side (south flank)')
    y = 730
    for line in wrap(dr, spec['caption'], F['text'], 1110):
        dr.text((24, y), line, font=F['text'], fill=(220, 220, 220))
        y += 24
    y += 10
    rows = bb.bearing(kf)
    if rows:
        dr.text((24, y), 'Lines of fire (the --check verdict)', font=F['head'], fill=(230, 230, 230))
        y += 28
        for kind, a, b, best, tgt_el in rows:
            okb = best is not None and best[0]
            how = (f'bears: {best[2]}, {best[3]:+.0f} deg above her deck' if okb else
                   'no guns known' if best is None else f'CANNOT BEAR, {best[1]:.0f} deg short')
            dr.text((24, y), f"{'missiles' if kind == 'missiles' else 'guns'}:  {name(a)} at {name(b)}",
                    font=F['text'], fill=BEARS_C if okb else CANNOT_C)
            dr.text((520, y), how, font=F['text'], fill=(190, 190, 190))
            y += 23
    marks = sorted({k for k, _ in fx_marks(spec)})
    if marks:
        dr.text((24, min(y + 8, H - 60)), 'Effects (gold rings): ' + ', '.join(m.replace('_on', '').replace('_', ' ') for m in marks),
                font=F['small'], fill=FX_C)
    dr.text((24, H - 30), 'Hulls at true length; the white bar points to each ship\'s deck, the white dot is her bow. '
            'Blue merchant, red pirate.', font=F['small'], fill=(150, 150, 160))
    out = OUT / f'{n:02d}-{kf}-{spec["shot"].lower()}.png'
    im.save(out)
    return out


SHOTS = [('S1a', 6, 'Overhead: the view the player knows', 'k0'),
         ('S1', 8, 'Establishing: the convoy over the day side', 'k1'),
         ('S2', 6, 'Alongside the Colossus, Morrow at her quarter', None),
         ('S3', 8, 'Running dark: the platform, then a hulk lights up', 'k1b'),
         ('S4', 6, 'First contact: twelve hulls climb out', 'k2'),
         ('S5', 6, 'The Manticore (P) opens at long range', None),
         ('S6', 8, 'The screen breaks: Phaeton cooks off', 'k3'),
         ('S6b', 8, 'The escorts answer: a Hound (P) breaks up', 'k3c'),
         ('S6c', 8, 'The column is cut: Wolves (P) blink through', 'k3d'),
         ('S6d', 6, 'The rear guard: Hound against Hounds (P)', 'k3e'),
         ('S7', 8, 'Boarding the Tarsus', 'k3b'),
         ('S8', 6, 'Close on the Colossus: port pod shot out', 'k4'),
         ('S9', 8, 'She will not be taken', 'k4'),
         ('S9b', 8, "The lead's last run: the Falcon overloads", 'k4b'),
         ('S10', 8, 'The prize taken; the Colossus falling', 'k5'),
         ('S11', 8, 'Entry: plasma round both hulls', None),
         ('S12', 8, 'Two trails toward the canyons. End.', 'k6')]


def overview():
    """The whole fight on one sheet: the beat timeline, and the battle side-on with each key frame's
    centre of action."""
    F = fonts()
    im = Image.new('RGB', (W, H), BG)
    dr = ImageDraw.Draw(im, 'RGBA')
    dr.text((24, 16), 'The fight in one sheet', font=F['title'], fill=(255, 255, 255))
    dr.text((W - 560, 24), 'Layout diagram, not a render', font=F['head'], fill=(170, 170, 180))
    total = sum(s[1] for s in SHOTS)
    x, y0, y1 = 24.0, 70, 150
    sc = (W - 48) / total
    for shot, secs, text, kf in SHOTS:
        w = secs * sc
        fill = (40, 60, 90) if kf else (30, 32, 42)
        dr.rectangle((x, y0, x + w - 3, y1), fill=fill, outline=EDGE)
        dr.text((x + 6, y0 + 4), f'{shot}  {secs}s' + ('  K' if kf else ''), font=F['label'], fill=(240, 240, 240))
        for j, line in enumerate(wrap(dr, text, F['small'], w - 12)[:3]):
            dr.text((x + 6, y0 + 26 + j * 17), line, font=F['small'], fill=(200, 200, 210))
        x += w
    dr.text((24, y1 + 6), f'{total} s in all. K = a key frame exists (sheets that follow); grey beats have no '
            'staging yet.', font=F['small'], fill=(150, 150, 160))
    # side-on: the convoy, the climb, each key frame's action centre
    box = (24, 200, W - 24, 1056)
    x0, yb0, x1, yb1 = box
    dr.rectangle(box, fill=PANEL, outline=EDGE, width=2)
    dr.text((x0 + 12, yb0 + 8), 'The battle from the south flank (prograde to the right, up is up; 1 km grid)',
            font=F['head'], fill=(230, 230, 230))
    sx = (x1 - x0 - 120) / 13000.0

    def Q(p):
        return x1 - 420 + p[0] * sx, yb0 + 150 - p[2] * sx

    for k in range(-11, 4):
        gx = Q((k * 1000, 0, 0))[0]
        dr.line((gx, yb0 + 40, gx, yb1 - 4), fill=GRID)
    for k in range(-5, 2):
        gy = Q((0, 0, k * 1000))[1]
        if yb0 + 40 < gy < yb1:
            dr.line((x0 + 2, gy, x1 - 2, gy), fill=GRID)
    plat = bb.KEYFRAMES['k2']['platform']
    px, py = Q(plat)
    dr.rectangle((px - 26, py - 9, px + 26, py + 9), fill=(150, 140, 128))
    dr.text((px - 70, py + 14), 'derelict platform', font=F['small'], fill=(200, 190, 175))
    for key, sp in bb.CONVOY.items():
        q = Q(sp)
        dr.ellipse((q[0] - 4, q[1] - 4, q[0] + 4, q[1] + 4), fill=MERCHANT_C)
    for key, sp in bb.PIRATES_RISING.items():
        q = Q(sp)
        dr.ellipse((q[0] - 4, q[1] - 4, q[0] + 4, q[1] + 4), fill=PIRATE_C)
    a, b = Q((-7800, 0, -3500)), Q((-1400, 0, -300))
    arrow(dr, a, b, PIRATE_C, 3, 16)
    dr.text((Q((-6600, 0, -3400))[0], Q((-6600, 0, -3400))[1]), 'S4-S5: the pirates climb out from behind and below',
            font=F['text'], fill=PIRATE_C)
    dr.text((Q((-1500, 0, 0))[0] - 200, Q((0, 0, 650))[1] - 30), 'S1-S2: the convoy in column, slowing for its descent',
            font=F['text'], fill=MERCHANT_C)
    arrow(dr, Q((-200, 0, 0)), Q((2600, 0, 0)), MERCHANT_C, 3, 16)
    notes = [('k1b', 'S3: running dark among the hulks', (-330, 100)),
             ('k3', 'S6: screen breaks, Phaeton cooks off', (-330, 150)),
             ('k3d', 'S6b-S6d: the brawl along the column', (-330, 175)),
             ('k3b', 'S7: Tarsus boarded, falls astern', (-330, 200)),
             ('k4', 'S8-S9: the Mk.III on the Colossus', (-330, 250)),
             ('k5', 'S10: Tarsus clamped; Colossus falling', (-330, 300))]
    for kf, text, (dx, dy) in notes:
        sh = bb.KEYFRAMES[kf]['ships']
        focus = [sh[k] for k in sh if k in ('phaeton', 'tarsus', 'colossus', 'colossus3')] or list(sh.values())
        cx = sum(p[0] for p in focus) / len(focus)
        cz = sum(p[2] for p in focus) / len(focus)
        q = Q((cx, 0, cz))
        dr.ellipse((q[0] - 10, q[1] - 10, q[0] + 10, q[1] + 10), outline=FX_C, width=3)
        dr.line((q, (q[0] + dx, q[1] + dy)), fill=FX_C, width=1)
        dr.text((q[0] + dx - dr.textlength(text, font=F['text']) - 6, q[1] + dy - 12), text, font=F['text'], fill=FX_C)
    fall = [Q((x, 0, -x * x / 900.0 - 700 if x > 0 else -700)) for x in range(0, 2400, 50)]
    fall = [q for q in fall if q[1] < yb1 - 40]
    dr.line(fall, fill=(255, 180, 110), width=3)
    dr.text((fall[-1][0] - 420, fall[-1][1] + 10), 'S10-S12: Colossus and Morrow lose their orbit (schematic)',
            font=F['text'], fill=(255, 180, 110))
    dr.text((x0 + 12, yb1 - 30), 'Positions are the blockout\'s own: the convoy and the climbing pirates from S1/S4; gold '
            'rings mark where each later key frame stages its action.', font=F['small'], fill=(150, 150, 160))
    out = OUT / '00-overview.png'
    im.save(out)
    return out


ORBITRON = HERE.parent / 'second-wind-cinematic' / 'corvus-approach' / 'ui_fonts' / 'Orbitron[wght].ttf'


def ui_mock(kf='k1', speaker='COLOSSUS', rows=('Convoy, Colossus. Window opens in forty.', 'All ships ease to two-thirds.')):
    """The opening as the player would draw it: the place card and a caption over the S1 frame.
    Caption geometry is ArrivalCinematicScreen.drawCaptions' (speaker tag 168 px above the
    bottom, rows 44 px apart, a feathered band 1020 x 140 under them)."""
    from PIL import ImageFilter, ImageFont
    F = fonts()
    im = Image.new('RGB', (W, H), BG)
    dr = ImageDraw.Draw(im, 'RGBA')
    camera_panel(im, dr, F, kf, bb.KEYFRAMES[kf], (0, 0, W, H), labelled=False)
    band = Image.new('L', (W, H), 0)
    ImageDraw.Draw(band).rounded_rectangle((W / 2 - 510, 912 + 66 - 70, W / 2 + 510, 912 + 66 + 70), 60, fill=107)
    band = band.filter(ImageFilter.GaussianBlur(36))
    im.paste((0, 0, 0), (0, 0), band)
    dr = ImageDraw.Draw(im, 'RGBA')
    try:
        tag_f = ImageFont.truetype(str(ORBITRON), 17)
        tag_f.set_variation_by_axes([700])
        card_big = ImageFont.truetype(str(ORBITRON), 44)
        card_big.set_variation_by_axes([700])
        card_small = ImageFont.truetype(str(ORBITRON), 20)
        card_small.set_variation_by_axes([500])
    except (OSError, ValueError):
        tag_f, card_big, card_small = F['head'], F['title'], F['text']
    row_f = review_font(30)

    def shadowed(xy, txt, font, fill):
        dr.text((xy[0] + 2, xy[1] + 2), txt, font=font, fill=(0, 0, 0, 200))
        dr.text(xy, txt, font=font, fill=fill)

    tw = dr.textlength(speaker, font=tag_f)
    shadowed((W / 2 - tw / 2, 912 - 20), speaker, tag_f, (170, 222, 255))
    for j, row in enumerate(rows):
        rw = dr.textlength(row, font=row_f)
        shadowed((W / 2 - rw / 2, 942 - 26 + j * 44), row, row_f, (238, 242, 246))
    # the place card, lower left, clear of the caption band
    x, y = 72, 812
    dr.line((x, y - 10, x, y + 84), fill=(170, 222, 255, 200), width=3)
    shadowed((x + 18, y - 8), 'ASHARU', card_big, (238, 242, 246))
    shadowed((x + 18 + dr.textlength('ASHARU', font=card_big) + 16, y + 14), 'CORVUS', card_small, (170, 222, 255))
    shadowed((x + 18, y + 50), 'LOW ORBIT  /  THREE CYCLES AGO', card_small, (200, 205, 210))
    dr.text((24, 16), 'UI layout mock over the S1 frame: the place card (in at 1.5 s for about 4 s) and the first caption',
            font=F['head'], fill=(170, 170, 180))
    dr.text((24, 44), 'Drawn by the cinematic player at run time, never baked into frames. Caption position, colours and '
            "band are the arrival player's own.", font=F['small'], fill=(150, 150, 160))
    out = OUT / '90-ui-mock-s1.png'
    im.save(out)
    return out


CHANNELS = ('COLOSSUS', 'ESCORT LEAD', 'MORROW', 'PHAETON', 'TARSUS', 'MULE', 'REAR ESCORT')


def recording_mock():
    """S0: the recording screen the cinematic opens on, audio only. A storytelling device, not an
    object in the plot: no source line, just where, when, and who is on the channel."""
    import random
    from PIL import ImageFont
    F = fonts()
    im = Image.new('RGB', (W, H), (4, 5, 8))
    dr = ImageDraw.Draw(im, 'RGBA')
    try:
        big = ImageFont.truetype(str(ORBITRON), 64)
        big.set_variation_by_axes([700])
        mid = ImageFont.truetype(str(ORBITRON), 22)
        mid.set_variation_by_axes([600])
        small = ImageFont.truetype(str(ORBITRON), 17)
        small.set_variation_by_axes([500])
    except (OSError, ValueError):
        big, mid, small = F['title'], F['head'], F['text']
    blue, text, dim = (170, 222, 255), (238, 242, 246), (90, 100, 115)
    x0 = 300
    dr.text((x0, 250), 'ASHARU', font=big, fill=text)
    dr.text((x0 + dr.textlength('ASHARU', font=big) + 24, 282), 'CORVUS  /  LOW ORBIT', font=mid, fill=blue)
    dr.text((x0, 340), 'c203.05.14   06:41:12', font=mid, fill=(200, 205, 210))
    dr.text((x0 + 330, 344), '(the arrival date minus three cycles, computed in play)', font=F['small'], fill=dim)
    # channels: one row per voice, the speaking one lit
    for j, ch in enumerate(CHANNELS):
        y = 430 + j * 38
        live = ch == 'COLOSSUS'
        dr.ellipse((x0, y + 5, x0 + 12, y + 17), fill=(120, 230, 150) if live else (40, 46, 56))
        dr.text((x0 + 28, y), ch, font=small, fill=text if live else dim)
    # the waveform of the speaking channel
    rnd = random.Random(7)
    wx0, wx1, wy = x0 + 300, W - 300, 560
    pts = []
    for i in range(wx1 - wx0):
        env = math.sin(math.pi * i / (wx1 - wx0)) ** .6
        pts.append((wx0 + i, wy + rnd.gauss(0, 1) * 60 * env * (.4 + .6 * abs(math.sin(i / 23)))))
    dr.line(pts, fill=(120, 230, 150, 200), width=2)
    dr.line((wx0, wy, wx1, wy), fill=(60, 70, 80), width=1)
    # caption: the same tag and row the picture uses
    row_f = review_font(30)
    try:
        tag_f = ImageFont.truetype(str(ORBITRON), 17)
        tag_f.set_variation_by_axes([700])
    except (OSError, ValueError):
        tag_f = F['head']
    tw = dr.textlength('COLOSSUS', font=tag_f)
    dr.text((W / 2 - tw / 2, 912 - 20), 'COLOSSUS', font=tag_f, fill=blue)
    row = 'Convoy, Colossus. Window opens in forty.'
    dr.text((W / 2 - dr.textlength(row, font=row_f) / 2, 942 - 26), row, font=row_f, fill=text)
    dr.text((24, 16), 'S0 mock: the recording the cinematic opens on (audio only, before any picture)',
            font=F['head'], fill=(170, 170, 180))
    dr.text((24, 44), 'A storytelling device, not an object in the story. The date is a placeholder; '
            'the player computes the real one.', font=F['small'], fill=(150, 150, 160))
    out = OUT / '89-recording-mock-s0.png'
    im.save(out)
    return out


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    outs = [overview()]
    for n, kf in enumerate((k for k in bb.KEYFRAMES if bb.KEYFRAMES[k]['ships']), start=1):
        outs.append(sheet(kf, n))
    outs.append(recording_mock())
    outs.append(ui_mock())
    print('\n'.join(str(o) for o in outs))


if __name__ == '__main__':
    main()
