package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveProjection;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.PolyMesh;
import com.dillon.starsectormarines.render2d.PolyTess;
import com.dillon.starsectormarines.ui.Fonts;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;

/** Public objective readouts, above atmosphere so night cannot obscure the next action. */
public final class MissionMarkerRenderSystem implements RenderSystem {
    private final PolyMesh mesh = new PolyMesh(1024);
    private static final float WIDTH = 308;
    /** Below this many pixels a cell, the map is an overview: cards shrink to names and decorations fade. */
    static final float OVERVIEW_CELL_PX = 16;
    /** How far a card stays clear of the edge indicators parked against the HUD rail. */
    private static final float EDGE_CLEARANCE = 48;
    private static final Color PAPER = new Color(229, 227, 215);
    private static final Color MUTED = new Color(180, 188, 185);

    @Override public RenderLayer layer() { return RenderLayer.MISSION_MARKERS; }

    @Override public void collect(RenderContext ctx, DrawList out) {
        collect(MissionObjectiveProjection.capture(ctx.sim), ctx, out);
    }

    /** Also used by controlled presentation fixtures; it never advances mission state. */
    public void collect(MissionObjectiveSnapshot picture, RenderContext ctx, DrawList out) {
        mesh.reset();
        var camera = ctx.camera;
        var labels = new ArrayList<float[]>();
        float alpha = ctx.alphaMult;
        boolean overview = camera.cellPxSize() < OVERVIEW_CELL_PX;
        float decoration = overview ? .3f : .6f;
        for (var item : picture.objectives()) for (var marker : item.markers()) {
            boolean focus = marker.id().equals(item.focusMarkerId());
            boolean active = marker.state() == MissionObjectiveSnapshot.State.ACTIVE;
            float rawX = camera.cellToScreenX(marker.x() + .5f);
            float rawY = camera.cellToScreenY(marker.y() + .5f);
            boolean onScreen = rawX >= camera.vpX() && rawX <= camera.vpX() + camera.vpW()
                    && rawY >= camera.vpY() && rawY <= camera.vpY() + camera.vpH();
            // Off camera, only the mission's own open steps keep an edge indicator; a side objective is a Locate away.
            if (!onScreen && (!focus || !active || item.optional())) continue;
            boolean blocked = focus && active && !item.blocker().isBlank();
            Color tone = marker.state() == MissionObjectiveSnapshot.State.COMPLETE ? BattlefieldMarkerPresentation.VALID
                    : marker.state() == MissionObjectiveSnapshot.State.FAILED ? BattlefieldMarkerPresentation.INVALID
                    : blocked ? BattlefieldMarkerPresentation.CONTESTED
                    : item.optional() ? BattlefieldMarkerPresentation.OPTIONAL : BattlefieldMarkerPresentation.PROGRESS;
            float r = tone.getRed() / 255f, g = tone.getGreen() / 255f, b = tone.getBlue() / 255f;
            if (marker.radius() > 0) {
                float radiusPx = marker.radius() * camera.cellPxSize();
                PolyTess.appendAnnulus(mesh, rawX, rawY, Math.max(0, radiusPx - .75f), radiusPx + .75f,
                        64, r, g, b, decoration * alpha);
            }
            if (focus && active && !marker.footprint().isEmpty()) {
                var cells = new HashSet<>(marker.footprint());
                for (var cell : cells) {
                    float left = camera.cellToScreenX(cell.x()), right = camera.cellToScreenX(cell.x() + 1);
                    float bottom = camera.cellToScreenY(cell.y()), top = camera.cellToScreenY(cell.y() + 1);
                    if (!cells.contains(new MissionObjectiveSnapshot.Cell(cell.x() - 1, cell.y())))
                        out.addLine(layer(), left, bottom, left, top, 1.5f, r, g, b, decoration * alpha);
                    if (!cells.contains(new MissionObjectiveSnapshot.Cell(cell.x() + 1, cell.y())))
                        out.addLine(layer(), right, bottom, right, top, 1.5f, r, g, b, decoration * alpha);
                    if (!cells.contains(new MissionObjectiveSnapshot.Cell(cell.x(), cell.y() - 1)))
                        out.addLine(layer(), left, bottom, right, bottom, 1.5f, r, g, b, decoration * alpha);
                    if (!cells.contains(new MissionObjectiveSnapshot.Cell(cell.x(), cell.y() + 1)))
                        out.addLine(layer(), left, top, right, top, 1.5f, r, g, b, decoration * alpha);
                }
            }
            float x = clamp(rawX, camera.vpX() + WIDTH / 2 + 8, camera.vpX() + camera.vpW() - WIDTH / 2 - 8);
            float y = clamp(rawY, camera.vpY() + 128, camera.vpY() + camera.vpH() - 60);
            // An edge indicator never sits under the HUD rail.
            if (!onScreen) x = uncovered(camera, x, y + 12, 20);
            // The fixed-size diamond is a locator, never an invented capture radius; a side objective's is a ring,
            // so the map reads "must" apart from "may".
            if (item.optional()) PolyTess.appendAnnulus(mesh, x, y, 7, 10, 24, r, g, b, alpha);
            else PolyTess.appendAnnulus(mesh, x, y, 9, 12, 4, r, g, b, alpha);
            if (focus && item.progress() >= 0 && active)
                PolyTess.appendArc(mesh, x, y, 15, 18, Math.min(1, item.progress()), 40, r, g, b, alpha);
            if (blocked) {
                out.addLine(layer(), x - 3, y - 4, x - 3, y + 4, 2, r, g, b, alpha);
                out.addLine(layer(), x + 3, y - 4, x + 3, y + 4, 2, r, g, b, alpha);
            }
            if (onScreen && (x != rawX || y != rawY))
                out.addLine(layer(), x, y, rawX, rawY, 1, r, g, b, alpha * .65f);
            if (!onScreen) {
                float dx = rawX - x, dy = rawY - y;
                float length = Math.max(1, (float) Math.hypot(dx, dy));
                out.addLine(layer(), x, y, x + dx / length * 32, y + dy / length * 32, 2, r, g, b, alpha);
            }
            List<String> details = new ArrayList<>();
            String status = focus && active && !item.progressText().isBlank() ? item.progressText() : marker.status();
            // An overview card is the name alone, unless it is the open step or off camera.
            if (!overview || (focus && active) || !onScreen) details.add((onScreen ? "" : "OFF SCREEN / ") + status);
            boolean expanded = !overview && focus && active
                    && (!item.id().startsWith("territory-") || camera.cellPxSize() >= 12)
                    && (blocked || !item.instruction().equals(status));
            if (expanded) details.addAll(Fonts.INSIGNIA_15_AA.wrapLines(
                    blocked ? item.blocker() : item.instruction(), WIDTH - 20));
            var title = Fonts.ORBITRON_12_BOLD.wrapLines(marker.name(), WIDTH - 20);
            boolean bar = focus && item.progress() >= 0 && !overview;
            float height = 10 + title.size() * 16 + details.size() * 17 + (bar ? 9 : 0);
            // The rule and bar span the card's own text, never the full card slot.
            float half = 0;
            for (String line : title) half = Math.max(half, Fonts.ORBITRON_12_BOLD.measureWidth(line));
            for (String line : details) half = Math.max(half, Fonts.INSIGNIA_15_AA.measureWidth(line));
            half = Math.min(WIDTH, half + 16) / 2;
            float top = y - 23;
            float beaconX = x;
            // Alternate above/below, then move by full card heights; keep every label in view and out from under the
            // HUD rail, stepping a card sideways away from the rail when the column is full.
            for (int attempt = 0; attempt < 12; attempt++) {
                top = clamp(top, camera.vpY() + height + 8, camera.vpY() + camera.vpH() - 8);
                // Wide enough to clear the edge indicators parked against the rail too.
                x = uncovered(camera, x, top, half + EDGE_CLEARANCE);
                boolean overlaps = false;
                for (var prior : labels) if (Math.abs(prior[0] - x) < prior[3] + half + 6
                        && top > prior[1] - prior[2] - 5 && top - height < prior[1] + 5) {
                    overlaps = true;
                    top = prior[1] + height + 6;
                    if (top > camera.vpY() + camera.vpH() - 8) {
                        top = prior[1] - prior[2] - 6;
                        float step = prior[3] + half + 8;
                        float right = x + step;
                        x = clamp(uncovered(camera, right, top, half + EDGE_CLEARANCE) == right ? right : x - step,
                                camera.vpX() + half + 8, camera.vpX() + camera.vpW() - half - 8);
                    }
                    break;
                }
                if (!overlaps) break;
            }
            if (x != beaconX) out.addLine(layer(), x, top, beaconX, y, 1, r, g, b, alpha * .65f);
            labels.add(new float[]{x, top, height, half});
            // Battle glass: no panel behind the card, so units beneath it stay visible;
            // the text carries the HUD's shadow halo to read on any ground.
            if (!overview) out.addLine(layer(), x - half, top, x + half, top, 2, r, g, b, alpha);
            float textY = top - 5;
            for (String line : title) {
                out.addCustom(layer(), new WorldLabel(Fonts.ORBITRON_12_BOLD, line, x, textY, PAPER, alpha,
                        WorldLabel.GLASS_HALO));
                textY -= 16;
            }
            for (int i = 0; i < details.size(); i++) {
                out.addCustom(layer(), new WorldLabel(Fonts.INSIGNIA_15_AA, details.get(i), x, textY,
                        blocked || i == 0 ? tone : MUTED, alpha, WorldLabel.GLASS_HALO));
                textY -= 17;
            }
            if (bar) {
                float left = x - half + 9, right = x + half - 9, bottom = top - height + 5;
                out.addSolidRect(layer(), left, bottom, right, bottom + 3, .18f, .21f, .2f, alpha);
                out.addSolidRect(layer(), left, bottom, left + (right - left) * clamp(item.progress(), 0, 1), bottom + 3,
                        r, g, b, alpha);
            }
        }
        if (!mesh.isEmpty()) out.addPoly(layer(), mesh);
    }

    /** Moves a card centred on {@code x} left of the HUD rail when its top edge reaches under it. */
    static float uncovered(BattleCamera camera, float x, float top, float half) {
        if (top < camera.coverBottom() || x + half + 8 < camera.coverLeft()) return x;
        return Math.max(camera.vpX() + half + 8, camera.coverLeft() - half - 8);
    }

    private static float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }
}
