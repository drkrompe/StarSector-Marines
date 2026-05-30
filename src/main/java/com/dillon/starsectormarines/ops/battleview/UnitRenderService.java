package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.drone.DroneHubUnit;
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.world.tiles.SpriteSheetFrames;
import com.dillon.starsectormarines.render2d.BattleCamera;

/**
 * Emits the {@link RenderLayer#UNITS} layer — the heavy world pass. Story J's
 * target shape: a stateless consumer that sweeps the unit list <em>once per
 * render-task-kind</em> (footprints, then sprites, then HP bars), branching on a
 * type-flyweight {@link RenderAppearance} + its capability tags instead of an
 * {@code instanceof}/{@code combatant}/{@code deathPoseIdx} ladder. Per-stratum
 * sweeps in paint order dissolve the per-entity decorator ordering trap: "HP bars
 * on top across all kinds" is simply "the bar sweep runs last". See
 * {@code roadmap/battle-render/stories/story-j-units.md}.
 *
 * <p><b>Migration state (slice J4).</b> Live now: the <em>footprint</em> sweep
 * (turret + hub road pads), the turret + hub <em>whole-sprite body</em> sweeps,
 * and the <em>dead-sprite</em> sweep (batched {@code SHEET_QUAD}). Still inline in
 * {@code BattleRenderer.renderUnits}: the live-infantry sprites (J5) and the
 * layer-wide HP-bar pass (J6). {@code renderUnits} drains this layer
 * ({@code drainLayer(RenderLayer.UNITS)}) before its inline live + bar loops, so
 * the full paint order holds: footprints → turret → hub → dead → live → bars.
 *
 * <p><b>Sweep order is paint order.</b> The collect order below <em>is</em> the
 * submission (= paint) order under the strict-painter drain. One intentional
 * refinement vs. the old inline passes: footprints now sweep <em>before all</em>
 * bodies (the inline code interleaved them per structure type — turret pad, turret
 * sprite, hub pad, hub sprite). Pads are ground decals meant to sit under the
 * structures, so a turret sprite overhanging an adjacent hub's pad now correctly
 * paints on top of it.
 */
public final class UnitRenderService implements RenderSystem {

    private final BattleSprites sprites;

    public UnitRenderService(BattleSprites sprites) {
        this.sprites = sprites;
    }

    @Override
    public RenderLayer layer() {
        return RenderLayer.UNITS;
    }

    @Override
    public void collect(RenderContext ctx, DrawList out) {
        sweepFootprints(ctx, out);
        sweepTurretBodies(ctx, out);
        sweepHubBodies(ctx, out);
        sweepDeadSprites(ctx, out);
    }

    /**
     * Ground pads under every live map turret + drone hub, emitted first so they
     * sit under all bodies. Faithful port of the two inline {@code ROAD_FILL}
     * footprint fills (one per structure type), now one {@code SOLID_RECT} per
     * structure via {@link GroundFootprint} — they coalesce into a single
     * {@code SolidQuadBatch} flush in the drain.
     */
    private void sweepFootprints(RenderContext ctx, DrawList out) {
        BattleCamera cam = ctx.camera;
        float cellPx = cam.cellPxSize();
        float alphaMult = ctx.alphaMult;
        for (Unit u : ctx.sim.getUnits()) {
            if (!u.isAlive()) continue;
            if (!RenderAppearance.of(u.type).drawsFootprint) continue;
            float x0 = cam.cellToScreenX(u.getCellX());
            float y0 = cam.cellToScreenY(u.getCellY());
            GroundFootprint.emit(out, RenderLayer.UNITS, x0, y0, cellPx, alphaMult);
        }
    }

    /**
     * Map-turret bodies: an optional recoil-displaced barrel {@code SPRITE} under
     * the base {@code SPRITE} (both whole-texture rotated). Faithful port of the
     * inline turret pass — same recoil easing/offset, same per-{@code TurretKind}
     * base/barrel caches — minus the end-of-pass {@code setAngle(0)} reset loops
     * (the {@code SPRITE} drain resets angle after each draw). When the base sprite
     * is missing, a {@code DEFENDER_COLOR} {@code SOLID_RECT} fallback stands in
     * (and the barrel is skipped), exactly as the inline fallback did.
     */
    private void sweepTurretBodies(RenderContext ctx, DrawList out) {
        BattleCamera cam = ctx.camera;
        float cellPx = cam.cellPxSize();
        float alphaMult = ctx.alphaMult;
        for (Unit u : ctx.sim.getUnits()) {
            if (!(u instanceof MapTurret) || !u.isAlive()) continue;
            MapTurret t = (MapTurret) u;
            float cx = cam.cellToScreenX(t.getCellX() + 0.5f);
            float cy = cam.cellToScreenY(t.getCellY() + 0.5f);

            ShuttleSpriteCache base = sprites.turretSprites().get(t.kind);
            if (base == null) {
                float half = cellPx * BattleRenderer.UNIT_FRAC / 2f;
                out.addSolidRect(RenderLayer.UNITS, cx - half, cy - half, cx + half, cy + half,
                        BattleRenderer.DEFENDER_COLOR.getRed() / 255f,
                        BattleRenderer.DEFENDER_COLOR.getGreen() / 255f,
                        BattleRenderer.DEFENDER_COLOR.getBlue() / 255f, alphaMult);
                continue;
            }

            ShuttleSpriteCache barrel = sprites.turretRecoilSprites().get(t.kind);
            if (barrel != null) {
                float recoilT = 0f;
                if (t.recoilTimer < BattleRenderer.RECOIL_DURATION) {
                    recoilT = 1f - t.recoilTimer / BattleRenderer.RECOIL_DURATION;
                }
                float pushPx = recoilT * BattleRenderer.RECOIL_DISTANCE_FRAC * t.kind.visualCells * cellPx;
                double rad = Math.toRadians(t.facingDegrees);
                float bx = (float) Math.sin(rad) * pushPx;
                float by = -(float) Math.cos(rad) * pushPx;
                emitWholeSprite(out, barrel, t.facingDegrees, t.kind.visualCells, cellPx,
                        cx + bx, cy + by, alphaMult);
            }
            emitWholeSprite(out, base, t.facingDegrees, t.kind.visualCells, cellPx, cx, cy, alphaMult);
        }
    }

    /**
     * Drone-hub bodies: one unrotated whole-texture {@code SPRITE} per live hub.
     * Faithful port of the inline hub pass; the hub sprite is loaded at
     * {@code BattleScreen.attach} (hoisted out of the pass) so this stays GL-free,
     * and is simply skipped if the load failed (the footprints already drew).
     */
    private void sweepHubBodies(RenderContext ctx, DrawList out) {
        ShuttleSpriteCache hub = sprites.droneHubSprite();
        if (hub == null) return;
        BattleCamera cam = ctx.camera;
        float cellPx = cam.cellPxSize();
        float alphaMult = ctx.alphaMult;
        for (Unit u : ctx.sim.getUnits()) {
            if (!(u instanceof DroneHubUnit) || !u.isAlive()) continue;
            float cx = cam.cellToScreenX(u.getCellX() + 0.5f);
            float cy = cam.cellToScreenY(u.getCellY() + 0.5f);
            emitWholeSprite(out, hub, 0f, DroneHubUnit.VISUAL_CELLS, cellPx, cx, cy, alphaMult);
        }
    }

    /**
     * Emits one whole-texture rotated body sprite, sized {@code visualCells} tall
     * (× the sprite's natural aspect wide). Mirrors
     * {@code ShuttleRenderSystem.emitTurretLayer} — the {@code SPRITE} drain owns
     * size/angle/alpha/blend/color and resets angle afterward.
     */
    private static void emitWholeSprite(DrawList out, ShuttleSpriteCache cache, float facingDegrees,
                                        float visualCells, float cellPx, float cx, float cy, float alphaMult) {
        float pxH = visualCells * cellPx;
        float pxW = pxH * cache.aspect;
        out.addSprite(RenderLayer.UNITS, cache.sprite, cx, cy, pxW, pxH, facingDegrees,
                1f, 1f, 1f, alphaMult);
    }

    /**
     * Corpse sweep: dead units with a death pose draw their frozen pose frame as a
     * batched {@code SHEET_QUAD}. Faithful port of the former inline
     * {@code BattleRenderer.renderDeadUnits} — same two gates, same pose-frame
     * selection, same aspect-fit into the {@code renderScale}d cell box, no vision
     * gate (corpses persist through fog), no flip, no HP bar.
     *
     * <p>Two gates, both required: {@link RenderAppearance#hasDeathPose} is the
     * type-level "this type declares a corpse sheet" flag, but {@code deathPoseIdx}
     * is set to a non-negative pose for <em>every</em> dying unit (including
     * null-corpse civilians), so the instance {@code deathPoseIdx >= 0} check is
     * still needed — the flyweight tag does not subsume it. The cache guard then
     * covers the not-yet-loaded / empty-sheet case.
     */
    private void sweepDeadSprites(RenderContext ctx, DrawList out) {
        BattleCamera cam = ctx.camera;
        // Base cell-sprite size shared across UNITS strata; renderScale applied below.
        float unitSize = cam.cellPxSize() * BattleRenderer.UNIT_FRAC;
        float alphaMult = ctx.alphaMult;

        for (Unit u : ctx.sim.getUnits()) {
            if (u.isAlive()) continue;
            if (u.deathPoseIdx < 0) continue;
            RenderAppearance app = RenderAppearance.of(u.type);
            if (!app.hasDeathPose) continue;
            UnitSpriteCache cache = sprites.unitDeadSprites().get(u.type);
            if (cache == null || cache.sheet == null || cache.frames == null
                    || cache.frames.frames.length == 0) continue;

            SpriteSheetFrames frames = cache.frames;
            int frameIdx = ((u.deathPoseIdx % frames.frames.length) + frames.frames.length)
                    % frames.frames.length;
            SpriteSheetFrames.Frame f = frames.frames[frameIdx];

            float scaledSize = unitSize * app.renderScale;
            float targetW, targetH;
            if (f.w >= f.h) {
                targetW = scaledSize;
                targetH = scaledSize * f.h / (float) f.w;
            } else {
                targetH = scaledSize;
                targetW = scaledSize * f.w / (float) f.h;
            }
            float cx = cam.cellToScreenX(u.getRenderX() + 0.5f);
            float cy = cam.cellToScreenY(u.getRenderY() + 0.5f);
            out.addSheetQuad(RenderLayer.UNITS, cache.sheet,
                    f.x, f.y, f.w, f.h,
                    cx, cy, targetW, targetH,
                    1f, 1f, 1f, alphaMult);
        }
    }
}
