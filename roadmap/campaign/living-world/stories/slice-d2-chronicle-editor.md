# Slice D2b — Chronicle two-band editor

**Status:** CODE COMPLETE (2026-08-12)

## Goal

Convert terminal political chains into selective learned history instead of an
omniscient exhaust feed.

## Locked rules

- `DiscoveryPropagationSystem` consumes each terminal chain exactly once and
  snapshots it into `chronicle[]` only when it crosses a news band.
- An outcome involving an actor or target with a player-reputation row is
  `INTIMATE`, regardless of tier.
- Otherwise Tier-3+ outcomes are `EPIC`; untouched Tier-1/2 outcomes form the
  silent middle and receive only their processed tick.
- Intimate takes precedence when a touched high-tier outcome qualifies for both.
- ACTIVE chains are neither learned nor marked processed. Active rumors and
  intervention contracts remain a later layer driven by `discoveryRisk`.

## Automated verification

- `DiscoveryPropagationSystemTest` covers intimate and epic snapshots, failed
  outcomes, exact-once replay prevention, silent processing, and active rows.
