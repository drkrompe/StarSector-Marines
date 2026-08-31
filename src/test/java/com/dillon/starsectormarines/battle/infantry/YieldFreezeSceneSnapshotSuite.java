package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.infantry.YieldFreezeScene.Scene;
import com.dillon.starsectormarines.battle.infantry.YieldFreezeScene.Tally;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Two recordings of one squad under one order. The <b>workable</b> loop is the
 * control — the assigned zone holds a defender, so the order is worth having
 * and the squad crosses to it. The <b>yielded</b> loop changes exactly one
 * thing: the room is empty, so {@code ClearAssignedZoneGoal} declines its own
 * order on purpose.
 *
 * <p><b>The number to read is plan-less ticks, and the frames are the weaker
 * evidence here.</b> A squad holding position deliberately looks identical to a
 * frozen one in a picture — neither moves — so the GIFs are for confirming the
 * scene is the scene, and the printed line is the finding. A squad with
 * {@code currentPlan == null} is not resting; it is unable to act and dropping
 * the path it was walking, and only the counter can tell those apart.
 *
 * <p>A fix for the yielded case should drive plan-less ticks to zero
 * <em>without</em> necessarily moving the squad a single cell: a considered
 * hold is the right answer to a yielded order, and a squad that wanders off
 * looking for work has been given the mission-inventing behaviour the noun doc
 * forbids. Ground covered is printed alongside so that failure is visible too.
 *
 * <p><b>What it records today.</b> The yielded loop is plan-less on every one of
 * its 1801 ticks, never holds a goal at all, and does not move one cell: a
 * six-marine squad under orders, unbroken, at full strength, standing still for
 * a minute of battle. The control crosses into the room it was sent to — 23.8
 * cells, holding {@code ClearAssignedZone} — and then falls into the same hole
 * the moment it finishes, going plan-less for good at tick 474 and never
 * recovering. <b>That second reading is the more useful one</b>: it is the whole
 * sequence rather than the endpoint, and it is exactly the position the squad
 * that prompted this work was in — orders completed, nothing beneath them.
 *
 * <p><b>The mech loops answer the same question of the other dispatcher, and
 * the answer is different.</b> A lance under the identical yielded order never
 * loses its goal at all - {@code MechAssignedObjectiveGoal} does not stand down
 * on a zone that turns out to be clear the way its infantry counterpart does,
 * and beneath it the mech engagement floor is both always relevant and always
 * plannable, so that ladder cannot reach an idle bucket. The mech loops are
 * kept because that is worth being able to re-check rather than remember, and
 * because the invariant holding it up lives in the action library where nothing
 * would announce its removal. {@code MechLadderHasAFloorTest} pins it.
 *
 * <p><b>The frames show positions, not terrain.</b> The map is built by hand so
 * the three rooms are exact, and hand-built grids carry no generator art, so the
 * walls do not draw. Read the GIFs to confirm the scene is the scene — six cyan
 * marines west, one red defender in the middle room, one sealed away in the
 * corner — and read the printed line for the finding.
 */
public final class YieldFreezeSceneSnapshotSuite implements SnapshotSuite {

    private static final int TICKS = 1800;
    private static final int FRAME_EVERY_TICKS = 30;
    private static final int FRAME_DELAY_MILLIS = 90;
    private static final int WIDTH = 576;
    private static final int HEIGHT = 224;

    @Override public String id() { return "yield-freeze"; }

    @Override public String label() {
        return "Yielded order: does a squad whose mission goal declines still have a plan?";
    }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        BattleReviewFrameRenderer renderer =
                new BattleReviewFrameRenderer(context.modRoot(), WIDTH, HEIGHT);
        return List.of(
                record(renderer, "workable", true, YieldFreezeScene.Force.INFANTRY),
                record(renderer, "yielded", false, YieldFreezeScene.Force.INFANTRY),
                record(renderer, "mech-workable", true, YieldFreezeScene.Force.MECH),
                record(renderer, "mech-yielded", false, YieldFreezeScene.Force.MECH));
    }

    private SnapshotArtifact record(BattleReviewFrameRenderer renderer, String label,
                                    boolean workable, YieldFreezeScene.Force force)
            throws Exception {
        Scene scene = YieldFreezeScene.build(workable, force);
        List<BufferedImage> frames = new ArrayList<>(TICKS / FRAME_EVERY_TICKS + 1);
        for (int tick = 0; tick <= TICKS; tick++) {
            if (tick % FRAME_EVERY_TICKS == 0) {
                frames.add(renderer.render(scene.sim(), caption(scene, label, tick)));
            }
            YieldFreezeScene.advance(scene, tick);
        }
        Tally tally = scene.tally();
        // Printed as well as drawn, because the finding is a number that a
        // picture of two stationary squads cannot carry.
        System.out.printf(Locale.ROOT,
                "    [yield-freeze] %-14s zone=%d planless=%d/%d (%.0f%%)  planlessForGoodFrom=%s"
                        + "  travelled=%.1f cells  endZone=%d  goal first=%s last=%s  alive=%d%n",
                label, tally.targetZoneId,
                tally.planlessTicks, tally.ticks, percent(tally.planlessTicks, tally.ticks),
                tally.wentPlanlessForGoodTick < 0 ? "never"
                        : String.valueOf(tally.wentPlanlessForGoodTick),
                tally.cellsTravelled,
                scene.sim().getZoneGraph().zoneIdAt((int) scene.lastX, (int) scene.lastY),
                tally.firstGoal, tally.lastGoal, tally.membersAlive);
        return SnapshotArtifact.animation(label + ".gif", frames, FRAME_DELAY_MILLIS);
    }

    private static float percent(int part, int whole) {
        return whole <= 0 ? 0f : 100f * part / whole;
    }

    private static String caption(Scene scene, String label, int tick) {
        Tally tally = scene.tally();
        return String.format(Locale.ROOT, "%s  t=%d  planless=%d  goal=%s",
                label, tick, tally.planlessTicks, tally.lastGoal);
    }
}
