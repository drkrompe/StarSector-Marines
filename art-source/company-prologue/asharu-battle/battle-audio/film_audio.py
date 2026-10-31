"""The Asharu film's sound as the game plays it: the locked mix (final-mix.json) baked into one clip
per sound, each a one-shot cue on the film's clock.

  python art-source/company-prologue/asharu-battle/battle-audio/film_audio.py --takes DIR

`--takes` is the Battle Cut page's published `sfx/` folder (the effects takes the owner mixed,
read back with the Artifact tool), and the voice takes are `voice-takes/` beside this script. The
plates' own sound is plate-audio/plate_audio.py's build/asharu-plate-audio/s0.wav and s13.wav.

Every playing line and cue becomes `asharu_film_<id>`, baked exactly as the page plays it
(asharu-cut.html's sound engine): its level, its smoothstep fades, its pan (Web Audio's
StereoPannerNode), cut at its out, a bed looped to its out, and a radio-bus sound sent through the
comms effect after its gain, as the page's bus does: a 330 Hz high-pass and a 3100 Hz low-pass
(Web Audio biquads, Q 1 dB), a tanh clipper at the Mixer's drive, then 0.85. The page clips the
radio bus's sum; a clip here clips its own sound, which only differs where two radio sounds overlap.
The Mixer's static and squelch are off (-60 and -40), so neither is baked.

The game's own explosions and the shield overload are not baked: their cues name the game's files
in sounds.json (read from the install, never copied), at the cue's level, unpanned.

Writes the clips (FLAC, 48 kHz stereo) under asset-pipeline/src/tool/resources/audio/asharu-battle/,
registers them in audio.json and every cue's sound in mod/data/config/sounds.json, and lists the
cues, by sequence time, in film-cues.json beside this script for film_export.py.
"""
import argparse
import json
import re
import subprocess
import sys
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
FINAL = HERE / "final-mix.json"
CUES_OUT = HERE / "film-cues.json"
CLIPS = ROOT / "asset-pipeline" / "src" / "tool" / "resources" / "audio" / "asharu-battle"
AUDIO_JSON = CLIPS.parent / "audio.json"
SOUNDS_JSON = ROOT / "mod" / "data" / "config" / "sounds.json"
PLATES = ROOT / "build" / "asharu-plate-audio"
OUT_DIR = "story/asharu-battle"
PREFIX = "asharu_film_"
SR = 48000
Q_DB = 1.0                 # Web Audio's default biquad Q, which a high- or low-pass reads in dB
FX_OUT = 0.85              # the page's radio bus output gain
# The game's own sounds a page take was made from: (file under sounds/, pitch, volume) per part,
# offset in seconds. The overload is overload_take.py's three parts, its loop faded over its last 0.8 s.
GAME = {
    "sfx/game-explosion_01.mp3": [(0.0, "sfx_impacts/explosion_01.ogg", 1.0, 1.0)],
    "sfx/game-explosion_02.mp3": [(0.0, "sfx_impacts/explosion_02.ogg", 1.0, 1.0)],
    "sfx/game-explosion_04.mp3": [(0.0, "sfx_impacts/explosion_04.ogg", 1.0, 1.0)],
    "sfx/game-explosion_secondary_01.mp3": [(0.0, "sfx_impacts/explosion_secondary_01.ogg", 1.0, 1.0)],
    "sfx/game-explosion_secondary_02.mp3": [(0.0, "sfx_impacts/explosion_secondary_02.ogg", 1.0, 1.0)],
    "sfx/game-explosion_secondary_03.mp3": [(0.0, "sfx_impacts/explosion_secondary_03.ogg", 1.0, 1.0)],
    "sfx/game-shield-overload.mp3": [(0.0, "sfx_impacts/shield_hit_heavy_01.ogg", 1.0, 0.7),
                                     (0.0, "sfx_shields/shields_burnout_oneshot.ogg", 1.0, 1.0),
                                     (0.3, "sfx_shields/shields_burnout_loop.ogg", 1.0, 0.6)],
}


def db(x):
    return 10 ** (x / 20)


def read(path, af=None):
    """A sound file as float stereo at SR, through an optional ffmpeg filter chain."""
    cmd = ["ffmpeg", "-v", "error", "-i", str(path)] + (["-af", af] if af else []) + \
          ["-ac", "2", "-ar", str(SR), "-f", "f32le", "-"]
    raw = subprocess.run(cmd, check=True, capture_output=True).stdout
    return np.frombuffer(raw, np.float32).astype(np.float64).reshape(-1, 2)


def write(x, path):
    pcm = (np.clip(x, -1, 1) * 32767).astype("<i2").tobytes()
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "s16le", "-ar", str(SR), "-ac", "2", "-i", "-",
                    "-c:a", "flac", str(path)], input=pcm, check=True)


def smooth(x):
    x = np.clip(x, 0, 1)
    return x * x * (3 - 2 * x)


def envelope(n, fade_in, fade_out):
    """The page's env(): smoothstep in from the start and out to the end, the lower of the two."""
    t = np.arange(n) / SR
    length = n / SR
    a = smooth(t / fade_in) if fade_in > 0 else np.ones(n)
    b = 1 - smooth((t - (length - fade_out)) / fade_out) if fade_out > 0 else np.ones(n)
    return np.minimum(a, b)


def pan(x, p):
    """StereoPannerNode on a stereo input (Web Audio spec)."""
    if not p:
        return x
    p = max(-1.0, min(1.0, p))
    left, right = x[:, 0], x[:, 1]
    if p <= 0:
        g = (p + 1) * np.pi / 2
        return np.stack([left + right * np.cos(g), right * np.sin(g)], axis=1)
    g = p * np.pi / 2
    return np.stack([left * np.cos(g), right + left * np.sin(g)], axis=1)


def radio(x, mixer):
    """The page's comms bus after an item's gain: the band, the clipper, the output gain."""
    q = db(Q_DB)
    raw = (np.clip(x, -64, 64)).astype("<f4").tobytes()
    band = subprocess.run(["ffmpeg", "-v", "error", "-f", "f32le", "-ar", str(SR), "-ac", "2", "-i", "-",
                           "-af", f"highpass=f={mixer['lo']}:width_type=q:width={q},"
                                  f"lowpass=f={mixer['hi']}:width_type=q:width={q}",
                           "-f", "f32le", "-"], input=raw, check=True, capture_output=True).stdout
    y = np.frombuffer(band, np.float32).astype(np.float64).reshape(-1, 2)
    k = mixer["drive"]
    return np.tanh(k * y) / np.tanh(k) * FX_OUT


def span(sound, seconds, loop):
    """The take cut, or for a bed looped, to `seconds`."""
    n = int(round(seconds * SR))
    if loop and len(sound) < n:
        sound = np.concatenate([sound] * (n // len(sound) + 1))
    return sound[:n]


def bake(sound, seconds, level_db, fade_in, fade_out, p, bus, mixer, loop=False):
    x = span(sound, seconds, loop)
    x = pan(x * db(level_db) * envelope(len(x), fade_in, fade_out)[:, None], p)
    return radio(x, mixer) if bus == "radio" else x


def cue_seconds(c, take):
    if c.get("t1", 0) > c["t0"]:
        return c["t1"] - c["t0"]
    if c.get("dur", 0) > 0:
        return c["dur"]
    return len(take) / SR


def sound_id(name):
    return PREFIX + re.sub(r"[^a-z0-9]+", "_", name.lower()).strip("_")


def register(baked, game):
    """audio.json and sounds.json: drop this film's old entries, add the current ones."""
    text = AUDIO_JSON.read_text(encoding="utf-8")
    lines = [ln for ln in text.split("\n") if f'"{PREFIX}' not in ln]
    end = max(i for i, ln in enumerate(lines) if ln.strip() == "]")
    last = max(i for i in range(end) if lines[i].strip().startswith("{"))
    lines[last] = lines[last].rstrip().rstrip(",") + ","
    new = [f'    {{"id": "{sid}", "source": "asharu-battle/{sid}.flac", "kind": "ui", "out": "{OUT_DIR}"}},'
           for sid in sorted(baked)]
    new[-1] = new[-1].rstrip(",")
    AUDIO_JSON.write_text("\n".join(lines[:last + 1] + new + lines[last + 1:]), encoding="utf-8", newline="\n")

    text = SOUNDS_JSON.read_text(encoding="utf-8")
    lines = [ln for ln in text.split("\n") if f'"{PREFIX}' not in ln]
    end = max(i for i, ln in enumerate(lines) if ln.strip() == "}")
    entries = [f'\t"{sid}": [{{"file": "sounds/{OUT_DIR}/{sid}.ogg", "pitch": 1, "volume": 1.0}}],' for sid in sorted(baked)]
    entries += [f'\t"{sid}": [{{"file": "sounds/{f}", "pitch": {pitch}, "volume": {vol:.4f}}}],'
                for sid, (f, pitch, vol) in sorted(game.items())]
    SOUNDS_JSON.write_text("\n".join(lines[:end] + entries + lines[end:]), encoding="utf-8", newline="\n")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--takes", required=True, help="the Battle Cut page's published sfx/ folder")
    args = ap.parse_args()
    takes = Path(args.takes)
    final = json.loads(FINAL.read_text(encoding="utf-8"))
    mixer = final["mixer"]
    CLIPS.mkdir(parents=True, exist_ok=True)
    for old in CLIPS.glob(PREFIX + "*.flac"):
        old.unlink()
    cues, baked, game = [], set(), {}

    def put(sid, x, t):
        write(x, CLIPS / (sid + ".flac"))
        baked.add(sid)
        peak = float(np.abs(x).max())
        cues.append({"t": round(t, 4), "sound": sid, "volume": 1.0})
        print("%-34s %8.2f s  %5.2f s  peak %6.1f dB%s" % (sid, t, len(x) / SR, 20 * np.log10(max(peak, 1e-9)),
                                                            "  CLIPPED" if peak > 1 else ""))

    for lid, ln in sorted(final["lines"].items()):
        if ln.get("off") or not ln.get("voice"):
            continue
        take = read(HERE / ln["voice"])
        seconds = min(ln["t1"] - ln["t0"], len(take) / SR)
        x = bake(take, seconds, (ln.get("gain") or 0) + mixer["voice"], ln.get("fadeIn") or 0,
                 ln.get("fadeOut") or 0, 0, ln.get("bus") or "radio", mixer)
        put(sound_id(lid), x, ln["t0"])

    for cid, c in sorted(final["cues"].items()):
        if c.get("off") or not c.get("take"):
            continue
        level = (c.get("level") or 0) + mixer["sfx"]
        if c["take"] in GAME:
            for k, (dt, f, pitch, vol) in enumerate(GAME[c["take"]]):
                sid = sound_id(cid) + ("" if k == 0 else f"_{k + 1}")
                game[sid] = (f, pitch, min(1.0, vol * db(level)))
                cue = {"t": round(c["t0"] + dt, 4), "sound": sid, "volume": 1.0}
                if f.endswith("shields_burnout_loop.ogg"):     # held to the take's end, faded as it was
                    cue.update(until=round(c["t0"] + c["dur"], 4), fade_out=0.8)
                cues.append(cue)
                print("%-34s %8.2f s  the game's %s" % (sid, cue["t"], f))
            continue
        take = read(takes / Path(c["take"]).name)
        x = bake(take, cue_seconds(c, take), level, c.get("fadeIn") or 0, c.get("fadeOut") or 0, c.get("pan") or 0,
                 c.get("bus") or "full", mixer, loop=c.get("kind") == "bed")
        put(sound_id(cid), x, c["t0"])

    for shot, start in (("s0", -21.0), ("s13", 138.0)):
        put(sound_id("plate_" + shot), read(PLATES / f"{shot}.wav") * db(mixer["plates"]), start)

    register(baked, game)
    cues.sort(key=lambda c: (c["t"], c["sound"]))
    CUES_OUT.write_text(json.dumps({"cues": cues}, indent=1) + "\n", encoding="utf-8", newline="\n")
    print(len(baked), "clips,", len(game), "game sounds,", len(cues), "cues ->", CUES_OUT)


if __name__ == "__main__":
    sys.exit(main())
