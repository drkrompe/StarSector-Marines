# Extraction command

Status: ACTIVE — generic Extraction has paired corridor/interdiction commanders,
Civilian Rescue has an asymmetric corridor/swarm command picture, and Silent
Colony has stable Marine expedition branches; live acceptance remains open.

Written: 2026-08-27

Updated: 2026-09-01 — defined an actionable contact for the Extraction alarm responders, so the source-perimeter fallback is a rule rather than a total belief loss.

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
-Pmission=extraction` replays the production fixture under forced-serial
scheduling; `-Prepeat=2` restores the paired byte-stable replay. The canonical
matrix proves ownership, redaction, source-perimeter fallback, belief-driven
interdiction, and deterministic terminal law. The paired alarm-response fixture
deliberately concentrates twelve six-seat
shuttles into one sortie. It is branch evidence rather than a balance target:
it exists so the alarm interdiction phase is published somewhere in the matrix.
Across the matrix, defenders publish both belief-driven interdiction and
source-perimeter fallback when no contact is actionable.
`-Pfixture` and `-PmaxTicks` continue to select labelled ad hoc evidence rather
than adding a mission-specific Gradle task.

Generic conventional defenders receive a small mission-owned patrol pool while
authored garrisons retain their posts. One patrol guards the public source and
the rest remain an explicit reserve until the identity-free alarm mobilizes at
most three squads. Responders use defender-local contact beliefs for
interdiction when one is actionable; otherwise they reinforce separate
reachable source-perimeter positions. The alarm authorizes mobilization but
never reveals egress, live package position, progress, cohort state, escort,
controlling squad, or hostile identity.

**Actionable is narrower than known, and the difference is what the fallback
is for.** The side's whole belief set is a memory: the influence picture
aggregates every defender squad's contacts across the map and keeps each one
until its confidence decays away, tens of seconds after anybody last had eyes
on it. A belief older than one command pulse says where the enemy was; a
belief whose ground this responder cannot reach names a place it cannot go.
Neither is something to interdict, and the answer to both is the source
perimeter — this pool is bounded source security, not a hunting party, and
treating the whole remembered set as targets sends the source's own reserve
across the map to a garrison's last glimpse and back again on the next pulse.
The published role therefore names the ground actually taken rather than the
intent that selected it: a squad standing on the source perimeter is a
responder however it got there. The coarse contact count the defender
publishes stays the whole known set, because that is what the side knows.

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
six surviving civilians boarded at tick 3,502, publishes 46 Marine command
pictures and 156 swarm-director revisions, and replays byte-identically. This is
deterministic correctness evidence, not a pressure or balance target; live
pressure tuning remains in the campaign Rescue acceptance story.

## Silent Colony

Silent Colony divides the expedition into stable archive and survivor branches.
Membership begins from route cost and capability, persists through ordinary
pulses, and rebalances only for loss, completion, or bounded emergency. Archive
recovery may rejoin survivor escort; an empty survivor branch may reinforce the
archive. Opposition may remain scripted or gain a bounded security-network
director after the Marine branches are stable.

The production Marine planner now consumes the two owning-side payload
projections through a frozen Silent Colony frame. It assigns one reachable
element to each live branch when possible, uses route cost and surviving squad
strength for deterministic initial allocation, and retains membership until
loss or objective transition. Archive recovery transfers its squads into
survivor escort; an exhausted survivor cohort transfers its squads into archive
recovery. Landing sorties carry expedition ownership at squad birth, while
external authorities and active player leases remain intact.

The selected-squad panel and state dump publish both objective phases and
progress, branch membership and reasons, target, and faction-known pressure.
They do not disclose the frozen threat seed or exact automated-defense cells.
Mission-duration evidence and live acceptance remain open, as does the later
choice between scripted opposition and a bounded security-network director.

The survivor cohort and sealed archive publish two distinct payload projections.
The survivor branch retains cohort/egress semantics; the archive remains a
source-recovery branch without invented boarding state.

`generic-extraction-corridor-command.md`,
`generic-extraction-interdiction-command.md`, and
`silent-colony-expedition-branches.md` own the open command work. The shipped
Rescue migration is retained in `shipped.md`; live pressure tuning remains in
`roadmap/campaign/living-world/stories/rescue-pressure-acceptance.md`.
