# Derelict security network

Status: PLANNED — owner direction; nothing built. Feeds slice 5 of
`ruins-expedition-fork.md` through `ruins-occupants.md`.

Written: 2026-11-09

Read `derelict-lore-guide.md` in full first, then `ruins-expedition.md`
(The Ruins mission family, Derelict security), `extraction-command.md` (the
swarm director, the pattern this director follows, and the security-network
director it leaves open for Silent Colony) and `mechs-nouns.md`.

## Goal

Derelict security fights on the ground: dormant security zones that wake on
what their sensors see, hold their perimeters, run the contingency when the
vault is breached, and stop relaying and repairing when their node dies. It is
a mission director with honest knowledge, like the swarm's.

## Slices

1. **Zones and states.** A security zone is a control node with the posts,
   cradles, walkers and repair drones it controls, and its perimeter. States are
   dormant, alert and awake; the wake triggers are a sensor contact, gunfire
   inside the perimeter and damage to a unit of the zone; an awake zone with no
   contact for a while settles to alert. Linked zones go alert on a relay.
   Unit-test the state rules on a zone alone.
2. **The director.** The security-network director owns its zones' mobile units:
   inside the perimeter while awake, back to it after contact, never past it,
   except in the contingency, when every mobile unit that can reach the vault
   zone or the route out is called in. It publishes its phase, each zone's
   state and only aggregated target contexts, as the swarm director does.
   `sceneEvidence` scenes for a wake, a relay, the perimeter and the
   contingency.
3. **Posts and cradles that wake.** A folded defence post that does not fire or
   take a target until its zone is awake, and a drone hub that keeps its drones
   docked until then. Existing post and hub mechanics otherwise.
4. **The node.** An armoured, unarmed structure. Destroyed, its zone stops
   relaying both ways, stops repairing and is out of the contingency's call.
5. **Repair drones.** The tender's builder drone working a damaged post or hub
   back up while its node stands and its stock lasts, standing down under fire.
6. **The security walker.** One unpiloted mech chassis in two liveries (Domain,
   contingency), light patrol frame and heavy vault frame by loadout. New art
   through the usual picks; ordinary mech parts.
7. **Power branches.** At a site whose power hall feeds the network, a
   junction that cuts a branch powers down the zones on it (`ruins-works.md`).
8. **Looks.** Domain and contingency looks for the folded posts, cradles, drones
   and node (`tech-families.md`), with picks.

## Constraints

- Every unit is an existing mechanical role, except the folded post's wake gate
  and the node; anything else new is raised first.
- No knowledge the zone's sensors and relays do not give it, and no damage
  immunity.
- Silent Colony keeps its scripted opposition until a separate decision moves it
  to this director.

## Done

A derelict expedition on each grade plays through `ruins-occupants.md` and the
owner has played one. Fold the director into `ruins-expedition.md`, the units
into their noun docs, add a row to `shipped.md`, and delete this story.
