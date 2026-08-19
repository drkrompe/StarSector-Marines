# Narrative S5 — Remembered Target Locations

**Status:** SHIPPED (2026-08-19, `7b5367d4`)

## Goal

Let patron-memory callbacks name where prior work happened, grounding shared
history in recognizable campaign places instead of only contract types and
outcomes.

## What shipped

- New patron-engagement snapshots freeze the prior operation's target-market
  registry slot before the source contract can be compacted.
- Strike, Escort, and Planetary Assault memories derive that slot from the
  source target house. Garrison, Cadre, and system extraction memories use the
  contract market. Invalid mission targets remain unknown.
- Save migration initializes the new column to `-1`, so legacy and targetless
  rows remain valid engagement facts. Capacity growth preserves the sentinel.
- `PatronTargetNameResolver` keeps stored registry identity separate from
  player-facing prose. Production resolves through the live economy to the
  market's primary-entity name; missing data uses a neutral location phrase and
  never exposes a registry id.
- S1 direct callbacks support `{target}`, S2 continuity supports
  `{previousTarget}` and `{latestTarget}`, and S3 local echoes support
  `{otherTarget}`. Selected authored variants use those tokens without changing
  deterministic selection or the direct → Chronicle → local precedence.
- No new callback layer, UI, reputation, economy, or contract behavior was
  added.

## Verification

- Automated coverage locks mission/stationing/extraction target derivation,
  invalid targets, immutable snapshots, compaction, capacity growth, save/load,
  and null-array legacy migration.
- Direct, two-engagement, and local rendering tests lock player-facing names,
  chronological target ordering, deterministic replay, and neutral fallback
  for missing or failing economy resolution.
- Focused S1–S5 coverage and the full `gradlew test` suite pass.
- Manual UI validation remains deferred by user direction.

## Deferred

- Captain observations, longer conversations, patron state evolution, inferred
  links between old and new operations, and new UI remain future stories.
