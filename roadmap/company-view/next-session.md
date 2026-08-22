# Company View — Next Session

## Where we are

Track opened 2026-08-22 from an inventory of the shipped squad AI and
commander ("squad of squads") tiers. **Design stage: six stories contracted
(C1–C6), none started, no code written.**

Read [`overview.md`](overview.md) first — it holds the inventory of what
exists today and the seven design commitments the stories assume.

## The finding that created this track

Campaign fireteam identity does not survive deployment:

- `CampaignMarineDeployment.freeze` flattens selected fireteams into a flat
  `List<MarineLoadout>` seat list.
- `MarineLoadout` carries `campaignSoldierId` but **no fireteam id**.
- `InfantryPayload.tryDeploy` mints one battle `Squad` per *shuttle
  mission*, so a battle squad is "whoever rode this dropship."
- `ShuttleType.AEROSHUTTLE` / `KITE` carry 4 seats against a 6-marine
  `MarineSquad.CAPACITY`, so lifts split teams by arithmetic.

Everything that wants to show a *deployed* force under its real names is
blocked on that seam. That is [C1](stories/c1-fireteam-identity-through-the-drop.md).

## Recommended pickup

**[C1 — Fireteam identity through the drop seam](stories/c1-fireteam-identity-through-the-drop.md).**
No dependencies, small, and it unblocks C5 and C6. Slice 1 (fields +
deterministic freeze ordering) is pure plumbing verifiable by unit test.

[C2 — Formation model](stories/c2-formation-model.md) is equally
unblocked and can land in parallel; it is the prerequisite for all three UI
stories and its second slice (rewire `SquadDeploymentScreen`'s counts, no
visible change) is a safe proof.

## Decisions to make before the UI stories

1. **Is one card a captain's command, or the whole company?** The overview
   commits to *captain's command*; the alternative is one card with the
   captain as a fireteam badge, which is better for a one-captain early
   game. C2 models both so this stays a C3 rendering decision.
2. **Split-lift teams: one battle squad or two?** C1 leans one squad per
   (fireteam, landing zone), with `(A)`/`(B)` suffixes across LZs, because
   a single squad spanning two landings would let leader-pull cohesion drag
   members across the map.

## Boundaries worth restating

- **No orders.** `Selection` stays view-only in this track. The card is the
  obvious future order surface, but `Squad.assignedObjective` is owned by
  the `MissionCommand` tier and a player channel into it needs its own
  design pass.
- **S8 owns the leaf row.** Progression
  [S8](../progression/stories/s8-roster-legibility.md) defines what a
  single marine row says (aptitude bars, XP tiers, traits, career) and the
  per-role type scale. This track owns the grouping and reserves the space.
- **Nothing new gets persisted.** `MarineRoster` stays authoritative; every
  rollup here is derived.
- **Contracts track is live.** C4 reads stationing state that G31/G32 are
  actively reshaping — read
  [`../campaign/contracts/next-session.md`](../campaign/contracts/next-session.md)
  before touching term/deadline fields.

## Commit chain

_(none yet)_
