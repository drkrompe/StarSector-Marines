package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.cordon.CordonPlan;
import com.dillon.starsectormarines.battle.cordon.CordonVariant;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.Basin;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.PlanetSkin;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspCityGenerator;
import com.dillon.starsectormarines.battle.world.gen.hull.HullSite;
import com.dillon.starsectormarines.battle.world.gen.overgrown.HeartCountry;
import com.dillon.starsectormarines.battle.world.gen.pool.StoryMap;
import com.dillon.starsectormarines.battle.world.gen.pool.StoryMapPool;
import com.dillon.starsectormarines.battle.world.gen.pool.StoryMapPools;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.model.MapScale;
import com.dillon.starsectormarines.battle.world.gen.overgrown.HeartCountry.Form;
import com.dillon.starsectormarines.battle.world.gen.overgrown.HeartCountry.Density;
import com.dillon.starsectormarines.ops.MissionType;
import com.dillon.starsectormarines.ops.OperationTier;
import com.dillon.starsectormarines.ops.RiskLevel;

import java.util.Collections;
import java.util.Set;

/**
 * The Long Survey's ground ({@code long-survey-arc.md}, Seeds): each chapter's owned map, the ground of the mission it
 * is fought as with the chapter's fallen hull laid over it by the pool entry's patch ({@code fallen-hull-nouns.md}).
 * The chapter's own setup (its family's adapter) stands its battle on this ground; this class only builds it, the way
 * its base mission's setup generates it, so the map is the base's own seed with a hull on it.
 *
 * <ul>
 *   <li>The Fall: the Colony Breach's colony ({@code colony-breach}'s generation, basin and all).</li>
 *   <li>Stakes: mine country of its own, a Sabotage plan in remote sprawl at the colony's size.</li>
 *   <li>The Line: the Cordon's line ({@link CordonSetup#map}, redoubts and base built).</li>
 *   <li>The Surveyor: Broad Front's country ({@code heart-front}'s plan and basin), its airbases placed.</li>
 * </ul>
 */
public final class LongSurveyGround {

    private static final BspCityGenerator MAP_GEN = new BspCityGenerator();

    /**
     * The market a Stakes country is derived from: a mining district of a settled world, so the plan grows an
     * installation (the mine works) and the country round it. The campaign's world only skins it.
     */
    static TargetProfile stakesMarket(SurfacePalette surface) {
        return new TargetProfile(5, 5, 2, 1, "", Set.of(EconomicFunction.MINING), surface, SettlementLink.ROAD);
    }

    private LongSurveyGround() { }

    /** A chapter's ground: the finished map, carrying its {@link MapResult#hullSite}, and the plan it grew from. */
    public record Ground(StoryMap chapter, MapResult map, PrecinctPlan plan, long battleSeed) {
        public HullSite hull() { return map.hullSite; }
    }

    /** The chapter's owned ground for a world of {@code world}. */
    public static Ground ground(StoryMap chapter, SurfacePalette world) {
        return ground(chapter, world, StoryMapPools.pool(chapter).owned());
    }

    /**
     * {@code entry}'s ground for a world of {@code world}: laid out on the chapter's home palette and skinned for the
     * world, as every owned map is ({@code story-map-pools.md}).
     */
    public static Ground ground(StoryMap chapter, SurfacePalette world, StoryMapPool.Entry entry) {
        if (!chapter.longSurvey()) throw new IllegalArgumentException(chapter.id + " is not a Long Survey chapter");
        SurfacePalette home = StoryMapPools.pool(chapter).layoutFor(world);
        TargetProfile worldProfile = BloomHeartSetup.world(world);
        long seed = entry.seed();
        return switch (chapter) {
            case LONG_SURVEY_FALL -> {
                TargetProfile profile = BloomHeartSetup.world(home)
                        .withBasin(Basin.ravBloom(home, BattleSetup.COLONY_BASIN_X, BattleSetup.COLONY_BASIN_Y));
                PrecinctPlan plan = entry.patch().pinned(BattleSetup.colonyPlanOn(MapScale.COLONY, seed, worldProfile));
                MapResult map = MAP_GEN.generate(MapScale.COLONY.width, MapScale.COLONY.height, seed, null, profile, plan);
                yield new Ground(chapter, PlanetSkin.apply(map, home, worldProfile), plan, seed);
            }
            case LONG_SURVEY_STAKES -> {
                TargetProfile market = stakesMarket(home);
                PrecinctPlan plan = BattleSetup.precinctPlanFor(MissionType.SABOTAGE, OperationTier.REINFORCED,
                        RiskLevel.HIGH, market, MapScale.COLONY, seed, PrecinctPlan.Sprawl.REMOTE);
                plan = PlanetSkin.sealedFor(entry.patch().pinned(plan), worldProfile);
                MapResult map = MAP_GEN.generate(MapScale.COLONY.width, MapScale.COLONY.height, seed, null, market, plan);
                yield new Ground(chapter, PlanetSkin.apply(map, home, worldProfile), plan, seed);
            }
            case LONG_SURVEY_LINE -> {
                CordonSetup.CordonMap cordon = CordonSetup.map(CordonVariant.LINE, CordonPlan.Density.RURAL, world, entry);
                yield new Ground(chapter, cordon.map(), cordon.plan(), cordon.battleSeed());
            }
            case LONG_SURVEY_SURVEYOR -> {
                Form form = Form.BROAD_FRONT;
                TargetProfile profile = BloomHeartSetup.world(home)
                        .withBasin(Basin.ravBloom(home, BloomHeartSetup.BASIN_REACH_X, BloomHeartSetup.BASIN_REACH_Y));
                PrecinctPlan plan = entry.patch().pinned(HeartCountry.planFor(form, Density.RURAL, seed));
                plan = PlanetSkin.sealedFor(plan, worldProfile);
                MapResult map = MAP_GEN.generate(form.scale.width, form.scale.height, seed, null, profile, plan);
                yield new Ground(chapter, PlanetSkin.apply(map, home, worldProfile), plan, seed);
            }
            default -> throw new IllegalArgumentException(chapter.id);
        };
    }

    /**
     * The ground stood up as a battle with nobody on it: for the ground's checks and its look shots, never to play.
     * A chapter's own setup stands its battle on {@link #ground} instead.
     */
    public static BattleSimulation stand(Ground ground, SurfacePalette world) {
        BattleSimulation sim = BattleSetup.buildMap(ground.map(), Collections.emptyList(), Collections.emptyList(),
                ground.battleSeed()).sim();
        sim.setSurface(world);
        return sim;
    }
}
