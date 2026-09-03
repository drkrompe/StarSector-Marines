# A lane rung is dropped for reasons that are not about the ground

Status: PLANNED — two silent defects in lane seeding, both on `main` today,
both found while measuring `lane-fan.md` and neither caused by it.

Written: 2026-09-02

## What is wrong

Both canonical Conquest fixtures seat eight of their nine lane rungs. The
missing one is the middle lane's, on both, and neither defect below has
anything to do with whether there is room.

**`LANE_SEED_SEPARATION` never applies.** Lane rungs are meant to be allowed
closer to each other (32 cells) than ordinary precinct seeds are to anything
(`MIN_SEED_SEPARATION`, 60 at Conquest scale) — a ladder of outposts up one
route is supposed to be able to be a ladder. But a seated rung is appended to
the plan's own `taken` list as well as to the lane's, so the 60-cell rule finds
it there first and always dominates. The lane separation is dead code. The
middle lane is the shortest and so loses its rung first, which is why the
symptom is the same on both fixtures.

**The seed's jitter window is clamped to the lane's own lateral third.** A rung
whose fitted position sits near the edge of its third — or outside it, which a
bent path can produce today and a future derivation will produce more often —
gets a window of a few cells to jitter in and is refused. The window should be
bounded by the map margin, which is the real constraint; the third is where the
lane is aimed, not a wall.

## The model

Hold the pre-lane seeds and the lane seeds as two lists and test a candidate
against each at its own separation, rather than merging them and testing once at
the stricter of the two. Bound the jitter window with the map margin.

Measured under `lane-fan.md`'s tree, that seats all nine rungs on both canonical
fixtures.

## The reason this is a story and not a fix in passing

Seating a ninth rung puts another compound on both canonical maps, which is a
balance change to the thing every Conquest reading is measured against. It owes
a full both-ways matrix before it lands, the same as any other change to what
the map holds. `commanderEvidence -Pmission=conquest` on the current `main` is
the control.

## Acceptance

- `PrecinctLanePlanTest` pins that the two separations are distinct and that a
  rung may stand closer to its own ladder than to a settlement.
- Both canonical fixtures seat all nine rungs; a rung that is still dropped
  reports itself by name, as now.
- The full Conquest matrix against `main`, recorded here. Captures and held
  compounds may move — a tenth compound on the map is a real change — but the
  reading must be understood rather than merely accepted.
