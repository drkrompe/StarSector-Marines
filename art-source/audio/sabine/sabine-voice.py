"""Save the approved Sabine Hale preview as a voice, then audition its emotional range.

python art-source/audio/sabine/sabine-voice.py --candidate N --create   # one paid request: saves the chosen candidate as a voice
python art-source/audio/sabine/sabine-voice.py --candidate N --test     # one TTS request per test line on eleven_v4
Neither step retries; neither runs during a build.
"""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[3]
HERE = Path(__file__).parent
AUDITION = HERE / "audition"
MODEL = "eleven_v4"

# Emotional tests: the timbre was approved, the delivery read as too flat.
TESTS = [
    ("warm", "[quietly proud] All twelve back aboard. Counted twice."),
    ("strained", "[tense, under fire] Castle, we're thin down here. I can hold. I can't push."),
    ("urgent", "[urgent, clipped] Back to the pad. Ox, you're last out. Nobody gets left."),
    ("grief", "[subdued, steady] I've counted twice. The roll's shorter. I'll give Castle the names."),
    ("command", "[firm, focused] Copy, Castle. Hammer, take the gate. Viper, cover them from the wall."),
]


def helper():
    spec = importlib.util.spec_from_file_location("narration_authoring", ROOT / "tools/generate_wayline_narration.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def post(url, payload, key):
    body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    request = urllib.request.Request(url, data=body, headers={"xi-api-key": key, "Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(request, timeout=180) as response:
            return response.read(), hashlib.sha256(body).hexdigest()
    except urllib.error.HTTPError as error:
        raise SystemExit("HTTP " + str(error.code) + ": " + error.read().decode("utf-8", "replace")[:400]
                         + "; no retry made.") from None
    except (OSError, urllib.error.URLError):
        raise SystemExit("Connection failed; no retry made.") from None


def voice_file(candidate):
    return HERE / f"voice-candidate-{candidate}.json"


def create(key, number):
    VOICE, CANDIDATE = voice_file(number), AUDITION / f"sabine-candidate-{number}.json"
    if VOICE.exists():
        raise SystemExit("voice.json already exists; the voice was already created.")
    candidate = json.loads(CANDIDATE.read_text(encoding="utf-8"))
    design = json.loads((AUDITION / "receipt.json").read_text(encoding="utf-8"))["payload"]
    payload = dict(voice_name=f"Sabine Hale - Candidate {number}", voice_description=design["voice_description"],
                   generated_voice_id=candidate["generated_voice_id"])
    data, _ = post("https://api.elevenlabs.io/v1/text-to-voice", payload, key)
    voice_id = json.loads(data)["voice_id"]
    VOICE.write_text(json.dumps(dict(
        voiceId=voice_id, name=payload["voice_name"], generatedVoiceId=candidate["generated_voice_id"],
        description=payload["voice_description"], candidate=CANDIDATE.name,
        provenance=f"Original ElevenLabs Voice Design, candidate {number} of one preview request, saved for "
                   "audition. No game recordings uploaded or cloned."), indent=2) + "\n", encoding="utf-8")
    print("Created voice from", CANDIDATE.name, flush=True)


def test(key, model, only, number):
    voice_id = json.loads(voice_file(number).read_text(encoding="utf-8"))["voiceId"]
    out = AUDITION / f"candidate-{number}-{model}"
    out.mkdir(parents=True, exist_ok=True)
    for name, text in TESTS:
        if only and name not in only:
            continue
        if model == "eleven_multilingual_v2":
            text = text.split("] ", 1)[1]  # v2 reads bracketed directions aloud
        audio = out / (name + ".mp3")
        if audio.exists():
            print("Exists:", audio.name, flush=True)
            continue
        data, digest = post("https://api.elevenlabs.io/v1/text-to-speech/" + voice_id
                            + "?output_format=mp3_44100_128", dict(text=text, model_id=model), key)
        audio.write_bytes(data)
        audio.with_suffix(".json").write_text(json.dumps(dict(
            text=text, modelId=model, inputSha256=digest,
            audioSha256=hashlib.sha256(data).hexdigest()), indent=2) + "\n", encoding="utf-8")
        print("Saved:", audio.name, flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--create", action="store_true")
    parser.add_argument("--test", action="store_true")
    parser.add_argument("--model", default=MODEL)
    parser.add_argument("--only", action="append")
    parser.add_argument("--candidate", type=int, required=True)
    args = parser.parse_args()
    if not (args.create or args.test):
        print(json.dumps(dict(voiceSaved=voice_file(args.candidate).exists(), tests=[t[0] for t in TESTS], model=MODEL)))
        return
    key = helper().credential()
    if args.create:
        create(key, args.candidate)
    if args.test:
        test(key, args.model, args.only, args.candidate)


if __name__ == "__main__":
    main()
