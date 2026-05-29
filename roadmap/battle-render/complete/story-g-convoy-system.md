# Story G — CONVOY pass → ConvoyRenderSystem — ✅ SHIPPED & VERIFIED

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

`mcp__intellij__build_project` clean; `gradlew test` green. **Rotation parity
confirmed** — a background critique proved (in code) that `appendRotated` matches
`SpriteAPI.setAngle` in sign and zero: `appendRotated` at θ=0 is byte-identical
to `append`, and its `(+cos/−sin, +sin/+cos)` CCW matrix is the same one
`TurretAuthorPanel` pairs with `setAngle` to *define* the turret mount/pivot
offsets (so the offsets are correct under it), corroborated by the SHOTS pass
(`atan2−90` → `setAngle`, same 0°=art-up reference) drawing in the same
`renderWorld` ortho space. Confirmed in live play. The inline
`renderConvoyVehicles` fallback has been **deleted**.

## Notes

- The only thing not provable from code is the absolute handedness of Starsector's
  undocumented `SpriteAPI.setAngle` — but that's the convention the entire
  existing render path already stands on, so it's settled in practice.
