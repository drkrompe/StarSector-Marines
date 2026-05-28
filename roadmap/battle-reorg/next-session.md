# Next session — battle-tier feature-vertical reorg

Read [`overview.md`](overview.md) first — concept, target tree, full
file-by-file mapping, the GOAP partition rule, and the 10-slice plan.

## Commit chain so far

```
ea177c1  battle-reorg: slice 1 — consolidate combat pipeline into combat/
0c7bf92  battle-reorg: slice 2a — sprites/ → world/tiles/
579db91  battle-reorg: slice 2b — mapgen/ tree → world/gen/
f44ba82  battle-reorg: slice 2c — map/ → world/model/, UrbanMapGenerator → world/gen/
```

## State of play

- **Slice 1 SHIPPED.** `fx/` + `shots/` + `damage/` + the combat-shared
  half of `weapons/` → `battle/combat/`; visual sink → `battle/combat/fx/`.
  `fx/`/`shots/`/`damage/` dissolved.
- **Slice 2 SHIPPED** (2a/2b/2c). `sprites/` → `world/tiles/`; the whole
  `mapgen/` tree → `world/gen/` (bsp/, bsp/fill/, road/ preserved);
  `map/` → `world/model/` with `UrbanMapGenerator` peeled off to
  `world/gen/`. `map/`/`mapgen/`/`sprites/` all dissolved. `world/` now =
  `{model:15, gen:11+subdirs, tiles:9}`. Build + full suite green.
  - `MapVehicle`/`VehicleKind` parked in `world/model/` — **slice 3 lifts
    them to `vehicle/`.**
  - `DistrictTheme` (world/model) vs `MapDistrictTheme` (world/gen) dedup
    still deferred (a logic change, not a relocation).
- **Proceeded ahead of the facade-drop** because sibling sessions are
  paused (tree quiet). Remaining slices should re-check tree quietness.
- **Note:** a paused sibling agent's worktree under `.claude/worktrees/`
  still references old packages; reconciles on its rebase — not our commits.

## When picking up

1. Re-confirm the tree is quiet (no concurrent large churn).
2. Next is **slice 3 (`vehicle/`)** — `ground/` → `vehicle/`, and lift
   `MapVehicle`/`VehicleKind` out of `world/model/` into `vehicle/`. See
   `overview.md` § Slice plan.
3. Proven per-slice recipe (refine as needed):
   - `git mv` files; for a package *split*, do an **ordered** FQN rewrite
     (specific-class redirects before the general prefix rewrite).
   - Fix package declarations; global FQN rewrite over `src/main`+`src/test`.
   - Strip redundant self-package imports (per subpackage depth).
   - `compileJava --rerun-tasks` — incremental **under-reports** the
     missing-import set; force a full compile. For a split, the moved-out
     class needs explicit imports for the siblings it used by bare name
     (a quick cross-check script beats iterating the compiler).
   - Relocate same-package **test** files to mirror; their bare refs break too.
   - **Commit renames with the common-parent pathspec** (`.../battle` +
     any outside-`battle/` consumers) so the delete and add halves land in
     the *same* commit — a pathspec naming only the new path records adds +
     orphaned deletes instead of renames (cost slice 2a an amend).
   - Keep the per-file GOAP partition judgment (slice 6) on the main thread.

## Decisions already locked (don't relitigate)

- Feature-vertical, with a thin framework core (engine vs game framing).
- Three boundary calls: shared GOAP vocab → framework built-ins; single
  `EffectsService` sink in `combat/`; `HeavyWeapons` mechanism → `combat/`.
- `entity/` rename (slice 8) is optional — decide at slice time.
