# The Heart — the allied commander

Status: SHIPPED. The owner's list is built: all four tools, chosen by need. Open: how the gunship is risked (the owner's question).

Written: 2026-10-23
Updated: 2026-10-24 — stuck members straggle out of a lane's formation; waves join a standing lane.

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
- **Late waves** from the vehicle park join the main where they catch it up,
  and a machine or squad stuck short of its slot is a straggler the formation
  leaves behind (`allied-command.md`, The slowest sets the pace).
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
and be refused. Built (`HeartGunship`): all four; rung 2 is the bore the broken lane
is on, rung 3 the satellite F6 places beside the supply road while a swarm body
stands on the road (a satellite merely beside it is the company's objective; an
earlier rung 3 that struck it at once had it down inside ninety seconds). The gate is the
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

**Re-aiming a wave (F2).** A broken lane the company has not gone into gets
the next wave to step off from the whole lane nearest it that still has waves to
come (`heart.wave_reaimed_<lane>_from_<lane>_<n>` when the commander decides,
`heart.wave_sent_<lane>_from_<lane>_<n>` when the column steps off). Not a column
already marching: those are in the thick of their own fight and seldom get
anywhere new. The gap is where the
broken lane's survivors stood at that moment, or a little short of its bore when
none did: a lane's aim on Broad Front is the bore itself, whose mouth no column
walks into. Once that wave is in, its teams
are the broken lane's and the lane is whole again, measured from what stands now
(`heart.gap_closed_<lane>_<n>`). If the company goes into the gap first, the wave is
called back to its own lane (`heart.wave_recalled_<lane>_<n>`).

## Open

- Every played sortie so far was shot down by spore anti-air, three hits each
  against 60 structure and 30 armour. The first flew into bore 4's outposts
  (which is why the gate counts outposts now); the second, with the approach
  clear, flew into mother-1's colonies, which the gate does not look at. How
  the commander risks it is the owner's question `heart-gunship`: (a) only where
  no standing anti-air of any bore reaches the target or the way there, (b) as
  before, losses accepted, (c) the base's guns clear what reaches the way first,
  (d) a sortie three times as hard to bring down. `HeartGunship.Policy` holds all
  four behind `-Dbattle.heart.gunshipPolicy` until the pick.
