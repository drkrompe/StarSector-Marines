"""Pack the four-second painted-window test without movie burn-ins."""
import argparse
import os
from pathlib import Path
import subprocess

from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
DEFAULT_FFMPEG = 'ffmpeg'


def font(size):
    for name in ('C:/Windows/Fonts/consola.ttf', 'C:/Windows/Fonts/arial.ttf',
                 '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            pass
    return ImageFont.load_default(size=size)


def read_frame(path):
    with Image.open(path) as image:
        if image.size != (1920, 1080):
            raise ValueError(f'{path}: expected 1920x1080, got {image.size}')
        return image.convert('RGB')


def stills(frames, review):
    entries = ((HERE / 'guides' / 'window-guide.png', 'Previous 3D foliage'),
               (frames / 'frame_0048.png', 'Painted projection / midpoint'),
               (frames / 'frame_0000.png', 'Start'),
               (frames / 'frame_0095.png', 'End'))
    missing = [str(path) for path, _ in entries if not path.is_file()]
    if missing:
        raise FileNotFoundError('Missing review stills: ' + ', '.join(missing))
    sheet = Image.new('RGB', (1920, 1080), (16, 22, 22))
    label_font = font(22)
    for index, (path, title) in enumerate(entries):
        image = read_frame(path).resize((960, 540), Image.Resampling.LANCZOS)
        draw = ImageDraw.Draw(image, 'RGBA')
        draw.rectangle((0, 0, 959, 35), fill=(9, 14, 15, 225))
        draw.text((13, 5), title, font=label_font, fill=(235, 235, 224, 255))
        sheet.paste(image, (index % 2 * 960, index // 2 * 540))
    sheet.save(review / 'contact-sheet.jpg', quality=94)
    read_frame(frames / 'frame_0048.png').save(review / 'poster.jpg', quality=95)
    print(f'Review stills: {review}')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--frames', type=Path, default=HERE / 'build' / 'frames')
    parser.add_argument('--output', type=Path, default=HERE / 'review' / 'jangala-painted-window-test.mp4')
    parser.add_argument('--ffmpeg', default=os.environ.get('FFMPEG', DEFAULT_FFMPEG))
    parser.add_argument('--preview', action='store_true', help='Create review stills only')
    args = parser.parse_args()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    if args.preview:
        stills(args.frames, args.output.parent)
        return
    paths = [args.frames / f'frame_{frame:04d}.png' for frame in range(96)]
    missing = [path.name for path in paths if not path.is_file()]
    if missing:
        raise FileNotFoundError(f'Missing {len(missing)} of 96 required frames: '
                                + ', '.join(missing[:16]) + (' ...' if len(missing) > 16 else ''))
    # Check dimensions before starting the encoder, preserving clean PNG sources.
    for path in paths:
        with Image.open(path) as image:
            if image.size != (1920, 1080):
                raise ValueError(f'{path}: expected 1920x1080, got {image.size}')
    command = [args.ffmpeg, '-hide_banner', '-loglevel', 'error', '-y',
               '-framerate', '24', '-start_number', '0', '-i', str(args.frames / 'frame_%04d.png'),
               '-frames:v', '96', '-an', '-c:v', 'libx264', '-crf', '19',
               '-pix_fmt', 'yuv420p', '-movflags', '+faststart', str(args.output)]
    result = subprocess.run(command, check=False)
    if result.returncode:
        raise RuntimeError(f'FFmpeg failed with exit status {result.returncode}')
    stills(args.frames, args.output.parent)
    print(f'Video: {args.output} (4 seconds, 1920x1080, 24 fps, silent)')


if __name__ == '__main__':
    try:
        main()
    except (FileNotFoundError, ValueError, RuntimeError) as exc:
        raise SystemExit(str(exc)) from exc
