# Story — Per-turret LoS for air mounts (sim/render-synced positions)

> Shared-core member of the [`air/`](../overview.md) category — builds on the
> shipped [`global-pixel-density-scale.md`](global-pixel-density-scale.md)
> (hulls are now large enough that body-center LoS is wrong).

## Shipped — `6f0d586`, in-game verification pending

Landed exactly as designed below. `Shuttle` gained a cached `turretSpread()`
plus shared `turretWorldX/Y(mount, cos, sin, extraScale)`; `AirSystem` positions
sim mounts through them (`extraScale = 1`, ground-real) so `originCellX/Y` spread
across the hull and the already-per-mount `TurretAim` LoS (`canSeePair` /
`airLosVisible`) differentiates front from rear for free; `ShuttleRenderSystem`
uses the same helper (`extraScale = scaleMult` + altitude Y-offset) so a round
fires from where the turret is drawn. Turret size now scales by `turretSpread`
too. No new LoS code — just real per-mount inputs.

**Outstanding:** in-game eyeball of the front-vs-rear divergence on a long
shuttle straddling a wall (full `gradlew build` blocked at commit time by an
unrelated concurrent-session `ShotRenderService` error; edited files verified
clean via IDE inspection bar pre-existing javadoc-link noise in `Shuttle`).
`SHUTTLE_AIR_LOS_RADIUS = 3.5` is a fixed cell radius — revisit if it reads
wrong relative to a very large hull.

## Goal

A long ship's **front** turrets should be able to engage while its **rear**
turrets are still obstructed by a wall — each mount acquires, checks line of
sight, and applies "see through nearby walls" from **its own** position on the
hull, not from one body-center point shared by the whole ship.

## The key realization — the aim loop is already per-turret

`AirSystem.tickShuttleTurrets` already builds a **fresh `TurretAim.State` per
mount** and floors *that mount's* world position into `originCellX/originCellY`
(`AirSystem.java:283-285`). `TurretAim.tick` then does acquisition
(`scoring.findBestTarget`), the range check, the LoS check (`canSeePair` /
`airLosVisible`), and the close-wall transparency pass **all from that per-mount
origin**. So the per-turret LoS *mechanism* exists today — front and rear mounts
already get independent `State`s.

**What defeats it is the turret position, not the LoS code:**

1. **Sim positions ignore the hull-relative spread.** `AirSystem` computes the
   mount's world position from the **raw** authored offset —
   `float lx = mt.mount.localOffsetX;` (`AirSystem.java:273`) — with no
   `scaleMult` and (since the pixel-density work) no `turretScale`. The renderer
   now draws the same mount at `localOffsetX * scaleMult * turretScale`
   (`ShuttleRenderSystem.emitTurrets`). Two consequences:
   - **Sim/render desync** ([[air_unit_render_sync]]): rounds originate from a
     clustered center while the turret *sprites* sit spread across the hull.
     Pre-existing for the `scaleMult` (altitude) factor; the param-aware
     `turretScale` widened it to ~3× on a Valkyrie.
   - **Per-turret LoS collapses to body-center.** Raw offsets are tiny
     (±0.6 cells), so every mount floors to ~the same cell as the body — the
     independent `State`s all trace LoS from one point. The mechanism is there;
     the inputs are degenerate.

So this story is **not** "build per-turret LoS." It's "**give each sim turret its
real hull-relative position** so the per-turret LoS already in `TurretAim`
actually differentiates front from rear."

## Design

### 1. One shared turret-offset helper (sim + render)

Extract the hull-relative world offset into a single function both callers use,
so they can never drift again:

```
worldOffset(mount, facingCos, facingSin, turretSpread) // body-relative, in cells
  lx = mount.localOffsetX * turretSpread
  ly = mount.localOffsetY * turretSpread
  → rotate (lx,ly) by facing
```

where `turretSpread = HullFootprintResolver.visualLengthCells(renderHullId) /
AirScale.TURRET_AUTHORING_HULL_CELLS` — the same factor `ShuttleRenderSystem`
already computes. Cache the resolved hull length on the `Shuttle` (resolve once,
not per tick) rather than calling the resolver inside the aim loop.

- **Sim (`AirSystem`)** uses `turretSpread` only — the turret genuinely sits that
  far out on the hull; this is a sim-real position.
- **Render (`ShuttleRenderSystem`)** uses `turretSpread`, then additionally
  applies `scaleMult` (the altitude visual zoom) and the altitude Y-offset — both
  are render-only, consistent with how the shot origin already nudges Y for
  altitude (`AirSystem.java:307-313`) while sim LoS uses the ground projection.

### 2. Per-turret LoS then "just works"

With sim mounts spread across the hull, the existing `TurretAim` per-mount
`State` does the rest: a front mount near the nose and a rear mount near the
tail floor to **different** cells, so `findBestTarget` + `canSeePair` +
`airLosVisible` run independently per mount. Front engages, rear stays blocked —
no new LoS code, just real inputs. Mounts can even lock **different** targets
based on their own sight lines.

### 3. Close-wall radius is already per-mount

`ignoreCloseWalls` + `closeWallRadius` (`SHUTTLE_AIR_LOS_RADIUS = 3.5`) are set
on each per-mount `State`, so the "fire over the building I'm above"
transparency is centered on each turret once positions spread. No change needed;
flag the 3.5-cell value as a tuning knob for very large hulls (it's a fixed cell
radius, so it shrinks *relative* to a 172-cell hull).

## Implementation slices

1. **Cache hull length on `Shuttle`** (resolve `HullFootprintResolver` once) +
   add the shared `worldOffset(mount, cos, sin, spread)` helper in `battle/air/`.
2. **`AirSystem`**: position sim mounts via the helper with `turretSpread` →
   `originCellX/Y` now spread across the hull. Shot origin X/Y follow the same
   helper (keeping the existing altitude Y-nudge for render origin).
3. **`ShuttleRenderSystem`**: route `emitTurrets` through the same helper
   (× `scaleMult` + altOffset on top) so sim and render share one definition.
4. **Verify** front-vs-rear divergence: a long shuttle straddling a wall fires
   its exposed mounts while the blocked mounts hold.

## Out of scope

- Ground `MapTurret`s — single position, body-center LoS is already correct.
- Ships' concave-hull collision footprint (separate ships story).
- Flyby fighters (`battle/flyby/`) — slated for replacement by the fighters track.
- Real altitude-Z / camera-Z — render-layer track; this keeps the
  sim=ground-projection, render=altitude-adjusted split unchanged.

## Done when

- Sim turret origins match the rendered turret positions (one shared helper),
  spread across the hull by `turretSpread`.
- A long shuttle's front turrets engage through a gap while rear turrets, still
  behind a wall, hold fire — observed in-game.
- Story moves to `roadmap/air/complete/`.
