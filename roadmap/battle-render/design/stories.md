# Battle Rendering stories

Status: ACTIVE — unqueued extensions and proposed render work.

Written: 2026-08-23

| Story | State | Intent |
|---|---|---|
| `fog-field.md` | PLANNED | Fog draws as one map-sized single-channel texture sampled by one quad, patched from the vision service's per-tick delta, instead of a quad per cell; the per-cell stream stays as the control and the fallback. |
| `dense-render-tiles.md` | PARKED | Add view-resident, invalidatable baked cell tiles for **decals**. Static ground is settled — it is a resident mesh, and the baked-tile answer was measured against and rejected for it. |
| `unified-sprite-registry.md` | PROPOSED | Consolidate duplicate render-tier asset cache lifecycles behind stable path-based asset resolution without putting graphics handles into simulation state. |
| `ground-collector-allocation.md` | PROPOSED | Hoist stable ground-kind mappings and colors out of the per-frame collector after profiling confirms the allocation path matters. |
