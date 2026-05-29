# Story H — SHUTTLES pass → ShuttleRenderSystem — ✅ SHIPPED & VERIFIED

Aircraft (shuttles) + their mounted turrets + engine FX migrated into the
command model as `ShuttleRenderSystem`, emitting the `SHUTTLES` layer. First pass
to combine `SPRITE` and the `CUSTOM` escape hatch in one system.

## Command decomposition (per shuttle, submission = paint order)

1. **Engine FX** → `CUSTOM` — `EngineFxRenderer.draw(...)` manages its own GL, so
   it rides the drain's custom escape hatch (drops out of the textured-quad
   bracket). Emitted first so it paints under the hull.
2. **Hull** → `SPRITE` — whole rotated sprite (`ShuttleSpriteCache.sprite`).
3. **Turrets** (per `MountedTurret`) → optional recoil-displaced barrel `SPRITE`
   then base `SPRITE`, positioned by the mount local-offset × body-rotation and
   the recoil push (faithful port of `renderShuttleTurrets`/`drawTurretLayer`).

All sprites are whole-texture (not sheet sub-rects), so they use the non-batched
`SPRITE` command — **no batch registration needed** (unlike VEHICLES/CONVOY).

## No rotation-parity risk (unlike CONVOY)

The hull/turret rotation goes through the `SPRITE` command
(`DrawListRenderer.drawSprite` → `SpriteAPI.setAngle` + `renderAtCenter`) — the
*exact same* API the inline pass used. So rotation is identical by construction;
the convention question that CONVOY had (which switched to `appendRotated`) does
not arise here. The `SPRITE` command also resets sprite angle after each draw, so
the old end-of-pass `setAngle(0)` reset loop is dropped.

## Shared constants

`RECOIL_DURATION` / `RECOIL_DISTANCE_FRAC` are shared between the map-turret pass
(still inline in `BattleRenderer`) and shuttle turrets, so they stayed in
`BattleRenderer` as the single source of truth and were made package-visible
(`static final`, dropped `private`); `ShuttleRenderSystem` references them.

## Verified

`mcp__intellij__build_project` clean; `gradlew test` green. **In-game render
confirmed in live play** — hulls render with engine FX beneath, turrets at the
right mounts with recoil kick, correct altitude offset, aircraft pierce the
fog-roof. A background critique returned clean (no Critical/Worth-fixing): paint
order, the engine-FX `Custom` GL-state parity, turret kinematics byte-for-byte,
hull sizing, and the dropped `setAngle(0)` reset all confirmed correct. The
inline `renderShuttles` + `renderShuttleEngines` + `renderShuttleTurrets`
fallback has been **deleted** (orphaned `Shuttle` import dropped with it).

## Notes / follow-ups

- The engine-FX `Custom` allocates one lambda per shuttle per frame (sparse
  layer; acceptable — ship-then-optimize). The pooled `CUSTOM` slot also retains
  the lambda + captured `Shuttle`/camera across frames until reused (pure
  retention, zero render impact). If shuttle counts ever spike, hoist to a
  reusable command.
- `drawTurretLayer` was **not** deleted — it is still live, shared by the
  inline map-turret / drone-hub / drone passes (`BattleRenderer` ~592/595/641/
  680). `RECOIL_DURATION`/`RECOIL_DISTANCE_FRAC` likewise stay (shared with the
  map-turret pass).
