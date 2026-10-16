"""Encode and verify the four-second managed-land motion proof; no art edits."""
import argparse
from fractions import Fraction
import hashlib
import json
from pathlib import Path
import subprocess

from PIL import Image, ImageChops, ImageDraw, ImageFont, ImageStat

HERE = Path(__file__).resolve().parent


def frame(path):
    with Image.open(path) as source:
        if source.size != (1920, 1080):
            raise ValueError(f'{path}: expected 1920x1080, got {source.size}')
        return source.convert('RGB')


def stills(paths, review):
    review.mkdir(exist_ok=True)
    for index, name in ((0, 'start'), (48, 'poster'), (95, 'end')):
        frame(paths[index]).save(review / f'{name}.jpg', quality=95)
    sheet = Image.new('RGB', (1920, 1080), (16, 21, 24))
    entries = [
        (HERE.parent / 'layered-next-scenes/review/shot-03-review.jpg', 'Original managed-land blockout'),
        (paths[48], 'Realism / midpoint'),
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
    sheet.save(review / 'contact-sheet.jpg', quality=94)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--ffmpeg', default='ffmpeg')
    parser.add_argument('--ffprobe', default='ffprobe')
    parser.add_argument('--preview', action='store_true')
    args = parser.parse_args()
    paths = [HERE / 'build/frames' / f'frame_{i:04d}.png' for i in range(1, 97)]
    review = HERE / 'review'
    stills(paths, review)
    if args.preview:
        return
    scene_hash = hashlib.sha256((HERE / 'managed-land-realism.blend').read_bytes()).hexdigest()
    scene_check = json.loads((review / 'scene-validation.json').read_text(encoding='utf-8'))
    if not scene_check['passed'] or scene_check['scene_sha256_after'] != scene_hash:
        raise ValueError('Validate the current saved scene before encoding its review.')
    hashes, changes, previous = [], [], None
    for path in paths:
        current = frame(path)
        hashes.append(hashlib.sha256(current.tobytes()).hexdigest())
        if previous is not None:
            changes.append(sum(ImageStat.Stat(ImageChops.difference(current, previous)).mean) / 3)
        previous = current
    if len(set(hashes)) != 96:
        raise ValueError('Expected 96 distinct moving source frames.')
    movie = review / 'jangala-managed-land-realism.mp4'
    subprocess.run([args.ffmpeg, '-hide_banner', '-loglevel', 'error', '-y',
                    '-framerate', '24', '-start_number', '1', '-i', str(paths[0].parent / 'frame_%04d.png'),
                    '-frames:v', '96', '-an', '-c:v', 'libx264', '-crf', '19',
                    '-pix_fmt', 'yuv420p', '-movflags', '+faststart', str(movie)], check=True)
    probe = json.loads(subprocess.check_output([args.ffprobe, '-v', 'error', '-count_frames',
                     '-show_streams', '-show_format', '-of', 'json', str(movie)], text=True))
    video = next(s for s in probe['streams'] if s['codec_type'] == 'video')
    assert (video['width'], video['height']) == (1920, 1080)
    assert Fraction(video['avg_frame_rate']) == 24
    assert int(video['nb_read_frames']) == 96
    assert abs(float(probe['format']['duration']) - 4) < .01
    assert video['codec_name'] == 'h264'
    assert not any(s['codec_type'] == 'audio' for s in probe['streams'])
    subprocess.run([args.ffmpeg, '-v', 'error', '-xerror', '-i', str(movie), '-f', 'null', '-'], check=True)
    report = {
        'passed': True, 'movie': movie.name, 'dimensions': [1920, 1080],
        'duration_seconds': 4, 'fps': 24, 'decoded_frames': 96, 'unique_source_frames': len(set(hashes)),
        'source_frame_mapping': '240 + 0.5*i at source 12fps, i=0..95',
        'scene_sha256': scene_hash,
        'audio': False, 'codec': video['codec_name'], 'pixel_format': video['pix_fmt'],
        'bytes': movie.stat().st_size, 'sha256': hashlib.sha256(movie.read_bytes()).hexdigest(),
        'full_decode': 'FFmpeg -xerror exit 0',
        'adjacent_mean_absolute_pixel_change_0_to_255': {'min': min(changes), 'max': max(changes),
                                                       'mean': sum(changes) / len(changes)},
        'limits': 'Media integrity and cadence only; visual quality and physical plausibility require rendered review.'
    }
    (review / 'media-validation.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
