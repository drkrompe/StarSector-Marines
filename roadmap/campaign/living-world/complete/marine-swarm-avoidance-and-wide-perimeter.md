# Marine swarm avoidance and wide pickup perimeter — shipped

**Status:** CODE COMPLETE (2026-08-19)

**Implemented:** `c6589910`

## Outcome

- Allied marine, regular, and militia infantry now receive a local repulsion
  steer from hostile `ALIEN` and `SWARM_RUNNER` bodies within five cells.
  Conventional soldiers, structures, civilians, and mechs do not trigger or
  consume this behavior.
- Avoidance runs after authored path movement rather than replacing the squad's
  route or objective. One alien at moderate range slows a direct advance;
  several aliens in the same direction add pressure up to a 2.5-cells/second
  cap and can produce a controlled backstep.
- The steer uses live alien positions, contributes to the marine's applied
  movement velocity, and applies the same full/X-slide/Y-slide walkability
  guard as body separation. It cannot push infantry through walls or map edges.
- The five pickup squads now occupy a thirteen-cell-radius star. On normal
  rescue maps its anchor bounds are 25x25 cells, and the LZ center sits at
  least fifteen cells from the nearest edge so the complete perimeter fits.
- Small synthetic maps scale the radius and inset down together rather than
  losing rescue placement. The initial swarm exclusion follows that realized
  radius, keeping runners out of the wider defense footprint.

## Verification

- Focused tests cover idle withdrawal, a slowed single-contact advance,
  density-driven retreat, non-alien exclusion, boundary safety, production-loop
  wiring, exact production formation radius, and 25x25 anchor spans.
- `gradlew.bat test` passes 1,804 root tests plus the asset-pipeline test
  (1,805 total).

## Manual follow-up

Play the rescue mission at all three risks and watch for oscillation when a
runner repeatedly enters and leaves the five-cell bubble. Tune the radius or
2.5-cells/second cap only from that feel pass; the current shape deliberately
lets one runner slow rather than completely halt a healthy advance.
