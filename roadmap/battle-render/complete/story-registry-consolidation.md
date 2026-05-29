# Structural — RenderSystem registry + collect/drain phase split — ✅ SHIPPED

After five passes migrated to `RenderSystem`s (ground/vehicle/doodad/convoy/
shuttle), `renderWorld` still hand-wired each as a `xSystem.collect(rc, drawList)`
immediately followed by `drainLayer(RenderLayer.X)` — five named fields, five
duplicated collect+drain pairs, with the system→layer mapping restated at every
call site. This collapses that into an ordered registry, the structural step
toward the overview's "Final" (a systems-loop + drain).

## What changed

- **`RenderSystem.layer()`** — the interface now declares the `RenderLayer` a
  system feeds, so the system is the single source of truth for its layer (no
  parallel knowledge at the call site). Implemented on all five systems.
- **`BattleRenderer`** — the five named system fields
  (`groundSystem`/`vehicleSystem`/…) are replaced by one ordered
  `List<RenderSystem> worldSystems` built in the constructor in paint order
  (GROUND, VEHICLES, DOODADS, CONVOY, SHUTTLES).
- **`renderWorld` split into two phases:**
  1. **Collect-all** — one loop over `worldSystems` calling `collect`. Collect is
     GL-free and each command is layer-tagged into its own `DrawList` buffer, so
     the order among collects is immaterial (verified: no inline pass between the
     old collect points mutates sim state a later system reads; systems only read
     `sim` + `sprites`).
  2. **Drain** — the existing per-layer `drainLayer(RenderLayer.X)` sequence,
     unchanged, still interleaved with the not-yet-migrated inline passes
     (`renderDecals`, `renderUnits`, `renderRoofs`, `renderDrones`,
     `renderObjectiveMarkers`, the compound/highlight/fog passes, the convoy debug
     overlays) at their exact paint-order slots.

## Behavior-identical by construction

No GL or paint-order change. The only reordering is that all five `collect`s now
run before any inline pass (instead of each collect sitting immediately before its
drain). That's safe because `collect` only appends layer-tagged, GL-free commands
into per-layer buffers — the drain order (= paint order) is untouched, and no
collect depends on output of an interleaved inline pass.

## Endgame

This is the skeleton of the overview's "Final": **collect-all → drain-all**. Today
the drain phase still has explicit per-layer calls interleaved with inline passes.
As each remaining inline pass (DRONES next, then UNITS, …) migrates to a
`RenderSystem`, it joins `worldSystems` and its bespoke drain slot folds into a
single ordered drain loop — until no inline passes remain and `renderWorld` is
just the two loops.

## Verified

`mcp__intellij__build_project` clean; `gradlew test` green. Behavior-neutral;
covered by the next live battle smoke-test (no dedicated re-verify needed — no
render change).
