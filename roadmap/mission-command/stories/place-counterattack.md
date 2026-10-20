Status: IN PROGRESS

Written: 2026-10-20

# Place counter-attack: informed, for any place worth retaking

## Why

The fort counter-attack (`conquest-command.md`, Defender command) launches in
six battles of nine on the trial ground and retakes once. Every push into a
consolidated hold breaks at a sixth to a third of its strength, because the
group sizes itself from what it believes, and from a staging cell ninety cells
short of the fort it cannot see the holders: it believes it needs the floor of
twelve while it faces about three times that.

It is also fort-only, and the owner's direction is that **the game is much larger
than forts**. A side loses compounds, objectives, held buildings, aprons, trench
stretches, capture points and story-mission sites far more often than it loses a
fort, and the same law — retake in proportion or not at all, never fed piecemeal
— applies to every one of them.

## Scope

1. **Generalise.** `FortCounterattack` becomes `PlaceCounterattack`, a planner
   over *places*: anything a side holds that it can lose and might retake. A
   place supplies its centre, its ground radius, how it stands (held, losing,
   lost), whether its retake is under way, what it is worth to the side, and how
   a group is ordered at it. A fort is one case (its critical aspect, the massed
   commit at that aspect); a compound is another (its capture cell); a story
   site is a third. The place is valued by what it means to the side — a
   mission objective, its own worth, nearness to the keep or home — not by
   being a fort.
2. **Inform it.** Before a group commits, it establishes what holds the place.
   A **probe** — one member of the group, or any own squad, recon car or drone
   flight already standing within observation range — closes to an
   **observation post** short of the place and watches. The group sizes itself
   from the **observed holders** with a margin, plus a weight for the side's
   own **remembered losses** on that ground, raised for each failed attempt;
   then it either raises enough and launches only when the strength stands at
   the staging cell, or does not launch: it keeps waiting, re-observes once the
   estimate is stale, and after the bound gives the place up. A repulse writes
   what the push saw at its peak into the estimate. Belief only, never hostile
   truth: holders are believed contacts near the place, losses are the side's
   own casualty memory.
3. **Keep the laws.** Reserve law (the reserve is the counter-attack's, counted
   in the law's share), pickets before massing, the lost-cause rule, never fed
   piecemeal; a turned place's ground is the counter-attack's alone.
4. **Give other commanders the shared mechanism where it fits.** Conquest's
   defender for forts and compounds; Relay Nine and Thunder Run, whose finite
   counter-attack pool today converges on the lost site squad by squad.
   Junction's and the depot Counterattack's retakes are authored story beats
   with tuned timing; the story records whether they move to the shared
   mechanism.

## Constraints

- Belief only. Holders are believed contacts; losses are `lossesAt`.
- No fort-only mechanics: anything built here works for a compound or a site.
- Same-build control: `-Dbattle.counterattack=false` (the old
  `battle.conquest.fortCounterattack` switch keeps working as an alias).
- New unit tests under a second each; battles are evidence.

## Acceptance

- Unit tests: valuation, the estimate (observed holders, losses, failure
  growth, staleness), probe selection and dwell, launch gate.
- A behaviour scene on a **non-fort** place (a lost compound): the probe goes
  first, the group is sized from what it observed, it launches only with that
  strength at the staging cell, and a repulse raises the estimate. Mechanics
  only; who wins is a metric.
- `fort-counterattack` and every garrison scene stay green.
- `commanderEvidence` against the same-build control on Conquest and at least
  two other mission types: launches, retakes, timeouts, losses.

## Plan

1. Design law in `garrisonables.md`, `conquest-command.md`, `ai-nouns.md`;
   this story.
2. `PlaceCounterattack` with places, value and the informed estimate; fort and
   compound places for the Conquest defender; tests; `fort-counterattack`
   updated.
3. Scene `place-counterattack` on a lost compound.
4. Relay Nine / Thunder Run on the shared mechanism.
5. Evidence against the control; fold.
