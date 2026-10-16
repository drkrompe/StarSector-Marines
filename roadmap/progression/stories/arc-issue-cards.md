# Arc issue cards

> Each early arc teaches a way of fighting. Give each one a weapon card that
> names it, stocked where the arc happened.

Status: PLANNED — builds on shipped market listings (`progression-nouns.md`, Kit and armory).
Written: 2026-10-15

Read `campaign-kit-arc.md` (the arc map this implements), `progression-nouns.md`
(Kit and armory, including market listings) and `role-and-access.md` before
implementing.

## Problem

The Rampart revetment, the Palisade point-defence pod and the Arrestor
interceptor pack are learnable templates, but a squad is equipped by one weapon
card and one armour sheet, and no card carries any of them. A company can own
the pack and never field it.

Meanwhile the Broken Supply Route and Corvus arcs pay only credits. Their
tactical lessons have no kit that names them: holding a checkpoint, escorting a
convoy, answering mortars. `campaign-kit-arc.md` proposes one card per arc beat.
This story builds them and stocks them through market listings.

## Scope

**Four weapon cards**, each a `SquadEquipmentDoctrines` weapon doctrine of
three four-person fire teams, like Trench-Clearing Issue. A weapon card pairs
with whatever armour sheet the squad already wears. Every special named below
is an existing template.

| Card | Character | Specials across the twelve seats |
| --- | --- | --- |
| **Checkpoint Issue** | Service rifles that dig in and hold | Rampart revetments (`special.field-revetment`) and smoke (`special.smoke-grenade`); no rockets |
| **Convoy Escort Issue** | A screen that keeps rockets off the trucks | Rocket tubes (`special.rocket-launcher`) and one Palisade pod (`special.point-defence-emplacement`) |
| **Counter-Battery Issue** | Hegemony issue for living under mortar fire | Two Arrestor packs (`special.point-defence-pack`) and one anti-materiel rifle (`special.anti-materiel-rifle`) |
| **Builder's Guard** | Holding a construction site | Revetments and Palisade pods, from the Shepherd yard stores |

Exact seat counts, primaries and grades are settled on the `loadoutMatchup`
ladder, not here. Each card must earn its place against the starter control and
against Trench-Clearing Issue in at least one scenario. A card that wins nowhere
is cut or reshaped, not shipped as a trophy. Use uncarded `kit:` experiments
first, then author the card. Each card needs its name and description strings,
and a `squad-loadout-presentations` entry.

**Stocked by the arcs that teach them.** Each card enters the world through a
market listing created by its arc's existing once-only settlement:

| Card | Trigger | Listing scope |
| --- | --- | --- |
| Checkpoint Issue | Wayline opening: Relief settled | Jangala open market |
| Convoy Escort Issue | Broken Supply Route: Thunder Run settled (`SupplyRouteCampaign.settle`) | the Thunder Run employer's market |
| Counter-Battery Issue | Corvus Account: A Cold Account settled (`CorvusWaylineCampaign.settle`) | Hegemony open markets in Corvus |
| Builder's Guard | Shepherd recovery: Working Passage settled (`ShepherdRecoveryCampaign.settlePassage`) | Asharu open market |

- **Checkpoint Issue is bought, not chosen.** It stays out of *A Proper Issue*'s
  free transfer (`FirstRefit`). The free choice remains Steady Line or Forward
  Screen; Checkpoint Issue is a purchase beside it.
- **Counter-Battery Issue is a nod, not a licence.** It is listed only after the
  company fought beside Kord, and it carries Advanced kit. It needs no military
  warrant, because none of its specials is Restricted.

**Ordinary circulation stays authored.** Each card also joins its tradition's
faction pool at a modest weight, so a company that missed the arc can still find
it, and the reachability audit has a non-story owner. That means a Hegemony
licence for Counter-Battery Issue, and independent market or licence entries for
the other three. The listing is the guaranteed route; the pool is the fallback.

## Out of scope

- Mortar fire at Blind Watch and approved Hegemony kit at Garnir. Those are
  Corvus roster changes, tracked in `campaign-kit-arc.md`.
- New special equipment. Every special here already exists.

## Acceptance

- Each card's billets, specials and tier read correctly in `loadoutCardSheet`.
- A `loadoutMatchup` run is recorded in the commit message for each card
  against the starter control. Read the rows, not a verdict, since matchups are
  evidence rather than acceptance.
- A unit test per trigger: settling the arc's mission creates exactly one
  listing for its card at its scope, and a repeated settlement creates none.
- The reachability audit passes with the new pool entries.
- Every new test completes in under one second (CLAUDE.md).

## Plan

1. Matchup experiments with uncarded `kit:` sides; settle each card's shape.
2. Author the four doctrines, their strings and presentations; add pool entries.
3. Wire each settlement to `MarketListings.listAndRestock`.
4. Fold the cards into `campaign-kit-arc.md`, mark the arc map as shipped, and
   delete this story.
