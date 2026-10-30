"""The Asharu battle's reentry: the owner's shuttle reentry clip, held from S11 to the cut into S13.

  python art-source/company-prologue/asharu-battle/battle-audio/reentry.py [--out DIR]

The clip (sfx-takes/s11-shuttle-reentry_t01, 14.5 s) is too short for S11 and S12: its steady
middle is looped with crossfades until the cut at 138 s, where it stops over a short fade.
The burn is in frame all through S12, so it is heard all through S12 (owner, 2026-10-30), and the
further the long lens pulls back the duller it is: S11 rides just behind the hulls and keeps the
clip as made; from S12's cut a low-pass follows S12's lens, its corner LENS_HZ per millimetre,
and its level falls with the corner. Writes `reentry.wav` and `reentry.mp3` under
build/asharu-reentry/, starting at START.
"""
import argparse
import subprocess
import sys
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))
import battle_blockout as bb  # noqa: E402
import engines  # noqa: E402

SF = engines.SF
OUT = HERE.parents[3] / "build" / "asharu-reentry"
TAKE = HERE / "sfx-takes" / "s11-shuttle-reentry_t01.mp3"
START, S12, CUT = 118.2, 126.0, 138.0
LOOP = (3.0, 11.0)       # the clip's steady middle, seconds
XFADE, END_FADE = 1.0, 0.25
LENS_HZ = 45.0           # S12's low-pass corner per mm of lens: 18 mm 810 Hz, 85 mm 3.8 kHz
LEVEL_POWER = 0.35       # level ~ (corner / the brightest S12 corner) ** this


def clip():
    raw = subprocess.run(["ffmpeg", "-v", "error", "-i", str(TAKE), "-ac", "2", "-ar", str(SF.SR), "-f", "f32le", "-"],
                         check=True, capture_output=True).stdout
    return np.frombuffer(raw, np.float32).astype(np.float64).reshape(-1, 2)


def held(x, n):
    """The clip, its LOOP stretch repeated with crossfades until it runs n samples."""
    a, b, fade = int(LOOP[0] * SF.SR), int(LOOP[1] * SF.SR), int(XFADE * SF.SR)
    ramp = np.linspace(0, 1, fade)[:, None]
    out = x[:b]
    while len(out) < n:
        out = np.concatenate([out[:-fade], out[-fade:] * (1 - ramp) + x[a:a + fade] * ramp, x[a + fade:b]])
    return out[:n]


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--out", default=str(OUT))
    args = parser.parse_args()
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    n = int(round((CUT - START) * SF.SR))
    x = held(clip(), n)
    t = START + np.arange(n) / SF.SR
    cam = bb.SHOTS['k6']['camera']['at']
    grid = np.arange(S12, CUT + .05, .05)
    corner = np.interp(t, grid, [LENS_HZ * cam(g)['lens'] for g in grid])
    dull = np.stack([SF.one_pole(SF.one_pole(x[:, c], corner), corner) for c in (0, 1)], axis=1)
    level = (corner / corner[t >= S12].max()) ** LEVEL_POWER
    y = np.where((t < S12)[:, None], x, dull * level[:, None])
    y *= np.minimum(1.0, (CUT - t) / END_FADE)[:, None]
    y *= engines.PEAK / np.abs(y).max()
    wav = out / "reentry.wav"
    SF.write(y, wav)
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", str(wav), "-b:a", "192k", str(wav.with_suffix(".mp3"))],
                   check=True)
    for s in np.arange(START, CUT, 1.0):
        k = (t >= s) & (t < s + 1)
        print('%.1f s  %5.0f Hz  %5.1f dB' % (s, corner[k].mean() if s >= S12 else 0,
                                              20 * np.log10(np.sqrt((y[k] ** 2).mean()) + 1e-9)))
    print(wav)


if __name__ == "__main__":
    main()
