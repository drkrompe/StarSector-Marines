"""C1 overlay: what is drawn over the finished frame in screen space (ui-overlay.md).

Owned by the UI pass. `apply(img, t, duration)` takes and returns the 1920x1080 RGB
frame. Two pieces:

- **Planet ID.** A sensor lock on Jangala: four short arcs close on the limb as
  Approach names itself, and a leader runs from the upper-right arc to a label
  (JANGALA, then CORVUS / HEGEMONY). It tracks the planet layer through the push
  and truck, and leaves as Approach keys down after line 1.
- **Captions.** Every hail line in C1, with a speaker tag, timed from
  `build_hail_preview.timeline()` and the catalog. Long lines are paged into chunks of
  at most two rows, each held for its share of the take.

No portraits: see ui-overlay.md. Nothing here reads traffic, station or ship positions.

Toggle with the environment variable C1_UI: unset or `1` draws everything, `0` / `off`
draws nothing (the clean plate), and a comma list such as `planet` or `captions` draws
only those parts. `ENABLED` overrides the variable when set from code.
"""
import json
import math
import os
import re
import sys
from functools import lru_cache
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent))
from c1_core import BASE, FOCUS, H, PLANET, PLANET_DIAMETER, PUSH, TRUCK, W, smooth  # noqa: E402
import ui_style as ui  # noqa: E402

sys.path.insert(0, str(BASE.parent / "arrival-audio"))
import build_hail_preview as hail  # noqa: E402

ENABLED = None                 # None: follow C1_UI; or a set such as {"planet"}
PARTS = ("planet", "captions")
PLANET_DEPTH_FALLBACK = 0.04   # compose_c1's planet layer; read from the live scene when present


def parts():
    if ENABLED is not None:
        return set(ENABLED)
    raw = os.environ.get("C1_UI", "1").strip().lower()
    if raw in ("0", "off", "false", "none", ""):
        return set()
    if raw in ("1", "on", "true", "all"):
        return set(PARTS)
    return {p.strip() for p in raw.split(",")} & set(PARTS)


# ---------------------------------------------------------------- timing

@lru_cache(maxsize=1)
def hail_lines():
    """[(line, start, end, key_up)] for the whole hail; key_up is the squelch-open time
    for an Approach transmission, else the start."""
    lines = hail.hail_catalog()
    starts, squelch, _ = hail.timeline(lines)
    opens = iter(t for kind, t in squelch if kind == "open")
    closes = iter(t for kind, t in squelch if kind == "close")
    out = []
    for line, start in zip(lines, starts):
        end = start + hail.duration(hail.TREATED / (line["id"] + ".ogg"))
        radio = line["speaker"] == hail.APPROACH
        out.append((line, start, end, next(opens) if radio else start, next(closes) if radio else end))
    return out


def planet_lock_times():
    """The lock lands as Approach says 'Jangala' in its first call (estimated by the word's
    character position in the take) and clears when that transmission keys down."""
    for line, start, end, _, key_down in hail_lines():
        if line["speaker"] == hail.APPROACH and "Jangala" in line["text"]:
            text = line["text"]
            return start + (end - start) * text.index("Jangala") / len(text), key_down
    first = hail_lines()[0]
    return first[1], first[4]


# ---------------------------------------------------------------- planet tracking

def _planet_depth(t):
    for name in ("__main__", "compose_c1", "__mp_main__"):
        scene = getattr(sys.modules.get(name), "SCENE", None)
        for el in scene or ():
            if el.name == "planet":
                return el.depth_at(t)
    return PLANET_DEPTH_FALLBACK


def planet_screen(t, duration):
    """Globe centre and radius on screen: compose_c1's camera applied to c1_core.PLANET."""
    depth = _planet_depth(t)
    u = t / duration
    cam = PUSH[0] + (PUSH[1] - PUSH[0]) * smooth(u)
    zoom = cam ** (0.4 + 0.6 * depth / 0.6)
    cx = PLANET[0] - TRUCK * depth * u
    return (FOCUS[0] + (cx - FOCUS[0]) * zoom, FOCUS[1] + (PLANET[1] - FOCUS[1]) * zoom,
            PLANET_DIAMETER / 2 * zoom)


# ---------------------------------------------------------------- planet ID

ARC_DEG = 16.0         # each lock arc's span
LOCK_SECONDS = 0.7     # arcs closing in
OUT_SECONDS = 0.9
LEADER = 46.0          # diagonal run from the lower-right arc out to the label rule


@lru_cache(maxsize=None)
def _label_patches():
    name_f, detail_f = ui.font(30, "SemiBold", display=True), ui.font(14, "Medium", display=True)
    name = "JANGALA"
    nw = int(ui.text_width(name, name_f, 4)) + 8
    a = Image.new("RGBA", (nw, 46), (0, 0, 0, 0))
    ui.draw_text(ImageDraw.Draw(a), (4, 2), name, name_f, ui.UI_CYAN + (255,), 4)
    parts_ = [("CORVUS  /  ", ui.UI_CYAN), ("HEGEMONY", ui.HEGEMONY)]
    dw = int(sum(ui.text_width(s, detail_f, 1.5) for s, _ in parts_)) + 8
    b = Image.new("RGBA", (dw, 24), (0, 0, 0, 0))
    d, x = ImageDraw.Draw(b), 4.0
    for s, c in parts_:
        x += ui.draw_text(d, (x, 2), s, detail_f, c + (230,), 1.5) + 1.5
    return ui.with_shadow(a, 3, 0.7), ui.with_shadow(b, 2.5, 0.7)


def _arc(cx, cy, rr, mid_deg, span=ARC_DEG, steps=24):
    return [(cx + rr * math.cos(math.radians(mid_deg + span * (i / steps - 0.5))),
             cy - rr * math.sin(math.radians(mid_deg + span * (i / steps - 0.5)))) for i in range(steps + 1)]


def draw_planet_id(frame, t, duration):
    t_lock, t_out = planet_lock_times()
    if t < t_lock or t > t_out + OUT_SECONDS:
        return
    fade = 1.0 - smooth((t - t_out) / OUT_SECONDS)
    p = smooth((t - t_lock) / LOCK_SECONDS)
    cx, cy, r = planet_screen(t, duration)
    rr = r * (1.045 + 0.14 * (1.0 - p))
    # A brief brightening as the lock lands, settling to a quiet hold.
    flash = math.exp(-((t - t_lock - LOCK_SECONDS) / 0.25) ** 2)
    ring_a = (0.6 + 0.35 * flash) * smooth((t - t_lock) / 0.35) * fade
    segs = [_arc(cx, cy, rr, deg) for deg in (45, 135, 225, 315)]
    # From the upper-right arc a leader runs up and out to a rule the label sits on: ships
    # cross the lower right of the approach, and the label must not cover them.
    name, detail = _label_patches()
    width = max(name.width, detail.width) - 4
    ax, ay = cx + rr * math.cos(math.radians(45)), cy - rr * math.sin(math.radians(45))
    ex, ey = ax + LEADER, ay - LEADER
    run = smooth((t - t_lock - LOCK_SECONDS * 0.6) / 0.6)
    if run > 0:
        k = min(1.0, run * 2)
        leader = [(ax + 4, ay - 4), (ax + 4 + (ex - ax - 4) * k, ay - 4 + (ey - ay + 4) * k)]
        if run > 0.5:
            leader.append((ex + (width + 6) * (run - 0.5) * 2, ey))
        segs.append(leader)
    patch, x0, y0 = ui.lines_patch(segs, ui.UI_CYAN, 1.6)
    ui.blend(frame, patch, x0, y0, ring_a)
    text_a = smooth((t - t_lock - LOCK_SECONDS * 1.2) / 0.45) * fade
    slide = 10 * (1 - smooth((t - t_lock - LOCK_SECONDS * 1.2) / 0.6))
    top = ey - 4 - name.height - 2 - detail.height
    ui.blend(frame, name, ex + 2 - slide, top, text_a)
    ui.blend(frame, detail, ex + 2 - slide, top + name.height + 2, 0.9 * text_a)


# ---------------------------------------------------------------- captions

CAPTION_SIZE = 34
CAPTION_MAX_W = 960
CAPTION_TOP = H - 168      # speaker tag row; text rows hang below it at fixed places
ROW = 44
FADE = 0.14
TAIL = 0.45                # a line stays up this long after its take ends, for reading
BRIDGE = 1.8               # gaps shorter than this keep the caption field up
SPEAKERS = {
    hail.APPROACH: ("JANGALA APPROACH", ui.HEGEMONY),
    "Castle": ("CASTLE  /  SECOND WIND", ui.UI_CYAN),
}


def _wrap(text, f, width):
    """Greedy rows, then a two-row result rebalanced so neither row is a stub."""
    rows, row = [], ""
    for word in text.split():
        trial = (row + " " + word).strip()
        if row and f.getlength(trial) > width:
            rows.append(row)
            row = word
        else:
            row = trial
    rows += [row] if row else []
    if len(rows) == 2:
        words = text.split()
        best = min(range(1, len(words)), key=lambda i: max(f.getlength(" ".join(words[:i])),
                                                            f.getlength(" ".join(words[i:]))))
        rows = [" ".join(words[:best]), " ".join(words[best:])]
    return rows


@lru_cache(maxsize=None)
def chunks(text):
    """Page a line into captions of at most two rows, splitting at sentences, then at
    commas only where one sentence alone needs three rows."""
    f = ui.font(CAPTION_SIZE)
    pieces = []
    for sentence in re.split(r"(?<=[.?!])\s+", text.strip()):
        if len(_wrap(sentence, f, CAPTION_MAX_W)) <= 2:
            pieces.append(sentence)
        else:
            pieces += re.split(r"(?<=,)\s+", sentence)
    out, cur = [], ""
    for piece in pieces:
        trial = (cur + " " + piece).strip()
        if cur and len(_wrap(trial, f, CAPTION_MAX_W)) > 2:
            out.append(cur)
            cur = piece
        else:
            cur = trial
    return tuple(out + ([cur] if cur else []))


def caption_schedule(duration):
    """[(speaker, key_up, release, [(text, t0, t1)])] for every line that starts in C1.
    Chunks share the take in proportion to their length."""
    out = []
    lines = hail_lines()
    for i, (line, start, end, key_up, _) in enumerate(lines):
        if start >= duration:
            continue
        nxt = lines[i + 1][3] if i + 1 < len(lines) else math.inf
        release = min(end + TAIL, nxt - 0.05, duration)
        cs = chunks(line["text"])
        weights = [len(c) + 6 for c in cs]
        total, acc, pages = sum(weights), start, []
        for c, w in zip(cs, weights):
            t1 = acc + (end - start) * w / total
            pages.append((c, acc, t1))
            acc = t1
        pages[-1] = (pages[-1][0], pages[-1][1], release)
        pages[0] = (pages[0][0], min(pages[0][1], key_up + 0.1), pages[0][2])
        out.append((line["speaker"], key_up, release, pages))
    return out


def _window(t, t0, t1, fade_in=FADE, fade_out=FADE):
    return smooth((t - t0) / fade_in) * (1 - smooth((t - (t1 - fade_out)) / fade_out))


@lru_cache(maxsize=None)
def _caption_patch(text):
    f = ui.font(CAPTION_SIZE)
    rows = _wrap(text, f, CAPTION_MAX_W)
    img = Image.new("RGBA", (CAPTION_MAX_W + 40, ROW * len(rows) + 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for i, row in enumerate(rows):
        d.text(((img.width - f.getlength(row)) / 2, 6 + i * ROW), row, font=f, fill=ui.TEXT + (255,))
    return ui.with_shadow(img, 3.5, 0.9)


@lru_cache(maxsize=None)
def _tag_patch(speaker):
    label, color = SPEAKERS.get(speaker, (speaker.upper(), ui.UI_CYAN))
    f = ui.font(15, "SemiBold", display=True)
    tw = ui.text_width(label, f, 3)
    img = Image.new("RGBA", (int(tw) + 60, 28), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # Short rules either side, like a channel tag on a comms readout.
    mid, x0 = 14, 30
    d.line((4, mid, 22, mid), fill=color + (150,), width=1)
    ui.draw_text(d, (x0, 4), label, f, color + (255,), 3)
    d.line((x0 + tw + 8, mid, x0 + tw + 26, mid), fill=color + (150,), width=1)
    return ui.with_shadow(img, 2.5, 0.7)


@lru_cache(maxsize=1)
def _field():
    return ui.soft_panel(CAPTION_MAX_W + 120, 2 * ROW + 56, alpha=0.42, feather=34)


def draw_captions(frame, t, duration):
    sched = caption_schedule(duration)
    if not sched:
        return
    # The caption field: up across the whole exchange, bridged over short gaps.
    spans = []
    for _, k, r, _ in sched:
        if spans and k - spans[-1][1] < BRIDGE:
            spans[-1][1] = r
        else:
            spans.append([k, r])
    field_a = max((_window(t, a, b, 0.5, 0.6) for a, b in spans), default=0)
    if field_a > 0:
        panel, pad = _field()
        ui.blend(frame, panel, W / 2 - panel.width / 2, CAPTION_TOP - 14 - pad, field_a)
    for speaker, key_up, release, pages in sched:
        if not key_up - 0.01 <= t <= release:
            continue
        tag = _tag_patch(speaker)
        ui.blend(frame, tag, W / 2 - tag.width / 2, CAPTION_TOP, _window(t, key_up, release, 0.2, 0.25))
        for text, t0, t1 in pages:
            a = _window(t, t0, t1, FADE, FADE if t1 < release else 0.25)
            if a > 0:
                cap = _caption_patch(text)
                ui.blend(frame, cap, W / 2 - cap.width / 2, CAPTION_TOP + 30, a)


# ---------------------------------------------------------------- entry point

def apply(img, t, duration):
    want = parts()
    if not want:
        return img
    frame = np.asarray(img, np.float32) / 255
    frame = frame.copy()
    if "planet" in want:
        draw_planet_id(frame, t, duration)
    if "captions" in want:
        draw_captions(frame, t, duration)
    return Image.fromarray((np.clip(frame, 0, 1) * 255 + 0.5).astype(np.uint8))
