# Phase 2 — Unit-ref fields → long entity IDs

All cross-tick targeting fields migrated off live `Unit` refs:

- **2b** (`14cf2e6`) — `Unit.target` → `long targetId`.
- **2c** (`5e06311`) — `Unit.burstTarget` + `Unit.secondaryAimTarget` +
  `MapTurret.burstTarget`.
- **2d** (`2afee3d`) — `MountedTurret.target` + `MountedTurret.burstTarget`.

No class holds a live `Unit` ref for cross-tick targeting state.

## Test-helper closure (`e7a97fc` + `fffd973` + `1f26de4`)

Production death pairs `hp=0` with `releaseFromRegistry`; tests now do
the same via `TestUnits.kill(sim, unit)`. Defensive `isAlive()` follow-up
on every registry-resolve site dropped at ~25 sites.

Contract: after any tick boundary, `sim.resolveUnit(id) != null` ⟺ unit alive.
