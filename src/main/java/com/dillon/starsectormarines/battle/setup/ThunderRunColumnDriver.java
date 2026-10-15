package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.ConvoyService;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.vehicle.VehicleLeg;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.vehicle.VehicleState;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.world.gen.ThunderRunCompound;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Drives the Thunder Run column like drivers rather than rails.
 *
 * <p>{@link ThunderRunSequence} still decides <em>when</em> each hull moves and
 * <em>how far</em>; this decides how it gets there. Every leg is a gently
 * weaving lane through the fifteen-cell freight band instead of a two-point
 * line, and while a hull is on the road it reacts to what it can see:
 * <ul>
 *   <li><b>Threat side.</b> Haulers drift toward the far shoulder from the
 *       hostile infantry beside the road; the heavy lead edges toward it to put
 *       its hull between. Escorts keep their flank lanes, which are the screen.</li>
 *   <li><b>Under fire.</b> A hauler that has just been hit stops cruising and
 *       runs at full speed, jinking harder.</li>
 *   <li><b>Wrecks and parked hulls.</b> The lane is moved, across the whole
 *       band if it must be, to the nearest line that clears every burnt-out or
 *       stationary hull ahead; a goal that lies on one is met beside it.</li>
 *   <li><b>Station keeping.</b> A hull closes no nearer than a few cells to
 *       the one ahead in its lane, and waits behind it when it has stopped.</li>
 *   <li><b>People.</b> Friendly infantry in the road ahead get a slowing hull;
 *       hostile infantry get the bow wave and nothing else.</li>
 * </ul>
 * Routes are rebuilt only when the wanted lane moves materially and not more
 * often than {@link #REROUTE_TICKS}, so the chassis planner tracks a stable
 * corridor. Everything here is deterministic: the weave phase hashes the
 * vehicle id, and the scans walk the roster in dense order.
 */
final class ThunderRunColumnDriver {
    enum Role { CARGO, ESCORT, HEAVY, TECHNICAL }

    /** {@code -Dbattle.thunderRun.reactiveColumn=false} is the control: straight two-point legs, no reactions. */
    static final boolean REACTIVE = Boolean.parseBoolean(System.getProperty("battle.thunderRun.reactiveColumn", "true"));

    /** Centerline and edges of the reserved freight band. */
    static final float ROAD_CENTRE = 75f, BAND_TOP = 68.4f, BAND_BOTTOM = 82.6f;
    /** Haulers and the heavy lead stay between the escorts' lanes. */
    static final float INNER_TOP = 72.6f, INNER_BOTTOM = 77.4f;
    /** Waypoint pitch of an authored lane, in cells. */
    static final float WAYPOINT_PITCH = 5f;
    /** Ticks between threat-picture scans. */
    static final int SCAN_TICKS = 15;
    /** Minimum ticks between reactive route rebuilds for one hull. */
    static final int REROUTE_TICKS = 75;
    /** A wanted lane must move this far, in cells, before a route is rebuilt. */
    static final float REROUTE_SHIFT = 1.0f;
    /** Hostile infantry this far from a hull, in cells, shapes its threat side. */
    static final float THREAT_RANGE = 26f;
    /** How long a hit keeps a hauler running flat out, in ticks. */
    static final int UNDER_FIRE_TICKS = 4 * ThunderRunPacing.TICKS_PER_SECOND;
    /** Ordinary cruising fraction of a hauler's top speed. */
    static final float CARGO_CRUISE = .8f;
    /** Bumper-to-bumper gap a hull keeps to the one ahead, in cells. */
    static final float STATION_GAP = 2.5f;
    /** Wrecks and parked hulls this far ahead, in cells, shape the lane. */
    static final float BLOCKER_LOOKAHEAD = 36f;
    /** Side clearance a lane keeps from a wreck or parked hull, in cells. */
    static final float LANE_MARGIN = .25f;
    /** Slowest moving speed a driver asks for; below this it waits instead. */
    static final float CRAWL = .55f;

    private static final class Plan {
        final Role role;
        float baseLane;
        float goalX;
        VehicleLeg leg;
        float lane;
        int lastRouteTick = Integer.MIN_VALUE / 2;
        float threatShift;
        boolean avoiding, wasAvoiding;
        float lastHp = -1f;
        int lastHitTick = Integer.MIN_VALUE / 2;
        Plan(Role role, float baseLane) { this.role = role; this.baseLane = baseLane; this.lane = baseLane; }
    }

    private final Map<Long, Plan> plans = new LinkedHashMap<>();
    private int reroutes, swerves, waits, punches;

    /** Reactive rebuilds since the battle began; evidence, not behaviour. */
    int reroutes() { return reroutes; }
    /** Rebuilds that steered a hull around a wreck in its lane. */
    int swerves() { return swerves; }
    /** Ticks a hull spent holding station behind another. */
    int waits() { return waits; }
    /** Times a hauler was hit and ran flat out. */
    int punches() { return punches; }

    void register(long id, Role role, float lane) {
        if (id != 0L) plans.put(id, new Plan(role, lane));
    }

    /** Sends {@code id} to {@code goalX} on a new lane {@code laneY}, which it then keeps. */
    void order(BattleSimulation sim, long id, float goalX, float laneY, VehicleLeg leg) {
        Plan plan = plans.get(id);
        if (plan != null) plan.baseLane = laneY;
        order(sim, id, goalX, leg);
    }

    /** Sends {@code id} along its lane to {@code goalX}; replaces any leg it was on. */
    void order(BattleSimulation sim, long id, float goalX, VehicleLeg leg) {
        Plan plan = plans.get(id);
        if (plan == null || !drivable(sim, id)) return;
        plan.goalX = goalX;
        plan.leg = leg;
        if (!REACTIVE) {
            VehicleMission mission = sim.convoy().mission(id);
            GroundBody body = sim.convoy().body(id);
            mission.authoredRouteX = new float[]{body.x, goalX};
            mission.authoredRouteY = new float[]{body.y, plan.role == Role.TECHNICAL ? plan.baseLane : body.y};
            mission.authoredLeg = leg;
            return;
        }
        plan.lane = wantedLane(sim, id, plan, sim.convoy().body(id), sim.convoy().vehicleType(id));
        route(sim, id, plan, sim.getSimTickIndex());
    }

    void tick(BattleSimulation sim) {
        if (!REACTIVE) return;
        int tick = sim.getSimTickIndex();
        ConvoyService convoy = sim.convoy();
        boolean scan = tick % SCAN_TICKS == 0;
        for (var entry : plans.entrySet()) {
            long id = entry.getKey();
            Plan plan = entry.getValue();
            if (!drivable(sim, id)) continue;
            VehicleMission mission = convoy.mission(id);
            float hp = convoy.structure(id);
            if (plan.lastHp >= 0f && hp < plan.lastHp - .5f) {
                if (plan.role == Role.CARGO && tick - plan.lastHitTick > UNDER_FIRE_TICKS) punches++;
                plan.lastHitTick = tick;
            }
            plan.lastHp = hp;
            if (mission.authoredRouteX == null) {
                mission.speedCap = Float.POSITIVE_INFINITY;
                mission.authoredRoutePaused = false;
                continue;
            }
            GroundBody body = convoy.body(id);
            VehicleType type = convoy.vehicleType(id);
            if (scan && plan.role != Role.TECHNICAL && body.x < plan.goalX - 12f) {
                plan.threatShift = threatShift(sim, plan.role, body);
                float wanted = wantedLane(sim, id, plan, body, type);
                if (Math.abs(wanted - plan.lane) >= REROUTE_SHIFT && tick - plan.lastRouteTick >= REROUTE_TICKS) {
                    if (plan.avoiding && !plan.wasAvoiding) swerves++;
                    plan.wasAvoiding = plan.avoiding;
                    plan.lane = wanted;
                    route(sim, id, plan, tick);
                    reroutes++;
                }
            }
            pace(sim, id, plan, body, type, mission, tick);
        }
    }

    private static boolean drivable(BattleSimulation sim, long id) {
        return sim.convoy().isVehicle(id) && sim.convoy().mission(id).state != VehicleState.WRECKED;
    }

    private static float wantedLane(BattleSimulation sim, long id, Plan plan, GroundBody body, VehicleType type) {
        if (plan.role == Role.TECHNICAL) return plan.baseLane;
        float lane = plan.baseLane + plan.threatShift;
        lane = plan.role == Role.ESCORT ? clampBand(lane, type) : clamp(lane, INNER_TOP, INNER_BOTTOM);
        float cleared = clearLane(sim, id, body, type, lane);
        plan.avoiding = Math.abs(cleared - lane) > 1e-3f;
        return cleared;
    }

    private static float clampBand(float y, VehicleType type) {
        float half = type.visualWidthCells * .5f;
        return clamp(y, BAND_TOP + half, BAND_BOTTOM - half);
    }

    /** Lateral lane offset from the hostile picture: haulers away, the heavy lead toward. */
    private static float threatShift(BattleSimulation sim, Role role, GroundBody body) {
        if (role == Role.ESCORT) return 0f;
        float north = 0f, south = 0f;
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().faction(unit) != Faction.DEFENDER) continue;
            float dx = sim.world().x(unit) - body.x, dy = sim.world().y(unit) - body.y;
            if (dx < -8f || dx > THREAT_RANGE || Math.abs(dy) > THREAT_RANGE) continue;
            float weight = 1f - (float) Math.hypot(dx, dy) / (THREAT_RANGE * 1.42f);
            if (sim.world().y(unit) < ROAD_CENTRE) north += weight; else south += weight;
        }
        float lean = north - south;
        if (Math.abs(lean) < .6f) return 0f;
        float magnitude = Math.min(1f, Math.abs(lean) / 3f);
        float away = lean > 0f ? 1f : -1f;
        return role == Role.CARGO ? away * 2.4f * magnitude : -away * 2f * magnitude;
    }

    /**
     * The lane nearest {@code preferred} that clears every wreck and parked hull
     * from alongside to {@link #BLOCKER_LOOKAHEAD} cells ahead, searched across
     * the whole band. Where nothing clears, the lane with the most room.
     */
    static float clearLane(BattleSimulation sim, long self, GroundBody body, VehicleType type, float preferred) {
        ConvoyService convoy = sim.convoy();
        float half = type.visualWidthCells * .5f;
        int count = 0;
        float[] ys = new float[convoy.vehicleCount()], reach = new float[ys.length];
        for (long other : convoy.entityIds()) {
            if (other == self || !blocker(convoy, other)) continue;
            GroundBody o = convoy.body(other);
            VehicleType ot = convoy.vehicleType(other);
            float ahead = o.x - body.x;
            if (ahead < -(type.visualLengthCells + ot.visualLengthCells) * .5f || ahead > BLOCKER_LOOKAHEAD) continue;
            ys[count] = o.y;
            reach[count++] = half + ot.visualWidthCells * .5f + LANE_MARGIN;
        }
        if (count == 0 || room(ys, reach, count, preferred) >= 0f) return preferred;
        float best = preferred, bestCost = Float.MAX_VALUE, fallback = preferred, fallbackRoom = -Float.MAX_VALUE;
        for (float y = BAND_TOP + half; y <= BAND_BOTTOM - half + 1e-3f; y += .2f) {
            float room = room(ys, reach, count, y);
            if (room >= 0f && Math.abs(y - preferred) < bestCost) { bestCost = Math.abs(y - preferred); best = y; }
            if (room > fallbackRoom) { fallbackRoom = room; fallback = y; }
        }
        return bestCost < Float.MAX_VALUE ? best : fallback;
    }

    private static float room(float[] ys, float[] reach, int count, float y) {
        float room = Float.MAX_VALUE;
        for (int i = 0; i < count; i++) room = Math.min(room, Math.abs(y - ys[i]) - reach[i]);
        return room;
    }

    /** A hull that will not move out of the way by itself: burnt out, or parked with no leg to drive. */
    private static boolean blocker(ConvoyService convoy, long id) {
        VehicleMission mission = convoy.mission(id);
        return mission.state == VehicleState.WRECKED
                || mission.isVisible() && mission.authoredRouteX == null && Math.abs(convoy.body(id).speed) < .2f;
    }

    /** Speed cap and hold from station keeping, people in the road, and incoming fire. */
    private void pace(BattleSimulation sim, long id, Plan plan, GroundBody body, VehicleType type,
                      VehicleMission mission, int tick) {
        boolean underFire = tick - plan.lastHitTick <= UNDER_FIRE_TICKS;
        float cap = plan.role == Role.CARGO && !underFire ? type.maxSpeed * CARGO_CRUISE : type.maxSpeed;
        double rad = Math.toRadians(body.facingDegrees);
        float fx = (float) -Math.sin(rad), fy = (float) Math.cos(rad);
        float halfL = type.visualLengthCells * .5f, halfW = type.visualWidthCells * .5f;
        ConvoyService convoy = sim.convoy();
        float gap = Float.MAX_VALUE;
        boolean aheadStopped = false;
        for (long other : convoy.entityIds()) {
            if (other == id || !convoy.mission(other).isVisible()) continue;
            GroundBody o = convoy.body(other);
            VehicleType ot = convoy.vehicleType(other);
            float dx = o.x - body.x, dy = o.y - body.y;
            float along = dx * fx + dy * fy, across = Math.abs(dx * fy - dy * fx);
            if (along <= 0f || across >= halfW + ot.visualWidthCells * .5f + .3f) continue;
            float g = along - halfL - ot.visualLengthCells * .5f;
            if (g < gap) {
                gap = g;
                // Queue behind a column member that has paused; a blocker is driven around instead.
                aheadStopped = !blocker(convoy, other) && Math.abs(o.speed) < .2f;
            }
        }
        if (gap < Float.MAX_VALUE) {
            float keep = STATION_GAP + Math.abs(body.speed) * .6f;
            if (gap < STATION_GAP && aheadStopped) {
                hold(mission);
                waits++;
                return;
            }
            if (gap < keep + 6f) cap = Math.min(cap, Math.max(CRAWL, (gap - STATION_GAP) * .9f));
        }
        float friendly = Float.MAX_VALUE;
        float reach = halfL + 1.5f + Math.abs(body.speed) * 1.2f;
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long unit = sim.liveUnitAt(i);
            Faction faction = sim.identity().faction(unit);
            if (faction != Faction.MARINE && faction != Faction.ALLY) continue;
            UnitType unitType = sim.identity().type(unit);
            if (unitType == null || unitType.isStatic()) continue;
            float dx = sim.world().x(unit) - body.x, dy = sim.world().y(unit) - body.y;
            float along = dx * fx + dy * fy;
            if (along <= 0f || along > reach || Math.abs(dx * fy - dy * fx) > halfW + .8f) continue;
            friendly = Math.min(friendly, along - halfL);
        }
        if (friendly < Float.MAX_VALUE) cap = Math.min(cap, Math.max(CRAWL, friendly * .8f));
        mission.authoredRoutePaused = false;
        mission.speedCap = cap;
    }

    private static void hold(VehicleMission mission) {
        mission.authoredRoutePaused = true;
        mission.speedCap = 0f;
    }

    private void route(BattleSimulation sim, long id, Plan plan, int tick) {
        VehicleMission mission = sim.convoy().mission(id);
        GroundBody body = sim.convoy().body(id);
        VehicleType type = sim.convoy().vehicleType(id);
        float[][] lane = lane(body.x, body.y, plan.goalX, plan.lane, weave(plan.role),
                wavelength(id), phase(id), plan.role == Role.TECHNICAL ? null : type);
        mission.authoredRouteX = lane[0];
        mission.authoredRouteY = lane[1];
        mission.authoredLeg = plan.leg;
        mission.authoredRoutePaused = false;
        plan.lastRouteTick = tick;
    }

    private static float weave(Role role) {
        return switch (role) { case CARGO -> 1.1f; case ESCORT -> .6f; case HEAVY -> .5f; case TECHNICAL -> 0f; };
    }

    private static float wavelength(long id) { return 46f + (hash(id) & 15); }
    private static float phase(long id) { return ((hash(id) >>> 8) & 1023) / 1024f * (float) (Math.PI * 2); }
    private static long hash(long id) {
        long h = id * 0x9E3779B97F4A7C15L;
        return h ^ (h >>> 31);
    }

    /**
     * A weaving lane from the current pose to {@code goalX}: merges from the
     * current y onto the lane over a distance that keeps the turn well inside
     * the chassis' curvature, weaves about it, and straightens over the last
     * stretch so the hull arrives on the lane heading east. A null
     * {@code type} skips the band clamp (the technical rides the shoulder).
     */
    static float[][] lane(float fromX, float fromY, float goalX, float laneY, float amplitude,
                          float wavelength, float phase, VehicleType type) {
        float span = goalX - fromX;
        if (span <= WAYPOINT_PITCH * 2f) return new float[][]{{fromX, goalX}, {fromY, laneY}};
        float merge = Math.min(span * .5f, Math.max(14f, Math.abs(laneY - fromY) * 6f));
        float settle = Math.min(span * .25f, 12f);
        float straightFrom = Math.min(goalX, ThunderRunCompound.WIDTH - 8f) - settle;
        List<float[]> points = new ArrayList<>();
        points.add(new float[]{fromX, fromY});
        for (float x = fromX + WAYPOINT_PITCH; x < goalX - WAYPOINT_PITCH * .5f; x += WAYPOINT_PITCH) {
            float t = smooth(Math.min(1f, (x - fromX) / merge));
            float wave = amplitude * t * (float) Math.sin(x / wavelength * Math.PI * 2 + phase);
            if (x > straightFrom) wave *= Math.max(0f, 1f - (x - straightFrom) / settle);
            float y = fromY + (laneY - fromY) * t + wave;
            if (type != null) y = clampBand(y, type);
            points.add(new float[]{x, y});
        }
        points.add(new float[]{goalX, type != null ? clampBand(laneY, type) : laneY});
        float[] xs = new float[points.size()], ys = new float[points.size()];
        for (int i = 0; i < xs.length; i++) { xs[i] = points.get(i)[0]; ys[i] = points.get(i)[1]; }
        return new float[][]{xs, ys};
    }

    private static float smooth(float t) { return t * t * (3f - 2f * t); }
    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }
}
