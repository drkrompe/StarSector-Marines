"""Music candidates for the arrival's closing, generated with ElevenLabs' music model.

    python art-source/company-prologue/second-wind-cinematic/arrival-audio/make_music.py plan
    python art-source/company-prologue/second-wind-cinematic/arrival-audio/make_music.py generate [--candidate ID ...]

The closing cue (owner, 2026-10-19): hopeful but fragile. Refugees landing somewhere with sky
after a failing station: worn and industrial, a beginning, not a victory. No vocals. It comes in
under Castle's last line, swells as the camera rises (C5 18-26 s), resolves under the title (C5 24
s on) and fades to black with the yard by C5 33. Each candidate is 18 s laid from C5 15.5, so its
swell sits on the rise and its resolve on the title; sfx_mix.py --music lays one into c4c5.

Each take is saved to music-takes/<id>_tNN.mp3 with a JSON sidecar holding its prompt, request,
hash and measured loudness. Requires ffmpeg/ffprobe on PATH and ELEVENLABS_API_KEY in the
environment (never printed).
"""

import argparse
import hashlib
import json
from pathlib import Path
import urllib.error
import urllib.request

from make_sfx import credential, levels, probe

HERE = Path(__file__).resolve().parent
TAKES = HERE / "music-takes"
MODEL = "music_v1"
SECONDS = 18

SHAPE = ("18 seconds long. Starts almost silent and sparse for the first 3 seconds, swells gently but "
         "clearly from 3 to 8 seconds, reaches a warm open major chord at about 8 seconds and lets it "
         "ring out and thin away to silence by the end. Hopeful but fragile: refugees from a failing "
         "station landing somewhere with open sky, worn and humble, a beginning rather than a victory. "
         "Instrumental only, no vocals, no choir.")

CANDIDATES = {
    "pad_piano": "Ambient cinematic closing cue: a warm, slightly worn analog synth pad with soft felt "
                 "piano notes, slow and unhurried, around 60 BPM, a suspended uncertain colour that opens "
                 "into major. No drums. " + SHAPE,
    "strings_horn": "Cinematic closing cue: low strings, cellos and violas in long hushed bowed notes, with a "
                    "distant muted French horn rising far away during the swell. Intimate, restrained, no "
                    "percussion, no big orchestral hit. " + SHAPE,
    "guitar_ambient": "Sparse ambient closing cue: slow fingerpicked clean electric guitar with long reverb and "
                      "a faint tape-worn texture over an airy shimmering ambient bed, a few notes left hanging "
                      "in space. No drums. " + SHAPE,
    "industrial_pulse": "Industrial ambient closing cue: a soft muted pulse of metallic percussion like distant "
                        "machinery, a low synth bass drone and a slow rising pad above it; at the resolve the "
                        "pulse drops away and leaves only the warm chord. Worn, mechanical, human. " + SHAPE,
}


def takes(candidate):
    return sorted(TAKES.glob(candidate + "_t[0-9][0-9].mp3"))


def generate(candidates):
    key = credential()
    TAKES.mkdir(parents=True, exist_ok=True)
    for candidate in candidates:
        payload = {"prompt": CANDIDATES[candidate], "music_length_ms": SECONDS * 1000,
                   "model_id": MODEL, "force_instrumental": True}
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
        meta.update(modelId=MODEL, prompt=CANDIDATES[candidate],
                    request={k: v for k, v in payload.items() if k not in ("model_id", "prompt")},
                    audioSha256=hashlib.sha256(data).hexdigest(), durationSeconds=probe(audio),
                    peakDb=peak, integratedLufs=lufs)
        audio.with_suffix(".json").write_text(json.dumps(meta, indent=2) + "\n", encoding="utf-8")
        print("Saved %s %5.2fs peak %5.1f dB  %s LUFS  cost %s" % (
            audio.name, meta["durationSeconds"], peak, lufs, meta["characterCost"]), flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("stage", choices=("plan", "generate"))
    parser.add_argument("--candidate", action="append", choices=list(CANDIDATES))
    args = parser.parse_args()
    chosen = args.candidate or list(CANDIDATES)
    if args.stage == "plan":
        for candidate in chosen:
            print("%s (%d s, %d takes so far)\n  %s\n" % (candidate, SECONDS, len(takes(candidate)),
                                                         CANDIDATES[candidate]))
    else:
        generate(chosen)


if __name__ == "__main__":
    main()
