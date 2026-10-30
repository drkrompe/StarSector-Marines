"""Export the Kit Ladder page's saved state and weapon notes, and report how the page differs from the game.

    python tools/kits/export_state.py <export dir> [--page <page.html>] [--no-notes]

<export dir> is the folder an agent filled with the ArtifactData tool (no script can reach the artifact's
database; kit-ladder.md, "Export"):
    ArtifactData get  url=<page> collection=kits         doc_id=state           out_dir=<export dir>
    ArtifactData list url=<page> collection=weapon-notes query={"limit": 1000} out_dir=<export dir>
which leaves <export dir>/kits/state.json and <export dir>/weapon-notes/<clip id>.json.

Writes, never touching game data:
  tools/kits/review/weapon-notes.json   the owner's notes and statuses per weapon clip, normalized and tracked
                                        (review input, not game data; skipped with --no-notes or with no notes)
  build/kits/export/ladder-state.json   the page state, normalized (untracked: the repo's data is canonical)
  build/kits/export/ladder-diff.md      the diff report, also printed: adds, moves, conflicts, orphans, unknown
  build/kits/export/ladder-diff.json    stations, card content edits (ladder.diff documents each kind)
The diff is three-way when the published page's manifest is known (--page <html from an Artifact read>, else
tools/kits/review/published.json): it then tells an owner edit (move) from a game change the page has not seen
(game-moved). apply_ladder.py reads the same export dir and writes the move and add rows (and, with --cards,
the accepted card contents).
"""
import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import kitdata  # noqa: E402
import ladder  # noqa: E402

OUT = ladder.ROOT / "build/kits/export"
NOTES = ladder.REVIEW / "weapon-notes.json"


def load_notes(folder):
    """{clip id: note}; a note filed under a clip id or weapon id the game has since renamed (kitdata.RETIRED_CLIPS,
    kitdata.RETIRED_WEAPONS) is read as its successor's, the newer note winning when both exist."""
    notes = {}
    for f in sorted((Path(folder) / "weapon-notes").glob("*.json")):
        doc = json.loads(f.read_text(encoding="utf-8"))
        note = {k: doc.get(k) or "" for k in ("weaponId", "name", "status", "text", "updatedAt")}
        note["weaponId"] = kitdata.RETIRED_WEAPONS.get(note["weaponId"], note["weaponId"])
        clip = kitdata.RETIRED_CLIPS.get(f.stem, f.stem)
        if clip not in notes or note["updatedAt"] > notes[clip]["updatedAt"]:
            notes[clip] = note
    return notes


def state_path(folder):
    path = Path(folder) / "kits" / "state.json"
    if not path.exists():
        raise SystemExit("export_state.py: %s is missing; fetch it with ArtifactData get (see this file's docstring)" % path)
    return path


def report(folder, page=None):
    """(state, game, entries, markdown) for an export dir."""
    state = ladder.load_state(state_path(folder))
    game = ladder.game()
    base = ladder.load_base(page)
    station_ids = {sid for _, sid in game["stations"]}
    entries = ladder.diff(state, game["rowStations"], game["cards"], game["items"], station_ids,
                          base, game["pieces"])
    names = dict(game["names"])
    names.update({k["id"]: k.get("name") for lst in ("weapon", "armor") for k in state.get(lst) or [] if k["id"] not in names})
    counts = {}
    for e in entries:
        counts[e["kind"]] = counts.get(e["kind"], 0) + 1
    md = ["# Kit Ladder: page state against the game data", "",
          "Page state saved %s. Base: %s." % (state.get("_savedAt") or "unknown",
                                              "the given page" if page else "tools/kits/review/published.json" if base else
                                              "none (two-way: every disagreement reads as a move)"),
          "", "Counts: " + (", ".join("%s %d" % kv for kv in counts.items()) or "none"), "",
          ladder.diff_table(entries, names)]
    edits = [e for e in entries if e["kind"] == "content" and e.get("detail")]
    if edits:
        md += ["## Card content edits on the page", "",
               "Seats are read game -> page; a card the game changed after publish (content-stale) is left out. A card's "
               "contents live in mod/data/marines/loadout-cards.json (a weapon card's seats, a tactic sheet's suits); "
               "on the owner's go-ahead apply_ladder.py --cards <ids> writes the accepted ones.", ""]
        for e in edits:
            md += ["### %s (`%s`)" % (names.get(e["id"], ""), e["id"]), ""] + ["- " + d for d in e["detail"]] + [""]
    return state, game, entries, "\n".join(md)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("folder")
    ap.add_argument("--page", help="the published page's HTML (an Artifact read's saved file) as the diff's base")
    ap.add_argument("--no-notes", action="store_true")
    args = ap.parse_args()
    state, game, entries, md = report(args.folder, args.page)
    OUT.mkdir(parents=True, exist_ok=True)
    clean = {k: v for k, v in state.items() if k in ("weapon", "armor", "place")}
    clean["place"] = dict(sorted((clean.get("place") or {}).items()))
    (OUT / "ladder-state.json").write_text(json.dumps(clean, indent=1, ensure_ascii=False), encoding="utf-8", newline="\n")
    (OUT / "ladder-diff.md").write_text(md, encoding="utf-8", newline="\n")
    (OUT / "ladder-diff.json").write_text(json.dumps(entries, indent=1), encoding="utf-8", newline="\n")
    notes = load_notes(args.folder)
    if notes and not args.no_notes:
        ladder.REVIEW.mkdir(parents=True, exist_ok=True)
        NOTES.write_text(json.dumps(dict(sorted(notes.items())), indent=1, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")
        print("notes: %d clips (%d with text) -> %s" % (len(notes), sum(1 for n in notes.values() if n["text"]), NOTES))
    print(md)
    print("wrote %s" % OUT)


if __name__ == "__main__":
    main()
