package com.dillon.starsectormarines.battle.derelict;

import com.dillon.starsectormarines.battle.command.AssaultCapability;
import com.dillon.starsectormarines.battle.command.CapabilityProfile;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The garrison against the watch (`long-survey-arc.md`, The Line; `stories.md`, The garrison against the watch): which
 * of the garrison's and allies' squads goes after what, the counterpart of {@link WatchTasking}. A pure function of
 * the squads and of what their side sees of the watch, so the rule is proved where it is decided. The battle adapter
 * reads the squads and the threats each pulse and issues the orders as {@link ObjectiveAssignment#attackMove}s.
 *
 * <p>By turns: first each walker that comes within {@link #WALKER_ANSWER_CELLS} of the garrison is answered by the
 * nearest free anti-armour squad (rockets, shells: {@link #antiArmour}); a squad of rifles is no answer to a frame and
 * is not sent at one. Then each flock within {@link #FLOCK_ANSWER_CELLS} is answered by the nearest free squad, a
 * rifle squad before an anti-armour one, since any gun brings a drone down and the rockets are wanted for the frames.
 * Then every squad still free goes at the claim's stakes, in {@link StakeTargets} order (the kill that strands the most
 * first), one squad a stake, the nearest free squad to each; squads beyond the stakes double up on the first. A squad
 * with nothing to answer and no stake standing has no order and keeps the one it has.
 *
 * <p>Fair: the squads are the side's own, and the threats are what the side sees, which the adapter reads; nothing
 * here is read of a hostile beyond its place.
 *
 * <p>Every distance is UNTUNED, chosen to answer a threat near the line rather than chase one across the map.
 */
public final class GarrisonTasking {

    /** How near a walker comes to the garrison, cells, before an anti-armour squad goes after it. UNTUNED. */
    public static final float WALKER_ANSWER_CELLS = 40f;
    /** How near a flock comes to the garrison, cells, before a squad goes after it. UNTUNED. */
    public static final float FLOCK_ANSWER_CELLS = 30f;

    /** What a squad has been sent after. */
    public enum Duty { ARMOUR, AIR, STAKE }

    /** One of the garrison's squads: where it stands and whether it carries anti-armour fire. */
    public record Squad(int id, int x, int y, boolean antiArmour) { }

    /** One squad's order: what it is after, and the cell to attack-move to. */
    public record Order(int squadId, Duty duty, int x, int y) {
        public ObjectiveAssignment assignment() {
            return ObjectiveAssignment.attackMove(squadId, x, y);
        }
    }

    private GarrisonTasking() { }

    /** Whether a squad's kit hurts a frame: direct fire that hurts armour (`CapabilityProfile`). */
    public static boolean antiArmour(CapabilityProfile profile) {
        return profile.has(AssaultCapability.DIRECT_STRUCTURE_FIRE);
    }

    /**
     * The garrison's orders this pulse.
     *
     * @param squads  the garrison's and allies' squads that can fight
     * @param walkers the watch's walkers the side sees, {x, y}
     * @param flocks  the watch's flocks the side sees, {x, y}
     * @param stakes  the claim's standing stakes ({@link FallenHull#stakeTargets}), in order
     */
    public static List<Order> orders(List<Squad> squads, List<int[]> walkers, List<int[]> flocks,
                                     List<StakeTargets.Target> stakes) {
        List<Squad> free = new ArrayList<>(squads);
        free.sort(Comparator.comparingInt(Squad::id));
        List<Order> out = new ArrayList<>();
        for (int[] w : nearestFirst(walkers, free)) {
            Squad s = nearest(free, w, WALKER_ANSWER_CELLS, true);
            if (s == null) continue;
            free.remove(s);
            out.add(new Order(s.id(), Duty.ARMOUR, w[0], w[1]));
        }
        for (int[] f : nearestFirst(flocks, free)) {
            Squad s = nearest(free, f, FLOCK_ANSWER_CELLS, false);
            if (s == null) s = nearest(free, f, FLOCK_ANSWER_CELLS, true);
            if (s == null) continue;
            free.remove(s);
            out.add(new Order(s.id(), Duty.AIR, f[0], f[1]));
        }
        for (StakeTargets.Target t : stakes) {
            if (free.isEmpty()) break;
            Squad s = nearest(free, new int[] {t.x(), t.y()}, Float.POSITIVE_INFINITY, null);
            free.remove(s);
            out.add(new Order(s.id(), Duty.STAKE, t.x(), t.y()));
        }
        if (!stakes.isEmpty()) {
            StakeTargets.Target first = stakes.get(0);
            for (Squad s : free) out.add(new Order(s.id(), Duty.STAKE, first.x(), first.y()));
        }
        out.sort(Comparator.comparingInt(Order::squadId));
        return out;
    }

    /** The threats, the one nearest any squad first (ties by place, so the order is fixed). */
    private static List<int[]> nearestFirst(List<int[]> threats, List<Squad> squads) {
        List<int[]> out = new ArrayList<>(threats);
        out.sort(Comparator.comparingDouble((int[] t) -> nearestDistance(squads, t))
                .thenComparingInt(t -> t[0]).thenComparingInt(t -> t[1]));
        return out;
    }

    private static double nearestDistance(List<Squad> squads, int[] at) {
        double best = Double.POSITIVE_INFINITY;
        for (Squad s : squads) best = Math.min(best, Math.hypot(s.x() - at[0], s.y() - at[1]));
        return best;
    }

    /**
     * The nearest of {@code squads} within {@code reach} of {@code at} whose anti-armour matches {@code armour} (any
     * when null); the lower id on a tie.
     */
    private static Squad nearest(List<Squad> squads, int[] at, float reach, Boolean armour) {
        Squad best = null;
        double bestD = Double.POSITIVE_INFINITY;
        for (Squad s : squads) {
            if (armour != null && s.antiArmour() != armour) continue;
            double d = Math.hypot(s.x() - at[0], s.y() - at[1]);
            if (d > reach || d >= bestD) continue;
            best = s;
            bestD = d;
        }
        return best;
    }
}
