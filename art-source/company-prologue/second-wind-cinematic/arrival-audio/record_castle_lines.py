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
treat     applies each line's treatment: the shipped `comms` intercom recipe (ProcessAudioTask),
          the stronger SHIP_PA loudspeaker, or clean for off-intercom lines, writing <take>_treated.wav beside each take.
package   assembles the chosen takes (PICKS) into numbered MP3s under --out;
          package-v2 / package-v3 do the same for the second C5 draft (PICKS_V2) and its
          confident re-read (PICKS_V3).
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
# C5's all-ship announcement plays over the overhead shot of the families walking off, so it is
# a loudspeaker in the hold heard from the yard: narrower, driven, with two short slap echoes.
# Owner's pick 2026-10-18 for that placement ("03"); `comms` stays for lines heard in the cabin.
# Loudness-matched to the comms takes (about -19 LUFS).
SHIP_PA = ("highpass=f=450:p=2,lowpass=f=2600:p=2,equalizer=f=1600:t=q:w=1:g=6,"
           "acompressor=threshold=0.06:ratio=6:attack=3:release=70:makeup=3,"
           "asoftclip=type=tanh:threshold=0.35:oversample=4,lowpass=f=2800:p=2,"
           "aecho=0.8:0.5:31|67:0.3|0.18,loudnorm=I=-19:TP=-1.5:LRA=7")
TREATMENTS = {"comms": COMMS, "ship_pa": SHIP_PA, "clean": "anull"}

C5_ALL_HANDS = ("All hands, this is Castle. We're down. "
                "Everyone we took aboard at the Claim is walking off this ship on their own feet. "
                "Take your filters. The air's thick out there, but it's air, and nobody's metering it. "
                "Mind the ramp. Go and see the sky.")
C5_PRIVATE = "A new start, Commander. Let's not waste it."
# Second draft, owner's choice 2026-10-18: "this is Castle" dropped, plain orders, a wry last line.
C5_ANNOUNCE = ("All hands, we're down. Filters on before you step out; it's wet out there. "
               "Take it slow on the ramp, and follow the yard crew to the workshop.")
C5_LAST = "That was the easy part, Commander."

# id, treatment, caption text, synthesis text
LINES = [
    ("entry", "comms", "Cleared. Everybody breathe.",
     "[relieved, warm, tired voice, speaking gently to the passengers] Cleared. Everybody breathe."),
    ("c4_reels", "comms", "Like the travel reels they ran in the canteen... except this one doesn't loop.",
     "[quiet, tired, a little wonder under the words] Like the travel reels they ran in the canteen... "
     "except this one doesn't loop."),
    ("c4_greywater", "comms", "Greywater's past the cranes. Let me do the talking.",
     "[calm, practical, quiet] Greywater's past the cranes. Let me do the talking."),
    # Owner's pick 2026-10-19 of the Kilo Nine drafts (c5-v2-closing.md): "the most personable".
    ("c4_kilo_nine", "comms", "Look for the fenced yard on the east side. Kilo Nine. "
     "Mind your manners down there; it's a favour.",
     "[warm, dry, a small smile in it, pointing something out to the passengers] Look for the fenced yard "
     "on the east side. Kilo Nine. Mind your manners down there; it's a favour."),
    ("c5_allhands_flat", "comms", C5_ALL_HANDS,
     "[calm, tired, practical ship's announcement, unhurried] " + C5_ALL_HANDS),
    ("c5_allhands_warm", "comms", C5_ALL_HANDS,
     "[calm, tired ship's announcement, quiet hope held under the words] " + C5_ALL_HANDS),
    ("c5_private_flat", "clean", C5_PRIVATE,
     "[quiet, plain, tired, to one person beside her] " + C5_PRIVATE),
    ("c5_private_catch", "clean", C5_PRIVATE,
     "[quiet, to one person beside her, a small catch in the voice, holding it together] " + C5_PRIVATE),
    # The longer catch direction above repeated the line in five of six takes.
    ("c5_private_catch_short", "clean", C5_PRIVATE, "[quiet, voice catching slightly] " + C5_PRIVATE),
    ("c5v2_announce", "ship_pa", C5_ANNOUNCE,
     "[calm, tired, practical ship's announcement, unhurried] " + C5_ANNOUNCE),
    ("c5v2_last_plain", "clean", C5_LAST, "[quiet, tired, plain, to one person beside her] " + C5_LAST),
    ("c5v2_last_wry", "clean", C5_LAST, "[quiet, tired, dry, a small wry smile] " + C5_LAST),
    # Third pass: the v2 reads sounded worried. Owner: keep her confident. "Tired" is out of
    # the directions; the calm is a captain's who has brought her people down.
    ("c5v3_announce", "ship_pa", C5_ANNOUNCE,
     "[calm, confident, steady ship's announcement, a captain in control, warm] " + C5_ANNOUNCE),
    ("c5v3_last", "clean", C5_LAST, "[quiet, confident, dry, a hint of a smile] " + C5_LAST),
    # Owner, same pass: "resolute resolve".
    ("c5v3r_announce", "ship_pa", C5_ANNOUNCE,
     "[resolute, steady, firm ship's announcement, quiet determination] " + C5_ANNOUNCE),
    ("c5v3r_last", "clean", C5_LAST, "[quiet, resolute, determined, looking ahead] " + C5_LAST),
]

# Takes that read the line cleanly and then repeated it: keep the first read, as the shipped
# line 9 was (its receipt records the same edit). start, end in seconds; a short fade closes it.
TRIMS = {
    "c5_private_catch_t02": (0.0, 3.5),
    "c5_private_catch_t05": (0.0, 3.3),
    "c5v2_last_plain_t03": (0.0, 2.3),
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
    for line_id, treatment, _, _ in LINES:
        for audio in takes(line_id):
            target = audio.with_name(audio.stem + "_treated.wav")
            chain = TREATMENTS[treatment]
            if audio.stem in TRIMS:
                start, end = TRIMS[audio.stem]
                chain = "atrim=%.2f:%.2f,asetpts=PTS-STARTPTS,afade=t=out:st=%.2f:d=0.25,%s" % (
                    start, end, end - start - 0.25, chain)
            subprocess.run(["ffmpeg", "-hide_banner", "-v", "error", "-y", "-i", str(audio), "-af", chain,
                            "-ar", "44100", str(target)], check=True)
            print("Treated", target.name, treatment, flush=True)


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

# Second draft. The announcement starts about 5 s into C5 and runs about 8.5 s; the last line
# belongs over the pull-out (18-26 s), so the gap here is the one the picture asks for.
PICKS_V2 = [
    ("01-c5-plain-a", ["c5v2_announce_t02", "c5v2_last_plain_t01"]),
    ("02-c5-plain-b", ["c5v2_announce_t03", "c5v2_last_plain_t03"]),
    ("03-c5-wry-a", ["c5v2_announce_t04", "c5v2_last_wry_t02"]),
    ("04-c5-wry-b", ["c5v2_announce_t02", "c5v2_last_wry_t04"]),
]
PICTURE_GAP = 5.0
PICKS_V3 = [
    ("01-c5-confident-a", ["c5v3_announce_t01", "c5v3_last_t01"]),
    ("02-c5-confident-b", ["c5v3_announce_t03", "c5v3_last_t04"]),
    ("03-c5-resolute-a", ["c5v3r_announce_t02", "c5v3r_last_t02"]),
    ("04-c5-resolute-b", ["c5v3r_announce_t03", "c5v3r_last_t03"]),
]


def package(target, picks=PICKS, gap=OFF_CHANNEL_GAP):
    target.mkdir(parents=True, exist_ok=True)
    for name, parts in picks:
        inputs, graph = [], []
        for i, part in enumerate(parts):
            inputs += ["-i", str(OUT / (part + "_treated.wav"))]
            pad = ",apad=pad_dur=%.2f" % gap if i < len(parts) - 1 else ""
            graph.append("[%d:a]aformat=sample_rates=44100:channel_layouts=mono%s[p%d]" % (i, pad, i))
        graph.append("%sconcat=n=%d:v=0:a=1[out]" % ("".join("[p%d]" % i for i in range(len(parts))), len(parts)))
        out = target / (name + ".mp3")
        subprocess.run(["ffmpeg", "-hide_banner", "-v", "error", "-y", *inputs, "-filter_complex", ";".join(graph),
                        "-map", "[out]", "-c:a", "libmp3lame", "-q:a", "2", str(out)], check=True)
        print("%-26s %5.2fs peak %5.1f dB  %s" % (out.name, probe(out), peak_db(out), " + ".join(parts)), flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("stage", choices=["plan", "generate", "check", "treat", "package", "package-v2", "package-v3"])
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
    elif args.stage == "package":
        package(Path(args.out))
    elif args.stage == "package-v2":
        package(Path(args.out), PICKS_V2, PICTURE_GAP)
    else:
        package(Path(args.out), PICKS_V3, PICTURE_GAP)


if __name__ == "__main__":
    main()
