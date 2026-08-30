package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.infantry.KillingGroundScene.Scene;
import com.dillon.starsectormarines.battle.infantry.KillingGroundScene.Wave;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Two recordings of the same two lanes. The <b>ambushed</b> loop is the
 * question — a squad is destroyed in the west lane and a second is sent up
 * behind it — and the <b>clear</b> loop is the control that shows which lane
 * the pathfinder picks when nothing has happened in either.
 *
 * <p>The caption names each wave's lane; the printed line adds the finer
 * reading, which is how close the follow-up ever came to a fallen marine and
 * how long it spent among them. Read the two loops together. The lane is coarse
 * — these lanes are thirty cells wide, and a squad can sidestep the exact
 * ground its predecessor died on without leaving the lane at all — so a layer
 * that remembers casualties may show up in the distance rather than in the
 * lane, and either counts.
 *
 * <p>What it records today: the control takes the west lane, and so does the
 * follow-up with {@code -Dbattle.pathfinding.casualtyRouteCost=false} - within
 * half a cell of a fallen marine, for seven hundred member-ticks among them.
 * With the costing live the follow-up goes east instead and never comes within
 * fourteen cells of the dead. Run it both ways; the switch is the control, and
 * it is a great deal more honest than an older commit.
 *
 * <p><b>The ground is blank on purpose and the frames are a position record
 * rather than a terrain view.</b> Ground art comes from the generator, and this
 * scene builds its map by hand because the two lanes have to be exact mirrors —
 * a generated map would decide the question by being slightly shorter one side.
 * The cost is that the divider between the lanes does not draw, so the frames
 * show where the squads are and the caption says which lane that is. A reader
 * wanting to see the walls should reach for the printed line, which the suite
 * emits on every run.
 */
public final class KillingGroundSceneSnapshotSuite implements SnapshotSuite {

    private static final int TICKS = 5400;
    private static final int FRAME_EVERY_TICKS = 45;
    private static final int FRAME_DELAY_MILLIS = 90;
    /** Ticks kept rolling after the follow-up commits, so the last frames show it. */
    private static final int TAIL_TICKS = 300;
    private static final int WIDTH = 448;
    private static final int HEIGHT = 448;

    @Override public String id() { return "killing-ground"; }

    @Override public String label() {
        return "Killing ground: does the squad behind you avoid the lane you died in?";
    }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        BattleReviewFrameRenderer renderer =
                new BattleReviewFrameRenderer(context.modRoot(), WIDTH, HEIGHT);
        return List.of(
                record(renderer, "ambushed", true),
                record(renderer, "clear", false));
    }

    private SnapshotArtifact record(BattleReviewFrameRenderer renderer, String label,
                                    boolean ambushed) throws Exception {
        Scene scene = KillingGroundScene.build(ambushed);
        List<BufferedImage> frames = new ArrayList<>(TICKS / FRAME_EVERY_TICKS + 1);
        int settled = -1;
        for (int tick = 0; tick <= TICKS; tick++) {
            if (tick % FRAME_EVERY_TICKS == 0) {
                frames.add(renderer.render(scene.sim(), caption(scene, label, tick)));
            }
            if (settled < 0 && KillingGroundScene.finished(scene)) settled = tick;
            if (settled >= 0 && tick - settled >= TAIL_TICKS) break;
            KillingGroundScene.advance(scene, tick);
        }
        // The finding is a sentence, not a picture: which lane each wave took.
        // Printed as well as drawn so a run reports its own result without
        // anyone having to scrub a GIF frame by frame.
        Wave first = scene.first();
        Wave following = scene.following();
        System.out.printf(Locale.ROOT,
                "    [killing-ground] %-9s frames=%d  wave1 lane=%s died=%s  wave2 %s%n",
                label, frames.size(), lane(first),
                first.destroyedTick < 0 ? "no" : "t" + first.destroyedTick,
                following == null ? "never sent"
                        : "lane=" + lane(following) + " sent=t" + following.spawnedTick
                        + " alive=" + KillingGroundScene.alive(scene.sim(), following.squadId)
                        + " closestToFallen=" + distance(following.closestToFallen)
                        + " memberTicksAmongFallen=" + following.memberTicksAmongFallen
                        + " track=" + track(following));
        if (first.spawnedTick >= 0) {
            System.out.printf(Locale.ROOT,
                    "    [killing-ground] %-9s wave1 track=%s%n",
                    label, track(first));
        }
        return SnapshotArtifact.animation(
                "killing-ground-" + label + ".gif", frames, FRAME_DELAY_MILLIS);
    }

    private static String caption(Scene scene, String label, int tick) {
        Wave first = scene.first();
        Wave following = scene.following();
        return String.format(Locale.ROOT,
                "%s  •  t%-4d  •  wave 1 %s (%d up)  •  wave 2 %s",
                label, tick, lane(first),
                KillingGroundScene.alive(scene.sim(), first.squadId),
                following == null ? "not sent" : lane(following) + " ("
                        + KillingGroundScene.alive(scene.sim(), following.squadId) + " up)");
    }

    /** Where a wave's traffic actually ran between the dividers. */
    private static String track(Wave wave) {
        return wave.trackMinX > wave.trackMaxX ? "—"
                : "x" + wave.trackMinX + ".." + wave.trackMaxX;
    }

    /** Never-measured reads as a dash rather than as a very large number. */
    private static String distance(float value) {
        return value == Float.MAX_VALUE ? "—"
                : String.format(Locale.ROOT, "%.1f", value);
    }

    private static String lane(Wave wave) {
        return switch (wave.lane) {
            case WEST -> "west";
            case EAST -> "east";
            case NEITHER -> "—";
        };
    }
}
