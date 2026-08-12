# G30 — Internal-flip Garrison trigger

**Status:** CODE COMPLETE (2026-08-12)

## Goal

Make a live faction-ownership change at a protected market trigger the final
reactive Garrison-defense source.

## Locked rules

- The daily campaign tick checks only markets with active Garrison assignments.
- A Garrison arms when the market's current vanilla faction differs from its
  patron house's persisted faction.
- Other Garrison patrons at the same market whose faction matches the current
  owner remain ACTIVE.
- Event identity is stable for `(market id, current faction id)`, so a resolved
  flip cannot re-arm every day; a later change to another faction is new work.
- The current market faction is persisted as the attacker faction and the
  source is `INTERNAL_FLIP`.
- Flip detection runs before stationing default, payment, training, incident,
  and lifecycle systems.

## Automated verification

- `InternalFlipGarrisonSystemTest` covers faction comparison, per-patron
  filtering on shared markets, persisted payload, stable event keys, and
  consumed-event rejection.
- `CampaignStateSystemOrderTest` locks the producer before stationing defaults.
- Existing shared-trigger tests continue to cover the other defense sources.
