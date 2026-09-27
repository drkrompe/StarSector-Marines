# Slice 5 — Planner Performance Budget

Status: IN PROGRESS — measure remaining recovery slices, fleet-wide admission and planner-start failures.

Written: 2026-06-02

Updated: 2026-09-27 — per-vehicle recovery slices are bounded; fleet-wide and physical-progress questions remain.

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

## Remaining scope

Recovery now retains its direction enumeration and active on-demand search
across ticks, with finite expansion slices and at most one newly allocated
search per vehicle per tick. Pending does not enter the failure memo; changed
request identity cancels obsolete work. This is a per-vehicle limit, not a
battle-wide scheduler or a bound on allocation and turn refinement.

- Attribute expensive slices separately from search allocation, completed-path
  refinement and exact failed-result reuse. A bounded node count alone does
  not bound wall time; compare cancellation with completion to detect starvation.
- Investigate ranking departure bearings before proving their routes. The
  current winner depends on forward alignment and stable direction order,
  not route length; a lower-ranked feasible route cannot replace a higher-ranked
  feasible one. Pin selection parity before short-circuiting those proofs.
- Share a battle-wide allowance if simultaneous vehicle slices remain a spike
  source, preserving safe tracking/holding and fair progress of retained proofs.
- Use invalid-start classifications to distinguish physical overlap from a
  physically legal pose rejected only by planning padding. Making rejection
  cheap does not restore progress. Any escape behavior needs separate motion
  acceptance, including map edges, walls, docking, and off-map departure.

## Candidate levers

- stagger replans across vehicles under a per-frame planning budget;
- cache goal-distance fields shared by vehicles on one route;
- bound and reuse planner workspaces;
- skip replanning while the current trajectory remains valid and well tracked.

## Acceptance

Show fresh recovery's actual per-tick searches, expanded nodes and wall time,
not just lower cumulative cost from reuse. A resumed search must make progress
under its allowance, including when its lifetime attempt budget is spent but
an already-paid frontier remains. Preserve route selection unless a deliberate
behavior change is explicitly measured.

Any resumed search needs an explicit identity/invalidation rule for changed
pose, route, exclusions, and navigation inputs. Vehicle wrecks do not mutate
terrain; real topology changes must be handled by the owning routing contract.
Keep physical collision and safe on-grid holding intact. Do not loosen the
planner's padding globally to make a failure counter disappear.

## Out of scope

- Multithreaded planning.
- Speculative optimization before profiling.
