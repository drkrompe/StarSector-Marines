# Debug political contract completion

Status: DRAFT

Written: 2026-08-23

## Goal

Make the debug contract-completion action honest about the political field. A
debug-completed eligible political contract must either apply the same
attributable stake/promotion outcome as an equivalent successful operation, or
the action must be unavailable and explain why.

## Scope

- Reuse one domain-level political completion boundary rather than duplicating
  stake or promotion mutation in debug UI code.
- Derive only from persisted contract facts that are valid for the outcome; do
  not invent a target, industry, mission result, or civil-war contribution.
- Preserve exactly-once behavior, ordinary contract reputation rules, and all
  terminal guards.

## Acceptance

- A supported debug completion changes contract and political state once.
- Unsupported contracts remain explicitly non-political rather than silently
  resembling a full field mutation.
- Real mission resolution keeps its current authority and is not made dependent
  on debug UI.

## Boundaries

`contracts-nouns.md` owns contract lifecycle. `living-world-nouns.md` owns the
meaning of the resulting political change. This is a diagnostic cohesion fix,
not a rebalance of contract rewards.
