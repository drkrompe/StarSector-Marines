# Skybattle Fleet Control

Status: PARKED

Written: 2026-08-23

Read `vanilla-combat-bridge-nouns.md` before reviving this story.

## Current substrate

A one-shot `ASSAULT` assignment can nudge player carriers toward the ground band,
but normal admiral and ship caution soon pull them away. Carrier takeover proves
stronger movement authority exists, while the current debug scene has no enemy
fleet capable of threatening the invasion transport.

## Goal

Turn the fleet-above layer into a real contested battle with coherent fleet
command, standoff behavior, and cross-layer consequences.

## Contract

- Define which fleet authority owns movement while ground-control mode is active.
- Give carriers a readable standoff band that projects fighters without turning
  every order into a suicidal charge.
- Add enemy fleet pressure and explicit air-to-ground target priorities.
- Let carrier destruction make undeployed invasion capacity genuinely at risk.
- Exchange only bounded events/resources with the ground simulation; neither
  engine mirrors the other's mutable combat state.

## Acceptance

The player can command a contested fleet layer, carriers hold intentional
positions, both sides exert legible pressure on the ground battle, and loss of
an orbiting transport resolves the already-declared undeployed-wave stake.

## Out of scope

- Vanilla terrain collision or wall-clamping.
- Directly piloting a simulation proxy.
- Ground-unit selection and orders; see `ground-control-mode.md`.
