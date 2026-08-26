# Repeatable headless battle performance pass

Status: PROPOSED — opt-in evidence infrastructure; does not displace current commander work.

Written: 2026-08-25

Updated: 2026-08-25 — fixed sample statistics, provenance, fixture digest, and early-completion contracts before implementation.

Read `battle-fixtures-nouns.md` before implementing this story.

## Intent

Turn production-shaped fixture replay into a repeatable, machine-local
performance pass. Measure fixed-tick throughput and existing workload counters
at named battle ages, compare compatible runs without pretending timings are
portable between machines, and hand suspicious rows to the shipped JFR profiler
for diagnosis.

## Scope

- Add one opt-in `battleFixturePerformance` task, excluded from ordinary
  `test` and `build`, with named smoke and full workload profiles rather than
  separate tasks.
- Start from checked-in civilian-rescue and canonical Conquest fixtures. Each
  workload row selects its fixture and declares warm-up, pre-roll battle age,
  measured fixed ticks, repetitions, and scheduler mode. The runner derives
  SHA-256 from the exact fixture bytes it loaded; a second handwritten digest
  is not an authority.
- Reuse or extract the existing fixture measurement seam: construct through
  the production factory, advance only `BattleSimulation.TICK_DT`, exclude
  construction and pre-roll from timing, use a fresh simulation per sample,
  and close every simulation-owned resource.
- Publish raw samples plus timestamp-free, schema-versioned `summary.json` and
  `summary.md` under `build/reports/performance/battle-fixtures/`. The primary
  sample is `activeTickNanos / measuredTicks`; report its median and median
  absolute deviation, with ticks per second derived only for readability. Also
  report existing
  `TickInnerProfile` workload counts and costs, live-unit range, fixture and
  workload provenance, source revision or build label, dirty-state marker,
  JVM/runtime configuration, OS, CPU, and scheduler mode. Revision and dirty
  state identify the builds being compared but are not part of compatibility,
  because a useful comparison normally spans revisions.
- Fail a workload when its fresh reconstruction completes during pre-roll or
  before the full measured slice. Never truncate a sample, time no-op ticks, or
  reconstruct midway through a sample to satisfy its declared length.
- Atomically replace only a complete report. An optional prior-report
  comparison may calculate deltas only when fixture SHA, workload definition,
  scheduler configuration, JVM, and host compatibility signature match;
  incompatible evidence is labelled instead of compared.
- Document how one selected workload row can be reproduced through the shipped
  `profileBattleFixture` task for JFR drill-down. Do not record JFR for every
  matrix sample.

## Acceptance

- [ ] One command runs smoke or full workloads without Starsector, OpenGL, or
  player input, while ordinary `test` and `build` remain unchanged.
- [ ] Workload shape is reproducible: every result identifies fixture bytes,
  battle age, exact measured ticks, repetition, scheduler, and runtime/host
  provenance, plus source/build revision and dirty state.
- [ ] Results contain schema-versioned sample timings, median and median
  absolute deviation of nanoseconds per active tick, live-unit range, and the
  existing inner-profile workload counters needed to distinguish more work
  from slower work.
- [ ] Automated coverage proves setup and pre-roll are excluded, samples begin
  at the declared battle age, and every sample advances exact fixed ticks on a
  fresh production reconstruction; early completion invalidates the workload.
- [ ] Prior-report deltas are emitted only for compatible evidence and remain
  advisory until repeated runs establish useful variance and thresholds.
- [ ] A selected matrix row can be handed to `profileBattleFixture` with
  equivalent fixture, battle-age, slice, and scheduler inputs for JFR analysis.
- [ ] Commander/referee traces remain timing-free deterministic evidence; a
  performance pass does not weaken their byte-stability contract.

## Constraints

- No checked-in absolute timing golden, cross-machine performance claim, or
  hard regression gate before variance is measured.
- No second fixture codec, scenario factory, simulation loop, JFR sampler, or
  profiler event model.
- Timings never enter construction fixtures or canonical commander traces.
- Every scheduler mode is explicit and unlike modes are never compared.
  Forced-serial runs are the stable comparator; production scheduling may be
  reported as informational evidence.
- Do not tune AI doctrine, mission forces, weapons, or other game balance in
  this infrastructure story. Optimizations require a measured hotspot and
  their own focused change.

## Exit

Fold durable performance-pass vocabulary and compatibility laws into
`battle-fixtures-nouns.md`, add this story to `shipped.md`, and delete it when
the repeatable matrix, reports, comparison seam, and JFR handoff ship.
