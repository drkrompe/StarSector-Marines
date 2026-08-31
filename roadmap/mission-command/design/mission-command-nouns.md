# Mission-command nouns

Status: ACTIVE — the shared autonomous command architecture is in production for paired Conquest, Sabotage, Assault, Raid, and generic Extraction command duels, plus asymmetric Civilian Rescue command.

Written: 2026-08-27

Updated: 2026-08-31 — active player infantry context now exclusively owns the
squad's mission-tier plan, including specialist and rescue pickup squads, while
the underlying directive remains authoritative and resumes on release.
Earlier 2026-08-31 — a selected Marine infantry squad may hold a persistent
player-placed 40-cell-diameter defense area beneath its authoritative directive.
Earlier 2026-08-31 — an uncaptured Conquest compound may be selected as a
contextual squad action that persists through approach and fighting until the
territorial objective reports completion.
Earlier 2026-08-31 — a selected Marine infantry squad may take a one-shot
tactical destination below its directive; arrival or withdrawal hands execution
back without replacing mission ownership.
Earlier: the casualty memory now also publishes a route costing, so the movers
route around a side's own dead rather than only the commander choosing among
places to send them.
Earlier: added the casualty memory itself, and the order mix — which orders a
battle was actually made of, with unassigned pulses as a bucket rather than an
omission.
Earlier: made navigation-revision caching and commander-pulse
performance evidence part of the shared command boundary.

Mission command is the slow, faction-scoped layer that turns authored mission
meaning and faction-honest knowledge into stable squad assignments. It is the
strategic half of the auto-battler baseline: a battle should progress without
continuous player rescue, while player intervention remains able to tip a
competent autonomous contest.

This feature owns the common command architecture. Each mission document owns
the geometry, phases, legal disclosure, allocation policy, and opposing-force
shape that give that architecture meaning. `ai-nouns.md` owns squad beliefs,
local contact doctrine, planning, and unit execution. Mission objective domains
retain progress, completion, victory, and campaign consequences.

## Command duel

A **command duel** has one perspective-specific strategy for every side with
strategic agency. Shared machinery is symmetrical; strategies need not be. An
attacker may seize compounds, search sectors, service named sites, strike a
target, or escort a cohort while the opposition delays, guards, intercepts, or
uses a non-human mission director.

A **command pulse** has five ordered stages:

1. Freeze every participating perspective from the same battle tick.
2. Let each strategy plan independently from its frozen input.
3. Validate proposals against ownership, mission law, and reachability.
4. Commit accepted directives only after every side has planned.
5. Publish immutable snapshots and interrupt affected squad plans.

Planning both sides before either commit prevents dispatch order from becoming
knowledge or behavior leakage. The initial cadence is 2.5 simulated seconds;
directive stability, rather than a faster global loop, prevents churn.

The frozen public topology and influence connectivity topology are immutable
derived views. Public topology is reused until either the raw grid changes or
the derived-navigation revision advances at the flushed breach boundary;
influence connectivity is reused until that flush. Unit movement and changing
belief rebuild frames and fields without recopying unchanged map geometry.
Equal-strength influence emitters in one connectivity component may
share one propagation traversal while retaining additive strength and the
per-emitter attenuation cutoff.

## Perspective and disclosure

A **command frame** is one side's immutable input. It contains own-force state,
faction-local influence, public topology, current directives, frozen doctrine,
and only the objective facts a mission is legally allowed to disclose. A
production strategy receives no `BattleView` and retains no simulation
reference.

A **mission disclosure** is the stateless, perspective-specific adapter that
projects mission facts into a frame. It must distinguish:

- authored/public geometry from hidden occupancy;
- own objective state from opponent-only task state;
- faction belief from authoritative referee truth; and
- a legal objective alarm from an inferred hostile identity or cell.

Absence of belief is uncertainty, not proof that ground is clear. A mission may
authorize a bounded probe into unknown space, but it must state that policy
explicitly.

## Ownership and directives

A **command pool** is the set of squads a strategy may allocate. Born
garrisons, payload guards, scripted actors, and reinforcement forces awaiting
handoff stay outside it. Ownership may exist without a tactical assignment,
which lets a delivery or special-task system reserve a squad without inventing
a destination.

A **directive** records the optional `ObjectiveAssignment`, issuer, authority,
reason, issue tick, target meaning, and stability or lease state. The shared
arbiter is the only commit path. Garrison, payload, reinforcement, scripted,
mission-command, and future intervention writers must register ownership or
perform an incumbent-checked handoff; direct assignment writes are not a legal
escape hatch. A legacy mission planner that has not yet adopted frozen frames
receives only the same scoped directive-control facade during its serial pulse;
legacy planning input does not imply legacy mutation authority.

Directive **stability** is a minimum useful execution interval. An objective
ending, target becoming unreachable, squad loss, context expiry, explicit
handoff, or higher authority may invalidate it early. An authority **lease** is
different: it bounds temporary external control such as a player request.
Repeating an unchanged directive renews neither clock.

Form-up is execution suspension, not ownership transfer. The authoritative
directive remains visible while its executable assignment is withheld until
the squad assembles or the suspension times out.

## Mission strategy adapter

Every mission adapter supplies the same kinds of decisions without sharing one
universal geometry:

| Contract | Mission responsibility |
|---|---|
| Objective disclosure | Define which authored facts each perspective may know. |
| Command geometry | Tracks, sectors, sites, targets, corridors, branches, or another explicit shape. |
| Pool policy | Identify mobile force, reserves, fixed duties, and handoff points. |
| Phase model | Explain how objective state changes allocation priorities. |
| Assignment vocabulary | Issue only tactical contexts consumed by squad planners. |
| Command picture | Publish the mission-specific explanation layered over the common snapshot. |
| Evidence adapter | Define progress, stalls, response, and outcome metrics without feeding referee truth back into command. |

Geometry is reusable implementation material, not universal doctrine. Conquest
tracks do not become Assault sectors; a Rescue pressure director does not need
fake human reserves; a Raid target corridor does not acquire Sabotage planting
law merely because both use named places.

## Snapshot, diagnostics, and evidence

A **commander snapshot** is the immutable post-commit explanation. Its common
envelope names perspective, strategy, phase, command pool, reserves, objective
summaries, and every squad directive or explicit unassigned/rejected reason.
Mission pictures extend it with their own geometry and decision vocabulary.

Selected-squad UI, map overlays, dumps, and traces consume the published
snapshot rather than reconstructing intent from live state. Debug tools select
one perspective at a time; showing both sides is an explicit diagnostic mode,
not a merged tactical truth.

The live tick-profile dump and opt-in fixture JFR boundary expose command
assignment synchronization, topology freeze, per-perspective frame freeze,
strategy planning, and commit as separate subphases. Cross-cutting influence
topology, source aggregation, and propagation publish their own refresh counts
and costs; they may be triggered by command frame capture or tactical GOAP and
must not be added to the enclosing phase. These are timing diagnostics, not
commander facts, and never enter deterministic traces.

A **casualty memory** is a side's record of where it recently lost people, at
influence-block resolution and decaying with a half-life. It is own-force
knowledge of the same class as the friendly influence field — a faction knows
who it lost and roughly where — and it is emphatically not intelligence about
the enemy: it names no hostile, and a lane stays expensive long after whatever
held it has moved on. Only combatant losses count, because a civilian caught in
the open says nothing about whether a fire team can cross that ground.

Ground does not become safe on a deadline, so the memory fades rather than
expiring, and a second squad lost in the same place pushes it back up. A
commander may use it to choose among places that are otherwise equally good:
approach avoidance moves where the **next** squad is sent, within the geometry
the mission already authorized. It does not re-route a squad already moving,
does not invent a way around a lane that has only one, and never overrides the
mission's own law about where the objective is.

A **route costing** is that same memory expanded into what it makes each cell
cost to cross, and handed to the pathfinder. It is the memory's other consumer
and the one that changes behaviour without any commander deciding anything: a
squad ordered somewhere still goes there, and simply prefers not to walk over
its own dead on the way. Only the long route to a commanded destination reads
it. A firing position a few cells away does not, because moving somebody off
cover for something that happened elsewhere is not a tactical decision about
the shot in front of them.

Three laws hold it in shape. **The ground stays crossable**: the penalty
saturates, so a place bought with a squad is discouraging and never forbidden,
or an objective defended well enough would stop being approached at all.
**The block is the resolution**: the costing steps at block boundaries rather
than being smoothed across them, because the block is what the side actually
knows and a gradient would draw a confidence the knowledge does not have.
**A costing is a publication, not a variable**: it is rebuilt on a fixed
cadence, frozen, and identified by a revision, because the pathfinder retains
reverse trees grown under it and a tree that outlives its costing would keep
serving routes computed against costs that no longer exist. That revision is
what tells two sides' costings apart, and what retires the trees grown under a
superseded one.

An **order mix** is a perspective's published directives broken down by
assignment kind, as squad-pulses and squad-ticks. It answers the one question
the rest of the evidence cannot: not how well an order went, but which orders
the battle was made of. Latency, churn, travel and capture all describe the
fate of a directive already issued, and two runs that play nothing alike — one
that is nine-tenths compound capture, one that is nine-tenths lane fighting —
score similarly on every one of them.

Pulses with no published assignment are a bucket in the mix rather than an
omission, so the shares cover the whole and "the commander said nothing" is
legible beside what it did say. That bucket is deliberately not a defect
reading: a perspective whose force is mostly born garrisons and payload guards
holds most of its squads outside the command pool by design, and the mix cannot
currently tell that apart from a commander with nothing to say. Reading a large
unassigned share as a fault requires checking pool ownership first.

A **command trace** has perspective streams and a separately labelled neutral
referee stream. Perspective events contain only published command facts.
Objective transitions, exact contested occupancy, casualties, duration, and
terminal result may appear in referee evidence but never become commander
input. Capture gaps create explicit observation windows, and a bounded run ends
as `TIMEOUT` instead of inventing a winner.

Canonical headless evidence uses forced-serial duplicate replay and byte-stable
traces. `commanderEvidence -Pmission=<id>` selects the mission adapter;
`-Pfixture` and `-PmaxTicks` are arguments. A new mission registers another
adapter rather than another Gradle task. Metrics describe what the trace proves:
command-unassigned does not automatically mean physically idle, and a single
seed is not a balance target.

A perspective **travel episode** begins when an executable assignment first
publishes a measurable destination and ends at its first observable boundary.
Arrival, retarget, release, own-squad loss, execution suspension, capture gap,
timeout, and terminal result are distinct exits; an incomplete live trace
leaves the episode open rather than inventing an outcome. Contact, active-path
movement, and quiet travel are overlapping context on that episode, never
alternative exit reasons. A rejected proposal does not end its still-effective
incumbent, and reissuing the same semantic destination does not create a new
trip.

Retarget provenance is likewise perspective evidence. A secure trip may end
because command selected another compound, changed the approach marker inside
the same compound, or replaced capture duty with another assignment; a row
whose accepted action/directive pair cannot establish that cause remains
unclassified. Squad loss is located from the final living command observation,
not claimed as an exact death cell: reports retain straight-line distance to
the objective, progress relative to the trip's starting distance, and whether
that final picture carried local contact, track-level hostile belief, no
published contact, or insufficient track context. Exact position relative to
a normalized front requires normalized own-squad progress and must not be
inferred from cell coordinates alone.

Final-living tactical evidence stays orthogonal rather than collapsing into a
guessed posture. A command pulse may publish the squad contact picture's
posture, doctrine, initiative and engageable fire line; actual moving members;
directional cover against the published primary believed contact; recent
incoming-fire/morale facts; the current action; and members presently inside a
weapon cooldown. None of those alone proves suppression or a shot between
command pulses. Missing threat direction is unknown cover, not exposure, and
older trace schemas remain unknown rather than receiving zero-valued facts.

A neutral **capture-zone presence cohort** is a contiguous observation of one
or more Marine units inside a compound's exact capture room before that
compound becomes Marine-held. It describes changing zone population, not
personnel identity. An observed zero-to-positive transition supplies entry
strength; a first positive baseline is left-censored. The cohort retains peak
strength, positive zone-member deltas, defenders cleared from entry, observed
uncontested control, mixed duration, and capture latency. Perspective samples
may be correlated offline by exact target zone to retain the first and peak
published squad count, members physically in-zone, total alive strength of
those squads, and whether additional squads appeared;
that correlation still does not expose neutral occupancy to either commander.
Capture, a
defender-present or empty Marine exit, an unresolved or changed capture zone,
an observation gap, timeout, and terminal result are distinct boundaries; an
incomplete trace leaves the cohort open. Capture wins a same-tick tie with
zone exit. Topology changes censor rather than masquerading as reinforcement,
and pre-schema-7 anchor-zone rows remain unavailable for this exact-room
metric. These are referee facts for offline balance evidence only and never
commander input.

An opt-in **visual replay** samples the first deterministic replay at a requested
tick cadence, renders the production battle scene through the GL-free Java2D
drain, and retains both numbered PNG frames and one looping review GIF. It always
includes tick zero and the terminal or bounded final tick. The image is an
explicit neutral-observer artifact: it may show the whole battlefield for
offline review and adds high-contrast faction markers over live entities, but it
is never commander input or player-facing intelligence. GL-owned custom and
ribbon decorations may be omitted by the Java2D drain. Rendering is read-only
and the second replay must still match the captured first replay byte-for-byte.

## Mission catalog

| Mission | Command shape | Current state | Design |
|---|---|---|---|
| Conquest | Directional tracks converging on territorial compounds and the keep | Paired production duel; live/evidence acceptance remains | `conquest-command.md` |
| Sabotage | Three named-site task groups with planter logistics and site security | Paired production duel shipped | `sabotage-command.md` |
| Assault | Two-dimensional search sectors versus strongpoint security areas | Paired production duel; live/evidence acceptance remains | `assault-command.md` |
| Raid | Primary high-value target plus authored ingress/egress corridor | Paired production duel; canonical/live acceptance remains | `raid-command.md` |
| Extraction | Payload/cohort corridor, with bounded conventional interdiction or scenario-specific directors | Generic paired command, Civilian Rescue, and Silent Colony Marine branches shipped; live acceptance remains | `extraction-command.md` |

Opening Operations reuse the Assault battle type and now apply the shared
frozen frame/plan/commit envelope to scenario-specific preserve/secure command
meaning. Civilian Rescue and Silent Colony are authored
Extraction-family scenarios; each preserves its own payload law while exposing
the shared objective projection. Civilian Rescue proves that an opposing command
picture may be a mission director rather than a mirrored squad commander.

## Player and faction extensions

The player is an **intervention authority**, not the baseline commander. A
future request may lease a legal priority, rally, reserve commitment, focus, or
fallback. It cannot manufacture knowledge, bypass objective law, or seize an
externally owned squad. Expiry hands control back without a planless interval.

A tactical squad move is narrower than such a directive lease: it temporarily
supplies the selected Marine infantry squad's executable destination while its
authoritative directive and owner remain unchanged. Local AI still decides how
the squad crosses contact and fires, form-up and cohesion survival may suspend
it, and hard withdrawal cancels it. Arrival removes the temporary context before
the ordinary directive replans, so the intervention neither becomes a second
assignment writer nor leaves an unowned interval. Until that boundary, the
player context is the exclusive MISSION-tier input to local planning; a
unit-level specialist task or authored last stand cannot compete around the
executable assignment and make the accepted order inert. Infantry assigned to
a rescue pickup perimeter remain eligible because the intervention changes
execution, not rescue ownership. Shelter militia remain mission-owned rather
than becoming part of the player's deployed force.

A contextual squad action applies the same execution-only authority to a legal
mission interaction. The first production action is Conquest capture: clicking
an uncaptured compound binds its stable authored identity to the live capture
room and supplies the existing `SECURE_COMPOUND` context. Arrival is not
completion; the squad approaches, clears, and holds through the ordinary local
AI until territorial authority reports `MARINE_HELD`. Completion, hard
withdrawal, loss, or invalidated reachability removes the temporary context and
reveals the latest authoritative directive without an unassigned interval.

A player-placed defend area is another execution-only intervention, not an
Assault command-area claim. The player selects an eligible Marine infantry
squad, arms the order, and places a circle twenty cells in radius around a
reachable center. The local squad plan owns threat-facing cover, bounded firing
positions, and moving fire inside that geometry; mission command retains its
directive and ownership underneath. The defense persists until superseded or
hard withdrawal rather than completing merely because the squad reached its
center.

A frozen **command doctrine profile** may bias legal assignment, reserve,
recapture, and local-posture choices. It does not add objectives, knowledge,
forces, or combat privileges. Mission adapters define which profile axes are
meaningful in their geometry.

## Boundaries

Mission command chooses where a squad is useful. It does not author the squad's
local action sequence or individual fire intent. AI beliefs and doctrine remain
the tactical truth after contact.

Map generation owns zones, portals, compounds, sites, and navigation geometry.
Mission objectives own progress and victory. Reinforcement owns requests,
tickets, and delivery; a mission owns only an explicit deployment policy or
post-delivery handoff. Rendering observes published facts and never becomes a
sensor.
