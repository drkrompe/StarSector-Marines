package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.story.StoryBeatLog;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.testsupport.HeadlessBattles;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The owner's question {@code heart-gunship} (2026-10-24): every played sortie of the heart's gunship had been shot
 * down by spore anti-air, so how should the commander risk it? One shot per {@link HeartGunship.Policy}, each the same
 * Broad Front town battle as {@link HeartCommanderLookSuite} with only the policy changed, played unrecorded to the
 * policy's telling moment and recorded through the look preset from there: the first sortie called, or, for a policy
 * that never sends one by {@link #MOMENT_BY}, the target it is waiting on. The four plays run side by side; each prints
 * what its policy did (sorties, shoots, aircraft lost). Opt-in, minutes.
 */
public final class HeartGunshipOptionsLookSuite implements SnapshotSuite {

    /** The latest tick a policy's moment is waited for. */
    static final int MOMENT_BY = 16000;
    /** Ticks recorded from the moment. */
    static final int RECORD_TICKS = 1200;
    /** Recorded frames (one every 6 ticks) kept as stills: the sortie on its way, over its target, and after. */
    static final int[] STILLS = {40, 110, 180};
    /** The camera's width in cells. */
    static final float CELLS_ACROSS = 110f;

    @Override public String id() { return "heart-gunship-options"; }

    @Override public String label() { return "Heart gunship policy: one Broad Front battle per option of the owner's question"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        HeadlessStrings strings = HeadlessBattles.install(context.projectRoot());
        Map<HeartGunship.Policy, CompletableFuture<BattleSimulation>> played = new EnumMap<>(HeartGunship.Policy.class);
        ExecutorService pool = Executors.newFixedThreadPool(HeartGunship.Policy.values().length);
        try {
            for (HeartGunship.Policy policy : HeartGunship.Policy.values()) {
                played.put(policy, CompletableFuture.supplyAsync(() -> playToMoment(policy), pool));
            }
            List<SnapshotArtifact> out = new ArrayList<>();
            for (HeartGunship.Policy policy : HeartGunship.Policy.values()) {
                BattleSimulation sim = played.get(policy).join();
                String name = "gunship-" + policy.name().toLowerCase().replace('_', '-');
                out.addAll(LookReview.record(context.modRoot(), new LookReview.Shot(name, LookGround.verdant(), 40, 30,
                        placement -> sim,
                        placement -> List.of(new LookReview.View("play", HeartGunshipOptionsLookSuite::focus)),
                        RECORD_TICKS, 6, 3).withStills(STILLS)));
                System.out.printf("    [gunship/%s] after recording: %s%n", policy, tally(sim));
            }
            return out;
        } finally {
            pool.shutdownNow();
            strings.close();
        }
    }

    /** The battle under {@code policy}, played to its first sortie called or to {@link #MOMENT_BY}. */
    static BattleSimulation playToMoment(HeartGunship.Policy policy) {
        BattleSimulation sim;
        try {
            sim = HeadlessBattles.fixture(new JSONObject(HeartCommanderLookSuite.FIXTURE));
        } catch (Exception e) {
            throw new IllegalStateException("heart fixture", e);
        }
        sim.getHeart().lanes().gunship().policy(policy);
        int seen = 0;
        boolean called = false;
        while (!called && sim.getSimTickIndex() < MOMENT_BY) {
            sim.advance(BattleSimulation.TICK_DT);
            List<StoryBeatLog.Event> beats = sim.getStoryBeats();
            for (; seen < beats.size(); seen++) if (beats.get(seen).id().contains("gunship_called")) called = true;
        }
        System.out.printf("    [gunship/%s] moment at tick %d (%s): %s%n", policy, sim.getSimTickIndex(),
                called ? "sortie called" : "no sortie", tally(sim));
        return sim;
    }

    /** On the base's aircraft while one is up, else on what the commander wants struck, else on the company. */
    static float[] focus(BattleSimulation sim) {
        for (long id : sim.getAirEntityIds()) {
            if (sim.identity().faction(id) == Faction.ALLY && sim.world().isAlive(id)) {
                return new float[]{sim.world().x(id), sim.world().y(id), CELLS_ACROSS};
            }
        }
        int[] target = sim.getHeart().lanes().gunship().target(sim, sim.getHeart().lanes());
        if (target != null) return new float[]{target[0] + .5f, target[1] + .5f, CELLS_ACROSS};
        float[] centre = HeartObjectives.companyCentre(sim);
        if (centre == null) centre = new float[]{sim.getGrid().getWidth() / 2f, sim.getGrid().getHeight() / 2f};
        return new float[]{centre[0], centre[1], CELLS_ACROSS};
    }

    /** The policy's beats so far: sorties called, the base's shoots, and aircraft of ours shot down. */
    static String tally(BattleSimulation sim) {
        int calls = 0, shoots = 0;
        StringBuilder at = new StringBuilder();
        for (StoryBeatLog.Event beat : sim.getStoryBeats()) {
            if (beat.id().contains("gunship_called")) { calls++; at.append(" call@").append(beat.tick()); }
            if (beat.id().contains("air_shoot")) { shoots++; at.append(" shoot@").append(beat.tick()); }
        }
        int up = 0;
        for (long id : sim.getAirEntityIds()) if (sim.identity().faction(id) == Faction.ALLY && sim.world().isAlive(id)) up++;
        return calls + " sorties, " + shoots + " shoots, " + up + " allied aircraft up;" + at;
    }
}
