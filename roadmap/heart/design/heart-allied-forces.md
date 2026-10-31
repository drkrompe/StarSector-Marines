# The Heart — allied forces and their tools

Status: SHIPPED — the owner's answer to `heart-allied-tool`: all four tools, the commander choosing by need (`heart-allied-commander.md`). The standing model is `heart-nouns.md` (Logistics base, Rounds on hand).

Written: 2026-10-23
Updated: 2026-10-23 — all four built; folded into `heart-nouns.md`.

`heart-nouns.md` is the model; the laws
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
  barrage (B5); trucks lost on the road do not. Built as the shed's fourth
  tool (`HeartGuns`): the Burn's battery and the flak pits the beats dig keep
  their rounds on hand (`GunStock`). Every barrage shell is a round off its
  gun, and a dry gun fires one shell in four. A place under half its rounds
  calls a truck from the vehicle park, and the truck's arrival refills it.
  B5's trucks come from the same shed (`HeartGuns.send`): every truck has one
  producer.
- **Mech lance** (`allies-mechs`): a pair of Hounds walks with a lane in the
  town and the city, where armour is roadbound. Exists in `HeartLanes`.
- **Fire-support section** (`heart-allied-tools-next.md`): an LRM carrier and a
  mortar carrier, called by a lane whose ring cannot hurt its bore, standing
  behind its support line and firing on what its militia see.

The owner's answer: offer all four, and let the commander choose by need. The
trucks may also rearm turrets and flak pits at the bases. The base stocks the
tools as one `AlliedToolShed`:
- armour pairs, one per lane, restocking at the old wave pace;
- one gunship, gated on open air;
- mech lances, in town and city;
- one ammunition truck, restocking every ninety seconds. On The Burn, which
  has no lanes, the park is a shed of trucks alone.

## The military's air

Besides the base's tools, the world's military attaches a forward observer to
each Broad Front lane, and its air wing strikes the masses those observers see
(`heart-nouns.md`, Forward observer). On The Burn each battlegroup carries one,
on the wing flying from the military's airbase behind the start line.

## Open

- What it costs to lose the base.
- The armour drives cross-country, not along the supply road, and F6's
  road-cutting satellite is not placed yet (`heart-map-intent.md`).
