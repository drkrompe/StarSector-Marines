package com.dillon.starsectormarines.battle.mapgen.bsp;

import com.dillon.starsectormarines.battle.Doodad;
import com.dillon.starsectormarines.battle.PointOfInterest;
import com.dillon.starsectormarines.battle.map.CellTopology;
import com.dillon.starsectormarines.battle.map.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.mapgen.BlockFiller;
import com.dillon.starsectormarines.battle.mapgen.BlockKind;
import com.dillon.starsectormarines.battle.mapgen.BlockLeaf;
import com.dillon.starsectormarines.battle.mapgen.MapGenerator;
import com.dillon.starsectormarines.battle.mapgen.MapResult;
import com.dillon.starsectormarines.battle.mapgen.bsp.fill.BuildingCommercialFiller;
import com.dillon.starsectormarines.battle.mapgen.bsp.fill.BuildingIndustrialFiller;
import com.dillon.starsectormarines.battle.mapgen.bsp.fill.BuildingResidentialFiller;
import com.dillon.starsectormarines.battle.mapgen.bsp.fill.DenseBlockFiller;
import com.dillon.starsectormarines.battle.mapgen.bsp.fill.FortifiedPostFiller;
import com.dillon.starsectormarines.battle.mapgen.bsp.fill.IndustrialYardFiller;
import com.dillon.starsectormarines.battle.mapgen.bsp.fill.LandingZoneFiller;
import com.dillon.starsectormarines.battle.mapgen.bsp.fill.ParkFiller;
import com.dillon.starsectormarines.battle.mapgen.bsp.fill.PlazaFiller;
import com.dillon.starsectormarines.battle.mapgen.bsp.fill.WastelandRubbleFiller;
import com.dillon.starsectormarines.battle.mapgen.bsp.fill.WaterfrontFiller;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The new BSP-based urban map generator. Drives the four-step pipeline:
 * segment → label → fill → finalize.
 *
 * <ol>
 *   <li>{@link Bsp#partition} chops the grid into irregular leaves connected
 *       by shared road strips.</li>
 *   <li>{@link #labelLeaves} assigns each leaf a {@link BlockKind} weighted
 *       by size and re-rolled against adjacency constraints (LANDING_ZONE
 *       must touch road; WATERFRONT must touch map edge).</li>
 *   <li>The matching {@link BlockFiller} is dispatched for each leaf; the
 *       road frame is painted with {@link GroundKind#STREET}.</li>
 *   <li>Wall flagging + cover baking + spawn-anchor selection close out the
 *       {@link MapResult}.</li>
 * </ol>
 *
 * <p>Filler registration happens once in the constructor. New per-kind
 * fillers can replace the {@link StubBlockFiller} entries with a single
 * {@code register(new XxxFiller())} call — agents implementing fillers in
 * parallel won't collide because the map is keyed by {@link BlockKind}.
 */
public final class BspCityGenerator implements MapGenerator {

    // Raw log4j 1.2 logger rather than Global.getLogger — keeps the class
    // instantiable in plain JUnit (no Starsector runtime needed), which the
    // map-preview test depends on. At runtime the game configures the
    // root appender; we get the same log output either way.
    private static final Logger LOG = Logger.getLogger(BspCityGenerator.class);

    /** Default starting wall HP — matches legacy {@code UrbanMapGenerator.WALL_HP_DEFAULT}. */
    private static final int WALL_HP_DEFAULT = 100;

    private final Map<BlockKind, BlockFiller> fillers = new EnumMap<>(BlockKind.class);

    public BspCityGenerator() {
        // Default every kind to a stub. Real fillers replace these via
        // register(...). Order doesn't matter — each filler self-identifies
        // via BlockFiller.kind().
        for (BlockKind k : BlockKind.values()) {
            fillers.put(k, new StubBlockFiller(k));
        }
        register(new BuildingResidentialFiller());
        register(new BuildingCommercialFiller());
        register(new BuildingIndustrialFiller());
        register(new FortifiedPostFiller());
        register(new LandingZoneFiller());
        register(new PlazaFiller());
        register(new ParkFiller());
        register(new IndustrialYardFiller());
        register(new WastelandRubbleFiller());
        register(new WaterfrontFiller());
        register(new DenseBlockFiller());
    }

    /** Swap in a per-kind filler. Idempotent — last write wins. */
    public void register(BlockFiller filler) {
        fillers.put(filler.kind(), filler);
    }

    @Override
    public MapResult generate(int width, int height, long seed) {
        Random rng = new Random(seed);
        NavigationGrid grid = new NavigationGrid(width, height);
        CellTopology topology = new CellTopology(width, height);

        // Step 0 — initialize every cell as walkable STREET. Fillers overwrite
        // both the ground kind and walkability inside their leaves; the road
        // frame keeps these defaults.
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, GroundKind.STREET);
            }
        }

        // Step 1 — segment.
        Bsp.Partition partition = Bsp.partition(width, height, rng);

        // Step 1b — lay down a district overlay. The labeler then samples each
        // leaf's BlockKind from the district's theme weights so blocks cluster
        // (residential next to park next to plaza, instead of pure hodgepodge).
        DistrictMap districtMap = new DistrictMap(width, height, rng);
        LOG.info("BspCityGenerator: " + partition.leaves.size() + " leaves on "
                + width + "x" + height + " grid, "
                + districtMap.districtsX() + "x" + districtMap.districtsY() + " districts");
        this.lastDistrictMap = districtMap;

        // Step 2 — label each leaf using its district's theme.
        labelLeaves(partition, districtMap, rng);

        // Step 3 — dispatch fillers. Each filler owns its leaf's cells (NOT
        // the road frame around it).
        List<PointOfInterest> pois = new ArrayList<>();
        List<Doodad> doodads = new ArrayList<>();
        for (BlockLeaf leaf : partition.leaves) {
            BlockFiller filler = fillers.get(leaf.kind);
            filler.fill(leaf, grid, topology, pois, doodads, rng);
        }

        // Step 4 — finalize: HP on walls, cover bake, wall flag, spawn anchors.
        seedWallHp(grid);
        bakeCoverFromWalls(grid);
        topology.tagDefaultWalls(grid);

        int[] marine   = pickSpawnAnchor(grid, 1, 1, width / 2, height - 1, rng);
        int[] defender = pickSpawnAnchor(grid, width / 2, 1, width - 1, height - 1, rng);

        return new MapResult(grid, topology,
                marine[0], marine[1], defender[0], defender[1],
                pois, doodads);
    }

    /**
     * Labels each leaf using the {@link com.dillon.starsectormarines.battle.mapgen.MapDistrictTheme}
     * weights of the district its center sits in. That clusters same-kind
     * leaves into thematic neighborhoods (e.g., a {@code RESIDENTIAL}
     * district fills mostly with houses and parks; an {@code INDUSTRIAL}
     * district fills mostly with factories and yards).
     *
     * <p>Constraint guard: only WATERFRONT-theme districts can produce
     * WATERFRONT blocks (the theme's weight table is the only one that
     * includes the kind). DistrictMap already constrains WATERFRONT theme
     * to map-edge districts, so by transitivity WATERFRONT blocks only
     * appear on edges.
     */
    private void labelLeaves(Bsp.Partition partition, DistrictMap districtMap, Random rng) {
        for (BlockLeaf leaf : partition.leaves) {
            leaf.kind = districtMap.themeAt(leaf.centerX(), leaf.centerY()).pickBlockKind(rng);
        }
    }

    /** Last district map produced by {@link #generate} — exposed for the preview test's overlay rendering. Not part of {@link com.dillon.starsectormarines.battle.mapgen.MapResult} because nothing at runtime needs it. */
    private DistrictMap lastDistrictMap;
    public DistrictMap getLastDistrictMap() { return lastDistrictMap; }

    /** Every non-walkable cell gets a starting HP. Mirrors legacy seed. */
    private void seedWallHp(NavigationGrid grid) {
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                if (!grid.isWalkable(x, y)) grid.setWallHp(x, y, WALL_HP_DEFAULT);
            }
        }
    }

    /** Cardinal-neighbor wall count → cover level. Mirrors legacy bake. */
    private void bakeCoverFromWalls(NavigationGrid grid) {
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                if (!grid.isWalkable(x, y)) continue;
                int walls = 0;
                if (!grid.isWalkable(x + 1, y)) walls++;
                if (!grid.isWalkable(x - 1, y)) walls++;
                if (!grid.isWalkable(x, y + 1)) walls++;
                if (!grid.isWalkable(x, y - 1)) walls++;
                grid.setCoverAt(x, y, walls);
            }
        }
    }

    /**
     * Random walkable cell in the given rect. Linear-scan fallback if 64
     * random tries miss (very tight maps). LANDING_ZONE-anchor preference
     * will land here once that filler exists — for now any walkable corner
     * cell will do.
     */
    private int[] pickSpawnAnchor(NavigationGrid grid, int xMin, int yMin, int xMax, int yMax, Random rng) {
        int spanX = Math.max(1, xMax - xMin);
        int spanY = Math.max(1, yMax - yMin);
        for (int i = 0; i < 64; i++) {
            int x = xMin + rng.nextInt(spanX);
            int y = yMin + rng.nextInt(spanY);
            if (grid.inBounds(x, y) && grid.isWalkable(x, y)) return new int[]{x, y};
        }
        for (int y = yMin; y < yMax; y++) {
            for (int x = xMin; x < xMax; x++) {
                if (grid.isWalkable(x, y)) return new int[]{x, y};
            }
        }
        return new int[]{(xMin + xMax) / 2, (yMin + yMax) / 2};
    }
}
