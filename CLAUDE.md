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

- **Never run `gradlew --stop`, and never make it a retry step.** It stops every
  daemon on the machine, not just yours. Concurrent sessions are the norm here,
  so a single `--stop` aborts whatever they are running: their `:test` runs fail
  with `Gradle build daemon has been stopped: stop command received`, and a
  `runStarsector` session loses the game itself, because the game is a child of
  the daemon and goes down with the build. That looks exactly like a silent game
  crash from the inside — exit status 0, no exception, no `hs_err`, no shutdown
  hook, no `run-summary.txt` — and one such "crash" cost a long investigation
  before the daemon log gave it away at the matching second. As a retry step it
  is self-feeding: the stop fails other sessions, whose retries stop more
  daemons. A build that seems stuck is nearly always another session holding a
  lock; wait, or run the one task you need. To confirm a stop after the fact,
  grep `~/.gradle/daemon/<version>/*.log` for `stop() called on daemon` and
  compare the timestamp. Routine `other compatible daemons were started ... idle
  for 0 minutes` entries are ordinary culling of idle daemons and are harmless.

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
  mission's documented construction-fixture matrix in a forced-serial,
  zero-input simulation and writes canonical traces plus `summary.json` /
  `summary.md` under `build/reports/commander/<mission>/`. Supported mission
  arguments are currently `conquest`, `sabotage`, `assault`, `raid`, and `extraction`; later mission harnesses
  extend that argument instead of creating another Gradle task. The run is
  opt-in and excluded from ordinary `test` / `build`.
  **Each fixture is replayed once.** It used to be replayed twice, with the two
  traces asserted byte-identical, which on Conquest meant four ~105s replays
  and 422s of wall clock — and the assert is a determinism check on the
  simulation rather than evidence about the battle, so it does not need to ride
  along on every balance run. `simDeterminism` below owns it. `-Prepeat=2`
  restores the double replay here when a particular balance run wants it.
  Replays run **side by side by default**, on half the machine's processors —
  `-Pparallelism=N` sets that explicitly and `-Pparallelism=1` is the serial
  control. It was serial until `simDeterminism` found two process-globals —
  `LosCache` and the map generator's own preview fields — and both are fixed;
  two replicas now produce byte-identical traces.
  **Conquest is the slow one, and slow is not stuck.** At 560x336 the matrix
  takes about 17 minutes of wall clock with the fixtures side by side (one
  replay each; the other four missions finish in under half a minute), because
  a 400-seat fixture on that map runs near 30 ticks/s before contact and slower
  after it. A run with no output for twenty minutes once looked like a frozen
  map and was killed; it was honest work. Before killing one, read the staging
  trace under `build/reports/commander/.conquest-staging-*/traces/` — its
  `compound-state` events say whether the battle is moving — or ask the cheap
  question first: `-PmaxTicks=3000` plays both fixtures in about 100s. Use
  `-PmaxTicks=9000` or
  `-Pfixture=C:\path\to\fixture.json` for explicitly ad-hoc evidence. Add
  `-PsnapshotEveryTicks=300` to render neutral-observer PNG frames from the
  first replay and assemble `visuals/<fixture>/review.gif`; the frames add
  cyan marine, red defender, and yellow civilian markers for whole-map review.
  They are also **annotated**: every compound is boxed and labelled with what
  it is and who holds it (the command post reads `KEEP`, in its own colour),
  the ground the marines came ashore on is boxed and reads `LZ`, and an arrow
  runs from the beachhead to the keep. Without them a whole-map Conquest frame
  is a picture of a city in which nothing says which grey rectangle is the
  headquarters. `-PsnapshotAnnotations=false` renders the bare scene. Optional
  `-PgifFrameDelayMillis=125`, `-PsnapshotWidth=960`, and
  `-PsnapshotHeight=640` arguments control review playback and output size.
- `gradlew.bat simDeterminism` → replays the Conquest matrix twice for a bounded
  tick budget (`-PmaxTicks`, default 3000) and fails on the first byte that
  differs, writing under `build/reports/sim-determinism/`. It exists because a
  balance run was paying for a determinism check on every fixture at full
  length: the question "does the same fixture play the same way twice" needs one
  fixture and a few thousand ticks, not eighteen thousand of two. Opt-in and
  excluded from `test` / `check`.
  The two replicas run **side by side by default** (half the machine's
  processors; `-Pparallelism=1` is the serial control), which asks a second
  question the serial form cannot: whether two simulations in one JVM can see
  each other. **They could, and this found it on its first run.** The sim's
  per-thread scratch is all `ThreadLocal`, but `LosCache` was not per
  simulation — its enable flag was one static volatile that every sim raised at
  its own tick top and lowered at its own tick end, and `clearAll` swept every
  cache in the process rather than the caller's own. So one battle emptied
  another's line-of-sight cache mid-tick and switched its caching off, and two
  replicas of `reinforced-south` that agreed for three hundred ticks recorded
  the same compound-presence change one tick apart. A cache now belongs to the
  `NavigationGrid` whose topology invalidates it, so a sweep reaches only that
  grid's own.
  **The second thing it found was worse, and only concurrency could find it.**
  `BspCityGenerator` is one instance shared by every battle in the process, and
  `assembleResult` stored the finished tactical map, road graph and biome map on
  `this` before handing them to the `MapResult` — reading them back off the
  instance rather than out of the context it had just generated. So a battle
  whose generation overlapped another's was built on **the other battle's
  map**: a `reinforced-south` replica came out with `full-strength-west`'s
  compounds and its garrison allocated against them. It is composed from locals
  now; the `last*` fields remain as the single-threaded preview seam the render
  tests read. Nothing serial ever saw it, and nothing serial ever will.
- `gradlew.bat profileConquestTail` → opt-in 30 Hz-paced, continuous Conquest
  tail pass through the production async-route and parallel-unit scheduler.
  It runs the checked-in full-strength fixture for 1200 ticks by default,
  excludes the first 300 from timing, and writes
  `build/reports/performance/conquest-tail/summary.json`. Each retained worst
  tick includes phase wall times, inner behavior/action/search counters, squad
  replan triggers and slowest squads, unit-worker timings and slowest units,
  plus async-route queue/activity deltas;
  the report also retains the worst tick for each phase. It includes summed
  tick wall time, per-phase totals, overlapping inner-operation totals, and
  progressive convoy clearance/cost cell evaluations, node expansions, and
  searches started per retained tick and summed across the run, plus
  navigation-mesh refresh shape, setup-pending changes, and per-refresh
  cover/seam/whole-snapshot assembly times so cumulative cost and rare topology
  spikes can be read together. Add `-Dbattle.tail.jfr=true` for a JFR
  execution-sample recording and one event per measured tick in the same output directory;
  JFR adds overhead, so use the ordinary run for timing magnitude and the
  recording for code-path attribution. Add
  `-Dbattle.tail.workerTimeline=true` together with JFR to retain every unit
  callback's worker identity, last entered GOAP action, nanoTime interval and
  thread-CPU delta for dispatches lasting at least 10 ms. Includes early-return
  callbacks. Separate dispatch markers identify stream-root completion and host
  wakeup: the latest callback is not assumed to be the join's completion. Callback
  events are emitted by the host after join; use their explicit timestamps and
  worker identity, not the JFR emission timestamp/thread. This diagnostic adds
  per-callback CPU reads, 1 ms stack sampling, and zero-threshold monitor events;
  do not use it for ordinary performance comparisons. Add
  `-Dbattle.tail.convoyUncachedStages=true` to measure isolated terrain-cost,
  APC-clearance, connectivity-label construction, and a frozen progressive
  route-input snapshot on the same grid after the timed replay; this does not
  depend on a convoy winning dispatch and does not change measured ticks. Use
  `-Dbattle.fixture.path=<absolute path>` to replay construction inputs from a
  live `tick_profile_spike_*.json.data` or its `.fixture.json.data` sibling,
  and `-Dbattle.tail.totalTicks=N`, `-Dbattle.tail.warmupTicks=N`,
  `-Dbattle.tail.paceMillis=N`, or `-Dbattle.tail.outputDir=<absolute path>`
  for targeted runs. The test fork prefers the game's bundled Java 17 when
  present; `-Dbattle.tail.javaExecutable=<absolute path>` overrides it. The
  live dump is a tick-zero construction fixture, not
  an in-flight state snapshot, so replay need not reproduce the exact spike.
  `-Dbattle.pathfinding.compactCasualtyRouteCost=false` is the dense-storage
  control for casualty routing costs. Both representations calculate the same
  block multipliers; the control additionally expands them across the map.
  `-Dbattle.pathfinding.asyncDefendSite=false` keeps defended-site travel on
  synchronous A* while leaving track rallies asynchronous, for same-build
  coverage controls. Slow flat-search samples include member, squad, stable
  action identity, and route reason; action totals span all measured ticks.
  `-Dbattle.pathfinding.pruneFlankCandidates=false` restores exhaustive flank
  candidate A* fan-out. The default skips candidates whose admissible score
  bound cannot improve the incumbent, preserving the selected cell and tie order;
  actual proofs are labelled `FLANK_SNAP` in slow-search samples. Tail reports
  retain immutable post-tick goal, assignment, objective footprint, zone extent,
  and member/target positions alongside slow searches. This context is not a
  query-time snapshot. `-Dbattle.pathfinding.boundFlankProofs=false` disables
  the independent flank-proof ceiling: by default A* stops when its minimum
  frontier cost exceeds every route allowed by the existing detour-step limit.
  Accepted route ordering is unchanged; this is not a per-tick work budget.
  `-Dbattle.pathfinding.flankStepGate=false` disables the independent shared
  minimum-step rejection gate. Difficult flank selections may build one local,
  expansion-capped flood after four candidate proofs; it rejects impossible
  detour budgets, leaving unknown candidates and winner selection to ordinary
  A*. `FLANK_STEP_FIELD/EXPANDED/REJECT` separates this work from saved searches.
  `-Dbattle.goap.retainFlankPlans=false` disables positive ReinforceContact
  waypoint retention across routine replans. The default retains a choice for
  300–360 ticks with four-cell anchor-displacement and intent/terrain/plan
  invalidation. `FLANK_PLAN_REUSE/SELECT` records reuse and fresh choices. This
  does not extend the separate worker-side AttackMove memo's lifetime.
  `GROUND_*` and `VEHICLE_*` counters separate ground stages, local trajectory
  heuristic/lattice work, and synchronous recovery searches from convoy
  dispatch proofs. These nested elapsed times overlap; expansion, failed-plan,
  recovery-attempt, and heuristic-storage-cell counters are count-only.
  `-Dbattle.vehicle.rejectInvalidPlannerStart=false` restores local heuristic
  setup for a padded starting footprint that already rejects every successor.
  Invalid-start counters distinguish actual-chassis versus padding-only failure
  and bounds versus terrain/closed-edge failure. Both arms classify the input;
  the default skips the already-doomed heuristic and lattice work.
  `-Dbattle.vehicle.reuseFailedRecovery=false` repeats identical failed bounded
  recovery queries. The default reuses only completed failures against the same
  frozen routing inputs and exact request; changed requests and control resets
  require a fresh answer. Recovery failure/reuse counters separate actual work
  from reuse. Neither control changes physical clearance or terminal policy.
  Tail reports
  also include squad-field fallback searches, with their miss cause, expanded
  nodes, and unsigned destination occupancy (including path reservations).
  `-Dbattle.pathfinding.squadRouteAdmission=false` restores member A* when
  shared squad preparation defers an intent. The default publishes exact pending
  intents and admits older waits first; pending objective travel does not mutate
  or advance an old path. Failed, uncovered, mismatched, and intentionally
  unprepared queries retain their ordinary fallbacks. Tail reports separate
  pending requests/calls, admissions, successful resumptions, and wait age.
  A zero `battle.pathfinding.maxSquadRouteBuildsPerTick` keeps legacy fallback
  instead of creating a queue that cannot make progress.
  `-Dbattle.pathfinding.omitFixedGoalOccupancy=false` restores the unavoidable
  terminal crowding toll in flat A*. By default only that additive constant is
  omitted during search; intermediate crowding and terrain costs still rank
  routes. Equal-cost path ties need not match the control. The switch is
  recorded in tail reports so same-build comparisons remain identifiable.
  Tail reports
  record this and the squad-traffic switches, include counts at 30/40/50 ms,
  and exclude warmup from async-route cumulative deltas. Pass
  `-Dbattle.tail.sourceRevision=<commit>` to retain an explicit source revision;
  omission records `unknown`, not a guessed checkout. Lifetime latency maxima
  remain explicitly warmup-inclusive.
  Configurable orders such as DefendSite and DefendTrack are reported separately.
  `-Dbattle.targeting.squadFiringPositions=true` enables the off-by-default
  shared constrained firing-position experiment for patrol and zone-entry
  execution. `FIRING_POOL_*` and `FIRING_INDIVIDUAL_*` inner counters expose
  reuse and candidate/ray work (count-only counters have zero time).
  `-Dbattle.targeting.firingReachabilityComponents=false` restores discarded A*
  reachability proofs for unconstrained firing positions. The default uses the
  grid's topology-revision component cache without changing movement rules.
  `-Dbattle.goap.localHoldPositions=false` restores whole-zone capture-post
  spreading; the default scopes posts to the compound footprint and maintains
  nearest-selected distances incrementally. `HOLD_POSITION` records total picker
  time, `HOLD_POSITION_CELL` inspected cells, and `HOLD_POSITION_FALLBACK` the
  exceptional nearest-zone-cell fallback when no local intersection exists.
  `-Dbattle.goap.priorityOrderedEvaluation=false` evaluates every goal category
  eagerly; the default descends only after higher-category goals cannot supply
  a plan. `-Dbattle.goap.localBreachChecks=false` restores whole-roster breach
  eligibility scans. `-Dbattle.targeting.retainFiringPositions=false` disables
  positive execution-position retention. The default validates one chosen cell
  and retains it for 300–360 ticks unless intent or legality changes; eligibility
  probes and negative/vantage-only answers are not retained. `FIRING_RETAIN_*`
  counters distinguish validation time, reuse, and fresh searches. The older
  shared-pool experiment remains independent and off; enabling it bypasses the
  per-member constrained retention path so controls remain separable.
  JFR tail recordings explicitly capture 1 ms monitor/park waits, full safepoint
  lifecycle, GC pause and VM-operation events, and a `UnitDispatch` event around
  parallel submission/join. Its `wallNanos` is the real elapsed interval; the
  outer `awaitWorkers` metric includes useful execution as well as waiting.
  `-Dbattle.targeting.boundKnownContactScan=false` restores target-ring expansion
  beyond the sight/known-contact bound. `TARGET_SCAN_VISIT/RING/RAY` count its
  candidates, completed rings, and visibility checks. Distant beliefs still
  extend the default bound; unknown enemies cannot become eligible merely
  because no visible winner was found nearby.
  `-Dbattle.targeting.pruneClearZoneSelection=false` restores separate unpruned
  visible/nearest room-target scans. `CLEAR_ZONE_TARGET_SELECT/VISIT/RAY` report
  selection wall time, roster visits, and LOS checks. The default preserves exact
  room scope and first-dense-roster ties without a visibility-radius cap.
  `-Dbattle.squad.isolatedAdvanceThreat=false` restores the broad squad monitor
  for route-threat publication. The default uses a dedicated once-per-tick
  monitor, preserving complete current-tick decisions and hysteresis.
  `-Dbattle.pathfinding.phaseOwnedRallyRequests=false` restores worker-side
  synchronized route requests. The default publishes member-owned intents after
  join; `RALLY_REQUEST_PREPARE/COMMIT` separates host work from worker dispatch,
  and route poll/cancel/no-op counters quantify the former contention surface.
  `-Dbattle.projectiles.phaseOwnedPublication=false` restores per-reader copies
  and synchronized projectile appends. The default shares an update-start
  hazard view and collects launch intents before projectile physics; fresh
  launches remain visible to reservation checks. `PROJECTILE_PUBLICATION_*`
  records host preparation and collection rather than hiding moved work.
  Timings are machine-local diagnostic evidence, never a portable test gate.
  `-Dbattle.unitUpdate.parallelism=N` selects an explicit unit-worker count for
  scheduling controls; omission retains the production processor-count policy.
  The tail report separates sampled per-unit wall time from summed worker CPU
  time across the dispatch. CPU excludes time off-CPU, not just useful work;
  worker scheduling overhead is included, and `cpuMeasuredThreads` reports
  counter availability. Both sums overlap across workers and are not tick wall
  time. Use the custom tick event's `wallNanos` for JFR wall comparisons: on the
  bundled Intel Java 17 running through Rosetta, JFR event duration was measured
  at about 0.4 times `nanoTime`/real elapsed time. Event overlaps remain useful,
  but uncalibrated JFR durations there are not milliseconds of wall time.
  This focused tail pass does not replace the proposed fixture performance
  matrix and its cross-run compatibility checks.
- `gradlew.bat profileConvoyRoute` → isolated full-size Conquest route-proof
  evidence on both traversal axes, independent of whether the timed battle
  selects convoy as its reinforcement means. It reports first-dispatch snapshot
  time, total and worst proof-step time, search attempts and node expansions,
  and how many clearance and terrain-cost cells were actually derived out of
  the 560x336 map under `build/reports/performance/convoy-route/summary.json`.
  Use `-Dbattle.convoyRoute.outputDir=<absolute path>` to redirect the report.
  Its timings are diagnostic, not a cross-host gate.
- `gradlew.bat profileSquadFiringPositions` → opt-in paired scorer evidence on
  six mixed-weapon infantry, target motion and topology repair; writes
  `build/reports/performance/squad-firing-positions/summary.json`.
  `-Dbattle.squadFiringEvidence.outputDir=<path>` redirects it. This measures
  selected-cell validity, spread and reachability, not autonomous combat outcomes.
- `gradlew.bat crewEvidence` → crews a transport and a capital from their own
  room programs, runs each for four minutes of ship's time, and reports what the
  complement actually spent it doing: the idle share, the activity histogram, the
  longest unbroken stretch of doing nothing, how many are still alive, and which
  compartment purposes hold anybody. Opt-in and excluded from `test` — it costs
  minutes of wall clock and needs two whole hulls to answer its question. The
  standing goal is an idle share of zero, with the resting, socialising and
  exercising columns carrying the crew's off-watch time instead; see
  `CrewLivelinessEvidence` for why the dead count as idle.
- `gradlew.bat shaderEvidence` → everything that needs a **real OpenGL driver**.
  It compiles and links every shader the mod defines, checks that every uniform
  the ground composite uploads exists in the linked program, and runs the state
  brackets against the state the game hands a UI hook. This is the only evidence
  here that runs GL rather than modelling it: the snapshot suites draw through
  Java2D and the shader oracles re-implement the arithmetic on the CPU, which
  proves geometry and is blind to a syntax error, a construct one driver
  rejects, or a renamed uniform — and that last one is silent by specification,
  since `glUniform*` on an unknown location is defined to do nothing.
  Opt-in and excluded from `test`, because it needs an accelerated driver and a
  suite that requires a GPU fails on the machine that has none; where no context
  can be made it reports that it skipped rather than failing. It uses LWJGL 2
  from the game's own install (`HeadlessGl`), deliberately: the mod's render
  classes are written against those bindings, so this runs them rather than a
  re-implementation of them, and it costs no new dependency.
- **A clean context proves nothing about the brackets, which is why the hostile
  fixture exists.** A fresh context starts at GL defaults — alpha writes on,
  ordinary blending, scissor off, no program bound — which are very nearly the
  values `GlStateBracket.applyTextured2DState` sets. Every normalisation in that
  method is therefore a no-op on a clean context, and dropping any one of them
  leaves a clean-context test just as green while the game renders wrong.
  `GlStateBracketHostileStateEvidence` sets the documented incoming state —
  alpha masked out of the colour write mask, a foreign blend function left
  behind by another draw — and asserts both directions: our draw still lands
  correctly, and the caller's state comes back untouched afterwards. It carries
  its own control (the same draw unbracketed) so it cannot pass by measuring
  nothing.
  It also pins that **scissor is inherited on purpose**. The tile brackets leave
  the scissor test alone because a tile pass draws into the game's own target,
  inside the panel rect Starsector has already clipped to, and wants to stay
  there; the FBO brackets disable it because that target is ours and the UI's
  clip rect refers to a different surface. Same reasoning, opposite answer —
  neither is a default worth inheriting by accident.
  **What it still cannot do** is discover a hostile value nobody wrote down in
  it. A fixture encodes what we believe arrives; that residue is the part that
  still wants a live pass.
- `gradlew.bat renderEvidence` → what a frame costs, per layer, on the map the
  owner actually plays. It generates the canonical 560x336 Conquest and a
  280x168 control, plays them until units, decals, shadows and effects exist,
  and then runs the shipping `BattleRenderer` — the real collect and the real
  drain — into a `HeadlessGl` context at three framings: a compound, a lane, and
  the whole map. Per layer it reports commands collected, quads, sprites and
  custom passes drained, draw calls, texture binds, and the milliseconds
  collection and submission each took, plus a whole-frame wall clock with the
  GPU waited on. `build/reports/render/summary.md` and `summary.json`;
  `-PrenderMaxTicks`, `-PrenderFrames`, `-PrenderDir`. Opt-in and excluded from
  `test`, for `shaderEvidence`'s reasons — it needs an accelerated driver, and
  it reports that it skipped where no context can be made.
  **Counts must repeat and times must not be read as one.** The task asserts
  that the same world at the same framing collects the identical command count
  every frame, and that **the same fixture stood up twice collects the same
  counts as well**; the times are medians of nine frames after a thirty-frame
  warm-up and carry about 0.06 ms of run-to-run spread per layer. Compare a
  lever against **its own run's control**
  (`-Dbattle.render.zoomGates=false`, `-Dbattle.render.groundMesh=false`,
  `-Dbattle.render.residentRelief=false`, `-Dbattle.render.fogField=false`,
  `-Dbattle.render.groundAtlas=false`,
  `-Dbattle.render.residentDecoration=false`, `-Dbattle.render.unitAtlas=false`,
  `-Dbattle.render.residentRoofs=false`,
  `-Dbattle.render.collectCulling=false`), never against a number in a
  document — and never against a delta smaller than that spread.
  **The world it profiles has to be the same world every run**, which it was
  not: the harness plays 600 ticks before it draws, and under the production
  scheduler that landed somewhere different every time — the same fixture on
  unchanged code collected 496, 567 and 537 `UNITS` commands on three runs. The
  frame-to-frame assertion could not see it, because the harness does not
  advance the simulation between frames and so a single run's counts repeat
  perfectly while meaning something else than the last run's. The task forces
  the serial scheduler the way `commanderEvidence` and `simDeterminism` do, and
  the harness asserts that rather than setting it.
  **Thirty warm-up frames, because three was measuring the JIT.** Three covers
  the texture uploads and the batch growth and nothing of compilation: at three,
  the 280x168 control reported `GROUND` at 0.36 ms of collection at a lane
  framing against 0.14 at the whole map — four times the cells through the same
  loop for a third of the cost. At thirty the same framing reads 0.08 and the
  ordering comes right; every figure early in a run fell by more than half.
  Anything measured here before 2026-09-03 was reading that gradient.
  **A lever that can drop something is accepted on pixels, not on counts.** A
  body wrongly culled is silent — the frame is simply missing it and looks
  ordinary — so the run reads back each framing culled and unculled and asserts
  they are identical, and walks a chosen marine out through the viewport edge in
  half-cell steps to put a straddling body there on purpose. Those readbacks run
  after every timed frame on a map rather than between them: eight megabytes off
  the card landed in the next framing's median as noise several times the effect
  being measured.
  Where it stands: **nothing is submission-bound, and nothing collects more than
  the camera shows.** `UNITS` is five draws across two binds at every framing on
  either map, `ROOFS` is one custom pass and one command, and `GROUND` is three
  draws and **no texture binds at all**. Collectors now visit what is in view:
  at a compound framing on the 560x336 map `UNITS` collects 160 commands where
  it used to collect 527 at every framing alike, `COMPOUND` 6 against 98,
  `UNIT_SHADOWS` 18 against 54 — 0.35 ms of our own against the control's 0.47,
  and `UNITS` there is 0.05 ms of collection against 0.12 of drain, so it is not
  collection-bound any more. What is left is `GROUND` at the whole map, 0.27 ms
  over 929 commands that are all genuinely on screen; culling cannot reach that
  and the next lever there is a framing gate or cheaper per-cell resolution.
  **The harness does not advance the simulation between frames**, so a resident
  thing's patch cost is charged once, in the frame that catches it up, and every
  later frame in a framing reads as the steady state. That is the shape of the
  instrument rather than a flattering choice — the counts assertion requires a
  still world — but it is why a delta-patched layer reads at a hundredth of a
  millisecond here and should be expected to cost more on the frames a live
  battle actually changes something in.
  **A headless run has no `Display`, and that used to size the composite 1x1.**
  `GroundParallaxPipeline` scales its targets by the framebuffer over the
  reported screen, and off a pbuffer the framebuffer is zero -- so every relief
  target was one pixel, blitted over the viewport as a flat colour. The
  per-layer CPU numbers were honest; the composite's own fill was not measured
  at all, and any picture read back was an average of the frame. A zero display
  now means a scale of one.
  **Our share is not the frame.** The per-layer times are CPU: building the
  stream and handing it over. The whole-frame column exists because a lever can
  trade our cost for the driver's — a resident mesh draws the whole map however
  close the camera is — and every other column here would call that a win.
- `gradlew.bat createSnapshots` → every deterministic visual-evidence suite under
  `build/snapshots/` without launching Starsector or creating an OpenGL context.
  It reads art from `mod/` first and the installed game second — the game's own
  order — so vanilla-sourced sprites such as aircraft hulls appear in headless
  frames. The install is already required to build at all (`starsectorDir`), and
  a suite degrades to not drawing those sprites if it is missing. Select
  suites with `-Psnapshot=airfield-sortie,armory,deployable-cover,durability-bars,firing-line,frontage-scene,integral-system-fx,killing-ground,late-arrival,layers,mech-doctrine,perception-sweep,player-order,point-defence,prosecution-hold,runway-sortie,ship-decks,ships-boats,sun-shadows,swarm-overkill,turrets,ui,yield-freeze`
  (default `all`) and redirect the common output root with `-PsnapshotDir=<path>`.
- `gradlew.bat sceneEvidence` → plays the behaviour scenes for their verdicts and
  prints a PASS/FAIL table, one row per loop, with a line beneath each failing
  row saying what was measured. The same scenes already play under
  `createSnapshots`, but rendering is the slow part of that run and **a GIF
  cannot fail**: a scene whose finding has regressed still produces a perfectly
  good animation of the regression. This plays the same catalog with no
  renderer attached, writes `<sceneId>/<loopId>.verdict.json` plus
  `summary.json` / `summary.md` under `build/reports/scenes/`, and exits 1 when
  any verdict failed. Select with `-Pscene=<id>` or a comma-separated list
  (default `all`) and redirect the output root with `-PsceneDir=<path>`. A scene
  that throws while standing its world up fails its own loop rather than the
  run, so one broken scene cannot hide what the others found. Opt-in and
  excluded from `test` / `check`: it plays whole battles, which is evidence.
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
  The Rooms page edits one shipboard room: its footprint, its deck, and the
  fixtures standing on it. It opens on **which room?** — a grid of every room
  the ship has, each one drawn as it generates today through the battle
  renderer, so choosing is looking rather than reading twenty enum names. The
  whole set costs one deck generation and the tiles fill in as they are drawn.
  From there it walks four screens: the
  footprint, the deck, the fixtures, and a comparison. **No room starts from an
  empty grid.** Opening one runs the procedural fitting that owns it and records
  what it did, so the first thing on screen is the room that already ships and
  the first edit is a change to it.
  The room is edited **on its own picture**: the battle renderer draws it as a
  small map of its own and the grid marks that up, rather than replacing it with
  coloured rectangles. Choosing between a vent plate and hazard striping is a
  decision that can only be made by looking, and the flat-colour version was
  perfectly clear about reservations while being useless for the one question
  the deck screen exists to answer. Rendered at one cell per cell with no
  surround, so the marks line up without arithmetic, and redrawn off the event
  thread after every edit — a render that finishes after a newer edit is dropped,
  so the grid keeps the last good picture instead of blanking. The marks are what
  a render cannot say: which cells are deck, which are reserved circulation, and
  which step is anchored where. The comparison
  screen generates **the same hull at the same seed twice**, with the layout
  suppressed and applied, and renders both — so a room drawn too large to fit
  shows up as a missing room rather than as a surprise later.
  **Both ways an authored room fails are silent**, so the page replays every
  draft before trusting it. A fixture whose cell is taken is refused and the
  room merely comes out sparser; an arrangement that severs its own circulation
  has its whole fill thrown away and the room generates as bare deck. A
  checkerboard of crates across an armoury — fifteen fixtures, each legal alone
  — produced a compartment with nothing in it, while the count still said
  fifteen. `RoomLayoutCheck` is what turns both into sentences, the fixture
  count shown is what actually stands up, and a layout that would seal its room
  is refused at save.
  A room draws its **deck** from blocks too: paint a run with `road.vent`,
  `road.striped`, a `floors.*` variant pool, anything that is not a wall. That
  is flavour and nothing else — a vent run and a striped run are the same floor
  to pathing, cover and sight — which is why it is kept separate from setting a
  cell's `GroundKind`, the thing consumers actually read. The picker offers
  every non-wall block rather than a list of floor layouts, because the ways of
  being a floor keep growing and an enumerated picker would omit the next one.
  A room may also name its own **bulkhead**, offered from the blocks that can
  actually be a wall — shape rather than spelling, since `road.embankment` is
  one. It changes the picture only; topology, cover and sight are untouched, and
  where two rooms back onto each other the shared ring is one wall that the
  later room wins. This uncovered a defect worth knowing about: a wall with no
  exterior face draws its block's transparent centre, and no ship stage ever
  stamped a face, so **every bulkhead on every generated deck was rendering as
  nothing**. A wall nobody stamped now takes a face on each open side; a mask
  somebody already set is never re-derived, so cities are untouched.
  A kept room is **written and loaded**: `mod/data/world/rooms/` holds one
  document per purpose and refit level, and `rooms.json` indexes them because
  mod code reads through `SettingsAPI` and cannot list a folder. The tool
  rebuilds that index from what is on disk rather than appending to it, since an
  index that drifted from the folder is a room that silently stopped loading.
  `RoomLayoutCatalog.loadBuiltins` installs them at application load, defensively
  — an unreadable room generates the way it did before anybody authored it,
  which is a worse room rather than a broken ship.
  Reserved circulation is called a **walkway** on the page, drawn as hatching
  rather than a tint — a translucent wash over a busy deck is invisible, and the
  one thing an author needs to see here is which cells refuse furniture. A
  seeded armoury comes back with six of its eight rows reserved, so walkways are
  drawn and rubbed out cell by cell with a tool, plus a clear-everything command
  for the common case.
  **The deck screen shows its tool rather than hiding a mode.** An earlier
  version had a "set the kind" button that silently redirected every later
  click, so an author who pressed it once found that painting a floor did
  nothing for the rest of the session — which is exactly what it looked like
  from outside, and was reported as a bug in the painting. Click and drag paint
  with the chosen tool; the room is redrawn once per stroke rather than per
  cell. Zoom is explicit with a Fit default, since a vehicle bay is forty cells
  across and a server room is five.
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
- `gradlew.bat runStarsector` → deploys then launches the game, running the java
  command line read out of `starsector-core/starsector.bat` rather than the .bat
  itself. The installed .bat ends in a malformed `if errorlevel 1 {` block, so
  `cmd /c` returns that block's status and a JVM that dies mid-battle still
  leaves the task green. Running the command line directly makes the reported
  exit code the JVM's own.
  **The JVM's own output never reaches `starsector.log`.** A fatal-error block, a
  native loader failure, and anything printed outside log4j go to stdout/stderr
  only, so the task tees them to `build/starsector-run/console.log` and points
  `-XX:ErrorFile` at `build/starsector-run/hs_err_pid<pid>.log`. Every run also
  writes `build/starsector-run/run-summary.txt` with the decoded exit status.
  The summary is written by a finalizer, so it appears even when the launch task
  is aborted — a `doLast` is skipped in exactly the cases worth recording, and a
  run that produced no summary at all once left its exit status unrecoverable.
  When the game dies without explanation, read those before `starsector.log` —
  log4j buffers, so a hard kill can drop the log's last lines while the console
  capture keeps them.
  **`-PstockJvm` drops every `-XX:` flag the install carries, plus `-noverify`**,
  keeping heap sizing, `--enable-preview`, the module opens, the system
  properties and the classpath (100 launch args become 32). The installed
  `vmparams` is not stock — it is a community performance file carrying
  `UseAVX=3`, `AVX3Threshold=0`, `-AlignVector`, `EnableVectorAggressiveReboxing`,
  `UseVectorStubs`, `ShenandoahGCMode=iu` and `-noverify`, any of which can end a
  process in ways that skip the JVM's own crash reporting. It is a control for
  "is it the flags?", not a recommendation; bisect them only once that answers
  yes. The summary records which mode ran.
  **The exit status is the fact that separates the cases**, which is why it is
  written to a file rather than only logged. `0` means something asked the
  process to stop, so a `0` with no shutdown banner in the log means it was
  killed from outside the JVM. An NTSTATUS names a cause instead:
  `0xC0000005` is a native crash, and `0xC000001D` (illegal instruction) points
  at the experimental `-XX:UseAVX` / vector flags in the installed `vmparams`
  rather than at anything in the mod.
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
| `ships-boats` | One hull's own boats: a bay with its rank of shuttles, every bay ringed with the door it launches through, and an animated turnaround — a boat leaving its berth, the berth standing empty, and the boat home again | `build/snapshots/ships-boats/` |
| `turrets` | Authored mount-state strips, including projectile and impact effects | `build/snapshots/turrets/` |
| `ui` | Marine Ops screens at authored viewport sizes, including the scale-invariant battle task-force plate and MLX command rail | `build/snapshots/ui/` |
| `firing-line` | Two animated loops of six marines in column in a corridor: the file shooting through its own men, and the same file stepping a cell out of each other's lanes. The acceptance for the friendly-lane step-aside | `build/snapshots/firing-line/` |
| `frontage-scene` | Animated garrison stand-to on a generated compound, one loop per approach edge | `build/snapshots/frontage-scene/` |
| `point-defence` | Animated LRM salvos against a placed interceptor pod: rounds stopped, rounds missed, rounds arriving | `build/snapshots/point-defence/` |
| `deployable-cover` | Animated controlled comparison of a placed revetment: the same fire into a covered lane, an open lane, and a screened post shot from the flank | `build/snapshots/deployable-cover/` |
| `integral-system-fx` | A running integral system's halo: a narrow authored screen beside a wide one draining their soak pools, one breaking under concentrated fire, and one pattern's screen at four facings | `build/snapshots/integral-system-fx/` |
| `perception-sweep` | The player's own picture — fog overlay and hidden-unit gating included — before, during, and after a Janus sensor sweep | `build/snapshots/perception-sweep/` |
| `airfield-sortie` | Three animated loops of one garrison airfield: a sortie's crew walking to the pad unopposed, the same walk under fire, and a fire team burning the based aircraft on their stands | `build/snapshots/airfield-sortie/` |
| `runway-sortie` | Two animated loops of one station flying a fighter off its strip: the whole cycle unopposed — taxi, roll, gun runs, approach, rollout, taxi in — and the same cycle with a fire team astride the taxiway | `build/snapshots/runway-sortie/` |
| `killing-ground` | Two mirror-image lanes to one objective: a squad destroyed in one of them, its killers removed, and the next squad sent up to choose again | `build/snapshots/killing-ground/` |
| `mech-doctrine` | Four animated loops of one Bulwark under Brawler, Tank, Long Range Support, and Balanced doctrine, plus a paired Form-on-Lead / Free-Reign Brawler comparison | `build/snapshots/mech-doctrine/` |
| `swarm-overkill` | One squad meeting a rush of runners a single rifle kills, both sides ordered to hold the ground so they actually fight. The no-regression control for damage-aware target crowding | `build/snapshots/swarm-overkill/` |
| `player-order` | One squad under a long standing mission and one ground click off its axis, recorded three ways: the click answered and carried out, the same world with nobody clicking, and the same click into a picket's fire. The acceptance for the order-path rebuild | `build/snapshots/player-order/` |
| `prosecution-hold` | One squad ordered to take a room with an enemy standing off the way there that nobody can shoot at, recorded three ways: the squad getting on with the order, the same world frozen with the fall-through off, and the same contact placed near enough to take firing positions against | `build/snapshots/prosecution-hold/` |
| `late-arrival` | One campaign squad that stepped off short-handed and the rest of it landing thirty cells behind, recorded four ways: the crossing, the same crossing past an armed picket, and both of those again with the rejoin state switched off | `build/snapshots/late-arrival/` |
| `yield-freeze` | One squad under one order, recorded four ways: the order worth having and the same order over a zone that turns out to be empty, as infantry and again as a mech lance. Counts plan-less ticks rather than distance | `build/snapshots/yield-freeze/` |
| `sun-shadows` | One generated city under the directional sun: an elevation ladder, a bearing sweep, one building's roof caved in beside itself intact, marines casting beside the same marines with the shadow layer left out, and one craft at three altitudes walking its shadow away from itself — each against a control. Terrain shading is the **CPU model of the composite shader, not the shader**; the bodies panel is the real `UnitShadowRenderSystem` collected and drained | `build/snapshots/sun-shadows/` |

Run all suites with `gradlew.bat createSnapshots`. Use
`-Psnapshot=<id>` for one suite or a comma-separated selector for several; quote
the whole property in PowerShell, for example
`'-Psnapshot=layers,turrets'`. `-PsnapshotDir=<path>` changes the shared output
root while retaining the per-suite subdirectories. Command-line generation
replaces matching files without prompting and does not remove stale ones, so a
suite that renames an artifact leaves the old name behind until it is deleted.

A `-Dbattle.*` switch on the command line reaches the Gradle daemon and stops
there. `createSnapshots` and every `Test` task forward them into the fork, so a
**control run** — the same evidence with one routing or pathfinding layer
switched off — is one command away:

```powershell
.\gradlew.bat createSnapshots '-Psnapshot=killing-ground' '-Dbattle.pathfinding.casualtyRouteCost=false'
```

Reach for that rather than checking out an older commit. A commit-to-commit
comparison measures every other difference between the two trees at the same
time, which is how a seven-thousand-tick swing in the Conquest matrix was once
credited to a squad behaviour and turned out to be another session's
reinforcement work arriving on a merge.

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

**A headless aircraft is the size somebody primed it to be.** Hull geometry
comes from the install's own `.ship` specs through `SettingsAPI`, and outside
the game there is no `SettingsAPI`, so `HullFootprintResolver` quietly falls
back to one flat length for every hull alike — a Wasp the size of a Valkyrie,
and with it a body radius, a drawn hull and a blast catch belonging to an
aircraft that exists nowhere. `InstalledHullSpecs.install()` is the one way to
prime it; the JUnit extension and `CreateSnapshotsCli` call it, and a scene
calls it itself so a scratch harness gets it too. Anything that *measures*
aircraft size should assert `HullFootprintResolver.isMeasured` rather than
trust the number, because the fallback is silent by design.

Prefer a scene over a mission harness whenever the question is about one
behavior rather than about a whole battle's balance, and add another scene
rather than widening this one past what its name claims.

`RunwaySortieScene` is the fourth: a station with a strip, one fighter flying
the whole cycle off it — out of the shed, round the hangars, down to the
threshold, along the strip, out to the target, gun runs, home, down, and back
in — recorded unopposed and again with a fire team astride the taxiway. It
exists because the unit tests each pin one link (the phase hands to the next,
the route avoids the wall, the landing captures on the centreline) and none of
them can show the thing a strip is for: that the crossing is a stretch of time
somebody can be standing beside.

Building it found the fault that mattered most in the whole feature. Air could
be engaged only by defence posts and only while airborne, so the minute of open
ground the runway buys was a minute of complete safety and the trade was a
fiction. Evidence is worth building for what it turns up on the way.

**A scene's opposition can be too good.** Six marines beside the taxiway killed
the fighter in under four seconds beside its own shed, which is not a crossing
under fire — it is a recording of a machine dying at home. Three take about half
its hull over the run. Equally, a platoon left in the open advances: the target
walked to the airfield and shot two parked fighters before the field's first
sortie was due, so it is dug in *and* most of the map away, because a gun run
breaches the pit it is attacking and the survivors walk out.

`KillingGroundScene` is the third: two mirror-image lanes to one objective, a
squad destroyed in one of them, and the next squad sent up to choose again. It
exists because the Conquest matrix cannot answer a question about one behaviour
— a seven-thousand-tick swing there was credited to a squad behaviour and turned
out to be another session's reinforcement work arriving on a merge.

**A control loop is part of the instrument, not a luxury.** The scene records
the same map with nothing having happened in either lane, because "the squad
went west" means nothing until you know which way it goes when west is
unremarkable.

**Remove whatever would answer the question for the wrong reason.** A squad
already routes away from enemies it can see, so an ambush left standing sends
the next squad down the other lane for a reason that has nothing to do with
memory — the first version of this scene recorded exactly that and would have
credited a casualty layer with an avoidance the game already had. The killers
are despawned once they have done their work, leaving two lanes identical in
everything a router can observe except that one is full of dead marines.

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

`YieldFreezeScene` is the fifth: one marine squad, one `CLEAR_ZONE` order, and
three rooms in a row. It exists because a mission goal may decline its own order
deliberately — `ClearAssignedZoneGoal` yields when the assigned zone turns out to
hold no live enemy, so the commander can reassign — and beneath a yielded
mission goal the ladder was empty. The squad got no goal at all: a null plan, and
members that drop their paths by design.

**Plan-less ticks are the reading, not distance.** A squad holding position
deliberately does not move either, so distance cannot tell a considered halt from
an absence of orders. What separates them is whether the squad holds a plan at
all, and a fix here should drive plan-less ticks to zero *without* necessarily
moving the squad one cell — a squad that wanders off looking for work has been
given the mission-inventing behaviour the noun doc forbids.

**A distant enemy is an attractor, not a bystander.** `BreachToEngage` falls back
to an omniscient nearest-enemy scan for squads that have not ticked targeting
yet, so the lone far-off defender every scene keeps alive to stop the simulation
terminating will be walked to if it can be reached. The first version of this
scene left a door in the far wall and recorded both loops crossing the whole map
to it — near-identical distances, and nothing whatever about the yield. Seal that
room: the goal's own reachability gate then rules the defender out.

What it records now that the floor exists: both loops sit at 1 plan-less tick of
1801 — tick zero, before the first replan — and the yielded loop still covers
0.0 cells. That pairing is the whole acceptance. Plan-less at zero says the
squad is no longer unable to act; distance still at zero says it did not answer
that by wandering off to find work it was never given.

**The same question of the other dispatcher gets a different answer.** The scene
also runs the pair as a mech lance, and a lance under the identical yielded order
never loses its goal: `MechAssignedObjectiveGoal` does not stand down on a clear
zone the way its infantry counterpart does, and beneath it the mech engagement
floor is both always relevant and always plannable, so that ladder cannot reach
an idle bucket at all. A floor goal added to `MECH_GOALS` today would be code
that cannot run. That safety is an accident of the action library rather than a
guarantee — give a doctrine action a precondition and the mech ladder silently
acquires the defect the infantry one was cured of — so it is pinned by
`MechLadderHasAFloorTest` rather than left to be rediscovered.

**A control that reproduces the defect measures nothing.** The control's defender
first stood on the doorway's own sight line, so the squad shot it down the
corridor without ever crossing, the zone went clear, and the control yielded and
froze exactly like the case it was meant to contrast with. Moved off that line it
crosses properly — and then falls into the same hole once it finishes, which is
the more useful recording of the two.

`SwarmOverkillScene` is the sixth: one squad, open ground, and twenty
`SWARM_RUNNER`s closing on it. It exists because the Conquest matrix cannot ask
its question at all — every defender there is a marine or an emplacement, and
this needs an enemy whose durability is smaller than one shooter's output. A
runner carries 20 structure against a rifle's 18, so a second rifle on the same
runner is very nearly a wasted shot while the rest of the rush closes
unanswered.

**Both sides carry a mission goal, and without it there is no fight to record.**
Eight marines meeting twenty runners is an unfavourable local balance and the
doctrine reading it is correct to disengage; the rush breaks too once it has
taken casualties. The first recording was both sides withdrawing in opposite
directions for twenty seconds — a difference between two targeting arms that
could not possibly show up, because neither side was firing. A mission goal
outranks survival by bucket, which is what pins them in the fight the scene is
named for.

**What it records is a negative, and that is the point.** Damage-aware crowding
changes nothing here — cleared at tick 450 with the squad intact, either way —
because per-body crowding was already about right for a target one rifle kills:
the committed share is 0.9, so the two agree. The case that was actually broken
is the opposite one, a chassis many rifles deep, and Conquest is where that
shows. The scene is kept as the control proving the swarm case does not regress,
and as the instrument for the next attempt at it.

`PlayerOrderScene` is the seventh: one squad, a commander's `ATTACK_MOVE` to the
far east edge, and one player click at (40, 6) — well off that axis, so a squad
merely continuing east cannot be mistaken for one obeying. It asks whether a
squad takes a direct order at once, holds it, and hands back to its mission when
done, and it is the acceptance for the order-path rebuild. Three loops: the
click, the same world with nobody clicking, and the click into a two-marine
picket's fire. It is the first scene on the `BehaviorScene` instrument, so it
plays under `sceneEvidence` for its verdicts and under
`createSnapshots -Psnapshot=player-order` for the same run's frames.

**Its first run found the order path taking an order and never giving it
back.** The first two promises held: the click was accepted on tick 61 — the
first tick that can see it — the squad was planning under it on that same tick,
and there was not one plan-less tick in 899. The last two did not. The squad
walked to the ground it was pointed at, arrived around tick 640, and stayed
there with the player's order still standing for the rest of the battle; played
out to three thousand ticks it was the identical centroid to two decimal
places, and the commander's mission was never resumed. The contested loop
parked the same way, so it was not about the fight; the control held its
mission goal for all 899 ticks, so it was not about the map.

**Two rules for one arrival was one too many.** The release tested the squad
centroid against `AttackMove.ARRIVAL_RADIUS` (2 cells); the action completes
when somebody is inside that radius *and* everybody is inside its 5-cell
`SQUAD_ARRIVAL_RADIUS` footprint, and an arrived member then holds its ground
on purpose so one marine cannot complete a step the squad shares. Six people
stopped by the action's rule settle with a centroid a little under three cells
out — satisfying the action, missing the release by most of a cell — and
nothing moves them again, because the order does not expire and outranks the
mission it masks while it stands. The release now asks
`AttackMove.squadHasArrived`, the action's own completion rule, and the scene
records the handback at tick 600 with the mission replanned on that same tick
and the squad 15.7 cells further east by the end; under fire it hands back at
590. Every verdict passes, and the bar was not moved to get there.

`FiringLineScene` is the eighth: six marines in column in a corridor, four
defenders at the end of it, and one question — does a marine with his own man in
front of him step out of the lane instead of shooting through him? It is the
acceptance for `LaneSidestep`, the third measured attempt at a fault the
decision side had never asked about at all: every line test bottoms out in the
navigation grid, which holds terrain and no units, while the ballistic model has
always given a friendly met before the target about a third of a chance of
catching the round. Two earlier attempts answered it by switching target and
both lost Conquest captures; this one steps aside instead.

**A corridor three cells wide answers the question by removing the option.**
That was the first version, and it looks right — a file down the middle with a
lane either side of it. It recorded not one sidestep in nine hundred ticks. A
firing lane is a cell and a half of half-width, so leaving one takes more than a
cell of lateral movement, and three cells offer exactly one. The reading — no
sidesteps, identical friendly fire in both loops — was indistinguishable from a
reflex that did not work. Five cells is the narrowest passage in which stepping
aside is a move that exists.

**The standing firing line holds no pursuit target, which is the other thing it
found.** A planted squad runs `OverwatchPosture`, which deliberately sets no
`COMBAT_TARGET_ID` and leaves the shooting to the dispatcher's opportunity pass.
A reflex gated on the pursuit target alone could therefore never fire in the
case it was written for. The registered threat is the persistent answer — the
firing system will not let a round go until it matches — and asking for it is
what made the scene say anything at all.

**A reflex that hands the tick back owes two things, and the scene pins both.**
The step-aside authors a path and declines, so the assigned step fires the
marine and walks him — which is only safe while *exactly one* caller moves him,
and a marine advanced twice simply arrives in half the ticks with every other
reading looking right. `one-mover-per-tick` measures the busiest sidestep tick
against one tick of his own travel. The other obligation is that a step which
plants a marine who can fire from where he stands throws the authored move
away on the tick it was made: measured, nine sidesteps with three covering any
ground. `sidesteps-complete` counts moves that *went somewhere*, because the
weaker form of it — "at least one moved" — passed on the broken version, as did
the friendly-fire and damage figures. Three plant sites now consult
`LaneSidestep.isStepping`.

What it records: the control puts 5.9 HP into its own men and the subject puts
none, both loops land the identical 100.0 HP on the enemy, and all six marines
are alive at the end of both. Five sidesteps carry the whole difference, and
every one covers ground. Conquest is where it is decided, and two shapes of the
same step were measured there. Consuming the tick cost reinforced-south ten of
its fourteen captures; handing it back recovers almost all of that — 12 captures
and 11 held against the control's 14 and 9, with 56 more defenders killed. It
ships **on**, behind `battle.infantry.laneSidestep`, because held compounds are
the outcome a Conquest is decided on and captures is throughput: 12 taken and 11
kept beats 14 taken and 9 kept, and it holds more on both fixtures. The scene's
control loop sets the toggle false for itself.

`ProsecutionHoldScene` is the ninth: one squad ordered to take a room sixty
cells east, and one stationary enemy thirty-two cells off that axis which not
one marine can reach. It asks whether a squad prosecuting a contact it has no
firing position against gets on with the order it was given, and it is the
acceptance for `battle.squad.prosecutionFallThrough`.

**It was written from two squad dumps of a live battle rather than from a
theory.** Twelve marines under `CLEAR_ZONE`, executing `EnterZone`, stood on
their landing pad for a hundred and eighty consecutive frames — nobody moving,
nobody holding a path, every member settled — under HOLD / ADVANCING /
PROSECUTE against a defender thirty cells away with zero engageable members and
a force ratio of 0.18 in their favour. Prosecution leashes its firing-position
search to the squad centroid at twelve cells; the contact was further off than
that plus weapon reach; the search returned nothing; and nothing was read as
"no path", whose answer is to plant. `contactHoldIsFresh` is true while any
contact is observed, so the picture that caused the freeze was renewed by it.

What it records: the control makes **0.0 cells** of eastward progress in nine
hundred ticks with 898 of its 899 contact ticks reading PROSECUTE, which is the
live dump reproduced exactly; the subject makes 46.0 cells with the identical
picture and not one plan-less tick; and the third loop, with the contact moved
to twenty cells where a firing position does exist, still puts 25 HP into it and
covers 6.3 cells to the subject's 15.6 over the first three hundred ticks — so
the fix did not delete prosecution, it bounded it.

**Two ways the world was wrong before it was right, both silent.** A hole left
in a wall as ordinary floor is a gap and not a portal: the detector merged the
room into the field, the order named the zone the squad was already standing
in, the tactical axis pointed at the middle of the map, and the contact came out
FRONT rather than flank — so the doctrine was ADVANCE and there was no
prosecution to measure at all. `SceneBuilder.doorway` is the marked-cell
version. And the room's own occupant becomes the picture's primary as the squad
closes on it, which is correct and which halved the measured prosecution share;
every reading here is gated on the primary being the off-axis body the scene is
named for.

**A scene whose geometry is measured in weapon reach cannot use a rolled kit.**
The default roll hands out carbines and one pulse rifle, ten cells apart, so at
this distance five marines have no firing position and the sixth does — five men
standing still beside one walking off north, which is neither of the behaviours
being compared. `SceneBuilder.primary` issues one named weapon to every seat.

**A scene answers rather than merely records.** A `BehaviorScene` returns one
`SceneReport` per loop — its verdicts and the readings they were judged from —
and registers through `META-INF/services` so `sceneEvidence` and a snapshot
suite reach the same catalog. `sceneEvidence` plays it with no renderer and
writes the verdicts under `build/reports/scenes/`; a `BehaviorSceneSnapshotSuite`
subclass plays it once *with* a renderer, so the picture and the verdict come
from the same run and cannot disagree.

`LateArrivalScene` is the tenth: a campaign squad whose form-up has already
timed out, four marines landing at its abandoned landing zone thirty-one cells
behind it, and one question — does a late arrival cross to its squad without
picking a fight of its own? It is the acceptance for `SquadRejoin`.

**The uncontested control very nearly ties, and that is the finding rather than
a disappointment.** Four marines dropped that far back are inside cohesion again
at tick 836 without the state and 799 with it. The cohesion pull has been there
all along and does most of this work on an empty map; if that were the whole
measurement the state would not be worth shipping.

**What it ships for is the contested pair.** Two armed defenders eight cells off
the crossing, and the control *stops*: 420 of its 1,528 crossing ticks —
fourteen seconds — standing still trading fire with a picket it was never sent
to fight, reaching its squad at tick 906. The subject stands still on **none** of
its 849, fires 114 rounds while walking, and is back inside cohesion at 755.
Neither loop strays more than a couple of cells off the axis, so the fault was
never that a late arrival walks *toward* the enemy: it halts, where it stands,
for as long as the enemy lives. `kept-crossing` is what measures that, and no
lateral distance could have seen it — the first version of this scene measured
only how far off the axis they went, and both loops answered 1.9 cells.

**Two traps in the instrument, both of which reported a firefight as silence.**
Fire intent is authored and consumed inside the advance, so `fireTargetId` reads
zero from every observer and the first contested loop recorded 411 ticks under
the picket's guns with not one round fired; the shot events of the previous
advance are the seam that says what actually left a barrel. And the picket has
to be **placed at the lift rather than at the start**: the main body walks the
same axis on its way east and kills it before the second lift is in the air,
which is `AirfieldSortieScene`'s scenery mistake arriving from the other
direction.

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
