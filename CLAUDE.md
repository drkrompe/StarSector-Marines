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
- `gradlew.bat renderArmoryPreviews` → deterministic equipment-doll and sample-soldier PNGs
  in `build/headless-armory-previews/` without launching Starsector or creating an OpenGL
  context. Pass `-ParmoryPreviewDir=<path>` to redirect the output.
- `gradlew.bat renderUiPreviews` → authored retained-view PNGs in
  `build/headless-ui-previews/`, using the same document layout, styles, bitmap fonts,
  clipping, and canvas producers as the live UI. Pass `-PuiPreviewDir=<path>` to redirect.
- `gradlew.bat layerAuthoring` → standalone marine/mech layer workbench with drag,
  scale, rotation, frame playback, combined-sheet export, and validated atomic writes
  to `mod/data/appearance/unit-layer-layouts.appearance.json`.
- `gradlew.bat renderLayerAuthoringSheets` → headless combined sheets for every unit in
  that authoring document under `build/layer-authoring/`.
- `gradlew.bat deployMod` → generates the gitignored `mod/sounds/` outputs
  (requires `ffmpeg` on `PATH`) and syncs `mod/` into
  `<starsectorDir>/mods/StarsectorMarines/`.
- `gradlew.bat runStarsector` → deploys then launches via `starsector-core/starsector.bat`.

## Mod layout

The `mod/` folder in this repo is what ships. `mod_info.json` lists the jar at
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
