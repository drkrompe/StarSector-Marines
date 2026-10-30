"""Stand the Kit Ladder page up on a new artifact (another Claude account, or a lost one) from a clean checkout.

    python tools/kits/bootstrap.py url                       print the recorded artifact url (exit 1 if none)
    python tools/kits/bootstrap.py render [--skip-reel] [--skip-dolls]
                                                             the page's rendered inputs: machine dolls (Gradle),
                                                             the weapon reel (Gradle + ffmpeg, minutes) and the cut
                                                             clips and posters, all under build/
    python tools/kits/bootstrap.py first-build [--out <html>]
                                                             the first page: posters only, no clip urls (a new
                                                             artifact has no asset store until a published version
                                                             declares `assets`)
    python tools/kits/bootstrap.py record-url <url>          record the new artifact's url in
                                                             tools/kits/review/published.json (artifactUrl); a url
                                                             unlike the recorded one empties clip-assets.json, whose
                                                             asset urls belong to the old artifact
    python tools/kits/bootstrap.py seed [--out <dir>]        the db seed: tools/kits/review/page-state.json and
                                                             weapon-notes.json as ArtifactData batch files

The Artifact-tool steps between these are an agent's (kit-ladder.md, "Moving to another account"; the kit-ladder
skill): publish the first build with capabilities {db, user, downloads, assets}, record its url, upload the clips 25
a call, `refresh.py record-assets`, `refresh.py build`, republish to the same url, `refresh.py record-published`,
then apply each seed batch with ArtifactData batch. Nothing here reaches the network.

seed writes <dir>/docs/<collection>/<doc id>.json (one document's fields each) and <dir>/batch-NN.json, each a list
of at most 50 ArtifactData batch writes {op: "set", collection, doc_id, file_path}: pass a file's list as the batch's
`writes`. The writes create documents, so they carry no if_version; on a db that already holds a document, read it
and add its version (or let the page keep its own state: the page reseeds nothing it already has).
"""
import json
import re
import shutil
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import ladder  # noqa: E402
import refresh  # noqa: E402

ROOT = ladder.ROOT
PAGE_STATE = ladder.REVIEW / "page-state.json"
NOTES = ladder.REVIEW / "weapon-notes.json"
SEED = ROOT / "build/kits/seed"
NO_CLIPS = refresh.CLIPS / "assets-none.json"
BATCH = 50  # ArtifactData batch: at most 50 writes
DOC_ID = re.compile(r"^(?!\.\.?$)[A-Za-z0-9_\-.~:@+]{1,200}$")
NOTE_FIELDS = ("text", "status", "weaponId", "name", "updatedAt")


def cmd_url(_):
    url = refresh.artifact_url()
    if not url:
        raise SystemExit("no artifact url recorded in %s: run 'bootstrap.py record-url <url>' after the first publish" % ladder.PUBLISHED)
    print(url)


def cmd_record_url(args):
    if not args or not re.match(r"^https://claude\.ai/(code/)?artifact/[\w-]+$", args[0]):
        raise SystemExit("record-url <https://claude.ai/artifact/<id>>")
    url = args[0]
    old = refresh.artifact_url()
    data = json.loads(ladder.PUBLISHED.read_text(encoding="utf-8")) if ladder.PUBLISHED.exists() else {}
    data["artifactUrl"] = url
    ladder.REVIEW.mkdir(parents=True, exist_ok=True)
    ladder.PUBLISHED.write_text(json.dumps(data, indent=1, sort_keys=True), encoding="utf-8", newline="\n")
    print("recorded %s in %s" % (url, ladder.PUBLISHED))
    if old and old != url:
        refresh.save_assets({})
        print("the url changed (was %s): emptied %s, so every clip uploads again" % (old, refresh.ASSETS))


def cmd_render(args):
    if "--skip-dolls" not in args:
        refresh.cmd_dolls([])
    if "--skip-reel" not in args:
        refresh.cmd_reel([])
    refresh.cmd_clips([])


def cmd_first_build(args):
    out = Path(args[args.index("--out") + 1]) if "--out" in args else refresh.PAGE
    index = refresh.CLIPS / "index.json"
    cmd = [sys.executable, str(ROOT / "tools/kits/build.py"), "--artifact"]
    dolls = refresh.DOLLS_DIR if (refresh.DOLLS_DIR / "manifest.json").exists() else refresh.PAGE_DOLLS
    if dolls.exists():
        cmd += ["--dolls", str(dolls)]
    else:
        print("warning: no machine dolls (run 'bootstrap.py render'): the Machines tab will have no pictures")
    if index.exists():
        NO_CLIPS.write_text("{}", encoding="utf-8", newline="\n")
        cmd += ["--reel", str(index), "--clips", str(NO_CLIPS)]
    else:
        print("warning: no %s (run 'bootstrap.py render'): the Weapons tab will be empty" % index)
    subprocess.run(cmd + [str(out)], check=True)


def seed_writes():
    """[(collection, doc id, fields)] for a fresh db: kits/state from page-state.json (the page stores its state as a
    JSON string in `json`, beside `at`) and one weapon-notes doc per clip id from weapon-notes.json."""
    writes = []
    if PAGE_STATE.exists():
        ps = json.loads(PAGE_STATE.read_text(encoding="utf-8"))
        state = ps["state"]
        for key in ("weapon", "armor", "sel"):
            if key not in state:
                raise SystemExit("%s: the state has no %r; the page would ignore it" % (PAGE_STATE, key))
        writes.append(("kits", "state", {"at": ps.get("at") or "", "json": json.dumps(state, ensure_ascii=False, separators=(",", ":"))}))
    else:
        print("no %s: kits/state is not seeded (the page seeds itself from the game data)" % PAGE_STATE)
    notes = json.loads(NOTES.read_text(encoding="utf-8")) if NOTES.exists() else {}
    for clip, note in sorted(notes.items()):
        writes.append(("weapon-notes", clip, {k: note.get(k) or "" for k in NOTE_FIELDS}))
    return writes


def validate(batches):
    """Problems with a list of batches (each a list of write dicts), as strings; empty when ArtifactData takes them."""
    problems = []
    for n, batch in enumerate(batches, 1):
        if not 1 <= len(batch) <= BATCH:
            problems.append("batch %d has %d writes (1-%d)" % (n, len(batch), BATCH))
        seen = set()
        for w in batch:
            if w.get("op") not in ("set", "update", "delete"):
                problems.append("batch %d: op %r" % (n, w.get("op")))
            if not DOC_ID.match(w.get("doc_id") or ""):
                problems.append("batch %d: doc id %r is not a valid ArtifactData id" % (n, w.get("doc_id")))
            if not all(DOC_ID.match(s) for s in (w.get("collection") or "").split("/")):
                problems.append("batch %d: collection %r" % (n, w.get("collection")))
            key = (w.get("collection"), w.get("doc_id"))
            if key in seen:
                problems.append("batch %d addresses %s/%s twice" % (n, *key))
            seen.add(key)
            if ("data" in w) == ("file_path" in w):
                problems.append("batch %d: %s/%s needs exactly one of data or file_path" % (n, *key))
    return problems


def cmd_seed(args):
    out = Path(args[args.index("--out") + 1]) if "--out" in args else SEED
    if out.exists():
        shutil.rmtree(out)
    writes, entries = seed_writes(), []
    for collection, doc_id, fields in writes:
        path = out / "docs" / collection / (doc_id + ".json")
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(fields, indent=1, ensure_ascii=False), encoding="utf-8", newline="\n")
        entries.append({"op": "set", "collection": collection, "doc_id": doc_id, "file_path": str(path.resolve())})
    batches = [entries[i:i + BATCH] for i in range(0, len(entries), BATCH)]
    problems = validate(batches)
    if problems:
        raise SystemExit("seed: " + "; ".join(problems))
    out.mkdir(parents=True, exist_ok=True)
    for n, batch in enumerate(batches, 1):
        (out / ("batch-%02d.json" % n)).write_text(json.dumps(batch, indent=1), encoding="utf-8", newline="\n")
    print("seed: %d documents in %d batch file(s) under %s; apply each with ArtifactData batch (writes = the file's list)"
          % (len(entries), len(batches), out))


COMMANDS = {"url": cmd_url, "record-url": cmd_record_url, "render": cmd_render, "first-build": cmd_first_build,
            "seed": cmd_seed}


def main():
    if len(sys.argv) < 2 or sys.argv[1] not in COMMANDS:
        raise SystemExit(__doc__)
    COMMANDS[sys.argv[1]](sys.argv[2:])


if __name__ == "__main__":
    main()
