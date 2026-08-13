# Ballistics — next session handoff

## State of play (2026-08-13)

- **Design complete, nothing implemented.** Full design record in
  [`overview.md`](overview.md) — read it first; the decisions there
  (fire-time space-time raycast, cover→block-chance swap at 15/30/45%,
  accuracy stack as per-contact roll, shooter lead, committed outcomes)
  were settled in-session and are not open for casual re-derivation.
- No code touched. No story docs yet — S1's story doc is the first
  pickup artifact.

## Pickup instructions

1. **Use a worktree** (owner request) — implementation runs isolated from
   the shared main tree; docs/commits merge back per repo commit rules.
2. Write `stories/s1-resolver-core.md` from the S1 sketch in overview.md,
   resolving the three open questions there (friendly-fire policy,
   near-miss morale interim, incidental-contact roll) — friendly-fire is
   the one that needs an owner call if playtesting feel is unclear.
3. Verify at pickup (cheap, before building on them):
   - `MOVEMENT_VEL_X/Y` columns still live (`FacingSystem` consumes them).
   - `UnitSpatialIndex` still snapshot-position buckets (segment query
     builds on that contract).
   - `CoverAccuracyResolver` call sites — the swap must remove *all*
     accuracy-side cover terms in the same slice that adds physical
     interception (double-count trap, overview §4).

## Key files (current hitscan path being replaced)

- `battle/infantry/InfantryWeapons.fireShot` — the hitscan roll + inline
  damage + `ShotEvent` post.
- `battle/combat/CoverAccuracyResolver` — the accuracy-side cover term
  that dies in S1.
- `battle/combat/ShotEndpoint`, `ShotRaycast` — absorbed/retired by S4.
- `battle/combat/ShotService` — grows the pending-impact clock.
- `battle/nav/NavigationGrid.firstWallOnLine`, `world/model/DoodadService`
  — collision inputs.
