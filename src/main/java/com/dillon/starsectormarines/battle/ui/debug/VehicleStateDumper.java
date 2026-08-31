package com.dillon.starsectormarines.battle.ui.debug;

import com.dillon.starsectormarines.DebugOnly;
import com.dillon.starsectormarines.StarsectorMarinesModPlugin;
import com.dillon.starsectormarines.battle.sim.ConvoyService;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.vehicle.GroundTurret;
import com.dillon.starsectormarines.battle.vehicle.Pose;
import com.dillon.starsectormarines.battle.vehicle.Trajectory;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.vehicle.VehicleState;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.vehicle.components.VehicleControlComponent;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Diagnostic dumper for a selected convoy vehicle. Writes a JSON snapshot
 * to {@code saves/common/starsector_marines/debug/vehicle_state.json.data},
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

            VehicleControlComponent ctl = convoy.control(id);
            root.put("waypointIndex", ctl != null ? ctl.waypointIndex() : 1);
            root.put("trajectoryProgress", round(ctl != null ? ctl.trajectoryProgress() : 0f));
            root.put("wallStuckTime", round(ctl != null ? ctl.wallStuckTime() : 0f));
            root.put("hasTrajectory", ctl != null && ctl.hasTrajectory());
            root.put("recovery", ctl != null ? ctl.recovery.name() : "NONE");
            root.put("recoveryAttempts", ctl != null ? ctl.recoveryAttempts : 0);
            root.put("recoveryBestRemaining", round(ctl != null ? ctl.recoveryBestRemaining : 0f));
            root.put("timeSinceProgress", round(ctl != null ? ctl.timeSinceProgress : 0f));
            root.put("localPlanFailureTime", round(ctl != null ? ctl.localPlanFailureTime : 0f));
            root.put("localPlanFailureRerouteAttempted",
                    ctl != null && ctl.localPlanFailureRerouteAttempted);
            root.put("rescueFirstStepTriedMask", ctl != null ? ctl.rescueFirstStepTriedMask : 0);
            root.put("rerouteAvoidCount", ctl != null ? ctl.rerouteAvoidCount : 0);
            root.put("trajectory", trajectoryJson(ctl != null ? ctl.trajectory : null));
            root.put("marinesRemaining", v.marinesRemaining);
            root.put("structure", round(convoy.structure(id)));
            root.put("maxStructure", round(convoy.maxStructure(id)));
            root.put("armor", round(convoy.armor(id)));
            root.put("maxArmor", round(convoy.maxArmor(id)));
            root.put("armorRating", round(convoy.armorRating(id)));
            root.put("turretAmmo", turret != null ? turret.ammo : 0);

            root.put("inbound", waypointsJson(v.inboundX, v.inboundY));
            root.put("outbound", waypointsJson(v.outboundX, v.outboundY));

            root.put("history", historyJson(v));
            root.put("localGrid", localGridJson(body, grid));

            writeSnapshot(root);
            LOG.info("VehicleStateDumper: wrote saves/common/" + PATH + ".data");
        } catch (Exception ex) {
            LOG.warn("VehicleStateDumper: dump failed", ex);
        }
    }

    /**
     * Write synchronously so the success log means the snapshot reached disk.
     * Starsector's {@code onlyIfChanged=true} path delegates to a shared
     * background writer; if that worker has stopped, the API still returns
     * successfully after merely queueing the write.
     */
    static void writeSnapshot(JSONObject root) throws Exception {
        Global.getSettings().writeJSONToCommon(PATH, root, false);
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

    private static JSONArray trajectoryJson(Trajectory trajectory) throws Exception {
        JSONArray points = new JSONArray();
        if (trajectory == null) return points;
        for (int i = 0; i < trajectory.size(); i++) {
            putPose(points, trajectory.pose(i));
        }
        return points;
    }

    private static void putPose(JSONArray points, Pose pose) throws Exception {
        JSONObject point = new JSONObject();
        point.put("x", round(pose.x));
        point.put("y", round(pose.y));
        point.put("facing", round(pose.facingDeg));
        points.put(point);
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
