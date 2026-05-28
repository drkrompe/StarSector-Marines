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
- **Captain discovery system** — cryo-pod recovery from salvageable
  derelicts as the canonical in-universe acquisition path. Hook into
  vanilla salvage events.
- **Roster cap scaling** — replace hardcoded 10 with `f(playerLevel)`.
- **Trait mechanics** — currently placeholder enums (`SIEGE_SPECIALIST`,
  `SAPPER`, etc). Wire to mission resolution modifiers.
- **Captain rank promotion** — XP threshold, ranks unlock larger squad
  capacities (already encoded in `Rank` enum).
- **Injury recovery** — periodic tick in `MarineRosterScript.advance` to
  return injured captains to ACTIVE after some days.
- **Faction-enemy covert ops** — high-rep with a faction unlocks
  "deniable contracts against their enemies" as a mission category in the
  current client's list. Not a separate client row.
- **Mission gating by reputation** — locked client rows already exist;
  extend to per-mission gating (e.g., high-risk only at WELCOMING+).
- **Flyby fighters as real air entities** — flyby fighters are today a
  scripted cosmetic overlay (`battle/flyby/`: heading-based weave +
  strafing runs, a single sim coupling via
  `BattleSimulation#applyExternalDamage`). Long-term, rebuild them on the
  `AirBody` abstraction they'd share with shuttles (`battle/air/`) so they
  become genuine flying entities — spawn in/off the map edges, optionally
  land at bases on larger maps, take fire and be shot down. When that
  refactor lands, fold `flyby/` into `air/`. The package move was
  deliberately *not* done in battle-reorg slice 10: flyby shares zero code
  with `AirBody` today, so relocating it now would front-run the data model
  — the same call made for the deferred `unit/`→`entity/` rename. Relocate
  when it has *become* an air entity, not before.

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
- **Stale `WeaponSimContext` javadoc link** — `battle/combat/fx/EffectsService.java`
  carries a `{@link ...battle.weapons.WeaponSimContext}` doc ref to a class
  (and package) that no longer exists. Delete or repoint when `EffectsService`
  is next touched. (Loose end from the completed battle-reorg, slice 7.)
- **`DistrictTheme` / `MapDistrictTheme` dedup** — two theme types across
  `battle/world/model` and `battle/world/gen`; a logic merge, not a
  relocation, so it was left out of the battle-reorg (slice 2).

## Performance

Raw findings from the **2026-05-21** JFR capture
(`IdeaSnapshots/StarfarerLauncher_2026_05_21_111442.jfr`, 31s @ ~400 units,
1799 CPU samples). 30% of samples hit our package; of those, **67% render
path, 33% sim path.** Records the *raw insights* — refactor work tracked
as separate entries below.

### Render path (the bigger share — 367 samples)

- **`QuadBatch.flush` dominates** — 285 samples (78% of render). Reason
  unconfirmed: could be too many small batches not amortizing GL state
  changes, driver-side stalls being attributed to flush, or CPU-bound
  float-buffer packing in the flush body itself.
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

## Translation / community

- **i18n coverage audit** — all user-facing strings should already route
  through `Strings.get(...)`. Periodically sweep for hardcoded English.
- **Translation mod template** — eventually ship a `strings-template.json`
  with comments explaining the override pattern.
