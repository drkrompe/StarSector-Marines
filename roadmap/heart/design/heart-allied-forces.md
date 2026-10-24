# The Heart — allied forces and their tools

Status: DRAFT — options out to the owner (question `heart-allied-tool`); armour up the road, the recommended one, is built.

Written: 2026-10-23

Step 3 of `heart-mission-depth.md`. `heart-nouns.md` is the model; the laws
still hold: allies never end the battle, the guns cannot take the heart, and a
Harrier pack flies only where the company or an ally has anti-air.

## What the militia would really have brought

The Broad Front is a militia offensive along several lanes. The Burn is the
company's own push behind a battery. What each side brings follows the
country (`heart-map-intent.md`, Density):

| Density | Lanes bring | Base sends |
| --- | --- | --- |
| Rural | armour columns up the roads, technicals | hulls up the supply road; a gunship when the air is clear |
| Town | armour and infantry together | the same, and ammunition for the guns |
| City | infantry and mech lances, little armour | mechs; the gunship works the streets |

## The logistics base

A **logistics base** is the ground behind the start line that the layout
already lays: the ammunition dumps, the vehicle park and the supply road
(`heart-map-intent.md`, the cordon's works). It is the allies' tool shed.
While it stands it sends things up at a pace; a base whose dumps are lost
sends nothing more. It never fights on its own, and it is never what ends the
battle.

## The tools (the options)

Each is staged in `heart-allies-look` (opt-in `createSnapshots` suite) against
the same swarm pack, so the four pictures read side by side.

- **Armour up the road** (`allies-armour`): the vehicle park sends a tank pair
  and an APC up the supply road every few minutes to the nearest lane. F6 is
  what it hangs on: the satellite against the road cuts it until it falls.
  Built: the lanes' armour waves set out from the laid vehicle park
  (`HeartVehiclePark`, an `AlliedDepot`) and wait while the swarm is on it.
  They drive cross-country to their lanes for now, not along the road, and
  the road-cutting satellite does not stop them yet.
- **Gunship on call** (`allies-gunship`): the base calls an allied gunship
  orbit over a bore, once F3's corridor is open (the spore colonies over the
  approach are dead). One sortie at a time; it can be shot down. Built from
  the allied gunship Thunder Run and Wayline already fly.
- **Ammunition trucks** (`allies-ammo`): supply trucks run from the dumps up
  the farm road to the Burn's battery. Trucks that arrive add a line to the
  barrage (B5); trucks lost on the road do not. New: nothing links a truck to
  the battery's rounds yet.
- **Mech lance** (`allies-mechs`): a pair of Hounds walks with a lane in the
  town and the city, where armour is roadbound. Exists in `HeartLanes`.

Recommended first: armour up the road, because it gives the logistics base a
body on the map now, F6 a reason, and the allied commander (step 4) something
to aim. The gunship and the trucks follow on the same base.

## Open

The owner's pick. Then the base itself: where it stands, what it costs to
lose, and its pace by density.
