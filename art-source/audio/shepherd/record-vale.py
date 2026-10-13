"""Explicit selected-voice save and approved nine-line batch; no shared catalog writes."""
import argparse
from datetime import datetime, timezone
import importlib.util
import json
from pathlib import Path
import sys
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[3]
OUT = Path(__file__).with_name("vale-audition")
ISOLATED = Path(__file__).with_name("vale-recording-catalog.json")
IDS = ["basin_cold_start_power_restored", "basin_cold_start_complete", "basin_passage_briefing",
       "basin_passage_bend_build", "basin_passage_militia_hold_0", "basin_passage_narrows_build",
       "basin_passage_militia_hold_1", "basin_passage_departure", "basin_passage_complete"]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("stage", choices=["save-voice", "generate"])
    args = parser.parse_args()
    spec = importlib.util.spec_from_file_location("authoring", ROOT / "tools/generate_wayline_narration.py")
    helper = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(helper)
    chosen = OUT / "selected-voice.json"
    if args.stage == "save-voice":
        marker = OUT / "voice-save-started.json"
        if marker.exists():
            raise SystemExit("Voice save already started; no automatic repeat.")
        audition = json.loads((OUT / "receipt.json").read_text())
        candidate = json.loads((OUT / "vale-candidate-3.json").read_text())
        payload = dict(voice_name="Tamsin Vale — Basin Reclamation",
                       voice_description=audition["payload"]["voice_description"],
                       generated_voice_id=candidate["generated_voice_id"])
        key = helper.credential()
        marker.write_text(json.dumps(dict(payload=payload, startedAt=datetime.now(timezone.utc).isoformat()),indent=2)+"\n")
        request = urllib.request.Request("https://api.elevenlabs.io/v1/text-to-voice",
                    data=json.dumps(payload).encode(), headers={"xi-api-key":key,"Content-Type":"application/json"})
        try:
            with urllib.request.urlopen(request,timeout=120) as response:
                result=json.loads(response.read())
                headers={name:response.headers.get(name) for name in ("request-id","character-cost")}
        except urllib.error.HTTPError as error:
            raise SystemExit("Voice save HTTP "+str(error.code)+"; no retry.") from None
        except (OSError,urllib.error.URLError):
            raise SystemExit("Voice save connection failed; no retry.") from None
        receipt=dict(voiceId=result["voice_id"],name=result.get("name"),selectedCandidate=3,
                     generatedVoiceId=candidate["generated_voice_id"],description=payload["voice_description"],
                     headers=headers,createdAt=datetime.now(timezone.utc).isoformat(),requestCount=1)
        chosen.write_text(json.dumps(receipt,indent=2)+"\n",encoding="utf-8")
        print(json.dumps(receipt))
        return
    voice_id=json.loads(chosen.read_text())["voiceId"]
    if ISOLATED.exists():
        raise SystemExit("Isolated batch already prepared; no automatic repeat.")
    catalog=json.loads((ROOT / "mod/data/marines/wayline_narration.json").read_text())
    catalog["lines"]=[line for line in catalog["lines"] if line["id"] in IDS]
    assert len(catalog["lines"])==9
    assert sum(len(line["synthesisText"]) for line in catalog["lines"])==1282
    for line in catalog["lines"]:
        assert line["speaker"].startswith("TAMSIN VALE")
        line["voiceId"]=voice_id
        line["voiced"]=False
    ISOLATED.write_text(json.dumps(catalog,indent=2)+"\n",encoding="utf-8")
    helper.CATALOG=ISOLATED
    sys.argv=["generate_wayline_narration.py","--generate"]
    for line_id in IDS:
        sys.argv += ["--line",line_id]
    helper.main()


if __name__=="__main__":
    main()
