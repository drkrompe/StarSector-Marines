# Slice 4 — Tune cost weights, clearance, string-pull

> Feel pass for the cost router. May fold into navigation-rework slice 4.

## Goal

Make routed convoys *look* right: roads preferred but not slavishly, sensible
corner-cutting, no awkward terrain-slumming, clearance neither too timid nor too
permissive.

## What lands (candidate knobs)

- Per-`GroundKind` cost weights (slice-0 starting values → playtested values).
- Clearance erosion radius per vehicle class (too timid = avoids drivable
  streets; too permissive = clips walls the local planner then fights).
- String-pull aggressiveness, and **cost-aware pull** if trucks visibly cut
  across lawns at intersections (only straighten when the cost increase stays
  under a threshold).
- Interaction with the rolling `LocalTrajectoryPlanner` horizon — the corridor
  is advisory, so over-tight string-pull just makes the local planner work
  harder; find the balance.

## Acceptance

- Playtest: convoys read as drivers picking good routes — hugging roads,
  cutting plazas when it's obviously faster, never threading impossible gaps,
  never slumming across terrain a driver wouldn't.

## Notes

- Pure-tuning slice; no new structure expected. If it stays small, fold into
  [`../../navigation-rework/stories/slice-4-tuning-feel.md`](../../navigation-rework/stories/slice-4-tuning-feel.md)
  rather than shipping separately.
