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
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.WastelandRubbleFiller;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.WaterfrontFiller;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.BeachShorelineStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.BiomeGroundOverrideStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.BspPartitionStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.CompoundClaimStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.CompoundSeedStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.FillDispatchStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.FinalizeStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.InitFloorStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.LabelLeavesStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.PedestrianFrameStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.RoadGraphStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.SpawnAnchorStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.TacticalLinkStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.TrunkSkeletonStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.ZoningOverlayStage;
import com.dillon.starsectormarines.battle.world.gen.road.RoadGraph;
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

        this.conquestRecipe = buildConquestRecipe();
        this.legacyRecipe = buildLegacyRecipe();
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
                new SpawnAnchorStage()));                   // spawn anchors
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
        Random rng = new Random(seed);
        NavigationGrid grid = new NavigationGrid(width, height);
        CellTopology topology = new CellTopology(width, height);

        // The generation blackboard. Spine (grid/topology/rng/seed + the output
        // accumulators) lives on the context; optional overlays (axis, biome
        // map, road masks, compounds, pipeline intermediates) are bound under
        // BspKeys as the stages compute them.
        GenContext ctx = new GenContext(grid, topology, rng, width, height, seed);
        if (axis != null) ctx.put(BspKeys.AXIS, axis);

        // Recipe selection is the conquest/legacy fork: axis present → the full
        // conquest sequence; axis absent → the legacy district recipe (which
        // omits the conquest-only stages rather than running them as no-ops).
        GenRecipe recipe = (axis != null) ? conquestRecipe : legacyRecipe;
        recipe.run(ctx);

        // Surface the preview accessors + assemble the result from the context.
        this.lastBiomeMap = ctx.get(BspKeys.BIOME_MAP);
        this.lastDistrictMap = ctx.get(BspKeys.DISTRICT_MAP);
        List<Compound> compounds = ctx.get(BspKeys.COMPOUNDS);
        this.lastCompounds = compounds != null ? compounds : new ArrayList<>();
        this.lastTacticalMap = ctx.get(BspKeys.TACTICAL_MAP);
        RoadGraph roadGraph = ctx.get(BspKeys.ROAD_GRAPH);
        this.lastRoadGraph = roadGraph != null ? roadGraph : RoadGraph.EMPTY;

        Buildings buildings = ctx.get(BspKeys.BUILDINGS);
        int[] marine = ctx.get(BspKeys.MARINE_SPAWN);
        int[] defender = ctx.get(BspKeys.DEFENDER_SPAWN);

        return new MapResult(grid, topology,
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
}
