# Project: Starsector Marines

A Starsector mod (game version 0.98a-RC8). The game install at
`C:\Program Files (x86)\Fractal Softworks\Starsector` is a read-only reference;
never edit it. Its vanilla files are the canonical examples for data schemas.

For vision, current focus and next-up, read `roadmap/README.md` first. Within a
feature, start with its noun doc (`design/<feature>-nouns.md`) and
`design/stories.md`. This file holds the rules every session needs; the
documents it points to hold the detail, so read one when its task comes up.

## Agent model selection

Subagents and sub-sessions run on **Opus**, **Sonnet** and **Sol 6.1**. Set the
`model` argument explicitly for each job rather than inheriting it:

- **Opus** for design-heavy work: modelling and art direction, architecture,
  tricky debugging, review that needs judgement.
- **Sonnet** for mechanical or read-only work: reconnaissance, searches, sweeps,
  bulk edits, running a known command and reporting.
- **Sol 6.1** for reconnaissance and implementation when the tool offers it;
  otherwise use Opus or Sonnet by the rules above.

The user may name a different model, and that wins. **Codex CLI is for image
generation only**, through the `codex-imagegen` skill. Never use `codex exec`
or any other Codex call for work a Claude agent can do.

## Session worktrees (default workflow)

The main workspace stays on `main`, clean, and is used only for integration and
worktree administration: no builds, no task edits, no generated files. Every
task that may change repository files runs in a worktree unless the user says
otherwise; read-only investigation does not need one.

1. Check `git status` and `git worktree list`. If the session is already in a
   linked worktree under `.claude/worktrees/`, use it; never nest one.
2. Otherwise, from the main workspace (after `git fetch origin` and
   fast-forwarding `main` to `origin/main`), cut one:
   `git worktree add -b session/<unique-name> .claude/worktrees/<unique-name> main`,
   and do all edits, builds, tests and commits there.
3. Stage only your own paths. Never `git stash`, and never touch another
   session's branch or worktree.
4. Before integrating: `git fetch origin`, `git merge main`,
   `git merge origin/main` in the worktree, resolve conflicts there, and re-run
   the relevant verification: the tests your change touches, never the full
   suite (see Tests).
5. Integrate with `powershell -File tools/integrate.ps1 <branch>`
   (`tools/integrate.sh` on the Mac). It checks the main workspace, fast-forwards
   `main`, pushes `origin` and then `hub`, and stops at the first failure. If it
   fails because `main` moved, merge again and rerun it. **Never force-push**,
   never push any branch but `main`, and if the main workspace has uncommitted
   changes, leave them and report the blocker.
6. Once the commit is reachable from `main`, remove the worktree and delete the
   merged branch.

**Standing authorization:** when the user has authorized repository changes,
complete the whole flow above, including the merge and the pushes, without
asking "merge it?". Integrate at every green, self-contained commit chain, not
once at the end. This does not turn a diagnosis, review or status request into
permission to edit, and does not authorize force-pushes, other branches,
deploying the mod, publishing artifacts or discarding unrelated work.

**GitHub (`origin`) is the source of truth.** `hub` is a LAN mirror on the Mac
that only ever receives what `origin` accepted; never integrate against it.
Branches cut before the 2026-10-18 history rewrite must never be merged. Remotes,
the manual integration fallback and rewrite recovery: `git-workflow.md`.

**Never commit rendered output.** Renders, review sheets, overlays, frame
sequences and graded textures are regenerated, not tracked; they live under
ignored paths (`build/`, `art-source/company-prologue/.gitignore`). Tracked:
scripts, data, ImageGen generations with their prompts and guides, finish
references, hand-made and story art. Never force-add an ignored path.

## Showing the owner work

- **Every preview is 1920x1080 at 100% UI scale.** Other sizes may be checked
  internally but never shown; resizing does not count.
- **Show the pictures themselves**, in the chat (`SendUserFile`,
  `display: "render"`) or on the parent's picks page, never just a Drive path.
  A sub-session hands its parent the image paths.
- **Owner-facing images come from the look preset** (`lookShots()`), never from
  a verdict scene's arena frames.
- **Every decision goes on the top-level parent's picks page** as picture cards,
  never only in chat. Sub-sessions send the question up to their parent rather
  than asking. Build on the recommended option meanwhile.
- Large files for the other machine go to Google Drive (`G:\My Drive\starsector`),
  never into git; review sets go under `review/<asset>/<date>_<what>_<machine>/`,
  one writer per run.

The full procedure (Drive layout, pruning, previews, the picks page and its
`options.json`): `owner-review.md`.

## Build and run

- **Never run `gradlew --stop`, and never make it a retry step.** It stops every
  daemon on the machine, failing other sessions' builds and killing any game a
  `runStarsector` session launched. A build that seems stuck is nearly always
  another session holding a lock: wait, or run only the task you need.
- Set `JAVA_HOME` explicitly before Gradle in PowerShell; never guess a JDK or
  use Starsector's bundled runtime:
  `$env:JAVA_HOME = 'C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.2\jbr'`.
- The toolchain is JDK 25; bytecode targets **Java 17** (`--release 17`). The
  game runs Zulu 17 with `--enable-preview`: no language features newer than 17,
  no preview features at compile time.
- `gradlew.bat shadowJar` writes `mod/jars/StarsectorMarines.jar` directly.
  `build` and `check` also run the whole test suite and are expensive, so
  sessions do not use them (see Tests).
- `gradlew.bat deployMod` syncs `mod/` into the game (needs `ffmpeg` for sounds);
  `gradlew.bat runStarsector` deploys and launches it, recording the exit status
  and JVM output under `build/starsector-run/`; `gradlew.bat packageRelease`
  builds the public zip. Details, crash triage and flags: `build-and-run.md`.
- **Load every mod texture through `ModTextures`**, never
  `Global.getSettings().getSprite` or `openStream`: shipped textures are fitted
  to their drawn size and only `ModTextures` maps them back (`texture-fit.md`).
- Re-run `gradlew.bat :asset-pipeline:deriveTileMaps` after re-exporting a
  tileset with height/normal companions.
- Blender renders go through the render queue (`render-queue` skill), never a
  session's own shell.

## Evidence and visual tasks

Anything that plays a battle, generates a world, renders a frame or measures
cost is an opt-in Gradle task, excluded from `test`. Pick the task, then read
its doc for flags and outputs.

| Task | What it answers | Detail |
|------|-----------------|--------|
| `commanderEvidence -Pmission=<id>` | A mission's fixture matrix played headless, with traces and summaries (Conquest takes ~17 min: slow is not stuck) | `evidence-tasks.md`, `commander-evidence-missions.md` |
| `simDeterminism` | Does the same fixture play the same twice, serially and side by side | `evidence-tasks.md` |
| `battleProbe` | Step one battle and query it between steps, instead of bisecting | `evidence-tasks.md` |
| `sceneEvidence` | Behaviour scene verdicts, PASS/FAIL, no rendering | `evidence-tasks.md`, `behavior-scenes.md` |
| `garrisonEvidence`, `generatedSpaceEvidence`, `storyMapPools` | Map and setup assertions that need a generated world; run `storyMapPools` after any mapgen change that may move a story map | `evidence-tasks.md` |
| `loadoutCardSheet`, `loadoutMatchup`, `armorMatchup` | Loadout stats, and infantry or machine fights against a control | `evidence-tasks.md` |
| `bloomEvidence`, `crewEvidence` | Campaign Rav Bloom over years; ship crews' activity | `evidence-tasks.md` |
| `profileConquestTail`, `profileConvoyRoute`, `profileSwarmFlow` | Simulation cost profiles | `evidence-tasks.md`, `conquest-tail-profile.md` |
| `shaderEvidence` | Shaders compile and link on a real driver; GL state brackets survive hostile state | `render-evidence.md` |
| `renderEvidence` | Frame cost per layer on real GL; `-PrenderMaps=stress` is the ~45 s fast path; Nsight capture | `render-evidence.md` |
| `createSnapshots` | Every deterministic visual-evidence suite (`-Psnapshot=<id>`) | `snapshot-suites.md`, `gpu-review-drain.md` |
| `layerAuthoring`, `tools/authoring.sh` | The authoring workbench, and its tools headless (always through `tools/`, never `build/authoring/`) | `layer-authoring-workbench.md`, `authoring-tools` skill |

- **`createSnapshots` is the only visual-evidence task.** A new kind of picture
  is a `SnapshotSuite` registered through `META-INF/services`, never a new Gradle
  task. Snapshot and tool code never enters the shipped jar;
  `verifyModJarBoundary` (part of `check`; run it directly when a change
  touches snapshot or tool code) enforces it.
- **Control runs, not old commits.** `-Dbattle.*` switches are forwarded into
  every Test fork and `createSnapshots`, so a control is the same command with
  one switch off. A commit-to-commit comparison measures every other change too.
- **A switch added for an A/B is temporary.** Its property constant's Javadoc
  states the removal condition, it is listed in `switches.md`
  (`SwitchRegistryTest` fails on an unlisted `battle.*` or `marines.*` read in
  `src/main`), and the session that added it removes it with the losing path
  once the comparison is decided. A permanent lever states its reason there.
- **Balance missions against a player, not the autopilot** (owner). In a
  played battle the player's squads take no objective from mission command;
  only allies and the opposition are commanded. Evidence runs still let the
  Marine commander play the company, so their win rates and durations show a
  mission works, not that it is hard enough: playtesting found campaign
  missions too easy that way. Tune for a player giving orders
  (`mission-command-nouns.md`, Player and faction extensions).
- **Raise a heap only after finding the retainer**; a battle's live set is well
  under 1 GB (`evidence-tasks.md`, Memory).
- Never run two of the same Test-based task in one worktree at once.

## Tests

**A unit test tests a unit**: one class or function, on the smallest input that
shows the mechanism is right. A test that stands up a world generator, a battle
or a renderer is not a unit test and does not belong in `test`.

- **Every new test completes in under one second.** Measure it in the JUnit XML
  under `build/test-results/test/`. If it misses, move the question down to its
  unit or move the test out of `test`; never raise the bar.
- **Test less, and only meaningful core behaviour** (owner). Test the rules the
  code owns, not the content it ships: no assertions on what an image, data file
  or tuned number currently contains. Delete such a test when it fails for that
  reason rather than updating it.
- **Minimize end-to-end tests** (owner). Prove a rule where it is decided, and
  structure code so it can be; measure a change whose failure mode is a logged
  fallback rather than gating it on a played battle.
- **Run only the tests your change touches** (owner): `gradlew.bat test --tests
  <Class>` for the classes covering the code you edited. Never run `check`,
  `build` or the whole `test` task, not even before integrating; the full suite
  is expensive and is not a session's job. Say in the commit or report which
  classes you ran.
- Not tests, so not in `test`: mission and battle runs (they belong in an
  evidence task), and rendering or pixel comparison (a `SnapshotSuite`, or
  `shaderEvidence` / `renderEvidence`). Existing slow tests are not a pattern
  to copy: when you touch one, move it out or cut it down.
- Tests read data off disk, so a checked-in file a test opens must be declared
  with `inputs.dir` in `build.gradle`'s `test {}` block, or `:test` reports
  UP-TO-DATE unverified. Never declare a build output (`mod/jars`, `mod/sounds`)
  that way.

## Mod layout and Starsector API

- `mod/` is exactly what ships (`deployMod` syncs all of it). Raw art inputs,
  ImageGen masters, tileset authoring documents and the scripts deriving shipped
  art live under `art-source/` (`RawArtStaysOutOfModTest`, `art-source/README.md`).
  New modular terrain and cover art follows `art-source/geometry-first-imagegen.md`.
- Mod ID `starsector_marines`; package root `com.dillon.starsectormarines`;
  `modPlugin` is `com.dillon.starsectormarines.StarsectorMarinesModPlugin`;
  `mod_info.json` lists `jars/StarsectorMarines.jar`. Its version must match
  `build.gradle`'s.
- Compile-only, never bundled: `starfarer.api.jar`, `starfarer_obf.jar`,
  `lwjgl.jar`, `lwjgl_util.jar`, `json.jar`, `log4j-1.2.9.jar`,
  `xstream-1.4.10.jar`, `fs.common_obf.jar`, all in
  `<starsectorDir>/starsector-core/`. API sources are in `starfarer.api.zip`
  there (unzip locally, never check in).
- Logging: `Global.getLogger(Class)` (log4j 1.2), to
  `starsector-core/starsector.log`.
- `BaseModPlugin` lifecycle: `onApplicationLoad` once before any save;
  `onNewGame` / `onNewGameAfterEconomyLoad` / `onNewGameAfterTimePass`;
  `onGameLoad(newGame)` on every load; `beforeGameSave` / `afterGameSave`.
- Data: factions in `mod/data/world/factions/<id>.faction` (JSON), hulls and
  variants in `mod/data/hulls/` and `mod/data/variants/`, strings in
  `mod/data/strings/strings.json`.
- Subprojects: `:asset-pipeline` has a `main` runtime set bundled into the jar
  (JOML only) and a `tool` set (Assimp, LWJGL 3, Log4j 2) that never ships
  (`gradlew :asset-pipeline:processModels` runs the importer);
  `:layer-authoring` is the generic authoring and snapshot host.

## Roadmap docs

`roadmap/` holds the enduring model and unshipped work, never implementation
history; code, tests, Javadoc and Git are the authority for how shipped
behaviour works.

- Each feature has one canonical noun doc. Read it in full before planning,
  implementing or reviewing in that domain, and fit new concepts into its
  vocabulary rather than inventing parallel nouns.
- A change that alters the standing model updates its noun doc in the same
  commit.
- A story lives in `stories/` only while planned or in progress. When it ships,
  fold its lasting content into the noun or design docs, add one row to
  `design/shipped.md`, redirect its citations, and delete it, all in one commit.
- Cite docs by bare slug in backticks (`` `central-keep.md` ``), code by
  backticked symbol, memory as `[[slug]]`; never by relative path.
- No session logs, handoff journals or dated progress notes in living docs.

Layout, status lines and the retirement procedure: `roadmap-docs.md`.

## Code style

- **Never write an inline fully-qualified name.** Import and use the simple
  name, in every line you write or edit, even in a file full of FQNs. The only
  exception is a Javadoc `{@link}`. Do not sweep a file's existing FQNs unasked.

## Committing

- Stage explicit paths (`git add <path> …`), never `-A` or `.`; review
  `git status` and the staged diff; commit only this task's files.
- **`-m` goes before `--`**: `git commit -m "msg" -- <path> …`. A
  failed-pathspec error means nothing was committed; fix the order and re-run.
- Quote for the shell you are in: the `@'…'@` here-string is PowerShell only.
  In Bash pass a normal double-quoted message, avoiding backticks and `$`.
