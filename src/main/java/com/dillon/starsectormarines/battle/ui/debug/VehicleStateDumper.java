package com.dillon.starsectormarines.battle.ui.debug;

import com.dillon.starsectormarines.StarsectorMarinesModPlugin;
import com.dillon.starsectormarines.battle.vehicle.Vehicle;
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
public final class VehicleStateDumper {

    private static final Logger LOG = Logger.getLogger(VehicleStateDumper.class);
    private static final String PATH =
            StarsectorMarinesModPlugin.MOD_ID + "/debug/vehicle_state.json";
    private static final int LOCAL_GRID_RADIUS = 8;

    private VehicleStateDumper() {}

    public static void dump(Vehicle v, NavigationGrid grid) {
        try {
            JSONObject root = new JSONObject();
            root.put("type", v.type.name());
            root.put("state", v.state.name());
            root.put("faction", v.faction.name());

            JSONObject body = new JSONObject();
            body.put("x", round(v.body.x));
            body.put("y", round(v.body.y));
            body.put("facingDeg", round(v.body.facingDegrees));
            body.put("speed", round(v.body.speed));
            root.put("body", body);

            root.put("waypointIndex", v.waypointIndex);
            root.put("playbackProgress", round(v.playbackProgress));
            root.put("wallStuckTime", round(v.wallStuckTime));
            root.put("pathRefined", v.pathRefined);
            root.put("hasPlayback", v.inboundHeading != null || v.outboundHeading != null);
            root.put("marinesRemaining", v.marinesRemaining);
            root.put("overwatchCountdown", round(v.overwatchCountdown));
            root.put("turretAmmo", v.turretAmmo);

            root.put("inbound", waypointsJson(v.inboundX, v.inboundY));
            root.put("outbound", waypointsJson(v.outboundX, v.outboundY));

            root.put("history", historyJson(v));
            root.put("localGrid", localGridJson(v, grid));

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

    private static JSONArray historyJson(Vehicle v) throws Exception {
        JSONArray a = new JSONArray();
        for (int i = 0; i < v.histCount; i++) {
            int idx = (v.histHead - v.histCount + i + Vehicle.HISTORY_SIZE) % Vehicle.HISTORY_SIZE;
            JSONObject tick = new JSONObject();
            tick.put("x", round(v.histX[idx]));
            tick.put("y", round(v.histY[idx]));
            tick.put("facing", round(v.histFacing[idx]));
            tick.put("speed", round(v.histSpeed[idx]));
            tick.put("stuck", round(v.histStuck[idx]));
            tick.put("state", Vehicle.State.values()[v.histState[idx]].name());
            a.put(tick);
        }
        return a;
    }

    private static JSONObject localGridJson(Vehicle v, NavigationGrid grid) throws Exception {
        int cx = (int) Math.floor(v.body.x);
        int cy = (int) Math.floor(v.body.y);
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
