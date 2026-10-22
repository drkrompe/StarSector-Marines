package com.dillon.starsectormarines.battle.unit;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.evacuation.SwarmStrains;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.engine.ecs.ArchetypeTable;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The body pile cap against its control (`colony-breach-nouns.md`, Body pile cap): the same Runner
 * waves die on the same bare killing ground in front of a defended line, where no creep and no bore
 * clears them, for {@link #WAVE_SECONDS}, once with the cap off and once with it on. Each frame is
 * 1920x1080 at 32 px per cell; the capped frame shows each {@link BodyPileCap#AREA_CELLS}-cell area
 * held at {@link BodyPileCap#AREA_LIMIT} bodies, its oldest faded out.
 */
public final class BodyPileSnapshotSuite implements SnapshotSuite {

    private static final int W = 60, H = 34;
    /** Seconds of waves dying on the ground. */
    private static final int WAVE_SECONDS = 90;
    /** Bodies a second, so the pile builds with distinct ages. */
    private static final int DEAD_PER_SECOND = 10;
    /** The killing ground: a band in front of the line at {@link #LINE_X}. */
    private static final int LINE_X = 44, BAND_DEPTH = 16, BAND_TOP = 5, BAND_BOTTOM = 29;

    @Override public String id() { return "body-pile"; }

    @Override public String label() {
        return "Body pile cap: 900 Runner dead before a defended line, uncapped against capped";
    }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        List<SnapshotArtifact> out = new ArrayList<>();
        for (boolean capped : new boolean[]{false, true}) {
            NavigationGrid grid = new NavigationGrid(W, H);
            CellTopology topology = new CellTopology(W, H);
            for (int y = 0; y < H; y++) {
                for (int x = 0; x < W; x++) {
                    grid.setWalkableFloor(x, y);
                    topology.setGroundKind(x, y, CellTopology.GroundKind.DIRT);
                }
            }
            SceneWorld world = SceneBuilder.over(grid, topology).seed(20261022L).missionCompletion(false).build();
            try (BattleSimulation sim = world.sim()) {
                sim.bodyPileCap().setEnabled(capped);
                Random random = new Random(0xB0D1E5L);
                int perTick = 0;
                int laid = 0;
                int ticks = Math.round(WAVE_SECONDS / BattleSimulation.TICK_DT);
                BattleComponents c = sim.getBattleComponents();
                List<Long> falling = new ArrayList<>();
                for (int t = 0; t < ticks; t++) {
                    // last tick's arrivals die where they lie, each turned its own way
                    for (long runner : falling) sim.applyDamage(runner, 1_000_000f, 1000f);
                    falling.clear();
                    // deaths crowd toward the wire, as a wave's do
                    perTick += DEAD_PER_SECOND;
                    while (perTick >= Math.round(1f / BattleSimulation.TICK_DT)) {
                        perTick -= Math.round(1f / BattleSimulation.TICK_DT);
                        double depth = Math.sqrt(random.nextDouble());
                        int x = LINE_X - 1 - (int) ((1 - depth) * BAND_DEPTH);
                        int y = BAND_TOP + random.nextInt(BAND_BOTTOM - BAND_TOP);
                        long runner = SwarmStrains.spawn(sim, "dead " + laid++, UnitType.SWARM_RUNNER, x, y);
                        sim.world().setPos(runner, x + random.nextFloat(), y + random.nextFloat());
                        sim.getEntityWorld().setFloat(runner, c.LAYERED_ANIMATION,
                                BattleComponents.LAYERED_FACING_DEGREES, random.nextFloat() * 360f);
                        falling.add(runner);
                    }
                    sim.advance(BattleSimulation.TICK_DT);
                }
                for (long runner : falling) sim.applyDamage(runner, 1_000_000f, 1000f);
                // the last fades run out
                int settle = Math.round((BodyPileCap.FADE_SECONDS + 2f) / BattleSimulation.TICK_DT);
                for (int t = 0; t < settle; t++) sim.advance(BattleSimulation.TICK_DT);
                for (int x = 8; x < W; x += 16) sim.getFogOfWar().addEphemeralSource(x, H / 2, 28, 28f);
                int lying = lying(sim);
                BattleReviewFrameRenderer camera = new BattleReviewFrameRenderer(context.modRoot(), 1920, 1080,
                        W / 2f, H / 2f, W);
                String title = "BODY PILE / " + laid + " Runner dead over " + WAVE_SECONDS + " s before a line at x="
                        + LINE_X + " / no creep, no bore / " + (capped
                        ? "CAPPED: " + BodyPileCap.AREA_LIMIT + " per " + BodyPileCap.AREA_CELLS + "x"
                        + BodyPileCap.AREA_CELLS + " cells, " + lying + " lying, " + sim.bodyPileCap().retired()
                        + " faded out" : "UNCAPPED (-Dbattle.bodyPileCap=false): " + lying + " lying")
                        + " / 32 px per cell";
                out.add(new SnapshotArtifact(capped ? "02-capped.png" : "01-uncapped.png", camera.render(sim, title)));
            }
        }
        return out;
    }

    /** Bodies still drawn on the field. */
    private static int lying(BattleSimulation sim) {
        BattleComponents c = sim.getBattleComponents();
        int lying = 0;
        for (ArchetypeTable t : sim.getEntityWorld().matched(c.corpses)) {
            int[] sprite = t.ints(c.SPRITE, BattleComponents.SPRITE_INDEX).array();
            for (int r = 0, n = t.rowCount(); r < n; r++) if (sprite[r] >= 0) lying++;
        }
        return lying;
    }
}
