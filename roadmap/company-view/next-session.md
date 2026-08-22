# Company View — Next Session

## Where we are

Track opened 2026-08-22 from an inventory of the shipped squad AI and
commander ("squad of squads") tiers. **Design stage: eight stories
contracted (C1–C8), none started, no code written.**

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

[C7 — Organization and ranks](stories/c7-organization-and-ranks.md) is also
unblocked and is worth doing early: it settles the language and the command
scope every UI story renders, and doing it *after* the cards exist means
rewriting them. Its slice 0 needs a user decision on save compatibility.

## Decisions taken (2026-08-22)

1. **A card is one officer's command — the company.** The named officer
   commands the company; its squads are led by NCOs who are not modelled as
   officers. The rank ladder is incoherent for that role today (a Private
   capped at 5 marines, a starting Sergeant capped at 42 because seven
   six-marine teams had to fit), so it changes: officer ranks denominated
   in squads, a separate enlisted ladder, and an explicit squad leader.
   That is [C7](stories/c7-organization-and-ranks.md).
2. **A lift carries at least one whole squad, and a split squad stays one
   squad.** Capacity is denominated in squads with a floor of six; larger
   hulls carry multiple squads or a squad plus equipment; late arrivals
   join their squad and catch up rather than forming a new unit. That is
   [C8](stories/c8-lift-capacity-and-multi-pass-drops.md), and it settles
   C1's open split-lift question.
3. **Squad size stays 6.** Between a real fire team (4) and a real rifle
   squad (12+); chosen so the group count stays readable and every
   transport can carry one.
4. **Large organizations degrade, never get forbidden.** Past ~8 squads the
   UI compacts, past ~20 it paginates within the officer grouping, and the
   off-screen count is always stated. C3 and C5 carry the tiers.

### Consequences worth knowing before starting

- **C7 slice 0 is a save-compatibility decision**, not code. `Rank` is a
  persisted enum and xstream writes enums by name, so deleting constants
  breaks existing saves. Either alias the legacy names on load or take a
  deliberate save break — decide with the user first.
- **C8 slice 1 is a live balance change.** Raising the capacity floor lifts
  every early-game transport by 50–100%; the two Independent opening jobs
  are mid-playtest and their force ratios assume today's seats. Read
  [`../campaign/early-operations/next-session.md`](../campaign/early-operations/next-session.md)
  before touching capacities.
- `AirSystem.java:471` resets `mission.squadId` per cycle today, so every
  reinforcement wave currently mints an unrelated squad. C8 slice 2 scopes
  that; C5 must not paper over it with display-only grouping.
- The rejoin behaviour C8 needs is mostly already there —
  `RegroupPosture` + `InfantryCohesion.cohesionOverride`. Extend that
  layer; a second cohesion mechanism would fight the first.

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
