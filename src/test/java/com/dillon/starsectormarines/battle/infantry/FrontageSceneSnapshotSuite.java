package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.infantry.FrontageScene.Approach;
import com.dillon.starsectormarines.battle.infantry.FrontageScene.Sample;
import com.dillon.starsectormarines.battle.infantry.FrontageScene.Scene;
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
 * Animated review of the defense-frontage scene: one production-stamped
 * compound, a defender garrison inside it, and a marine assault walking in
 * from each of the four edges.
 *
 * <p>The caption names what the garrison is doing at that instant, which is
 * the whole point of watching it — the interesting frames are the ones where
 * the goal changes from patrolling rooms to manning the wall while the assault
 * is still well outside, and the geometry alone cannot show that.
 */
public final class FrontageSceneSnapshotSuite implements SnapshotSuite {

    /** Same seed the scene test asserts against, so the animation and the assertions describe one battle. */
    private static final long SEED = 20260828L;
    /** One garrison per emitted node plus a spread assault, so a recording shows the layered defense rather than one squad. */
    private static final int GARRISON_SQUADS = 4;
    private static final int GARRISON_SIZE = 8;
    private static final int ASSAULT_SQUADS = 3;
    private static final int ASSAULT_SIZE = 8;
    private static final int TICKS = 2400;
    /** One frame per 30 ticks — fine enough to catch the stand-to, coarse enough to stay watchable. */
    private static final int FRAME_EVERY_TICKS = 30;
    private static final int FRAME_DELAY_MILLIS = 90;
    /**
     * Sized for a readable 112-cell map at a few megabytes a loop. Frame area
     * and frame count are the two terms that matter, and both only became
     * measurable once the writer stopped leaving the tail of a previous,
     * longer recording behind the new one.
     */
    private static final int WIDTH = 560;
    private static final int HEIGHT = 560;

    @Override public String id() { return "frontage-scene"; }

    @Override public String label() { return "Defense frontage: garrison stand-to under assault"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        BattleReviewFrameRenderer renderer =
                new BattleReviewFrameRenderer(context.modRoot(), WIDTH, HEIGHT);
        List<SnapshotArtifact> artifacts = new ArrayList<>();
        for (Approach approach : Approach.values()) {
            artifacts.add(record(renderer, approach.renderedEdge(),
                    List.of(approach), ASSAULT_SQUADS));
        }
        // The four-sided case, one squad per edge. Its interest is the opposite
        // of the single-axis loops: those show the defense aim, this shows what
        // aiming everywhere costs and how briefly the wall lasts.
        artifacts.add(record(renderer, "all-sides",
                List.of(Approach.values()), 1));
        return artifacts;
    }

    private SnapshotArtifact record(BattleReviewFrameRenderer renderer, String label,
                                    List<Approach> approaches, int squadsPerApproach)
            throws Exception {
        Scene scene = FrontageScene.build(SEED, GARRISON_SQUADS, GARRISON_SIZE,
                squadsPerApproach, ASSAULT_SIZE, approaches);
        BattleSimulation sim = scene.sim();
        List<BufferedImage> frames = new ArrayList<>(TICKS / FRAME_EVERY_TICKS + 1);
        for (int tick = 0; tick <= TICKS; tick++) {
            if (tick % FRAME_EVERY_TICKS == 0) {
                frames.add(renderer.render(sim, caption(scene, label, tick)));
            }
            if (sim.isComplete()) break;
            sim.advance(BattleSimulation.TICK_DT);
        }
        return SnapshotArtifact.animation(
                "assault-from-" + label + ".gif", frames, FRAME_DELAY_MILLIS);
    }

    /** Tick, the garrison's current goal, and — while it is standing to — how its posts are split. */
    private static String caption(Scene scene, String label, int tick) {
        Sample sample = FrontageScene.sample(scene, tick);
        StringBuilder caption = new StringBuilder(String.format(Locale.ROOT,
                "assault from %s  •  t%-4d  •  %s",
                label, tick, sample.goal()));
        if (sample.posts() > 0) {
            // On one axis the useful figure is how many posts face the threat.
            // On several, that collapses to a single number that cannot say
            // whether a side was left blind, so the multi-axis caption names
            // the least-covered live edge instead.
            caption.append(scene.approaches().size() > 1
                    ? String.format(Locale.ROOT,
                            "  •  %d posts, weakest live side %d  •  %d on post",
                            sample.aperturePosts(), sample.coverageOfWeakestLiveAxis(),
                            sample.membersOnPost())
                    : String.format(Locale.ROOT,
                            "  •  %d posts, %d cover, %d reserve  •  %d on post",
                            sample.aperturePosts(), sample.postsFacingThreat(),
                            sample.reservePosts(), sample.membersOnPost()));
        }
        if (sample.enemyInside()) caption.append("  •  BREACHED");
        return caption.toString();
    }
}
