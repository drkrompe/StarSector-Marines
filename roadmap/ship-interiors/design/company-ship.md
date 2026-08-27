# The company ship

Status: ACTIVE — direction. No part of this is implemented; `ship-interiors-nouns.md` owns the model this extends.

Written: 2026-08-27

Updated: 2026-08-27 — separated form from state: refits change the deck, battle
damage marks it. An earlier draft let d-mods resize the program, which quietly
rebuilt the ship around its injuries. Damaged fixtures are out of service, so
damage costs capacity.

The company's interior is not a fixed set. It is **one ship the player picks out
of their own fleet**, and picking it is a decision they make at founding and
re-make whenever a better hull comes along. That single choice is what turns the
interior from scenery into a system, because it puts the player's ship and the
player's rooms on the same axis: a bigger, more specialised hull is more
interior, and it is bought the way any ship is bought.

## Two axes, not one

Progression runs in two directions at once, and they are independent:

| Axis | What the player does | What changes inside |
|---|---|---|
| **Hull** | acquires a larger or more specialised ship and transfers to it | how much interior there is, and which facilities the hull can hold at all |
| **Fit** | refits the ship they have | how much a given facility holds, and what it can do |

Neither dominates. A cramped hull fitted well can out-berth a large hull fitted
badly, and a hull that structurally cannot hold a mech bay does not acquire one
by being refitted. That is the tension worth having: the player who wants a
proper lab has to go and get a ship that can have one.

## Form and state are different questions

A ship's interior has two independent inputs, and conflating them produces a
ship that has been rebuilt around its own injuries.

**Form** is what the ship can do when whole: how much interior there is, and
which facilities it holds. Refits change form, in both directions. A hull
carrying Additional Berthing really does have more berthing and should generate
more of it; a hull whose fighter bays were converted to holds really has lost
those bays. Both are deliberate acts by the owner, and the deck should come out
different.

**State** is what has happened to that form since. Battle damage is state. A
ship that comes home with Compromised Storage has not been rebuilt with a
smaller hold — it has the hold it was built with, wrecked, with part of it
unusable. Generating a smaller room in its place would erase the damage by
absorbing it into the architecture, and the player would see a tidy small room
where they should see their own bad afternoon.

So the deck is generated from **capability when whole**, and damage is laid over
that form afterwards.

## Where the numbers come from

`DeckSizing.programFor` already derives the room program from four numbers —
hull class, role, min crew, max crew, cargo — and the base game reports every one
of them, stat-derived, on `FleetMemberAPI`. Those effective values fold in
refits and damage alike, so they are the right input for form only once the
damage is taken back out.

That separation is cheap rather than clever, because the game will do the
arithmetic. Hull mods stamp their own id as the source of every stat bonus they
apply, and a d-mod is identified by its `dmod` tag, so the whole-ship reading is
the variant cloned, its d-mods removed, and a throwaway fleet member built from
the clone — `ShipVariantAPI.clone`, `DModManager.removeDMod`,
`FactoryAPI.createFleetMember`. Nothing about how stats compose has to be
reimplemented, which matters because reimplementing it is exactly the kind of
thing that stays subtly wrong for a year.

The consequence is still that **we do not model hull mods**. We read the ship
twice: once as it was built and fitted, for the form, and once for the list of
injuries.

The one number already derived rather than read is the interesting one. The
program treats `maxCrew - minCrew` as **lift** — the people aboard who are not
required to fly the ship, which is to say the ground force. That makes mods
which raise *minimum* crew quietly expensive in interior terms: Militarized
Subsystems and Faulty Automated Systems both leave max crew alone and take the
difference out of the company's own space. The ship spends more of itself
running itself. That is a real trade and it falls out of the existing model
without being authored.

It only became real once berthing was split, though. Boats, armories and ranges
all derive from lift and moved when minimum crew rose; berths were sized from the
whole complement and did not, so the largest crew space on the ship was exempt
from the trade this section describes. Crew quarters now come from minimum crew
and the barracks from lift, and a mod that takes hands out of the company's
column takes their bunks with them.

## What hull mods change that the stats do not

Stats cover *how much*. Hull mods additionally change *what kind*, and those are
the cases worth reading the mod list for:

| Mod | What it means inside |
|---|---|
| Converted Hangar / Converted Cargo Bay / Vast Hangar | a bay on a hull whose spec says it has none — the room exists because of the fit, not the hull |
| Converted Fighter Bay | the reverse: bays become improvised holds, so a carrier loses the room the ground force needed |
| Blast Doors | reinforced doors at critical junctures — a door and chokepoint treatment, not a capacity change |
| Automated Ship | takes no crew at all: no berthing, no mess, no life. A genuinely different interior |
| Militarized Subsystems | a civilian hull run as a warship; the interior should read as converted rather than built |
| Salvage Gantry, Surveying Equipment, Shielded Cargo Holds | specialised working rooms a general hull would not have |
| Ground Support Package | the one mod that is already about our subject matter — marines, and putting them ashore |

## Damage names a room and wrecks it

The base game persists battle damage as **d-mods**, added by `DModManager` after
combat and readable from the variant. Read as damage rather than as sizing, most
of them point at a room:

| D-mod | The room it damages |
|---|---|
| Compromised Storage | the holds, and the stores generally — the broadest of them, since it costs crew, cargo and fuel capacity at once |
| Degraded Life Support | berthing and the spaces that keep people alive |
| Damaged Flight Deck | the bay: the deck the ground force launches from |
| Defective Manufactory | the production floor and the machine shop |
| Increased Maintenance, Faulty Automated Systems | engineering, and the machinery aft |
| Structural Damage | the hull itself: bulkheads, frames, the fabric of the deck rather than what is in it |
| Erratic Fuel Injector | the fuel spaces |

A damaged room is the same room with its fixtures wrecked, its floor strewn, and
some of what it held no longer usable. The vocabulary for drawing that already
exists in the tile registry — damaged crates, damaged shelving, damaged desks,
and four rubble decals — which is the same vocabulary a bay that is mid-service
wants. Damage state and room variation are one problem approached from two
sides, and they should be built as one.

Two d-mods are worth reading for their own sake rather than for their effect.
**Special Modifications** is a description of ship architecture — passages
narrowed, overheating conduits panelled over, flush-set panels awkward to reach
— which is a corridor and wall treatment we could generate. **Ill-Advised
Modifications** is the same idea without the politics: a hull modified off-spec
by nobody qualified.

**A wrecked fixture is out of service, and the facility is that much smaller
until the ship is repaired.** A bay that comes home with two of its four
gantries wrecked services two machines. This is law 4: capacity counts working
fixtures, and damage is how a fixture stops working.

That costs something real — every count that reads a facility has to ask for
working fixtures rather than fixtures — and it buys the thing that makes ship
collection interesting. A hull is no longer rated by its class. A pristine
example of a hull is genuinely better than a battered one, so two of the same
ship are worth different amounts and worth looking for; and a mauled capital may
still out-berth and out-service a pristine frigate, so the player is
continually weighing a big damaged shell against a small sound one and moving
between them as they find better. The interior is what makes that judgement
concrete instead of a number comparison, because the player can see which
gantries are out.

**Which fixtures are out must be stable.** Severity says how many; something has
to say which, and it has to give the same answer every time the player opens the
room. Derived from the ship and the set of d-mods on it, a damaged bay has the
same two gantries out on every visit until something about the ship changes. Any
other answer makes the room shimmer, and a room that reshuffles its own damage
is one the player stops reading as a fact about their ship.

**Repair is the base game's, not ours.** D-mods come off at a spaceport, through
restoration the player already knows how to buy. So the interior needs no repair
transaction of its own: the fixtures come back into service because the ship did.

**How badly a room is damaged should be read, not tuned.** The form is generated
from whole-ship capability and the game reports the damaged capability, so the
ratio between them is a severity the base game already decided — per stat, and
therefore per room, since cargo names the holds and max crew names the berthing.
Taking that fraction of a room's fixtures out of service keeps the interior and
the game's own numbers agreeing by construction rather than by a tuning table
that will drift out of step the first time the base game rebalances a d-mod.

## Capability is spatial, and absence has to read as a fact

`programFor` already gates rooms on hull class — below destroyer size there is no
range, no briefing room, no proper sick bay — and `DeckGraph.unplaced()` already
reports every room the hull could not hold. The measurement exists. What does not
exist is the player-facing consequence.

A ship that cannot hold a mech bay must not present an empty Mech Lab. The
screen has to say the ship cannot host one, because an empty room and an absent
facility are different facts and only one of them is a reason to go shopping for
a hull. This is the point where the interior stops being decoration and starts
being an argument for the next ship.

## The four moments this has to serve

1. **Founding.** Before the company exists, the player chooses which of their
   ships it lives on. This is the first real decision about what the company is
   for.
2. **Transfer.** A screen for moving the company to a different ship in the
   fleet, with the consequences legible before committing: what is gained, what
   is lost, and what does not fit.
3. **Damage.** A ship mauled in ordinary combat comes back with the same rooms
   in a worse state. No separate damage model; the d-mods the base game already
   applied name which rooms, and repairing the ship clears them.
4. **Refit.** Installing Additional Berthing changes the barracks. This is
   law 5 — an upgrade changes the room or it is not an upgrade — reached through
   the base game's own refit screen rather than through an economy we would have
   to invent.

## What this does not decide

- Whether the deck regenerates or is held once a ship is chosen. Form and state
  now answer differently: a refit changes the form and warrants regenerating,
  while damage arriving mid-campaign should reach the deck without rebuilding
  it.
- Whether damaged rooms lose capacity or only look wrecked.
- Whether transfer costs anything, and whether a facility's contents move with
  the company or are lost with the hull.
- What happens to the company when its ship is destroyed rather than damaged.
- Whether more than one deck of a ship is ever generated. The hull-size model in
  `ship-interiors-nouns.md` already says larger hulls have decks past the
  playable envelope; which deck the company occupies is unaddressed.
