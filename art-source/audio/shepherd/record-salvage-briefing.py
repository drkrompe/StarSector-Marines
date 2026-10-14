"""Scoped Salvage Rights authoring command. Dry-run by default; never a build step.

After explicit recording authorization:
  python art-source/audio/shepherd/record-salvage-briefing.py --generate
One request per pending turn, no automatic retry. Existing approved takes are kept.
The isolated catalog records measured durations; promotion into runtime is separate.
"""
import argparse
import importlib.util
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[3]
CATALOG = Path(__file__).with_name("salvage-briefing-recording-catalog.json")
IDS = [f"basin_salvage_briefing_{number:02d}" for number in range(1, 9)]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--generate", action="store_true")
    parser.add_argument("--line", action="append", choices=IDS)
    args = parser.parse_args()
    spec = importlib.util.spec_from_file_location(
        "narration_authoring", ROOT / "tools/generate_wayline_narration.py")
    helper = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(helper)
    helper.CATALOG = CATALOG
    sys.argv = ["generate_wayline_narration.py"]
    if args.generate:
        sys.argv.append("--generate")
    for line_id in args.line or IDS:
        sys.argv.extend(["--line", line_id])
    helper.main()


if __name__ == "__main__":
    main()
