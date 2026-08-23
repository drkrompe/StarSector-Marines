# FX child entities

Status: PARKED

Written: 2026-08-23

Read `ecs-nouns.md` before reviving this story.

## Activation gate

A concrete effect needs independent attachment, detachment, or lifetime behavior
that the current effect services cannot express cleanly.

## Goal

Prove entity-backed effects with one bounded FX family before generalizing. Its
parent link, local offset, lifetime, and tier-neutral appearance are component
data; spawning, cleanup, and rendering have explicit owners.

## Acceptance

- The chosen effect attaches, detaches, expires, and survives parent loss by a
  documented rule.
- Creation stays immediate and walk-safe; removal or archetype changes defer
  through the command buffer when they affect a query currently being walked.
- Rendering consumes the effect through a capability query without graphics
  handles entering simulation state.
- Existing effect families remain on their current paths until separately migrated.
