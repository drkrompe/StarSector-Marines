# Elevation — Open Stories

Status: ACTIVE

Written: 2026-10-22

`elevation-nouns.md` is the canonical model. Read it in full before any of these
stories. The `high-ground` behaviour scene, generation (plateaus in the outdoor landscape, asked for per mission),
the grid model and pathing (tiers, faces, ramps, closed cliff edges,
the elevation plan and brush) and sight and fire (one skyline rule for the fog, line of sight and the fire ray; face cover by tier; fire stopped at a face) have landed (`shipped.md`).

| Story | State and dependency |
| --- | --- |
| elevation-look | **IN PROGRESS.** Grass-topped cliffs draw in the live renderer as the owner's layered escarpment with cut stone ramp steps, baked once per battle (`CliffArtLayer`; `renderEvidence -PrenderMaps=overlook` against `-Dbattle.render.cliffLook=false`). A shot down from higher ground exposes the shooter for three seconds (`FiringExposure`, the `firing-down` scene). Left: the stepped south faces (a fault in `ElevationLayout.tilted`), and why the cliffs cast no sun shadow in the evidence frames. |

Owner decisions (2026-10-22): skyline sight, no uphill penalty, no climbing
swarm, one-cell cliff tiles with tall south faces, cut stone ramp steps, and a
holder that fires down revealed for about three seconds, and layered-escarpment
cliff edges.
