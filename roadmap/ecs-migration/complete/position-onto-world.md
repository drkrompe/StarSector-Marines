# Position onto the EntityWorld — the corpse keeps its cell by lifecycle (step 3b)

**Shipped 2026-06-03** — `b92c8bd`. Full suite green at 760, first compile
after the fan-out.

Second live capability on the archetype engine
([`archetype-storage.md`](../archetype-storage.md) migration step 3). The
cell pair (cellX/cellY) left `UnitRegistry`'s dense int arrays for the world's
`POSITION` component; the live archetype is now `{IDENTITY, POSITION, HEALTH}`.

## What landed

- **Spawn**: `allocate` seeds the cell into the world `POSITION` columns from
  `seedCellX/Y`; the registry's dense cell arrays + by-index accessors +
  `cellXArray()/cellYArray()` bulk views are **deleted**. Transitional by-id
  adapters: `cellXById` / `cellYById` / `setCellPosById` (same shape and fate
  as the hp adapters). `World` facade cell surface unchanged for its callers.
- **Death**: `POSITION` persists alive→dead, so **"the corpse keeps its cell"
  is now the component's own lifecycle** — the death transmute's row-move
  carries it. `DeadBodySystem`'s corpse-add mask dropped `POSITION` and the
  death-cell re-write is gone (the event snapshot is the same value by
  construction: nothing moves a unit after the kill zeroes its hp; demolition
  / wreck handlers still read the cell off the `DeathEvent`).
- **Movement**: `Entity.advanceAlongPath` reads/writes the cell by id;
  `moveProgress` stays a dense registry column.
- **Consumer sweep (~85 sites / 20 files)** via 4 Sonnet agents on disjoint
  buckets with the compiler-backstop pattern (`65a61f1` precedent):
  `TacticalScoring` alone (~55 sites, 19 methods), combat+infantry,
  squad/nav/vision/turret/air/sim, spatial indexes+render. Per-site rule:
  resolved-index reads → by-id of the source id (index locals kept only where
  other dense columns still need them); dense-loop reads → by-id of the
  in-scope entity; bulk array captures deleted. `UnitSpatialIndex` /
  `UnitDestinationSpatialIndex` keep their internal snapshot arrays — only
  the fill-reads changed. Existing hoisting shapes preserved (per-method
  `sx/sy` locals, `resolveThreatColumns` projections), so the per-candidate
  hot loops still run on locals, with probes only at the hoist points.

## Perf note

Dense-loop cell reads are now one world location-probe per unit instead of a
raw array read — the accepted step-back
([[feedback_storage_foundation_build_right]]), bounded at N≈200 and recovered
later when bulk consumers walk world tables directly (blocked today on mixed
storage: those loops also read registry-side columns by dense index).

## Follow-ups

- Next capabilities: Combat group (granularity question — one fat Combat vs
  primary/secondary/burst split — is still open in the design doc), Movement,
  AiState; then fold `Crashing`/`MechLoadout` into archetype membership.
