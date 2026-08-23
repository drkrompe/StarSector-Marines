# Slice 4 — Route and Driving Feel

Status: READY — tune only from observed issues in the manual route-and-motion pass.

Written: 2026-06-02

Updated: 2026-08-23 — unified controller, cost, clearance, and recovery feel tuning.

Read `convoy-nouns.md` and run `route-and-motion-acceptance.md` first.

## Goal

Make a convoy read as a deliberate heavy vehicle without changing its authority
boundaries or adding new mechanics.

## Current baseline

Speed-scaled lookahead and a curvature speed governor are shipped. The cost
field prefers roads, the clearance mask rejects static gaps, the local planner
tracks a rolling feasible trajectory, and recovery can reverse or reroute.

## Evidence-led knobs

- lookahead-versus-speed and corner-speed curves;
- local replan cadence and horizon;
- acceleration, braking, and steering slew for each existing handling profile;
- terrain costs, clearance erosion, and string-pull aggressiveness;
- docking range/speed and recovery reaction timing;
- cumulative avoidance or reroute caps only if a repeated-route loop appears.

Cost-aware string-pulling is a conditional refinement: add it only if geometric
pulling visibly cuts across terrain that contradicts the route's road preference.

## Acceptance

A representative city run has continuous corners, decisive recovery, sensible
road preference, and a clean stop/departure without nervous wobble, timid
clearance, terrain slumming, or tuning that masks a correctness failure.

## Out of scope

- New vehicle mechanics or variants.
- Turn-aware macro routing unless the dedicated recovery story admits it from
  repeated evidence.
- Performance work without a measured multi-vehicle cost.
