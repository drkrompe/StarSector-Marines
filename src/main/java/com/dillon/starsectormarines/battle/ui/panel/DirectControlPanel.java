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

/** World-space identity bracket and point-aim cursor; the retained plate owns actions. */
public final class DirectControlPanel implements HudPanel {
    private static final Color CONTROLLED = new Color(0x6E, 0xD7, 0xFF);
    /** A warship's laid ground: guns in the controlled blue, missiles in orange, danger in amber-red. */
    private static final Color GUNS_AREA = new Color(0x6E, 0xD7, 0xFF);
    private static final Color MISSILES_AREA = new Color(0xFF, 0xA0, 0x50);
    private static final Color DANGER_AREA = new Color(0xFF, 0x5A, 0x4A);
    private static final int RING_SEGMENTS = 64;
    /** The magazine ring beside the cursor: radius and pip width in screen pixels. */
    private static final float AMMO_RING_RADIUS = 20f;
    private static final float AMMO_PIP_WIDTH = 4f;
    private static final Color ROUND_LIVE = new Color(0xEB, 0xF0, 0xF0);
    private static final Color ROUND_LOW = new Color(0xF0, 0x5A, 0x4A);
    private static final Color ROUND_SPENT = new Color(0x46, 0x4C, 0x50, 0xC0);
    private static final Color ROUND_LOADING = new Color(0xFF, 0xC4, 0x46);
    private static final Color RING_BACKING = new Color(0x0A, 0x0C, 0x0E, 0xB0);
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
        long id = sim.directControl().activeUnitId();
        float x = camera.cellToScreenX(sim.world().renderX(id));
        float y = camera.cellToScreenY(sim.world().renderY(id));
        float radius = Math.max(9f, camera.cellPxSize() * Math.max(0.7f, sim.physicalRadius(id) + 0.15f));
        HudDraw.prepBlend();
        HudDraw.borderRect(x - radius, y - radius, radius * 2f, radius * 2f,
                CONTROLLED, alpha);
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
        scatterRing(sim, id, aim.aimX(), aim.aimY(), aimX, aimY, camera.cellPxSize(), alpha);
        ammoRing(sim, id, aimX, aimY, alpha);
        crosshair(aimX, aimY, alpha);
    }

    /**
     * The controlled Marine's magazine beside the cursor, where the eye already is: a pip per round round
     * the crosshair's right side, spent pips dark, the last quarter red, and a reload sweeping amber.
     */
    private static void ammoRing(BattleSimulation sim, long id, float aimX, float aimY, float alpha) {
        var combat = sim.combat();
        if (!combat.has(id) || combat.primaryMagazine(id) == null) return;
        MagazineGauge gauge = MagazineGauge.of(combat.primaryWeaponDef(id), combat.equipmentGrade(id),
                combat.roundsLoaded(id), combat.reloadTimer(id), combat.reloadSeconds(id));
        if (gauge == null || gauge.capacity() == 0) return;
        float pip = gauge.pipStepDegrees() * (gauge.capacity() > 12 ? 0.62f : 0.72f);
        float width = gauge.capacity() > 12 ? AMMO_PIP_WIDTH - 1f : AMMO_PIP_WIDTH;
        for (int i = 0; i < gauge.capacity(); i++) {
            float from = gauge.pipStartDegrees(i);
            HudDraw.arc(aimX, aimY, AMMO_RING_RADIUS, width + 2f, from - 0.8f, from + pip + 0.8f, RING_BACKING, alpha);
            Color color = switch (gauge.round(i)) {
                case LIVE -> gauge.low() ? ROUND_LOW : ROUND_LIVE;
                case LOADING -> ROUND_LOADING;
                case SPENT -> ROUND_SPENT;
            };
            HudDraw.arc(aimX, aimY, AMMO_RING_RADIUS, width, from, from + pip, color, alpha);
        }
    }

    /**
     * Where a thrown rocket or LRM salvo will land: orange inside every covered mount's range, red past it,
     * where the disk grows faster than distance, and red inside an arcing rack's minimum, where it will not fire.
     */
    private static void scatterRing(BattleSimulation sim, long id, float aimCellX, float aimCellY,
                                    float aimX, float aimY, float cellPx, float alpha) {
        if (!sim.world().hasMechLoadout(id)) return;
        float distance = (float) Math.hypot(aimCellX - sim.world().renderX(id), aimCellY - sim.world().renderY(id));
        var ring = DirectControlScatter.mech(sim.world().mechLoadout(id), sim.directControl().selectedWeapon(), distance);
        if (ring == null) return;
        float r = ring.radiusCells() * cellPx;
        if (r < 4f) return;
        HudDraw.ring(aimX, aimY, r, ring.warning() ? DANGER_AREA : MISSILES_AREA, alpha * 0.8f, RING_SEGMENTS, 1.5f);
    }

    /**
     * The hull's outline, each battery's laid ground as a ring (red when a round aimed into
     * it would reach a friendly), and the cursor's crosshair. A ship fires on ground rather
     * than along a lane, so there is no aim lane.
     */
    private void renderWarship(BattleSimulation sim, BattleCamera camera, Warship ship, float alpha) {
        if (ship == null) return;
        HudDraw.prepBlend();
        HudDraw.outline(hullOutline(camera, ship), CONTROLLED, alpha, 1.5f);
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

    /** The four corners of the hull's footprint on screen, as x,y pairs. */
    private static float[] hullOutline(BattleCamera camera, Warship ship) {
        double r = Math.toRadians(ship.facingDegrees);
        float c = (float) Math.cos(r);
        float s = (float) Math.sin(r);
        float centreX = ship.x + ship.pivotX * c - ship.pivotY * s;
        float centreY = ship.y + ship.pivotX * s + ship.pivotY * c;
        float halfW = ship.widthCells * 0.5f;
        float halfL = ship.lengthCells * 0.5f;
        float[] out = new float[8];
        int[][] signs = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
        for (int i = 0; i < 4; i++) {
            float lx = signs[i][0] * halfW;
            float ly = signs[i][1] * halfL;
            out[i * 2] = camera.cellToScreenX(centreX + lx * c - ly * s);
            out[i * 2 + 1] = camera.cellToScreenY(centreY + lx * s + ly * c);
        }
        return out;
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
