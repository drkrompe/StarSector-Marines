# Story: burst-fire triple SoA promotion

## Context

The burst-continuation group — `burstRemaining` (int), `burstTimer`
(float), `burstTargetId` (long) — is ticked **every tick across every
unit** in `InfantryWeapons.tick()` (the burst drain pass that fires
queued rounds at `MarineWeapon.burstSpacing` intervals).

This is structurally identical to the secondary-weapon group shipped in
`01fe905` (float cooldown + float action timer + long aim-target id).
Same three array types, same accessor shape, same lifecycle — the
cleanest "do it again" story in the backlog.

Writes are already funneled through `Unit.beginBurst(Unit)` and
`Unit.setBurstTarget(Unit)`; the only per-tick mutator is
`InfantryWeapons.tick`.

## Scope

### Primitive promotion (UnitRegistry + Unit)

- `int[] burstRemaining`, `float[] burstTimer`, `long[] burstTargetId`
  on UnitRegistry — grow/swap/snapshot lifecycle like the hp template.
- `Unit.localBurstRemaining` / `localBurstTimer` / `localBurstTargetId`
  transient seed/snapshot fields.
- Final accessors on Unit. `beginBurst` and `setBurstTarget(Unit)` keep
  their signatures and route through the setters.
- Three tests per primitive = 9 new UnitRegistryTest tests.

### Consumer migration

- **`InfantryWeapons.tick`** — primary mutator (decrement timer, fire,
  decrement remaining, clear on exhaustion). Migrate to the accessors;
  if it iterates the registry dense view, read the arrays directly.
- **`Unit.beginBurst` / `setBurstTarget`** — write path, route through
  setters.
- GOAP actions that call `beginBurst(...)` go through the helper — no
  field touch, no change.

### Verify during implementation

- **`MapTurret` references `burstTargetId`** (`turret/MapTurret.java`).
  MapTurret is a `Unit` subclass, so confirm whether it inherits the
  promoted field or declares its own turret-burst field that shadows it.
  The turret burst path (`TurretFireService`) may want its own storage
  separate from the infantry burst group — resolve before promoting, so
  the accessor doesn't silently alias two distinct burst states.

## Acceptance

- `gradlew.bat compileJava` clean; all tests pass.
- Nine new UnitRegistryTest tests (3 per primitive).
- No direct field reads of `burstRemaining` / `burstTimer` /
  `burstTargetId` remain outside Unit's `local*` fields and the
  registry's allocate/release.

## Priority

Best warm-up — exact structural repeat of the just-shipped
secondary-weapon story. Good first slice of this batch.
