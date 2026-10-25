"""One final shot's frames -> its MP4 (the render queue's follow-up job after a shot's chunks).

    python assemble_shot.py <frames dir> <kf> <first frame> <count> <out.mp4> [--wait-minutes 30] [--ffmpeg <path>] [--fps 24]
        [--label "S6c The column is cut | 68.5-70.5 s | df837c8dc"] [--guns <chunks>] [--blobs]

The frames are battle_blockout's final composites, '<kf>_f<frame>.png' at --fps (24; the lit
animatic is 12), copied to Drive by
chunk jobs that may have run on the other machine; Drive can take a while to bring them here, so this
waits (up to --wait-minutes) until every one is present before encoding. Exits 1 if any are still
missing, naming them.

--guns N: the shot's N chunks each wrote the guns they picked ('<kf>_guns_f<first>.json', battle_blockout);
waits for all N and exits 1 without encoding if any two differ: a chunk that fired other turrets
than the one before it makes the rounds in flight jump at the seam (coordinator, 2026-10-25, S6b).

--blobs: every frame's white regions ('<kf>_blobs_f<frame>.json', battle_blockout) are waited for and
chained over the whole shot in frame order (blob_lint.chain); a region held past a flash, a chunk seam
included, exits 1 without encoding. A chunk's own lint restarts at its first frame, so a hold that runs
across a seam is caught only here (Fix 2, 2026-10-25).

--label burns a caption into every frame (owner, 2026-10-23: "no labels on these clips"): the text
given (shot id and name, span, commit) and the frame's own sequence frame number, top left.
"""
import json
import os
import subprocess
import tempfile
import sys
import time
from pathlib import Path


def _font():
    for f in ('C:/Windows/Fonts/bahnschrift.ttf', '/System/Library/Fonts/Supplemental/Arial.ttf',
              '/Library/Fonts/Arial.ttf', '/System/Library/Fonts/Helvetica.ttc'):
        if os.path.exists(f):
            return f
    return None


def _path(p):
    """A path for a drawtext option: forward slashes, the drive colon escaped."""
    return str(p).replace('\\', '/').replace(':', r'\:')


def burn(label_file, first):
    """An ffmpeg drawtext chain: the label (read from label_file, so it needs no escaping), then
    'frame N' (the sequence frame), top left, on a dark box, sized to the frame's height."""
    font = _font()
    ff = f"fontfile='{_path(font)}':" if font else ''
    box = 'fontcolor=white:box=1:boxcolor=black@0.55:boxborderw=6:fontsize=h/30'
    return (f"drawtext={ff}textfile='{_path(label_file)}':x=12:y=10:{box},"
            f"drawtext={ff}text='frame %{{eif\\:n+{first}\\:d}}':x=12:y=10+h/22:{box}")


def main(argv):
    frames, kf, first, count, out = Path(argv[0]), argv[1], int(argv[2]), int(argv[3]), Path(argv[4])
    ffmpeg = argv[argv.index('--ffmpeg') + 1] if '--ffmpeg' in argv else 'ffmpeg'
    fps = argv[argv.index('--fps') + 1] if '--fps' in argv else '24'
    wait = float(argv[argv.index('--wait-minutes') + 1]) if '--wait-minutes' in argv else 30.0
    label = argv[argv.index('--label') + 1] if '--label' in argv else None
    guns = int(argv[argv.index('--guns') + 1]) if '--guns' in argv else 0
    names = [frames / f'{kf}_f{first + i:05d}.png' for i in range(count)]
    blobs = [frames / f'{kf}_blobs_f{first + i:05d}.json' for i in range(count)] if '--blobs' in argv else []
    deadline = time.time() + wait * 60
    while True:
        missing = [p.name for p in names + blobs if not p.exists() or p.stat().st_size == 0]
        picks = sorted(frames.glob(f'{kf}_guns_f*.json'))
        if len(picks) < guns:
            missing.append(f'{guns - len(picks)} of {guns} chunk gun picks ({kf}_guns_f*.json)')
        if not missing:
            break
        if time.time() > deadline:
            print(f'ASSEMBLE {kf}: {len(missing)} of {count} frames missing, e.g. {missing[:5]}', flush=True)
            return 1
        print(f'ASSEMBLE {kf}: waiting for {len(missing)} frames', flush=True)
        time.sleep(60)
    if guns:
        seen = {p.name: p.read_text(encoding='utf-8') for p in picks}
        if len(set(seen.values())) > 1:
            print(f'ASSEMBLE {kf}: its chunks picked different guns, so the rounds jump at a seam; not encoding:',
                  flush=True)
            for name, text in seen.items():
                print(f'  {name}: {text}', flush=True)
            return 1
        print(f'ASSEMBLE {kf}: {len(seen)} chunks picked the same guns', flush=True)
    if blobs:
        import blob_lint
        held = blob_lint.chain([json.loads(p.read_text(encoding='utf-8')) for p in blobs])
        if held:
            print(f'ASSEMBLE {kf}: {len(held)} frames hold a white region past a flash; not encoding:', flush=True)
            for frame, row in held[:12]:
                print(f'  f{frame}: {row}', flush=True)
            return 1
        print(f'ASSEMBLE {kf}: blob lint over all {count} frames, seams included: none held', flush=True)
    out.parent.mkdir(parents=True, exist_ok=True)
    tmp = out.with_suffix('.partial.mp4')
    vf, label_file = [], None
    if label:
        with tempfile.NamedTemporaryFile('w', suffix='.txt', delete=False, encoding='utf-8') as fh:
            fh.write(label)
            label_file = fh.name
        vf = ['-vf', burn(label_file, first)]
    try:
        subprocess.check_call([ffmpeg, '-y', '-framerate', fps, '-start_number', str(first),
                               '-i', str(frames / f'{kf}_f%05d.png'), '-frames:v', str(count), *vf,
                               '-c:v', 'libx264', '-preset', 'slow', '-crf', '14', '-pix_fmt', 'yuv420p',
                               str(tmp)])
    finally:
        if label_file:
            os.unlink(label_file)
    tmp.replace(out)
    print(f'ASSEMBLE {kf}: {count} frames -> {out}', flush=True)
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
