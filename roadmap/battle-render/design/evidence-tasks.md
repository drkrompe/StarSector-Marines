# Evidence tasks

Status: ACTIVE

Written: 2026-10-24

The opt-in Gradle tasks that play, generate or measure something too big for
`test`: what each answers, how to run it, and where it writes. Moved out of
`CLAUDE.md`, which keeps one line per task. Every task here is excluded from
`test` / `check`. Render and GPU tasks are in `render-evidence.md`, the snapshot
catalog in `snapshot-suites.md`.

## Shared conventions

- **Determinism switches.** Every `Test` task gets synchronous routes and
  influence, and the battle tasks force the serial unit scheduler, so a run is
  the same battle every time. A JavaExec without them plays the live game's
  asynchronous battle, which is not the one the evidence reported.
- **`-Dbattle.*` switches are forwarded** into every Test fork and
  `createSnapshots`, so a control run is the same command with one switch off.
  Compare against a control run, never a checkout of an older commit, which
  measures every other difference between the trees as well.
- **Memory: measure before raising the heap.** Test tasks, `battleProbe`,
  `sceneEvidence` and `sceneDeterminism` run under `-PtestHeap` (default 6g);
  `createSnapshots` keeps the JVM default unless given it. A battle's live set
  is small: a framed The Burn peaks near 0.3 GB and an unframed 9,000-tick
  Conquest sits near 0.22 GB, both under `-PtestHeap=2g`. When a run dies of
  OOM, find the retainer rather than raising the flag:
  `-PtestHeap=2g "-PtestJvmArgs=-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=<dir>"`
  leaves a dump, and `-XX:StartFlightRecording=filename=<file>.jfr,settings=profile`
  in the same property gives the heap-after-GC curve
  (`jfr print --events jdk.GCHeapSummary`). A curve that rises with ticks and
  never falls is a leak. A render-side cache must be bounded by what the frame
  draws, not by a size cap it reaches every run.
- Never run two of the same Test-based task at once in one worktree: they share
  `build/test-results/<task>` and the second fails on the first's files.

## Battles and missions

### `commanderEvidence -Pmission=<id>`

Runs the selected mission's documented construction-fixture matrix in a
zero-input simulation and writes canonical traces plus `summary.json` /
`summary.md` under `build/reports/commander/<mission>/`. Missions: `conquest`,
`conquest-trial`, `sabotage`, `assault`, `raid`, `extraction`, `relief`,
`counterattack`, `junction`, `last-lift`, `relay-nine`, `thunder-run`,
`supply-route`, `corvus`, `colony-breach`, `cordon-line`, `cordon-road`,
`heart`, and the story missions `yard-owners`, `water-under-seal`,
`limited-warrant`, `district-escort`, `terms-of-protection`, `small-war`,
`salvage-rights`, `cold-start` and `working-passage`. A new mission harness
extends this argument rather than adding a task. Per-mission matrices, fixture
fields and report contents: `commander-evidence-missions.md`.

- Each fixture is replayed once; `-Prepeat=2` restores the double replay with
  its byte-identical assertion. `simDeterminism` owns that question.
- Replays run side by side on half the processors; `-Pparallelism=N` sets it,
  `-Pparallelism=1` is the serial control.
- **Conquest is slow, and slow is not stuck**: about 17 minutes at 560x336
  (the other missions finish in under half a minute). Before killing a quiet
  run, read the staging trace under
  `build/reports/commander/.conquest-staging-*/traces/`, whose `compound-state`
  events show whether the battle moves, or ask the cheap question first:
  `-PmaxTicks=3000` plays both fixtures in about 100 s.
- Ad hoc: `-PmaxTicks=9000`, `-Pfixture=C:\path\to\fixture.json`,
  `-PevidenceDir=<path>` (so a run and its control do not overwrite each
  other).
- Pictures: `-PsnapshotEveryTicks=300` renders neutral-observer frames from the
  first replay into `visuals/<fixture>/review.gif`, annotated (compounds boxed
  and named, the keep as `KEEP`, the landing as `LZ`, an arrow from beachhead
  to keep); `-PsnapshotAnnotations=false` drops the annotations.
  `-PsnapshotFocus=x,y,cellsAcross` frames a close view with shots and impacts,
  `-PsnapshotFromTick=T` plays the ticks before `T` undrawn, and
  `-PgifFrameDelayMillis`, `-PsnapshotWidth`, `-PsnapshotHeight` set playback
  and size.

### `simDeterminism`

Replays the Conquest matrix twice for a bounded tick budget (`-PmaxTicks`,
default 3000) and fails on the first byte that differs, writing under
`build/reports/sim-determinism/`. The two replicas run side by side by default
(`-Pparallelism=1` is the serial control), which also asks whether two
simulations in one JVM can see each other. That has caught process-global
state twice (a line-of-sight cache shared across battles, and the map
generator storing its result on a shared instance): anything per battle must
live on the battle, never in a static or a shared generator.

### `battleProbe -Pbattle=<source> -Pscript=<file>`

Ask a battle questions instead of bisecting for them. Stands one battle up,
steps it on command and answers queries between steps, one JSON line each:
`until T`, `until-beat final_run`, `entity ID` (health, plate, target, orders,
squad, telemetry), `near ID R`, `find label=TURRET`, `squads`, `beats`, and,
after `hitlog on`, `hits target=ID` / `hits-by attacker target=ID` (every
resolved hit with penetration, plate rating and arc, and what went to armor and
structure). Sources: `thunder-run[:seed=N,...]` and `fixture:<path>`; without
`-Pscript` it reads stdin. The battle is deterministic, so stepping to the tick
that matters and switching the hit log on there answers any number of
questions for one replay. The hit log (`HitLog`) draws no random number and is
off unless asked for. A mission with its own construction adds a source in
`HeadlessBattles.build`, which the snapshot suites share, so a probe reading
reproduces in a clip.

### `sceneEvidence`

Plays the behaviour scenes for their verdicts, with no renderer, and prints a
PASS/FAIL table, one row per loop, with what was measured beneath each failing
row. Writes `<sceneId>/<loopId>.verdict.json` plus `summary.json` /
`summary.md` under `build/reports/scenes/` and exits 1 on any failed verdict.
`-Pscene=<id>[,<id>...]` (default `all`), `-PsceneDir=<path>`. A scene that
throws while standing up fails its own loop rather than the run. Use it rather
than `createSnapshots` to check verdicts: a GIF cannot fail.

### `garrisonEvidence`

Stands up every story mission at tick zero and inventories what each map
offers a squad to hold: per tactical node, windows, entrances, distinct stand
cells and fighting-position runs, plus what no node covers. Each fort kind
stands on its own look-study map (`FORT_BATTERY_FORT`, `FORT_SHIELD_FORT`) with
a table of its buildings' floor, exterior wall, doors and windows.
`-Pcases=<name>,...` surveys only those cases. Writes
`build/reports/garrison/summary.md`. See `garrisonables.md`.

### `generatedSpaceEvidence`

The JUnit assertions that have to generate a map, stand up a mission or man a
deck to ask their question (map scans, landing sites, setup and arrival plans,
filler batches, deck runs). Tag a method or class
`@Tag("generated-space-evidence")` to put it here. It still asserts, so run it
when touching world generation, setup or the deck sim.

### `storyMapPools`

The Rav Bloom story maps (Cordon Line and Road, The Burn, Broad Front) play
only from pools of vetted seeds, three per world biome, in
`mod/data/world/pools/<map>.pool.json` (`story-map-pools.md`). This regenerates
every pooled seed and fails when one no longer passes its mission's acceptance
on the first generation: **run it after any mapgen change that may move a story
map.** `commanderEvidence` on `cordon-line`, `cordon-road` and `heart` plays the
whole pool by default, and `createSnapshots -Psnapshot=story-map-pools` renders
every pooled map whole; Conquest and skirmish stay fully random. `-Pcandidates=cordon-line:1-40,heart-burn/frozen:1-20` screens unpooled
seeds (reported, never failing). About 30 s; writes
`build/reports/story-map-pools/summary.md`.

## Loadouts and machines

### `loadoutCardSheet`

The design-pass stat sheet for every loadout card, from the shipped data:
`build/reports/loadout-cards/cards.md` and `cards.csv`. Per card: access tier
and unlock gate, the twelve billets seat by seat, strength as
`LoadoutEffectiveness` measures it (effective DPS and FIRE rating, absorbed
damage and GUARD rating) with matchup columns, reach, specials, mobility,
systems and selling faction channels; then the armor-pattern and weapon-grade
reference tables. Ratings leave out cover, morale and orders.

### `loadoutMatchup`

Fights loadouts against each other headlessly: twelve regular marines a side,
kit as the cards issue it (`LoadoutCardIssue.seats`). Writes `matchups.md`,
`matchups.csv` and per-fight `runs.csv` under `build/reports/loadout-matchups/`.

- Scenarios (attackers from the west, defenders hold the east): `open-field`;
  `trench` (a twelve-cell trench manned with `defendRun`); `trench-vulcans`
  (the same with a Vulcan past each end, which must be destroyed too); and
  `building` (the production `MARKET_HALL` stamp, garrisoned, attackers given
  `secureCompound`). `-Pscenarios=open-field,trench` selects. A new scenario
  implements `MatchupScenario` and joins `MatchupScenarios.all()`; its walls have
  no art, so it overrides `annotations()` to box what it built.
- The default ladder fights every weapon card and every armor sheet against
  the starter issue, attacking and defending, at `-Pseeds=3`. Starter against
  starter is the control row, because the ground favours one side.
- `-Pattacker=` / `-Pdefender=` (`weapons:x+armor:y`) replace the ladder;
  several sides separated by `;` fight every pairing. Uncarded experiments:
  `kit:field-rifle@surplus,rocket-launcher*2,frag-grenade*6` and
  `mix:cordon-shell*10,packframe*2` (the `weapon.`, `special.` and `armor.`
  prefixes may be left off).
- `-PfireMission=true` gives the attackers a fire mission on the defenders'
  ground; without one a marching squad never sets a field mortar down.
- A fight ends at a wipe-out, at 1,200 ticks with no damage on either side
  (`STALEMATE`), or at the tick limit; read survivors. The sim seed changes hit
  and morale rolls only. Each run replays the control twice side by side first
  and reports determinism in the header. `-Pparallelism=N` (default half the
  processors).
- `-Pgifs=true` records each fight's first `-PgifSeeds=1` seeds as 1920x1080
  GIFs under `gifs/<scenario>/`, linked from the Replay column; on by default
  for up to four explicit pairings, off otherwise. About 20-30 s a recording.
  They are drawn through Java2D; `-PsnapshotBackend=gl` draws them on the GPU
  through the shipping renderer, which is the only way to see per-quad effects
  such as body lighting and wear.
- Controlled construction with a detached kit, not a campaign roster, and not
  balance acceptance.

### `armorMatchup`

Machines against machines, with `loadoutMatchup`'s conventions (`;` pairings,
`-Pseeds` default 2, `-Pscenarios`, `-Pparallelism`, `-Pgifs`, the control
replayed twice). Writes `armor-matchups.md`, `armor-matchups.csv` and
`runs.csv` under `build/reports/armor-matchups/`.

- A side is `unit(+unit)*`; a unit is `[Nx]` then a mech (`hound`,
  `bulwark:refit`,
  `bulwark:arms=linear-cannon,rs=shoulder-laser,engine=uprated,vents=auxiliary,role=assault`,
  pattern ids with or without their `component.mech-` / `internal.` prefix,
  where `arms` fits every arm mount, `la`/`ra` one Bulwark arm, `none` empties a
  mount, `refit` fills the best buildable internals, and an over-budget fit runs
  labelled) or a `VehicleType` in kebab case (`breakthrough-tank`, `technical`,
  `scout`, `dozer`).
- Scenarios: `open-ground`; `hull-down` (berms hide the defenders' hulls);
  `street` (defenders behind the last two corners).
- Attackers are moved as a player would: to a cell at about 70% of their
  longest reach that sees the nearest enemy (`-Pstandoff=0.7`); inside range,
  doctrine and turrets decide. `-PmechOrder=attack-move` restores the old
  attack-move. `-PdefenderFacing=90|180` turns defending hulls side-on or rear-on
  (`-Dbattle.vehicle.armorArcs=false` is its control). On open ground
  `-PattackerSpread=<degrees>` and `-PattackerRadius=<cells>` fan several
  attackers round the defender.
- The default ladder (`-Pladder=control,mech,mixed,vehicle`) is about 800
  fights in under a minute. Rows report wins, time to kill, machines and
  integrity left, hit rate, mounts lost, legs crippled, flux-limited seconds
  and damage by weapon. One machine a side with no cost parity: read rows
  against each other, not as balance acceptance.

## Campaign and crews

### `bloomEvidence`

Runs `RavBloomSystem` with the real event lifecycle over `-Pyears=20` cycles of
a synthetic vanilla-shaped sector at `-Pseeds=8`, for every severity and
frequency preset under three player policies, and writes blooms a year,
outcomes, spread and size lost to `build/reports/blooms/summary.md` /
`summary.json` (`-PevidenceDir`). About fifteen seconds.

### `crewEvidence`

Crews a transport and a capital from their room programs, runs each for four
minutes of ship's time, and reports what the complement spent it doing: the
idle share, the activity histogram, the longest idle stretch, the living and
which compartments hold anybody. The goal is an idle share of zero, with off
watch spent resting, socialising and exercising; the dead count as idle
(`CrewLivelinessEvidence`).

## Performance profiles

- `profileConquestTail`: a 30 Hz-paced Conquest tail through the production
  async-route and parallel-unit scheduler, 1200 ticks by default with the first
  300 untimed; writes `build/reports/performance/conquest-tail/summary.json`.
  Switches and reports: `conquest-tail-profile.md`.
- `profileConvoyRoute`: convoy route-proof cost on the full-size Conquest map on
  both traversal axes: first-dispatch snapshot time, proof-step times, search
  attempts and expansions, and cells derived, in
  `build/reports/performance/convoy-route/summary.json`
  (`-Dbattle.convoyRoute.outputDir=<path>`). Diagnostic timings, not a gate.
- `profileSwarmFlow`: swarm strategic-movement cost at 280x168, 400x240 and
  560x336 with 64 to 1000 runners, flow field on and off, then two replicas per
  lever side by side for determinism (fails if they differ).
  `build/reports/performance/swarm-flow/summary.md`. Narrow with
  `-Dbattle.swarmFlow.sizes=400x240`, `.counts=150,300`, `.levers=on`,
  `.spawnDistance=20`, `.measureTicks`, `.warmupTicks`, `.outputDir`. Readings
  and the decision they support: `swarm-movement-cost.md`.
