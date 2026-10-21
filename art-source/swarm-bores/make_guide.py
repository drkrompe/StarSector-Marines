"""Geometry and lighting guides for the swarm bore bodies and the three spore-colony grades (art pass 2).

Every part is one centre-pivoted, upright structure-art layer (``StructureArt``) on a square canvas whose
edge is ``CANVAS_CELLS`` cells; the layer's ``cells`` in ``turret-emplacements.turret.json`` is that same
number. Each part has several **variants** (``VARIANTS``), each its own seed, so a map of bores does not look
stamped; the runtime picks one per structure (``StructureArt.variantFor``).

Pass 1 drew radially symmetric rings of identical plates round a centred pit. Pass 2 builds each body as a
**heightfield** out of seeded, lopsided primitives and lights it with the game's own sun
(``SunLight`` defaults: azimuth 135 degrees, so from the upper left of the screen, elevation 38 degrees),
ray-marched, so every depth cue comes out of the geometry rather than being painted on:

- A bore tore up through the ground at a slant. One side is **upheaved**: tall plates reared up at the lip,
  tusks leaning over the hole, paving slabs and earth thrown up. The opposite side has **collapsed**: low torn
  plates, the berm broken, plates and slabs slid into the funnel. The shaft descends towards the upheaved side,
  so its wall there is sheer and on the collapsed side it is a ramp. Lit from the upper left, the far
  (lower-right) wall of the shaft is lit, the near wall falls in shadow, ribs recede into the dark, and a
  pale limb and a few amber points sit far down.
- A spore colony is an uneven cluster: one dominant sac set off-centre and leaning, smaller sacs and buds
  pressed against it, cupping plates on one side only, spines bunched on one flank.

The lighting is baked into the albedo; nothing here is rotated or mirrored at runtime, because that would
turn the baked sun with it. The ground shadow and the cracks a body leaves in the ground are not painted by
ImageGen: ``ground_layer`` draws them from the same heightfield and ``export.py`` lays them under the reskin.

The material map (``Field.m``) records what every pixel is, so a recolour to another creature palette is a
mechanical remap of the creature materials only (``export.py --palette``), leaving earth and paving alone.

Run from the repository root: ``python art-source/swarm-bores/make_guide.py``.
Writes ``art-source/templates/swarm-<part>-<variant>-guide.png``.
"""

import math
import random
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

N = 1024

# SunLight defaults. Screen x runs right and y down; the sun sits up and to the left.
SUN_AZIMUTH = math.radians(135.0)
SUN_ELEVATION = math.radians(38.0)

NONE, CHITIN, UNDER, BONE, FLESH, EARTH, SLAB, SAC = range(8)
CREATURE = (CHITIN, UNDER, BONE, FLESH, SAC)
MATERIAL_NAMES = ("none", "chitin", "under", "bone", "flesh", "earth", "slab", "sac")

# Guide albedo per material: neutral enough to be palette-agnostic, distinct enough that the reskin knows
# which surface is which.
ALBEDO = np.array([
    (0, 0, 0),
    (118, 112, 120),   # chitin plate
    (92, 104, 96),     # sinew, under-plate, roots
    (214, 208, 194),   # bone: tusks, spines
    (126, 80, 90),     # wet flesh lining the shaft
    (132, 108, 84),    # thrown earth
    (172, 166, 152),   # paving slabs
    (150, 140, 134),   # spore-sac membrane
], dtype=np.float32) / 255.0
GLOW = np.array((255, 176, 70), dtype=np.float32) / 255.0

CANVAS_CELLS = {
    "bore": 3.8,
    "mother-bore": 4.6,
    "spore-colony-fissure": 1.6,
    "spore-colony-bore": 2.0,
    "spore-colony-maw": 2.4,
}
# How much larger than the modelled body a part is drawn: its layer's `cells` is CANVAS_CELLS times this. The
# heightfield is modelled at the body's physical size (one cell, one metre) so the sun's shadow length is right
# for it; the drawn size is raised on top so the structures read as structures on a mass of creep and bodies.
# Presentation only: the structure's hit radius and footprint do not change with it.
DISPLAY_SCALE = {
    "bore": 1.3,
    "mother-bore": 1.25,
    "spore-colony-fissure": 1.4,
    "spore-colony-bore": 1.4,
    "spore-colony-maw": 1.4,
}


def display_cells(part: str) -> float:
    """The layer's edge on the map, in cells."""
    return round(CANVAS_CELLS[part] * DISPLAY_SCALE[part], 2)


VARIANTS = {
    "bore": 3,
    "mother-bore": 2,
    "spore-colony-fissure": 3,
    "spore-colony-bore": 3,
    "spore-colony-maw": 3,
}
PARTS = tuple(CANVAS_CELLS)
SEEDS = {"bore": 1100, "mother-bore": 2300, "spore-colony-fissure": 3100, "spore-colony-bore": 3700,
         "spore-colony-maw": 4100}


def to_sun_screen():
    """Unit vector towards the sun in screen pixels (x right, y down)."""
    # World y is up and the sun's azimuth is anticlockwise from +x, so screen y is the negated world y.
    return math.cos(SUN_AZIMUTH), -math.sin(SUN_AZIMUTH)


class Field:
    """A heightfield in cells (one cell is one metre), with a material and a glow per pixel."""

    def __init__(self, cells: float, seed: int):
        self.cells = cells
        self.ppc = N / cells
        self.h = np.zeros((N, N), np.float32)
        self.m = np.zeros((N, N), np.uint8)
        self.glow = np.zeros((N, N), np.float32)
        self.over = np.zeros((N, N), bool)  # pixels a body occupies even where it sits at ground height
        yy, xx = np.mgrid[0:N, 0:N].astype(np.float32)
        self.x = (xx + 0.5 - N / 2) / self.ppc
        self.y = (yy + 0.5 - N / 2) / self.ppc
        self.rng = random.Random(seed)
        self.cracks: list[tuple[list[tuple[float, float]], float]] = []

    # --- masks -------------------------------------------------------------------------------------------

    def px(self, x: float, y: float) -> tuple[float, float]:
        return N / 2 + x * self.ppc, N / 2 + y * self.ppc

    def polygon_mask(self, points) -> np.ndarray:
        image = Image.new("L", (N, N), 0)
        ImageDraw.Draw(image).polygon([self.px(x, y) for x, y in points], fill=255)
        return np.asarray(image) > 127

    def soft(self, mask: np.ndarray, radius_cells: float) -> np.ndarray:
        """0 at a shape's edge rising to 1 inside: rounds the shoulder of a raised shape."""
        r = max(1.0, radius_cells * self.ppc)
        blurred = np.asarray(Image.fromarray((mask * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(r)),
                             np.float32) / 255.0
        return np.clip((blurred - 0.5) * 2.0, 0.0, 1.0) ** 0.5 * mask

    # --- composition -------------------------------------------------------------------------------------

    def raise_(self, mask: np.ndarray, height: np.ndarray, material: int):
        """A surface standing on whatever is there: drawn where it is the higher one."""
        take = mask & (height >= self.h)
        self.h[take] = height[take]
        self.m[take] = material
        self.over |= take & (height > 0)

    def drape(self, mask: np.ndarray, lift, material: int):
        """A surface lying on whatever is there (a plate slid into the funnel), ``lift`` above it."""
        self.h[mask] = self.h[mask] + (lift[mask] if isinstance(lift, np.ndarray) else lift)
        self.m[mask] = material
        self.over |= mask

    def node(self, x: float, y: float, r: float, strength: float = 1.0):
        d = np.hypot(self.x - x, self.y - y) / r
        self.glow = np.maximum(self.glow, strength * np.clip(1.2 - d, 0.0, 1.0) ** 1.2)

    def height_at(self, x: float, y: float) -> float:
        i = int(np.clip(N / 2 + y * self.ppc, 0, N - 1))
        j = int(np.clip(N / 2 + x * self.ppc, 0, N - 1))
        return float(self.h[i, j])

    # --- primitives --------------------------------------------------------------------------------------

    def plate(self, cx, cy, angle, length, width, h_in, h_out, bulge=0.08, jagged=0.0, material=CHITIN,
              drape=False, round_=0.035):
        """A curved chitin plate whose spine runs from (cx, cy) outward along ``angle``.

        Height runs from ``h_in`` at the inner end to ``h_out`` at the outer end, bowed up across its width.
        ``jagged`` tears the outer end. A draped plate is laid over the surface beneath it instead.
        """
        rng = self.rng
        ux, uy = math.cos(angle), math.sin(angle)
        vx, vy = -uy, ux
        curl = rng.uniform(-0.3, 0.3)
        steps = 14
        left, right = [], []
        for i in range(steps + 1):
            t = i / steps
            # A petal of a jaw: narrow at the lip, widest two thirds out, the outer end rounded and hooked.
            half = width / 2 * (0.3 + 0.7 * math.sin(math.pi * min(1.0, 0.12 + 0.75 * t)) ** 0.6)
            bend = curl * length * t * t
            sx, sy = cx + ux * length * t * 0.85 + vx * bend, cy + uy * length * t * 0.85 + vy * bend
            left.append((sx - vx * half, sy - vy * half))
            right.append((sx + vx * half, sy + vy * half))
        tip = []
        end = curl * length
        for i in range(1, 8):
            q = i / 8
            reach = length * (0.85 + 0.15 * math.sin(math.pi * q))
            if jagged and rng.random() < 0.7:
                reach -= length * jagged * rng.random()
            off = (q - 0.5) * 2 * width / 2 * 0.95 * math.cos((q - 0.5) * 1.2)
            tip.append((cx + ux * reach + vx * (off + end), cy + uy * reach + vy * (off + end)))
        # The hook: one side of the rim drawn out past the other.
        hook = rng.choice((0, 6))
        hx, hy = tip[hook]
        tip[hook] = (hx + ux * length * 0.12 + vx * (width * 0.08 * (1 if hook else -1)),
                     hy + uy * length * 0.12 + vy * (width * 0.08 * (1 if hook else -1)))
        tip = tip[::-1] if False else tip
        left, right = left, right
        mask = self.polygon_mask(left + tip + right[::-1])
        u = ((self.x - cx) * ux + (self.y - cy) * uy) / length
        v = ((self.x - cx) * vx + (self.y - cy) * vy) / (width / 2)
        t = np.clip(u, 0.0, 1.0)
        height = h_in + (h_out - h_in) * t + bulge * np.clip(1.0 - v * v, 0.0, 1.0)
        # Overlapping scales: a ridge across the plate at two thirds throws its own little shadow.
        height += 0.035 * np.exp(-((t - 0.42) / 0.05) ** 2) + 0.025 * np.exp(-((t - 0.7) / 0.04) ** 2)
        shoulder = self.soft(mask, round_)
        if drape:
            self.drape(mask, (0.04 + bulge * np.clip(1.0 - v * v, 0.0, 1.0)) * shoulder, material)
        else:
            self.raise_(mask, height * (0.35 + 0.65 * shoulder), material)
        return (cx + ux * length * 0.55 + vx * curl * length * 0.3, cy + uy * length * 0.55 + vy * curl * length * 0.3)

    def tusk(self, bx, by, angle, length, width, h_base, h_tip, curl=0.0, material=BONE):
        """A tapering horn from (bx, by) along ``angle``, curving by ``curl``; height climbs towards the tip."""
        steps = 40
        left, right = [], []
        a = angle
        x, y = bx, by
        spine = []
        for i in range(steps + 1):
            t = i / steps
            spine.append((x, y, t))
            half = width / 2 * (1 - t) ** 0.8 + 0.004
            nx, ny = -math.sin(a), math.cos(a)
            left.append((x - nx * half, y - ny * half))
            right.append((x + nx * half, y + ny * half))
            x += math.cos(a) * length / steps
            y += math.sin(a) * length / steps
            a += curl / steps
        mask = self.polygon_mask(left + right[::-1])
        # Height by nearest spine sample.
        best = np.full((N, N), 1e9, np.float32)
        height = np.zeros((N, N), np.float32)
        for sx, sy, t in spine:
            d = (self.x - sx) ** 2 + (self.y - sy) ** 2
            closer = d < best
            best[closer] = d[closer]
            height[closer] = h_base + (h_tip - h_base) * math.sin(t * math.pi * 0.6) / math.sin(math.pi * 0.6)
        self.raise_(mask, height * (0.6 + 0.4 * self.soft(mask, 0.015)), material)

    def slab(self, cx, cy, angle, w, d, lift_edge, lift, material=SLAB, drape=False):
        """A paving fragment, one edge levered up by ``lift``: its tilted face catches or loses the sun."""
        rng = self.rng
        ux, uy = math.cos(angle), math.sin(angle)
        vx, vy = -uy, ux
        corners = [(-w / 2, -d / 2), (w / 2, -d / 2), (w / 2, d / 2), (-w / 2, d / 2)]
        pts = []
        for i, (a, b) in enumerate(corners):
            pts.append((cx + ux * a + vx * b, cy + uy * a + vy * b))
            if rng.random() < 0.5:  # a broken corner or a chip out of the edge
                na, nb = corners[(i + 1) % 4]
                q = rng.uniform(0.3, 0.7)
                ma, mb = a + (na - a) * q, b + (nb - b) * q
                pull = rng.uniform(0.08, 0.25)
                pts.append((cx + ux * ma * (1 - pull) + vx * mb * (1 - pull), cy + uy * ma * (1 - pull) + vy * mb * (1 - pull)))
        mask = self.polygon_mask(pts)
        u = ((self.x - cx) * ux + (self.y - cy) * uy) / (w / 2)
        tilt = np.clip((u * lift_edge + 1) / 2, 0.0, 1.0)
        thick = 0.05
        if drape:
            self.drape(mask, thick + lift * 0.4 * tilt, material)
        else:
            self.raise_(mask, (thick + lift * tilt) * (0.5 + 0.5 * self.soft(mask, 0.01)), material)

    def blob(self, cx, cy, r, h, material=EARTH, lumps=5, squash=1.0, angle=0.0, drape=False):
        """An irregular low mound: thrown earth, a clod, a sac's footing."""
        rng = self.rng
        phases = [rng.uniform(0, 2 * math.pi) for _ in range(3)]
        dx, dy = self.x - cx, self.y - cy
        ca, sa = math.cos(angle), math.sin(angle)
        lx, ly = (dx * ca + dy * sa), (-dx * sa + dy * ca) / squash
        theta = np.arctan2(ly, lx)
        edge = r * (1 + 0.10 * np.sin(lumps * theta + phases[0]) + 0.05 * np.sin((lumps + 2) * theta + phases[1])
                    + 0.03 * np.sin(7 * theta + phases[2]))
        d = np.hypot(lx, ly) / edge
        mask = d < 1
        profile = h * np.clip(1 - d * d, 0, 1) ** 0.7
        if drape:
            self.drape(mask, profile + 0.02, material)
        else:
            self.raise_(mask, profile, material)

    def sac(self, cx, cy, r, h, lean_angle, lean, material=SAC, pore=True):
        """A spore sac: a swollen dome whose crown is pushed off-centre by ``lean``, with a glowing pore on it."""
        rng = self.rng
        px, py = cx + math.cos(lean_angle) * lean * r, cy + math.sin(lean_angle) * lean * r
        dx, dy = self.x - cx, self.y - cy
        theta = np.arctan2(dy, dx)
        ph = rng.uniform(0, 2 * math.pi)
        edge = r * (1 + 0.05 * np.sin(3 * theta + ph) + 0.03 * np.sin(5 * theta + 2 * ph))
        d0 = np.hypot(dx, dy) / edge
        # Distance from the pushed crown, scaled so the edge stays where it is.
        pull = np.clip(1 - d0, 0, 1)
        qx, qy = self.x - (cx + (px - cx) * pull), self.y - (cy + (py - cy) * pull)
        d = np.clip(np.hypot(qx, qy) / edge, 0, 1)
        mask = d0 < 1
        # Lobes: a shallow furrow or two across the dome so it reads swollen rather than turned.
        bend = (dx * math.cos(ph) + dy * math.sin(ph)) / r
        across = (-dx * math.sin(ph) + dy * math.cos(ph)) / r - 0.25 * bend * bend - 0.15
        furrow = 1 - 0.06 * np.exp(-(across ** 2) / 0.006) * (d < 0.8)
        self.raise_(mask, h * np.sqrt(np.clip(1 - d * d, 0, 1)) * furrow, material)
        if pore:
            self.node(px, py, r * 0.17, 1.0)
        return px, py

    def tube(self, points, width, h, material=UNDER):
        """A root or tendril along a polyline, round in section, thinning to its end."""
        height = np.zeros((N, N), np.float32)
        mask = np.zeros((N, N), bool)
        n = len(points) - 1
        for i in range(n):
            (ax, ay), (bx, by) = points[i], points[i + 1]
            w = width * (1 - 0.75 * i / n)
            lo_x, hi_x = min(ax, bx) - w, max(ax, bx) + w
            lo_y, hi_y = min(ay, by) - w, max(ay, by) + w
            j0, i0 = (int(max(0, v)) for v in self.px(lo_x, lo_y))
            j1, i1 = (int(min(N, v + 2)) for v in self.px(hi_x, hi_y))
            if j1 <= j0 or i1 <= i0:
                continue
            xs, ys = self.x[i0:i1, j0:j1], self.y[i0:i1, j0:j1]
            ex, ey = bx - ax, by - ay
            ll = ex * ex + ey * ey or 1e-9
            t = np.clip(((xs - ax) * ex + (ys - ay) * ey) / ll, 0, 1)
            dist = np.hypot(xs - (ax + ex * t), ys - (ay + ey * t)) / w
            inside = dist < 1
            sect = h * (1 - 0.6 * i / n) * np.sqrt(np.clip(1 - dist * dist, 0, 1))
            sub = height[i0:i1, j0:j1]
            np.maximum(sub, np.where(inside, sect, 0), out=sub)
            mask[i0:i1, j0:j1] |= inside
        self.raise_(mask, height, material)

    def wander(self, x, y, angle, length, steps=7, jitter=0.35):
        pts = [(x, y)]
        for _ in range(steps):
            angle += self.rng.uniform(-jitter, jitter)
            x += math.cos(angle) * length / steps
            y += math.sin(angle) * length / steps
            pts.append((x, y))
        return pts

    def crack(self, x, y, angle, length, width):
        pts = self.wander(x, y, angle, length, steps=6, jitter=0.6)
        self.cracks.append((pts, width))
        if self.rng.random() < 0.6:
            k = self.rng.randint(2, 4)
            bx, by = pts[k]
            self.cracks.append((self.wander(bx, by, angle + self.rng.choice((-1, 1)) * self.rng.uniform(0.5, 1.0),
                                            length * 0.4, steps=4, jitter=0.6), width * 0.7))


def rim_radius(theta, base, phases, collapse_angle, collapse):
    r = base * (1 + 0.11 * np.sin(2 * theta + phases[0]) + 0.07 * np.sin(3 * theta + phases[1])
                + 0.035 * np.sin(7 * theta + phases[2]))
    return r * (1 + collapse * np.clip(np.cos(theta - collapse_angle), 0, 1) ** 2)


def bore_body(f: Field, mother: bool):
    rng = f.rng
    half = f.cells / 2
    R = 0.78 if mother else 0.58          # pit rim radius
    D = 2.6 if mother else 1.8            # shaft depth
    up = rng.uniform(0, 2 * math.pi)      # the upheaved side
    down = up + math.pi + rng.uniform(-0.4, 0.4)  # the collapsed side
    # The pit sits off-centre, nudged away from the shadow side so the cast shadow stays on the canvas.
    c0 = (rng.uniform(-0.16, 0.16) - 0.10, rng.uniform(-0.16, 0.16) - 0.10)
    phases = [rng.uniform(0, 2 * math.pi) for _ in range(3)]
    slant = R * 0.55

    # Thrown earth: a berm round the rim, heaped on the upheaved side and broken through on the collapsed one.
    dx, dy = f.x - c0[0], f.y - c0[1]
    theta = np.arctan2(dy, dx)
    rim = rim_radius(theta, R, phases, down, 0.22)
    upness = np.cos(theta - up)
    width = R * (0.42 + 0.48 * np.clip(upness, 0, 1) ** 1.3) * (1 + 0.18 * np.sin(5 * theta + phases[1]))
    width *= np.where(np.cos(theta - down) > 0.75, 0.35, 1.0)
    rr = np.hypot(dx, dy)
    s = (rr - rim) / width
    berm_mask = (s > -0.2) & (s < 1)
    berm_h = (0.10 + 0.32 * np.clip(upness, 0, 1) ** 1.5) * (1 if not mother else 1.25)
    berm = berm_h * np.clip(np.sin(np.clip(s + 0.2, 0, 1.2) / 1.2 * math.pi), 0, 1) ** 0.8
    f.raise_(berm_mask, berm, EARTH)
    for _ in range(rng.randint(9, 14) if mother else rng.randint(6, 10)):
        a = up + rng.gauss(0, 1.3)
        r0 = R * rng.uniform(1.15, 1.85)
        f.blob(c0[0] + math.cos(a) * r0, c0[1] + math.sin(a) * r0, rng.uniform(0.05, 0.13) * (1.3 if mother else 1),
               rng.uniform(0.05, 0.14), EARTH, lumps=rng.randint(3, 6))

    # The shaft: rings stepping down and towards the upheaved side, so that wall is sheer and the other a ramp.
    for k in range(97):
        t = k / 96
        cx = c0[0] + math.cos(up) * slant * t
        cy = c0[1] + math.sin(up) * slant * t
        ddx, ddy = f.x - cx, f.y - cy
        th = np.arctan2(ddy, ddx)
        radius = rim_radius(th, R, phases, down, 0.22 * (1 - t)) * (1 - 0.55 * min(1.0, t / 0.55) ** 0.8)
        inside = np.hypot(ddx, ddy) < radius
        depth = -D * t ** 0.85
        for rib in (0.22, 0.43, 0.62, 0.8):  # fleshy rings of the throat, receding into the dark
            depth += 0.06 * (1 - t) * math.exp(-((t - rib) / 0.025) ** 2)
        f.h[inside] = depth
        f.m[inside] = FLESH
        f.over |= inside
    bottom = (c0[0] + math.cos(up) * slant, c0[1] + math.sin(up) * slant)

    # Far down: a pale limb climbing and a few embers.
    a = rng.uniform(0, 2 * math.pi)
    lx, ly = bottom[0] + math.cos(a) * R * 0.12, bottom[1] + math.sin(a) * R * 0.12
    f.tusk(lx, ly, a + 2.2, R * 0.38, R * 0.08, -D * 0.92, -D * 0.78, curl=1.4, material=BONE)
    for _ in range(rng.randint(3, 5)):
        a = rng.uniform(0, 2 * math.pi)
        q = rng.uniform(0.02, 0.2) * R
        f.node(bottom[0] + math.cos(a) * q, bottom[1] + math.sin(a) * q, rng.uniform(0.018, 0.03) * R / 0.58, 0.8)
    f.node(bottom[0], bottom[1], R * 0.28, 0.22)

    # Plates: irregular in angle, size and count; reared up on the upheaved side, torn and slumped opposite.
    count = rng.randint(12, 15) if mother else rng.randint(8, 10)
    # Crowded where the ground rose, sparse where it fell in.
    angles = sorted(up + rng.gauss(0, 1.35) for _ in range(count))
    nodes = rng.sample(range(count), k=min(count, rng.randint(2, 4)))
    for i, a in enumerate(angles):
        k = math.cos(a - up)
        rim_here = float(rim_radius(np.array(a), R, phases, down, 0.22))
        twist = rng.uniform(-0.45, 0.45)
        if k < -0.45 and rng.random() < 0.55:
            # Slid into the funnel: a broken plate lying on the ramp.
            r_in = rim_here * rng.uniform(0.45, 0.8)
            f.plate(c0[0] + math.cos(a) * r_in, c0[1] + math.sin(a) * r_in, a + math.pi + twist * 1.5,
                    R * rng.uniform(0.38, 0.55), R * rng.uniform(0.55, 0.8), 0, 0, bulge=0.06,
                    jagged=0.35, drape=True)
            continue
        big = max(0.0, k)
        length = R * (0.55 + 0.6 * big) * rng.uniform(0.75, 1.25) * (1.1 if mother else 1)
        width = R * rng.uniform(0.75, 1.1) * (0.8 + 0.35 * big)
        h_in = 0.10 + (0.95 if mother else 0.7) * big ** 1.2 * rng.uniform(0.7, 1.15)
        h_out = 0.06 + 0.22 * big
        r_in = rim_here * rng.uniform(0.74, 0.92)
        mid = f.plate(c0[0] + math.cos(a) * r_in, c0[1] + math.sin(a) * r_in, a + twist, length, width,
                      h_in, h_out, bulge=0.06 + 0.06 * big, jagged=0.0 if k > -0.2 else rng.uniform(0.2, 0.45))
        if i in nodes:
            f.node(mid[0], mid[1], 0.028 * (1.2 if mother else 1), 1.0)
    if mother:
        # The mother bore's second, inner terrace of plates, only on the side that rose.
        for _ in range(rng.randint(4, 6)):
            a = up + rng.uniform(-1.3, 1.3)
            rim_here = float(rim_radius(np.array(a), R, phases, down, 0.22))
            f.plate(c0[0] + math.cos(a) * rim_here * 0.62, c0[1] + math.sin(a) * rim_here * 0.62, a + rng.uniform(-0.4, 0.4),
                    R * rng.uniform(0.3, 0.45), R * rng.uniform(0.5, 0.7), rng.uniform(0.5, 0.9), 0.35,
                    bulge=0.05)

    # Tusks: bone hooks leaning in over the hole from the upheaved side, each its own length, some snapped.
    for _ in range(rng.randint(5, 8) if mother else rng.randint(2, 4)):
        a = up + rng.uniform(-1.25, 1.25)
        rim_here = float(rim_radius(np.array(a), R, phases, down, 0.22))
        base = rim_here * rng.uniform(1.0, 1.25)
        length = R * rng.uniform(0.35, 0.8) * (0.5 if rng.random() < 0.25 else 1.0)
        f.tusk(c0[0] + math.cos(a) * base, c0[1] + math.sin(a) * base, a + math.pi + rng.uniform(-0.35, 0.35),
               length, R * rng.uniform(0.09, 0.15), rng.uniform(0.35, 0.6), rng.uniform(0.75, 1.15) * (1.2 if mother else 1),
               curl=rng.uniform(-0.9, 0.9))

    # Paving thrown up and cracked round the rim, mostly where the ground rose; a slab or two sliding in.
    for _ in range(rng.randint(10, 15) if mother else rng.randint(6, 9)):
        a = up + rng.gauss(0, 1.1)
        r0 = R * rng.uniform(1.05, 1.75)
        size = rng.uniform(0.16, 0.36) * (1.2 if mother else 1)
        toward_pit = a + math.pi
        f.slab(c0[0] + math.cos(a) * r0, c0[1] + math.sin(a) * r0, toward_pit + rng.uniform(-0.6, 0.6), size,
               size * rng.uniform(0.6, 1.0), rng.choice((-1, 1)), rng.uniform(0.08, 0.32))
    for _ in range(rng.randint(1, 3)):
        a = down + rng.uniform(-0.5, 0.5)
        rim_here = float(rim_radius(np.array(a), R, phases, down, 0.22))
        r0 = rim_here * rng.uniform(0.6, 0.85)
        size = rng.uniform(0.16, 0.26)
        f.slab(c0[0] + math.cos(a) * r0, c0[1] + math.sin(a) * r0, a + rng.uniform(-0.5, 0.5), size, size * 0.8,
               1, 0.1, drape=True)

    # Roots running out further on one side.
    root_side = up + rng.choice((-1, 1)) * rng.uniform(0.6, 1.6)
    for _ in range(rng.randint(6, 9) if mother else rng.randint(4, 6)):
        near = rng.random() < 0.65
        a = root_side + rng.uniform(-0.6, 0.6) if near else rng.uniform(0, 2 * math.pi)
        start = R * rng.uniform(1.05, 1.3)
        sx, sy = c0[0] + math.cos(a) * start, c0[1] + math.sin(a) * start
        room = half * 0.93 - max(abs(sx), abs(sy))
        length = min(room, (R * rng.uniform(0.9, 1.6)) if near else R * rng.uniform(0.25, 0.55))
        if length > 0.08:
            f.tube(f.wander(sx, sy, a, length, steps=8, jitter=0.4), rng.uniform(0.035, 0.06) * (1.2 if mother else 1),
                   rng.uniform(0.05, 0.09), UNDER)

    # Cracks in the ground the body lifted.
    for _ in range(rng.randint(9, 13) if mother else rng.randint(6, 9)):
        a = up + rng.gauss(0, 1.4)
        r0 = R * rng.uniform(1.3, 1.7)
        f.crack(c0[0] + math.cos(a) * r0, c0[1] + math.sin(a) * r0, a, R * rng.uniform(0.4, 1.0), 0.012)


def colony_body(f: Field, grade: str):
    rng = f.rng
    body = {"spore-colony-fissure": 0.5, "spore-colony-bore": 0.675, "spore-colony-maw": 0.85}[grade]
    lean = rng.uniform(0, 2 * math.pi)
    cx, cy = rng.uniform(-0.1, 0.06) * body, rng.uniform(-0.1, 0.06) * body

    # Footing: lopsided mound of earth and under-plate the cluster has pushed up.
    f.blob(cx + rng.uniform(-0.1, 0.1) * body, cy + rng.uniform(-0.1, 0.1) * body, body * rng.uniform(0.82, 0.95),
           0.08, EARTH, lumps=rng.randint(3, 5), squash=rng.uniform(0.7, 0.95), angle=rng.uniform(0, math.pi))

    # Roots, mostly to one side.
    side = rng.uniform(0, 2 * math.pi)
    for _ in range({"spore-colony-fissure": 3, "spore-colony-bore": 4, "spore-colony-maw": 6}[grade]):
        a = side + rng.uniform(-0.9, 0.9) if rng.random() < 0.7 else rng.uniform(0, 2 * math.pi)
        sx, sy = cx + math.cos(a) * body * 0.55, cy + math.sin(a) * body * 0.55
        room = f.cells / 2 * 0.9 - max(abs(sx), abs(sy))
        length = min(room, body * rng.uniform(0.35, 0.8))
        if length > 0.05:
            f.tube(f.wander(sx, sy, a, length, steps=6, jitter=0.45), body * rng.uniform(0.04, 0.06),
                   rng.uniform(0.03, 0.05), UNDER)

    # Cupping plates on one flank only, splayed and of differing size.
    flank = rng.uniform(0, 2 * math.pi)
    plates = {"spore-colony-fissure": rng.randint(2, 3), "spore-colony-bore": rng.randint(3, 4),
              "spore-colony-maw": rng.randint(4, 6)}[grade]
    for _ in range(plates):
        a = flank + rng.uniform(-1.2, 1.2)
        r_in = body * rng.uniform(0.25, 0.4)
        f.plate(cx + math.cos(a) * r_in, cy + math.sin(a) * r_in, a + rng.uniform(-0.3, 0.3),
                body * rng.uniform(0.42, 0.62), body * rng.uniform(0.55, 0.85), rng.uniform(0.18, 0.3) * body / 0.5,
                0.05, bulge=0.04, jagged=rng.uniform(0, 0.3), round_=0.02)

    # The dominant sac, then secondaries pressed against it at uneven angles, then buds.
    dom_r = body * {"spore-colony-fissure": 0.62, "spore-colony-bore": 0.5, "spore-colony-maw": 0.48}[grade]
    dom_h = dom_r * rng.uniform(1.15, 1.4)
    crown = f.sac(cx, cy, dom_r, dom_h, lean, rng.uniform(0.2, 0.35))
    sacs = [(cx, cy, dom_r, dom_h)]
    secondaries = {"spore-colony-fissure": 0, "spore-colony-bore": 2, "spore-colony-maw": 3}[grade]
    a = rng.uniform(0, 2 * math.pi)
    for _ in range(secondaries):
        a += rng.uniform(1.1, 2.3)
        r = dom_r * rng.uniform(0.42, 0.7)
        d = dom_r + r * rng.uniform(0.25, 0.55)
        sx, sy = cx + math.cos(a) * d, cy + math.sin(a) * d
        h = r * rng.uniform(1.0, 1.35)
        f.sac(sx, sy, r, h, a + rng.uniform(-0.6, 0.6), rng.uniform(0.15, 0.4))
        sacs.append((sx, sy, r, h))
    buds = {"spore-colony-fissure": rng.randint(1, 2), "spore-colony-bore": rng.randint(2, 4),
            "spore-colony-maw": rng.randint(3, 5)}[grade]
    for _ in range(buds):
        px_, py_, pr, _ = rng.choice(sacs)
        a = rng.uniform(0, 2 * math.pi)
        r = dom_r * rng.uniform(0.18, 0.3)
        d = pr + r * 0.3
        f.sac(px_ + math.cos(a) * d, py_ + math.sin(a) * d, r, r * rng.uniform(0.9, 1.3), a, 0.3,
              pore=rng.random() < 0.5)

    # Spines bunched on one flank of the crown, each its own length and lean.
    spines = {"spore-colony-fissure": rng.randint(1, 3), "spore-colony-bore": rng.randint(3, 5),
              "spore-colony-maw": rng.randint(6, 9)}[grade]
    bunch = rng.uniform(0, 2 * math.pi)
    for _ in range(spines):
        sx, sy, r, h = rng.choice(sacs[:2] if len(sacs) > 1 else sacs)
        a = bunch + rng.gauss(0, 0.6)
        q = rng.uniform(0.55, 0.85)
        bx, by = sx + math.cos(a) * r * q, sy + math.sin(a) * r * q
        base_h = f.height_at(bx, by)
        f.tusk(bx, by, a + rng.uniform(-0.4, 0.4), body * rng.uniform(0.16, 0.34), body * rng.uniform(0.05, 0.08),
               base_h, base_h + rng.uniform(0.05, 0.14), curl=rng.uniform(-0.6, 0.6))

    # Cracks where the footing heaved.
    for _ in range({"spore-colony-fissure": 3, "spore-colony-bore": 4, "spore-colony-maw": 6}[grade]):
        a = rng.uniform(0, 2 * math.pi)
        f.crack(cx + math.cos(a) * body * 0.8, cy + math.sin(a) * body * 0.8, a, body * rng.uniform(0.3, 0.6), 0.008)
    return crown


def build(part: str, variant: int) -> Field:
    f = Field(CANVAS_CELLS[part], SEEDS[part] + 17 * variant)
    if part == "bore":
        bore_body(f, mother=False)
    elif part == "mother-bore":
        bore_body(f, mother=True)
    else:
        colony_body(f, part)
    return f


# --- lighting -----------------------------------------------------------------------------------------------


def box_blur(a: np.ndarray, r: int) -> np.ndarray:
    if r < 1:
        return a
    p = np.pad(a, r, mode="edge")
    c = np.cumsum(np.cumsum(p, 0), 1)
    c = np.pad(c, ((1, 0), (1, 0)))
    k = 2 * r + 1
    return (c[k:, k:] - c[:-k, k:] - c[k:, :-k] + c[:-k, :-k]) / (k * k)


def sun_visibility(f: Field) -> np.ndarray:
    """1 where the sun reaches a pixel, 0 where something between it and the sun is taller than the ray."""
    sx, sy = to_sun_screen()
    step = 1.0  # pixels per march step along the sun bearing
    rise = step / f.ppc * math.tan(SUN_ELEVATION)
    h = f.h
    span = float(h.max() - h.min())
    steps = int(span / rise) + 2
    lit = np.ones((N, N), bool)
    for s in range(1, steps + 1):
        ox, oy = int(round(sx * step * s)), int(round(sy * step * s))
        shifted = np.full((N, N), -1e9, np.float32)
        # sample h at (x + ox, y + oy)
        ys0, ys1 = max(0, -oy), min(N, N - oy)
        xs0, xs1 = max(0, -ox), min(N, N - ox)
        shifted[ys0:ys1, xs0:xs1] = h[ys0 + oy:ys1 + oy, xs0 + ox:xs1 + ox]
        lit &= ~(shifted > h + rise * s + 1e-4)
    vis = lit.astype(np.float32)
    return np.asarray(Image.fromarray((vis * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(1.6)),
                      np.float32) / 255.0


def light(f: Field):
    """Diffuse sun with ray-marched shadow, sky ambient, and occlusion from depth and creases."""
    hp = f.h * f.ppc
    gy, gx = np.gradient(hp)
    norm = np.sqrt(gx * gx + gy * gy + 1)
    nx, ny, nz = -gx / norm, -gy / norm, 1 / norm
    sx, sy = to_sun_screen()
    lx, ly, lz = sx * math.cos(SUN_ELEVATION), sy * math.cos(SUN_ELEVATION), math.sin(SUN_ELEVATION)
    lambert = np.clip(nx * lx + ny * ly + nz * lz, 0, 1)
    vis = sun_visibility(f)
    depth = np.clip(-f.h, 0, None)
    ao_depth = np.exp(-depth * 0.55) * np.clip(1 - np.clip(depth - 1.2, 0, None) * 0.6, 0.15, 1)
    crease = np.clip(box_blur(f.h, int(0.05 * f.ppc)) - f.h, 0, None)
    ao_crease = np.clip(1 - crease * 4.0, 0.45, 1)
    sky = 0.30 * (0.55 + 0.45 * nz)
    return np.clip((sky + 0.95 * lambert * vis) * ao_depth * ao_crease, 0, 1.2), vis


def guide_image(f: Field) -> Image.Image:
    shade, _ = light(f)
    albedo = ALBEDO[f.m]
    rgb = albedo * shade[..., None]
    g = np.clip(f.glow, 0, 1)[..., None]
    rgb = rgb * (1 - g) + GLOW * g
    alpha = (f.m != NONE).astype(np.float32)
    alpha = np.asarray(Image.fromarray((alpha * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(0.7)),
                       np.float32) / 255.0
    out = np.dstack([np.clip(rgb, 0, 1), alpha])
    return Image.fromarray((out * 255 + 0.5).astype(np.uint8), "RGBA")


def ground_layer(f: Field) -> Image.Image:
    """What the body does to the ground around it, beneath the body: its cast shadow, a contact shade, cracks.

    Drawn from the heightfield, so the shadow falls exactly where the guide's lighting says it does. Dark and
    translucent, so it reads on paving, grass or creep alike.
    """
    _, vis = light(f)
    body = f.m != NONE
    ground = ~body
    shadow = (1 - vis) * ground
    contact = np.asarray(Image.fromarray((body * 255).astype(np.uint8)).filter(
        ImageFilter.GaussianBlur(0.06 * f.ppc)), np.float32) / 255.0
    contact = np.clip(contact * 1.6, 0, 1) * ground
    cracks = Image.new("L", (N, N), 0)
    draw = ImageDraw.Draw(cracks)
    for pts, width in f.cracks:
        xy = [f.px(x, y) for x, y in pts]
        for i in range(len(xy) - 1):
            w = max(1, int(width * f.ppc * (1 - 0.7 * i / len(xy))))
            draw.line(xy[i:i + 2], fill=255, width=w)
    crack = np.asarray(cracks.filter(ImageFilter.GaussianBlur(0.8)), np.float32) / 255.0 * ground
    # Under the body itself the ground is in deep contact shade, so wherever the reskin's edge falls a pixel
    # inside the guide's, the ground showing through is shaded rather than a bright seam.
    under = np.asarray(Image.fromarray((body * 255).astype(np.uint8)).filter(ImageFilter.MaxFilter(5)), np.float32) / 255.0
    alpha = np.clip(np.maximum(0.42 * shadow + 0.30 * contact + 0.75 * crack, 0.55 * under), 0, 0.85)
    rgb = np.zeros((N, N, 3), np.float32)
    rgb[...] = (0.07, 0.07, 0.10)
    out = np.dstack([rgb, alpha])
    return Image.fromarray((out * 255 + 0.5).astype(np.uint8), "RGBA")


def material_image(f: Field) -> Image.Image:
    return Image.fromarray(f.m, "L")


def guide_path(templates: Path, part: str, variant: int) -> Path:
    return templates / f"swarm-{part}-{variant}-guide.png"


def main() -> None:
    templates = Path(__file__).resolve().parent.parent / "templates"
    for part in PARTS:
        for v in range(1, VARIANTS[part] + 1):
            f = build(part, v)
            out = guide_path(templates, part, v)
            guide_image(f).save(out, optimize=True)
            print(out)


if __name__ == "__main__":
    main()
