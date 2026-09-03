# Battle Rendering stories

Status: ACTIVE — unqueued extensions and proposed render work.

Written: 2026-08-23

| Story | State | Intent |
|---|---|---|
| `collect-culling.md` | IN PROGRESS | Cull at collection: a collector visits the bodies and cells the camera can see, plus a margin from the largest sprite extent. Fixes `renderEvidence`'s cross-run non-reproducibility first. |
| `dense-render-tiles.md` | PARKED | Add view-resident, invalidatable baked cell tiles for **decals**. Static ground is settled — it is a resident mesh, and the baked-tile answer was measured against and rejected for it. |
| `unified-sprite-registry.md` | PROPOSED | Consolidate duplicate render-tier asset cache lifecycles behind stable path-based asset resolution without putting graphics handles into simulation state. |
| `ground-collector-allocation.md` | PROPOSED | Hoist stable ground-kind mappings and colors out of the per-frame collector after profiling confirms the allocation path matters. |
