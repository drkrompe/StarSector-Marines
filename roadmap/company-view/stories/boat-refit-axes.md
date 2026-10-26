# Boat refit axes: the Hermes as the company's boat

Status: PLANNED — design; D2, D4 and D5 open on the picks page
Written: 2026-10-26
Updated: 2026-10-26 — owner answered lift (D3), old saves (D6), other patterns (D7) and ship changes; the budget (D1) follows.

The standing model is in `company-view-nouns.md` (Boat Deck, Boat spare, Fitting)
and `air-nouns.md` (fitted boat, Lift, the rotation). This story replaces the
three fitting slots with six refit axes and makes the Hermes the boat they are
built around. Nothing here is shipped; the open decisions are at the end and on
the boat refit picks page (`tools/picks/pages.md`).

## Why

Today a boat is upgraded on three slots (plating, drive, lift) plus its door
guns, and the Hermes, the boat Second Wind starts with, takes none of them: its
frame "has no upgrade path", and the only way forward is Secondhand Wings, which
trades both Hermes for two Aeroshuttles. That makes the company's first boat a
dead end and its first boat upgrade a pattern swap rather than a refit.

Owner direction (2026-10-26): scrap the Aeroshuttle conversion and build the
upgrade equipment tiers around the Hermes. Upgrades are reworked around six
ideas: speed, lift, seats, weapons, armor and shield, and flux. Upgrading or
swapping boats is no longer part of the campaign.

## The company flies two Hermes

- **The campaign's boats are two Hermes, for the whole campaign.** The company
  repairs the two founding wrecks and refits them; it never buys, converts or
  trades for another pattern. Getting better boats means refitting these two.
- The Hermes takes every axis. `BoatFitting.supports` and `BoatGun.supports`
  stop refusing it; "no upgrade path" copy goes.
- **Its stock numbers are tier 1** of every ladder: 55 HP, 11 cells/s, 4 seats
  (one fire team), one hardpoint, no lift, no shield, no flux. Each tier above is
  authored as a step from those numbers, so a fully refitted Hermes is a
  different boat in play while staying the same airframe, sprite and hull id.
- Tiers are parts, kept as boat spares (`company-view-nouns.md`): built from a
  bill into stores, dragged on and off sockets, and moved freely between the
  two boats.
- **Secondhand Wings is deleted outright.** Mara's offer, its notice, its modal,
  `SecondhandWings`, `SecondhandWingsCampaign`, the deck's conversion record and
  the hull's Aeroshuttle adapter all go. The second bay keeps its own
  restoration in Engineering (`CompanyBayRestoration`), already a separate
  order, so Second Wind reaches two Hermes by restoring the bay and repairing
  the second wreck.
- Other patterns (Aeroshuttle, Valkyrie, the bus-class freighters) remain
  employer and mission transports with their pattern numbers. The refit ladder
  is the company's Hermes's.
- **The two Hermes belong to the company and move with it to any ship**
  (owner). A change of ship never leaves a boat behind and never reconciles the
  deck against the new hull's pattern: `BoatDeck.reconcile`'s left-behind path
  and `LeftBehind` go, and the berths a hull offers only say where the boats
  stand aboard her. A hull with fewer berths than boats still carries both; a
  boat with no berth is stowed, flies like any other, and is not drawn in the bay.

## The six axes

Each axis is a socket kind on the boat doll, with a yard-standard tier 1 part
that is free and never stored. Every other part has a **weight** against the
Hermes frame's rating (below).

1. **Speed** (drive socket). How fast she gets in, out and round again. Tiers
   scale forward speed and both accelerations, as the drive does today, and are
   also what carries a heavy load at speed (see Lift). Whether speed also
   shortens the deboard cadence or the deck turnaround is D2.
2. **Lift** (hold socket, its own part). The heaviest machine class she
   carries: none at tier 1, then Light (Hound, heavy APC), Medium (Warden,
   escort APC, militia technical) and Heavy (Bulwark, breakthrough tank).
   **Lift never costs seats** (owner: the company has only two boats). A
   sortie is still one kind: a squad load or exactly one machine
   (`air-nouns.md`, Lift). Every consumer still asks `LiftAdmission`.
   - **A load slows her.** A boat carrying a machine flies slower by its lift
     class. Drive tiers win that speed back, and they weigh against the frame,
     so a Hermes fitted to carry a Bulwark at full speed has that much less for
     armour, shield and guns. An empty or squad sortie flies at the drive's own
     speed.
3. **Seats** (cabin socket, new). How many marines a sortie lands: 4, then 6
   (a half-squad), then 8 (two fire teams). Today seats are the pattern's
   `capacity` and `FittedBoat` delegates them untouched; the cabin makes them a
   fitted number. More seats means fewer trips to land a squad.
4. **Weapons** (hardpoint sockets). The Hermes's one hardpoint does not
   change; what goes on it does. Today four ballistic door guns. Whether
   energy guns (drawing flux) and missile pods (finite rounds) join is D5.
5. **Armor and shield** (plating socket, plus an emitter socket). Plating
   tiers scale the hull and its armour skin, as today. A **shield emitter** is
   new for boats: raised, it turns anti-air hits from inside its arc into hard
   flux instead of hull damage. How it is modelled is D4.
6. **Flux** (power socket, new). A capacitor and vent part: flux capacity and
   dissipation. It is what a shield and any energy gun draw on, so it is the
   axis that makes the other two a trade. With no shield and no energy gun
   fitted it does nothing, and the card says so.

**The frame budget** (settled by the lift answer). Every part above tier 1 has
a weight, and the Hermes frame has a rating, as a mech's parts are weighed
against its engine (`MechFrameBudget`). A refit over the rating is refused.
The trade is the point: lift and the drive that keeps a loaded boat fast take
weight that armour, shield, flux and guns cannot use. The Boat Deck shows the
frame as a meter, the way the Mech Lab shows the engine budget.

## What the battle reads

`FittedBoat` is still the only battle noun, frozen at launch. It gains seats,
lift, shield and flux figures and the load slowdown, and if D2 says so a
deboard cadence. The air systems read them from the frame; nothing in the
battle learns about the campaign. Seats move from `type.capacity` to the frame
in `AirSystem` and `LiftQueueReadout`. The load slowdown applies while a
machine is aboard, so the run in is slow and the run out is the boat's own
speed. A shield reuses the warship's flux and shield rules (`WarshipFlux`,
`WarshipShield`) at boat scale if D4 picks the full model. Fixtures keep
writing part ids, never the frame.

## Saves

No old saves need to load (owner). The work deletes compatibility code rather
than adding any: Secondhand Wings' purchase record and adapter, and the
fitting slots' legacy readings, go without a migration.

## Owner answers

- **D1 Budget:** a frame weight budget; follows from the lift answer.
- **D3 Hermes lift:** a separate lift part up to Heavy, costing no seats; a
  load slows the boat, and drive tiers that cost frame weight fix it.
- **D6 Old saves:** none to support.
- **Ship changes:** the two Hermes move with the company to any ship; none is
  ever left behind.
- **D7 Other patterns:** parts transfer between the company's boats, but
  upgrading or swapping boats leaves the campaign; the company flies two Hermes.

## Open decisions (picks page)

- **D2 Cycle.** Speed is flight only / flight plus deboard cadence, a quick
  ramp (recommended) / flight, deboard and deck turnaround.
- **D4 Shield and flux.** No shield, armour only / the warship shield and flux
  model at boat scale (recommended) / a flat damage cut that spends power points.
- **D5 Weapon families.** Ballistic door guns only / add energy guns on flux
  and missile pods with finite rounds (recommended).

## Acceptance

- A new Second Wind company restores its second bay, repairs both Hermes and
  refits them on all six axes from spares; no screen offers another pattern.
- A refit over the frame rating is refused, and the frame meter shows why.
- Each axis changes the number the battle reads, shown by a unit test on
  `FittedBoat` per axis; a loaded boat flies slower than an empty one and a
  drive tier recovers it; a shield absorbs a hit into flux in one unit test.
- Boat Deck previews at 1920x1080 show a Hermes at tier 1, one fitted to lift,
  and one fitted to fight.
