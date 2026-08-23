# Slice 3 — Recovery Terminal Policy

Status: PLANNED — recovery safeguards are shipped; the terminal no-route policy awaits route acceptance.

Written: 2026-06-02

Updated: 2026-08-23 — folded shipped recovery rungs and retained only unresolved policy.

Read `convoy-nouns.md` before implementing this story.

## Shipped substrate

The controller already commits to bounded reverse after wall blockage or a
geometrically impossible forward turn, detects lack of corridor progress, and
requests a cost-field reroute that avoids the failing area. These behaviors are
standing convoy semantics, not open implementation scope.

## Goal

Give a genuinely unroutable convoy an explicit terminal outcome instead of a
repeated tracking-and-reroute loop.

## Decision to make

Choose the least surprising payload-safe terminal policy from live-play
evidence: abort and remove the delivery, disembark at a safe nearby cell, or
enter a durable disabled/held state. The choice must state what happens to the
passengers, reinforcement request, world actor, and any ticket already spent.

## Conditional follow-up

Turn-aware route or approach validation is not automatic scope. Add it only if
`route-and-motion-acceptance.md` shows static clearance repeatedly selecting
approaches the body fits through but cannot enter kinematically.

## Acceptance

An unrecoverable vehicle reaches one explicit terminal state without clipping,
oscillation, repeated reroute spam, duplicated passengers, or a silently lost
reinforcement accounting outcome.

## Out of scope

- General handling feel and cost-field tuning.
- Dynamic vehicle/infantry or multi-truck avoidance.
