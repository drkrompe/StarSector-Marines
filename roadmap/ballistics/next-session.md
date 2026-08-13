# Ballistics — next session handoff

## State of play (2026-08-13)

- **Design complete; S1 implementation in flight** (worktree +
  orchestrated agents, this session). Full design record in
  [`overview.md`](overview.md); the three open questions are now owner-
  resolved there (friendly fire = partial damage 0.5×, near-miss morale =
  path-proximity in S1, incidental contacts = flat 0.35 graze).
- S1 implementation contract:
  [`stories/s1-resolver-core.md`](stories/s1-resolver-core.md).

## Pickup instructions (if S1 didn't land)

1. **Use a worktree** (owner request) — implementation runs isolated from
   the shared main tree; docs/commits merge back per repo commit rules.
2. Implement against `stories/s1-resolver-core.md` — it is the contract
   (APIs, constants, algorithm, tests). Don't re-derive design from chat.
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
