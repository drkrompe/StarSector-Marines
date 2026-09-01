package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.infantry.SwarmOverkillScene.Scene;
import com.dillon.starsectormarines.battle.infantry.SwarmOverkillScene.Tally;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A squad meeting a rush of things one rifle kills.
 *
 * <p><b>The numbers are the finding and the frames are the confirmation.</b>
 * Read marines alive against runners still standing: a squad that survives
 * because the rush ran out of bodies has not done well, and one that clears the
 * ground having lost half its strength has not either. The frames are there to
 * show the scene is the scene — eight cyan marines, twenty red runners closing
 * across open ground — rather than to carry the result.
 *
 * <p>Run the control with
 * {@code -Dbattle.targeting.committedFire=false}, which restores per-body
 * crowding. Nothing else differs between the two.
 */
public final class SwarmOverkillSceneSnapshotSuite implements SnapshotSuite {

    private static final int TICKS = 900;
    private static final int FRAME_EVERY_TICKS = 15;
    private static final int FRAME_DELAY_MILLIS = 70;
    private static final int WIDTH = 640;
    private static final int HEIGHT = 320;

    @Override public String id() { return "swarm-overkill"; }

    @Override public String label() {
        return "Swarm rush: does a squad spread its fire when concentrating it is waste?";
    }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        BattleReviewFrameRenderer renderer =
                new BattleReviewFrameRenderer(context.modRoot(), WIDTH, HEIGHT);
        Scene scene = SwarmOverkillScene.build();
        List<BufferedImage> frames = new ArrayList<>(TICKS / FRAME_EVERY_TICKS + 1);
        for (int tick = 0; tick <= TICKS; tick++) {
            if (tick % FRAME_EVERY_TICKS == 0) {
                frames.add(renderer.render(scene.sim(), caption(scene, tick)));
            }
            SwarmOverkillScene.advance(scene, tick);
        }
        Tally tally = scene.tally();
        System.out.printf(Locale.ROOT,
                "    [swarm-overkill] marines %d/%d alive  runners %d/%d alive"
                        + "  cleared=%s  ticks=%d%n",
                tally.marinesAlive, tally.marinesStarting,
                tally.runnersAlive, tally.runnersStarting,
                tally.clearedTick < 0 ? "never" : String.valueOf(tally.clearedTick),
                tally.ticks);
        return List.of(SnapshotArtifact.animation("rush.gif", frames, FRAME_DELAY_MILLIS));
    }

    private static String caption(Scene scene, int tick) {
        Tally tally = scene.tally();
        return String.format(Locale.ROOT, "swarm rush  t=%d  marines=%d  runners=%d",
                tick, tally.marinesAlive, tally.runnersAlive);
    }
}
