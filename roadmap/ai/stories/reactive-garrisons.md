# Reactive garrisons

Status: IN PROGRESS — reactive manning, flow and the unified reservation built and churn damped; strength floor and multi-seed evidence next

Written: 2026-10-13

Updated: 2026-10-14 — the "idle" reserve was a responder recalled and re-sent every other pulse as belief of the attack lapsed; threat memory and alarmed-fort threats fix it (trial matrix, same-build control: defender reserve share 68% vs 81%, onsets answered 17/27 vs 12/31, marine losses 201 vs 230 of 324, defender losses level). Next: a belt garrison holds its own stretch while the attack is elsewhere in the fort.

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

## Findings so far

- Pressure registers on every pressed belt, and evaluation costs about
  0.01–0.03 ms per pulse warm.
- Patrols are 100–200 cells from the belts. Ordered between ticks 1380 and
  2340, they reached the belt between 1770 and 4230, and some never did; pressure
  episodes are shorter than the march, so four of nine squads were released en
  route. Next: send only squads that can arrive while the pressure is likely to
  last, hold the order while a squad is still closing, and consider a fort-local
  reserve so the nearest free squad is near the belt.
- Run ids renumber when the index rebuilds; the commander re-resolves a manned
  run by its centre cell and counts a vanished run as lost.

## Next

- A belt garrison holds the stretch it was sent to even when the attack has
  moved round the fort or inside the works; an alarmed fort should redeploy
  its garrison toward where the fort is actually being lost.
- The track rally alternates between the remembered contact band and the
  alarmed fort's rear when both apply; same direction, but each switch resets
  the route.
- A structure-defence order left a squad stationary for about 400 ticks
  before a retake moved it; not yet diagnosed.
- Single runs swing by several hundred dug-in samples; use the trial ground
  (`conquest-trial-ground.md`) across seeds before tuning any weight or dwell.
- Some squads still die on marches of a thousand ticks or more.

## Constraints

- Belief only: pressure from the defender's own influence, never hostile truth.
- Commander gains no new kind of order; the garrison is a defend-area context on
  the belt.
- Keep and other declared holds remain born garrisons.
- Deterministic; candidate belts found spatially from threatened bands, on the
  command cadence.
- `battle.fort.beltGarrison`, `battle.garrison.flow`, `battle.garrison.fromRally` and
  `battle.garrison.unifiedRally` keep same-build controls.

## Acceptance

- A quiet fort's belt holds only its token picket; the patrol pool is not
  otherwise reduced by belts.
- A fort under attack has its threatened belt stretch manned within a few
  command pulses, dug in, and released after the attack passes.
- A breached belt garrison falls back to the works rather than dying in place.
