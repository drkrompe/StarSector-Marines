# C7 — Organization and ranks

> A Private commanding five marines and a Sergeant commanding forty-two are
> both incoherent. Rank-and-file marines have no rank at all, and nothing
> models the person actually leading a squad.

**Status:** not started. No hard dependencies; pairs with
[C2](c2-formation-model.md) and settles the language
[C3](c3-company-card-stack.md) renders.

## Decision this story implements

**The named officer is the company commander.** Squads are led by their own
NCOs, who are not modelled as officers. Where the current rank ladder makes
that incoherent, the ladder changes.

## Problem

- `Rank` is one ladder — PRIVATE, CORPORAL, SERGEANT, LIEUTENANT, CAPTAIN,
  MAJOR, COLONEL, GENERAL — applied to `MarineCaptain`, the named officer
  character. Its `squadCap` is a **marine** count (5, 10, 42, 80, 160, 320,
  640, 1280) and `fireteamCap()` divides it by 6.
- A starting officer is a **Sergeant** with a 42-marine cap, because the
  enum comment needed "the full opening company: seven six-marine
  fireteams" to fit. That is a cap chosen to satisfy a UI constraint, and
  it puts an NCO rank in an officer's role.
- `MarineSoldier` carries **no rank**. Aptitude and XP only. Nothing
  distinguishes a marine on their first contract from one on their
  fortieth in the way the fiction would.
- Nothing models a **squad leader** in the campaign. The battle tier has
  `Squad.leaderId` (promotable on death, drives leader-pull cohesion), and
  it is seeded arbitrarily — whichever member spawns first.
- The UI says "fireteam" while the class is `MarineSquad` and the thing has
  six members. Six is a squad-sized element, not a fire team (four).

## Design

### One word for the unit: squad

The persistent six-marine element is a **squad** in all display language.
The class is already `MarineSquad`; this mostly retires the word "fireteam"
from UI strings and renames `Rank.fireteamCap()` → a squad-denominated cap.

Six is a deliberate middle: a real fire team is four and a real Marine
rifle squad is twelve-plus. Six keeps the group count low enough to read
(a 60-marine company is ten rows, not fifteen) and gives every transport a
sane minimum lift ([C8](c8-lift-capacity-and-multi-pass-drops.md)).

### Two ladders, not one

**Enlisted** — new field on `MarineSoldier`, nullable:

| Rank | Role |
| --- | --- |
| `MARINE` | rank and file |
| `LANCE_CORPORAL` | senior marine, second in the squad |
| `CORPORAL` | squad leader |
| `SERGEANT` | senior squad leader; the officer's right hand |

**Officer** — replaces the current ladder on `MarineCaptain`, and caps
command in **squads** rather than marines:

| Rank | Commands | Scope |
| --- | --- | --- |
| `LIEUTENANT` | 3 squads | a platoon — the starting officer |
| `CAPTAIN` | 6 squads | a company |
| `MAJOR` | 12 squads | two companies |
| `LT_COLONEL` | 24 squads | a battalion |
| `COLONEL` | 48 squads | — |

Numbers are a first pass; the shape is what matters. Today's ladder doubles
its cap each tier and the XP thresholds double with it — keep that curve,
just denominate it in squads. The player starts with 10 marines
(`bootstrapInitialComplement(10)`), so a 3-squad Lieutenant is the right
opening ceiling: reachable, and the first promotion visibly widens it.

Multiple officers then means the player's organization has grown past one
company — which is exactly the merc-company-becomes-an-institution arc the
campaign is already telling. One card per officer stays coherent at every
size.

### Squad leaders

- `MarineSquad` gains `leaderSoldierId`.
- Assigned on squad creation and re-assigned when the leader is lost:
  prefer the highest enlisted rank, then the most experienced.
- The leader's enlisted rank is the squad's; promoting a marine to
  `CORPORAL` is what makes them a leader, not a separate flag.
- At deploy, the campaign squad leader seeds the battle `Squad.leaderId`
  through C1's seat data, so leader-pull cohesion follows the person the
  player thinks is in charge — and so a leader death in battle is a named
  event rather than an invisible id swap.

### Save compatibility — do this first

`MarineCaptain.rank` is a persisted enum. xstream writes enums **by name**,
so deleting `PRIVATE` / `CORPORAL` / `SERGEANT` / `GENERAL` from `Rank`
makes an existing save fail to deserialize the roster. Options, in
preference order:

1. **Legacy alias map on load** — keep the old names resolvable and map
   them to the nearest new officer rank (`PRIVATE`/`CORPORAL`/`SERGEANT` →
   `LIEUTENANT`), applied once when the roster script loads.
2. **Accept a save break** if the user is happy to start fresh — cheapest,
   and honest for a mod still in development.

The new `MarineSoldier` rank field is additive: an old save simply has no
element for it, so it must tolerate null and backfill (`MARINE` for
everyone, `CORPORAL` for whoever becomes the squad leader).

## Slices

0. **Save-compat decision + mechanism.** Nothing else lands until this is
   settled, because it determines whether the enum can be edited freely.
1. **Officer ladder in squads.** New constants, caps denominated in squads,
   `fireteamCap()` retired. Consumers: `MarineRoster` (four call sites),
   `CaptainDeploymentPolicy`, `ArmoryScreen`, `BriefingScreen`,
   `SquadDeploymentScreen`, `StationingScreen`.
2. **Enlisted ranks + squad leader.** Field on `MarineSoldier`,
   `leaderSoldierId` on `MarineSquad`, promotion-on-loss rule, backfill.
3. **Display language sweep.** "Fireteam" → "squad" in UI strings; leader
   name on the squad row.
4. **Battle leader seeding.** Campaign squad leader → `Squad.leaderId`
   (needs [C1](c1-fireteam-identity-through-the-drop.md)).

## Acceptance

- No rank is used outside the role it names: officers command, NCOs lead
  squads, marines fill them.
- A loaded pre-change save either migrates cleanly or fails loudly at a
  known point with a stated decision behind it — never silently drops a
  captain.
- Command caps still gate deployment exactly as `CaptainDeploymentPolicy`
  gates it today; only the unit of measure changes.
- Losing a squad leader promotes a successor deterministically, and the
  same person leads in the next battle.
- The starting officer can command the starting complement without the cap
  being reverse-engineered from a UI constraint.

## Out of scope

- Rank affecting combat stats. Aptitude and experience already do that;
  progression [S10](../../progression/stories/s10-trait-mechanics.md) owns
  whether traits add more. An NCO who fights like a private is fine for
  now.
- Pay, upkeep, or morale scaling by rank.
- Promotion ceremonies, XP curves for enlisted marines beyond what
  progression [S4](../../progression/stories/s4-performance-derived-experience.md)
  already contracts.

## Open questions

- Should enlisted rank do anything mechanically, or stay flavor until a
  story needs it? Leaning flavor-first — it is the cheapest way to make the
  roster legible, and a leadership bonus can be added later without
  reshaping the ladder.
- Does the player character have a rank of their own, or are they the
  company owner standing outside the ladder? The campaign fiction (a named
  merc company, not a faction) suggests outside it.
