"""Review takes of Castle's new arrival lines (c5-v2-closing.md, "The dialogue arc into it").

  python art-source/company-prologue/second-wind-cinematic/arrival-audio/record_castle_lines.py plan
  python .../record_castle_lines.py generate [--line ID ...] [--takes N]
  python .../record_castle_lines.py check
  python .../record_castle_lines.py treat

Takes are review output, not shipped audio: everything is written under
build/audio-previews/castle-lines/ and nothing in mod/ or the shipped hail catalog is
touched. The voice is the one the shipped hail uses (mod/data/marines/jangala_arrival_hail.json).

generate  one paid text-to-speech request per take, never retried; existing takes are kept
          and new ones numbered after them.
check     one speech-to-text request per take (cached by audio hash) to confirm the words,
          plus duration and peak level; writes check.json.
treat     applies the shipped `comms` intercom recipe (ProcessAudioTask) to intercom lines and
          leaves off-intercom lines clean, writing <take>_treated.wav beside each take.
package   assembles the chosen takes (PICKS) into numbered MP3s under --out.
Requires ffmpeg/ffprobe on PATH and ELEVENLABS_API_KEY in the environment (never printed).
"""
import argparse
import difflib
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import urllib.error
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[4]
CATALOG = ROOT / "mod/data/marines/jangala_arrival_hail.json"
OUT = ROOT / "build/audio-previews/castle-lines"

# The shipped `comms` recipe, ProcessAudioTask: light intercom band, no squelch, no noise floor.
COMMS = ("highpass=f=280:p=2,lowpass=f=3600:p=2,"
         "acompressor=threshold=0.125:ratio=3:attack=5:release=90:makeup=1.5,"
         "asoftclip=type=tanh:threshold=0.65:oversample=2,volume=2.5,"
         "alimiter=limit=0.89:level=false:latency=true")

C5_ALL_HANDS = ("All hands, this is Castle. We're down. "
                "Everyone we took aboard at the Claim is walking off this ship on their own feet. "
                "Take your filters. The air's thick out there, but it's air, and nobody's metering it. "
                "Mind the ramp. Go and see the sky.")
C5_PRIVATE = "A new start, Commander. Let's not waste it."

# id, intercom?, caption text, synthesis text
LINES = [
    ("entry", True, "Cleared. Everybody breathe.",
     "[relieved, warm, tired voice, speaking gently to the passengers] Cleared. Everybody breathe."),
    ("c4_reels", True, "Like the travel reels they ran in the canteen... except this one doesn't loop.",
     "[quiet, tired, a little wonder under the words] Like the travel reels they ran in the canteen... "
     "except this one doesn't loop."),
    ("c4_greywater", True, "Greywater's past the cranes. Let me do the talking.",
     "[calm, practical, quiet] Greywater's past the cranes. Let me do the talking."),
    ("c5_allhands_flat", True, C5_ALL_HANDS,
     "[calm, tired, practical ship's announcement, unhurried] " + C5_ALL_HANDS),
    ("c5_allhands_warm", True, C5_ALL_HANDS,
     "[calm, tired ship's announcement, quiet hope held under the words] " + C5_ALL_HANDS),
    ("c5_private_flat", False, C5_PRIVATE,
     "[quiet, plain, tired, to one person beside her] " + C5_PRIVATE),
    ("c5_private_catch", False, C5_PRIVATE,
     "[quiet, to one person beside her, a small catch in the voice, holding it together] " + C5_PRIVATE),
    # The longer catch direction above repeated the line in five of six takes.
    ("c5_private_catch_short", False, C5_PRIVATE, "[quiet, voice catching slightly] " + C5_PRIVATE),
]

# Takes that read the line cleanly and then repeated it: keep the first read, as the shipped
# line 9 was (its receipt records the same edit). start, end in seconds; a short fade closes it.
TRIMS = {
    "c5_private_catch_t02": (0.0, 3.5),
    "c5_private_catch_t05": (0.0, 3.3),
}
BY_ID = {line[0]: line for line in LINES}


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


def castle():
    catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
    return catalog["voices"]["Castle"]["voiceId"], catalog["modelId"], catalog["voiceSettings"]


def probe(path):
    out = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration",
                          "-of", "default=nw=1:nk=1", str(path)], check=True, capture_output=True, text=True)
    return round(float(out.stdout.strip()), 2)


def peak_db(path):
    out = subprocess.run(["ffmpeg", "-hide_banner", "-i", str(path), "-af", "volumedetect", "-f", "null", "-"],
                         capture_output=True, text=True).stderr
    return float(re.search(r"max_volume: (-?[\d.]+) dB", out).group(1))


def takes(line_id):
    return sorted(OUT.glob(line_id + "_t[0-9][0-9].mp3"))


def generate(ids, count):
    voice_id, model_id, settings = castle()
    key = credential()
    OUT.mkdir(parents=True, exist_ok=True)
    for line_id in ids:
        _, _, _, synthesis = BY_ID[line_id]
        payload = {"text": synthesis, "model_id": model_id}
        if settings:
            payload["voice_settings"] = settings
        start = len(takes(line_id)) + 1
        for n in range(start, start + count):
            audio = OUT / ("%s_t%02d.mp3" % (line_id, n))
            request = urllib.request.Request(
                "https://api.elevenlabs.io/v1/text-to-speech/" + voice_id + "?output_format=mp3_44100_128",
                data=json.dumps(payload).encode(), headers={"xi-api-key": key, "Content-Type": "application/json"})
            try:
                with urllib.request.urlopen(request, timeout=120) as response:
                    data = response.read()
                    meta = {"requestId": response.headers.get("request-id"),
                            "characterCost": response.headers.get("character-cost")}
            except urllib.error.HTTPError as error:
                raise SystemExit("Generation failed: HTTP " + str(error.code) + "; no retry made.") from None
            except (OSError, urllib.error.URLError):
                raise SystemExit("Generation connection failed; no retry made.") from None
            audio.write_bytes(data)
            meta.update(voiceId=voice_id, modelId=model_id, synthesisText=synthesis,
                        audioSha256=hashlib.sha256(data).hexdigest(), durationSeconds=probe(audio))
            audio.with_suffix(".json").write_text(json.dumps(meta, indent=2) + "\n", encoding="utf-8")
            print("Saved", audio.name, meta["durationSeconds"], "s", flush=True)


def words(text):
    return re.findall(r"[a-z0-9']+", text.lower().replace("’", "'"))


def transcribe(key, audio):
    boundary = uuid.uuid4().hex
    fields = {"model_id": "scribe_v2", "language_code": "en", "tag_audio_events": "true",
              "timestamps_granularity": "word"}
    body = b"".join(("--%s\r\nContent-Disposition: form-data; name=\"%s\"\r\n\r\n%s\r\n"
                     % (boundary, k, v)).encode() for k, v in fields.items())
    body += ("--%s\r\nContent-Disposition: form-data; name=\"file\"; filename=\"take.mp3\"\r\n"
             "Content-Type: audio/mpeg\r\n\r\n" % boundary).encode() + audio + ("\r\n--%s--\r\n" % boundary).encode()
    request = urllib.request.Request("https://api.elevenlabs.io/v1/speech-to-text", data=body, headers={
        "xi-api-key": key, "Content-Type": "multipart/form-data; boundary=" + boundary})
    try:
        with urllib.request.urlopen(request, timeout=120) as response:
            return json.loads(response.read())
    except urllib.error.HTTPError as error:
        raise SystemExit("Transcription failed: HTTP " + str(error.code) + "; no retry made.") from None
    except (OSError, urllib.error.URLError):
        raise SystemExit("Transcription connection failed; no retry made.") from None


def check():
    cache_path = OUT / "transcripts.json"
    cache = json.loads(cache_path.read_text(encoding="utf-8")) if cache_path.exists() else {}
    key = None
    report = {}
    for line_id, _, text, _ in LINES:
        for audio in takes(line_id):
            data = audio.read_bytes()
            digest = hashlib.sha256(data).hexdigest()
            if digest not in cache:
                key = key or credential()
                cache[digest] = transcribe(key, data)
                cache_path.write_text(json.dumps(cache, indent=1) + "\n", encoding="utf-8")
            result = cache[digest]
            spoken = [w for w in result.get("words", []) if w.get("type") == "word"]
            heard = [p for w in spoken for p in words(w["text"])]
            problems = ["%s script=%r heard=%r" % (op, " ".join(words(text)[a1:a2]), " ".join(heard[b1:b2]))
                        for op, a1, a2, b1, b2 in difflib.SequenceMatcher(
                            a=words(text), b=heard, autojunk=False).get_opcodes() if op != "equal"]
            events = [w["text"] for w in result.get("words", []) if w.get("type") == "audio_event"]
            if events:
                problems.append("audio events: " + ", ".join(events))
            speech_end = spoken[-1]["end"] if spoken else 0.0
            report[audio.name] = dict(line=line_id, seconds=probe(audio), speechEnds=speech_end,
                                      peakDb=peak_db(audio), transcript=result.get("text", ""),
                                      problems=problems)
            print("%-26s %5.2fs peak %6.1f dB  %s" % (audio.name, report[audio.name]["seconds"],
                  report[audio.name]["peakDb"], "; ".join(problems) or "words ok"), flush=True)
    (OUT / "check.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")


def treat():
    for line_id, intercom, _, _ in LINES:
        for audio in takes(line_id):
            target = audio.with_name(audio.stem + "_treated.wav")
            chain = COMMS if intercom else "anull"
            if audio.stem in TRIMS:
                start, end = TRIMS[audio.stem]
                chain = "atrim=%.2f:%.2f,asetpts=PTS-STARTPTS,afade=t=out:st=%.2f:d=0.25,%s" % (
                    start, end, end - start - 0.25, chain)
            subprocess.run(["ffmpeg", "-hide_banner", "-v", "error", "-y", "-i", str(audio), "-af", chain,
                            "-ar", "44100", str(target)], check=True)
            print("Treated", target.name, "intercom" if intercom else "clean", flush=True)


# Review set: file name, then the treated takes played in order. A C5 closing is the all-hands
# announcement on the intercom, a beat for the click-off, then the private line, clean.
PICKS = [
    ("01-entry-a", ["entry_t06"]),
    ("02-entry-b", ["entry_t02"]),
    ("03-c4-travel-reels-a", ["c4_reels_t06"]),
    ("04-c4-travel-reels-b", ["c4_reels_t02"]),
    ("05-c4-greywater-a", ["c4_greywater_t01"]),
    ("06-c4-greywater-b", ["c4_greywater_t03"]),
    ("07-c5-closing-flat-a", ["c5_allhands_flat_t02", "c5_private_flat_t01"]),
    ("08-c5-closing-flat-b", ["c5_allhands_flat_t03", "c5_private_flat_t03"]),
    ("09-c5-closing-catch-a", ["c5_allhands_warm_t02", "c5_private_catch_t02"]),
    ("10-c5-closing-catch-b", ["c5_allhands_warm_t03", "c5_private_catch_short_t01"]),
]
OFF_CHANNEL_GAP = 1.4  # as build_hail_preview.py: Castle closes the channel, then speaks to the room


def package(target):
    target.mkdir(parents=True, exist_ok=True)
    for name, parts in PICKS:
        inputs, graph = [], []
        for i, part in enumerate(parts):
            inputs += ["-i", str(OUT / (part + "_treated.wav"))]
            pad = ",apad=pad_dur=%.2f" % OFF_CHANNEL_GAP if i < len(parts) - 1 else ""
            graph.append("[%d:a]aformat=sample_rates=44100:channel_layouts=mono%s[p%d]" % (i, pad, i))
        graph.append("%sconcat=n=%d:v=0:a=1[out]" % ("".join("[p%d]" % i for i in range(len(parts))), len(parts)))
        out = target / (name + ".mp3")
        subprocess.run(["ffmpeg", "-hide_banner", "-v", "error", "-y", *inputs, "-filter_complex", ";".join(graph),
                        "-map", "[out]", "-c:a", "libmp3lame", "-q:a", "2", str(out)], check=True)
        print("%-26s %5.2fs peak %5.1f dB  %s" % (out.name, probe(out), peak_db(out), " + ".join(parts)), flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("stage", choices=["plan", "generate", "check", "treat", "package"])
    parser.add_argument("--out", default=str(OUT / "review"))
    parser.add_argument("--line", action="append", choices=list(BY_ID))
    parser.add_argument("--takes", type=int, default=3)
    args = parser.parse_args()
    ids = args.line or list(BY_ID)
    if args.stage == "plan":
        for line_id in ids:
            print("%-18s %d takes, %d chars, have %d" % (line_id, args.takes, len(BY_ID[line_id][3]),
                                                         len(takes(line_id))))
    elif args.stage == "generate":
        generate(ids, args.takes)
    elif args.stage == "check":
        check()
    elif args.stage == "treat":
        treat()
    else:
        package(Path(args.out))


if __name__ == "__main__":
    main()
