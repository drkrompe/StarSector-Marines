# The Heart — the allied commander

Status: DRAFT. Built: lanes as screened advances by country, and the tools called by need from the vehicle park's tool shed.

Written: 2026-10-23
Updated: 2026-10-23 — commander built on the shared `AlliedToolShed`; mechs first in town and city; gunship gated on open air.

Step 4 of `heart-mission-depth.md`. The lanes' commander is the heart's
mission, carrying out its choices through the shared machinery:
- `allied-command.md` (station, carry, the screened advance);
- `allied-tools.md` (the logistics base and its catalogue);
- `heart-allied-forces.md` (which tools the heart has).

It is still not a general commander that weighs the whole field. It is the
authored kind the owner asked for: rules a mission states, so the allies look
smarter than they are. The laws hold: allies never end the battle, and the
commander never orders the company.

## What it decides

**How a lane goes forward.** Each lane is a screened advance from its foot to
its next bore, re-routed when that bore falls.
- **Screen by country:** infantry screen in woods and streets, armour screen
  on open farmland. The lane's ground picks it, so one lane can change screen
  as it leaves the farmland.
- **Fighting advance on contact:** the formation turns to the threat, and
  pushes through at the cap, because the swarm never lets up.
- **Late waves** from the vehicle park join the main where they catch it up.
- **A broken screen** hands the lead to the main.

The pictures compare the two screens with today's control. Built behind `battle.heart.laneFormation`: a lane forms only once it has armour, and after it rallies on its bore it attacks the bore as before.

**When to call armour up the road.** It replaces the timer with need, read
with the Cordon commander's signal names (`cordon-allied-command.md`). The
commander asks the base for a pair (`AlliedProducer.request`, the lane as the
place) for the lane whose armour is weakest, when that lane is:
- **uncovered**: it has lost a machine;
- broken: its screen broke (F2's `lane_broken_N`);
- stalled: its formation stalled at the cap twice running.

There is never more than one call on the road per lane (`onRoad`). Built on
the shared `AlliedToolShed`, behind `battle.heart.armourByNeed`. The lanes'
timed waves become the vehicle park's stock, and the base keeps its pace by
density as restock (`allied-tools.md`). A lane that is uncovered or broken
calls its next machines, and `heart.armour_called_<lane>_<n>` marks each call.
- In town and city, where hulls are roadbound, it asks for a **mech lance**
  (two Hounds) first, and an armour pair when no lance is in stock.
- In rural country it asks for an armour pair (a breakthrough tank and a
  technical).
- Stalled does not call yet.

**When to commit the gunship.** Only once the air is open (F3's
`air_corridor_open`), and only onto something worth one sortie, in this order:
1. a Matriarch engaging a lane's armour (F4);
2. a satellite surging onto a broken lane (F2);
3. the satellite against the road (F6);
4. the bore the most lanes are stalled on.

It comes off station at half structure, and the next sortie waits for the
base. The air gate sits inside the base's `request`, so the commander may ask
and be refused. Built (`HeartGunship`) for the first rung and for a broken
lane's bore, gated on the colonies of the approach satellites. In the first
played Broad town run they never died, so no sortie was called.

**Where the company stands in it.** The company is not under this commander.
The commander reads the company's position as one more fact:
- it does not send a wave to plug a gap the company has gone into (F2);
- it keeps a lane's screen off the company's satellite.

## Open

- **Ammunition trucks** as the shed's fourth tool: the owner asked for them to
  rearm the bases' turrets and flak pits as well as the Burn's battery
  (`GunStock`, `SupplyDelivery`). B5 still sends its trucks directly.
- **Stalled** as a third reason to call.
- Whether the commander should also re-aim the next infantry wave (F2's "the
  allied commander re-aims the next wave to close it"), which needs the
  militia column to accept a new target mid-march.
