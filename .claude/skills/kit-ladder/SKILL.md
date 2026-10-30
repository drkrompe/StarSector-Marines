---
name: kit-ladder
description: Work the Kit Ladder - the owner's artifact page for placing loadout cards and machine patterns on unlock stations, editing card seats and noting weapon clips - and the repo tools behind it (tools/kits/). Use when the owner says "notes in" or "ladder updated", when kit-ladder.json or loadout-cards.json changes, when the page needs a refresh or republish, when weapon clips or clip ids change, or when the page must move to a new artifact or Claude account.
---

# Kit Ladder

The page is an editor over the game data, never a second source. Detail lives in
`roadmap/progression/design/kit-ladder.md` (the runbook); this is the working
summary.

## Principles

- **The data files are canonical**: `mod/data/marines/kit-ladder.json` (stations,
  placements) and `mod/data/marines/loadout-cards.json` (weapon cards, tactic
  sheets). The page is built from them by `tools/kits/build.py`.
- **Nothing writes the repo behind the owner's back.** Page edits reach the game
  only through export -> diff report -> apply on the owner's explicit go-ahead.
- **The tools read repo files, never the network.** Only the agent's Artifact and
  ArtifactData tool calls touch the artifact; scripts read what those calls saved.
- **The artifact url** is `artifactUrl` in `tools/kits/review/published.json`;
  `python tools/kits/bootstrap.py url` prints it. Never hardcode it.
- Tracked review state: `tools/kits/review/` (`weapon-notes.json`,
  `page-state.json`, `clip-assets.json`, `published.json`). Rendered media
  (reel, clips, posters, dolls, built page) stays under `build/`, never committed.

## The owner's workflow

1. Owner says **"notes in"** or **"ladder updated"**.
2. Export (below), then report the diff (`build/kits/export/ladder-diff.md`) and
   the notes. Moves and adds are listed for approval; content edits, orphans and
   conflicts are listed as decisions.
3. On the owner's go-ahead only: `apply_ladder.py`, `check.py`, the touched
   tests (`KitLadderTest`, `KitLadderCatalogTest`), integrate.
4. Refresh the page so it seeds from the new data; `refresh.py record-published`.

## Commands (run from the worktree root)

| Command | When |
|---|---|
| `python tools/kits/export_state.py <dir> [--page <html>]` | After the ArtifactData export: writes `review/weapon-notes.json`, `review/page-state.json`, and the diff report |
| `python tools/kits/apply_ladder.py <dir> --check` | Preview what an apply would write |
| `python tools/kits/apply_ladder.py <dir> [--only ids] [--cards ids\|all]` | Owner approved: write moves/adds (and accepted card seats with `--cards`) |
| `python tools/kits/check.py [--page <html>] [--reel <index.json>]` | After any ladder, card, pattern or weapon change; before every publish. Exit 1 = errors |
| `python tools/kits/check.py --self-test` | After editing `tools/kits/` |
| `python tools/kits/balance.py [--tasks ...]` | Before and after a balance pass |
| `python tools/kits/refresh.py reel [--only KEYS]` | Weapons look or sound different (Gradle, minutes: run in the background and poll) |
| `python tools/kits/refresh.py dolls` | Machine patterns changed (Gradle `createSnapshots -Psnapshot=machine-dolls`) |
| `python tools/kits/refresh.py clips` / `pending` | Cut clips + posters into `build/kits/clips/` and list uploads, 25 a batch / list again without cutting |
| `python tools/kits/refresh.py from-page <html>` | No render: reuse the published page's clips, posters, urls and dolls |
| `python tools/kits/refresh.py record-assets <json>` | After uploads: `{"<clip id>": "<url exactly as returned>"}` |
| `python tools/kits/refresh.py build` | Build `build/kits/kit-ladder.html` (artifact form) |
| `python tools/kits/refresh.py record-published` | After a successful publish; commit `tools/kits/review/` |
| `python tools/weapon_reel.py [--only KEYS] [--skip-render]` | What `refresh.py reel` wraps; `--skip-render` re-cuts existing renders |
| `python tools/kits/cut_weapon_clips.py <index.json> <out>` | What `refresh.py clips` wraps |
| `python tools/kits/bootstrap.py url\|render\|first-build\|record-url\|seed` | New artifact / new account (below) |

## Artifact tool steps

- **Export**: load the tool with `ToolSearch select:ArtifactData`, then
  `ArtifactData get` (`collection: kits`, `doc_id: state`, `out_dir: <dir>`) and
  `ArtifactData list` (`collection: weapon-notes`, `query: {"limit": 1000}`,
  `out_dir: <dir>`), `<dir>` in the scratchpad. Then `export_state.py <dir>`.
- **Read the page**: `Artifact read` with the url; the saved html feeds
  `--page` and `refresh.py from-page`.
- **Republish**: `Artifact` publish, `file_path build/kits/kit-ladder.html`,
  `url` the recorded url. Omit `capabilities` to keep them.
- **Upload clips**: `Artifact` publish with `url`, `asset: true`, `file_paths`
  at most **25** mp4s per call (`refresh.py clips` prints the batches). Record
  the returned urls with `refresh.py record-assets`.
- **Clip ids change on a rename.** Clip ids are the weapon reel's shot names, so
  renaming a weapon changes them, and the db's `weapon-notes` docs stay under
  the old id. The tools translate old ids through `kitdata.RETIRED_CLIPS` /
  `RETIRED_WEAPONS` / `RETIRED_PAGE_IDS` / `RETIRED_SPECIALS` (add the old ->
  new pair there with every rename). Migrate the db at the next refresh: one
  `ArtifactData batch` that `set`s each note under its new id (fields `text,
  status, weaponId, name, updatedAt`, no `if_version` for a new doc) and
  `delete`s the old doc (with the `version` you read as `if_version`).

## First publish quirk

Assets can only be uploaded once a **published version declares `assets`**. A new
artifact therefore goes up twice: first a posters-only page with the
capabilities declared, then (after the uploads) the page with clip urls.

## New artifact or new Claude account

Before switching (on the old account): export as above and run
`export_state.py <dir>`; commit `tools/kits/review/` (the notes and the page
state are then on disk). On the new account, from a clean checkout:

1. `python tools/kits/bootstrap.py render` (dolls, reel, clips; minutes, run in
   the background). `--skip-reel`/`--skip-dolls` reuse what `build/` has.
2. `python tools/kits/bootstrap.py first-build`, then `python tools/kits/check.py`.
3. `Artifact` publish `build/kits/kit-ladder.html` (no `url`) with
   `capabilities: {"db": {}, "user": {}, "downloads": true, "assets": {}}`
   declared explicitly, and an `icon`. Load `artifact-capabilities` first.
4. `python tools/kits/bootstrap.py record-url <new url>` (empties
   `clip-assets.json`, whose urls belonged to the old artifact), then
   `python tools/kits/refresh.py pending`.
5. Upload each batch of 25, `refresh.py record-assets`, `refresh.py build`,
   republish to the new url, `refresh.py record-published`.
6. `python tools/kits/bootstrap.py seed`, then for each
   `build/kits/seed/batch-NN.json`: `ArtifactData batch` with `writes` = the
   file's list. Then `ArtifactData get kits/state` to confirm.
7. Replace the old url wherever docs list it (`grep -rn <old id> roadmap tools`;
   `tools/picks/pages.md` has a row), commit `tools/kits/review/` and the docs.

## Gotchas

- Windows: set `JAVA_HOME` (`refresh.py` defaults to the IDEA jbr) before
  Gradle; never run `gradlew --stop`.
- The reel takes minutes; start it with `run_in_background` and wait for the
  notification instead of blocking.
- Never commit `build/` output, clips or the built page.
- The page keeps the owner's saved state on load and reseeds only items or
  stations it has never seen, so a republish never loses page edits.
