"""Pack the greybox PNG sequence with editorial burn-ins; sources remain untouched."""
import argparse
import subprocess
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

FFMPEG = Path('C:/Users/Dillon/AppData/Local/Microsoft/WinGet/Packages/Gyan.FFmpeg_Microsoft.Winget.Source_8wekyb3d8bbwe/ffmpeg-8.1.1-full_build/bin/ffmpeg.exe')
HERE = Path(__file__).resolve().parent
REPRESENTATIVES = (36, 144, 288, 432, 570, 648, 720, 744, 780)
SHOTS = (
    (72, 'ENTRY', 'Review entry glow and passenger space'),
    (216, 'REVEAL', 'Review window framing, canopy, river and storm horizon'),
    (360, 'MANAGED LAND', 'Review parallax, clearings and routes'),
    (504, 'PORT WINDOW', 'Review port frontage and window sightline'),
    (624, 'EXTERIOR', 'Review hull silhouette and four proposed underside lift modules'),
    (780, 'LANDING', 'Review support contact before retaining fixtures engage'),
)


def font(size):
    for path in ('C:/Windows/Fonts/consola.ttf', 'C:/Windows/Fonts/arial.ttf'):
        try:
            return ImageFont.truetype(path, size)
        except OSError:
            pass
    return ImageFont.load_default(size=size)


def shot_for(frame):
    return next((title, focus) for end, title, focus in SHOTS if frame <= end)


def phase_for(frame):
    if frame < 625:
        return ''
    for end, phase in ((648, 'HOLD'), (743, 'LOWER'), (750, 'CONTACT'),
                       (774, 'SECURE'), (780, 'WEIGHT ON CRADLE')):
        if frame <= end:
            return phase
    return ''


def annotated(path, frame):
    with Image.open(path) as source:
        if source.size != (1920, 1080):
            raise ValueError(f'{path.name}: expected 1920x1080, got {source.size}')
        image = source.convert('RGB')
    overlay = Image.new('RGBA', image.size)
    draw = ImageDraw.Draw(overlay)
    title, focus = shot_for(frame)
    phase = phase_for(frame)
    draw.rectangle((0, 0, 1919, 53), fill=(10, 13, 16, 220))
    draw.rectangle((0, 1011, 1919, 1079), fill=(10, 13, 16, 225))
    draw.text((21, 9), 'GREYBOX / ' + title + (' / ' + phase if phase else ''),
              font=font(31), fill=(234, 234, 225))
    # Timecode is source time at 12 fps, beginning at 00:00:00:00.
    t = frame - 1
    tc = f'{t//43200:02d}:{t//720%60:02d}:{t//12%60:02d}:{t%12:02d}'
    draw.text((21, 1015), f'{tc}  |  {focus}', font=font(25), fill=(234, 234, 225))
    note = 'PROPOSED: 150 m ship scale; four underside lift modules. Silent geometry / timing review.'
    if 217 <= frame <= 504:
        note += '  OPTIONAL CUE: Castle / existing Nessa memory [reserved; no audio].'
    # Scale long editorial notes to retain full-frame visibility at fixed bar height.
    note_font = font(21)
    while draw.textbbox((0, 0), note, font=note_font)[2] > 1878:
        size = getattr(note_font, 'size', 21) - 1
        if size < 14:
            break
        note_font = font(size)
    draw.text((21, 1048), note, font=note_font, fill=(190, 194, 190))
    image = Image.alpha_composite(image.convert('RGBA'), overlay).convert('RGB')
    return image


def make_contact_sheet(frames, output, preview=False):
    available = [n for n in REPRESENTATIVES if (frames / f'frame_{n:04d}.png').is_file()]
    if not available:
        raise FileNotFoundError(f'No representative frames found in {frames}; expected {REPRESENTATIVES}')
    if not preview and len(available) != len(REPRESENTATIVES):
        missing = sorted(set(REPRESENTATIVES) - set(available))
        raise FileNotFoundError(f'Missing representative frames: {missing}')
    # 1920x1080 review sheet preserves each 16:9 frame in a 3x3 grid.
    sheet = Image.new('RGB', (1920, 1080), (20, 24, 25))
    draw = ImageDraw.Draw(sheet)
    for i, frame in enumerate(REPRESENTATIVES):
        x, y = i % 3 * 640, i // 3 * 360
        path = frames / f'frame_{frame:04d}.png'
        if path.is_file():
            image = annotated(path, frame).resize((640, 360), Image.Resampling.LANCZOS)
            sheet.paste(image, (x, y))
        else:
            draw.text((x + 20, y + 160), f'Frame {frame:04d} unavailable', font=font(23), fill=(190, 190, 180))
    target = output.parent / 'jangala-greybox-contact-sheet.jpg'
    sheet.save(target, quality=94)
    print(f'Contact sheet: {target}')
    poster = frames / 'frame_0570.png'
    if poster.is_file():
        annotated(poster, 570).save(output.parent / 'poster.jpg', quality=95)
        print(f'Poster: {output.parent / "poster.jpg"}')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--frames', type=Path, default=HERE / 'build' / 'frames')
    parser.add_argument('--output', type=Path, default=HERE / 'review' / 'jangala-greybox-animatic.mp4')
    parser.add_argument('--preview', action='store_true', help='Make contact sheet from available representative frames only')
    args = parser.parse_args()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    if args.preview:
        make_contact_sheet(args.frames, args.output, preview=True)
        return
    paths = [args.frames / f'frame_{n:04d}.png' for n in range(1, 781)]
    missing = [p.name for p in paths if not p.is_file()]
    if missing:
        raise FileNotFoundError(f'Missing {len(missing)} of 780 source frames in {args.frames}: '
                                + ', '.join(missing[:15]) + (' ...' if len(missing) > 15 else ''))
    if not FFMPEG.is_file():
        raise FileNotFoundError(f'FFmpeg executable missing: {FFMPEG}')
    command = [str(FFMPEG), '-hide_banner', '-loglevel', 'error', '-y',
               '-f', 'rawvideo', '-pixel_format', 'rgb24', '-video_size', '1920x1080',
               '-framerate', '12', '-i', '-', '-an', '-vf', 'fps=24',
               '-c:v', 'libx264', '-crf', '20', '-pix_fmt', 'yuv420p',
               '-movflags', '+faststart', str(args.output)]
    process = subprocess.Popen(command, stdin=subprocess.PIPE)
    try:
        for frame, path in enumerate(paths, 1):
            process.stdin.write(annotated(path, frame).tobytes())
            if frame % 120 == 0:
                print(f'Packed {frame}/780 source frames', flush=True)
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
    make_contact_sheet(args.frames, args.output)
    print(f'Video: {args.output} (65 s, 24 fps, no audio)')


if __name__ == '__main__':
    try:
        main()
    except (FileNotFoundError, ValueError, RuntimeError) as exc:
        raise SystemExit(str(exc)) from exc
