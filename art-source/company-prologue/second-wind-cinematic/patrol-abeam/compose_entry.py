"""The entry transition between C2 and C3: heat glow, then cloud, across the one window.

    python compose_entry.py [--stills] [--workers N]

3.0 s at 1920x1080 / 24 fps, on C2's camera and cabin (c2-layout.md, "Entry transition").
Back to front:

  1. Jangala as C2 leaves it (c2_planet, still drifting), behind everything
  2. heat glow   assets/c2-entry-glow-v1.png: the plasma sheath's filaments, scrolling aft
                 (right to left) at FILAMENT_PX_S inside a wipe that enters from the bow side
                 (right jamb) over GLOW_IN; translate only, the wipe is the card's soft edge
  3. cloud       assets/c2-entry-cloud-v1.png: its ragged leading edge leads, its luminous
                 body follows, the card translating right to left over CLOUD_IN; then the
                 glass settles to C3's flat entry white (244, 246, 248)
  4. the cabin   C2's painted cabin, relit from the glass: space -> glow (orange, strongest
                 on the bow side, right side first as the wipe reaches it) -> cloud (soft fill)

The camera change. C2 looks 19.29 degrees down, C3 9 degrees. The cloud's arrival floods
the cabin with light and the frame blooms close to white (BLOOM); under the peak the C2
cabin is exchanged for C3's (c3_cabin(), the same assembly as first-open-sky/c3_layers.py),
and the bloom recedes onto C3's frame 0 exactly: C3's daylight cabin around a glass of
flat entry white (first-open-sky/c3-handoff contract). The cut to C3 is then invisible.

Line 9 ("Well." / "Somebody knows we're here.") plays inside the transition; no squelch.

Writes build/cinematic/entry/frames/%05d.png, entry.mp4 (review), timeline.json and
contact-sheet.png; review stills to review/entry-*.png.
"""
import argparse
import json
import math
import os
import sys
from functools import lru_cache
from pathlib import Path

import numpy as np
from PIL import Image

BASE = Path(__file__).resolve().parent
sys.path.insert(0, str(BASE))
import c2_core as C  # noqa: E402
import c2_planet  # noqa: E402
import compose_c2 as K  # noqa: E402

SHOT = "entry"
OUT = K.ROOT / "build" / "cinematic" / SHOT
DURATION = C.ENTRY_LEN
W, H, FPS = C.W, C.H, C.FPS
C3_DIR = K.CIN / "first-open-sky"
ENTRY_WHITE = np.array([244, 246, 248], np.float32) / 255          # C3's frame-0 glass (sRGB)
FILAMENT_PX_S = (210.0, 720.0)   # the sheath body, and the fast streaks drawn out of the same plate
GLOW_GAIN = 1.25
BLOOM = (2.40, 2.72, 2.96)        # rise, peak (cabin exchanged), back to C3's frame 0


def duration():
    return DURATION


def captions():
    return K.captions_between(C.ENTRY_START, C.ENTRY_START + DURATION)


def overlays():
    return []


def audio_cues():
    """Line 9 off channel (no squelch), the cabin bed, and the wind C3 continues."""
    starts = {line["id"]: start for line, start, _ in K._hail()}
    return [
        (0.0, "jangala_arrival_cabin_bed", 0.55, DURATION),
        (0.35, "jangala_arrival_wind", 0.45),
        (round(starts["jangala_arrival_hail_09"] - C.ENTRY_START, 3), "jangala_arrival_hail_09", 1.0),
    ]


# ---------------------------------------------------------------- sources

@lru_cache(maxsize=None)
def glow_card(stretch=1.0, h=1000):
    """The filament plate at a scale that covers the glass height. stretch > 1 draws it out
    along the flow, for the fast streak layer."""
    im = Image.open(BASE / "assets" / "c2-entry-glow-v1.png").convert("RGB")
    im = im.resize((round(im.width * h / im.height * stretch), h), Image.LANCZOS)
    a = np.asarray(im, np.float32) / 255
    return a[(h - 760) // 2:(h - 760) // 2 + 760] if h > 760 else a


@lru_cache(maxsize=1)
def cloud_card():
    im = Image.open(BASE / "assets" / "c2-entry-cloud-v1.png").convert("RGB")
    h = 1100
    im = im.resize((round(im.width * h / im.height), h), Image.LANCZOS)
    return np.asarray(im, np.float32)[150:950] / 255


@lru_cache(maxsize=1)
def c3_cabin():
    """C3's cabin exactly as first-open-sky/c3_layers.py assembles it (sRGB RGBA float)."""
    G = C3_DIR / "guides"
    cab = np.asarray(Image.open(G / "c3-cabin-projected-p09.png").convert("RGBA"), np.float32) / 255
    un = np.asarray(Image.open(G / "c3-cabin-unpainted-p09.png").convert("L"), np.float32) / 255
    seam = int(np.argmin(un[:, W // 2] > 0.5))
    top = np.asarray(Image.open(C3_DIR / "assets" / "c3-cabin-top-v2.png").convert("RGB").resize((W, H), Image.LANCZOS),
                     np.float32) / 255
    yy = np.arange(H, dtype=np.float32)[:, None]
    w = np.maximum(((seam + 4 - yy) / 8).clip(0, 1) * np.ones((1, W), np.float32), un)
    rgb = cab[..., :3] * (1 - w[..., None]) + top * w[..., None]
    return rgb, cab[..., 3:4]


@lru_cache(maxsize=1)
def c3_frame0():
    rgb, a = c3_cabin()
    return rgb * a + ENTRY_WHITE * (1 - a)


def strip(card, x):
    """Columns of a card placed with its left edge at frame x (sub-pixel), clipped to the frame."""
    ch, cw = card.shape[:2]
    cols = np.arange(W, dtype=np.float32) - x
    i0 = np.floor(cols).astype(int)
    f = (cols - i0)[None, :, None]
    ok = ((i0 >= 0) & (i0 < cw - 1))[None, :, None]
    i0 = np.clip(i0, 0, cw - 2)
    return (card[:, i0] * (1 - f) + card[:, i0 + 1] * f) * ok, ok


def wipe(edge, soft, ys):
    """1 to the right of a slanted, soft edge travelling right to left (bow to stern)."""
    xs = np.arange(W, dtype=np.float32)[None, :]
    lead = edge - 0.18 * (ys - 350.0)                 # the low glass, nearest the hull's underside, leads
    return np.clip((xs - lead) / soft + 0.5, 0, 1)


# ---------------------------------------------------------------- the frame

def frame_entry(e):
    cab = K.cabin_states()
    t2 = C.C2_LEN + e
    ys = np.arange(H, dtype=np.float32)[:, None]
    lin = K.to_lin(c2_planet.plate_layer(t2))

    # --- heat glow
    g_u = C.smooth((e - C.GLOW_IN[0]) / (C.GLOW_IN[1] - C.GLOW_IN[0]))
    glow_edge = 2150 - 2900 * g_u
    def layer(card, x):
        rows, _ = strip(card, x)
        out = np.empty((H, W, 3), np.float32)
        out[:rows.shape[0]] = rows
        out[rows.shape[0]:] = rows[-1]
        return out

    body = layer(glow_card(), -60 - FILAMENT_PX_S[0] * e)
    streak = layer(glow_card(2.0, 760), -40 - FILAMENT_PX_S[1] * e)
    gl = K.to_lin(body) + 0.35 * K.to_lin(streak)
    # the wipe's edge is ragged along the filaments themselves, not a straight soft line
    fil = (body @ np.array([0.3, 0.6, 0.1], np.float32))
    xs_ = np.arange(W, dtype=np.float32)[None, :]
    lead = glow_edge - 0.18 * (ys - 350.0)
    gm = np.clip((xs_ - lead) / 700.0 + 0.5 + 0.9 * (fil - 0.45), 0, 1)[..., None] * min(1.0, g_u * 4)
    heat = (0.55 + 0.45 * np.clip(np.arange(W, dtype=np.float32) / W, 0, 1))[None, :, None]
    flicker = 1.0 + 0.06 * math.sin(e * 31.0) + 0.04 * math.sin(e * 53.0 + 1.1)
    glow_fade = 1 - C.smooth((e - 2.1) / 0.6)                  # the cloud has quenched it by the white
    sheath = gl * heat * GLOW_GAIN * flicker
    lin = lin * (1 - 0.92 * gm) + sheath * gm * glow_fade

    # --- cloud
    c_u = C.smooth((e - C.CLOUD_IN[0]) / (C.CLOUD_IN[1] - C.CLOUD_IN[0]))
    cc = cloud_card()
    cx = W + 40 - (W + 40 + 380) * c_u - 120 * (e - C.CLOUD_IN[0])            # leading edge in at the right jamb; body over the glass by 2.5
    crow, cok = strip(cc, cx)
    cl = np.zeros((H, W, 3), np.float32)
    cl[:crow.shape[0]] = crow
    cl[crow.shape[0]:] = crow[-1]
    lum = cl @ np.array([0.3, 0.6, 0.1], np.float32)
    dens = np.clip((lum - 0.25) / 0.30, 0, 1)[..., None]       # torn edge: thin vapour is see-through
    cm = np.clip(dens * 1.6, 0, 1) * wipe(cx + 420, 700.0, ys)[..., None]   # never shows the card's own edge
    cl_lin = K.to_lin(cl) * 1.15
    lin = lin * (1 - cm) + cl_lin * cm
    # white: the glass settles to C3's flat entry white
    wh = C.smooth((e - 2.35) / 0.35)
    lin = lin * (1 - wh) + K.to_lin(ENTRY_WHITE) * wh

    # --- the cabin, relit from the glass
    xs = np.arange(W, dtype=np.float32)[None, :, None]
    reach = np.clip((xs - (glow_edge - 500)) / 1100.0, 0, 1) * min(1.0, g_u * 1.6)      # right side first
    reach = reach * glow_fade
    cloud_amt = C.smooth((e - 1.45) / 1.0)
    cab_lin = cab["space"] * (1 - reach) * (1 - cloud_amt) + cab["glow"] * flicker * reach * (1 - cloud_amt) \
        + cab["cloud"] * cloud_amt
    a = cab["alpha"]
    out = K.to_srgb(lin * (1 - a) + cab_lin * a)

    # --- bloom into C3: brighten toward white, swap the cabin at the peak, settle on C3 frame 0
    b0, bp, b1 = BLOOM
    if e >= b0:
        rise = C.smooth((e - b0) / (bp - b0))
        fall = C.smooth((e - bp) / (b1 - bp))
        amount = 0.9 * rise * (1 - fall)
        if e >= bp:
            out = c3_frame0()
        out = out * (1 - amount) + ENTRY_WHITE * amount
    return Image.fromarray((np.clip(out, 0, 1) * 255 + 0.5).astype(np.uint8))


def _render(i):
    frame_entry(i / FPS).save(OUT / "frames" / ("%05d.png" % i))
    return i


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--stills", action="store_true")
    parser.add_argument("--workers", type=int, default=max(1, (os.cpu_count() or 2) // 2))
    args = parser.parse_args()
    K.check_hail()
    n = round(DURATION * FPS)
    for name, e in (("entry-1-glow-in", 0.5), ("entry-2-glow", 0.9), ("entry-3-cloud", 1.9), ("entry-4-white", 2.5),
                    ("entry-5-bloom", 2.75), ("entry-6-last", (n - 1) / FPS)):
        frame_entry(e).save(BASE / "review" / (name + ".png"))
    if args.stills:
        return
    K.write_frames(_render, n, OUT / "frames", args.workers)
    last = np.asarray(Image.open(OUT / "frames" / ("%05d.png" % (n - 1))), np.float32) / 255
    seam = float(np.abs(last - c3_frame0()).mean() * 255)
    caps = captions()
    (OUT / "timeline.json").write_text(json.dumps({
        "shot": SHOT, "duration": DURATION, "fps": FPS, "frames": n, "hail_clock_start": C.ENTRY_START,
        "captions": caps, "overlays": overlays(), "audio_cues": audio_cues(),
        "last_frame_vs_c3_frame0_mean_abs_8bit": round(seam, 3)}, indent=1), encoding="utf-8")
    K.contact_sheet(OUT / "frames", n, OUT / "contact-sheet.png", cols=6, rows=4, label="ENTRY")
    K.review_movie(OUT / "frames", n, caps, C.ENTRY_START, DURATION, OUT / (SHOT + ".mp4"))
    print("Entry: last frame vs C3 frame 0, mean |diff| %.2f / 255" % seam)


if __name__ == "__main__":
    main()
