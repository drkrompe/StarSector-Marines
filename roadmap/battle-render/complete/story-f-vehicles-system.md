# Story F — VEHICLES pass → VehicleRenderSystem — ✅ SHIPPED (in-game verify pending)

Parked map vehicles (`renderVehicles`) migrated into the command model as
`VehicleRenderSystem`, emitting the `VEHICLES` layer.

## What landed

- **`VehicleRenderSystem`** (game-side, mirrors `DoodadRenderSystem`): one
  axis-aligned `SHEET_QUAD` per `MapVehicle` at its footprint center, frame
  aspect-fit into the footprint box (faithful port of the inline math). Drained
  through the strict-painter batch path.
- **Sprite-sheet batch registration** (new): `buildTileBatches` now also builds
  a `QuadBatch` per distinct vehicle sheet and registers it in `batchBySheet`,
  so `SHEET_QUAD`s targeting a vehicle sheet resolve to a batch. Vehicle sheets
  are loaded by `ensureVehicleSheets()` *before* `buildTileBatches()` in
  `BattleScreen.attach()`, so they're present at registration time. This is the
  reusable seam UNITS/DRONES land on next (both are `UnitSpriteCache` sheets).
- `renderWorld` calls `vehicleSystem.collect(...) + drainLayer(VEHICLES)` between
  the DECALS and DOODADS passes (matches `RenderLayer` ordinal order).

## Dropped (batch path makes them unnecessary)

The inline pass mutated the shared sheet sprite per vehicle
(`setTexX/Y/Width/Height`, `setSize`, `setColor`) and reset `setColor(WHITE)` for
every touched sheet at end of pass. The `QuadBatch` path writes per-vertex color
+ UVs into its vertex buffer and binds the texture once on flush — no shared-
sprite state to leak, so the color-reset loop is gone.

## Verified

`mcp__intellij__build_project` clean; `gradlew test` green. **In-game render
verification pending** — confirm parked vehicles draw at the right place/size/
frame, above decals and below doodads/units. The inline `renderVehicles` is
retained `@Deprecated` + **uncalled** as a one-line-rewire rollback; delete once
the live battle confirms parity.

## Notes

- Vehicles are few, so batching is a consistency win more than a perf one; the
  drain still coalesces same-sheet runs. Emission is in vehicle-list order (not
  sheet-grouped) — faithful to the old pass; sheet-grouping is a trivial future
  tweak if a map ever fields many mixed-sheet vehicles.
