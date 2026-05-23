# Next session — picking up the migration

Where today's session (2026-05-22) left off. Read the
[parent README](README.md) first for the locked-in design rules.

## Today's commit chain

```
2afee3d  battle: Phase 2d — MountedTurret refs → long entity ids
e7a97fc  battle: TestUnits.kill helper + sweep raw hp=0 test writes
fffd973  battle: drop redundant isAlive() follow-ups on registry-resolve sites
1f26de4  battle: critique chaser — three more isAlive() drops in the aim path
7972009  battle: Phase 3 — hp/maxHp SoA promotion
53ee895  battle: Phase 3 critique polish — final accessors + xstream caveat
e78bd25  battle: SquadMoraleSystem — first SoA consumer
a78d417  battle: SoA cellX/cellY — Unit logical position into int[] arrays
9787bd9  battle: critique polish for cellX/cellY SoA lift
4edb1f4  battle: UnitSpatialIndex + DestIndex rebuild — second SoA consumer
d2a1cbd  battle: DamageResolver.pickPromotionCandidate — third SoA consumer
```

Each non-trivial slice went through a critique-agent pass. All landed
with ship verdicts; chaser commits captured the polish items the
critique surfaced.

## State of play

- **Storage seam is proven.** Two primitives (hp/maxHp, cellX/cellY)
  live in SoA arrays on `UnitRegistry`. Pattern documented in the
  parent README's "Design rules locked in" section.
- **Three concrete SoA consumers** demonstrate the migration shape
  end-to-end (SquadMoraleSystem, spatial-index rebuild,
  pickPromotionCandidate).
- **Unit object now holds zero live Unit refs for cross-tick targeting
  state.** All target slots route through long-keyed registry resolves.
- **Build green; all non-mapgen tests pass.** The
  `BuildingShellCoreLabelTest.partitionedCarveStampsBothLabels` failure
  observed during testing is a sibling-session flaky seed-based mapgen
  test ("MULTI_ROOM_CHANCE seems broken") — unrelated to this
  migration.

## Next-slice candidates, ranked

### 1. TacticalScoring bulk loops (highest tick-cost win)
`TacticalScoring` is the densest reader of cellX/cellY in the
codebase. `findBestTarget`, `alliesNearForSpread`, exposure scoring,
crowdScore — all walk units to compute distance-based things. The
`cellDistance` helper itself stays static (takes ints), but the
callers can iterate dense + read int[] cellX/cellY directly.

Several functions in there iterate the units list with nested
spatial-index gather calls. Pick the two or three hottest, migrate
to the established consumer pattern. Probably the biggest cumulative
per-tick win still on the table.

### 2. SquadAlertSystem.tick (multi-loop hot path)
Per-tick alert update has multiple nested loops over units:

- Centroid sum + kill-zone LOS scan (O(N²) for garrison squads).
- Audible-gunfire scan (units × active shots).
- Sustained-fire scan (units × active shots).

Each is a bulk iter that fits the SoA consumer shape. Likely the
single hottest file by tick cost — measure before committing scope.

### 3. Next primitive — `cooldownTimer` (next-cleanest float candidate)
- Single field, simple consumer (`InfantryUnitPrep.tickCooldowns`
  decrements every tick on every alive unit).
- ~10 readers across the codebase, small surface.
- Establishes the "decrementer-style" SoA pattern that future
  per-tick floats (e.g., `moveProgress`) follow.

After cooldownTimer, the next natural candidates are
`moveProgress` (movement interp) and `renderX/renderY` (renderer
hot path). Both small surfaces.

### 4. Tactical primitives — `attackRange` / `attackDamage` / `accuracy`
Per-unit stats baked in at construction, modified rarely (captain
traits, loadout). Single-write/many-read pattern. Lower priority since
they don't churn per tick, but they're a natural batch.

## Things to keep in mind

### Critique-agent cadence
The
[`feedback_critique_pass`](../../../.claude/projects/.../memory) memory
calls for a background critique agent after each non-trivial slice
commit. This session ran it after each of: 2d, hp/maxHp lift,
cellX/cellY lift. Several real follow-ups surfaced (missing parity
tests, accessor naming, missed sweep sites). Keep doing this.

### Sibling-session noise
Multiple sessions sometimes touch overlapping files. Today the
mapgen / fill packages had unrelated work landing in parallel.
Strategy:
- `git diff --numstat | awk '$1+$2 > 0'` to filter actual content
  diffs from CRLF marker noise.
- Stage only files touched intentionally for the task; don't blanket
  `git add -A`.
- Pre-existing test failures (like `BuildingShellCoreLabelTest` today)
  may come from sibling sessions — verify by checking whether the
  file is in your diff before debugging.

### Bulk-sed strategy for wide sweeps
The cellX/cellY migration touched 62 files. Bulk sed for known
Unit-typed variable prefixes (`member.`, `target.`, `shooter.`,
`self.`, etc.) is the right tool, BUT:
- False positives on non-Unit classes named the same (Doodad's
  `d.cellX`, RoadGraph.Node's `n.cellX`, SmokingWreck's `w.cellX`,
  CellHighlight's `h.cellX`, GuardPost's `p.cellX`). Compile errors
  catch these; per-file `sed -i` revert restores the field access.
- Method-vs-field collisions: `cs.cellX()` (ChargeSiteObjective
  method) sed'd to `cs.getCellX()()` (double call). Manual fix.
- Test files with the same variable names need the same sweep.

The migration was tractable; ~500 callsites mechanical. For the next
big primitive (probably positions/render), reuse the same sed approach
but pre-list the false-positive-prone variable names to exclude.

## Spatial-index notes worth not losing

The user pointed at MoonLight engine's `LinkedSpatialGrid` as the
right shape for our spatial index if/when we revisit it. Design
captured in [`spatial-index-options.md`](spatial-index-options.md).
Not blocking; revisit when N grows or `gather()` shows up in
profiling.

## Sanity check before resuming

- `gradlew.bat compileJava` should be clean.
- All non-mapgen tests pass.
- `git log --oneline -5` should show `d2a1cbd` at the top (or your own
  recent work if you've been at it).
