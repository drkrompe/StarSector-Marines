# Commander trace and balance harness

Status: PLANNED — follows `autonomous-mission-command-foundation.md` and its Conquest migration.

Written: 2026-08-25

Read `ai-nouns.md` and `battle-fixtures-nouns.md` before implementing this
story.

## Intent

Make the auto-battler baseline measurable. Run deterministic zero-input command
duels across representative seeds and expose why a side progressed, stalled,
churned, or collapsed before tuning force budgets or doctrine.

## Scope

- Record two synchronized channels: perspective command events drawn only from
  that side's frames/snapshots, and a neutral referee stream for authoritative
  objective progress, casualties, combat outcomes, duration, and final result.
- Batch production-shaped fixtures over stratified seeds and supported force
  commitments.
- Report unassigned mobile combat power, directive churn, response latency,
  stalled/unreachable directives, reserve duration and release timing, force
  concentration, objective progress, casualties, duration, and outcome.
- Start with Conquest, then register each mission command duel as it ships.
- Keep the harness headless and separate from player-facing difficulty claims;
  it supplies evidence rather than silently changing balance.

## Acceptance

- [ ] Repeating a fixture and seed produces byte-stable command events and the
  same aggregate result.
- [ ] Each perspective command event identifies its side and uses only facts
  present in that side's published frame/snapshot at the time; neutral referee
  events are labelled and never fed back into command.
- [ ] A fixture that leaves mobile squads idle, thrashes assignments, or fails
  to progress an actionable objective is reported explicitly.
- [ ] Conquest produces zero-input duration, outcome, casualty, territorial
  progress, reserve, and response metrics across a documented seed set.
- [ ] The harness can compare a bounded player intervention with the same
  zero-input baseline once interventions exist, without making input mandatory.

## Constraints

- Do not turn one deterministic seed into a balance target.
- Do not tune weapon damage, mission forces, reinforcement budgets, or doctrine
  inside the evidence story.
- Snapshot/trace serialization is diagnostic and may show both perspectives;
  it does not authorize player-facing enemy intelligence.

## Exit

Fold durable balance-evidence laws into `ai-nouns.md`, add this story to
`shipped.md`, and delete it when the reusable trace and Conquest batch ship.
