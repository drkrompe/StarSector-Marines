# Slice G4 — civilian-rescue mission lineage

**Status:** CODE COMPLETE (2026-08-12)

**Implemented:** `2a5461a6`, `0d49d30e`, `fdfb0aef`

## What shipped

- `CAMPAIGN_EVENT` is an append-only mission-source value for black-swan work.
  Mission and outcome snapshots carry stable event id, market registry slot,
  and frozen civilian stakes without touching `contractId`.
- Existing constructors default event id/market to `-1` and stakes to zero, so
  generated, story, stationing, and contract paths retain their prior behavior.
- `CivilianRescueMissionKey` round-trips `civilian-rescue:<eventId>` and rejects
  malformed/non-positive identities.
- `CivilianRescueMissionFactory` validates the matching local committed row and
  builds a deterministic Extraction-shaped mission with zero payout, salvage,
  employer support, contract lineage, and industry target. Creation is pure and
  replay-stable.
- Mission outcomes add `civiliansRescued`, where exactly `-1` means no valid
  battle report. Generic `MissionResolver.compute` emits that sentinel because
  the existing elimination placeholder does not measure evacuation.
- `CivilianRescueMissionResolution` validates source, mission key, explicit
  event identity, market, at-risk snapshot, zero-economy shape, committed state,
  day, and the full `0..atRisk` report range before resolving once.
- Event outcomes are validated before ordinary mission side effects. Missing,
  invalid, stale, and replayed reports return early; a generic battle cannot
  resolve the event or replay casualties.
- The factory is intentionally not emitted by `MarineOpsContext`. Distress Net
  continues to show committed work as mission-pending until the battle has a
  real evacuation cohort metric.

## Verification

- Focused tests cover constructor defaults, stable factory snapshots, wrong
  market/state rejection, key parsing, missing-report neutrality, explicit
  zero/partial/full resolution, terminal replay, source/key/market/stakes/range
  mismatches, and pending-state rejection.
- Full `.\\gradlew.bat build --no-daemon --max-workers=1` passes.
- Manual playtesting remains intentionally skipped for this session.

## Next

- Add a representative battle-side rescue cohort with explicit evacuation—not
  survival—accounting.
- Scale that metric to campaign stakes and only then emit the mission locally.
- Add the swarm faction/roster/AI/art payload after the objective is measurable.
