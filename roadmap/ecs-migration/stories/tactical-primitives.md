# Story: attackRange / attackDamage / accuracy SoA promotion

## Context

Per-unit combat stats baked at construction, modified rarely (captain
traits, loadout). Single-write/many-read pattern — hot in the
weapon-fire critical path and TacticalScoring's bulk loops.

~41 files reference these. Lower per-tick churn than the movement
group, but high read density in hot loops that already iterate the
dense array (TacticalScoring is a migrated consumer).

## Scope

### Primitive promotion

- `float[] attackRange`, `float[] attackDamage`, `float[] accuracy`
  on UnitRegistry.
- Local fields + final accessors on Unit.
- 9 new UnitRegistryTest tests (3 per primitive).

### Consumer migration

- **TacticalScoring** — already iterates dense array; currently reads
  these via `dense[i].attackRange` etc. Switch to SoA array reads.
- **Weapon fire paths** (`FireStance`, `MarineWeapon`, `HeavyWeapons`,
  `TurretAim`) — reads during fire resolution.
- **AI action classes** — occasional reads for range checks.
- **DroneHubUnit** subclass — seeds different values than base Unit at
  construction. Verify `localAttackRange` etc. seed correctly through
  allocate.

## Priority

Lower than move-render primitives. These don't churn per tick, so the
SoA cache-friendliness win is smaller. Natural follow-up once the
movement group lands.
