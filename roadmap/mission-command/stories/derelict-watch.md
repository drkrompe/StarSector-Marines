# Derelict watch: how a live ruin moves, wakes, grows heavier and repairs

Status: IN PROGRESS — four picks open (`derelict-movement`, `derelict-waking`,
`derelict-walker-sizes`, `derelict-repair`), each built on its recommended
option (a) while open. What is built is listed under each mechanic.

Written: 2026-11-10

Read `derelict-lore-guide.md` in full first, then `derelict-security-network.md`
(What is built), `ruins-pressure.md` (The model), `ruins-guardians.md` and
`ruins-expedition.md` (Occupants: Derelict security; The ruins' doors are leaf
doors).

## Goal

The owner (2026-11-10): "We should keep pursuing work there to flesh them out
for mechanics like we did for the ravs. We could consider how they move through
the ruins and what their growth / scaling looks like throughout the mission.
For instance we could have the facility waking opening more passages and
releasing more swarms. We could also look at medium and large walkers and
different mechanics like "repair"."

The Rav got a campaign arc and a mission arc of their own: blooms that grow and
spread, bores that wake, strains that arrive with time. Derelict security has
its pieces (zones, flocks, the heavy line, nodes, posts, repair drones, the
hive and the vault frame), but over a mission it is flat until a zone wakes and
then the contingency. This story gives it the four things the owner named,
each in the watch's own terms, never the Rav's:

| | Rav | The watch (this story) |
| --- | --- | --- |
| Moves | each beast hunting its own prey field; bores erupt | on fixed **rounds** while the site sleeps, through its **service ducts** between vents, and to what its flocks pin once awake |
| Grows over a mission | it breeds: bores feed and spawn | it **wakes**: the facility brings up stock it always had, a stage at a time, and never more than it had |
| Heavier units | strains by grade and time | the **rounds walker** (medium) and the **hall frame** (large) beside the shipped heavy |
| Recovers | it does not; it replaces | it **repairs**: wrecks are raised from a zone's **service bay**, unless the company denies them |

The lore guide's laws hold throughout: the watch does not grow, eat or hunt; its
numbers are fixed and only woken; it never pursues past its perimeter except in
the contingency; flocks never enter a roofed cell; it knows only what its
sensors, flocks and relays tell it.

## 1. Movement: rounds and service ducts

Pick `derelict-movement`.

Today a dormant or alert zone stands still: its walkers hold their posts and its
flock waits at its doorway. The briefing already tells the player otherwise
("Movement on fixed patrol paths"), so the watch should be seen moving before it
is awake.

- **Rounds.** Each zone has a **round**: a loop through the places it keeps,
  its route posts and the interior chambers, courts and joins inside its
  perimeter, in order round the node (`PatrolRound`). While the zone is dormant
  or alert its **rounds walker** walks the round, a place at a time, pausing
  at each. It is the facility's watchman: what it sees is a contact like any
  other, and wakes its zone. Heavies stay powered down at their posts until the
  zone wakes; they are the anchor, not the patrol.
- **Service ducts.** The plant was built with service ducts behind its walls,
  and the drones still use them. A duct opens at a **drone vent**: an open-sky
  cell (the exterior near each zone's node, and each interior court, which is a
  light well) with a hatch in the paving. A flock may go into a vent and come up
  at another vent of its own zone or a linked zone after a transit time set by
  the duct's length (`ServiceDucts`). A flock in a duct is off the map: nobody
  sees it and nobody can shoot it. So a flock whose zone lies indoors reaches
  the light wells ahead of the company without ever passing under a roof, and a
  company in a court is never sure the sky above it is clear.
- **When a flock uses a duct.** A flock tasked to an anchor whose open-sky way
  is longer than the duct's way plus its transit (every way round a
  megastructure is), takes the duct to the vent nearest the anchor. A flock
  going home does the same.
- **Counterplay.** A company charge over a vent collapses it for good, as it
  collapses a wake point (`RuinsWorks.collapses`): the ducts behind it close at
  that end. A dark zone's vents are shut (no power to the hatches). The vents are
  rail markers once seen.

| Option | What moves |
| --- | --- |
| a (recommended) | Rounds walked by the rounds walker while a zone sleeps, and flocks through service ducts between drone vents |
| b | Rounds only: the watch is seen patrolling, but flocks keep to open ground |
| c | Ducts only: flocks surface in the light wells, and everything else stands at its post until woken (today's posture) |

## 2. Waking: the facility comes up in stages

Pick `derelict-waking`.

The owner: "the facility waking opening more passages and releasing more
swarms". The site's stir (`ruins-pressure.md`) already climbs with time inside,
gunfire and loud acts, a level at a time, and never falls. At a derelict site
the stir is also the **facility's wake**: each level brings up something the
plant always had and had shut down (`FacilityWake`). Nothing is built or grown:
a reserve bay holds the flock it was stocked with, and a bulkhead was always
there. That keeps the watch apart from the Rav.

| Stage (stir level) | What wakes |
| --- | --- |
| Quiet | Nothing: dormant zones, rounds walked |
| Stirring | The **service ducts** come up: drone vents open (section 1) |
| Roused | The **reserve bays** open: each online zone's sealed reserve bay puts up a second flock, the deepest zones first |
| Hunting | The **security bulkheads** open: shut-down service passages between halls open as short ways, and every online zone goes alert where it stands |
| Contingency (the breach) | Everything left wakes, as today |

- **The reserve bay.** A sealed cradle housing beside a zone's node, plated
  while shut. It holds one flock (`FLOCK_SIZE`), launched when its stage comes.
  It is a target the company can deny: destroyed while shut, its flock never
  rises. A bay whose zone is dark or has lost its node stays shut at its stage,
  and opens if the zone comes back online later in that stage or after.
- **Bulkheads** are the doors the network keeps shut: service passages between
  halls, two cells wide, that the delve check never counts as a way (the delve
  must hold without them). Hunting opens them for both sides: the watch's way to
  the company gets shorter, and so does the company's way to the vault. A
  bulkhead is a leaf door (`ruins-expedition.md`, The ruins' doors are leaf
  doors) in the sealed state until its stage, then answers. Built on the doors
  session's leaf door; until that lands, a bulkhead's cells stay wall.
- **Pushes stay pushes.** A stir step still sends its push out of the reserve
  (line frames at a wake point). The wake is separate: a push is never a flock,
  and a reserve bay is never part of the reserve.
- **Cues.** Each stage's comms line in the watch's voice ("Vent hatches cycling
  in the walls", "Bay doors opening below", "Bulkheads unlocking across the
  site"), on the step sting that already plays.

| Option | What sets the stage |
| --- | --- |
| a (recommended) | The stir, as above: time inside and noise wake the whole site a stage at a time |
| b | Depth: a zone's reserve bay and bulkheads open when the company first enters its layer, so the site wakes ahead of the company as it goes in |
| c | Only the breach: the site stays as today until the vault is breached, and the contingency opens every bay and bulkhead at once |

## 3. Medium and large walkers: the rounds walker and the hall frame

Pick `derelict-walker-sizes`.

The shipped MG-40 is the heavy: sized for a two-cell corridor, it holds a layer
and comes to pins (line, siege and vault frames). The plant's ways come in three
widths (one-cell side-room and store doors, two-cell corridors and bulkheads,
three-cell hall joins and four-cell cargo docks), and each walker size is the
one its ways admit (`DerelictWalkerClass.fits`). The line stays one family:
each size is a chassis of the derelict line with the sentinel's look and its
own leg rig at its size (`mech-lines.md`, law 1: never a recolour; one look,
three builds).

| Class | Chassis | Fits | Role |
| --- | --- | --- | --- |
| Medium | **MG-25 rounds walker**: low and quick, light plate, a chaingun and an SRM-5 | every way, one-cell doors too | Walks its zone's round (section 1); awake, the zone's first answer to a pin anywhere inside the perimeter, side rooms and stores included |
| Heavy | **MG-40** (shipped): line, siege and vault frames | two-cell ways and wider | The anchor, as built |
| Large | **MG-70 hall frame**: a hall-bound walker twice the heavy's height, heaviest plate, a heavy cannon pair and fragmentation racks | three-cell ways and wider: halls, joins, cargo docks, never a corridor | Holds the plant's largest hall; the company fights it from the corridor mouths it cannot enter |

- **The hall frame and the doors.** It cannot pass a corridor or a two-cell
  door. A dead door (stuck part-open, the cells under its leaves wall) narrows a
  join below its width; a hall frame that must pass one **forces** it, standing
  against it for `FORCE_SECONDS` until a leaf tears out, and the door is forced
  for good, open to everyone (`DerelictWalkerClass.FORCE_SECONDS`). A sealed
  door holds it. So the company can keep a hall frame in its hall by leaving a
  door sealed, and should mind that a dead door between it and the vault route
  will not stay shut.
- **The hall frame and the contingency.** In the contingency it walks to the
  egress only where its way fits; a hall frame whose way out is all corridor
  stays on its hall, and its hall is then the egress fight.
- **Strength by grade** (UNTUNED): a rounds walker per zone from widespread (one
  at scattered); a hall frame at extensive, two at vast, posted in the largest
  plant halls. Neither is part of the stir's reserve; pushes stay line frames.
- **Counterplay.** The rounds walker is light: a fire team with a rocket kills
  it. The hall frame is the watch's heaviest piece outside the vault, and its
  weakness is its size: it cannot follow, it cannot turn in a corridor mouth,
  and its knees are as far apart as its hall is wide, so a flanking squad finds
  them.

| Option | Sizes |
| --- | --- |
| a (recommended) | Both: the rounds walker and the hall frame beside the heavy |
| b | The hall frame only; the heavy walks the rounds |
| c | The rounds walker only; the halls keep the heavy |

## 4. Repair: service bays and raised wrecks

Pick `derelict-repair`.

Shipped: each zone's repair drone mends plate on damaged posts, cradles and
walkers from a stock it carries, and stands down under fire. That is upkeep,
not recovery: what the company kills stays dead. The owner asked for repair
that gives the company targets to deny.

- **The service bay.** A low armoured structure beside each zone's node, where
  the zone's repair stock is held (`ServiceBay`). The drone carries a load,
  works it off, and flies back to the bay to take another. Destroying the bay
  leaves the drone only what it carries. A dark zone's bay issues nothing.
- **Wrecks.** A walker or a post of a zone that is destroyed leaves a **wreck**
  where it fell. A drone with a load works a wreck for `RAISE_SECONDS` and the
  unit is **raised**: it stands again at its post's zone with a share of its
  plate and structure (`Wreck.RAISE_SHARE`), its knees whole. A wreck is raised
  once; a raised unit destroyed again is scrap. A raise costs the bay
  `RAISE_COST`, so a bay raises two or three wrecks a battle at most.
- **What the drone does first.** A raise under way continues; otherwise the
  most worn charge below half, then a wreck, then any charge below
  `MEND_BELOW`. It still stands down under fire and goes home with its node
  down, its zone dark or its bay empty.
- **Denial.** The company scraps a wreck by standing infantry on it for
  `SCRAP_SECONDS` with no hostile near, or by setting a charge on it; a scrapped
  wreck cannot be raised. Shooting the drone, the bay or the node, or cutting the
  zone's branch, all stop repairs. So the repair line gives the company a
  choice after every kill: spend a fire team's time scrapping the wreck, or push
  on and risk meeting it again.
- **Cues.** A drone working a wreck shows its welding sparks, and the wreck's
  marker reads "being raised" once seen; a scrapped wreck reads as scrap.
- **Not raised:** flocks (their cradles rebuild drones, as built), the node, the
  bay itself, guardians (the hive and the vault frame), pushes.

| Option | Repair |
| --- | --- |
| a (recommended) | Service bays and raised wrecks, with scrapping |
| b | Service bays only: the drone reloads at its bay, and the bay is the one target; nothing dead comes back |
| c | Raised wrecks only, from the drone's own stock as today; no bay to strike |

## Slices

1. **Rules.** The round, the ducts, the wake stages, the walker classes and
   what they fit, the bay, wrecks, raising and scrapping, each a pure rule
   unit-tested where it is decided.
2. **Battle.** The director walks the rounds, sends flocks through ducts, opens
   reserve bays at their stage, stands the rounds walkers and hall frames,
   keeps wrecks and raises them.
3. **Cues.** Comms lines per stage, the vent and wreck markers, the drone's
   sparks over a wreck.
4. **Looks.** The rounds walker and the hall frame are the sentinel at their
   sizes until art says otherwise; the vent, the reserve bay and the service
   bay are housings painted after the node and the cradle
   (`art-source/derelict-security`), through ruins-theme-art and a pick.
5. **Bulkheads.** With the doors session's leaf door: bulkheads laid by the
   ground, sealed until the hunting stage.

## Constraints

- Strength and timings are not tuned from autopilot runs: they wait on the owner
  playing a derelict battle. No session runs a mission to judge them.
- No growth: everything the wake brings up was on the site from the start.
- No knowledge the zone's sensors, flocks and relays do not give it.
- Guardians stay their own: the hive keeps its hatch rhythm, the vault frame its
  brace; neither is raised.

## Done

Each of the four mechanics stands in a played derelict battle and the owner has
played one. Fold the mechanics into `derelict-lore-guide.md` and
`ruins-expedition.md` (Occupants), the units into their noun docs, add a row to
`shipped.md`, and delete this story.
