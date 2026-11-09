# Derelict security network

Status: IN PROGRESS — zones, the director over walkers and flocks, the
derelict heavy line and spotting are built (What is built); the node, repair
drones, waking posts, power branches and every look are not. Feeds slice 5 of
`ruins-expedition-fork.md` through `ruins-occupants.md`.

Written: 2026-11-09

Updated: 2026-11-09 — slices 1 to 4 built on owner pick `derelict-mech-line`'s
recommended option (a line of its own), pending the owner's answer.

Read `derelict-lore-guide.md` in full first, then `ruins-expedition.md`
(The Ruins mission family, Derelict security), `extraction-command.md` (the
swarm director, the pattern this director follows, and the security-network
director it leaves open for Silent Colony) and `mechs-nouns.md`.

## Goal

Derelict security fights on the ground as drone flocks and heavy walkers
(owner, 2026-11-09): dormant security zones that wake on what their sensors
see; flocks that go up first, find, pin and screen on a bounded leash; heavies
that anchor their layers and come to what the flocks have pinned; a
contingency on the vault breach; and a dead node that stops relaying, repairing
and rebuilding. It is a mission director with honest knowledge, like the Rav's
swarm director, and it commands flocks as units.

## Slices

1. **Zones and states.** A security zone is a control node with the cradles,
   walkers, posts and repair drones it controls, and its perimeter. States are
   dormant, alert and awake; the wake triggers are a sensor contact, gunfire
   inside the perimeter and damage to a unit of the zone; an awake zone with no
   contact for a while settles to alert. Linked zones go alert on a relay.
   Unit-test the state rules on a zone alone.
2. **The director.** The security-network director gives each woken zone's
   flock a task (probe, screen, strike, pursue to its leash, return) through
   the flock model (`derelict-drone-flocks.md`), and holds each walker inside its
   perimeter at the layer's chokes and courts, sending it to what the flocks
   have pinned there. In the contingency every flock that can reach the haul's
   escort strikes it and the walkers walk to the egress route and hold it. It
   publishes its phase, each zone's state, each flock's task and only aggregated
   target contexts. `sceneEvidence` scenes for a wake, a relay, a flock's leash,
   a heavy coming to a pin, and the contingency.
3. **The derelict heavy line.** One new heavy chassis with its own art, sound
   and doctrine, outside the player's grid and never fielded by the company
   (`mech-lines.md`), in three loadouts: line frame (heavy cannon, plate),
   siege frame (mortar and missile racks), vault frame (heaviest plate). Domain
   and contingency are liveries. Mech parts and mech doctrine otherwise
   (`mechs-nouns.md`); art and sound through picks.
4. **Spotting.** A siege frame fires on contacts its zone's flocks hold, so a
   flock blinded by smoke or killed leaves it firing on old contacts. Uses the
   existing indirect-fire path; no new knowledge.
5. **The node.** An armoured, unarmed structure. Destroyed, its zone stops
   relaying both ways, stops repairing and rebuilding drones, and is out of the
   contingency's call.
6. **Repair drones.** The tender's builder drone working a damaged post, cradle
   or walker back up while its node stands and its stock lasts, standing down
   under fire.
7. **Posts that wake.** A folded defence post that does not fire or take a
   target until its zone is awake. Secondary: a few per zone at doors and
   courts.
8. **Power branches.** At a site whose power hall feeds the network, a
   junction that cuts a branch powers down the zones on it (`ruins-works.md`).
9. **Looks.** Domain and contingency looks for the cradles, posts and node
   (`tech-families.md`), with picks.

## What is built

Everything is in `battle.derelict`.

- **Zones (slice 1).** `SecurityZone` holds one zone's state (dormant, alert,
  awake), its node cell and a Chebyshev perimeter. A stir (contact, gunfire
  inside the perimeter, damage) wakes it; an awake zone quiet for
  `SETTLE_TICKS` settles to alert, never to dormant; a dead node still wakes
  on its own senses but neither relays nor hears a relay, and is out of the
  contingency's call. `SecurityNetwork` holds the zones and their relay links:
  a waking zone alerts its linked neighbours one hop; `breachVault` calls the
  contingency, which keeps every live zone awake for the rest of the battle.
- **Posture (slice 2, walkers).** `WalkerPosture.decide` is the pure rule per
  walker: dormant or alert holds the post; awake, a line frame comes to the
  nearest pin inside its own perimeter, a vault frame only to a pin within
  `VAULT_REACH_CELLS` of its post, a siege frame holds and fires on the
  freshest sighting within its reach; in the contingency line and vault frames
  hold the egress cell nearest their post (past the perimeter) and a siege
  frame fires on the haul's escort first.
- **Tasks (slice 2, flocks).** `FlockTasking.decide` is the pure rule per
  flock: under a third of strength it goes home; in the contingency it
  strikes the haul's escort off the leash; a dormant zone's flock stays home,
  an alert one screens its node; an awake one strikes inside the perimeter,
  pursues to `LEASH_CELLS` past it, screens the perimeter's edge toward
  anything beyond, and probes where its zone was stirred when it holds
  nothing.
- **The director.** `DerelictSecurityDirector`, ticked by `BattleSimulation`
  every half second, learns only the side's merged contacts
  (`getCommanderInfluence(DEFENDER)`) and its flocks' reports, stirs the zones
  they fall in and the zones whose walkers lost plate or structure, calls the
  contingency when `RuinsVaultObjective.breached()`, and then gives each
  walker lance its posture as a `MISSION_COMMAND` assignment (`defendArea` at
  the post or egress, `attackMove` onto a pin) and each flock its task. A zone
  with no flock up is its walkers' own eyes: they come to and fire on what the
  network's units hold. The haul's escort is the units the network has seen
  within `ESCORT_RADIUS` of the vault since the breach.
- **The flock seam.** `FlockCommandPort` (reports in, task and anchor out) is
  the director's only view of flocks; `FlockSystemPort` implements it on the
  shipped `FlockSystem` (`derelict-drone-flocks.md`), launching one `WASP`
  flock of `FLOCK_SIZE` per zone over its node with the zone's perimeter plus
  the leash, and not re-giving an order the flock already stands on.
- **The heavy line (slice 3).** `MechVariant.SECURITY_WALKER` (`MG-40`, catalog
  `security_walker`) is one new heavy chassis, `automated()`: kept out of the
  Mech Lab's ceilings, the debug bay, field sheds and every pattern list, so
  the company never fields, builds or refits it. `DerelictWalkerFrame` gives
  its three loadouts (line: HC-10, AC-6, two SRM-5; siege: two chainguns, two
  LRM-15 frag racks; vault: two HC-10, two SRM-15) and their plate as a share
  of the chassis's (siege 0.85, vault 1.45). Its art, body plan and sound are
  the Bulwark's until the line's own are picked; it is two-armed like the
  Bulwark so the stand-in draws its arms.
- **Spotting (slice 4).** The director hands a siege frame's squad each fresh
  sighting it fires on as a `BeliefSource.SPOTTED` contact
  (`Squad.observeSpottedContact`). `MechTargeting`, when nothing else is
  engageable, takes a spotted belief seen within `SPOTTED_FRESH_TICKS` for a
  mech with an indirect mount, within that mount's range and the hips'
  traverse, without a line of sight; the shipped no-sight accuracy penalty of
  indirect fire applies. Not built: firing on a cell after the sighting goes
  stale, so a blinded flock leaves the siege frame without a target rather
  than firing on the old contact.
- **The seam into the battle.** `BattleSetup.createRuins` with a derelict
  occupant (`RuinsOccupant.isDerelict`) posts no generic squads or patrols and
  calls `DerelictSecurity.install(sim, grade)`, which lays the grade's zones
  along the vault's first lift route (`zonesAlong`: the vault first, the last
  fifth of the route left to the company, perimeters meeting), posts the
  grade's walkers (`heavies`, `zoneOf`: the vault frame at the vault, the siege
  frame mid-route, line frames outward in turn), sets the egress cells
  (`egressAlong`), launches the flocks and installs the director in place of
  `ExtractionDefenderCommand`. `RuinsExpeditionMissionFactory` names the
  occupant's own faction for a derelict site.

Evidence: `SecurityNetworkTest`, `WalkerPostureTest`, `FlockTaskingTest`,
`DerelictSecurityTest`; fixtures `ruins-derelict-vast-v1` and
`ruins-derelict-extensive-v1`; `createSnapshots
-Psnapshot=derelict-heavies-look` (the walkers at their posts, then the fight
from the first walker contact). Played headless through `battleProbe`: line
frames hold their posts until a pin falls inside their perimeter and then
attack-move onto it; the siege frame engages from its post at twenty-odd
cells.

Not built yet: `sceneEvidence` scenes for a wake, a relay, a leash, a heavy
coming to a pin and the contingency; the node (slice 5); repair drones (6);
waking posts (7); power branches (8); looks for the walker, cradles, posts and
node (3, 9). Balance is not tuned: on the vast fixture the flocks alone wipe a
four-boat company at the landing, outside every zone.

## Constraints

- New mechanics are the flock (its own story), the derelict heavy chassis, the
  folded post's wake gate and the node; anything else new is raised first.
- No knowledge the zone's sensors, flocks and relays do not give it, and no
  damage immunity.
- Silent Colony keeps its scripted opposition until a separate decision moves it
  to this director.

## Done

A derelict expedition on each grade plays through `ruins-occupants.md` and the
owner has played one. Fold the director into `ruins-expedition.md`, the units
into their noun docs, add a row to `shipped.md`, and delete this story.
