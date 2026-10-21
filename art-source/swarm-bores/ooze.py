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

A long wound is a fissure instead, and sprays (``spray_frame``, its own sheet with the same layout).

Run from the repository root: ``python art-source/swarm-bores/ooze.py``.
Writes ``mod/graphics/structures/swarm-ooze.png`` and ``swarm-spray.png``.
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


# --- the fissure: a gash spraying ichor ----------------------------------------------------------------------------
#
# A long wound (a gash, a torn plate's socket) is a fissure rather than a puncture. On the same heartbeat its lips
# part and it sprays: a fan of droplets thrown out downhill, arcing through the air with their shadows on the ground
# beneath them (the sun's, so the shadow's offset is the droplet's height), landing as splats that soak away before
# the next beat, while a film runs from the gash. It needs more room than a puncture, so it has its own sheet with a
# larger frame; the layout is the same.

SPRAY_FRAME_PX = 80
SPRAY_KINDS = 2
SPRAY_OUT = "mod/graphics/structures/swarm-spray.png"
# Where the gash sits in its frame: uphill of the centre, so the spray has the downhill side to land on.
SPRAY_BACK = 0.5
SHADOW = np.array((12, 0, 4), np.float32)


def shade(height: np.ndarray, n: int, exponent: float = 40.0):
    """Diffuse and specular of a heightfield under the game's sun."""
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
    spec = np.clip((nx * hx + ny * hy + nz * hz) / hn, 0.0, 1.0) ** exponent
    return diffuse, spec


def ichor(height: np.ndarray, n: int) -> np.ndarray:
    """A droplet in flight: the painting's raw-flesh ramp, lit brighter than the film and glossy, so it reads."""
    diffuse, spec = shade(height, n, exponent=36.0)
    tone = 0.08 + 0.7 * diffuse
    rgb = export.gradient(np.clip(tone * 135, 0, 255), export.BLOOM_SINEW).astype(np.float32)
    return rgb + spec[..., None] * (SHEEN - rgb)


class SprayKind:
    """The seeded shape of one fissure: its gash and every droplet each beat throws."""

    def __init__(self, seed):
        rng = random.Random(seed)
        self.half = rng.uniform(0.17, 0.22)
        self.bend = rng.uniform(-0.6, 0.6)
        self.phases = [rng.uniform(0, 2 * math.pi) for _ in range(3)]
        self.reach = rng.uniform(0.55, 0.7)
        # Rivulets running from the gash: (where along it, width, how far, sideways drift, wobble phase).
        self.rivulets = [(rng.uniform(-0.6, 0.6) * self.half, rng.uniform(0.03, 0.045), rng.uniform(0.5, 1.0),
                          rng.uniform(-0.8, 0.8), rng.uniform(0, 2 * math.pi)) for _ in range(rng.randint(1, 2))]
        self.drops = []
        for at, strength in BEATS:
            for _ in range(round(30 * strength)):
                start = rng.uniform(-0.85, 0.85) * self.half
                fan = rng.gauss(0.0, 0.42) + start / self.half * 0.35
                distance = rng.uniform(0.25, 0.95) * strength ** 0.5
                # Lands inside the frame, droplet and all.
                while math.hypot(distance * math.cos(fan) - SPRAY_BACK, start + distance * math.sin(fan)) > 0.84:
                    distance *= 0.92
                self.drops.append(dict(
                    at=at, start=start, fan=fan, distance=distance,
                    flight=rng.uniform(0.12, 0.22) * (0.6 + 0.4 * distance),
                    peak=rng.uniform(0.08, 0.2) * distance,
                    radius=rng.uniform(0.03, 0.05),
                    # Not all at once: a burst over a few frames, so they do not pile up at the gash.
                    delay=rng.uniform(0.0, 0.09)))


def spray_frame(kind: SprayKind, direction: float, t: float) -> Image.Image:
    n = SPRAY_FRAME_PX * SUPER
    yy, xx = np.mgrid[0:n, 0:n].astype(np.float32)
    x = (xx + 0.5) / n * 2 - 1
    y = (yy + 0.5) / n * 2 - 1
    c, s = math.cos(direction), math.sin(direction)
    along = x * c + y * s + SPRAY_BACK
    across = -x * s + y * c
    push = beat(t)
    wet = welled(t)
    p = kind.phases

    def ground(a: float, b: float):
        """Image-space point of run-local (along, across)."""
        a -= SPRAY_BACK
        return a * c - b * s, a * s + b * c

    # The gash: a ragged slit across the run, its lips parting on the beat.
    # The gash bows; beyond its ends the bow holds, so the run lanes stay straight there.
    bow = kind.bend * np.clip(across, -1.5 * kind.half, 1.5 * kind.half) ** 2
    span = np.clip(1 - (across / kind.half) ** 2, 0, 1)
    gap = ((0.016 + 0.03 * push) * span ** 0.6
           * (1 + 0.45 * np.sin(across * 29 + p[0]) * np.sin(across * 11 + p[2]) + 0.2 * np.sin(across * 67)))
    off = np.abs(along - bow)
    inside = (off < gap) & (span > 0)
    lips = (0.035 + 0.03 * push) * np.exp(-((off - gap) / 0.022) ** 2) * (off >= gap) * np.clip(span * 1.3, 0, 1) ** 0.5

    # Ichor brimming over the lower lip, and rivulets running from it downhill, fed by each beat, draining between.
    front = kind.reach * (0.4 + 0.6 * min(1.0, wet / 1.2))
    below = along - bow
    brim = np.clip(1 - ((below - 0.02) / 0.05) ** 2, 0, 1) * np.clip(span * 1.2, 0, 1) * (below > -0.02)
    film = 0.04 * np.sqrt(brim)
    for start, width, length, drift, phase in kind.rivulets:
        reach = front * length
        q = np.clip(below / reach, 0, 1.2)
        centre = start + drift * below + 0.04 * np.sin(below * 12 + phase)
        w = width * (1 - 0.5 * q) * (1 + 0.6 * min(1.0, wet) * np.exp(-np.maximum(below, 0) / 0.15))
        lane = np.clip(1 - ((across - centre) / w) ** 2, 0, 1) * (below > 0) * (1 - smoothstep(0.8, 1.0, q))
        # A bead swelling at the rivulet's head as it runs.
        head = np.clip(1 - ((below - reach * 0.92) ** 2 + (across - start - drift * reach) ** 2) / (w * 1.5) ** 2, 0, 1)
        film = np.maximum(film, (0.035 + 0.02 * min(1.0, wet)) * np.sqrt(np.maximum(lane, head)))

    # Droplets: in flight, an elongated bead and its shadow on the ground; landed, a splat soaking away.
    sx, sy = make_guide.to_sun_screen()
    cast = 1.0 / math.tan(make_guide.SUN_ELEVATION)
    splats = np.zeros_like(x)
    shadow = np.zeros_like(x)
    flying = np.zeros_like(x)
    for d in kind.drops:
        dt = since(t, d["at"]) - d["delay"]
        if dt < 0:
            continue
        fade = 1 - smoothstep(0.7, 0.97, dt)
        f = dt / d["flight"]
        ux, uy = math.cos(d["fan"]), math.sin(d["fan"])
        ga, gb = min(f, 1.0) * d["distance"] * ux, d["start"] + min(f, 1.0) * d["distance"] * uy
        px, py = ground(ga, gb)
        r = d["radius"]
        if f < 1.0:
            z = 4 * d["peak"] * f * (1 - f)
            # Drawn along its flight, stretched by its speed.
            vx, vy = ground(ux, uy)
            vx, vy = vx - ground(0, 0)[0], vy - ground(0, 0)[1]
            dx, dy = x - px, y - py
            lon = dx * vx + dy * vy
            lat = -dx * vy + dy * vx
            stretch = 1.0 + 1.2 * (1 - f)
            bead = np.clip(1 - (lon / (r * stretch)) ** 2 - (lat / r) ** 2, 0, 1)
            flying = np.maximum(flying, 0.06 * np.sqrt(bead))
            # The shadow falls away from the sun, as far as the droplet is high.
            hx, hy = px - sx * z * cast, py - sy * z * cast
            dist = np.hypot(x - hx, y - hy) / (r * 1.3)
            shadow = np.maximum(shadow, 0.45 * np.clip(1 - dist * dist, 0, 1))
        else:
            spread = min(1.0, (dt - d["flight"]) / 0.05)
            soak = math.exp(-(dt - d["flight"]) / 0.3) * fade
            dist = np.hypot(x - px, y - py) / (r * (1.0 + 0.7 * spread))
            splats = np.maximum(splats, 0.03 * soak * np.sqrt(np.clip(1 - dist * dist, 0, 1)))

    film = np.maximum(film, splats)
    height = np.where(inside, -0.05, np.maximum(film, lips))
    membrane = (lips >= film) & ~inside & (lips > 0.004)
    diffuse, spec = shade(height, n)
    tone = np.where(membrane, 0.25 + 0.75 * diffuse, 0.04 + 0.6 * diffuse ** 1.5)
    rgb = export.gradient(np.clip(tone * np.where(membrane, 140, 115), 0, 255), export.BLOOM_SINEW).astype(np.float32)
    rgb *= (0.45 + 0.55 * smoothstep(0.0, 0.035, np.maximum(film, lips)))[..., None]
    rgb = rgb + (spec * np.where(membrane, 0.4, 1.0))[..., None] * (SHEEN - rgb)
    rgb = np.where(inside[..., None], np.array(HOLE, np.float32), rgb)
    alpha = np.where(inside | membrane, 1.0,
                     (0.55 + 0.35 * smoothstep(0.0, 0.07, film)) * smoothstep(0.0, 0.012, film))

    # Shadows lie on the ground under everything; the droplets in flight over everything.
    out_a = alpha + shadow * (1 - alpha)
    out_rgb = np.where(out_a[..., None] > 0, (SHADOW * shadow[..., None] * (1 - alpha[..., None])
                                               + rgb * alpha[..., None]) / np.maximum(out_a, 1e-6)[..., None], 0)
    drop_rgb = ichor(flying, n)
    drop_a = smoothstep(0.0, 0.01, flying)
    final_a = drop_a + out_a * (1 - drop_a)
    final_rgb = (drop_rgb * drop_a[..., None] + out_rgb * (out_a * (1 - drop_a))[..., None]) / np.maximum(final_a, 1e-6)[..., None]
    image = np.dstack([np.clip(final_rgb, 0, 255), np.clip(final_a * 255, 0, 255)]).astype(np.uint8)
    return Image.fromarray(image, "RGBA").resize((SPRAY_FRAME_PX, SPRAY_FRAME_PX), Image.Resampling.LANCZOS)


def spray_sheet() -> Image.Image:
    out = Image.new("RGBA", (SPRAY_FRAME_PX * FRAMES, SPRAY_FRAME_PX * SPRAY_KINDS * DIRECTIONS), (0, 0, 0, 0))
    for k in range(SPRAY_KINDS):
        kind = SprayKind(7300 + 37 * k)
        for d in range(DIRECTIONS):
            for f in range(FRAMES):
                out.paste(spray_frame(kind, math.radians(45 * d), f / FRAMES),
                          (f * SPRAY_FRAME_PX, (k * DIRECTIONS + d) * SPRAY_FRAME_PX))
    return out


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
    for out, build in ((OUT, sheet), (SPRAY_OUT, spray_sheet)):
        path = root / out
        build().save(path, optimize=True)
        print(path)


if __name__ == "__main__":
    main()
