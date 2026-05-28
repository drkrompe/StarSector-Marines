# Story: AI countdown-timer SoA promotion

**Shipped in two slices** — Slice A `b620e77` (`repositionCooldown`),
Slice B `9104c85` (the fall-back group + `wanderDwellTimer`), both
2026-05-28. The last cluster of per-tick AI countdown/cache fields lifted
off the `Unit` POJO into UnitRegistry SoA arrays. **Ninth + tenth
primitive-group promotions.**

## What landed vs. planned

### Slice A — `repositionCooldown` (`b620e77`, ninth)

- `float[] repositionCooldown` with grow/seed/snapshot/tail-swap;
  `Unit.localRepositionCooldown` + final `getRepositionCooldown()` /
  `setRepositionCooldown()`. Rides the existing `tickCooldowns` drain in
  `InfantryUnitPrep` directly below the SoA `cooldownTimer` /
  `secondaryCooldownTimer` decrements.
- Consumers migrated: `RepositionToCover`, `WorldStateBuilder`,
  `EngagePosture` (comment). 3 new UnitRegistryTest cases.

### Slice B — fall-back group + `wanderDwellTimer` (`9104c85`, tenth)

- **As planned:** `float[] fallbackTimer`, `int[] fallbackCellX` /
  `int[] fallbackCellY` (paired `setFallbackCell(x, y)`, same int-pair
  template as `cellX/cellY`), and the optional ride-along
  `float[] wanderDwellTimer`. `Unit.local*` transient seed/snapshot
  fields + final accessors. The `-1/-1` cell sentinel rides the
  allocate-seed unchanged.
- **Consumers migrated to accessors:** `FallbackBehavior`, `FleeBehavior`,
  `TacticalScoring.fallbackDestinationNeedsRefresh`, `UnitUpdateSystem`,
  `SquadAlertSystem`, `HitResponseService`,
  `BattleSimulation.writeFallbackInline` (the lone Unit-write site — now
  one `setFallbackCell` + one `setFallbackTimer`), and the
  `BreakContact` / `BreakLOS` / `MechBreakContact` GOAP actions. The
  mechanical sweep (13 files incl. tests) was fanned out to a Sonnet
  subagent; the registry/accessor design + tests stayed on the main
  thread.
- **9 new UnitRegistryTest cases** (allocate-seed / release-snapshot /
  tail-swap for the cell pair, `fallbackTimer`, and `wanderDwellTimer`).

## Non-Unit look-alikes left untouched

- `PendingTargetMutation.fallbackCellX` / `fallbackCellY` — the queued
  mutation-record payload the shooter's worker fills, not Unit storage.
- `DamageService`'s `m.fallbackCellX` / `m.fallbackCellY` writes/reads
  (`m` is a `PendingTargetMutation`) — verified by grep after the sweep:
  the only remaining bare `.fallbackCell*` hits in `src/` are those three
  intentional sites.

## Result

`gradlew.bat :test` green at both commits. With this story done the
remaining ECS-migration backlog is the `path-mutation-to-navigation`
**Service** cleanup plus the deferred low-payoff primitives
(`attackCooldown`/`visionRange`/`moveSpeed`, `squadId`/`role`).
