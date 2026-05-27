# Phase 3 — SoA primitive promotions

Pattern locked in; every promotion follows the same shape
(see design rules in [`overview.md`](../overview.md)).

## Primitives promoted

| Field | Type | Commit | Notes |
|-------|------|--------|-------|
| hp / maxHp | `float[]` | `7972009` + `53ee895` | First promotion; established the pattern |
| cellX / cellY | `int[]` | `a78d417` + `9787bd9` | Parallel arrays, not interleaved (design rule 5) |
| cooldownTimer | `float[]` | `a4df09b` | First decrementer-style per-tick float; 13 consumer files |

## Consumer migrations (dense-iter + array reads)

| Consumer | Commit | Scope |
|----------|--------|-------|
| SquadMoraleSystem | `e78bd25` | First demonstration |
| UnitSpatialIndex + DestIndex rebuild | `4edb1f4` | Per-tick rebuild, biggest payoff |
| DamageResolver.pickPromotionCandidate | `d2a1cbd` | Locks pattern across damage path |
| TacticalScoring | `ef4d798` | 5 reader loops |
| SquadAlertSystem | `9ff4dae` | Hottest per-tick file; O(N²) LoS scan |
