"""Encode/validate the derivative lift proof; retained baseline outputs are untouched."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import sys
from PIL import Image

HERE = Path(__file__).resolve().parent
FRAMES = HERE / 'build/atlas-lift-frames'
REVIEW = HERE / 'review/atlas-lift'
MOVIE = REVIEW / 'jangala-parallax-atlas-lift.mp4'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--ffmpeg', default='ffmpeg')
    parser.add_argument('--ffprobe', default='ffprobe')
    args = parser.parse_args()
    old_report = REVIEW / 'media-validation.json'
    previous = json.loads(old_report.read_text()) if old_report.is_file() else {}
    subprocess.run([sys.executable, str(HERE/'assemble.py'), '--frames', str(FRAMES),
                    '--output', str(MOVIE), '--ffmpeg', args.ffmpeg], check=True)
    probe = json.loads(subprocess.check_output([args.ffprobe, '-v', 'error', '-count_frames',
                       '-show_streams', '-show_format', '-of', 'json', str(MOVIE)]))
    stream = probe['streams'][0]
    subprocess.run([args.ffmpeg, '-v', 'error', '-xerror', '-i', str(MOVIE), '-f', 'null', '-'], check=True)
    hashes = []
    for i in range(96):
        with Image.open(FRAMES/f'frame_{i:04d}.png') as image:
            if image.size != (1920,1080):
                raise ValueError('Lift frames must be native1920x1080')
            hashes.append(hashlib.sha256(image.tobytes()).hexdigest())
    if len(set(hashes)) != 96 or int(stream['nb_read_frames']) != 96:
        raise ValueError('Expected96 unique source frames and96 decoded frames')
    if (stream['width'],stream['height'],stream['r_frame_rate']) != (1920,1080,'24/1') or float(probe['format']['duration']) != 4:
        raise ValueError('Expected1920x1080/24fps/four seconds')
    digest = hashlib.sha256(MOVIE.read_bytes()).hexdigest()
    report = {'passed':True, 'scope':'Integrity and source uniqueness; browser observation retained only for matching movie hash',
              'movie':MOVIE.name, 'dimensions':[1920,1080], 'fps':'24/1', 'decoded_frames':96,
              'duration_seconds':4, 'source_frames':96, 'unique_source_frames':len(set(hashes)),
              'full_decode':'ffmpeg -xerror exit0', 'bytes':MOVIE.stat().st_size, 'sha256':digest}
    if previous.get('sha256') == digest and 'normal_speed_browser_playback' in previous:
        report['normal_speed_browser_playback'] = previous['normal_speed_browser_playback']
    old_report.write_text(json.dumps(report,indent=2)+'\n')
    print(json.dumps(report))


if __name__ == '__main__':
    main()
