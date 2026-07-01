package com.dillon.starsectormarines.battle.ui.debug;

import com.dillon.starsectormarines.DebugOnly;
import com.dillon.starsectormarines.StarsectorMarinesModPlugin;
import com.dillon.starsectormarines.battle.sim.ConvoyService;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.vehicle.GroundTurret;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.vehicle.VehicleState;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Diagnostic dumper for a selected convoy vehicle. Writes a JSON snapshot
 * to {@code saves/common/starsector_marines/debug/vehicle_state.json},
 * overwritten each time. Includes current state, path waypoints, tick
 * history ring buffer, and a local walkability grid sample around the
 * vehicle for offline analysis of stuck-in-wall scenarios.
 */
@DebugOnly
public final class VehicleStateDumper {

    private static final Logger LOG = Logger.getLogger(VehicleStateDumper.class);
    private static final String PATH =
            StarsectorMarinesModPlugin.MOD_ID + "/debug/vehicle_state.json";
    private static final int LOCAL_GRID_RADIUS = 8;

    private VehicleStateDumper() {}

    public static void dump(long id, ConvoyService convoy, NavigationGrid grid) {
        VehicleMission v = convoy.mission(id);
        if (v == null) return;
        VehicleType type = convoy.vehicleType(id);
        Faction faction = convoy.faction(id);
        GroundBody body = convoy.body(id);
        GroundTurret turret = convoy.turret(id);
        try {
            JSONObject root = new JSONObject();
            root.put("type", type.name());
            root.put("state", v.state.name());
            root.put("faction", faction.name());

            JSONObject bodyJson = new JSONObject();
            bodyJson.put("x", round(body.x));
            bodyJson.put("y", round(body.y));
            bodyJson.put("facingDeg", round(body.facingDegrees));
            bodyJson.put("speed", round(body.speed));
            root.put("body", bodyJson);

            root.put("waypointIndex", v.controller != null ? v.controller.waypointIndex() : 1);
            root.put("trajectoryProgress", round(v.controller != null ? v.controller.trajectoryProgress() : 0f));
            root.put("wallStuckTime", round(v.controller != null ? v.controller.wallStuckTime() : 0f));
            root.put("hasTrajectory", v.controller != null && v.controller.hasTrajectory());
            root.put("marinesRemaining", v.marinesRemaining);
            root.put("overwatchCountdown", round(v.overwatchCountdown));
            root.put("turretAmmo", turret != null ? turret.ammo : 0);

            root.put("inbound", waypointsJson(v.inboundX, v.inboundY));
            root.put("outbound", waypointsJson(v.outboundX, v.outboundY));

            root.put("history", historyJson(v));
            root.put("localGrid", localGridJson(body, grid));

            Global.getSettings().writeJSONToCommon(PATH, root, true);
            LOG.info("VehicleStateDumper: wrote saves/common/" + PATH);
        } catch (Exception ex) {
            LOG.warn("VehicleStateDumper: dump failed", ex);
        }
    }

    private static JSONArray waypointsJson(float[] xs, float[] ys) throws Exception {
        JSONArray a = new JSONArray();
        for (int i = 0; i < xs.length; i++) {
            JSONObject wp = new JSONObject();
            wp.put("x", round(xs[i]));
            wp.put("y", round(ys[i]));
            a.put(wp);
        }
        return a;
    }

    private static JSONArray historyJson(VehicleMission v) throws Exception {
        JSONArray a = new JSONArray();
        for (int i = 0; i < v.histCount; i++) {
            int idx = (v.histHead - v.histCount + i + VehicleMission.HISTORY_SIZE) % VehicleMission.HISTORY_SIZE;
            JSONObject tick = new JSONObject();
            tick.put("x", round(v.histX[idx]));
            tick.put("y", round(v.histY[idx]));
            tick.put("facing", round(v.histFacing[idx]));
            tick.put("speed", round(v.histSpeed[idx]));
            tick.put("stuck", round(v.histStuck[idx]));
            tick.put("state", VehicleState.values()[v.histState[idx]].name());
            a.put(tick);
        }
        return a;
    }

    private static JSONObject localGridJson(GroundBody body, NavigationGrid grid) throws Exception {
        int cx = (int) Math.floor(body.x);
        int cy = (int) Math.floor(body.y);
        int r = LOCAL_GRID_RADIUS;
        int x0 = Math.max(0, cx - r), x1 = Math.min(grid.getWidth() - 1, cx + r);
        int y0 = Math.max(0, cy - r), y1 = Math.min(grid.getHeight() - 1, cy + r);

        JSONObject o = new JSONObject();
        o.put("originX", x0);
        o.put("originY", y0);
        o.put("width", x1 - x0 + 1);
        o.put("height", y1 - y0 + 1);
        o.put("vehicleCellX", cx);
        o.put("vehicleCellY", cy);

        StringBuilder sb = new StringBuilder();
        for (int y = y1; y >= y0; y--) {
            for (int x = x0; x <= x1; x++) {
                sb.append(grid.isWalkable(x, y) ? '.' : '#');
            }
            if (y > y0) sb.append('\n');
        }
        o.put("walkability", sb.toString());
        return o;
    }

    private static double round(float v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
