# Slice 3 — Retire Obsolete Road-Graph Routing

Status: READY — the consumer audit bounds this to dead route expansion and debug setup.

Written: 2026-06-02

Updated: 2026-08-23 — preserved live graph authorities and narrowed the cleanup.

Read `convoy-nouns.md` before implementing this story.

## Goal

Remove the obsolete graph-only route construction without deleting the road
graph authorities that map generation and convoy endpoint selection still use.

## Scope

- Confirm `BattleSetup.maybeSpawnDebugConvoy` has no live caller, then delete
  that legacy spawn path and only the helpers/imports made dead with it.
- Re-audit `ConvoyPlanner.planPath` and `expandToWaypoints`; delete them when the
  dead debug path is their final consumer.
- Keep `ConvoyPlanner.pickExitNode` while production convoy selection still
  uses it. Replacing graph-based exit selection is a separate behavioral change.
- Update comments that describe the cost-field route as graph-expanded or cite
  the deleted nested routing docs.

## Preserve

`RoadGraph`, `RoadGraphBuilder`, the generation stage, road reservation,
compound circulation, final validation, map preview/debug, and production
entry/drop-off/exit selection are live. This story must not delete or weaken
those consumers merely because roads stopped being motion rails.

## Acceptance

- No live code references the deleted debug path or obsolete route-expansion
  methods.
- Production convoy dispatch still selects graph-informed endpoints and uses
  the cost-field router between them.
- Map generation, reservation, validation, and preview retain their road-graph
  contracts.

## Out of scope

- Cell-based replacement of production endpoint selection.
- Cost, clearance, controller, or recovery tuning.
