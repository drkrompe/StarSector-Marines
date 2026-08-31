package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.LongBucket;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import it.unimi.dsi.fastutil.longs.LongArrayList;

/**
 * Closes the ground an airframe is standing on.
 *
 * <p>One patch of ground for three arrivals: a hull placed on its berth
 * ({@link AirfieldSystem}), a wreck settling onto that same hardstand, and a
 * wreck settling wherever a taxiing aircraft went down ({@link AirSystem}).
 * The ground does not care which of them put an aircraft there, only that a
 * patch of it just stopped being flat concrete.
 *
 * <p><b>A hull that arrives moves nobody; a hull that falls does.</b> The two
 * are not the same event. A wreck comes down once, on the tick something died,
 * and stepping the survivors out from under it is the alternative to sealing
 * them in. An aircraft is <em>placed</em> — at the start of the battle and
 * again every time a turnaround finishes — so a step-clear there would be a
 * free, repeatable shove that a defender gets for finishing a refit and an
 * attacker standing on the apron has no answer to. A placement therefore takes
 * only the cells nobody is standing in and leaves the rest open, which is the
 * same trade the wreck already makes one step earlier: a gap under the hull is
 * far cheaper than a body that cannot move. That gap lasts until the aircraft
 * next leaves and is placed again — nothing watches the cell for the moment it
 * vacates, because watching would cost a grid write per berth per tick to buy
 * back a cell somebody is standing in anyway.
 *
 * <p><b>A hull that is shot at may not blind itself.</b> A wreck is
 * see-through everywhere: a burnt-out airframe is a frame with holes in it, and
 * an apron strewn with them is still an apron you can cover by fire. An intact
 * hull is a solid object and says so on the cell it actually stands on — but
 * only there, because a sight line exempts its two endpoints and nothing else,
 * so a hull opaque across its whole footprint is a hull no round can reach.
 * That is not a hypothetical: it emptied the airfield raid outright, with six
 * riflemen four cells from three aircraft unable to scratch any of them, and
 * the aircraft's own explosion unable to reach the riflemen. This is the
 * convention a defence post already follows for the same reason — its turret
 * cell is opaque and every other cell of the emplacement is non-walkable and
 * see-through, and what those cells give is cover rather than concealment.
 * Movement is denied across the whole footprint either way.
 *
 * <p><b>What is taken is remembered, because it is not derivable.</b> The
 * square and the ground actually taken are different things: a cell somebody
 * is standing in is never closed, and a shed bay's own wall can lie inside the
 * square. {@link #stand} returns what it took <em>and</em> what it overwrote,
 * so {@link #lift} restores those cells to the state they were in rather than
 * declaring them floor — an apron that is handed back a little flatter on
 * every sortie corrodes without anybody seeing it happen.
 */
final class AirframeFootprint {

    /** Half-extent of the footprint, cells — the 3x3 ground an aircraft hull covers wherever it stands. */
    private static final int HALF = ParkedAircraft.FOOTPRINT_HALF;

    /** Cells on a side, and the stride each plane of the {@link #stand} mask is addressed on. */
    private static final int WIDTH = 2 * HALF + 1;

    /** Cells in the square, and the width of one plane of the mask. */
    private static final int CELLS = WIDTH * WIDTH;

    /**
     * The three bit planes {@link #stand} packs into its result: which cells
     * the hull took, and the two flags it overwrote on each of them. Restoring
     * needs the second and third — a cell is only taken if it was walkable, but
     * its sight and vehicle marks are whatever the map had put there.
     */
    private static final int TAKEN = 0;
    private static final int WAS_SEE_THROUGH = CELLS;
    private static final int WAS_VEHICLE = 2 * CELLS;

    /**
     * How far from their own cell somebody caught under a settling wreck is
     * allowed to be moved. Chebyshev rings, so this is the ground immediately
     * around the hull — a step out from under it, not a relocation.
     */
    private static final int STEP_CLEAR_RADIUS = 3;

    private AirframeFootprint() {
    }

    /**
     * Stands an intact hull on {@code (centerX, centerY)}.
     *
     * <p>{@code nearby} must hold every unit that could be inside the square;
     * a cell one of them is standing in is left open rather than closed over
     * them. Ground the aircraft did not take is left exactly as it was: a shed
     * bay's own wall can lie inside the square, and a hull does not own a wall
     * merely by parking beside it.
     *
     * @return what {@link #lift} needs to undo this exactly — the cells taken
     *         and the flags overwritten on them
     */
    static long stand(NavigationGrid grid, CellTopology topology, World world,
                      LongBucket nearby, int centerX, int centerY) {
        long taken = 0L;
        for (int y = centerY - HALF; y <= centerY + HALF; y++) {
            for (int x = centerX - HALF; x <= centerX + HALF; x++) {
                if (!grid.inBounds(x, y)) continue;
                if (!grid.isWalkable(x, y)) continue;
                if (occupied(world, nearby, x, y)) continue;
                taken |= bit(x, y, centerX, centerY, TAKEN);
                if (grid.isSeeThrough(x, y)) taken |= bit(x, y, centerX, centerY, WAS_SEE_THROUGH);
                if (topology.isVehicle(x, y)) taken |= bit(x, y, centerX, centerY, WAS_VEHICLE);
                grid.setWalkable(x, y, false);
                grid.setSeeThrough(x, y, x != centerX || y != centerY);
                topology.setVehicle(x, y, true);
            }
        }
        recomputeCoverAround(grid, centerX, centerY);
        return taken;
    }

    /**
     * Gives back exactly the ground {@code taken} names, in the state it was in
     * before the hull stood on it.
     */
    static void lift(NavigationGrid grid, CellTopology topology,
                     int centerX, int centerY, long taken) {
        if (taken == 0L) return;
        for (int y = centerY - HALF; y <= centerY + HALF; y++) {
            for (int x = centerX - HALF; x <= centerX + HALF; x++) {
                if ((taken & bit(x, y, centerX, centerY, TAKEN)) == 0L) continue;
                grid.setWalkable(x, y, true);
                grid.setSeeThrough(x, y,
                        (taken & bit(x, y, centerX, centerY, WAS_SEE_THROUGH)) != 0L);
                topology.setVehicle(x, y,
                        (taken & bit(x, y, centerX, centerY, WAS_VEHICLE)) != 0L);
            }
        }
        recomputeCoverAround(grid, centerX, centerY);
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
     *
     * <p>This is never stamped over an intact hull's own footprint. The berth
     * gives that ground back before the wreck asks for it, so the step-clear
     * and the occupancy reads below see the concrete rather than the dead
     * aircraft's own marks.
     */
    static void settleWreck(NavigationGrid grid, CellTopology topology, World world,
                            LongBucket nearby, int centerX, int centerY) {
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

    /**
     * Cover is derived from the neighbourhood, so the ring around a hull that
     * has just arrived or just gone is stale until it is asked again.
     */
    private static void recomputeCoverAround(NavigationGrid grid, int centerX, int centerY) {
        for (int y = centerY - HALF - 1; y <= centerY + HALF + 1; y++) {
            for (int x = centerX - HALF - 1; x <= centerX + HALF + 1; x++) {
                if (grid.inBounds(x, y)) grid.recomputeCoverAt(x, y);
            }
        }
    }

    /** This cell's bit in one plane of the {@link #stand} mask. */
    private static long bit(int x, int y, int centerX, int centerY, int plane) {
        return 1L << (plane + (y - centerY + HALF) * WIDTH + (x - centerX + HALF));
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
