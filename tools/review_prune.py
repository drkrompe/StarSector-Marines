#!/usr/bin/env python3
"""Prune old review runs on the shared Drive folder.

Layout (owner-review.md, "Review sets"):
    <drive>/starsector/review/<asset>/<YYYY-MM-DD>_<what>_<machine>/

Per asset, the newest KEEP_RUNS run folders stay, plus any run the owner
marked: a folder name ending in "_keep", or a file named KEEP inside it.
Everything else is deleted. On Google Drive for Desktop a delete goes to
Drive's trash, which keeps it for 30 days, so a prune can be undone there.

Dry run by default; pass --apply to delete. A session that posts a run
prunes that asset only (--asset <asset> --apply), so cleanup happens as work
lands and two sessions only meet if they post the same asset at once.
"""
import argparse
import glob
import os
import shutil
import sys

KEEP_RUNS = 3


def default_root():
    env = os.environ.get("STARSECTOR_DRIVE")
    if env:
        return env
    if sys.platform == "win32":
        return r"G:\My Drive\starsector"
    hits = glob.glob(os.path.expanduser("~/Library/CloudStorage/GoogleDrive-*/My Drive/starsector"))
    return hits[0] if hits else ""


def posted(path):
    """When a run was posted: its newest file's modification time (the folder's own if it is empty)."""
    times = [os.path.getmtime(os.path.join(path, f)) for f in os.listdir(path)]
    return max(times) if times else os.path.getmtime(path)


def order(adir, name):
    """Runs sort by their YYYY-MM-DD prefix, then, among runs of one day, by when they were posted.

    The name alone used to decide within a day, so a day's newest run could sort
    first and be pruned (it happened: a run whose description began with 'h'
    sorted before an older 'p' and 's' run of the same date)."""
    return (name[:10], posted(os.path.join(adir, name)), name)


def marked(run):
    return os.path.basename(run).endswith("_keep") or os.path.exists(os.path.join(run, "KEEP"))


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--root", default=default_root(), help="the Drive starsector folder")
    ap.add_argument("--keep", type=int, default=KEEP_RUNS, help="newest runs kept per asset")
    ap.add_argument("--asset", help="prune only this asset's runs")
    ap.add_argument("--apply", action="store_true", help="delete; without it, only report")
    args = ap.parse_args()

    review = os.path.join(args.root, "review")
    if not os.path.isdir(review):
        sys.exit(f"no review folder at {review}")

    doomed = []
    for asset in sorted(os.listdir(review)):
        adir = os.path.join(review, asset)
        if not os.path.isdir(adir) or (args.asset and asset != args.asset):
            continue
        runs = sorted((d for d in os.listdir(adir) if os.path.isdir(os.path.join(adir, d))),
                      key=lambda d: order(adir, d))
        newest = set(runs[-args.keep:]) if args.keep > 0 else set()
        for r in runs:
            path = os.path.join(adir, r)
            state = "keep (newest)" if r in newest else "keep (marked)" if marked(path) else "prune"
            print(f"{state:14} {asset}/{r}")
            if state == "prune":
                doomed.append(path)

    if not doomed:
        print("nothing to prune")
        return
    if not args.apply:
        print(f"{len(doomed)} run(s) would be pruned; pass --apply to delete")
        return
    for path in doomed:
        shutil.rmtree(path)
    print(f"pruned {len(doomed)} run(s); Drive's trash holds them for 30 days")


if __name__ == "__main__":
    main()
