# Derelict security network

Status: PLANNED — owner direction (flocks and heavy mechs, 2026-11-09);
nothing built. Feeds slice 5 of `ruins-expedition-fork.md` through
`ruins-occupants.md`.

Written: 2026-11-09

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
