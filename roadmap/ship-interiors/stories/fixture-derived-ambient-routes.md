# Fixture-derived ambient routes

Status: IN PROGRESS

Written: 2026-08-26

Updated: 2026-08-27 — roles, jobs and shifts are built and the vehicle bay is
staffed from its own fixtures. What remains is the barracks rotation, the
live-fire window on a generated range, and the seed sweep.

Read `ship-interiors-nouns.md` before implementing this story. Depends on
`facility-room-themes.md`.

Derive crew routes from placed fixtures. `AmbientTaskService` is already a
generic executor — it runs any route it is handed — but the only route authors in
the codebase are the two hand-written scene layouts, where every waypoint is a
literal float tied to a literal prop coordinate. A generated compartment
currently has no way to be inhabited, and this is the single gap between a
generated room and a dead one.

## Scope

- A route author that reads a compartment's fixtures and their declared
  affordances and emits stops: berths become rest, gantries become work with a
  weld focus, firing lanes become practice, consoles and racks become inspection,
  lounge fixtures become socializing.
- Per-actor variation from the compartment's own random stream: differing loop
  lengths, phase offsets, and fixture assignments, so a crew reads as a rotation
  rather than a queue.
- Threat policy stays a parameter of the route, so the same author serves a home
  deck that admits no combatants and a prize deck that does.

## Constraints

- No hand-listed waypoints per deck or per compartment. If a room needs literal
  coordinates to feel alive, the affordance model is incomplete — fix the model.
- Routes must not path through fixtures that block, and must not require a hatch
  that a bulkhead stage did not author.
- Determinism holds: the same deck and seed produce the same assignments and
  phases.

## Acceptance

- A generated barracks produces the rest, socialize, inspect, practice rotation
  currently seen in `barracks-wide.png` without any authored waypoint.
- A generated mech bay produces fabrication, parts-running, and inspection
  activity around its gantries, scaling with gantry count.
- The live-fire window still resolves on a generated firing range: rounds are
  fired through the bounded simulation against a simulation-owned target, with
  campaign personnel and inventory untouched.
- Actors do not converge on one fixture, stall against blocking fixtures, or
  leave a compartment their route did not name.
- A seed sweep produces no route that is unreachable or that never advances.

## Out of scope

New ambient activities, new poses, and combat behavior. This story changes who
authors routes, not what a route can express.
