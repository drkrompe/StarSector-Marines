"""Validate the complete parallax movie, rendered frames, and untouched layer PNGs."""
import argparse
from fractions import Fraction
import hashlib
import json
import os
from pathlib import Path
import subprocess

from PIL import Image, ImageChops, ImageStat

HERE = Path(__file__).resolve().parent


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def probe_movie(path, ffmpeg, ffprobe):
    probe = json.loads(subprocess.check_output([
        ffprobe, '-v', 'error', '-count_frames', '-show_streams',
        '-show_format', '-of', 'json', str(path)], text=True))
    video = next(s for s in probe['streams'] if s['codec_type'] == 'video')
    assert (video['width'], video['height']) == (1920, 1080)
    assert Fraction(video['avg_frame_rate']) == 24
    assert int(video['nb_read_frames']) == 96
    assert abs(float(probe['format']['duration']) - 4) < .01
    assert not any(s['codec_type'] == 'audio' for s in probe['streams'])
    assert video['codec_name'] == 'h264'
    subprocess.run([ffmpeg, '-v', 'error', '-xerror', '-i', str(path),
                    '-f', 'null', '-'], check=True)
    return {'path': os.path.relpath(path,HERE).replace(os.sep,'/'), 'size': [video['width'], video['height']],
            'fps': 24, 'decoded_frames': 96, 'duration_seconds': 4,
            'audio': False, 'codec': video['codec_name'],
            'pixel_format': video['pix_fmt'], 'bytes': path.stat().st_size,
            'sha256': sha(path), 'full_decode': 'FFmpeg -xerror exit 0'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--ffmpeg', default=os.environ.get('FFMPEG', 'ffmpeg'))
    parser.add_argument('--ffprobe', default=os.environ.get('FFPROBE', 'ffprobe'))
    parser.add_argument('--movie', type=Path, default=HERE / 'review/jangala-parallax-window-test.mp4')
    parser.add_argument('--frames', type=Path, default=HERE / 'build/frames')
    parser.add_argument('--compare', type=Path, default=HERE.parent / 'painted-window/review/jangala-painted-window-test.mp4')
    args = parser.parse_args()
    report = {'movie': probe_movie(args.movie, args.ffmpeg, args.ffprobe)}
    hashes, changes, previous = [], [], None
    for index in range(96):
        path = args.frames / f'frame_{index:04d}.png'
        with Image.open(path) as source:
            assert source.size == (1920, 1080), str(path)
            frame = source.convert('RGB')
        hashes.append(hashlib.sha256(frame.tobytes()).hexdigest())
        if previous is not None:
            changes.append(sum(ImageStat.Stat(ImageChops.difference(frame, previous)).mean) / 3)
        previous = frame
    assert len(set(hashes)) == 96, 'Duplicated rendered frames'
    report['unique_source_frames'] = len(set(hashes))
    report['adjacent_mean_absolute_pixel_change_0_to_255'] = {
        'min': min(changes), 'max': max(changes), 'mean': sum(changes) / len(changes)}
    report['layers'] = {}
    for path in sorted((HERE / 'layers').glob('*.png')):
        with Image.open(path) as source:
            native_alpha = 'A' in source.getbands()
            entry = {'size': list(source.size), 'mode': source.mode,
                     'native_alpha': native_alpha, 'bytes': path.stat().st_size,
                     'sha256': sha(path)}
            if native_alpha:
                histogram = source.getchannel('A').histogram()
                entry['alpha'] = {'zero': histogram[0], 'opaque255': histogram[255],
                                  'partial': sum(histogram[1:255]),
                                  'near_opaque250_to255': sum(histogram[250:]),
                                  'extrema': list(source.getchannel('A').getextrema())}
            report['layers'][path.name] = entry
    assert len(report['layers']) >= 6, 'Expected six landscape layers'
    for name in ('clouds-v1.png', 'far-trees-v1.png', 'hills-v1.png',
                 'medium-trees-v1.png', 'near-trees-v1.png'):
        assert report['layers'][name]['native_alpha'], name
        assert report['layers'][name]['alpha']['zero'] > 0, name
    comparison = probe_movie(args.compare, args.ffmpeg, args.ffprobe)
    assert comparison['sha256'] != report['movie']['sha256'], 'Movie equals previous projection'
    report['previous_movie'] = comparison
    report['comparison'] = 'Both movies decode at matching duration, frame count, resolution and FPS; bytes differ.'
    report['passed'] = True
    report['limits'] = 'Technical media validation does not establish artistic acceptance or exact source-pixel alignment.'
    destination = args.movie.parent / 'media-validation.json'
    destination.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
