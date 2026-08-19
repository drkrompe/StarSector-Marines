# G7 — Contract smoke-test controls

**Status:** CODE COMPLETE (2026-08-12)

**Implemented in:** `c641cff0`

## Goal

Make Escort/Garrison/Cadre verticals directly reachable in game without waiting
for seeded daily RNG or manually manipulating patron ranks and offer tables.

## Acceptance

- Debug intel has explicit local Escort, Garrison, and Cadre spawn controls.
- Rank gates and per-patron open-offer caps match production behavior.
- Forced rows reuse production payout/salvage/phase shapes.
- Stationing rows cannot use the old debug Accept shortcut; assignment must go
  through the Ops UI so captain/marine invariants hold.
- Pure tests cover forced row shapes and rejection rules.
- Full build green.

## Automated verification

- `DebugContractOfferSpawnerTest` covers Garrison and Escort row shapes, rank
  gates, unsupported types, target requirements, and duplicate suppression.
- `ContractOfferTemplateTest` remains green through the shared forced profile.
- `gradlew.bat build` passes on 2026-08-12.
