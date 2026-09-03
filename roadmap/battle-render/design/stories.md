# Battle Rendering stories

Status: ACTIVE — unqueued extensions and proposed render work.

Written: 2026-08-23

| Story | State | Intent |
|---|---|---|
| `relief-field-budget.md` | PLANNED | The relief composite is what is left of a whole-map frame once the ground is resident: its height and normal fields are rebuilt as a quad per visible cell every frame. |
| `dense-render-tiles.md` | PARKED | Add view-resident, invalidatable baked cell tiles for **decals**. Static ground is settled — it is a resident mesh, and the baked-tile answer was measured against and rejected for it. |
| `unified-sprite-registry.md` | PROPOSED | Consolidate duplicate render-tier asset cache lifecycles behind stable path-based asset resolution without putting graphics handles into simulation state. |
| `ground-collector-allocation.md` | PROPOSED | Hoist stable ground-kind mappings and colors out of the per-frame collector after profiling confirms the allocation path matters. |
