# Kit Ladder: the page, the game data and the tools between them

Status: ACTIVE

Written: 2026-10-30

Updated: 2026-10-30 — tactic sheets can be seated suit by suit, and the start cards are the new company's baseline.

The Kit Ladder is defined in `progression-nouns.md` (Kit Ladder): the owner-authored
table placing every loadout card and machine pattern on an unlock station. This doc
is the process around it: what is canonical, the owner's editing page, and the
repeatable tools that move edits, notes and checks between the page and the repo.
`heavy-equipment-progression.md` and `mechs-nouns.md` own what the patterns are.

## What is canonical

- **The data files are the single source of truth.** `mod/data/marines/kit-ladder.json`
  holds the stations in campaign order, each with its reach condition, and one
  placement per card and machine pattern; `mod/data/marines/loadout-cards.json`
  holds every built-in weapon card (twelve seats) and tactic sheet (twelve seated
  suits, or a role plan and its suits). `KitLadderCatalog` loads and validates both at application load;
  `KitLadder`, `UnlockStation`, `SquadEquipmentDoctrines`, `ArmorSheetBundles` and
  `FirstRefit` are thin readers over it. Code still owns what is algorithmic: the
  closed set of reach condition types (`UnlockStation.Reach`), how an unseated
  sheet's suits are dealt to seats by role (`ArmorIssueResolver`), which Wayline paths exist
  (`FirstRefit.Path`, named in saves), and each pattern's tier (`MechPattern`).
- **The page is an editor over it, never a second source.** The Kit Ladder
  artifact (`https://claude.ai/artifact/LuCPJvBHLeTQG1CpXHRRbC`) is built by
  `tools/kits/build.py`, which seeds every placement and card from the data files. The owner's
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
4. On the owner's go-ahead, Claude applies the accepted stations and card
   contents (`apply_ladder.py`, `--cards` for contents), runs the checks and the
   touched tests, and integrates.
5. Claude refreshes the page so it seeds from the new game data, and records the
   publish.

## Data

Both files are strict JSON (no comments), written by `tools/kits/kitdata.py` one
placement or seat per line with LF endings, so an edit is a one-line diff and a
rewrite of unchanged data changes nothing. Edit them by hand or through the tools;
either way run `check.py`.

`kit-ladder.json`:

```json
{
  "stations": [
    {"id": "start", "reach": {"type": "always"}},
    {"id": "relief", "reach": {"type": "storyMission", "mission": "story_opening_relief"}},
    {"id": "junction", "reach": {"type": "arcBeat", "arc": "broken-supply-route", "beat": "junction-won"}}
  ],
  "placements": [
    {"item": "loadout-card:weapon:weapons:checkpoint", "station": "relief"},
    {"item": "mech-pattern:chassis:warden", "station": "junction"}
  ]
}
```

- **Stations** are in campaign order, `start` first and reached `always`. `none`
  is implicit (never sold) and never declared. A reach is one of three types:
  `always`; `storyMission` with `mission` (a story mission id the roster
  completes); `arcBeat` with `arc` and `beat`, read from that arc's save-owned
  ledger: `broken-supply-route` (`junction-won`, `closed`), `corvus-account`
  (`dust-ledger`, `blind-watch`, `cold-account`), `shepherd-recovery`
  (`claim-secured`, `repaired`, `departed`), `wellhollow-bloom` (`colony-breach`,
  `cordon-road`, `the-burn`, `broad-front`), `agreus` (`small-war-settled`). A
  new arc or beat is a Java change to `UnlockStation.Reach`; a new station on an
  existing beat is data only.
- **Placements** use the game's store ids (`LoadoutCard.id`, `MechPattern.id`), on
  a declared station or `none`; each item once. They are kept in blocks (weapon
  cards, sheets, chassis, mech weapons, internals, vehicles, kits), each in
  campaign order. An item with no placement is not sold; an id a save carries
  that the data no longer has reads as `none`.

`loadout-cards.json`:

```json
{
  "weaponCards": [
    {
      "id": "weapons:checkpoint",
      "name": "@squadDoctrineCheckpointWeaponsName",
      "description": "@squadDoctrineCheckpointWeaponsDescription",
      "members": [
        {"role": "Team Leader", "primary": "weapon.field-rifle", "grade": "SERVICE", "special": "special.smoke-grenade"},
        {"role": "Grenadier", "primary": "weapon.field-rifle", "grade": "SERVICE", "special": "special.flamer", "belt": "special.frag-grenade"}
      ]
    }
  ],
  "tacticSheets": [
    {
      "id": "armor:recon",
      "name": "@squadDoctrineReconArmorName",
      "description": "@squadDoctrineReconArmorDescription",
      "tradition": "TRITACHYON",
      "mix": "RECONNAISSANCE",
      "seats": ["armor.aegis-composite", "armor.scout", "armor.packframe", "armor.riot-shell", "..."]
    },
    {
      "id": "armor:outlaw-line",
      "name": "@squadDoctrineOutlawLineArmorName",
      "description": "@squadDoctrineOutlawLineArmorDescription",
      "tradition": "PIRATES",
      "mix": "BALANCED",
      "suits": ["armor.reaver", "armor.outlaw", "armor.packframe", "armor.scout"]
    }
  ]
}
```

- A **weapon card** has exactly twelve seats in billet order: `role` (an English
  role literal, translated by `BilletRoles`), `primary` (a marine primary weapon
  id), `grade` (`SURPLUS`, `SERVICE`, `MILSPEC`, `MASTERWORK`), optional
  `special` (a special-equipment id) and `belt` (a thrown special). Array order is
  the presentation order.
- A **tactic sheet** has `tradition` (`ArmorTradition`), `mix` (`BALANCED`,
  `BREACH`, `RECONNAISSANCE`, `LINE_HOLD`, `IRREGULAR`) and its suits, one of two ways:
  - **Seated**: `seats`, twelve armour ids in billet order, exactly what the game
    issues each seat (`SquadArmorPlan.seatSuitAt`). The card teaches the distinct
    suits in seat order; each seat's protection class and job are its suit's, so a
    standard sheet may seat power suits beside standard ones (the sheet is then a
    mixed section). `suits`, `armorClass` and `seatClasses` are derived and refused
    beside `seats`. A sheet the owner seats on the page and has applied is seated.
  - **Dealt by role**: `suits`, the armour ids the card teaches, with either
    `armorClass` (`POWER_SUIT` for an all-suit sheet; `STANDARD` is the default) or
    `seatClasses` (twelve, for a mixed section). The resolver deals the suits to
    seats by the mix's roles, class and tier, so a suit no seat can take is taught
    but never worn, and the page's seeded seating of such a sheet is only an even
    spread, not what the game issues.
- A card's **access tier** is the scarcest piece it carries, so seating a Prestige
  suit raises the whole card to Prestige (patron and recovery gates and polity
  release read it; the store's station does not).
- The cards placed on `start` are the **new company's baseline**: a new company
  holds them and exactly the equipment they name (`MarineArmory.startCards`), and
  save repair restores the same. Editing a start card on the page edits the
  baseline; tests read it from the data.
- `name` and `description` are string keys (`@key`) in `strings.json`.
  `"grant": "free-pick"` marks Relief's free pick, which must be exactly the
  `FirstRefit.Path` cards; every other card is `sale` (the default, omitted).
- **Validation** (`KitLadderCatalog.parse`, mirrored by `kitdata.validate` in
  `check.py`) refuses the whole load with every problem listed, each naming its
  file and entry: an unknown station, reach type, arc or beat; an item, primary,
  special, belt item or suit the game lacks; an item placed twice; a card with no
  placement; a squad or seated sheet not of twelve; a sheet with no suits, or with
  `seats` beside a derived field; a missing string key; a free pick that is not a
  Wayline path's.
- **Saves.** Cards are referenced by id, so an edited card applies on load. A
  save's own copy of a Wayline weapon card is re-dressed from the data on load
  (`MarineArmory.refreshWaylineCards`), and squads are re-dressed from their cards
  by the roster's refresh.

## Tools

| Command | What it does | When |
|---------|--------------|------|
| `python tools/kits/export_state.py <dir> [--page <html>]` | Reads the ArtifactData export, writes `tools/kits/review/weapon-notes.json` and the diff report `build/kits/export/ladder-diff.md` | "notes in", "ladder updated" |
| `python tools/kits/apply_ladder.py <dir> [--check] [--only ids] [--cards ids]` | Writes accepted moves and adds into `kit-ladder.json`, and with `--cards` (ids, or `all`) accepted seats and suits into `loadout-cards.json`, deterministically; `--check` only reports | After the owner approves a diff |
| `python tools/kits/check.py [--page <html>] [--reel <index.json>]` | Validates the data as the game loads it, plus prerequisites, tiers, staleness and the Weapons tab; exit 1 on errors | After any ladder, card, pattern or weapon change; before a publish |
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
apply), both moving reads `conflict`. Card seats compare seat by seat, for tactic
sheets against the seated suits (or, for a sheet still dealt by role, the even
spread the page was seeded with, so reseating one on the page reads as an edit), and
a seat naming a piece the game lacks is flagged. A seat's internal system override
on the page is not compared: a system belongs to its suit pattern in the game. Special ids the game
has since renamed (`kitdata.RETIRED_SPECIALS`: the rocket launcher, scrap rocket
and missile pod) are read as their current ids. Commit the notes file with the work
it prompted.

### Apply

`apply_ladder.py` rewrites only `move` and `add` placements (`--take-conflicts`
for an approved conflict) and, with `--cards`, the named `content` cards: a weapon
card takes the page's twelve seats, a sheet the page's twelve suits as its `seats`
(dropping its `suits`, `armorClass` and `seatClasses`). It never writes names,
descriptions, grants, system overrides, page-made cards or orphans, refuses a write that would not
load, and a second run changes nothing. Then run `check.py` and the tests covering
the ladder and the store (`KitLadderTest`, `KitLadderCatalogTest`).

### Checks

Errors: everything the game refuses at load (Data, Validation), a real item with
no placement, the page's station list out of step with `kit-ladder.json`, a free
pick off Relief, a clip firing a weapon no catalog defines.
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
- The page has no way to mark a card edit accepted or declined; the owner says so
  in chat and `apply_ladder.py --cards` takes the accepted ids.
- The page's station names and notes live in `build.py`, not in the data; they are
  page copy, not game text.
