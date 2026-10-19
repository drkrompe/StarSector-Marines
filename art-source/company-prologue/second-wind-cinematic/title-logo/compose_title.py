"""Put each title-logo direction over a C5 port frame, at 1920x1080, for review.

    python compose_title.py --bg <frame.png> [--out build/title-logo/review] [--width 0.5]

Reads every master in masters/ (transparent PNGs from tools/codex_imagegen.py, one per
prompts/<name>.txt), trims it to its visible pixels, scales it to --width of the frame,
and centres it a little above the middle of the shot (`title-logo.md`, "Where it sits").

Behind the logo goes what the brief allows for legibility over a bright, busy overhead
view: a soft darkening of the ground under the title (a wide blurred ellipse, at most
--scrim strength) and a soft shadow of the logo's own shape. Nothing else: no box, no
bar, no outline the art does not already have.

Writes NN-<name>.png per direction, 00-all.png (a contact sheet of every direction in
place) and NN-background.png (the bare frame) to --out. Renders are output: they are
never committed (CLAUDE.md, "Never commit rendered output").
"""
import argparse
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter

HERE = Path(__file__).resolve().parent
W, H = 1920, 1080


def trimmed(path):
    im = Image.open(path).convert('RGBA')
    alpha = im.getchannel('A')
    if alpha.getextrema()[0] == 255:
        raise SystemExit(f'{path.name}: no transparency; the master must come back with alpha')
    box = alpha.point(lambda a: 255 if a > 8 else 0).getbbox()
    return im.crop(box)


def scrim(strength, cx, cy, rx, ry):
    """A soft dark ellipse: the ground under the title, a little darker."""
    m = Image.new('L', (W, H), 0)
    ImageDraw.Draw(m).ellipse((cx - rx, cy - ry, cx + rx, cy + ry), fill=round(255 * strength))
    return m.filter(ImageFilter.GaussianBlur(min(rx, ry) * 0.6))


def compose(bg, logo, width, strength):
    frame = bg.copy()
    w = round(W * width)
    h = round(logo.height * w / logo.width)
    if h > H * 0.42:                      # a tall mark (patch beside words) is held by height
        h = round(H * 0.42)
        w = round(logo.width * h / logo.height)
    logo = logo.resize((w, h), Image.LANCZOS)
    x, y = (W - w) // 2, round(H * 0.46 - h / 2)

    dark = Image.new('RGB', (W, H), (14, 16, 18))
    frame = Image.composite(dark, frame, scrim(strength, W // 2, y + h // 2, w * 0.72, h * 1.05))

    shadow_a = Image.new('L', (W, H), 0)
    shadow_a.paste(logo.getchannel('A'), (x + 3, y + 5))
    shadow_a = shadow_a.filter(ImageFilter.GaussianBlur(9)).point(lambda a: round(a * 0.55))
    frame = Image.composite(dark, frame, shadow_a)

    frame = frame.convert('RGBA')
    frame.alpha_composite(logo, (x, y))
    return frame.convert('RGB')


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--bg', required=True, help='a 1920x1080 C5/C4 port frame')
    ap.add_argument('--out', default=str(HERE.parents[3] / 'build' / 'title-logo' / 'review'))
    ap.add_argument('--width', type=float, default=0.5, help='logo width as a share of the frame')
    ap.add_argument('--scrim', type=float, default=0.35, help='darkening under the title, 0-1')
    args = ap.parse_args()

    bg = Image.open(args.bg).convert('RGB')
    if bg.size != (W, H):
        raise SystemExit(f'background is {bg.size}; review frames are 1920x1080 at 100% scale')
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)

    masters = sorted((HERE / 'masters').glob('*.png'))
    frames = []
    for i, m in enumerate(masters, 1):
        f = compose(bg, trimmed(m), args.width, args.scrim)
        f.save(out / f'{i:02d}-{m.stem.split("-", 1)[1]}.png')
        frames.append(f)
        print('wrote', out / f'{i:02d}-{m.stem.split("-", 1)[1]}.png')
    bg.save(out / f'{len(masters) + 1:02d}-background.png')

    cols = 3 if len(frames) > 4 else 2
    rows = -(-len(frames) // cols)
    tw, th = W // cols, round(H / cols)
    sheet = Image.new('RGB', (W, H), (20, 20, 20))
    oy = (H - rows * th) // 2
    for i, f in enumerate(frames):
        sheet.paste(f.resize((tw, th), Image.LANCZOS), ((i % cols) * tw, oy + (i // cols) * th))
    sheet.save(out / '00-all.png')


if __name__ == '__main__':
    main()
