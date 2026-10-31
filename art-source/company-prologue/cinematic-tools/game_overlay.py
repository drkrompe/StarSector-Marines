"""What the game draws over a film's picture, drawn into a frame for a video shown outside the game.

CinematicScreen draws each shot's lock overlays live over the decoded picture: four arcs closing on a
limb, a leader out to a rule, and a title over coloured detail runs in the game's Orbitron bitmap
fonts. This is that drawing, ported line for line so the posted film matches the player:

  LockTrack.at                -> lock_state
  CinematicScreen.drawLocks   -> draw_locks (source pixels, scale 1, as at 1920x1080)
  BitmapFont.drawStringScaled -> Font.draw (BMFont glyph quads, bilinear, alpha blended)

The picture's own fade (CinematicScreen FADE_IN, FADE_OUT) is youtube_master's. Captions are not
drawn: a posted film carries them as a sidecar.
"""
import math
import os
import re
from functools import lru_cache
from pathlib import Path

import numpy as np
from PIL import Image

FONTS = Path(os.environ.get("STARSECTOR_DIR", r"C:\Program Files (x86)\Fractal Softworks\Starsector")) \
    / "starsector-core" / "graphics" / "fonts"
COLORS = {"player": (170, 222, 255), "hegemony": (245, 150, 30), "pirate": (235, 90, 70), "pirates": (235, 90, 70)}
PLAYER = COLORS["player"]
RING_MARGIN = 1.045          # LockTrack
CLOSE_TRAVEL = 0.14
LINE_WIDTH = 1.6             # drawLocks: glLineWidth(1.6 * scale), GL_LINE_SMOOTH


def smooth(x):
    c = min(1.0, max(0.0, x))
    return c * c * (3 - 2 * c)


class Font:
    """An AngelCode BMFont text file and its page, as BitmapFont reads them (no kerning)."""

    def __init__(self, name):
        text = (FONTS / name).read_text(encoding="utf-8")
        common = dict(re.findall(r"(\w+)=(-?\d+)", re.search(r"^common .*$", text, re.M).group(0)))
        self.line_height = int(common["lineHeight"])
        page = re.search(r'^page id=0 file="([^"]+)"', text, re.M).group(1)
        self.page = np.asarray(Image.open(FONTS / page).convert("RGBA"), np.float64) / 255
        self.glyphs = {}
        for line in re.findall(r"^char .*$", text, re.M):
            g = {k: int(v) for k, v in re.findall(r"(\w+)=(-?\d+)", line)}
            self.glyphs[g["id"]] = g

    def measure(self, s):
        return sum(self.glyphs[ord(ch)]["xadvance"] for ch in s if ord(ch) in self.glyphs)

    def draw(self, frame, s, x, y, scale, color, alpha):
        """BitmapFont.drawStringScaled from the text's top-left (x, y) in source pixels, y down."""
        if alpha <= 0:
            return
        cursor = 0.0
        for ch in s:
            g = self.glyphs.get(ord(ch))
            if g is None:
                continue
            if g["width"] > 0 and g["height"] > 0:
                x0, y0 = x + (cursor + g["xoffset"]) * scale, y + g["yoffset"] * scale
                self._quad(frame, g, x0, y0, scale, color, alpha)
            cursor += g["xadvance"]

    def _quad(self, frame, g, x0, y0, scale, color, alpha):
        w, h = g["width"] * scale, g["height"] * scale
        i0, i1 = max(0, math.floor(x0)), min(frame.shape[1], math.ceil(x0 + w))
        j0, j1 = max(0, math.floor(y0)), min(frame.shape[0], math.ceil(y0 + h))
        if i0 >= i1 or j0 >= j1:
            return
        # pixel centres inside the quad sample the page bilinearly (GL_LINEAR), clamped to the glyph
        px = np.arange(i0, i1) + 0.5
        py = np.arange(j0, j1) + 0.5
        inside_x = (px >= x0) & (px < x0 + w)
        inside_y = (py >= y0) & (py < y0 + h)
        u = g["x"] + (px - x0) / scale - 0.5
        v = g["y"] + (py - y0) / scale - 0.5
        u = np.clip(u, g["x"], g["x"] + g["width"] - 1)
        v = np.clip(v, g["y"], g["y"] + g["height"] - 1)
        ua, va = np.floor(u).astype(int), np.floor(v).astype(int)
        fu, fv = (u - ua)[None, :, None], (v - va)[:, None, None]
        ub = np.minimum(ua + 1, self.page.shape[1] - 1)
        vb = np.minimum(va + 1, self.page.shape[0] - 1)
        p = self.page
        tex = (p[va][:, ua] * (1 - fu) * (1 - fv) + p[va][:, ub] * fu * (1 - fv)
               + p[vb][:, ua] * (1 - fu) * fv + p[vb][:, ub] * fu * fv)
        a = tex[..., 3] * alpha * (inside_y[:, None] & inside_x[None, :])
        rgb = tex[..., :3] * (np.array(color) / 255)
        blend(frame, j0, i0, rgb, a)


def blend(frame, j0, i0, rgb, a):
    """glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA) onto frame[j0:, i0:] (float RGB 0..1)."""
    h, w = a.shape
    region = frame[j0:j0 + h, i0:i0 + w]
    region *= 1 - a[..., None]
    region += rgb * a[..., None]


def strip(frame, points, color, alpha):
    """A GL_LINE_STRIP with GL_LINE_SMOOTH at LINE_WIDTH: each pixel covered by its distance to the strip."""
    if alpha <= 0 or len(points) < 2:
        return
    pts = np.asarray(points, np.float64)
    pad = LINE_WIDTH + 2
    i0, j0 = max(0, math.floor(pts[:, 0].min() - pad)), max(0, math.floor(pts[:, 1].min() - pad))
    i1 = min(frame.shape[1], math.ceil(pts[:, 0].max() + pad))
    j1 = min(frame.shape[0], math.ceil(pts[:, 1].max() + pad))
    if i0 >= i1 or j0 >= j1:
        return
    gx, gy = np.meshgrid(np.arange(i0, i1) + 0.5, np.arange(j0, j1) + 0.5)
    dist = np.full(gx.shape, np.inf)
    for (ax, ay), (bx, by) in zip(pts[:-1], pts[1:]):
        dx, dy = bx - ax, by - ay
        k = np.clip(((gx - ax) * dx + (gy - ay) * dy) / max(1e-12, dx * dx + dy * dy), 0, 1)
        dist = np.minimum(dist, np.hypot(gx - (ax + k * dx), gy - (ay + k * dy)))
    cover = np.clip(LINE_WIDTH / 2 + 0.5 - dist, 0, 1)
    blend(frame, j0, i0, np.broadcast_to(np.array(color) / 255, cover.shape + (3,)), cover * alpha)


def sample(track, t):
    """LockTrack.sample: the tracked [t, x, y, r], linearly interpolated, clamped at both ends."""
    if t <= track[0][0]:
        return track[0]
    for a, b in zip(track, track[1:]):
        if t > b[0]:
            continue
        k = 1 if b[0] == a[0] else (t - a[0]) / (b[0] - a[0])
        return [t] + [a[n] + (b[n] - a[n]) * k for n in (1, 2, 3)]
    return track[-1]


def lock_state(lock, t):
    """LockTrack.at: (cx, cy, ring radius, ring alpha, run, text alpha, slide), or None off screen."""
    t_lock, out, out_s, close = lock["t_in"], lock["t_out"], lock.get("out_seconds", 0.9), lock.get("close_seconds", 0.7)
    if t < t_lock or t > out + out_s or not lock["track"]:
        return None
    p = sample(lock["track"], t)
    fade = 1 - smooth((t - out) / out_s)
    closed = smooth((t - t_lock) / close)
    radius = p[3] * (RING_MARGIN + CLOSE_TRAVEL * (1 - closed))
    flash = math.exp(-((t - t_lock - close) / 0.25) ** 2)
    ring_alpha = (0.6 + 0.35 * flash) * smooth((t - t_lock) / 0.35) * fade
    run = smooth((t - t_lock - close * 0.6) / 0.6)
    text_start = t_lock + close * 1.2
    text_alpha = smooth((t - text_start) / 0.45) * fade
    slide = 10 * (1 - smooth((t - text_start) / 0.6))
    return p[1], p[2], radius, ring_alpha, run, text_alpha, slide


@lru_cache(maxsize=None)
def font(name):
    return Font(name)


def text(frame, f, s, x, y, scale, color, alpha):
    """CinematicScreen.text: a black drop shadow, then the text."""
    if alpha <= 0:
        return
    f.draw(frame, s, x + 1.5 * scale, y + 1.5 * scale, scale, (0, 0, 0), alpha * 0.8)
    f.draw(frame, s, x, y, scale, color, alpha)


def draw_locks(frame, timeline, t, strings, alpha=1.0):
    """CinematicScreen.drawLocks at scale 1 onto frame (float RGB 0..1, 1080x1920) at shot time t."""
    for lock in (o for o in timeline.get("overlays", []) if o.get("kind") == "lock"):
        st = lock_state(lock, t)
        if st is None:
            continue
        cx, cy, rr, ring_alpha, run, text_alpha, slide = st
        line_alpha = ring_alpha * alpha
        arc = lock.get("arc_degrees", 16.0)
        for mid in range(45, 360, 90):
            pts = []
            for i in range(25):
                a = math.radians(mid + arc * (i / 24.0 - 0.5))
                pts.append((cx + rr * math.cos(a), cy - rr * math.sin(a)))
            strip(frame, pts, PLAYER, line_alpha)
        name_font, detail_font = font("orbitron24aabold.fnt"), font("orbitron12bold.fnt")
        title = strings[lock["title_key"]]
        name_w = name_font.measure(title) * 1.25
        detail_w = sum(detail_font.measure(strings[d["key"]]) * 1.15 for d in lock.get("detail", []))
        width = max(name_w, detail_w)
        ax, ay = cx + rr * math.cos(math.radians(45)), cy - rr * math.sin(math.radians(45))
        leader = lock.get("leader", 46.0)
        ex, ey = ax + leader, ay - leader
        if run > 0:
            k = min(1.0, run * 2)
            pts = [(ax + 4, ay - 4), (ax + 4 + (ex - ax - 4) * k, ay - 4 + (ey - ay + 4) * k)]
            if run > 0.5:
                pts.append((ex + (width + 6) * (run - 0.5) * 2, ey))
            strip(frame, pts, PLAYER, line_alpha)
        ta = text_alpha * alpha
        if ta <= 0:
            continue
        tx = ex + 2 - slide
        name_h = name_font.line_height * 1.25 + 2
        top = ey - 4 - name_h - detail_font.line_height * 1.15
        text(frame, name_font, title, tx, top, 1.25, PLAYER, ta)
        dx = tx
        for d in lock.get("detail", []):
            s = strings[d["key"]]
            text(frame, detail_font, s, dx, top + name_h, 1.15, COLORS.get(d.get("color"), PLAYER), 0.9 * ta)
            dx += detail_font.measure(s) * 1.15


def lock_window(timeline):
    """(first, end) seconds over which any lock is on screen in this shot, or None."""
    spans = [(o["t_in"], o["t_out"] + o.get("out_seconds", 0.9))
             for o in timeline.get("overlays", []) if o.get("kind") == "lock" and o["track"]]
    return (min(a for a, _b in spans), max(b for _a, b in spans)) if spans else None
