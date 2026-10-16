"""Cabin bed and radio squelches for the Jangala arrival (storyboard-v3.md, shots C1-C2).

python art-source/company-prologue/second-wind-cinematic/arrival-audio/make_arrival_sounds.py

Writes the three source clips that audio.json builds into mod sounds, beside the hail takes in
asset-pipeline/src/tool/resources/audio/jangala-arrival/:

  cabin_bed.wav      a seamless 16 s loop, the same recipe as build_hail_preview's bed
  squelch_open.wav   the key-up burst before an Approach transmission
  squelch_close.wav  the key-down tail after it

The player schedules them as cues (see runtime-contract.md), so the dialogue stays separately
mixable. Seeded and deterministic. Requires ffmpeg on PATH.
"""
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import build_hail_preview as hail  # noqa: E402

OUT = hail.CLEAN
LOOP = 16.0
JOIN = 2.0


def ffmpeg(*args):
    subprocess.run(["ffmpeg", "-hide_banner", "-v", "error", "-y", *args], check=True)


def squelch(kind, index):
    ffmpeg("-f", "lavfi", "-i", hail.squelch_source(kind, index), "-ar", "44100", "-ac", "2", str(OUT / ("squelch_%s.wav" % kind)))
    print("Wrote", OUT / ("squelch_%s.wav" % kind))


def cabin_bed():
    n = LOOP + JOIN
    # Structural rumble from the train recording below the radio band, with fan ventilation,
    # as in the audition mix; the first JOIN seconds are cross-faded onto the tail so the file
    # loops without a seam.
    graph = (
        "[0:a]lowpass=f=320:p=2,volume=0.55,aformat=sample_rates=44100:channel_layouts=stereo[r];"
        "[1:a]highpass=f=200,lowpass=f=4000,volume=0.18,aformat=sample_rates=44100:channel_layouts=stereo[f];"
        "[r][f]amix=inputs=2:normalize=0:duration=longest,atrim=0:%(n)s,asetpts=N/SR/TB,asplit=3[a][b][c];"
        "[a]atrim=0:%(j)s,asetpts=N/SR/TB,afade=t=in:d=%(j)s[head];"
        "[b]atrim=%(l)s:%(n)s,asetpts=N/SR/TB,afade=t=out:d=%(j)s[tail];"
        "[head][tail]amix=inputs=2:normalize=0:duration=longest[join];"
        "[c]atrim=%(j)s:%(l)s,asetpts=N/SR/TB[body];"
        "[join][body]concat=n=2:v=0:a=1[out]" % {"n": n, "j": JOIN, "l": LOOP})
    ffmpeg("-stream_loop", "-1", "-i", str(hail.AMBIENCE / "subwayTrain_B-line.wav"),
           "-stream_loop", "-1", "-i", str(hail.AMBIENCE / "fan_noise.wav"),
           "-filter_complex", graph, "-map", "[out]", "-t", str(LOOP), "-ar", "44100", "-ac", "2", str(OUT / "cabin_bed.wav"))
    print("Wrote", OUT / "cabin_bed.wav")


if __name__ == "__main__":
    squelch("open", 0)
    squelch("close", 1)
    cabin_bed()
