# Next session — battle-tier feature-vertical reorg

Read [`overview.md`](overview.md) first — concept, target tree, full
file-by-file mapping, the GOAP partition rule, and the 10-slice plan.

## Commit chain so far

```
<slice 1>  battle-reorg: slice 1 — consolidate fx/shots/damage/weapons into combat/
```

## State of play

- **Slice 1 SHIPPED.** `fx/` + `shots/` + `damage/` + the combat-shared
  half of `weapons/` (Detonations, FireStance, HeavyWeapons, RangeFalloff,
  ShotEndpoint, ShotRaycast) now live in `battle/combat/`; the visual sink
  (EffectsService + Decal/Particle/SmokePlume/SmokingWreck/WeaponLights/
  Impact*) lives in `battle/combat/fx/`. The `fx/`, `shots/`, `damage/`
  packages are dissolved. 23 prod files + 2 test files relocated; ~63
  consumers rewritten. Build + full test suite green.
- **Proceeded ahead of the facade-drop** because the sibling sessions were
  paused (tree quiet) — the sequencing caveat below was about concurrent
  churn, which didn't apply. Remaining slices should re-check tree
  quietness before starting.
- **Note:** a paused sibling agent's worktree under `.claude/worktrees/`
  still references the old packages; that's its own checkout and will
  reconcile on its next rebase/merge — not part of slice 1.

## When picking up

1. Re-confirm the tree is quiet (no concurrent large churn) — re-check the
   facade-drop status if sibling sessions resumed.
2. Next is **slice 2 (`world/` consolidation)** — `map/` + `mapgen/` +
   `sprites/` → `world/{model,gen,tiles}`, merge `UrbanMapGenerator`, lift
   `MapVehicle`/`VehicleKind` out for slice 3. See `overview.md` § Slice plan.
3. Each slice: relocate (git mv) + ordered FQN rewrite → strip redundant
   self-package imports → `compileJava` (use `--rerun-tasks` for the full
   error set; incremental under-reports) → relocate same-package test files
   to mirror → tests green → commit. Keep the per-file GOAP partition
   judgment (slice 6) on the main thread.

## Decisions already locked (don't relitigate)

- Feature-vertical, with a thin framework core (engine vs game framing).
- Three boundary calls: shared GOAP vocab → framework built-ins; single
  `EffectsService` sink in `combat/`; `HeavyWeapons` mechanism → `combat/`.
- `entity/` rename (slice 8) is optional — decide at slice time.
