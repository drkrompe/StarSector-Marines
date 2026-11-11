package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.ops.MissionType;
import com.dillon.starsectormarines.ops.OperationTier;
import com.dillon.starsectormarines.ops.RiskLevel;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspCityGenerator;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.model.MapScale;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

/**
 * The walled places a derived plan lays ({@code precincts.md}, A claim must not come out geometric), one map per
 * mission family that derives one: a Sabotage on a settled world, an Assault on a small map, a Raid on a world with
 * no site template, and a Conquest with its lanes. Through the look preset, roofs on: the whole map
 * ({@code <family>/1-whole.png}) and each walled place close ({@code <family>/2-<place>.png}). Ground only: nothing
 * is played. Opt-in; {@code -Dsnapshot.garrisonCompound.families=sabotage,conquest} names families, and
 * {@code -Dsnapshot.garrisonCompound.cantonments=true} lays each plan's garrisons as a Raid site's cantonment rows
 * ({@link PrecinctPlan#withCantonments}) instead, written under {@code cantonment/<family>/}.
 */
public final class GarrisonCompoundLookSuite implements SnapshotSuite {

    private static final int W = 1920, H = 1080;
    private static final long SEED = 141_418L;
    /** How many cells across a close shot is, at least. */
    private static final float CLOSE = 110f;

    private static final TargetProfile FIXTURE = new TargetProfile(6, 5, 4, 2, "independent",
            EnumSet.of(EconomicFunction.SPACEPORT, EconomicFunction.HEAVY_INDUSTRY, EconomicFunction.COMMERCE),
            SurfacePalette.ROCK, SettlementLink.ROAD);
    private static final TargetProfile FARMING = new TargetProfile(5, 6, 2, 1, "independent",
            EnumSet.of(EconomicFunction.AGRICULTURE, EconomicFunction.COMMERCE, EconomicFunction.HABITATION),
            SurfacePalette.VERDANT, SettlementLink.ROAD);
    private static final TargetProfile MILITARY = new TargetProfile(6, 5, 6, 3, "independent",
            EnumSet.of(EconomicFunction.MILITARY, EconomicFunction.HEAVY_INDUSTRY, EconomicFunction.SPACEPORT),
            SurfacePalette.ARID, SettlementLink.ROAD);

    private record Map(MapResult result, PrecinctPlan plan) { }

    @Override public String id() { return "garrison-compound-look"; }

    @Override public String label() { return "Derived plans' walled places, one map per mission family"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        TileRegistryTestInstaller.install(context.projectRoot());
        String asked = System.getProperty("snapshot.garrisonCompound.families", "").trim();
        List<String> families = asked.isEmpty() ? List.of("sabotage", "assault", "raid", "conquest")
                : List.of(asked.split(","));
        List<SnapshotArtifact> out = new ArrayList<>();
        for (String family : families) {
            Map map = map(family.trim());
            out.addAll(shots(context, (CANTONMENTS ? "cantonment/" : "") + family.trim(), map));
        }
        return out;
    }

    private static final boolean CANTONMENTS = Boolean.getBoolean("snapshot.garrisonCompound.cantonments");

    private static PrecinctPlan laid(PrecinctPlan plan, TargetProfile profile, MapScale scale) {
        return CANTONMENTS && plan != null ? plan.withCantonments(profile, scale.width, scale.height) : plan;
    }

    private static Map map(String family) {
        RiskLevel risk = RiskLevel.MEDIUM;
        switch (family) {
            case "sabotage": {
                OperationTier tier = OperationTier.ESTABLISHED;
                MapScale scale = MapScale.forTier(tier);
                PrecinctPlan plan = laid(BattleSetup.precinctPlanFor(MissionType.SABOTAGE, tier, risk, FIXTURE, scale,
                        SEED, PrecinctPlan.Sprawl.BALANCED), FIXTURE, scale);
                return new Map(new BspCityGenerator().generate(scale.width, scale.height, SEED, null, FIXTURE, plan),
                        plan);
            }
            case "assault": {
                OperationTier tier = OperationTier.FIRST_CONTRACT;
                MapScale scale = MapScale.forTier(tier);
                PrecinctPlan plan = laid(BattleSetup.precinctPlanFor(MissionType.ASSAULT, tier, risk, FIXTURE, scale,
                        SEED, null), FIXTURE, scale);
                return new Map(BattleSetup.proceduralOperationMap(MissionType.ASSAULT, tier, risk, FIXTURE, scale,
                        SEED, null, plan), plan);
            }
            case "raid": {
                OperationTier tier = OperationTier.ESTABLISHED;
                MapScale scale = MapScale.forRaid(tier);
                PrecinctPlan plan = laid(BattleSetup.precinctPlanFor(MissionType.RAID, tier, risk, FARMING, scale,
                        SEED, null), FARMING, scale);
                return new Map(BattleSetup.proceduralOperationMap(MissionType.RAID, tier, risk, FARMING, scale,
                        SEED, null, plan), plan);
            }
            case "conquest": {
                OperationTier tier = OperationTier.REINFORCED;
                MapScale scale = MapScale.CONQUEST;
                PrecinctPlan plan = laid(BattleSetup.conquestPlanFor(tier, risk, MILITARY, null,
                        TraversalAxis.SOUTH_TO_NORTH, SEED), MILITARY, scale);
                return new Map(new BspCityGenerator().generate(scale.width, scale.height, SEED, null, MILITARY, plan),
                        plan);
            }
            default:
                throw new IllegalArgumentException("no family " + family);
        }
    }

    private static List<SnapshotArtifact> shots(SnapshotContext context, String family, Map map) throws Exception {
        List<SnapshotArtifact> out = new ArrayList<>();
        MapResult result = map.result();
        try (BattleSimulation sim = BattleSetup.buildMap(result, List.of(), result.defensePosts, List.of(), SEED)
                .sim()) {
            int w = sim.getGrid().getWidth(), h = sim.getGrid().getHeight();
            BattleReviewFrameRenderer look = BattleReviewFrameRenderer.look(context.modRoot(), W, H, w / 2f, h / 2f,
                    Math.max(w, h * 16f / 9f) + 4f);
            out.add(new SnapshotArtifact(family + "/1-whole.png", look.render(sim, "")));
            if (map.plan() != null) {
                for (Precinct place : map.plan().precincts()) {
                    if (place.boundary() != Precinct.Boundary.WALLED || !place.isProgrammed()) continue;
                    float across = Math.min(Math.max(w, h * 16f / 9f), CLOSE);
                    look.refocus(place.seedX(), place.seedY(), across);
                    out.add(new SnapshotArtifact(family + "/2-" + place.name() + ".png", look.render(sim, "")));
                    System.out.printf(Locale.ROOT, "[garrison-compound-look] %s %s: seed %d,%d, square %s, program %s%n",
                            family, place.name(), place.seedX(), place.seedY(),
                            place.fortification() != null && place.fortification().square(),
                            place.program().cantonment() ? "cantonment" : "packed");
                }
            }
            System.out.printf(Locale.ROOT, "[garrison-compound-look] %s: %dx%d, %d points of interest, %d posts%n",
                    family, w, h, result.pointsOfInterest.size(), result.defensePosts.size());
        }
        return out;
    }
}
