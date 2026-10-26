"""Quiet-bed candidates for the battle music, generated with ElevenLabs' music model.

    python art-source/audio/battle-music/make_quiet_beds.py plan
    python art-source/audio/battle-music/make_quiet_beds.py generate [--candidate ID ...]
    python art-source/audio/battle-music/make_quiet_beds.py loop TAKE [--bars N] [--start-bar N]

The quiet bed plays under a battle before the fighting (battle-music-director.md): the Dust
Ledger's walk-in, any battle's pre-contact quiet, and the aftermath. Owner, 2026-10-26: "more
neutral music loops that tie in to action ... something scifi synthy like 90s action". So each
candidate is tense but not combat, retro analog synth, and sits in the combat pool's family
(measured around 130 BPM, E minor and its neighbours) so the alarm's crossfade lands naturally.

A take is one composition plan timed in bars at TEMPO, so its sections fall on bar lines. `loop`
cuts a bar-aligned span out of a take and crossfades the bars after it into its head, so the
file wraps without a seam; that loop is what the asset pipeline encodes, so it is written straight
into its music sources as quiet-bed-<take>.ogg (audio.json, QuietBed).

Takes go to takes/<id>_tNN.mp3 with a JSON sidecar holding the request, hash and measured
loudness; each loop's receipt to loops/<id>_tNN_loop.json. Requires ffmpeg/ffprobe on
PATH and ELEVENLABS_API_KEY in the environment (never printed). Prompts describe the style in
plain musical terms and never name a work or an artist.
"""

import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
import urllib.error
import urllib.request
from pathlib import Path

HERE = Path(__file__).resolve().parent
TAKES = HERE / "takes"
LOOPS = HERE / "loops"
SHIPPED = HERE.parents[2] / "asset-pipeline" / "src" / "tool" / "resources" / "audio" / "music"
MODEL = "music_v1"
TEMPO = 130
BAR_MS = 4 * 60000 / TEMPO
CROSSFADE_SECONDS = 2.0

# Shared by every candidate: the family and what a bed must not do.
FAMILY = ["instrumental retro science-fiction synth score, 1990s action film sound",
          "analog synthesizers, warm detuned pads, tape-saturated",
          "130 BPM with a half-time feel, E minor",
          "tense and watchful but not yet fighting, steady, neutral, leaves room for sound effects",
          "a seamless loop: the same energy from the first bar to the last, no intro build and no ending"]
NEGATIVE = ["vocals", "choir", "lyrics", "orchestra", "heroic brass fanfare", "big drum fills", "crash cymbals",
            "guitar solo", "dubstep drop", "fade in", "fade out", "silence", "final chord", "fast tempo"]

# Each candidate: its own colour, then sections in bars (A, B, A again, and four bars that turn
# back into A so the loop cut has A's texture on both sides).
CANDIDATES = {
    "sequencer_pulse": {
        "styles": ["a pulsing eighth-note analog bass sequence drives it",
                   "an arpeggiated synth ostinato in sixteenths, filtered and slowly opening and closing",
                   "a restrained drum machine: soft kick on one and three, a gated snare on three, ticking closed hi-hat",
                   "cold, mechanical, precise"],
        "sections": [("Pulse", 16, ["bass sequence and arpeggio over a low pad", "drums light"]),
                     ("Lift", 16, ["the arpeggio filter opens a little brighter", "a second pad layer holds long minor chords",
                                   "a soft metallic stab every two bars"]),
                     ("Pulse again", 16, ["back to the opening texture", "bass sequence and arpeggio over a low pad"]),
                     ("Turn", 8, ["same groove and level as the opening, leading straight back into it"])],
    },
    "frontier_twang": {
        "styles": ["a lonely reverb-drenched clean electric guitar with tremolo plays sparse twangy notes",
                   "a pulsing synth bass in eighths and a low analog drone underneath",
                   "dusty frontier-colony mood with industrial metal clanks and a slow mechanical half-time beat",
                   "gritty, working-class, military outpost"],
        "sections": [("Outpost", 16, ["guitar phrases with space between them over the bass pulse"]),
                     ("Patrol", 16, ["a quiet arpeggiated synth joins under the guitar", "the beat firms up slightly"]),
                     ("Outpost again", 16, ["back to the opening texture", "guitar phrases over the bass pulse"]),
                     ("Turn", 8, ["same groove and level as the opening, leading straight back into it"])],
    },
    "night_ops": {
        "styles": ["dark and sparse: a deep sub-bass pulse on every beat", "a filtered arpeggio rising out of the murk",
                   "a ticking hi-hat and a distant reverberant snare, minimal",
                   "sonar-like pings and cold glassy synth swells", "stealthy, patient, something is about to happen"],
        "sections": [("Murk", 16, ["sub pulse, ticking hats and pings", "arpeggio barely audible"]),
                     ("Closer", 16, ["the arpeggio comes forward", "a low string-like synth pad swells under it"]),
                     ("Murk again", 16, ["back to the opening texture", "sub pulse, ticking hats and pings"]),
                     ("Turn", 8, ["same groove and level as the opening, leading straight back into it"])],
    },
    "briefing_drive": {
        "styles": ["a confident steady mid-tempo synth groove", "a punchy analog bass riff in syncopated eighths",
                   "bright arpeggiated synth chords and a soft lead line that hints at a theme without resolving it",
                   "tight drum machine with rimshots and a shaker", "purposeful, mission-ready, the calm before deployment"],
        "sections": [("Groove", 16, ["bass riff, arpeggiated chords and drums"]),
                     ("Theme hint", 16, ["the soft lead line states a short motif twice, held back"]),
                     ("Groove again", 16, ["back to the opening texture", "bass riff, arpeggiated chords and drums"]),
                     ("Turn", 8, ["same groove and level as the opening, leading straight back into it"])],
    },
}


def credential():
    key = os.environ.get("ELEVENLABS_API_KEY")
    if not key and sys.platform == "win32":
        import winreg
        try:
            with winreg.OpenKey(winreg.HKEY_CURRENT_USER, "Environment") as env:
                key = winreg.QueryValueEx(env, "ELEVENLABS_API_KEY")[0]
        except OSError:
            pass
    if not key:
        raise SystemExit("ELEVENLABS_API_KEY is unavailable; no request sent.")
    return key


def probe(path):
    out = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration",
                          "-of", "default=nw=1:nk=1", str(path)], check=True, capture_output=True, text=True)
    return round(float(out.stdout.strip()), 2)


def levels(path):
    """Peak and integrated loudness, from one ffmpeg pass."""
    err = subprocess.run(["ffmpeg", "-hide_banner", "-nostats", "-i", str(path),
                          "-af", "volumedetect,ebur128=peak=true", "-f", "null", "-"],
                         capture_output=True, text=True).stderr
    peak = float(re.search(r"max_volume: (-?[\d.]+) dB", err).group(1))
    lufs = re.findall(r"I:\s+(-?[\d.]+) LUFS", err)
    return peak, float(lufs[-1]) if lufs else None


def plan(candidate):
    spec = CANDIDATES[candidate]
    return {"positive_global_styles": FAMILY + spec["styles"], "negative_global_styles": NEGATIVE,
            "sections": [{"section_name": name, "positive_local_styles": styles, "negative_local_styles": [],
                          "duration_ms": int(round(bars * BAR_MS)), "lines": []}
                         for name, bars, styles in spec["sections"]]}


def takes(candidate):
    return sorted(TAKES.glob(candidate + "_t[0-9][0-9].mp3"))


def generate(candidates):
    key = credential()
    TAKES.mkdir(parents=True, exist_ok=True)
    for candidate in candidates:
        payload = {"composition_plan": plan(candidate), "model_id": MODEL, "respect_sections_durations": True}
        audio = TAKES / ("%s_t%02d.mp3" % (candidate, len(takes(candidate)) + 1))
        request = urllib.request.Request(
            "https://api.elevenlabs.io/v1/music?output_format=mp3_44100_128",
            data=json.dumps(payload).encode(), headers={"xi-api-key": key, "Content-Type": "application/json"})
        try:
            with urllib.request.urlopen(request, timeout=300) as response:
                data = response.read()
                meta = {"requestId": response.headers.get("request-id"),
                        "characterCost": response.headers.get("character-cost")}
        except urllib.error.HTTPError as error:
            detail = error.read()[:400].decode("utf-8", "replace")
            raise SystemExit("Generation failed: HTTP %d %s; no retry made." % (error.code, detail)) from None
        except (OSError, urllib.error.URLError):
            raise SystemExit("Generation connection failed; no retry made.") from None
        audio.write_bytes(data)
        peak, lufs = levels(audio)
        meta.update(modelId=MODEL, tempo=TEMPO,
                    request={k: v for k, v in payload.items() if k != "model_id"},
                    audioSha256=hashlib.sha256(data).hexdigest(), durationSeconds=probe(audio),
                    peakDb=peak, integratedLufs=lufs)
        audio.with_suffix(".json").write_text(json.dumps(meta, indent=2) + "\n", encoding="utf-8")
        print("Saved %s %5.2fs peak %5.1f dB  %s LUFS  cost %s" % (
            audio.name, meta["durationSeconds"], peak, lufs, meta["characterCost"]), flush=True)


def loop(take, bars, start_bar):
    """Cuts `bars` bars from `start_bar` and crossfades the audio after the cut into the head."""
    source = TAKES / (take + ".mp3")
    start = start_bar * BAR_MS / 1000
    length = bars * BAR_MS / 1000
    fade = CROSSFADE_SECONDS
    if probe(source) < start + length + fade:
        raise SystemExit("%s is too short for %d bars from bar %d plus the crossfade" % (take, bars, start_bar))
    LOOPS.mkdir(parents=True, exist_ok=True)
    out = SHIPPED / ("quiet-bed-" + take + ".ogg")
    # body: the span itself; tail: what follows it, faded out over the head while the head fades in.
    graph = ("[0:a]atrim=start=%.4f:end=%.4f,asetpts=PTS-STARTPTS[body];"
             "[0:a]atrim=start=%.4f:end=%.4f,asetpts=PTS-STARTPTS,afade=t=out:d=%.3f:curve=qsin[tail];"
             "[body]asplit[b1][b2];"
             "[b1]atrim=end=%.4f,afade=t=in:d=%.3f:curve=qsin[head];"
             "[b2]atrim=start=%.4f,asetpts=PTS-STARTPTS[rest];"
             "[head][tail]amix=inputs=2:normalize=0[joined];"
             "[joined][rest]concat=n=2:v=0:a=1[out]") % (
        start, start + length, start + length, start + length + fade, fade, fade, fade, fade)
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", str(source), "-filter_complex", graph, "-map", "[out]",
                    "-c:a", "libvorbis", "-q:a", "8", str(out)], check=True)
    peak, lufs = levels(out)
    meta = {"take": source.name, "tempo": TEMPO, "startBar": start_bar, "bars": bars,
            "crossfadeSeconds": fade, "durationSeconds": probe(out), "peakDb": peak, "integratedLufs": lufs}
    (LOOPS / (take + "_loop.json")).write_text(json.dumps(meta, indent=2) + "\n", encoding="utf-8")
    print("Looped %s: %d bars from bar %d, %.2fs" % (out.name, bars, start_bar, meta["durationSeconds"]))


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("stage", choices=("plan", "generate", "loop"))
    parser.add_argument("take", nargs="?")
    parser.add_argument("--candidate", action="append", choices=list(CANDIDATES))
    parser.add_argument("--bars", type=int, default=52)
    parser.add_argument("--start-bar", type=int, default=0)
    args = parser.parse_args()
    if args.stage == "loop":
        if not args.take:
            raise SystemExit("loop needs a take id, such as sequencer_pulse_t01")
        loop(args.take, args.bars, args.start_bar)
        return
    chosen = args.candidate or list(CANDIDATES)
    if args.stage == "plan":
        for candidate in chosen:
            print(json.dumps({"candidate": candidate, "composition_plan": plan(candidate)}, indent=2))
    else:
        generate(chosen)


if __name__ == "__main__":
    main()
