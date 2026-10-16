"""Join three reviewed four-second approach excerpts with explicit editorial cuts."""
import argparse
from fractions import Fraction
import hashlib
import json
from pathlib import Path
import subprocess


HERE = Path(__file__).resolve().parent
SHOTS = (
    ('Forest reveal / Atlas lift', 'forest-reveal-realism', 'jangala-forest-reveal-realism.mp4', 120),
    ('Managed land / Atlas passage', 'managed-land-realism', 'jangala-managed-land-realism.mp4', 240),
    ('Working port abeam', 'port-approach-realism', 'jangala-port-approach-realism.mp4', 400),
)


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def inspect(path, ffprobe, frames, seconds):
    info = json.loads(subprocess.check_output([
        ffprobe, '-v', 'error', '-count_frames', '-show_streams', '-show_format',
        '-of', 'json', str(path)], text=True, encoding='utf-8'))
    video = next(s for s in info['streams'] if s['codec_type'] == 'video')
    if ((video['width'], video['height']) != (1920, 1080)
            or Fraction(video['avg_frame_rate']) != 24
            or int(video['nb_read_frames']) != frames
            or abs(float(info['format']['duration']) - seconds) > .01
            or any(s['codec_type'] == 'audio' for s in info['streams'])):
        raise ValueError(f'Unexpected review format or cadence: {path}')
    return info


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--ffmpeg', default='ffmpeg')
    parser.add_argument('--ffprobe', default='ffprobe')
    args = parser.parse_args()
    review = HERE / 'review'
    review.mkdir(parents=True, exist_ok=True)
    inputs, cuts = [], []
    for index, (title, folder, filename, source_start) in enumerate(SHOTS):
        path = HERE.parent / folder / 'review' / filename
        inspect(path, args.ffprobe, 96, 4)
        inputs.extend(['-i', str(path)])
        cuts.append({'title': title, 'movie': f'../{folder}/review/{filename}',
                     'sha256': digest(path), 'sequence_start_seconds': index * 4,
                     'source_frames': [source_start, source_start + 47.5],
                     'mapping': 'source_start + 0.5*i, i=0..95; source12fps, output24fps'})
    movie = review / 'jangala-approach-continuity.mp4'
    graph = ';'.join(f'[{i}:v]setpts=PTS-STARTPTS[v{i}]' for i in range(3))
    graph += ';[v0][v1][v2]concat=n=3:v=1:a=0[out]'
    subprocess.run([args.ffmpeg, '-hide_banner', '-loglevel', 'error', '-y', *inputs,
                    '-filter_complex', graph, '-map', '[out]', '-an', '-c:v', 'libx264',
                    '-crf', '19', '-pix_fmt', 'yuv420p', '-movflags', '+faststart',
                    str(movie)], check=True)
    inspect(movie, args.ffprobe, 288, 12)
    subprocess.run([args.ffmpeg, '-v', 'error', '-xerror', '-i', str(movie),
                    '-f', 'null', '-'], check=True)
    report = {'passed': True, 'movie': movie.name, 'sha256': digest(movie),
              'bytes': movie.stat().st_size, 'dimensions': [1920, 1080],
              'fps': 24, 'frames': 288, 'seconds': 12, 'audio': False,
              'full_decode': 'FFmpeg -xerror exit 0', 'cuts': cuts,
              'limits': 'Three representative excerpts with time ellipses; no entry, exterior landing, narration or runtime playback. Re-encoded from reviewed H.264 shots.'}
    (review / 'media-validation.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
