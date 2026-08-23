# Story: multi-truck convoys + spacing

Status: PLANNED — LZ separation is shipped; same-dispatch following waits on single-vehicle acceptance.

Written: 2026-05-28

Updated: 2026-08-23 — narrowed to staggered same-route vehicles and linked it to planner measurement.

Read `convoy-nouns.md` first. Separate dispatches already prefer distinct
drop-offs. What's left is *same-road staggered following* — a single dispatch
that emits 2–4 trucks down one road.

One truck per spawn reads as a coincidence; three reads as a deliberate
reinforcement push. This is the next visual upgrade after Conquest
integration.

## Scope

- **Spawn cadence.** Represent count and stagger at the reinforcement/mission
  boundary so each vehicle remains an ordinary component-native convoy actor.
- **Following distance.** Vehicles on one dispatch retain readable separation
  from true world positions without introducing a second movement authority or
  reviving the retired object-owned vehicle model.
- **Stagger on deboard.** Trucks arrive at the same LZ in sequence; the
  LZ deboard scan already handles "no free cell, retry next tick," so the
  second truck just waits its turn.

This story is the prerequisite for `slice-5-perf-budget.md`; simultaneous
planner optimization remains measurement-led.

## Open

- Does a multi-truck dispatch share one Hybrid A* plan (cheaper, identical
  paths) or re-plan per truck against the others' projected positions?
  Shared plan is the cheap start.

## Acceptance

A single request launches 2–4 visibly related vehicles on a stagger, each
unloads exactly one payload, and close following neither overlaps bodies nor
causes recovery thrash. Separate requests retain their existing drop-off
separation.
