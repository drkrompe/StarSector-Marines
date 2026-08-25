# Commander trace and balance harness

Status: IN PROGRESS — canonical live trace and Conquest command-picture debugging are the first slice; production fixtures, analysis, and batch execution remain.

Written: 2026-08-25

Updated: 2026-08-25 — added the shared live/headless trace seam and perspective-specific Conquest visualization to the evidence scope.

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
- Let live debug presentation consume the same published facts: a selected
  faction's Conquest tracks, friendly and known-hostile fronts, exact targets
  or clearly labelled zone markers, command reasons, and faction-local
  influence. It must never merge
  both perspectives into a false shared picture.
- Make opt-in canonical battle-long command/referee JSONL available through the
  in-battle dump tool so a visual anomaly and an offline trace can be compared
  at the same command tick.

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
- [x] Live Conquest diagnostics can select one perspective and display its
  published tracks, fronts, and current action targets/zone markers; selected-squad dumps
  include the matching commander influence contacts without resolving hidden
  live hostile state.
- [x] A battle-long canonical JSONL stream separates published perspective
  events from labelled neutral compound and terminal events and can be dumped
  from the live debug panel.

## Constraints

- Do not turn one deterministic seed into a balance target.
- Do not tune weapon damage, mission forces, reinforcement budgets, or doctrine
  inside the evidence story.
- Snapshot/trace serialization is diagnostic and may show both perspectives;
  it does not authorize player-facing enemy intelligence.

## Exit

Fold durable balance-evidence laws into `ai-nouns.md`, add this story to
`shipped.md`, and delete it when the reusable trace and Conquest batch ship.
