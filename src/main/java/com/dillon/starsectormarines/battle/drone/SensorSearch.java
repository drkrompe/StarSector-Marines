package com.dillon.starsectormarines.battle.drone;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.ConvoyService;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.LongBucket;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.vehicle.HullSightings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Where a hull's sensor drones look: a search of the ground round the hull, and a shadow on whatever the hull
 * cannot see for itself.
 *
 * <p><b>Search.</b> The ring round the hull is cut into four quadrants. The wing remembers when each was last in
 * sight of one of its own observers, a sensor drone, from a few sample cells at fixed ranges, and sends
 * each sensor to the stalest quadrant not already taken, leaning toward the way the hull faces. A drone holds its
 * quadrant for {@link #DWELL_TICKS} and moves on when it reads fresh, so the ground goes stale and is searched
 * again. The memory is the wing's own, built from the same line-of-sight test the drones' sightings use, so it works
 * for any side and reads nothing from the player's fog.
 *
 * <p><b>Shadow.</b> A sensor that sees a hostile its hull has no line of sight to takes it as a shadow target, an
 * armoured one before a soft one, and the nearest first. That drone holds a point between the hull and the target,
 * {@link #SHADOW_STANDOFF_CELLS} short of it and within the hull's leash, chosen to keep a line to it, while the
 * other keeps searching. A target out of sight is chased to where it was last seen for {@link #LAST_KNOWN_TICKS},
 * then let go. This is what holds vision for a launcher that fires on the call of somebody else.
 *
 * <p>Planned in the serial part of the tick every {@link #PLAN_TICKS}, and deterministic: stations are a function of
 * the world and the tick, with ties broken by id and quadrant order.
 */
final class SensorSearch {

    static final int PLAN_TICKS = 10;
    /** How far from the hull a searching sensor holds station, along its quadrant's bisector, in cells. */
    static final float SEARCH_RADIUS_CELLS = 12f;
    /** How far short of its shadow target a drone holds, in cells; inside its sight with margin. */
    static final float SHADOW_STANDOFF_CELLS = 26f;
    /** A quadrant sampled this recently reads fresh. */
    static final int FRESH_TICKS = 60;
    /** How long a sensor holds a quadrant before it may move on. */
    static final int DWELL_TICKS = 150;
    /** How long a shadow target may be out of every sensor's sight before it is chased to where it was. */
    static final int LOST_TICKS = 60;
    /** How long a lost target is chased to its last-known place before it is let go. */
    static final int LAST_KNOWN_TICKS = 240;
    /** Staleness is capped here, so an old quadrant does not outweigh the way the hull faces forever. */
    static final int STALE_CAP_TICKS = 600;
    /** What facing a quadrant squarely is worth, in ticks of staleness. */
    static final float LOOK_BONUS_TICKS = 150f;

    private static final int QUADRANTS = 4;
    private static final int NEVER = Integer.MIN_VALUE / 2;
    /** Sample cells of a quadrant, past the near ground the hull sees: {range, offset from the bisector in degrees}. */
    private static final float[][] SAMPLES = {{30f, 0f}, {34f, -30f}, {34f, 30f}, {38f, 0f}};
    /** Samples a quadrant needs in sight to read fresh: a drone in the next quadrant over sees at most two. */
    private static final int SAMPLES_TO_READ_FRESH = 3;
    private static final float[] STANDOFF_BEARINGS = {0f, 25f, -25f, 50f, -50f, 75f, -75f};

    private final int[] seenTick = new int[QUADRANTS];
    private final Map<Long, Integer> quadrantOf = new HashMap<>();
    private final Map<Long, Integer> assignedTick = new HashMap<>();
    private final LongBucket scratch = new LongBucket();
    private Map<Long, float[]> stations = Map.of();
    private int nextPlan;

    private long shadowTarget;
    private long shadowDrone;
    private int lostSince = -1;
    private float lastKnownX, lastKnownY;

    SensorSearch() {
        Arrays.fill(seenTick, NEVER);
    }

    /** The target a drone is shadowing, or {@code 0L}. */
    long shadowTarget() { return shadowTarget; }

    /** The sensor holding the shadow, or {@code 0L}. */
    long shadowDrone() { return shadowDrone; }

    /** The station each live sensor holds, replanned every {@link #PLAN_TICKS}. Empty when the wing has no sensor out. */
    Map<Long, float[]> plan(DroneWing wing, BattleSimulation sim, Faction side, int tick) {
        if (tick < nextPlan) return stations;
        nextPlan = tick + PLAN_TICKS;

        List<Long> sensors = new ArrayList<>();
        for (Long id : wing.drones()) {
            if (wing.role(id) == DroneRole.SENSOR && sim.resolveUnit(id) != 0L) sensors.add(id);
        }
        Collections.sort(sensors);
        quadrantOf.keySet().retainAll(sensors);
        assignedTick.keySet().retainAll(sensors);
        if (sensors.isEmpty()) {
            shadowTarget = 0L;
            shadowDrone = 0L;
            stations = Map.of();
            return stations;
        }

        if (shadowDrone != 0L && !sensors.contains(shadowDrone)) shadowDrone = shadowTarget == 0L ? 0L : sensors.get(0);
        NavigationGrid grid = sim.getGrid();
        ConvoyService convoy = sim.convoy();
        float hx = wing.anchorX(), hy = wing.anchorY();

        sample(sim, grid, sensors, hx, hy, tick);
        chooseShadow(wing, sim, side, grid, sensors, hx, hy, tick);

        Map<Long, float[]> next = new HashMap<>();
        boolean[] taken = new boolean[QUADRANTS];
        if (shadowDrone != 0L) {
            float[] at = shadowStation(grid, hx, hy);
            if (at != null) next.put(shadowDrone, at);
        }
        for (Long id : sensors) {
            if (next.containsKey(id)) continue;
            Integer q = quadrantOf.get(id);
            if (q != null) taken[q] = true;
        }
        for (Long id : sensors) {
            if (next.containsKey(id)) continue;
            Integer q = quadrantOf.get(id);
            boolean holds = q != null && !(tick - assignedTick.get(id) >= DWELL_TICKS
                    && tick - seenTick[q] < FRESH_TICKS);
            if (!holds) {
                if (q != null) taken[q] = false;
                q = stalest(taken, wing.anchorHeadingDegrees(), tick);
                taken[q] = true;
                quadrantOf.put(id, q);
                assignedTick.put(id, tick);
            }
            float a = (float) Math.toRadians(bisectorDegrees(q));
            next.put(id, new float[]{hx + SEARCH_RADIUS_CELLS * (float) Math.cos(a),
                    hy + SEARCH_RADIUS_CELLS * (float) Math.sin(a)});
        }
        stations = Map.copyOf(next);
        return stations;
    }

    /**
     * Marks each quadrant sampled now by a sensor with line of sight to enough of its sample cells. The hull is not
     * counted: the search is for ground the drones have looked at lately, which is what they add to its own sight.
     */
    private void sample(BattleSimulation sim, NavigationGrid grid, List<Long> sensors, float hx, float hy, int tick) {
        for (int q = 0; q < QUADRANTS; q++) {
            int seen = 0;
            for (float[] s : SAMPLES) {
                double a = Math.toRadians(bisectorDegrees(q) + s[1]);
                float px = hx + s[0] * (float) Math.cos(a);
                float py = hy + s[0] * (float) Math.sin(a);
                // Ground off the map has nothing in it to search.
                boolean visible = px < 0f || py < 0f || px >= grid.getWidth() || py >= grid.getHeight();
                for (int i = 0; i < sensors.size() && !visible; i++) {
                    long d = sensors.get(i);
                    visible = HullSightings.sees(grid, sim.world().x(d), sim.world().y(d), px, py,
                            DroneBaySystem.SENSOR_SIGHT_CELLS);
                }
                if (visible) seen++;
            }
            if (seen >= SAMPLES_TO_READ_FRESH) seenTick[q] = tick;
        }
    }

    private static float bisectorDegrees(int quadrant) {
        return quadrant * 90f + 45f;
    }

    /** The untaken quadrant with the most to search: how stale, and how squarely the hull faces it. */
    private int stalest(boolean[] taken, float headingDeg, int tick) {
        double h = Math.toRadians(headingDeg);
        float fx = (float) -Math.sin(h), fy = (float) Math.cos(h);
        int best = -1;
        float bestScore = -Float.MAX_VALUE;
        for (int q = 0; q < QUADRANTS; q++) {
            if (taken[q]) continue;
            double a = Math.toRadians(bisectorDegrees(q));
            float facing = Math.max(0f, fx * (float) Math.cos(a) + fy * (float) Math.sin(a));
            float score = Math.min(STALE_CAP_TICKS, tick - seenTick[q]) + LOOK_BONUS_TICKS * facing;
            if (score > bestScore) {
                bestScore = score;
                best = q;
            }
        }
        return best < 0 ? 0 : best;
    }

    // ---- shadow ----

    private void chooseShadow(DroneWing wing, BattleSimulation sim, Faction side, NavigationGrid grid,
                              List<Long> sensors, float hx, float hy, int tick) {
        if (shadowTarget != 0L && !alive(sim, shadowTarget)) release();

        long best = 0L;
        long bestDrone = 0L;
        int bestRank = -1;
        float bestDistance = Float.MAX_VALUE;
        boolean heldInSight = false;
        for (Long drone : sensors) {
            float dx = sim.world().x(drone), dy = sim.world().y(drone);
            scratch.clear();
            sim.getUnitIndex().gatherHostileCombatants(dx, dy, DroneBaySystem.SENSOR_SIGHT_CELLS, side, scratch);
            for (int k = 0; k < scratch.size; k++) {
                long target = scratch.ids[k];
                if (!sim.getRoster().isLive(target)) continue;
                float tx = sim.world().x(target), ty = sim.world().y(target);
                if (!HullSightings.sees(grid, dx, dy, tx, ty, DroneBaySystem.SENSOR_SIGHT_CELLS)) continue;
                int rank = sim.identity().type(target).isMech() ? 2 : 1;
                float distance = (float) Math.hypot(tx - hx, ty - hy);
                if (target == shadowTarget) {
                    heldInSight = true;
                    shadowDrone = drone;
                    lastKnownX = tx;
                    lastKnownY = ty;
                    lostSince = -1;
                }
                if (hullSees(grid, hx, hy, tx, ty)) continue;
                if (rank > bestRank || (rank == bestRank && distance < bestDistance)) {
                    bestRank = rank;
                    bestDistance = distance;
                    best = target;
                    bestDrone = drone;
                }
            }
            ConvoyService convoy = sim.convoy();
            for (int i = 0, n = convoy.vehicleCount(); i < n; i++) {
                long target = convoy.vehicleAt(i);
                Faction other = convoy.faction(target);
                if (other == null || !side.hostileTo(other) || !convoy.isPresent(target)) continue;
                GroundBody body = convoy.body(target);
                if (!HullSightings.sees(grid, dx, dy, body.x, body.y, DroneBaySystem.SENSOR_SIGHT_CELLS)) continue;
                float distance = (float) Math.hypot(body.x - hx, body.y - hy);
                if (target == shadowTarget) {
                    heldInSight = true;
                    shadowDrone = drone;
                    lastKnownX = body.x;
                    lastKnownY = body.y;
                    lostSince = -1;
                }
                if (hullSees(grid, hx, hy, body.x, body.y)) continue;
                // A hull is the heaviest thing to shell, so it ranks above a mech.
                if (3 > bestRank || (3 == bestRank && distance < bestDistance)) {
                    bestRank = 3;
                    bestDistance = distance;
                    best = target;
                    bestDrone = drone;
                }
            }
        }

        if (shadowTarget != 0L) {
            if (heldInSight) return;
            if (lostSince < 0) lostSince = tick;
            if (tick - lostSince > LOST_TICKS + LAST_KNOWN_TICKS) release();
            if (shadowTarget != 0L) return;
        }
        if (best != 0L) {
            shadowTarget = best;
            shadowDrone = bestDrone;
            lostSince = -1;
            float[] at = positionOf(sim, best);
            lastKnownX = at[0];
            lastKnownY = at[1];
        }
    }

    private void release() {
        shadowTarget = 0L;
        shadowDrone = 0L;
        lostSince = -1;
    }

    private static boolean alive(BattleSimulation sim, long id) {
        ConvoyService convoy = sim.convoy();
        if (convoy.isVehicle(id)) return convoy.isPresent(id);
        return sim.getRoster().isLive(id);
    }

    private static float[] positionOf(BattleSimulation sim, long id) {
        ConvoyService convoy = sim.convoy();
        if (convoy.isVehicle(id)) {
            GroundBody body = convoy.body(id);
            return new float[]{body.x, body.y};
        }
        return new float[]{sim.world().x(id), sim.world().y(id)};
    }

    private static boolean hullSees(NavigationGrid grid, float hx, float hy, float tx, float ty) {
        return grid.hasLineOfSight((int) Math.floor(hx), (int) Math.floor(hy), (int) Math.floor(tx), (int) Math.floor(ty));
    }

    /**
     * The point the shadowing drone holds: toward the target from the hull, {@link #SHADOW_STANDOFF_CELLS} short of
     * it and no farther out than the hull's leash, turned off the straight line until it has a line to the target. A
     * target lost from sight is chased to where it was last seen.
     */
    private float[] shadowStation(NavigationGrid grid, float hx, float hy) {
        float tx = lastKnownX, ty = lastKnownY;
        float dx = tx - hx, dy = ty - hy;
        float distance = (float) Math.hypot(dx, dy);
        if (distance < 1e-3f) return null;
        float out = Math.max(4f, Math.min(DroneWing.LEASH_CELLS, distance - SHADOW_STANDOFF_CELLS));
        double base = Math.atan2(dy, dx);
        float[] fallback = null;
        for (float offset : STANDOFF_BEARINGS) {
            double a = base + Math.toRadians(offset);
            float sx = hx + out * (float) Math.cos(a);
            float sy = hy + out * (float) Math.sin(a);
            if (fallback == null) fallback = new float[]{sx, sy};
            if (lostSince >= 0) return fallback;
            if (grid.hasLineOfSight((int) Math.floor(sx), (int) Math.floor(sy),
                    (int) Math.floor(tx), (int) Math.floor(ty))) {
                return new float[]{sx, sy};
            }
        }
        return fallback;
    }
}
