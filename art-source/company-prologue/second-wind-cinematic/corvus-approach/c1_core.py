"""Shared camera, light and element model for C1 (c1-layout.md).

Every module that contributes to the C1 composite imports its placement model from
here, so the ship, traffic and overlay passes cannot drift into separate cameras.
"""
import os
from pathlib import Path

import numpy as np
from PIL import Image, ImageEnhance

BASE = Path(__file__).resolve().parent
ROOT = BASE.parents[3]
GAME = Path(os.environ.get("STARSECTOR_DIR", r"C:\Program Files (x86)\Fractal Softworks\Starsector")) / "starsector-core" / "graphics"
W, H, FPS = 1920, 1080, 24
PUSH = (1.00, 1.07)          # camera scale over the shot
FOCUS = (1000.0, 520.0)      # push centre, between the fleet and Jangala
PLANET = (1290.0, 560.0)     # globe centre on the frame (interim disc and render target)
PLANET_DIAMETER = 760.0
TRUCK = 140.0                # pixels a depth-1.0 layer slides left over the shot
# Toward Corvus, in camera space (x right, y up, z toward the camera). render_planet.py's
# SUN_IN_CAMERA; every lit layer, painted or rendered, takes its key from this.
SUN_IN_CAMERA = (-0.62, 0.50, 0.50)


def latest(stem):
    """Highest-numbered accepted master, e.g. assets/station-v3.png."""
    found = [p for p in (BASE / "assets").glob(stem + "-v*.png") if p.stem.rsplit("-v", 1)[1].isdigit()]
    found.sort(key=lambda p: int(p.stem.rsplit("-v", 1)[1]))
    return found[-1] if found else None


def cutout(path, width):
    """A painted card cropped to its subject. ImageGen leaves a faint 1-8 alpha haze across
    the canvas; it would lift black space, so it is cleared before cropping."""
    im = Image.open(path).convert("RGBA")
    im.putalpha(im.getchannel("A").point(lambda a: 0 if a <= 8 else a))
    im = im.crop(im.getchannel("A").getbbox())
    return im.resize((round(width), round(width * im.height / im.width)), Image.LANCZOS)


def sprite(rel, height, heading=0.0, haze=0.0, dim=1.0):
    """Stand-in: a vanilla sprite (bow up) scaled to a height, turned to a fixed heading."""
    im = Image.open(GAME / rel).convert("RGBA")
    im = im.resize((max(1, round(im.width * height / im.height)), round(height)), Image.LANCZOS)
    if heading:
        im = im.rotate(heading, resample=Image.BICUBIC, expand=True)
    rgb, a = im.convert("RGB"), im.getchannel("A")
    rgb = ImageEnhance.Brightness(ImageEnhance.Color(rgb).enhance(1 - 0.35 * haze)).enhance(dim)
    if haze:
        rgb = Image.blend(rgb, Image.new("RGB", rgb.size, (70, 95, 130)), 0.25 * haze)
    rgb.putalpha(a)
    return rgb


def glow_dot(radius, color, strength=1.0):
    size = int(radius * 6) | 1
    yy, xx = np.mgrid[:size, :size] - size // 2
    d = np.sqrt(xx * xx + yy * yy) / radius
    a = np.clip(np.exp(-d * d * 1.6) * strength, 0, 1)
    arr = np.zeros((size, size, 4), np.uint8)
    arr[..., :3] = color
    arr[..., 3] = (a * 255).astype(np.uint8)
    return Image.fromarray(arr, "RGBA")


def smooth(u):
    u = min(1.0, max(0.0, u))
    return u * u * (3 - 2 * u)


class Element:
    """An image on one depth layer. Path keys are (t, cx, cy, scale), centre coordinates,
    eased between keys; with one key the element drifts at (vx, vy) px/s. A child sits
    at an offset in its parent's image pixels and follows the parent's scale.

    `path`, when given, replaces the keys: a function t -> (cx, cy, scale). `frames`, when
    given, replaces the fixed image: a function t -> RGBA image (a rendered sequence or an
    animation sheet). `alpha`, when given, is a function t -> opacity multiplier, and
    `blink` is (period, phase) for a light that shows for 18% of its period. `depth` may
    be a function of t; children always take their parent's."""

    def __init__(self, name, img, depth, keys, vx=0.0, vy=0.0, additive=False, blink=None, parent=None,
                 offset=(0, 0), path=None, frames=None, alpha=None):
        self.name, self.img, self.depth, self.keys = name, img, depth, keys
        self.vx, self.vy, self.additive, self.blink = vx, vy, additive, blink
        self.parent, self.offset = parent, offset
        self.path, self.frames, self.alpha = path, frames, alpha

    def depth_at(self, t):
        """Parallax rate; `depth` may be a function of t for a body that changes distance."""
        if self.parent is not None:
            return self.parent.depth_at(t)
        return self.depth(t) if callable(self.depth) else self.depth

    def image(self, t):
        return self.frames(t) if self.frames is not None else self.img

    def place(self, t):
        if self.parent is not None:
            px, py, ps = self.parent.place(t)
            pw, ph = self.parent.image(t).size
            ox, oy = self.offset(t) if callable(self.offset) else self.offset
            return px + (ox - pw / 2) * ps, py + (oy - ph / 2) * ps, ps
        if self.path is not None:
            return self.path(t)
        keys = self.keys
        if len(keys) == 1:
            _, x, y, s = keys[0]
            return x + self.vx * t, y + self.vy * t, s
        if t <= keys[0][0]:
            return keys[0][1:]
        for (t0, x0, y0, s0), (t1, x1, y1, s1) in zip(keys, keys[1:]):
            if t <= t1:
                u = smooth((t - t0) / (t1 - t0))
                return x0 + (x1 - x0) * u, y0 + (y1 - y0) * u, s0 * (s1 / s0) ** u
        return keys[-1][1:]
