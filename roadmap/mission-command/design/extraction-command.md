# Extraction command

Status: ACTIVE — generic Extraction has an authoritative payload law and paired corridor/interdiction commanders; canonical/live acceptance and authored variant pictures remain open.

Written: 2026-08-27

Updated: 2026-08-27 — added bounded defender source security and belief-driven interdiction without disclosing hidden corridor truth.

Read `mission-command-nouns.md` for the shared architecture and
`campaign-event-nouns.md` for the authored event stakes.

Extraction is a mission family organized around a protected **payload or
cohort** moving between authored endpoints. Its common geometry is a
**corridor** with pickup, route, screen, delayed-element, and egress contexts.
The payload system—not mission command—owns membership, motion/progress,
survival, boarding, and terminal outcome.

Every Extraction-family payload publishes one small objective projection:
stable identity and kind, source and optional egress, current phase and
location when represented, initial/active/boarded/lost counts, normalized
progress, alarm, escort/control relationship, and failure reason. A variant
omits facts it does not own rather than manufacturing them; Silent Colony's
archive branch therefore has no fake egress.

## Generic recovery law

Generic Extraction selects one reachable high-value source in defender ground
and a return corridor to the first Marine landing area. The recoverable package
is abstract mission state rather than a hidden carrier entity. Marines must
establish uninterrupted, uncontested control at the source. Once released, the
package advances only while a settled Marine combatant remains within its
escort leash and hostile control does not interdict the current package cell.

Sustained unattended defender control loses the package. Sustained abandonment
without either escort or hostile control also fails the mission, but ordinary
pauses and contact do not. At egress, uninterrupted uncontested escort completes
boarding. Success therefore requires neither defender elimination nor one
particular carrier to survive; loss and abandonment are explicit terminal
objective results. A bounded evidence run that reaches neither terminal state
remains a timeout.

The source is authored/public geometry. Marines receive exact egress,
payload location, progress, counts, and current escort ownership. Defenders
receive the source, a legal identity-free alarm, and terminal state, but not
the Marine egress, exact package position, progress, cohort counts, or
controlling squad. Neutral traces may record the full state but may never feed
it back into either perspective.

Generic Extraction installs a frame-only Marine corridor commander. It keeps
one stable payload element, adds one close escort when force strength permits,
and distributes remaining squads across lead, left, right, and rear screens.
Only the payload and close escort use the moving escort posture; screen squads
hold separate route-relative cells and therefore yield to locally acquired
contact through ordinary squad doctrine. The objective publishes a short
owning-side route guide so the screen follows authored turns through structures
instead of aiming directly through walls at the distant egress. Every target is
snapped to a walkable cell reachable by that squad.

The published command picture carries payload phase, position, route guide,
progress, escort control, stable role, reason, assignment kind, target cell,
and local-contact state. The selected-squad overlay, squad dump, and
perspective trace all consume that picture. `commanderEvidence
-Pmission=extraction` replays the production fixture twice under forced-serial
scheduling. With paired command active, the current canonical fixture reaches
an explained defender win at tick 5,079 before uncontested source control; it
proves quiet security, ownership, redaction, and deterministic terminal law,
but a fixture that actually raises the alarm is still needed to accept the
post-alarm evidence branch.

Generic conventional defenders receive a small mission-owned patrol pool while
authored garrisons retain their posts. One patrol guards the public source and
the rest remain an explicit reserve until the identity-free alarm mobilizes at
most three squads. Responders use defender-local contact beliefs for
interdiction when available; without a contact they reinforce separate
reachable source-perimeter positions. The alarm authorizes mobilization but
never reveals egress, live package position, progress, cohort state, escort,
controlling squad, or hostile identity.

The defender overlay, squad dump, and perspective trace publish only the
source, alarm and terminal disclosure, a coarse contact summary, mobile/reserve
counts, and own-force roles, reasons, and rally cells. Authored swarm or
security-network opposition remains a mission director when its force and
knowledge semantics are not those of a human squad commander.

## Civilian Rescue

Marine command owns shelter, cohort, moving-screen, and lift duties. The swarm
pressure system should remain an inspectable non-human director owning its
waves, legal cohort/screen context, approach/choke allocation, and pressure
reason. Architectural observability does not require fake squads, beliefs, or
reserves.

The existing rescue tracker and evacuation objective publish the shared cohort
projection while retaining physical civilian identity, movement, pickup
boarding, partial survival, and campaign scaling. The shared projection does
not replace any of those laws.

## Silent Colony

Silent Colony divides the expedition into stable archive and survivor branches.
Membership begins from route cost and capability, persists through ordinary
pulses, and rebalances only for loss, completion, or bounded emergency. Archive
recovery may rejoin survivor escort; an empty survivor branch may reinforce the
archive. Opposition may remain scripted or gain a bounded security-network
director after the Marine branches are stable.

The survivor cohort and sealed archive publish two distinct payload projections.
The survivor branch retains cohort/egress semantics; the archive remains a
source-recovery branch without invented boarding state.

`generic-extraction-corridor-command.md`,
`generic-extraction-interdiction-command.md`,
`rescue-corridor-command-picture.md`, and
`silent-colony-expedition-branches.md` own the open command work.
