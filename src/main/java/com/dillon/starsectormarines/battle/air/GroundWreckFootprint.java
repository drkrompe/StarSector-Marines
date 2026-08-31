package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.LongBucket;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import it.unimi.dsi.fastutil.longs.LongArrayList;

/**
 * Stamps a burnt airframe's footprint into the ground as a non-walkable,
 * see-through obstacle, stepping clear whoever the hull is coming down on top
 * of.
 *
 * <p>Shared by a wreck settling onto its own hardstand ({@link
 * AirfieldSystem}) and one settling wherever a taxiing aircraft went down
 * ({@link AirSystem}) — the ground does not care which death path put the
 * hull there, only that a patch of it just stopped being flat concrete. What
 * differs between the two callers is how the candidate units worth checking
 * are gathered — a full roster walk where one is already at hand, a
 * spatial-index reach where it is not — so gathering is the caller's job and
 * {@code nearby} must already hold every unit that could plausibly be inside
 * the footprint or its step-clear ring.
 *
 * <p><b>It blocks movement and nothing else.</b> The cells are explicitly
 * marked see-through, because a non-walkable cell is opaque here unless it
 * says otherwise, and a burnt-out airframe is a frame with holes in it — you
 * can see and shoot straight across ground covered in wreckage. That is
 * deliberately not how the intact scenery hulls dressing civilian berths
 * behave: a whole aircraft is a solid object.
 *
 * <p><b>A cell somebody could not be stepped clear of stays open.</b> A wreck
 * that settled on top of a survivor would seal them into a cell they can never
 * leave, and a unit that cannot move stops answering its orders for the rest
 * of the battle — a gap in the wreckage is by far the cheaper wrong.
 */
final class GroundWreckFootprint {

    /** Half-extent of the footprint, cells — the same 3x3 ground an aircraft hull covers standing anywhere else on the map. */
    private static final int HALF = ParkedAircraft.FOOTPRINT_HALF;

    /**
     * How far from their own cell somebody caught under a settling wreck is
     * allowed to be moved. Chebyshev rings, so this is the ground immediately
     * around the hull — a step out from under it, not a relocation.
     */
    private static final int STEP_CLEAR_RADIUS = 3;

    private GroundWreckFootprint() {
    }

    /**
     * Settles a wreck centred on {@code (centerX, centerY)}.
     *
     * <p>Gathered before anybody is moved: displacing mid-walk would have the
     * scan reading positions it has already passed judgement on.
     */
    static void settle(NavigationGrid grid, CellTopology topology, World world,
                       LongBucket nearby, int centerX, int centerY) {
        LongArrayList caught = new LongArrayList();
        for (int i = 0; i < nearby.size; i++) {
            long u = nearby.ids[i];
            if (within(world.cellX(u), world.cellY(u), centerX, centerY)) caught.add(u);
        }
        for (int i = 0; i < caught.size(); i++) {
            stepClear(grid, world, nearby, caught.getLong(i), centerX, centerY);
        }

        for (int y = centerY - HALF; y <= centerY + HALF; y++) {
            for (int x = centerX - HALF; x <= centerX + HALF; x++) {
                if (!grid.inBounds(x, y)) continue;
                if (occupied(world, nearby, x, y)) continue;
                grid.setWalkable(x, y, false);
                grid.setSeeThrough(x, y, true);
                topology.setVehicle(x, y, true);
            }
        }
        // Cover is derived from the neighbourhood, so the ring around the new
        // obstacle is stale until it is asked again.
        for (int y = centerY - HALF - 1; y <= centerY + HALF + 1; y++) {
            for (int x = centerX - HALF - 1; x <= centerX + HALF + 1; x++) {
                if (grid.inBounds(x, y)) grid.recomputeCoverAt(x, y);
            }
        }
    }

    /**
     * Moves one unit to the nearest free cell outside the wreck's footprint.
     *
     * <p>Box-spiral outward from where they are standing, so somebody at the
     * edge of the hull steps one cell off it rather than being sent to a
     * canonical corner with everybody else. Leaves the unit exactly where it
     * is when nothing within reach will take it — {@link #settle} then
     * declines to close that cell.
     */
    private static void stepClear(NavigationGrid grid, World world, LongBucket nearby,
                                  long unit, int centerX, int centerY) {
        int fromX = world.cellX(unit);
        int fromY = world.cellY(unit);
        for (int r = 1; r <= STEP_CLEAR_RADIUS; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    int x = fromX + dx;
                    int y = fromY + dy;
                    if (!grid.inBounds(x, y)) continue;
                    if (within(x, y, centerX, centerY)) continue;
                    if (!grid.isWalkable(x, y)) continue;
                    if (occupied(world, nearby, x, y)) continue;
                    world.setCellPos(unit, x, y);
                    return;
                }
            }
        }
    }

    /** Whether {@code (x, y)} is inside the wreck's square footprint. */
    private static boolean within(int x, int y, int centerX, int centerY) {
        return Math.abs(x - centerX) <= HALF && Math.abs(y - centerY) <= HALF;
    }

    /** Whether any candidate unit is standing in {@code (x, y)}. */
    private static boolean occupied(World world, LongBucket nearby, int x, int y) {
        for (int i = 0; i < nearby.size; i++) {
            long u = nearby.ids[i];
            if (world.cellX(u) == x && world.cellY(u) == y) return true;
        }
        return false;
    }
}
