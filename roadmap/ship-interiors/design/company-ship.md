# The company ship

Status: ACTIVE — direction. No part of this is implemented; `ship-interiors-nouns.md` owns the model this extends.

Written: 2026-08-27

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

## Where the numbers come from

**The deck is generated from the ship as it is, never from the hull it was.**

This is the whole trick, and it is nearly free. `DeckSizing.programFor` already
derives the room program from four numbers — hull class, role, min crew, max
crew, cargo — and the base game's own `FleetMemberAPI` reports every one of them
as an *effective* value: `getMinCrew`, `getMaxCrew`, `getCargoCapacity`,
`getFuelCapacity` are stat-derived, so every hull mod and every point of battle
damage is already folded into them before we read them.

The consequence is worth stating plainly, because it removes a large body of
work that looks necessary and is not: **we do not model hull mods.** We read the
ship. A ship carrying Additional Berthing reports more max crew and therefore
generates more berthing; a ship carrying Compromised Storage reports less of
everything and generates a smaller deck. Neither needs a rule of its own.

The one number already derived rather than read is the interesting one. The
program treats `maxCrew - minCrew` as **lift** — the people aboard who are not
required to fly the ship, which is to say the ground force. That makes mods
which raise *minimum* crew quietly expensive in interior terms: Militarized
Subsystems and Faulty Automated Systems both leave max crew alone and take the
difference out of the company's own space. The ship spends more of itself
running itself. That is a real trade and it falls out of the existing model
without being authored.

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

## Damage is already modelled — as d-mods

The base game persists battle damage on a ship as **d-mods**, added by
`DModManager` after combat and readable from the variant. Several of them reach
the interior directly through the same effective stats:

| D-mod | Effect on the deck |
|---|---|
| Compromised Storage | crew, cargo *and* fuel capacity all down — the deck shrinks on every axis at once |
| Degraded Life Support | max crew down, so lift falls: fewer berths, less ground force, smaller armory |
| Increased Maintenance, Faulty Automated Systems | min crew up, so lift falls without the ship getting any smaller |
| Damaged Flight Deck, Defective Manufactory | name a specific room as the damaged one |
| Structural Damage, Special Modifications, Ill-Advised Modifications | flavour for how a deck was cut about, not capacity |

Two of these are worth reading for their own sake. **Special Modifications** is a
description of ship architecture — passages narrowed, overheating conduits
panelled over, flush-set panels awkward to reach — which is a corridor and wall
treatment we could generate. **Ill-Advised Modifications** is the same idea
without the politics: a hull modified off-spec by nobody qualified.

So the answer to *how does battle damage show up in my ship* is that it already
does, the moment the deck is generated from the fleet member rather than the hull
spec. What remains is deciding how much of it the player should be able to *see*
— whether a damaged deck is merely a smaller deck, or whether the specific rooms
a d-mod names are visibly wrecked.

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
3. **Damage.** A ship mauled in ordinary combat comes back smaller inside. No
   separate damage model; the d-mods the base game already applied are the
   input.
4. **Refit.** Installing Additional Berthing changes the barracks. This is
   law 5 — an upgrade changes the room or it is not an upgrade — reached through
   the base game's own refit screen rather than through an economy we would have
   to invent.

## What this does not decide

- Whether the deck regenerates or is held once a ship is chosen, and what
  happens to a deck when its ship takes damage mid-campaign.
- Whether transfer costs anything, and whether a facility's contents move with
  the company or are lost with the hull.
- What happens to the company when its ship is destroyed rather than damaged.
- Whether more than one deck of a ship is ever generated. The hull-size model in
  `ship-interiors-nouns.md` already says larger hulls have decks past the
  playable envelope; which deck the company occupies is unaddressed.
