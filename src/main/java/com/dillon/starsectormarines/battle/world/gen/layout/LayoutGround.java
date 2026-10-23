package com.dillon.starsectormarines.battle.world.gen.layout;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.model.CellTopology;

/**
 * The grown ground as a {@link Deferred} piece may read it: where the places
 * claimed, where the roads run, what is walkable. Read-only by intent, and
 * with no generation random stream: a piece that wants variety draws it from
 * {@link #seed}, so resolving a layout never moves another stage's draws.
 */
public final class LayoutGround {

    private final GenContext ctx;
    private final boolean[][] roads, roadCells;
    private int[][] bounds;

    public LayoutGround(GenContext ctx) {
        this.ctx = ctx;
        this.roads = ctx.get(BspKeys.ROAD_RESERVATION);
        this.roadCells = ctx.get(BspKeys.ROAD_CELLS);
    }

    public int width() { return ctx.width; }

    public int height() { return ctx.height; }

    public NavigationGrid grid() { return ctx.grid; }

    public CellTopology topology() { return ctx.topology; }

    /** A seed for one named place's draws, fixed by the map's seed. */
    public long seed(String place) {
        return ctx.seed ^ (0x9E3779B97F4A7C15L * (place.hashCode() + 1L));
    }

    /** Whether the cell is road (reserved for traffic, or painted as a road). */
    public boolean isRoad(int x, int y) {
        if (x < 0 || y < 0 || x >= ctx.width || y >= ctx.height) return false;
        return (roads != null && roads[x][y]) || (roadCells != null && roadCells[x][y]);
    }

    /** Whether any place claimed the cell: a town's, a garrison's or a base's ground rather than open country. */
    public boolean isClaimed(int x, int y) {
        int[][] claim = ctx.get(BspKeys.PRECINCT_CLAIM);
        if (claim == null || x < 0 || y < 0 || x >= ctx.width || y >= ctx.height) return false;
        return claim[x][y] != GrownTrunkPlan.UNOWNED;
    }

    public boolean walkable(int x, int y) {
        return ctx.grid.inBounds(x, y) && ctx.grid.isWalkable(x, y);
    }

    /**
     * The inclusive bounds {x0, y0, x1, y1} of the ground the named place
     * claimed, or {@code null} when the map has no such place or it claimed nothing.
     */
    public int[] claimBounds(String place) {
        PrecinctPlan plan = ctx.get(BspKeys.PRECINCTS);
        int[][] claim = ctx.get(BspKeys.PRECINCT_CLAIM);
        if (plan == null || claim == null) return null;
        int index = -1;
        for (int i = 0; i < plan.precincts().size(); i++) {
            if (plan.precincts().get(i).name().equals(place)) index = i;
        }
        if (index < 0) return null;
        if (bounds == null) bounds = measure(claim, plan.precincts().size());
        int[] b = bounds[index];
        return b[2] < b[0] ? null : b.clone();
    }

    private int[][] measure(int[][] claim, int places) {
        int[][] out = new int[places][];
        for (int i = 0; i < places; i++) out[i] = new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE, -1, -1};
        for (int x = 0; x < ctx.width; x++) {
            for (int y = 0; y < ctx.height; y++) {
                int owner = claim[x][y];
                if (owner == GrownTrunkPlan.UNOWNED || owner < 0 || owner >= places) continue;
                int[] b = out[owner];
                b[0] = Math.min(b[0], x);
                b[1] = Math.min(b[1], y);
                b[2] = Math.max(b[2], x);
                b[3] = Math.max(b[3], y);
            }
        }
        return out;
    }
}
