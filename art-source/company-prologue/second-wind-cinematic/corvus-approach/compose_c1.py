"""C1 CORVUS layered animatic (storyboard-v3.md, c1-layout.md).

python art-source/company-prologue/second-wind-cinematic/corvus-approach/compose_c1.py [--stills] [--stand-ins]

Composes 1920x1080 / 24 fps frames from depth layers that only translate and
scale. Moving elements ride their own layer at that layer's parallax rate, along
keyframed paths. Second Wind makes its entrance from off-screen bottom left and
recedes toward Jangala. Writes build/cinematic/c1-corvus/{frames,c1-corvus.mp4}
and review stills to review/. The audio is the locked hail (lines 1-4) cut from
build_hail_preview.py's cabin mix, so run :asset-pipeline:processAudio first.

Painted layers come from assets/ and layers/ (see c1-layout.md). Where a painted
layer does not exist yet, --stand-ins substitutes the installed game's own
sprites (read from the install, never copied here) so timing can be reviewed.
"""
import argparse
import json
import math
import os
import subprocess
import sys
from concurrent.futures import ProcessPoolExecutor

import numpy as np
from PIL import Image, ImageEnhance

sys.path.insert(0, str(__import__("pathlib").Path(__file__).resolve().parent))
import c1_overlay  # noqa: E402
import c1_ship  # noqa: E402
import c1_traffic  # noqa: E402
from c1_core import BASE, FOCUS, FPS, H, PLANET, PLANET_DIAMETER, PUSH, ROOT, TRUCK, W, Element, smooth  # noqa: E402

OUT = ROOT / "build" / "cinematic" / "c1-corvus"
STAND_INS = False

sys.path.insert(0, str(BASE.parent / "arrival-audio"))
import build_hail_preview as hail  # noqa: E402


def c1_duration():
    """C1 ends where Approach keys up for line 7, which belongs to C2."""
    lines = json.loads(hail.CATALOG.read_text(encoding="utf-8"))["lines"]
    _, squelch, _ = hail.timeline(lines)
    opens = [t for kind, t in squelch if kind == "open"]
    return opens[2] - 0.3


# ---------------------------------------------------------------- sources

def interim_globe(diameter):
    """Placeholder until render_planet.py lands: the earlier plate's texture on a lit disc."""
    src = Image.open(BASE.parent / "space-approach" / "assets" / "planet-plate.png").convert("RGB")
    d = int(diameter)
    tex = np.asarray(src.crop((500, 0, 1400, 900)).resize((d, d), Image.LANCZOS), np.float32) / 255
    yy, xx = (np.mgrid[:d, :d] + 0.5) / d * 2 - 1
    r = np.sqrt(xx * xx + yy * yy)
    z = np.sqrt(np.clip(1 - r * r, 0, 1))
    sun = np.array([-0.55, -0.45, 0.70]); sun /= np.linalg.norm(sun)
    lit = np.clip(xx * sun[0] + yy * sun[1] + z * sun[2], 0, 1)[..., None]
    rgb = tex * (0.04 + 0.96 * lit) + np.array([0.35, 0.55, 0.9]) * (1 - z[..., None]) ** 3 * lit * 0.6
    alpha = np.clip((1 - r) * d / 2, 0, 1)
    arr = np.dstack([np.clip(rgb, 0, 1), alpha])
    return Image.fromarray((arr * 255).astype(np.uint8), "RGBA")


# ---------------------------------------------------------------- scene

def build_scene(duration):
    els = []

    def add(*a, **k):
        el = Element(*a, **k)
        els.append(el)
        return el

    sky = Image.open(BASE / "assets" / "star-sky-v1.png").convert("RGBA")
    scale = max(2300 / sky.width, 1300 / sky.height)        # cover the overscan
    sky = ImageEnhance.Brightness(sky.convert("RGB")).enhance(0.55).convert("RGBA")   # stars stay behind the hulls
    add("sky", sky.resize((round(sky.width * scale), round(sky.height * scale)), Image.LANCZOS), 0.03,
        [(0, 960, 540, 1.0)])

    # Pulled-back camera: Jangala is a whole distant globe at centre right, and the action
    # plays in open space in front of it.
    planet = BASE / "layers" / "planet.png"
    if planet.exists():
        img = Image.open(planet).convert("RGBA").resize((W, H), Image.LANCZOS)   # registered to the frame
        add("planet", img, 0.04, [(0, 960, 540, 1.0)])
    else:
        add("planet", interim_globe(PLANET_DIAMETER), 0.04, [(0, PLANET[0], PLANET[1], 1.0)])

    c1_traffic.build(add, duration, STAND_INS)
    c1_ship.build(add, duration)
    return els


# ---------------------------------------------------------------- rendering

SCENE = None


def frame_image(t, duration):
    global SCENE
    if SCENE is None:
        SCENE = build_scene(duration)
    u = t / duration
    cam = PUSH[0] + (PUSH[1] - PUSH[0]) * smooth(u)
    canvas = np.zeros((H, W, 3), np.float32)
    # Farthest first: a body's draw order follows its distance, so Second Wind passes behind
    # nearer traffic once it has receded past it. Children share their parent's depth and the
    # sort is stable, so glows and lights stay over their hull.
    for el in sorted(SCENE, key=lambda e: e.depth_at(t)):
        if el.blink is not None and ((t + el.blink[1]) % el.blink[0]) > el.blink[0] * 0.18:
            continue
        cx, cy, s_el = el.place(t)
        img = el.image(t)
        depth = el.depth_at(t)
        cx -= TRUCK * depth * u
        zoom = cam ** (0.4 + 0.6 * depth / 0.6)      # nearer layers feel the push more
        s = s_el * zoom
        sx, sy = FOCUS[0] + (cx - FOCUS[0]) * zoom, FOCUS[1] + (cy - FOCUS[1]) * zoom
        w, h = img.size
        x0, y0 = sx - w * s / 2, sy - h * s / 2
        bx0, by0 = max(0, math.floor(x0)), max(0, math.floor(y0))
        bx1, by1 = min(W, math.ceil(x0 + w * s)), min(H, math.ceil(y0 + h * s))
        if bx1 <= bx0 or by1 <= by0:
            continue
        # Inverse affine into just the covered box: sub-pixel placement, no stepping.
        tile = img.transform((bx1 - bx0, by1 - by0), Image.AFFINE,
                                (1 / s, 0, (bx0 - x0) / s, 0, 1 / s, (by0 - y0) / s),
                                resample=Image.BICUBIC, fillcolor=(0, 0, 0, 0))
        arr = np.asarray(tile, np.float32) / 255
        rgb, a = arr[..., :3], arr[..., 3:4]
        if el.alpha is not None:
            a = a * el.alpha(t)
        region = canvas[by0:by1, bx0:bx1]
        canvas[by0:by1, bx0:bx1] = region + rgb * a if el.additive else region * (1 - a) + rgb * a
    return Image.fromarray((np.clip(canvas, 0, 1) * 255).astype(np.uint8))


def review_image(t, duration):
    """The clean frame with the runtime overlay drawn on, to preview what the player will show."""
    return c1_overlay.apply(frame_image(t, duration), t, duration)


def init(stand_ins):
    global STAND_INS
    STAND_INS = stand_ins


def render(index, duration):
    frame_image(index / FPS, duration).save(OUT / "frames" / ("%05d.png" % index))
    return index


# ---------------------------------------------------------------- runtime data (runtime-contract.md)

DURATION = None
SPEAKER_IDS = {hail.APPROACH: "approach", "Castle": "castle"}
OVERLAY_SAMPLE_HZ = 8     # planet-lock track samples per second; the player interpolates


def duration():
    global DURATION
    if DURATION is None:
        DURATION = c1_duration()
    return DURATION


def captions():
    """[(t_in, t_out, speaker_id, catalog_line_id)]: one entry per hail line that starts in C1, over
    the whole take (first page in to last page out). No text: the exporter turns the id into a
    strings.json key and the player pages whatever that language says."""
    out = []
    starting = [line for line, start, *_ in c1_overlay.hail_lines() if start < duration()]
    for line, (speaker, _key_up, _release, pages) in zip(starting, c1_overlay.caption_schedule(duration())):
        out.append((round(pages[0][1], 3), round(pages[-1][2], 3), SPEAKER_IDS[speaker], line["id"]))
    return out


def overlays():
    """The planet lock as data: the globe's screen centre and radius (source pixels) over time."""
    global SCENE
    if SCENE is None:
        SCENE = build_scene(duration())
    t_lock, t_out = c1_overlay.planet_lock_times()
    t_end = min(duration(), t_out + c1_overlay.OUT_SECONDS)
    steps = max(1, math.ceil((t_end - t_lock) * OVERLAY_SAMPLE_HZ))
    track = []
    for i in range(steps + 1):
        t = t_lock + (t_end - t_lock) * i / steps
        cx, cy, r = c1_overlay.planet_screen(t, duration())
        track.append([round(t, 3), round(cx, 2), round(cy, 2), round(r, 2)])
    return [{
        "kind": "lock", "t_in": round(t_lock, 3), "t_out": round(t_out, 3),
        "close_seconds": c1_overlay.LOCK_SECONDS, "out_seconds": c1_overlay.OUT_SECONDS,
        "arc_degrees": c1_overlay.ARC_DEG, "leader": c1_overlay.LEADER,
        "title_key": "jangalaArrivalLockTitle",
        "detail": [{"key": "jangalaArrivalLockDetailSystem", "color": "player"},
                   {"key": "jangalaArrivalLockDetailFaction", "color": "hegemony"}],
        "track": track,
    }]


def audio_cues():
    """[{t, sound, volume, [until, fade_in, fade_out]}] in shot time (hail time is shot time).
    The cabin bed is C1's and carries on under the hail's later lines in C2 to `until`."""
    lines = json.loads(hail.CATALOG.read_text(encoding="utf-8"))["lines"]
    starts, squelch, total = hail.timeline(lines)
    cues = [{"t": 0.0, "sound": "jangala_cabin_bed", "volume": 1.0, "until": round(total - hail.TAIL + 0.5, 3),
             "fade_in": 1.0, "fade_out": 1.5}]
    # Lines 7-9 start after C1 ends (hail time is C1 time, so they fall past `duration()`): they
    # belong to C2's own cues, in C2 time.
    for kind, t in squelch:
        if t < duration():
            cues.append({"t": round(t, 3), "sound": "jangala_squelch_" + kind, "volume": 1.0})
    for line, start in zip(lines, starts):
        if start < duration():
            cues.append({"t": round(start, 3), "sound": line["id"], "volume": 1.0})
    return sorted(cues, key=lambda c: (c["t"], c["sound"]))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--stills", action="store_true", help="only the review stills")
    parser.add_argument("--stand-ins", action="store_true", help="vanilla sprites where painted layers are missing")
    args = parser.parse_args()
    init(args.stand_ins)
    duration = c1_duration()
    frames = round(duration * FPS)
    (BASE / "review").mkdir(exist_ok=True)
    for name, t in (("entrance", 2.5), ("settle", 7.0), ("mid", duration / 2), ("end", duration - 1 / FPS)):
        review_image(t, duration).save(BASE / "review" / (name + ".png"))
    print("C1 %.2fs, %d frames; stills in review/" % (duration, frames))
    if args.stills:
        return
    (OUT / "frames").mkdir(parents=True, exist_ok=True)
    for old in (OUT / "frames").glob("*.png"):
        old.unlink()
    with ProcessPoolExecutor(max(1, (os.cpu_count() or 2) // 2), initializer=init, initargs=(args.stand_ins,)) as pool:
        for done in pool.map(render, range(frames), [duration] * frames, chunksize=8):
            if done % 120 == 0:
                print("frame", done, flush=True)
    hail.main()
    audio = ROOT / "build" / "audio-previews" / "jangala-hail" / "cabin.mp3"
    # The shipped video is cut from the clean frames by cinematic_export.py; the review movie
    # shows what the player will draw over them.
    review = OUT / "review-frames"
    review.mkdir(exist_ok=True)
    for old in review.glob("*.png"):
        old.unlink()
    for i in range(frames):
        c1_overlay.apply(Image.open(OUT / "frames" / ("%05d.png" % i)).convert("RGB"), i / FPS, duration).save(
            review / ("%05d.png" % i))
    movie = OUT / "c1-corvus.mp4"
    subprocess.run(["ffmpeg", "-hide_banner", "-v", "error", "-y", "-framerate", str(FPS),
                    "-i", str(review / "%05d.png"), "-i", str(audio),
                    "-filter_complex", "[1:a]atrim=0:%.3f,afade=t=out:st=%.3f:d=0.6[a]" % (duration, duration - 0.6),
                    "-map", "0:v", "-map", "[a]", "-c:v", "libx264", "-crf", "18", "-pix_fmt", "yuv420p",
                    "-c:a", "aac", "-b:a", "160k", "-shortest", str(movie)], check=True)
    print("Wrote", movie.relative_to(ROOT))


if __name__ == "__main__":
    main()
