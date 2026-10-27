package com.dillon.starsectormarines.battle.ui.panel;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.ui.BattleUiContext;
import com.dillon.starsectormarines.battle.ui.HudPanel;
import com.dillon.starsectormarines.battle.tender.Tender;
import com.dillon.starsectormarines.battle.tender.TenderPilot;
import com.dillon.starsectormarines.battle.warship.Warship;
import com.dillon.starsectormarines.battle.warship.WarshipOrder;
import com.dillon.starsectormarines.battle.warship.WarshipPilot;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.fs.starfarer.api.input.InputEventAPI;

import java.awt.Color;
import java.util.List;

/**
 * World-space point-aim reticle and aim lanes; the retained plate owns actions. A ground body draws no
 * bracket of its own: the action camera follows it, which already says which body is controlled.
 */
public final class DirectControlPanel implements HudPanel {
    private static final Color CONTROLLED = new Color(0x6E, 0xD7, 0xFF);
    /** A warship's laid ground: guns in the controlled blue, missiles in orange, danger in amber-red. */
    private static final Color GUNS_AREA = new Color(0x6E, 0xD7, 0xFF);
    private static final Color MISSILES_AREA = new Color(0xFF, 0xA0, 0x50);
    private static final Color DANGER_AREA = new Color(0xFF, 0x5A, 0x4A);
    private static final int RING_SEGMENTS = 64;
    private final BattleUiContext context;

    public DirectControlPanel(BattleUiContext context) { this.context = context; }

    @Override public boolean isVisible() {
        return context.getSim() != null && context.getSim().directControl().active();
    }
    @Override public boolean visibleDuringDirectControl() { return true; }
    @Override public void update(float dt) {}
    @Override public void handleInput(List<InputEventAPI> events) {}

    @Override
    public void render(float alpha) {
        var sim = context.getSim();
        var camera = context.getCamera();
        if (sim == null || camera == null || !sim.directControl().active()) return;
        if (sim.directControl().warshipControl()) {
            renderWarship(sim, camera, sim.directControl().controlledWarship(), alpha);
            return;
        }
        HudDraw.prepBlend();
        if (!context.directControlAimVisible()) return;
        var aim = sim.directControl().intent();
        if (!Float.isFinite(aim.aimX()) || !Float.isFinite(aim.aimY())) return;
        float aimX = camera.cellToScreenX(aim.aimX());
        float aimY = camera.cellToScreenY(aim.aimY());
        if (!camera.containsScreen(aimX, aimY)) return;
        for (var origin : DirectControlAimOrigins.read(sim)) {
            var lane = DirectControlAimLane.observed(sim.getGrid(), origin, aim.aimX(), aim.aimY(),
                    sim.getFogOfWar()::isCellRevealed);
            if (lane == null) continue;
            for (var stroke : DirectControlAimLane.strokes(lane, camera.cellPxSize(),
                    camera.cellToScreenX(0f), camera.cellToScreenY(0f))) HudDraw.stroke(stroke, alpha);
        }
        var spread = DirectControlScatter.read(sim);
        float cellPx = camera.cellPxSize();
        float[] origin = DirectControlFireMarks.fireOrigin(sim);
        float fireX = origin != null ? aimX - camera.cellToScreenX(origin[0]) : 0f;
        float fireY = origin != null ? aimY - camera.cellToScreenY(origin[1]) : 0f;
        for (var stroke : DirectControlReticle.strokes(DirectControlFireMarks.read(sim), DirectControlLoad.read(sim),
                spread.leftCells() * cellPx, spread.rightCells() * cellPx, aimX, aimY, fireX, fireY, CONTROLLED)) {
            HudDraw.stroke(stroke, alpha);
        }
    }

    /** The cursor's crosshair colour, and the reticle's centre dot. */
    public static Color controlledColor() {
        return CONTROLLED;
    }

    /**
     * Each battery's laid ground as a ring (red when a round aimed into it would reach a friendly),
     * and the cursor's crosshair. A ship fires on ground rather than along a lane, so there is no aim
     * lane; and the camera is leashed to the piloted hull, so it draws no outline of its own.
     */
    private void renderWarship(BattleSimulation sim, BattleCamera camera, Warship ship, float alpha) {
        if (ship == null) return;
        HudDraw.prepBlend();
        ring(sim, camera, ship, ship.laidArea(Warship.FireGroup.GUNS), GUNS_AREA, alpha);
        ring(sim, camera, ship, ship.laidArea(Warship.FireGroup.MISSILES), MISSILES_AREA, alpha);
        if (!context.directControlAimVisible()) return;
        var aim = sim.directControl().intent();
        if (!Float.isFinite(aim.aimX()) || !Float.isFinite(aim.aimY())) return;
        float aimX = camera.cellToScreenX(aim.aimX());
        float aimY = camera.cellToScreenY(aim.aimY());
        if (!camera.containsScreen(aimX, aimY)) return;
        crosshair(aimX, aimY, alpha);
        Tender tender = sim.directControl().controlledTender();
        if (tender != null && sim.directControl().selectedBuildOption() != null) {
            siteReticle(sim, camera, tender, aim.aimX(), aim.aimY(), alpha);
        }
    }

    /** The cell a left click would lay a site on: the controlled blue where it can be built, red where it cannot. */
    private static void siteReticle(BattleSimulation sim, BattleCamera camera, Tender tender,
                                    float x, float y, float alpha) {
        var where = TenderPilot.placement(tender, x, y, sim.getGrid(), sim.getFogOfWar()::isCellRevealed);
        if (where == TenderPilot.Placement.OFF_MAP) return;
        float px = camera.cellPxSize();
        float left = camera.cellToScreenX((float) Math.floor(x));
        float top = camera.cellToScreenY((float) Math.floor(y));
        Color color = where == TenderPilot.Placement.OPEN ? CONTROLLED : DANGER_AREA;
        HudDraw.borderRect(Math.min(left, camera.cellToScreenX((float) Math.floor(x) + 1f)),
                Math.min(top, camera.cellToScreenY((float) Math.floor(y) + 1f)), px, px, color, alpha);
    }

    private static void ring(BattleSimulation sim, BattleCamera camera, Warship ship,
                             WarshipOrder.FireOnArea area, Color color, float alpha) {
        if (area == null) return;
        float cx = camera.cellToScreenX(area.x());
        float cy = camera.cellToScreenY(area.y());
        Color shown = WarshipPilot.endangersFriendlies(sim, ship, area) ? DANGER_AREA : color;
        float r = area.radius() * camera.cellPxSize();
        HudDraw.ring(cx, cy, r, shown, alpha, RING_SEGMENTS, 2f);
        HudDraw.ring(cx, cy, r * 0.5f, shown, alpha * 0.45f, RING_SEGMENTS / 2, 1f);
        crosshair(cx, cy, alpha * 0.8f, shown);
    }

    /** A battery's laid-ground ring colour before any danger: guns in the controlled blue, missiles in orange. */
    public static Color laidAreaColor(Warship.FireGroup group) {
        return group == Warship.FireGroup.MISSILES ? MISSILES_AREA : GUNS_AREA;
    }

    private static void crosshair(float x, float y, float alpha) {
        crosshair(x, y, alpha, CONTROLLED);
    }

    private static void crosshair(float x, float y, float alpha, Color color) {
        HudDraw.filledRect(x - 8f, y - 1f, 5f, 2f, color, alpha);
        HudDraw.filledRect(x + 3f, y - 1f, 5f, 2f, color, alpha);
        HudDraw.filledRect(x - 1f, y - 8f, 2f, 5f, color, alpha);
        HudDraw.filledRect(x - 1f, y + 3f, 2f, 5f, color, alpha);
    }
}
