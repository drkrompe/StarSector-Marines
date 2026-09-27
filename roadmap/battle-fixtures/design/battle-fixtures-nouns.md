# Battle fixtures

Status: ACTIVE — versioned scenario construction and post-briefing launch capture,
headless replay, and opt-in forced-serial Conquest and Sabotage command evidence
are shipped.

Written: 2026-08-24

Updated: 2026-09-27 — automatic live spike capture is opt-in, independent of
the live counters and manual profile dump.

## Vocabulary

A **battle fixture** is a versioned set of authored construction facts. It is
the smallest plain-data description that can ask a production scenario factory
to rebuild a battle at tick zero. It is not a serialized simulation, a save
game, or a mid-battle checkpoint.

A **capture** freezes those facts at the launch boundary, before scenario
construction can hide the requested seed behind deterministic map retries. A
profile dump may embed that fixture beside its live timing evidence. The timing
window describes the observed live battle; the embedded fixture describes how
to reconstruct its initial scenario. A capture large enough to push a dump past
the host's file-size limit travels as a sibling file the dump names instead;
the codec reads a bare fixture document and an embedding one alike, so where a
capture was written never changes how it replays.

A **replay** creates a fresh battle by feeding the captured facts back through
the same production factory used by ordinary play. Headless tests and profiling
tools advance that returned simulation through the ordinary fixed-tick loop.
They do not own a second scene builder.

A **profile run** is an opt-in headless replay measured by an external runtime
profiler. Performance evidence is an artifact, not a pass/fail timing budget;
continuous tests prove codec and deterministic-construction behavior instead.
The run boundary records aggregate and maximum commander and GOAP wall time,
plus commander synchronization, topology, frame, planning, and commit totals
and cross-cutting influence refresh counts and normalized topology/source/
propagation costs. The event duration includes reconstruction and pre-roll;
only active-tick nanoseconds are a measured-work denominator. Each 75 ticks spans one
current commander period; a 75-tick-aligned slice allows repeated
reconstruction of the same battle age without treating 10 ms statistical
samples as exact pulse timing.

A **visual replay** is an opt-in observation of a normal headless replay. It
samples current simulation state at a configured tick cadence and drains the
production battle renderer through Java2D into numbered PNG frames and a review
GIF. Neutral faction markers keep live forces legible at whole-map scale; GL-only
custom and ribbon decoration is outside the raster evidence contract. It is not
a serialized checkpoint and cannot resume a battle.

## Authority and flow

The campaign-to-battle boundary owns capture because it still has the requested
seed, shuttle commitments, risk, target-world profile, and scenario knobs. The
fixture owns only immutable copies of those inputs. `BattleSetup` remains the
authority for map selection, deterministic retries, payload installation,
spawns, loadouts, objectives, and commanders.

For supported scenarios, the flow is:

`MissionLaunch` facts → versioned construction fixture → production
`BattleSetup` factory → V3 launch wrapper and overlay → fresh
`BattleSimulation` → ordinary fixed ticks.

The active battle context retains the highest-fidelity available fixture as
cold diagnostic metadata. The existing tick-profile capture may embed it
without making the simulation or fixture codec depend on Starsector file APIs.

Automatic live spike capture is off for each new battle. The battle DEBUG
toggle arms a bounded batch of automatic dumps and switches off when that
batch fills; switching it off also stops automatic snapshot allocation.
Live phase timing and deliberate manual profile dumps remain available
independently. Capture serialization and host file writes are diagnostic work,
not part of the measured simulation tick.

## Laws

- A fixture stores construction inputs, never mutable ECS columns or live
  simulation objects.
- Replay delegates to a production scenario factory. Tests may compare and
  instrument the result but may not reproduce setup rules.
- The requested factory seed is authoritative. A retry seed is derived output
  and must not replace it in capture.
- Fixture schemas are explicitly versioned and scenario kinds are explicit.
  Unknown versions, kinds, and vocabulary fail loudly.
- Ordered inputs retain order. Set-like inputs encode in stable domain order.
- A fixture rebuilds tick zero. A profile captured later in the battle is not a
  claim that replay resumes at that tick.
- Simulation-owned executors are resources. Hosts and headless runners release
  them when the battle leaves service.
- Visual capture is read-only, explicitly neutral, and must not change the
  trace or result relative to an uncaptured duplicate replay.

## Fidelity layers

Civilian-rescue V1 captures the seven inputs of its production scenario
factory. It therefore reproduces the generated map, rescue payload, default
forces, aircraft, objectives, and deterministic setup rolls.

Conquest V1 captures the requested seed, ordered full-capacity shuttle manifest, heavy-armor
availability, operation tier, risk, target-world profile, and both ordered
fighter commitments accepted by its production factory. It therefore rebuilds
the canonical 240×160 map, compounds and keep, authored defenders, both
commanders, reinforcement layer, shuttles, and the factory's fighter-support
inputs without owning a parallel scenario builder. The later launch overlay
that installs persistent marine identities, equipment, powers, resources, and
the combined live flyby roster remains outside this construction layer.

The canonical Conquest command-evidence run is forced serial before simulation
construction because parallel unit updates may consume shared seeded random
draws in scheduler-dependent order. Each fixture is run twice; byte equality of
the canonical trace and normalized metrics is an evidence invariant, while a
production-scheduler run is informational. Battle-long evidence is opt-in and
bounded. Reaching the bound publishes a neutral timeout instead of fabricating
a winner. Trace schema 7 keeps simulation and command inputs untouched while
canonicalizing published scalar diagnostics to basis-point precision and squad
centroids to one tenth of a cell, preventing insignificant integration drift
from presenting as a different command decision. It records each compound's
authoritative capture cell/zone and the perspective-safe count of own squad
members in their assigned target zone; neutral whole-zone occupancy remains a
separate referee fact.

The Conquest summary document is version 5. It derives a single explicit exit
for every perspective-observed secure-compound travel segment and keeps local
contact, active path, and quiet travel as separate context. This is an analyzer
contract, not another trace fact: command-trace schema 7 already carries the
directive, own-squad state, observation windows, and run boundary needed to
make the distinction. The same perspective rows classify retarget destination
changes and locate squad-loss exits from the final living centroid relative to
the objective marker. The summary labels these as last-observed evidence and
does not invent normalized front position from raw cell coordinates.

Construction V2 adds each assignment's actual seats per sortie and the resolved
arrival policy plus employer/player shuttle boundary. Historical V1 documents
continue to decode as independent full-load point arrivals. The campaign launch path applies persistent marine identities, equipment,
fighter cover, command powers, and their resources after that factory returns.
Those overlays remain outside construction. A V3 `BattleLaunchFixture` wraps the V2
construction document with ordered stable-value commitments for marine seats,
marine/debug fighter additions, command powers, configured mech deployments,
the employer-shuttle offset, and launch-time supplies. Legacy V2 launch
documents with nested V1 construction remain readable. `MissionLaunch` and
headless replay both invoke `BattleLaunchOverlay`; production retains the live
campaign cargo adapter while replay receives an independent finite supply
account. Stable catalog ids resolve equipment and powers fail-loud, and
scenario-authored roles/objectives remain owned by the construction factory.

The checked-in canonical Conquest matrix now carries two V3 launch fixtures:
seventeen squads / 204 marines at Reinforced scale and thirty-four squads / 408
marines at Full Strength. Both use the production paired-arrival policy, three
landing areas, and six reusable six-seat Aeroshuttles; the wrapper freezes every
ordered marine commitment and whole-squad identity. Historical V1 construction
and V2 launch documents remain codec coverage, not canonical commander
workloads. Canonical validation rejects a row that loses its launch wrapper,
paired arrival shape, or declared company commitment.

Sabotage construction captures the same stable scenario inputs accepted by its
production factory and rebuilds the mission's three named charge sites and
Marine commander through that factory. The generic launch envelope can wrap
that construction without changing site identity or commander shape. Its
opt-in command-evidence run is likewise forced serial and compares two replay
traces and normalized summaries byte for byte. Sabotage-owned analysis reports
site coverage, planter/retriever and recovery transitions, directive churn,
objective progress, casualties, duration, and terminal or timeout outcome; it
does not turn one fixture into a balance target.

## Extension boundary

Additional scenario kinds join the same versioned construction envelope only
when their production factory has a stable plain-data input boundary.
Representative launch capture is now a workload-selection concern, not a new
fixture layer. Deterministic player-command logs can later drive a reconstructed
battle forward; a true mid-battle checkpoint is a
separate persistence problem and should not be smuggled into construction
capture.

Deterministic command replay also requires deterministic ownership of random
streams inside parallel simulation systems. V1 proves tick-zero construction
fidelity; it does not claim that concurrently advanced sims will schedule
shared random draws in the same order.
