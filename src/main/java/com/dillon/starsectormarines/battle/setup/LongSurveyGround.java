package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.derelict.ClaimLattice;
import com.dillon.starsectormarines.battle.derelict.FallenHullSite;
import com.dillon.starsectormarines.battle.derelict.SurveyStake;
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
import com.dillon.starsectormarines.battle.world.gen.hull.HullSite.HaulRoad;
import com.dillon.starsectormarines.battle.world.gen.hull.HullSite.LatticePoint;
import com.dillon.starsectormarines.battle.world.gen.hull.HullSite.Stake;
import com.dillon.starsectormarines.battle.world.gen.overgrown.HeartCountry;
import com.dillon.starsectormarines.battle.world.gen.pool.StoryMap;
import com.dillon.starsectormarines.battle.world.gen.pool.StoryMapPool;
import com.dillon.starsectormarines.battle.world.gen.pool.StoryMapPools;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.Apron;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.Area;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.Cell;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.model.MapScale;
import com.dillon.starsectormarines.battle.world.gen.overgrown.HeartCountry.Form;
import com.dillon.starsectormarines.battle.world.gen.overgrown.HeartCountry.Density;
import com.dillon.starsectormarines.ops.MissionType;
import com.dillon.starsectormarines.ops.OperationTier;
import com.dillon.starsectormarines.ops.RiskLevel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
 *   <li>The Line: the district capital, Broad Front's city ({@link HeartCountry#planFor}), the hull down across its north edge.</li>
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
        return ground(chapter, BloomHeartSetup.world(world), entry);
    }

    /**
     * {@code entry}'s ground for the world {@code worldProfile} states, its surface and its air: a world whose towns
     * are sealed ({@link PlanetSkin#sealsTowns}) walls every town in the ground with locks.
     */
    public static Ground ground(StoryMap chapter, TargetProfile worldProfile, StoryMapPool.Entry entry) {
        if (!chapter.longSurvey()) throw new IllegalArgumentException(chapter.id + " is not a Long Survey chapter");
        SurfacePalette world = worldProfile.surface();
        SurfacePalette home = StoryMapPools.pool(chapter).layoutFor(world);
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
                        RiskLevel.HIGH, market, MapScale.COLONY, seed, PrecinctPlan.Sprawl.REMOTE)
                        // The installation laid as a cantonment in a square wall, not a grown diamond-walled fort.
                        .withCantonments(market, MapScale.COLONY.width, MapScale.COLONY.height);
                plan = PlanetSkin.sealedFor(entry.patch().pinned(plan), worldProfile);
                MapResult map = MAP_GEN.generate(MapScale.COLONY.width, MapScale.COLONY.height, seed, null, market, plan);
                yield new Ground(chapter, PlanetSkin.apply(map, home, worldProfile), plan, seed);
            }
            case LONG_SURVEY_LINE -> {
                // The district capital: Broad Front's city, the columns stepping off from the south edge, the hull down
                // across the north edge in the heart's place.
                Form form = Form.BROAD_FRONT;
                TargetProfile profile = BloomHeartSetup.world(home)
                        .withBasin(Basin.ravBloom(home, BloomHeartSetup.BASIN_REACH_X, BloomHeartSetup.BASIN_REACH_Y));
                PrecinctPlan plan = entry.patch().pinned(HeartCountry.planFor(form, Density.CITY, seed));
                plan = PlanetSkin.sealedFor(plan, worldProfile);
                MapResult map = MAP_GEN.generate(form.scale.width, form.scale.height, seed, null, profile, plan);
                yield new Ground(chapter, PlanetSkin.apply(map, home, worldProfile), plan, seed);
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
     * How far apart two lattice points link for the rules: a lattice step, plus the half step two neighbours may have
     * moved apart finding their stake ground ({@link com.dillon.starsectormarines.battle.world.gen.hull.ClaimLattice}).
     * A stake's zone is then half of it ({@code FallenHull}), so neighbouring stakes' perimeters meet.
     */
    static final int LINK_CELLS = HullSite.LATTICE_SPACING * 5 / 4;

    /**
     * The ground's hull site as the hull's rules read it ({@link FallenHullSite}): the hull and its trench as the root
     * zone's box, each section (stern to bow) as plates its zone reaches off, the forge, the bay's apron as the mouth, the lattice and its roads (each from the hull outward),
     * the starting stakes and each way in's apron berth as egress. What stands on the claim and the hull's store are the chapter's.
     */
    public static FallenHullSite rulesSite(HullSite site, List<FallenHullSite.Pile> salvage,
                                           List<FallenHullSite.Posting> walkers, float feedstock) {
        Area f = site.footprint(), t = site.trench();
        FallenHullSite.Rect hull = new FallenHullSite.Rect(Math.min(f.left(), t.left()), Math.min(f.bottom(), t.bottom()),
                Math.max(f.right(), t.right()), Math.max(f.top(), t.top()));
        Cell mouth = mouth(site);
        // The root zone's reach off the nearest section's plates.
        int perimeter = LINK_CELLS / 2;
        List<FallenHullSite.Rect> sections = new ArrayList<>();
        for (Area a : site.sections()) sections.add(new FallenHullSite.Rect(a.left(), a.bottom(), a.right(), a.top()));
        List<ClaimLattice.Point> lattice = new ArrayList<>();
        for (LatticePoint p : site.lattice()) lattice.add(new ClaimLattice.Point(p.index(), p.cell().x(), p.cell().y()));
        List<int[]> roads = new ArrayList<>();
        for (HaulRoad road : site.roads()) {
            List<Cell> cells = road.cells();
            if (cells.size() < 2) continue;
            int[] line = new int[cells.size() * 2];
            for (int i = 0; i < cells.size(); i++) {
                Cell c = cells.get(cells.size() - 1 - i);
                line[i * 2] = c.x();
                line[i * 2 + 1] = c.y();
            }
            roads.add(line);
        }
        List<FallenHullSite.StartingStake> stakes = new ArrayList<>();
        for (Stake stake : site.stakes()) {
            stakes.add(new FallenHullSite.StartingStake(stake.point(), SurveyStake.Stage.valueOf(stake.stage().name())));
        }
        List<int[]> egress = new ArrayList<>();
        // Each way in's apron, at its first berth: open ground a heavy walker can reach and stand on, where a door's
        // threshold may not be.
        for (Apron apron : site.interior().aprons()) {
            egress.add(new int[] {apron.lift().berth(0).centerX, apron.lift().berth(0).centerY});
        }
        return new FallenHullSite(hull, site.forge().x(), site.forge().y(), mouth.x(), mouth.y(), perimeter, lattice,
                LINK_CELLS, roads, stakes, salvage, walkers, egress, feedstock, sections);
    }

    /** The bay's mouth: the first berth of the apron nearest the launch bay, open ground outside the hull. */
    static Cell mouth(HullSite site) {
        Area bay = site.bay();
        Apron best = null;
        long bestD = Long.MAX_VALUE;
        for (Apron apron : site.interior().aprons()) {
            long dx = apron.ground().centreX() - bay.centreX(), dy = apron.ground().centreY() - bay.centreY();
            if (dx * dx + dy * dy < bestD) {
                bestD = dx * dx + dy * dy;
                best = apron;
            }
        }
        return new Cell(best.lift().berth(0).centerX, best.lift().berth(0).centerY);
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
