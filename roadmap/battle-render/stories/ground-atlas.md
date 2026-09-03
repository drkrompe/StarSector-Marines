# Ground atlas and resident decoration

Status: IN PROGRESS

Written: 2026-09-02

## Where it stands

`renderEvidence` on the canonical 560x336 Conquest, whole-map framing, every
resident lever on:

| | commands | sheet quads | draws | binds | collect ms | drain ms |
|---|--:|--:|--:|--:|--:|--:|
| `GROUND` | 22,335 | 21,407 | 504 | 501 | 1.68 | 3.90 |

That is 5.58 ms of the 7.81 that are ours, and 71% of the frame's own cost. The
base terrain is already resident (`GroundMesh`, five buffers, 187,970 quads),
the relief fields are resident, and the fog is a field. What is left in `GROUND`
is the sparse decoration the mesh deliberately does not hold — fills, crosswalk
stripes, nature scatter, doorway decals, window panes and shared-edge features —
and it leaves as five hundred draws across five hundred texture binds, because
the batcher must flush every time the sheet changes in painter order.

## The two levers

1. **A runtime ground atlas** (`battle.render.groundAtlas`). At battle load,
   composite every sheet the `GROUND` layer can draw from into one GL texture
   and have the ground quads — resident and sparse alike — address it, so a
   whole layer is one bind. Painter order is untouched; only the bind count
   changes. Fails soft to the per-sheet path.
2. **Resident sparse decoration** (`battle.render.residentDecoration`).
   Whatever in that 22k stream is a pure function of topology joins the mesh as
   further sub-layers in painter order, patched from the same change log.

## Acceptance

- `renderEvidence` on both maps at all three framings, each lever measured
  against its own run's control: `GROUND` under 1.5 ms at whole-map 560x336
  with both on; close and mid not worse.
- GL pixel-equality evidence under `shaderEvidence` for each lever, following
  `ReliefFieldGlEvidence` / `FogFieldGlEvidence`.
- `createSnapshots -Psnapshot=sun-shadows,layers,frontage-scene,perception-sweep,ship-decks`
  byte-identical to a pre-change control set.
- `verifyModJarBoundary`, `:test` and `shaderEvidence` green.
