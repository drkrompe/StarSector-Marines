package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.air.RunwaySortieScene.Scene;
import com.dillon.starsectormarines.battle.air.RunwaySortieScene.Variant;
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
 * Two recordings of one station flying one fighter off its strip.
 *
 * <p>The first is the cycle end to end with nobody interfering: out of the
 * shed, round the hangars, down to the threshold, along the strip, out to the
 * target, gun runs, home, down, and back into the shed. The second is the same
 * cycle at the same seed with a marine fire team sitting on the apron the
 * aircraft has to cross, which is the entire argument for making an aircraft
 * roll instead of lifting vertically off a stand.
 *
 * <p>The caption carries the phase, the hull left on the aircraft, and how many
 * of the platoon it was sent against are still standing. Those three together
 * are the whole trade: the strip costs the fighter a minute in the open, and
 * what it buys is what happens to the people at the far end.
 */
public final class RunwaySortieSnapshotSuite implements SnapshotSuite {

    private static final long SEED = 20260830L;
    private static final int TICKS = 5400;
    private static final int FRAME_EVERY_TICKS = 45;
    private static final int FRAME_DELAY_MILLIS = 90;
    /** Ticks kept rolling after the sortie resolves, so the last frames show the outcome. */
    private static final int TAIL_TICKS = 120;
    /** Sized for a 76x78 map. */
    private static final int WIDTH = 470;
    private static final int HEIGHT = 470;

    @Override public String id() { return "runway-sortie"; }

    @Override public String label() {
        return "Runway: a fighter taxis out, rolls, makes its passes and comes home — clear and contested";
    }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        BattleReviewFrameRenderer renderer =
                new BattleReviewFrameRenderer(context.modRoot(), WIDTH, HEIGHT);
        return List.of(
                record(renderer, "unopposed", Variant.UNOPPOSED),
                record(renderer, "taxiway-ambush", Variant.INTERCEPTED));
    }

    private SnapshotArtifact record(BattleReviewFrameRenderer renderer, String label,
                                    Variant variant) throws Exception {
        Scene scene = RunwaySortieScene.build(SEED, variant);
        BattleSimulation sim = scene.sim();
        List<BufferedImage> frames = new ArrayList<>(TICKS / FRAME_EVERY_TICKS + 1);
        // Runs to the sortie's end rather than the battle's: a scene about one
        // behaviour is never "complete", and stopping on the battle's terms
        // would cut the recording at whichever moment a side happened to be
        // absent.
        int settled = -1;
        for (int tick = 0; tick <= TICKS; tick++) {
            if (tick % FRAME_EVERY_TICKS == 0) {
                frames.add(renderer.render(sim, caption(scene, label, tick)));
            }
            if (settled < 0 && RunwaySortieScene.finished(scene)) settled = tick;
            if (settled >= 0 && tick - settled >= TAIL_TICKS) break;
            sim.advance(BattleSimulation.TICK_DT);
        }
        return SnapshotArtifact.animation(
                "runway-" + label + ".gif", frames, FRAME_DELAY_MILLIS);
    }

    private static String caption(Scene scene, String label, int tick) {
        return String.format(Locale.ROOT, "%s  •  t%-4d  •  %s  •  hull %d%%  •  %d standing",
                label, tick, RunwaySortieScene.phase(scene),
                RunwaySortieScene.hullPercent(scene),
                RunwaySortieScene.targetsStanding(scene));
    }
}
