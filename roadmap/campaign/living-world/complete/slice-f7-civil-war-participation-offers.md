# Slice F7 — civil-war participation offers

**Status:** CODE COMPLETE (2026-08-12)

**Implemented:** `551081ab`, `4f6afb8b`

## What shipped

- A dedicated daily producer creates a paired claimant/incumbent offer for each
  discovered active civil-war band and prevents historical regeneration for the
  same side/band.
- Coalition building maps to `ESCORT`/`STRIKE`, mobilization maps to
  `CADRE`/`GARRISON`, and open conflict maps both sides to decisive
  `PLANETARY_ASSAULT` work with 60 attribution weight.
- Each pair freezes the strongest concrete local objective, preferring an
  industry contested by both houses, without changing the market-wide chain's
  `-1` industry sentinel. Mission generation honors that frozen objective.
- Band changes and terminal chains expire stale offers. The generic discovered-
  threat producer now leaves civil wars exclusively to the dedicated producer.
- `CivilWarOfferAcceptance` validates exclusive lineage, chain identity/state,
  frozen band, parties, market, type mapping, allegiance, and contradictory
  active work before a choice becomes active.
- Battle deployment, stationing assignment, and debug acceptance all use the
  shared choice semantics. Acceptance immediately expires the opposing side;
  stationing validation happens before marines are consumed, and later phases
  of an accepted Planetary Assault remain deployable.

## Verification

- Focused tests cover paired exact-once generation, band transitions, objective
  selection, discovery/terminal gates, allegiance withdrawal, malformed or
  contradictory acceptance, stationing integration, contribution recovery, and
  multi-phase Planetary Assault lifecycle behavior.
- Full `.\\gradlew.bat build --no-daemon --max-workers=1` passes.
- Manual playtesting was intentionally skipped for the remainder of this session.

## Next

- Define and apply exactly-once player-reputation consequences from successful
  claimant handoffs and attributed incumbent victories.
- Keep autonomous outcomes and mere offer acceptance player-reputation-neutral.
