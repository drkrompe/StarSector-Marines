# The Heart — the allied commander

Status: SHIPPED. The owner's list is built: all four tools, chosen by need. Open: the gunship's road-satellite rung (waits for F6's map placement) and re-aiming waves.

Written: 2026-10-23
Updated: 2026-10-23 — company-in-the-gap and stalled-bore rules built; the standing model folded into `heart-nouns.md` (Allied commander).

The lanes' commander is the heart's
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

Behind `battle.heart.laneFormation`: a lane forms only once it has armour, and
after it rallies on its bore it attacks the bore as before.

**When to call armour up the road.** It replaces the timer with need, read
with the Cordon commander's signal names (`cordon-allied-command.md`). The
commander asks the base for a pair (`AlliedProducer.request`, the lane as the
place) for the lane whose armour is weakest, when that lane is:
- **uncovered**: it has lost a machine;
- broken: its screen broke (F2's `lane_broken_N`);
- **stalled**: its formation pushed through two contacts at the cap in a row, clearing none (`heart.lane_stalled_<lane>`).

There is never more than one call on the road per lane (`onRoad`). Built on
the shared `AlliedToolShed`, behind `battle.heart.armourByNeed`. The lanes'
timed waves become the vehicle park's stock, and the base keeps its pace by
density as restock (`allied-tools.md`). A lane that is uncovered, broken or stalled
calls its next machines, and `heart.armour_called_<lane>_<n>` marks each call.
- In town and city, where hulls are roadbound, it asks for a **mech lance**
  (two Hounds) first, and an armour pair when no lance is in stock.
- In rural country it asks for an armour pair (a breakthrough tank and a
  technical).

**When to commit the gunship.** Only once the air is open (F3's
`air_corridor_open`), and only onto something worth one sortie, in this order:
1. a Matriarch engaging a lane's armour (F4);
2. a satellite surging onto a broken lane (F2);
3. the satellite against the road (F6);
4. the bore the most lanes are stalled on.

It comes off station at half structure, and the next sortie waits for the
base. The air gate sits inside the base's `request`, so the commander may ask
and be refused. Built (`HeartGunship`): rungs 1, 2 (the bore the broken lane is
on) and 4; rung 3 waits for the road satellite F6 places. The gate is the
colonies of the approach satellites. Nothing in play
reaches those colonies: the company and the lanes stop well short of the
satellites. So while the commander wants a sortie and the air is shut, the
base shoots the air open, a shoot on the living colonies at most every ninety
seconds (`heart.air_shoot_<n>`), and the sortie follows once they are dead.

**Where the company stands in it.** The company is not under this commander.
The commander reads the company's position as one more fact:
- it does not send a wave to plug a gap the company has gone into (F2): a
  broken lane with the company inside its band does not call;
- it keeps a lane's screen off the company's satellite: by construction, since
  a lane only takes the bores in its own band and the company's sector is
  not one.

## Open

- The gunship's rung 3, the satellite against the road, once F6's satellite
  is placed (`heart-map-intent.md`).
- A sortie dies fast over the bores: the first played one took two hits from
  the colonies round its target, eight seconds apart, and was down to 2 of 60
  structure before it could turn home. A reading, not tuned.
- Whether the commander should also re-aim the next infantry wave (F2's "the
  allied commander re-aims the next wave to close it"), which needs the
  militia column to accept a new target mid-march.
