# Battle fixtures

Status: ACTIVE — V1 scenario construction and V2 post-briefing launch capture,
headless replay, and opt-in forced-serial Conquest command evidence are shipped.

Written: 2026-08-24

Updated: 2026-08-25 — added the V2 launch overlay around the unchanged V1
construction layer.

## Vocabulary

A **battle fixture** is a versioned set of authored construction facts. It is
the smallest plain-data description that can ask a production scenario factory
to rebuild a battle at tick zero. It is not a serialized simulation, a save
game, or a mid-battle checkpoint.

A **capture** freezes those facts at the launch boundary, before scenario
construction can hide the requested seed behind deterministic map retries. A
profile dump may embed that fixture beside its live timing evidence. The timing
window describes the observed live battle; the embedded fixture describes how
to reconstruct its initial scenario.

A **replay** creates a fresh battle by feeding the captured facts back through
the same production factory used by ordinary play. Headless tests and profiling
tools advance that returned simulation through the ordinary fixed-tick loop.
They do not own a second scene builder.

A **profile run** is an opt-in headless replay measured by an external runtime
profiler. Performance evidence is an artifact, not a pass/fail timing budget;
continuous tests prove codec and deterministic-construction behavior instead.

## Authority and flow

The campaign-to-battle boundary owns capture because it still has the requested
seed, shuttle commitments, risk, target-world profile, and scenario knobs. The
fixture owns only immutable copies of those inputs. `BattleSetup` remains the
authority for map selection, deterministic retries, payload installation,
spawns, loadouts, objectives, and commanders.

For supported scenarios, the flow is:

`MissionLaunch` facts → V1 construction fixture → production `BattleSetup`
factory → V2 launch overlay → fresh `BattleSimulation` → ordinary fixed ticks.

The active battle context retains the highest-fidelity available fixture as
cold diagnostic metadata. The existing tick-profile capture may embed it
without making the simulation or fixture codec depend on Starsector file APIs.

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

## Fidelity layers

Civilian-rescue V1 captures the seven inputs of its production scenario
factory. It therefore reproduces the generated map, rescue payload, default
forces, aircraft, objectives, and deterministic setup rolls.

Conquest V1 captures the requested seed, ordered shuttle manifest, heavy-armor
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
a winner.

The campaign launch path applies persistent marine identities, equipment,
fighter cover, command powers, and their resources after that factory returns.
Those overlays remain outside V1. A V2 `BattleLaunchFixture` wraps the unchanged
construction document with ordered stable-value commitments for marine seats,
marine/debug fighter additions, command powers, configured mech deployments,
the employer-shuttle offset, and launch-time supplies. `MissionLaunch` and
headless replay both invoke `BattleLaunchOverlay`; production retains the live
campaign cargo adapter while replay receives an independent finite supply
account. Stable catalog ids resolve equipment and powers fail-loud, and
scenario-authored roles/objectives remain owned by the V1 factory.

The checked-in V2 Conquest launch proves post-overlay headless reconstruction,
and the command harness can consume either V1 or V2. The canonical command
matrix remains on its historical V1 fixtures until representative live
full-company launches are captured and deliberately rebaselined.

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
