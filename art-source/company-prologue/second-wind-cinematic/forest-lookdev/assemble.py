"""Assemble the 16-second forest material study from untouched Blender frames."""
import argparse
import os
from pathlib import Path
import subprocess
from functools import lru_cache

from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
DEFAULT_FFMPEG = 'C:/Users/Dillon/AppData/Local/Microsoft/WinGet/Packages/Gyan.FFmpeg_Microsoft.Winget.Source_8wekyb3d8bbwe/ffmpeg-8.1.1-full_build/bin/ffmpeg.exe'
FRAMES = tuple(range(120, 192)) + tuple(range(396, 468)) + tuple(range(801, 849))
CONTACT_FRAMES = (144, 432, 456, 816)


@lru_cache(maxsize=12)
def font(size):
    for path in ('C:/Windows/Fonts/consola.ttf', 'C:/Windows/Fonts/arial.ttf',
                 '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'):
        try:
            return ImageFont.truetype(path, size)
        except OSError:
            pass
    return ImageFont.load_default(size=size)


def frame_path(directory, frame):
    return directory / f'frame_{frame:04d}.png'


def annotated(path, frame):
    with Image.open(path) as source:
        if source.size != (1920, 1080):
            raise ValueError(f'{path}: expected 1920x1080, got {source.size}')
        result = source.convert('RGBA')
    layer = Image.new('RGBA', result.size)
    draw = ImageDraw.Draw(layer)
    if frame <= 191:
        title = 'HIGH WINDOW / 3D-derived canopy impostors'
    elif frame <= 467:
        title = 'LOW WINDOW / 3D trunks + layered foliage'
    else:
        title = 'INSPECTION CAMERA / foliage depth | LOOKDEV INSPECTION / additional camera'
    draw.rectangle((0, 0, 1919, 53), fill=(9, 14, 15, 220))
    draw.rectangle((0, 1011, 1919, 1079), fill=(9, 14, 15, 225))
    draw.text((21, 10), title, font=font(29), fill=(232, 235, 225))
    draw.text((21, 1018), 'FOLIAGE MATERIAL STUDY | original ship / window geometry retained | silent',
              font=font(24), fill=(232, 235, 225))
    # Source-time readout, not a claim that this edited study runs for 65 s.
    tick = frame - 1
    tc = f'{tick // 720:02d}:{tick // 12 % 60:02d}:{tick % 12:02d}'
    draw.text((21, 1051), f'SOURCE FRAME {frame:04d} / {tc} at 12 fps',
              font=font(18), fill=(184, 195, 188))
    return Image.alpha_composite(result, layer).convert('RGB')


def contact_sheet(directory, review, preview=False):
    paths = [(n, frame_path(directory, n)) for n in CONTACT_FRAMES]
    available = [(n, p) for n, p in paths if p.is_file()]
    if not available:
        raise FileNotFoundError(f'No contact frames found in {directory}; expected {CONTACT_FRAMES}')
    if not preview and len(available) != len(paths):
        raise FileNotFoundError('Missing contact frames: ' + ', '.join(str(n) for n, p in paths if not p.is_file()))
    sheet = Image.new('RGB', (1920, 1080), (17, 23, 22))
    draw = ImageDraw.Draw(sheet)
    for index, (frame, path) in enumerate(paths):
        x, y = index % 2 * 960, index // 2 * 540
        if path.is_file():
            image = annotated(path, frame).resize((960, 540), Image.Resampling.LANCZOS)
            sheet.paste(image, (x, y))
        else:
            draw.text((x + 25, y + 250), f'Source frame {frame:04d} unavailable',
                      font=font(24), fill=(180, 190, 181))
    sheet.save(review / 'contact-sheet.jpg', quality=94)
    poster = frame_path(directory, 432)
    if poster.is_file():
        annotated(poster, 432).save(review / 'poster.jpg', quality=95)
    print(f'Review sheet: {review / "contact-sheet.jpg"}')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--frames', type=Path, default=HERE / 'build' / 'frames')
    parser.add_argument('--output', type=Path, default=HERE / 'review' / 'jangala-forest-motion-study.mp4')
    parser.add_argument('--ffmpeg', default=os.environ.get('FFMPEG', DEFAULT_FFMPEG),
                        help='FFmpeg executable; defaults to FFMPEG environment variable or configured install')
    parser.add_argument('--preview', action='store_true', help='Compose available contact frames; do not encode video')
    args = parser.parse_args()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    if args.preview:
        contact_sheet(args.frames, args.output.parent, preview=True)
        return
    missing = [frame_path(args.frames, n).name for n in FRAMES if not frame_path(args.frames, n).is_file()]
    if missing:
        raise FileNotFoundError(f'Missing {len(missing)} of 192 required motion frames: '
                                + ', '.join(missing[:16]) + (' ...' if len(missing) > 16 else ''))
    command = [args.ffmpeg, '-hide_banner', '-loglevel', 'error', '-y',
               '-f', 'rawvideo', '-pixel_format', 'rgb24', '-video_size', '1920x1080',
               '-framerate', '12', '-i', '-', '-an', '-vf', 'fps=24',
               '-c:v', 'libx264', '-crf', '19', '-pix_fmt', 'yuv420p',
               '-movflags', '+faststart', str(args.output)]
    process = subprocess.Popen(command, stdin=subprocess.PIPE)
    try:
        for frame in FRAMES:
            process.stdin.write(annotated(frame_path(args.frames, frame), frame).tobytes())
        process.stdin.close()
        code = process.wait()
        if code:
            raise RuntimeError(f'FFmpeg failed with exit status {code}')
    except BaseException:
        if process.stdin and not process.stdin.closed:
            process.stdin.close()
        if process.poll() is None:
            process.terminate()
            process.wait()
        raise
    contact_sheet(args.frames, args.output.parent)
    print(f'Video: {args.output} (16 seconds, 24 fps, silent)')


if __name__ == '__main__':
    try:
        main()
    except (FileNotFoundError, ValueError, RuntimeError) as exc:
        raise SystemExit(str(exc)) from exc
