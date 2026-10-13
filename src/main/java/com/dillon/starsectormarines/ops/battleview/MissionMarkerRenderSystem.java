package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveProjection;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot;
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
        for (var item : picture.objectives()) for (var marker : item.markers()) {
            boolean focus = marker.id().equals(item.focusMarkerId());
            boolean active = marker.state() == MissionObjectiveSnapshot.State.ACTIVE;
            float rawX = camera.cellToScreenX(marker.x() + .5f);
            float rawY = camera.cellToScreenY(marker.y() + .5f);
            boolean onScreen = rawX >= camera.vpX() && rawX <= camera.vpX() + camera.vpW()
                    && rawY >= camera.vpY() && rawY <= camera.vpY() + camera.vpH();
            if (!onScreen && (!focus || !active)) continue;
            boolean blocked = focus && active && !item.blocker().isBlank();
            Color tone = marker.state() == MissionObjectiveSnapshot.State.COMPLETE ? BattlefieldMarkerPresentation.VALID
                    : marker.state() == MissionObjectiveSnapshot.State.FAILED ? BattlefieldMarkerPresentation.INVALID
                    : blocked ? BattlefieldMarkerPresentation.CONTESTED : BattlefieldMarkerPresentation.PROGRESS;
            float r = tone.getRed() / 255f, g = tone.getGreen() / 255f, b = tone.getBlue() / 255f;
            if (marker.radius() > 0) {
                float radiusPx = marker.radius() * camera.cellPxSize();
                PolyTess.appendAnnulus(mesh, rawX, rawY, Math.max(0, radiusPx - .75f), radiusPx + .75f,
                        64, r, g, b, .6f * alpha);
            }
            if (focus && active && !marker.footprint().isEmpty()) {
                var cells = new HashSet<>(marker.footprint());
                for (var cell : cells) {
                    float left = camera.cellToScreenX(cell.x()), right = camera.cellToScreenX(cell.x() + 1);
                    float bottom = camera.cellToScreenY(cell.y()), top = camera.cellToScreenY(cell.y() + 1);
                    if (!cells.contains(new MissionObjectiveSnapshot.Cell(cell.x() - 1, cell.y())))
                        out.addLine(layer(), left, bottom, left, top, 1.5f, r, g, b, .6f * alpha);
                    if (!cells.contains(new MissionObjectiveSnapshot.Cell(cell.x() + 1, cell.y())))
                        out.addLine(layer(), right, bottom, right, top, 1.5f, r, g, b, .6f * alpha);
                    if (!cells.contains(new MissionObjectiveSnapshot.Cell(cell.x(), cell.y() - 1)))
                        out.addLine(layer(), left, bottom, right, bottom, 1.5f, r, g, b, .6f * alpha);
                    if (!cells.contains(new MissionObjectiveSnapshot.Cell(cell.x(), cell.y() + 1)))
                        out.addLine(layer(), left, top, right, top, 1.5f, r, g, b, .6f * alpha);
                }
            }
            float x = clamp(rawX, camera.vpX() + WIDTH / 2 + 8, camera.vpX() + camera.vpW() - WIDTH / 2 - 8);
            float y = clamp(rawY, camera.vpY() + 128, camera.vpY() + camera.vpH() - 60);
            // The fixed-size diamond is a locator, never an invented capture radius.
            PolyTess.appendAnnulus(mesh, x, y, 9, 12, 4, r, g, b, alpha);
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
            details.add((onScreen ? "" : "OFF SCREEN / ") + status);
            boolean expanded = focus && active && (!item.id().startsWith("territory-") || camera.cellPxSize() >= 12);
            if (expanded) details.addAll(Fonts.INSIGNIA_15_AA.wrapLines(
                    blocked ? item.blocker() : item.instruction(), WIDTH - 20));
            var title = Fonts.ORBITRON_12_BOLD.wrapLines(marker.name(), WIDTH - 20);
            boolean bar = focus && item.progress() >= 0;
            float height = 10 + title.size() * 16 + details.size() * 17 + (bar ? 9 : 0);
            float top = y - 23;
            // Alternate above/below, then move by full card heights; keep every label in view.
            for (int attempt = 0; attempt < 12; attempt++) {
                top = clamp(top, camera.vpY() + height + 8, camera.vpY() + camera.vpH() - 8);
                boolean overlaps = false;
                for (var prior : labels) if (Math.abs(prior[0] - x) < WIDTH + 6
                        && top > prior[1] - prior[2] - 5 && top - height < prior[1] + 5) {
                    overlaps = true;
                    top = prior[1] + height + 6;
                    if (top > camera.vpY() + camera.vpH() - 8) {
                        top = prior[1] - prior[2] - 6;
                        x = clamp(x + WIDTH + 8, camera.vpX() + WIDTH / 2 + 8,
                                camera.vpX() + camera.vpW() - WIDTH / 2 - 8);
                    }
                    break;
                }
                if (!overlaps) break;
            }
            labels.add(new float[]{x, top, height});
            out.addSolidRect(layer(), x - WIDTH / 2, top - height, x + WIDTH / 2, top,
                    .035f, .045f, .048f, .94f * alpha);
            out.addLine(layer(), x - WIDTH / 2, top, x + WIDTH / 2, top, 2, r, g, b, alpha);
            float textY = top - 5;
            for (String line : title) {
                out.addCustom(layer(), new WorldLabel(Fonts.ORBITRON_12_BOLD, line, x, textY, PAPER, alpha));
                textY -= 16;
            }
            for (int i = 0; i < details.size(); i++) {
                out.addCustom(layer(), new WorldLabel(Fonts.INSIGNIA_15_AA, details.get(i), x, textY,
                        blocked || i == 0 ? tone : MUTED, alpha));
                textY -= 17;
            }
            if (bar) {
                float left = x - WIDTH / 2 + 9, right = x + WIDTH / 2 - 9, bottom = top - height + 5;
                out.addSolidRect(layer(), left, bottom, right, bottom + 3, .18f, .21f, .2f, alpha);
                out.addSolidRect(layer(), left, bottom, left + (right - left) * clamp(item.progress(), 0, 1), bottom + 3,
                        r, g, b, alpha);
            }
        }
        if (!mesh.isEmpty()) out.addPoly(layer(), mesh);
    }

    private static float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }
}
