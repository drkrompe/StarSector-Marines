package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.command.SquadCommandClaim;
import com.dillon.starsectormarines.battle.command.objective.EliminateFactionObjective;
import com.dillon.starsectormarines.battle.command.objective.HullClaimObjective;
import com.dillon.starsectormarines.battle.derelict.BuildQueue;
import com.dillon.starsectormarines.battle.derelict.DerelictWalkerFrame;
import com.dillon.starsectormarines.battle.derelict.FallenHull;
import com.dillon.starsectormarines.battle.derelict.FallenHullSite;
import com.dillon.starsectormarines.battle.derelict.SurveyStake;
import com.dillon.starsectormarines.battle.drone.FlockLivery;
import com.dillon.starsectormarines.battle.power.MechDropService;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.pool.StoryMap;
import com.dillon.starsectormarines.battle.world.gen.pool.StoryMapPool;
import com.dillon.starsectormarines.battle.world.gen.pool.StoryMapPools;
import com.dillon.starsectormarines.i18n.Strings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The Long Survey's chapters 2 to 4 as battles ({@code long-survey-arc.md}, Chapters): each chapter's owned ground
 * ({@link LongSurveyGround}) with the company landed on it, the fallen hull's watch over it ({@link FallenHull}) and
 * the chapter's objective against the claim ({@link HullClaimObjective}). The player commands the company; no
 * autonomous commander is installed for it. Lost as every chapter is, the company eliminated.
 *
 * <ul>
 *   <li>Stakes: the Sabotage family's three named sites are the three stakes the ground planted; kill them.</li>
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
