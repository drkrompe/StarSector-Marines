package com.dillon.starsectormarines.battle.command;

import java.util.ArrayList;
import java.util.List;

/**
 * Which sight lines a set of smoke screens cuts (`ai-nouns.md`, "Smoke screens partition the field"). A screen blocks sight for
 * everyone: it blinds the gun it was laid against, and it equally blinds the side that laid it, its own base of fire and its
 * observers, and it leaves whoever is inside or beyond it without the fire that would otherwise cover them. This class is the
 * one answer to "does this screen stand between those two points", asked by every planner that lays a screen or reasons about
 * one standing, the attacker's {@link SatchelApproach} and, because smoke is public ground whoever threw it, a defender weighing
 * the same cloud, so no two readers can disagree about what a screen cuts.
 *
 * <p>Pure over the clouds it is handed: a cloud is where it stands, how far it blocks, and the tick it stops holding that
 * radius (a thinning cloud is counted only for the stretch it is whole). It reads no unit, no nest and no map: sight beyond
 * the screen itself (walls) is the caller's, as the planner's {@link SatchelApproach.Sight} is.
 */
final class SmokePartition {

    private final List<SatchelApproach.Cloud> clouds;

    SmokePartition(List<SatchelApproach.Cloud> clouds) {
        this.clouds = List.copyOf(clouds);
    }

    /** The clouds standing now, as a side sees them: a cloud's life is the tick it stops holding its radius. */
    static SmokePartition of(List<SmokeCloudFact> standing, int tick, int ticksPerSecond) {
        List<SatchelApproach.Cloud> out = new ArrayList<>();
        for (SmokeCloudFact cloud : standing) {
            out.add(new SatchelApproach.Cloud(cloud.x(), cloud.y(), cloud.radius(),
                    tick + Math.round(cloud.remainingSeconds() * ticksPerSecond)));
        }
        return new SmokePartition(out);
    }

    List<SatchelApproach.Cloud> clouds() {
        return clouds;
    }

    /** This partition with more clouds stood in it: a screen being weighed laid over the ones already there. */
    SmokePartition with(List<SatchelApproach.Cloud> more) {
        List<SatchelApproach.Cloud> all = new ArrayList<>(clouds);
        all.addAll(more);
        return new SmokePartition(all);
    }

    /**
     * Whether the straight line between two points passes through a cloud that holds its radius for at least {@code minLife}
     * more ticks (the time the thing that depends on the line being cut will take).
     */
    boolean cuts(double ax, double ay, double bx, double by, int tick, int minLife) {
        for (SatchelApproach.Cloud cloud : clouds) {
            if (cloud.untilTick() > tick && cloud.untilTick() - tick >= minLife
                    && touches(cloud.x(), cloud.y(), cloud.radius(), ax, ay, bx, by)) return true;
        }
        return false;
    }

    /** As {@link #cuts(double, double, double, double, int, int)}, between the centres of two cells. */
    boolean cuts(int[] a, int[] b, int tick, int minLife) {
        return cuts(a[0] + 0.5, a[1] + 0.5, b[0] + 0.5, b[1] + 0.5, tick, minLife);
    }

    /**
     * The (observer, subject) index pairs this partition cuts: each observer position against each subject position. The partition
     * of a field is exactly this set of pairs, which is what a planner weighing support reads.
     */
    List<int[]> cutPairs(List<int[]> observers, List<int[]> subjects, int tick, int minLife) {
        List<int[]> out = new ArrayList<>();
        for (int i = 0; i < observers.size(); i++) {
            for (int j = 0; j < subjects.size(); j++) {
                if (cuts(observers.get(i), subjects.get(j), tick, minLife)) out.add(new int[] {i, j});
            }
        }
        return out;
    }

    /** Whether the subject is cut from every one of the observers (and there is at least one): it stands alone beyond the screen. */
    boolean isolates(List<int[]> observers, int[] subject, int tick, int minLife) {
        if (observers.isEmpty()) return false;
        for (int[] observer : observers) {
            if (!cuts(observer, subject, tick, minLife)) return false;
        }
        return true;
    }

    /** Whether a cloud of this radius at this centre touches the segment between two points. */
    static boolean touches(double cx, double cy, double radius, double ax, double ay, double bx, double by) {
        return distanceToSegment(cx, cy, ax, ay, bx, by) <= radius;
    }

    static double distanceToSegment(double px, double py, double ax, double ay, double bx, double by) {
        double dx = bx - ax;
        double dy = by - ay;
        double lengthSquared = dx * dx + dy * dy;
        double t = lengthSquared == 0 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / lengthSquared));
        return Math.hypot(px - (ax + t * dx), py - (ay + t * dy));
    }
}
