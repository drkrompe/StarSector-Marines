package com.dillon.starsectormarines.battle.reinforcement;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.Faction;
import com.dillon.starsectormarines.battle.FactionUnitRoster;
import com.dillon.starsectormarines.battle.air.Shuttle;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.mapgen.TraversalAxis;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * Air-drop reinforcement means. Picks a walkable LZ near the rally, mints
 * a single-cycle {@link Shuttle} that flies in from the side-appropriate
 * off-map edge, lands, deboards its capacity into a fresh defender squad,
 * and departs. Reuses the existing shuttle state machine in
 * {@code AirSystem} — this class only writes the spawn-time inputs the
 * sim already consumes for marine drops.
 *
 * <p>Priority slot is between {@link ConvoyMeans} (most readable, needs
 * road graph) and {@link WalkInMeans} (always-feasible floor). A defender
 * rally on a road-less map but near walkable ground gets a shuttle
 * instead of dropping straight to walk-in; a rally in a clogged interior
 * with no LZ within {@link #LZ_SCAN_RADIUS} cells of the rally yields to
 * walk-in.
 *
 * <p>Narrative read: shuttle reinforcement is an "elite strike team"
 * deploying via aircraft. Deboarded units inherit the {@code UnitType.MARINE}
 * stats baked into {@code AirSystem.tryDeboardMarine} — costlier delivery,
 * better troops than the {@link WalkInMeans} militia floor.
 */
public final class ShuttleMeans implements ReinforcementMeans {

    private static final Logger LOG = Global.getLogger(ShuttleMeans.class);

    /** Max BFS radius from the rally when searching for a walkable LZ cell. */
    private static final int LZ_SCAN_RADIUS = 8;

    /** Cells the off-map entry sits outside the grid. Mirrors {@code BattleSetup.SHUTTLE_OFFMAP_Y}; duplicated here so the means is self-contained and the existing constant stays {@code private}. */
    private static final float OFFMAP_PAD = 8f;

    /** Default shuttle for SMALL strength. Nimble, 4-capacity — single-squad reinforcement reads as quick-response delivery. */
    private static final ShuttleType DEFAULT_TYPE = ShuttleType.AEROSHUTTLE;

    private final TraversalAxis axis;

    public ShuttleMeans(TraversalAxis axis) {
        this.axis = axis;
    }

    @Override
    public boolean canFulfill(BattleSimulation sim, ReinforcementRequest req) {
        if (!req.hasRally()) return false;
        if (req.side != Faction.DEFENDER) return false;
        return findLz(sim.getGrid(), req.rallyX, req.rallyY) != null;
    }

    @Override
    public void dispatch(BattleSimulation sim, ReinforcementRequest req) {
        NavigationGrid grid = sim.getGrid();
        int[] lz = findLz(grid, req.rallyX, req.rallyY);
        if (lz == null) {
            LOG.warn("ShuttleMeans: no walkable LZ within " + LZ_SCAN_RADIUS
                    + " cells of rally=(" + req.rallyX + "," + req.rallyY + ")");
            return;
        }

        float lzX = lz[0] + 0.5f;
        float lzY = lz[1] + 0.5f;
        float[] entry = entryForSide(req.side, axis, lzX, lzY, grid.getWidth(), grid.getHeight());

        Shuttle shuttle = new Shuttle(
                DEFAULT_TYPE, req.side,
                lzX, lzY,
                entry[0], entry[1],
                entry[2], entry[3],
                /*pendingDelay*/ 0f);
        shuttle.totalCycles = 1;
        // Reinforcement shuttles deboard the faction's elite tier (the
        // narrative of "expensive air-drop = stiffening delivery"). Default
        // player shuttles leave deboardUnitType null and get the bulk
        // infantry slot — see roadmap/reinforcement/faction-roster.md.
        shuttle.deboardUnitType = FactionUnitRoster.forFaction(req.side).elite();
        // No marineLoadout / no turret kit — AirSystem deboards plain COMBATANT
        // units and the null assignedRole skips HOVER_STATION (shuttle drops,
        // unloads, and leaves immediately).
        sim.addShuttle(shuttle);
        LOG.info("ShuttleMeans: dispatched " + DEFAULT_TYPE + " side=" + req.side
                + " lz=(" + lz[0] + "," + lz[1] + ") entry=(" + entry[0] + "," + entry[1] + ")");
    }

    /**
     * BFS from the rally for the first walkable cell. Returns {@code null}
     * when no cell within {@link #LZ_SCAN_RADIUS} is walkable — caller treats
     * that as "not feasible" and the next means in priority order
     * ({@link WalkInMeans}) gets a turn.
     */
    private static int[] findLz(NavigationGrid grid, int rallyX, int rallyY) {
        if (grid.inBounds(rallyX, rallyY) && grid.isWalkable(rallyX, rallyY)) {
            return new int[]{rallyX, rallyY};
        }
        Set<Long> seen = new HashSet<>();
        Deque<int[]> q = new ArrayDeque<>();
        q.add(new int[]{rallyX, rallyY, 0});
        seen.add(((long) rallyX << 32) | (rallyY & 0xFFFFFFFFL));
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!q.isEmpty()) {
            int[] p = q.poll();
            if (p[2] > LZ_SCAN_RADIUS) continue;
            if (grid.inBounds(p[0], p[1]) && grid.isWalkable(p[0], p[1])) {
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

    /**
     * Entry + exit world coords for a shuttle landing at {@code (lzX, lzY)}.
     * The entry comes from the side appropriate to the requesting faction —
     * defender from the "end" of the {@link TraversalAxis} (the rear),
     * marine from the "start" (the staging side). Mirrors
     * {@code BattleSetup.shuttleEntryFor} for the marine case and inverts
     * the axis edge for defender.
     *
     * @return {@code [entryX, entryY, exitX, exitY]}; exit sits 4 cells
     *         further off-map so the departing leg has a moment of climb.
     */
    private static float[] entryForSide(Faction side, TraversalAxis axis,
                                        float lzX, float lzY, int gridW, int gridH) {
        boolean defender = side == Faction.DEFENDER;
        if (axis == TraversalAxis.SOUTH_TO_NORTH) {
            if (defender) {
                return new float[]{
                        lzX, gridH + OFFMAP_PAD,
                        lzX, gridH + OFFMAP_PAD + 4f};
            }
            return new float[]{
                    lzX, -OFFMAP_PAD,
                    lzX, -OFFMAP_PAD - 4f};
        }
        if (axis == TraversalAxis.WEST_TO_EAST) {
            if (defender) {
                return new float[]{
                        gridW + OFFMAP_PAD, lzY,
                        gridW + OFFMAP_PAD + 4f, lzY};
            }
            return new float[]{
                    -OFFMAP_PAD, lzY,
                    -OFFMAP_PAD - 4f, lzY};
        }
        // Null-axis default — drop from above (high y). Stable, matches the
        // legacy fallback in BattleSetup.shuttleEntryFor.
        return new float[]{
                lzX, gridH + OFFMAP_PAD,
                lzX, gridH + OFFMAP_PAD + 4f};
    }
}
