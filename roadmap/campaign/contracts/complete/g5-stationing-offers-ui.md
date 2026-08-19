# G5 — Stationing offers and assignment UI

**Status:** CODE COMPLETE — in-game smoke pending (2026-08-12)

**Implemented in:** `0b2829ec`

## Goal

Expose Garrison/Cadre as their own commercial commitment rather than forcing
them through the battle-mission briefing.

## Locked vertical

- Tier 1 remains STRIKE-only in this first player-facing stationing vertical.
- Tier 2/3 first roll a 20% stationing branch: Garrison and Cadre split evenly.
- The remaining 80% retains the existing 35% Escort / 65% Strike mix.
- Stationing rows have no target house, payout, or mission representation.
- A stationing action appears above the selected patron's mission dossiers and
  opens a dedicated assignment screen.
- The screen selects an active captain, committed marines, and a term; it shows
  the frozen monthly retainer before acceptance.

## Acceptance

- Generator type selection and stationing row shape are directly tested.
- Lookup only surfaces matching OFFERED stationing rows at the pickup market.
- Unsupported stationing contracts never enter `MissionGenerator`.
- Acceptance invokes the G4 exactly-once service and returns to mission select.
- Full build green; layout/click/payment smoke remains the shipping gate.

## Automated verification

- `ContractOfferTemplateTest` covers stationing branch/type split plus retained
  mission-mode probabilities.
- `ContractGeneratorTest` proves targetless, zero-phase stationing row shape.
- `StationingOfferLookupTest` covers patron, market, state, and type filtering.
- G3/G4 tests continue to cover the acceptance and post-acceptance lifecycle.
- `gradlew.bat build` passes on 2026-08-12.
