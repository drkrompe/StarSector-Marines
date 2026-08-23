# Battle Rendering stories

Status: ACTIVE — unqueued extensions and proposed render work.

Written: 2026-08-23

| Story | State | Intent |
|---|---|---|
| `dense-render-tiles.md` | PARKED | Add view-resident, invalidatable baked cell tiles for static ground and decals only when profiling or map growth makes the current per-cell path a measured ceiling. |
| `unified-sprite-registry.md` | PROPOSED | Consolidate duplicate render-tier asset cache lifecycles behind stable path-based asset resolution without putting graphics handles into simulation state. |
| `ground-collector-allocation.md` | PROPOSED | Hoist stable ground-kind mappings and colors out of the per-frame collector after profiling confirms the allocation path matters. |
