# Next session — picking up the migration

Read [`overview.md`](overview.md) first for design rules.
Shipped work is in [`complete/`](complete/).

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
9ff4dae  battle: SquadAlertSystem — fifth SoA consumer migration
a4df09b  battle: SoA cooldownTimer — third primitive promotion
489b1db  battle: SoA moveProgress + renderX/renderY — fourth promotion
c929087  battle: SoA attackRange/attackDamage/accuracy — fifth promotion  ← 2026-05-27
```

## State of play

- **Nine primitives promoted:** hp/maxHp, cellX/cellY, cooldownTimer,
  moveProgress, renderX/renderY, attackDamage, attackRange, accuracy.
- **Five consumers** on dense-iter + SoA array reads.
- **Build green; all tests pass.**

## Active stories (priority order)

1. ~~[`move-render-primitives`](stories/move-render-primitives.md)~~ —
   **SHIPPED** (`489b1db`). Moved to [`complete/`](complete/move-render-primitives.md).

2. ~~[`tactical-primitives`](stories/tactical-primitives.md)~~ —
   **SHIPPED** (`c929087`). Logged in [`complete/`](complete/phase3-soa-promotions.md).

3. **[`secondary-weapon-primitives`](stories/secondary-weapon-primitives.md)** —
   secondaryAimTargetId/secondaryActionTimer/secondaryCooldownTimer. Tightly
   scoped to ~8 files. Next up if continuing SoA promotions.

## Sanity check before resuming

- `gradlew.bat compileJava` should be clean.
- All tests pass.
- `git log --oneline -5` should show `c929087` or your own recent work
  at the top.
