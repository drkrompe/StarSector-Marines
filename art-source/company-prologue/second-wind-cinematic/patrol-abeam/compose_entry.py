"""The entry transition between C2 and C3: the ship coming down through the heat, then cloud.

    python compose_entry.py [--stills] [--workers N] [--fps N]

7.0 s at 1920x1080 / 24 fps, through C3's window: C3's camera and C3's own cabin, so the
window does not change at the cut (owner, 2026-10-19; c2-layout.md, "Entry transition"),
reworked as a descent after the owner's arrival sound review (2026-10-19): longer, the
camera and window shaking, and flames and smoke streaming across the glass from bottom
right to top left, the way the air goes past a ship coming down bow first. Back to front:

  1. Jangala as C2 leaves it (c2_planet, still drifting, lowered to C3's 9-degree view),
              hidden as the sheath thickens
  2. plasma   the sheath of glowing air the hull drives in front of it, as light added over the
              planet rather than paint over it (the planet sinks as the eye adapts to the glow, and
              shimmers through the heat): seeded fractal turbulence streamed along c2_core.FLOW_DEG
              at three depths (broad and slow, a body, thin fast streaks against the hull), snaking
              on a slow warp; a wavering shock front standing off the leading edge (bottom right,
              the side the air hits first), the heat spreading up-left from there over HEAT;
              burning ablator embers streaking past; glare in the glass. Coloured from rose air-glow
              through the effects kit's plume orange and hot core (port_fleet.PLUME_RGB, CORE_RGB,
              the C1/C4 drive plumes) to near white
  3. smoke    torn off the sheath: the cloud card's billows, thin and darkened, streaming the
              same way and thicker toward the top left, where it trails
  4. cloud    assets/c2-entry-cloud-v1.png, its ragged edge first, carried in along the same
              line from the bottom right over CLOUD_IN; warm while the heat is still in it,
              then white; fast wisps stream through it; the glass then settles to C3's flat
              entry white (244, 246, 248)
  5. the cabin C3's painted cabin, relit from the glass: space (dark, cold planet-shine) ->
              fire (orange, flickering) -> cloud (C3's own daylight paint)

The whole picture, cabin included, shakes (first-open-sky/c3_core.camera_shake): rising
through the heat, hardest in the cloud, and carried on into C3, where it eases off.

No camera change. C2 (on the bridge, or the old cabin at 19.29 degrees down) cuts to this
window from space; from then on there is one window and one camera. In the white the frame
settles onto C3's frame 0 exactly (SETTLE), and C3 picks up one frame later on the same
shake clock, so the cut is a continuation rather than a held frame.

Castle's first line to the Commander ("We've got a few days planetside...", castle_lines.py)
plays early in the heat, off the hail; no squelch.

Writes build/cinematic/entry/frames/%05d.png, entry.mp4 (review), timeline.json and
contact-sheet.png; review stills to review/entry-*.png.
"""
import argparse
import json
import math
import os
import shutil
import sys
from functools import lru_cache
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

BASE = Path(__file__).resolve().parent
sys.path.insert(0, str(BASE))
sys.path.insert(1, str(BASE.parent / "first-open-sky"))
import c2_core as C  # noqa: E402
import c2_planet  # noqa: E402
import compose_c2 as K  # noqa: E402
import castle_lines  # noqa: E402  (arrival-audio, on the path through compose_c2)
import c3_core as Q  # noqa: E402
import compose_c3 as C3  # noqa: E402

SHOT = "entry"
OUT = K.ROOT / "build" / "cinematic" / SHOT
DURATION = C.ENTRY_LEN
W, H, FPS = C.W, C.H, C.FPS
ENTRY_WHITE = np.array([244, 246, 248], np.float32) / 255          # C3's frame-0 glass (sRGB)
SPEED = {"body": 520.0, "streak": 1500.0, "smoke": 820.0, "wisp": 1150.0}   # px/s along the stream
if abs(Q.ENTRY_LEN - C.ENTRY_LEN) > 1e-9:
    raise SystemExit("c2_core.ENTRY_LEN %.2f != c3_core.ENTRY_LEN %.2f" % (C.ENTRY_LEN, Q.ENTRY_LEN))


def duration():
    return DURATION


def captions():
    return castle_lines.captions("entry")


def overlays():
    return []


def audio_cues():
    """Castle to the Commander, off the hail (no squelch: the missing key-up is how the ear knows
    the channel is closed), and the wind rising with the heat, which C3 carries on. No bed:
    C1's cabin bed runs through the entry."""
    return castle_lines.cues("entry") + [{"t": 0.35, "sound": "jangala_arrival_wind", "volume": 0.45}]


# ---------------------------------------------------------------- the stream

A = math.radians(C.FLOW_DEG)
_X = np.arange(W, dtype=np.float32)[None, :] - W / 2
_Y = np.arange(H, dtype=np.float32)[:, None] - H / 2
UF = _X * math.cos(A) + _Y * math.sin(A)              # along the stream, + toward the bottom right
VF = -_X * math.sin(A) + _Y * math.cos(A)             # across it
U_HALF = W / 2 * math.cos(A) + H / 2 * math.sin(A)    # the frame's half-extent along the stream
UN = UF / U_HALF                                      # -1 top left .. +1 bottom right


def smooth_arr(u):
    u = np.clip(u, 0.0, 1.0)
    return u * u * (3 - 2 * u)


def _ramp(e, span):
    """0 -> 1 -> 1 -> 0 over a four-point span (in, full, out-start, gone)."""
    return C.smooth((e - span[0]) / (span[1] - span[0])) * (1 - C.smooth((e - span[2]) / (span[3] - span[2])))


@lru_cache(maxsize=None)
def detail(name):
    """A card's luminance with its broad light divided out: a texture of filaments (glow) or
    billows (cloud) of mean ~1 that can be streamed without carrying the card's gradient."""
    im = Image.open(BASE / "assets" / name).convert("L")
    lum = np.asarray(im, np.float32) / 255 + 0.02
    broad = np.asarray(im.filter(ImageFilter.GaussianBlur(70)), np.float32) / 255 + 0.02
    d = lum / broad
    lo, mid, hi = np.percentile(d, [5, 50, 95])
    return 1.0 + 0.5 * np.clip((d - mid) / ((hi - lo) / 2), -1.6, 1.6)     # median 1, ~0.2..1.8


@lru_cache(maxsize=1)
def smoke_tex():
    """Big soft plumes: the cloud card's billows at their own scale (not its fine detail),
    normalised to 0..1 so a threshold cuts separate plumes out of it."""
    im = Image.open(BASE / "assets" / "c2-entry-cloud-v1.png").convert("L")
    soft = np.asarray(im.filter(ImageFilter.GaussianBlur(10)), np.float32)
    broad = np.asarray(im.filter(ImageFilter.GaussianBlur(220)), np.float32)
    d = soft - broad
    lo, hi = np.percentile(d, [3, 97])
    return np.clip((d - lo) / (hi - lo), 0, 1)


def stream(tex, along, su, sv, v_off=0.0):
    """Sample a texture laid along the stream: texture x runs bottom right -> top left as
    `along` grows (features travel up-left), mirrored so it never runs out."""
    th, tw = tex.shape[:2]
    u = (UF + along) / su
    v = VF / sv + th / 2 + v_off

    def fold(q, n):
        q = np.mod(q, 2 * (n - 1))
        return np.where(q > n - 1, 2 * (n - 1) - q, q)
    u, v = fold(u, tw), fold(v, th)
    i0, j0 = np.floor(u).astype(np.int32), np.floor(v).astype(np.int32)
    i1, j1 = np.minimum(i0 + 1, tw - 1), np.minimum(j0 + 1, th - 1)
    fu, fv = u - i0, v - j0
    top = tex[j0, i0] * (1 - fu) + tex[j0, i1] * fu
    bot = tex[j1, i0] * (1 - fu) + tex[j1, i1] * fu
    return top * (1 - fv) + bot * fv


@lru_cache(maxsize=1)
def cloud_card():
    im = Image.open(BASE / "assets" / "c2-entry-cloud-v1.png").convert("RGB")
    h = 1150
    im = im.resize((round(im.width * h / im.height), h), Image.LANCZOS)
    return np.asarray(im, np.float32) / 255


def cloud_sheet(front):
    """The cloud card carried along the stream, ragged edge first. `front` is where its
    leading edge stands along the stream (frame px from centre, + toward the bottom right).
    Beyond the card's far end the dense body continues (clamped)."""
    cc = cloud_card()
    th, tw = cc.shape[:2]
    u = np.clip(UF - front, 0, tw - 1.001)
    v = np.mod(VF + th / 2, 2 * (th - 1))
    v = np.clip(np.where(v > th - 1, 2 * (th - 1) - v, v), 0, th - 1.001)   # mirrored across the stream
    i0, j0 = u.astype(np.int32), v.astype(np.int32)
    fu, fv = (u - i0)[..., None], (v - j0)[..., None]
    top = cc[j0, i0] * (1 - fu) + cc[j0, i0 + 1] * fu
    bot = cc[j0 + 1, i0] * (1 - fu) + cc[j0 + 1, i0 + 1] * fu
    rgb = top * (1 - fv) + bot * fv
    rag = stream(smoke_tex(), front * 0.35, 1.1, 0.9, v_off=330.0) - 0.5
    lead = np.clip((UF - front) / 240.0 + 0.5 + 1.4 * rag, 0, 1)   # a torn edge, nothing ahead of it
    return rgb, lead


# The plasma's colours are the effects kit's (industrial-exterior/port_fleet.CORE_RGB, PLUME_RGB; the
# drive plumes in C1, C4 and C5): its hot core and its orange plume, with the faint rose of glowing
# air in the thin outer sheath and near white where the shock layer is densest against the hull.
KIT_CORE, KIT_PLUME = (1.0, 0.80, 0.52), (1.0, 0.42, 0.08)
FIRE = [(0.00, (0.0, 0.0, 0.0)), (0.12, (0.16, 0.025, 0.04)), (0.30, (0.55, 0.10, 0.07)),
        (0.55, tuple(0.95 * c for c in KIT_PLUME)), (0.85, tuple(1.25 * c for c in KIT_CORE)),
        (1.25, (1.75, 1.55, 1.20)), (1.80, (2.4, 2.3, 2.1))]


def fire_colour(temp):
    """Linear RGB of the sheath at temperature 0..~1.8: rose air-glow, the kit's plume orange, its
    hot core, near white."""
    ks = np.array([k for k, _ in FIRE], np.float32)
    out = np.empty(temp.shape + (3,), np.float32)
    for c in range(3):
        out[..., c] = np.interp(temp, ks, np.array([v[c] for _, v in FIRE], np.float32))
    return out


@lru_cache(maxsize=1)
def plasma_noise():
    """Seeded fractal value noise, 1024 square, 0..1: the sheath's turbulence, streamed along the flow
    at several stretches and speeds (no painted card, so nothing in it reads as a picture)."""
    rng = np.random.default_rng(31)
    n, out, amp, tot = 1024, np.zeros((1024, 1024), np.float32), 1.0, 0.0
    for cells in (6, 12, 24, 48, 96, 192):
        g = rng.random((cells, cells)).astype(np.float32)
        g = np.concatenate([g, g[:1]], 0)
        g = np.concatenate([g, g[:, :1]], 1)
        up = np.asarray(Image.fromarray(g, "F").resize((n + n // cells, n + n // cells), Image.BICUBIC), np.float32)[:n, :n]
        out += amp * up
        tot += amp
        amp *= 0.62
    out /= tot
    lo, hi = np.percentile(out, [1, 99])
    return np.clip((out - lo) / (hi - lo), 0, 1)


def sample(tex, u, v):
    """Bilinear sample of a texture at (u, v) texel coordinates, mirrored so it never runs out."""
    th, tw = tex.shape[:2]

    def fold(q, n):
        q = np.mod(q, 2 * (n - 1))
        return np.where(q > n - 1, 2 * (n - 1) - q, q)
    u, v = fold(u, tw), fold(v, th)
    i0, j0 = np.floor(u).astype(np.int32), np.floor(v).astype(np.int32)
    i1, j1 = np.minimum(i0 + 1, tw - 1), np.minimum(j0 + 1, th - 1)
    fu, fv = u - i0, v - j0
    return (tex[j0, i0] * (1 - fu) + tex[j0, i1] * fu) * (1 - fv) + (tex[j1, i0] * (1 - fu) + tex[j1, i1] * fu) * fv


def plasma_field(e):
    """The sheath's brightness (0..~1.5) over the frame at entry second e: three depths of flow, the
    nearest (against the hull) thin, fast and stretched into streaks, the farthest broad and slow; a
    slow sideways warp so the streams snake rather than rule straight lines."""
    n = plasma_noise()
    warp = (sample(n, UF / 9.0 + 140 * e, VF / 9.0 + 300) - 0.5) * 70.0          # px across the flow
    v = VF + warp
    far = sample(n, (UF + 300.0 * e) / 2.2, v / 2.6 + 200)
    mid = sample(n, (UF + 900.0 * e) / 5.0, v / 1.3 + 500)
    near = sample(n, (UF + 2100.0 * e) / 11.0, v / 0.55 + 800)
    streaks = np.clip((near - 0.55) / 0.30, 0, 1) ** 2
    body = np.clip((0.55 * mid + 0.45 * far - 0.30) / 0.45, 0, 1)
    return 0.55 * body + 0.85 * streaks * (0.4 + body), body


def shock_front(e, h):
    """The bright shock layer standing off the leading edge (bottom right, where the air meets the
    hull): a band across the flow that wavers and breathes, brightest at full heat."""
    n = plasma_noise()
    edge = 0.78 + 0.07 * (sample(n, VF / 5.0 + 60 * e, np.full_like(VF, 40.0) + 30 * e) - 0.5) * 2
    width = 0.10 + 0.05 * h
    return np.exp(-((UN - edge) / width) ** 2) * h + np.clip((UN - edge) / 0.25, 0, 1) * h


SPARKS = None
EMBER = np.array([2.2, 1.15, 0.30], np.float32)          # burning ablator: the kit's plume, hotter


def sparks(e, heat):
    """Ablation embers off the heat shield: small bright points streaking up-left along the flow,
    each a motion-blurred line (a 1/48 s shutter at their speed). More of them at full heat."""
    global SPARKS
    if SPARKS is None:
        rng = np.random.default_rng(57)
        k = 260
        SPARKS = {"born": rng.uniform(0.6, 5.2, k), "v": rng.normal(0, 380, k), "speed": rng.uniform(1900, 3600, k),
                  "life": rng.uniform(0.25, 0.7, k), "bright": rng.uniform(0.4, 1.0, k) ** 2, "u0": rng.uniform(-200, 500, k)}
    sp = SPARKS
    im = Image.new("L", (W, H), 0)
    d = ImageDraw.Draw(im)
    ca, sa = math.cos(A), math.sin(A)
    for i in range(len(sp["born"])):
        age = e - sp["born"][i]
        if age < 0 or age > sp["life"][i] or sp["bright"][i] > heat * 1.1:
            continue
        u = U_HALF - sp["u0"][i] - sp["speed"][i] * age          # from the bottom-right edge, up-left
        tail = sp["speed"][i] / 48.0
        fade = 1 - age / sp["life"][i]
        val = int(255 * min(1.0, sp["bright"][i] * 1.6) * fade)
        pts = []
        for uu in (u, u + tail):
            pts.append((W / 2 + uu * ca - sp["v"][i] * sa, H / 2 + uu * sa + sp["v"][i] * ca))
        d.line(pts, fill=val, width=3)
        d.ellipse([pts[0][0] - 2, pts[0][1] - 2, pts[0][0] + 2, pts[0][1] + 2], fill=val)   # the hot head
    a = np.asarray(im.filter(ImageFilter.GaussianBlur(0.8)), np.float32) / 255
    return a


def bloom(lin):
    """Glare in the glass: the brightest of the sheath spread wide and soft (at quarter size)."""
    hot = np.clip(lin - 0.7, 0, None)
    small = hot[::4, ::4]
    for axis in (0, 1):                                            # three box passes ~ a Gaussian of 14
        for _ in range(3):
            small = _box(small, 12, axis)
    out = [np.asarray(Image.fromarray(np.ascontiguousarray(small[..., c], dtype=np.float32), "F").resize((W, H), Image.BILINEAR), np.float32)
           for c in range(3)]
    return np.dstack(out)


def _box(a, k, axis):
    """Running mean of width 2k+1 along an axis, edges clamped."""
    pad = [(0, 0)] * a.ndim
    pad[axis] = (k + 1, k)
    c = np.cumsum(np.pad(a, pad, mode="edge"), axis=axis)
    n = a.shape[axis]
    hi = np.take(c, np.arange(2 * k + 1, 2 * k + 1 + n), axis=axis)
    lo = np.take(c, np.arange(0, n), axis=axis)
    return (hi - lo) / (2 * k + 1)


# ---------------------------------------------------------------- the frame

def frame_entry(e):
    cab = cabin_states()
    lin = planet(e)
    flicker = 1.0 + 0.14 * Q._noise(e, 9.0, 21) + 0.07 * Q._noise(e, 23.0, 22)

    # --- the plasma sheath. It is light, not paint: it adds to the planet behind it, and the planet
    # sinks as the eye (and the glass) adapt to the glow. The heat field is fixed to the glass (the
    # air meets the hull at the bottom right) and spreads up-left from there over HEAT; the flow
    # streams through it.
    h = _ramp(e, C.HEAT)
    onset = C.smooth((e - C.HEAT[0]) / (C.HEAT[1] - C.HEAT[0] + 0.6))
    field, body = plasma_field(e)
    front = 1.25 - 2.6 * onset                                     # the hot edge spreads up-left
    reach = np.clip((UN - front) / 0.7 + 0.35 * (body - 0.5), 0, 1)
    lead = (0.18 + 0.82 * ((UN + 1) / 2) ** 1.4)                   # hotter toward the leading edge
    glow = (0.10 + field) * reach * lead * h * flicker + 0.75 * shock_front(e, h * onset) * reach * flicker
    temp = 1.05 * glow
    adapt = np.clip(glow * 1.6, 0, 0.93)[..., None]                # exposure: the planet sinks under the glow
    if h > 0.02:                                                   # heat shimmer on what shows through
        n = plasma_noise()
        dx = (sample(n, UF / 4.0 + 500 * e, VF / 4.0) - 0.5) * 7 * h
        xi = np.clip((np.arange(W)[None, :] + dx).astype(np.int32), 0, W - 1)
        lin = np.take_along_axis(lin, np.repeat(xi[..., None], 3, 2), axis=1)
    lin = lin * (1 - adapt) + fire_colour(temp)
    if h > 0.02:
        ember = sparks(e, h) * np.clip(reach * 1.5, 0, 1)            # embers only inside the sheath
        lin = lin + ember[..., None] * EMBER
    lin = lin + 0.35 * bloom(lin) * h

    # --- smoke torn off the sheath, trailing to the top left
    sm = _ramp(e, C.SMOKE)
    if sm > 0:
        billow = smoke_tex()
        d = 0.65 * stream(billow, SPEED["smoke"] * e + 400.0, 1.5, 1.15, v_off=-90.0)             + 0.35 * stream(billow, SPEED["smoke"] * 1.25 * e + 1700.0, 1.2, 0.9, v_off=210.0)
        trail = np.clip(0.2 + 0.8 * (1 - UN) / 2, 0, 1)
        dens = smooth_arr((d - 0.62) / 0.16) * trail * sm * 0.5           # thin: the sheath shows through
        # grey-brown soot, its underside lit by the fire it came off
        under = smooth_arr((0.70 - d) / 0.12)[..., None]
        smoke = np.array([0.030, 0.027, 0.024], np.float32) + fire_colour(temp * 0.8) * (0.08 + 0.35 * under)
        lin = lin * (1 - dens[..., None]) + smoke * dens[..., None]

    # --- cloud, carried in from the bottom right along the stream; warm, then white
    c_u = C.smooth((e - C.CLOUD_IN[0]) / (C.CLOUD_IN[1] - C.CLOUD_IN[0]))
    cloud_amt = 0.0
    if e >= C.CLOUD_IN[0] - 0.05:
        front_px = U_HALF + 60 - (2 * U_HALF + 1250) * c_u - 160.0 * max(0.0, e - C.CLOUD_IN[1])
        rgb, lead = cloud_sheet(front_px)
        lum = rgb @ np.array([0.3, 0.6, 0.1], np.float32)
        dens = np.clip((lum - 0.30) / 0.28, 0, 1) * lead          # its torn edge: thin vapour shows through
        wisp = stream(detail("c2-entry-cloud-v1.png"), SPEED["wisp"] * e, 2.6, 0.8, v_off=60.0)
        warm = 1 - C.smooth((e - C.CLOUD_IN[0] - 0.4) / 1.2)
        tint = np.array([1.0, 0.62, 0.38], np.float32) * warm + (1 - warm)
        cl_lin = K.to_lin(rgb) * 1.12 * tint * (0.9 + 0.12 * wisp[..., None])
        cm = np.clip(dens * 1.5, 0, 1)[..., None]
        lin = lin * (1 - cm) + cl_lin * cm
        cloud_amt = c_u
    # white: the glass settles to C3's flat entry white
    wh = C.smooth((e - C.WHITE[0]) / (C.WHITE[1] - C.WHITE[0]))
    lin = lin * (1 - wh) + K.to_lin(ENTRY_WHITE) * wh

    # --- the cabin, relit from the glass
    k_fire = float(np.clip(h * flicker * min(1.0, onset * 1.6), 0, 1.2)) * (1 - cloud_amt)
    cab_lin = cab["space"] * max(0.0, 1 - k_fire) * (1 - cloud_amt) + cab["glow"] * k_fire + cab["cloud"] * cloud_amt
    a = cab["alpha"]
    out = K.to_srgb(lin * (1 - a) + cab_lin * a)

    # --- settle exactly onto C3's frame 0 (same window, same camera: nothing to hide)
    k = C.smooth((e - C.SETTLE[0]) / (C.SETTLE[1] - C.SETTLE[0]))
    if k > 0:
        out = out * (1 - k) + c3_frame0() * k
    return C3.shaken(out, e)


# ---------------------------------------------------------------- C3's window, relit

PLANET_DY = 250   # C2's plate looks 19.29 deg down; at C3's 9 deg its horizon sits ~250 px lower


def planet(e):
    """Jangala as C2 leaves it, still drifting, moved down to where it sits from C3's camera."""
    p = c2_planet.plate_layer(C.C2_LEN + e)
    out = np.zeros_like(p)
    out[PLANET_DY:] = p[:H - PLANET_DY]
    return K.to_lin(out)


@lru_cache(maxsize=1)
def cabin_states():
    """C3's own cabin (first-open-sky/build/layers/cabin.png, exactly what C3 draws), relit
    for the entry, as linear RGB plus coverage alpha. The painting is lit by C3's daylight,
    so it is used as it is for the cloud state, which is therefore C3's cabin and lands on
    C3's frame 0 with no change. Space and fire light come from the glass: a falloff from
    the aperture (near surfaces lit, deep cabin dark), the fire hotter on the bow side."""
    im = Image.open(C3.LAYER_DIR / "cabin.png").convert("RGBA")
    a = np.asarray(im, np.float32) / 255
    painted, alpha = K.to_lin(a[..., :3]), a[..., 3:4]
    glass = Image.fromarray(((1 - a[..., 3]) * 255).astype(np.uint8))
    near = np.asarray(glass.filter(ImageFilter.GaussianBlur(40)), np.float32) / 255
    wide = np.asarray(glass.filter(ImageFilter.GaussianBlur(180)), np.float32) / 255
    window = np.clip(1.6 * near + 1.1 * wide, 0, 1.4)[..., None]
    alb = painted / 0.55                                          # the paint is lit at about this level
    xs = np.arange(W, dtype=np.float32)[None, :, None]
    ys = np.arange(H, dtype=np.float32)[:, None, None]
    bias = (0.55 + 0.75 * (xs / W)) * (0.85 + 0.3 * np.clip(ys / H, 0, 1))
    space = alb * (0.012 + window * np.array([0.06, 0.08, 0.15], np.float32))   # planet-shine, cold
    glow = alb * (0.01 + window * bias * np.array([1.0, 0.42, 0.11], np.float32) * 0.75)
    return {"space": space, "glow": glow, "cloud": painted, "alpha": alpha}


@lru_cache(maxsize=1)
def c3_frame0():
    """C3's frame 0 before the shake (the shake is the entry's own, applied after)."""
    return C3.frame(0.0, unshaken=True)


def _render(i):
    frame_entry(i / FPS).save(OUT / "frames" / ("%05d.png" % i))
    return i


STILLS = (("entry-1-first-heat", 1.0), ("entry-2-fire", 2.6), ("entry-3-smoke", 3.6), ("entry-4-cloud", 4.6),
          ("entry-5-white", 5.8), ("entry-6-bloom", 6.5))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--stills", action="store_true")
    parser.add_argument("--fps", type=int, default=FPS, help="sketch rate: render every FPS/fps-th frame and hold it")
    parser.add_argument("--workers", type=int, default=max(1, (os.cpu_count() or 2) // 2))
    args = parser.parse_args()
    K.check_hail()
    n = round(DURATION * FPS)
    (BASE / "review").mkdir(exist_ok=True)
    for old in (BASE / "review").glob("entry-*.png"):
        old.unlink()
    for name, e in STILLS + (("entry-7-last", (n - 1) / FPS),):
        frame_entry(e).save(BASE / "review" / (name + ".png"))
    if args.stills:
        return
    step = max(1, round(FPS / args.fps))
    frames = OUT / "frames"
    frames.mkdir(parents=True, exist_ok=True)
    for old in frames.glob("*.png"):
        old.unlink()
    from concurrent.futures import ProcessPoolExecutor
    with ProcessPoolExecutor(args.workers) as pool:
        for done in pool.map(_render, list(range(0, n, step)) + ([n - 1] if (n - 1) % step else []), chunksize=2):
            if done % 48 == 0:
                print("frame", done, flush=True)
    if step > 1:                                                  # a sketch: hold each drawn frame
        for i in range(n):
            if i % step and i != n - 1:
                shutil.copyfile(frames / ("%05d.png" % (i - i % step)), frames / ("%05d.png" % i))
    # The cut: the entry's last frame against C3's first, one frame on along the same shake.
    last = np.asarray(Image.open(frames / ("%05d.png" % (n - 1))), np.float32)
    first = np.asarray(C3.frame(0.0), np.float32)
    seam = float(np.abs(last - first).mean())
    caps = captions()
    (OUT / "timeline.json").write_text(json.dumps({
        "shot": SHOT, "duration": DURATION, "fps": FPS, "frames": n, "sketch_step": step,
        "hail_clock_start": C.ENTRY_START, "captions": caps, "overlays": overlays(), "audio_cues": audio_cues(),
        "phases": {"heat": C.HEAT, "smoke": C.SMOKE, "cloud_in": C.CLOUD_IN, "white": C.WHITE, "settle": C.SETTLE},
        "last_frame_vs_c3_frame0_mean_abs_8bit": round(seam, 3)}, indent=1), encoding="utf-8")
    K.contact_sheet(frames, n, OUT / "contact-sheet.png", cols=6, rows=4, label="ENTRY")
    K.review_movie(frames, n, castle_lines.review_pages("entry"), audio_cues(), DURATION,
                   OUT / (SHOT + ".mp4"), bed_from=C.ENTRY_START)
    print("Entry: last frame vs C3 frame 0, mean |diff| %.2f / 255" % seam)


if __name__ == "__main__":
    main()
