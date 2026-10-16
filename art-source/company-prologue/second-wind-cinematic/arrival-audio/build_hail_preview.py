"""Audition mixes of the Jangala Approach hail (storyboard-v3.md, shots C1-C2).

python art-source/company-prologue/second-wind-cinematic/arrival-audio/build_hail_preview.py

Reads the treated clips that `:asset-pipeline:processAudio` writes to
mod/sounds/story/jangala-arrival/ and the clean takes beside their receipts, and
writes three MP3s to build/audio-previews/jangala-hail/:

  cabin.mp3  treated turns, squelch on Approach's transmissions, over a cabin bed
  radio.mp3  the same timeline without the bed, for intelligibility
  clean.mp3  the clean takes on the same timeline, for performance

Nothing here is paid and nothing ships. The bed and squelch are audition stand-ins
built from repository ambience and seeded noise, not final sound design.
Requires ffmpeg on PATH.
"""
import json
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
CATALOG = ROOT / "mod/data/marines/jangala_arrival_hail.json"
TREATED = ROOT / "mod/sounds/story/jangala-arrival"
CLEAN = ROOT / "asset-pipeline/src/tool/resources/audio/jangala-arrival"
AMBIENCE = ROOT / "asset-pipeline/src/tool/resources/audio/ambient-machinery"
OUT = ROOT / "build/audio-previews/jangala-hail"

LEAD_IN = 3.0          # bed alone before the first call
TURN_GAP = 0.6         # silence between turns
SQUELCH_OPEN = 0.16    # key-up burst before an Approach transmission
SQUELCH_CLOSE = 0.22   # key-down tail after it
OFF_CHANNEL_GAP = 1.4  # Castle closes the channel, then speaks to the room
TAIL = 3.0
APPROACH = "Jangala Approach"


def duration(path):
    out = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration",
                          "-of", "default=nw=1:nk=1", str(path)], check=True, capture_output=True, text=True)
    return float(out.stdout.strip())


def timeline(lines):
    """Start time of each line, and of each squelch burst, in seconds."""
    t, starts, squelch = LEAD_IN, [], []
    for i, line in enumerate(lines):
        if i == len(lines) - 1:
            t += OFF_CHANNEL_GAP - TURN_GAP
        radio = line["speaker"] == APPROACH
        if radio:
            squelch.append(("open", t))
            t += SQUELCH_OPEN
        starts.append(t)
        t += duration(TREATED / (line["id"] + ".ogg"))
        if radio:
            squelch.append(("close", t))
            t += SQUELCH_CLOSE
        t += TURN_GAP
    return starts, squelch, t + TAIL


def squelch_source(kind, index):
    # Seeded band-limited noise with a hard click at the edge; the open burst decays fast,
    # the close tail a little slower, like a carrier dropping.
    length = SQUELCH_OPEN if kind == "open" else SQUELCH_CLOSE
    fade = ("afade=t=out:st=0.03:d=%.2f" % (length - 0.03) if kind == "open"
            else "afade=t=in:d=0.01,afade=t=out:st=0.02:d=%.2f" % (length - 0.02))
    return ("anoisesrc=d=%.2f:c=white:a=0.5:seed=%d:r=44100,highpass=f=500:p=2,lowpass=f=2200:p=2,"
            "%s,volume=0.35,aformat=channel_layouts=stereo" % (length, 41 + index, fade))


def mix(lines, folder, suffix, bed, name):
    starts, squelch, total = timeline(lines)
    inputs, graph, labels = [], [], []
    for i, line in enumerate(lines):
        inputs += ["-i", str(folder / (line["id"] + suffix))]
        graph.append("[%d:a]aformat=sample_rates=44100:channel_layouts=stereo,adelay=%d:all=1[v%d]"
                     % (i, round(starts[i] * 1000), i))
        labels.append("[v%d]" % i)
    n = len(lines)
    if suffix == ".ogg":
        for j, (kind, at) in enumerate(squelch):
            inputs += ["-f", "lavfi", "-i", squelch_source(kind, j)]
            graph.append("[%d:a]adelay=%d:all=1[s%d]" % (n + j, round(at * 1000), j))
            labels.append("[s%d]" % j)
        n += len(squelch)
    if bed:
        # Structural rumble from the train recording, kept below the radio band, with
        # ventilation from the fan; both looped under the whole exchange.
        inputs += ["-stream_loop", "-1", "-i", str(AMBIENCE / "subwayTrain_B-line.wav"),
                   "-stream_loop", "-1", "-i", str(AMBIENCE / "fan_noise.wav")]
        graph.append("[%d:a]lowpass=f=320:p=2,volume=0.55,aformat=sample_rates=44100:channel_layouts=stereo[r]" % n)
        graph.append("[%d:a]highpass=f=200,lowpass=f=4000,volume=0.18,aformat=sample_rates=44100:channel_layouts=stereo[f]" % (n + 1))
        labels += ["[r]", "[f]"]
    graph.append("%samix=inputs=%d:normalize=0:duration=longest,atrim=0:%.2f,"
                 "afade=t=in:d=1.0,afade=t=out:st=%.2f:d=1.5,alimiter=limit=0.9[out]"
                 % ("".join(labels), len(labels), total, total - 1.5))
    OUT.mkdir(parents=True, exist_ok=True)
    target = OUT / name
    subprocess.run(["ffmpeg", "-hide_banner", "-v", "error", "-y", *inputs, "-filter_complex", ";".join(graph),
                    "-map", "[out]", "-c:a", "libmp3lame", "-q:a", "2", str(target)], check=True)
    print("Wrote", target.relative_to(ROOT), "%.1fs" % total)


def main():
    lines = json.loads(CATALOG.read_text(encoding="utf-8"))["lines"]
    missing = [l["id"] for l in lines if not (TREATED / (l["id"] + ".ogg")).exists()]
    if missing:
        raise SystemExit("Run :asset-pipeline:processAudio first; missing " + ", ".join(missing))
    mix(lines, TREATED, ".ogg", True, "cabin.mp3")
    mix(lines, TREATED, ".ogg", False, "radio.mp3")
    mix(lines, CLEAN, ".mp3", False, "clean.mp3")


if __name__ == "__main__":
    main()
