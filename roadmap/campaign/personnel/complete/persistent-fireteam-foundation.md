# Persistent fireteam foundation

**Status:** CODE COMPLETE (2026-08-12)

## What shipped

- Persisted six-person fireteams, a reserve pool, recruitment, transfers,
  vacancies, renaming, and legacy-save membership backfill.
- Cargo-backed enlistment and ready-reservist demobilization, with a one-time
  free starting complement.
- A dedicated pre-battle fireteam assignment screen and selected-personnel
  overlay onto player-owned shuttle cycles.
- Stable battle identity and deterministic RTD/WIA/MIA/KIA resolution, WIA
  recovery timers, survivor XP, and a fireteam Results debrief.
- Individual armory allocation plus atomic whole-fireteam presets.

## Hardening

- Explicit but stale selection no longer falls back to unrelated line
  personnel (`4737404d`).
- A generated-battle integration test proves employer shuttle seats remain
  anonymous while the first player shuttle receives persistent identity
  (`c35e88d4`).
- Re-resolving the same frozen personnel outcome yields the same casualty map
  (`822572b7`).

## Deferred

- Captain-to-fireteam command ownership and rank-scaled formation capacity.
- Trait effects and other progression gates without a shipped consumer.
- Manual visual/feel verification during the current automated-only session.

