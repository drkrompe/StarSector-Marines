# Reactive garrisons

Status: DRAFT

Written: 2026-10-13

## Intent

First consumer of `garrisonables.md`. Fort belt garrisons are born manned today:
each belt nest seats a defender squad in its trench from the first tick, which on
the fort control took 120 defenders out of the patrol pool and left no roving
reserve. Make belt manning reactive: belts are empty while quiet, the defender
commander mans the ones under believed pressure from its mobile pool, and
releases them when the pressure passes.

## Slices

1. **Belts stop being born garrisons.** Belt nests keep publishing their
   garrisonable (posts on the trench and parapet cells) but no longer spawn a
   squad there. Measure the patrol pool on the fort control returning to its
   pre-belt size.
2. **The commander mans threatened belts.** When the defender's threatened band
   contains or adjoins a belt, a rallied patrol is sent to man it — through the
   same claims as a defend-area order — instead of to a bare track point.
   Pickets before massing across belts; at least one free patrol stays in reserve
   as the existing law requires. Hysteresis on start and release, with a dwell.
3. **Release and fall back.** A belt garrison whose pressure decays below the
   release level returns to the pool. One whose belt is breached (enemy inside
   the run) or destroyed below the useful size falls back to the fort's works
   (the existing belt-post structure defence) if the fort still stands, else
   releases.
4. **Evidence.** Fort control: belt-manned squad count over time against
   threatened bands, patrol-pool size, dig-in samples, releases and fall backs; a
   scene with one belt under a passing attack showing man, hold, release.

## Constraints

- Belief only: pressure from the defender's own influence, never hostile truth.
- Commander gains no new kind of order; the garrison is a defend-area context on
  the belt.
- Keep and other declared holds remain born garrisons.
- Deterministic; candidate belts found spatially from threatened bands, on the
  command cadence.
- `battle.fort.beltGarrison` keeps a same-build control.

## Acceptance

- A quiet fort's belt is empty; the patrol pool is not reduced by belts.
- A fort under attack has its threatened belt stretch manned within a few
  command pulses, dug in, and released after the attack passes.
- A breached belt garrison falls back to the works rather than dying in place.
