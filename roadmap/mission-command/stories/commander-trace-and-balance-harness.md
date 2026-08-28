# Commander trace and balance harness

Status: IN PROGRESS — live diagnostics, deterministic Conquest evidence, and physical approach/capture-zone metrics are landed; bounded intervention comparison remains.

Written: 2026-08-25

Updated: 2026-08-28 — used schema-8 tactical evidence to move the next
Conquest investigation from travel loss to contested-zone conversion.

Read `mission-command-nouns.md` and `battle-fixtures-nouns.md` before implementing this
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
- Sample the first deterministic replay at an argument-selected tick cadence,
  retain numbered neutral-observer PNG frames, and assemble a looping GIF for
  quick spatial review without launching Starsector or adding another Gradle
  task. Add neutral faction markers so forces remain legible at whole-map scale.
  Always include initial and final state, and keep rendering read-only so the
  uncaptured replay remains the determinism oracle.

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
- [x] Every observed Conquest secure-compound travel segment ends once by
  target entry, retarget, release, squad loss, execution suspension,
  observation gap, timeout, or terminal result; incomplete traces remain open
  and contact/path/quiet observations remain orthogonal context.
- [x] Secure-travel retarget exits distinguish objective, marker, and
  replacement-assignment changes; squad-loss exits retain measured/unknown
  last-alive objective distance, approach progress, and published contact/front
  context without claiming an exact casualty cell.
- [x] Conquest capture allocation preserves in-flight and adjacent commitments
  while globally retaining useful executable force for actionable front
  resistance; published actions expose a binding distant-capture deferral
  separately from the reason for actual front work.
- [x] Conquest command-unassigned time is split into lifecycle, execution
  suspension, local contact, active-path movement, genuine idle, and legacy
  unclassified buckets using only published own-force facts; the buckets are
  visible in the report, selected-squad panel, and squad dump.
- [ ] The harness can compare a bounded player intervention with the same
  zero-input baseline once interventions exist, without making input mandatory.
- [x] Live Conquest diagnostics can select one perspective and display its
  published tracks, fronts, and current action targets/zone markers; selected-squad dumps
  include the matching commander influence contacts without resolving hidden
  live hostile state.
- [x] A battle-long canonical JSONL stream separates published perspective
  events from labelled neutral compound and terminal events and can be dumped
  from the live debug panel.
- [x] Every mission selected through `commanderEvidence` can emit cadence-based
  PNG frames, a manifest, and a looping review GIF from the production scene
  renderer; ordinary evidence runs remain render-free.

## Canonical Conquest matrix

`commanderEvidence -Pmission=conquest` runs each entry twice with unit updates forced serial,
advancing only `BattleSimulation.TICK_DT`, and compares both the JSONL bytes and
normalized summary bytes. The default 18,000-tick bound is ten simulated
minutes; an unfinished battle records `TIMEOUT` and remains evidence rather
than being scored as a defender win.

| Fixture | Seed / axis | Commitment | Authored pressure |
|---|---|---|---|
| `reinforced-south` | 1 / south-to-north | 17 squads / 204 frozen marines; six Aeroshuttles cycle 6/6/6/6/5/5 | Reinforced / low risk / no heavy armor |
| `full-strength-west` | 4096 / west-to-east | 34 squads / 408 frozen marines; six Aeroshuttles cycle 12/12/11/11/11/11 | Full Strength / medium risk / heavy armor |

Generated traces and timestamp-free summaries live under
`build/reports/commander/conquest/`. The matrix is intentionally opt-in; normal
tests cover fixture codecs, full-company launch shape, production construction,
trace schema, and analysis contracts without executing full battles. Canonical
rows must retain a V3 launch wrapper, paired six-seat Aeroshuttle arrivals, and
their declared marine/squad commitment. Each canonical invocation performs
two replays of both full 240×160 fixtures—72,000 bounded simulation ticks before
early terminal results—and may take several minutes. A max-tick or external-
fixture override is labelled ad hoc in the report; `summary.json` is the full
machine-readable evidence and `summary.md` is its human overview.

Command-trace schema 8 canonicalizes diagnostic scalar floats to basis-point
precision and published squad centroids to one tenth of a cell. The simulation
and commander still consume their unmodified values. This boundary keeps
sub-cell integration drift from masquerading as a different command decision
while retaining much finer spatial resolution than any objective zone or
movement marker requires. Schema 6 added the count of squad members with an
unexhausted movement path. Schema 7 adds the authoritative compound capture
cell/zone and the perspective-safe count of own squad members in their current
assigned target zone. Schema 8 adds final-living tactical-contact facets:
published contact posture/doctrine/initiative and engageability, actual-moving
members, directional cover from the believed primary contact, recent incoming
fire, morale state, current goal/action, and members presently cooling down.
Cooldown is not reported as shots fired and no suppression state is invented.
The offline analyzer applies strict
lifecycle → suspension → contact → active-path → genuine-idle precedence to
command-unassigned actions; schema 5 and older rows remain explicitly
unclassified rather than being reinterpreted without the missing path fact.

## Representative V3 full-company baseline

The 2026-08-28 forced-serial canonical run completed all four 18,000-tick
replays in approximately nineteen minutes. Both fixtures timed out without a
fabricated winner, and each fixture's command events and normalized metrics
were byte-identical across its two replays.

| Fixture | Marine / defender losses | Peak live force | Capture travel | Territory at timeout |
|---|---:|---:|---:|---|
| `reinforced-south` | 209 / 181 | 58 members / 11 squads | 18/25 movement episodes closed; 1/18 secure episodes entered its zone | 1 capture, then lost; 0 held |
| `full-strength-west` | 431 / 412 | 211 members / 27 squads | 225/284 movement episodes closed; 2/42 secure episodes entered their zone | 5 captures; 5 held |

The representative launches invalidate the old conclusion that the commander
could not produce territorial progress. Reinforced-south reached two compounds,
peaked at 7,500 capture-progress basis points, and briefly captured one.
Full-strength-west reached five compounds and retained all five through the
bound. Adjacent-assault commitments occurred in both rows (two and six), so the
final-compound exception and broader-front reserve now receive integrated—not
only focused—coverage. Fresh distant capture was deferred on 196 Marine
squad-pulses in reinforced-south and 20 in full-strength-west.

The defender command also mobilized against every observed threat episode:
seven reinforced-south and ten full-strength-west samples, all published with
zero-pulse latency and no unmobilized episode. This does not establish balance
from two seeds, but it does establish that paired commander response and lane
pressure survive representative force delivery.

The next Conquest evidence seam was command inactivity, not transport shape.
The Marine commander published 165 command-unassigned/no-actionable pulses in
reinforced-south and 398 in full-strength-west, while only 1/18 and 2/42
secure-compound episodes were observed inside their exact target zone. Before
tuning allocation, split those pulses by physical cause—destroyed or not-yet-
arrived squad, form-up/execution suspension, active local contact, preserved
useful movement, or genuine idle command pool—and make the genuine idle cases
visible in the trace/report. Exact neutral capture-zone presence remains
referee evidence and must not become commander input.

## Command-inactivity classification follow-up

The 2026-08-28 schema-6 bounded rerun classified the first 6,000 ticks of each
representative V3 launch twice, with byte-identical trace and metric output.
Reinforced-south produced no command-unassigned time before losing 202 of its
204 committed marines. Full-strength-west produced 104 no-actionable
squad-pulses / 7,799 squad-ticks; every one classified as active local contact.
Lifecycle, execution suspension, path-only movement, genuine idle, and
unclassified time were all zero. The selected-squad dump fixture independently
exercised the precedence case where local contact and an active path coexist.

This evidence closes the suspected early-window commander-idle gap without a
behavior change: `NO_ACTIONABLE_TRACK_TARGET` was handing squads to their local
tactical contact doctrine, not abandoning quiet squads. Preserving an expired
lane advance or inventing an own-force lead fallback remains a valid future
option only if a longer or different representative trace records non-zero
genuine-idle time. Do not tune it from the old aggregate count. The next
Conquest investigation should instead focus on why secure-compound assignments
so rarely cross into their exact target zones, using the existing movement,
contact, suspension, and neutral presence evidence without feeding referee
occupancy back into command.

## Capture-room authority follow-up

The low target-zone count exposed two implementation gaps and one measurement
gap. Compound capture resolved the nearest standable cell inside the footprint,
while command targets, markers, and evidence still used the raw node anchor.
Multi-room compounds then allowed the hold action to post members outside the
one room that actually captures. Finally, target arrival sampled only the
squad leader, so an assault could make real capture progress without recording
an entry.

The capture-room resolver is now shared authority across capture, both
Conquest commanders, assignment rebinding, action markers, and trace presence.
Intermediate room travel remains cautious, but the final room hop commits
through route contact and subsequent hold posts stay inside the capture room.
Schema 7 records how many members of the perspective's own squad occupy its
assigned target zone; exact mixed or hostile occupancy remains neutral referee
evidence and is not available to command.

Focused coverage and the full unit suite pass. A 6,000-tick reinforced-south
run reached marine-only capture presence, peaked at seven marines and 7,500
capture-progress basis points, captured the barracks at tick 2,490, and later
lost it at tick 5,670. Three Marine command squad-pulses recorded at least one
member in the assigned target zone, totaling eleven member observations. The
paired full-strength-west replay completed byte-stably at a shorter 4,000-tick
diagnostic bound but had not yet reached a compound; two attempted 6,000-tick
full-strength replays were stopped externally at similar wall time, so that
partial window is not a balance conclusion.

Secure travel now has that explicit single-exit lifecycle without a trace
schema change. Same-target reissues remain one trip, rejected proposals leave
their incumbent intact, resumed in-zone baselines are left-censored, and an
incomplete live trace stays open. Conquest summary schema 4 publishes the exit
breakdown beside overlapping local-contact, active-path, and quiet-travel
episode counts.

The sealed duplicate 6,000-tick reinforced-south rerun finalized all 23 secure
travel segments: four target entries, five retargets, one release, eleven squad
losses, and two timeout exits. Every segment observed local contact, 21 retained
an active path, and only one had any quiet-travel observation. The run captured
and held one compound at the bound. This is evidence that lethal contact—not
an unexplained passive-travel bucket—dominates non-arrival in this fixture; it
is not a balance target.

The paired two-fixture run again completed reinforced-south but received an
external Gradle stop during full-strength-west before it could publish the
matrix. The isolated reinforced fixture then completed and published byte-
stable trace and summary output. Full-strength secure-exit comparison therefore
remains an environment-bounded evidence follow-up, not missing analyzer work.

## Retarget provenance and last-alive loss follow-up

Conquest summary schema 5 now classifies secure retargets as another compound,
a changed capture marker, a replacement assignment, or unclassified observed
transition. Squad-loss exits retain measured/unknown final-living-pulse
distance to the objective and normalized approach closure, plus local-contact,
track-belief-only, no-published-contact, or unknown-track context. These are
perspective-safe pulse observations, not exact casualty positions. Trace schema
7 remains sufficient; it does not publish normalized own-squad progress, so
the analyzer deliberately does not claim behind/at/beyond-front placement.

The isolated duplicate 6,000-tick reinforced row remained byte-stable after
concurrent combat changes on main. It finalized 19 secure trips: two target
entries, sixteen squad losses, and one replacement-assignment retarget, with
no compound-target or marker change. Every lost squad's final living pulse had
local contact and a measurable objective distance. The median was 44.8 cells
from the capture marker at 3,957 approach-progress basis points; four losses
were within 13.5 cells and five had crossed half their starting range. The run
captured two compounds and held one at timeout. This isolates tactical survival
under contact—and secondarily the final breach—as the next question; it does
not authorize force, damage, or doctrine tuning from one seed.

## Tactical-contact loss follow-up

Command-trace schema 8 and Conquest summary schema 6 now retain the last living
command-pulse tactical picture for every secure-travel squad-loss exit. The
report exposes orthogonal facts instead of forcing a single explanation:
breach action, actual movement, movement with no directional cover from the
published primary contact, contact doctrine and initiative, engageable members
and fireteams, recent incoming fire, majority directional cover, and weapon
cooldown presence. Schema 7 losses remain explicitly unknown.

The duplicate forced-serial 6,000-tick reinforced replay was byte-stable and
materially changed the current picture. Of 32 secure trips, 23 entered their
exact target zone, five retargeted, two ended in squad loss, and two remained
active at timeout. Twenty-two adjacent-assault commitments and eight compounds
with Marine presence confirm that approach and final-room entry are no longer
the dominant failure in this row.

The two loss exits likewise do not support an uncovered charge or final-breach
failure. SQ-124's final living pulse was one stationary Marine 45.6 cells from
the marker on `EnterZone[97]`, with `HOLD` / `RECEIVE`, one engageable member
and fireteam, and one member cooling down. SQ-126's was one stationary Marine
69.9 cells out on `BreakContact`, recently under fire and morale-broken, with
`ADVANCE` / `NONE`, one engageable member and fireteam, and one member cooling
down. Neither row published `BreachAndAdvance` or actual movement. Cooldown
remains a recovery-state observation rather than a cumulative shot count.

The stronger next seam is contested capture conversion. The same run recorded
41,230 mixed-occupancy compound-ticks, zero marine-only compound-ticks, zero
captures, and peak capture progress of 6,667 basis points despite those 23
entries. Extend neutral evidence around capture-zone cohorts and correlate it
with the perspective squad actions already in the zone: arrival strength,
friendly reinforcement, hostile clearance, rotation or displacement, and time
to uncontested control. Exact hostile and whole-zone occupancy remain referee
facts and must not become commander input. Add a monotonic squad-level
rounds-fired counter only if this cohort view leaves return fire unresolved.

## Historical construction-only baselines

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

This remains evidence, not a balance verdict. These historical construction
fixtures replay the production scenario factory but do not apply the campaign
deployment overlay; their generated sortie squads therefore do not exercise
campaign identity or form-up suspension. They remain here to explain why the
matrix was deliberately replaced rather than silently rebaselined. The
independently valid policy correction is now implemented:
unknown occupancy permits a measured probe but is not positive knowledge that
a distant compound is clear. Fresh distant capture allocation leaves at least
one executable actionable squad on the front, prefers squads without useful
front work, preserves in-flight captures, and exempts the adjacent-threshold
commitment path. Neutral referee occupancy never becomes commander input. A
representative canonical rerun now establishes where that cap binds above; the
prior baseline remains historical evidence rather than validation of the
correction.

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
the reserve binds, in-flight captures remain stable, and the historical
fixtures lacked launch-faithful simultaneous force. Neither fixture
exercised the adjacent exception, which remains covered by focused unit tests.

## Constraints

- Do not turn one deterministic seed into a balance target.
- Do not tune weapon damage, mission forces, reinforcement budgets, or doctrine
  inside the evidence story.
- Snapshot/trace serialization is diagnostic and may show both perspectives;
  it does not authorize player-facing enemy intelligence.

## Exit

Fold durable balance-evidence laws into `mission-command-nouns.md`, add this story to
`shipped.md`, and delete it when the reusable trace and Conquest batch ship.
