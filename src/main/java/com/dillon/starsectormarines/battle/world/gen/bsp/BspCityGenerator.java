package com.dillon.starsectormarines.battle.world.gen.bsp;

import com.dillon.starsectormarines.battle.world.model.Buildings;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.gen.BlockFiller;
import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenRecipe;
import com.dillon.starsectormarines.battle.world.gen.GenStage;
import com.dillon.starsectormarines.battle.world.gen.MapGenerator;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.BuildingCommercialFiller;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.BuildingIndustrialFiller;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.BuildingResidentialFiller;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.DenseBlockFiller;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.FortifiedPostFiller;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.IndustrialYardFiller;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.DenseQuarterFiller;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.GatedHousingFiller;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.LandingZoneFiller;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.MilitaryBaseFiller;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.NatureZoneFiller;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.ParkFiller;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.PlazaFiller;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.SpaceportFiller;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.WastelandRubbleFiller;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.WaterfrontFiller;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.BeachShorelineStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.BiomeGroundOverrideStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.BspPartitionStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.CompoundClaimStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.CompoundSeedStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.ConcentricLayoutStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.CoreSpawnStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.DiamondLayoutStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.CorridorStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.FillDispatchStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.FinalizeStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.InitFloorStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.InitSolidStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.LabelLeavesStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.PedestrianFrameStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.RoadGraphStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.RoomCarveStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.SpawnAnchorStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.StationPartitionStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.StationSpawnStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.StationTopologyStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.TacticalLinkStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.TacticalRegionStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.TrunkSkeletonStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.ZoningOverlayStage;
import com.dillon.starsectormarines.battle.world.gen.road.RoadGraph;
import com.dillon.starsectormarines.battle.world.gen.taxonomy.TacticalRegionMap;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.decision.TacticalMap;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The BSP-based urban map generator, reified as a context + stage + recipe
 * pipeline. The {@code generate()} entry point builds a {@link GenContext}
 * blackboard, selects a {@link GenRecipe}, runs it, and assembles the
 * {@link MapResult} from the context. Each numbered step of the legacy monolith
 * lives in its own stage — the per-step passes under {@code bsp.stage}, plus the
 * four post-fill stampers ({@link FortressWallStamper}, {@link DefensePostStamper},
 * {@link CompoundPerimeterDefenderStamper}, {@link KeepEntryChamberStamper}),
 * which each implement {@link GenStage} directly.
 *
 * <p>The conquest/legacy fork is now <b>recipe membership</b>: the conquest
 * recipe runs the full stage list, the legacy recipe omits the conquest-only
 * stages ({@code CompoundSeed}, biome ground override, beach shoreline, fortress
 * wall, defense posts, compound-perimeter defenders) entirely. Stages shared by
 * both modes ({@code ZoningOverlay}, {@code LabelLeaves}, {@code CompoundClaim},
 * {@code SpawnAnchor}) still fork internally on {@link BspKeys#BIOME_MAP} /
 * {@link BspKeys#AXIS}. {@code generate(…, axis)} picks the recipe by axis
 * presence; output is byte-identical to the pre-recipe single-list path.
 *
 * <p>Filler registration happens once in the constructor. New per-kind fillers
 * replace the {@link StubBlockFiller} entries via {@code register(...)}; the
 * {@link FillDispatchStage} holds the registries by reference so post-construction
 * swaps are still seen.
 */
public final class BspCityGenerator implements MapGenerator {

    private final Map<BlockKind, BlockFiller> fillers = new EnumMap<>(BlockKind.class);
    private final Map<BlockKind, CompoundFiller> compoundFillers = new EnumMap<>(BlockKind.class);

    /** Conquest map recipe (full stage list). Built once in the constructor; replayed per {@code generate()} call against a fresh context. */
    private final GenRecipe conquestRecipe;
    /** Legacy district-urban recipe (conquest-only stages omitted). */
    private final GenRecipe legacyRecipe;

    /** Station-interior recipe — the inverted (solid-default) rooms-and-corridors map type. Selected via {@link #generateStation}. */
    private final GenRecipe stationRecipe;

    /** Concentric "onion" station recipe — defensive rings around a central core. Selected via {@link #generateConcentricStation}. */
    private final GenRecipe concentricStationRecipe;

    /** Diamond defense-station recipe — cardinal ports converging inward to a besieged core. Selected via {@link #generateDiamondStation}. */
    private final GenRecipe diamondStationRecipe;

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
        register(new SpaceportFiller());
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

        this.conquestRecipe = buildConquestRecipe();
        this.legacyRecipe = buildLegacyRecipe();
        this.stationRecipe = buildStationRecipe();
        this.concentricStationRecipe = buildConcentricStationRecipe();
        this.diamondStationRecipe = buildDiamondStationRecipe();
    }

    /**
     * The conquest map recipe — the full stage sequence, in call order. The
     * biome stages + conquest-only stampers still self-gate on
     * {@link BspKeys#BIOME_MAP} / {@link BspKeys#AXIS} (belt-and-suspenders), but
     * the axis is always bound on the conquest path so every stage proceeds.
     * Output is byte-identical to the pre-recipe single-list path — same
     * {@code rng} draws in the same order.
     */
    private GenRecipe buildConquestRecipe() {
        return new GenRecipe("ConquestCity", List.of(
                new InitFloorStage(),                       // Step 0
                new TrunkSkeletonStage(),                   // Step 1a
                new BspPartitionStage(),                    // Step 1b
                new ZoningOverlayStage(),                   // Step 1c
                new LabelLeavesStage(),                     // Step 2
                new CompoundSeedStage(),                    // Step 2a   conquest-only
                new CompoundClaimStage(),                   // Step 2b
                new RoadGraphStage(),                       // Step 2c
                new FillDispatchStage(fillers, compoundFillers), // Step 3
                new PedestrianFrameStage(),                 // Step 3a'
                new BiomeGroundOverrideStage(),             // Step 3b   conquest-only
                new BeachShorelineStage(),                  // Step 3b'  conquest-only
                new FortressWallStamper(),                  // Step 3c   conquest-only
                new DefensePostStamper(),                   // Step 3c'  conquest-only
                new CompoundPerimeterDefenderStamper(),     // Step 3c'' conquest-only
                new KeepEntryChamberStamper(),              // Step 3c'''
                new TacticalLinkStage(),                    // Step 3d
                new FinalizeStage(),                        // Step 4 + 4b
                new TacticalRegionStage(),                  // structural taxonomy (post-finalize)
                new OverwatchTowerStage(),                  // taxonomy consumer — corner-tower guns
                new SpawnAnchorStage()));                   // spawn anchors
    }

    /**
     * The legacy district-urban recipe — the conquest recipe minus the six
     * conquest-only stages (compound seeding, biome ground override, beach
     * shoreline, fortress wall, defense posts, compound-perimeter defenders).
     * Each omitted stage was verified RNG- and mutation-inert when
     * {@link BspKeys#BIOME_MAP} / {@link BspKeys#AXIS} is unbound, so dropping
     * them from the list reproduces the old legacy path exactly: the kept stages
     * keep their relative order and see an identical {@code rng} stream. The
     * shared stages ({@code ZoningOverlay} / {@code LabelLeaves} /
     * {@code CompoundClaim} / {@code SpawnAnchor}) fork to their district / legacy
     * behavior internally.
     */
    private GenRecipe buildLegacyRecipe() {
        return new GenRecipe("LegacyUrban", List.of(
                new InitFloorStage(),                       // Step 0
                new TrunkSkeletonStage(),                   // Step 1a
                new BspPartitionStage(),                    // Step 1b
                new ZoningOverlayStage(),                   // Step 1c   binds DISTRICT_MAP
                new LabelLeavesStage(),                     // Step 2
                new CompoundClaimStage(),                   // Step 2b
                new RoadGraphStage(),                       // Step 2c
                new FillDispatchStage(fillers, compoundFillers), // Step 3
                new PedestrianFrameStage(),                 // Step 3a'
                new KeepEntryChamberStamper(),              // Step 3c'''
                new TacticalLinkStage(),                    // Step 3d
                new FinalizeStage(),                        // Step 4 + 4b
                new TacticalRegionStage(),                  // structural taxonomy (post-finalize)
                new SpawnAnchorStage()));                   // spawn anchors
    }

    /**
     * The station-interior recipe — the inversion of the city. Where the urban
     * recipes start all-walkable and carve walls, the station starts all-solid
     * ({@link InitSolidStage}) and carves rooms ({@link RoomCarveStage}) out of
     * the BSP leaves, then a {@link CorridorStage} connects them along a
     * spanning-tree-plus-sparse-loops subset of the leaf-adjacency graph and
     * publishes the room/corridor {@link StationGraph}. Spawns land at the two
     * ends of that graph's diameter ({@link StationSpawnStage}). The generic
     * {@link TacticalLinkStage} + {@link FinalizeStage} are reused verbatim
     * (no tactical nodes yet → an empty {@code TacticalMap}; finalize tags the
     * un-carved hull as wall and flood-fills the interior buildings).
     */
    private GenRecipe buildStationRecipe() {
        return new GenRecipe("Station", List.of(
                new InitSolidStage(),         // solid hull
                new StationPartitionStage(),  // BSP leaves (reuses Bsp.partition)
                new RoomCarveStage(),         // carve one room per leaf
                new CorridorStage(),          // connect rooms; publish StationGraph
                new StationSpawnStage(),      // diameter-endpoint spawns
                new StationTopologyStage(),   // derive depth / articulation / bridge / on-loop roles
                new TacticalLinkStage(),      // (empty node list → empty map)
                new FinalizeStage()));        // wall HP / cover / wall tags / buildings
    }

    /**
     * The concentric "onion" station recipe — the defense-station layout. Same
     * solid-default inversion as {@link #buildStationRecipe}, but
     * {@link ConcentricLayoutStage} replaces the BSP partition + corridor with
     * nested defensive rings around a central control core, and
     * {@link CoreSpawnStage} pins the defender to the core / the marine to the
     * outer ring. {@link StationTopologyStage} then reads the gates as bridges
     * and the radial gradient as depth-from-entry; the generic
     * {@link TacticalLinkStage} + {@link FinalizeStage} are reused verbatim.
     */
    private GenRecipe buildConcentricStationRecipe() {
        return new GenRecipe("ConcentricStation", List.of(
                new InitSolidStage(),         // solid hull
                new ConcentricLayoutStage(),  // rings + core + doors + gates; publish StationGraph
                new CoreSpawnStage(),         // defender at core, marine at outer ring
                new StationTopologyStage(),   // radial depth / gate bridges / on-loop
                new TacticalLinkStage(),      // (empty node list → empty map)
                new FinalizeStage()));        // wall HP / cover / wall tags / buildings
    }

    /**
     * The diamond defense-station recipe — the cardinal-ports-converging-inward
     * layout. {@link DiamondLayoutStage} replaces the concentric layout with a
     * diamond footprint (dead map corners), isolated outer ports, radial cardinal
     * spokes, and a single connective ring; {@link CoreSpawnStage} (reused) drops
     * the marine at a random port and the defender at the core.
     */
    private GenRecipe buildDiamondStationRecipe() {
        return new GenRecipe("DiamondStation", List.of(
                new InitSolidStage(),         // solid hull
                new DiamondLayoutStage(),     // diamond rings + ports + spokes; publish StationGraph
                new CoreSpawnStage(),         // defender at core, marine at a port
                new StationTopologyStage(),   // radial depth / port + spoke bridges / connective loop
                new TacticalLinkStage(),
                new FinalizeStage()));
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
     * BEACH biome as {@code SAND}, and pins the marine spawn to the beach end
     * and the defender spawn to the fortress end.
     *
     * <p>When {@code axis} is null this delegates to the legacy district-driven
     * pipeline — used by the smaller 80×80 preview path and any non-conquest
     * generation. The fork is expressed as {@link BspKeys#AXIS} presence: the
     * biome stages no-op without it.
     */
    @Override
    public MapResult generate(int width, int height, long seed, TraversalAxis axis) {
        return generate(width, height, seed, axis, TargetProfile.NEUTRAL);
    }

    /**
     * Canonical entry point — all overloads funnel here. The {@code profile}
     * (campaign → battle bridge) is bound on the context under
     * {@link BspKeys#MARKET_PROFILE} for stages that scale off the target world
     * (e.g. {@link OverwatchTowerStage}'s defense intensity);
     * {@link TargetProfile#NEUTRAL} reproduces the pre-bridge output.
     */
    @Override
    public MapResult generate(int width, int height, long seed, TraversalAxis axis, TargetProfile profile) {
        Random rng = new Random(seed);
        NavigationGrid grid = new NavigationGrid(width, height);
        CellTopology topology = new CellTopology(width, height);

        // The generation blackboard. Spine (grid/topology/rng/seed + the output
        // accumulators) lives on the context; optional overlays (axis, biome
        // map, road masks, compounds, pipeline intermediates) are bound under
        // BspKeys as the stages compute them.
        GenContext ctx = new GenContext(grid, topology, rng, width, height, seed);
        if (axis != null) ctx.put(BspKeys.AXIS, axis);
        ctx.put(BspKeys.MARKET_PROFILE, profile != null ? profile : TargetProfile.NEUTRAL);

        // Recipe selection is the conquest/legacy fork: axis present → the full
        // conquest sequence; axis absent → the legacy district recipe (which
        // omits the conquest-only stages rather than running them as no-ops).
        GenRecipe recipe = (axis != null) ? conquestRecipe : legacyRecipe;
        recipe.run(ctx);

        return assembleResult(ctx);
    }

    /**
     * Station-interior generation — the inverted (solid-default) rooms-and-
     * corridors map type. Runs the {@link #buildStationRecipe() station recipe}
     * against a fresh context and assembles the {@link MapResult} via the same
     * tail as {@link #generate}. Not part of the {@link MapGenerator} interface
     * (no production caller selects stations yet); the preview/scan gut-check
     * tests drive it directly, and it's the entry battle setup will call once
     * stations are wired in.
     */
    public MapResult generateStation(int width, int height, long seed) {
        Random rng = new Random(seed);
        NavigationGrid grid = new NavigationGrid(width, height);
        CellTopology topology = new CellTopology(width, height);

        GenContext ctx = new GenContext(grid, topology, rng, width, height, seed);
        ctx.put(BspKeys.MARKET_PROFILE, TargetProfile.NEUTRAL);

        stationRecipe.run(ctx);

        return assembleResult(ctx);
    }

    /**
     * Concentric "onion" station generation — the defense-station layout:
     * defensive rings around a central control core, the player breaching the
     * outer ring and fighting inward through gated ring walls. Like
     * {@link #generateStation} it's not on the {@link MapGenerator} interface
     * (no production caller selects stations yet); the preview/scan tests drive
     * it directly.
     */
    public MapResult generateConcentricStation(int width, int height, long seed) {
        Random rng = new Random(seed);
        NavigationGrid grid = new NavigationGrid(width, height);
        CellTopology topology = new CellTopology(width, height);

        GenContext ctx = new GenContext(grid, topology, rng, width, height, seed);
        ctx.put(BspKeys.MARKET_PROFILE, TargetProfile.NEUTRAL);

        concentricStationRecipe.run(ctx);

        return assembleResult(ctx);
    }

    /**
     * Diamond defense-station generation — cardinal ports converging inward to a
     * besieged core (dead map corners, isolated outer spokes, a connective inner
     * ring). Like the other station entries it's not on the {@link MapGenerator}
     * interface yet; the preview/scan tests drive it directly.
     */
    public MapResult generateDiamondStation(int width, int height, long seed) {
        Random rng = new Random(seed);
        NavigationGrid grid = new NavigationGrid(width, height);
        CellTopology topology = new CellTopology(width, height);

        GenContext ctx = new GenContext(grid, topology, rng, width, height, seed);
        ctx.put(BspKeys.MARKET_PROFILE, TargetProfile.NEUTRAL);

        diamondStationRecipe.run(ctx);

        return assembleResult(ctx);
    }

    /**
     * Surface the preview accessors and assemble the {@link MapResult} from a
     * finished context. Overlays a given recipe didn't produce read back null
     * and degrade gracefully (empty compound list, {@link RoadGraph#EMPTY}, null
     * biome/district/tactical-region maps) — so this is shared verbatim across
     * the urban and station recipes.
     */
    private MapResult assembleResult(GenContext ctx) {
        this.lastBiomeMap = ctx.get(BspKeys.BIOME_MAP);
        this.lastDistrictMap = ctx.get(BspKeys.DISTRICT_MAP);
        List<Compound> compounds = ctx.get(BspKeys.COMPOUNDS);
        this.lastCompounds = compounds != null ? compounds : new ArrayList<>();
        this.lastTacticalMap = ctx.get(BspKeys.TACTICAL_MAP);
        RoadGraph roadGraph = ctx.get(BspKeys.ROAD_GRAPH);
        this.lastRoadGraph = roadGraph != null ? roadGraph : RoadGraph.EMPTY;
        this.lastTacticalRegions = ctx.get(BspKeys.TACTICAL_REGIONS);
        this.lastStationGraph = ctx.get(BspKeys.STATION_GRAPH);

        Buildings buildings = ctx.get(BspKeys.BUILDINGS);
        int[] marine = ctx.get(BspKeys.MARINE_SPAWN);
        int[] defender = ctx.get(BspKeys.DEFENDER_SPAWN);

        return new MapResult(ctx.grid, ctx.topology,
                marine[0], marine[1], defender[0], defender[1],
                ctx.pois, ctx.doodads, this.lastTacticalMap, buildings,
                ctx.defensePosts, this.lastRoadGraph);
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

    /** Last tactical-region segmentation produced by {@link #generate} — the structural-taxonomy artifact. Exposed for the preview/analysis test; null only if generation never ran. */
    private TacticalRegionMap lastTacticalRegions;
    public TacticalRegionMap getLastTacticalRegions() { return lastTacticalRegions; }

    /** Last station room/corridor graph produced by {@link #generateStation} (with topological roles applied). Exposed for the station preview/topology tests; null in city modes. */
    private StationGraph lastStationGraph;
    public StationGraph getLastStationGraph() { return lastStationGraph; }
}
