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
c693c27  battle-reorg: slice 6a — engine/scoring/world + dispatch + tactical → decision/
52817d7  battle-reorg: slice 6b — actor behaviors → infantry/mech/drone/turret
0198331  battle-reorg: split merged package/import lines from slice 6b move
882586a  battle-reorg: slice 6c — partition goals/actions; ai/ fully dissolved
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
- **Slice 6a SHIPPED.** First sub-slice of the `ai/` dissolve. Framework
  core out of `ai/`/`tactical/` → new `decision/`: GOAP engine (Action,
  ActionStatus, Goal, Planner, Predicate, WorldState) → `decision/goap/`;
  `scoring/`+`world/` → `decision/goap/{scoring,world}/`; dispatch infra
  (UnitUpdateSystem, UnitBehavior, TacticalScoring, AttackerIndexService) +
  the two role-agnostic dispatch behaviors (FallbackBehavior, FleeBehavior)
  → `decision/` flat; `tactical/` graph → `decision/` flat (dissolved).
  **`ai/` now holds only** actor behaviors + the 3 `Goap*Behavior`
  composers + `goals/`/`actions/` (for 6b/6c). Key gotchas: (1) **no
  blanket `ai.goap.` prefix rewrite** — `actions/`/`goals/` stay, so rewrite
  only the 6 specific engine class FQNs + the `scoring.`/`world.`
  subpackage prefixes; (2) **subpackage refs were already-existing imports**
  the FQN rewrite fixed — only *same-package* bare refs needed new imports
  (the 3 composers ← engine; staying `ai/` behaviors ← `decision.UnitBehavior`
  /`TacticalScoring`; moved `UnitUpdateSystem`/`TacticalScoring` ← staying
  `ai/` classes); (3) **javac cascade-masks** bare-ref errors round-to-round
  — needed 3 full `--rerun-tasks` compiles to surface the whole set; (4) the
  package-rewrite perl dropped the blank line after `package` in each moved
  file (cosmetic, left as-is). 158 files, 32 renames, build + suite green.
- **Slice 6b SHIPPED** (`52817d7`). Actor behaviors + their 3 GOAP
  composers distributed out of `ai/`: `infantry/` (new) ← CombatantBehavior,
  InfantryCohesion, InfantryUnitPrep, KitRetrieverBehavior,
  GoapInfantryBehavior; `mech/` (new) ← MechCombatantBehavior,
  GoapMechBehavior; `drone/` ← DroneHubBehavior, GoapDroneBehavior; `turret/`
  ← TurretBehavior, TurretAim, StructureBehavior. Mapped the cross-ref
  surface up front (only 4 cross-destination bare refs needed new imports —
  compiled green first try, no cascade iteration). Staying goals/actions
  redirected purely via their imports. `ai/` now holds **only**
  `goap/{actions,goals}` — 6c finishes the dissolve.
- **Slice 6c SHIPPED** (`882586a`) — **slice 6 / the `ai/` dissolve is
  COMPLETE; `ai/` no longer exists.** Goals partitioned by composer
  (infantry 12 / mech 4 / drone 1). Actions per the **lean-engine choice**
  (user): postures stay infantry-owned, only shared/framework-consumed
  actions are built-ins. `decision/goap/action/` built-ins = `BreakContact`
  (cross-slice) + `ClearZone`/`EnterZone`/`HoldZone` (constructed by
  framework `ZoneQueries`); infantry 13 / mech 4 / drone 1. Used a
  **newline-safe** package rewrite (`s/^package [^;]+;/.../`) so the moved
  files kept their blank-after-package — no merge regression like 6b's
  `0198331` fix. Compiled green first try (action interconnections were
  almost all javadoc, not code). Verified no framework→feature *code* edges.
- **FOLLOW-UP logged** (see `overview.md` open items): the `decision/`
  built-in zone actions carry javadoc `{@link}` to infantry postures — a
  soft doc-level framework→feature coupling, deferred.
- **Proceeded ahead of the facade-drop** because sibling sessions are
  paused (tree quiet). Remaining slices should re-check tree quietness.
- **Note:** a paused sibling agent's worktree under `.claude/worktrees/`
  still references old packages; reconciles on its rebase — not our commits.

## When picking up

1. Re-confirm the tree is quiet (no concurrent large churn).
2. Next is **slice 7 (`weapons/` slice split)** — `Marine*` →
   `infantry/`, `Mech*` → `mech/` (the combat-shared half of `weapons/`
   already moved to `combat/` in slice 1). `infantry/` and `mech/` now
   exist (from slice 6b/6c) so these are lifts into established packages.
   See `overview.md` § Slice plan items 7–10. After 7: slice 8 (`entity/`
   rename, optional — largest reference surface), slice 9 (`sim/`+`setup/`
   tidy), slice 10 (tail: `equipment/`→`infantry/`, `flyby/`, `LosCache`).
3. **Recipe refinements learned in slice 6** (apply going forward):
   - For a package *split into multiple destinations*, drive the FQN rewrite
     from an explicit `name→destpkg` map (class-specific `\b`-bounded
     substitutions), NEVER a blanket prefix rewrite — sibling subpackages may
     stay.
   - Use the **newline-safe** package rewrite `s/^package [^;]+;/package X;/`
     (NOT `s/^package .*;\s*$/.../`, which eats the trailing newline and
     merges `package` onto the first import when there's no blank line — cost
     6b the `0198331` fix-up).
   - `javac` **cascade-masks** missing-symbol errors round-to-round; map the
     same-package bare-ref surface up front (grep movers' cross-refs) so you
     fix imports in one pass instead of N compile cycles.
   - Only *same-package* bare refs need new imports after a move; *subpackage*
     refs were already imports the FQN rewrite repoints automatically.
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
