package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.profile.TickInnerProfile;

/**
 * One chassis width's {@link VehicleClearance} mask and its
 * {@link ClearanceComponents} labels, held for as long as they are still true
 * and refreshed only for what the grid says actually moved.
 *
 * <p>Both are pure derivations of the navigation grid, and a full sweep of it
 * is expensive: eroding a 560x336 map is 188,160 cells times a 3x3 footprint
 * test, and labelling walks the result again — about twelve milliseconds each.
 * That was being paid on <em>every dispatch and every feasibility probe</em>,
 * for arrays that are very nearly always identical to the last ones. Measured
 * on the production Conquest fixture the convoy stall was traced from, holding
 * them takes twenty-five milliseconds off the dispatch tick and empties the
 * probe ticks entirely. Neither pays the full sweep on a change any more: the
 * mask re-evaluates the neighbourhood of each changed cell (see
 * {@link #clearance}) and the labels relabel only the tiles those cells fall
 * in before re-uniting the tile seams (see {@link #components}); both fall
 * back to the whole sweep only when the grid's change log no longer reaches
 * back to the last catch-up.
 *
 * <p><b>The key is {@link NavigationGrid#topologyRevision()}, which is exactly
 * the fact this derivation depends on.</b> It counts every change to a cell's
 * flags or to an edge's passability — the two things the erosion and the
 * component flood read — so a revision that has not moved is a proof that
 * recomputing would produce the same arrays, and a revision that has moved is
 * the signal to catch up. There is nothing to remember to call: a new
 * obstacle, a breached wall, an opened edge all bump it on the write that
 * makes them true, and {@link NavigationGrid#changeCount()} names exactly
 * which cells did.
 *
 * <p><b>What actually changes it during a battle is worth knowing.</b> The map
 * only ever becomes more permissive by destruction — a breached wall stays
 * walkable, a dead mount becomes rubble — and runtime construction may not
 * close a navigation transition at all, so a route proved once stays provable.
 * The one live write that closes ground is an <em>aircraft</em> coming down:
 * a settled airframe wreck seals the cells its hull covers, and a mask retained
 * across that would keep offering a drive through a burnt-out fuselage. A
 * <em>vehicle</em> wreck writes nothing to the navigation grid and needs no
 * invalidation, which is a fact about the wreck rather than a fact about this
 * cache — the revision covers both without knowing which is which.
 *
 * <p>Not thread-safe, and not meant to be: it belongs to one caller on the game
 * thread.
 */
public final class VehicleClearanceCache {

    /** Sentinel that no real {@link NavigationGrid#topologyRevision()} can take, so the first ask always builds. */
    private static final long UNBUILT = Long.MIN_VALUE;

    private final int radiusCells;

    private NavigationGrid clearanceGrid;
    private VehicleClearance clearance;
    private long clearanceRevision = UNBUILT;
    /** {@link NavigationGrid#changeCount()} the held mask has been replayed up to. */
    private long clearanceCaughtUp = UNBUILT;

    private NavigationGrid componentsGrid;
    private ClearanceComponents components;
    private long componentsRevision = UNBUILT;
    /** {@link NavigationGrid#changeCount()} the held labels have been replayed up to; tracked apart from the mask's because the labels are asked for less often. */
    private long componentsCaughtUp = UNBUILT;

    private int clearanceBuilds;
    private int clearanceCatchUps;
    private int componentBuilds;
    private int componentCatchUps;

    /** A cache for the mask a chassis of this footprint radius erodes. */
    public VehicleClearanceCache(int radiusCells) {
        this.radiusCells = Math.max(0, radiusCells);
    }

    /**
     * The eroded mask for {@code grid} as of {@code topologyRevision}.
     *
     * <p>Unchanged since the last ask, this returns the held instance untouched.
     * Changed, it catches up from {@link NavigationGrid}'s changed-cell log —
     * one {@link VehicleClearance#copyOf} clone plus a {@link
     * VehicleClearance#refreshAround} per logged cell, patching only the
     * Chebyshev neighbourhood each change could have flipped — and falls back to
     * a full {@link VehicleClearance#erode} only when the grid reports the
     * reader has fallen behind what the log still holds (a whole-grid
     * {@code clear()}, or more changes than {@link NavigationGrid#changeLogCapacity()}
     * since the last catch-up).
     */
    public VehicleClearance clearance(NavigationGrid grid, long topologyRevision) {
        if (clearance == null || clearanceGrid != grid) {
            TickInnerProfile profile = TickInnerProfile.currentIfBound();
            long started = profile != null ? System.nanoTime() : 0L;
            clearance = VehicleClearance.erode(grid, radiusCells);
            if (profile != null) profile.record(
                    TickInnerProfile.Bucket.CONVOY_CLEARANCE_BUILD,
                    System.nanoTime() - started);
            clearanceGrid = grid;
            clearanceRevision = topologyRevision;
            clearanceCaughtUp = grid.changeCount();
            clearanceBuilds++;
            return clearance;
        }
        if (clearanceRevision == topologyRevision) {
            return clearance;
        }
        long changeCount = grid.changeCount();
        if (grid.hasCaughtUpFrom(clearanceCaughtUp)) {
            TickInnerProfile profile = TickInnerProfile.currentIfBound();
            long started = profile != null ? System.nanoTime() : 0L;
            VehicleClearance patched = VehicleClearance.copyOf(clearance);
            int width = grid.getWidth();
            for (long seq = clearanceCaughtUp; seq < changeCount; seq++) {
                int idx = grid.changedCellAt(seq);
                patched.refreshAround(grid, idx % width, idx / width);
            }
            clearance = patched;
            if (profile != null) profile.record(
                    TickInnerProfile.Bucket.CONVOY_CLEARANCE_CATCHUP,
                    System.nanoTime() - started);
            clearanceCatchUps++;
        } else {
            TickInnerProfile profile = TickInnerProfile.currentIfBound();
            long started = profile != null ? System.nanoTime() : 0L;
            clearance = VehicleClearance.erode(grid, radiusCells);
            if (profile != null) profile.record(
                    TickInnerProfile.Bucket.CONVOY_CLEARANCE_BUILD,
                    System.nanoTime() - started);
            clearanceBuilds++;
        }
        clearanceRevision = topologyRevision;
        clearanceCaughtUp = changeCount;
        return clearance;
    }

    /**
     * The component labels of {@link #clearance}, built separately so a
     * feasibility probe — which only asks whether a gate admits a body — does
     * not pay for the labelling a route proof needs.
     */
    public ClearanceComponents components(NavigationGrid grid, long topologyRevision) {
        VehicleClearance mask = clearance(grid, topologyRevision);
        if (components == null || componentsGrid != grid) {
            TickInnerProfile profile = TickInnerProfile.currentIfBound();
            long started = profile != null ? System.nanoTime() : 0L;
            components = ClearanceComponents.of(grid, mask);
            if (profile != null) profile.record(
                    TickInnerProfile.Bucket.CONVOY_COMPONENT_BUILD,
                    System.nanoTime() - started);
            componentsGrid = grid;
            componentsRevision = topologyRevision;
            componentsCaughtUp = grid.changeCount();
            componentBuilds++;
            return components;
        }
        if (componentsRevision == topologyRevision) {
            return components;
        }
        long changeCount = grid.changeCount();
        if (grid.hasCaughtUpFrom(componentsCaughtUp)) {
            TickInnerProfile profile = TickInnerProfile.currentIfBound();
            long started = profile != null ? System.nanoTime() : 0L;
            components = components.catchUp(grid, mask, componentsCaughtUp,
                    changeCount, radiusCells);
            if (profile != null) profile.record(
                    TickInnerProfile.Bucket.CONVOY_COMPONENT_CATCHUP,
                    System.nanoTime() - started);
            componentCatchUps++;
        } else {
            TickInnerProfile profile = TickInnerProfile.currentIfBound();
            long started = profile != null ? System.nanoTime() : 0L;
            components = ClearanceComponents.of(grid, mask);
            if (profile != null) profile.record(
                    TickInnerProfile.Bucket.CONVOY_COMPONENT_BUILD,
                    System.nanoTime() - started);
            componentBuilds++;
        }
        componentsRevision = topologyRevision;
        componentsCaughtUp = changeCount;
        return components;
    }

    /** How many times the mask has been fully re-eroded. Evidence, not behavior. */
    public int clearanceBuilds() { return clearanceBuilds; }

    /** How many times the mask has caught up from the changed-cell log instead. Evidence, not behavior. */
    public int clearanceCatchUps() { return clearanceCatchUps; }

    /** How many times the labels have been rebuilt over every tile. Evidence, not behavior. */
    public int componentBuilds() { return componentBuilds; }

    /** How many times the labels have caught up by relabelling only the tiles that moved. Evidence, not behavior. */
    public int componentCatchUps() { return componentCatchUps; }
}
