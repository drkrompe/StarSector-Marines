# Tiered rewards

Status: PROPOSED

Written: 2026-08-23

Read `mission-tier-nouns.md` before implementing this story.

## Goal

Make payout and recovery-pool value reflect an operation's tier while keeping
risk responsible for variance and rare-quality gates.

## Acceptance

- Two missions of equal type and risk but different tiers have intentionally
  different payout and recovery-pool expectations.
- Tier reaches the frozen mission outcome and recovery request without letting
  a later UI or fleet change reroll value.
- Contract salvage entitlement remains a percentage of the resulting recovery
  pool, not a second reward-scale axis.
- Tests cover monotonic tier rewards and risk variance independently.

## Out of scope

Retuning the whole campaign economy or changing contract entitlement policy.
