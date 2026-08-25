# Reinforcement Strength Scaling

Status: PLANNED

Written: 2026-08-23

Read `reinforcement-nouns.md` before implementing this story.

## Current substrate

Requests carry SMALL, MEDIUM, or LARGE, but the production ladder delivers its
fixed small-response baseline. Quantity, staggering, and ticket cost therefore
do not yet express the requested scale consistently.

## Goal

Make request strength one coordinated force, cost, and pacing contract across
convoy, shuttle, and walk-in delivery.

## Decisions

- Define delivered force and ticket cost for each strength.
- Define how multi-actor means stagger arrivals and reserve destinations.
- Keep means-specific presentation while preserving comparable force intent.
- Decide how partial feasibility degrades: smaller fulfillment, later retry, or
  rejection and fall-through.

## Acceptance

Every means interprets each supported strength deliberately, pays the declared
cost once, and delivers at a cadence that does not create accidental burst
advantage. Unsupported scale has an explicit fallback rather than being ignored.

## Out of scope

- General convoy following or planner optimization beyond what measured
  multi-vehicle delivery requires.
- Unit-tier selection; the battle-frozen `GroundRosterProfile` remains
  authoritative for campaign-target defenders, with `FactionUnitRoster` only
  the side-level legacy/player fallback.
- Mission-specific balance tuning before the shared contract exists.
