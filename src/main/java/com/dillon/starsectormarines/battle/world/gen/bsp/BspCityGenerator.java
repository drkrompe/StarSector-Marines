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
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SettlementZoning;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.BuildingCommercialFiller;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.AirbaseCompoundFiller;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.AirbasePadFiller;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.BuildingCivicFiller;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.BuildingIndustrialFiller;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.BuildingResidentialFiller;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.DenseBlockFiller;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.FortifiedPostFiller;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.IndustrialYardFiller;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.IndustrialCompoundFiller;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.DenseQuarterFiller;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.GatedHousingFiller;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.LandingZoneFiller;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.MilitaryBaseFiller;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.MedicalCampusFiller;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.NatureZoneFiller;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.ParkFiller;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.PlazaFiller;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.SpaceportFiller;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.SpaceportDistrictFiller;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.WastelandRubbleFiller;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.WaterfrontFiller;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.BeachShorelineStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.AirbasePadSeedStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.BiomeGroundOverrideStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.BspPartitionStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.CompoundClaimStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.CompoundSeedStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.ConquestLandingAreaStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.ConcentricLayoutStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.CoreSpawnStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.DiamondLayoutStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.CorridorStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.DoorwayClearanceStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.FillDispatchStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.FortressWardStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.FinalizeStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.FrontDepthStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.InteriorAnchorFitStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.HinterlandFillStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.PrecinctSkeletonStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.PrecinctDefenceStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.PrecinctLandingAreaStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.PrecinctWardStage;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.InitFloorStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.InitSolidStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.LabelLeavesStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.PedestrianFrameStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.RoadGraphStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.RoomCarveStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.SettlementLandingLinkStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.SpawnAnchorStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.SpaceportDistrictPlanStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.StationPartitionStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.StationSpawnStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.StationTopologyStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.TacticalLinkStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.TacticalRegionStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.GrownTrunkSkeletonStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.TrunkSkeletonStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.VehicleCorridorStage;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.ZoningOverlayStage;
import com.dillon.starsectormarines.battle.world.gen.road.RoadGraph;
import com.dillon.starsectormarines.battle.world.gen.taxonomy.TacticalRegionMap;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.decision.TacticalNode;

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

    /** Conquest map recipe (full stage list). Rebuilt by the constructor and by {@link #useGrownRoads}; replayed per {@code generate()} call against a fresh context. */
    private GenRecipe conquestRecipe;
    /** Legacy district-urban recipe (conquest-only stages omitted). Rebuilt alongside {@link #conquestRecipe}. */
    private GenRecipe legacyRecipe;
    /** Explicit grown-roads override from {@link #useGrownRoads}; null means "ask the campaign". */
    private GrownTrunkPlan.Profile grownOverride;

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
        register(new BuildingCivicFiller());
        register(new BuildingIndustrialFiller());
        register(new FortifiedPostFiller());
        register(new LandingZoneFiller());
        register(new SpaceportFiller());
        register(new AirbasePadFiller());
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
        registerCompound(new IndustrialCompoundFiller());
        registerCompound(new MedicalCampusFiller());
        registerCompound(new SpaceportDistrictFiller());
        registerCompound(new AirbaseCompoundFiller());

        this.conquestRecipe = buildConquestRecipe(new TrunkSkeletonStage(), null, null);
        this.legacyRecipe = buildLegacyRecipe(new TrunkSkeletonStage(), null, null);
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
     *
     * @param trunkStage the Step 1a trunk-planning stage — {@link TrunkSkeletonStage}
     *                   by default, or a {@link GrownTrunkSkeletonStage} when
     *                   {@link #useGrownRoads} has installed one.
     */
    private GenRecipe buildConquestRecipe(GenStage trunkStage, GenStage hinterlandStage,
                                          GenStage landingLinkStage) {
        return new GenRecipe("ConquestCity", compose(
                new InitFloorStage(),                       // Step 0
                trunkStage,                                 // Step 1a
                new BspPartitionStage(),                    // Step 1b
                new ZoningOverlayStage(),                   // Step 1c
                new LabelLeavesStage(),                     // Step 2
                new CompoundSeedStage(),                    // Step 2a   conquest-only
                new AirbasePadSeedStage(),                  // Step 2a'  city landmark
                landingLinkStage,                           // Step 2a'' landing-linked settlements only
                new CompoundClaimStage(),                   // Step 2b
                new RoadGraphStage(),                       // Step 2c
                new VehicleCorridorStage(),                 // Step 2c'  conquest-only
                new FillDispatchStage(fillers, compoundFillers), // Step 3
                hinterlandStage,                            // Step 3a   grown-roads-only; null omits it
                new PedestrianFrameStage(),                 // Step 3a'
                new BiomeGroundOverrideStage(),             // Step 3b   conquest-only
                new BeachShorelineStage(),                  // Step 3b'  conquest-only
                new FortressWardStage(),                    // Step 3b'' conquest-only
                new FortressWallStamper(),                  // Step 3c   conquest-only
                new DefensePostStamper(),                   // Step 3c'  conquest-only
                new CompoundPerimeterDefenderStamper(),     // Step 3c'' conquest-only
                new KeepEntryChamberStamper(),              // Step 3c'''
                new TacticalLinkStage(),                    // Step 3d
                new FinalizeStage(),                        // Step 4 + 4b
                new DoorwayClearanceStage(),                // Step 4c   nothing stands in a doorway
                new TacticalRegionStage(),                  // structural taxonomy (post-finalize)
                new OverwatchTowerStage(),                  // taxonomy consumer — corner-tower guns
                new SpawnAnchorStage(),                     // spawn anchors
                new ConquestLandingAreaStage(),             // paired BEACH arrival geometry
                new FrontDepthStage(),                      // the front, as a depth
                new InteriorAnchorFitStage()));             // closing: POI anchors vs the finished grid
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
     *
     * @param trunkStage the Step 1a trunk-planning stage — {@link TrunkSkeletonStage}
     *                   by default, or a {@link GrownTrunkSkeletonStage} when
     *                   {@link #useGrownRoads} has installed one.
     */
    private GenRecipe buildLegacyRecipe(GenStage trunkStage, GenStage hinterlandStage,
                                        GenStage landingLinkStage) {
        return buildLegacyRecipe(trunkStage, hinterlandStage, landingLinkStage, null);
    }

    private GenRecipe buildLegacyRecipe(GenStage trunkStage, GenStage hinterlandStage,
                                        GenStage landingLinkStage, GenStage wardStage) {
        return buildLegacyRecipe(trunkStage, hinterlandStage, landingLinkStage, wardStage, null);
    }

    private GenRecipe buildLegacyRecipe(GenStage trunkStage, GenStage hinterlandStage,
                                        GenStage landingLinkStage, GenStage wardStage,
                                        GenStage defenceStage) {
        return buildLegacyRecipe(trunkStage, hinterlandStage, landingLinkStage,
                wardStage, defenceStage, null, null);
    }

    private GenRecipe buildLegacyRecipe(GenStage trunkStage, GenStage hinterlandStage,
                                        GenStage landingLinkStage, GenStage wardStage,
                                        GenStage defenceStage, GenStage landingAreaStage,
                                        GenStage frontStage) {
        return new GenRecipe("LegacyUrban", compose(
                new InitFloorStage(),                       // Step 0
                trunkStage,                                 // Step 1a
                new BspPartitionStage(),                    // Step 1b
                new ZoningOverlayStage(),                   // Step 1c   binds DISTRICT_MAP
                new LabelLeavesStage(),                     // Step 2
                new SpaceportDistrictPlanStage(),           // Step 2a   campaign civilian spaceport
                landingLinkStage,                           // Step 2a'' landing-linked settlements only
                new CompoundClaimStage(),                   // Step 2b
                new RoadGraphStage(),                       // Step 2c
                new FillDispatchStage(fillers, compoundFillers), // Step 3
                hinterlandStage,                            // Step 3a   grown-roads-only; null omits it
                new PedestrianFrameStage(),                 // Step 3a'
                wardStage,                                  // Step 3b'' precinct-only; null omits it
                defenceStage,                               // Step 3c'  precinct-only; null omits it
                new KeepEntryChamberStamper(),              // Step 3c'''
                new TacticalLinkStage(),                    // Step 3d
                new FinalizeStage(),                        // Step 4 + 4b
                new DoorwayClearanceStage(),                // Step 4c   nothing stands in a doorway
                new TacticalRegionStage(),                  // structural taxonomy (post-finalize)
                new SpawnAnchorStage(),                     // spawn anchors
                landingAreaStage,                           // precinct-only; null omits it
                frontStage,                                 // precinct-only; null omits it
                new InteriorAnchorFitStage()));             // closing: POI anchors vs the finished grid
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
                new FinalizeStage(),          // wall HP / cover / wall tags / buildings
                new InteriorAnchorFitStage()));  // closing: POI anchors vs the finished grid
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
                new FinalizeStage(),          // wall HP / cover / wall tags / buildings
                new InteriorAnchorFitStage()));  // closing: POI anchors vs the finished grid
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
                new FinalizeStage(),
                new InteriorAnchorFitStage()));  // closing: POI anchors vs the finished grid
    }

    /** Stage list with null entries dropped, so an optional stage can be omitted by passing null. */
    private static List<GenStage> compose(GenStage... stages) {
        List<GenStage> out = new ArrayList<>(stages.length);
        for (GenStage stage : stages) {
            if (stage != null) out.add(stage);
        }
        return out;
    }

    /** Swap in a compound-aware filler. Idempotent — last write wins. */
    public void registerCompound(CompoundFiller filler) {
        compoundFillers.put(filler.kind(), filler);
    }

    /** Swap in a per-kind filler. Idempotent — last write wins. */
    public void register(BlockFiller filler) {
        fillers.put(filler.kind(), filler);
    }

    /**
     * An explicit composition choice: replaces {@link TrunkSkeletonStage}'s
     * fixed crossroad with a {@link GrownTrunkSkeletonStage} grown from
     * {@code profile} wherever Step 1a runs in either the conquest or the
     * legacy recipe, rebuilding both. Everything else in both recipes stays
     * byte-identical. Passing {@code null} restores the stock stage.
     *
     * <p>Stock behavior is unchanged unless a caller opts in — no default
     * path selects grown roads on its own.
     *
     * @return this, for chaining
     */
    public BspCityGenerator useGrownRoads(GrownTrunkPlan.Profile profile) {
        this.grownOverride = profile;
        return this;
    }

    /**
     * The legacy recipe with the grown road skeleton in place of the fixed
     * crossroad, plus the stages that only a grown settlement needs.
     *
     * <p>Built per call rather than cached, because its shape depends on the
     * settlement: density and lifeline come from the world being fought over,
     * and one shared pre-built recipe cannot answer for two different markets.
     * A recipe is an immutable stage list, so this costs a couple of dozen
     * allocations once per battle.
     *
     * <p><b>There is no grown conquest recipe.</b> Conquest keeps the fixed
     * crossroad until the grown maps have been shown to play as well, so that
     * the mission the campaign is built around does not change underneath a
     * quality judgement that has not been made yet.
     */
    /**
     * The conquest sequence on a grown skeleton — biome bands, fortress ward
     * and all — for judging the new ground against the old before anything is
     * moved onto it. Reached only through an explicit {@link #useGrownRoads},
     * never from a campaign profile.
     */
    private GenRecipe grownConquestRecipe(GrownTrunkPlan.Profile profile) {
        return buildConquestRecipe(new GrownTrunkSkeletonStage(profile),
                new HinterlandFillStage(), landingLinkStageFor(profile));
    }

    /**
     * Omitted rather than run as a no-op: recipe membership is how this
     * pipeline forks, and a present-but-inert stage is what that avoids.
     */
    private GenStage landingLinkStageFor(GrownTrunkPlan.Profile profile) {
        return (profile.link == SettlementLink.LANDING)
                ? new SettlementLandingLinkStage()
                : null;
    }

    /**
     * A map made of several places rather than one settlement. Reached only
     * by passing a plan to
     * {@link #generate(int, int, long, TraversalAxis, TargetProfile, PrecinctPlan)}.
     */
    private GenRecipe precinctRecipe(PrecinctPlan plan) {
        return buildLegacyRecipe(new PrecinctSkeletonStage(plan),
                new HinterlandFillStage(), null, new PrecinctWardStage(),
                new PrecinctDefenceStage(), new PrecinctLandingAreaStage(),
                new FrontDepthStage());
    }

    private GenRecipe grownLegacyRecipe(GrownTrunkPlan.Profile profile) {
        return buildLegacyRecipe(new GrownTrunkSkeletonStage(profile),
                new HinterlandFillStage(), landingLinkStageFor(profile));
    }

    /**
     * Which recipe this battle gets.
     *
     * <p><b>A stated plan wins outright.</b> A caller that named the places on
     * the map has answered the question the rest of this method exists to
     * guess at.
     *
     * <p><b>Conquest is pinned to the stock crossroad</b> unless something asks
     * for otherwise in as many words. It is the mission the campaign is built
     * around and its balance was measured against the maps it has, so the
     * ground under it does not move on the strength of a market size or any
     * other thing a battle carries incidentally. An explicit
     * {@link #useGrownRoads} is the one way through, because the comparison has
     * to be possible before the judgement can be made: this is what renders a
     * grown conquest map beside the shipped one at the same seed. Nothing in
     * ordinary play calls it.
     *
     * <p>Every other battle grows its settlement from what the campaign says
     * about the market: {@link SettlementZoning} derives the density and the
     * lifeline, {@link TargetProfile} carries them, and the road skeleton,
     * hinterland and off-map link follow.
     *
     * <p><b>A profile with no market behind it takes the stock recipe</b>, and
     * that is a rule rather than a carve-out for tests: density is derived from
     * market size, so a battle with nothing behind it has nothing to derive it
     * from, and the fixed crossroad is the honest answer to an absent question.
     *
     * <p>An explicit {@link #useGrownRoads} override still wins over both. That
     * is the tooling and comparison path — it is how a render ladder asks for a
     * density the campaign would never choose.
     */
    private GenRecipe recipeFor(TraversalAxis axis, TargetProfile profile, PrecinctPlan precincts) {
        if (precincts != null) return precinctRecipe(precincts);
        if (axis != null) {
            return grownOverride == null ? conquestRecipe : grownConquestRecipe(grownOverride);
        }
        if (grownOverride != null) return grownLegacyRecipe(grownOverride);
        if (profile == null || profile.marketSize() <= 0) return legacyRecipe;
        return grownLegacyRecipe(GrownTrunkPlan.Profile.of(
                SettlementZoning.densityFor(profile.marketSize()), profile.link()));
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
     * Campaign-aware generation with no places stated. The {@code profile}
     * (campaign → battle bridge) is bound on the context under
     * {@link BspKeys#MARKET_PROFILE} for stages that scale off the target world
     * (e.g. {@link OverwatchTowerStage}'s defense intensity);
     * {@link TargetProfile#NEUTRAL} reproduces the pre-bridge output.
     */
    @Override
    public MapResult generate(int width, int height, long seed, TraversalAxis axis, TargetProfile profile) {
        return generate(width, height, seed, axis, profile, null);
    }

    /**
     * Canonical entry point — every other overload funnels here.
     *
     * <p>{@code precincts} is a per-call argument rather than generator state
     * because one instance serves every battle in a session: a plan remembered
     * on the generator would build the next battle's map out of the last
     * battle's places.
     *
     * <p><b>A plan with an axis is refused.</b> Conquest is pinned to the stock
     * crossroad by {@code precincts.md} until the grown maps are judged, and a
     * rule that important should not be reachable by a caller that passed both
     * without meaning to.
     */
    @Override
    public MapResult generate(int width, int height, long seed, TraversalAxis axis,
                              TargetProfile profile, PrecinctPlan precincts) {
        if (precincts != null && axis != null) {
            throw new IllegalArgumentException(
                    "conquest keeps the stock crossroad: a traversal axis and a precinct "
                            + "plan are two different maps and cannot both be asked for");
        }
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
        GenRecipe recipe = recipeFor(axis, profile, precincts);
        recipe.run(ctx);
        if (axis != null) requireExactlyOneCentralKeep(ctx);
        if (precincts != null && precincts.objective() != null) {
            requireNoCompetingKeep(ctx);
        }

        return assembleResult(ctx);
    }

    /**
     * A stock-recipe Conquest map must have exactly one canonical central keep,
     * because that command post is the required territorial climax and the
     * recipe seeds a citadel unconditionally. Zero omits the climax; more than
     * one creates competing command posts and
     * {@code ConquestCommand.canonicalKeep} returns null, which disables the
     * keep phase silently. The objective also fails closed, but generation
     * should reject the malformed match first.
     */
    private static void requireExactlyOneCentralKeep(GenContext ctx) {
        int keeps = 0;
        for (TacticalNode node : ctx.tactical) {
            if (node.kind == TacticalNode.Kind.COMMAND_POST) keeps++;
        }
        if (keeps != 1) {
            throw new IllegalStateException("Conquest map seed " + ctx.seed + " at "
                    + ctx.width + "x" + ctx.height + " generated " + keeps
                    + " COMMAND_POST nodes; expected exactly one central keep");
        }
    }

    /**
     * A map grown from places may hold one keep or none, never two.
     *
     * <p>The same law as {@link #requireExactlyOneCentralKeep} split at the one
     * point where the two recipes genuinely differ. <b>Two is malformed
     * either way</b>: {@code ConquestCommand.canonicalKeep} returns null for
     * competing command posts and the keep phase then disables itself in
     * silence, which is what the settlement's own military base used to cause
     * before {@code MilitaryBaseFiller} stood it down to a supply hub.
     *
     * <p><b>Zero is not, here.</b> A precinct is packed from a program into the
     * ground it won, and {@code precincts.md} records that granted ground is
     * not usable ground — a garrison short of room builds what fits and reports
     * the rest under {@code BspKeys.UNPLACED_PROGRAM}. For Assault and Raid
     * that is simply a smaller installation; nothing about those missions needs
     * a keep. For Conquest it is a map the mission cannot be played on, and
     * {@code MissionMapRequirements} already answers it the right way — with
     * another seed rather than an exception. Throwing here would take that
     * re-roll away and turn a recoverable shortfall into a failed battle.
     */
    private static void requireNoCompetingKeep(GenContext ctx) {
        int keeps = 0;
        for (TacticalNode node : ctx.tactical) {
            if (node.kind == TacticalNode.Kind.COMMAND_POST) keeps++;
        }
        if (keeps > 1) {
            throw new IllegalStateException("Precinct map seed " + ctx.seed + " at "
                    + ctx.width + "x" + ctx.height + " generated " + keeps
                    + " COMMAND_POST nodes; a map has one place to take or none");
        }
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
     * Assemble the {@link MapResult} from a finished context, then surface the
     * preview accessors. Overlays a given recipe didn't produce read back null
     * and degrade gracefully (empty compound list, {@link RoadGraph#EMPTY}, null
     * biome/district/tactical-region maps) — so this is shared verbatim across
     * the urban and station recipes.
     *
     * <p><b>The result is composed from locals, never read back off the
     * {@code last*} fields.</b> One generator instance serves every battle in
     * the process, so a version of this that stored the tactical map on
     * {@code this} and then handed {@code this.lastTacticalMap} to the
     * {@link MapResult} gave a battle whichever map a concurrently generating
     * battle had just published. That is not a subtle drift: a Conquest fixture
     * came out with another fixture's compounds, its garrison allocated against
     * them, and {@code simDeterminism -Pparallelism=2} caught it as two
     * replicas of one fixture disagreeing on their first referee line.
     *
     * <p>The {@code last*} fields themselves stay: they are a preview seam for
     * the map-render tests, each of which generates on one thread through its
     * own generator. They say what this instance generated most recently and
     * nothing more, and no {@link MapResult} depends on them.
     */
    private MapResult assembleResult(GenContext ctx) {
        List<Compound> compounds = ctx.get(BspKeys.COMPOUNDS);
        TacticalMap tacticalMap = ctx.get(BspKeys.TACTICAL_MAP);
        RoadGraph roadGraph = ctx.get(BspKeys.ROAD_GRAPH);
        BiomeMap biomeMap = ctx.get(BspKeys.BIOME_MAP);

        Buildings buildings = ctx.get(BspKeys.BUILDINGS);
        int[] marine = ctx.get(BspKeys.MARINE_SPAWN);
        int[] defender = ctx.get(BspKeys.DEFENDER_SPAWN);

        // Berths and fixture work come out of the shared room fittings, which a
        // city map now uses too: a fortress vehicle shed publishes the same
        // machine berths a deck's bay does, and dropping them here would leave
        // the sheds furnished and empty.
        MapResult result = new MapResult(ctx.grid, ctx.topology,
                marine[0], marine[1], defender[0], defender[1],
                ctx.pois, ctx.doodads, tacticalMap, buildings,
                ctx.defensePosts,
                roadGraph != null ? roadGraph : RoadGraph.EMPTY,
                ctx.landingPads, ctx.landingAreas,
                biomeMap, ctx.gantries, ctx.fixtureTasks,
                ctx.runways, ctx.shelters, ctx.get(BspKeys.VEHICLE_CORRIDOR),
                ctx.get(BspKeys.FRONT_DEPTH));

        this.lastBiomeMap = biomeMap;
        this.lastDistrictMap = ctx.get(BspKeys.DISTRICT_MAP);
        this.lastCompounds = compounds != null ? compounds : new ArrayList<>();
        this.lastTacticalMap = tacticalMap;
        this.lastRoadGraph = roadGraph != null ? roadGraph : RoadGraph.EMPTY;
        this.lastTacticalRegions = ctx.get(BspKeys.TACTICAL_REGIONS);
        this.lastStationGraph = ctx.get(BspKeys.STATION_GRAPH);
        return result;
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
