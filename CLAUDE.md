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
- Tests read data off disk rather than off the classpath, so a checked-in file a
  test opens at runtime must be declared with `inputs.dir` in `build.gradle`'s
  `test {}` block, or `:test` reports UP-TO-DATE with the change unverified.
  Never declare a build output (`mod/jars`, `mod/sounds`) that way — a suite that
  re-runs on every build is a suite people start skipping.
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
- `gradlew.bat crewEvidence` → crews a transport and a capital from their own
  room programs, runs each for four minutes of ship's time, and reports what the
  complement actually spent it doing: the idle share, the activity histogram, the
  longest unbroken stretch of doing nothing, how many are still alive, and which
  compartment purposes hold anybody. Opt-in and excluded from `test` — it costs
  minutes of wall clock and needs two whole hulls to answer its question. The
  standing goal is an idle share of zero, with the resting, socialising and
  exercising columns carrying the crew's off-watch time instead; see
  `CrewLivelinessEvidence` for why the dead count as idle.
- `gradlew.bat createSnapshots` → every deterministic visual-evidence suite under
  `build/snapshots/` without launching Starsector or creating an OpenGL context. Select
  suites with `-Psnapshot=airfield-sortie,armory,deployable-cover,durability-bars,frontage-scene,integral-system-fx,layers,perception-sweep,point-defence,ship-decks,turrets,ui`
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
  New sheets are ingested through the `ingest-tileset` skill: the
  `tileset_measure` tool measures a sheet and drafts its authoring seed, and
  `ProjectTilesetSeedsTest` fails the build for raw art that arrives without
  one.
  The Tilesets page opens on a question rather than on a workspace: **what are
  you doing?** Three ways in, each a walkthrough of numbered screens with Back
  and Next, and each screen holding only the controls its own step needs. The
  page used to present every command at once on one toolbar — seventeen controls
  in the order they were written, with the cut, the annotation, the grouping and
  the export interleaved — which is a palette for somebody who already knows the
  procedure and nothing at all for somebody who does not.
  **What can be a wall?** is about the surface, not about sheets. Screen one is
  a grid of every `GroundKind` and `SurfaceRole` the generator can ask for, each
  showing a 100px picture of whatever is drawn for it today, grouped
  **Structure** then **Outdoors** and within each **Walls / Floors / Other**.
  That grouping is a browsing aid owned by the tool, not vocabulary the game
  consults — nothing resolves differently because a surface is filed under
  Outdoors — and it is kept off `GroundKind` and `SurfaceRole` for exactly that
  reason. Every surface must be filed explicitly; `SurfaceCategoryTest` fails
  the build for one that is not, because where a surface belongs is a judgement
  and there is nothing to derive it from. A preview paints the block's
  `fillRgb` behind its cells, since that fill is what the renderer draws for the
  case a hollow layout leaves empty — most of a courtyard is the fill, and shown
  on a transparency checker it reads as an earthwork rather than paving. Screen two is **the set**: every block in the project that could fill
  that surface, whichever sheet it is on, as a grid of pictures — because
  `urban.wall` and `road.embankment` are both walls and are nothing alike to
  look at. The one in use comes up selected and is drawn large beside the grid
  **as a room**, three cells on a side, which is the only view that shows a
  mirrored wall.
  Four things can be done to the set. **Draw this one** points the surface at
  the chosen block by editing the mapping in place — a surgical replacement of
  one value, validated against the real catalog before it lands, because
  re-serialising that file would reorder every key in it. **Add one from a
  sheet…** hands off to the ingest walkthrough, which is the only place sheets
  appear in this flow. **Remove from the set** dissolves the block, giving
  its pieces back as doodads; it is refused while the mapping still points
  there, since a surface with no block is a startup crash. Next opens the
  block's sheet on a third screen, **Adjust the cut**.
  That screen moves the selection's rectangle and nothing else. Slicing keys on
  alpha and splitting divides by a stated pitch, and both are right most of the
  time and wrong for a particular piece — a prop whose contact shadow was keyed
  away with it, a cell whose seam sits a pixel off the line through its
  neighbours — so re-slicing to fix one of them moves every other piece too.
  **The selection moves as one grid**, because that is the shape the fault
  usually has: a wall block's nine cells are one plate cut on one grid, and
  correcting them a cell at a time is nine edits that have to agree and will
  not. So the controls are a `GridCut`'s — where the first line falls, and how
  big one cell is — and one piece is simply the 1x1 case. `GridPatch` derives
  that grid from where the pieces actually sit rather than from their slot
  names, since a selection may be a whole plate or a row of four; edges within
  a quarter of a cell of each other are one grid line, so a cell already nudged
  by a pixel stays in its column, and which lattice index each line holds is
  recovered from the spacing, so a selection that skips a column still lands on
  the right addresses. **A patch is a lattice, not a rectangle**: the selection
  need not fill the grid it lies on. `floors.brick` is five cells in a plus —
  one, then three, then one, on the sheet's own 47.55px pitch — which is an
  ordinary variant pool cut on one grid, and requiring a filled rectangle
  refused it for a reason that had nothing to do with the art. What is refused
  is pieces no single origin and pitch describes, and the reason is written
  where the picture would have been, because it is a paragraph and the caption
  is a label in a split pane. The cut is drawn over the plate magnified, with
  the seams between cells drawn and the excluded pixels dimmed, because a
  boundary one pixel out is invisible at 1:1 and a pitch a fraction out shows
  up against the interior seams rather than the boundary. Adopting a grid says
  how far it would move the cells that are already there, and applies to all of
  them or none. Saving applies the move, writes the document and re-exports,
  since a cut is only fixed once the atlas is packed from it — and a re-export
  drops the held atlas and thumbnails, because the picture a preview is holding
  came from the sheet that was just replaced. `tileset_set_cut` is the
  single-piece operation headless, previewing by default and refusing a
  rectangle that leaves the sheet. The screen is also the fourth of the ingest
  walkthrough.
  **New art arrived** is the ingest sequence: pick a sheet, find the pieces,
  say what each piece is, group blocks, name and size the output, save and
  export. A screen will not advance until it has been answered, and says what it
  wants rather than grepping out a disabled button — "Slice or split the sheet
  so it has pieces to annotate".
  **Just look at it** opens a sheet and shows the tileset as the game loads it
  and a generated map drawn with it. Nothing on those screens writes.
  The sheet list itself is found rather than browsed for: every sheet under
  `art-source/tilesets/` with its state — raw, seeded, annotated, exported.
  Dropping a raw sheet there is enough to make it appear; a hand-written document
  carrying settings but no pieces is a valid seed and is sliced on open.
  The page finds pieces by keying on alpha and proposes a footprint for each from
  the sheet's stated `gridCols` x `gridRows` layout — cells need not be square,
  and a fused plate is cut into exactly that grid — but footprints are edited
  there rather than inferred, because
  how much deck a piece covers is a judgement about the object, not a measurement
  of the art.
  **Fit grid to art** measures where that stated grid actually sits — generated
  art sits inside a margin and is rarely drawn to a pitch that divides its own
  pixel size evenly — and reports how many boundaries landed on a real seam and
  how far they lie from the line through them. It applies only the axes that
  measured well, and its dialog's fields are editable so the measurement can be
  overridden. The cell *count* is never measured; only the placement is.
  A fitted cut moves the plate's existing cells onto new rectangles, keeping
  every id, block slot and annotation.
  Pieces are picked on the sheet itself — click, ctrl-click to add,
  shift-click to run, drag a box — and the table follows, because a cut cell's id
  cannot be recognised in a list of a hundred. Each cell carries its `col,row` on
  the picture, which is what lets a person and a model name the same cell.
  **Copy selection for LLM** writes a labelled contact sheet of the picked cells
  under `build/tileset-authoring/` and puts a table of their current annotation,
  keyed by the same coordinates, on the clipboard with that image's path.
  A piece becomes a doodad or a cell of a named block; walls
  and corners are authored by grouping pieces into an autotile block's slots,
  which the packer places as one contiguous patch. A block declared with layout
  `variants` is instead a pool of interchangeable ground tiles picked by hashing
  the cell — slots `v1`, `v2`, ... — written as an explicit cell list and packed
  as a run, which is the shape `water.water` and the `floors.*` families load in.
  A sheet whose document carries a
  `strip` block exports as a sliced auto-strip instead: frames in a row at an
  authored scale, addressed by frame index rather than `(col, row)`, which is
  the shape `urban-tileset-3` and `nature-tiles` load in. A frame may declare a
  `material` — its picture comes from a tileable file rather than from the plate
  — or a `spriteBorderPx`, which mirrors away the drawn rim that would otherwise
  tile as a lattice; both work on either shape, and a piece claiming neither is
  packed as it was cut. Export writes a packed
  atlas holding only
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
  tools headlessly — list what can be a wall, list/measure/read/write/slice/fit/split/export a tileset, move one piece's cut,
  declare
  or dissolve one of its autotile blocks, render its map-preview comparison, run the snapshot catalog — with no workbench window
  and nothing to start first. `--list` names the tools, `--describe <tool>`
  prints its schema, `--json` returns the structured result. Arguments are one
  JSON object, inline or as `@file` or `-` for stdin; from PowerShell quote the
  `@file` or use `-`, because a bare `@token` is its splatting operator. Exit
  status is 1 when the tool reports a failure and 2 on a usage mistake, and 3
  when the freshness build below failed, in which case nothing was called.
  See the `authoring-tools` skill.
- **Always go through `tools/`, never through `build/authoring/`.** The
  generated launchers run whatever was compiled last, and nothing in their
  output says how old that is. The checked-in wrappers exist to close that:
  each runs `installAuthoringTools` before handing over, so a call cannot use
  classes older than the working tree, and refuses to call anything at all if
  that build fails. It costs about 0.7s per call against a warm daemon (0.85s
  total, against 0.15s for the launcher alone), which is the right trade for
  tools that rewrite hand-authored documents — a stale call has already
  destroyed a hand-cut tileset once, silently, by running a safety guard's
  pre-guard classes. Build chatter goes to stderr; stdout stays the tool result.
- `gradlew.bat installAuthoringTools` → writes the generated launchers under
  `build/authoring/` and prints the `.mcp.json` snippet that registers the same
  tools as an MCP stdio server. The wrappers above run it for you on every call,
  so there is no longer anything to remember after a dependency change or a
  `clean`; run it by hand only to see that snippet. The launchers embed an
  absolute classpath and are therefore generated rather than checked in.
  Register `tools/authoring-mcp.cmd`, not the launcher it execs — the wrapper
  is what rebuilds first. Even so, an MCP server holds its classes for a whole
  session: **after changing tool code, restart the server or use
  `tools/authoring.sh`**, which rebuilds per call. Prefer the shell wrapper over
  MCP registration unless a session already has the server: an MCP stdio server
  must be registered before the session that wants it starts, which is exactly
  the constraint a one-shot command removes. Both entry points are separate
  front doors onto the same domain code, never an embedded server — an editor
  holding unsaved changes and a tool writing the same document would be two
  writers. See `authoring-entry-points.md`.
- `gradlew.bat :asset-pipeline:deriveTileMaps` → bakes `<sheet>_height.png` /
  `<sheet>_normal.png` beside each terrain albedo listed in
  `asset-pipeline/src/tool/resources/tilemaps/tilemaps.json`. **Re-run it after
  re-exporting any tileset that has those companions.** They are found by naming
  convention and sampled at the albedo's own frame coordinates, so a re-packed
  atlas leaves them reading the relief of the sheet they replaced — silently.
  `UrbanTileset3AlphaTest` fails when they drift out of step.
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
| `ui` | Marine Ops screens at authored viewport sizes, including the scale-invariant battle task-force plate | `build/snapshots/ui/` |
| `frontage-scene` | Animated garrison stand-to on a generated compound, one loop per approach edge | `build/snapshots/frontage-scene/` |
| `point-defence` | Animated LRM salvos against a placed interceptor pod: rounds stopped, rounds missed, rounds arriving | `build/snapshots/point-defence/` |
| `deployable-cover` | Animated controlled comparison of a placed revetment: the same fire into a covered lane, an open lane, and a screened post shot from the flank | `build/snapshots/deployable-cover/` |
| `integral-system-fx` | A running integral system's halo: a narrow authored screen beside a wide one draining their soak pools, one breaking under concentrated fire, and one pattern's screen at four facings | `build/snapshots/integral-system-fx/` |
| `perception-sweep` | The player's own picture — fog overlay and hidden-unit gating included — before, during, and after a Janus sensor sweep | `build/snapshots/perception-sweep/` |
| `airfield-sortie` | Three animated loops of one garrison airfield: a sortie's crew walking to the pad unopposed, the same walk under fire, and a fire team burning the based aircraft on their stands | `build/snapshots/airfield-sortie/` |

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

`AirfieldSortieScene` is the second: a garrison airfield, a shuttle on its
hardstand, and the crew that has to walk out to board it, recorded three ways —
unopposed, with a marine fire team on the walk, and with a fire team on the
apron burning the based aircraft where they stand. It exists because the
unit tests around embarkation each pin one link (the means sets the state, the
system takes a marine aboard, the gate rejects without a field) and none of them
can show the thing the change was for: that the crossing is a stretch of time
during which somebody can be shot.

**A scene's marines need a squad and a weapon, or they are scenery.** A unit
spawned from a bare `EntitySpec` carries no loadout and cannot fire, and target
acquisition runs off the squad, so an "ambush" of unarmed, unsquadded marines is
six people standing in a field watching a crew walk past. This scene recorded
exactly that for a while, and its under-fire loop reported a delivery that was
never actually contested. Seed a loadout and mint a squad, then check the
recording says what you think it says.

Prefer a scene over a mission harness whenever the question is about one
behavior rather than about a whole battle's balance, and add another scene
rather than widening this one past what its name claims.

**A scene needs both sides on the map even when only one of them acts.** The
simulation returns immediately once a side is absent, so a scene with a lone
faction never advances a tick — and one whose only enemies die mid-recording
freezes at that instant with the clock stopped. Every airfield loop keeps a
single distant marine nobody can reach for exactly this reason.

That is not always enough. A side present only as *structures* can still be a
decided battle, and a decided battle stops ticking everything, aircraft
included — the airfield raid loop, whose defenders are three parked hulls,
recorded six marines standing perfectly still for its whole length until the
scene called `setMissionCompletionEnabled(false)`. A scene is about one
behaviour and not about who wins; turn the terminal check off.

The scene is reached through its snapshot suite —
`gradlew.bat createSnapshots -Psnapshot=frontage-scene` — which plays it and
records the animated evidence. It carried a JUnit harness and a JSON report as
well; both were deleted on 2026-08-28 because playing the scene twelve times
cost 93s of a 560s `:test` run, and the owner judged the invariants not worth
that. A scene is still the right instrument for a question about one behavior;
reach for it from a snapshot suite or a scratch harness rather than from the
default suite.

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

## Tests

**A unit test tests a unit.** It exercises one class or one function
directly, on the smallest input that can show the mechanism is right. If it
has to stand up a world generator, a battle, or a renderer to ask its
question, it is not a unit test and does not belong in `test` — whatever it
is measuring, there is a unit underneath it that can be asked directly.

The failure mode is proving the *case* instead of the *core*. A fill-quality
question is about the fitting and the floor it fills, so it is asked of
those. Generating five hulls at six seeds to look at the result establishes
nothing the one fitting did not, fails for reasons belonging to the inputs
rather than the code, and costs ninety world generations on every run by
every concurrent session forever. The arithmetic hides: five times six times
three reads like one test.

A test runs in a second or two. That is a consequence rather than the rule —
a test aimed at one unit is small because the unit is.

Measurement that genuinely needs the whole space is **evidence, not a
test**, and belongs in an opt-in Gradle task excluded from `test`, the shape
`commanderEvidence` and `createSnapshots` already use.

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
