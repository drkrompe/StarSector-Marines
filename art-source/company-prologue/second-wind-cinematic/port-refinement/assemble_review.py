"""Encode the refined port and a forest/farm/port continuity cut; verify media."""
import argparse
from fractions import Fraction
import hashlib
import json
from pathlib import Path
import subprocess

from PIL import Image, ImageChops, ImageDraw, ImageFont, ImageStat

HERE = Path(__file__).resolve().parent
REVIEW = HERE / 'review'


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def inspect(path, probe, frames, seconds):
    info = json.loads(subprocess.check_output([
        probe, '-v', 'error', '-count_frames', '-show_streams', '-show_format',
        '-of', 'json', str(path)], text=True, encoding='utf-8'))
    video = next(s for s in info['streams'] if s['codec_type'] == 'video')
    if ((video['width'], video['height']) != (1920, 1080)
            or Fraction(video['avg_frame_rate']) != 24
            or int(video['nb_read_frames']) != frames
            or abs(float(info['format']['duration']) - seconds) > .01
            or video['codec_name'] != 'h264' or video['pix_fmt'] != 'yuv420p'
            or any(s['codec_type'] == 'audio' for s in info['streams'])):
        raise ValueError(f'Unexpected review format or cadence: {path}')
    return info


def frame(path):
    with Image.open(path) as source:
        if source.size != (1920, 1080):
            raise ValueError(f'{path}: expected 1920x1080, got {source.size}')
        return source.convert('RGB')


def make_stills(paths):
    for index, name in ((0, 'start'), (48, 'poster'), (95, 'end')):
        frame(paths[index]).save(REVIEW / f'{name}.jpg', quality=95)
    sheet = Image.new('RGB', (1920, 1080), (16, 21, 24))
    entries = [
        (HERE.parent / 'port-approach-realism/review/poster.jpg', 'Earlier port study'),
        (paths[48], 'Refined working port / midpoint'),
        (paths[0], 'Start'),
        (paths[95], 'End'),
    ]
    try:
        font = ImageFont.truetype('C:/Windows/Fonts/arial.ttf', 22)
    except OSError:
        font = ImageFont.load_default(size=22)
    for i, (path, label) in enumerate(entries):
        tile = frame(path).resize((960, 540), Image.Resampling.LANCZOS)
        draw = ImageDraw.Draw(tile)
        draw.rectangle((0, 504, 960, 540), fill=(16, 21, 24))
        draw.text((14, 510), label, font=font, fill=(235, 237, 230))
        sheet.paste(tile, ((i % 2) * 960, (i // 2) * 540))
    sheet.save(REVIEW / 'contact-sheet.jpg', quality=94)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--ffmpeg', default='ffmpeg')
    parser.add_argument('--ffprobe', default='ffprobe')
    args = parser.parse_args()
    REVIEW.mkdir(exist_ok=True)
    paths = [HERE / 'build/frames' / f'frame-{i:04d}.png' for i in range(96)]
    scene_hash = digest(HERE / 'port-refinement.blend')
    check = json.loads((REVIEW / 'scene-validation.json').read_text(encoding='utf-8'))
    render = json.loads((HERE / 'build/render-manifest.json').read_text(encoding='utf-8'))
    if (not check['passed'] or check['scene_sha256'] != scene_hash
            or render['scene_sha256'] != scene_hash or render['indices'] != list(range(96))):
        raise ValueError('Validate and render the current saved scene before encoding.')
    if any(digest(paths[row['index']]) != row['sha256'] for row in render['frames']):
        raise ValueError('A rendered frame changed after the recorded render.')
    hashes, changes, previous = [], [], None
    for path in paths:
        current = frame(path)
        hashes.append(hashlib.sha256(current.tobytes()).hexdigest())
        if previous is not None:
            changes.append(sum(ImageStat.Stat(ImageChops.difference(current, previous)).mean) / 3)
        previous = current
    if len(set(hashes)) != 96:
        raise ValueError('Expected 96 distinct moving source frames.')
    movie = REVIEW / 'jangala-port-refined.mp4'
    subprocess.run([args.ffmpeg, '-hide_banner', '-loglevel', 'error', '-y',
                    '-framerate', '24', '-start_number', '0', '-i', str(paths[0].parent / 'frame-%04d.png'),
                    '-frames:v', '96', '-an', '-c:v', 'libx264', '-crf', '19',
                    '-pix_fmt', 'yuv420p', '-movflags', '+faststart', str(movie)], check=True)
    inspect(movie, args.ffprobe, 96, 4)
    subprocess.run([args.ffmpeg, '-v', 'error', '-xerror', '-i', str(movie), '-f', 'null', '-'], check=True)
    make_stills(paths)
    report = {
        'passed': True, 'movie': movie.name, 'dimensions': [1920, 1080],
        'duration_seconds': 4, 'fps': 24, 'decoded_frames': 96, 'unique_source_frames': 96,
        'source_frame_mapping': '400 + 0.5*i at source 12fps, i=0..95',
        'scene_sha256': scene_hash, 'audio': False, 'codec': 'h264', 'pixel_format': 'yuv420p',
        'bytes': movie.stat().st_size, 'sha256': digest(movie),
        'full_decode': 'FFmpeg -xerror exit 0',
        'adjacent_mean_absolute_pixel_change_0_to_255': {
            'min': min(changes), 'max': max(changes), 'mean': sum(changes) / len(changes)},
        'limits': 'Media integrity and cadence only; visual quality requires rendered review.',
    }
    (REVIEW / 'media-validation.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    shots = [
        ('Forest reveal / Atlas lift', HERE.parent / 'forest-reveal-realism/review/jangala-forest-reveal-realism.mp4', 120),
        ('Managed land / Atlas passage', HERE.parent / 'managed-land-realism/review/jangala-managed-land-realism.mp4', 240),
        ('Refined working port', movie, 400),
    ]
    inputs, cuts = [], []
    for index, (title, path, start) in enumerate(shots):
        inspect(path, args.ffprobe, 96, 4)
        inputs.extend(['-i', str(path)])
        cuts.append({'title': title, 'movie': str(path.relative_to(HERE.parent)),
                     'sha256': digest(path), 'sequence_start_seconds': index * 4,
                     'source_frames': [start, start + 47.5]})
    sequence = REVIEW / 'jangala-approach-refined.mp4'
    graph = ';'.join(f'[{i}:v]setpts=PTS-STARTPTS[v{i}]' for i in range(3))
    graph += ';[v0][v1][v2]concat=n=3:v=1:a=0[out]'
    subprocess.run([args.ffmpeg, '-hide_banner', '-loglevel', 'error', '-y', *inputs,
                    '-filter_complex', graph, '-map', '[out]', '-an', '-c:v', 'libx264',
                    '-crf', '19', '-pix_fmt', 'yuv420p', '-movflags', '+faststart',
                    str(sequence)], check=True)
    inspect(sequence, args.ffprobe, 288, 12)
    subprocess.run([args.ffmpeg, '-v', 'error', '-xerror', '-i', str(sequence), '-f', 'null', '-'], check=True)
    sequence_report = {
        'passed': True, 'movie': sequence.name, 'sha256': digest(sequence),
        'bytes': sequence.stat().st_size, 'dimensions': [1920, 1080],
        'fps': 24, 'frames': 288, 'seconds': 12, 'audio': False,
        'full_decode': 'FFmpeg -xerror exit 0', 'cuts': cuts,
        'limits': 'Three excerpts with editorial time ellipses, re-encoded from H.264; not a continuous descent or complete cinematic.',
    }
    (REVIEW / 'sequence-validation.json').write_text(
        json.dumps(sequence_report, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'port': report, 'sequence': sequence_report}, indent=2))


if __name__ == '__main__':
    main()
