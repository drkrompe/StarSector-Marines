# Elevation — Open Stories

Status: ACTIVE

Written: 2026-10-22

`elevation-nouns.md` is the canonical model. Read it in full before any of these
stories. The grid model and pathing (tiers, faces, ramps, closed cliff edges,
the elevation plan and brush) have landed (`shipped.md`).

| Story | State and dependency |
| --- | --- |
| elevation-sight-fire | **IN PROGRESS.** One rule for the fog's shadowcast, pair line of sight and the direct-fire ray: sees down freely, sees up only to the rim (skyline). Rim cover only against lower shooters; a cliff foot gets no cover from fire off the top. Fire does not cross a face. |
| elevation-look | **PLANNED.** Faces drawn with the natural terrain's face materials in the face cells; tops a step lighter; tier heights in the macro-relief field so the sun casts plateau shadows; ramp art. Measured with `renderEvidence` against its control. |
| elevation-generation | **PLANNED.** Countryside plateaus with ramps facing the roads in `OutdoorLandscapeStage`; an authored test map. |
| elevation-scene | **PLANNED.** A behaviour scene: a squad takes a held plateau by its ramp; verdicts assert the route used the ramp and that sight followed the rule. Path cost measured against a flat control. |

Owner decisions pending (picture cards through the coordinating session): the
sight rule (skyline / strict / flat), firing up (none / accuracy penalty), the
swarm climbing cliffs (no / slowly / a climbing strain), and the cliff look
(face width, material, top tint).
