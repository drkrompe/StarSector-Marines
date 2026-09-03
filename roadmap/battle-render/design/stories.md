# Battle Rendering stories

Status: ACTIVE — unqueued extensions and proposed render work.

Written: 2026-08-23

| Story | State | Intent |
|---|---|---|
| `units-atlas-resident-roofs.md` | IN PROGRESS | The two ceilings after the ground: composed unit bodies as sheet quads from one atlas, and roof quads resident and patched from the topology's change log. |
| `dense-render-tiles.md` | PARKED | Add view-resident, invalidatable baked cell tiles for **decals**. Static ground is settled — it is a resident mesh, and the baked-tile answer was measured against and rejected for it. |
| `unified-sprite-registry.md` | PROPOSED | Consolidate duplicate render-tier asset cache lifecycles behind stable path-based asset resolution without putting graphics handles into simulation state. |
| `ground-collector-allocation.md` | PROPOSED | Hoist stable ground-kind mappings and colors out of the per-frame collector after profiling confirms the allocation path matters. |
