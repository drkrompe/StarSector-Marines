"""Mux the C1 v2 animatic frames with the shipped C1 audio cues into one review mp4.

    python c1v2_animatic_mux.py [--window t0:t1]

--window cuts one beat (seconds of shot time) out of the cue mix, matching frames rendered with
c1v2_scene.py --animatic --frames; the output is named for the window.

Reads build/c1v2/animatic/frames/%05d.png (from c1v2_scene.py --animatic) and the cue list in
mod/data/cinematic/jangala-arrival/c1.json, mixes the cues the way the in-game player schedules
them (start time, volume, `until`, fades), and writes build/c1v2/animatic/c1v2-animatic.mp4.
Review output only; it is never shipped.
"""
import json
import subprocess
import sys
from pathlib import Path

BASE = Path(__file__).resolve().parent
REPO = BASE.parents[3]
TIMELINE = REPO / 'mod' / 'data' / 'cinematic' / 'jangala-arrival' / 'c1.json'
SOUNDS = REPO / 'mod' / 'sounds' / 'story' / 'jangala-arrival'
ANIMATIC = REPO / 'build' / 'c1v2' / 'animatic'


def main():
    timeline = json.loads(TIMELINE.read_text())
    duration = timeline['duration']
    t0, t1 = 0.0, duration
    if '--window' in sys.argv:
        a, b = sys.argv[sys.argv.index('--window') + 1].split(':')
        t0, t1 = float(a), float(b)
    inputs, chains = [], []
    for i, cue in enumerate(timeline['cues']):
        path = SOUNDS / (cue['sound'] + '.ogg')
        if not path.exists():
            raise FileNotFoundError(f'{path} missing; run gradlew :asset-pipeline:processAudio')
        inputs += ['-i', str(path)]
        length = min(cue.get('until', duration), duration) - cue['t']
        f = [f'atrim=0:{length:.3f}', f"volume={cue['volume']}"]
        if cue.get('fade_in'):
            f.append(f"afade=t=in:d={cue['fade_in']}")
        if cue.get('fade_out') and cue.get('until', 0) <= duration:
            f.append(f"afade=t=out:st={max(0.0, length - cue['fade_out']):.3f}:d={cue['fade_out']}")
        delay = round(cue['t'] * 1000)
        f.append(f'adelay={delay}|{delay}')
        chains.append(f'[{i + 1}:a]' + ','.join(f) + f'[a{i}]')
    mix = ''.join(f'[a{i}]' for i in range(len(chains)))
    graph = (';'.join(chains) + f';{mix}amix=inputs={len(chains)}:normalize=0,'
             f'atrim={t0:.3f}:{t1:.3f},asetpts=PTS-STARTPTS[aout]')
    tag = '' if (t0, t1) == (0.0, duration) else f'-{t0:g}-{t1:g}s'
    out = ANIMATIC / f'c1v2-animatic{tag}.mp4'
    first = round(t0 * timeline['fps'])
    cmd = ['ffmpeg', '-loglevel', 'error', '-y', '-framerate', str(timeline['fps']),
           '-start_number', str(first), '-i', str(ANIMATIC / 'frames' / '%05d.png'), *inputs,
           '-filter_complex', graph, '-map', '0:v', '-map', '[aout]',
           '-c:v', 'libx264', '-crf', '20', '-pix_fmt', 'yuv420p', '-c:a', 'aac', '-b:a', '192k',
           '-shortest', str(out)]
    subprocess.run(cmd, check=True)
    print('Wrote', out)


if __name__ == '__main__':
    main()
