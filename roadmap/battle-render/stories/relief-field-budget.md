# The relief composite is what is left of a whole-map frame

Status: PLANNED — named by `renderEvidence` the moment the ground stopped being
the answer.

Written: 2026-09-02

## What the measurement says

With the resident ground mesh on, a whole-map frame of the canonical 560x336
Conquest costs 159 ms, of which the GROUND layer is 147. The mesh removed the
188,000 base-terrain quads from the command stream entirely — GROUND now
collects 22,000 commands and drains 504 draw calls — so almost none of that 147
ms is the ground being submitted any more.

Turning the relief composite off says exactly what it is. Four runs of
`renderEvidence`, whole-map on the Conquest map, frame ms with the GPU waited
on:

| | relief on | relief off |
|---|--:|--:|
| ground mesh off | 203.5 | 55.8 |
| ground mesh on | 159.5 | 14.9 |

The mesh is worth 45 ms with the composite on and 41 ms with it off; the
composite is worth about 140 ms either way. With both levers pulled the ceiling
is no longer the ground at all — it is FOG, at 4.2 ms of a 9.8 ms frame.

## Where the cost is

`GroundParallaxPipeline.renderGround` runs four passes: the colour drain into an
FBO, a height field, a normal field, and the composite. The height and normal
passes are the shape of cost the ground has just stopped paying — each rebuilds
its field as **a quad per visible cell, every frame**, and at whole-map on this
map that is 188,000 cells twice. `renderHeightFbo` says why it is rebuilt per
frame rather than cached: a roof caves in and a wall is breached mid-battle, and
a field held across frames would keep shadowing a building that is no longer
there. That is a correct reason for invalidation and not a reason for a full
rebuild, which is precisely the argument the ground mesh answered.

## The candidates, in the order they should be measured

1. **A framing gate on the composite** (law 19). Parallax bump relief at 3.4
   pixels per cell is sub-pixel by definition. The complication is that the same
   composite carries the sun's terrain shading, which does read at that framing —
   so this is not the free gate it looks like, and what it costs the picture has
   to be looked at rather than argued about. `createSnapshots -Psnapshot=sun-shadows`
   is the instrument.
2. **Resident height and normal fields**, patched from the same
   `CellTopology` change log the ground mesh reads. The invalidation the per-frame
   rebuild exists for is already recorded, cell by cell.
3. **A coarser field.** Height and normal are inputs to a lighting march, not
   the picture; whether either needs a texel per cell at every framing is a
   question nobody has asked.

## Acceptance

- `renderEvidence` reports the whole-map Conquest frame under 30 ms with the
  chosen lever on, measured against its own control run.
- `createSnapshots -Psnapshot=sun-shadows` is unchanged at its authored zooms,
  or the difference is looked at and accepted deliberately.
