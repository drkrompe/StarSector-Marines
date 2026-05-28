# Next session — battle-tier feature-vertical reorg

Read [`overview.md`](overview.md) first — concept, target tree, full
file-by-file mapping, the GOAP partition rule, and the 10-slice plan.

## Commit chain so far

```
ea177c1  battle-reorg: slice 1 — consolidate combat pipeline into combat/
0c7bf92  battle-reorg: slice 2a — sprites/ → world/tiles/
579db91  battle-reorg: slice 2b — mapgen/ tree → world/gen/
f44ba82  battle-reorg: slice 2c — map/ → world/model/, UrbanMapGenerator → world/gen/
e63bd32  battle-reorg: slice 3 — ground/ → vehicle/, absorb MapVehicle/VehicleKind
5b68452  battle-reorg: slice 4 — objective/reinforcement/compound + resources → command/
6134d1e  battle-reorg: slice 5 — Squad/SquadPlan/SquadAlertLevel → squad/
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
  - `DistrictTheme` (world/model) vs `MapDistrictTheme` (world/gen) dedup
    still deferred (a logic change, not a relocation).
- **Slice 3 SHIPPED.** `ground/` (11 files) → `vehicle/`; `MapVehicle` +
  `VehicleKind` (nested `VehicleSheet` rides along) lifted out of
  `world/model/` into `vehicle/`. `ground/` dissolved. Pure package rename
  — no class splits, no new cross-imports (ground files and the two lifted
  classes never referenced each other). One outside-`battle/` consumer
  (`ops/BattleScreen.java`) folded into the same commit. Build + suite green.
- **Slice 4 SHIPPED.** `objective/` (6), `reinforcement/` (12), `compound/`
  (3) nested under `command/` as subpackages; `BattleResources` +
  `ResourceType` lifted flat from `sim/` into `command/`. `BattleSimulation`
  (sim sibling) gained an explicit `command.BattleResources` import — the
  only bare-name casualty of the lift. No self-imports anywhere; 8
  same-package tests relocated. `ops/BattleScreen.java` (objective +
  reinforcement consumer) folded in. Build + suite green.
- **Slice 5 SHIPPED.** First true *split*: `Squad` (from `unit/`),
  `SquadPlan` (from `ai/goap/`), `SquadAlertLevel` (from `ai/`) pulled into
  the existing `squad/` (already home to the 4 Squad*System consumers).
  Moved-file imports done on main thread; the 8 identical left-behind
  sibling imports (`squad.Squad`×2, `squad.SquadPlan`×6) fanned out to a
  Sonnet subagent (verified clean). **Gotcha:** a same-package *test*
  (`ai/goap/PlannerTest`) used `SquadPlan` bare — single-class lifts have no
  test dir to `git mv`, so the relocation glob misses them; the compiler
  caught it. **Always grep bare usages of moved types in `src/test` too.**
  111 files, build + suite green.
- **Proceeded ahead of the facade-drop** because sibling sessions are
  paused (tree quiet). Remaining slices should re-check tree quietness.
- **Note:** a paused sibling agent's worktree under `.claude/worktrees/`
  still references old packages; reconciles on its rebase — not our commits.

## When picking up

1. Re-confirm the tree is quiet (no concurrent large churn).
2. Next is **slice 5 (`squad/` consolidation)** — move `Squad` (from
   `unit/`), `SquadPlan` (from `ai/goap/`), `SquadAlertLevel` (from `ai/`)
   into `squad/`. See `overview.md` § Slice plan. Note: pulling pieces *out*
   of `unit/` and `ai/` (splits, not whole-dir renames) — each moved class
   needs imports for the bare-name siblings it leaves behind.
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
