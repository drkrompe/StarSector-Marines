# S2 Slice 2 — Employer power co-source + baseline gate — ✅ SHIPPED (compile-verified)

Second slice of the S2 explicit-detachment arc. Makes the employer/contract a
real co-source of command powers and stops giving recon ping away unconditionally
— it's now *sourced* (a committed ship's kit or an employer offer), with a dev
flag to keep the loop demoable.

## What landed

- **`DevConfig.ALWAYS_GRANT_RECON_PING`** (default `true`, dev convenience) —
  seeds recon ping for free so the power loop stays exercised on a fleet with no
  recon ship. Flip off to feel the real gating (ship kit / employer offer only).
- **`PowerCatalog.resolve`** — the unconditional baseline `ReconPing` is now
  behind that flag; the ship-kit mapping (Hi-Res Sensors / Surveying Equipment /
  Apogee) and the employer offer are the real sources.
- **`MissionGenerator.rollEmployerPowers(Random, RiskLevel)`** — sibling to
  `rollEmployerShuttles`. Risk-scaled chance (15/30/45% LOW/MED/HIGH) for a
  contract to offer recon ping; wired into the contract-mission builder, feeding
  `Mission.employerPowerIds` (was the empty placeholder from Slice 1).
  Patron/contract is the co-source home ([[feedback_patron_narrative_discoverable]]);
  faction-direct industry missions don't offer powers.

## Scope note — narrowing moved to Slice 3

The original plan put "power narrowing to the committed subset" here. That's a
no-op before the commit UI exists: a recon-capable ship (Apogee / Hi-Res Sensors)
is neither a transport nor a carrier, so there's no committed-ship set to narrow
against yet. Shuttles are *already* a committed subset (`deselectedTransports`).
So power/fighter narrowing folds into Slice 3, where the unified
Committed-Detachment UI produces the committed-ship set that drives it.

## Verified

`gradlew compileJava` green.
