# Story: secondaryAimTargetId / secondaryActionTimer / secondaryCooldownTimer

**Shipped `01fe905`** (2026-05-28). Two `float[]` + one `long[]`
(`secondaryAimTargetId` — first long[] primitive in the registry) +
final accessors on UnitRegistry; consumers migrated from direct field
reads (InfantryUnitPrep, EngagePosture, TacticalScoring squad-aim scan,
BattleScreen). 9 new UnitRegistryTest tests. Rollup in
[`phase3-soa-promotions.md`](phase3-soa-promotions.md).

## Context

Paired fields for the secondary weapon subsystem (rocket volleys).
`shouldCommitRocket`'s squad-aim-window scan iterates these on every
member of the shooter's squad — hot path during contested turret
pushes with multi-rocketeer squads.

~32 references across ~8 files. Tightly scoped to the secondary
weapon subsystem.

## Scope

### Primitive promotion

- `long[] secondaryAimTargetId` — entity-id array (like targetId, but
  this one is already `long` on Unit).
- `float[] secondaryActionTimer`, `float[] secondaryCooldownTimer`
  on UnitRegistry.
- Local fields + final accessors on Unit.
- 9 new UnitRegistryTest tests (3 per primitive).

### Consumer migration

- **`InfantryUnitPrep`** — primary writer (initialization, ticking).
- **`EngagePosture`** — secondary firing logic.
- **`TacticalScoring`** — reads during aim-window evaluation.
- **`BattleScreen`** — UI reads for secondary weapon display.

## Priority

Lowest of the three story groups. Small reference surface, isolated
subsystem. Do after tactical primitives unless profiling flags the
squad-aim scan as hot.
