# Nature variant-pool authority cleanup

Status: PROPOSED

Written: 2026-08-23

Read `moddable-tilesets-nouns.md` before implementing this story.

## Problem

Primary grass and dirt rendering resolves stable tile ids, but the membership
of each two-item variant pool still lives in
`TileManifest.pickNatureGrassTileId` and
`TileManifest.pickNatureDirtTileId`. Changing those content choices therefore
requires Java even though the tileset model assigns content membership to data.

This is narrower than removing every `TileManifest` picker. Sidewalk selection
is a topology-owned rule that already returns semantic ids, and the wall picker
is an explicit degraded path used when no registry is installed.

## Scope

- Declare the primary grass and dirt variant pools in tileset or mapping data.
- Make the color and micro-height render paths resolve the same declared pool.
- Retire the two production nature picker methods once no consumer needs them.
- Preserve the existing bundled-content output exactly.

## Constraints

- Keep the coordinate hash and its current result for bundled content.
- Do not add RNG draws or change seeded generation behavior.
- Do not fold topology-owned sidewalk selection into data.
- Preserve the registry-unavailable wall fallback.
- An empty or unresolved declared pool must have one explicit failure or
  degraded rule; it must not silently fall back to coordinates or enum order.

## Acceptance

- [ ] Primary grass and dirt rendering selects only from a declared content
  pool, including the matching `GroundMicroHeightSampler` path.
- [ ] No production call remains to `pickNatureGrassTileId` or
  `pickNatureDirtTileId`.
- [ ] Existing coordinates choose the same bundled tile ids before and after
  the cleanup, with unchanged RNG consumption.
- [ ] Invalid or empty pool data follows the documented failure/degraded rule.
- [ ] Sidewalk selection and the registry-unavailable wall fallback remain
  behaviorally unchanged.

## Plan

1. Choose the narrow schema home for named sliced-tile variant pools and add
   the bundled grass/dirt declarations.
2. Add registry resolution that preserves declared order and rejects unknown
   members.
3. Route both color and micro-height selection through the shared resolver.
4. Remove the obsolete nature picker methods and update focused parity tests.
