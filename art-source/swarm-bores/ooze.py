"""The bleed sheet: a punctured membrane pulsing ichor, looped, drawn over a swarm structure's wounded painting.

The structure's wounds bleed the way the Zerg's did: a few small looping animations pinned to the painting's own
wounds, more of them opening as the body is hurt further (``StructureArt.Bleed``). A wound is a puncture in a
living membrane, so the loop is a heartbeat, not a sputter: on each beat (a double beat, then a rest) the
puncture dilates and its torn flaps lift, a gout of ichor is pushed out over the lip as a glossy film that runs
downhill with ripples spreading across it, and between beats the hole draws closed and the film drains and thins.

Drawn procedurally rather than painted: the frames of a loop have to agree with each other pixel for pixel, and a
generator repaints a little differently every call. Each frame is a heightfield lit by the game's sun from
``make_guide`` (upper left), with a tight specular for the wet sheen and the bloom sinew ramp ``export.py`` paints
raw flesh with, so the ooze is the same stuff as the wounds it comes out of.

The run is downhill, which on a mound is away from the body's centre, so the sheet holds the loop in every one of
eight directions rather than turning one at runtime: turning it would turn the baked sun with it.

Layout: ``FRAMES`` columns; ``KINDS * DIRECTIONS`` rows, row ``kind * DIRECTIONS + direction``. Direction ``k``
runs at ``k * 45`` degrees in image space (0 to the right, 90 down). Frame edge ``FRAME_PX``.

Run from the repository root: ``python art-source/swarm-bores/ooze.py``.
Writes ``mod/graphics/structures/swarm-ooze.png``.
"""

import math
import random
import sys
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import export  # noqa: E402
import make_guide  # noqa: E402

FRAME_PX = 64
FRAMES = 24
# Frames a second the runtime plays the loop at: one heartbeat every FRAMES / FPS seconds.
FPS = 12.0
DIRECTIONS = 8
KINDS = 3
SUPER = 4
# How steep the ooze reads to the sun: its heights are in units of the frame half-edge, times this.
RELIEF = 0.9
OUT = "mod/graphics/structures/swarm-ooze.png"

# The beat: a strong push, a weaker second one, then a rest. (phase in the loop, strength)
BEATS = ((0.0, 1.0), (0.16, 0.6))
HOLE = (16, 0, 5)
SHEEN = np.array((250, 205, 220), np.float32)


def smoothstep(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0.0, 1.0)
    return t * t * (3 - 2 * t)


def since(t: float, at: float) -> float:
    """Loop time since a beat at ``at``, 0 to 1."""
    return (t - at) % 1.0


def beat(t: float) -> float:
    """How hard the membrane is pushing at loop time ``t``: a fast rise and a slower fall after each beat."""
    total = 0.0
    for at, strength in BEATS:
        dt = since(t, at)
        total += strength * min(1.0, dt / 0.035) * math.exp(-dt / 0.09)
    return total


def welled(t: float) -> float:
    """How much ichor lies out on the body: topped up by each beat, draining away between them (periodic)."""
    total = 0.0
    for at, strength in BEATS:
        for lap in (0.0, 1.0, 2.0):
            dt = since(t, at) + lap
            total += strength * (1 - math.exp(-dt / 0.05)) * math.exp(-dt / 0.55)
    return total


class Kind:
    """The seeded shape of one wound: its puncture, its torn flaps and the way its ichor spreads."""

    def __init__(self, seed):
        rng = random.Random(seed)
        self.hole = rng.uniform(0.11, 0.14)
        self.slit = rng.uniform(0, math.pi)
        self.phases = [rng.uniform(0, 2 * math.pi) for _ in range(3)]
        count = rng.randint(4, 6)
        start = rng.uniform(0, 2 * math.pi)
        self.flaps = []
        for i in range(count):
            centre = start + 2 * math.pi * (i + rng.uniform(-0.2, 0.2)) / count
            self.flaps.append((centre, rng.uniform(0.35, 0.55) * math.pi / count * 2, rng.uniform(0.1, 0.15)))
        self.bend = rng.uniform(-0.3, 0.3)
        self.reach = rng.uniform(0.8, 0.95)
        self.spread = rng.uniform(0.7, 0.85)


def frame(kind: Kind, direction: float, t: float) -> Image.Image:
    n = FRAME_PX * SUPER
    yy, xx = np.mgrid[0:n, 0:n].astype(np.float32)
    x = (xx + 0.5) / n * 2 - 1
    y = (yy + 0.5) / n * 2 - 1
    c, s = math.cos(direction), math.sin(direction)
    # The film runs downhill along +along; the puncture sits a little uphill of the frame's centre.
    along = x * c + y * s + 0.3
    across = -x * s + y * c
    push = beat(t)
    wet = welled(t)
    radius = np.hypot(along, across)
    theta = np.arctan2(across, along)
    p = kind.phases

    # The puncture: a ragged hole in the membrane that dilates on the beat and draws closed between.
    # Torn rather than drilled: drawn out along a slit, ragged at its edge.
    rim = kind.hole * (1 + 0.38 * np.cos(2 * (theta - kind.slit)) + 0.1 * np.sin(5 * theta + p[0])
                       + 0.08 * np.sin(9 * theta + p[1])) * (1 + 0.3 * push)
    inside = radius < rim

    # The torn membrane round it: flaps of skin standing off the lip, lifting and spreading out on the beat.
    lip = np.zeros_like(x)
    for centre, width, length in kind.flaps:
        off = np.angle(np.exp(1j * (theta - centre)))
        across_flap = np.clip(1 - (off / (width / 2)) ** 2, 0, 1)
        reach = rim + length * (1 + 0.25 * push) * across_flap ** 0.5
        out = (radius - rim) / np.maximum(reach - rim, 1e-4)
        lip = np.maximum(lip, (0.07 + 0.05 * push) * across_flap * np.clip(1 - out, 0, 1) ** 0.6 * (radius >= rim))
    ring = (0.035 + 0.02 * push) * np.exp(-((radius - rim) / 0.025) ** 2) * (radius >= rim)
    lip = np.maximum(lip, ring)

    # The ichor pushed out over the lip: a film spreading downhill, swollen fresh after the beat, draining after.
    front = kind.reach * (0.45 + 0.55 * min(1.0, wet / 1.2))
    q = np.clip(along / front, 0, 1.2)
    centre = kind.bend * front * q * q
    half = (kind.hole * 1.2 + kind.spread * 0.22 * q ** 0.7) * (1 - 0.65 * smoothstep(0.55, 1.0, q))
    half = half * (1 + 0.05 * np.sin(along * 9 + p[2]) + 0.03 * np.sin(along * 23))
    body = np.clip(1 - (np.abs(across - centre) / half) ** 2, 0, 1) * (along > -kind.hole * 1.4)
    body *= 1 - smoothstep(0.85, 1.05, along / front)
    body = np.maximum(body, np.clip(1 - (radius / (kind.hole * 1.9)) ** 2, 0, 1))
    thick = 0.045 + 0.04 * min(1.0, wet) * np.exp(-np.maximum(along, 0) / (front * 0.5))
    # Ripples spreading out from the hole with each beat catch the sun as they go.
    ripple = 0.0
    for at, strength in BEATS:
        dt = since(t, at)
        ring_at = 0.05 + dt * 1.1
        ripple = ripple + strength * math.exp(-dt / 0.2) * np.exp(-((radius - ring_at) / 0.05) ** 2)
    film = body ** 0.5 * thick + 0.006 * ripple * (body > 0)

    height = np.where(inside, -0.05, np.maximum(film, lip))

    # Shading: the game's sun from the upper left, and a tight highlight for the wet sheen.
    gy, gx = np.gradient(height)
    slope = n / 2 * RELIEF
    nx, ny, nz = -gx * slope, -gy * slope, np.ones_like(height)
    norm = np.sqrt(nx * nx + ny * ny + nz * nz)
    nx, ny, nz = nx / norm, ny / norm, nz / norm
    sx, sy = make_guide.to_sun_screen()
    ce, se = math.cos(make_guide.SUN_ELEVATION), math.sin(make_guide.SUN_ELEVATION)
    lx, ly, lz = sx * ce, sy * ce, se
    diffuse = np.clip(nx * lx + ny * ly + nz * lz, 0.0, 1.0)
    hx, hy, hz = lx, ly, lz + 1.0
    hn = math.sqrt(hx * hx + hy * hy + hz * hz)
    spec = np.clip((nx * hx + ny * hy + nz * hz) / hn, 0.0, 1.0) ** 40
    membrane = (lip >= film) & ~inside & (lip > 0.004)
    # Membrane is the paler raw flesh of the painting; the ichor on it darker and glossier.
    tone = np.where(membrane, 0.25 + 0.75 * diffuse, 0.04 + 0.6 * diffuse ** 1.5)
    rgb = export.gradient(np.clip(tone * np.where(membrane, 175, 115), 0, 255), export.BLOOM_SINEW).astype(np.float32)
    rgb *= (0.45 + 0.55 * smoothstep(0.0, 0.035, np.maximum(film, lip)))[..., None]
    rgb = rgb + (spec * np.where(membrane, 0.4, 1.0))[..., None] * (SHEEN - rgb)
    # Down the puncture: dark, the depth falling away from the lip.
    deep = smoothstep(0.0, 1.0, 1 - radius / np.maximum(rim, 1e-4))
    rgb = np.where(inside[..., None], np.array(HOLE, np.float32) * (1.4 - 0.6 * deep)[..., None], rgb)

    # The ichor is a little translucent: the raw flesh under a thin run shows through it.
    alpha = np.where(inside | membrane, 1.0, (0.55 + 0.35 * smoothstep(0.0, 0.07, film)) * smoothstep(0.0, 0.012, film))
    image = np.dstack([np.clip(rgb, 0, 255), np.clip(alpha * 255, 0, 255)]).astype(np.uint8)
    return Image.fromarray(image, "RGBA").resize((FRAME_PX, FRAME_PX), Image.Resampling.LANCZOS)


def sheet() -> Image.Image:
    out = Image.new("RGBA", (FRAME_PX * FRAMES, FRAME_PX * KINDS * DIRECTIONS), (0, 0, 0, 0))
    for k in range(KINDS):
        kind = Kind(5100 + 31 * k)
        for d in range(DIRECTIONS):
            for f in range(FRAMES):
                out.paste(frame(kind, math.radians(45 * d), f / FRAMES),
                          (f * FRAME_PX, (k * DIRECTIONS + d) * FRAME_PX))
    return out


def main() -> None:
    root = Path(__file__).resolve().parents[2]
    path = root / OUT
    sheet().save(path, optimize=True)
    print(path)


if __name__ == "__main__":
    main()
