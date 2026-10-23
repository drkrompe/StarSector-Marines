# The Heart — the allied commander

Status: DRAFT — the lane formation is out to the owner (question `heart-lane-formation`).

Written: 2026-10-23

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

The pictures compare the two screens with today's control.

**When to call armour up the road.** It replaces the timer with need. The
park sends a pair to the lane whose armour is weakest, when:
- that lane has lost a machine;
- its screen broke (F2's `lane_broken_N`);
- or its formation stalled at the cap twice running.

There is never more than one pair on the road per lane. The pace by density is
the base's own (`allied-tools.md`).

**When to commit the gunship.** Only once the air is open (F3's
`air_corridor_open`), and only onto something worth one sortie, in this order:
1. a Matriarch engaging a lane's armour (F4);
2. a satellite surging onto a broken lane (F2);
3. the satellite against the road (F6);
4. the bore the most lanes are stalled on.

It comes off station at half structure, and the next sortie waits for the base.

**Where the company stands in it.** The company is not under this commander.
The commander reads the company's position as one more fact:
- it does not send a wave to plug a gap the company has gone into (F2);
- it keeps a lane's screen off the company's satellite.

## Open

- The owner's pick on the formation.
- Whether the commander should also re-aim the next infantry wave (F2's "the
  allied commander re-aims the next wave to close it"), which needs the
  militia column to accept a new target mid-march.
