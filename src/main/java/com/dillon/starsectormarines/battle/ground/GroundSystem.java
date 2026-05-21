package com.dillon.starsectormarines.battle.ground;

import com.dillon.starsectormarines.battle.MarineLoadout;
import com.dillon.starsectormarines.battle.Squad;
import com.dillon.starsectormarines.battle.Unit;
import com.dillon.starsectormarines.battle.UnitType;
import com.dillon.starsectormarines.battle.air.AirSimContext;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/**
 * Owns every ground vehicle in the battle and drives them each tick.
 * Today that's just the convoy trucks; future entries (player-side APCs,
 * armored cars) hook in here as they come online.
 *
 * <p>Mirrors {@link com.dillon.starsectormarines.battle.air.AirSystem}'s
 * shape — narrow context handle, per-tick state-machine pass, BFS deboard
 * scan — minus the air-only flourish (altitude lerp, hover-station, mounted
 * turrets). Kinematics differ: ground vehicles use the {@link GroundBody}
 * abstraction (currently {@link BicycleBody}, future tank/etc) driven by
 * a pure-pursuit carrot along the polyline, instead of the shuttle's
 * "rotate-then-thrust" hover model. The carrot keeps moving forward along
 * the path so trucks never orbit a stationary waypoint, and the bicycle
 * model gives the front-steer-rear-follows feel without needing per-corner
 * tuning.
 */
public class GroundSystem {

    /** Distance threshold (cells) for landing on the LZ — final waypoint. Tight so the snap-to-LZ at LANDED is invisible. */
    private static final float LZ_ARRIVAL_DIST = 0.25f;
    /** Distance threshold (cells) at which a DEPARTING vehicle hits its final exit waypoint and transitions to GONE. */
    private static final float EXIT_ARRIVAL_DIST = 1.0f;
    /** Max BFS radius from the LZ when looking for a free deboard cell. Past this we drop the deboard for this tick and retry. */
    private static final int DEBOARD_SCAN_RADIUS = 5;

    private final List<Vehicle> vehicles = new ArrayList<>();

    public List<Vehicle> getVehicles() { return vehicles; }
    public void add(Vehicle v) { vehicles.add(v); }

    /**
     * Advances every ground vehicle one tick by {@code dt} seconds. Same
     * fixed-tick contract as {@link com.dillon.starsectormarines.battle.air.AirSystem#tick}
     * — caller is responsible for matching {@code dt} to its tick cadence.
     */
    public void tick(AirSimContext ctx, float dt) {
        for (Vehicle v : vehicles) {
            switch (v.state) {
                case PENDING:
                    v.pendingDelay -= dt;
                    if (v.pendingDelay <= 0f) v.state = Vehicle.State.INCOMING;
                    break;

                case INCOMING:
                    advancePath(v, v.inboundX, v.inboundY, dt, true);
                    break;

                case LANDED:
                    v.deboardCountdown -= dt;
                    if (v.deboardCountdown <= 0f && v.marinesRemaining > 0) {
                        if (tryDeboardMarine(v, ctx)) {
                            v.marinesRemaining--;
                        }
                        v.deboardCountdown = v.type.deboardInterval;
                    }
                    if (v.marinesRemaining == 0) {
                        // Reset the waypoint cursor for the outbound queue.
                        // Start at index 1 — the truck is already at outbound[0]
                        // (typically same cell as the LZ for V1's reverse path)
                        // and steering toward outbound[1].
                        v.waypointIndex = 1;
                        v.state = Vehicle.State.DEPARTING;
                    }
                    break;

                case DEPARTING:
                    advancePath(v, v.outboundX, v.outboundY, dt, false);
                    break;

                case GONE:
                default:
                    break;
            }
        }
    }

    /**
     * Pure-pursuit path follower. Each tick:
     * <ol>
     *   <li>Pick a carrot {@code lookAheadCells} along the polyline ahead of
     *       the body — {@link PurePursuit#pick} also advances
     *       {@code v.waypointIndex} past any waypoint the body has crossed.</li>
     *   <li>Compute target speed from remaining path length so the body
     *       brakes to rest at the final waypoint:
     *       {@code min(maxSpeed, sqrt(2·brake·remaining))}. Intermediate
     *       corners don't taper speed explicitly — the bicycle's steering
     *       constraint takes care of cornering geometry.</li>
     *   <li>Tick the body toward the carrot at the target speed; the body's
     *       kinematic model (bicycle / future tank) decides how that
     *       translates into pose updates.</li>
     *   <li>If within the terminal arrival threshold of the last waypoint,
     *       transition state. INCOMING snaps to the LZ for a clean stop
     *       (the brake taper is asymptotic and would creep the last
     *       fraction of a cell otherwise); DEPARTING flips to GONE.</li>
     * </ol>
     */
    private void advancePath(Vehicle v, float[] xs, float[] ys, float dt, boolean isInbound) {
        PurePursuit.Carrot carrot = PurePursuit.pick(
                v.body.x, v.body.y, xs, ys, v.waypointIndex, v.type.lookAheadCells);
        v.waypointIndex = carrot.nextIdx;

        float remaining = PurePursuit.remainingPathLength(
                v.body.x, v.body.y, xs, ys, v.waypointIndex);
        float taper = (float) Math.sqrt(2f * v.type.brakingAccel * Math.max(0f, remaining));
        float targetSpeed = Math.min(v.type.maxSpeed, taper);

        v.body.tick(carrot.x, carrot.y, targetSpeed, dt);

        int lastIdx = xs.length - 1;
        float distToLast = v.body.distanceTo(xs[lastIdx], ys[lastIdx]);
        float threshold = isInbound ? LZ_ARRIVAL_DIST : EXIT_ARRIVAL_DIST;
        if (distToLast < threshold) {
            if (isInbound) {
                v.body.teleport(xs[lastIdx], ys[lastIdx], v.body.facingDegrees);
                v.state = Vehicle.State.LANDED;
                v.deboardCountdown = v.type.deboardInterval;
            } else {
                v.state = Vehicle.State.GONE;
            }
        }
    }

    /**
     * Finds a free cell adjacent to the LZ and spawns a militia there as a
     * fresh {@link Unit}. Same BFS shape as the shuttle deboard — copied
     * rather than shared so the air/ground split stays clean and the BFS
     * is small enough that duplication isn't a real cost.
     */
    private boolean tryDeboardMarine(Vehicle v, AirSimContext ctx) {
        int lzCellX = (int) Math.floor(v.lzX);
        int lzCellY = (int) Math.floor(v.lzY);
        int[] cell = findDeboardCell(lzCellX, lzCellY, ctx);
        if (cell == null) return false;
        Unit marine = new Unit(ctx.nextMarineId(), v.faction, UnitType.MARINE, cell[0], cell[1]);
        int slot = v.type.capacity - v.marinesRemaining;
        MarineLoadout loadout = (v.marineLoadout != null && slot < v.marineLoadout.length)
                ? v.marineLoadout[slot] : null;
        if (loadout != null) {
            marine.role = loadout.role;
            marine.assignedObjective = loadout.objective;
            if (loadout.primary != null) {
                marine.primaryWeapon = loadout.primary;
                marine.attackRange = loadout.primary.range;
                marine.attackDamage = loadout.primary.damage;
                marine.accuracy = loadout.primary.accuracy;
                marine.attackCooldown = loadout.primary.cooldown;
            }
            if (loadout.secondary != null && loadout.secondaryAmmo > 0) {
                marine.secondaryWeapon = loadout.secondary;
                marine.secondaryAmmo = loadout.secondaryAmmo;
            }
        }
        if (v.squadId == Unit.NO_SQUAD) {
            v.squadId = ctx.mintSquad(v.faction, marine);
        }
        marine.squadId = v.squadId;
        // Ratchet peak-strength for the SurviveContact predicate, matching
        // the shuttle-deboard path.
        Squad squad = ctx.getSquad(v.squadId);
        if (squad != null) squad.originalSize++;
        ctx.addUnit(marine);
        return true;
    }

    /**
     * BFS outward from the LZ cell for the first walkable, unoccupied cell
     * at distance ≥ 1. Distance 0 (the LZ itself) is skipped so the marine
     * sprite doesn't draw directly under the parked truck.
     */
    private int[] findDeboardCell(int lzX, int lzY, AirSimContext ctx) {
        NavigationGrid grid = ctx.getGrid();
        Set<Long> seen = new HashSet<>();
        Queue<int[]> q = new ArrayDeque<>();
        q.add(new int[]{lzX, lzY, 0});
        seen.add(((long) lzX << 32) | (lzY & 0xFFFFFFFFL));
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!q.isEmpty()) {
            int[] p = q.poll();
            if (p[2] > DEBOARD_SCAN_RADIUS) continue;
            if (p[2] > 0
                    && grid.inBounds(p[0], p[1])
                    && grid.isWalkable(p[0], p[1])
                    && !ctx.isCellOccupied(p[0], p[1])) {
                return new int[]{p[0], p[1]};
            }
            for (int[] d : dirs) {
                int nx = p[0] + d[0];
                int ny = p[1] + d[1];
                if (!grid.inBounds(nx, ny)) continue;
                long k = ((long) nx << 32) | (ny & 0xFFFFFFFFL);
                if (!seen.add(k)) continue;
                q.add(new int[]{nx, ny, p[2] + 1});
            }
        }
        return null;
    }
}
