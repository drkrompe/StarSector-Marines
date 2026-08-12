# Slice D3a — Active-rumor persistence foundation

**Status:** CODE COMPLETE (2026-08-12)

## Goal

Persist active-chain discovery independently from terminal outcome processing,
and give learned rumors an immutable, explicitly uncertain Chronicle shape.

## Locked rules

- Each chain separately records its last active-discovery evaluation day and
  first successful discovery day; both use `-1` until set.
- Terminal `chainDiscoveryProcessedTick` retains its D2 meaning and is never
  overloaded as an active-rumor cadence clock.
- `ACTIVE_CHAIN_RUMOR` appends after `CHAIN_OUTCOME`; event ordinals never move.
- `ChronicleConfidence` is append-safe with `CONFIRMED` at ordinal zero so D2
  and legacy rows backfill correctly; active rumors snapshot as `RUMOR`.
- A rumor stores chain identity, participants, location, initiation day, and
  learned day. A later outcome appends a new row rather than rewriting history.

## Automated verification

- `CampaignStateChronicleColumnsTest` covers confirmed outcomes, rumor payload,
  capacity sentinels, independent discovery clocks, and legacy backfill.
