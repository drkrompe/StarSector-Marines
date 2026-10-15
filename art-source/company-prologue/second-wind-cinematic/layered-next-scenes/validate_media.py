"""Validate the encoded rough review and retain compact, reproducible evidence."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess

HERE = Path(__file__).resolve().parent


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--ffmpeg', default='ffmpeg')
    parser.add_argument('--ffprobe', default='ffprobe')
    args = parser.parse_args()
    movie = HERE / 'review/layered-next-scenes-review.mp4'
    result = subprocess.run([args.ffprobe, '-v', 'error', '-count_frames',
                             '-show_streams', '-show_format', '-of', 'json', str(movie)],
                            check=True, capture_output=True, text=True)
    probe = json.loads(result.stdout)
    video = next(s for s in probe['streams'] if s['codec_type'] == 'video')
    assert (video['width'], video['height']) == (1920, 1080)
    assert video['avg_frame_rate'] == '24/1'
    assert int(video['nb_read_frames']) == 576
    assert abs(float(probe['format']['duration']) - 24) < .001
    assert video['codec_name'] == 'h264' and video['pix_fmt'] == 'yuv420p'
    assert not any(s['codec_type'] == 'audio' for s in probe['streams'])
    subprocess.run([args.ffmpeg, '-v', 'error', '-xerror', '-i', str(movie),
                    '-f', 'null', '-'], check=True, capture_output=True)
    frame_report = json.loads((HERE / 'review/frame-validation.json').read_text(encoding='utf-8'))
    report = dict(passed=True, movie=movie.relative_to(HERE).as_posix(),
                  bytes=movie.stat().st_size, sha256=hashlib.sha256(movie.read_bytes()).hexdigest(),
                  dimensions=[1920, 1080], fps=24, frames=576, duration_seconds=24,
                  audio=False, full_decode='FFmpeg -xerror exit 0', source_frames=frame_report,
                  limits='Media integrity only; untextured staging, not final visual acceptance.')
    (HERE / 'review/media-validation.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
