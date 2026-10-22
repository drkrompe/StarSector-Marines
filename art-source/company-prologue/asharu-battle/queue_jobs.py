"""Asharu battle: write render-queue jobs for a review round (see the render-queue skill).

    python queue_jobs.py --run r8 --what round8-new-builds --out <dir> k3 k3b,k4,r1,r2

Each argument after the options is one job's frame set. Every job:
- renders into its own {work}/<run>/ (frame resume after a swap), at 48 samples, in the review lane;
- takes every build in roster.FINISHED as a drive input, pinned by the sha256 of its .blend and
  manifest as they are on Drive now, so a build that changes or half-syncs is refused;
- posts its labelled stills to review/cinematic-asharu-battle/<run date>_<what>_<machine>; two jobs
  of one run share that folder.

Stand-in sprites, hull data and the desert map are read from the Starsector install, which both
machines have at their platform default (identical files), so jobs are `machine: any` by default
and either runner can take a frame; give each frame its own set to let both machines share a
round. A machine posts to its own run folder. Submit each written job with
tools/render_queue.py submit <job.json> --commit <commit>.
"""
import argparse
import hashlib
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE.parent))
import roster  # noqa: E402

DRIVE = Path('G:/My Drive/starsector')


def sha256(path):
    h = hashlib.sha256()
    with open(path, 'rb') as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b''):
            h.update(chunk)
    return h.hexdigest()


def inputs():
    out = [{'kind': 'git', 'commit': '<filled in by --commit>'}]
    for f in roster.FINISHED.values():
        src = DRIVE / f['drive']
        out.append({'kind': 'drive', 'from': '{drive}/' + f['drive'],
                    'to': '{checkout}/build/asharu-battle/builds/' + f['dir'],
                    'sha256': {name: sha256(src / name) for name in (f['file'], f.get('manifest', 'manifest.json'))}})
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--run', required=True)
    ap.add_argument('--what', required=True)
    ap.add_argument('--out', required=True)
    ap.add_argument('--samples', type=int, default=48)
    ap.add_argument('--machine', default='any', choices=('any', 'windows', 'mac'))
    ap.add_argument('--light', action='store_true', help='light lane: half size (use --samples 16 or fewer)')
    ap.add_argument('--swfx', action='store_true', help='draw the key frames with the effects kit (scene_fx)')
    ap.add_argument('--layers', action='store_true',
                    help='render the key frames as layers and compose them (composite_layers.py); the EXRs go to '
                         'Drive builds/asharu-layers/<run>_<machine>/ for recomposing without a render')
    ap.add_argument('--date', required=True, help='the job id date, YYYY-MM-DD')
    ap.add_argument('sets', nargs='+')
    a = ap.parse_args()
    if a.light and a.samples > 16:
        raise SystemExit('a light job renders at 16 samples or fewer')
    ins = inputs()
    for frames in a.sets:
        names = frames.split(',')
        job = {
            'id': f"asharu-{a.run}-{'-'.join(names)}-{a.date}",
            'machine': a.machine,
            'lane': 'light' if a.light else 'review',
            'priority': 0,
            'after': [],
            'requires': ['gpu'],
            'cwd': '{checkout}/art-source/company-prologue/asharu-battle',
            'command': ['{python}', 'battle_blockout.py', '--frames', frames, '--samples', str(a.samples),
                        '--root', '{work}', '--run', a.run, '--date', a.date, '--post', '{machine}', '--what', a.what] + (['--half'] if a.light else []) + (['--swfx'] if a.swfx else []) + (['--layers'] if a.layers else []),
            'env': {},
            'inputs': ins,
            'frames': {'dir': '{work}/' + a.run + '/raw',
                       'glob': f'{names[0]}.png' if len(names) == 1 else '*.png', 'count': len(names)},
        }
        if a.layers:
            job['outputs'] = [{'from': '{work}/' + a.run + '/raw', 'glob': '*.layers.exr',
                               'to': '{drive}/builds/asharu-layers/' + a.run + '_{machine}'}]
        path = Path(a.out) / f"{job['id']}.json"
        path.write_text(json.dumps(job, indent=1))
        print(path)


if __name__ == '__main__':
    main()
