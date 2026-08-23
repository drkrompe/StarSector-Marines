# Company View — Next Session

## Where we are

Track opened 2026-08-22 from an inventory of the shipped squad AI and
commander ("squad of squads") tiers. **Design stage: ten stories
contracted (C1–C10), none started, no code written.**

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

[C10 — The company between contracts](stories/c10-company-between-contracts.md)
is unblocked too, and is the one story here that ships player-visible value
without C2 first: two of its three panes (standing, running deadlines) read
state that is already persisted or already computed, and its third pane is
the reserved space C3 and C4 later furnish. It also gives
[G32](../campaign/contracts/complete/g32-player-event-popup.md)'s **Hold**
option somewhere to go — today a deferred event popup dismisses into nothing.
Its slice 1 is a spike: confirm a `TOGGLE` ability with a no-op `activate()`
fires `pressButton()` without latching, before anything is built on top.

[C7 — Organization and ranks](stories/c7-organization-and-ranks.md) is also
unblocked and is worth doing early: it settles the language and the command
scope every UI story renders, and doing it *after* the cards exist means
rewriting them. With no saves to preserve it is now a clean edit — the
enum can change outright.

## Decisions taken (2026-08-22)

1. **A card is one officer's command — the company.** The named officer
   commands the company; its squads are led by NCOs who are not modelled as
   officers. The rank ladder is incoherent for that role today (a Private
   capped at 5 marines, a starting Sergeant capped at 42 because seven
   six-marine teams had to fit), so it changes: officer ranks denominated
   in squads, a separate enlisted ladder, and an explicit squad leader.
   That is [C7](stories/c7-organization-and-ranks.md).
2. **A lift carries at least one whole fire team, and a split squad stays
   one squad.** Capacity is denominated in four-marine teams — only a
   Valkyrie lands a whole squad in one pass — and late arrivals join their
   squad and catch up rather than forming a new unit. That is
   [C8](stories/c8-lift-capacity-and-multi-pass-drops.md), and it settles
   C1's open split-lift question.
3. **A squad is twelve marines in three fire teams of four.** *Revised
   from six.* Progression S1 shipped a **9x infantry lethality scale**
   (pulse rifle vs an unarmored marine: ~30 s to ~3.3 s), so six puts a
   squad past `SquadFallbackSystem`'s trigger ratio within seconds and
   leaves the shipped two-team bounding overwatch nothing to split. Fire
   teams are modelled but behind the scenes — the AI's maneuver element
   ([C9](stories/c9-fire-teams-as-the-maneuver-element.md)), not a card
   level. Lifts are denominated in teams, so only a Valkyrie lands a squad
   intact and an assembling squad forms up at its LZ before advancing.
4. **Large organizations degrade, never get forbidden.** Past ~8 squads the
   UI compacts, past ~20 it paginates within the officer grouping, and the
   off-screen count is always stated. C3 and C5 carry the tiers.

5. **Save compatibility is not a constraint.** Nothing is shipped to
   anyone and there are no saves to preserve, so `Rank` can be edited
   freely — no legacy alias map, no migration slice. (The mechanism still
   matters once saves exist: xstream writes enums by name, so deleting a
   constant is the breaking direction; adding a field is the safe one.)
6. **The company is reachable from the campaign map, not from a planet.**
   The between-contracts surface gets an ability-bar button opening the
   planet-free host [G32](../campaign/contracts/complete/g32-player-event-popup.md)
   shipped. The ability bar is already the campaign-only HUD element, so it
   hides itself whenever a core tab, dialog, or menu opens and needs no
   visibility gate of ours. Checked while deciding: `ArmoryScreen` has zero
   market/planet references and `StationingWithdrawalService.withdraw` takes
   no planet, so most roster work was gated by where its button sits, not by
   any fiction. That is [C10](stories/c10-company-between-contracts.md), and
   it supersedes C4's slice 3.
7. **Take C8's capacity change now, re-tune after.** The opening ladder's
   force ratios are mid-playtest against today's seats, and raising the
   floor moves them 50–100% — tuning against numbers we intend to replace
   is wasted work. Flagged in
   [`../campaign/early-operations/next-session.md`](../campaign/early-operations/next-session.md).

### Consequences worth knowing before starting

- **Scale is governed at the source, not by the UI.** The officer rank cap
  and the lift capacity together bound what reaches one battle — a dozen
  squads, realistically. Hundreds of squads is a state neither the meta
  game nor the HUD can carry, so future cap numbers get picked with that
  ceiling in mind. The *roster* still outgrows it, which is why the fleet
  view paginates and the battle view does not need to.
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
