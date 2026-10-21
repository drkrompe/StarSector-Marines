"""The bleed sheet: a looping ooze welling out of a wound in a swarm structure, drawn over its wounded painting.

The structure's wounds bleed the way the Zerg's did: a few small looping animations pinned to the painting's own
wounds, more of them opening as the body is hurt further (``StructureArt.Bleed``). Each loop is a pool of ichor that
swells and settles over the wound, a bubble rising in it and popping, and a rivulet running away from the body with
beads of ooze travelling down it over a dark stain.

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
FRAMES = 12
DIRECTIONS = 8
KINDS = 3
SUPER = 4
# How steep the ooze reads to the sun: its heights are in units of the frame half-edge, times this.
RELIEF = 0.9
OUT = "mod/graphics/structures/swarm-ooze.png"

STAIN = (52, 3, 18)
SHEEN = np.array((250, 205, 220), np.float32)


def smoothstep(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0.0, 1.0)
    return t * t * (3 - 2 * t)


def dome(d):
    """A rounded cap over a unit radius: 1 at the centre, 0 at the edge and beyond."""
    return np.sqrt(np.clip(1.0 - d * d, 0.0, 1.0))


class Kind:
    """The seeded shape of one ooze: its pool, its run, its beads and its bubble."""

    def __init__(self, seed):
        rng = random.Random(seed)
        self.pool = rng.uniform(0.23, 0.3)
        self.lobes = [rng.uniform(0, 2 * math.pi) for _ in range(3)]
        self.bend = rng.uniform(-0.35, 0.35)
        self.length = rng.uniform(0.7, 0.86)
        self.width = rng.uniform(0.085, 0.105)
        self.wobble = rng.uniform(0, 2 * math.pi)
        self.beads = rng.choice((1, 2))
        self.bubble = (rng.uniform(-0.12, 0.04), rng.uniform(-0.1, 0.1))
        self.pop = rng.uniform(0.6, 0.75)
        self.shift = rng.uniform(0, 1)
        # Spatter thrown out of the wound: still droplets about the pool.
        self.spatter = []
        for _ in range(rng.randint(3, 5)):
            a = rng.uniform(math.pi * 0.35, math.pi * 1.65)
            r = self.pool * rng.uniform(1.15, 1.7)
            self.spatter.append((math.cos(a) * r, math.sin(a) * r, rng.uniform(0.03, 0.05)))


def frame(kind: Kind, direction: float, t: float) -> Image.Image:
    n = FRAME_PX * SUPER
    yy, xx = np.mgrid[0:n, 0:n].astype(np.float32)
    x = (xx + 0.5) / n * 2 - 1
    y = (yy + 0.5) / n * 2 - 1
    c, s = math.cos(direction), math.sin(direction)
    along = x * c + y * s
    across = -x * s + y * c
    wave = math.sin(2 * math.pi * t)

    # The pool over the wound, swelling and settling once a loop: a liquid, flat-topped with a steep meniscus.
    theta = np.arctan2(across, along)
    edge = kind.pool * (1 + 0.1 * np.sin(3 * theta + kind.lobes[0]) + 0.06 * np.sin(5 * theta + kind.lobes[1])
                        + 0.04 * np.sin(9 * theta + kind.lobes[2]) + 0.1 * wave)
    pool_d = np.hypot(along, across) / edge
    height = (0.26 + 0.08 * wave) * dome(pool_d) ** 0.4

    # The run: a channel bending away downhill, ragged at its banks and thinning to its end.
    q = np.clip(along / kind.length, 0.0, 1.0)
    centre = kind.bend * kind.length * q * q
    half = (kind.width * (1 - 0.5 * q) * (1 + 0.22 * np.sin(along * 21 + kind.wobble) + 0.12 * np.sin(along * 47))
            * (1 + 0.1 * math.sin(2 * math.pi * (t + 0.25))))
    run_d = np.abs(across - centre) / half
    inside = (along > 0) & (along < kind.length)
    run = 0.12 * dome(run_d) ** 0.5 * (1 - smoothstep(0.8, 1.0, q)) * inside
    height = np.maximum(height, run)

    # Beads of ooze travelling down the run: swellings of the stream itself, born out of the pool.
    for i in range(kind.beads):
        p = (t + kind.shift + i / kind.beads) % 1.0
        at = kind.pool * 0.7 + p * (kind.length * 1.02 - kind.pool * 0.7)
        r = kind.width * (1.3 - 0.5 * p)
        k = at / kind.length
        bx, by = at, kind.bend * kind.length * k * k
        d = np.hypot((along - bx) * 0.45, across - by) / r
        swell = smoothstep(0.0, 0.2, p) * (1 - smoothstep(0.8, 1.0, p))
        height = np.maximum(height, 0.2 * swell * dome(d) ** 0.5)

    for sx_, sy_, r in kind.spatter:
        px, py = sx_ * c - sy_ * s, sx_ * s + sy_ * c
        d = np.hypot(x - px, y - py) / r
        height = np.maximum(height, 0.08 * dome(d) ** 0.5)

    # A bubble rising in the pool, popping, and its ring spreading out.
    bx, by = kind.bubble
    d = np.hypot(along - bx, across - by)
    if t < kind.pop:
        grow = t / kind.pop
        r = 0.02 + 0.08 * grow ** 0.8
        height = height + 0.16 * dome(d / r) * (pool_d < 1)
    else:
        spread = (t - kind.pop) / (1 - kind.pop)
        ring = 0.04 + 0.2 * spread
        height = height + 0.06 * (1 - spread) * np.exp(-((d - ring) / 0.022) ** 2) * (pool_d < 1)

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
    spec = np.clip((nx * hx + ny * hy + nz * hz) / hn, 0.0, 1.0) ** 32
    # Dark, deep ichor: little light comes back out of it but the sheen.
    tone = 0.06 + 0.75 * diffuse ** 1.3
    rgb = export.gradient(np.clip(tone * 150, 0, 255), export.BLOOM_SINEW).astype(np.float32)
    # A dark wet edge where the film thins out, so the ooze stands off the raw flesh it lies on.
    rgb *= (0.45 + 0.55 * smoothstep(0.0, 0.07, height))[..., None]
    rgb = rgb + spec[..., None] * (SHEEN - rgb)

    alpha = np.clip(height / 0.06, 0.0, 1.0)
    # The stain the run has already left: the ground darkened where the ooze has been.
    stain_d = np.abs(across - centre) / (kind.width * 1.5 * (1 - 0.4 * q))
    stain = 0.42 * (1 - smoothstep(0.6, 1.0, stain_d)) * (along > -0.05) * (1 - smoothstep(0.85, 1.12, along / kind.length))
    stain = np.maximum(stain, 0.42 * (1 - smoothstep(0.85, 1.25, pool_d)))
    out = rgb * alpha[..., None] + np.array(STAIN, np.float32) * (1 - alpha[..., None])
    a = np.maximum(alpha, stain)
    image = np.dstack([np.clip(out, 0, 255), np.clip(a * 255, 0, 255)]).astype(np.uint8)
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
