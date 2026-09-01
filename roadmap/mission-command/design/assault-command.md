# Assault command

Status: ACTIVE — paired production command is implemented; live and canonical-duration acceptance remain.

Updated: 2026-09-01 — the force plan owes the defender commander enough
mobile squads to hold a reserve.

Written: 2026-08-27

Read `mission-command-nouns.md` for the shared architecture. Assault currently
retains elimination objective authority while its commanders solve the search
and security problem without using hidden occupancy.

Assault is a two-dimensional search-and-destroy command duel, not a directional
front. Both perspectives share one stable rectangular partition as public
geometry but keep separate command pictures.

## Marine search

Marines own sector coverage, deterministic sweep legs, active or suspected
contact reports, stable sector affinity, neighbor reinforcement, and bounded
rechecks. Reachable unfinished sectors receive one squad before surplus
concentrates. Fresh direct evidence marks a sector active; older or indirect
evidence marks it suspected. Missing belief is not positive clearance, and a
completed first pass becomes a bounded recheck while objective truth says the
battle continues.

## Defender area security

Defenders own authored strongpoints, coarse security areas, routine coverage,
and a bounded mobile reserve. Setup garrisons retain `GARRISON` authority;
starting patrols alone enter `assault-defender`, and reinforcement stays
external. Routine squads spread before doubling. Readiness squads hold real
`DEFEND_AREA` orders, and a legal area report may mobilize one responder per
threatened area before known hostile strength authorizes bounded
counter-concentration. Reserve exchange may keep one dispatchable squad without
breaking the minimum routine-coverage floor.

**A reserve has to be handed the force for one.** The force plan garrisons
greedily down to a mobile floor, so on a map with tactical nodes to spare the
floor is exactly what the commander owns — and a lone squad is routine coverage
by the reserve's own rule, never a reserve. Assault kept a one-squad floor
from before Raid and Extraction were written; the maps it ran on starved the
garrison pass of nodes and left it several squads by accident, and a fortress
map that emits a node per packed room took the accident away. The mobile floor
is now three squads' worth for all three missions that hold a reserve.

A defender **area report** comes only from defender influence. Direct evidence
is active; older or indirect evidence is suspected. Each identity remains
latched through its disclosed confidence expiry so report disappearance does
not become hidden death knowledge. Response rallies snap to walkable
strongpoint/area context and never reveal the contact cell. Expiry returns the
responder to readiness.

`DEFEND_AREA` is Assault-specific. It is distinct from Conquest tracks and
Sabotage sites, and it composes with local contact doctrine after the squad
develops its own belief.

## Variants and evidence

Opening Operations reuse the Assault battle type but now run a paired frozen
preserve/secure scenario command rather than the general search picture. Their
campaign objective and finite-force laws remain with Early Operations. The
current deterministic anchors are command geometry; authored COMMS/depot place
legibility remains a separate scenario-content and live-acceptance concern.

The Assault picture publishes sectors/areas, status, coverage, sweep/rally,
reports, strongpoints, reserve, roles, directives, and reasons. The shared
evidence runner selects it with `-Pmission=assault`. Open acceptance is tracked
by `assault-search-sector-picture.md`, `assault-area-defense-command.md`, and
`opening-operation-objective-command.md`.
