# Compound vehicle hangar

Status: IN PROGRESS

Written: 2026-08-28

Read `mapgen-nouns.md` before implementing this story, especially Tactical
space and the parcel-ownership paragraph. `ship-interiors-nouns.md` owns the
deck family this borrows its fill method from; nothing here changes it.

Give a military compound a vehicle hangar that is a real place — bays sized for
a machine, a door a machine can drive through, and berths whose occupants come
from a roster rather than from the fill.

## Why

The compound already has the role. `Compound.Role.VEHICLE_BAY` labels a member
leaf, `MilitaryBaseFiller` carves it, and the interior it gets is a generator
every three cells along one wall and cable reels every four along the other.
Nothing in that room establishes the scale of the machine it exists to hold,
and nothing is held in it.

Two structural facts underneath the dressing are the actual defect:

- A compound sub-building's doorway is one cell, punched at a random position
  on a side **after** the layout has been chosen. Nothing drives through a
  one-cell door, and the fill has no say in where the door lands.
- Nothing is berthed. `Gantry` — the authored machine berth — is already
  family-neutral and already published on `MapResult`, and no city map has ever
  produced one.

The ship deck solved both for the mech bay. The bay module, the reserved
service lane, the framed structure, the painted deck, and the berth-with-no-
occupant are not ship ideas; they are how you draw a room for a machine. What
is ship-specific is only the compartment the fill is handed.

## The bay module is shared, not copied

`VehicleBayFitting`'s five-by-seven bay is the standing module for a berthed
machine across both families. It is deliberately generous: five cells across is
the machine plus a working column each side, seven along is bow to stern, and
that envelope holds a walker, a tracked vehicle, or whatever is chassis'd later
without re-authoring the room. A compound hangar that invented its own smaller
module would be a second answer to a question already answered, and would go
stale the first time a larger chassis shipped.

Sharing it means the fitting machinery moves out of `gen.ship.fit` to a
family-neutral home rather than being duplicated. That move is scoped here
because this story is what forces it; it is a relocation and a seam, not a
redesign.

## Scope

**Lift the fill surface out of the ship family.** `CompartmentFloor`,
`RoomFitting`, `RoomFittings`, `RoomFit`, `RoomShape`, `RoomPose`, `Hookup` and
the authored fittings move to a neutral fitting package. `CompartmentFloor`
stops naming a deck compartment and takes a **furnishable room**: a footprint,
a pose, its doors, and its purpose. `DeckGraph.Compartment` supplies one; so
does a carved compound sub-building. Dispatch stays keyed on `RoomPurpose`,
which both families already label.

**Furnish a compound sub-building through a fitting.** `MilitaryBaseFiller`'s
`VEHICLE_BAY` member routes to a fitting instead of a `BuildingLayouts` recipe,
and keeps the rollback contract: a fill that would seal the building is thrown
away entire — doodads, berths and published tasks — leaving bare floor.

**A hangar arrangement of its own.** Not the mech bay verbatim. A ship bay is a
compartment amidships with passages either end; a hangar is a shed inside a
walled yard, and the machine has to get out to the parade ground and through a
gate. It takes a single rank of bays where the footprint is shallow and two
where it is deep, the apron in front of the bay mouths is reserved lane, and
the whole rank faces the wall the vehicle door is in.

**A vehicle door.** The hangar declares through `Hookup` where its opening may
be, and the shell honors it before the fill runs rather than punching a
one-cell doorway afterwards. The opening is bay-width, not cell-width, and the
parade ground outside it stays clear of dressing so a machine can reach the
compound gate.

**Berths, no occupants.** Each bay publishes a `Gantry` with its clear
footprint and the facing that points a machine at the door. The fill never
names a chassis, never places a vehicle prop in a berth, and never varies by
what might park there. What occupies a berth is the host's decision from a
roster, exactly as it is on the deck.

## Constraints

- Fixtures use existing registry doodad ids. This story adds no art.
- No vehicle type is named in generation. `HEAVY_APC` is today's only chassis
  and tomorrow's is not; a fill that reads a chassis is a fill that has to be
  edited when one is added.
- Capacity is the bay count. Nothing records it a second time.
- The relocation changes no ship behavior. Deck generation, the Mech Lab view,
  and the `ship-decks` snapshots are unchanged by it.
- Decoration cannot silently alter topology. The reserved apron and the vehicle
  door are authored structure, declared, not implied by where props happen not
  to be.
- Compound laws still bind: gates, building thresholds, two-cell circulation,
  road reservations, the firing apron, and final walkable connectivity all
  survive the fill.

## Acceptance

- A `MILITARY_BASE` compound whose `VEHICLE_BAY` member is large enough comes
  out with at least one five-by-seven berth, and the berth's cells are clear.
- The hangar has an opening at least one bay wide, and a machine-width path
  runs from every berth through that opening to the parade ground.
- Berth count scales with the member footprint, and nothing else records the
  count.
- A seed sweep over compound generation produces no hangar that seals itself,
  blocks its own door, or fails compound connectivity — and no map on which the
  fill's rollback leaves a published berth behind.
- A member too small for one bay is left as an ordinary fitted building rather
  than a hangar with no berths.
- Ship deck evidence is byte-identical across the relocation commit.
- No occurrence of a `VehicleType` constant anywhere under generation.

## Out of scope

Spawning anything into a berth, sourcing reinforcement from a surviving hangar,
and any change to Conquest capture or `reinforcement-nouns.md`. Those are the
battle-tier consumer, deliberately held back so the map work lands on its own.
`ARMORY` and `BARRACKS` members keep their current fills; they follow the same
shape once the hangar proves the seam.
