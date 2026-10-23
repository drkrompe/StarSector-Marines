"""The cue sheet's effects as the game's sounds: one clip per layer of `sfx_mix.SHOTS`.

    python art-source/company-prologue/second-wind-cinematic/arrival-audio/build_arrival_stems.py

`sfx_mix.py` is the cue sheet (`sfx-cues.md`) as data: every effect layer's take (the chosen one in
sfx-manifest.json, or a synth_flyby / synth_landing stem), its time, its level against dialogue,
and the offset, filter, loop, fades and dB envelope that shape it. The player cannot draw an
envelope, so each layer is rendered here exactly as `sfx_mix.place` lays it, into a clip of its
own that starts where the layer starts, and the game plays that clip as a one-shot.

Each clip is peak-normalised to PEAK; the gain that takes it back to the sheet's level is written
to arrival-stems.json, and from there to arrival-mix.json as the cue's starting `gain_db`, so 0 dB
on the mix is the loudest a clip can play and the sheet's level is the number already there. A
layer the sheet wants louder than a full-scale clip is held at 0 dB and reported.

C4 and C5 are one 61.4 s shot on the sheet (C4_LENGTH 21.4 + C5's 40). A layer starting before
21.4 is a C4 cue and plays on across the cut; one starting after is a C5 cue at its time - 21.4.

Writes the clips to asset-pipeline/src/tool/resources/audio/jangala-arrival/sfx/ (sources for
`:asset-pipeline:processAudio`), registers them in audio.json and sounds.json, and writes
arrival-stems.json. Deterministic for the same takes and code.
"""
import json
import re
import subprocess
import sys
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import sfx_mix  # noqa: E402
import synth_flyby  # noqa: E402
import synth_landing  # noqa: E402

ROOT = sfx_mix.ROOT
SR = sfx_mix.SR
AUDIO = ROOT / "asset-pipeline" / "src" / "tool" / "resources" / "audio"
STEM_DIR = AUDIO / "jangala-arrival" / "sfx"
AUDIO_JSON = AUDIO / "audio.json"
SOUNDS = ROOT / "mod" / "data" / "config" / "sounds.json"
STEMS = HERE / "arrival-stems.json"
SYNTH = ROOT / "build" / "audio-previews" / "arrival-stems" / "synth"
PEAK = 10 ** (-1 / 20)       # each clip's peak, -1 dBFS
SILENT = 10 ** (-70 / 20)    # a clip quieter than this throughout is left out
PREFIX = "jangala_sfx_"
SPLIT = sfx_mix.C4_LENGTH    # the joined C4-C5 clock's cut


def render(layer, path, length):
    """(start, samples): the layer as sfx_mix.place lays it on a shot of `length` seconds, but only
    its own span, from where it starts (never before the shot) to where it ends (never after)."""
    clip = sfx_mix.decode(path, layer.get("af"))
    if layer.get("match", True):
        clip = clip * 10 ** ((sfx_mix.DIALOGUE_LUFS + layer["level"] - sfx_mix.loudness(path)) / 20)
    start = layer["t"] if layer["t"] is not None else layer["ends"] - len(clip) / SR
    clip = clip[int(layer.get("offset", 0.0) * SR):]
    until = layer.get("until")
    if until is not None:
        want = int(round((until - start) * SR))
        if want > len(clip) and (layer.get("loop") or layer["kind"] == "sfx" and layer.get("cue") in sfx_mix.BEDS):
            clip = np.concatenate([clip] * (want // len(clip) + 1))
        clip = clip[:want]
    n = len(clip)
    times = start + np.arange(n) / SR
    gain = np.ones(n)
    for edge, seconds in (("in", layer.get("fade_in", 0.02)), ("out", layer.get("fade_out", 0.05))):
        k = min(n, int(seconds * SR))
        if k:
            ramp = np.linspace(0, 1, k)
            if edge == "in":
                gain[:k] *= ramp
            else:
                gain[n - k:] *= ramp[::-1]
    if layer.get("env"):
        pts = np.array(layer["env"], float)
        gain *= 10 ** (np.interp(times, pts[:, 0], pts[:, 1]) / 20)
    clip = clip * gain[:, None]
    a = int(round(start * SR))
    lo, hi = max(a, 0), min(a + n, int(round(length * SR)))
    return lo / SR, clip[lo - a:hi - a]


def layer_name(layer):
    return layer.get("synth") or layer["cue"]


def placements():
    """[(shot, t on that shot's clock, name, layer, sheet shot, sheet length)] for every effect layer."""
    out = []
    for sheet in ("c1", "c2", "entry", "c3", "c4c5"):
        length, layers = sfx_mix.SHOTS[sheet]
        for layer in layers:
            if layer["kind"] != "sfx":
                continue
            out.append((sheet, layer, length))
    return out


def write_ogg(samples, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "f32le", "-ar", str(SR), "-ac", "2", "-i", "-",
                    "-ar", "44100", "-c:a", "libvorbis", "-q:a", "6", "-map_metadata", "-1", str(path)],
                   input=samples.astype(np.float32).tobytes(), check=True)


def register(ids):
    """Every clip in audio.json and sounds.json, replacing the block a previous run wrote."""
    begin, end = "// arrival-stems: begin (build_arrival_stems.py)", "// arrival-stems: end"
    rows = "".join('    {"id": "%s", "source": "jangala-arrival/sfx/%s.ogg", "kind": "ui", "out": "story/jangala-arrival/sfx"},\n'
                   % (i, i) for i in ids)
    text = AUDIO_JSON.read_text(encoding="utf-8")
    text = re.sub(r"    %s\n.*?    %s\n" % (re.escape(begin), re.escape(end)), "", text, flags=re.S)
    anchor = '    {"id": "jangala_arrival_wind",'
    i = text.index(anchor)
    text = text[:i] + "    %s\n%s    %s\n" % (begin, rows, end) + text[i:]
    AUDIO_JSON.write_text(text, encoding="utf-8")

    begin, end = "# arrival-stems: begin (build_arrival_stems.py)", "# arrival-stems: end"
    rows = "".join('\t"%s": [{"file": "sounds/story/jangala-arrival/sfx/%s.ogg", "pitch": 1, "volume": 1.0}],\n'
                   % (i, i) for i in ids)
    text = SOUNDS.read_text(encoding="utf-8")
    text = re.sub(r"\t%s\n.*?\t%s\n" % (re.escape(begin), re.escape(end)), "", text, flags=re.S)
    anchor = '\t"jangala_arrival_wind":'
    i = text.index(anchor)
    text = text[:i] + "\t%s\n%s\t%s\n" % (begin, rows, end) + text[i:]
    SOUNDS.write_text(text, encoding="utf-8")


def main():
    manifest = json.loads(sfx_mix.MANIFEST.read_text(encoding="utf-8"))
    chosen = {cue: row["chosen"] for cue, row in manifest["cues"].items() if row["chosen"]}
    sfx_mix.BEDS.update(cue for cue, row in manifest["cues"].items() if row["loop"])
    synth_flyby.build(SYNTH)
    synth_landing.build(SYNTH)
    dirs = {"synth": SYNTH}
    for old in STEM_DIR.glob("*.ogg"):
        old.unlink()
    rows, counts, held, total = [], {}, [], 0.0
    for sheet, layer, length in placements():
        start, clip = render(layer, sfx_mix.source(layer, dirs, chosen), length)
        loud = np.flatnonzero(np.abs(clip).max(axis=1) >= SILENT) if len(clip) else []
        if len(loud):
            # Leading and trailing silence (a synth pass sits on the whole shot clock) costs memory
            # in the game and nothing else: the clip starts at its first sound.
            margin = int(0.01 * SR)
            a, b = max(0, loud[0] - margin), min(len(clip), loud[-1] + margin + 1)
            start, clip = start + a / SR, clip[a:b]
        peak = float(np.abs(clip).max()) if len(clip) else 0.0
        if peak < SILENT:
            print("  %-6s %-22s silent on the sheet, left out" % (sheet, layer_name(layer)))
            continue
        if sheet == "c4c5":
            shot, t = ("c4", start) if start < SPLIT else ("c5", start - SPLIT)
        else:
            shot, t = sheet, start
        name = layer_name(layer)
        key = (shot, name)
        counts[key] = counts.get(key, 0) + 1
        bare = name[len(shot) + 1:] if name.startswith(shot + "_") else name
        sid = PREFIX + "%s_%s" % (shot, bare) + ("_%d" % counts[key] if counts[key] > 1 else "")
        gain_db = 20 * np.log10(peak / PEAK)
        if gain_db > 0:
            held.append("%s %+.1f dB" % (sid, gain_db))
            gain_db = 0.0
        write_ogg(clip / peak * PEAK, STEM_DIR / (sid + ".ogg"))
        seconds = len(clip) / SR
        total += seconds
        rows.append({"shot": shot, "t": round(t, 3), "sound": sid, "gain_db": round(float(gain_db), 1),
                     "seconds": round(seconds, 2), "layer": name})
        print("  %-5s %6.2fs %-34s %5.1fs  %+6.1f dB" % (shot, t, sid, seconds, gain_db))
    rows.sort(key=lambda r: (["c1", "c2", "entry", "c3", "c4", "c5"].index(r["shot"]), r["t"], r["sound"]))
    STEMS.write_text(json.dumps({"_doc": "Written by build_arrival_stems.py; each row is one cue of the "
                                         "cue sheet as a one-shot clip. Levels are edited in arrival-mix.json.",
                                 "stems": rows}, indent=1) + "\n", encoding="utf-8")
    register([r["sound"] for r in rows])
    print("%d clips, %.0f s of audio (%.0f MB as 44.1 kHz stereo PCM)" % (len(rows), total, total * 44100 * 4 / 1e6))
    for h in held:
        print("  held at 0 dB, the sheet wants more than a full-scale clip:", h)


if __name__ == "__main__":
    main()
