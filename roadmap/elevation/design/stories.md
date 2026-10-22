# Elevation — Open Stories

Status: ACTIVE

Written: 2026-10-22

`elevation-nouns.md` is the canonical model. Read it in full before any of these
stories. The `high-ground` behaviour scene, generation (plateaus in the outdoor landscape, asked for per mission),
the grid model and pathing (tiers, faces, ramps, closed cliff edges,
the elevation plan and brush) and sight and fire (one skyline rule for the fog, line of sight and the fire ray; face cover by tier; fire stopped at a face) have landed (`shipped.md`).

| Story | State and dependency |
| --- | --- |
| elevation-look | **IN PROGRESS.** Faces, stacked second tier, ramp floors and tier heights in the relief field have landed (the `elevation` snapshot suite). The owner kept the cliff tiles with tall south faces. Left: the ramp look (owner pick pending between graded earth, a worn track and cut steps, all with walls that taper up the cut), carrying it into the ground, the tiles' stepped south faces (a fault in `ElevationLayout.tilted`), ramp floor art that reads as a slope rather than a pad (the relief already slopes it), ground cover left standing on faces, and a `renderEvidence` measurement against its control. |

Owner decisions (2026-10-22): skyline sight, no uphill penalty, no climbing
swarm, one-cell cliff tiles with tall south faces. Pending: the ramp look, and
whether firing down reveals a holder back from the rim (picture cards through
the coordinating session).
