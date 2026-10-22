"""Asharu battle: what the kit's volumes cost a key frame, as timed renders (a light render-queue job).

    python volume_timing.py --frame k3b --root DIR [--percent 12] [--samples 1]

Renders the frame once per variant, small and at few samples, and prints each render's wall time:
    as-is        the frame as a review round renders it
    step-4       scene volume_step_rate 4 and volume_max_steps 256 (no effect unless biased: Blender 5's
                 default unbiased volumes do null scattering, whose cost follows density, not steps)
    biased       volume_biased: ray marching instead, at step rate 1; biased-step-4 at step rate 4
    no-thrusters, no-smoke, no-fire: one kind of the kit's volumes left out
    no-haze      the engines' smoke haze left out (vacuum still hangs a short haze off every drive)
    no-volumes   every kit volume left out: what the hulls, lights and planet cost on their own
A frame that takes hours at review size is diagnosed here in minutes: the variants say whether the
cost is the volumes at all, which volumes, and whether coarser marching alone brings it down.
"""
import argparse
import re
import subprocess
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent

VARIANTS = {
    'as-is': [],
    'step-4': ['--volume-step-rate', '4', '--volume-max-steps', '256'],
    'biased': ['--volume-biased'],
    'biased-step-4': ['--volume-biased', '--volume-step-rate', '4', '--volume-max-steps', '256'],
    'no-haze': ['--hide-volumes', 'SWFX smoke haze'],
    'no-thrusters': ['--hide-volumes', 'SWFX thrusters'],
    'no-smoke': ['--hide-volumes', 'SWFX smoke'],
    'no-fire': ['--hide-volumes', 'SWFX fire'],
    'no-volumes': ['--hide-volumes', 'all'],
}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--frame', default='k3b')
    ap.add_argument('--root', required=True)
    ap.add_argument('--percent', default='12')
    ap.add_argument('--samples', default='1')
    ap.add_argument('--variants', default=','.join(VARIANTS))
    a = ap.parse_args()
    rows = []
    for name in a.variants.split(','):
        t0 = time.time()
        out = subprocess.run([sys.executable, str(HERE / 'battle_blockout.py'), '--frames', a.frame, '--samples', a.samples,
                              '--swfx', '--combined', '--percent', a.percent, '--root', a.root, '--run', f'timing-{name}', '--rerender',
                              *VARIANTS[name]], capture_output=True, text=True)
        wall = time.time() - t0
        m = re.search(r'ASHARU render time \S+ ([\d.]+) s', out.stdout)
        hid = re.search(r'ASHARU hid (\d+)', out.stdout)
        rows.append((name, float(m.group(1)) if m else None, wall, hid.group(1) if hid else '-', out.returncode))
        print(f'TIMING {a.frame} {name:11} render {rows[-1][1]} s  (whole run {wall:.0f} s, volumes hidden {rows[-1][3]}, '
              f'exit {out.returncode})', flush=True)
        if out.returncode:
            print(out.stdout[-3000:], out.stderr[-3000:], flush=True)
    base = rows[0][1]
    for name, r, _, _, _ in rows:
        if r and base:
            print(f'TIMING {a.frame} {name:11} {r / base * 100:6.1f}% of as-is', flush=True)
    (Path(a.root) / f'timing-{a.frame}.txt').write_text('\n'.join(f'{n}\t{r}\t{w:.0f}\t{h}\t{e}' for n, r, w, h, e in rows))


if __name__ == '__main__':
    main()
