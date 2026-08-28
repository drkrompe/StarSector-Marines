# Project: Starsector Marines

A Starsector mod (game version 0.98a-RC8). Source-of-truth game install is at
`C:\Program Files (x86)\Fractal Softworks\Starsector` — read-only reference, never edit.

For project vision, current focus, and immediate next-up, see
[`roadmap/`](roadmap/). Read `roadmap/README.md` first. Within a feature,
start with its canonical noun doc for the high-level model and
`design/stories.md` for the open-work board.

## Session worktrees (default workflow)

Keep the main workspace checked out on `main` and free of session edits so it
remains available as the integration point for concurrent sessions. For every
task that may change repository files, use this workflow unless the user
explicitly asks for a different one. Read-only investigation does not require a
worktree.

1. At the start of the task, inspect `git status` and `git worktree list`. If the
   session is already in a linked worktree under `.claude/worktrees/`, use it;
   do not create a nested worktree.
2. Otherwise, from the main workspace, create a uniquely named branch and
   linked worktree based on the current local `main`:

   ```powershell
   git worktree add -b session/<unique-name> .claude/worktrees/<unique-name> main
   ```

3. Change the session's working directory to that worktree. Perform all edits,
   builds, tests, staging, and commits there. Never make task changes directly
   in the main workspace.
4. Stage only paths owned by the session and commit all intended changes on the
   session branch. Do not use `git stash`, and do not modify, remove, or reuse
   another session's branch or worktree.
5. Before integration, merge the latest local `main` into the session branch
   inside the worktree and resolve conflicts there. Re-run relevant verification.
6. Return to the main workspace, verify that it is still on `main` and clean,
   then integrate with a fast-forward-only merge:

   ```powershell
   git merge --ff-only session/<unique-name>
   ```

   If `main` advanced and the fast-forward fails, go back to the session
   worktree, merge `main` again, verify, and retry. If the main workspace has
   uncommitted changes, do not absorb, discard, or overwrite them; leave the
   worktree intact and report the integration blocker.
7. Only after confirming that the session commit is reachable from `main`,
   remove the linked worktree and delete its merged branch from the main
   workspace:

   ```powershell
   git worktree remove .claude/worktrees/<unique-name>
   git branch -d session/<unique-name>
   ```

### Standing authorization to integrate

For any task in which the user has authorized repository changes, the repository
owner gives standing authorization to complete the entire local Git workflow:
stage the session's owned paths, commit the verified change, merge the latest
local `main` into the session branch, fast-forward local `main`, and clean up the
merged session worktree and branch. **Do not pause to ask for a separate
"merge it?" confirmation. Always merge a green, self-contained change into
local `main` when the workflow above permits it.**

This standing authorization does not turn a read-only diagnosis, explanation,
review, or status request into permission to edit files. It also does not
authorize pushing remotes, deploying the mod, publishing artifacts, or
discarding unrelated work. Those actions still require scope from the user's
request or another explicit project instruction.

Integrate at every coherent commit chain, not once at the end of a session.
A session that has reached a green, self-contained state should merge to `main`
before starting the next chunk, then keep working in the same worktree. Banking
several chains and merging them together only widens the window in which a
sibling session's merge turns into a conflict.

The main workspace is for brief integration and worktree administration only.
Do not run builds or leave generated task files there.

## Build & deploy

- Shell `JAVA_HOME`: `C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.2\jbr`.
  Set this explicitly before invoking Gradle from PowerShell; do not guess a
  Java install or substitute Starsector's bundled runtime:

  ```powershell
  $env:JAVA_HOME = 'C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.2\jbr'
  ```

- Toolchain: Eclipse Adoptium JDK 25 (registered via Gradle's auto-detected toolchain).
- Bytecode target: Java 17 (`--release 17`). The game ships Zulu 17.0.10 + `--enable-preview`,
  so do NOT use language features newer than Java 17, and do NOT rely on preview features
  at compile time.
- `gradlew.bat build` → `mod/jars/StarsectorMarines.jar` (directly into the mod folder; no
  intermediate copy step).
- `gradlew.bat commanderEvidence -Pmission=conquest` → runs the selected
  mission's documented construction-fixture matrix twice in a forced-serial,
  zero-input simulation and writes canonical traces plus `summary.json` /
  `summary.md` under `build/reports/commander/<mission>/`. Supported mission
  arguments are currently `conquest`, `sabotage`, `assault`, `raid`, and `extraction`; later mission harnesses
  extend that argument instead of creating another Gradle task. The run is
  opt-in and excluded from ordinary `test` / `build`. Use `-PmaxTicks=9000` or
  `-Pfixture=C:\path\to\fixture.json` for explicitly ad-hoc evidence. Add
  `-PsnapshotEveryTicks=300` to render neutral-observer PNG frames from the
  first replay and assemble `visuals/<fixture>/review.gif`; the frames add
  cyan marine, red defender, and yellow civilian markers for whole-map review. Optional
  `-PgifFrameDelayMillis=125`, `-PsnapshotWidth=960`, and
  `-PsnapshotHeight=640` arguments control review playback and output size.
- `gradlew.bat createSnapshots` → every deterministic visual-evidence suite under
  `build/snapshots/` without launching Starsector or creating an OpenGL context. Select
  suites with `-Psnapshot=armory,durability-bars,frontage-scene,layers,ship-decks,turrets,ui`
  (default `all`) and redirect the common output root with `-PsnapshotDir=<path>`.
- `gradlew.bat layerAuthoring` → extensible standalone authoring workbench. The
  Layers page provides drag, scale, rotation, variant-scoped phase-driven
  animation playback, combined-sheet export, the shared snapshot
  catalog, and validated atomic writes to
  `mod/data/appearance/unit-layer-layouts.appearance.json`.
  The Turrets page edits linked weapon, mount, structure, FX, and bounded
  multi-turret defense-post layout data with a live deterministic preview.
  It exposes specialized projectile/artillery behavior and audio; preview
  flight reads the authored burst, boost, arc, contrail, and directional
  launch-FX data instead of substituting a generic projectile treatment.
  New sheets are ingested through the `ingest-tileset` skill:
  `art-source/tilesets/measure_sheet.py` measures a sheet and drafts its authoring
  seed, and `ProjectTilesetSeedsTest` fails the build for raw art that arrives
  without one.
  The Tilesets page turns a raw art sheet into a loadable tileset. It lists every
  sheet under `art-source/tilesets/` with its state — raw, seeded, annotated,
  exported — so sheets are picked from the project rather than browsed for.
  Dropping a raw sheet there is enough to make it appear; a hand-written document
  carrying settings but no pieces is a valid seed and is sliced on open.
  The page finds pieces by keying on alpha and proposes a footprint for each from
  the sheet's stated `gridCols` x `gridRows` layout — cells need not be square,
  and a fused plate is cut into exactly that grid — but footprints are edited
  there rather than inferred, because
  how much deck a piece covers is a judgement about the object, not a measurement
  of the art. A piece becomes a doodad or a cell of a named autotile block; walls
  and corners are authored by grouping pieces into a block's slots, which the
  packer places as one contiguous patch. Export writes a packed atlas holding only
  the included pieces, its `*.tileset.json`, and a generated `*.tileset.md`
  catalog card; the atlas goes to `graphics/tilesets/` when the sheet declares
  blocks and `graphics/doodads/` when it is only props, unless the document names
  an `outputSheet`. Annotations are saved to
  `art-source/tilesets/<name>.tileset-authoring.json`, so a sheet can be annotated
  across several sittings; re-slicing carries existing annotations onto the newly
  found pieces and names any that no longer match.
  Its Map preview generates a city and draws it twice at one seed: as it ships,
  and with pieces bound through the "stands in for" column painted over the
  cells of the shipped id they are candidates for. The substitution is made in
  the pixels, so the two maps are the same map and only the art differs. The
  binding is preview-only and is never exported.
  All three pages validate before replacement; the Turrets page prepares every
  linked target before replacing files atomically and rolls back earlier files
  if a later replacement fails.
- `tools/authoring.sh <tool> [json]` (or `tools/authoring.cmd`) → call one
  authoring tool and exit. This is the **default** way to reach the authoring
  tools headlessly — list/measure/read/write/slice/export a tileset, render its
  map-preview comparison, run the snapshot catalog — with no workbench window
  and nothing to start first. `--list` names the tools, `--describe <tool>`
  prints its schema, `--json` returns the structured result. Arguments are one
  JSON object, inline or as `@file` or `-` for stdin; prefer `@file` from
  PowerShell, which rewrites quotes on their way to a native executable. Exit
  status is 1 when the tool reports a failure and 2 on a usage mistake. See the
  `authoring-tools` skill.
- `gradlew.bat installAuthoringTools` → writes the generated launchers under
  `build/authoring/` and prints the `.mcp.json` snippet that registers the same
  tools as an MCP stdio server. The wrappers above run this for you when the
  output is missing; run it yourself after a dependency change or a `clean`,
  since the launchers embed an absolute classpath and are therefore generated
  rather than checked in. Prefer the shell wrapper over MCP registration unless
  a session already has the server: an MCP stdio server must be registered
  before the session that wants it starts, which is exactly the constraint a
  one-shot command removes. Both entry points are separate front doors onto the
  same domain code, never an embedded server — an editor holding unsaved changes
  and a tool writing the same document would be two writers. See
  `authoring-mcp-server.md`.
- `gradlew.bat deployMod` → generates the gitignored `mod/sounds/` outputs
  (requires `ffmpeg` on `PATH`) and syncs `mod/` into
  `<starsectorDir>/mods/StarsectorMarines/`.
- `gradlew.bat runStarsector` → deploys then launches via `starsector-core/starsector.bat`.
- `gradlew.bat prepareCatalogSmoke` → stages the additive two-provider catalog
  acceptance fixture without launching Starsector. Pass
  `-PcatalogSmokeMode=collision` to stage the duplicate-id variant.
- `gradlew.bat catalogSmokeLive` → explicitly launches the installed Starsector
  executable against the isolated staged fixture, captures application-load
  evidence, and terminates only its launched process tree. It redirects mods,
  saves, screenshots, and logs under `build/catalog-smoke/`; use only when a
  live game-runtime acceptance pass is intentionally required.

### Visual snapshot workflow

`createSnapshots` is the only top-level task for deterministic visual evidence.
Do not add per-domain Gradle tasks such as `renderTurretPreviews`; add a
`SnapshotSuite` provider to the shared catalog instead. Both the Gradle task and
the layer-authoring workbench consume that catalog, so a registered suite is
available from automation and the editor without a second integration path.

The discovered suite ids and default output directories are:

| Suite | Evidence | Output |
|-------|----------|--------|
| `armory` | Loadout previews and their contact sheet | `build/snapshots/armory/` |
| `durability-bars` | Ownership matrix and an authored-profile magnitude ladder, true scale and magnified | `build/snapshots/durability-bars/` |
| `layers` | One combined composition sheet per authored unit | `build/snapshots/layers/` |
| `ship-decks` | Generated ship-deck plan views, tinted by longitudinal zone | `build/snapshots/ship-decks/` |
| `turrets` | Authored mount-state strips, including projectile and impact effects | `build/snapshots/turrets/` |
| `ui` | Retained Marine Ops screens at authored viewport sizes | `build/snapshots/ui/` |
| `frontage-scene` | Animated garrison stand-to on a generated compound, one loop per approach edge | `build/snapshots/frontage-scene/` |

Run all suites with `gradlew.bat createSnapshots`. Use
`-Psnapshot=<id>` for one suite or a comma-separated selector for several; quote
the whole property in PowerShell, for example
`'-Psnapshot=layers,turrets'`. `-PsnapshotDir=<path>` changes the shared output
root while retaining the per-suite subdirectories. Command-line generation
replaces matching files without prompting and does not remove stale ones, so a
suite that renames an artifact leaves the old name behind until it is deleted.

A suite artifact is a PNG or, for a suite whose evidence is a played battle
rather than a composition, an animated GIF built from a frame sequence. The
runner writes both; a suite never writes files itself. Animated review frames
come from `BattleReviewFrameRenderer`, shared with commander evidence so the
two do not drift into separate camera fits and marker palettes.

### Behavior scenes

`FrontageScene` is a small, purpose-built battle — one production-stamped
compound, a configurable number of defender garrisons and marine assault
squads, an approach edge — played headless in seconds. It exists because a
whole-mission harness is both slower and worse at answering a question about
one behavior: the scene found a real aperture-derivation bug in minutes that
two full Conquest runs had hidden entirely.

Prefer a scene over a mission harness whenever the question is about one
behavior rather than about a whole battle's balance, and add another scene
rather than widening this one past what its name claims.

`gradlew.bat test --tests '*FrontageSceneTest*'` plays it and writes
`build/reports/frontage-scene/<label>.json`: the force, the derived frontage,
per-squad rows naming which layer each garrison held, crowding measurements,
the ticks at which the run first stood to / manned a post / was breached, and a
compact per-sample timeline. The report is deterministic for a given seed and
configuration, so two runs of an unchanged scene produce identical bytes and a
diff is a real change. It records measurements only — the verdicts live in the
test, because a report that decided what "good" meant would let a threshold
drift without anything failing.

Snapshot generation is tool/test infrastructure and must not enter the shipped
mod jar. Keep reusable catalog and runner code in `:layer-authoring`, keep
mod-specific providers in the root test source set, register providers through
`META-INF/services`, and keep renderers deterministic and independent of a
Starsector process or OpenGL context.

`gradlew.bat verifyModJarBoundary` enforces that, and `check` depends on it.
It computes the forbidden set — everything on the tool runtime classpath that is
not also on the shipped one, which is the workbench, the MCP host, JUnit and the
game's own jars — and fails if any of it is in `StarsectorMarines.jar`. Nothing
to keep in step: a new tool dependency is covered the moment it is added.

The `layerAuthoring` workbench's **Snapshots** tab invokes the same catalog in
process. It renders off the Swing event thread, confirms before replacing PNGs,
and refuses to create `layers` evidence while the authoring document has unsaved
changes. It can render all suites or one selected suite and, like the command,
does not remove obsolete PNGs from earlier runs.

The workbench discovers top-level authoring pages through `AuthoringPageProvider`
services on the tool runtime classpath. Keep the generic host and lifecycle in
`:layer-authoring`; keep mod-domain pages such as Turrets in root tool/test
sources so the shipped mod jar and the generic tool module do not acquire each
other's domain dependencies.

## Mod layout

The `mod/` folder in this repo is what ships. Pre-pack art inputs — raw
generated sheets, ImageGen masters, retained `sources/` originals, tileset
authoring documents, and the scripts
that derive shipped art from them — live under `art-source/` instead, because
`deployMod` is a `Sync` of the whole `mod/` folder and would otherwise copy them
into every install. `RawArtStaysOutOfModTest` enforces that boundary; see
`art-source/README.md`. `mod_info.json` lists the jar at
`jars/StarsectorMarines.jar`. The `modPlugin` entry point is
`com.dillon.starsectormarines.StarsectorMarinesModPlugin`.

## Starsector API conventions

- Compile-only deps (never bundle into the jar): `starfarer.api.jar`, `starfarer_obf.jar`,
  `lwjgl.jar`, `lwjgl_util.jar`, `json.jar`, `log4j-1.2.9.jar`, `xstream-1.4.10.jar`,
  `fs.common_obf.jar` — all live in `<starsectorDir>/starsector-core/`.
- API sources are in `<starsectorDir>/starsector-core/starfarer.api.zip` — unzip locally
  for IDE attachment, do not check in.
- Logging: `Global.getLogger(Class)` returns a log4j 1.2 `Logger`. Game logs to
  `<starsectorDir>/starsector-core/starsector.log`.
- The `BaseModPlugin` lifecycle: `onApplicationLoad` (once at game start, before any save),
  `onNewGame`/`onNewGameAfterEconomyLoad`/`onNewGameAfterTimePass`, `onGameLoad(newGame)`
  (every load), `beforeGameSave`/`afterGameSave`.
- Faction definitions: `mod/data/world/factions/<id>.faction` (JSON despite the extension).
- Hulls/variants: `mod/data/hulls/`, `mod/data/variants/` mirroring vanilla.
- Strings (for i18n): `mod/data/strings/strings.json`.

## Doc-driven development

`roadmap/` records the enduring model and the implementation work that has not
shipped yet. It is not an archive of implementation history. Code, tests, Git,
and focused Javadoc are the authority for how shipped behavior is implemented.

### Noun docs are the canonical feature model

Each coherent feature/domain has one canonical noun doc, normally
`roadmap/<feature>/design/<feature>-nouns.md`. A narrow child feature that does
not own a separate model cites its parent's noun doc instead of restating it.

The noun doc is the expanding, high-level explanation of how the feature fits
together. It owns:

- the domain vocabulary and the distinction between easily-confused concepts;
- relationships, ownership, authority boundaries, and end-to-end flow;
- standing behavior, invariants, and design laws that future work must preserve;
- boundaries with adjacent features and the durable extension points.

New concepts must fit or amend the canonical vocabulary rather than create a
parallel noun in a story or implementation. Read the applicable noun doc in
full before planning, implementing, reviewing, or changing a story in that
domain.

Keep implementation detail out of noun docs: no per-class inventories, field
layouts, method contracts, file-by-file code maps, chunk logs, dated progress
notes, or commit narratives. Those details belong in code, tests, Javadoc, and
Git. A noun doc may cite a stable code symbol when that helps a reader find the
implementation, but it explains the system rather than duplicating it.

### Roadmap structure

Feature directories under `roadmap/` converge on this layout:

```
roadmap/<feature>/
  design/
    <feature>-nouns.md  — canonical vocabulary, relationships, laws, and flow
    stories.md          — concise board of open work only
    shipped.md          — one-line ledger of folded and deleted stories
    *.md                — enduring rationale or direction that does not fit the noun doc
  stories/              — one temporary doc per active/planned implementation unit
  assets/               — optional design or acceptance evidence
```

The two documentation buckets are distinguished by kind and lifecycle:

| Bucket | Holds | Lifecycle |
|--------|-------|-----------|
| `design/` | Noun docs, enduring architecture/rationale, direction docs, the open-work board, and the shipped ledger. | Living reference; not a unit of work and never moved merely because an epic ships. |
| `stories/` | A discrete, shippable implementation unit with scope, constraints, acceptance, and a plan. | Exists only while the work is planned or in progress; folded and deleted when it ships. |

Create broad system direction in `design/`. Create a concrete task that is
about to be implemented in `stories/`. A feature with more than a couple of
open stories keeps `design/stories.md` as the single cold-start board: open
stories and their one-line state only. Shipped rows leave the board.

`next-session.md` is not part of the target structure. The board says what is
available, the active story says what remains inside its scope, and Git records
the history. Do not create new handoff journals or append session changelogs to
living design docs.

### Retiring a shipped story (there is no live `complete/` bucket)

A shipped story is folded into the canonical docs and deleted, never archived
in place. Sort its content by what remains useful:

| Story content | Destination |
|---------------|-------------|
| Standing feature semantics: vocabulary, relationships, authority, behavior, invariants, and boundaries | Integrate into the existing sections of the feature's noun doc. Do not append a story-history section. |
| Enduring rationale: alternatives considered, rejected directions, capacity bounds, or a decision whose reason is not evident from the resulting model | Integrate into the appropriate `design/` doc, creating one only when the rationale has real continuing value. |
| Implementation detail and work log: class/field mechanics, chunk tables, progress notes, commit chains, landed-vs-planned narration | Do not copy into roadmap docs. Keep the implementation legible in code/tests/Javadoc and let Git retain the work history. |

Before deleting the story, run `git grep -F '<slug>.md'` and redirect every
citation to the noun/design section that absorbed its standing content. Then,
in the same commit:

1. fold the durable content;
2. remove the story from `design/stories.md`;
3. add one row to `design/shipped.md` with slug, ship date, commit ref(s), and
   fold destination;
4. delete the story doc.

`design/shipped.md` is a ledger, not a narrative: one row per shipped story,
never a section-sized retrospective. Git can recover the deleted source when
needed. Existing `complete/` folders are legacy migration input, not a live
destination; never add a newly shipped story to one.

### Document status and freshness

- Every story starts with `Status:` and `Written: YYYY-MM-DD`. Add or replace a
  single `Updated: YYYY-MM-DD — <brief freshness note>` line on material
  revision; never accumulate update history in the doc. A story never rests at
  `COMPLETE`—shipping means fold, ledger, and delete.
- Every enduring design doc starts with a `Status:` describing the direction,
  not an implementation task: `ACTIVE`, `SHIPPED`, `SUPERSEDED by <slug>`, or
  `DRAFT`. It remains under `design/` when shipped. Use the same `Written:` and
  single-line `Updated:` convention.
- Keep `roadmap/README.md` current focus and immediate next-up honest. It is the
  project-level orientation, not a second feature board or a shipped-work log.
- **Update docs at commit boundaries.** A change that alters the standing model
  updates its noun doc in the same commit. Do not accumulate documentation debt
  across story commits.

### Cross-references: bare slugs, not relative paths

- **Reference another roadmap doc by bare slug in backticks** — `` `central-keep.md` ``,
  not `[central-keep.md](../central-keep.md)`. A slug survives the
  bucket reorganizations a live doc may undergo, and
  `git grep -F '<slug>.md'` finds every citation in one sweep before a story
  is folded and deleted. Look docs up by name (fuzzy file-open), not by path.
  Filenames are stable slugs — status lives in the bucket and the doc's own
  status line, never in the filename. A bare slug does not excuse a dangling
  citation: redirect it when its story is retired.
- **Reference code by backticked symbol, not a path link** —
  `` `TacticalScoring.hasReachableFiringSpot` ``, not a `../../src/main/...`
  link. Package moves are routine here; the symbol name is what stays true
  and what you would actually search for.
- **Reference project memory as `[[slug]]`** — e.g. `[[battle_services_systems]]`.
  The memory directory lives outside the repo, so a relative link can never
  resolve.
- **When you fold and delete a doc, redirect its citations in the same commit.**
  `git grep -F '<slug>.md'` across `roadmap/` *and* `src/` — package-info
  charters and javadoc cite roadmap docs too. A fold that leaves dangling
  citations has traded stale docs for broken ones.

(Convention adopted 2026-08-22 from the sibling MoonLightEngine project,
after a link sweep found 41 rotted references — nearly all of them paths to
docs that had merely moved between roadmap buckets.)

## Conventions for this repo

- Package root: `com.dillon.starsectormarines`.
- Mod ID: `starsector_marines` (snake_case is the Starsector convention).
- Version in `mod_info.json` and `build.gradle` should match.
- Do not edit anything under `C:\Program Files (x86)\Fractal Softworks\Starsector` — it's
  read-only reference. Vanilla files there are the canonical examples for data schemas.

## Code style

- **NEVER write an inline fully-qualified name. Use `import` + simple name —
  always, in every line you write or edit.** (`Vehicle v`, not
  `com.dillon.starsectormarines.battle.vehicle.Vehicle v`.) The ONLY exception
  is a Javadoc `{@link}`, where an FQN is fine and needs no import. This is
  unconditional for new/edited code: **do not "follow context clues."** If the
  file you're editing is full of inline FQNs, you still add an import and use the
  simple name for your additions — match the project style, never the file's bad
  habit. (Two carve-outs that are about *not touching other code*, not about
  writing FQN: don't do sweeping FQN→import refactors of existing files unasked,
  and a mechanical package-move rewrite that merely preserves a file's existing
  FQNs is fine.)

## Committing

Linked worktrees give each session its own working tree and index, but commits
should still stay narrowly scoped:

1. Stage explicit paths only — `git add <path> …`, never `git add -A`/`.`.
2. Review `git status` and the staged diff before committing.
3. Commit only the current task's files. Leave unrelated files and other
   sessions' work alone.
4. Never `git stash`; it complicates ownership and recovery across worktrees.

### Commit-command mechanics (these have bitten before)

- **`-m` goes BEFORE `--`.** `git commit -m "msg" -- <path> …`. Anything after
  `--` is a pathspec, so `git commit -- <path> -m "msg"` makes git treat `-m`
  and the message as filenames (`pathspec '-m' did not match any file(s)`).
- **Match the shell to the tool.** The here-string for multi-line messages
  (`-m @'…'@`) is **PowerShell only** — use it in the PowerShell tool. The
  **Bash** tool reads `@'…'@` literally and mangles the commit. In the Bash
  tool, pass the message as a normal double-quoted string (`-m "line1
  line2"`); avoid backticks and `$` in it, or single-quote. Pick one tool per
  commit and quote for that shell.
- A failed-pathspec error means **nothing was committed** — fix the flag order
  / quoting and re-run (the `git add` already staged the files; don't re-add).

## Workspace location

Sessions normally start in the main workspace at
`C:/Users/Dillon/IdeaProjects/starsectormarines`. Read-only tasks may remain
there. For tasks that change files, follow the session worktree workflow above
and run all task commands from `C:/Users/Dillon/IdeaProjects/starsectormarines/.claude/worktrees/<unique-name>`.

## Multi-project layout

- `:` (root) — the mod itself. `src/main/java` holds `StarsectorMarinesModPlugin`, the
  bridge intel plugin, the scene-graph renderer.
- `:asset-pipeline` — vendored copy of MoonLight Engine's asset code. Two source sets:
    - `main` (runtime) — MeshData, LoadedModel, MaterialInfo, Animation, Skeleton, Bone,
      BvhParser, AnimationRetargeter, ModelSerializer. **Bundled into the mod jar via
      fat-jar.** Depends only on JOML at runtime. No Lombok, no Log4j 2, no LWJGL 3.
    - `tool` (build-time importer) — ModelLoader, MeshExtractor, MaterialExtractor,
      AnimationExtractor, BoneRemapConfig, ProcessModelsTask, ConventionNormalizer,
      AssetConventionConfig. Uses Assimp + LWJGL 3 + Log4j 2. **Never ships.**
      Invoke via `gradlew :asset-pipeline:processModels`.
- The mod's `jar` task pulls in `:asset-pipeline:main` outputs + JOML via the
  runtime classpath, producing a single fat jar at `mod/jars/StarsectorMarines.jar`.
