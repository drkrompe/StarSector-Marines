"""Transcribe Shepherd takes and flag ones that do not say what the script says.

  python art-source/audio/shepherd/check-takes.py --line ID [--line ID ...]
  python art-source/audio/shepherd/check-takes.py --all-unvoiced

One ElevenLabs speech-to-text request per take, never retried. Transcripts are cached in
take-check.json keyed by the take's audio hash, so re-running only bills changed takes.
Flags: words added or missing against the script (a doubled word shows as an addition, with
its timestamp so it can be cut), non-speech audio events, and delivery-tag words read aloud.
"""
import argparse
import difflib
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import urllib.error
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[3]
TAKES = ROOT / "asset-pipeline/src/tool/resources/audio/wayline"
CATALOG = ROOT / "mod/data/marines/wayline_narration.json"
CACHE = Path(__file__).with_name("take-check.json")
ENDPOINT = "https://api.elevenlabs.io/v1/speech-to-text"


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
    request = urllib.request.Request(ENDPOINT, data=body, headers={
        "xi-api-key": key, "Content-Type": "multipart/form-data; boundary=" + boundary})
    try:
        with urllib.request.urlopen(request, timeout=120) as response:
            return json.loads(response.read())
    except urllib.error.HTTPError as error:
        raise SystemExit("Transcription failed: HTTP " + str(error.code) + "; no retry made.") from None
    except (OSError, urllib.error.URLError):
        raise SystemExit("Transcription connection failed; no retry made.") from None


def review(line, result):
    script = words(line["text"])
    spoken = [w for w in result.get("words", []) if w.get("type") == "word"]
    heard = [words(w["text"]) for w in spoken]
    flat, owner = [], []
    for index, parts in enumerate(heard):
        flat += parts
        owner += [index] * len(parts)
    problems = []
    for op, a1, a2, b1, b2 in difflib.SequenceMatcher(a=script, b=flat, autojunk=False).get_opcodes():
        if op == "equal":
            continue
        extra = flat[b1:b2]
        at = ""
        if extra:
            first, last = spoken[owner[b1]], spoken[owner[b2 - 1]]
            at = " @%.2f-%.2fs" % (first["start"], last["end"])
        said, got = script[a1:a2], extra
        minor = op == "replace" and len(said) == len(got) == 1 and said[0][:3] == got[0][:3]
        problems.append("%s%s script=%r heard=%r%s" % ("minor " if minor else "", op, " ".join(said), " ".join(got), at))
    events = [w["text"] for w in result.get("words", []) if w.get("type") == "audio_event"]
    if events:
        problems.append("audio events: " + ", ".join(events))
    tag = line["synthesisText"].split("]", 1)[0][1:] if line["synthesisText"].startswith("[") else ""
    leaked = [t for t in words(tag) if len(t) > 3 and t in flat and t not in script]
    if leaked:
        problems.append("delivery tag read aloud? " + ", ".join(leaked))
    return problems


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--line", action="append", default=[])
    parser.add_argument("--all-unvoiced", action="store_true")
    args = parser.parse_args()
    catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
    lines = [l for l in catalog["lines"] if l["id"] in args.line or args.all_unvoiced
             and l["missionId"].startswith("story_shepherd") and not l["voiced"]]
    cache = json.loads(CACHE.read_text(encoding="utf-8")) if CACHE.exists() else {}
    spec = importlib.util.spec_from_file_location("narration_authoring", ROOT / "tools/generate_wayline_narration.py")
    helper = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(helper)
    key = None
    flagged = 0
    for line in lines:
        audio_path = TAKES / (line["id"] + ".mp3")
        if not audio_path.exists():
            print("MISSING", line["id"])
            flagged += 1
            continue
        audio = audio_path.read_bytes()
        digest = hashlib.sha256(audio).hexdigest()
        entry = cache.get(line["id"])
        if not entry or entry["audioSha256"] != digest:
            key = key or helper.credential()
            entry = {"audioSha256": digest, "result": transcribe(key, audio)}
            cache[line["id"]] = entry
            CACHE.write_text(json.dumps(cache, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        problems = review(line, entry["result"])
        if problems:
            serious = any(not p.startswith("minor ") for p in problems)
            flagged += serious
            print("FLAG" if serious else "minor", line["id"])
            print("  script:", line["text"])
            print("  heard: ", entry["result"].get("text", ""))
            for problem in problems:
                print("  -", problem)
    print("checked %d, flagged %d (minor-only takes are listed but not counted)" % (len(lines), flagged))


if __name__ == "__main__":
    main()
