package com.dillon.starsectormarines.battle.derelict;

import com.dillon.starsectormarines.battle.drone.FlockLivery;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneRun;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A fallen hull's claim in battle (`fallen-hull-nouns.md`, In a battle), on the open-ground stand-in site
 * ({@link FallenHullFixtures}), with no company on the claim. What the rules' unit tests cannot show: the battle
 * carrying them out.
 *
 * <p>{@code train-plants}: the forge prints a stake; its train walks out of the mouth to the nearest lattice point and
 * plants it; the pylon rises into a new zone with its node, linked to the hull's, and a flock goes up from it.
 * {@code train-scrapped}: the train's hauler is destroyed on the way; the stake is scrap and nothing stands on the point.
 * {@code stranded}: an established stake's node is destroyed; the planted stake beyond it is stranded and never rises.
 * {@code feedstock}: a stripper cuts a pile on the claim and a hauler carries the metal to the hull's store.
 */
public final class FallenHullScene implements BehaviorScene {

    public static final String TRAIN_PLANTS = "train-plants";
    public static final String TRAIN_SCRAPPED = "train-scrapped";
    public static final String STRANDED = "stranded";
    public static final String FEEDSTOCK = "feedstock";

    /** Ticks a second. */
    static final int HZ = 30;

    private final int frameEvery;

    public FallenHullScene() {
        this(30);
    }

    public FallenHullScene(int frameEvery) {
        this.frameEvery = frameEvery;
    }

    @Override public String id() { return "fallen-hull"; }

    @Override public String label() {
        return "Fallen hull: a stake train plants and its stake rises with a flock; a train whose hauler dies leaves "
                + "scrap; a cut chain strands the stake beyond; strippers and haulers feed the hull's store";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) {
        return List.of(trainPlants(frames, false), trainPlants(frames, true), stranded(frames), feedstock(frames));
    }

    private static SceneWorld ground() {
        int w = FallenHullFixtures.WIDTH;
        int h = FallenHullFixtures.HEIGHT;
        // Holdouts boxed in at the far corners keep both sides on the field.
        return SceneBuilder.openGround(w, h).missionCompletion(false)
                .wall(w - 9, h - 9, w - 2, h - 9).wall(w - 9, h - 2, w - 2, h - 2)
                .wall(w - 9, h - 9, w - 9, h - 2).wall(w - 2, h - 9, w - 2, h - 2)
                .unit("marine-holdout", Faction.MARINE, UnitType.MARINE, w - 5, h - 5)
                .wall(w - 9, 1, w - 2, 1).wall(w - 9, 8, w - 2, 8)
                .wall(w - 9, 1, w - 9, 8).wall(w - 2, 1, w - 2, 8)
                .unit("defender-holdout", Faction.DEFENDER, UnitType.MARINE, w - 5, 4)
                .build();
    }

    private SceneReport trainPlants(FrameSink frames, boolean killHauler) {
        String id = killHauler ? TRAIN_SCRAPPED : TRAIN_PLANTS;
        SceneWorld world = ground();
        BattleSimulation sim = world.sim();
        // One stake, then an item the forge can never pay for, so only the one train ever goes out.
        BuildQueue queue = new BuildQueue(List.of(BuildQueue.Item.stake(0f, DerelictWalkerFrame.ROUNDS),
                BuildQueue.Item.walker(DerelictWalkerFrame.LINE, 1.0e6f)));
        FallenHull hull = FallenHull.install(sim, FallenHullFixtures.site(List.of(), List.of(), List.of(), 0f), queue,
                FlockLivery.DOMAIN);
        DerelictSecurityDirector director = sim.derelictSecurity();
        int flocksAtStart = sim.flocks().flocks().size();
        int stakeZone = FallenHull.zoneOf(0);
        int[] plantedAt = {-1};
        int[] raisedAt = {-1};
        int[] trainOutAt = {-1};
        float[] haulerFarthest = {0f};
        int[] hauler = {-1};
        int ticks = (int) ((Autoforge.CYCLE_SECONDS + 30f + SurveyStake.RAISE_SECONDS + 10f) * HZ);
        SceneRun.of(world).observe((s, tick) -> {
            if (!hull.trains().isEmpty() && trainOutAt[0] < 0) {
                trainOutAt[0] = tick;
                hauler[0] = hull.trains().get(0).haulerSquad;
            }
            if (hauler[0] >= 0 && s.squadMemberCount(hauler[0]) > 0) {
                long lead = s.squadMemberAt(hauler[0], 0);
                haulerFarthest[0] = Math.max(haulerFarthest[0], s.world().x(lead) - FallenHullFixtures.MOUTH_X);
                // The scrapped loop kills the hauler halfway out.
                if (killHauler && s.world().x(lead) > FallenHullFixtures.MOUTH_X + 6f) {
                    s.applyExternalDamage(lead, 1.0e6f, 1.0e6f);
                }
            }
            if (plantedAt[0] < 0 && hull.stakes().containsKey(0)) plantedAt[0] = tick;
            if (raisedAt[0] < 0 && director.network().zone(stakeZone) != null) raisedAt[0] = tick;
        }).frames(frames, id, frameEvery, tick -> id + " / tick " + tick + " / printed " + hull.forge().printed())
                .run(ticks);
        boolean linked = director.network().linked(FallenHull.HULL_ZONE).contains(stakeZone);
        boolean node = director.nodes().containsKey(stakeZone);
        int flocks = sim.flocks().flocks().size() - flocksAtStart;
        int scrapped = hull.scrappedTrains();
        boolean pointOpen = !hull.stakes().containsKey(0);
        int printed = hull.forge().printed();
        int enRoute = hull.trains().size();
        String stakesNow = hull.stakes().keySet().toString();
        sim.close();

        List<Verdict> v = new ArrayList<>();
        v.add(Verdict.of("train-goes-out", trainOutAt[0] >= 0,
                "the forge printed a stake train at tick " + trainOutAt[0]));
        if (killHauler) {
            v.add(Verdict.of("scrap", scrapped == 1 && pointOpen && raisedAt[0] < 0,
                    "trains scrapped " + scrapped + "; point 0 open " + pointOpen + "; zone raised " + (raisedAt[0] >= 0)
                            + "; printed " + printed + "; en route " + enRoute + "; stakes " + stakesNow));
        } else {
            v.add(Verdict.of("plants", plantedAt[0] >= 0,
                    "hauler walked " + round(haulerFarthest[0]) + " cells out; planted at tick " + plantedAt[0]));
            v.add(Verdict.of("rises-after-countdown", raisedAt[0] >= 0
                            && raisedAt[0] - plantedAt[0] >= (int) (SurveyStake.RAISE_SECONDS * HZ) - 30,
                    "raised at tick " + raisedAt[0] + ", " + (raisedAt[0] - plantedAt[0]) / HZ + " s after planting"));
            v.add(Verdict.of("zone-with-node-and-flock", linked && node && flocks >= 1,
                    "linked to the hull " + linked + "; node " + node + "; flocks up " + flocks));
        }
        Map<String, Number> m = new LinkedHashMap<>();
        m.put("trainOutTick", trainOutAt[0]);
        m.put("plantedTick", plantedAt[0]);
        m.put("raisedTick", raisedAt[0]);
        return new SceneReport(id(), id, ticks, v, m);
    }

    private SceneReport stranded(FrameSink frames) {
        SceneWorld world = ground();
        BattleSimulation sim = world.sim();
        // Point 0 established, point 1 planted beyond it; the forge prints nothing it could pay for.
        FallenHull hull = FallenHull.install(sim, FallenHullFixtures.site(List.of(
                        new FallenHullSite.StartingStake(0, SurveyStake.Stage.ESTABLISHED),
                        new FallenHullSite.StartingStake(1, SurveyStake.Stage.PLANTED)), List.of(), List.of(), 0f),
                BuildQueue.survey(), FlockLivery.DOMAIN);
        DerelictSecurityDirector director = sim.derelictSecurity();
        long node = director.nodes().get(FallenHull.zoneOf(0));
        sim.applyExternalDamage(node, 1.0e6f, 1.0e6f);
        int ticks = (int) ((SurveyStake.RAISE_SECONDS + 15f) * HZ);
        SceneRun.of(world).frames(frames, STRANDED, frameEvery, tick -> STRANDED + " / tick " + tick).run(ticks);
        SurveyStake beyond = hull.stakes().get(1);
        SurveyStake cut = hull.stakes().get(0);
        sim.close();
        List<Verdict> v = new ArrayList<>();
        v.add(Verdict.of("node-cut-strands-it", cut.stranded(), "the established stake is stranded " + cut.stranded()));
        v.add(Verdict.of("beyond-stranded-and-folded", beyond != null && beyond.stranded()
                        && beyond.stage() == SurveyStake.Stage.PLANTED,
                "the stake beyond: " + (beyond == null ? "gone" : beyond.stage() + ", stranded " + beyond.stranded())));
        return new SceneReport(id(), STRANDED, ticks, v, new LinkedHashMap<>());
    }

    private SceneReport feedstock(FrameSink frames) {
        SceneWorld world = ground();
        BattleSimulation sim = world.sim();
        int pileX = FallenHullFixtures.MOUTH_X + 8;
        FallenHull hull = FallenHull.install(sim, FallenHullFixtures.site(List.of(),
                        List.of(new FallenHullSite.Pile(pileX, FallenHullFixtures.ROW_Y + 4, 500f)),
                        List.of(new FallenHullSite.Posting(DerelictWalkerFrame.STRIPPER, pileX - 2,
                                        FallenHullFixtures.ROW_Y + 2),
                                new FallenHullSite.Posting(DerelictWalkerFrame.HAULER, FallenHullFixtures.MOUTH_X,
                                        FallenHullFixtures.ROW_Y - 3)), 0f),
                // A program it cannot pay for keeps the store's metal where the haulers put it.
                new BuildQueue(List.of(BuildQueue.Item.walker(DerelictWalkerFrame.LINE, 1.0e6f))), FlockLivery.DOMAIN);
        int ticks = 90 * HZ;
        SceneRun.of(world).frames(frames, FEEDSTOCK, frameEvery, tick -> FEEDSTOCK + " / tick " + tick + " / store "
                + Math.round(hull.hullStore().metal())).run(ticks);
        float stored = hull.hullStore().metal();
        float pileLeft = hull.salvage().isEmpty() ? 0f : hull.salvage().get(0).metal();
        sim.close();
        List<Verdict> v = new ArrayList<>();
        v.add(Verdict.of("stripped-and-hauled-home", stored >= StripperWork.CAPACITY - 0.5f,
                "hull store " + round(stored) + "; pile left " + round(pileLeft) + " of 500"));
        Map<String, Number> m = new LinkedHashMap<>();
        m.put("stored", round(stored));
        return new SceneReport(id(), FEEDSTOCK, ticks, v, m);
    }

    private static float round(float value) {
        return Math.round(value * 10f) / 10f;
    }
}
