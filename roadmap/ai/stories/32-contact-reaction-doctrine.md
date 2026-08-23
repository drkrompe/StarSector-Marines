# Story 32 — Contact-Reaction Doctrine

**Status:** IN PROGRESS — implementation complete; playtest deferred

**Written:** 2026-08-23

**Updated:** 2026-08-23 — selected-squad doctrine visibility and dump contract

## The player-visible story

A marine squad advancing through lethal terrain takes fire on one side. The
hit fireteam immediately gets out of the lane while its sibling fireteam
plants and returns fire. A squad that enters or re-enters direct contact, or
crosses its morale break point, changes posture now rather than continuing an
obsolete order for up to two seconds. Squads react to what they have actually
seen or heard; an unobserved unit elsewhere on the map cannot tighten a patrol
leash or stop an advance.

The result should read as one squad making a coordinated decision, with each
fireteam doing a different part of it.

## Why this slice exists

Infantry time-to-kill is short enough that the periodic two-second GOAP refresh
is useful maintenance but too slow to be the primary contact reaction. The
perception system, stable fireteams, and belief-derived influence maps now
provide the right inputs, but three seams still undermine them:

1. entering direct contact and morale transitions do not interrupt a fresh
   plan;
2. objective-advance and guard-post threat checks still read hidden live units;
3. `BreakLOS` moves the whole squad even when only one fireteam is exposed.

## Doctrine and authority

- **Local squad reaction** uses the squad's immutable contact belief. Remembered
  cells and confidence may inform a decision; current hidden positions may not.
- **Fresh squad-wide direct contact, alert transitions, incoming-fire onset,
  casualties, and morale break/clear transitions** are tactical interrupts.
  The existing periodic refresh remains the convergence and cleanup path. A
  fresh contact means the first direct sighting after a tick with no direct
  LOS; additional hostile identities entering sight during continuous contact
  are folded into the current engagement instead of repeatedly interrupting
  the planner.
- **Fireteams are the coordination unit.** On a recoverable ambush, exposed
  teams displace while unexposed sibling teams hold and cover. A lone team, or
  a squad whose every team is exposed, all displaces.
- **Commander influence** remains a coarse command-layer input. Player vision
  remains rendering state and is never an AI sensor.

## Local reaction slice

### 1. Event-driven replanning

Publish one-tick squad flags for a newly started direct-contact episode,
alert-level change, and morale broken-state change. Consume them in the squad
replan gate alongside the existing incoming-fire edge and casualty check.

### 2. Honest threat reads

Make advance threat and guard-post leash scoring consume identified squad
contacts at their believed cells, weighted by confidence. Live posture may be
read only for a direct contact observed on the current tick. Friendly force is
known exactly within the squad's own faction.

### 3. Team-scoped ambush recovery

At `BreakLOS` role assignment, map incoming shot endpoints to exposed
fireteams. Assign those teams `displace:*`; assign remaining teams `cover:*`.
Cover teams stop and remain eligible for opportunity fire. The shared step
completes when every living displacer reaches a fallback cell; cover-team
arrival is not part of the completion gate.

## Acceptance

- The first direct sighting, or reacquisition after a no-LOS tick, replaces an
  otherwise-fresh plan in the same simulation tick. Additional hostile
  identities seen during continuous squad contact do not trigger another
  immediate replan.
- Crossing into or out of broken morale replaces an otherwise-fresh plan in
  the same simulation tick.
- Hidden, unremembered enemies contribute nothing to advance or defensive
  leash threat; remembered contacts contribute at their last believed cell and
  confidence.
- With two intact fireteams and fire landing on one, only that team receives a
  displacement role and the sibling receives a cover role.
- A cover member holds position and can use the normal opportunity-fire seam;
  it cannot finish the step while a displacer is still exposed.
- Existing periodic replanning, single-team ambush recovery, and open-terrain
  least-exposed fallback behavior remain intact.

## Contact-picture and doctrine slice

This slice turns the squad's individual contact memories into one immutable
tactical picture published before planning. It answers the questions a squad
leader needs without granting omniscience: where the believed threat lies
relative to the squad's current axis, whether a freshly observed contact is
closing or withdrawing, and how believed hostile strength compares with
nearby known friendlies.

The picture classifies contacts as front, flank, or rear and chooses a dominant
sector. Motion is reported only when consecutive direct observations support
it; remembered or audio-only contacts remain motion-unknown. Local hostile
strength is confidence-weighted, while friendly strength uses exact same-side
positions. The squad then derives a sticky `ADVANCE`, `HOLD`, or `DISENGAGE`
doctrine from its posture, contact geometry, motion, and local force balance.
Small score changes must not make the doctrine oscillate each tick.

Advancing squads use the doctrine to decide whether to press, establish a
contact line, or break away from a badly compromised approach. Defending
squads prefer to hold their assigned ground, but can disengage when they are
locally overmatched and the assignment is not a must-hold order. Explicit
morale-break survival behavior remains authoritative.

### Acceptance

- The published contact picture is immutable and derived from squad beliefs;
  an unobserved enemy does not appear in its count, sector, or force balance.
- A stable movement or objective axis divides believed contacts into front,
  left/right flank, and rear sectors; the dominant sector is deterministic.
- Approach/withdraw motion is produced only from consecutive fresh direct
  observations and becomes unknown when the track is stale or audio-only.
- Friendly-to-hostile local odds account for confidence and nearby support,
  and expose a coarse favorable/even/unfavorable assessment to planning.
- Doctrine has hysteresis and produces `ADVANCE`, `HOLD`, or `DISENGAGE`
  decisions that differ appropriately for advancing and defending squads.
- Advance and defensive engagement behavior consume the doctrine instead of
  independently reconstructing a partial threat picture.
- Diagnostics expose the picture and selected doctrine for test and later
  playtest inspection.
- Automated tests cover sector boundaries, motion freshness, force balance,
  doctrine hysteresis, and advancing-versus-defending reactions.

### Deferred validation

- Tune the advance/hold/disengage thresholds from the parallel player-facing
  playtest pass. Automated verification covers the implementation contract,
  but is not a substitute for combat-feel acceptance.

## Selected-squad visibility slice

The selected-squad GOAP detail must make the contact decision inspectable
without requiring a dump. It shows the selected doctrine beside the posture
and force balance that shaped it, then the dominant sector, fresh motion,
direct/remembered contact counts, local strengths, tactical axis, and primary
believed contact. The world overlay extends the existing believed-contact
ghosts with a short doctrine-colored axis from the squad centroid, so a front,
flank, or rear classification can be checked spatially.

The manual squad dump remains the durable offline diagnostic. Its
`contactPicture` object carries the same published snapshot plus picture age,
hostile-to-friendly ratio, doctrine-transition flag, resolved primary-contact
identity, and primary evidence freshness. Presentation does not reconstruct a
new threat score or read hidden hostile positions.

### Acceptance

- Selecting a living squad exposes doctrine, posture, balance, dominant
  sector, primary motion, contact counts, strengths, axis, and primary belief
  near the top of the GOAP detail panel.
- `ADVANCE`, `HOLD`, and `DISENGAGE` use distinct, stable debug colors without
  changing the decision thresholds.
- A selected squad publishes a bounded doctrine-colored tactical-axis trace;
  deselection or squad loss clears it with the other debug-only highlights.
- Believed-contact ghosts remain confidence/source-coded and no presentation
  path resolves an unknown enemy's current cell.
- The DUMP button serializes the full published contact picture, its age and
  ratio, the transition flag, and primary belief evidence in a test-covered
  JSON shape.

## Parked follow-ons

- Mechanical suppression and a commander consumer for the influence field.
- Cross-squad briefing and reserve commitment.
- Player-facing doctrine visualization and tuning playtests.
