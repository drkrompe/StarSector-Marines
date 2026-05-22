package com.dillon.starsectormarines.battle.mapgen.bsp;

import com.dillon.starsectormarines.battle.DefensePost;
import com.dillon.starsectormarines.battle.Doodad;
import com.dillon.starsectormarines.battle.PointOfInterest;
import com.dillon.starsectormarines.battle.map.Buildings;
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
import com.dillon.starsectormarines.battle.mapgen.bsp.fill.NatureZoneFiller;
import com.dillon.starsectormarines.battle.mapgen.bsp.fill.ParkFiller;
import com.dillon.starsectormarines.battle.mapgen.bsp.fill.PlazaFiller;
import com.dillon.starsectormarines.battle.mapgen.bsp.fill.WastelandRubbleFiller;
import com.dillon.starsectormarines.battle.mapgen.bsp.fill.WaterfrontFiller;
import com.dillon.starsectormarines.battle.mapgen.road.RoadGraph;
import com.dillon.starsectormarines.battle.mapgen.road.RoadGraphBuilder;
import com.dillon.starsectormarines.battle.mapgen.road.RoadReservation;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.tactical.TacticalLinker;
import com.dillon.starsectormarines.battle.tactical.TacticalMap;
import com.dillon.starsectormarines.battle.tactical.TacticalNode;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.EnumMap;
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
        register(new NatureZoneFiller(BlockKind.NATURE_GRASSLAND));
        register(new NatureZoneFiller(BlockKind.NATURE_WETLAND));
        register(new NatureZoneFiller(BlockKind.NATURE_BEACH));

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
    @Override
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
        // as boulevards rather than ordinary back-streets. Wide trunks
        // (TrunkKind.sidewalkFlankWidth > 0) drop an explicit SIDEWALK band
        // on each side so the surface reads as "road in the middle, brick
        // sidewalk on the curbs" rather than relying on render-time wall
        // adjacency that can't see across the trunk's full width.
        TrunkPlan.Plan plan = TrunkPlan.generate(width, height, rng);
        for (TrunkPlan.TrunkSegment trunk : plan.trunks) {
            paintTrunkGround(topology, trunk);
        }
        // Punch the road core through the intersection. Without this, the
        // PRIMARY trunk's outer SIDEWALK flank band would extend across the
        // SECONDARY trunk's vehicular path, breaking the road at the
        // crossing. Repainting the intersection rect as STREET keeps the
        // sidewalks butted up to the intersection edges and lets the road
        // continue uninterrupted across.
        for (int y = plan.intersection.y0; y <= plan.intersection.y1; y++) {
            for (int x = plan.intersection.x0; x <= plan.intersection.x1; x++) {
                topology.setGroundKind(x, y, GroundKind.STREET);
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

        // Step 2c — extract the vehicle-navigation skeleton from the road
        // mask before fillers run. The graph is built once over the trunk +
        // BSP-frame road cells produced so far; fillers paint inside their
        // own leaves and never touch road cells, so the skeleton is stable
        // through the rest of the pipeline. Stored as a field so the preview
        // overlay can render it; passed through to MapResult for runtime
        // ground-vehicle pathing.
        //
        // {@code roadReservation} is the cell mask of every graph node + edge
        // cell. Passed to every stamper that runs AFTER the graph so they
        // don't clobber a centerline — defense posts, fortress wall, beach
        // shoreline, compound walls/bridges. Without this, a turret pad or
        // perimeter wall lands on a road cell, the convoy graph is intact in
        // memory but the cell underneath is non-walkable, and the truck
        // visually drives "through" the stamped structure.
        this.lastRoadGraph = RoadGraphBuilder.build(plan.roadCells, plan);
        boolean[][] roadReservation = RoadReservation.mask(this.lastRoadGraph, width, height);

        // Step 3 — dispatch fillers. Each per-leaf filler owns its leaf's
        // cells (NOT the road frame around it); compound fillers own the
        // union of their member leaves plus the bridged road between them.
        List<PointOfInterest> pois = new ArrayList<>();
        List<Doodad> doodads = new ArrayList<>();
        List<TacticalNode> tactical = new ArrayList<>();
        List<DefensePost> defensePosts = new ArrayList<>();
        for (BlockLeaf leaf : partition.leaves) {
            if (leaf.kind == BlockKind.COMPOUND_MEMBER) continue;
            Compound compound = compoundBySeed.get(leaf);
            if (compound != null) {
                CompoundFiller cFiller = compoundFillers.get(compound.kind);
                if (cFiller != null) {
                    cFiller.fill(compound, grid, topology, plan.roadCells, roadReservation,
                            pois, doodads, tactical, rng);
                }
                continue;
            }
            BlockFiller filler = fillers.get(leaf.kind);
            filler.fill(leaf, grid, topology, pois, doodads, rng);
        }

        // Step 3a' — pedestrian-frame classification. Per-leaf-pair RNG roll
        // converts narrow road frames between pairs of non-vehicular leaves
        // (residential / plaza / park) into GRASS + curb-side SIDEWALK,
        // dropping the road in favor of a park-like inter-building pocket.
        // Adds visual variety to larger maps without disrupting the trunk
        // road network or any vehicular district's access.
        classifyPedestrianFrames(grid, topology, partition.leaves, plan, rng);

        // Step 3b — biome ground overrides. In conquest mode the BEACH biome
        // repaints its walkable non-building, non-water ground as SAND so the
        // beach reads as a continuous sandy landing zone rather than the
        // generator's default STREET grey. Runs after fill so we know which
        // cells are INDOOR (skip) vs outdoors (paint).
        if (biomeMap != null) {
            applyBiomeGroundOverrides(grid, topology, biomeMap);
        }

        // Step 3b' — beach shoreline. Carves a wavy water band along the
        // attacker-facing map edge inside the BEACH biome. Runs after the
        // SAND override so the water cells survive (the override skips
        // GroundKind.WATER). Strips any leaf-fill state at the shoreline
        // cells — doodads, walls, building hints, nature overlays — so
        // nothing else "lives" in the reserved water strip.
        if (biomeMap != null) {
            applyBeachShoreline(grid, topology, axis, biomeMap, roadReservation, doodads, rng);
        }

        // Step 3c — fortress super-wall. Stamps the Kremlin-style perimeter
        // around the FORTRESS_DISTRICT biome — wall rectangle inset into the
        // biome (kill-zone buffer between biome edge and wall), corner towers
        // + periodic heavy mid-towers, MG nests in the gaps, 1-3 gates on the
        // attacker-facing edge, and 2-4 forward bunkers in the kill zone.
        // Runs after fill so the wall overrides whatever BSP put under it.
        if (biomeMap != null) {
            FortressWallStamper.stamp(grid, topology, axis, biomeMap, roadReservation,
                    doodads, tactical, rng);
        }

        // Step 3c' — defense posts. Manned turret emplacements scattered through
        // BEACH (LIGHT vent rings), PORT (MEDIUM sandbag embankments), and the
        // FORTRESS_DISTRICT kill zone (LARGE multi-turret embankments). Runs
        // AFTER the fortress wall so kill-zone posts can't overlap wall cells
        // — the stamper's footprint validation auto-rejects non-walkable cells
        // produced by the wall and forward bunkers.
        if (biomeMap != null) {
            DefensePostStamper.stamp(grid, topology, axis, biomeMap, roadReservation,
                    doodads, tactical, defensePosts, rng);
        }

        // Step 3c'' — compound perimeter defenders. Walks the already-emitted
        // COMMAND_POST / BARRACKS / ARMORY tactical nodes and stamps a small
        // GUARDPOST on each compound's attacker-facing edge. Slice 5 of the
        // compound-as-supply design — gives the defender allocator explicit
        // lookout positions on the approach to each supply structure without
        // any allocator changes. See roadmap/conquest/central-keep.md.
        CompoundPerimeterDefenderStamper.stamp(grid, axis, tactical);

        // Step 3c''' — keep multi-chamber detection. When the COMMAND_POST
        // sub-building got a BuildingShellCore multi-room partition (≥7
        // cells in some dimension, 65% chance), emit an INNER_POSITION
        // tactical node anchored in the chamber that doesn't contain the
        // COMMAND_POST anchor — the antechamber. INNER_POSITION is an
        // interior garrison anchor only: not a compound, no capture marker,
        // not in the compound-leaf fallback graph. Defender allocator picks
        // it up Pass 1 so the antechamber gets manned. Slice 6 of
        // central-keep.md (patch 1 of the slice-6 fix sequence).
        KeepEntryChamberStamper.stamp(grid, topology, tactical);

        // Step 3d — link tactical nodes. Runs once after every node is
        // emitted (from compound fillers + fortress wall stamping); geometric
        // rules wire up OVERWATCHES, SUPPLIES, FALLBACK_TO, GUARDS. Always
        // runs even in legacy non-conquest mode — the compound fillers may
        // still have contributed BARRACKS/COMMAND/ARMORY nodes.
        this.lastTacticalMap = new TacticalMap(tactical);
        TacticalLinker.link(this.lastTacticalMap);

        // Step 4 — finalize: HP on walls, cover bake, wall flag, spawn anchors.
        seedWallHp(grid, topology);
        bakeCoverFromWalls(grid);
        topology.tagDefaultWalls(grid);

        // Step 4b — flood-fill building interiors from the stamped kind hints.
        // Runs after tagDefaultWalls so the wall predicate the flood-fill reads
        // is authoritative; the result is the Buildings registry that drives
        // the roof-render and fog-of-war visibility passes.
        Buildings buildings = BuildingFloodFill.populate(topology, seed);

        int[] marine;
        int[] defender;
        if (axis != null) {
            marine   = pickBiomeSpawn(grid, biomeMap, BiomeKind.BEACH,             rng, axis, false);
            defender = pickBiomeSpawn(grid, biomeMap, BiomeKind.FORTRESS_DISTRICT, rng, axis, true);
        } else {
            marine   = pickSpawnAnchor(grid, 1, 1, width / 2, height - 1, rng);
            defender = pickSpawnAnchor(grid, width / 2, 1, width - 1, height - 1, rng);
        }

        // Diagnostic — any centerline cell that ended up non-walkable means a
        // stamper trampled the road graph despite the reservation. Log once
        // per generation so a regression surfaces in starsector.log without
        // needing the dedicated dumper machinery.
        verifyRoadGraphWalkable(grid, this.lastRoadGraph);

        return new MapResult(grid, topology,
                marine[0], marine[1], defender[0], defender[1],
                pois, doodads, this.lastTacticalMap, buildings, defensePosts, this.lastRoadGraph);
    }

    private static void verifyRoadGraphWalkable(NavigationGrid grid, RoadGraph graph) {
        if (graph == null || graph.edges().isEmpty()) return;
        int blocked = 0;
        int firstX = -1, firstY = -1;
        for (RoadGraph.Edge e : graph.edges()) {
            for (int i = 0; i < e.cellsX.length; i++) {
                int x = e.cellsX[i];
                int y = e.cellsY[i];
                if (!grid.inBounds(x, y)) continue;
                if (grid.isWalkable(x, y)) continue;
                if (blocked == 0) { firstX = x; firstY = y; }
                blocked++;
            }
        }
        if (blocked > 0) {
            LOG.warn("BspCityGenerator: " + blocked + " road-graph cell(s) ended up non-walkable"
                    + " (first at " + firstX + "," + firstY + ") — a stamper bypassed the reservation");
        }
    }

    /** Minimum dimension a LANDING_ZONE leaf must have on both axes to keep that kind — smaller leaves get demoted to PLAZA, since tiny LZ pads tucked between big buildings read as "courtyard interior" rather than open touchdown apron. */
    private static final int LANDING_ZONE_MIN_SIDE = 5;

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
     *
     * <p>Per-kind size constraints applied after the roll:
     * <ul>
     *   <li>{@link BlockKind#LANDING_ZONE} requires both sides &gt;=
     *       {@link #LANDING_ZONE_MIN_SIDE}. Smaller leaves get demoted to
     *       {@link BlockKind#PLAZA} — a tiny striped pad wedged between
     *       building leaves reads visually as "courtyard inside the
     *       buildings" rather than as an open landing apron.</li>
     * </ul>
     */
    private void labelLeaves(Bsp.Partition partition, BiomeMap biomeMap,
                              DistrictMap districtMap, Random rng) {
        for (BlockLeaf leaf : partition.leaves) {
            MapDistrictTheme theme = (biomeMap != null)
                    ? biomeMap.themeAt(leaf.centerX(), leaf.centerY())
                    : districtMap.themeAt(leaf.centerX(), leaf.centerY());
            leaf.kind = theme.pickBlockKind(rng);
            if (leaf.kind == BlockKind.LANDING_ZONE
                    && (leaf.width() < LANDING_ZONE_MIN_SIDE
                        || leaf.height() < LANDING_ZONE_MIN_SIDE)) {
                leaf.kind = BlockKind.PLAZA;
            }
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

    /** Last tactical map produced by {@link #generate}. Null only if generation never ran. Conquest mode emits ~15-30 nodes; legacy mode emits whatever the compound fillers contribute (typically 0-5). */
    private TacticalMap lastTacticalMap;
    public TacticalMap getLastTacticalMap() { return lastTacticalMap; }

    /** Last road graph produced by {@link #generate}. Exposed for the preview test's overlay rendering. {@link RoadGraph#EMPTY} only if generation never ran. */
    private RoadGraph lastRoadGraph = RoadGraph.EMPTY;
    public RoadGraph getLastRoadGraph() { return lastRoadGraph; }

    /**
     * Paints one trunk's ground band onto the topology. If
     * {@link TrunkPlan.TrunkKind#sidewalkFlankWidth} is non-zero, the outer
     * {@code sidewalkFlankWidth} cells on each side of the band are tagged
     * {@link GroundKind#SIDEWALK} and the inner span is tagged
     * {@link TrunkPlan.TrunkKind#roadGround} — producing a "boulevard"
     * topology of road core + brick sidewalk curbs in one pass at gen time.
     *
     * <p>Bands too narrow to host the requested flanks (i.e.
     * {@code width <= 2*flank}) fall back to painting the entire band as
     * {@link GroundKind#SIDEWALK} so the configured kind still wins out
     * over the default {@link GroundKind#STREET} from step 0.
     */
    private static void paintTrunkGround(CellTopology topology, TrunkPlan.TrunkSegment trunk) {
        int flank = trunk.kind.sidewalkFlankWidth;
        int bandWidth = trunk.horizontal
                ? (trunk.bottom - trunk.top + 1)
                : (trunk.right - trunk.left + 1);
        boolean noRoadCore = bandWidth <= 2 * flank;
        if (trunk.horizontal) {
            for (int y = trunk.top; y <= trunk.bottom; y++) {
                int distFromEdge = Math.min(y - trunk.top, trunk.bottom - y);
                GroundKind kind = (flank > 0 && (noRoadCore || distFromEdge < flank))
                        ? GroundKind.SIDEWALK
                        : trunk.kind.roadGround;
                for (int x = trunk.left; x <= trunk.right; x++) {
                    topology.setGroundKind(x, y, kind);
                }
            }
        } else {
            for (int x = trunk.left; x <= trunk.right; x++) {
                int distFromEdge = Math.min(x - trunk.left, trunk.right - x);
                GroundKind kind = (flank > 0 && (noRoadCore || distFromEdge < flank))
                        ? GroundKind.SIDEWALK
                        : trunk.kind.roadGround;
                for (int y = trunk.top; y <= trunk.bottom; y++) {
                    topology.setGroundKind(x, y, kind);
                }
            }
        }
    }

    /** Cells of perpendicular-scan depth to find the leaf bounding a road frame. Catches every BSP frame (3-4 cells wide) but stops before the SECONDARY trunk (5 cells). */
    private static final int PEDESTRIAN_SCAN_DEPTH = 5;
    /** Frame widths up to this count qualify as "narrow" and may be converted to pedestrian zones. Wider frames stay vehicular. */
    private static final int PEDESTRIAN_MAX_FRAME_WIDTH = 4;
    /** Per-leaf-pair probability of converting their shared frame to a pedestrian zone. ~50% gives noticeable variety on larger maps without taking over. */
    private static final float PEDESTRIAN_FRAME_CHANCE = 0.8f;

    /**
     * Walks every {@link CellTopology.GroundKind#STREET} cell that isn't part
     * of a {@link TrunkPlan.TrunkSegment} band, finds the two leaves bounding
     * the frame perpendicular to the cell, and — if both leaves are
     * non-vehicular kinds and a per-pair RNG roll passes — converts the cell
     * to a pedestrian zone: {@link CellTopology.GroundKind#SIDEWALK} where
     * the cell butts up against a leaf (the curb-side strip), or
     * {@link CellTopology.GroundKind#GRASS} for cells in the frame interior.
     *
     * <p>Pair decisions are cached so all cells in the same frame agree on
     * the outcome — partial frames (some cells converted, some not) would
     * read as artifact.
     *
     * <p>Cells at four-way intersections (both perpendicular axes resolve to
     * a leaf within scan range) are skipped — intersections stay vehicular
     * regardless of district mix.
     */
    private void classifyPedestrianFrames(NavigationGrid grid, CellTopology topology,
                                          List<BlockLeaf> leaves, TrunkPlan.Plan plan,
                                          Random rng) {
        int w = grid.getWidth();
        int h = grid.getHeight();
        BlockLeaf[][] cellLeaf = new BlockLeaf[w][h];
        for (BlockLeaf leaf : leaves) {
            for (int y = leaf.top; y <= leaf.bottom; y++) {
                for (int x = leaf.left; x <= leaf.right; x++) {
                    if (x >= 0 && x < w && y >= 0 && y < h) cellLeaf[x][y] = leaf;
                }
            }
        }

        // Per-(unordered-pair-of-leaves) cached decision. IdentityHashMap so
        // BlockLeaf identity drives the key — there's no equals/hashCode on it.
        Map<BlockLeaf, Map<BlockLeaf, Boolean>> pairFlag = new IdentityHashMap<>();
        for (BlockLeaf leaf : leaves) pairFlag.put(leaf, new IdentityHashMap<>());

        int converted = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!topology.isStreet(x, y)) continue;
                if (isInTrunk(x, y, plan)) continue;
                BlockLeaf nLeaf = scanForLeaf(cellLeaf, x, y, 0,  1, w, h);
                BlockLeaf sLeaf = scanForLeaf(cellLeaf, x, y, 0, -1, w, h);
                BlockLeaf eLeaf = scanForLeaf(cellLeaf, x, y, 1,  0, w, h);
                BlockLeaf wLeaf = scanForLeaf(cellLeaf, x, y, -1, 0, w, h);
                boolean hasNS = nLeaf != null && sLeaf != null && nLeaf != sLeaf;
                boolean hasEW = eLeaf != null && wLeaf != null && eLeaf != wLeaf;
                if (hasNS == hasEW) continue;  // intersection (both) or unbounded (neither)
                BlockLeaf l1 = hasNS ? nLeaf : eLeaf;
                BlockLeaf l2 = hasNS ? sLeaf : wLeaf;
                if (!isPedestrianKind(l1.kind) || !isPedestrianKind(l2.kind)) continue;
                if (!leafPairConverts(pairFlag, l1, l2, rng)) continue;
                // Wall-adjacent cells (any cardinal neighbor is part of a
                // leaf) become SIDEWALK so the curb-side art kicks in; the
                // frame interior becomes GRASS. Width-3 frames produce a
                // SIDEWALK/GRASS/SIDEWALK strip; width-4 frames split into
                // SIDEWALK/GRASS/GRASS/SIDEWALK.
                if (isAdjacentToLeafCell(cellLeaf, x, y, w, h)) {
                    topology.setGroundKind(x, y, CellTopology.GroundKind.SIDEWALK);
                } else {
                    topology.setGroundKind(x, y, CellTopology.GroundKind.GRASS);
                }
                converted++;
            }
        }
        if (converted > 0) {
            LOG.info("BspCityGenerator: pedestrian-frame pass converted "
                    + converted + " STREET cell(s) to GRASS/SIDEWALK");
        }
    }

    /** True for {@link BlockKind}s that read as pedestrian — residential, plaza, park. Commercial / industrial / fortified / LZ keep their road access. */
    private static boolean isPedestrianKind(BlockKind kind) {
        switch (kind) {
            case BUILDING_RESIDENTIAL:
            case PLAZA:
            case PARK:
                return true;
            default:
                return false;
        }
    }

    /** Cell-in-rect predicate against {@link TrunkPlan.TrunkSegment}s. Pedestrian conversion always skips trunk cells regardless of the kinds touching them. */
    private static boolean isInTrunk(int x, int y, TrunkPlan.Plan plan) {
        for (TrunkPlan.TrunkSegment t : plan.trunks) {
            if (x >= t.left && x <= t.right && y >= t.top && y <= t.bottom) return true;
        }
        return false;
    }

    /** First non-null leaf hit when stepping {@code (dx, dy)} up to {@link #PEDESTRIAN_SCAN_DEPTH} cells. Null if none. */
    private static BlockLeaf scanForLeaf(BlockLeaf[][] cellLeaf, int x, int y,
                                         int dx, int dy, int w, int h) {
        for (int step = 1; step <= PEDESTRIAN_SCAN_DEPTH; step++) {
            int nx = x + dx * step;
            int ny = y + dy * step;
            if (nx < 0 || nx >= w || ny < 0 || ny >= h) return null;
            BlockLeaf leaf = cellLeaf[nx][ny];
            if (leaf != null) {
                if (step > PEDESTRIAN_MAX_FRAME_WIDTH) return null;  // frame too wide
                return leaf;
            }
        }
        return null;
    }

    /** True if any cardinal neighbor of {@code (x, y)} belongs to a leaf (so this STREET cell is curb-side against a building). */
    private static boolean isAdjacentToLeafCell(BlockLeaf[][] cellLeaf, int x, int y, int w, int h) {
        if (x + 1 < w && cellLeaf[x + 1][y] != null) return true;
        if (x - 1 >= 0 && cellLeaf[x - 1][y] != null) return true;
        if (y + 1 < h && cellLeaf[x][y + 1] != null) return true;
        if (y - 1 >= 0 && cellLeaf[x][y - 1] != null) return true;
        return false;
    }

    /** Per-pair decision, memoized in both directions so all cells in the same frame see the same result. */
    private static boolean leafPairConverts(Map<BlockLeaf, Map<BlockLeaf, Boolean>> pairFlag,
                                            BlockLeaf l1, BlockLeaf l2, Random rng) {
        Boolean cached = pairFlag.get(l1).get(l2);
        if (cached != null) return cached;
        boolean ped = rng.nextFloat() < PEDESTRIAN_FRAME_CHANCE;
        pairFlag.get(l1).put(l2, ped);
        pairFlag.get(l2).put(l1, ped);
        return ped;
    }

    /** Every non-walkable, non-water cell gets a starting HP. Water is non-walkable but isn't a destructible wall. */
    private void seedWallHp(NavigationGrid grid, CellTopology topology) {
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                if (!grid.isWalkable(x, y) && !topology.isWater(x, y)) {
                    grid.setWallHp(x, y, WALL_HP_DEFAULT);
                }
            }
        }
    }

    /** Per-facing cardinal-wall bake. Each facing reads 1 if a wall sits there, else 0. */
    private void bakeCoverFromWalls(NavigationGrid grid) {
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                grid.recomputeCoverAt(x, y);
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

    /** Nominal water depth at the attacker-facing edge of the BEACH biome, in cells. The wavy shore jitters around this baseline. */
    private static final int SHORELINE_NOMINAL_DEPTH = 3;
    /** Per-column jitter amplitude on the shore depth. Smoothed across neighbors so the boundary reads as gentle waves rather than sawtooth. */
    private static final int SHORELINE_JITTER = 2;
    /** Rolling window radius for the shore-boundary smoothing. Larger = longer wavelength. */
    private static final int SHORELINE_SMOOTH_RADIUS = 4;
    /** Minimum guaranteed water depth at every column. Keeps at least one continuous water row so the shoreline never has a "dry gap". */
    private static final int SHORELINE_MIN_DEPTH = 1;
    /** Maximum allowed water depth at any column. Prevents the jitter from eating the entire beach. */
    private static final int SHORELINE_MAX_DEPTH = 5;

    /**
     * Stamps a wavy water band along the attacker-facing map edge inside the
     * BEACH biome. For {@link TraversalAxis#SOUTH_TO_NORTH} the band sits at
     * the bottom of the map (low {@code y}); for {@link TraversalAxis#WEST_TO_EAST}
     * at the left (low {@code x}). Depth varies per-column (or per-row) via
     * smoothed random jitter so the sand/water boundary reads as a gentle
     * coastline.
     *
     * <p>For each cell flipped to water this method also strips:
     * <ul>
     *   <li>the {@code WALL} tag, so leaf-stamped walls don't draw on top of
     *       water;</li>
     *   <li>the nature overlay, so plant/rock sprites don't float on water;</li>
     *   <li>the building-kind hint + building id, so the flood-fill pass
     *       doesn't mistake a half-drowned building cell for a real interior;</li>
     *   <li>any {@link Doodad} whose cell falls inside the band.</li>
     * </ul>
     * Together these guarantee the shoreline strip contains nothing but
     * walkable-blocking water — buildings or LZs that the BSP placed there
     * are cleanly evicted.
     *
     * <p>Wall-direction masks at the water cells are also cleared so the
     * subsequent {@code tagDefaultWalls} pass can't pick up stale bits.
     */
    private void applyBeachShoreline(NavigationGrid grid, CellTopology topology,
                                     TraversalAxis axis, BiomeMap biomeMap,
                                     boolean[][] roadReservation,
                                     List<Doodad> doodads, Random rng) {
        int w = grid.getWidth();
        int h = grid.getHeight();
        boolean horizontalAxis = (axis == TraversalAxis.SOUTH_TO_NORTH);

        // Per-perpendicular-coordinate shore depth. For S->N, depth indexed
        // by x; for W->E, depth indexed by y. Smoothed noise so the boundary
        // reads as gentle waves.
        int spanLen = horizontalAxis ? w : h;
        int[] depths = noisyShoreDepth(spanLen, SHORELINE_NOMINAL_DEPTH,
                SHORELINE_JITTER, SHORELINE_SMOOTH_RADIUS, rng);

        for (int i = 0; i < spanLen; i++) {
            int depth = clamp(depths[i], SHORELINE_MIN_DEPTH, SHORELINE_MAX_DEPTH);
            for (int j = 0; j < depth; j++) {
                int x = horizontalAxis ? i : j;
                int y = horizontalAxis ? j : i;
                if (x < 0 || x >= w || y < 0 || y >= h) continue;
                if (biomeMap.biomeAt(x, y) != BiomeKind.BEACH) continue;
                // Trunk centerline meets the shore — the road runs onto a
                // jetty or pier here; drowning the cell would orphan the
                // convoy's perimeter exit. Leave it dry.
                if (roadReservation[x][y]) continue;
                stampShorelineCell(grid, topology, x, y);
            }
        }

        // Drop any doodad whose cell got drowned. Pre-shoreline filler doodads
        // (LZ arrows, beach crates, etc.) would otherwise float on water.
        if (!doodads.isEmpty()) {
            doodads.removeIf(d -> {
                if (!grid.inBounds(d.cellX, d.cellY)) return false;
                return topology.isWater(d.cellX, d.cellY);
            });
        }
    }

    /** Stamp a single shoreline cell as water + strip everything else that might still be tagged at this cell. */
    private static void stampShorelineCell(NavigationGrid grid, CellTopology topology, int x, int y) {
        topology.setGroundKind(x, y, GroundKind.WATER);
        topology.setTag(x, y, CellTopology.Tag.WALL,    false);
        topology.setTag(x, y, CellTopology.Tag.VEHICLE, false);
        topology.setWallDirMask(x, y, 0);
        topology.setBuildingId(x, y, 0);
        topology.setBuildingKindHint(x, y, null);
        topology.setNatureOverlay(x, y, null);
        // Water is non-walkable but see-through — matches WaterfrontFiller /
        // NatureZoneFiller convention. setWalkable preserves any existing
        // floor flags we don't care about here.
        grid.setWalkable(x, y, false);
        grid.setSeeThrough(x, y, true);
    }

    /**
     * Per-coordinate shore-depth array of length {@code len}, centered on
     * {@code nominal} with smoothed jitter. Same noise+rolling-average shape
     * as {@code BiomeMap.noisyBoundary}; duplicated locally rather than
     * pulled into a shared helper because the smoothed-shore use cases are
     * still small and the parameters differ.
     */
    private static int[] noisyShoreDepth(int len, int nominal, int jitter, int smoothRadius, Random rng) {
        float[] raw = new float[len];
        for (int i = 0; i < len; i++) {
            raw[i] = (rng.nextFloat() - 0.5f) * 2f * jitter;
        }
        int[] out = new int[len];
        for (int i = 0; i < len; i++) {
            int lo = Math.max(0, i - smoothRadius);
            int hi = Math.min(len - 1, i + smoothRadius);
            float sum = 0f;
            for (int j = lo; j <= hi; j++) sum += raw[j];
            out[i] = nominal + Math.round(sum / (hi - lo + 1));
        }
        return out;
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
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
