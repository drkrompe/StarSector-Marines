"""Short audio auditions of the engine voice, so a change of sound is judged without a full mix.

    python art-source/company-prologue/second-wind-cinematic/arrival-audio/audition_engines.py

Writes build/audio-previews/cinematic-sfx/audition/*.mp3: the synthesised rocket voice close (a
landing burn), at a few hundred metres (Second Wind's drive from C4's sky camera) and passing
(C1), plus every generated `c4_launch_burn` take for comparison.
"""

from pathlib import Path
import subprocess

import numpy as np

import synth_flyby as fb

HERE = Path(__file__).resolve().parent
OUT = HERE.parents[3] / "build" / "audio-previews" / "cinematic-sfx" / "audition"

CLIPS = {
    "01-synth-close-landing-burn": dict(fb.HOUND, start=0.0, end=9.0, path=fb.fixed(0.0, 20.0, -60.0),
                                        muffle_far=900.0, muffle_near=900.0, bass=2.6, crackle=0.9,
                                        rush_gain=0.1, distance_power=1.0, pan_width=0.0, seed=71,
                                        thrust=[(0.0, 0.0), (0.4, 1.0), (8.0, 1.0), (9.0, 0.0)]),
    "02-synth-drive-600m": dict(fb.PASSES["second_wind_c4_drive"], start=0.0, end=9.0,
                                thrust=[(0.0, 0.0), (0.5, 1.0), (8.0, 1.0), (9.0, 0.0)]),
    "03-synth-c1-pass": dict(fb.PASSES["second_wind_pass"], start=5.0, end=15.0),
}


def mp3(samples, path):
    peak = np.abs(samples).max()
    samples = samples * (0.7 / peak if peak else 1.0)
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "f32le", "-ar", str(fb.SR), "-ac", "2", "-i", "-",
                    "-c:a", "libmp3lame", "-q:a", "2", str(path)],
                   input=samples.astype(np.float32).tobytes(), check=True)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for name, spec in CLIPS.items():
        mp3(fb.render(spec), OUT / (name + ".mp3"))
        print("Wrote", OUT / (name + ".mp3"), flush=True)


if __name__ == "__main__":
    main()
