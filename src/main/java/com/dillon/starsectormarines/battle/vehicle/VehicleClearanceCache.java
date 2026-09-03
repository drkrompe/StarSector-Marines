package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;

/**
 * One chassis width's {@link VehicleClearance} mask and its
 * {@link ClearanceComponents} labels, held for as long as they are still true
 * and rebuilt only when the grid's passability has actually changed.
 *
 * <p>Both are pure derivations of the navigation grid, and both cost a full
 * sweep of it: eroding a 560x336 map is 188,160 cells times a 3x3 footprint
 * test, and labelling walks the result again — about twelve milliseconds each.
 * That was being paid on <em>every dispatch and every feasibility probe</em>,
 * for arrays that are very nearly always identical to the last ones. Measured
 * on the production Conquest fixture the convoy stall was traced from, holding
 * them takes twenty-five milliseconds off the dispatch tick and empties the
 * probe ticks entirely.
 *
 * <p><b>The key is {@link NavigationGrid#topologyRevision()}, which is exactly
 * the fact this derivation depends on.</b> It counts every change to a cell's
 * flags or to an edge's passability — the two things the erosion and the
 * component flood read — so a revision that has not moved is a proof that
 * rebuilding would produce the same arrays, and a revision that has moved is
 * the only signal needed to throw them away. There is nothing to remember to
 * call: a new obstacle, a breached wall, an opened edge all bump it on the
 * write that makes them true.
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

    private NavigationGrid componentsGrid;
    private ClearanceComponents components;
    private long componentsRevision = UNBUILT;

    private int clearanceBuilds;
    private int componentBuilds;

    /** A cache for the mask a chassis of this footprint radius erodes. */
    public VehicleClearanceCache(int radiusCells) {
        this.radiusCells = Math.max(0, radiusCells);
    }

    /**
     * The eroded mask for {@code grid} as of {@code topologyRevision}, rebuilt
     * only when that revision has moved since the held one.
     */
    public VehicleClearance clearance(NavigationGrid grid, long topologyRevision) {
        if (clearance == null || clearanceGrid != grid
                || clearanceRevision != topologyRevision) {
            clearance = VehicleClearance.erode(grid, radiusCells);
            clearanceGrid = grid;
            clearanceRevision = topologyRevision;
            clearanceBuilds++;
        }
        return clearance;
    }

    /**
     * The component labels of {@link #clearance}, built separately so a
     * feasibility probe — which only asks whether a gate admits a body — does
     * not pay for the labelling a route proof needs.
     */
    public ClearanceComponents components(NavigationGrid grid, long topologyRevision) {
        VehicleClearance mask = clearance(grid, topologyRevision);
        if (components == null || componentsGrid != grid
                || componentsRevision != topologyRevision) {
            components = ClearanceComponents.of(grid, mask);
            componentsGrid = grid;
            componentsRevision = topologyRevision;
            componentBuilds++;
        }
        return components;
    }

    /** How many times the mask has actually been eroded. Evidence, not behavior. */
    public int clearanceBuilds() { return clearanceBuilds; }

    /** How many times the labels have actually been rebuilt. Evidence, not behavior. */
    public int componentBuilds() { return componentBuilds; }
}
