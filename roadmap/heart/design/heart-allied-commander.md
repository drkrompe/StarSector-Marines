# The Heart — the allied commander

Status: SHIPPED. The owner's list is built: all four tools, chosen by need. The gunship is risked as the owner answered: flown when called, the known anti-air shelled first.

Written: 2026-10-23
Updated: 2026-10-24 — the gunship flies when called, the known anti-air shelled first (the owner's answer); stragglers, standing lanes, neighbour re-aims.

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
the bore assault takes it once it has rallied.

**How a lane attacks its bore.** On The Burn once the guns allow it, and on
Broad Front where its formation rallies (on the bore's support line, not the
bore), a lane assaults its bore in phases (`BoreAssault`, `heart-bore-assault.md`):
- **base of fire:** machines on a stand-off ring outside the bore's mouth, on
  the lane's near side, facing it; militia on a support line behind them,
  a squad short of it moving up by attack move and holding it once there;
- **assault:** the militia go in on a spent surge;
- **falling back:** the next surge sends them back to the support line.

After a fall the lane takes its next bore armour first. Waves join at the
support line. Behind `battle.heart.boreAssault`.

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
base. Built (`HeartGunship`): all four; rung 2 is the bore the broken lane
is on, rung 3 the satellite F6 places beside the supply road while a swarm body
stands on the road (a satellite merely beside it is the company's objective; an
earlier rung 3 that struck it at once had it down inside ninety seconds).

**Risking the gunship** (owner, 2026-10-24: "Things being lost is a feature.
Invincible shuttles flying only safe missions are not fun", and a commander
would not know where every anti-air of a growing mass is). The gunship flies
when called and losses are accepted. Before a sortie the base shells only the
spore anti-air it **knows** of: colonies and outposts a friendly squad, the
company's or the allies', has seen in the last two minutes, within the base
guns' reach of the middle of the field, whose own reach covers the target or the
way there. One shoot at most every ninety seconds (`heart.air_shoot_<n>`), sized
to kill each, and the sortie goes once none of what it knows stands. What
nobody has seen, or stands past the guns, is still there, so a sortie is
sometimes shot down (`heart.gunship_lost_<n>`).

**Where the company stands in it.** The company is not under this commander.
The commander reads the company's position as one more fact:
- it does not send a wave to plug a gap the company has gone into (F2): a
  broken lane with the company inside its band does not call;
- it keeps a lane's screen off the company's satellite: by construction, since
  a lane only takes the bores in its own band and the company's sector is
  not one.

**Re-aiming a wave (F2).** A broken lane the company has not gone into gets
the next wave to step off from the whole lane nearest it that still has waves to
come, and only from a **neighbour**: a lane with no other wave-taking lane between
it and the gap (the armour-only ground beside the company does not count). Past
its neighbours the gap waits for the company or the next call, rather than a wave
marching across the map to it (`heart.wave_reaimed_<lane>_from_<lane>_<n>` when the commander decides,
`heart.wave_sent_<lane>_from_<lane>_<n>` when the column steps off). Not a column
already marching: those are in the thick of their own fight and seldom get
anywhere new. The gap is where the
broken lane's survivors stood at that moment, or a little short of its bore when
none did: a lane's aim on Broad Front is the bore itself, whose mouth no column
walks into. Once that wave is in, its teams
are the broken lane's and the lane is whole again, measured from what stands now
(`heart.gap_closed_<lane>_<n>`). If the company goes into the gap first, the wave is
called back to its own lane (`heart.wave_recalled_<lane>_<n>`).
