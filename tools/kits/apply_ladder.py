"""Write an accepted Kit Ladder page state into KitLadder.java.

    python tools/kits/apply_ladder.py <export dir> [--check] [--page <page.html>] [--only id,id] [--take-conflicts]

<export dir> is what export_state.py read (<dir>/kits/state.json, fetched with ArtifactData). Runs the same diff
export_state.py reports and writes only two kinds of finding, which are the stations the ladder owns:
  move   an existing row's station           -> the row's station constant is rewritten in place
  add    a real item the ladder has no row for -> a row is added after the last row of its block
Every block of rows stays in campaign order (a stable sort, so an unmoved row never moves) and the UnlockStation
static imports are regenerated from the stations in use. Running it again on the same state changes nothing.

Never written: conflicts (unless --take-conflicts), game-moved rows (the page is stale: republish instead),
unknown stations, orphans, card contents, free-pick grants (FirstRefit) and new cards; the report lists them for the
owner. --check reports what would change and exits 1 if anything would, 0 if KitLadder.java already matches.
--only limits the write to those item ids. Run the Kit Ladder tests afterwards (kit-ladder.md, Apply).
"""
import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import ladder  # noqa: E402
from export_state import report  # noqa: E402

WRITTEN = ("move", "add")


def plan(entries, game, take_conflicts=False, only=None):
    """(changes {id: station}, adds {id: (kind, ref, station)}) from diff entries."""
    kinds = WRITTEN + (("conflict",) if take_conflicts else ())
    changes, adds = {}, {}
    for e in entries:
        if e["kind"] not in kinds or (only and e["id"] not in only):
            continue
        if e["id"] in game["rowStations"]:
            changes[e["id"]] = e["page"]
        else:
            adds[e["id"]] = row_for(e["id"], game) + (e["page"],)
    return changes, adds


def row_for(item, game):
    """(kind, ref) of a new row for a real item."""
    if item in game["cards"]:
        if item.startswith("wayline:"):
            path = {v: k for k, v in ladder.first_refit_paths().items()}[item]
            return "weapon", "FirstRefit.Path.%s.weapons" % path
        const = [c for c, v in game["doctrines"].items() if v == item]
        if len(const) != 1:
            raise ladder.LadderError("%s matches %d SquadEquipmentDoctrines constants" % (item, len(const)))
        return ("sheet" if item.startswith("armor:") else "weapon"), "SquadEquipmentDoctrines." + const[0]
    kind, enum = item.split(":", 1)
    return "pattern", "%s.%s" % (ladder.KIND_CLASS[kind], enum)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("folder")
    ap.add_argument("--check", action="store_true")
    ap.add_argument("--page")
    ap.add_argument("--only")
    ap.add_argument("--take-conflicts", action="store_true")
    args = ap.parse_args()
    state, game, entries, md = report(args.folder, args.page)
    only = set(args.only.split(",")) if args.only else None
    changes, adds = plan(entries, game, args.take_conflicts, only)
    skipped = [e for e in entries if e["kind"] not in WRITTEN and e["kind"] != "unseen"]
    for item, station in sorted(changes.items()):
        print("move  %-40s %s -> %s" % (item, game["rowStations"][item], station))
    for item, (kind, ref, station) in sorted(adds.items()):
        print("add   %-40s %s(%s, %s)" % (item, kind, ref, station))
    if skipped:
        print("not written (%d): %s" % (len(skipped), ", ".join(sorted({e["kind"] for e in skipped}))))
    text = ladder.read(ladder.KIT_LADDER)
    patched = ladder.patch(text, changes, adds, game["stations"], game["doctrines"], ladder.first_refit_paths())
    if patched == text:
        print("KitLadder.java already matches the page's stations.")
        return 0
    if args.check:
        print("--check: KitLadder.java would change (%d moves, %d adds); nothing written." % (len(changes), len(adds)))
        return 1
    ladder.KIT_LADDER.write_text(patched, encoding="utf-8", newline="\n")
    print("wrote %s: %d moves, %d adds" % (ladder.KIT_LADDER, len(changes), len(adds)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
