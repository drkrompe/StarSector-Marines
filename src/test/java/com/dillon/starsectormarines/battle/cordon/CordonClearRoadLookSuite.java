package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.testsupport.HeadlessBattles;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.List;

/**
 * R1, Clear the road, as the owner would see it ({@code cordon-nouns.md}, Optional objectives): the town Road the probe
 * plays, played once unrecorded to find the tick the road through the wreck opens, then played again to before it
 * and recorded through the look preset with the camera on the dozer, as its blade opens the wreck and it pulls aside.
 *
 * <p>{@code -Dsnapshot.cordonClearRoad.seed=8} and {@code .ticks=900} choose the battle and the length; the shot opens
 * {@link #LEAD} of the ticks before the road opens. Opt-in: it plays a battle twice before it draws.
 */
public final class CordonClearRoadLookSuite implements SnapshotSuite {

    /** The latest tick the road is waited for. */
    static final int BY = 9000;
    /** The share of the shot before the road opens. */
    static final float LEAD = 0.4f;
    /** Ticks between recorded frames. */
    static final int EVERY = 2;

    @Override public String id() { return "cordon-clear-road-look"; }

    @Override public String label() { return "Cordon: the dozer opening the wreck across the Road's corridor"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        long seed = Long.parseLong(System.getProperty("snapshot.cordonClearRoad.seed", "8").trim());
        int ticks = Integer.parseInt(System.getProperty("snapshot.cordonClearRoad.ticks", "900").trim());
        BattleSimulation probe = battle(seed);
        while (!beat(probe).cleared()) {
            if (probe.getSimTickIndex() >= BY) throw new IllegalStateException("road not open by tick " + BY + ": "
                    + beat(probe).describe(probe));
            probe.advance(BattleSimulation.TICK_DT);
        }
        int from = Math.max(1, probe.getSimTickIndex() - Math.round(ticks * LEAD));
        // Stills are counted in recorded frames, one every EVERY ticks.
        int frames = ticks / EVERY;
        return LookReview.record(context.modRoot(), new LookReview.Shot("clear-road", LookGround.of(SurfacePalette.ARID, seed),
                40, 30, placement -> {
                    BattleSimulation sim = battle(seed);
                    while (sim.getSimTickIndex() < from) sim.advance(BattleSimulation.TICK_DT);
                    placement.subject("dozer", beat(sim).dozer());
                    System.out.printf("    [look/clear-road] seed %d: %s, recorded from tick %d%n", seed,
                            beat(sim).describe(sim), sim.getSimTickIndex());
                    return sim;
                }, placement -> List.of(
                        LookReview.View.follow("play", placement, BattleReviewFrameRenderer.PLAY_ZOOM_CELLS, "dozer"),
                        LookReview.View.follow("close", placement, 32f, "dozer")), ticks, EVERY, 3)
                .withStills(0, frames / 4, Math.round(frames * LEAD), 3 * frames / 4, frames - 1));
    }

    /** The battle the probe plays: the Road fixture's company on this seed's town map. */
    private static BattleSimulation battle(long seed) {
        try {
            return HeadlessBattles.build("cordon:variant=road,density=town,biome=arid,seed=" + seed);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static ClearRoadBeat beat(BattleSimulation sim) {
        for (CordonBeat b : sim.getCordon().beats().running()) if (b instanceof ClearRoadBeat r) return r;
        throw new IllegalStateException("Clear the road was not rolled on this battle");
    }
}
