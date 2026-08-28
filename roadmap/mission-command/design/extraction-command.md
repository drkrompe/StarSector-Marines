# Extraction command

Status: ACTIVE — generic Extraction has paired corridor/interdiction commanders and Civilian Rescue has an asymmetric corridor/swarm command picture; live acceptance and Silent Colony remain open.

Written: 2026-08-27

Updated: 2026-08-27 — shipped deterministic Civilian Rescue corridor command and its separately disclosed swarm-pressure director.

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
proves quiet security, ownership, redaction, and deterministic terminal law.
The paired alarm-response fixture deliberately concentrates eight six-seat
shuttles into one sortie. It is branch evidence rather than a balance target:
Marines complete extraction at tick 8,712 while surviving defender patrols
publish six source-perimeter response pictures and eight belief-driven
interdiction pictures after the alarm. Both fixtures replay byte-identically.
`-Pfixture` and `-PmaxTicks` continue to select labelled ad hoc evidence rather
than adding a mission-specific Gradle task.

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

Marine command consumes only an owning-side frame containing the public shelter,
cohort projection, route topology, own force, and local influence. One stable
mobile squad escorts the cohort while the remaining mobile squads retain stable
lead, left, right, and rear screen roles. Before release, separate shelter-
approach cells organize relief; after release, route-relative guide and screen
cells form the corridor to the lift. Repeated role echelons receive stable
offsets instead of forming one brick. A local actionable contact may hold only
the affected squad's current target for a bounded interval; it does not freeze
the whole formation.

Authored shelter and pickup guards remain outside the command pool under their
existing garrison or payload authority. The commander does not invent a missing
pickup-guard post. Mission command owns where the mobile force is useful while
the evacuation tracker, squad doctrine, and individual combat behavior retain
their existing authority.

The swarm remains an inspectable non-human director rather than a mirrored
squad commander. It owns `SWARM_PRESSURE` runners and reinforcement waves,
allocates wave cells across four perimeter approaches, and publishes its phase,
pressure reason, population, owned wave, approaches, and only aggregated target
contexts (`ROAMING`, `MARINE_SCREEN`, or `COHORT_CONTACT`). It does not expose
the exact cohort cell, Marine positions, controlling squad, or invented beliefs
and reserves. Individual swarm pursuit and attack behavior remains local.

The selected-squad overlay, squad dump, and perspective trace expose Marine
roles, reasons, targets, local slowdown, shelter/cohort/guide/lift geometry, and
the last owned swarm wave and approaches. Director trace rows are explicitly
labelled as a separate swarm perspective and deduplicated by director revision.

The existing rescue tracker and evacuation objective publish the shared cohort
projection while retaining physical civilian identity, movement, pickup
boarding, partial survival, and campaign scaling. The shared projection does
not replace any of those laws.

The existing `commanderEvidence -Pmission=extraction` selector runs both generic
Extraction and Rescue adapters; no scenario-specific Gradle task is required.
The canonical Rescue fixture uses two six-seat Aeroshuttles, completes with all
eight civilians boarded at tick 3,669, publishes 48 Marine command pictures and
162 swarm-director revisions, and replays byte-identically. This is deterministic
correctness evidence, not a pressure or balance target; live pressure tuning
remains in the campaign Rescue acceptance story.

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
`generic-extraction-interdiction-command.md`, and
`silent-colony-expedition-branches.md` own the open command work. The shipped
Rescue migration is retained in `shipped.md`; live pressure tuning remains in
`roadmap/campaign/living-world/stories/rescue-pressure-acceptance.md`.
