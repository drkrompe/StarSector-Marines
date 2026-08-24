# Backlog

Known future work, grouped by area. Loose priorities; the README's
"Immediate next-up" is what's actually queued.

## Music
https://davidkbd.itch.io/eternity-metal-scfi-music-pack

## Gameplay

- **Briefing screen** — mission detail view with accept/decline. Reads
  selected mission from `MarineOpsContext`.
- **Mission resolution** — consume marines (from cargo and/or captain's
  squad), apply trait bonuses, award XP, roll for injury/death on bad
  outcomes. Hooks into `MarineRosterScript`.
- ~~**Captain discovery system**~~ — **SHIPPED**: cryo-pod recovery from
  salvageable derelicts is the canonical in-universe acquisition path, including
  persistent intake, vanilla salvage publication, interaction resolution, and
  deferred Personnel review (`055abb97`, `d780e345`, `7a32e771`).
- **Roster cap scaling** — replace hardcoded 10 with `f(playerLevel)`.
- ~~**Trait mechanics**~~ — **absorbed into the progression track** as
  `s10-trait-mechanics.md`,
  which also covers the missing trait UI and the level-up acquisition path
  the `Trait` Javadoc already promises. Six of eleven traits are still
  inert enums.
- **Captain rank promotion** — XP threshold, ranks unlock larger squad
  capacities (already encoded in `Rank` enum).
- **Injury recovery** — periodic tick in `MarineRosterScript.advance` to
  return injured captains to ACTIVE after some days.
- **Faction-enemy covert ops** — high-rep with a faction unlocks
  "deniable contracts against their enemies" as a mission category in the
  current client's list. Not a separate client row.
- **Mission gating by reputation** — locked client rows already exist;
  extend to per-mission gating (e.g., high-risk only at WELCOMING+).
- **Fold flyby fighters into Air** — fighters already fly on `AirBody` with
  hull-derived handling, but the legacy flyby layer still owns their private
  roster, lifecycle, firing, and rendering. `fighter-air-entities.md` owns the
  remaining move into the shared world/entity lifecycle; survivability,
  wing-level composition, and modeled air fire remain later stories.

## Bugs

- **UI ignores `getScreenScaleMult()`** — the mod never calls it. Our UI
  ortho spans `getScreenWidth()`/`getScreenHeight()`, which the API
  documents as *virtual* pixels (already divided by the scale mult), so
  every font-size and layout judgement the project has made was made at one
  unstated UI scale — Orbitron 20 is 30 physical px at 1.5x and 20 at 1.0x.
  Affects every screen, not just one. `BattleScreen`,
  `GroundParallaxPipeline`, and `BridgeRenderer` each derive the same
  quantity by hand as `Display.getWidth() / getScreenWidth()`; consolidate
  rather than adding a fourth. Scoped as
  `s8-roster-legibility.md`
  Slice 0, but worth landing independently.
- ~~**`HoldPost` double-ticks the attack cooldown**~~ — **FIXED `b418d835`
  (2026-07-01, FiringSystem sweep)**, along with two more instances the epic's
  audit found (`GuardPostPatrol.engage`, `PatrolMotion.fireIfAble`). Direction
  correction to the original entry: the double drain emptied the cooldown in
  half the time, so those units fired ~2× too **fast** (not too slowly).
  Post-fix cadence drops to the intended `attackCooldown` spacing — verify
  garrison fire feel in playtest (the game may have been implicitly balanced
  around the bug; if garrisons feel too soft, tune `attackCooldown` data, don't
  resurrect the double-tick). Record:
  `ecs-nouns.md` and its shipped ledger.
## UI

- **Briefing screen** (also gameplay item above).
- **Mission resolution screen** — outcome readout, casualty list, XP +
  loot.
- **Scroll bar widget** — visual companion to `ScrollRegionWidget`
  scroll-wheel handling. Track + thumb visualizing how much content is
  above/below + position in the scroll range; click-track-to-jump,
  draggable thumb. Replaces / augments the `▲ scroll up` / `▼ N more`
  text hints in `CommsConsolePanel`'s dossier stack. Most useful for
  long lists (debug client's 15 missions); vanilla clients rarely hit
  scroll. Generic enough to reuse for any scrollable list later.
- **Captain roster screen** — the eventual 3D bridge view, accessed from
  the intel screen. Replaces the cube placeholder.
- **Text wrapping in `BitmapFont`** — single-line method exists; add
  `drawStringWrapped` returning consumed height. Briefing screen will
  force this.
- **Tooltip widget** — generic hover popup primitive. Mission popup is
  the prototype; generalize when a second consumer appears.
- **Scrolling for the client list** — currently fits 4–6 rows; if a
  planet ends up with many factions present, we need scroll.
- **Faction-themed UI colors per selected client** — column borders /
  accents pick up the client's faction color for a "you're in their
  context now" feel.
- **Political world map (mission-select)** — the mission-select tactical
  map is currently random dots that reflect no vanilla world data. The
  aspiration is a real political map: per-planet faction holdings,
  contested zones, faction-color regions, anchored to Starsector campaign
  state. Until that tech exists, the list-view stays the centerpiece —
  treat the map as a *thumbnail / spatial anchor*, keep all load-bearing
  data on the cards/list (never "see the map for context"), and keep the
  layout decomposable so a future political-map upgrade is an additive
  promotion, not a redesign.

## Asset pipeline

- **Real asset import end-to-end** — drop a test FBX/glTF into
  `asset-pipeline/src/tool/resources/`, run
  `gradlew :asset-pipeline:processModels`, load resulting `.mlmodel` at
  runtime, render via a new `MeshDrawable`. Proves the pipeline beyond
  the round-trip test.
- **Skinned animation runtime** — `Animation` / `Skeleton` /
  `AnimationRetargeter` are already in the runtime source set; wire to
  a `SkinnedMeshDrawable` once a test asset exists.

## Polish

- **Pole warping on planet sphere** — fragment-shader fade-to-tint near
  poles to hide the equirectangular squish. Costs one shader change.
- **Texture-aware mission placement** — read pixel color on the planet
  texture at the candidate position, reject if it's water/ice. Long-term
  ask from playtest.
- **`BridgeRenderer` debug scaffolding cleanup** — `DEBUG_QUADRANTS` and
  `DEBUG_DIRECT_QUADRANTS` flags can go now that the foundation is solid.

## Architecture / refactor candidates

- ~~**Deprecate the LIGHTING layer / `WeaponLights`**~~ ✅ **DONE (2026-06-29).** The pseudo
  time-of-day lightmap is gone: deleted `LightAccumulator`, `Light`, `LightKernel`,
  `WeaponLights`, `TimeOfDay`; stripped `EngineFxRenderer.emitLights` (the plume `draw` stays),
  the `LIGHTING` `RenderLayer`, `BattleRenderer`'s accumulator field/pass/getter, `BattleScreen`'s
  light-pass drive, and `FlybyOverlay`'s `pumpEngineLights` + fighter light calls. Full project
  build green. What survived (it was never the lightmap): the muzzle-flash / impact / smoke
  **particles** (`ImpactFx`, the daylight burst pop), engine **plumes** (`EngineFxRenderer.draw`),
  LoS shadowing, and **DECALS**. The system only ever rendered in DUSK/NIGHT, and the game was
  hard-coded to DAY (bypass), so removal is visually a no-op. The bridge's remaining persistent
  render-target gap is now **DECALS alone**. Recoverable from git history if night battles ever return.
- **Share the combat FX/sound driver** — `BattleScreen.advance` (standalone) and
  `combathybrid/.../GroundSimPresentation` (bridge) both dispatch per-weapon
  impact-FX + fire/impact sounds off the sim's per-frame `ShotEvent` lists, in
  different audio frames (`cell×30` abstract vs combat-world). The weapon→sound /
  weapon→`ImpactProfile` dispatch is duplicated; extract a frame-parameterized
  presenter both call when this next drifts (a new weapon added to one and not
  the other is the failure mode to watch). [[feedback_followup_tasks]]
- **Screen abstraction** — pull when the second screen (briefing) actually
  needs to transition. One screen is a guess; two informs the interface.
- **Per-panel `WidgetRoot`** — current plugin rebuilds the entire widget
  tree on selection change (`ClientRowWidget` hover state flickers for
  one frame). Per-panel subtrees would let the tactical map rebuild in
  isolation.
- **`BridgeRenderer` naming** — it's a generic FBO scene renderer at
  this point, not bridge-specific. Rename when it gets a third consumer.
- **`PlanetIntelPanel` `nameRowH` duplication** — same calc in
  `buildIntelBlock` and `onRender`; extract.
- **`LabelWidget` variable-height** — for wrapped text. Tied to text
  wrapping in `BitmapFont`.
- **Mission name generation through i18n** — currently hardcoded English
  templates in `MissionGenerator`. Move to strings.json with template
  formatting.
- **`DistrictTheme` / `MapDistrictTheme` dedup** — two theme types across
  `battle/world/model` and `battle/world/gen`; a logic merge, not a
  relocation, so it was left out of the battle-reorg (slice 2).
- **Relocate `BattleSimulation.TICK_DT`** — the fixed-timestep constant
  (`1f/30f`) lives on `BattleSimulation` and is referenced in ~17 files
  across combat / decision / drone / infantry / mech / turret / command.
  After the GOAP + command tiers narrowed to `BattleView`/`BattleControl`,
  this static-constant reference is the *only* thing keeping a
  `BattleSimulation` import alive in several otherwise-decoupled files
  (e.g. `ChargeSiteObjective`, `HoldPost`, `DroneSwarmAction`) — they'd
  compile against a stub `BattleView` if not for it. Move `TICK_DT` to a
  neutral home (a `BattleConstants`/`Timestep` holder, or a `static final`
  on `BattleView`) and repoint all callers. Pure tidiness — a shared
  literal, not behavioral coupling — and the last thread of the
  `ecs-nouns.md` rule that `BattleSimulation` is the tick coordinator rather
  than a general dependency. Surfaced by the original facade-narrowing audit.

## Performance

Raw findings from the **2026-05-21** JFR capture
(`IdeaSnapshots/StarfarerLauncher_2026_05_21_111442.jfr`, 31s @ ~400 units,
1799 CPU samples). 30% of samples hit our package; of those, **67% render
path, 33% sim path.** Records the *raw insights* — refactor work tracked
as separate entries below.

### Render path (the bigger share — 367 samples)

- **`QuadBatch.flush`** — was 78% of render / 17.4% of total mod CPU. ✅ **FIXED
  & VERIFIED (2026-06-01)** — see `battle-render-nouns.md` and its shipped ledger.
  Confirmed across 3 captures it was the per-vertex immediate-mode submission
  (12 JNI calls/quad), *not* float-packing (`append` <1%) or GL-submit-stall or
  flush-thrash (the drain already coalesces). Fix: both `QuadBatch.flush` +
  `SolidQuadBatch.flush` → client-side vertex arrays + `glDrawArrays` (not a VBO
  — marginal here). **Result: −75% combined flush CPU** (722→178 samples;
  SolidQuadBatch 193→1; render path 37%→18% of our CPU). Residual `QuadBatch`
  cost is the GROUND batch's per-frame memcpy + draw submission → any future
  bake/residency work is tracked by `dense-render-tiles.md`.
  The candidates below are superseded.
- **Roots:** 233 samples cascade from `BattleScreen.renderGrid`, 51 from
  `MarineOpsPanelPlugin.render`. Floor + wall tiled passes are the
  biggest single sub-pass (25 samples to
  `renderTiledFloorsAndWalls` direct, more through `renderGrid`).

**Lever candidates (render-only, separate from sim refactor):**

- **Audit `QuadBatch.flush` callers** — find batches that flush more
  often than they batch. Likely culprits: per-sprite flushes in any FX
  layer that doesn't pre-sort by texture.
- **Batch-by-texture audit on the tile passes** — verify ordering in
  `renderTiledFloorsAndWalls` actually keeps one bind per sheet. The
  [[render2d_batching]] memory documents the intent; check it still
  holds.
- **Profile `QuadBatch.flush` body itself** — confirm whether the time
  is in buffer packing (CPU) vs the GL submit (driver). Different fix.

### Sim path (the refactor target — 176 samples)

- **`HoldPost` + `findFiringPosition*` chain** is the fattest sim path:
  58 samples (33% of sim CPU) cascade through here. Garrison squads
  scoring candidate cells every tick when ENGAGED.
- **`TacticalScoring.alliesNearForSpread`** is the single hottest
  *leaf* — 26 samples at lines 1301-1302 (the Pass-2 full-unit walk).
  Already partly indexed; the dest-cell pass is the residual O(N).
- **`NavigationGrid.hasLineOfSight`** — 26 samples across 4 source
  lines. Bresenham loop body. Hard to optimize directly; lever is
  calling it less often by batching / caching candidate-cell scoring.
- **GOAP action execution combined** — ~14% of sim CPU across
  HoldPost / ClearZone / ApproachPosture / EnterZone / EngagePosture.
  Healthy distribution; no single action dominates outside HoldPost.

**Lever candidates (sim, ranked by payoff vs effort):**

- **Destination spatial index** — `TacticalScoring.alliesNearForSpread`
  Pass 2 walks `sim.getUnits()` checking each unit's *path destination*.
  Build a second `UnitSpatialIndex` keyed on dest cells at tick start;
  Pass 2 becomes another `gather()`. Estimated **10-15% sim CPU drop**,
  one day of work, no semantic change.
- **Memoize `alliesNearForSpread` within one `findFiringPosition*`
  call** — adjacent candidate cells share neighborhoods. Smaller win,
  smaller change. Combine with above for stack savings.
- **`updateUnit` read/write split** (already on the docket from the
  parallelization audit) — confirmed by both per-phase profile (85% of
  tick) and JFR (33% of sim CPU lands inside it). The data-oriented
  refactor lands here.

### Methodology notes for future captures

- The `jfr` tool ships with the JDK (`$JAVA_HOME/bin/jfr.exe`).
  `jfr print --json --events jdk.ExecutionSample <file>` dumps stack
  traces in machine-readable form. Aggregate by deepest our-package
  frame (LEAF) for "where is the CPU" and by highest our-package frame
  (TOPMOST) for "what call kicked the work off."
- Class names in JFR JSON use `/` separators
  (`com/dillon/starsectormarines`). Filter accordingly.
- Render vs sim split: anything reachable from `BattleScreen.render*`,
  `QuadBatch.flush`, `MarineOpsPanelPlugin.render` is render path; the
  rest is sim. ~67/33 split was consistent across the capture.
- IntelliJ's source-jump may not bind to the deployed jar's frames.
  Right-click in the profiler result → "Attach Sources" pointing at
  `src/main/java` to wire it up.

## Known flaky test

- **`SquadLeadershipTest.anUndermannedSquadStillLeadsTheTeamsItHas` fails
  about one run in five.** Found in passing during progression S3, 2026-08-22;
  not caused by it, and reproducible on `main`. `MarineRoster.SENIORITY`
  breaks ties on `MarineSoldier::id`, and ids are `UUID.randomUUID()` — so
  among five equal-rank, zero-XP recruits the squad leader is effectively
  chosen at random, while the test asserts the leader sits in team 0. A probe
  constructing the roster 300 times measured 62 failures.

  Not fixed here because it is a design question, not a typo: either
  `refreshLeadership` should reorder the squad so the leader occupies roster
  position 0, or the tiebreak should be roster position rather than a random
  id, or the test should stop asserting the leader's team index. The
  company-view track owns squad leadership (C7) and should pick.

## Constructor sprawl

Both offenders are fixed; kept here as the shape to reach for next time.

- **`MissionOutcome`** — six constructors, thirty-six positional parameters,
  seven call sites. Replaced by `MissionOutcome.builder()` on 2026-08-22.
- **`Mission`** — six constructors, twenty-eight positional parameters,
  twenty-one call sites. Replaced by `Mission.builder()` the same day, plus
  `Mission.builder(Mission)` for copy-with-changes.

The pattern in both: every new frozen field arrives as one more overload
delegating inward, and the older overloads keep passing whatever sentinel
meant "absent" at the time. Call sites decay into runs of bare literals, and
a caller picking the wrong overload silently gets defaults it never asked
for. Make builder defaults the absent-values the class already normalizes to,
and the overloads stop being necessary at all.

`MissionBuilderTest` pins the defaults and asserts `builder(Mission)` copies
every field — that second test is the guard against a new field being added
to the class and not to the copy path.

## Translation / community

- **i18n coverage audit** — all user-facing strings should already route
  through `Strings.get(...)`. Periodically sweep for hardcoded English.
- **Translation mod template** — eventually ship a `strings-template.json`
  with comments explaining the override pattern.
