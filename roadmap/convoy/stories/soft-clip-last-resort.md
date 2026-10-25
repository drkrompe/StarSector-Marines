# Brushing soft ground as the last rung

Status: PLANNED — design only; owner decisions below before implementation.

Written: 2026-10-25

Read `convoy-nouns.md` (Neutral pivot, recovery, the move-order bullets) and
`allied-command.md` (posts and stands) first. Neighbours: `slice-3-recovery-ladder.md`
owns the terminal outcome this rung comes just before; `slice-5-perf-budget.md`
asks to tell physical overlap from padding-only refusal, which this rung relies on.

## Why

After neutral pivots, order ends judged from where the hull stands, refused-goal
memory and posts with stands, what The Burn still refuses is a hull that would
fit if a corner of it could brush a bush, a crown's edge or a fence. A real
driver shoves through that. Today the hull stands still until the order is given
up, which reads worse than the clip.

## Model change

The standing rule (`convoy-nouns.md`):

> A vehicle moves under its own body or performs a bounded recovery. It never
> solves failure by crossing a wall or snapping through a corner.

becomes:

> A vehicle never crosses **hard** ground: a wall, a building, a barricade,
> water it does not float on, a tree trunk, a boulder, another hull, the map
> edge. As the last rung before an order is given up, it may **brush soft
> ground** (undergrowth it cannot flatten, the outer crown of a living tree, a
> fence or low wall along a cell edge) by at most a bounded depth at any point
> of its footprint, at a crawl, and only after every legal answer has failed.
> Dispatch never plans on it.

Every planner still reads one clearance mask. The brush is an exception granted
by the controller and the order proof, never by the mask.

## Nouns

- **Hard ground / soft ground.** A classification of what a cell or edge is
  made of, read per hull. Soft: a `CRUSHABLE` cell that is growth (not a
  boulder: the tag covers both today, so the boulder needs telling apart), a
  crown cell of a living tree outside its trunk (`CreepVegetation.TRUNK_RADIUS`),
  and a closed shared edge between two walkable cells (a fence). Everything else
  closed is hard. For a hull that already crushes undergrowth
  (`VehicleType.crushesUndergrowth`) that growth is open ground, not soft.
- **Brush depth.** How far the footprint may reach into soft ground: one
  number, proposed 0.3 cells. It is enforced as two checks with the existing
  footprint test, not a new geometry: the **full** footprint must clear a view
  of the map with soft ground opened (so nothing hard is touched at all), and the
  footprint **inset by the brush depth** on every side must clear the real map
  (so soft ground is touched by no more than that depth). The sweep runs both and
  keeps the shorter proven travel.
- **Brushing.** The state of a hull on this rung: it crawls (proposed 0.3 of top
  speed), and leaves the ground as it was (no map edit: a vehicle does not edit
  the battlefield, except the blade and the tracks).

## Where it applies

1. **The controller, last.** After the wheeled last-resort pivot window, while
   a move order is still within its give-up time, the hull's sweeps and its
   pivot or corridor tracking read the brush checks instead of the strict one.
   The rolling forward planner is asked again at its cadence and takes the hull
   back the moment a strict plan exists. Proposed: from 6 s of braked failure,
   give-up at 8 s unchanged.
2. **The order proof, last.** When an order's route search ends without a route
   and the failure was footprint-only (`straight-clips` or `arc-clips` in the
   diagnostic's words, not a disconnected goal or a turn the geometry cannot
   make), one more refinement of the best route uses the brush checks. A route
   accepted this way is marked, so the controller brushes along it from the
   start instead of waiting out the window.
3. **Never** for dispatch, docking, the departure turnaround, or allied
   command's choice of stand: a plan that is only possible by brushing is not
   one the commander should prefer.

## Owner decisions

1. **Brush depth.** 0.3 cells (a corner over a bush) or larger (0.5: half a
   cell, a hull visibly in the hedge).
2. **Fences.** Brushed and left standing, or broken through the destruction
   path (`MapEditor`, which can only open ground) when a hull brushes one.
3. **Mature crowns.** Soft for every hull, only for medium and heavy hulls, or
   never (growth only).
4. **Which hulls.** Every hull, or wheeled and hover only (tracks already crush
   growth, and pivot).
5. **What it costs.** Crawl only, or a small scrape to structure per brushed
   second as well.

## Acceptance

- A unit test per check: the inset footprint brushes soft ground up to the
  depth and no further; the full footprint never touches hard ground, whatever
  the depth.
- The diagnostic breakdown (refusals by hull type and failure kind), read from
  one Burn run with `-Dbattle.vehicle.softBrush=false` as the control, shows
  footprint-only refusals and give-ups falling, and no brushed pose touching hard
  ground.
- Eyes on The Burn: a brushed turn reads as a hull shoving past growth, not as a
  hull sliding through a tree.

## Out of scope

- Crushing by light hulls, and any map edit beyond the fence decision.
- The terminal outcome when even this fails (`slice-3-recovery-ladder.md`).
