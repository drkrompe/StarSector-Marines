package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.command.RescueCommandFacts.KnownBore;
import com.dillon.starsectormarines.battle.command.SquadCommandClaim;
import com.dillon.starsectormarines.battle.command.objective.EliminateFactionObjective;
import com.dillon.starsectormarines.battle.command.objective.HullClaimObjective;
import com.dillon.starsectormarines.battle.derelict.BuildQueue;
import com.dillon.starsectormarines.battle.derelict.DerelictWalkerFrame;
import com.dillon.starsectormarines.battle.derelict.FallenHull;
import com.dillon.starsectormarines.battle.derelict.FallenHullSite;
import com.dillon.starsectormarines.battle.derelict.SurveyStake;
import com.dillon.starsectormarines.battle.drone.FlockLivery;
import com.dillon.starsectormarines.battle.evacuation.HeartLanes;
import com.dillon.starsectormarines.battle.power.MechDropService;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.hull.HullSite;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.Area;
import com.dillon.starsectormarines.battle.world.gen.overgrown.HeartCountry.Form;
import com.dillon.starsectormarines.battle.world.gen.pool.StoryMap;
import com.dillon.starsectormarines.battle.world.gen.pool.StoryMapPool;
import com.dillon.starsectormarines.battle.world.gen.pool.StoryMapPools;
import com.dillon.starsectormarines.i18n.Strings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.function.IntFunction;

/**
 * The Long Survey's chapters 2 to 4 as battles ({@code long-survey-arc.md}, Chapters): each chapter's owned ground
 * ({@link LongSurveyGround}) with the company landed on it, the fallen hull's watch over it ({@link FallenHull}) and
 * the chapter's objective against the claim ({@link HullClaimObjective}). The player commands the company; no
 * autonomous commander is installed for it. Lost as every chapter is, the company eliminated.
 *
 * <ul>
 *   <li>Stakes: the Sabotage family's three named sites are the three stakes the ground planted; kill them.</li>
 *   <li>The Line: Broad Front's allied lanes push through the port city, each lane's objective its next stake, won
 *   when every stake on the map is down.</li>
 *   <li>The Surveyor: the same lanes' short push on the full claim, then the delve into the hull, won when the
 *   autoforge is out in its forge hall, guarded by the vault frame and a hall frame.</li>
 * </ul>
 */
public final class LongSurveyBattle {

    /** Who claims the company's squads as they land: the player's company, on the survey's contract. */
    static final String ISSUER = "long-survey";

    private LongSurveyBattle() { }

    /** {@code map}'s chapter on its owned {@code seed}, fought on the world {@code profile} states. */
    public static BattleSimulation create(StoryMap map, long seed, List<ShuttleAssignment> manifest,
                                          TargetProfile profile, FlockLivery livery) {
        return switch (map) {
            case LONG_SURVEY_STAKES -> stakes(seed, manifest, profile, livery);
            case LONG_SURVEY_LINE -> line(seed, manifest, profile, livery);
            case LONG_SURVEY_SURVEYOR -> surveyor(seed, manifest, profile, livery);
            default -> throw new IllegalArgumentException(map.id + " is not a built Long Survey battle");
        };
    }

    // ---- Stakes ----

    /**
     * Stakes' forge program (`long-survey-arc.md`, Stakes: trains carrying stakes out across the mine country): the
     * survey program, stakes escorted by rounds walkers between its workers. UNTUNED.
     */
    static final BuildQueue STAKES_QUEUE = BuildQueue.survey();
    /** The hull's store at the start: a stake and a line frame's worth. UNTUNED. */
    static final float STAKES_FEEDSTOCK = BuildQueue.STAKE_COST + BuildQueue.LINE_COST;
    /** The watch already out at the mouth: a line frame, a rounds walker and a stripper. UNTUNED. */
    static final List<DerelictWalkerFrame> STAKES_WALKERS = List.of(DerelictWalkerFrame.LINE,
            DerelictWalkerFrame.ROUNDS, DerelictWalkerFrame.STRIPPER);

    /** Stakes: the company down in the mine country, the three planted stakes its sites. */
    static BattleSimulation stakes(long seed, List<ShuttleAssignment> manifest, TargetProfile profile,
                                   FlockLivery livery) {
        Stood stood = stand(StoryMap.LONG_SURVEY_STAKES, seed, manifest, profile, "Company lands in the mine country");
        FallenHullSite site = LongSurveyGround.rulesSite(stood.ground().hull(), List.of(),
                atMouth(stood.ground(), STAKES_WALKERS), STAKES_FEEDSTOCK);
        FallenHull hull = FallenHull.install(stood.sim(), site, STAKES_QUEUE, livery);
        List<Integer> planted = planted(site);
        stood.sim().addObjective(HullClaimObjective.named(hull, planted, Strings.get("longSurveyObjectiveStakes")));
        return stood.sim();
    }

    /** The stakes the ground stood planted, by lattice point: Stakes' named sites. */
    static List<Integer> planted(FallenHullSite site) {
        List<Integer> out = new ArrayList<>();
        for (FallenHullSite.StartingStake s : site.stakes()) if (s.stage() == SurveyStake.Stage.PLANTED) out.add(s.pointId());
        if (out.isEmpty()) throw new IllegalStateException("the ground stood no planted stake");
        return out;
    }

    // ---- The Line ----

    /**
     * The Line's forge program (`long-survey-arc.md`, The Line: haul roads live, trains running): haulers on the
     * roads, stakes escorted by line frames, more line frames for the city. UNTUNED.
     */
    static final BuildQueue LINE_QUEUE = new BuildQueue(List.of(
            BuildQueue.Item.walker(DerelictWalkerFrame.HAULER, BuildQueue.WORKER_COST),
            BuildQueue.Item.stake(BuildQueue.STAKE_COST, DerelictWalkerFrame.LINE),
            BuildQueue.Item.walker(DerelictWalkerFrame.LINE, BuildQueue.LINE_COST),
            BuildQueue.Item.walker(DerelictWalkerFrame.ROUNDS, BuildQueue.WORKER_COST),
            BuildQueue.Item.stake(BuildQueue.STAKE_COST, DerelictWalkerFrame.ROUNDS),
            BuildQueue.Item.walker(DerelictWalkerFrame.LINE, BuildQueue.LINE_COST)));
    /** The hull's store at the start: a train's worth. UNTUNED. */
    static final float LINE_FEEDSTOCK = BuildQueue.STAKE_COST + BuildQueue.WORKER_COST;
    /** The watch already out at the mouth: line frames, a rounds walker and a hauler. UNTUNED. */
    static final List<DerelictWalkerFrame> LINE_WALKERS = List.of(DerelictWalkerFrame.LINE, DerelictWalkerFrame.LINE,
            DerelictWalkerFrame.ROUNDS, DerelictWalkerFrame.HAULER);

    /**
     * The Line: the company down on the port city's avenue, Broad Front's allied lanes stepping off from the port
     * yards, each going for its next stake (an established stake, nearest the hull, is one any lane goes for once its
     * own are down), won when every stake on the map is down.
     *
     * <p>The lanes advance on the stakes and fight what they meet; the allied commander fighting the watch (anti-air
     * on flocks, anti-armour on frames, firing on stakes' pylons and nodes) is the hull-rules session's
     * ({@code stories.md}, The garrison against the watch), so a lane at a standing stake holds round it until the
     * company or that work brings it down.
     */
    static BattleSimulation line(long seed, List<ShuttleAssignment> manifest, TargetProfile profile,
                                 FlockLivery livery) {
        Stood stood = stand(StoryMap.LONG_SURVEY_LINE, seed, manifest, profile, "Company lands on the avenue");
        BattleSimulation sim = stood.sim();
        FallenHullSite site = LongSurveyGround.rulesSite(stood.ground().hull(), List.of(),
                atMouth(stood.ground(), LINE_WALKERS), LINE_FEEDSTOCK);
        FallenHull hull = FallenHull.install(sim, site, LINE_QUEUE, livery);
        sim.addObjective(HullClaimObjective.claim(hull, Strings.get("longSurveyObjectiveLine")));
        HeartLanes lanes = BloomHeartSetup.alliedLanes(sim, Form.BROAD_FRONT, stood.ground().battleSeed())
                .targets(s -> stakeTargets(hull.stakes(), hull::seatOf));
        sim.installAlliedPush(lanes);
        return sim;
    }

    /**
     * The claim's stakes as the lanes' targets, keyed {@code stake-N} by lattice point: each at its seat, living while
     * it stands; an established stake is a mother (any lane's once its own are down), a younger one a satellite (the
     * lane's whose band it stands in). A stake with no seat yet is not a target.
     */
    static List<KnownBore> stakeTargets(Map<Integer, SurveyStake> stakes, IntFunction<int[]> seat) {
        List<KnownBore> out = new ArrayList<>();
        for (Map.Entry<Integer, SurveyStake> e : new TreeMap<>(stakes).entrySet()) {
            int[] at = seat.apply(e.getKey());
            if (at == null) continue;
            SurveyStake stake = e.getValue();
            out.add(new KnownBore("stake-" + e.getKey(), stake.stage() == SurveyStake.Stage.ESTABLISHED, at[0], at[1],
                    !HullClaimObjective.down(stake)));
        }
        return out;
    }

    // ---- The Surveyor ----

    /** The Surveyor's forge program: the full claim's, line and siege frames with escorted stakes between. UNTUNED. */
    static final BuildQueue SURVEYOR_QUEUE = new BuildQueue(List.of(
            BuildQueue.Item.walker(DerelictWalkerFrame.LINE, BuildQueue.LINE_COST),
            BuildQueue.Item.walker(DerelictWalkerFrame.ROUNDS, BuildQueue.WORKER_COST),
            BuildQueue.Item.stake(BuildQueue.STAKE_COST, DerelictWalkerFrame.LINE),
            BuildQueue.Item.walker(DerelictWalkerFrame.SIEGE, BuildQueue.LINE_COST),
            BuildQueue.Item.walker(DerelictWalkerFrame.LINE, BuildQueue.LINE_COST)));
    /** The hull's store at the start: two line frames' worth. UNTUNED. */
    static final float SURVEYOR_FEEDSTOCK = BuildQueue.LINE_COST * 2;
    /** The watch already out at the mouth: line frames, a siege frame and a rounds walker. UNTUNED. */
    static final List<DerelictWalkerFrame> SURVEYOR_WALKERS = List.of(DerelictWalkerFrame.LINE,
            DerelictWalkerFrame.LINE, DerelictWalkerFrame.SIEGE, DerelictWalkerFrame.ROUNDS);

    /**
     * The Surveyor (`long-survey-arc.md`, The Surveyor): the company down on the supply road with Broad Front's lanes
     * pushing on the full claim round the hull, its stakes their targets as in The Line; the hull's interior the delve,
     * the vault frame posted in the forge hall and a hall frame in the hall nearest it; won when the autoforge is out.
     * The push is the way in, not the win: cutting the outer chain strands the claim, and the forge is the objective.
     */
    static BattleSimulation surveyor(long seed, List<ShuttleAssignment> manifest, TargetProfile profile,
                                     FlockLivery livery) {
        Stood stood = stand(StoryMap.LONG_SURVEY_SURVEYOR, seed, manifest, profile, "Company lands on the supply road");
        BattleSimulation sim = stood.sim();
        HullSite ground = stood.ground().hull();
        List<FallenHullSite.Posting> walkers = new ArrayList<>(atMouth(stood.ground(), SURVEYOR_WALKERS));
        Area forgeHall = ground.forgeHall();
        walkers.add(new FallenHullSite.Posting(DerelictWalkerFrame.VAULT, forgeHall.centreX(), forgeHall.centreY()));
        Area hall = hallPost(ground.interior().chambers(), forgeHall);
        walkers.add(new FallenHullSite.Posting(DerelictWalkerFrame.HALL, hall.centreX(), hall.centreY()));
        FallenHullSite site = LongSurveyGround.rulesSite(ground, List.of(), walkers, SURVEYOR_FEEDSTOCK);
        FallenHull hull = FallenHull.install(sim, site, SURVEYOR_QUEUE, livery);
        sim.addObjective(HullClaimObjective.forge(hull, Strings.get("longSurveyObjectiveForge")));
        HeartLanes lanes = BloomHeartSetup.alliedLanes(sim, Form.BROAD_FRONT, stood.ground().battleSeed())
                .targets(s -> stakeTargets(hull.stakes(), hull::seatOf));
        sim.installAlliedPush(lanes);
        return sim;
    }

    /**
     * Where the hall frame stands: the interior's chamber nearest the forge hall that is not the forge hall itself
     * (the last hall before the vault), else the forge hall when the interior has no other.
     */
    static Area hallPost(List<Area> chambers, Area forgeHall) {
        Area best = null;
        long bestD = Long.MAX_VALUE;
        for (Area a : chambers) {
            if (a.contains(forgeHall.centreX(), forgeHall.centreY())) continue;
            long dx = a.centreX() - forgeHall.centreX(), dy = a.centreY() - forgeHall.centreY(), d = dx * dx + dy * dy;
            if (d < bestD) {
                bestD = d;
                best = a;
            }
        }
        return best == null ? forgeHall : best;
    }

    // ---- common ----

    /** A chapter's battle stood up: its ground and the simulation on it, the company landing. */
    record Stood(LongSurveyGround.Ground ground, BattleSimulation sim, List<LandingPad> pads) { }

    /**
     * {@code map}'s owned ground stood up with the company landing on its pads by shuttle, its machines called down
     * on them, and the company's loss: every chapter is lost when the company is eliminated.
     */
    static Stood stand(StoryMap map, long seed, List<ShuttleAssignment> manifest, TargetProfile profile, String landing) {
        if (manifest == null || manifest.isEmpty()) throw new IllegalArgumentException("a chapter needs a company to land");
        StoryMapPool.Entry entry = StoryMapPools.pool(map).entry(profile.surface(), seed);
        if (entry == null) throw new IllegalArgumentException(map.id + " does not own seed " + seed);
        LongSurveyGround.Ground ground = LongSurveyGround.ground(map, profile, entry);
        BattleSimulation sim = BattleSetup.buildMap(ground.map(), Collections.emptyList(), Collections.emptyList(),
                ground.battleSeed()).sim();
        sim.setSurface(profile.surface());
        sim.addObjective(new EliminateFactionObjective(Faction.DEFENDER, Faction.MARINE));
        List<LandingPad> pads = LandingPadSelector.select(ground.map(), manifest.size(), BattleSetup.LZ_MIN_SEPARATION);
        BattleSetup.recordLzPads(sim, pads);
        BattleSetup.landCompany(sim, pads, manifest, new Random(ground.battleSeed()),
                SquadCommandClaim.mission(ISSUER, landing), InfantryLoadoutRolls::lateCampaignSquad);
        sim.setMechDrops(new MechDropService(profile.declaredMechDrops()));
        sim.mechDrops().installScriptedCaller(Faction.MARINE, SquadCommandClaim.mission(ISSUER, "Company's mech lance"),
                () -> MechDropService.centroidOf(sim.getMarineLandingPads()));
        return new Stood(ground, sim, pads);
    }

    /** {@code frames} already out at the hull's mouth. */
    static List<FallenHullSite.Posting> atMouth(LongSurveyGround.Ground ground, List<DerelictWalkerFrame> frames) {
        int[] mouth = mouth(ground);
        List<FallenHullSite.Posting> out = new ArrayList<>();
        for (DerelictWalkerFrame frame : frames) out.add(new FallenHullSite.Posting(frame, mouth[0], mouth[1]));
        return out;
    }

    private static int[] mouth(LongSurveyGround.Ground ground) {
        FallenHullSite probe = LongSurveyGround.rulesSite(ground.hull(), List.of(), List.of(), 0f);
        return new int[] {probe.mouthX(), probe.mouthY()};
    }
}
