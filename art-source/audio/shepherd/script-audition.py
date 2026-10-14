"""Audition the 2026-10-14 Shepherd script before the full recording pass. Dry-run by default.

  python art-source/audio/shepherd/script-audition.py            # list the batch and its size
  python art-source/audio/shepherd/script-audition.py --generate # one request per line, no retries

Takes land in script-audition/ beside this file, with an isolated catalog that records
measured durations. Nothing here touches the runtime catalog, the shipped sound bank or
the approved source takes; promotion is a separate, explicit step.
"""
import argparse
import importlib.util
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[3]
OUT = Path(__file__).with_name("script-audition")
CATALOG = OUT / "audition-catalog.json"
IDS = [
    # Mara: the referral's register and the word Asharu.
    "basin_referral_01", "basin_referral_03", "basin_referral_05", "basin_referral_11",
    # Castle: dry pressure, and the Hegemony argument.
    "basin_salvage_not_wayline_01", "basin_salvage_schedules_sold_01", "basin_salvage_descent_03",
    "basin_passage_negotiation_17", "basin_cold_start_turret_ready",
    # Vale: names (Asharu, Wayline, Harlan, Jory), refusal, urgency, grief.
    "basin_salvage_wreckfall_01", "basin_salvage_fuel_order_01", "basin_salvage_cousin_01",
    "basin_passage_negotiation_13", "basin_passage_negotiation_14", "basin_cold_start_approach",
    "basin_cold_start_power_restored", "basin_cold_start_south_haul_overrun",
    "basin_debrief_passage_militia_losses",
]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--generate", action="store_true")
    args = parser.parse_args()
    runtime = json.loads((ROOT / "mod/data/marines/wayline_narration.json").read_text(encoding="utf-8"))
    by_id = {line["id"]: line for line in runtime["lines"]}
    missing = [i for i in IDS if i not in by_id]
    if missing:
        raise SystemExit("Unknown audition lines: " + ", ".join(missing))
    lines = [dict(by_id[i], voiced=False) for i in IDS]
    print(json.dumps(dict(lines=len(lines), characters=sum(len(l["synthesisText"]) for l in lines),
                          speakers=sorted({l["speaker"] for l in lines}), generate=args.generate)))
    if not args.generate:
        return
    OUT.mkdir(parents=True, exist_ok=True)
    if not CATALOG.exists():
        catalog = {k: v for k, v in runtime.items() if k != "lines"}
        catalog["lines"] = lines
        CATALOG.write_text(json.dumps(catalog, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    spec = importlib.util.spec_from_file_location("narration_authoring", ROOT / "tools/generate_wayline_narration.py")
    helper = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(helper)
    helper.CATALOG = CATALOG
    helper.SOURCES = OUT
    sys.argv = ["generate_wayline_narration.py", "--generate"] + [a for i in IDS for a in ("--line", i)]
    helper.main()


if __name__ == "__main__":
    main()
