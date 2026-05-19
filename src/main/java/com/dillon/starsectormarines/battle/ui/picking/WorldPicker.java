package com.dillon.starsectormarines.battle.ui.picking;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.Unit;
import com.dillon.starsectormarines.battle.ui.BattleUiContext;
import com.dillon.starsectormarines.battle.ui.HudPanel;
import com.dillon.starsectormarines.ops.battleview.BattleCamera;
import com.fs.starfarer.api.input.InputEventAPI;

import java.util.List;

/**
 * Input-only HUD slot. Translates an unconsumed LMB click inside the camera
 * viewport into a squad selection: nearest live unit (any faction) within a
 * forgiveness radius wins. A click on empty world space clears selection.
 *
 * <p>Pairs with the existing {@code SquadOverviewPanel} row clicks — both write
 * to {@link Selection}, so {@code SquadPlanDebugPanel} can show a filtered
 * detail view for whichever squad was last picked, regardless of how it was
 * picked. Defender squads aren't reachable from the overview panel today, so
 * world-picking is the only way to inspect their GOAP plan for debugging
 * garrison behavior.
 *
 * <p>Registered after the row-clicking panels in {@code BattleHud} so those
 * still claim their own click events first; this picker only fires on the
 * leftover clicks that landed in the world, not in a UI dock.
 */
public final class WorldPicker implements HudPanel {

    /** Pick radius in cells. Units may be mid-cell during movement (renderX/Y) so we want forgiveness around the visual centre. */
    private static final float PICK_RADIUS_CELLS = 0.6f;

    private final BattleUiContext ctx;

    public WorldPicker(BattleUiContext ctx) {
        this.ctx = ctx;
    }

    @Override public boolean isVisible() { return true; }
    @Override public void update(float dt) {}
    @Override public void render(float alphaMult) {}

    @Override
    public void handleInput(List<InputEventAPI> events) {
        if (events == null) return;
        BattleSimulation sim = ctx.getSim();
        BattleCamera camera = ctx.getCamera();
        if (sim == null || camera == null) return;

        for (InputEventAPI e : events) {
            if (e.isConsumed()) continue;
            if (!e.isLMBDownEvent()) continue;
            if (!camera.containsScreen(e.getX(), e.getY())) continue;

            float worldX = camera.screenToCellX(e.getX());
            float worldY = camera.screenToCellY(e.getY());
            Unit picked = nearestUnit(sim, worldX, worldY);
            if (picked != null && picked.squadId != Unit.NO_SQUAD) {
                ctx.getSelection().selectSquad(picked.squadId);
            } else {
                ctx.getSelection().clear();
            }
            e.consume();
        }
    }

    /**
     * Closest live unit to {@code (worldX, worldY)} within {@link #PICK_RADIUS_CELLS},
     * or {@code null} if the closest miss exceeds the radius. Uses each unit's
     * {@code renderX/renderY} so a unit mid-move is hit-tested at its visible
     * position, not at its discrete grid cell.
     */
    private static Unit nearestUnit(BattleSimulation sim, float worldX, float worldY) {
        Unit best = null;
        float bestDistSq = PICK_RADIUS_CELLS * PICK_RADIUS_CELLS;
        for (Unit u : sim.getUnits()) {
            if (!u.isAlive()) continue;
            float dx = u.renderX - worldX;
            float dy = u.renderY - worldY;
            float d2 = dx * dx + dy * dy;
            if (d2 < bestDistSq) {
                bestDistSq = d2;
                best = u;
            }
        }
        return best;
    }
}
