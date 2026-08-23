# Slice 4 — Route and Driving Feel

Status: READY — tune only from observed issues in the manual route-and-motion pass.

Written: 2026-06-02

Updated: 2026-08-23 — removed shipped turn correctness from the remaining feel knobs.

Read `convoy-nouns.md` and run `route-and-motion-acceptance.md` first.

## Goal

Make a convoy read as a deliberate heavy vehicle without changing its authority
boundaries or adding new mechanics.

## Current baseline

Speed-scaled lookahead and a curvature speed governor are shipped. The cost
field prefers roads, the clearance mask rejects static gaps, the local planner
tracks a rolling forward-feasible trajectory, route bends are minimum-radius
validated, and recovery can reverse or cumulatively reroute.

## Evidence-led knobs

- lookahead-versus-speed and corner-speed curves;
- local replan cadence and horizon;
- acceleration, braking, and steering slew for each existing handling profile;
- terrain costs, clearance erosion, and string-pull aggressiveness;
- docking range/speed and recovery reaction timing;
- reroute caps only if the remaining bounded no-route retries read poorly.

Cost-aware string-pulling is a conditional refinement: add it only if geometric
pulling visibly cuts across terrain that contradicts the route's road preference.

## Acceptance

A representative city run has continuous corners, decisive recovery, sensible
road preference, and a clean stop/departure without nervous wobble, timid
clearance, terrain slumming, or tuning that masks a correctness failure.

## Out of scope

- New vehicle mechanics or variants.
- New route-planning architecture beyond the shipped minimum-radius validation.
- Performance work without a measured multi-vehicle cost.
