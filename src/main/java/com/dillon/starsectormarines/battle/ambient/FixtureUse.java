package com.dillon.starsectormarines.battle.ambient;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.task.TaskPoint;
import com.dillon.starsectormarines.battle.task.TaskPointService;
import com.dillon.starsectormarines.battle.world.model.Doodad;

import java.util.ArrayList;
import java.util.List;

/**
 * Whether somebody is at work on a prop: the one fact a machine that runs only while it is worked needs, and the one
 * a crew member's work motion and the prop's own motion have to agree on.
 *
 * <p>A prop is worked through its task points, the exclusive stands whose focus lies on its footprint. A point is
 * worked when its claimant has that point's job in hand; an actor still walking to the point, or one whose rotation
 * has taken them elsewhere with the claim not yet released, is not working it. So a treadmill runs while somebody is
 * on it rather than while somebody means to be.
 */
public final class FixtureUse {

    private FixtureUse() {}

    /** The published points whose work is done to {@code prop}: their focus lies on its footprint. */
    public static List<TaskPoint> pointsOn(TaskPointService points, Doodad prop) {
        List<TaskPoint> on = new ArrayList<>(2);
        for (TaskPoint point : points.points()) {
            if (prop.occupiesCell((int) Math.floor(point.focusX()), (int) Math.floor(point.focusY()))) on.add(point);
        }
        return on;
    }

    /** Whether {@code point}'s claimant is at its work now. */
    public static boolean worked(TaskPointService points, AmbientTaskService ambient, TaskPoint point) {
        long actor = points.claimant(point.id());
        if (actor == 0L) return false;
        String job = ambient.jobInHand(actor);
        return job != null && job.equals(point.group());
    }

    /** Whether any of {@code on} is being worked now. */
    public static boolean anyWorked(TaskPointService points, AmbientTaskService ambient, List<TaskPoint> on) {
        for (TaskPoint point : on) if (worked(points, ambient, point)) return true;
        return false;
    }

    /** Whether somebody is at work on {@code prop} in {@code sim}. Scans every point; cache {@link #pointsOn} to ask often. */
    public static boolean inUse(BattleSimulation sim, Doodad prop) {
        return anyWorked(sim.taskPoints(), sim.ambientTasks(), pointsOn(sim.taskPoints(), prop));
    }
}
