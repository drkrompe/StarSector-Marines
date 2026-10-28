package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;

import java.util.function.ToDoubleFunction;

/**
 * Per-cell traversal cost for vehicle routing, baked from
 * {@link CellTopology.GroundKind}. The cost-field router (slice 1) multiplies
 * each grid step by the destination cell's cost, so search <em>prefers</em>
 * cheap terrain (roads) but will cross dearer terrain (open ground) when that
 * genuinely shortens the route. See {@code convoy-nouns.md}.
 *
 * <p>Costs are <b>multiplicative with a 1.0 road baseline</b> — nothing is
 * cheaper than a road, so the router's octile distance heuristic (which assumes
 * unit step cost) never overestimates and A* stays optimal. This field encodes
 * <em>preference</em> only, not passability: walkability and vehicle clearance
 * gate where a vehicle may go ({@link VehicleClearance} +
 * {@link com.dillon.starsectormarines.battle.nav.NavigationGrid}). Even the
 * highest-cost kinds here are finite, so a cost lookup never means "blocked."
 *
 * <p>Storage is {@code float[]} for clarity; the value count is small (one per
 * cell) and the slice-1 A* reads it once per expanded edge. Revisit a scaled
 * {@code byte[]} only if profiling flags the multiply (slice 5 / perf).
 *
 * <p>Pure: {@code CellTopology -> cost field}, no {@link VehicleMission} coupling.
 * Starting weights are slice-0 values, tuned in slice 4.
 */
public final class TerrainCostField {

    /** Drive-on infrastructure — the baseline the heuristic is admissible against. */
    static final float COST_ROAD = 1.0f;
    /** Hardscape a vehicle crosses without complaint (courtyard, plaza, polished floor). */
    static final float COST_HARDSCAPE = 1.5f;
    /** Open natural ground — passable but dear, so roads win unless the shortcut is large. */
    static final float COST_OPEN_GROUND = 3.0f;
    /** Knocked-down wall: drivable but ugly; avoid unless it's the shortcut that matters. */
    static final float COST_RUBBLE = 5.0f;
    /** Building interiors / water surfaces — strongly avoided. (Water is also non-walkable, so the router never reads it; interiors are discouraged where a breach made them walkable.) */
    static final float COST_AVOID = 8.0f;

    private final int width;
    private final int height;
    private final float[] cost;

    private TerrainCostField(int width, int height, float[] cost) {
        this.width = width;
        this.height = height;
        this.cost = cost;
    }

    /** Bake a cost field from the map's ground kinds. */
    public static TerrainCostField from(CellTopology topology) {
        return from(topology, TerrainCostField::costFor);
    }

    /**
     * Bake a cost field with a hull's own price per ground kind (a hover skirt's, {@link HoverSkirt#costField}).
     * Prices below {@link #COST_ROAD} would break the router's admissible heuristic and are refused.
     */
    static TerrainCostField from(CellTopology topology, ToDoubleFunction<GroundKind> price) {
        int w = topology.getWidth();
        int h = topology.getHeight();
        float[] cost = new float[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float c = (float) price.applyAsDouble(topology.getGroundKind(x, y));
                if (!(c >= COST_ROAD)) throw new IllegalArgumentException("Route cost below the road baseline: " + c);
                cost[y * w + x] = c;
            }
        }
        return new TerrainCostField(w, h, cost);
    }

    /**
     * This field with every cell carrying {@code tag} on {@code grid} raised to at least {@code floor}, for a hull
     * that pays more for ground it has to work through ({@link HeavyTracks#costField}). A new field; this one is
     * unchanged.
     */
    /** A cell test, by coordinates. */
    interface CellTest { boolean test(int x, int y); }

    /** This field with every cell {@code test} passes priced at least {@code floor}. */
    TerrainCostField withFloor(CellTest test, float floor) {
        float[] raised = cost.clone();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (test.test(x, y)) raised[y * width + x] = Math.max(raised[y * width + x], floor);
            }
        }
        return new TerrainCostField(width, height, raised);
    }

    TerrainCostField withFloor(NavigationGrid grid, NavigationGrid.CellTag tag, float floor) {
        if (grid.getWidth() != width || grid.getHeight() != height) {
            throw new IllegalArgumentException("Cost field and grid dimensions differ");
        }
        float[] raised = cost.clone();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (grid.hasTag(x, y, tag)) raised[y * width + x] = Math.max(raised[y * width + x], floor);
            }
        }
        return new TerrainCostField(width, height, raised);
    }

    public int getWidth()  { return width; }
    public int getHeight() { return height; }

    /** Multiplicative step cost at (x, y); {@link #COST_AVOID} out of bounds. */
    public float costAt(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) return COST_AVOID;
        return cost[y * width + x];
    }

    /** Hot-path variant for callers holding a flat index ({@code y*width + x}). */
    public float costAtIndex(int idx) {
        return cost[idx];
    }

    /** Backing array for {@link VehicleRoutePlanner} to hand straight to the pathfinder — do not mutate. */
    float[] costArray() {
        return cost;
    }

    /** The multiplicative cost a vehicle pays to drive onto a cell of this ground kind. */
    static float costFor(GroundKind kind) {
        switch (kind) {
            case STREET:
            case SIDEWALK:
            case STRIPED:
                return COST_ROAD;
            case COURTYARD:
            case BRICK:
            case STONE:
            case TILE:
                return COST_HARDSCAPE;
            case GRASS:
            case DIRT:
            case SAND:
            case SNOW:
                return COST_OPEN_GROUND;
            case RUBBLE:
                return COST_RUBBLE;
            case INDOOR:
            case WATER:
            default:
                return COST_AVOID;
        }
    }
}
