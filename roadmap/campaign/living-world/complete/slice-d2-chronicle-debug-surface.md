# Slice D2c — Chronicle debug surface

**Status:** CODE COMPLETE (2026-08-12)

**Implemented:** `55eefe4f`

## Goal

Make learned political history inspectable without committing yet to the final
newspaper UI or procedural prose layer.

## Locked rules

- Campaign debug intel includes Chronicle count and a newest-first learned-event
  section capped at 100 visible rows.
- The existing local-system toggle filters dispatches by their snapshotted
  market, matching house and contract inspection behavior.
- Each row exposes band, terminal outcome, actor, target, industry, market,
  happened day, and learned day.
- Missing mutable house/registry references degrade to stable id/slot labels
  rather than hiding or crashing an old dispatch.

## Automated verification

- `CampaignDebugIntelChronicleTest` covers named formatting and all fallback
  labels. Full build verifies the Starsector intel API integration.
