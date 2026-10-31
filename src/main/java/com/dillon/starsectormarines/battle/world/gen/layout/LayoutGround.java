package com.dillon.starsectormarines.battle.world.gen.layout;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.PreparedGround;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.precinct.Landscape;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.gen.rural.RuralLayout;
import com.dillon.starsectormarines.battle.world.model.Building;
import com.dillon.starsectormarines.battle.world.model.Buildings;
import com.dillon.starsectormarines.battle.world.model.CellTopology;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The grown ground as a {@link Deferred} piece may read it: where the places
 * claimed, where the roads run, what is walkable. Read-only by intent, and
 * with no generation random stream: a piece that wants variety draws it from
 * {@link #seed}, so resolving a layout never moves another stage's draws.
 */
public final class LayoutGround {

    private final GenContext ctx;
    private final boolean[][] roads, roadCells;
    private final int[][] roadOwner, claim;
    private int[][] bounds;

    public LayoutGround(GenContext ctx) {
        this.ctx = ctx;
        this.roads = ctx.get(BspKeys.ROAD_RESERVATION);
        this.roadCells = ctx.get(BspKeys.ROAD_CELLS);
        this.roadOwner = ctx.get(BspKeys.PRECINCT_ROAD);
        this.claim = ctx.get(BspKeys.PRECINCT_CLAIM);
    }

    public int width() { return ctx.width; }

    public int height() { return ctx.height; }

    public NavigationGrid grid() { return ctx.grid; }

    public CellTopology topology() { return ctx.topology; }

    /** A seed for one named place's draws, fixed by the map's seed. */
    public long seed(String place) {
        return ctx.seed ^ (0x9E3779B97F4A7C15L * (place.hashCode() + 1L));
    }

    /**
     * Whether the cell is road: reserved for traffic, painted as a road, or a road the places' network holds in open
     * country (a weld, an artery, a road the layout carved).
     */
    public boolean isRoad(int x, int y) {
        if (x < 0 || y < 0 || x >= ctx.width || y >= ctx.height) return false;
        if ((roads != null && roads[x][y]) || (roadCells != null && roadCells[x][y])) return true;
        return roadOwner != null && claim != null && roadOwner[x][y] != GrownTrunkPlan.UNOWNED
                && claim[x][y] == GrownTrunkPlan.UNOWNED;
    }

    /** Whether any place claimed the cell: a town's, a garrison's or a base's ground rather than open country. */
    public boolean isClaimed(int x, int y) {
        int[][] claim = ctx.get(BspKeys.PRECINCT_CLAIM);
        if (claim == null || x < 0 || y < 0 || x >= ctx.width || y >= ctx.height) return false;
        return claim[x][y] != GrownTrunkPlan.UNOWNED;
    }

    /**
     * Whether the landscape held the cell before the places grew: a held or sealed ridge's ground, a terrace's
     * kept ground or a knoll's ring ({@code elevation-nouns.md}, Held ground). A deferred piece that keeps off it
     * leaves the ridge raised there unbroken.
     */
    public boolean isHeld(int x, int y) {
        boolean[][] held = ctx.get(BspKeys.HELD_GROUND);
        return held != null && x >= 0 && y >= 0 && x < ctx.width && y < ctx.height && held[x][y];
    }

    public boolean walkable(int x, int y) {
        return ctx.grid.inBounds(x, y) && ctx.grid.isWalkable(x, y);
    }

    /**
     * Whether something may be built on the cell: open ground (walkable, no door, prop or wall) that is not road and
     * not a place's built ground (a building's floor).
     */
    public boolean buildable(int x, int y) {
        return PreparedGround.open(ctx, x, y) && !isRoad(x, y) && ctx.topology.getBuildingId(x, y) == 0
                && ctx.topology.getGroundKind(x, y) != CellTopology.GroundKind.INDOOR;
    }

    /** Whether the cell is open enough to take a prop: walkable, and no doorway, fixture or wall ({@link PreparedGround#open}). */
    public boolean open(int x, int y) {
        return PreparedGround.open(ctx, x, y);
    }

    /** The buildings standing, once the map is finished (the works phase); empty before. */
    public Collection<Building> buildings() {
        Buildings buildings = ctx.get(BspKeys.BUILDINGS);
        return buildings == null ? List.of() : buildings.all();
    }

    /** The surveyed farmland of an open-field landing ({@code mapgen-nouns.md}, Rural country), or {@code null}. */
    public RuralLayout rural() {
        return ctx.get(BspKeys.RURAL_LAYOUT);
    }

    /** Whether the cell is worked ground: a plot somebody farms (not left wild), or a farm's yard. */
    public boolean farmed(int x, int y) {
        RuralLayout rural = rural();
        if (rural == null) return false;
        RuralLayout.Plot plot = rural.plotAt(x, y);
        return plot != null && plot.use() != RuralLayout.Use.WILD || rural.farmsteadAt(x, y) != null;
    }

    /**
     * Whether a way runs over the cell: {@link #isRoad road}, a road or track of the surveyed farmland, or a track of
     * the plan's landscape (a lane's), the last two painted only once the map is finished.
     */
    public boolean isWay(int x, int y) {
        if (isRoad(x, y)) return true;
        RuralLayout rural = rural();
        if (rural != null && rural.roadAt(x, y) != null) return true;
        PrecinctPlan plan = plan();
        if (plan == null) return false;
        for (Landscape.Track track : plan.landscape().tracks()) if (track.contains(x, y)) return true;
        return false;
    }

    /** The plan the map is laid out from (its places where they were pinned). */
    public PrecinctPlan plan() {
        return ctx.get(BspKeys.PRECINCTS);
    }

    /** The middles {x, y} of the vehicle berths the places packed, if any. */
    public List<int[]> berths() {
        List<int[]> out = new ArrayList<>();
        for (Gantry g : ctx.gantries) out.add(new int[]{g.centerX, g.centerY});
        return out;
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
