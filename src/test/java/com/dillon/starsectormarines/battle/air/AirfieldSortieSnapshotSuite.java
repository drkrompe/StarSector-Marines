package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.air.AirfieldSortieScene.Scene;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Animated comparison of one reinforcement sortie loading on its own airfield,
 * played twice: once with the crew's walk to the pad unopposed, and once with a
 * marine fire team sitting on it.
 *
 * <p>The caption names the sortie's phase and how many of the crew are still
 * walking, because that is the whole thing worth watching. A shuttle that
 * spawns loaded has no frames between "requested" and "arriving"; this one has
 * a minute of them, and what happens in that minute is the difference between
 * a delivery and a lost sortie.
 */
public final class AirfieldSortieSnapshotSuite implements SnapshotSuite {

    private static final long SEED = 20260828L;
    private static final int TICKS = 4200;
    /** One frame per 30 ticks, matching the other scene recordings. */
    private static final int FRAME_EVERY_TICKS = 45;
    private static final int FRAME_DELAY_MILLIS = 90;
    /** Ticks kept rolling after the sortie resolves, so the last frames show the outcome. */
    private static final int TAIL_TICKS = 120;
    /** Sized for a 64x48 map. Small enough that a hundred frames stay a couple of megabytes. */
    private static final int WIDTH = 448;
    private static final int HEIGHT = 336;

    @Override public String id() { return "airfield-sortie"; }

    @Override public String label() {
        return "Airfield sortie: a crew walks out to its shuttle, opposed and not";
    }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        BattleReviewFrameRenderer renderer =
                new BattleReviewFrameRenderer(context.modRoot(), WIDTH, HEIGHT);
        return List.of(
                record(renderer, "unopposed", false),
                record(renderer, "under-fire", true));
    }

    private SnapshotArtifact record(BattleReviewFrameRenderer renderer, String label,
                                    boolean opposed) throws Exception {
        Scene scene = AirfieldSortieScene.build(SEED, opposed);
        BattleSimulation sim = scene.sim();
        List<BufferedImage> frames = new ArrayList<>(TICKS / FRAME_EVERY_TICKS + 1);
        // Runs to the sortie's end rather than the battle's. A scene with one
        // side on the field is "complete" on its first tick, and stopping there
        // records a single frame of a shuttle sitting on a pad — which is the
        // one thing this recording is not about.
        int settled = -1;
        for (int tick = 0; tick <= TICKS; tick++) {
            if (tick % FRAME_EVERY_TICKS == 0) {
                frames.add(renderer.render(sim, caption(scene, label, tick)));
            }
            if (settled < 0 && AirfieldSortieScene.finished(scene)) settled = tick;
            if (settled >= 0 && tick - settled >= TAIL_TICKS) break;
            sim.advance(BattleSimulation.TICK_DT);
        }
        return SnapshotArtifact.animation(
                "sortie-" + label + ".gif", frames, FRAME_DELAY_MILLIS);
    }

    private static String caption(Scene scene, String label, int tick) {
        return String.format(Locale.ROOT, "%s  •  t%-4d  •  %s  •  %d crew walking",
                label, tick, AirfieldSortieScene.phase(scene),
                AirfieldSortieScene.crewStillWalking(scene));
    }
}
