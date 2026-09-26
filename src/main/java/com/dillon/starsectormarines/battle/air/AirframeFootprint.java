package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.ManualTerrainMotion;
import com.dillon.starsectormarines.battle.mech.MechSpawnPlacement;
import com.dillon.starsectormarines.battle.sim.IdentityService;
import java.util.function.LongToDoubleFunction;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.LongBucket;
import com.dillon.starsectormarines.battle.unit.StandingRoom;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import it.unimi.dsi.fastutil.longs.LongArrayList;

/**
 * Closes the ground a <em>wrecked</em> airframe is lying on.
 *
 * <p>Two arrivals, one patch of ground: a wreck settling onto the hardstand it
 * was parked on, and a wreck settling wherever a taxiing aircraft went down
 * ({@link AirSystem}). The ground does not care which of them put a hulk there,
 * only that a patch of it just stopped being flat concrete.
 *
 * <p><b>An intact aircraft writes no terrain at all.</b> It is a unit standing
 * on its cell, and everything that follows from that — being seen, gated by
 * fog, traced against line of sight, hit, attributed, killed — follows from
 * being a unit rather than from a second copy of the aircraft stamped into the
 * navigation grid. An earlier version stamped a 3x3 around every parked hull,
 * with an opaque centre, a step-clear for whoever was under the wheels, and a
 * packed bit mask remembering which cells and which marks to restore on
 * departure. All of that existed to keep one thing true — an aircraft is
 * something you walk round — at the cost of a second representation of the
 * aircraft that had to be given back exactly, by every ending, forever. The
 * hull covers a cell now, like every other body on the field.
 *
 * <p>The one case the stamp genuinely carried is handled elsewhere and better:
 * an arriving hull that lands on somebody steps them aside, which
 * {@code UnitRosterService.settleFooting} does for every immobile arrival —
 * a turret on its mount, a machine off a shed's stocks, an aircraft on its
 * stand — rather than once per kind.
 *
 * <p>A wreck still writes, because a wreck is not a unit. The hull that died is
 * dead, released, and never coming back; what is left on the concrete is drawn
 * off the berth and is nobody's body. Terrain is the only place left to say it
 * is there.
 *
 * <p><b>A wreck comes down once and is see-through.</b> A burnt-out airframe is
 * a frame with holes in it, and an apron strewn with them is still an apron you
 * can cover by fire. It blocks movement across its whole footprint and sight
 * across none of it.
 */
final class AirframeFootprint {

    /** Half-extent of the footprint, cells — the 3x3 ground a downed hull covers wherever it lies. */
    private static final int HALF = 1;

    /**
     * How far from their own cell somebody caught under a settling wreck is
     * allowed to be moved. Chebyshev rings, so this is the ground immediately
     * around the hull: a step out from under it, not a relocation.
     */
    private static final int STEP_CLEAR_RADIUS = 3;

    private AirframeFootprint() {
    }

    /**
     * Settles a wreck centred on {@code (centerX, centerY)}, blocking movement
     * and nothing else, and stepping clear whoever the hull is coming down on.
     *
     * <p>Everybody is gathered before anybody is moved: displacing mid-walk
     * would have the scan reading positions it has already passed judgement on.
     * A cell nobody could be stepped out of is left open — a unit sealed into a
     * cell it can never leave stops answering its orders for the rest of the
     * battle, which is far worse than a hull with a gap in it.
     */
    static void settleWreck(NavigationGrid grid, CellTopology topology, World world,
                            LongBucket nearby, int centerX, int centerY,
                            IdentityService identity, LongToDoubleFunction radius) {
        LongArrayList caught = new LongArrayList();
        for (int i = 0; i < nearby.size; i++) {
            long u = nearby.ids[i];
            // Only somebody standing in a cell can be stepped out of one. The
            // scan reaches every body near the hull, and some of them —
            // a convoy chassis, the aircraft that is dying here — move on their
            // own kinematics and have no cell to be moved to.
            if (!world.hasPosition(u)) continue;
            if (within(world.cellX(u), world.cellY(u), centerX, centerY)) caught.add(u);
        }
        for (int i = 0; i < caught.size(); i++) {
            stepClear(grid, world, nearby, caught.getLong(i), centerX, centerY, identity, radius);
        }

        for (int y = centerY - HALF; y <= centerY + HALF; y++) {
            for (int x = centerX - HALF; x <= centerX + HALF; x++) {
                if (!grid.inBounds(x, y)) continue;
                if (occupied(world, nearby, x, y) || mechOverlaps(world, nearby, identity, radius, x, y)) continue;
                grid.setWalkable(x, y, false);
                grid.setSeeThrough(x, y, true);
                topology.setVehicle(x, y, true);
            }
        }
        recomputeCoverAround(grid, centerX, centerY);
    }

    /**
     * Moves one unit to the nearest free cell outside the wreck's footprint.
     *
     * <p>Box-spiral outward from where they are standing, so somebody at the
     * edge of the hull steps one cell off it rather than being sent to a
     * canonical corner with everybody else. Leaves the unit exactly where it
     * is when nothing within reach will take it — the stamp then declines to
     * close that cell.
     */
    private static void stepClear(NavigationGrid grid, World world, LongBucket nearby,
                                  long unit, int centerX, int centerY,
                                  IdentityService identity, LongToDoubleFunction radius) {
        if (identity.mechVariant(unit) != null) {
            if (!world.hasMovement(unit)) return;
            stepMechClear(grid, world, nearby, unit, centerX, centerY, identity, radius);
            return;
        }
        long cell = StandingRoom.nearest(world.cellX(unit), world.cellY(unit), STEP_CLEAR_RADIUS,
                (x, y) -> grid.inBounds(x, y) && grid.isWalkable(x, y)
                        && !within(x, y, centerX, centerY),
                (x, y) -> occupied(world, nearby, x, y));
        if (cell == StandingRoom.NOWHERE) return;
        world.setCellPos(unit, StandingRoom.cellX(cell), StandingRoom.cellY(cell));
    }

    private static void stepMechClear(NavigationGrid grid, World world, LongBucket nearby,
                                       long unit, int centerX, int centerY,
                                       IdentityService identity, LongToDoubleFunction radii) {
        float radius = (float) radii.applyAsDouble(unit);
        int startX = world.cellX(unit);
        int startY = world.cellY(unit);
        for (int distance = 1; distance <= STEP_CLEAR_RADIUS; distance++) {
            for (int y = startY - distance; y <= startY + distance; y++) {
                for (int x = startX - distance; x <= startX + distance; x++) {
                    if (Math.max(Math.abs(x - startX), Math.abs(y - startY)) != distance) continue;
                    MechSpawnPlacement.Point point = MechSpawnPlacement.nearCell(grid, radius, x, y,
                            (px, py) -> !within((int) Math.floor(px), (int) Math.floor(py), centerX, centerY),
                            (px, py) -> ManualTerrainMotion.canSweepStraight(grid, world.x(unit), world.y(unit),
                                    px - world.x(unit), py - world.y(unit), radius)
                                    && clearOfBodies(world, nearby, identity, radii, unit, px, py, radius));
                    if (point == null) continue;
                    world.setPos(unit, point.x(), point.y());
                    return;
                }
            }
        }
    }

    private static boolean clearOfBodies(World world, LongBucket nearby, IdentityService identity,
                                          LongToDoubleFunction radii, long excluded, float x, float y, float radius) {
        for (int i = 0; i < nearby.size; i++) {
            long other = nearby.ids[i];
            if (other == excluded || !world.hasPosition(other) || !world.isAlive(other)) continue;
            if (identity.airframe(other) != null) {
                if ((int) Math.floor(x) == world.cellX(other)
                        && (int) Math.floor(y) == world.cellY(other)) return false;
                continue;
            }
            float dx = x - world.x(other);
            float dy = y - world.y(other);
            double separation = radius + radii.applyAsDouble(other);
            if (dx * dx + dy * dy < separation * separation) return false;
        }
        return true;
    }

    /** A body that could not step clear keeps its entire envelope open, including adjacent cells. */
    private static boolean mechOverlaps(World world, LongBucket nearby, IdentityService identity,
                                         LongToDoubleFunction radius, int x, int y) {
        for (int i = 0; i < nearby.size; i++) {
            long unit = nearby.ids[i];
            if (!world.hasPosition(unit) || identity.mechVariant(unit) == null) continue;
            if (MechSpawnPlacement.overlapsCell(world.x(unit), world.y(unit),
                    (float) radius.applyAsDouble(unit), x, y)) return true;
        }
        return false;
    }

    /**
     * Cover is derived from the neighbourhood, so the ring around a hull that
     * has just come down is stale until it is asked again.
     */
    private static void recomputeCoverAround(NavigationGrid grid, int centerX, int centerY) {
        for (int y = centerY - HALF - 1; y <= centerY + HALF + 1; y++) {
            for (int x = centerX - HALF - 1; x <= centerX + HALF + 1; x++) {
                if (grid.inBounds(x, y)) grid.recomputeCoverAt(x, y);
            }
        }
    }

    /** Whether {@code (x, y)} is inside the hull's square footprint. */
    private static boolean within(int x, int y, int centerX, int centerY) {
        return Math.abs(x - centerX) <= HALF && Math.abs(y - centerY) <= HALF;
    }

    /** Whether any candidate unit is standing in {@code (x, y)}. */
    private static boolean occupied(World world, LongBucket nearby, int x, int y) {
        for (int i = 0; i < nearby.size; i++) {
            long u = nearby.ids[i];
            // Same rule as the gather above, and the same reason: a body with
            // no cell holds none. It also keeps the aircraft that is dying here
            // from reserving the ground its own hull is about to come down on,
            // which would leave a hole in the middle of its wreck.
            if (!world.hasPosition(u)) continue;
            if (world.cellX(u) == x && world.cellY(u) == y) return true;
        }
        return false;
    }
}
