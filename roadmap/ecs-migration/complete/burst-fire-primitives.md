# Story: burstRemaining / burstTimer / burstTargetId SoA promotion

**Shipped `024344f`** (2026-05-28). The burst-fire continuation triple
promoted off the `Unit` POJO into UnitRegistry SoA arrays
(`int[] burstRemaining`, `float[] burstTimer`, `long[] burstTargetId` —
the registry's **second `long[]`** after `secondaryAimTargetId`) + final
accessors on Unit. **Seventh primitive-group promotion.**

## What landed vs. planned

- **As planned:** the three arrays + grow/seed/snapshot/tail-swap
  lifecycle; `Unit.local*` seed/snapshot fields + final accessors;
  `beginBurst` / `setBurstTarget` routed through the setters; the sole
  consumer `InfantryWeapons.tick` migrated to accessors. 9 new
  UnitRegistryTest cases (allocate-seed, release-snapshot, tail-swap ×3).
- **The shadowing question (flagged in the story) resolved cleanly:**
  `MapTurret` declares its own `burstRemaining`/`burstTimer`/`burstTargetId`
  and its burst-tick (`TurretBehavior`) reads/writes them via a
  MapTurret-typed reference — so they were never the same storage as
  Unit's. After the rename they simply stop shadowing a same-named field;
  the turret path is untouched. `MountedTurret` and `FlybyOverlay` burst
  state are separate non-Unit objects, also untouched. MapTurret's doc
  comment was updated to say "independent of" rather than "shadows."

## Consumer surface (small, as expected)

Only three Unit-burst touch points existed: `Unit.beginBurst`,
`Unit.setBurstTarget`, and `InfantryWeapons.tick`. Every GOAP-action
caller goes through `beginBurst`, so no behavior-class churn.

## Notes for the next promotion

`InfantryWeapons.tick` still iterates the legacy `units` list and routes
through the OO accessors rather than a dense `[0, liveCount())` sweep over
`burstRemainingArray()`. That dense-iter conversion is a separate consumer
optimization, deferred — the field→accessor swap (the promotion's required
part) is done.
