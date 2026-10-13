"""One explicitly authorized Vale Voice Design preview request; never creates a voice."""
import argparse
import base64
from datetime import datetime, timezone
import hashlib
import importlib.util
import json
from pathlib import Path
import shutil
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[3]
OUT = Path(__file__).with_name("vale-audition")
GALLERY = Path("C:/Users/Dillon/.codex/visualizations/2026/10/08/01a11c63-5274-7182-9776-c2dca180c046/shepherd-audio/vale-audition")
ENDPOINT = "https://api.elevenlabs.io/v1/text-to-voice/design?output_format=mp3_44100_128"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--generate", action="store_true")
    args = parser.parse_args()
    review = Path(__file__).with_name("dialogue-review.txt").read_text(encoding="utf-8")
    description = " ".join(review.split("Proposed Voice Design: ", 1)[1].split("Bounded audition:", 1)[0].split())
    passage = " ".join(review.split("Audition passage: ", 1)[1].split("\n\n", 1)[0].split())
    payload = dict(voice_description=description, text=passage, model_id="eleven_ttv_v3",
                   auto_generate_text=False, stream_previews=False, should_enhance=False)
    assert 20 <= len(description) <= 1000
    assert 100 <= len(passage) <= 1000
    print(json.dumps(dict(endpoint=ENDPOINT, descriptionCharacters=len(description),
                          textCharacters=len(passage), model=payload["model_id"],
                          generate=args.generate)), flush=True)
    if not args.generate:
        return
    OUT.mkdir(parents=True, exist_ok=True)
    marker = OUT / "request-started.json"
    if marker.exists():
        raise SystemExit("A request was already started; do not repeat an ambiguous or completed paid request.")
    spec = importlib.util.spec_from_file_location("narration_authoring", ROOT / "tools/generate_wayline_narration.py")
    helper = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(helper)
    key = helper.credential()
    body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    marker.write_text(json.dumps(dict(endpoint=ENDPOINT, payload=payload,
                          startedAt=datetime.now(timezone.utc).isoformat(),
                          inputSha256=hashlib.sha256(body).hexdigest()), indent=2)+"\n", encoding="utf-8")
    request = urllib.request.Request(ENDPOINT, data=body,
                                    headers={"xi-api-key": key, "Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(request, timeout=180) as response:
            result = json.loads(response.read())
            headers = {name: response.headers.get(name) for name in
                       ("request-id", "character-cost", "history-item-id")}
    except urllib.error.HTTPError as error:
        status = dict(status="http_error", code=error.code, noRetry=True)
        (OUT / "failure.json").write_text(json.dumps(status, indent=2)+"\n")
        raise SystemExit("Voice Design HTTP " + str(error.code) + "; no retry made.") from None
    except (OSError, urllib.error.URLError):
        (OUT / "failure.json").write_text(json.dumps(dict(status="ambiguous_connection_failure", noRetry=True),indent=2)+"\n")
        raise SystemExit("Voice Design connection failed; no retry made.") from None
    previews = result.get("previews", [])
    receipts = []
    for number, preview in enumerate(previews, 1):
        audio = base64.b64decode(preview["audio_base_64"], validate=True)
        audio_path = OUT / f"vale-candidate-{number}.mp3"
        audio_path.write_bytes(audio)
        metadata = {k: v for k,v in preview.items() if k != "audio_base_64"}
        metadata.update(file=audio_path.name, audioSha256=hashlib.sha256(audio).hexdigest(),
                        audioBytes=len(audio), auditionOnly=True, permanentVoiceCreated=False)
        audio_path.with_suffix(".json").write_text(json.dumps(metadata,indent=2)+"\n", encoding="utf-8")
        receipts.append(metadata)
    receipt = dict(endpoint=ENDPOINT, modelId=payload["model_id"], payload=payload,
                   completedAt=datetime.now(timezone.utc).isoformat(), headers=headers,
                   responseText=result.get("text"), candidates=receipts,
                   requestCount=1, permanentVoiceCreated=False, listeningVerified=False)
    (OUT / "receipt.json").write_text(json.dumps(receipt, indent=2)+"\n", encoding="utf-8")
    GALLERY.mkdir(parents=True, exist_ok=True)
    for source in OUT.iterdir():
        if source.is_file():
            shutil.copy2(source, GALLERY / source.name)
    print(json.dumps(dict(requestCount=1, candidateCount=len(receipts), headers=headers,
                          gallery=str(GALLERY), candidates=[dict(file=x["file"], duration=x.get("duration_secs")) for x in receipts])), flush=True)


if __name__ == "__main__":
    main()
