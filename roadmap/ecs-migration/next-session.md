# Next session — picking up the migration

Where today's session (2026-05-27) left off. Read the
[parent README](README.md) first for the locked-in design rules.

## Commit chain so far

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
ef4d798  battle: TacticalScoring bulk loops — fourth SoA consumer
9ff4dae  battle: SquadAlertSystem — fifth SoA consumer migration       ← 2026-05-27
a4df09b  battle: SoA cooldownTimer — third primitive promotion          ← 2026-05-27
```

Critique-agent passes on both `9ff4dae` and `a4df09b` came back SHIP,
no chasers.

## State of play

- **Three primitives promoted:** hp/maxHp, cellX/cellY, cooldownTimer.
  cooldownTimer is the first "decrementer-style" per-tick float;
  establishes the pattern for future timer promotions.
- **Five concrete consumers** demonstrate the migration shape:
  SquadMoraleSystem, spatial-index rebuild, DamageResolver,
  TacticalScoring, and SquadAlertSystem (hottest per-tick file).
- **Build green; all tests pass.**

## Next-slice candidates

### 1. Next primitive — `moveProgress` (movement interpolation)
- Single float, ticked every frame in `Unit.advanceAlongPath`.
- Small reader surface (renderer reads it for inter-cell lerp).
- Same decrementer-style pattern as cooldownTimer.

### 2. `renderX` / `renderY` (renderer hot path)
- Paired floats written by movement, read by BattleScreen's draw loops.
- Same parallel-array layout as cellX/cellY.
- Natural batch with moveProgress since they're updated together in
  `advanceAlongPath`.

### 3. Tactical primitives — `attackRange` / `attackDamage` / `accuracy`
Per-unit stats baked at construction, modified rarely (captain traits,
loadout). Single-write/many-read. Lower priority since they don't
churn per tick, but natural batch for SoA consumer hot loops in
TacticalScoring that already read them.

### 4. `secondaryAimTargetId` / `secondaryActionTimer` — paired
`shouldCommitRocket`'s squad-aim-window scan iterates these on every
member of the shooter's squad. Hot path during contested turret pushes
with multi-rocketeer squads.

## Sanity check before resuming

- `gradlew.bat compileJava` should be clean.
- All tests pass.
- `git log --oneline -5` should show `a4df09b` or your own recent work
  at the top.
