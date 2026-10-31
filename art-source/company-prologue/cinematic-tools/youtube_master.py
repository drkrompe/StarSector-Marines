"""A film as one video to post (YouTube), from its approved masters rather than the shipped encodes.

  python art-source/company-prologue/cinematic-tools/youtube_master.py jangala-arrival|asharu-battle

writes build/youtube/<film>.mp4 and build/youtube/<film>.en.srt:

  picture   each shot's approved final, as its exporter lists it: the Jangala arrival's PNG frames
            off Drive (cinematic_export.FINALS), the Asharu film's Drive masters and its terminal
            plates (film_export.FINALS, PLATE_FRAMES; render the plates first, as film_export says).
            H.264 High, crf 16, half-second closed GOPs, BT.709: YouTube's recommended upload.
  sound     the exporter's own mix of the shipped timelines, as the player plays them
            (arrival_mix.sequence_mix, film_export.film_mix), scaled as a whole so its peak sits at
            -1 dBFS (never raised), AAC 384 kb/s at 48 kHz.
  captions  the shipped timelines' captions in English (strings.json), speaker first, as a sidecar
            to upload, not burned in. What the game draws over the picture (the arrival's lock) is
            not in the video.
"""
import json
import math
import subprocess
import sys
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
MOD = ROOT / "mod"
OUT = ROOT / "build" / "youtube"
FPS = 24
CRF = 16
HEADROOM_DB = -1.0
FILMS = {"jangala-arrival": HERE.parent / "second-wind-cinematic", "asharu-battle": HERE.parent / "asharu-battle"}


def timelines(film):
    """[(shot id, its timeline, its start on the film's clock)] in sequence order."""
    data = MOD / "data" / "cinematic" / film
    out, at = [], 0.0
    for sid in json.loads((data / "sequence.json").read_text(encoding="utf-8"))["shots"]:
        tl = json.loads((data / (sid + ".json")).read_text(encoding="utf-8"))
        out.append((sid, tl, at))
        at += tl["frames"] / FPS
    return out


def jangala(work):
    import arrival_mix
    import cinematic_export
    inputs = []
    for sid, tl, _at in timelines("jangala-arrival"):
        frames = {}
        for folder in cinematic_export.FINALS[sid]:
            frames.update(cinematic_export.final_frames(cinematic_export.DRIVE_BUILDS / folder))
        if sorted(frames) != list(range(tl["frames"])):
            raise SystemExit("%s: %d final frames, the timeline has %d" % (sid, len(frames), tl["frames"]))
        listing = work / (sid + ".ffconcat")
        listing.write_text("ffconcat version 1.0\n" + "".join(
            "file '%s'\nduration %r\n" % (frames[i].as_posix(), 1 / FPS) for i in range(len(frames))), encoding="utf-8")
        inputs.append(["-f", "concat", "-safe", "0", "-i", str(listing)])
    mix, _offsets, _tls = arrival_mix.sequence_mix([sid for sid, _tl, _at in timelines("jangala-arrival")])
    return inputs, mix, arrival_mix.SR


def asharu(work):
    import film_export
    inputs = []
    for sid, _tl, _at in timelines("asharu-battle"):
        if sid in film_export.PLATE_FRAMES:
            pattern, first = film_export.PLATE_FRAMES[sid]
            if not (film_export.PLATES / sid / (pattern % first)).is_file():
                raise SystemExit("%s: no plate frames in %s" % (sid, film_export.PLATES / sid))
            inputs.append(["-framerate", str(FPS), "-start_number", str(first), "-i", str(film_export.PLATES / sid / pattern)])
        else:
            inputs.append(["-i", str(film_export.DRIVE / film_export.FINALS[sid])])
    return inputs, film_export.film_mix(), film_export.SR


def srt(film, path):
    lines = (MOD / "data" / "strings" / "strings.json").read_text(encoding="utf-8").splitlines()
    flat = json.loads("\n".join(l for l in lines if not l.lstrip().startswith("#")))["marineOps"]   # # comment lines
    cues = []
    for _sid, tl, at in timelines(film):
        for c in tl["captions"]:
            who = tl["speakers"].get(c["speaker"], {}).get("label_key")
            text = flat[c["key"]]
            cues.append((at + c["t_in"], at + c["t_out"], (flat[who] + ": " if who else "") + text))

    def stamp(t):
        ms = int(round(t * 1000))
        return "%02d:%02d:%02d,%03d" % (ms // 3600000, ms // 60000 % 60, ms // 1000 % 60, ms % 1000)
    path.write_text("".join("%d\n%s --> %s\n%s\n\n" % (i + 1, stamp(a), stamp(b), text)
                            for i, (a, b, text) in enumerate(sorted(cues))), encoding="utf-8")
    return len(cues)


def main(argv):
    film = argv[0] if argv else None
    if film not in FILMS:
        raise SystemExit(__doc__)
    sys.path.insert(0, str(FILMS[film]))
    work = OUT / film
    work.mkdir(parents=True, exist_ok=True)
    inputs, mix, sr = (jangala if film == "jangala-arrival" else asharu)(work)
    frames = sum(tl["frames"] for _sid, tl, _at in timelines(film))
    mix = np.asarray(mix, np.float64)[:int(math.ceil(frames / FPS * sr))]
    peak = float(np.abs(mix).max())
    gain = min(1.0, 10 ** (HEADROOM_DB / 20) / peak)
    wav = work / "mix.wav"
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "f32le", "-ar", str(sr), "-ac", "2", "-i", "-",
                    "-c:a", "pcm_f32le", str(wav)], input=(mix * gain).astype("<f4").tobytes(), check=True)
    n = len(inputs)
    chain = "".join("[%d:v]fps=%d,scale=out_color_matrix=bt709:out_range=tv,format=yuv420p,setsar=1[v%d];" % (i, FPS, i)
                    for i in range(n))
    chain += "".join("[v%d]" % i for i in range(n)) + "concat=n=%d:v=1:a=0[v]" % n
    target = OUT / (film + ".mp4")
    subprocess.run(["ffmpeg", "-hide_banner", "-v", "error", "-stats", "-y", *[a for i in inputs for a in i],
                    "-i", str(wav), "-filter_complex", chain, "-map", "[v]", "-map", "%d:a" % n,
                    "-c:v", "libx264", "-preset", "slow", "-crf", str(CRF), "-profile:v", "high", "-r", str(FPS),
                    "-g", str(FPS // 2), "-bf", "2", "-x264-params", "open-gop=0",
                    "-colorspace", "bt709", "-color_primaries", "bt709", "-color_trc", "bt709",
                    "-c:a", "aac", "-b:a", "384k", "-ar", "48000", "-shortest", "-movflags", "+faststart",
                    str(target)], check=True)
    got = int(subprocess.run(["ffprobe", "-v", "error", "-select_streams", "v:0", "-count_packets", "-show_entries",
                              "stream=nb_read_packets", "-of", "csv=p=0", str(target)],
                             capture_output=True, text=True, check=True).stdout)
    if got != frames:
        raise SystemExit("%s: %d frames, the film is %d" % (target, got, frames))
    lufs = subprocess.run(["ffmpeg", "-hide_banner", "-nostats", "-i", str(target), "-map", "0:a", "-af", "ebur128=peak=true",
                           "-f", "null", "-"], capture_output=True, text=True).stderr
    summary = lufs[lufs.rfind("Summary:"):]
    integrated = summary.split("I:")[1].split("LUFS")[0].strip()
    true_peak = summary.split("Peak:")[1].split("dBFS")[0].strip()
    captions = srt(film, OUT / (film + ".en.srt"))
    print("%s: %d frames (%.1fs), %.1f MB; mix peak %.1f dB scaled by %.1f dB, %s LUFS, true peak %s dBFS; %d captions"
          % (target, frames, frames / FPS, target.stat().st_size / 1e6, 20 * math.log10(peak), 20 * math.log10(gain),
             integrated, true_peak, captions))


if __name__ == "__main__":
    main(sys.argv[1:])
