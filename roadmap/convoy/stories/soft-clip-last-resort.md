# Brushing soft ground as the last rung

Status: IN PROGRESS — implemented behind `battle.vehicle.softBrush` (`SoftBrush`); the Burn evidence and eyes-on below remain.

Written: 2026-10-25

The model is in `convoy-nouns.md` (Brushing soft ground, and the standing law
that a vehicle never crosses hard ground). Neighbours: `slice-3-recovery-ladder.md`
owns the terminal outcome this rung comes just before; `slice-5-perf-budget.md`
asks to tell physical overlap from padding-only refusal, which this rung relies on.

## Why

After neutral pivots, order ends judged from where the hull stands, refused-goal
memory and posts with stands, what The Burn still refuses is a hull that would
fit if a corner of it could brush a bush, a crown's edge or a fence. A real
driver shoves through that. Standing still until the order is given up reads
worse than the clip.

## Owner decisions (taken)

Brush depth 0.3 of a cell; fences brushed and left standing; mature crowns soft
for medium and heavy hulls only; every hull may brush; crawl only, no scrape
damage. Fences turned out to be closed one-cell fixtures carrying an
industrial-fence prop, not closed cell edges (a closed edge between walkable
cells is a window, a building's wall and hard), so a fence is soft by its prop.

## Remaining acceptance

- The diagnostic breakdown (refusals by hull type and failure kind), read from
  one Burn run with `-Dbattle.vehicle.softBrush=false` as the control, shows
  footprint-only refusals and give-ups falling, and no brushed pose touching hard
  ground. Ask the owner before the run: the machine is shared.
- Eyes on The Burn: a brushed turn reads as a hull shoving past growth, not as a
  hull sliding through a tree.

When both are read, retire this story and the switch's losing path.

## Out of scope

- Crushing by light hulls, and any map edit beyond the fence decision.
- The terminal outcome when even this fails (`slice-3-recovery-ladder.md`).
