# The landing zone is a place

Status: PLANNED — direction settled with the owner on 2026-09-01.

Written: 2026-09-01

## What the frames showed

The attacker region is a third of the map, and the landing stage scans it
from the approach edge for the first open ground a pair of berths fits on.
Open ground is anything walkable outside a building — which includes the
streets of a settlement, so on one 560x336 frame the marines came down in the
middle of a base district, and on every frame the beachhead is three boxes on
whatever happened to be clear. Nothing on the map is *the landing zone*: the
settlement can grow over it, no defender reasons about it, and there is
nothing to hold or lose.

## The model

**The landing zone is a precinct.** It is seeded in the plan after the
objective and the lanes and before the settlement, inside the attacker region
at the standoff, so it claims its ground first and a town grows around it
rather than under it. Its berths are authored inside its own claim; the
marine spawn is inside it; nothing else may claim a cell of it.

**It has a kind, derived from the world and stateable by the mission.** A
`LandingKind`:

| kind | what stands there | derived when |
|---|---|---|
| `SPACEPORT` | a programmed place: authored berths, a terminal, a hangar, a control office, a fuel yard, an apron road out — the civilian spaceport campus the stock recipe already knows how to fill | the market reports a real spaceport |
| `FIELD` | a zoned open place: berths on bare ground, a marked strip, nothing built | no spaceport, or a `REMOTE` world |
| `STRIP` | between the two: a field with one hardstand and a hut, the off-grid `LANDING` link's shape | a settlement supplied by ship |

Stated on the mission, fixture and debug stepper the way sprawl, standoff and
lanes are.

**It is held, and it can be lost.** The landing place registers as a compound
that starts `MARINE_HELD` — the first link of every lane's chain, band 3 or
beyond — so the same capture rule and the same reinforcement layer reason
about it: a defender counterattack can take the beachhead, and the marines
have something to hold at their end of the map as well as something to take
at the other. What losing it *costs* — whether a lift may still land on a
contested beachhead — is a rule for `reinforcement-nouns.md` to state and is
left open here; the place exists first.

**It is annotated as what it is.** The review frame boxes it by kind and
state like any other compound, and the approach arrow starts from it.

## What it does not do

- It does not decide who wins the beachhead; it makes the beachhead a thing.
- It does not change how many berths a mission gets or the arrival cadence;
  those are `conquest-560-contact.md`'s.

## Acceptance

- On both canonical fixtures no landing berth lies inside another precinct's
  claim, every berth lies inside the landing place's, and the marine spawn is
  inside it.
- A `SPACEPORT` landing place packs its program and emits its rooms as
  tactical nodes; a `FIELD` builds nothing and still seats the berths.
- The landing place is a compound reading `MARINE_HELD` at tick zero, and a
  defender standing in it alone for the hold time flips it.
- The annotated frame boxes it, and the approach arrow starts from it.

## Plan

1. `LandingKind` and its derivation from `TargetProfile`; a spaceport program
   from the existing spaceport fillers' vocabulary; unit tests.
2. The landing precinct in `PrecinctPlan.derive` and its claim; the landing
   stage confined to it; the spawn anchor inside it; the ward stage emitting
   its nodes and registering it as a compound with an initial state.
3. Mission vocabulary; evidence on the matrix (does a held beachhead change
   the west fixture); fold into `precincts.md` ("It can be landed on") and
   `conquest-nouns.md`.
