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
 * A convoy's lead truck taking the turn where two stretches of the road's route meet ({@code CordonConvoys} lays the
 * stretch from halfway along the one before where they meet sharply), as the owner would see it: the town Road
 * verdant 8, played once unrecorded to find the tick the lead truck first comes near the square's corner, then played
 * again to before it and recorded through the look preset following that truck round the corner.
 *
 * <p>{@code -Dsnapshot.cordonAlley.seed=8}, {@code .corner=157,143} and {@code .ticks=1500} choose the battle, the
 * corner and the length; the shot opens {@link #LEAD} ticks before the truck comes near the corner. Opt-in: it plays
 * a battle twice before it draws.
 */
public final class CordonAlleyLookSuite implements SnapshotSuite {

    /** The latest tick the truck is waited for. */
    static final int BY = 9000;
    /** Ticks of the approach the shot keeps before the truck comes near the corner. */
    static final int LEAD = 120;
    /** Cells from the corner that count as near it. */
    static final float NEAR = 8f;
    /** Ticks between recorded frames. */
    static final int EVERY = 2;

    @Override public String id() { return "cordon-alley-look"; }

    @Override public String label() { return "Cordon: a convoy's lead truck turning the square's corner on the town Road"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        long seed = Long.parseLong(System.getProperty("snapshot.cordonAlley.seed", "8").trim());
        String[] corner = System.getProperty("snapshot.cordonAlley.corner", "157,143").split(",");
        float cx = Float.parseFloat(corner[0].trim()), cy = Float.parseFloat(corner[1].trim());
        int ticks = Integer.parseInt(System.getProperty("snapshot.cordonAlley.ticks", "1500").trim());
        BattleSimulation probe = battle(seed);
        while (lead(probe) == 0L || !near(probe, lead(probe), cx, cy)) {
            if (probe.getSimTickIndex() >= BY) throw new IllegalStateException("no truck near the corner by tick " + BY);
            probe.advance(BattleSimulation.TICK_DT);
        }
        int from = Math.max(1, probe.getSimTickIndex() - LEAD);
        // Stills are counted in recorded frames, one every EVERY ticks.
        int frames = ticks / EVERY;
        return LookReview.record(context.modRoot(), new LookReview.Shot("alley", LookGround.of(SurfacePalette.VERDANT, seed),
                40, 30, placement -> {
                    BattleSimulation sim = battle(seed);
                    while (sim.getSimTickIndex() < from) sim.advance(BattleSimulation.TICK_DT);
                    placement.subject("truck", lead(sim));
                    System.out.printf("    [look/alley] seed %d: truck %d, convoys %s, recorded from tick %d%n",
                            seed, lead(sim), sim.getCordon().convoys().describe(sim), sim.getSimTickIndex());
                    return sim;
                }, placement -> List.of(
                        LookReview.View.follow("play", placement, BattleReviewFrameRenderer.PLAY_ZOOM_CELLS, "truck"),
                        LookReview.View.follow("close", placement, 32f, "truck")), ticks, EVERY, 3)
                .withStills(0, frames / 3, frames / 2, 2 * frames / 3, frames - 1));
    }

    /** The battle the probe plays: the Road fixture's company on this seed's verdant town map. */
    private static BattleSimulation battle(long seed) {
        try {
            return HeadlessBattles.build("cordon:variant=road,density=town,biome=verdant,seed=" + seed);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** The first convoy's lead truck, or 0 before it is on the road. */
    private static long lead(BattleSimulation sim) {
        CordonConvoys convoys = sim.getCordon().convoys();
        if (convoys == null || convoys.trucks().isEmpty()) return 0L;
        return convoys.trucks().get(0);
    }

    private static boolean near(BattleSimulation sim, long hull, float x, float y) {
        if (sim.convoy().body(hull) == null) return false;
        return Math.hypot(sim.convoy().body(hull).x - x, sim.convoy().body(hull).y - y) <= NEAR;
    }
}
