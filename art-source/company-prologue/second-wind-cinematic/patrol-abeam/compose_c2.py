"""C2 PATROL ABEAM: production frames (c2-layout.md, runtime-contract.md).

    python compose_c2.py [--stills] [--workers N]

Composes 1920x1080 / 24 fps frames, back to front, through the one fixed cabin camera:

  1. space        black; no stars at an exposure set for sunlit metal (layout, Sun and light)
  2. Jangala      the painted plate (c2_planet.py), every row sliding aft at its own ground rate
  3. the Legion   render_patrol_sequence.py's Cycles frames: hull, mounts, fighters, running
                  lights, motion blur; light from c2_core.SUN only, plus planet-shine
  4. bloom        from the rendered emitters only (engines, lights), so the cabin never glows
  5. the cabin    forest-reveal's painted cabin from its own camera, relit into space by
                  c2_cabin_render.py's light passes: a hard sun patch on the forward jamb and
                  sill, faint planet-shine, black elsewhere (see cabin_states)

Writes build/cinematic/c2-patrol/frames/%05d.png (no text), c2-patrol.mp4 (review: the
hail mix under it and the captions burnt in, the shipped frames stay clean),
timeline.json (captions, overlays, audio cues), contact-sheet.png, and review stills
to review/c2-*.png. The entry transition (compose_entry.py) shares this module.

The player takes DURATION, captions(), overlays() and audio_cues() from here.
"""
import argparse
import json
import math
import os
import subprocess
import sys
import tempfile
from concurrent.futures import ProcessPoolExecutor
from functools import lru_cache
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

BASE = Path(__file__).resolve().parent
CIN = BASE.parent
ROOT = BASE.parents[3]
sys.path.insert(0, str(BASE))
sys.path.insert(0, str(CIN / "corvus-approach"))
sys.path.insert(0, str(CIN / "arrival-audio"))
import c2_core as C  # noqa: E402
import c2_planet  # noqa: E402

SHOT = "c2-patrol"
OUT = ROOT / "build" / "cinematic" / SHOT
LEGION = ROOT / "build" / "cinematic" / "c2-patrol" / "legion-seq"
GUIDES = BASE / "guides"
DURATION = C.C2_LEN
W, H, FPS = C.W, C.H, C.FPS


# ---------------------------------------------------------------- runtime contract

def duration():
    return DURATION


@lru_cache(maxsize=1)
def _hail():
    """[(line, start, end)] on the absolute hail clock, from the catalog and the treated clips."""
    import c1_overlay
    return [(line, start, end) for line, start, end, _, _ in c1_overlay.hail_lines()]


def captions_between(t0, t1):
    """(t_in, t_out, speaker_id, text) for every hail line starting in [t0, t1) on the hail
    clock, shot-relative. Paging, reading tail and speaker ids are C1's (c1_overlay):
    a line is split at sentences into pages of at most two rows, each held for its share
    of the take; speaker_id is the catalog speaker, which the player maps to C1's tags."""
    import c1_overlay
    out = []
    lines = _hail()
    for i, (line, start, end) in enumerate(lines):
        if not t0 <= start < t1:
            continue
        nxt = lines[i + 1][1] if i + 1 < len(lines) else math.inf
        release = min(end + c1_overlay.TAIL, nxt - 0.05, t1)
        pages = c1_overlay.chunks(line["text"])
        weights = [len(p) + 6 for p in pages]
        acc = start
        for k, (text, w) in enumerate(zip(pages, weights)):
            t_out = acc + (end - start) * w / sum(weights)
            if k + 1 < len(pages):           # a measured sentence start beats the proportional share
                t_out = SENTENCE_STARTS.get(pages[k + 1].split()[0:3].__str__(), t_out)
            out.append((round(acc - t0, 3), round(release - t0 if k == len(pages) - 1 else t_out - t0, 3),
                        line["speaker"], text))
            acc = t_out
    return out


# Sentence starts measured from the treated takes (c2_core.HAIL), keyed by a page's first words.
SENTENCE_STARTS = {str(["Patrol", "has", "your"]): C.HAIL["line7_patrol"][0],
                   str(["Somebody", "knows", "we're"]): C.HAIL["line9_knows"][0]}


def captions():
    return captions_between(C.C2_START, C.ENTRY_START)


def overlays():
    return []


def audio_cues():
    """(t, sound_id, volume[, until]), shot-relative. Lines 7 and 8 at their hail-clock
    starts; the cabin bed under the whole shot (C3's loop, reused by every window shot).
    The hail's squelch bursts are synthesised by build_hail_preview.py and have no mod
    sound yet, so they are in the review mix only."""
    starts = {line["id"]: start for line, start, _ in _hail()}
    return [
        (0.0, "jangala_arrival_cabin_bed", 0.55, DURATION),
        (round(starts["jangala_arrival_hail_07"] - C.C2_START, 3), "jangala_arrival_hail_07", 1.0),
        (round(starts["jangala_arrival_hail_08"] - C.C2_START, 3), "jangala_arrival_hail_08", 1.0),
    ]


def check_hail():
    """Refuse to compose against timings that have moved since c2_core was written."""
    starts = {line["id"]: (start, end) for line, start, end in _hail()}
    for key, line_id in (("line8", "jangala_arrival_hail_08"),):
        if abs(starts[line_id][0] - C.HAIL[key][0]) > 0.05:
            raise SystemExit("hail line %s moved: %.2f, c2_core says %.2f" % (line_id, starts[line_id][0], C.HAIL[key][0]))
    if abs(starts["jangala_arrival_hail_07"][0] - C.HAIL["line7"][0]) > 0.05:
        raise SystemExit("hail line 7 moved")
    if abs(starts["jangala_arrival_hail_09"][0] - C.HAIL["line9"][0]) > 0.05:
        raise SystemExit("hail line 9 moved")


# ---------------------------------------------------------------- the cabin

def _load16(name):
    return np.asarray(Image.open(GUIDES / name), np.float32)[..., None] / 65535.0


@lru_cache(maxsize=1)
def cabin_states():
    """The painted cabin relit, as linear RGB arrays, plus its coverage alpha (1 = cabin,
    0 = glass). The painting is the albedo; light comes from c2_cabin_render.py's passes.

    The painting and the cabin model agree at the glass, jambs and sill, but below the sill
    the model has a deep ledge where the painting shows a vertical panel wall facing the
    camera. Direct sun is therefore taken only above the sill's front edge (SILL_FRONT);
    the wall below gets the soft spill a sunlit sill throws on a wall beneath it, not the
    model's ledge light. Planet-shine and the window emitter are soft enough to use whole."""
    info = json.loads((GUIDES / "c2-cabin.json").read_text(encoding="utf-8"))
    if abs(info["pitch_down_deg"] - C.CABIN_PITCH_DEG) > 0.05:
        raise SystemExit("cabin camera pitch %.2f != c2_core.CABIN_PITCH_DEG" % info["pitch_down_deg"])
    paint = np.asarray(Image.open(GUIDES / "c2-cabin-paint.png").convert("RGBA"), np.float32) / 255
    alb, alpha = paint[..., :3] ** 2.2, paint[..., 3:4]
    sun, shine, window = _load16("c2-cabin-sun.png"), _load16("c2-cabin-shine.png"), _load16("c2-cabin-window.png")
    ys = np.arange(H, dtype=np.float32)[:, None, None]
    xs = np.arange(W, dtype=np.float32)[None, :, None]
    SILL_FRONT = 812.0
    above = np.clip((SILL_FRONT - ys) / 6.0, 0, 1)
    direct = np.clip((sun - 0.06) / 0.03, 0, 1)
    sun_col = np.array(C.SUN_COLOUR, np.float32)
    spill = 0.03 * np.exp(-np.maximum(ys - SILL_FRONT, 0) / 220.0) * (1 - above)
    space_light = (sun * direct * above * 5.5 + sun * (1 - direct) * above * 0.25 + spill) * sun_col \
        + shine * np.array([0.25, 0.40, 0.80], np.float32) * 0.5
    # Heat glow: orange light from the glass, hottest on the bow (right) side and low.
    bias = (0.55 + 0.75 * (xs / W)) * (0.85 + 0.3 * np.clip(ys / H, 0, 1))
    glow_light = window * bias * np.array([1.0, 0.42, 0.11], np.float32) * 3.0
    # Cloud: the sequence's first soft, diffuse fill; slightly warm from the last of the heat.
    cloud_light = window * np.array([0.93, 0.94, 0.96], np.float32) * 2.2 + 0.02
    return {"space": alb * space_light, "glow": alb * glow_light, "cloud": alb * cloud_light,
            "alpha": alpha, "albedo": alb}


def to_srgb(lin):
    return np.clip(lin, 0, 1) ** (1 / 2.2)


def to_lin(srgb):
    return np.clip(srgb, 0, 1) ** 2.2


# ---------------------------------------------------------------- the world

def legion_frame(i):
    """Linear RGB and alpha of the Legion pass at C2 frame i (straight alpha from Blender)."""
    path = LEGION / ("f_%05d.png" % min(i, round(DURATION * FPS) - 1))
    if not path.exists():
        return None
    a = np.asarray(Image.open(path).convert("RGBA"), np.float32) / 255
    return to_lin(a[..., :3]), a[..., 3:4]


def bloom(lin, threshold=0.8, gain=0.6):
    """Soft glow from the brightest parts only (engine bells, running lights, fighter flames)."""
    hot = np.clip(lin - threshold, 0, None)
    if hot.max() <= 0:
        return lin
    small = Image.fromarray((np.clip(hot, 0, 4) / 4 * 255).astype(np.uint8)).resize((W // 4, H // 4), Image.BILINEAR)
    glow = np.zeros_like(lin)
    for r, w in ((2, 0.5), (6, 0.35), (16, 0.25)):
        g = np.asarray(small.filter(ImageFilter.GaussianBlur(r)).resize((W, H), Image.BILINEAR), np.float32) / 255 * 4
        glow += g * w
    return lin + glow * gain


def world(t, i):
    """Linear RGB of everything outside the glass at C2 time t (frame i)."""
    canvas = to_lin(c2_planet.plate_layer(t))
    leg = legion_frame(i)
    if leg is not None:
        rgb, a = leg
        canvas = canvas * (1 - a) + rgb * a
    return bloom(canvas)


def frame_c2(t):
    i = int(round(t * FPS))
    cab = cabin_states()
    lin = world(t, i)
    lin = lin * (1 - cab["alpha"]) + cab["space"] * cab["alpha"]
    return Image.fromarray((to_srgb(lin) * 255 + 0.5).astype(np.uint8))


# ---------------------------------------------------------------- review helpers

def burn_captions(img, t, caps):
    """Review only: C1's caption look over a frame (the shipped frames stay clean)."""
    import c1_overlay
    import ui_style as ui
    frame = np.asarray(img, np.float32) / 255
    frame = frame.copy()
    for t_in, t_out, speaker, text in caps:
        if t_in - 0.2 <= t <= t_out + 0.2:
            a = c1_overlay._window(t, t_in, t_out, 0.14, 0.2)
            panel, pad = c1_overlay._field()
            ui.blend(frame, panel, W / 2 - panel.width / 2, c1_overlay.CAPTION_TOP - 14 - pad, a * 0.9)
            tag = c1_overlay._tag_patch(speaker)
            ui.blend(frame, tag, W / 2 - tag.width / 2, c1_overlay.CAPTION_TOP, a)
            cap = c1_overlay._caption_patch(text)
            ui.blend(frame, cap, W / 2 - cap.width / 2, c1_overlay.CAPTION_TOP + 30, a)
    return Image.fromarray((np.clip(frame, 0, 1) * 255 + 0.5).astype(np.uint8))


def contact_sheet(frames_dir, n_frames, out, cols=6, rows=5, label=""):
    sheet = Image.new("RGB", (W, H), (12, 12, 14))
    cw, ch = W // cols, H // rows
    d = ImageDraw.Draw(sheet)
    for k in range(cols * rows):
        i = min(n_frames - 1, round(k * (n_frames - 1) / (cols * rows - 1)))
        im = Image.open(frames_dir / ("%05d.png" % i)).convert("RGB").resize((cw - 4, ch - 22), Image.LANCZOS)
        x, y = (k % cols) * cw + 2, (k // cols) * ch + 2
        sheet.paste(im, (x, y))
        d.text((x + 4, y + ch - 20), "%s %.2fs (f%d)" % (label, i / FPS, i), fill=(220, 220, 220))
    sheet.save(out)


def review_movie(frames_dir, n_frames, caps, audio_from, length, movie):
    """mp4 for review: frames with captions burnt in, and the hail mix cut at the shot's clock."""
    import build_hail_preview as hail
    audio = ROOT / "build" / "audio-previews" / "jangala-hail" / "cabin.mp3"
    if not audio.exists():
        hail.main()
    with tempfile.TemporaryDirectory() as tmp:
        for i in range(n_frames):
            im = Image.open(frames_dir / ("%05d.png" % i)).convert("RGB")
            burn_captions(im, i / FPS, caps).save(Path(tmp) / ("%05d.png" % i), compress_level=1)
        subprocess.run(["ffmpeg", "-hide_banner", "-v", "error", "-y", "-framerate", str(FPS),
                        "-i", str(Path(tmp) / "%05d.png"), "-ss", "%.3f" % audio_from, "-t", "%.3f" % length,
                        "-i", str(audio), "-map", "0:v", "-map", "1:a", "-c:v", "libx264", "-crf", "17",
                        "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "160k", "-shortest", str(movie)], check=True)


def write_frames(fn, n, frames_dir, workers):
    frames_dir.mkdir(parents=True, exist_ok=True)
    for old in frames_dir.glob("*.png"):
        old.unlink()
    with ProcessPoolExecutor(workers) as pool:
        for done in pool.map(fn, range(n), chunksize=4):
            if done % 48 == 0:
                print("frame", done, flush=True)


def _render_c2(i):
    frame_c2(i / FPS).save(OUT / "frames" / ("%05d.png" % i))
    return i


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--stills", action="store_true", help="only the review stills")
    parser.add_argument("--workers", type=int, default=max(1, (os.cpu_count() or 2) // 2))
    args = parser.parse_args()
    check_hail()
    n = round(DURATION * FPS)
    meta = json.loads((LEGION / "meta.json").read_text(encoding="utf-8"))
    missing = [i for i in range(n) if i not in set(meta.get("rendered", []))]
    if missing:
        print("WARNING: Legion frames missing (%d), e.g. %s; run render_patrol_sequence.py" % (len(missing), missing[:5]))
    (BASE / "review").mkdir(exist_ok=True)
    for name, t in (("c2-1-bow", 3.0), ("c2-2-launch", 5.6), ("c2-3-abeam", 8.0), ("c2-4-rising", 11.4),
                    ("c2-5-clear", 14.2)):
        frame_c2(t).save(BASE / "review" / (name + ".png"))
    print("C2 %.1fs, %d frames; stills in review/" % (DURATION, n))
    if args.stills:
        return
    write_frames(_render_c2, n, OUT / "frames", args.workers)
    caps = captions()
    (OUT / "timeline.json").write_text(json.dumps({
        "shot": SHOT, "duration": DURATION, "fps": FPS, "frames": n, "hail_clock_start": C.C2_START,
        "captions": caps, "overlays": overlays(), "audio_cues": audio_cues(),
        "legion": {"blend": meta.get("blend"), "key": meta.get("key"), "samples": meta.get("samples")}}, indent=1),
        encoding="utf-8")
    contact_sheet(OUT / "frames", n, OUT / "contact-sheet.png", label="C2")
    review_movie(OUT / "frames", n, caps, C.C2_START, DURATION, OUT / (SHOT + ".mp4"))
    print("Wrote", (OUT / (SHOT + ".mp4")).relative_to(ROOT))


if __name__ == "__main__":
    main()
