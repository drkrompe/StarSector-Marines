# Battle fixtures

Status: ACTIVE — tick-zero construction capture and headless replay are shipped; full post-briefing launch fidelity remains proposed.

Written: 2026-08-24

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

For the first supported scenario, the flow is:

`MissionLaunch` facts → versioned civilian-rescue fixture → production
`BattleSetup` factory → fresh `BattleSimulation` → ordinary fixed ticks.

The active battle context retains its construction fixture as cold diagnostic
metadata. The existing tick-profile capture may embed it without making the
simulation or fixture codec depend on Starsector file APIs.

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

## V1 fidelity boundary

Civilian-rescue V1 captures the seven inputs of its production scenario
factory. It therefore reproduces the generated map, rescue payload, default
forces, aircraft, objectives, and deterministic setup rolls.

The campaign launch path applies persistent marine identities, equipment,
fighter cover, command powers, and their resources after that factory returns.
Those overlays are deliberately outside V1. Exact post-briefing reproduction
requires a higher-level frozen launch fixture that invokes those same overlay
seams; partially copying their results into the construction fixture would
create two authorities.

## Extension boundary

Additional scenario kinds join the same versioned construction envelope only
when their production factory has a stable plain-data input boundary. Exact
launch replay is the next fidelity layer. Deterministic player-command logs can
later drive a reconstructed battle forward; a true mid-battle checkpoint is a
separate persistence problem and should not be smuggled into construction
capture.

Deterministic command replay also requires deterministic ownership of random
streams inside parallel simulation systems. V1 proves tick-zero construction
fidelity; it does not claim that concurrently advanced sims will schedule
shared random draws in the same order.
