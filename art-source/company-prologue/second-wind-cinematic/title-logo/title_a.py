"""Direction A, developed: the game's own wordmark, sharpened, with MARINES in warm white.

    python title_a.py --bg <frame.png> [--out build/title-logo/a] [--no-video]

The owner chose A (`title-logo.md`, "Direction A"). Writes, at 1920x1080:
  01-port.png         the title in place over the port, as the cinematic ends on it
  02-sharpening.png   close-up: the wordmark plainly enlarged against the sharpened one
  03-menu.png         the title alone on a plain dark screen, for menus and loading
  04-reveal-steps.png the reveal at labelled moments
  05-reveal.mp4       the reveal as video (needs ffmpeg on PATH)

The wordmark is read from the installed game and never copied into the repository.
Sharpening happens here, locally, from the shipped pixels only: the bright letter body is
enlarged smoothly, rounded and cut to a clean edge, and filled with the wordmark's own
colours enlarged the same way; the dark blue glow stays soft, as it is in the original.
Renders are output: never committed.
"""
import argparse
import shutil
import subprocess
import tempfile
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

from compose_game_wordmark import WORDMARK, marines, scrim

HERE = Path(__file__).resolve().parent
W, H = 1920, 1080
SCALE = 2.4                 # the wordmark at about 53% of the screen width
SUPER = 4                   # worked at four times the final size, then reduced
GLOW = (0.0, 72 / 255, 1.0)  # the wordmark's own dark-blue halo
FPS = 30

# The reveal, in seconds from the moment Castle's last line ends (`title-logo.md`).
STARSECTOR_IN = (1.0, 3.0)
MARINES_IN = (2.6, 3.8)
FADE_OUT = (7.5, 9.0)
LENGTH = 9.5


def _blur(x, r):
    im = Image.fromarray((np.clip(x, 0, 1) * 255).astype(np.uint8))
    return np.asarray(im.filter(ImageFilter.GaussianBlur(r))).astype(np.float32) / 255


def _enlarge(arr, w, h):
    return np.stack([np.asarray(Image.fromarray(arr[..., i]).resize((w, h), Image.BICUBIC))
                     for i in range(arr.shape[2])], -1)


def wordmark_sharp(scale=SCALE):
    src = Image.open(WORDMARK).convert('RGBA')
    src = src.crop(src.getchannel('A').point(lambda a: 255 if a > 8 else 0).getbbox())
    a = np.asarray(src).astype(np.float32) / 255
    w, h = src.size
    bw, bh = round(w * scale * SUPER), round(h * scale * SUPER)

    pre = a.copy()
    pre[..., :3] *= pre[..., 3:4]                      # enlarge colour premultiplied, so
    big = _enlarge(pre, bw, bh)                        # no black creeps in at the edges
    alpha = np.clip(big[..., 3], 1e-4, 1)
    colour = np.clip(big[..., :3] / alpha[..., None], 0, 1)

    lum = (0.3 * a[..., 0] + 0.59 * a[..., 1] + 0.11 * a[..., 2]) * a[..., 3]
    body = _enlarge((lum > 0.45).astype(np.float32)[..., None], bw, bh)[..., 0]
    body = (_blur(body, SUPER * 2.0) > 0.5).astype(np.float32)   # rounded, clean edge

    halo = _blur(np.clip(big[..., 3], 0, 1), SUPER * 1.5)
    out = np.zeros((bh, bw, 4), np.float32)
    out[..., :3] = np.array(GLOW) * halo[..., None]
    out[..., 3] = halo * 0.9
    out[..., :3] = colour * body[..., None] + out[..., :3] * (1 - body[..., None])
    out[..., 3] = body + out[..., 3] * (1 - body)
    im = Image.fromarray((np.clip(out, 0, 1) * 255).astype(np.uint8), 'RGBA')
    return im.resize((round(w * scale), round(h * scale)), Image.LANCZOS)


def wordmark_plain(scale=SCALE):
    src = Image.open(WORDMARK).convert('RGBA')
    src = src.crop(src.getchannel('A').point(lambda a: 255 if a > 8 else 0).getbbox())
    return src.resize((round(src.width * scale), round(src.height * scale)), Image.LANCZOS)


def layout(mark):
    """Wordmark and MARINES with their places in a 1920x1080 frame."""
    sub = marines('ours', mark)
    gap = round(mark.height * 0.35)
    total = mark.height + gap + sub.height
    top = round(H * 0.46 - total / 2)
    return (mark, ((W - mark.width) // 2, top)), (sub, ((W - sub.width) // 2, top + mark.height + gap))


def fade(layer, k):
    if k >= 1:
        return layer
    out = layer.copy()
    out.putalpha(layer.getchannel('A').point(lambda a: round(a * k)))
    return out


def frame(bg, parts, k_top, k_sub, k_scrim, strength=0.6):
    (mark, (mx, my)), (sub, (sx, sy)) = parts
    bottom = sy + sub.height
    dark = Image.new('RGB', (W, H), (10, 12, 16))
    s = scrim(W // 2, (my + bottom) // 2, mark.width * 0.75, (bottom - my) * 0.95, strength * k_scrim)
    f = Image.composite(dark, bg, s).convert('RGBA')
    f.alpha_composite(fade(mark, k_top), (mx, my))
    f.alpha_composite(fade(sub, k_sub), (sx, sy))
    return f.convert('RGB')


def ramp(t, span):
    a, b = span
    x = min(1.0, max(0.0, (t - a) / (b - a)))
    return x * x * (3 - 2 * x)                     # ease in and out


def label(im, text, size=40):
    font = ImageFont.truetype(r'C:\Windows\Fonts\segoeuib.ttf', size)
    d = ImageDraw.Draw(im)
    b = d.textbbox((0, 0), text, font=font)
    p = size // 2
    d.rectangle((0, 0, b[2] + 2 * p, b[3] + p + p // 2), fill=(15, 15, 15))
    d.text((p, p // 2), text, font=font, fill=(255, 255, 255))
    return im


def reveal(bg, parts, t):
    """One moment of the reveal; the camera still settles, so the port eases out 3% to rest."""
    z = 1 + 0.03 * (1 - ramp(t, (0, FADE_OUT[0])))
    zw, zh = round(W * z), round(H * z)
    moving = bg.resize((zw, zh), Image.LANCZOS).crop(((zw - W) // 2, (zh - H) // 2,
                                                      (zw - W) // 2 + W, (zh - H) // 2 + H))
    f = frame(moving, parts, ramp(t, STARSECTOR_IN), ramp(t, MARINES_IN), ramp(t, STARSECTOR_IN))
    out = 1 - ramp(t, FADE_OUT)
    return Image.blend(Image.new('RGB', (W, H)), f, out) if out < 1 else f


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--bg', required=True, help='a 1920x1080 C5/C4 port frame')
    ap.add_argument('--out', default=str(HERE.parents[3] / 'build' / 'title-logo' / 'a'))
    ap.add_argument('--no-video', action='store_true')
    args = ap.parse_args()

    bg = Image.open(args.bg).convert('RGB')
    if bg.size != (W, H):
        raise SystemExit(f'background is {bg.size}; review frames are 1920x1080 at 100% scale')
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)

    sharp = wordmark_sharp()
    parts = layout(sharp)
    frame(bg, parts, 1, 1, 1).save(out / '01-port.png')

    # Close-up of the left half of the wordmark, shown at 3x so the edges can be judged.
    plain = wordmark_plain()
    crop = (0, 0, sharp.width // 2, sharp.height)
    cmp = Image.new('RGB', (W, H), (8, 9, 12))
    for i, (im, name) in enumerate(((plain, 'Enlarged only (round three)'), (sharp, 'Sharpened (now)'))):
        tile = Image.new('RGBA', (crop[2], crop[3]), (8, 9, 12, 255))
        tile.alpha_composite(im.crop(crop))
        tile = tile.resize((tile.width * 3 // 2, tile.height * 3 // 2), Image.NEAREST).convert('RGB')
        y = 140 + i * (tile.height + 160)
        cmp.paste(tile, ((W - tile.width) // 2, y))
        ImageDraw.Draw(cmp).text(((W - tile.width) // 2, y - 60), name, fill=(230, 230, 230),
                                 font=ImageFont.truetype(r'C:\Windows\Fonts\segoeuib.ttf', 40))
    cmp.save(out / '02-sharpening.png')

    menu = Image.new('RGB', (W, H), (12, 14, 18))
    menu = Image.composite(Image.new('RGB', (W, H), (26, 30, 38)), menu,
                           scrim(W // 2, H // 2, W * 0.55, H * 0.55, 1.0))
    frame(menu, parts, 1, 1, 0).save(out / '03-menu.png')

    steps = [0.5, 2.0, 3.2, 5.0]
    sheet = Image.new('RGB', (W, H), (15, 15, 15))
    tw, th = W // 2, H // 2
    for i, t in enumerate(steps):
        tile = label(reveal(bg, parts, t).resize((tw, th), Image.LANCZOS), f'{t:.1f} s', 30)
        sheet.paste(tile, ((i % 2) * tw, (i // 2) * th))
    sheet.save(out / '04-reveal-steps.png')

    if args.no_video:
        return
    ffmpeg = shutil.which('ffmpeg')
    if not ffmpeg:
        raise SystemExit('ffmpeg not on PATH; rerun with --no-video')
    with tempfile.TemporaryDirectory() as tmp:
        for n in range(round(LENGTH * FPS)):
            reveal(bg, parts, n / FPS).save(Path(tmp) / f'{n:04d}.png')
        subprocess.run([ffmpeg, '-y', '-loglevel', 'error', '-framerate', str(FPS),
                        '-i', str(Path(tmp) / '%04d.png'), '-c:v', 'libx264', '-pix_fmt', 'yuv420p',
                        '-crf', '16', str(out / '05-reveal.mp4')], check=True)
    print('wrote', out)


if __name__ == '__main__':
    main()
