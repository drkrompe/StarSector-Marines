"""C1 UI pass: palette, fonts and antialiased drawing primitives for c1_overlay.py.

Colours are Starsector's own UI values (read from the install's `player.faction` and
`hegemony.faction`, never copied): the player's base UI cyan for the company, Hegemony
orange for Approach, the game's standard text grey-white for dialogue.

Fonts come in two roles. Display text (the planet name, speaker tags) uses the first
`ui_fonts/*.ttf`: Orbitron, the open face behind Starsector's headings (SIL OFL 1.1,
`ui_fonts/OFL.txt`). Caption text stays in a plain face, Windows' Bahnschrift or Segoe
UI, because Orbitron is a display face and tires the eye at sentence length. Pillow's
bundled default is the last resort.
Nothing here ships: the frames are an animatic, and runtime captions are drawn by the
game with its own bitmap fonts (ui-overlay.md).
"""
from functools import lru_cache
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE = Path(__file__).resolve().parent

UI_CYAN = (170, 222, 255)        # player.faction baseUIColor
UI_DARK = (31, 94, 112)          # player.faction darkUIColor
HEGEMONY = (245, 150, 30)        # hegemony.faction color
TEXT = (226, 228, 222)           # settings.json standardTextColor, warmed slightly
SHADOW = (0, 4, 8)

_SYSTEM = [Path(r"C:\Windows\Fonts\bahnschrift.ttf"), Path(r"C:\Windows\Fonts\segoeui.ttf")]


def font_path(display=False):
    local = sorted((HERE / "ui_fonts").glob("*.ttf")) if (HERE / "ui_fonts").is_dir() else []
    for p in (local if display else []) + _SYSTEM:
        if p.exists():
            return p
    return None


@lru_cache(maxsize=None)
def font(size, weight="Regular", display=False):
    """A TrueType face at a pixel size. `weight` names a variable-font instance where the
    face has them (Bahnschrift: 'SemiBold', 'SemiLight Condensed', ...); `display` picks
    the heading face rather than the caption face."""
    path = font_path(display)
    if path is None:
        return ImageFont.load_default(size)
    f = ImageFont.truetype(str(path), size)
    try:
        names = [n.decode() if isinstance(n, bytes) else n for n in f.get_variation_names()]
        if weight in names:
            f.set_variation_by_name(weight)
    except (OSError, AttributeError):
        pass                                   # static face: one weight only
    return f


def text_width(text, f, tracking=0.0):
    if not tracking:
        return f.getlength(text)
    return sum(f.getlength(c) for c in text) + tracking * max(0, len(text) - 1)


def draw_text(draw, xy, text, f, fill, tracking=0.0):
    """Text with optional letter spacing; returns the advance."""
    x, y = xy
    if not tracking:
        draw.text((x, y), text, font=f, fill=fill)
        return f.getlength(text)
    for c in text:
        draw.text((x, y), c, font=f, fill=fill)
        x += f.getlength(c) + tracking
    return x - xy[0] - tracking


def with_shadow(patch, radius=3.0, strength=0.85, pad=0):
    """Put a soft dark halo under an RGBA patch so text holds over bright hull."""
    a = patch.getchannel("A").filter(ImageFilter.GaussianBlur(radius))
    a = a.point(lambda v: min(255, int(v * strength * 1.6)))
    out = Image.new("RGBA", patch.size, SHADOW + (0,))
    out.putalpha(a)
    return Image.alpha_composite(out, patch)


def blend(frame, patch, x, y, opacity=1.0):
    """Composite an RGBA patch over a float32 HxWx3 frame at a sub-pixel position."""
    if opacity <= 0.002:
        return
    ix, iy = int(np.floor(x)), int(np.floor(y))
    fx, fy = x - ix, y - iy
    if fx or fy:
        w, h = patch.size
        patch = patch.transform((w + 1, h + 1), Image.AFFINE, (1, 0, -fx, 0, 1, -fy),
                                resample=Image.BILINEAR, fillcolor=(0, 0, 0, 0))
    arr = np.asarray(patch, np.float32) / 255
    H, W = frame.shape[:2]
    x0, y0 = max(0, ix), max(0, iy)
    x1, y1 = min(W, ix + arr.shape[1]), min(H, iy + arr.shape[0])
    if x1 <= x0 or y1 <= y0:
        return
    sub = arr[y0 - iy:y1 - iy, x0 - ix:x1 - ix]
    a = sub[..., 3:4] * opacity
    region = frame[y0:y1, x0:x1]
    frame[y0:y1, x0:x1] = region * (1 - a) + sub[..., :3] * a


def lines_patch(segments, color, width=1.5, ss=4, pad=4):
    """Antialiased polylines: drawn at `ss`x and box-filtered down. Returns (patch, x0, y0)
    with the patch's top-left on whole frame pixels, so the drawing keeps its sub-pixel
    placement without a resample."""
    xs = [p[0] for seg in segments for p in seg]
    ys = [p[1] for seg in segments for p in seg]
    x0, y0 = int(np.floor(min(xs))) - pad, int(np.floor(min(ys))) - pad
    w, h = int(np.ceil(max(xs))) + pad - x0, int(np.ceil(max(ys))) + pad - y0
    big = Image.new("L", (w * ss, h * ss), 0)
    d = ImageDraw.Draw(big)
    for seg in segments:
        pts = [((px - x0) * ss, (py - y0) * ss) for px, py in seg]
        d.line(pts, fill=255, width=max(1, round(width * ss)), joint="curve")
    a = big.reduce(ss)
    patch = Image.new("RGBA", (w, h), color + (0,))
    patch.putalpha(a)
    return patch, x0, y0


def soft_panel(w, h, color=(0, 0, 0), alpha=0.5, feather=28):
    """A feathered dark field behind a text block: no hard box edge on the picture."""
    pad = feather * 2
    m = Image.new("L", (w + 2 * pad, h + 2 * pad), 0)
    ImageDraw.Draw(m).rounded_rectangle((pad, pad, pad + w, pad + h), radius=feather, fill=int(255 * alpha))
    m = m.filter(ImageFilter.GaussianBlur(feather * 0.8))
    p = Image.new("RGBA", m.size, color + (0,))
    p.putalpha(m)
    return p, pad
