"""Source clips for C3's audio cues: the cabin bed and the exterior wind.

python c3_audio.py      # writes asset-pipeline/src/tool/resources/audio/jangala-arrival/
                        #   jangala_arrival_cabin_bed.flac, jangala_arrival_wind.flac

C3 is silent apart from cabin ambience (c3-layout.md), so it cues two sounds, built
here from repository ambience and seeded noise and then encoded like every other mod
sound by `:asset-pipeline:processAudio` (audio.json ids `jangala_arrival_cabin_bed`
and `jangala_arrival_wind`). Deterministic: the same inputs give the same files.

- Cabin bed: a 16 s seamless loop for any window shot. The audition bed the hail
  preview already uses (subway-train rumble under fan noise, build_hail_preview.py),
  low-passed to read as a sealed hull, over a faint low rumble. The tail is crossfaded
  into the head so a looping player never clicks.
- Wind: one 12.5 s pass of air over the hull. Band-limited noise with slow gusts and a
  faint whistle; loud at the start, where the entry's buffeting carries over, easing
  with the world shake (0.9 s time constant, c3_core / compose_c3.jitter) to a settled
  low level for the rest of the shot.
"""
import subprocess
import tempfile
import wave
from pathlib import Path

import numpy as np

from c3_core import ROOT

AUDIO = ROOT / "asset-pipeline" / "src" / "tool" / "resources" / "audio"
AMB = AUDIO / "ambient-machinery"
OUT = AUDIO / "jangala-arrival"
SR = 44100


def ffmpeg(*args):
    subprocess.run(["ffmpeg", "-hide_banner", "-v", "error", "-y", *args], check=True)


def read_wav(path):
    with wave.open(str(path)) as w:
        a = np.frombuffer(w.readframes(w.getnframes()), np.int16).astype(np.float32) / 32768
        return a.reshape(-1, w.getnchannels())


def write_flac(a, path):
    with tempfile.TemporaryDirectory() as td:
        tmp = Path(td) / "x.wav"
        with wave.open(str(tmp), "wb") as w:
            w.setnchannels(2), w.setsampwidth(2), w.setframerate(SR)
            w.writeframes((np.clip(a, -1, 1) * 32767).astype(np.int16).tobytes())
        ffmpeg("-i", str(tmp), "-c:a", "flac", str(path))


def render(graph_inputs, graph, seconds):
    with tempfile.TemporaryDirectory() as td:
        tmp = Path(td) / "r.wav"
        ffmpeg(*graph_inputs, "-filter_complex", graph, "-t", "%.3f" % seconds,
               "-ar", str(SR), "-ac", "2", "-c:a", "pcm_s16le", str(tmp))
        return read_wav(tmp)


def cabin_bed(loop=16.0, xfade=2.0):
    a = render(["-stream_loop", "-1", "-i", str(AMB / "subwayTrain_B-line.wav"),
                "-stream_loop", "-1", "-i", str(AMB / "fan_noise.wav"),
                "-f", "lavfi", "-i", "anoisesrc=c=brown:a=0.4:seed=303:r=%d" % SR],
               "[0:a]aformat=channel_layouts=stereo,lowpass=f=380,volume=0.55[t];"
               "[1:a]aformat=channel_layouts=stereo,lowpass=f=1600,highpass=f=120,volume=0.22[f];"
               "[2:a]aformat=channel_layouts=stereo,lowpass=f=90,volume=0.5[r];"
               "[t][f][r]amix=inputs=3:normalize=0", loop + xfade)
    n, x = round(loop * SR), round(xfade * SR)
    out = a[:n].copy()
    w = np.linspace(0, 1, x, dtype=np.float32)[:, None]
    out[:x] = a[n:n + x] * (1 - w) + a[:x] * w       # the tail flows into the head
    return out / max(1e-6, np.abs(out).max()) * 0.5


def wind(seconds=12.5):
    a = render(["-f", "lavfi", "-i", "anoisesrc=c=pink:a=0.5:seed=91:r=%d" % SR,
                "-f", "lavfi", "-i", "anoisesrc=c=white:a=0.5:seed=92:r=%d" % SR],
               "[0:a]aformat=channel_layouts=stereo,highpass=f=140,lowpass=f=1300[body];"
               "[1:a]aformat=channel_layouts=stereo,bandpass=f=880:width_type=q:w=12,volume=0.35[whistle];"
               "[body][whistle]amix=inputs=2:normalize=0", seconds)
    t = np.arange(len(a), dtype=np.float32) / SR
    rng = np.random.default_rng(17)
    gust = np.ones_like(t)
    for f, ph, d in zip(rng.uniform(0.11, 0.35, 4), rng.uniform(0, 6.28, 4), (0.18, 0.12, 0.08, 0.06)):
        gust += d * np.sin(2 * np.pi * f * t + ph)
    buffet = np.exp(-t / 0.9)                                    # the shake's decay
    flutter = 1 + 0.35 * buffet * np.sin(2 * np.pi * 9.0 * t)   # the entry's last buffeting
    env = (0.32 + 0.68 * buffet) * gust * flutter
    env *= np.clip(t / 0.05, 0, 1) * np.clip((seconds - t) / 0.6, 0, 1)
    out = a * env[:, None]
    return out / max(1e-6, np.abs(out).max()) * 0.7


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    write_flac(cabin_bed(), OUT / "jangala_arrival_cabin_bed.flac")
    write_flac(wind(), OUT / "jangala_arrival_wind.flac")
    print("wrote", OUT / "jangala_arrival_cabin_bed.flac", OUT / "jangala_arrival_wind.flac")


if __name__ == "__main__":
    main()
