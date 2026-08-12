# G28 — Garrison defense battle handoff

**Status:** CODE COMPLETE (2026-08-12)

## Goal

Turn a persisted Garrison-defense trigger into a playable local battle and
write its outcome back to the stationing assignment exactly once.

## Locked rules

- Pending defenses launch from assignment management with the assigned
  `GARRISONED` captain, committed marine pool, and local transport cycles.
- Defense missions use Assault, pay no extra cash, and preserve the Garrison's
  contract-wide negotiated salvage entitlement (25% baseline by default).
- The mission key binds contract id to the persisted campaign-event key.
- Stale or duplicate results are rejected before cargo, captain, or contract
  mutations can occur.
- A victory with a viable captain and surviving detachment returns the
  assignment to ACTIVE. Defeat, captain loss, or detachment wipe fails it.
- The consumed event key remains as a watermark so the same raid cannot
  immediately re-arm the Garrison.
- A pending defense must be resolved before the assignment can be withdrawn.

## Automated verification

- `GarrisonDefenseMissionKeyTest` covers identity round-trip and malformed keys.
- `GarrisonDefenseMissionFactoryTest` covers stationing source, local transport
  cycles, fixed mission shape, target identity, and negotiated salvage.
- `GarrisonDefenseResolutionTest` covers victory, defeat, captain loss, wipe,
  casualty debit, payload cleanup, retained watermark, and exact-once rejection.
- Existing Cadre stationing battle tests remain green through the generalized
  mission-result validation and routing path.
