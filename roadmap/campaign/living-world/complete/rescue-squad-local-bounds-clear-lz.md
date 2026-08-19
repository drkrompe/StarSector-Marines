# Squad-local rescue bounds and clear lift footprint — shipped

**Status:** CODE COMPLETE (2026-08-19)

**Implemented:** `5a0da48d`

## Outcome

- Mobile rescue squads now own independent route-progress and five-second
  contact-bound timers. An engaged squad only enters the slower two-cell
  advance while one of its members has a live alien within twelve cells.
- Unpressured squads continue toward the normal five-cell forward screen even
  while another squad is fighting, preventing one contact from stutter-stepping
  the entire escort force.
- Lift objectives now use a two-cell radius, producing a 5x5 trigger area.
  Placement accepts a center only when all 25 cells are in bounds, walkable,
  outdoors, and not doorways, so the shuttle/objective cannot overlap a wall or
  building interior.
- Production-sized maps retain the thirteen-cell pickup formation radius and
  fifteen-cell edge inset. Small test/scenario maps scale the formation and
  inset together while preserving the complete five-point formation.

## Verification

- Regression coverage proves a locally pressured squad no longer throttles an
  unrelated mobile squad.
- Placement coverage proves a building-overlapped 5x5 candidate is rejected and
  every cell of the replacement footprint is walkable and outdoors.
- The full Gradle suite passes: 1,821 tests, zero failures.

## Manual follow-up

Play a rescue with two separated contact fronts and confirm one squad can bound
under pressure while the other closes normally. Check several generated maps
for shuttle clearance and tune only the existing timing/cadence feel if needed.
