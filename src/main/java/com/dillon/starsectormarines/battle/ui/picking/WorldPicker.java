package com.dillon.starsectormarines.battle.ui.picking;

import com.dillon.starsectormarines.battle.vehicle.Vehicle;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Entity;
import com.dillon.starsectormarines.battle.ui.BattleUiContext;
import com.dillon.starsectormarines.battle.ui.HudPanel;
import com.dillon.starsectormarines.render2d.BattleCamera;
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

            long vehicleId = nearestVehicle(sim, worldX, worldY);
            if (vehicleId != 0L) {
                ctx.getSelection().selectVehicle(vehicleId);
                e.consume();
                continue;
            }

            Entity picked = nearestUnit(sim, worldX, worldY);
            if (picked != null && sim.squad().hasSquad(picked.entityId)) {
                ctx.getSelection().selectUnit(sim.squad().squadId(picked.entityId), picked.id);
            } else {
                ctx.getSelection().clear();
            }
            e.consume();
        }
    }

    private static final float VEHICLE_PICK_RADIUS_CELLS = 1.5f;

    /** Entity id of the nearest visible convoy vehicle within the pick radius, or {@code 0L} if none. */
    private static long nearestVehicle(BattleSimulation sim, float worldX, float worldY) {
        List<Vehicle> vehicles = sim.getConvoyVehicles();
        long bestId = 0L;
        float bestDistSq = VEHICLE_PICK_RADIUS_CELLS * VEHICLE_PICK_RADIUS_CELLS;
        for (int i = 0; i < vehicles.size(); i++) {
            Vehicle v = vehicles.get(i);
            if (!v.isVisible()) continue;
            float dx = v.body.x - worldX;
            float dy = v.body.y - worldY;
            float d2 = dx * dx + dy * dy;
            if (d2 < bestDistSq) {
                bestDistSq = d2;
                bestId = v.entityId;
            }
        }
        return bestId;
    }

    private static Entity nearestUnit(BattleSimulation sim, float worldX, float worldY) {
        Entity best = null;
        float bestDistSq = PICK_RADIUS_CELLS * PICK_RADIUS_CELLS;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            Entity u = sim.liveUnitAt(i);
            float dx = sim.world().renderX(u.entityId) - worldX;
            float dy = sim.world().renderY(u.entityId) - worldY;
            float d2 = dx * dx + dy * dy;
            if (d2 < bestDistSq) {
                bestDistSq = d2;
                best = u;
            }
        }
        return best;
    }
}
