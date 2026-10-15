"""Encode the clean rough blockout and make annotated mobile review stills."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess

from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
SHOTS = ((1, 96, '03', 'Managed clearings'), (97, 192, '04', 'Working port'),
         (193, 312, '05', 'Aligned approach'), (313, 576, '06', 'Cradle load transfer'))
DEPTH_LABELS = {
    '03': 'Near field rows / river and woodland / distant hills',
    '04': 'Near towers and roofs / port district / forest and hills',
    '05': 'Foreground yard / moving hull and cradle / distant industry',
    '06': 'Fixed yard / descent and support / background port',
}


def font(size):
    for candidate in ('DejaVuSans.ttf', 'Arial.ttf', 'C:/Windows/Fonts/arial.ttf'):
        try:
            return ImageFont.truetype(candidate, size)
        except OSError:
            pass
    return ImageFont.load_default(size=size)


def read(path):
    with Image.open(path) as source:
        if source.size != (1920, 1080):
            raise ValueError(f'{path}: expected 1920x1080; got {source.size}')
        return source.convert('RGB')


def annotate(image, title, shot):
    result = image.copy()
    draw = ImageDraw.Draw(result, 'RGBA')
    draw.rectangle((0, 972, 1919, 1079), fill=(12, 20, 24, 240))
    draw.text((28, 980), title + ' | Rough layered blockout', font=font(31), fill='white')
    draw.text((28, 1027), DEPTH_LABELS[shot],
              font=font(25), fill=(198, 215, 217, 255))
    return result


def stills(frames, review):
    sheet = Image.new('RGB', (1920, 1080))
    for index, (start, end, number, title) in enumerate(SHOTS):
        image = read(frames / f'frame_{(start + end) // 2:04d}.png')
        annotated = annotate(image, number + ' ' + title, number)
        annotated.save(review / f'shot-{number}-review.jpg', quality=95)
        sheet.paste(annotated.resize((960, 540), Image.Resampling.LANCZOS),
                    (index % 2 * 960, index // 2 * 540))
    sheet.save(review / 'contact-sheet.jpg', quality=95)


def encode(ffmpeg, frames, output, start, count, crf):
    subprocess.run([ffmpeg, '-hide_banner', '-loglevel', 'error', '-y',
                    '-framerate', '24', '-start_number', str(start),
                    '-i', str(frames / 'frame_%04d.png'), '-frames:v', str(count),
                    '-an', '-c:v', 'libx264', '-crf', str(crf), '-pix_fmt', 'yuv420p',
                    '-movflags', '+faststart', str(output)], check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--frames', type=Path, default=HERE / 'build/frames')
    parser.add_argument('--output', type=Path, default=HERE / 'review/layered-next-scenes-review.mp4')
    parser.add_argument('--ffmpeg', default=os.environ.get('FFMPEG') or shutil.which('ffmpeg') or 'ffmpeg')
    parser.add_argument('--preview', action='store_true', help='Make review JPGs from midpoint frames only')
    parser.add_argument('--per-shot', action='store_true', help='Also encode each shot as a separate movie')
    parser.add_argument('--crf', type=int, default=21)
    args = parser.parse_args()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    if not args.preview:
        hashes = []
        for index in range(1, 577):
            image = read(args.frames / f'frame_{index:04d}.png')
            hashes.append(hashlib.sha256(image.tobytes()).hexdigest())
        spans = []
        for start, end, number, _ in SHOTS:
            shot_hashes = hashes[start - 1:end]
            # Shot 06 begins with the authored hold above the cradle. Both the
            # approach and landing may end in a stationary hold as well.
            moving_start = 0
            if number == '06':
                while moving_start < 2 and shot_hashes[moving_start + 1] == shot_hashes[0]:
                    moving_start += 1
            moving_end = len(shot_hashes)
            while moving_end > 1 and shot_hashes[moving_end - 2] == shot_hashes[-1]:
                moving_end -= 1
            moving_hashes = shot_hashes[moving_start:moving_end]
            if len(set(moving_hashes)) != len(moving_hashes):
                raise ValueError(f'Shot {number}: duplicate frames inside moving span')
            spans.append({'shot': number, 'frames': len(shot_hashes),
                          'unique_frames': len(set(shot_hashes)),
                          'leading_hold_extra_frames': moving_start,
                          'trailing_hold_extra_frames': len(shot_hashes) - moving_end})
        (args.output.parent / 'frame-validation.json').write_text(
            json.dumps({'frame_count': 576, 'unique_frames': len(set(hashes)),
                        'size': [1920, 1080], 'fps': 24, 'shots': spans}, indent=2) + '\n',
            encoding='utf-8')
        encode(args.ffmpeg, args.frames, args.output, 1, 576, args.crf)
        if args.per_shot:
            for start, end, number, _ in SHOTS:
                encode(args.ffmpeg, args.frames, args.output.parent / f'shot-{number}.mp4',
                       start, end - start + 1, args.crf)
        print(f'{args.output}: 576 frames ({len(set(hashes))} unique), 24 seconds, 24fps, 1080p, silent')
    stills(args.frames, args.output.parent)
    print(f'Annotated review JPGs: {args.output.parent}')


if __name__ == '__main__':
    main()
