package com.dillon.starsectormarines.battle.ui.picking;

import com.dillon.starsectormarines.battle.sim.ConvoyService;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
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

    /**
     * Baseline pick radius in cells, used as a floor for small units. Units
     * may be mid-cell during movement (World.renderX/Y, the tolerant
     * center-based reads) so we want forgiveness around the visual centre.
     * {@link #nearestUnit} widens this per-unit to at least the unit's
     * {@link UnitType#radius} physical footprint (plus a small margin) so
     * bigger units (mechs, hubs) are easier to click without shrinking the
     * forgiveness infantry already had.
     */
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

            long picked = nearestUnit(sim, worldX, worldY);
            if (picked != 0L && sim.squad().hasSquad(picked)) {
                ctx.getSelection().selectUnit(sim.squad().squadId(picked), picked);
            } else {
                ctx.getSelection().clear();
            }
            e.consume();
        }
    }

    private static final float VEHICLE_PICK_RADIUS_CELLS = 1.5f;

    /** Entity id of the nearest visible convoy vehicle within the pick radius, or {@code 0L} if none. */
    private static long nearestVehicle(BattleSimulation sim, float worldX, float worldY) {
        long[] ids = sim.getConvoyVehicleIds();
        ConvoyService convoy = sim.convoy();
        long bestId = 0L;
        float bestDistSq = VEHICLE_PICK_RADIUS_CELLS * VEHICLE_PICK_RADIUS_CELLS;
        for (long id : ids) {
            VehicleMission v = convoy.mission(id);
            if (v == null || !v.isVisible()) continue;
            GroundBody body = convoy.body(id);
            float dx = body.x - worldX;
            float dy = body.y - worldY;
            float d2 = dx * dx + dy * dy;
            if (d2 < bestDistSq) {
                bestDistSq = d2;
                bestId = id;
            }
        }
        return bestId;
    }

    /**
     * Nearest live unit within its own pick radius, or {@code 0L} if none
     * qualify. Each unit's radius scales with its physical footprint
     * ({@link UnitType#radius}) so bigger units (mechs, hubs) are easier to
     * click; {@code bestDistSq} tracks absolute nearest-distance among
     * qualifying candidates, not distance relative to each unit's own radius,
     * so a small unit slightly closer than a big one still wins.
     */
    private static long nearestUnit(BattleSimulation sim, float worldX, float worldY) {
        long best = 0L;
        float bestDistSq = Float.MAX_VALUE;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long u = sim.liveUnitAt(i);
            float dx = sim.world().renderX(u) - worldX;
            float dy = sim.world().renderY(u) - worldY;
            float d2 = dx * dx + dy * dy;
            UnitType type = sim.identity().type(u);
            float pickRadius = Math.max(PICK_RADIUS_CELLS, type.radius + 0.3f);
            if (d2 > pickRadius * pickRadius) continue;
            if (d2 < bestDistSq) {
                bestDistSq = d2;
                best = u;
            }
        }
        return best;
    }
}
