"""Validate render-time bindings and encode 192-frame silent 1080p review.

--stills only validates the six stills and builds the deterministic contact sheet.
Default also encodes the complete motion manifest. Requires Pillow and ffmpeg.
"""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess

from PIL import Image, ImageDraw

BASE = Path(__file__).resolve().parent
REVIEW = BASE / 'review'


def sha(path):
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(chunk)
    return digest.hexdigest()


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def bound_path(relative):
    path = (BASE / relative).resolve()
    require(path.is_relative_to(BASE), 'Manifest path escapes package')
    return path


def validate(mode):
    manifest_path = REVIEW / (mode + '-manifest.json')
    data = json.loads(manifest_path.read_text(encoding='utf-8'))
    require(data['complete'] and not data['preview'] and data['mode'] == mode, 'Incomplete/preview/wrong manifest')
    require(data['dimensions'] == [1920, 1080] and data['fps'] == 24, 'Wrong render dimensions/fps')
    require(sha(bound_path(data['source_asset'])) == data['source_sha256_before'] == data['source_sha256_after'], 'Source asset changed')
    require(sha(bound_path(data['review_scene'])) == data['review_scene_sha256_before'] == data['review_scene_sha256_after'], 'Review scene changed')
    frames = data['frames']
    if mode == 'motion':
        require([f['frame'] for f in frames] == list(range(1, 193)), 'Need ordered frames 1..192')
    else:
        require([f['name'] for f in frames] == ['starboard', 'port', 'bow-quarter', 'stern-quarter', 'top', 'underside'], 'Need all six ordered views')
    for record in frames:
        require(record['source_sha256_before'] == record['source_sha256_after'] == data['source_sha256_before'], 'Frame source binding changed')
        require(record['review_scene_sha256_before'] == record['review_scene_sha256_after'] == data['review_scene_sha256_before'], 'Frame scene binding changed')
        path = bound_path(record['path'])
        require(path.stat().st_mtime_ns == record['mtime_ns'], 'Frame timestamp changed: ' + str(path))
        require(path.stat().st_size == record['bytes'] and sha(path) == record['sha256'], 'Frame bytes changed: ' + str(path))
        with Image.open(path) as image:
            require(image.size == (1920, 1080), 'Frame dimensions changed')
            image.verify()
    require(len({f['sha256'] for f in frames}) == len(frames) == data['unique_frame_hashes'], 'Repeated frames')
    return data, sha(manifest_path)


def contact_sheet(data):
    sheet = Image.new('RGB', (1920, 1080), '#141414')
    draw = ImageDraw.Draw(sheet)
    for index, record in enumerate(data['frames']):
        x, y = (index % 3) * 640, (index // 3) * 540
        with Image.open(bound_path(record['path'])) as image:
            image.thumbnail((640, 480), Image.Resampling.LANCZOS)
            sheet.paste(image, (x + (640 - image.width) // 2, y + 24 + (480 - image.height) // 2))
        draw.text((x + 20, y + 14), record['name'], fill='#dddddd')
    sheet.save(REVIEW / 'contact-sheet.png')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--stills', action='store_true')
    args = parser.parse_args()
    stills, still_hash = validate('stills')
    contact_sheet(stills)
    if args.stills:
        print('Verified six stills; contact-sheet.png created from bound current renders.')
        return
    data, manifest_hash = validate('motion')
    ffmpeg, ffprobe = shutil.which('ffmpeg'), shutil.which('ffprobe')
    require(ffmpeg and ffprobe, 'ffmpeg/ffprobe required on PATH')
    movie = REVIEW / 'second-wind-full-surface-turn.mp4'
    frames = bound_path(data['frames'][0]['path']).parent
    subprocess.run([ffmpeg, '-hide_banner', '-loglevel', 'error', '-y', '-framerate', '24', '-start_number', '1', '-i', str(frames / '%04d.png'), '-frames:v', '192', '-an', '-c:v', 'libx264', '-crf', '18', '-preset', 'medium', '-pix_fmt', 'yuv420p', '-movflags', '+faststart', str(movie)], check=True)
    info = json.loads(subprocess.check_output([ffprobe, '-v', 'error', '-count_frames', '-show_streams', '-show_format', '-of', 'json', str(movie)], text=True))
    streams = info['streams']
    require(len(streams) == 1, 'Movie must have one video stream and no audio')
    stream = streams[0]
    require(stream['codec_name'] == 'h264' and stream['width'] == 1920 and stream['height'] == 1080, 'Wrong encoded codec/dimensions')
    require(stream['r_frame_rate'] == '24/1' and int(stream['nb_read_frames']) == 192, 'Wrong encoded fps/frame count')
    require(abs(float(info['format']['duration']) - 8) < .001, 'Wrong duration')
    subprocess.run([ffmpeg, '-hide_banner', '-v', 'error', '-xerror', '-err_detect', 'explode', '-i', str(movie), '-map', '0:v:0', '-f', 'null', '-'], check=True)
    # Parse top-level MP4 atoms rather than searching byte strings inside payloads.
    atoms = []
    with movie.open('rb') as handle:
        while handle.tell() < movie.stat().st_size:
            header = handle.read(8)
            require(len(header) == 8, 'Truncated MP4 atom')
            size, kind = int.from_bytes(header[:4], 'big'), header[4:].decode('ascii')
            header_size = 8
            if size == 1:
                size = int.from_bytes(handle.read(8), 'big')
                header_size = 16
            if size == 0:
                size = movie.stat().st_size - handle.tell() + header_size
            require(size >= header_size, 'Invalid MP4 atom')
            atoms.append(kind)
            handle.seek(size - header_size, 1)
    require('moov' in atoms and 'mdat' in atoms and atoms.index('moov') < atoms.index('mdat'), 'MP4 is not faststart')
    # Recheck inputs after encoding too: no concurrent replacement is accepted.
    require(validate('motion')[1] == manifest_hash and validate('stills')[1] == still_hash, 'Manifest changed during encode')
    report = {'movie': movie.name, 'sha256': sha(movie), 'bytes': movie.stat().st_size,
              'dimensions': [1920, 1080], 'duration_seconds': 8, 'fps': 24,
              'frame_count': 192, 'audio': 'none', 'strict_decode': 'PASS', 'faststart': True,
              'motion_manifest_sha256': manifest_hash, 'stills_manifest_sha256': still_hash,
              'source_sha256': data['source_sha256_before'],
              'review_scene_sha256': data['review_scene_sha256_before'],
              'unique_frame_hashes': data['unique_frame_hashes'], 'probe': info,
              'visual_review': 'Numerical integrity only; inspect stills and playback separately.'}
    (REVIEW / 'media-validation.json').write_text(json.dumps(report, indent=2), encoding='utf-8', newline='\n')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
