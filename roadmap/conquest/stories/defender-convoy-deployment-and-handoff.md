# Defender Convoy Deployment and Handoff

Status: IN PROGRESS — implementation complete; live Conquest playtest remains.

Written: 2026-08-26

Read `conquest-nouns.md`, `reinforcement-nouns.md`, `convoy-nouns.md`, and
`means-dispatch-transaction.md` before implementing this story.

## Story

As the Conquest defender commander, I want ground reinforcements to enter from
my rear, unload behind the latest honestly known front, and remain under my
command while completing their relief objective, so convoy arrivals reinforce
the territorial defense instead of behaving as detached ambient patrols.

## Acceptance

- Reinforcement means report committed, rejected, or retryable dispatch
  outcomes. A convoy route failure is never logged as a success, never blocks a
  feasible later means, and never double-spends an ordinary ticket.
- A Conquest convoy enters only through the defender terminal edge: north for
  SOUTH_TO_NORTH and east for WEST_TO_EAST. Absence of a feasible rear route
  rejects convoy delivery instead of silently using a lateral or attacker edge.
- The defender commander derives each convoy's delivery hint and minimum safe
  forward band from its latest faction-local front snapshot. The selected
  drop-off remains behind known hostile pressure; without contact it falls back
  to the request's rear-shifted territorial hint.
- Route planning tries later legal rear entry/drop candidates when an earlier
  graph-connected pair fails vehicle clearance, cost routing, or turn
  feasibility. A committed result means exactly one convoy actor exists.
- The delivery hint is refreshed when the request is dispatched and then
  frozen for that vehicle journey. Mid-route destination retargeting is not
  implied.
- The first deboarded passenger creates one squad owned by MISSION_COMMAND /
  `conquest-defender`. A node-backed recapture becomes a hold commitment and a
  lost-zone response becomes a zone-clear commitment; all passengers share the
  squad, and subsequent command pulses preserve that task rather than replacing
  it with a soft track rally.
- Non-Conquest convoy deliveries retain reinforcement ownership and their
  existing entry/drop behavior.
- Headless diagnostics distinguish rejected routing, committed delivery,
  resolved entry/drop points, and the resulting squad directive without using
  hidden marine positions.

## Boundaries

Reinforcement still owns requests, tickets, means priority, and objective
identity. Convoy owns route proof, vehicle movement, deboarding, and route
locking. Conquest defender command owns only the mission-specific deployment
band and post-drop squad task. This story does not add mid-drive retargeting,
vehicle damage, terminal stuck recovery, multi-truck formations, or new
reinforcement strength scaling.
