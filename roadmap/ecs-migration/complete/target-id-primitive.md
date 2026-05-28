# Story: targetId SoA promotion (keystone cross-reference)

**Shipped `7ae84e6`** (2026-05-28). The current-target entity id promoted
off the `Unit` POJO into a UnitRegistry `long[]` (the registry's **third
`long[]`** after `secondaryAimTargetId` and `burstTargetId`) + final
accessors. **Eighth primitive-group promotion** and the most-read
per-unit cross-reference.

## What landed vs. planned

- **As planned:** `long[] targetId` with grow/seed/snapshot/tail-swap;
  `Unit.localTargetId` + final `getTargetId()` / `setTargetId(long)`;
  `setTarget(Unit)` routes through the setter; `targetOf()` stays a
  delegate over `getOrNull(getTargetId())`. 3 new UnitRegistryTest cases.
- **~17 consumer sites** migrated field→accessor across AI/GOAP actions,
  damage (`DamageService` reprio race-check, `HitResponseService`),
  squad (`SquadAlertSystem.clearSquadTargets`), and sim
  (`writeReprioInline`, `targetOf`). The mechanical sweep was fanned out
  to a Sonnet subagent; the registry/accessor design + tests stayed on
  the main thread.
- **Non-Unit look-alikes left untouched:** `MountedTurret.targetId` (its
  own field) and the three `AirSystem` `mt.targetId` sites — verified by
  grep after the sweep.

## Note

The full suite had one **unrelated** pre-existing failure at commit time
— `BspMapPreviewTest.renderConquestBatch` (a conquest map-gen walkability
flood, one unreachable cell on seed 100), caused by sibling mapgen
commits (`42b384b` fortress-wall / `56a0407` compound placement). Zero
mapgen files are in this changeset; `targetId` is combat-runtime state
with no path to BSP flood-fill. All targetId-touched tests pass.
