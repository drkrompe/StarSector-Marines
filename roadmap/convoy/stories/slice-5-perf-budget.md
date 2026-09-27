# Slice 5 — Planner Performance Budget

Status: IN PROGRESS — production-paced captures establish repeated local setup and synchronous recovery cost.

Written: 2026-06-02

Updated: 2026-09-27 — contract exact invalid-start rejection first; investigate recovery reuse separately.

Read `convoy-nouns.md` before implementing this story.

## Goal

Keep simultaneous rolling planners inside the battle tick budget without
weakening route or recovery quality.

## Trigger

The earlier multi-truck trigger is no longer required to establish a problem:
the production-paced Conquest tail on `d365b08f6` recorded 14,246 local plans,
all failing after one expansion, with 1.277 seconds of heuristic setup. A
separate recovery call consumed 51.255 ms for 16 attempts and 85,602 nodes.
The report is `/tmp/marines-route-admission-experiment.md`; these are distinct
costs and must not be credited to one optimization.

## Current scope

- Check the exact padded starting footprint before local heuristic allocation.
  An invalid padded start already rejects every first successor, so preserve
  the same null result without setup. Keep window-entry, off-map-tail, docking,
  physical clearance, and recovery policies unchanged.
- Classify refusals as actual-chassis-invalid versus planning-padding-only,
  and bounds versus terrain/closed-edge failure. Retain a same-build control.
- Prove refusal parity and zero heuristic work on focused inputs; measure
  classification and remaining recovery cost in the production-paced fixture.
- Reuse a completed failed bounded recovery only for an identical request
  against the same frozen routing inputs, including snapped endpoints,
  exclusions, type, and tried-bearing policy. Changed requests retry; successful
  answers are not negative entries. Control/route reset releases the entry.
  Keep the legacy live-input branch unchanged and provide an independent control.
- Progressive recovery remains separate: exact reuse removes repeated bursts,
  not the cost of a first attempt. A future pending route must not be treated
  as failure or installed after its intent changes.

## Candidate levers

- stagger replans across vehicles under a per-frame planning budget;
- cache goal-distance fields shared by vehicles on one route;
- bound and reuse planner workspaces;
- skip replanning while the current trajectory remains valid and well tracked.

## Acceptance

The first change preserves local-plan outcomes while avoiding heuristic and
lattice work for a proved invalid start; valid starts still plan, including
starts outside the supplied window that can move into it. Counters distinguish
the rejection causes and the control. This does not close the independent
recovery spike or the broader planner-budget story.

Any later cache or resumed search needs an explicit identity/invalidation rule
for changed pose, route, exclusions, and navigation inputs. Vehicle wrecks do
not mutate terrain; real topology changes must be handled by the owning
routing contract.

## Out of scope

- Multithreaded planning.
- Speculative optimization before profiling.
