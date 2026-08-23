# Slice 5 — Planner Performance Budget

Status: DEFERRED — blocked on multi-truck convoys and measured planner cost.

Written: 2026-06-02

Updated: 2026-08-23 — retained as measurement-led work behind simultaneous vehicles.

Read `convoy-nouns.md` before implementing this story.

## Goal

Keep simultaneous rolling planners inside the battle tick budget without
weakening route or recovery quality.

## Trigger

Implement `multi-truck-convoys.md`, profile a representative four-vehicle
dispatch using `[[jfr_analysis_workflow]]`, and contract only the levers that
the profile proves necessary.

## Candidate levers

- stagger replans across vehicles under a per-frame planning budget;
- cache goal-distance fields shared by vehicles on one route;
- bound and reuse planner workspaces;
- skip replanning while the current trajectory remains valid and well tracked.

## Acceptance

A representative four-vehicle convoy produces no visible replan hitch and the
measured planning cost meets the agreed tick budget. Any cache has an explicit
invalidation rule before vehicle wrecks or terrain mutation can make it stale.

## Out of scope

- Multithreaded planning.
- Speculative optimization before profiling.
