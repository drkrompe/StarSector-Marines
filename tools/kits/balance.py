"""Run the cheap balance evidence and gather its tables into one markdown for review.

    python tools/kits/balance.py [--tasks cards,ttk,armor,loadout] [--collate-only] [--out <md>]

Default subset, each an opt-in Gradle task (evidence-tasks.md), run one after another in this worktree:
  cards    loadoutCardSheet                                  every card's seats, ratings and channels
  ttk      generatedSpaceEvidence --tests '*TtkReportTest'   time to kill per family and per primary
  armor    armorMatchup (its default ladder)                 machines against machines
  loadout  loadoutMatchup -Pscenarios=open-field -Pseeds=2 -Pgifs=false
                                                             every card against the starter issue, open ground
Writes build/reports/balance/kit-ladder-balance.md (the run's commit, each task's command, status and time, then its
tables). --collate-only skips Gradle and gathers whatever reports are already there. Never part of test or check.
JAVA_HOME is taken from the environment, else the IntelliJ runtime CLAUDE.md names. Never run two of these at once
in one worktree.
"""
import argparse
import subprocess
import sys
import time
from datetime import datetime, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import ladder  # noqa: E402
from refresh import gradle  # noqa: E402

ROOT = ladder.ROOT
REPORTS = ROOT / "build/reports"
TASKS = {
    "cards": (["loadoutCardSheet"], [REPORTS / "loadout-cards/cards.md"]),
    "ttk": (["generatedSpaceEvidence", "--tests", "*TtkReportTest"],
            [REPORTS / "balance/ttk-baseline.md", REPORTS / "balance/ttk-primaries.md"]),
    "armor": (["armorMatchup"], [REPORTS / "armor-matchups/armor-matchups.md"]),
    "loadout": (["loadoutMatchup", "-Pscenarios=open-field", "-Pseeds=2", "-Pgifs=false"],
                [REPORTS / "loadout-matchups/matchups.md"]),
}
OUT = REPORTS / "balance/kit-ladder-balance.md"


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--tasks", default=",".join(TASKS))
    ap.add_argument("--collate-only", action="store_true")
    ap.add_argument("--out", default=str(OUT))
    args = ap.parse_args()
    names = [t.strip() for t in args.tasks.split(",") if t.strip()]
    unknown = [t for t in names if t not in TASKS]
    if unknown:
        raise SystemExit("unknown tasks %s; choose from %s" % (unknown, ", ".join(TASKS)))
    commit = subprocess.run(["git", "rev-parse", "--short", "HEAD"], cwd=ROOT, capture_output=True, text=True).stdout.strip()
    status = {}
    for name in names:
        cmd, _ = TASKS[name]
        if args.collate_only:
            status[name] = ("not run (collated)", 0.0)
            continue
        began = time.time()
        try:
            gradle(*cmd)
            status[name] = ("ok", time.time() - began)
        except subprocess.CalledProcessError as e:
            status[name] = ("FAILED (exit %d)" % e.returncode, time.time() - began)
    md = ["# Kit Ladder balance evidence", "",
          "Commit `%s`, %s. Headless evidence, not balance acceptance: read rows against each other "
          "(evidence-tasks.md)." % (commit, datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M UTC")), "",
          "| Report | Command | Status | Time |", "|---|---|---|---|"]
    for name in names:
        st, secs = status[name]
        md.append("| %s | `gradlew %s` | %s | %.0f s |" % (name, " ".join(TASKS[name][0]), st, secs))
    md.append("")
    for name in names:
        for path in TASKS[name][1]:
            md += ["## %s: %s" % (name, path.relative_to(ROOT).as_posix()), ""]
            if path.exists():
                age = datetime.fromtimestamp(path.stat().st_mtime, timezone.utc).strftime("%Y-%m-%d %H:%M UTC")
                body = path.read_text(encoding="utf-8")
                # Demote the report's own headings so they nest under this one.
                body = "\n".join(("##" + line) if line.startswith("#") else line for line in body.splitlines())
                md += ["_Written %s._" % age, "", body, ""]
            else:
                md += ["_Missing: the task did not write it._", ""]
    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text("\n".join(md), encoding="utf-8", newline="\n")
    print("wrote %s" % out)
    for name in names:
        print("  %-8s %s" % (name, status[name][0]))
    return 0 if all(s[0] in ("ok", "not run (collated)") for s in status.values()) else 1


if __name__ == "__main__":
    sys.exit(main())
