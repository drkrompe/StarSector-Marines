# C7 — Organization and ranks

> A Private commanding five marines and a Sergeant commanding forty-two are
> both incoherent. Rank-and-file marines have no rank at all, nothing models
> the person leading a squad, and — now that infantry kills nine times
> faster — a six-marine squad is a casualty away from being a fire team.

**Status:** not started. No hard dependencies; pairs with
[C2](c2-formation-model.md), settles the language
[C3](c3-company-card-stack.md) renders, and defines the element
[C9](c9-fire-teams-as-the-maneuver-element.md) maneuvers with.

## Decisions this story implements

1. **The named officer is the company commander.** Squads are led by their
   own NCOs, who are not officers.
2. **A squad is twelve marines, built from three four-marine fire teams.**
   *Revised 2026-08-22 (was six).* Fire teams are modelled but mostly
   behind the scenes — they are the AI's maneuver element, not a level of
   the player's card hierarchy.

### Why twelve

Progression [S1](../../progression/stories/s1-lethality-and-tier-spread.md)
slice 1 shipped a **9× infantry lethality scale**: a pulse rifle takes an
unarmored marine from ~30 s to ~3.3 s. Under that, a six-marine squad loses
half its strength — `SquadFallbackSystem`'s trigger ratio — in a few
seconds of contact, and the tactics already shipped stop having anything to
work with. `EnterZone`'s bounding overwatch splits the squad in half; at
six that is two teams of three, and after two casualties it is two pairs.

Twelve restores the headroom those systems were written for, matches the
real structure (a Marine rifle squad is three fire teams), and keeps the
row count low: a 72-marine company is six squad rows, not eighteen.

## Problem

- `Rank` is one ladder — PRIVATE, CORPORAL, SERGEANT, LIEUTENANT, CAPTAIN,
  MAJOR, COLONEL, GENERAL — applied to `MarineCaptain`, the named officer
  character. Its `squadCap` is a **marine** count (5, 10, 42, 80, 160, 320,
  640, 1280) and `fireteamCap()` divides it by 6.
- A starting officer is a **Sergeant** with a 42-marine cap, because the
  enum comment needed "the full opening company: seven six-marine
  squads" to fit. That is a cap chosen to satisfy a UI constraint, and
  it puts an NCO rank in an officer's role.
- `MarineSoldier` carries **no rank**. Aptitude and XP only.
- Nothing models a **squad leader**. The battle tier has `Squad.leaderId`
  (promotable on death, drives leader-pull cohesion), seeded by whichever
  member spawns first.
- Nothing models a **fire team**, so every squad tactic invents its own
  sub-grouping from scratch — see
  [C9](c9-fire-teams-as-the-maneuver-element.md).
- The UI says "squad" for a six-marine group while the class is
  `MarineSquad`.

## Design

### The unit: a squad of twelve, three teams of four

```
Squad (12)  ── led by a Corporal
  Fire team 1 (4)  ── led by the squad leader
  Fire team 2 (4)  ── led by a Lance Corporal
  Fire team 3 (4)  ── led by a Lance Corporal
```

`MarineSquad.CAPACITY` 6 → 12, with a fire-team index on each billet. The
squad leader doubles as team 1's leader; the real thirteen-man arrangement
(three teams plus a dedicated squad leader) is noted under Open questions,
but twelve divides cleanly into lifts and keeps the arithmetic honest.

Fire teams are **not** a level of the card hierarchy. The player commands
squads; teams show up as pip grouping on the squad row and as the AI's
maneuver element.

### Two ladders, not one

**Enlisted** — new field on `MarineSoldier`, nullable:

| Rank | Role |
| --- | --- |
| `MARINE` | rank and file |
| `LANCE_CORPORAL` | fire-team leader |
| `CORPORAL` | squad leader |
| `SERGEANT` | senior squad leader; the officer's right hand |

**Officer** — replaces the current ladder on `MarineCaptain`, capping
command in **squads**:

| Rank | Squads | Marines | Real-world analogue |
| --- | ---: | ---: | --- |
| `LIEUTENANT` | 3 | 36 | a platoon — the starting officer |
| `CAPTAIN` | 6 | 72 | a (merc-sized) company |
| `MAJOR` | 10 | 120 | — |
| `LT_COLONEL` | 16 | 192 | — |
| `COLONEL` | 24 | 288 | — |

The curve is gentler than today's doubling because each step now carries
twelve bodies, not six. Keep the doubling XP thresholds — the *reward*
should still accelerate even as the roster growth slows.

Starting complement rises from 10 to **12**: the player begins as exactly
one squad, under a Lieutenant with room for two more.

### The officer cap is the scale governor

The rank cap is not only a progression gate — it is what stops the game
from ever asking the player to think about a hundred squads. Two governors
stack: **rank** bounds the squads one officer fields in a battle (3 at
Lieutenant, 6 at Captain), and **lift capacity** bounds how many land per
wave ([C8](c8-lift-capacity-and-multi-pass-drops.md)). The battle HUD's
realistic worst case is a handful of squad rows, not fifty. The *roster*
outgrows that as the organization becomes a battalion, which is why the
fleet view paginates and the battle view does not have to. Pick future cap
numbers against that ceiling: a rank whose cap outruns what the player can
read is a rank that made the game worse.

### Squad and team leaders

- `MarineSquad` gains `leaderSoldierId` and a per-billet team index.
- Leadership follows rank: the `CORPORAL` leads the squad, the
  `LANCE_CORPORAL`s lead teams 2 and 3.
- On loss, promote deterministically — highest enlisted rank, then most
  experienced — so the same person leads next battle.
- At deploy the campaign squad leader seeds the battle `Squad.leaderId`
  through C1's seat data, so leader-pull cohesion follows the person the
  player thinks is in charge, and a leader death is a named event.

### Save compatibility — not a constraint

**Settled 2026-08-22: nothing is shipped to anyone and there are no saves
to preserve.** Edit `Rank` freely — delete constants, renumber caps, no
legacy alias map, no migration step. Change `MarineSquad.CAPACITY` outright.

The mechanism is worth knowing for later: `MarineCaptain.rank` is a
persisted enum and xstream writes enums **by name**, so once real saves
exist, deleting a constant breaks roster deserialization while adding a
field stays safe.

## Slices

1. **Squad size and fire teams.** `CAPACITY` 6 → 12, team index per billet,
   starting complement 12. Touches recruitment, vacancy math, transfers,
   the reserve pool, and `InfantryLoadoutRolls.playerSquad` (whose
   "last slot gets the rocket launcher" rule becomes one anti-armor billet
   per fire team, or stays one per squad — decide with the roll).
2. **Officer ladder in squads.** New constants, caps denominated in squads,
   `fireteamCap()` retired. Consumers: `MarineRoster` (four call sites),
   `CaptainDeploymentPolicy`, `ArmoryScreen`, `BriefingScreen`,
   `SquadDeploymentScreen`, `StationingScreen`.
3. **Enlisted ranks + leaders.** Field on `MarineSoldier`,
   `leaderSoldierId` on `MarineSquad`, promotion-on-loss, team leaders.
4. **Display language sweep.** "Fireteam" → "squad" for the twelve-marine
   unit; "fire team" now means the four-marine element. Leader name on the
   squad row.
5. **Battle leader seeding.** Campaign squad leader → `Squad.leaderId`
   (needs [C1](c1-fireteam-identity-through-the-drop.md)).

## Acceptance

- No rank is used outside the role it names: officers command, NCOs lead
  squads, lance corporals lead teams, marines fill them.
- Command caps gate deployment exactly as `CaptainDeploymentPolicy` gates
  it today; only the unit of measure changes.
- A twelve-marine squad survives first contact under the shipped 9×
  lethality scale as a unit that can still maneuver — verify against
  `TtkHarness` expectations rather than by eye.
- Losing a squad or team leader promotes a successor deterministically.
- The starting officer can command the starting complement without the cap
  being reverse-engineered from a UI constraint.
- Recruitment, vacancy, transfer, and reserve math all hold at 12.

## Out of scope

- What the fire teams *do* — [C9](c9-fire-teams-as-the-maneuver-element.md).
- Rank affecting combat stats. Aptitude and experience already do that;
  progression [S10](../../progression/stories/s10-trait-mechanics.md) owns
  whether traits add more.
- Pay, upkeep, or morale scaling by rank.

## Open questions

- **Twelve or thirteen?** A real Marine rifle squad is three fire teams
  plus a dedicated squad leader. Twelve divides into lifts cleanly and
  keeps the squad leader in a team; thirteen is authentic and gives the
  leader a free hand. Leaning twelve, revisit if the leader's dual role
  causes friction in [C9](c9-fire-teams-as-the-maneuver-element.md).
- **Does the squad want fixed billet roles** — automatic rifleman,
  grenadier, anti-armor — per fire team, the way a real one does? The
  loadout roll already singles out one anti-armor slot per squad. Doing it
  per team is a small change with real tactical texture, but it belongs to
  progression's equipment work as much as here.
- Should enlisted rank do anything mechanically, or stay flavor until a
  story needs it? Leaning flavor-first.
- Does the player character have a rank of their own, or stand outside the
  ladder as the company owner? The merc-company fiction suggests outside.
