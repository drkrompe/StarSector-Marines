# Commander trace and balance harness

Status: IN PROGRESS — live diagnostics, the Conquest construction fixture, canonical trace analysis, and the first reviewed forced-serial baseline are landed; richer physical-progress metrics and intervention comparison remain.

Written: 2026-08-25

Updated: 2026-08-25 — reviewed the first full canonical matrix, fixed false elimination between shuttle cycles, and recorded the zero-capture baseline.

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

- [x] Repeating a fixture and seed produces byte-stable command events and the
  same aggregate result.
- [x] Each perspective command event identifies its side and uses only facts
  present in that side's published frame/snapshot at the time; neutral referee
  events are labelled and never fed back into command.
- [x] A fixture that leaves mobile squads idle, thrashes assignments, or fails
  to progress an actionable objective is reported explicitly.
- [x] Conquest produces zero-input duration, outcome, casualty, territorial
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

## Canonical Conquest matrix

`conquestCommandBalance` runs each entry twice with unit updates forced serial,
advancing only `BattleSimulation.TICK_DT`, and compares both the JSONL bytes and
normalized summary bytes. The default 18,000-tick bound is ten simulated
minutes; an unfinished battle records `TIMEOUT` and remains evidence rather
than being scored as a defender win.

| Fixture | Seed / axis | Commitment | Authored pressure |
|---|---|---|---|
| `undercommitted-south` | 1 / south-to-north | 112 seats over 28 sequential cycles in three Kites (10/9/9) | Reinforced / low risk / no heavy armor |
| `expected-west` | 4096 / west-to-east | 336 seats over 28 sequential cycles in one Valkyrie | Reinforced / high risk / heavy armor |

Generated traces and timestamp-free summaries live under
`build/reports/commander/conquest/`. The matrix is intentionally opt-in; normal
tests cover fixture codecs, production construction, trace schema, and analysis
contracts without executing full battles. Each canonical invocation performs
two replays of both full 240×160 fixtures—72,000 bounded simulation ticks before
early terminal results—and may take several minutes. A max-tick or external-
fixture override is labelled ad hoc in the report; `summary.json` is the full
machine-readable evidence and `summary.md` is its human overview.

## First canonical baseline

The 2026-08-25 forced-serial run reached the bound on both fixtures, with both
replays byte-identical. A mission-rule defect initially declared defender
victory during the empty-ground rearm interval; future committed shuttle cycles
now keep the attacker in play, so the published evidence records timeouts
rather than fabricated losses.

| Fixture | Result | Marine / defender losses | Territory |
|---|---|---:|---|
| `undercommitted-south` | TIMEOUT at 18,000 | 111 / 52 | 0 captures; STALLED |
| `expected-west` | TIMEOUT at 18,000 | 252 / 30 | 0 captures; STALLED |

This is not a balance verdict. It establishes that assignment churn and
command-unassigned time are near zero while physical territorial progress is
also zero, so the next evidence slice must measure squad travel, contact,
assault entry, and capture-zone presence before doctrine or force budgets are
tuned.

## Constraints

- Do not turn one deterministic seed into a balance target.
- Do not tune weapon damage, mission forces, reinforcement budgets, or doctrine
  inside the evidence story.
- Snapshot/trace serialization is diagnostic and may show both perspectives;
  it does not authorize player-facing enemy intelligence.

## Exit

Fold durable balance-evidence laws into `ai-nouns.md`, add this story to
`shipped.md`, and delete it when the reusable trace and Conquest batch ship.
