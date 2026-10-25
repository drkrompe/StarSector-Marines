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
import sys
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
    "ground-colony": 2.0,
    "brood-spire": 7.0,
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
    "ground-colony": 1.4,
    "brood-spire": 1.45,
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
    "ground-colony": 3,
    "brood-spire": 3,
}
PARTS = tuple(CANVAS_CELLS)
# Damage states. ``wounded`` is drawn once a structure has lost half its durability: plates torn out of their
# sockets over raw sinew, a sac burst, a spine snapped, scars across the carapace, the amber dimmed. ``remains``
# is what stays on the ground after it dies: a bore's shaft caved in and choked with rubble, its plates slumped
# and its tusks snapped; a colony's sacs burst and deflated; no amber at all. Both are built from the same seeded
# geometry as the intact painting (a separate draw stream decides the damage), so a structure keeps its own shape
# through all three.
STATES = ("intact", "wounded", "remains")
SEEDS = {"bore": 1100, "mother-bore": 2300, "spore-colony-fissure": 3100, "spore-colony-bore": 3700,
         "spore-colony-maw": 4100, "ground-colony": 5300,
         "brood-spire": 6100}
# The ground colony's body plan (``ground_colony_body``).
GROUND_COLONY_STYLES = ("ridge", "star", "urchin")
GROUND_COLONY_STYLE = "star"  # the owner's pick, 2026-10-22
# The brood spire's body plan (``spire_body``); ``roost`` is built on until the owner picks.
SPIRE_STYLES = ("roost", "chimney", "clutch")
SPIRE_STYLE = "roost"


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
        self.state = "intact"
        self.tears = []
        # Damage decisions draw from their own stream, so the intact geometry's draws are the same in every state.
        self.dmg = random.Random(seed * 7919 + 13)

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
        strength *= {"intact": 1.0, "wounded": 0.55, "remains": 0.0}[self.state]
        d = np.hypot(self.x - x, self.y - y) / r
        self.glow = np.maximum(self.glow, strength * np.clip(1.2 - d, 0.0, 1.0) ** 1.2)

    def height_at(self, x: float, y: float) -> float:
        i = int(np.clip(N / 2 + y * self.ppc, 0, N - 1))
        j = int(np.clip(N / 2 + x * self.ppc, 0, N - 1))
        return float(self.h[i, j])

    # --- primitives --------------------------------------------------------------------------------------

    def plate(self, cx, cy, angle, length, width, h_in, h_out, bulge=0.08, jagged=0.0, material=CHITIN,
              drape=False, round_=0.035, torn=False, slump=1.0):
        """A curved chitin plate whose spine runs from (cx, cy) outward along ``angle``.

        Height runs from ``h_in`` at the inner end to ``h_out`` at the outer end, bowed up across its width.
        ``jagged`` tears the outer end. A draped plate is laid over the surface beneath it instead. A ``torn``
        plate has been ripped out of its socket: a jagged stump stands at its root and raw sinew lies where the
        rest of it was. ``slump`` lowers a dead plate.
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
        height = height * slump
        if torn:
            # Opened after every plate is laid (``open_tears``), so the hole goes through whatever overlapped it.
            ragged = 0.18 + 0.12 * np.sin(v * 9.0 + cx * 13.0)
            stump = mask & (u < ragged)
            self.tears.append((mask & (shoulder > 0.25) & ~stump, 0.012 + 0.02 * np.clip(1.0 - v * v, 0.0, 1.0),
                               stump, height * 0.6 * (0.35 + 0.65 * shoulder), material))
            return (cx + ux * length * 0.55, cy + uy * length * 0.55)
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

    def sac(self, cx, cy, r, h, lean_angle, lean, material=SAC, pore=True, burst=0.0):
        """A spore sac: a swollen dome whose crown is pushed off-centre by ``lean``, with a glowing pore on it.

        ``burst`` from 0 to 1 ruptures it: the dome sags and the crown is torn open on raw flesh, and it has no pore.
        """
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
        dome = h * np.sqrt(np.clip(1 - d * d, 0, 1)) * furrow
        if burst > 0:
            # Deflated: the dome sags to a wrinkled skin, torn open at the crown on raw flesh.
            sag = 1 - 0.72 * burst
            wrinkle = 1 + 0.12 * burst * np.sin(theta * 7 + ph) * np.clip(d, 0, 1)
            dome = dome * sag * wrinkle
            self.raise_(mask, dome, material)
            hole = mask & (d < 0.25 + 0.22 * burst + 0.05 * np.sin(theta * 5 + ph))
            self.h[hole] = np.minimum(self.h[hole], h * sag * 0.25)
            self.m[hole] = FLESH
            return px, py
        self.raise_(mask, dome, material)
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

    def open_tears(self):
        """Opens the torn plates' sockets: raw sinew lying low where each plate was, and a broken stump at its root.

        Low means at most a few centimetres up: a socket over the pit keeps the pit's depth.
        """
        for socket, lift, stump, stump_h, material in self.tears:
            self.h[socket] = np.minimum(self.h[socket], lift[socket])
            self.m[socket] = FLESH
            self.over |= socket
            self.h[stump] = stump_h[stump]
            self.m[stump] = material
            self.over |= stump
        self.tears = []

    def scar(self, pts, width, depth):
        """A gash across the carapace and sacs along a polyline: the surface cut down by ``depth``."""
        image = Image.new("L", (N, N), 0)
        draw = ImageDraw.Draw(image)
        xy = [self.px(x, y) for x, y in pts]
        for i in range(len(xy) - 1):
            draw.line(xy[i:i + 2], fill=255, width=max(1, int(width * self.ppc * (1 - 0.6 * i / len(xy)))))
        cut = np.asarray(image.filter(ImageFilter.GaussianBlur(1.0)), np.float32) / 255.0
        on = np.isin(self.m, (CHITIN, SAC)) & (cut > 0.05)
        self.h[on] -= depth * cut[on]
        self.m[on & (cut > 0.6)] = FLESH

    def scars(self, count, centre, radius):
        """Damage scars scattered over a body, from the damage stream."""
        dmg = self.dmg
        # Scars start on carapace or a sac, where a round would have struck.
        ys, xs = np.nonzero(np.isin(self.m, (CHITIN, SAC)) & (np.hypot(self.x - centre[0], self.y - centre[1]) < radius))
        if len(xs) == 0:
            return
        for _ in range(count):
            k = dmg.randrange(len(xs))
            x, y = float(self.x[ys[k], xs[k]]), float(self.y[ys[k], xs[k]])
            pts = [(x, y)]
            heading = dmg.uniform(0, 2 * math.pi)
            for _ in range(4):
                heading += dmg.uniform(-0.5, 0.5)
                x += math.cos(heading) * radius * 0.12
                y += math.sin(heading) * radius * 0.12
                pts.append((x, y))
            self.scar(pts, radius * dmg.uniform(0.03, 0.05), dmg.uniform(0.08, 0.14))

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
    dead = f.state == "remains"
    hurt = f.state == "wounded"
    dmg = f.dmg
    if dead:
        D *= 0.28                         # caved in: the throat has fallen in on itself
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
    if dead:
        # Rubble choking the hole: the berm and the lip slid in on top of the throat.
        for _ in range(dmg.randint(7, 11) if mother else dmg.randint(5, 8)):
            a = dmg.uniform(0, 2 * math.pi)
            q = R * dmg.uniform(0.0, 0.7)
            f.blob(c0[0] + math.cos(a) * q, c0[1] + math.sin(a) * q, R * dmg.uniform(0.18, 0.34),
                   dmg.uniform(0.04, 0.14), EARTH, lumps=dmg.randint(3, 6))

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
    # Torn out of their sockets: two of a wounded bore's plates, more of a dead one's.
    tear_left = (3 if hurt else 4 if dead else 0) + (1 if mother and f.state != "intact" else 0)
    slump = 0.4 if dead else 1.0
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
        # Torn out of their sockets: the big reared plates on the upheaved side, where it shows.
        torn_here = tear_left > 0 and big > 0.1 and dmg.random() < 0.75
        tear_left -= 1 if torn_here else 0
        length = R * (0.55 + 0.6 * big) * rng.uniform(0.75, 1.25) * (1.1 if mother else 1)
        width = R * rng.uniform(0.75, 1.1) * (0.8 + 0.35 * big)
        h_in = 0.10 + (0.95 if mother else 0.7) * big ** 1.2 * rng.uniform(0.7, 1.15)
        h_out = 0.06 + 0.22 * big
        r_in = rim_here * rng.uniform(0.74, 0.92)
        mid = f.plate(c0[0] + math.cos(a) * r_in, c0[1] + math.sin(a) * r_in, a + twist, length, width,
                      h_in, h_out, bulge=0.06 + 0.06 * big, jagged=0.0 if k > -0.2 else rng.uniform(0.2, 0.45),
                      torn=torn_here, slump=slump)
        if i in nodes:
            f.node(mid[0], mid[1], 0.028 * (1.2 if mother else 1), 1.0)
    f.open_tears()
    if mother:
        # The mother bore's second, inner terrace of plates, only on the side that rose.
        for _ in range(rng.randint(4, 6)):
            a = up + rng.uniform(-1.3, 1.3)
            rim_here = float(rim_radius(np.array(a), R, phases, down, 0.22))
            f.plate(c0[0] + math.cos(a) * rim_here * 0.62, c0[1] + math.sin(a) * rim_here * 0.62, a + rng.uniform(-0.4, 0.4),
                    R * rng.uniform(0.3, 0.45), R * rng.uniform(0.5, 0.7), rng.uniform(0.5, 0.9), 0.35,
                    bulge=0.05, slump=slump)

    # Tusks: bone hooks leaning in over the hole from the upheaved side, each its own length, some snapped.
    for _ in range(rng.randint(5, 8) if mother else rng.randint(2, 4)):
        a = up + rng.uniform(-1.25, 1.25)
        rim_here = float(rim_radius(np.array(a), R, phases, down, 0.22))
        base = rim_here * rng.uniform(1.0, 1.25)
        length = R * rng.uniform(0.35, 0.8) * (0.5 if rng.random() < 0.25 else 1.0)
        if dead or (hurt and dmg.random() < 0.4):
            length *= dmg.uniform(0.3, 0.5)  # snapped
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
                   rng.uniform(0.05, 0.09) * (0.5 if dead else 1.0), UNDER)

    # Cracks in the ground the body lifted.
    for _ in range(rng.randint(9, 13) if mother else rng.randint(6, 9)):
        a = up + rng.gauss(0, 1.4)
        r0 = R * rng.uniform(1.3, 1.7)
        f.crack(c0[0] + math.cos(a) * r0, c0[1] + math.sin(a) * r0, a, R * rng.uniform(0.4, 1.0), 0.012)
    if f.state != "intact":
        f.scars((6 if hurt else 10) * (2 if mother else 1), c0, R * 1.6)


def colony_body(f: Field, grade: str):
    rng = f.rng
    body = {"spore-colony-fissure": 0.5, "spore-colony-bore": 0.675, "spore-colony-maw": 0.85}[grade]
    lean = rng.uniform(0, 2 * math.pi)
    dead = f.state == "remains"
    hurt = f.state == "wounded"
    dmg = f.dmg
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
                   rng.uniform(0.03, 0.05) * (0.5 if dead else 1.0), UNDER)

    # Cupping plates on one flank only, splayed and of differing size.
    flank = rng.uniform(0, 2 * math.pi)
    plates = {"spore-colony-fissure": rng.randint(2, 3), "spore-colony-bore": rng.randint(3, 4),
              "spore-colony-maw": rng.randint(4, 6)}[grade]
    for _ in range(plates):
        a = flank + rng.uniform(-1.2, 1.2)
        r_in = body * rng.uniform(0.25, 0.4)
        f.plate(cx + math.cos(a) * r_in, cy + math.sin(a) * r_in, a + rng.uniform(-0.3, 0.3),
                body * rng.uniform(0.42, 0.62), body * rng.uniform(0.55, 0.85), rng.uniform(0.18, 0.3) * body / 0.5,
                0.05, bulge=0.04, jagged=rng.uniform(0, 0.3), round_=0.02,
                torn=(dead and dmg.random() < 0.5) or (hurt and dmg.random() < 0.3), slump=0.5 if dead else 1.0)

    f.open_tears()

    # The dominant sac, then secondaries pressed against it at uneven angles, then buds.
    dom_r = body * {"spore-colony-fissure": 0.62, "spore-colony-bore": 0.5, "spore-colony-maw": 0.48}[grade]
    dom_h = dom_r * rng.uniform(1.15, 1.4)
    # A wounded colony has one sac burst: the dominant one when it stands alone, else the first secondary.
    # A dead one has them all burst and deflated.
    burst_dominant = 1.0 if dead else (0.65 if hurt and grade == "spore-colony-fissure" else 0.0)
    crown = f.sac(cx, cy, dom_r, dom_h, lean, rng.uniform(0.2, 0.35), burst=burst_dominant)
    sacs = [(cx, cy, dom_r, dom_h)]
    secondaries = {"spore-colony-fissure": 0, "spore-colony-bore": 2, "spore-colony-maw": 3}[grade]
    a = rng.uniform(0, 2 * math.pi)
    for _ in range(secondaries):
        a += rng.uniform(1.1, 2.3)
        r = dom_r * rng.uniform(0.42, 0.7)
        d = dom_r + r * rng.uniform(0.25, 0.55)
        sx, sy = cx + math.cos(a) * d, cy + math.sin(a) * d
        h = r * rng.uniform(1.0, 1.35)
        burst = 1.0 if dead or (hurt and len(sacs) == 1) else 0.0
        f.sac(sx, sy, r, h, a + rng.uniform(-0.6, 0.6), rng.uniform(0.15, 0.4), burst=burst)
        sacs.append((sx, sy, r, h))
    buds = {"spore-colony-fissure": rng.randint(1, 2), "spore-colony-bore": rng.randint(2, 4),
            "spore-colony-maw": rng.randint(3, 5)}[grade]
    for _ in range(buds):
        px_, py_, pr, _ = rng.choice(sacs)
        a = rng.uniform(0, 2 * math.pi)
        r = dom_r * rng.uniform(0.18, 0.3)
        d = pr + r * 0.3
        f.sac(px_ + math.cos(a) * d, py_ + math.sin(a) * d, r, r * rng.uniform(0.9, 1.3), a, 0.3,
              pore=rng.random() < 0.5, burst=0.9 if dead else 0.0)

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
        heading = a + rng.uniform(-0.4, 0.4)
        length = body * rng.uniform(0.16, 0.34)
        if dead or (hurt and dmg.random() < 0.35):
            length *= dmg.uniform(0.3, 0.5)  # snapped
        f.tusk(bx, by, heading, length, body * rng.uniform(0.05, 0.08),
               base_h, base_h + rng.uniform(0.05, 0.14), curl=rng.uniform(-0.6, 0.6))

    # Cracks where the footing heaved.
    for _ in range({"spore-colony-fissure": 3, "spore-colony-bore": 4, "spore-colony-maw": 6}[grade]):
        a = rng.uniform(0, 2 * math.pi)
        f.crack(cx + math.cos(a) * body * 0.8, cy + math.sin(a) * body * 0.8, a, body * rng.uniform(0.3, 0.6), 0.008)
    if f.state != "intact":
        f.scars(4 if hurt else 6, (cx, cy), body)
    return crown


def ground_colony_body(f: Field, style: str):
    """A ground colony: a low growth hugging the creep that drives spines up out of the ground at what stands near it.

    It answers a gun the swarm cannot rush, so it must never read as a spore colony (domed sacs, a pore aimed at
    the sky). Everything here stays low, a quarter of a spore sac's height, and it is told by spines laid back
    along it rather than by sacs. Three body plans were drawn for the owner's pick (``GROUND_COLONY_STYLES``):

    - ``ridge``: a long, lopsided, half-buried armoured hump, overlapping plates down its back and a crest of bone
      spines laid back along the spine like a dorsal fin, roots fanning out from its low end.
    - ``star``: a knotted low core with thick arms sprawled out across the ground, each arm studded with a row of
      spines, an amber eye at the core.
    - ``urchin``: a flat rosette of plates pressed into the ground, a bristle of bone spines splayed out low from
      its centre in every direction.
    """
    rng = f.rng
    dead = f.state == "remains"
    hurt = f.state == "wounded"
    dmg = f.dmg
    slump = 0.55 if dead else 1.0
    cx, cy = rng.uniform(-0.08, 0.04), rng.uniform(-0.08, 0.04)

    def snapped(length):
        if dead or (hurt and dmg.random() < 0.35):
            return length * dmg.uniform(0.3, 0.5)
        return length

    def spine(bx, by, heading, length, width, rise):
        base = f.height_at(bx, by)
        f.tusk(bx, by, heading, snapped(length), width, base, base + rise * slump, curl=rng.uniform(-0.5, 0.5))

    if style == "ridge":
        axis = rng.uniform(0, 2 * math.pi)
        ux, uy = math.cos(axis), math.sin(axis)
        vx, vy = -uy, ux
        long_, wide = rng.uniform(0.62, 0.72), rng.uniform(0.3, 0.36)
        # Footing of heaved creep, then the hump itself: a long low dome, higher towards one end.
        f.blob(cx, cy, long_ * 1.05, 0.05, EARTH, lumps=4, squash=0.55, angle=axis)
        dx, dy = f.x - cx, f.y - cy
        u, v = (dx * ux + dy * uy) / long_, (dx * vx + dy * vy) / wide
        wob = 1 + 0.12 * np.sin(u * 5 + rng.uniform(0, 6))
        d = np.hypot(u, v / wob)
        hump = d < 1
        rise = 0.26 * (1 + 0.25 * u)
        f.raise_(hump, rise * np.sqrt(np.clip(1 - d * d, 0, 1)) * slump, CHITIN)
        # Shingled plates down each flank, each laid back towards the low end and splayed out off the ridge.
        for side in (-1, 1):
            for k in range(4):
                t = 0.7 - 1.45 * (k + rng.uniform(0.0, 0.3)) / 4
                px_, py_ = cx + ux * long_ * t + vx * wide * 0.2 * side, cy + uy * long_ * t + vy * wide * 0.2 * side
                a = axis + math.pi + side * rng.uniform(0.55, 0.85)
                f.plate(px_, py_, a, wide * rng.uniform(1.0, 1.25), wide * rng.uniform(0.85, 1.05),
                        0.24 * (1 + 0.25 * t), 0.05, bulge=0.035, jagged=rng.uniform(0, 0.2), round_=0.02,
                        torn=(dead and dmg.random() < 0.45) or (hurt and dmg.random() < 0.22), slump=slump)
        f.open_tears()
        # The crest: spines laid back along the ridge, longest mid-back.
        n = rng.randint(6, 8)
        for k in range(n):
            t = -0.75 + 1.5 * k / (n - 1)
            bx, by = cx + ux * long_ * t + vx * rng.uniform(-0.03, 0.03), cy + uy * long_ * t + vy * rng.uniform(-0.03, 0.03)
            lean = axis + math.pi + rng.uniform(-0.5, 0.5) + rng.choice((-1, 1)) * 0.35
            spine(bx, by, lean, 0.24 + 0.2 * (1 - abs(t)), 0.08, 0.16)
        # The vents the spines are driven up through: amber slits along the low end.
        for k in range(rng.randint(2, 3)):
            t = rng.uniform(-0.6, 0.6)
            f.node(cx + ux * long_ * t + vx * wide * 0.5 * rng.choice((-1, 1)),
                   cy + uy * long_ * t + vy * wide * 0.5 * rng.choice((-1, 1)), 0.035, 0.9)
        roots_from, root_dir, body = (cx - ux * long_ * 0.8, cy - uy * long_ * 0.8), axis + math.pi, long_
    elif style == "star":
        body = rng.uniform(0.28, 0.33)
        f.blob(cx, cy, body * 1.6, 0.05, EARTH, lumps=5, squash=0.85, angle=rng.uniform(0, math.pi))
        arms = rng.randint(5, 6)
        a0 = rng.uniform(0, 2 * math.pi)
        tips = []
        for k in range(arms):
            a = a0 + 2 * math.pi * k / arms + rng.uniform(-0.35, 0.35)
            length = rng.uniform(0.5, 0.82)
            pts = f.wander(cx + math.cos(a) * body * 0.5, cy + math.sin(a) * body * 0.5, a, length, steps=6, jitter=0.3)
            f.tube(pts, rng.uniform(0.09, 0.12), 0.13 * slump, UNDER)
            tips.append((pts, a))
        # Plated core.
        for k in range(rng.randint(5, 6)):
            a = a0 + 2 * math.pi * k / 6 + rng.uniform(-0.3, 0.3)
            f.plate(cx + math.cos(a) * body * 0.1, cy + math.sin(a) * body * 0.1, a, body * 1.1, body * 0.95,
                    0.24, 0.07, bulge=0.03, round_=0.02,
                    torn=(dead and dmg.random() < 0.45) or (hurt and dmg.random() < 0.25), slump=slump)
        f.open_tears()
        f.node(cx, cy, 0.07, 1.0)
        # A row of spines down each arm, raked back towards the core.
        for pts, a in tips:
            for (x, y) in pts[1:-1:1]:
                if rng.random() < 0.8:
                    spine(x, y, a + math.pi + rng.uniform(-0.6, 0.6), rng.uniform(0.16, 0.26), 0.065, 0.12)
            ex, ey = pts[-2]
            f.node(ex, ey, 0.025, 0.7)
        roots_from, root_dir = (cx, cy), a0
    else:  # urchin
        body = rng.uniform(0.34, 0.4)
        f.blob(cx, cy, body * 1.5, 0.05, EARTH, lumps=4, squash=0.8, angle=rng.uniform(0, math.pi))
        # A flat rosette: plates radiating, pressed into the ground.
        petals = rng.randint(7, 9)
        a0 = rng.uniform(0, 2 * math.pi)
        for k in range(petals):
            a = a0 + 2 * math.pi * k / petals + rng.uniform(-0.2, 0.2)
            f.plate(cx + math.cos(a) * body * 0.15, cy + math.sin(a) * body * 0.15, a, body * rng.uniform(0.95, 1.25),
                    body * rng.uniform(0.55, 0.7), 0.17, 0.03, bulge=0.025, jagged=rng.uniform(0.1, 0.35), round_=0.02,
                    torn=(dead and dmg.random() < 0.45) or (hurt and dmg.random() < 0.22), slump=slump)
        f.open_tears()
        f.blob(cx, cy, body * 0.4, 0.2 * slump, UNDER, lumps=5)
        f.node(cx, cy, 0.06, 1.0)
        # The bristle: spines splayed out low in every direction, uneven in length, bunched more on one side.
        bunch = rng.uniform(0, 2 * math.pi)
        for k in range(rng.randint(11, 14)):
            a = (bunch + rng.gauss(0, 1.1)) if rng.random() < 0.55 else rng.uniform(0, 2 * math.pi)
            q = body * rng.uniform(0.05, 0.3)
            spine(cx + math.cos(a) * q, cy + math.sin(a) * q, a + rng.uniform(-0.25, 0.25), body * rng.uniform(1.0, 1.9),
                  0.075, 0.07)
        roots_from, root_dir = (cx, cy), bunch + math.pi

    # Roots reaching out under the creep, mostly one way.
    for _ in range(rng.randint(3, 5)):
        a = root_dir + rng.uniform(-0.9, 0.9)
        sx, sy = roots_from[0] + math.cos(a) * body * 0.6, roots_from[1] + math.sin(a) * body * 0.6
        room = f.cells / 2 * 0.9 - max(abs(sx), abs(sy))
        length = min(room, body * rng.uniform(0.5, 0.9))
        if length > 0.05:
            f.tube(f.wander(sx, sy, a, length, steps=6, jitter=0.45), rng.uniform(0.025, 0.04),
                   rng.uniform(0.03, 0.05) * (0.5 if dead else 1.0), UNDER)
    for _ in range(5):
        a = rng.uniform(0, 2 * math.pi)
        f.crack(cx + math.cos(a) * 0.75, cy + math.sin(a) * 0.75, a, rng.uniform(0.15, 0.3), 0.008)
    if f.state != "intact":
        f.scars(5 if hurt else 8, (cx, cy), 0.7)


def stalk(f: Field, samples, rings: float = 0.0):
    """A round, tapering, leaning column through 3D ``samples`` of ``(x, y, z, r, material)``, seen from above.

    The top surface over a pixel is the highest point of any sample's sphere over it, so a column that leans
    shows its length across the ground and its lit flank, and one that stands straight shows only its crown and
    its shadow. ``rings`` lips the column with that many growth collars along its length, each a little proud
    of the segment above it, the way a horn or a shell grows.
    """
    height = np.full((N, N), -1e9, np.float32)
    material = np.zeros((N, N), np.uint8)
    n = len(samples)
    for k, (sx, sy, sz, r, mat) in enumerate(samples):
        if rings:
            t = k / max(1, n - 1)
            phase = (t * rings) % 1.0
            r = r * (1 + 0.11 * math.exp(-((phase - 0.88) / 0.07) ** 2))
        j0, i0 = (int(max(0, v)) for v in f.px(sx - r, sy - r))
        j1, i1 = (int(min(N, v + 2)) for v in f.px(sx + r, sy + r))
        if j1 <= j0 or i1 <= i0:
            continue
        xs, ys = f.x[i0:i1, j0:j1], f.y[i0:i1, j0:j1]
        d2 = (xs - sx) ** 2 + (ys - sy) ** 2
        top = np.where(d2 < r * r, sz + np.sqrt(np.clip(r * r - d2, 0, None)), -1e9)
        sub, msub = height[i0:i1, j0:j1], material[i0:i1, j0:j1]
        higher = top > sub
        sub[higher] = top[higher]
        msub[higher] = mat
    mask = height > -1e8
    take = mask & (height >= f.h)
    f.h[take] = height[take]
    f.m[take] = material[take]
    f.over |= take


def spire_spine(base, angle, lean, height, r0, r_tip, curl=0.0, steps=90, material=CHITIN, bone_from=1.1,
                droop=0.0):
    """Samples up a column from ``base`` (x, y, z): it rises ``height`` while leaning ``lean`` of it out along
    ``angle``, bending more towards the tip, turning by ``curl``; bone from ``bone_from`` of the way up. ``droop``
    bends the tip back down, a hook rather than a horn."""
    bx, by, bz = base
    out = []
    for k in range(steps + 1):
        t = k / steps
        a = angle + curl * t
        reach = lean * height * t ** 1.5
        z = bz + height * t - droop * height * t ** 3
        r = r_tip + (r0 - r_tip) * (1 - t) ** 0.85
        out.append((bx + math.cos(a) * reach, by + math.sin(a) * reach, z, r, BONE if t >= bone_from else material))
    return out


def spire_body(f: Field, style: str):
    """A brood spire: the tall growth on old creep that Harrier packs rise from (`colony-breach-nouns.md`).

    It must read at a glance as tall, where every other grown structure is low: its length shows as a lean across
    the ground, its height as a long shadow thrown down-sun, and it carries the brood (membrane pods, amber-lit
    from inside) the packs hatch from. Three body plans for the owner's pick (``SPIRE_STYLES``):

    - ``roost``: one great curved horn of stacked chitin collars leaning out of a plated footing, hooked bone
      perches up its flanks with brood pods hanging under them, an amber vent at its crown.
    - ``chimney``: a thick, flared, ribbed stack standing nearly upright, open at the top on a lit throat, a ring
      of torn membrane flaps peeled back off its rim where the packs burst out.
    - ``clutch``: three or four slender horns splayed out from one knotted root like a claw opening, bone-tipped,
      brood pods cradled in the crotch between them.
    """
    rng = f.rng
    sx, sy = to_sun_screen()
    # Off-centre towards the sun so the long shadow down-sun stays on the canvas.
    cx, cy = sx * 1.1 + rng.uniform(-0.15, 0.15), sy * 1.1 + rng.uniform(-0.15, 0.15)
    # Footing: heaved creep and a skirt of plates the column has pushed up through.
    f.blob(cx, cy, rng.uniform(1.0, 1.2), 0.1, EARTH, lumps=rng.randint(4, 6), squash=rng.uniform(0.75, 0.95),
           angle=rng.uniform(0, math.pi))
    for _ in range(rng.randint(5, 7)):
        a = rng.uniform(0, 2 * math.pi)
        side = f.cells / 2 * 0.85 - max(abs(cx), abs(cy))
        length = min(side, rng.uniform(0.8, 1.5))
        start = (cx + math.cos(a) * 0.5, cy + math.sin(a) * 0.5)
        f.tube(f.wander(start[0], start[1], a, length, steps=7, jitter=0.4), rng.uniform(0.06, 0.09),
               rng.uniform(0.05, 0.08), UNDER)
    skirt = rng.randint(6, 8)
    a0 = rng.uniform(0, 2 * math.pi)
    for k in range(skirt):
        a = a0 + 2 * math.pi * k / skirt + rng.uniform(-0.25, 0.25)
        f.plate(cx + math.cos(a) * 0.25, cy + math.sin(a) * 0.25, a + rng.uniform(-0.2, 0.2),
                rng.uniform(0.6, 0.85), rng.uniform(0.45, 0.6), rng.uniform(0.32, 0.45), 0.05, bulge=0.04,
                jagged=rng.uniform(0, 0.25), round_=0.02)
    # A lean away from the sun would hide the column in its own shadow; across or into the light shows it.
    toward = math.atan2(sy, sx)

    def pod(x, y, z, r, glow=1.0):
        f.stalk_pod(x, y, z, r, glow)

    if style == "roost":
        lean_a = toward + rng.uniform(1.2, 1.9) * rng.choice((-1, 1))
        H = rng.uniform(3.1, 3.4)
        spine = spire_spine((cx, cy, 0.0), lean_a, rng.uniform(0.4, 0.46), H, 0.62, 0.07,
                            curl=rng.uniform(-0.5, 0.5), bone_from=0.9)
        stalk(f, spine, rings=9)
        # Perches: hooked bone spurs off alternate flanks, a brood pod hanging under each.
        for k, t in enumerate((0.28, 0.42, 0.55, 0.68, 0.8)):
            x, y, z, r, _ = spine[int(t * (len(spine) - 1))]
            side = lean_a + (math.pi / 2 if k % 2 else -math.pi / 2) + rng.uniform(-0.5, 0.5)
            px_, py_ = x + math.cos(side) * r * 0.8, y + math.sin(side) * r * 0.8
            hook = spire_spine((px_, py_, z), side, 2.2, 0.28, 0.09, 0.025, curl=rng.uniform(-0.6, 0.6),
                               steps=24, material=BONE, droop=0.9)
            stalk(f, hook)
            hx, hy, hz, _, _ = hook[-1]
            pod(x + math.cos(side) * (r + 0.2), y + math.sin(side) * (r + 0.2), z - 0.25, rng.uniform(0.16, 0.22))
        tip = spine[-1]
        f.node(tip[0], tip[1], 0.12, 0.9)
    elif style == "chimney":
        lean_a = toward + rng.uniform(1.0, 2.0) * rng.choice((-1, 1))
        H = rng.uniform(2.4, 2.7)
        steps = 90
        lean = rng.uniform(0.1, 0.14)
        spine = []
        for k in range(steps + 1):
            t = k / steps
            r = 0.52 + 0.45 * (1 - t) ** 3 - 0.04 * t
            reach = lean * H * t
            spine.append((cx + math.cos(lean_a) * reach, cy + math.sin(lean_a) * reach, H * t, r, CHITIN))
        stalk(f, spine, rings=6)
        # Sinew cords run up its flanks, the ribs of the stack.
        for k in range(7):
            a = 2 * math.pi * k / 7 + rng.uniform(-0.2, 0.2)
            cord = []
            for x, y, z, r, _ in spine[::3]:
                cord.append((x + math.cos(a) * r * 0.9, y + math.sin(a) * r * 0.9, z, 0.09, UNDER))
            stalk(f, cord)
        top = spine[-1]
        # The throat: open down into the stack, flesh-lined and lit from below.
        d = np.hypot(f.x - top[0], f.y - top[1])
        throat = d < 0.36
        f.h[throat] = top[2] - 1.1 * np.clip(1 - d[throat] / 0.36, 0, 1) ** 0.6 + 0.15
        f.m[throat] = FLESH
        f.node(top[0], top[1], 0.18, 0.95)
        # The flaps: membrane peeled back off the rim, splayed and ragged.
        for k in range(rng.randint(6, 7)):
            a = 2 * math.pi * k / 6.5 + rng.uniform(-0.25, 0.25)
            f.plate(top[0] + math.cos(a) * 0.36, top[1] + math.sin(a) * 0.36, a + rng.uniform(-0.25, 0.25),
                    rng.uniform(0.45, 0.65), rng.uniform(0.35, 0.5), top[2] + 0.55, top[2] - 0.15, bulge=0.03,
                    jagged=rng.uniform(0.2, 0.45), material=SAC, round_=0.02)
    else:  # clutch
        horns = rng.randint(3, 4)
        a0 = rng.uniform(0, 2 * math.pi)
        tops = []
        for k in range(horns):
            a = a0 + 2 * math.pi * k / horns + rng.uniform(-0.35, 0.35)
            H = rng.uniform(2.0, 2.9)
            spine = spire_spine((cx + math.cos(a) * 0.15, cy + math.sin(a) * 0.15, 0.0), a, rng.uniform(0.38, 0.55),
                                H, rng.uniform(0.3, 0.36), 0.04, curl=rng.uniform(-0.6, 0.6), bone_from=0.82)
            stalk(f, spine, rings=rng.randint(6, 8))
            tops.append(spine)
        # The knot the horns grow from.
        stalk(f, [(cx, cy, z, 0.5 - 0.25 * z, UNDER) for z in np.linspace(0.0, 0.7, 12)])
        # Brood pods cradled in the crotch, and a few clinging up the horns.
        for k in range(horns):
            a = a0 + 2 * math.pi * (k + 0.5) / horns
            pod(cx + math.cos(a) * 0.3, cy + math.sin(a) * 0.3, 0.55, rng.uniform(0.2, 0.26))
        for spine in tops[:2]:
            x, y, z, r, _ = spine[int(0.45 * (len(spine) - 1))]
            pod(x, y, z + r * 0.4, 0.15, 0.8)
    for _ in range(6):
        a = rng.uniform(0, 2 * math.pi)
        f.crack(cx + math.cos(a) * 1.1, cy + math.sin(a) * 1.1, a, rng.uniform(0.3, 0.6), 0.01)


def _stalk_pod(self: Field, x, y, z, r, glow=1.0):
    """A brood pod: a swollen membrane clinging at height (x, y, z), amber-lit from inside."""
    stalk(self, [(x, y, z, r, SAC), (x + r * 0.15, y + r * 0.1, z - r * 0.3, r * 0.85, SAC)])
    self.node(x - r * 0.15, y - r * 0.15, r * 0.55, 0.75 * glow)


Field.stalk_pod = _stalk_pod


def build(part: str, variant: int, state: str = "intact", style: str | None = None) -> Field:
    f = Field(CANVAS_CELLS[part], SEEDS[part] + 17 * variant)
    f.state = state
    if part == "brood-spire":
        spire_body(f, style or SPIRE_STYLE)
    elif part == "ground-colony":
        ground_colony_body(f, style or GROUND_COLONY_STYLE)
    elif part == "bore":
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


def guide_path(templates: Path, part: str, variant: int, state: str = "intact") -> Path:
    suffix = "" if state == "intact" else f"-{state}"
    return templates / f"swarm-{part}-{variant}{suffix}-guide.png"


def main() -> None:
    templates = Path(__file__).resolve().parent.parent / "templates"
    only = set(sys.argv[1:])
    for part in PARTS:
        for v in range(1, VARIANTS[part] + 1):
            for state in STATES:
                if only and state not in only and part not in only:
                    continue
                f = build(part, v, state)
                out = guide_path(templates, part, v, state)
                guide_image(f).save(out, optimize=True)
                print(out)


if __name__ == "__main__":
    main()
