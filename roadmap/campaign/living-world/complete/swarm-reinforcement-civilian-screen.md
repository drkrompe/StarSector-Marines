# Rescue pressure and civilian screening — shipped

**Status:** CODE COMPLETE (2026-08-19)

**Implemented:** `ad11debf`

## Outcome

- Civilian-rescue battles now maintain roving swarm pressure instead of ending
  once the opening roster is thinned. When live runners fall below 70% of the
  opening population, the mission may add a deterministic perimeter wave after
  a six-second cadence.
- Each wave restores up to 25% of the opening roster, with a four-runner
  minimum, but never raises the live swarm above its opening population.
- Entry cells must be reachable from the shelter region, lie within the outer
  six-cell map band, avoid the shelter and lift, keep at least 14 cells from
  active evacuees and eight from marines, and space new runners apart.
- Reinforcements stop once no active rescue representatives remain. The generic
  reinforcement service stays empty; this is mission-local biological pressure.
- Escort behavior is now evaluated per civilian. Each evacuee stops without a
  live marine within five cells, so one protected representative cannot pull
  the whole cohort forward.
- An evacuee also stops when it is more than two cells closer to the lift than
  its nearest marine. When a visible enemy is within 12 cells, it instead seeks
  a walkable cell behind that marine relative to the threat, keeping the escort
  between civilian and attacker where the map permits.

## Verification

- Focused tests cover population floor/cap, safe perimeter placement, terminal
  shutoff, dedicated-factory configuration, independent civilian leashes,
  forward-lead restraint, and screened-side routing.
- The full Gradle build passes, including map and rendering regression suites.

## Manual follow-up

Play LOW/MEDIUM/HIGH rescue missions to tune the 70% floor, six-second cadence,
and 25% wave size together. The intended feel is a replenishing flow of small
packs, not an invisible spawn directly on top of the escort formation.
