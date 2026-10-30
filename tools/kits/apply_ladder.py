"""Write an accepted Kit Ladder page state into the game data (mod/data/marines/kit-ladder.json, loadout-cards.json).

    python tools/kits/apply_ladder.py <export dir> [--check] [--page <page.html>] [--only id,id] [--take-conflicts]
                                      [--cards id,id | --cards all]

<export dir> is what export_state.py read (<dir>/kits/state.json, fetched with ArtifactData). Runs the same diff
export_state.py reports and writes:
  move     an existing placement's station            -> rewritten in kit-ladder.json
  add      a real item the ladder does not place       -> a placement is added to its kind's block
  content  (only with --cards) a card the owner edited -> its weapon seats, or its tactic sheet's twelve suits (as the
           sheet's "seats"), are written into loadout-cards.json; names, descriptions and grants are never written
Placements stay in their kind's block in campaign order (a stable sort, so an unmoved row never moves), one per line,
LF endings: running it again on the same state changes nothing (kitdata.py).

Never written: conflicts (unless --take-conflicts), game-moved rows (the page is stale: republish instead), unknown
stations, orphans, content the game changed after publish (content-stale), free-pick grants (FirstRefit) and page-made
cards; the report lists them for the owner. --check reports what would change and exits 1 if anything would, 0 if the
data already matches. --only limits stations to those page ids. After writing, kitdata.validate runs and the write is
refused if the data would not load. Run check.py and the Kit Ladder tests afterwards (kit-ladder.md, Apply).
"""
import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import kitdata  # noqa: E402
import ladder  # noqa: E402
from export_state import report  # noqa: E402

WRITTEN = ("move", "add")


def plan(entries, game, take_conflicts=False, only=None):
    """(changes {game id: station}, adds {game id: station}) from diff entries."""
    kinds = WRITTEN + (("conflict",) if take_conflicts else ())
    changes, adds = {}, {}
    for e in entries:
        if e["kind"] not in kinds or (only and e["id"] not in only):
            continue
        gid = game["gameIds"].get(e["id"])
        if gid is None:
            raise ladder.LadderError("%s has no game id" % e["id"])
        (changes if e["id"] in game["rowStations"] else adds)[gid] = e["page"]
    return changes, adds


def card_edits(entries, state, wanted):
    """{card id: page card} for the content entries named in ``wanted`` ("all" for every one)."""
    page = {k["id"]: k for lst in ("weapon", "armor") for k in state.get(lst) or []}
    return {e["id"]: page[e["id"]] for e in entries
            if e["kind"] == "content" and (wanted == {"all"} or e["id"] in wanted)}


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("folder")
    ap.add_argument("--check", action="store_true")
    ap.add_argument("--page")
    ap.add_argument("--only")
    ap.add_argument("--take-conflicts", action="store_true")
    ap.add_argument("--cards", help="card ids whose page seats or suits to write, or 'all'")
    args = ap.parse_args()
    state, game, entries, md = report(args.folder, args.page)
    only = set(args.only.split(",")) if args.only else None
    changes, adds = plan(entries, game, args.take_conflicts, only)
    edits = card_edits(entries, state, set(args.cards.split(","))) if args.cards else {}
    page_of = {v: k for k, v in game["gameIds"].items()}
    for item, station in sorted(changes.items()):
        print("move  %-40s %s -> %s" % (page_of[item], game["rowStations"][page_of[item]], station))
    for item, station in sorted(adds.items()):
        print("add   %-40s %s" % (item, station))
    for cid in sorted(edits):
        print("card  %s" % cid)
    skipped = [e for e in entries if e["kind"] not in WRITTEN + (("content",) if args.cards else ()) and e["kind"] != "unseen"]
    if skipped:
        print("not written (%d): %s" % (len(skipped), ", ".join(sorted({e["kind"] for e in skipped}))))

    ladder_doc = game["ladder"]
    cards_doc = kitdata.load_cards()
    new_ladder = ladder.patch(ladder_doc, changes, adds)
    new_cards = ladder.patch_cards(cards_doc, edits)
    old_text = (kitdata.read(kitdata.LADDER_JSON), kitdata.read(kitdata.CARDS_JSON))
    new_text = (kitdata.dump_ladder(new_ladder), kitdata.dump_cards(new_cards))
    if new_text == old_text:
        print("kit-ladder.json and loadout-cards.json already match the page.")
        return 0
    if args.check:
        print("--check: the data would change (%d moves, %d adds, %d cards); nothing written." % (len(changes), len(adds), len(edits)))
        return 1
    errors = kitdata.validate(new_ladder, new_cards)
    if errors:
        print("refused: the data would not load:\n  " + "\n  ".join(errors))
        return 1
    kitdata.write_ladder(new_ladder)
    kitdata.write_cards(new_cards)
    print("wrote %s and %s: %d moves, %d adds, %d cards" % (kitdata.LADDER_JSON.name, kitdata.CARDS_JSON.name,
                                                           len(changes), len(adds), len(edits)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
