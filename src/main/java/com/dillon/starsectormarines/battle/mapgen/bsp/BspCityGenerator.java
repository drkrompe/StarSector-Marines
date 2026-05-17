package com.dillon.starsectormarines.battle.mapgen.bsp;

import com.dillon.starsectormarines.battle.Doodad;
import com.dillon.starsectormarines.battle.PointOfInterest;
import com.dillon.starsectormarines.battle.map.CellTopology;
import com.dillon.starsectormarines.battle.map.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.mapgen.BiomeKind;
import com.dillon.starsectormarines.battle.mapgen.BlockFiller;
import com.dillon.starsectormarines.battle.mapgen.BlockKind;
import com.dillon.starsectormarines.battle.mapgen.BlockLeaf;
import com.dillon.starsectormarines.battle.mapgen.MapDistrictTheme;
import com.dillon.starsectormarines.battle.mapgen.MapGenerator;
import com.dillon.starsectormarines.battle.mapgen.MapResult;
import com.dillon.starsectormarines.battle.mapgen.TraversalAxis;
import com.dillon.starsectormarines.battle.mapgen.bsp.fill.BuildingCommercialFiller;
import com.dillon.starsectormarines.battle.mapgen.bsp.fill.BuildingIndustrialFiller;
import com.dillon.starsectormarines.battle.mapgen.bsp.fill.BuildingResidentialFiller;
import com.dillon.starsectormarines.battle.mapgen.bsp.fill.DenseBlockFiller;
import com.dillon.starsectormarines.battle.mapgen.bsp.fill.FortifiedPostFiller;
import com.dillon.starsectormarines.battle.mapgen.bsp.fill.IndustrialYardFiller;
import com.dillon.starsectormarines.battle.mapgen.bsp.fill.DenseQuarterFiller;
import com.dillon.starsectormarines.battle.mapgen.bsp.fill.GatedHousingFiller;
import com.dillon.starsectormarines.battle.mapgen.bsp.fill.LandingZoneFiller;
import com.dillon.starsectormarines.battle.mapgen.bsp.fill.MilitaryBaseFiller;
import com.dillon.starsectormarines.battle.mapgen.bsp.fill.ParkFiller;
import com.dillon.starsectormarines.battle.mapgen.bsp.fill.PlazaFiller;
import com.dillon.starsectormarines.battle.mapgen.bsp.fill.WastelandRubbleFiller;
import com.dillon.starsectormarines.battle.mapgen.bsp.fill.WaterfrontFiller;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
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
    private final Map<BlockKind, CompoundFiller> compoundFillers = new EnumMap<>(BlockKind.class);

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

        registerCompound(new MilitaryBaseFiller());
        registerCompound(new GatedHousingFiller());
        registerCompound(new DenseQuarterFiller());
    }

    /** Swap in a compound-aware filler. Idempotent — last write wins. */
    public void registerCompound(CompoundFiller filler) {
        compoundFillers.put(filler.kind(), filler);
    }

    /** Swap in a per-kind filler. Idempotent — last write wins. */
    public void register(BlockFiller filler) {
        fillers.put(filler.kind(), filler);
    }

    @Override
    public MapResult generate(int width, int height, long seed) {
        return generate(width, height, seed, null);
    }

    /**
     * Conquest-aware generation. When {@code axis} is non-null the generator
     * lays a {@link BiomeMap} along the axis (beach → port → city → fortress
     * district), overrides leaf theme lookups to consult the biome rather than
     * the legacy {@link DistrictMap}, repaints walkable open ground in the
     * BEACH biome as {@link GroundKind#SAND}, and pins the marine spawn to
     * the beach end and the defender spawn to the fortress end.
     *
     * <p>When {@code axis} is null this delegates to the legacy district-driven
     * pipeline — used by the smaller 80×80 preview path and any non-conquest
     * generation. Eventually the legacy path will be retired once biome mode
     * covers every variant we generate.
     */
    public MapResult generate(int width, int height, long seed, TraversalAxis axis) {
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

        // Step 1a — plan the trunk skeleton. Trunks (the city's arterials)
        // get painted into the shared road mask before BSP runs, plus the
        // matching GroundKind onto the topology so the renderer reads them
        // as boulevards rather than ordinary back-streets.
        TrunkPlan.Plan plan = TrunkPlan.generate(width, height, rng);
        for (TrunkPlan.TrunkSegment trunk : plan.trunks) {
            for (int y = trunk.top; y <= trunk.bottom; y++) {
                for (int x = trunk.left; x <= trunk.right; x++) {
                    topology.setGroundKind(x, y, trunk.kind.ground);
                }
            }
        }

        // Step 1b — run BSP independently inside each sub-rect between
        // trunks/edges. All sub-rects share one road mask so connectivity
        // falls out by construction: every BSP road frame butts against the
        // trunk or perimeter cells already marked road in the mask.
        List<BlockLeaf> leaves = new ArrayList<>();
        for (TrunkPlan.SubRect r : plan.subRects) {
            Bsp.partitionRect(r.x0, r.y0, r.x1, r.y1, width, height,
                    Bsp.ROAD_WIDTH_MAX_WITH_TRUNKS, plan.roadCells, leaves, rng);
        }
        Bsp.Partition partition = new Bsp.Partition(leaves, plan.roadCells, width, height);

        // Step 1c — lay down the zoning overlay. In conquest mode (axis set)
        // a BiomeMap takes precedence — biome bands run along the traversal
        // axis and fully drive theme picks. In legacy mode a DistrictMap
        // scatters themes uniformly with a CIVIC nudge at the trunk crossing.
        DistrictMap districtMap = null;
        BiomeMap biomeMap = null;
        if (axis != null) {
            biomeMap = new BiomeMap(width, height, axis, rng);
            this.lastBiomeMap = biomeMap;
            this.lastDistrictMap = null;
            LOG.info("BspCityGenerator: " + partition.leaves.size() + " leaves on "
                    + width + "x" + height + " grid, "
                    + plan.trunks.size() + " trunk(s), biome axis=" + axis);
        } else {
            districtMap = new DistrictMap(width, height, rng);
            int ixCenterX = (plan.intersection.x0 + plan.intersection.x1) / 2;
            int ixCenterY = (plan.intersection.y0 + plan.intersection.y1) / 2;
            districtMap.forceThemeAt(ixCenterX, ixCenterY, MapDistrictTheme.CIVIC);
            this.lastDistrictMap = districtMap;
            this.lastBiomeMap = null;
            LOG.info("BspCityGenerator: " + partition.leaves.size() + " leaves on "
                    + width + "x" + height + " grid, "
                    + plan.trunks.size() + " trunk(s), "
                    + districtMap.districtsX() + "x" + districtMap.districtsY() + " districts");
        }

        // Step 2 — label each leaf using the active zoning overlay.
        labelLeaves(partition, biomeMap, districtMap, rng);

        // Step 2b — claim multi-leaf compounds (e.g. military bases). Each
        // compound's seed leaf keeps its BlockKind; absorbed neighbor leaves
        // are rewritten to COMPOUND_MEMBER so per-leaf dispatch skips them.
        Map<BlockLeaf, List<BlockLeaf>> adjacency = LeafAdjacency.compute(partition.leaves, width, height);
        List<Compound> compounds = CompoundClaim.claim(partition.leaves, adjacency, CompoundClaim.DEFAULT_SPECS, rng);
        this.lastCompounds = compounds;
        Map<BlockLeaf, Compound> compoundBySeed = new IdentityHashMap<>();
        for (Compound c : compounds) compoundBySeed.put(c.seed, c);
        if (!compounds.isEmpty()) {
            LOG.info("BspCityGenerator: " + compounds.size() + " compound(s) claimed");
        }

        // Step 3 — dispatch fillers. Each per-leaf filler owns its leaf's
        // cells (NOT the road frame around it); compound fillers own the
        // union of their member leaves plus the bridged road between them.
        List<PointOfInterest> pois = new ArrayList<>();
        List<Doodad> doodads = new ArrayList<>();
        for (BlockLeaf leaf : partition.leaves) {
            if (leaf.kind == BlockKind.COMPOUND_MEMBER) continue;
            Compound compound = compoundBySeed.get(leaf);
            if (compound != null) {
                CompoundFiller cFiller = compoundFillers.get(compound.kind);
                if (cFiller != null) {
                    cFiller.fill(compound, grid, topology, plan.roadCells, pois, doodads, rng);
                }
                continue;
            }
            BlockFiller filler = fillers.get(leaf.kind);
            filler.fill(leaf, grid, topology, pois, doodads, rng);
        }

        // Step 3b — biome ground overrides. In conquest mode the BEACH biome
        // repaints its walkable non-building, non-water ground as SAND so the
        // beach reads as a continuous sandy landing zone rather than the
        // generator's default STREET grey. Runs after fill so we know which
        // cells are INDOOR (skip) vs outdoors (paint).
        if (biomeMap != null) {
            applyBiomeGroundOverrides(grid, topology, biomeMap);
        }

        // Step 3c — fortress super-wall. Stamps the Kremlin-style perimeter
        // around the FORTRESS_DISTRICT biome — wall rectangle inset into the
        // biome (kill-zone buffer between biome edge and wall), corner towers
        // + periodic heavy mid-towers, MG nests in the gaps, 1-3 gates on the
        // attacker-facing edge, and 2-4 forward bunkers in the kill zone.
        // Runs after fill so the wall overrides whatever BSP put under it.
        if (biomeMap != null) {
            FortressWallStamper.stamp(grid, topology, axis, biomeMap, doodads, rng);
        }

        // Step 4 — finalize: HP on walls, cover bake, wall flag, spawn anchors.
        seedWallHp(grid);
        bakeCoverFromWalls(grid);
        topology.tagDefaultWalls(grid);

        int[] marine;
        int[] defender;
        if (axis != null) {
            marine   = pickBiomeSpawn(grid, biomeMap, BiomeKind.BEACH,             rng, axis, false);
            defender = pickBiomeSpawn(grid, biomeMap, BiomeKind.FORTRESS_DISTRICT, rng, axis, true);
        } else {
            marine   = pickSpawnAnchor(grid, 1, 1, width / 2, height - 1, rng);
            defender = pickSpawnAnchor(grid, width / 2, 1, width - 1, height - 1, rng);
        }

        return new MapResult(grid, topology,
                marine[0], marine[1], defender[0], defender[1],
                pois, doodads);
    }

    /**
     * Labels each leaf using whichever zoning overlay is active. In conquest
     * mode {@code biomeMap} drives the theme pick (biome-band placement along
     * the traversal axis); in legacy mode {@code districtMap} drives it
     * (uniform district scatter). Exactly one of the two is non-null.
     *
     * <p>Constraint guard for legacy mode: only WATERFRONT-theme districts
     * can produce WATERFRONT blocks; DistrictMap constrains that theme to
     * map-edge districts. Conquest mode lets WATERFRONT appear in BEACH
     * theme as well — accepting the occasional interior misfire because
     * BEACH biome cells get a SAND ground override that still sells the look.
     */
    private void labelLeaves(Bsp.Partition partition, BiomeMap biomeMap,
                              DistrictMap districtMap, Random rng) {
        for (BlockLeaf leaf : partition.leaves) {
            MapDistrictTheme theme = (biomeMap != null)
                    ? biomeMap.themeAt(leaf.centerX(), leaf.centerY())
                    : districtMap.themeAt(leaf.centerX(), leaf.centerY());
            leaf.kind = theme.pickBlockKind(rng);
        }
    }

    /** Last district map produced by {@link #generate} — exposed for the preview test's overlay rendering. Null in conquest (biome) mode. */
    private DistrictMap lastDistrictMap;
    public DistrictMap getLastDistrictMap() { return lastDistrictMap; }

    /** Last biome map produced by {@link #generate} — non-null when called with a {@link TraversalAxis}. Exposed for the preview test's overlay rendering. */
    private BiomeMap lastBiomeMap;
    public BiomeMap getLastBiomeMap() { return lastBiomeMap; }

    /** Last compound list produced by {@link #generate} — exposed for preview rendering. Empty if no compound was claimed. */
    private List<Compound> lastCompounds = new ArrayList<>();
    public List<Compound> getLastCompounds() { return lastCompounds; }

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
     * Repaint walkable outdoor cells inside the BEACH biome as
     * {@link GroundKind#SAND}. Skips {@link GroundKind#INDOOR} (preserve
     * building floors) and {@link GroundKind#WATER} (preserve waterfront
     * water bands), but rewrites STREET / COURTYARD / GRASS / DIRT / STONE
     * / TILE / etc. The visual effect: the entire beach reads as continuous
     * sand even though the BSP infill still produced varied block kinds
     * (parks become sand "scrub," roads become "beach paths").
     */
    private void applyBiomeGroundOverrides(NavigationGrid grid, CellTopology topology, BiomeMap biomeMap) {
        int w = grid.getWidth();
        int h = grid.getHeight();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!grid.isWalkable(x, y)) continue;
                if (biomeMap.biomeAt(x, y) != BiomeKind.BEACH) continue;
                GroundKind g = topology.getGroundKind(x, y);
                if (g == GroundKind.INDOOR || g == GroundKind.WATER) continue;
                topology.setGroundKind(x, y, GroundKind.SAND);
            }
        }
    }

    /**
     * Random walkable cell inside the rect that bounds every cell tagged
     * with {@code biome}. Falls back to a linear scan of every biome cell if
     * 64 random tries miss, then to map center if no walkable cell exists
     * inside the biome (shouldn't happen on real-size maps).
     *
     * <p>When {@code deepBias} is true the search rect is truncated to the
     * deepest 60% of the biome along the traversal {@code axis} (high y for
     * SOUTH_TO_NORTH, high x for WEST_TO_EAST). Used for the defender spawn
     * so it lands <em>inside</em> the fortress wall rather than in the kill
     * zone — the wall sits at ~30% inset from the biome's attacker-facing
     * edge, so 60% reliably lands past it.
     */
    private int[] pickBiomeSpawn(NavigationGrid grid, BiomeMap biomeMap, BiomeKind biome,
                                 Random rng, TraversalAxis axis, boolean deepBias) {
        int w = grid.getWidth();
        int h = grid.getHeight();
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE, top = Integer.MAX_VALUE, bot = Integer.MIN_VALUE;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (biomeMap.biomeAt(x, y) != biome) continue;
                if (x < lo)  lo = x;
                if (x > hi)  hi = x;
                if (y < top) top = y;
                if (y > bot) bot = y;
            }
        }
        if (lo == Integer.MAX_VALUE) {
            return new int[]{ w / 2, h / 2 };
        }
        if (deepBias) {
            if (axis == TraversalAxis.SOUTH_TO_NORTH) {
                top = top + (int) ((bot - top) * 0.4f);
            } else {
                lo = lo + (int) ((hi - lo) * 0.4f);
            }
        }
        int spanX = Math.max(1, hi - lo + 1);
        int spanY = Math.max(1, bot - top + 1);
        for (int i = 0; i < 64; i++) {
            int x = lo + rng.nextInt(spanX);
            int y = top + rng.nextInt(spanY);
            if (biomeMap.biomeAt(x, y) == biome && grid.isWalkable(x, y)) return new int[]{x, y};
        }
        for (int y = top; y <= bot; y++) {
            for (int x = lo; x <= hi; x++) {
                if (biomeMap.biomeAt(x, y) == biome && grid.isWalkable(x, y)) return new int[]{x, y};
            }
        }
        return new int[]{ (lo + hi) / 2, (top + bot) / 2 };
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
