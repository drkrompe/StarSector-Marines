# Slice F8 — civil-war player consequences

**Status:** CODE COMPLETE (2026-08-12)

**Implemented:** `753f3969`, `0ef347d3`, `03422b15`

## What shipped

- The terminal reputation contract scales cumulative successful contribution:
  +5/-8 below 30, +10/-15 below 60, and +15/-25 at 60 or above.
- Claimant victory supports the claimant and opposes the displaced incumbent
  only after the attributed throne handoff reaches `APPLIED`.
- Incumbent victory reverses those roles and qualifies only when the failed
  chain's resolution day equals its last incumbent contribution day, isolating
  the decisive player-caused failure from later unrelated invalidation.
- Chains and throne claims each persist an independent `PENDING`, `APPLIED`, or
  `NOT_APPLICABLE` player-consequence state plus an applied-day sentinel. The new
  columns initialize, grow, and backfill for legacy saves.
- A daily consumer runs after throne writeback and completed-contract
  contribution recovery. It applies sparse house-reputation rows atomically,
  clamps to `-100..100`, and closes autonomous or malformed terminal outcomes.
- Terminal political consequences do not change MRB reputation, contract
  completion/failure counters, or last-contract recency.

## Verification

- Focused tests cover all contribution tiers, both supported/opposed role
  directions, decisive incumbent end-to-end recovery, prepared-hand-off waiting,
  autonomous neutrality, stale-failure neutrality, clamping, replay, system
  order, table growth, and legacy backfill.
- Full `.\\gradlew.bat build --no-daemon --max-workers=1` passes.
- Manual playtesting remains intentionally skipped for this session.

## Next

- Specify the smallest silent moral-compass foundation that can accumulate
  meaningful choices without exposing numeric optimization UI.
- Reserve the explicit deposed-ruler testament/capstone reveal until those
  hidden axes have enough real inputs to say something earned about the player.
