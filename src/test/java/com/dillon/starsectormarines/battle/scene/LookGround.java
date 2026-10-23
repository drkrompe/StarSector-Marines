package com.dillon.starsectormarines.battle.scene;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspCityGenerator;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;

import java.util.EnumSet;

/**
 * Real ground for a look frame: a whole map stamped by the production generator for a small colony on a chosen
 * world, and a window of it open enough to stand a scene's subject on.
 *
 * <p>A behaviour scene's arena is a bare grid with no ground art, no props and no buildings, which is the right
 * instrument for a verdict and the wrong picture to show anyone asking how a thing looks: on the arena every hull
 * floats over the same flat colour. This stands the subject in the country a battle is actually fought in — the
 * generator's ground, roads, props and buildings, with its doodads handed to the simulation the way
 * {@code BattleSetup} hands a map's — and frames only the window, so what surrounds the subject is whatever the
 * generator put there.
 *
 * <p>A window is a place in map cells; scenes stand their subject in it by local coordinates through
 * {@link SceneBuilder#onTerrain}, which adds the window's corner to every coordinate it is given.
 */
public final class LookGround {

    /**
     * Map size the ground is generated at. Large enough that a small colony leaves open country around itself (about
     * three quarters of a 280x168 map is wild or farmed ground at market size 3, against a fifth at the 144x80 medium
     * battle, which is all town); generation is a fraction of a second either way.
     */
    private static final int MAP_W = 280;
    private static final int MAP_H = 168;
    /** Cells kept clear of the map edge, so a play-zoom frame on the window is never cut by the edge of the world. */
    private static final int EDGE = 32;

    private final SurfacePalette surface;
    private final long seed;

    private LookGround(SurfacePalette surface, long seed) {
        this.surface = surface;
        this.seed = seed;
    }

    /** Ground of the given world kind; the seed picks which map. */
    public static LookGround of(SurfacePalette surface, long seed) {
        if (surface == null) throw new IllegalArgumentException("surface is required");
        return new LookGround(surface, seed);
    }

    /** The default: green country with a small colony, the ground most fights are pictured on. */
    public static LookGround verdant() {
        return of(SurfacePalette.VERDANT, 7L);
    }

    public SurfacePalette surface() { return surface; }

    /** A small habitation market on this world: a town, its roads and fields, and wild ground beyond. */
    public TargetProfile profile() {
        return new TargetProfile(3, 3, 1, 0, "independent", EnumSet.of(EconomicFunction.HABITATION),
                surface, SettlementLink.ROAD);
    }

    /**
     * A freshly generated map. Fresh every call, because a battle writes into its grid and topology (a wall cut, a
     * breach) and two loops must not share one.
     */
    public MapResult generate() {
        return new BspCityGenerator().generate(MAP_W, MAP_H, seed, null, profile());
    }

    /**
     * The {@code width x height} window of {@code map} with the fewest cells a body could not stand on, nearest the
     * map's middle among equals, kept {@link #EDGE} cells off the map edge.
     */
    public static Site openSite(MapResult map, int width, int height) {
        return openSite(map.grid, map.topology, width, height);
    }

    /** {@link #openSite(MapResult, int, int)} over a grid and topology. */
    static Site openSite(NavigationGrid grid, CellTopology topology, int width, int height) {
        int w = grid.getWidth();
        int h = grid.getHeight();
        // Summed-area table of blocked cells.
        int[] sum = new int[(w + 1) * (h + 1)];
        for (int y = 0; y < h; y++) {
            int row = 0;
            for (int x = 0; x < w; x++) {
                row += blocked(grid, topology, x, y) ? 1 : 0;
                sum[(y + 1) * (w + 1) + x + 1] = sum[y * (w + 1) + x + 1] + row;
            }
        }
        int edgeX = Math.min(EDGE, Math.max(0, (w - width) / 2));
        int edgeY = Math.min(EDGE, Math.max(0, (h - height) / 2));
        Site best = null;
        int bestBlocked = Integer.MAX_VALUE;
        double bestDistance = Double.MAX_VALUE;
        for (int y0 = edgeY; y0 + height <= h - edgeY; y0++) {
            for (int x0 = edgeX; x0 + width <= w - edgeX; x0++) {
                int x1 = x0 + width;
                int y1 = y0 + height;
                int count = sum[y1 * (w + 1) + x1] - sum[y0 * (w + 1) + x1] - sum[y1 * (w + 1) + x0]
                        + sum[y0 * (w + 1) + x0];
                double distance = Math.hypot(x0 + width / 2.0 - w / 2.0, y0 + height / 2.0 - h / 2.0);
                if (count < bestBlocked || (count == bestBlocked && distance < bestDistance)) {
                    best = new Site(x0, y0, width, height, count);
                    bestBlocked = count;
                    bestDistance = distance;
                }
            }
        }
        if (best == null) throw new IllegalArgumentException(width + "x" + height + " does not fit a "
                + w + "x" + h + " map");
        return best;
    }

    /** A cell a body cannot be stood on: not walkable, inside a building, or on water, or off the world. */
    static boolean blocked(NavigationGrid grid, CellTopology topology, int x, int y) {
        if (!grid.isWalkable(x, y) || grid.isDoorway(x, y)) return true;
        if (topology.getBuildingId(x, y) != 0) return true;
        GroundKind kind = topology.getGroundKind(x, y);
        return kind == GroundKind.INDOOR || kind == GroundKind.WATER || kind == GroundKind.VOID;
    }

    /**
     * A window of a generated map, in map cells, and how many of its cells were blocked when it was chosen.
     * {@link #x(float)} and {@link #y(float)} turn a scene's local coordinates into the map's.
     */
    public record Site(int x0, int y0, int width, int height, int blockedCells) {

        public float x(float local) { return x0 + local; }

        public float y(float local) { return y0 + local; }

        public int x(int local) { return x0 + local; }

        public int y(int local) { return y0 + local; }

        public float centreX() { return x0 + width / 2f; }

        public float centreY() { return y0 + height / 2f; }
    }
}
