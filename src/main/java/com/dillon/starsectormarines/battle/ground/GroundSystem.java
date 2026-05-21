package com.dillon.starsectormarines.battle.ground;

import com.dillon.starsectormarines.battle.MarineLoadout;
import com.dillon.starsectormarines.battle.Squad;
import com.dillon.starsectormarines.battle.Unit;
import com.dillon.starsectormarines.battle.UnitType;
import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.air.AirSimContext;
import com.dillon.starsectormarines.battle.air.SteeringMode;
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
 * turrets). The kinematics are identical: each vehicle's {@link AirBody}
 * steers under its {@link VehicleType}'s {@link com.dillon.starsectormarines.battle.air.AirHandling}
 * profile. "Ground" is gameplay-facing: trucks consume a waypoint queue
 * along road centerlines instead of a single LZ point, and their handling
 * profile (high lateral damping, low turn rate) keeps them on rails.
 */
public class GroundSystem {

    /** Distance threshold (cells) for landing on the LZ — final waypoint. Tight so the snap-to-LZ at LANDED is invisible. */
    private static final float LZ_ARRIVAL_DIST = 0.25f;
    /** Distance threshold (cells) for advancing past an intermediate waypoint. Looser than the LZ so the truck doesn't hairpin around each cell-center on a straight run. */
    private static final float WAYPOINT_ARRIVAL_DIST = 0.7f;
    /** Distance threshold (cells) at which a DEPARTING vehicle hits its final exit waypoint and transitions to GONE. */
    private static final float EXIT_ARRIVAL_DIST = 1.0f;
    /** Max BFS radius from the LZ when looking for a free deboard cell. Past this we drop the deboard for this tick and retry. */
    private static final int DEBOARD_SCAN_RADIUS = 5;
    /**
     * Minimum dot-product between consecutive waypoint segments for the
     * look-ahead to treat the path as "still straight" and keep walking.
     * 0.5 = cos(60°); cell-list waypoints are axis-aligned so adjacent
     * segments are either dot=1 (aligned) or dot=0 (perpendicular). The
     * 0.5 threshold cleanly separates the two — straights walk through,
     * corners terminate the look-ahead at the corner cell.
     */
    private static final float LOOKAHEAD_ALIGN_COS = 0.5f;

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
     * Steer the body toward the look-ahead target found by walking the
     * waypoint queue forward through consecutive axis-aligned segments
     * (the same direction, within {@link #LOOKAHEAD_ALIGN_COS}). The
     * look-ahead is the last waypoint at the end of the current straight
     * run; brake-to-station targets it directly so the truck cruises at
     * full speed across long straights and decelerates only as the
     * corner approaches. At the corner, the look-ahead collapses to the
     * corner cell, the brake-formula tapers the speed, the heading slews
     * around, then the new look-ahead jumps to the end of the next
     * straight and the truck accelerates out of the turn.
     *
     * <p>Cursor advancement still happens on the immediate next waypoint
     * — the look-ahead only changes where we point, not where we are.
     * Same per-state-transition behavior as before:
     * {@code isInbound==true} flips to {@link Vehicle.State#LANDED} on
     * terminal arrival; {@code false} flips to {@link Vehicle.State#GONE}.
     */
    private void advancePath(Vehicle v, float[] xs, float[] ys, float dt, boolean isInbound) {
        int idx = v.waypointIndex;
        int lookIdx = findLookahead(xs, ys, idx);
        boolean last = idx >= xs.length - 1;
        float gx = xs[lookIdx];
        float gy = ys[lookIdx];
        v.body.tickToward(gx, gy, SteeringMode.BRAKE_TO_STATION, v.type, dt);
        float dist = v.body.distanceTo(xs[idx], ys[idx]);
        float threshold = last ? (isInbound ? LZ_ARRIVAL_DIST : EXIT_ARRIVAL_DIST)
                                : WAYPOINT_ARRIVAL_DIST;
        if (dist < threshold) {
            if (last) {
                if (isInbound) {
                    // Snap to LZ for a clean stop — the BRAKE_TO_STATION taper
                    // is asymptotic and would creep the last fraction of a cell.
                    v.body.teleport(xs[idx], ys[idx], v.body.facingDegrees);
                    v.state = Vehicle.State.LANDED;
                    v.deboardCountdown = v.type.deboardInterval;
                } else {
                    v.state = Vehicle.State.GONE;
                }
            } else {
                v.waypointIndex = idx + 1;
            }
        }
    }

    /**
     * Walk the waypoint queue forward from {@code startIdx} while each
     * successive segment stays aligned with the segment at startIdx (dot
     * product ≥ {@link #LOOKAHEAD_ALIGN_COS}). Returns the index of the
     * last aligned waypoint — that's the end of the current straight run,
     * or the corner cell at which the path turns. Empty / degenerate
     * inputs return {@code startIdx} unchanged.
     */
    private static int findLookahead(float[] xs, float[] ys, int startIdx) {
        int n = xs.length;
        if (startIdx >= n - 1) return startIdx;
        float baseDx = xs[startIdx + 1] - xs[startIdx];
        float baseDy = ys[startIdx + 1] - ys[startIdx];
        float baseLen = (float) Math.sqrt(baseDx * baseDx + baseDy * baseDy);
        if (baseLen < 1e-4f) return startIdx;
        float bDxN = baseDx / baseLen;
        float bDyN = baseDy / baseLen;
        int look = startIdx + 1;
        while (look < n - 1) {
            float fdx = xs[look + 1] - xs[look];
            float fdy = ys[look + 1] - ys[look];
            float fl = (float) Math.sqrt(fdx * fdx + fdy * fdy);
            if (fl < 1e-4f) break;
            float dot = (bDxN * fdx + bDyN * fdy) / fl;
            if (dot < LOOKAHEAD_ALIGN_COS) break;
            look++;
        }
        return look;
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
