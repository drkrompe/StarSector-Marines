"""C3 FIRST OPEN SKY: final frames (storyboard-v3.md, c3-layout.md, runtime-contract.md).

python compose_c3.py            # review stills and contact sheet only (review/, 1920x1080)
python compose_c3.py --movie    # also every frame and the review mp4

Writes build/cinematic/c3-first-open-sky/frames/%05d.png (1920x1080, 24 fps, sRGB, no
text) and c3-first-open-sky.mp4 beside them, with the audio cues mixed in for review.
The player takes captions(), overlays() and audio_cues() from here (runtime-contract.md).

Build order: c3_bands.py, c3_plates.py prep/check/assemble, c3_layers.py, then this.
Every layer only translates and scales; rates come from c3_core.py's camera and
distances. The window opens in the entry's last cloud: frame 0 is the aperture filled
with flat soft white, which burns off and slides aft over the first 1.6 s.
"""
import argparse
import json
import math
import os
import subprocess
import sys
from concurrent.futures import ProcessPoolExecutor

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

sys.path.insert(0, str(__import__("pathlib").Path(__file__).resolve().parent))
from c3_core import (BASE, CABIN_DEPTH_M, CIN, DURATION, F_PX, FPS, GROUND_SPEED, H, HORIZON_Y, LAYERS, ROOT,  # noqa: E402
                     SINK_RATE, W, PLATE_X0, PLATE_Y0, lean_scale, rise_px, smooth, truck_px)

LAYER_DIR = BASE / "build" / "layers"
AUDIO_SRC = ROOT / "asset-pipeline" / "src" / "tool" / "resources" / "audio" / "jangala-arrival"
OUT = ROOT / "build" / "cinematic" / "c3-first-open-sky"
SHOT = "c3-first-open-sky"

# The entry ends inside cloud; C3 picks up on this flat white in the aperture (the
# hand-off agreed in the shared notes, c3-handoff.md), then burns it off.
ENTRY_WHITE = np.array([244, 246, 248], np.float32) / 255
BURN = 1.6
DEBUG = False


# ---------------------------------------------------------------- runtime contract

def duration():
    return DURATION


def captions():
    """No dialogue: the shot lets the company, and the player, look."""
    return []


def overlays():
    return []


def audio_cues():
    """(t, sound_id, volume[, until]). Built by :asset-pipeline:processAudio from
    audio.json; sources from c3_audio.py. The bed loops for the whole shot; the wind
    is one pass whose buffeting eases with the world shake."""
    return [
        (0.0, "jangala_arrival_cabin_bed", 0.55, DURATION),
        (0.0, "jangala_arrival_wind", 0.80),
    ]


# ---------------------------------------------------------------- sources

def arr(im):
    return np.asarray(im, np.float32) / 255.0


def img(a):
    return Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8))


class Layer:
    def __init__(self, name, im, x, y, vx=0.0, vy=0.0, scale=1.0, alpha=None, path=None, world=True):
        self.name, self.im, self.x, self.y, self.vx, self.vy = name, im, x, y, vx, vy
        self.scale, self.alpha, self.path, self.world = scale, alpha, path, world


def baked(name, meta):
    """A baked layer at frame scale: anything stored larger is reduced once, with a
    proper filter, so per-frame placement never minifies (no shimmer in motion)."""
    im = Image.open(LAYER_DIR / (name + ".png")).convert("RGBA")
    s = meta[name]["scale"]
    if s < 1.0:
        im = im.resize((round(im.width * s), round(im.height * s)), Image.LANCZOS)
        s = 1.0
    return im, s


def bird_frame(phase, span, glide):
    """One small dark flier at a point of its flap cycle (phase 0..1), or gliding.
    A wing is two segments, shoulder-wrist and wrist-tip; the hand lags the arm, so the
    downstroke reads as a stroke and not as a hinge. Drawn at 6x and reduced."""
    k = 6
    s = int(span * k * 1.5 + 12)
    im = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    c, half = s / 2, span * k / 2
    if glide:
        arm, hand = 0.18, 0.10                                    # held a little up
    else:
        arm = 0.55 * math.sin(2 * math.pi * phase)
        hand = 0.75 * math.sin(2 * math.pi * (phase - 0.09))
    col = (36, 40, 42, 255)
    w = max(2, round(span * k / 8))
    for side in (-1, 1):
        wx, wy = c + side * half * 0.48 * math.cos(arm), c - half * 0.48 * math.sin(arm)
        tx, ty = wx + side * half * 0.52 * math.cos(hand), wy - half * 0.52 * math.sin(hand)
        d.line([(c, c), (wx, wy), (tx, ty)], fill=col, width=w, joint="curve")
    d.ellipse([c - w * 1.1, c - w * 0.7, c + w * 1.1, c + w * 0.8], fill=col)
    return im.resize((max(1, s // k), max(1, s // k)), Image.LANCZOS)


def build():
    meta = json.loads((LAYER_DIR / "layers.json").read_text(encoding="utf-8"))
    D = {n: d for n, d, _ in LAYERS}
    L = []
    sky = Image.open(CIN / "managed-land-realism" / "assets" / "sky-realism-v1.png").convert("RGBA")
    sky_scale = 1.25
    sky = sky.resize((round(sky.width * sky_scale), round(sky.height * sky_scale)), Image.LANCZOS)
    L.append(Layer("sky", sky, -60, HORIZON_Y + 90 - sky.height, vx=-truck_px(D["horizon"]) / DURATION))

    def plate(name, dist, extra_vx=0.0, dy=0.0):
        im, s = baked(name, meta)
        L.append(Layer(name, im, PLATE_X0, PLATE_Y0 + dy, scale=s,
                       vx=-truck_px(dist) / DURATION + extra_vx, vy=-rise_px(dist) / DURATION))

    breeze = 1.0                                                  # m/s from the bow
    plate("range", D["horizon"])
    plate("far", D["far"])
    plate("mist-high", 4500.0, extra_vx=-F_PX * breeze / 4500.0)
    plate("mid", D["mid"])
    # Atlas: 300 m hull at 2.2 km (189 px), bow right, climbing out at 60 m/s and 8 m/s.
    atlas, s = baked("atlas", meta)
    ad = 2200.0
    L.append(Layer("atlas", atlas, 520, 205, scale=s, vx=-F_PX * GROUND_SPEED / ad + F_PX * 60.0 / ad,
                   vy=-F_PX * (8.0 + SINK_RATE) / ad))
    plate("mist-low", 1700.0, extra_vx=-F_PX * breeze / 1700.0)
    plate("near", D["near"])
    # Flock: eleven 3 m fliers ~700 m out at about our altitude, against the bright low
    # sky; flying aft at 8 m/s they pass the ship and enter from behind the right jamb.
    fd = 700.0
    span = F_PX * 3.0 / fd * 1.4          # 5.9 px true; drawn 1.4x so a wingbeat reads at 1080p
    rng = np.random.default_rng(11)
    vx_f = (-F_PX * GROUND_SPEED - F_PX * 8.0) / fd
    for i in range(11):
        bx, by = rng.uniform(-70, 70), rng.uniform(-14, 14)
        ph, sz, rate = rng.uniform(0, 1), rng.uniform(0.8, 1.15), rng.uniform(2.9, 3.5)
        glide_at = rng.uniform(0, 3)                              # each bird glides now and then

        def frames(t, ph=ph, sz=sz, rate=rate, g0=glide_at):
            glide = ((t + g0) % 2.6) > 2.0
            return bird_frame((t * rate + ph) % 1.0, span * sz, glide)

        def bob(t, ph=ph):
            return 1.2 * math.sin(2 * math.pi * (t * 0.7 + ph))

        L.append(Layer("bird%d" % i, None, 1900 + bx - vx_f * 2.5, 255 + by, vx=vx_f, vy=-0.8, path=(frames, bob)))
    cabin, _ = baked("cabin", meta)
    L.append(Layer("cabin", cabin, 0, 0, world=False))
    # density of the entry's last cloud, sliding aft at ~800 m (c3-layout.md, Pickup)
    cloud = Image.open(CIN / "managed-land-realism" / "assets" / "clouds-realism-v1.png").convert("RGBA")
    cloud = cloud.filter(ImageFilter.GaussianBlur(5))             # out of focus, 0.8 km off
    return L, cloud


SCENE = None


def scene():
    global SCENE
    if SCENE is None:
        SCENE = build()
    return SCENE


def jitter(t):
    """The airframe's last buffeting from the entry: world shake decaying over ~3 s."""
    amp = 2.4 * math.exp(-t / 0.9)
    return amp * math.sin(t * 37.0) + 0.6 * amp * math.sin(t * 23.0 + 1.3), amp * 0.7 * math.sin(t * 29.0 + 0.4)


def place(canvas, im, x, y, s, alpha=1.0, tint=None):
    w, h = im.size
    bx0, by0 = max(0, math.floor(x)), max(0, math.floor(y))
    bx1, by1 = min(W, math.ceil(x + w * s)), min(H, math.ceil(y + h * s))
    if bx1 <= bx0 or by1 <= by0:
        return
    tile = im.transform((bx1 - bx0, by1 - by0), Image.AFFINE, (1 / s, 0, (bx0 - x) / s, 0, 1 / s, (by0 - y) / s),
                        resample=Image.BICUBIC, fillcolor=(0, 0, 0, 0))
    a = arr(tile)
    rgb, al = a[..., :3], a[..., 3:4] * alpha
    if tint is not None:
        rgb = rgb * 0.45 + np.array(tint, np.float32) * 0.55
    reg = canvas[by0:by1, bx0:bx1]
    canvas[by0:by1, bx0:bx1] = reg * (1 - al) + rgb * al


def burn_off(canvas, cloud, t, jx, jy):
    """The entry's cloud over the world: flat white at t = 0, thinning from its
    thinnest parts first as it slides aft, gone at BURN."""
    if t >= BURN:
        return
    u = smooth(t / BURN)
    s = 2.4
    x, y = -1250 - F_PX * GROUND_SPEED / 800.0 * t + jx, -560 + jy
    tile = cloud.transform((W, H), Image.AFFINE, (1 / s, 0, -x / s, 0, 1 / s, -y / s),
                           resample=Image.BILINEAR, fillcolor=(0, 0, 0, 0))
    c = arr(tile)
    dens = c[..., 3] * (0.55 + 0.45 * (c[..., :3] @ np.array([0.3, 0.6, 0.1], np.float32)))
    # A gentle slope: thin cloud fades while thick cloud still stands, so the world
    # comes through the cloud rather than through holes cut in it.
    a = np.clip((0.4 + 0.6 * dens + 1.0 - 2.4 * u) * 1.3, 0, 1)[..., None]
    col = ENTRY_WHITE * (1 - u) + (c[..., :3] * c[..., 3:4] + ENTRY_WHITE * (1 - c[..., 3:4])) * u
    canvas[:] = canvas * (1 - a) + col * a


def frame(t):
    layers, cloud = scene()
    canvas = np.zeros((H, W, 3), np.float32)
    jx, jy = jitter(t)
    for L in layers:
        if L.name == "cabin":
            burn_off(canvas, cloud, t, jx, jy)
            s = L.scale * lean_scale(t, CABIN_DEPTH_M)
            place(canvas, L.im, W / 2 - W / 2 * s, H / 2 - H / 2 * s, s)
            continue
        if L.path is not None:
            draw, bob = L.path
            im = draw(t)
            place(canvas, im, L.x + L.vx * t + jx, L.y + L.vy * t + bob(t) + jy, 1.0)
            continue
        # the lean moves the eye 5 cm toward km-distant layers: scale 1.00004, ignored
        tint = TINT.get(L.name) if DEBUG else None
        place(canvas, L.im, L.x + L.vx * t + jx, L.y + L.vy * t + jy, L.scale, tint=tint)
    return img(canvas)


TINT = {"sky": (0.35, 0.6, 1.0), "range": (0.6, 0.45, 1.0), "far": (1.0, 0.8, 0.25),
        "mist-high": (1, 1, 1), "mid": (0.25, 0.85, 0.45), "atlas": (1.0, 0.3, 0.9),
        "mist-low": (1, 1, 1), "near": (1.0, 0.35, 0.25)}


# ---------------------------------------------------------------- review

def label(im, text):
    d = ImageDraw.Draw(im)
    d.rectangle([0, H - 34, W, H], fill=(0, 0, 0))
    d.text((14, H - 26), text, fill=(235, 235, 235))
    return im


STILLS = [
    ("01-pickup", 0.0, "t=0.0s  pickup: the aperture full of the entry's flat white"),
    ("02-burn", 0.8, "t=0.8s  the cloud burning off, thinnest parts first, sliding aft"),
    ("03-settle", 4.0, "t=4.0s  vibration gone"),
    ("04-lean", 8.0, "t=8.0s  lean nearly complete: cabin x1.04; Atlas climbing right, flock in from the right"),
    ("05-out", DURATION - 1 / FPS, "t=12.0s  out"),
]


def contact_sheet(path):
    sheet = Image.new("RGB", (W, H))
    d = ImageDraw.Draw(sheet)
    n = 16
    for i in range(n):
        t = min(DURATION - 1 / FPS, i * DURATION / (n - 1))
        th = frame(t).resize((W // 4, H // 4), Image.LANCZOS)
        x, y = (i % 4) * W // 4, (i // 4) * H // 4
        sheet.paste(th, (x, y))
        d.rectangle([x, y, x + 64, y + 18], fill=(0, 0, 0))
        d.text((x + 4, y + 4), "%.2fs" % t, fill=(255, 255, 255))
    sheet.save(path)


def render(i):
    frame(i / FPS).save(OUT / "frames" / ("%05d.png" % i))
    return i


def review_audio(path):
    cues = audio_cues()
    inputs, graph, labels = [], [], []
    for k, cue in enumerate(cues):
        t, sid, vol = cue[:3]
        loop = len(cue) > 3
        if loop:
            inputs += ["-stream_loop", "-1"]
        inputs += ["-i", str(AUDIO_SRC / (sid + ".flac"))]
        graph.append("[%d:a]aformat=sample_rates=44100:channel_layouts=stereo,volume=%.2f,adelay=%d:all=1%s[a%d]"
                     % (k, vol, round(t * 1000), ",atrim=0:%.3f" % cue[3] if loop else "", k))
        labels.append("[a%d]" % k)
    graph.append("%samix=inputs=%d:normalize=0:duration=longest,atrim=0:%.3f[m]" % ("".join(labels), len(cues), DURATION))
    subprocess.run(["ffmpeg", "-hide_banner", "-v", "error", "-y", *inputs, "-filter_complex", ";".join(graph),
                    "-map", "[m]", "-c:a", "pcm_s16le", str(path)], check=True)


def main():
    global DEBUG
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--movie", action="store_true")
    args = ap.parse_args()
    rev = BASE / "review"
    rev.mkdir(exist_ok=True)
    for old in rev.glob("c3-0*.png"):
        old.unlink()
    for name, t, text in STILLS:
        label(frame(t), "C3  " + text).save(rev / ("c3-%s.png" % name))
    DEBUG = True
    label(frame(6.0), "C3 band check t=6.0s  sky / range / far / mid / near / Atlas tinted; mist white; cabin untinted").save(
        rev / "c3-06-bands.png")
    DEBUG = False
    contact_sheet(rev / "c3-07-contact-sheet.png")
    print("stills and contact sheet in", rev)
    if not args.movie:
        return
    fr = OUT / "frames"
    fr.mkdir(parents=True, exist_ok=True)
    for old in fr.glob("*.png"):
        old.unlink()
    n = round(DURATION * FPS)
    with ProcessPoolExecutor(max(1, (os.cpu_count() or 2) // 2)) as pool:
        for i in pool.map(render, range(n), chunksize=6):
            if i % 48 == 0:
                print("frame", i, flush=True)
    wav = OUT / "c3-review-audio.wav"
    review_audio(wav)
    movie = OUT / (SHOT + ".mp4")
    subprocess.run(["ffmpeg", "-hide_banner", "-v", "error", "-y", "-framerate", str(FPS), "-i", str(fr / "%05d.png"),
                    "-i", str(wav), "-map", "0:v", "-map", "1:a", "-c:v", "libx264", "-crf", "16", "-pix_fmt", "yuv420p",
                    "-c:a", "aac", "-b:a", "160k", "-shortest", str(movie)], check=True)
    (OUT / "timeline.json").write_text(json.dumps({
        "shot": SHOT, "duration": duration(), "fps": FPS, "frames": n,
        "captions": captions(), "overlays": overlays(), "audio_cues": audio_cues()}, indent=1), encoding="utf-8")
    print("wrote", movie)


if __name__ == "__main__":
    main()
