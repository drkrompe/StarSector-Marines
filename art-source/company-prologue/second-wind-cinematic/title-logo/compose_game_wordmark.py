"""The game's own "starsector" wordmark, as it ships, with MARINES set beneath it, over the port.

    python compose_game_wordmark.py --bg <frame.png> [--out build/title-logo/review-wordmark]

The owner's third round (`title-logo.md`, "The game's own wordmark"). The wordmark is read
straight from the installed game (`graphics/ui/starsector_title_alpha.png`, 450x50) and is
never copied into the repository; the mod would draw it from the player's install the same
way. It is used as is: no redrawing and no AI upscaling, only plain resampling when a layout
shows it larger than its native size, which softens it.

MARINES is set in Windows' Bahnschrift, in two treatments:
  match  thin, wide-tracked, cyan with the wordmark's blue glow, so the two read as one mark;
  ours   warm off-white, bold and condensed with a dark edge: the mod's own voice under the
         game's name.

Writes NN-<layout>.png per layout and 00-all.png to --out. Renders are output: never committed.
"""
import argparse
import os
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE = Path(__file__).resolve().parent


def game_core():
    """The install's game-data folder: $STARSECTOR_DIR if set, else the platform's default install.
    Windows keeps it in `starsector-core`, the macOS app bundle in `Contents/Resources/Java`."""
    root = Path(os.environ.get('STARSECTOR_DIR') or (
        '/Applications/Starsector.app' if sys.platform == 'darwin'
        else r'C:\Program Files (x86)\Fractal Softworks\Starsector'))
    mac = root / 'Contents' / 'Resources' / 'Java'
    return mac if mac.is_dir() else root / 'starsector-core'


def system_font(*names):
    """The first of `names` found in $TITLE_FONT_DIR, the Windows fonts folder or the macOS ones.
    Bahnschrift and Segoe UI ship with Windows only; on a Mac, install them (or point
    $TITLE_FONT_DIR at a folder holding them) to render the title as it is on Windows."""
    dirs = [os.environ.get('TITLE_FONT_DIR'), r'C:\Windows\Fonts', Path.home() / 'Library' / 'Fonts',
            '/Library/Fonts', '/System/Library/Fonts/Supplemental']
    for d in filter(None, dirs):
        for n in names:
            if (Path(d) / n).is_file():
                return Path(d) / n
    raise FileNotFoundError(f'none of {names} in {[str(d) for d in dirs if d]}')


WORDMARK = game_core() / 'graphics' / 'ui' / 'starsector_title_alpha.png'
W, H = 1920, 1080

CORE, GLOW = (92, 255, 255), (0, 72, 255)       # the wordmark's own core and halo colours
WARM, EDGE = (238, 230, 214), (24, 26, 28)

# (file name, wordmark scale, MARINES treatment)
# At its native 450 px the wordmark is under a quarter of the screen and MARINES beneath it
# cannot be read, so the cinematic layouts enlarge it; the native-size one is the control
# for how much softness the enlargement costs.
LAYOUTS = [
    ('match', 2.4, 'match'),
    ('ours', 2.4, 'ours'),
    ('native-match', 1.0, 'match'),
]


def wordmark(scale):
    im = Image.open(WORDMARK).convert('RGBA')
    im = im.crop(im.getchannel('A').point(lambda a: 255 if a > 8 else 0).getbbox())
    if scale != 1.0:
        im = im.resize((round(im.width * scale), round(im.height * scale)), Image.LANCZOS)
    return im


def tracked(text, font, tracking):
    """Text as an alpha mask, each letter spaced `tracking` px beyond its own advance."""
    widths = [font.getlength(c) for c in text]
    w = round(sum(widths) + tracking * (len(text) - 1))
    asc, desc = font.getmetrics()
    mask = Image.new('L', (w + 8, asc + desc + 8), 0)
    d = ImageDraw.Draw(mask)
    x = 4
    for c, cw in zip(text, widths):
        d.text((x, 4), c, font=font, fill=255)
        x += cw + tracking
    return mask.crop(mask.getbbox())


def marines(treatment, mark, text='MARINES', width=None):
    """The line under the wordmark. `width` is its share of the wordmark's width."""
    font = ImageFont.truetype(str(system_font('bahnschrift.ttf')), 64)
    if treatment == 'match':
        font.set_variation_by_name('Light')
        cap = mark.height * 0.5
    else:
        font.set_variation_by_name('Bold SemiCondensed')
        cap = mark.height * 0.62
    probe = tracked(text, font, 0)
    font = ImageFont.truetype(str(system_font('bahnschrift.ttf')), round(64 * cap / probe.height))
    font.set_variation_by_name('Light' if treatment == 'match' else 'Bold SemiCondensed')
    base = tracked(text, font, 0)
    target = mark.width * (width or (0.7 if treatment == 'match' else 0.46))
    mask = tracked(text, font, max(0.0, (target - base.width) / max(1, len(text) - 1)))

    pad = round(mask.height * 0.8)
    m = Image.new('L', (mask.width + 2 * pad, mask.height + 2 * pad), 0)
    m.paste(mask, (pad, pad))
    out = Image.new('RGBA', m.size, (0, 0, 0, 0))
    if treatment == 'match':
        halo = m.filter(ImageFilter.GaussianBlur(max(1.5, mask.height * 0.18)))
        out.paste(Image.new('RGBA', m.size, GLOW + (255,)), (0, 0), halo.point(lambda a: min(255, a * 2)))
        core = Image.new('RGBA', m.size, CORE + (255,))
        core.putalpha(m)
        out.alpha_composite(core)
    else:
        edge = m.filter(ImageFilter.MaxFilter(3)).filter(ImageFilter.GaussianBlur(1))
        out.paste(Image.new('RGBA', m.size, EDGE + (255,)), (0, 0), edge)
        face = Image.new('RGBA', m.size, WARM + (255,))
        face.putalpha(m)
        out.alpha_composite(face)
    return out.crop(out.getchannel('A').getbbox())


def lockup(scale, treatment):
    mark = wordmark(scale)
    sub = marines(treatment, mark)
    gap = round(mark.height * 0.35)
    w = max(mark.width, sub.width)
    im = Image.new('RGBA', (w, mark.height + gap + sub.height), (0, 0, 0, 0))
    im.alpha_composite(mark, ((w - mark.width) // 2, 0))
    im.alpha_composite(sub, ((w - sub.width) // 2, mark.height + gap))
    return im


def scrim(cx, cy, rx, ry, strength):
    m = Image.new('L', (W, H), 0)
    ImageDraw.Draw(m).ellipse((cx - rx, cy - ry, cx + rx, cy + ry), fill=round(255 * strength))
    return m.filter(ImageFilter.GaussianBlur(min(rx, ry) * 0.6))


def place(bg, logo, strength):
    x, y = (W - logo.width) // 2, round(H * 0.46 - logo.height / 2)
    dark = Image.new('RGB', (W, H), (10, 12, 16))
    frame = Image.composite(dark, bg, scrim(W // 2, y + logo.height // 2,
                                            logo.width * 0.75, logo.height * 1.4, strength))
    frame = frame.convert('RGBA')
    frame.alpha_composite(logo, (x, y))
    return frame.convert('RGB')


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--bg', required=True, help='a 1920x1080 C5/C4 port frame')
    ap.add_argument('--out', default=str(HERE.parents[3] / 'build' / 'title-logo' / 'review-wordmark'))
    ap.add_argument('--scrim', type=float, default=0.6, help='darkening under the title, 0-1')
    args = ap.parse_args()

    bg = Image.open(args.bg).convert('RGB')
    if bg.size != (W, H):
        raise SystemExit(f'background is {bg.size}; review frames are 1920x1080 at 100% scale')
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)

    frames = []
    for i, (name, scale, treatment) in enumerate(LAYOUTS, 1):
        f = place(bg, lockup(scale, treatment), args.scrim)
        f.save(out / f'{i:02d}-{name}.png')
        frames.append(f)
        print('wrote', out / f'{i:02d}-{name}.png')

    sheet = Image.new('RGB', (W, H), (20, 20, 20))
    for i, f in enumerate(frames):
        sheet.paste(f.resize((W // 2, H // 2), Image.LANCZOS), ((i % 2) * W // 2, (i // 2) * H // 2))
    sheet.paste(bg.resize((W // 2, H // 2), Image.LANCZOS), (W // 2, H // 2))
    sheet.save(out / '00-all.png')


if __name__ == '__main__':
    main()
