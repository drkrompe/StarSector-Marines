package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.squad.FrontageLine;
import com.dillon.starsectormarines.battle.ui.highlight.HighlightOverlay;
import com.dillon.starsectormarines.battle.ui.highlight.HighlightOverlay.LineMark;
import com.dillon.starsectormarines.battle.ui.highlight.HighlightOverlay.PlaceMark;
import com.dillon.starsectormarines.render2d.BattleCamera;

import java.awt.Color;

/**
 * A line order on the ground: the drawn frontage as a stroke with ticks toward its front, and,
 * while the drag is still being drawn, a dashed ring where each marine will stand, tethered to
 * the marine. Rings rather than cells because the question a preview answers is where the men
 * go, and a ring at a man's feet is the shape the selection already uses for a man.
 *
 * <p>A ring on a position (a trench, or a building's door or window post) is green; one on open
 * ground is pale, so a line that runs out of positions shows where it does.
 */
public final class OrderLineRenderSystem implements RenderSystem {

    /** Cells between the facing ticks along a drawn line. */
    private static final float TICK_SPACING = 3f;
    /** How far a facing tick reaches toward the front, in cells. */
    private static final float TICK_LENGTH = 1.1f;
    private static final float LINE_WIDTH = 2.2f;
    private static final float RING_RADIUS_CELLS = 0.46f;
    private static final int RING_SEGMENTS = 20;

    @Override public RenderLayer layer() { return RenderLayer.HIGHLIGHTS; }

    @Override
    public void collect(RenderContext ctx, DrawList out) {
        HighlightOverlay overlay = ctx.highlights;
        BattleCamera camera = ctx.camera;
        if (overlay == null || camera == null || ctx.sim == null) return;
        Color tone = HighlightOverlay.COLOR_SQUAD_MOVE_DESTINATION;
        float r = tone.getRed() / 255f, g = tone.getGreen() / 255f, b = tone.getBlue() / 255f;
        float alpha = ctx.alphaMult;
        for (LineMark mark : overlay.lineMarks()) {
            FrontageLine line = mark.line();
            stroke(out, camera, line.pointAt(0f, 0f), line.pointAt(1f, 0f), LINE_WIDTH, r, g, b, 0.9f * alpha);
            int ticks = Math.max(1, Math.round(line.length() / TICK_SPACING));
            for (int i = 0; i <= ticks; i++) {
                float t = (float) i / ticks;
                stroke(out, camera, line.pointAt(t, 0f), line.pointAt(t, TICK_LENGTH), 1.6f, r, g, b, 0.8f * alpha);
            }
            for (PlaceMark place : mark.places()) {
                float cx = camera.cellToScreenX(place.x() + 0.5f);
                float cy = camera.cellToScreenY(place.y() + 0.5f);
                if (ctx.sim.world().isAlive(place.member())) {
                    out.addLine(RenderLayer.HIGHLIGHTS,
                            camera.cellToScreenX(ctx.sim.world().renderX(place.member())),
                            camera.cellToScreenY(ctx.sim.world().renderY(place.member())),
                            cx, cy, 1f, 0.85f, 0.92f, 1f, 0.28f * alpha);
                }
                dashedRing(out, cx, cy, RING_RADIUS_CELLS * camera.cellPxSize(), place.position(), alpha);
            }
        }
    }

    /** A stroke between two points given in cells, as {@link FrontageLine#pointAt} returns them. */
    private static void stroke(DrawList out, BattleCamera camera, float[] from, float[] to, float width,
                               float r, float g, float b, float a) {
        out.addLine(RenderLayer.HIGHLIGHTS,
                camera.cellToScreenX(from[0] + 0.5f), camera.cellToScreenY(from[1] + 0.5f),
                camera.cellToScreenX(to[0] + 0.5f), camera.cellToScreenY(to[1] + 0.5f),
                width, r, g, b, a);
    }

    private static void dashedRing(DrawList out, float x, float y, float radius, boolean position, float alpha) {
        float r = position ? 0.45f : 0.86f;
        float g = position ? 1f : 0.92f;
        float b = position ? 0.55f : 1f;
        for (int i = 0; i < RING_SEGMENTS; i += 2) {
            double a = i * Math.PI * 2 / RING_SEGMENTS;
            double next = (i + 1) * Math.PI * 2 / RING_SEGMENTS;
            out.addLine(RenderLayer.HIGHLIGHTS,
                    x + radius * (float) Math.cos(a), y + radius * (float) Math.sin(a),
                    x + radius * (float) Math.cos(next), y + radius * (float) Math.sin(next),
                    1.8f, r, g, b, 0.9f * alpha);
        }
    }
}
