package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.air.AirAppearance;
import com.dillon.starsectormarines.battle.air.AirCrashImpact;
import com.dillon.starsectormarines.battle.air.Airframe;
import com.dillon.starsectormarines.battle.air.engine.HullFootprintResolver;
import com.dillon.starsectormarines.battle.air.engine.HullPivotResolver;
import com.dillon.starsectormarines.render2d.BattleCamera;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Shared source-strip rendering before and after the crashed entity is reaped. */
public final class HullWreckRenderer {
    private static final Map<AirCrashImpact, HullBreakup> TEARS = Collections.synchronizedMap(new WeakHashMap<>());
    private HullWreckRenderer() {}

    public static void emit(DrawList out, BattleCamera cam, ShuttleSpriteCache cache, Airframe airframe,
                            AirCrashImpact impact, float nowSeconds, float alphaMult) {
        if (cache == null || cache.sprite == null) return;
        float length = HullFootprintResolver.visualLengthCells(airframe.renderHullId()) * AirAppearance.GROUND_SCALE;
        float alongPx = length * cam.cellPxSize(), acrossPx = alongPx * cache.aspect;
        float[] pivot = HullPivotResolver.pivotOffset(airframe.renderHullId());
        double rad = Math.toRadians(impact.facingDegrees());
        float faceCos = (float) Math.cos(rad), faceSin = (float) Math.sin(rad);
        float baseX = impact.x() + (pivot[0] * faceCos - pivot[1] * faceSin) * AirAppearance.GROUND_SCALE;
        float baseY = impact.y() + (pivot[0] * faceSin + pivot[1] * faceCos) * AirAppearance.GROUND_SCALE;
        HullBreakup breakup = TEARS.computeIfAbsent(impact, key -> HullBreakup.of(key.seed()));
        ViewCull view = ViewCull.of(cam);
        for (HullBreakup.Piece piece : breakup.pieces()) {
            HullBreakupMotion.Pose pose = HullBreakupMotion.pose(impact, piece, impact.age(nowSeconds), length);
            float x = baseX + pose.x(), y = baseY + pose.y();
            // Cull each independently moving section, not the vanished actor's final anchor.
            if (!view.visible(x, y, length * Math.max(1f, cache.aspect) * 2f)) continue;
            float cx = cam.cellToScreenX(x), cy = cam.cellToScreenY(y);
            if (cache.pxW <= 0 || cache.pxH <= 0) {
                // Legacy hosts without source dimensions cannot address the partition.
                if (piece == breakup.pieces().get(0))
                    out.addSprite(RenderLayer.UNITS, cache.sprite, cx, cy, acrossPx, alongPx,
                            impact.facingDegrees(), .35f, .25f, .2f, alphaMult);
                continue;
            }
            double spin = Math.toRadians(pose.spinDegrees());
            float centreX = (piece.centreAcross() - .5f) * acrossPx;
            float centreY = (.5f - piece.centreAlong()) * alongPx;
            for (HullBreakup.Run run : piece.runs())
                emitWreckRun(out, cache, run, pose.spinDegrees(), acrossPx, alongPx,
                        centreX, centreY, (float) Math.cos(spin), (float) Math.sin(spin),
                        cx, cy, faceCos, faceSin, impact.facingDegrees(), alphaMult);
        }
    }

    private static float bleed(float extent) { return 2f * Math.min(.75f, extent * .08f); }
    private static void emitWreckRun(DrawList out, ShuttleSpriteCache cache,
                                     HullBreakup.Run run, float spinDegrees,
                                     float acrossPx, float alongPx,
                                     float centreX, float centreY,
                                     float spinCos, float spinSin,
                                     float baseX, float baseY, float faceCos, float faceSin,
                                     float facing, float alphaMult) {
        int srcX = Math.round(run.firstCol() * cache.pxW / (float) HullBreakup.GRID);
        int srcRight = Math.round((run.firstCol() + run.colCount()) * cache.pxW / (float) HullBreakup.GRID);
        int srcY = Math.round(run.row() * cache.pxH / (float) HullBreakup.GRID);
        int srcBottom = Math.round((run.row() + 1) * cache.pxH / (float) HullBreakup.GRID);
        if (srcRight <= srcX || srcBottom <= srcY) return;

        // Destination extents come from the rounded source rect, not from the
        // ideal lattice, so a sprite whose pixels do not divide evenly by the
        // grid is still drawn at its own scale rather than stretched to fit.
        float u0 = srcX / (float) cache.pxW;
        float u1 = srcRight / (float) cache.pxW;
        float v0 = srcY / (float) cache.pxH;
        float v1 = srcBottom / (float) cache.pxH;
        float localX = ((u0 + u1) * 0.5f - 0.5f) * acrossPx;
        float localY = (0.5f - (v0 + v1) * 0.5f) * alongPx;

        // Turn about the section centre in the impact frame. Translation is already
        // included in baseX/baseY by the independently sampled section pose.
        float aboutX = localX - centreX;
        float aboutY = localY - centreY;
        float spunX = aboutX * spinCos - aboutY * spinSin + centreX;
        float spunY = aboutX * spinSin + aboutY * spinCos + centreY;
        float screenX = baseX + spunX * faceCos - spunY * faceSin;
        float screenY = baseY + spunX * faceSin + spunY * faceCos;

        float stripAcross = (u1 - u0) * acrossPx;
        float stripAlong = (v1 - v0) * alongPx;
        out.addSheetQuad(RenderLayer.UNITS, cache.sprite, srcX, srcY, srcRight - srcX, srcBottom - srcY,
                screenX, screenY,
                stripAcross + bleed(stripAcross), stripAlong + bleed(stripAlong),
                facing + spinDegrees,
                .35f, .25f, .2f, alphaMult);
    }

}
