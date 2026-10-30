# Kit Ladder: the page, the game data and the tools between them

Status: ACTIVE

Written: 2026-10-30

The Kit Ladder is defined in `progression-nouns.md` (Kit Ladder): the owner-authored
table placing every loadout card and machine pattern on an unlock station. This doc
is the process around it: what is canonical, the owner's editing page, and the
repeatable tools that move edits, notes and checks between the page and the repo.
`heavy-equipment-progression.md` and `mechs-nouns.md` own what the patterns are.

## What is canonical

- **The game data is the single source of truth.** `KitLadder` holds one row per
  card and pattern; `UnlockStation` holds the stations in campaign order;
  `FirstRefit` owns Relief's free pick; `SquadEquipmentDoctrines`,
  `ArmorSheetBundles` and `MechPattern` own what each card holds and each
  pattern's tier.
- **The page is an editor over it, never a second source.** The Kit Ladder
  artifact (`https://claude.ai/artifact/LuCPJvBHLeTQG1CpXHRRbC`) is built by
  `tools/kits/build.py`, which seeds every placement from `KitLadder`. The owner's
  edits are saved in the artifact's database: doc `kits/state` (field `json`: the
  weapon and armour cards with their stations, grants and seats, and the `place`
  map of machine licences) and collection `weapon-notes` (one doc per weapon clip:
  note text and status).
- **Nothing writes the repo behind the owner's back.** Page edits reach the game
  only through an export, a diff report and an apply on the owner's go-ahead.
- **Tracked review state** lives in `tools/kits/review/`, never in living docs:
  `weapon-notes.json` (the owner's notes per clip, exported), `clip-assets.json`
  (each clip's uploaded asset url and the hash of the mp4 uploaded) and
  `published.json` (the manifest of the page last published: per-item
  fingerprints and seeded stations). Accepted check exceptions live in
  `tools/kits/check-allow.json`, each with a reason.

## The owner's workflow

1. The owner edits on the page: drags cards and licences between stations, edits
   card seats, writes weapon notes and statuses.
2. The owner tells Claude "ladder updated" or "notes in".
3. Claude exports (below) and reports the diff and the notes. Moves and adds are
   listed for approval; card content edits, free-pick changes, page-made cards,
   orphans and conflicts are listed as decisions.
4. On the owner's go-ahead, Claude applies the accepted stations, authors any
   accepted content edits by hand, runs the checks and the touched tests, and
   integrates.
5. Claude refreshes the page so it seeds from the new game data, and records the
   publish.

## Tools

| Command | What it does | When |
|---------|--------------|------|
| `python tools/kits/export_state.py <dir> [--page <html>]` | Reads the ArtifactData export, writes `tools/kits/review/weapon-notes.json` and the diff report `build/kits/export/ladder-diff.md` | "notes in", "ladder updated" |
| `python tools/kits/apply_ladder.py <dir> [--check] [--only ids]` | Writes accepted moves and adds into `KitLadder`, deterministically; `--check` only reports | After the owner approves a diff |
| `python tools/kits/check.py [--page <html>] [--reel <index.json>]` | Validates rows, items, prerequisites, tiers, staleness and the Weapons tab; exit 1 on errors | After any ladder, card, pattern or weapon change; before a publish |
| `python tools/kits/check.py --self-test` | The tools' own logic on small inputs | After editing `tools/kits/` |
| `python tools/kits/balance.py [--tasks ...]` | Runs the cheap balance evidence and gathers it into `build/reports/balance/kit-ladder-balance.md` | Before and after a balance pass |
| `python tools/kits/refresh.py <step>` | The local steps of a page refresh (runbook below) | After game data changes the page shows |

### Export

No script can reach the artifact's database, so an agent fetches it first with
the ArtifactData tool (load it with `ToolSearch select:ArtifactData`), into a
scratch folder:

- `ArtifactData get`, `url` the page, `collection: kits`, `doc_id: state`,
  `out_dir: <dir>`;
- `ArtifactData list`, `url` the page, `collection: weapon-notes`,
  `query: {"limit": 1000}`, `out_dir: <dir>`.

Then `python tools/kits/export_state.py <dir>`. The diff is three-way when the
published page's manifest is known (`--page` with the file an `Artifact read`
saved, else `tools/kits/review/published.json`): a station the owner moved reads
`move`, one the game moved since publish reads `game-moved` (republish, do not
apply), both moving reads `conflict`. Card seats compare seat by seat for weapon
cards and as a set of suits for tactic sheets (the game seats a sheet's suits by
role), and a seat naming a piece the game lacks is flagged. Commit the notes file
with the work it prompted.

### Apply

`apply_ladder.py` rewrites only `move` and `add` rows (`--take-conflicts` for an
approved conflict), keeps each block of rows in campaign order with a stable
sort, and regenerates the `UnlockStation` static imports; a second run changes
nothing. It never writes card contents, grants, page-made cards or orphans. Then
run `check.py` and the tests covering `KitLadder` and the store.

### Checks

Errors: a malformed row, an unknown station, an item placed twice, a row naming
no real item, a real item with no row, the page's station list out of step with
`UnlockStation`, a free pick off Relief, a clip firing a weapon no catalog defines.
Warnings, each acceptable in `check-allow.json` as `"<code> <id>": "<reason>"`:
`carrier` (a vehicle kit before any vehicle hull, a mech part before any
chassis), `restricted` (a Restricted card on a station still needs a warrant),
`start-tier`, `not-sold` (a `none` item, with where else the code names it),
`stale` (changed since publish), `no-clip`, `no-upload`, `orphan-note`, and `gap`
(the known code gaps below, which clear themselves once fixed).

## Refresh runbook

Local script steps and Artifact-tool steps alternate; the clip ids are the weapon
reel's shot names, so notes keyed by them survive every refresh.

1. Local, only when weapons look or sound different:
   `python tools/kits/refresh.py reel` (Gradle, minutes), then
   `python tools/kits/refresh.py clips`, which cuts clips and posters into
   `build/kits/clips/` and lists the clips to upload, 25 a batch.
   Otherwise: `Artifact read` the page and run
   `python tools/kits/refresh.py from-page <saved html>`, which reuses the
   published clips, posters, asset urls and machine dolls.
2. Artifact tool, after `clips` only: for each batch, `Artifact` publish with
   `url` the page, `asset: true`, `file_paths` the batch; write the returned urls
   as `{"<clip id>": "<url exactly as returned>"}` and run
   `python tools/kits/refresh.py record-assets <that json>`.
3. Local: `python tools/kits/refresh.py dolls` when the machines changed
   (otherwise the page's dolls are reused), then
   `python tools/kits/refresh.py build` and `python tools/kits/check.py`.
4. Artifact tool: `Artifact` publish with `file_path`
   `build/kits/kit-ladder.html` and `url` the page (capabilities are kept).
5. Local: `python tools/kits/refresh.py record-published`; commit
   `tools/kits/review/`.

The page's database is untouched by a refresh: on load the page keeps the owner's
saved state and reseeds only items it has never placed or stations it does not know.

## Open questions

- Vehicle kits (spall liner, ready rack) go on sale at Relief, before any vehicle
  hull is sold (`carrier`).
- Restricted sheets still need a warrant, so "the equipment wall opens at Working
  Passage" is not yet true for them (`restricted`).
- The Outlaw Heavy sheet never sells in practice; the Boarding Jack is
  story-reserved.
- A station opens a sale, but market pools and rotation still decide what a shelf
  shows on a given visit.
- `EquipmentCollectionCurve` grants free cards regardless of stations, and
  `FactionEquipmentCatalog.validateReachability` still uses the old tier rules
  (`gap`).
- A submod's cards have no row, so they read as never sold.
- Card seat edits made on the page wait for hand authoring; the page has no way to
  mark one accepted or declined.
