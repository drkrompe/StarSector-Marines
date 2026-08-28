# Fixture-derived ambient routes

Status: IN PROGRESS

Written: 2026-08-26

Updated: 2026-08-27 — roles, jobs and shifts are built, every room a marine uses
publishes its jobs, and live fire resolves on a generated range. What is missing
is the one thing that joins the four stops: a shift is built from a single
compartment's fixtures, so a marine gets each of them on its own. The seed sweep
also remains.

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
  currently seen in `barracks-wide.png` without any authored waypoint. All four
  jobs are now published by generated rooms — rest and inspect at the berth's own
  racks and lockers, socialize at a mess seat, practice at a firing point — and
  none of them is in the same compartment as all the others. The criterion is
  therefore down to a single remaining decision: whether a shift may span
  compartments. That is a decision about the shift, not about the fill.
- A generated mech bay produces fabrication, parts-running, and inspection
  activity around its gantries, scaling with gantry count.
- ~~The live-fire window still resolves on a generated firing range: rounds are
  fired through the bounded simulation against a simulation-owned target, with
  campaign personnel and inventory untouched.~~ Done. A detail staffed onto a
  generated range fires down its own lane at a frame the scene spawned on the
  butts the fitting bound the firing point to.
- Actors do not converge on one fixture, stall against blocking fixtures, or
  leave a compartment their route did not name.
- A seed sweep produces no route that is unreachable or that never advances.

## Out of scope

New ambient activities, new poses, and combat behavior. This story changes who
authors routes, not what a route can express.
