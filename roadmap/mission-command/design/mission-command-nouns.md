# Mission-command nouns

Status: ACTIVE — the shared autonomous command architecture is in production for paired Conquest, Sabotage, Assault, Raid, and generic Extraction command duels, plus asymmetric Civilian Rescue command.

Written: 2026-08-27

Updated: 2026-10-27 — in a battle the player plays, mission command no longer sends the player's own squads to objectives; it keeps commanding allies, and headless runs keep the Marine autopilot (see "Player and faction extensions").
Earlier 2026-10-21 — Thunder Run's militia holds are locked positions (`garrisonables.md`); a released squad stays in the pool it was handed to.
from independent acting-NCO leadership and physical mission routes.
Earlier 2026-08-31 — active player infantry context now exclusively owns the
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
meaning and faction-honest knowledge into stable squad assignments. It commands
the opposition, allied forces, and, in a headless run only, the player's own
company. In a battle the player plays, the player clears the map: their squads
take no objective from mission command (see "Player and faction extensions"),
so a mission must be balanced against a player giving orders, not against the
Marine autopilot that evidence runs use.

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
Frozen navigation-only views carry movement flags and edge connectivity, not
unused combat cover, damage or smoke storage. Compact storage must preserve
the same exact reachability and snapshot isolation as a full navigation copy;
it is independent of coarser strategic field resolution.
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
a destination. Squads enter and leave the pool over a battle — a release, a
handoff — so anything a strategy sizes from the pool, such as a reserve bound,
is re-derived from the pool as it stands on each pulse rather than latched at
the opening one. A strategy releasing a squad's order ends the order, not the
membership: a release removes the directive, so a squad handed to a strategy
after setup (a claim or a delivery in its name) stays in that strategy's pool
until another owner takes it, as the starting force does. Read from the
directive alone, the Conquest defender lost every such squad the first time its
track response ended, which in `machine-guard`'s `defender-lances` loop was a
lance and three patrols in the first forty seconds.

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
the squad assembles or the suspension times out. The suspension masks the
commander only: a player's lease passes it, because sending a partial squad
forward is the player's call (a mission that lands four at a time into a
stronger enemy cannot ask the player to wait for the rest).

## Subordinate command groups

A **command group** is the stable organizational identity of selected campaign
squads under one named officer, or one independently led squad under its acting
NCO. Deployment freezes that identity with the squad and carries it through
later lifts; a landing area, route, lane, or first observed position cannot
replace it. The operation commander may coordinate unattached squads within
available rank capacity. Squads beyond that capacity retain competent basic
mission execution under their own NCOs rather than becoming undeployable.

A named officer adds coordination across their squads, not a passive increase
to damage, accuracy, morale, or movement. A subordinate task is intent within
the faction's existing mission strategy. It produces ordinary squad proposals
through the same arbiter and acquires no competing assignment authority.
Mission law, global allocation budgets, player leases, delivery ownership and
born garrisons still determine which squads may execute it.

Conquest currently supplies this group coordinator. It preserves useful assault
work, supports the group's selected objective without multiplying capture
quotas, and keeps explicit hold obligations separate from assault strength.
Hold and assault may coexist in one command picture; this is a foundation for
mixed obligations, not an authoring system for new mixed missions. Other mission
adapters retain their existing allocation policies until they explicitly adopt
group coordination.

Group cohesion concerns useful separation along an authorized route, not
morale or individual formation. Quiet leading squads may briefly pause at a
reachable own-force rally while the nearby body closes. The pause is bounded,
ignores distant late arrivals, and yields to contact, incoming fire, broken
morale and execution suspension. Local squad AI still owns fighting, survival
and movement. Objective completion or loss of useful reachable work releases
an assault task for fresh mission allocation. Published group identity, task,
target and reason explain this decision alongside the committed squad orders.

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

Strategic field resolution and navigation legality are separate concerns.
Coarser influence blocks may reduce propagation work, but must not invent
connections through walls or change individual movement clearance. Propagation
attenuation is calibrated against physical block width, rather than letting a
larger block silently extend the influence horizon. A block's published scalar
can still aggregate disconnected local regions; this spatial aliasing is not
evidence that the regions connect. Resolution experiments compare identical
frozen geometry and faction-local knowledge, report that information loss
alongside computation, and do not by themselves establish combat outcomes.
The production influence and casualty-memory resolution remains unchanged
until such an experiment justifies a separate adoption decision.

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

## Side objectives

A **side objective** is an optional, mission-owned job with no bearing on
victory or defeat; it never enters `WinCheckSystem`. A mission declares one
(`SideObjective`) as a **hook** (offerer, name, steps, a line per state, story
keys under one prefix), a **trigger** (`SideTrigger`: reach, hold, carry,
escort, or the mission's own), a **window** (always, a phase, a clock, or
another job's state), a **battle effect** applied once on completion through
existing seams, and a **return** (a debrief line per outcome, a namespaced
campaign mark, and credits paid when done: either, both or neither, as fits
what the job gives). State runs `OFFERED`, `WORKING`, then one of `DONE`,
`LAPSED` (the window closed first, half-done included) or `FAILED` (the job
was ruined).

A job may be **hidden** behind a **discovery** (`SideObjective.Discovery`): a
company unit meets its offerer, reaches its place or sights its prop. Until
then it is absent from the briefing, the checklist and the map, and its
trigger is not judged; once found it joins the Optional section with a notice
and a field line, marked new for a moment. A hidden job never found ends
`MISSED`, never `LAPSED`: it earns nothing and the debrief says nothing of it
unless the mission authors a missed line.

`SideObjectiveService` on `BattleSimulation` owns the live state. It ticks
right after field salvage and before the story sequences, so a job finished on
the tick its window closes still counts; a control lever grants a job silently.
`SideObjectiveRows` publishes one optional row per job into the mission
picture; a mission never writes those rows itself. At resolution each job's
outcome rides on `MissionOutcome`, and settlement (never the live battle)
pays its credits and writes its mark into `CampaignMarks`, where it stays until
a story clears it. A mission's own outcome may settle a mark through the same
path (Blind Watch's `corvus.sable_wake.garnir_warned`). A mark is read only at a
fixture boundary, never by a live battle: Blind Watch's fixture asks whether
`corvus.teague.delivered` is missing and tells the battle only that its post is
stood-to (`BlindWatchProgress.stoodTo`). The
rolled pool in `optional-objectives.md` is a sibling: a pool rolls against the
laid map, a side objective is authored for one mission.

The effect hook works through existing seams only (allies released, defences
cut, a reveal, a hostile group standing down) and never grants victory or
knowledge a perspective may not have. Owner decisions (2026-10-26): marks are
permanent until a story clears them; a job may be hidden until found in the
field; and what a job pays (text and mark, credits, or both) depends on what
the mission means it to give. The Dust Ledger is the first user
(`dust-ledger-crossing.md`, Side objectives).

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

**The player commands their own company.** When a battle is put in front of the
player (`MarineOpsContext.setBattle`), `AssignmentArbiter.yieldToPlayer` hands
the Marine side to them: mission command may still own one of their squads
(claim it, take it over from a landing craft, release it) but an assignment at
`MISSION_COMMAND` is refused, or kept as a bare claim where a handoff carries
it, and a commander does not post their mech lances as machine guards. A squad
the player has not ordered holds where it stands and fights what it sees. The
one errand that still lands is `WITHDRAW`, since leaving the field is the
mission ending rather than a place to clear. Scripted, payload, garrison and
reinforcement authorities are untouched, as is every other side, so `ALLY`
forces keep their commanders; a force a mission wants to fight on its own
belongs on `ALLY`, not on the Marine side. Drone squads and lances flying a
rescue pickup are not the player's to order and stay on mission command.

A headless run (evidence tasks, tests, fixtures) never calls `yieldToPlayer`,
so the Marine commander still plays the company there. That autopilot is a
stand-in for a player so the opposition has something to fight; its outcomes
say whether a mission works mechanically, not whether it is hard enough.

Within the player's company, the player's orders are leases. A
future request may lease a legal priority, rally, reserve commitment, focus, or
fallback. It cannot manufacture knowledge, bypass objective law, or seize an
externally owned squad. Expiry hands control back without a planless interval.

A tactical squad move **is** such a lease. An accepted click is a directive at
`PLAYER_INTERVENTION` committed through the arbiter like every other assignment,
so a player's order carries provenance, a reason, an issue tick, and a bound —
there is no side channel by which the player points a squad somewhere. The
commander's directive is not displaced but **shelved**: the arbiter holds it and
puts it back when the order ends, which is what lets the handback happen without
a tick on which the squad owns no assignment at all. A squad under a lease is
therefore reported two ways at once — the order it is carrying out, and the
directive it will resume — and both are read from the ledger rather than
reconstructed.

Completion, expiry, invalidated reachability, squad ineligibility, and hard
withdrawal all end the lease through the same door. **Completion is the action's
own rule and nothing else**: the release asks the attack move whether the squad
has arrived by the test the action completes on, because a second radius kept
beside it let a squad satisfy the action, miss the release, and park under the
player's order for the rest of the battle. **Expiry is what covers an order that
cannot complete** — a straggler left outside the footprint forever would
otherwise hold the mission hostage — and is why a bounded order buys a bounded
interval of authority rather than the beginning of permanent manual control; the
commander re-issues after expiry whatever it still wants. A defence area is the
exception that proves the shape: it is unbounded, because it stands until
superseded or hard withdrawal rather than finishing by being reached.

Two rules keep the shelf honest. A **reissue keeps the original shelf**: a
player changing their mind is one intervention, not their own previous order
becoming the mission underneath. And a **commander's write during a lease is
shelved rather than lost** — the squad hands back to what command wants now, not
to the intent that happened to be standing when the player clicked. The one
order a lease does not outlast is a hard withdrawal, which takes the squad
immediately: everything else a player may issue is a place to be, and a squad
that walks there for another two minutes first is not withdrawing.

Local AI still decides how the squad crosses contact and fires, and form-up and
cohesion survival may suspend execution without touching ownership. While the
lease stands, the player's order is the exclusive MISSION-tier input to local
planning; a unit-level specialist task or authored last stand cannot compete
around the executable assignment and make the accepted order inert. Infantry
assigned to a rescue pickup perimeter remain eligible because the intervention
changes execution, not rescue ownership. Shelter militia remain mission-owned
rather than becoming part of the player's deployed force.

A contextual squad action is the same lease over a legal mission interaction.
The first production action is Conquest capture: clicking an uncaptured compound
binds its stable authored identity to the live capture room and leases the
existing `SECURE_COMPOUND` context. Arrival is not completion; the squad
approaches, clears, and holds through the ordinary local AI until territorial
authority reports `MARINE_HELD`. A zone rebuilt under the order is rebound by
reissuing the lease, which keeps the commander's directive on the shelf the
first click put it on. Completion, hard withdrawal, loss, expiry, or invalidated
reachability ends the lease and restores the latest authoritative directive
without an unassigned interval.

A player-placed defend area is another lease, not an Assault command-area claim.
The player selects an eligible Marine infantry squad, arms the order, and places
a circle twenty cells in radius around a reachable center. The local squad plan
owns threat-facing cover, bounded firing positions, and moving fire inside that
geometry; mission command's directive waits on the shelf underneath. This is the
unbounded lease: the defense persists until superseded or hard withdrawal rather
than completing merely because the squad reached its center.

**Run** is a pace on a standing lease, not a lease of its own. R toggles the
selected Marine infantry squads that are already under an order between walking
and running; with every one of them running it walks them again. A squad with no
standing order, a lance and a vehicle ignore the key, and so does a controlled
battle. A fresh order starts at a walk, a reissue that refreshes the same lease
(a ride driving off, a compound rebound) keeps its pace, and the run ends with
the lease. A running squad's Marines are paid for out of the same battle stamina
a controlled Marine sprints on (`direct-control-nouns.md`, Movement by carrier):
each one with stamina left moves at the sprint multiplier and is charged only for
the share of a full sprint it actually covered, so a Marine held up by a wall, a
crowd or its own fight spends nothing. A Marine who runs dry walks on, recovers to
the restart reserve and runs again while the order still runs; nothing is held
down for it to release. The body under direct control is the player's hand's and
is never boosted by its squad's run. Running does not change who fires or when:
local AI still owns contact. A winged foot stands at the upper right of a running
squad's lead body, opposite the medic's cross, with the squad's mean stamina in a
bar beneath it that greys while most of its Marines are winded. Evidence:
`sceneEvidence -Pscene=squad-run`, and its look shot `look/squad-run-*`.

A frozen **command doctrine profile** may bias legal assignment, reserve,
recapture, and local-posture choices. It does not add objectives, knowledge,
forces, or combat privileges. Mission adapters define which profile axes are
meaningful in their geometry.

Thunder Run shares the public-territory advance/hold model: the company clears
successive road posts, stable militia detachments occupy gains, and player
interventions retain their claims. The scenario separately authorizes employer
vehicle travel from held-site facts and finite dispatch windows. The frame also
carries the public active hold site: mission-owned company squads defend that
post during the window, while lost territory takes priority. Militia retain
stable first/second-post duties behind reachable roadside gun mounts; the
militia's holds are the generic locked positions (`garrisonables.md`, locked
positions): the staging until the first gain, then each gain once the company
has held it, oldest first, the surplus on the newest. Company
sections take separate, stable roadside defense sectors on both flanks, apart
from the militia crew positions; the final capture switches them to screening
the exit crossing. Before a second gain is available, militia sections occupy
different positions around the first. Player defense
leases survive the window ending and require a new player order. Infantry mission command receives no cargo
entity positions or hidden ambush roster. Its final hold supports a physical
convoy-exit objective; taking all sites is not itself victory.

## Boundaries

Mission command chooses where a squad is useful. It does not author the squad's
local action sequence or individual fire intent. AI beliefs and doctrine remain
the tactical truth after contact.

Map generation owns zones, portals, compounds, sites, and navigation geometry.
The player-facing mission picture is a read-only projection of objective-owned
state and friendly committed assignments. It shares authored place identities
with briefing and map markers, reports actual progress gates without exposing
private hostile geometry, and preserves each mission's terminal law. A squad's
published role explains its assignment; local contact and player overrides are
shown separately so intent is not presented as accomplished work. Presentation
never issues orders or grants objective completion.

Mission objectives own progress and victory. Reinforcement owns requests,
tickets, and delivery; a mission owns only an explicit deployment policy or
post-delivery handoff. Rendering observes published facts and never becomes a
sensor.
