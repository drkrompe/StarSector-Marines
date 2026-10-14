"""Scoped Shepherd briefing authoring command. Dry-run by default; never a build step.

After explicit recording authorization:
  python art-source/audio/shepherd/record-salvage-briefing.py --generate
Use --chapter passage for the post-Cold Start negotiation.
One request per pending turn, no automatic retry. Existing approved takes are kept.
The isolated catalog records measured durations; promotion into runtime is separate.
"""
import argparse
import importlib.util
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[3]
CATALOG = Path(__file__).with_name("salvage-briefing-recording-catalog.json")
CATALOGS = {"salvage": CATALOG, "passage": Path(__file__).with_name("working-passage-recording-catalog.json")}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--generate", action="store_true")
    parser.add_argument("--chapter", choices=CATALOGS, default="salvage")
    parser.add_argument("--line", action="append")
    args = parser.parse_args()
    spec = importlib.util.spec_from_file_location(
        "narration_authoring", ROOT / "tools/generate_wayline_narration.py")
    helper = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(helper)
    helper.CATALOG = CATALOGS[args.chapter]
    ids = [line["id"] for line in json.loads(helper.CATALOG.read_text(encoding="utf-8"))["lines"]]
    sys.argv = ["generate_wayline_narration.py"]
    if args.generate:
        sys.argv.append("--generate")
    for line_id in args.line or ids:
        sys.argv.extend(["--line", line_id])
    helper.main()


if __name__ == "__main__":
    main()
