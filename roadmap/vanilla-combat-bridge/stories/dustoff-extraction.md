# Dustoff Extraction

Status: PLANNED

Written: 2026-08-23

Read `vanilla-combat-bridge-nouns.md` and `air-nouns.md` before implementing
this story.

## Goal

Add the deliberate inverse of the drop invasion: surviving ground forces board
sim-native transports and leave through the bridge while exposed to the same
ground and fleet risks.

## Contract

- Extraction selects authoritative ground squads, not visual proxies.
- Landing, boarding, capacity, launch, damage, and loss use the normal air-body
  lifecycle.
- A boarded force is removed from ground authority exactly once and reported
  through the mission result only after its extraction outcome is known.
- Carrier and transport loss produce explicit stranded/lost outcomes; no unit
  is silently restored because the host session ended.

## Acceptance

A bounded extraction order can land transports, board eligible squads up to
finite capacity, launch them, and return survivor/loss results through the
production mission seam. Destroyed or interrupted lifts cannot duplicate or
erase personnel.

## Out of scope

- General campaign evacuation UX outside a battle.
- Enemy fleet command behavior.
- Replacing the simulation's shuttle model with vanilla ships.
