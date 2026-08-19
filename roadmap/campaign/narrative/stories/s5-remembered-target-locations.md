# Narrative S5 — Remembered Target Locations

**Status:** ACTIVE

## Goal

Let patron-memory callbacks name where prior work happened, grounding shared
history in recognizable campaign places instead of only contract types and
outcomes.

## Contract

- Each new immutable patron-engagement row freezes one target-market registry
  slot while the source contract still exists.
- Mission contracts derive the target from the source contract's target house.
  Garrison, Cadre, and system extraction work use the contract market itself.
  Invalid mission targets remain unknown rather than being rewritten as the
  patron's origin market.
- Legacy rows and targetless/malformed snapshots remain valid engagement facts;
  their new target slot migrates to `-1` and existing callbacks continue with a
  neutral location phrase.
- At render time, a small resolver maps the frozen market slot to the current
  player-facing market name. Registry ids are never shown as prose. Missing
  economy data also falls back to the neutral phrase.
- S1 direct-memory, S2 two-engagement continuity, and S3 local-echo templates
  gain optional target tokens. S4 Chronicle selection and the established
  direct → Chronicle → local precedence do not change.
- Lines may name only the persisted prior target location. They do not claim
  that the new offer is related, that the patron still controls the location,
  or why either operation occurred.
- No new callback layer, UI, reputation, economy, or contract behavior ships.

## Verification

- Persistence tests cover every target-derivation branch, compaction, capacity
  growth, save/load, and null-array legacy migration.
- Composition tests cover direct, two-engagement, and local target tokens,
  player-facing name resolution, neutral fallback, deterministic replay, and
  unchanged precedence.
- Focused S1–S5 coverage and the full Gradle test suite pass.
- Manual UI validation remains deferred by user direction.
