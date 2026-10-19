"""The entry transition between C2 and C3: the ship coming down through the heat, then cloud.

    python compose_entry.py [--stills] [--workers N] [--fps N]

7.0 s at 1920x1080 / 24 fps, on C2's camera and cabin (c2-layout.md, "Entry transition"),
reworked as a descent after the owner's arrival sound review (2026-10-19): longer, the
camera and window shaking, and flames and smoke streaming across the glass from bottom
right to top left, the way the air goes past a ship coming down bow first. Back to front:

  1. Jangala as C2 leaves it (c2_planet, still drifting), hidden as the sheath thickens
  2. fire     assets/c2-entry-glow-v1.png's filaments, flattened into a texture and streamed
              along c2_core.FLOW_DEG at two speeds (a body and fast streaks), coloured by a
              heat field that is hottest at the bottom right (the side the air hits first)
              and spreads up-left from there over HEAT
  3. smoke    torn off the sheath: the cloud card's detail, darkened, streaming the same way
              and thicker toward the top left, where it trails
  4. cloud    assets/c2-entry-cloud-v1.png, its ragged edge first, carried in along the same
              line from the bottom right over CLOUD_IN; warm while the heat is still in it,
              then white; fast wisps stream through it; the glass then settles to C3's flat
              entry white (244, 246, 248)
  5. the cabin C2's painted cabin, relit from the glass: space -> fire (orange, flickering)
              -> cloud (soft fill)

The whole picture, cabin included, shakes (first-open-sky/c3_core.camera_shake): rising
through the heat, hardest in the cloud, and carried on into C3, where it eases off.

The camera change. C2 looks 19.29 degrees down, C3 9 degrees. Under the white the frame
blooms (BLOOM); at the peak the C2 cabin is exchanged for C3's frame 0 (compose_c3.frame),
and the bloom recedes onto it. C3 picks up one frame later on the same shake clock, so the
cut is a continuation rather than a held frame.

Line 9 ("Well." / "Somebody knows we're here.") plays early in the heat; no squelch.

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
from PIL import Image, ImageFilter

BASE = Path(__file__).resolve().parent
sys.path.insert(0, str(BASE))
sys.path.insert(1, str(BASE.parent / "first-open-sky"))
import c2_core as C  # noqa: E402
import c2_planet  # noqa: E402
import compose_c2 as K  # noqa: E402
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
    return K.takes_between(C.ENTRY_START, C.ENTRY_START + DURATION)


def overlays():
    return []


def audio_cues():
    """Line 9, off channel (no squelch: the missing key-up is how the ear knows Castle has
    closed the channel), and the wind rising with the heat, which C3 carries on. No bed:
    C1's cabin bed runs through the entry."""
    starts = {line["id"]: start for line, start, _ in K._hail()}
    return [
        {"t": round(starts["jangala_arrival_hail_09"] - C.ENTRY_START, 3), "sound": "jangala_arrival_hail_09",
         "volume": 1.0},
        {"t": 0.35, "sound": "jangala_arrival_wind", "volume": 0.45},
    ]


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


FIRE = [(0.00, (0.0, 0.0, 0.0)), (0.25, (0.30, 0.025, 0.0)), (0.55, (0.95, 0.20, 0.02)),
        (0.85, (1.25, 0.55, 0.10)), (1.20, (1.6, 1.15, 0.55)), (1.70, (2.0, 1.85, 1.5))]


def fire_colour(temp):
    """Linear RGB of a flame at temperature 0..~1.7: deep red, orange, yellow, near white."""
    ks = np.array([k for k, _ in FIRE], np.float32)
    out = np.empty(temp.shape + (3,), np.float32)
    for c in range(3):
        out[..., c] = np.interp(temp, ks, np.array([v[c] for _, v in FIRE], np.float32))
    return out


# ---------------------------------------------------------------- the frame

def frame_entry(e):
    cab = K.cabin_states()
    lin = K.to_lin(c2_planet.plate_layer(C.C2_LEN + e))
    flicker = 1.0 + 0.14 * Q._noise(e, 9.0, 21) + 0.07 * Q._noise(e, 23.0, 22)

    # --- fire: the heat field is fixed to the glass (the air meets the hull at the bottom
    # right), the filaments stream through it
    h = _ramp(e, C.HEAT)
    onset = C.smooth((e - C.HEAT[0]) / (C.HEAT[1] - C.HEAT[0] + 0.6))
    body = detail("c2-entry-glow-v1.png")
    fb = stream(body, SPEED["body"] * e, 1.5, 1.0)
    fs = stream(body, SPEED["streak"] * e + 900.0, 3.2, 0.7, v_off=170.0)
    fil = np.clip(0.7 * fb + 0.5 * fs - 0.55, 0, None)           # dark lanes between the streams
    front = 1.25 - 2.6 * onset                                     # the hot edge spreads up-left
    reach = np.clip((UN - front) / 0.6 + 0.4 * (fil - 0.6), 0, 1)
    field = (0.22 + 0.78 * ((UN + 1) / 2) ** 1.3) * reach * h * flicker
    temp = field * (0.03 + 1.45 * fil ** 2.2)
    sheath = np.clip(reach * h * 1.6, 0, 1)[..., None]           # the plasma hides the planet
    lin = lin * (1 - 0.985 * sheath) + fire_colour(temp)

    # --- smoke torn off the sheath, trailing to the top left
    sm = _ramp(e, C.SMOKE)
    if sm > 0:
        billow = smoke_tex()
        d = 0.65 * stream(billow, SPEED["smoke"] * e + 400.0, 1.5, 1.15, v_off=-90.0)             + 0.35 * stream(billow, SPEED["smoke"] * 1.25 * e + 1700.0, 1.2, 0.9, v_off=210.0)
        trail = np.clip(0.2 + 0.8 * (1 - UN) / 2, 0, 1)
        dens = smooth_arr((d - 0.60) / 0.14) * trail * sm
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

    # --- bloom into C3: brighten toward white, swap the cabin at the peak, settle on C3 frame 0
    b0, bp, b1 = C.BLOOM
    if e >= b0:
        rise = C.smooth((e - b0) / (bp - b0))
        fall = C.smooth((e - bp) / (b1 - bp))
        amount = 0.9 * rise * (1 - fall)
        if e >= bp:
            out = c3_frame0()
        out = out * (1 - amount) + ENTRY_WHITE * amount
    return C3.shaken(out, e)


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
        "phases": {"heat": C.HEAT, "smoke": C.SMOKE, "cloud_in": C.CLOUD_IN, "white": C.WHITE, "bloom": C.BLOOM},
        "last_frame_vs_c3_frame0_mean_abs_8bit": round(seam, 3)}, indent=1), encoding="utf-8")
    K.contact_sheet(frames, n, OUT / "contact-sheet.png", cols=6, rows=4, label="ENTRY")
    K.review_movie(frames, n, K.review_pages_between(C.ENTRY_START, C.ENTRY_START + DURATION), audio_cues(), DURATION,
                   OUT / (SHOT + ".mp4"), bed_from=C.ENTRY_START)
    print("Entry: last frame vs C3 frame 0, mean |diff| %.2f / 255" % seam)


if __name__ == "__main__":
    main()
