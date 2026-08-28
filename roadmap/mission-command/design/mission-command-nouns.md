# Mission-command nouns

Status: ACTIVE — the shared autonomous command architecture is in production for Conquest, Sabotage, Assault, and Raid; generic Extraction still requires a mission-owned objective contract.

Written: 2026-08-27

Updated: 2026-08-27 — added Raid's paired primary-target strike-and-egress adapter and evidence selection.

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
escape hatch.

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

## Mission catalog

| Mission | Command shape | Current state | Design |
|---|---|---|---|
| Conquest | Directional tracks converging on territorial compounds and the keep | Paired production duel; live/evidence acceptance remains | `conquest-command.md` |
| Sabotage | Three named-site task groups with planter logistics and site security | Paired production duel shipped | `sabotage-command.md` |
| Assault | Two-dimensional search sectors versus strongpoint security areas | Paired production duel; live/evidence acceptance remains | `assault-command.md` |
| Raid | Primary high-value target plus authored ingress/egress corridor | Paired production duel; canonical/live acceptance remains | `raid-command.md` |
| Extraction | Payload/cohort corridor, with scenario-specific branches or directors | Family design; generic objective law incomplete | `extraction-command.md` |

Opening Operations reuse the Assault battle type but keep scenario-specific
preserve/secure command meaning. Civilian Rescue and Silent Colony are authored
Extraction-family scenarios, not proof that generic Extraction already has one
coherent payload law.

## Player and faction extensions

The player is an **intervention authority**, not the baseline commander. A
future request may lease a legal priority, rally, reserve commitment, focus, or
fallback. It cannot manufacture knowledge, bypass objective law, or seize an
externally owned squad. Expiry hands control back without a planless interval.

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
