# Moddable tilesets — Next Session

Read [`overview.md`](overview.md) first (concept, the three tile systems,
the data/algorithm seam, both schemas). **No active story** — Phase 1 and
Phase 2 are shipped and sealed in `complete/`. The only
remaining track work is **Phase 3 (mod-merge), deferred** until a real
submod exists (see overview Non-goals + slice table).

## State of play — Phases 1 + 2 COMPLETE ✅ (verified)

Both phases are functionally complete, behavior-preserving, and gradle-green.
Full per-slice ledgers (commit hashes + what landed vs. planned) live in the
sealed stories:

- [`complete/phase-1-tile-registry.md`](complete/phase-1-tile-registry.md) —
  id-addressed `TileRegistry`; sliced sheets fully data-driven
  (`NatureTile`/`UrbanTile3` **deleted**); all four grid sheets flipped to
  id-addressed autotile/variant blocks; per-cell labels folded into the
  tileset JSON; test-only `TileManifest` pickers retired.
- [`complete/phase-2-doodad-pools.md`](complete/phase-2-doodad-pools.md) —
  `GenMappingRegistry` + `*.mapping.json` carrying all three sections:
  `doodadPools` + cover (`DoodadDef`), `groundRender` (GroundKind→block +
  generic `GroundRenderSystem.drawGroundBlock`), and `fillers` params
  (`FillerParams` — `NatureZoneFiller` pools/chances).

**Verification (2026-06-28):** the two handoff-flagged parity tests pass —
`gradlew :test --tests "*FillerParamsParityTest*" --tests "*GroundRenderMappingTest*"`
→ BUILD SUCCESSFUL (tree compiles clean; the sibling-refactor red that was
present at the `2d37595a` commit has cleared).

## Commit chain

```
Phase 1
  91ee3f2   docs: new track — dual-JSON, id-addressed TileRegistry
  99de776   Phase 1a — id-addressed TileRegistry (sliced sheets)
  2859234   Phase 1a critique fixes — pin id→frame, fail-loud guards
  1b308ba   Phase 1b step i — dense tile index on the registry
  fa36eb3a  Phase 1b step ii — consumers read TileDef via registry
  51841174  Phase 1b step v — delete NatureTile/UrbanTile3 enums
  6d30f529  Phase 1c foundation — GridLayout/GridBlockDef + blocks ingest
  7fba02f4  Phase 1c — urban-tileset render flip
  5a9402d2  Phase 1c — urban-tileset-2 (road sheet) flip
  0e1df6c9  Phase 1c — Floors/Water grounds → variant-pool blocks
  ab6e98ac  Phase 1c cleanup — fold per-cell catalogs into tileset JSON
  bfe76d9e  Phase 1c — retire test-only TileManifest pickers + oracle
Phase 2
  fda40a33  sub-slice 1 — doodad defs + pools as data (additive)
  259a9b8e  sub-slices 2-3 — doodads fully data-driven; retire defaultCoverFor
  c78501b4  cover-gap fix (chairs/desks=MED, shelves=HEAVY)
  fdd757c8  GroundKind render dispatch as data + generic draw path
  2d37595a  NatureZoneFiller pools/chances as data (filler params)
```

## Carry-forward design items (don't lose)

Open, none blocking — naturally land with Phase 3 (mod-facing hardening) or
the optional Phase 2 extensions:

- **`validOn` exclusion is id-only — no `!layer:`/`!tag:` form.** "Non-water
  ground" can only be expressed by enumerating `!<id>` tokens, so a third
  water tile would force hand-editing every rock's `validOn`. Revisit with a
  tile `tag`/kind. (Tolerable today: two water tiles.)
- **No strict unknown-key validation.** A typo'd field key defaults silently
  rather than failing → Phase 3 mod-facing diagnostics; disproportionate for
  built-in sheets now.
- **Optional Phase 2 extensions:** a `"filler"` dispatch field naming the code
  filler per `BlockKind`; other fillers' scatter tunables (e.g.
  `UrbanMapGenerator` doodad chances); resolver doodads (turret embankments /
  LZ markers) as data if a submod ever needs to reskin them.

## Decisions locked (don't relitigate)

- Dual JSON, not one (tileset def vs. mapping have different lifecycles).
- Id-addressed registry is the linchpin; landed first (kills enum-order-=-PNG-
  order + hardcoded `(col,row)`).
- `TileRegistry.installed()` / `GenMappingRegistry.installed()` process-wide
  singletons (`Global.*`-shaped), NOT GenContext DI; tests install a
  disk-loaded registry via an auto-registered JUnit extension.
- Mapping JSON names code fillers; carve algorithms stay in Java. **No
  scripting layer.**
- `.catalog.json` promoted in place (folded into each `.tileset.json`), the
  in-game catalog editor deleted; `TilesetDebugScreen` is a read-only viewer.
- Nests under `GenRecipe` (recipe = stage order; mapping = tile/param content).

## Phase 3 (when a real submod exists)

Mod-merge load order, id-override/extend semantics, cross-mod id collisions,
strict-schema validation/diagnostics. Deferred per
[[feedback_ship_then_optimize]] — built when a second consumer exists, not
speculatively. No story doc yet (authored when the work is real).

> **Concurrent-session friction (recurring):** another session's drone/turret/
> sim refactor has repeatedly left `battle/` main OR its test files
> non-compiling. When `compileTestJava` is blocked by THEIR test files
> (`DroneCrashSystemTest`/`HubDemolitionSystemTest`/`TurretDemolitionSystemTest`),
> run with `--init-script` excluding those files (see
> [[concurrent-session-broken-test-workaround]]); don't touch their files.
> Verify our own files via IntelliJ `get_file_problems` when gradle is wedged.
