# Slice D2a — Chronicle storage foundation

**Status:** CODE COMPLETE (2026-08-12)

**Implemented:** `dc3da47d`

## Goal

Persist only the political events the player has learned, while giving the
editor an exactly-once marker even when an event belongs to the silent middle.

## Locked rules

- `chronicle[]` is an append-only snapshot table; it stores source chain,
  terminal outcome, news band, actor, target, location, happened day, and
  learned day rather than joining against mutable chain/house state later.
- Initial event vocabulary is append-safe `CHAIN_OUTCOME`; learned bands are
  `INTIMATE` and `EPIC`.
- Each chain persists the day discovery classified it. `-1` means unprocessed;
  a non-negative day can represent either a printed dispatch or silent discard.
- Capacity growth preserves `-1` sentinels and legacy saves backfill every new
  column without inventing learned history.

## Automated verification

- `CampaignStateChronicleColumnsTest` covers snapshot payload, id allocation,
  capacity growth sentinels, chain processed ticks, and legacy backfill.
