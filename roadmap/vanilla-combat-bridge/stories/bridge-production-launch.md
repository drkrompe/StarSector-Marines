# Combat Bridge Production Launch

Status: PLANNED

Written: 2026-08-23

Read `vanilla-combat-bridge-nouns.md` before implementing this story.

## Current substrate

The bridge, host session, live Conquest simulation, and invasion loop run only
through the debug hotkey probe. Durable host and adapter classes therefore
remain annotated and reached as debug-only code.

## Goal

Launch a sim-coupled vanilla battle from the production mission flow using a
frozen battle-native configuration.

## Contract

- Freeze the selected mission, fleet support, marine depth, and transport
  throughput at the campaign-to-battle boundary.
- Pass data into `GroundBattleConfig`; do not let the running simulation retain
  live campaign objects or query campaign cargo directly.
- Preserve the spectator camera, fleet stash/restore handshake, and
  simulation-owned completion behavior.
- Remove the probe launcher and debug-only reachability only after the
  production path exercises the same session lifecycle.

## Acceptance

A production mission can enter, run, resolve, and leave the bridge host with
the selected ground battle and committed fleet logistics intact. Campaign
state is restored exactly once, results return through the ordinary mission
seam, and the hotkey probe is no longer required.

## Out of scope

- Building the skybattle command layer.
- Dustoff/extraction.
- Changing the ground simulation's combat rules.
