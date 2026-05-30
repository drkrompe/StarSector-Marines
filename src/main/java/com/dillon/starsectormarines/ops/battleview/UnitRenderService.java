package com.dillon.starsectormarines.ops.battleview;

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
 * <p><b>Migration state (slice J3).</b> Only the <em>dead-sprite</em> sweep is
 * live here — the simplest sprite case (frame-sheet sub-rect, no facing flip, no
 * bar), batched via {@code SHEET_QUAD}. The other strata (turret/hub footprints +
 * whole-sprites, live infantry, HP bars) still draw inline from
 * {@code BattleRenderer.renderUnits}, which now calls
 * {@code drainLayer(RenderLayer.UNITS)} at the exact dead-units slot so paint
 * order is preserved (turrets → hubs → <b>dead</b> → live → bars). They fold into
 * this service in J4–J6; the inline fallback is deleted in J7.
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
        sweepDeadSprites(ctx, out);
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
        float unitSize = cam.cellPxSize(); // UNIT_FRAC == 1f
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
