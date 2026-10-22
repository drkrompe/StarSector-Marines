package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The SwarmDamageState loops close on the bore at 64 px per cell: whole, wounded, bleeding, and the remains. Then
 * the bleeding itself, which the loops' one-a-second frames cannot show: each bore hurt until every wound is open,
 * held still while the presentation clock runs, at play zoom and closer.
 */
public final class SwarmDamageStateSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    /** Thirty cells across a 1920-wide frame: 64 px per cell. */
    private static final float CELLS_ACROSS = 30f;
    /** Closer, for the ooze itself: 120 px per cell. */
    private static final float CLOSE_CELLS_ACROSS = 16f;
    private static final int BLEED_FRAMES = 36;
    private static final float BLEED_STEP_SECONDS = 1f / 15f;

    public SwarmDamageStateSceneSnapshotSuite() {
        super(new SwarmDamageStateScene(), 1920, 1080);
    }

    @Override protected boolean showUnitShadows() { return true; }

    @Override protected float closeViewScale() { return 1f; }

    @Override
    protected float[] closeView(String loopId) {
        return new float[]{SwarmDamageStateScene.CENTRE + 0.5f, SwarmDamageStateScene.CENTRE + 0.5f, CELLS_ACROSS};
    }

    @Override
    protected int[] stillFrames(String loopId) {
        // One frame in each phase: whole, wounded, bleeding from every wound, a colony dead, the bore dead.
        return new int[]{1, 3, 4, 6, 10};
    }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        List<SnapshotArtifact> out = new ArrayList<>(super.render(context));
        for (String loop : new String[]{"maw", "mother"}) {
            SceneWorld world = SwarmDamageStateScene.world();
            try (BattleSimulation sim = world.sim()) {
                SwarmBore bore = SwarmDamageStateScene.open(sim, loop);
                SwarmDamageStateScene.hurtTo(sim, bore, SwarmDamageStateScene.DEEP_LEFT);
                float c = SwarmDamageStateScene.CENTRE + 0.5f;
                out.add(bleeding(context, sim, loop, "bleed", c, CELLS_ACROSS));
                out.add(bleeding(context, sim, loop, "bleed-close", c, CLOSE_CELLS_ACROSS));
            }
        }
        return out;
    }

    private static SnapshotArtifact bleeding(SnapshotContext context, BattleSimulation sim, String loop, String name,
                                             float centre, float cellsAcross) throws Exception {
        BattleReviewFrameRenderer camera = new BattleReviewFrameRenderer(context.modRoot(), 1920, 1080,
                centre, centre, cellsAcross).withUnitShadows();
        List<BufferedImage> frames = new ArrayList<>();
        for (int i = 0; i < BLEED_FRAMES; i++) {
            float clock = i * BLEED_STEP_SECONDS;
            frames.add(camera.atClock(clock).render(sim, String.format(Locale.ROOT,
                    "%s / every wound open / %.0f px a cell / clock %.2fs", loop, 1920f / cellsAcross, clock)));
        }
        return SnapshotArtifact.colorAnimation(loop + "-" + name + ".gif", frames, Math.round(BLEED_STEP_SECONDS * 1000));
    }
}
