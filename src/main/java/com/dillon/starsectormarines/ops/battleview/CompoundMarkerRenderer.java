package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.PolyMesh;
import com.dillon.starsectormarines.render2d.PolyTess;
import com.dillon.starsectormarines.ui.BitmapFont;
import com.dillon.starsectormarines.ui.Fonts;

import java.awt.Color;
import java.util.EnumMap;
import java.util.Map;

/**
 * World-layer producer for compact compound capture beacons. Stable objectives
 * remain subdued; only a contested compound gains a progress readout and a
 * restrained realtime pulse. The marker is anchored to the tactical node's
 * visual centroid while command and capture continue to use the resolved cell.
 */
public final class CompoundMarkerRenderer {

    private static final float RING_THICKNESS_PX = 2f;
    private static final float ARC_GAP_PX = 2.5f;
    private static final float ARC_THICKNESS_PX = 3f;
    private static final float TICK_LENGTH_PX = 4f;
    /**
     * How far past the ring a marker's own text and ticks reach, for
     * {@link ViewCull}.
     *
     * <p>The status line sits three pixels above the ring at Orbitron 12 and a
     * status word is a few dozen pixels wide; forty covers both ends of it and
     * the ticks, and a marker rejected by mistake here loses a whole objective
     * label rather than a pixel of one.
     */
    private static final float LABEL_REACH_PX = 40f;
    private static final int ARC_SEGMENTS = 32;
    private static final int RING_SEGMENTS = 40;
    private static final Color CORE = new Color(0x08, 0x10, 0x16);

    private final BitmapFont font = Fonts.ORBITRON_12_BOLD;
    private final PolyMesh fillMesh = new PolyMesh(512);
    private float wallClock;

    /** Realtime animation continues while the simulation is paused. */
    public void update(float dtRealtime) {
        wallClock += dtRealtime;
    }

    /** Collect is GL-free; bitmap labels execute when the command list drains. */
    public void collect(BattleSimulation sim, CompoundService service,
                        BattleCamera camera, DrawList out, float alphaMult) {
        if (service == null || sim == null || camera == null
                || service.getRecords().isEmpty()) return;

        float cellPx = camera.cellPxSize();
        ViewCull view = ViewCull.of(camera);
        // The ring, its status line above it and its code inside it, as one
        // screen-space span. A marker's radius is clamped in pixels, so its
        // size in cells is a different number at every framing and the
        // screen-space test is the one that means anything here.
        float markerPx = (BattlefieldMarkerPresentation.objectiveRadius(cellPx)
                + LABEL_REACH_PX) * 2f;
        fillMesh.reset();
        Map<TacticalNode.Kind, Integer> ordinals =
                new EnumMap<>(TacticalNode.Kind.class);
        for (CompoundService.Record record : service.getRecords()) {
            // The ordinal is what names a marker -- KEEP 1, KEEP 2 -- so it is
            // counted for every record on the field whether or not this one is
            // on screen. A cull that changed the numbering would rename half
            // the objectives every time the camera moved.
            int ordinal = ordinals.merge(record.node.kind, 1, Integer::sum);
            if (!view.visibleScreen(centerX(camera, record), centerY(camera, record),
                    markerPx)) continue;
            BattlefieldMarkerPresentation.ObjectiveMarker marker =
                    BattlefieldMarkerPresentation.capture(record.node.kind, ordinal,
                            record.state, record.captureProgress);
            float cx = centerX(camera, record);
            float cy = centerY(camera, record);
            float pulse = BattlefieldMarkerPresentation.pulse(
                    wallClock, marker.emphasized());
            float ringOuter = BattlefieldMarkerPresentation.objectiveRadius(cellPx) * pulse;
            float ringInner = ringOuter - RING_THICKNESS_PX;

            appendAnnulus(fillMesh, cx, cy, 0f, ringInner - 1f,
                    CORE, 0.72f * alphaMult);
            appendAnnulus(fillMesh, cx, cy, ringInner, ringOuter,
                    marker.tone(), marker.opacity() * alphaMult);
            if (marker.emphasized() && marker.progress() > 0f) {
                float arcOuter = ringInner - ARC_GAP_PX;
                PolyTess.appendArc(fillMesh, cx, cy,
                        arcOuter - ARC_THICKNESS_PX, arcOuter,
                        marker.progress(), ARC_SEGMENTS,
                        BattlefieldMarkerPresentation.PROGRESS.getRed() / 255f,
                        BattlefieldMarkerPresentation.PROGRESS.getGreen() / 255f,
                        BattlefieldMarkerPresentation.PROGRESS.getBlue() / 255f,
                        alphaMult);
            }
        }
        if (!fillMesh.isEmpty()) out.addPoly(RenderLayer.COMPOUND, fillMesh);

        ordinals.clear();
        for (CompoundService.Record record : service.getRecords()) {
            int ordinal = ordinals.merge(record.node.kind, 1, Integer::sum);
            if (!view.visibleScreen(centerX(camera, record), centerY(camera, record),
                    markerPx)) continue;
            BattlefieldMarkerPresentation.ObjectiveMarker marker =
                    BattlefieldMarkerPresentation.capture(record.node.kind, ordinal,
                            record.state, record.captureProgress);
            emitTicks(out, centerX(camera, record), centerY(camera, record),
                    BattlefieldMarkerPresentation.objectiveRadius(cellPx),
                    marker.tone(), marker.opacity() * alphaMult);
        }

        out.addCustom(RenderLayer.COMPOUND, () -> {
            font.ensureLoaded();
            Map<TacticalNode.Kind, Integer> labelOrdinals =
                    new EnumMap<>(TacticalNode.Kind.class);
            for (CompoundService.Record record : service.getRecords()) {
                int ordinal = labelOrdinals.merge(record.node.kind, 1, Integer::sum);
                float cx = centerX(camera, record);
                float cy = centerY(camera, record);
                if (!view.visibleScreen(cx, cy, markerPx)) continue;
                BattlefieldMarkerPresentation.ObjectiveMarker marker =
                        BattlefieldMarkerPresentation.capture(record.node.kind, ordinal,
                                record.state, record.captureProgress);
                drawCentered(marker.code(), cx,
                        cy + font.getLineHeight() * 0.42f,
                        BattlefieldMarkerPresentation.LABEL, 1f, alphaMult);
                if (marker.emphasized()) {
                    drawCentered(marker.status(), cx,
                            cy - BattlefieldMarkerPresentation.objectiveRadius(cellPx) - 3f,
                            marker.tone(), 0.76f, alphaMult);
                }
            }
        });
    }

    private void drawCentered(String text, float cx, float y, Color color,
                              float scale, float alpha) {
        float width = font.measureWidth(text) * scale;
        font.drawStringScaled(text, cx - width * 0.5f, y,
                scale, scale, color, alpha);
    }

    private static void appendAnnulus(PolyMesh mesh, float cx, float cy,
                                       float inner, float outer, Color color,
                                       float alpha) {
        PolyTess.appendAnnulus(mesh, cx, cy, inner, outer, RING_SEGMENTS,
                color.getRed() / 255f, color.getGreen() / 255f,
                color.getBlue() / 255f, alpha);
    }

    private static void emitTicks(DrawList out, float cx, float cy, float radius,
                                  Color color, float alpha) {
        float inner = radius + 2f;
        float outer = inner + TICK_LENGTH_PX;
        float r = color.getRed() / 255f;
        float g = color.getGreen() / 255f;
        float b = color.getBlue() / 255f;
        out.addLine(RenderLayer.COMPOUND, cx - outer, cy, cx - inner, cy,
                1.5f, r, g, b, alpha);
        out.addLine(RenderLayer.COMPOUND, cx + inner, cy, cx + outer, cy,
                1.5f, r, g, b, alpha);
        out.addLine(RenderLayer.COMPOUND, cx, cy - outer, cx, cy - inner,
                1.5f, r, g, b, alpha);
        out.addLine(RenderLayer.COMPOUND, cx, cy + inner, cx, cy + outer,
                1.5f, r, g, b, alpha);
    }

    private static float centerX(BattleCamera camera, CompoundService.Record record) {
        TacticalNode node = record.node;
        return camera.cellToScreenX((node.left + node.right + 1) * 0.5f);
    }

    private static float centerY(BattleCamera camera, CompoundService.Record record) {
        TacticalNode node = record.node;
        return camera.cellToScreenY((node.top + node.bottom + 1) * 0.5f);
    }
}
