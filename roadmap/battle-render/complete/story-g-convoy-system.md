# Story G — CONVOY pass → ConvoyRenderSystem — ✅ SHIPPED (in-game verify pending)

Ground convoy vehicles (`renderConvoyVehicles`) migrated into the command model
as `ConvoyRenderSystem`, emitting the `CONVOY` layer. First pass to use
**rotated** sheet-quads.

## Engine extension — rotation on SHEET_QUAD

Convoy trucks face along their heading (chassis + turret), so this needed a
rotated sheet sub-rect — which neither `SHEET_QUAD` (axis-aligned `append`) nor
`SPRITE` (rotates but no sub-rect) covered. Added, engine-side:

- `DrawCommand.setSheetQuad(..., angleDeg, ...)` overload (the no-angle overload
  now delegates with `angleDeg = 0`, so pooled-slot reuse never carries a stale
  angle into a non-rotated quad).
- `DrawList.addSheetQuad(..., angleDeg, ...)` overload.
- `DrawListRenderer` routes `angleDeg != 0` through `QuadBatch.appendRotated`
  (already present) and keeps the cheap axis-aligned `append` for `angleDeg == 0`
  — so the dense GROUND/DOODADS layers pay no trig. Rotated + unrotated quads
  still batch together on one sheet.

This is reused by UNITS/DRONES (also rotated sheet sprites).

## ConvoyRenderSystem

Faithful port of the inline chassis + turret kinematics (mount/pivot world-space
rotation, aspect-fit sizing). One rotated chassis quad per visible vehicle, plus
a second rotated quad for the turret when `turretFrame >= 0`. Reuses the
sprite-sheet batch-registration seam from Story F (now a shared
`registerSpriteSheetBatches` helper fed both vehicle and convoy sheets).

The debug overlays the old method dispatched — Reeds-Shepp docking paths
(`DEBUG_RENDER_DOCKING_PATHS`, default on) and selected-vehicle debug — are
own-GL line passes; they stay inline in `renderWorld` after the CONVOY drain
(they're debug-only, not core paint order). Migrating them to `Custom` is a
future cleanup if wanted.

## Verified

`mcp__intellij__build_project` clean; `gradlew test` green. **In-game render
verification pending — and this slice has a real parity risk to check:** the
rotation now goes through `QuadBatch.appendRotated` (manual CCW corner rotation)
instead of `SpriteAPI.setAngle`. Confirm convoy chassis + turrets point the
right way (not mirrored / off by a sign) and sit at the right mount position,
above ground and below shuttles. The inline `renderConvoyVehicles` is retained
`@Deprecated` + **uncalled** as a one-line-rewire rollback; delete once the live
battle confirms parity.

## Notes

- If appendRotated's convention turns out to differ from `setAngle`, the fix is
  localized: negate `angleDeg` (or add a 90° offset) at the two emit sites, or
  add a setAngle-matching rotation to the drain. Verify before deleting the
  fallback.
