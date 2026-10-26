# Boat refit axes: the Hermes as the company's boat

Status: PLANNED — design, owner decisions open on the picks page
Written: 2026-10-26

The standing model is in `company-view-nouns.md` (Boat Deck, Boat spare, Fitting)
and `air-nouns.md` (fitted boat, Lift, the rotation). This story replaces the
three fitting slots with six refit axes and makes the Hermes the boat they are
built around. Nothing here is shipped; the open decisions are listed at the end
and put to the owner on the boat refit picks page (`tools/picks/pages.md`).

## Why

Today a boat is upgraded on three slots (plating, drive, lift) plus its door
guns, and the Hermes, the boat Second Wind starts with, takes none of them: its
frame "has no upgrade path", and the only way forward is Secondhand Wings, which
trades both Hermes for two Aeroshuttles. That makes the company's first boat a
dead end and its first boat upgrade a pattern swap rather than a refit.

Owner direction (2026-10-26): scrap the Aeroshuttle conversion and build the
upgrade equipment tiers around the Hermes. Upgrades are reworked around six
ideas: speed, lift, seats, weapons, armor and shield, and flux.

## The Hermes is the company's boat

- The Hermes takes every axis. `BoatFitting.supports` and `BoatGun.supports`
  stop refusing it; "no upgrade path" copy goes.
- **Its stock numbers are tier 1** of every ladder: 55 HP, 11 cells/s, 4 seats
  (one fire team), one hardpoint, no lift, no shield, no flux. Each tier above is
  authored as a step from those numbers, so a fully refitted Hermes is a
  different boat in play while staying the same airframe, sprite and hull id.
- Tiers are parts, kept as boat spares (`company-view-nouns.md`): built from a
  bill into stores, dragged on and off sockets, moved between boats.
- **Secondhand Wings goes.** Mara's Aeroshuttle offer, its notice and its modal
  are removed. The second bay keeps its own restoration in Engineering
  (`CompanyBayRestoration`), which is already a separate order, so Second Wind
  still reaches two Hermes by repairing the second wreck. What a save that
  already bought the pair keeps is decision D6.
- The Aeroshuttle itself stays an employer and mission transport (Conquest's
  paired arrivals); only the company conversion goes.

## The six axes

Each axis is a socket kind on the boat doll, with a yard-standard tier 1 part
that is free and never stored.

1. **Speed** (drive socket). How fast she gets in, out and round again. Tiers
   scale forward speed and both accelerations, as the drive does today. Whether
   it also shortens the deboard cadence or the deck turnaround is D2: today
   both are deliberately off the fitted frame (`air-nouns.md`, "The rotation is
   unchanged by the fit").
2. **Lift** (hold socket). The heaviest machine class she carries
   (`LiftRating`). A Hermes lifts nothing at tier 1. How far a Hermes climbs,
   and whether lift costs seats, is D3. Every consumer still asks
   `LiftAdmission`.
3. **Seats** (cabin socket, new). How many marines a sortie lands: 4, then 6
   (a half-squad, the Aeroshuttle's figure), then 8 (two fire teams). Today
   seats are the pattern's `capacity` and `FittedBoat` delegates them
   untouched; the cabin makes them a fitted number. More seats means fewer
   trips to land a squad, which is the cycling the owner named.
4. **Weapons** (hardpoint sockets). The pattern's hardpoint count does not
   change; what goes on each does. Today four ballistic door guns. Whether
   energy guns (drawing flux) and missile pods (finite rounds) join is D5.
5. **Armor and shield** (plating socket, plus an emitter socket). Plating
   tiers scale the hull and its armour skin, as today. A **shield emitter** is
   new for boats: raised, it turns anti-air hits from inside its arc into hard
   flux instead of hull damage. How it is modelled is D4.
6. **Flux** (power socket, new). A capacitor and vent part: flux capacity and
   dissipation. It is what a shield and any energy gun draw on, so it is the
   axis that makes the other two a trade. With no shield and no energy gun
   fitted it does nothing, and the card says so.

**Budget.** With six axes and free parts, the best boat is every axis maxed.
D1 decides whether that is allowed, or whether each part has a weight against
the frame's rating, as a mech's parts are weighed against its engine
(`MechFrameBudget`), so a fast boat with a full cabin carries no shield.

## What the battle reads

`FittedBoat` is still the only battle noun, frozen at launch. It gains seats,
shield and flux figures and, if D2 says so, a deboard cadence. The air
systems read them from the frame; nothing in the battle learns about the
campaign. Seats move from `type.capacity` to the frame in `AirSystem` and
`LiftQueueReadout`. A shield reuses the warship's flux and shield rules
(`WarshipFlux`, `WarshipShield`) at boat scale if D4 picks the full model.
Fixtures keep writing part ids, never the frame.

## Save migration

A saved boat keeps its plating, drive, cradle and guns; new sockets read as
yard-standard. A Hermes saved with nothing fitted is a tier 1 Hermes, which is
what it already flies as. Removing Secondhand Wings must not take a boat from a
save that bought it; see D6.

## Open decisions (picks page)

- **D1 Budget.** Slots only, no budget / slots plus a frame weight budget
  (recommended) / a brick grid like the mech compartment.
- **D2 Cycle.** Speed is flight only / flight plus deboard cadence, a quick
  ramp (recommended) / flight, deboard and deck turnaround.
- **D3 Hermes lift.** Never, the Hermes stays a squad boat / Light lift at
  the top hold tier, a sling that costs seats (recommended) / up to Medium.
- **D4 Shield and flux.** No shield, armour only / the warship shield and flux
  model at boat scale (recommended) / a flat damage cut that spends power points.
- **D5 Weapon families.** Ballistic door guns only / add energy guns on flux
  and missile pods with finite rounds (recommended).
- **D6 Secondhand Wings in old saves.** Keep the Aeroshuttles a save already
  bought (recommended) / turn them back into Hermes and refund the parts bill.
- **D7 Other patterns.** Parts are universal, applied as factors to each
  pattern's own numbers (recommended) / the ladder is the Hermes's alone and
  other patterns keep their fixed numbers.

## Acceptance

- A new Second Wind company refits a Hermes on all six axes from spares, and
  no screen offers an Aeroshuttle conversion.
- Each axis changes the number the battle reads, shown by a unit test on
  `FittedBoat` per axis; a shield absorbs a hit into flux in one unit test.
- An old save with fitted parts, and one that bought Secondhand Wings, load
  with nothing lost.
- Boat Deck previews at 1920x1080 show a Hermes at tier 1 and fully refitted.
