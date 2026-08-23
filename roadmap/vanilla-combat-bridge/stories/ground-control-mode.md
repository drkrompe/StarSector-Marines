# Ground Control Mode

Status: PLANNED

Written: 2026-08-23

Read `vanilla-combat-bridge-nouns.md` before implementing this story.

## Current substrate

`SeeThroughPlugin` proves a cursor-sized reveal disk can fade player ships
without revealing enemy proxies. It is a debug affordance, not a command mode:
the bridge has no ground selection, order routing, or ground-focused UI state.

## Goal

Give the player an explicit mode for inspecting, selecting, and commanding the
ground battle while the vanilla fleet continues under its own authority.

## Contract

- Mode entry is explicit and consumes ground-directed input before vanilla
  core controls.
- Ground picking reads simulation entities through the same camera projection
  used to render them.
- Selection and orders use ordinary ground-simulation authorities; the host
  owns routing and presentation only.
- Contextual ship see-through remains a visual aid with screen-stable radius,
  soft falloff, and clean restoration when the mode exits.

## Acceptance

The player can enter and leave the mode, select a visible ground unit, inspect
authoritative state, and issue one supported squad-level order without also
piloting or selecting a vanilla ship. Ship alpha and input ownership restore
cleanly on every exit path.

## Out of scope

- Fleet-side command and standoff behavior; see `skybattle-fleet-control.md`.
- A universal above-HUD rendering hook.
- Direct player control of a ground proxy as a vanilla ship.
