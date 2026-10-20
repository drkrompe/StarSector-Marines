# Machine garrison assault

Status: IN PROGRESS

Written: 2026-10-20

The player-facing half of the owner's decision in `garrisonables.md` (Decided
direction): mechs and vehicles never man a garrisonable, but an order onto one
the selection believes is enemy-held is an **assault by fire** from a
stand-off. Infantry still clears and holds.

## Today

A selected mech, lance or vehicle that right-clicks a trench or a held building
gets MOVE: the machine walks or drives to the nearest reachable cell under the
click, which can be in the trench or against the building's wall, and fights
whatever its doctrine or turret picks on the way. Infantry get OCCUPY, or CLEAR
in the hostile colour when the squad believes somebody is inside.

## The order

**Resolution.** A click by a mech (single or lance) or an armed vehicle on a
**garrisonable target** — a fighting-position run, else a held place, by the
same lookups the infantry occupy order uses (`OccupyPositionDecision`,
`HoldPlaceDecision`) — is an **assault** when the side believes a hostile is in
it: some squad of the side holds a contact last placed on the run (within a cell
of one of its cells) or inside the place's footprint. Otherwise the click is the
ordinary move. Belief, never truth: an occupant nobody has reported leaves the
cursor reading MOVE.

The machine reads the **side's** reports rather than its own squad's, because a
machine on a garrison is supporting fire for whoever found the garrison; a
vehicle has no squad of its own to read. That is the side's own picture, of the
same class as the reports a fort's guns raise, and every shot still needs the
machine's own line.

**Cursor.** `ASSAULT`, in the hostile colour, the same treatment as clear and
hold. An unarmed vehicle (a supply truck, a scout car) never assaults; it moves.

**Stand-off.** Accepted, the order picks one stand-off cell for the machine,
outside the target's footprint by a margin, at about seven tenths of the
machine's reach (never closer than a minimum, never at or past full reach),
reachable from where it stands, and preferring in turn: a cell with a clear line
to a believed occupant; a cell whose line to one stops on a wall of the target
(the wall that shelters him); any cell on the ring. Among equals, the nearest to
the machine. A lance's members take cells apart from each other. A mech that has
stood at its cell with nothing to fire at for a few seconds chooses again from
what is believed now. A vehicle drives to its cell by an ordinary vehicle move
route and is refused with the move's own reason when no route exists.

**Fire.** While the order stands the machine fires, in this order:

1. a visible enemy inside the close-threat radius (self-defence, unchanged);
2. a **believed occupant** of the target it has a clear shot at, inside reach;
3. the **sheltering wall**: the first wall on its line to a believed occupant,
   when that wall belongs to the target and a weapon of the machine does wall
   damage, through the same point-fire path an ordered breach uses
   (`WallBreachSystem` for a mech's mounts, the turret's point fire for a
   vehicle). No wall is fired on while a friendly stands within a few cells of
   it, so infantry going in are not shelled;
4. otherwise whatever its ordinary targeting picks.

**Never enters.** The order owns the machine's locomotion: it walks or drives to
the stand-off and stays there. Doctrine, a vehicle's duel-band engagement and a
lance's cohesion do not move it while the order stands.

**Ends.** When the side has believed nobody in the target for five seconds the
order is over and the machine is handed back to its doctrine or its idle hull
where it stands. Any new order supersedes it; a hard withdrawal, a crippled
chassis, a wrecked hull, direct control or a broken lance end or suspend it as
they do a move.

## Composing with infantry

A mixed selection resolves the one click per squad: infantry get clear and hold,
machines get the assault. The machines open the place from outside while the
infantry close; the danger-close rule keeps wall fire off the wall the infantry
are at, and the order ends by itself once the infantry's clearing leaves nobody
believed inside, so the machines stop shooting at a building their own side now
holds.

## Not here

- The defender commander's or an attacking commander's use of machines against
  a garrison (`reactive-garrisons.md` owns the defender's belt manning).
- Screening a manned place (open question in `garrisonables.md`).
- A vehicle choosing a new stand-off after acceptance.
- Sandbag parapets as walls: a trench is assaulted by direct fire only.

## Acceptance

- Unit tests: the intent for a single mech, a lance, an armed and an unarmed
  vehicle over a believed-held place, a believed-held trench, an unbelieved one
  and plain ground; the stand-off chooser keeps out of the footprint and prefers
  a line to the occupant; the sheltering-wall pick.
- Scene `machine-assault` (sceneEvidence), loops for a mech, a tank, a control
  with nobody believed inside, and a mech with an infantry squad given the same
  click. Mechanics only: the order resolved to an assault, the machine never
  stood inside the footprint, it fired on the place's occupants or walls, no
  wall round landed danger-close to a friendly, and the order released when the
  place stopped being believed held (conditional on that happening).
