# Slice F6 — civil-war participation foundation

**Status:** CODE COMPLETE (2026-08-12)

**Implemented:** `926047e8`, `4332927f`, `50591863`, `3610923d`,
`487134ae`

## What shipped

- A locked three-band participation contract maps claimant/incumbent work onto
  existing parent-chain and opposed-chain lineage without duplicate side fields.
- Chains persist `NONE`/`CLAIMANT`/`INCUMBENT` allegiance, cumulative successful
  contribution weight, and last contribution day.
- Contracts persist their offer-time civil-war band and exactly-once applied day.
  All columns support initialization, growth, compaction, and legacy backfill.
- `CivilWarParticipation` validates completed state, exclusive lineage, civil-war
  archetype, parties, market, band/type mapping, active chain state, and locked
  allegiance before mutation.
- Coalition and mobilization work applies ±15/±30 progress. Open-conflict claimant
  work arms threshold resolution; incumbent work fails the rebellion. Replays,
  malformed lineage, contradictory allegiance, and late terminal work do nothing.
- A daily system after contract lifecycle recovers completed mission/stationing
  contributions. Banded contracts bypass the older generic intervention shortcut.
- Successful throne handoff preparation snapshots player allegiance, contribution,
  and last contribution day immutably from its source chain.

## Verification

- Focused tests cover persistence sentinels, growth, compaction, legacy backfill,
  both sides and all outcome classes, exact-once replay, malformed identity,
  allegiance conflict, terminal safety, recovery-system ordering, legacy
  intervention isolation, and immutable handoff attribution.

## Next

- Generate paired discovered-civil-war offers once per side/band.
- Persist a concrete local objective and implement acceptance/withdrawal rules.
- Apply terminal player consequences from the attributed chain/handoff records.
