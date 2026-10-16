"""Join the six reviewed beats from space to the damaged surface landing."""
from pathlib import Path
from fractions import Fraction
import hashlib
import json
import shutil
import subprocess

HERE = Path(__file__).resolve().parent
SHOTS = [
    ('Space to Jangala', 'space-approach/review/jangala-space-approach.mp4', 4),
    ('Atmospheric entry tail', 'entry-window/review/jangala-entry-window.mp4', 4),
    ('Forest reveal and Atlas lift', 'forest-reveal-realism/review/jangala-forest-reveal-realism.mp4', 4),
    ('Managed fields and agriculture', 'managed-land-realism/review/jangala-managed-land-realism.mp4', 4),
    ('Working port abeam', 'port-refinement/review/jangala-port-refined.mp4', 4),
    ('Damaged Second Wind landing', 'landing-wreck-pass/review/jangala-damaged-landing.mp4', 8),
]

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def inspect(path, seconds, probe):
    info = json.loads(subprocess.check_output([
        probe, '-v', 'error', '-count_frames', '-show_streams',
        '-show_format', '-of', 'json', str(path)], text=True))
    stream = next(s for s in info['streams'] if s['codec_type'] == 'video')
    assert (stream['width'], stream['height']) == (1920, 1080), path
    assert Fraction(stream['avg_frame_rate']) == 24, path
    assert int(stream['nb_read_frames']) == seconds * 24, path
    assert abs(float(info['format']['duration']) - seconds) < .001, path
    assert stream['codec_name'] == 'h264' and stream['pix_fmt'] == 'yuv420p', path
    assert not any(s['codec_type'] == 'audio' for s in info['streams']), path
    return info

def main():
    ffmpeg, probe = shutil.which('ffmpeg'), shutil.which('ffprobe')
    assert ffmpeg and probe, 'FFmpeg and FFprobe must be on PATH'
    out = HERE / 'review'
    out.mkdir(parents=True, exist_ok=True)
    inputs, records, clock = [], [], 0
    for title, name, seconds in SHOTS:
        path = HERE.parent / name
        inspect(path, seconds, probe)
        records.append(dict(title=title, path='../'+name, sha256=sha(path),
                            start_seconds=clock, duration_seconds=seconds,
                            frames=seconds*24))
        inputs.extend(['-i', str(path)])
        clock += seconds
    graph = ';'.join(f'[{i}:v]setpts=PTS-STARTPTS[v{i}]' for i in range(len(SHOTS)))
    graph += ';' + ''.join(f'[v{i}]' for i in range(len(SHOTS)))
    graph += f'concat=n={len(SHOTS)}:v=1:a=0[out]'
    movie = out / 'jangala-space-to-berth.mp4'
    subprocess.run([ffmpeg, '-hide_banner', '-loglevel', 'error', '-y',
                    *inputs, '-filter_complex', graph, '-map', '[out]', '-an',
                    '-c:v', 'libx264', '-crf', '18', '-preset', 'medium',
                    '-pix_fmt', 'yuv420p', '-movflags', '+faststart', str(movie)],
                   check=True)
    result = inspect(movie, clock, probe)
    subprocess.run([ffmpeg, '-v', 'error', '-xerror', '-i', str(movie),
                    '-f', 'null', '-'], check=True)
    report = dict(passed=True, path='review/'+movie.name, sha256=sha(movie),
                  bytes=movie.stat().st_size, duration_seconds=clock,
                  dimensions=[1920,1080], fps=24, decoded_frames=clock*24,
                  audio=False, codec='h264', pixel_format='yuv420p',
                  full_decode='FFmpeg -xerror exit 0', sources=records,
                  script_sha256=sha(Path(__file__)), probe=result,
                  limits='Six short editorial excerpts; elapsed travel between scales is implied. '
                  'Entry and forest share one endpoint sample (1/24 s). No audio or game integration; '
                  'normal-speed visual observations are recorded separately.')
    (out/'media-validation.json').write_text(
        json.dumps(report, indent=2)+'\n', encoding='utf-8', newline='\n')
    print(json.dumps({k:report[k] for k in
                      ['passed','path','bytes','duration_seconds','decoded_frames','sha256']}))

if __name__ == '__main__':
    main()
