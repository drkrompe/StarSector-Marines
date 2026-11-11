package com.dillon.starsectormarines.battle.derelict;

import com.dillon.starsectormarines.battle.derelict.FlockCommandPort.Contact;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.UnaryOperator;

/**
 * What a fallen hull's watch does when nothing has breached it (`fallen-hull-nouns.md`, The watch): a pure rule over
 * the hull zone's armed walkers, decided each pulse. The hull is not a post to hold but a claim to keep, so the watch
 * leaves the bay:
 *
 * <ol>
 *   <li>The bay keeps a small guard ({@link #BAY_GUARD}): siege frames first, then the walkers nearest the mouth.</li>
 *   <li>Every other walker answers the contact on the claim nearest it.</li>
 *   <li>With no contact on the claim, they push toward the loudest fight within it ({@link #loudest}).</li>
 *   <li>A walker goes no farther than {@link #WATCH_REACH_CELLS} off the hull's plates ({@link #clamp}): the trench
 *       and the town's edge. Past that the flocks carry the fight.</li>
 *   <li>With neither, each worker out (a stripper or hauler) takes the nearest free walker as its escort; the rest
 *       stand with the guard.</li>
 * </ol>
 *
 * In the contingency the hull's walkers leave this rule and hold the egress as every derelict line does
 * ({@link WalkerPosture}).
 */
public final class WatchTasking {

    /** How far off the hull's plates, cells, a walker of the watch goes. UNTUNED. */
    public static final int WATCH_REACH_CELLS = 60;
    /** Walkers the bay keeps whatever else is happening. UNTUNED. */
    public static final int BAY_GUARD = 1;
    /** The side, in cells, of the squares fire is counted in when finding the loudest fight. */
    public static final int FIGHT_CELLS = 8;
    /** Seconds a flock ranging over the claim holds each place before moving on. UNTUNED. */
    public static final float RANGE_SECONDS = 12f;

    public enum Kind { GUARD, ANSWER, PUSH, ESCORT }

    /** One armed walker of the watch: its lance, frame and where it stands. */
    public record Watcher(int squad, DerelictWalkerFrame frame, float x, float y) { }

    /** A worker out on the claim: a stripper or hauler's lance and where it stands. */
    public record Worker(int squad, float x, float y) { }

    /** What a walker does: the cell it walks to, and the lance or unit it is about (-1 none). */
    public record Task(Kind kind, int x, int y, long subject) { }

    private WatchTasking() { }

    /**
     * Every watcher's task this pulse, keyed by its lance.
     *
     * @param watch    the hull zone's armed walkers
     * @param bayX     the bay mouth, where the guard stands
     * @param contacts hostile contacts on the claim
     * @param loudest  the loudest fight on the claim, {@code {x, y}}; null when none
     * @param workers  the workers out
     */
    public static Map<Integer, Task> assign(List<Watcher> watch, int bayX, int bayY, List<Contact> contacts,
                                            int[] loudest, List<Worker> workers) {
        return assign(watch, bayX, bayY, contacts, loudest, workers, cell -> cell);
    }

    /**
     * As the other {@code assign}, every walker's cell passed through {@code reach}: where the walker may go on the
     * way to it ({@link #clamp}).
     */
    public static Map<Integer, Task> assign(List<Watcher> watch, int bayX, int bayY, List<Contact> contacts,
                                            int[] loudest, List<Worker> workers, UnaryOperator<int[]> reach) {
        Map<Integer, Task> raw = decide(watch, bayX, bayY, contacts, loudest, workers);
        Map<Integer, Task> out = new LinkedHashMap<>();
        for (Map.Entry<Integer, Task> e : raw.entrySet()) {
            Task t = e.getValue();
            int[] at = reach.apply(new int[] {t.x(), t.y()});
            out.put(e.getKey(), new Task(t.kind(), at[0], at[1], t.subject()));
        }
        return out;
    }

    private static Map<Integer, Task> decide(List<Watcher> watch, int bayX, int bayY, List<Contact> contacts,
                                             int[] loudest, List<Worker> workers) {
        List<Watcher> order = new ArrayList<>(watch);
        order.sort(Comparator.comparingInt(Watcher::squad));
        Map<Integer, Task> out = new LinkedHashMap<>();
        // The guard: siege frames first, then the nearest the mouth.
        List<Watcher> byGuard = new ArrayList<>(order);
        byGuard.sort(Comparator.comparingInt((Watcher w) -> w.frame() == DerelictWalkerFrame.SIEGE ? 0 : 1)
                .thenComparingDouble(w -> distance(w.x(), w.y(), bayX, bayY))
                .thenComparingInt(Watcher::squad));
        for (int i = 0; i < Math.min(BAY_GUARD, byGuard.size()); i++) {
            out.put(byGuard.get(i).squad(), new Task(Kind.GUARD, bayX, bayY, -1L));
        }
        List<Watcher> free = new ArrayList<>();
        for (Watcher w : order) if (!out.containsKey(w.squad())) free.add(w);
        if (!contacts.isEmpty()) {
            for (Watcher w : free) {
                Contact c = nearest(contacts, w.x(), w.y());
                out.put(w.squad(), new Task(Kind.ANSWER, c.cellX(), c.cellY(), c.unitId()));
            }
            return out;
        }
        if (loudest != null) {
            for (Watcher w : free) out.put(w.squad(), new Task(Kind.PUSH, loudest[0], loudest[1], -1L));
            return out;
        }
        List<Worker> outWorkers = new ArrayList<>(workers);
        outWorkers.sort(Comparator.comparingInt(Worker::squad));
        for (Worker worker : outWorkers) {
            Watcher best = null;
            double bestD = Double.MAX_VALUE;
            for (Watcher w : free) {
                double d = distance(w.x(), w.y(), worker.x(), worker.y());
                if (d < bestD) {
                    best = w;
                    bestD = d;
                }
            }
            if (best == null) break;
            free.remove(best);
            out.put(best.squad(), new Task(Kind.ESCORT, (int) Math.floor(worker.x()), (int) Math.floor(worker.y()),
                    worker.squad()));
        }
        for (Watcher w : free) out.put(w.squad(), new Task(Kind.GUARD, bayX, bayY, -1L));
        return out;
    }

    /**
     * The loudest fight on the claim: the {@link #FIGHT_CELLS} square holding the most shots among {@code shots}
     * ({@code {x, y}} each) that land on the claim, as the mean of those shots; null when none do. Ties go to the
     * square lower in y, then x.
     */
    public static int[] loudest(List<float[]> shots, BiPredicate<Integer, Integer> onClaim) {
        Map<Long, float[]> squares = new HashMap<>();   // key -> {count, sumX, sumY, sqX, sqY}
        for (float[] s : shots) {
            int x = (int) Math.floor(s[0]), y = (int) Math.floor(s[1]);
            if (!onClaim.test(x, y)) continue;
            int sx = Math.floorDiv(x, FIGHT_CELLS), sy = Math.floorDiv(y, FIGHT_CELLS);
            float[] q = squares.computeIfAbsent(((long) sy << 32) ^ (sx & 0xffffffffL), k -> new float[] {0, 0, 0, sx, sy});
            q[0]++;
            q[1] += s[0];
            q[2] += s[1];
        }
        float[] best = null;
        for (float[] q : squares.values()) {
            if (best == null || q[0] > best[0] || (q[0] == best[0] && (q[4] < best[4]
                    || (q[4] == best[4] && q[3] < best[3])))) {
                best = q;
            }
        }
        return best == null ? null : new int[] {(int) Math.floor(best[1] / best[0]), (int) Math.floor(best[2] / best[0])};
    }

    /**
     * A cell brought within {@code reach} cells of the nearest of {@code sections}: the cell itself when it lies
     * within, else the point {@code reach} cells out from that section on the straight line toward it.
     */
    public static int[] clamp(List<FallenHullSite.Rect> sections, int reach, int[] cell) {
        FallenHullSite.Rect near = FallenHullSite.Rect.nearestOf(sections, cell[0], cell[1]);
        int span = near.chebyshev(cell[0], cell[1]);
        if (span <= reach) return cell;
        int[] from = near.nearest(cell[0], cell[1]);
        float k = reach / (float) span;
        return new int[] {from[0] + Math.round((cell[0] - from[0]) * k), from[1] + Math.round((cell[1] - from[1]) * k)};
    }

    /**
     * Where a flock ranging over the claim goes now: the places in turn, each held {@link #RANGE_SECONDS}, each flock
     * starting at its own place so two flocks do not range together; null with no places.
     */
    public static int[] rangePoint(List<int[]> places, long flockId, float seconds) {
        if (places.isEmpty()) return null;
        int step = (int) Math.floor(seconds / RANGE_SECONDS);
        int i = (int) Math.floorMod(flockId + step, (long) places.size());
        return places.get(i);
    }

    private static Contact nearest(List<Contact> contacts, float x, float y) {
        Contact best = null;
        double bestD = Double.MAX_VALUE;
        for (Contact c : contacts) {
            double d = distance(c.cellX() + .5f, c.cellY() + .5f, x, y);
            if (d < bestD || (d == bestD && c.unitId() < best.unitId())) {
                best = c;
                bestD = d;
            }
        }
        return best;
    }

    private static double distance(float ax, float ay, float bx, float by) {
        return Math.hypot(ax - bx, ay - by);
    }
}
