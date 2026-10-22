# Elevation — Open Stories

Status: ACTIVE

Written: 2026-10-22

`elevation-nouns.md` is the canonical model. Read it in full before any of these
stories. The `high-ground` behaviour scene, generation (plateaus in the outdoor landscape, asked for per mission),
the grid model and pathing (tiers, faces, ramps, closed cliff edges,
the elevation plan and brush) and sight and fire (one skyline rule for the fog, line of sight and the fire ray; face cover by tier; fire stopped at a face) have landed (`shipped.md`).

| Story | State and dependency |
| --- | --- |
| elevation-look | **IN PROGRESS.** Faces, stacked second tier, ramp floors and tier heights in the relief field have landed (the `elevation` snapshot suite). Left: the owner's pick between the cliff tiles and gradient shading with scree and ramp steps (the `elevation-look` study; the tiles' tall south faces step, a fault in `ElevationLayout.tilted`), carrying the pick into the ground composite, ramp floor art that reads as a slope rather than a pad (the relief already slopes it), ground cover left standing on faces, and a `renderEvidence` measurement against its control. |

Owner decisions pending (picture cards through the coordinating session): the
sight rule (skyline / strict / flat), firing up (none / accuracy penalty), the
swarm climbing cliffs (no / slowly / a climbing strain), and the cliff look
(face width, material, top tint).
