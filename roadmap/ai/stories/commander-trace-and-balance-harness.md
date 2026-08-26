# Commander trace and balance harness

Status: IN PROGRESS — live diagnostics, deterministic Conquest evidence, and physical approach/capture-zone metrics are landed; bounded intervention comparison remains.

Written: 2026-08-25

Updated: 2026-08-25 — schema-4 canonical evidence proves the distant-capture reserve binds; launch-fidelity concentration evidence remains.

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
- Distinguish commanded marker closure, contact-bound and quiet non-closure,
  target-zone arrival, adjacent compound-assault commitment, and exact neutral
  capture-zone presence. Form-up and observation gaps censor physical evidence.
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
- [x] Conquest evidence distinguishes own-squad approach and target-zone
  arrival from neutral mixed or marine-only presence in the exact compound
  capture zone without leaking opposing occupancy into commander perspective.
- [x] Conquest capture allocation preserves in-flight and adjacent commitments
  while globally retaining useful executable force for actionable front
  resistance; published actions expose a binding distant-capture deferral
  separately from the reason for actual front work.
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

`commanderEvidence -Pmission=conquest` runs each entry twice with unit updates forced serial,
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

## Canonical baselines

The 2026-08-25 forced-serial run reached the bound on both fixtures, with both
replays byte-identical. A mission-rule defect initially declared defender
victory during the empty-ground rearm interval; future committed shuttle cycles
now keep the attacker in play, so the published evidence records timeouts
rather than fabricated losses.

| Fixture | Result | Marine / defender losses | Territory |
|---|---|---:|---|
| `undercommitted-south` | TIMEOUT at 18,000 | 111 / 52 | 0 captures; STALLED |
| `expected-west` | TIMEOUT at 18,000 | 252 / 30 | 0 captures; STALLED |

The schema-3 baseline added the physical distinction the first baseline lacked:

| Fixture | Peak observed live force | Marker closure | Exact capture-zone presence |
|---|---:|---:|---:|
| `undercommitted-south` | 12 members / 5 squads | 8 / 28 secure-compound episodes | none |
| `expected-west` | 3 members / 1 squad | 0 / 21 secure-compound episodes | none |

All observed non-closing travel intervals carried local contact, and neither
fixture produced an adjacent-assault commitment or target-zone observation.
The traces exposed a commander-policy problem: every observed marine command
action in these two fixtures was a distant `SECURE_COMPOUND`, because absence
of a faction-local defender belief read as an uncontested compound. The
attacker therefore bypassed broader-front work and met contact on a long
capture route.

This remains evidence, not a balance verdict. The historical construction
fixtures replay the production scenario factory but do not apply the campaign
deployment overlay; their generated sortie squads therefore do not exercise
campaign identity or form-up suspension. V2 launch fixtures can now bring that
overlay into a representative commander workload, but the canonical matrix
must still be populated with captured full-company launches before
concentration or reinforcement timing is tuned. The independently valid policy correction is now implemented:
unknown occupancy permits a measured probe but is not positive knowledge that
a distant compound is clear. Fresh distant capture allocation leaves at least
one executable actionable squad on the front, prefers squads without useful
front work, preserves in-flight captures, and exempts the adjacent-threshold
commitment path. Neutral referee occupancy never becomes commander input. A
canonical rerun must now establish whether and where that cap binds in the two
construction fixtures; the prior baseline remains historical evidence rather
than validation of the correction.

The schema-4 forced-serial rerun exercised the correction and remained
byte-stable across both replays:

| Fixture | Result | Marine / defender losses | Peak live force | Capture travel | Deferred capture pulses |
|---|---|---:|---:|---:|---:|
| `undercommitted-south` | DEFENDER at 6,361 | 111 / 38 | 9 members / 4 squads | 5 / 18 episodes closed; 0 arrived | 36 |
| `expected-west` | TIMEOUT at 18,000 | 252 / 31 | 4 members / 1 squad | no travel episodes | 61 |

Both fixtures again produced zero capture-zone presence, adjacent commitments,
or territorial captures. `undercommitted-south` published 15 fresh distant
capture actions and 75 preserved capture actions while repeatedly retaining an
actionable squad; preserved orders and deferrals coexist across replans rather
than drip-feeding the last front squad. Three deferred actions retained real
track work, while 33 retained ambient local-contact handling with no legal
zone order. `expected-west` exposed the limiting one-squad case: all 61
observed marine actions were local-contact, command-unassigned deferrals, so it
proves the reserve bound but cannot demonstrate a multi-squad broader front.

The earlier terminal/outcome delta is descriptive only. Combat and rendering
work also advanced between these baselines, so the defender terminal in
`undercommitted-south` cannot be attributed solely to capture allocation. The
commander conclusion is narrower and supported directly by perspective data:
the reserve binds, in-flight captures remain stable, and the current canonical
fixtures still lack launch-faithful simultaneous force. Neither fixture
exercised the adjacent exception, which remains covered by focused unit tests.

## Constraints

- Do not turn one deterministic seed into a balance target.
- Do not tune weapon damage, mission forces, reinforcement budgets, or doctrine
  inside the evidence story.
- Snapshot/trace serialization is diagnostic and may show both perspectives;
  it does not authorize player-facing enemy intelligence.

## Exit

Fold durable balance-evidence laws into `ai-nouns.md`, add this story to
`shipped.md`, and delete it when the reusable trace and Conquest batch ship.
