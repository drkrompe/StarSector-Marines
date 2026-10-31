package com.dillon.starsectormarines.battle.cordon;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A light traffic controller over the convoys' corridor ({@code cordon-nouns.md}, Convoy traffic): the road's
 * <b>tight stretches</b> (the town's truck yard, and where the road narrows so two trucks cannot pass) are each a
 * reservation a convoy must hold to drive into. A convoy short of a stretch it does not hold waits at the stretch's
 * hold point, {@link #HOLD_BACK} cells short of it, until the stretch is clear; it holds the stretch for all its trucks
 * until the last of them is out of it, so a convoy is never split. Convoys going the same way may share a narrow
 * stretch (they cannot meet head-on); the yard, a dead end, takes one convoy at a time. Where convoys going opposite
 * ways ask together, the one going home goes first. Pure: corridor indices and a small table, no pathfinding.
 *
 * <p>No deadlock: stretches closer than a convoy's length and two hold-backs are merged into one, so a convoy waiting
 * at a hold point stands clear of every stretch and holds none; a holder is inside a stretch and never waits on another.
 */
final class CordonTraffic {

    /** Cells short of a stretch a convoy waiting for it stops. */
    static final int HOLD_BACK = 14;

    /** A tight stretch: corridor cells {@code lo..hi}, and whether it takes one convoy at a time (a dead end). */
    record Stretch(int lo, int hi, boolean exclusive) {
        Stretch {
            if (hi < lo) throw new IllegalArgumentException("a stretch runs " + lo + ".." + hi);
        }
    }

    /** A convoy on the road: its number, the way it drives (+1 to the town, -1 home), and its live trucks' cells. */
    record Mover(int number, int direction, int[] at) { }

    private final List<Stretch> stretches;
    /** Each stretch's holders by convoy number, and the way each drives. */
    private final List<Map<Integer, Integer>> holders = new ArrayList<>();

    CordonTraffic(List<Stretch> stretches) {
        this.stretches = List.copyOf(stretches);
        for (int i = 0; i < this.stretches.size(); i++) holders.add(new HashMap<>());
    }

    List<Stretch> stretches() { return stretches; }

    /**
     * The tight stretches, sorted and merged where two lie closer than {@code gap} cells (a convoy's length and two
     * hold-backs), so no hold point falls inside another stretch; merged with a dead end, the stretch is a dead end.
     */
    static List<Stretch> merged(List<Stretch> stretches, int gap) {
        List<Stretch> sorted = new ArrayList<>(stretches);
        sorted.sort(Comparator.comparingInt(Stretch::lo));
        List<Stretch> out = new ArrayList<>();
        for (Stretch s : sorted) {
            if (!out.isEmpty() && s.lo() - out.get(out.size() - 1).hi() < gap) {
                Stretch last = out.remove(out.size() - 1);
                out.add(new Stretch(last.lo(), Math.max(last.hi(), s.hi()), last.exclusive() || s.exclusive()));
            } else {
                out.add(s);
            }
        }
        return out;
    }

    /**
     * The runs of corridor cells where the road is narrower than {@code passing} cells ({@code widths[i]} is the
     * road's open width across cell {@code i}), as stretches. Pure.
     */
    static List<Stretch> narrows(int[] widths, int passing) {
        List<Stretch> out = new ArrayList<>();
        int from = -1;
        for (int i = 0; i <= widths.length; i++) {
            boolean narrow = i < widths.length && widths[i] < passing;
            if (narrow && from < 0) from = i;
            if (!narrow && from >= 0) {
                out.add(new Stretch(from, i - 1, false));
                from = -1;
            }
        }
        return out;
    }

    /**
     * One look at the road: releases what each convoy has driven clear of, grants what convoys reaching a hold point
     * ask for (going home first), and returns, by convoy number, the corridor cell its trucks must not drive past
     * (absent where nothing holds it). Convoys not in {@code movers} let go of everything.
     */
    Map<Integer, Integer> update(List<Mover> movers) {
        Map<Integer, Mover> byNumber = new HashMap<>();
        for (Mover m : movers) byNumber.put(m.number(), m);
        for (int s = 0; s < stretches.size(); s++) {
            Stretch stretch = stretches.get(s);
            holders.get(s).entrySet().removeIf(e -> {
                Mover m = byNumber.get(e.getKey());
                return m == null || m.direction() != e.getValue() && !stretch.exclusive() || clear(stretch, m);
            });
        }
        List<Mover> asking = new ArrayList<>(movers);
        // Going home first: a convoy leaving the yard is never held by one coming in.
        asking.sort(Comparator.comparingInt(Mover::direction).thenComparingInt(Mover::number));
        Map<Integer, Integer> holds = new HashMap<>();
        for (Mover m : asking) {
            int lead = lead(m);
            if (lead == Integer.MIN_VALUE) continue;
            for (int s = 0; s < stretches.size(); s++) {
                Stretch stretch = stretches.get(s);
                Map<Integer, Integer> held = holders.get(s);
                if (held.containsKey(m.number())) continue;
                if (!reaching(stretch, m, lead) && !inside(stretch, m)) continue;
                if (free(stretch, held, m.direction()) || inside(stretch, m)) {
                    held.put(m.number(), m.direction());
                } else {
                    int at = m.direction() > 0 ? stretch.lo() - HOLD_BACK : stretch.hi() + HOLD_BACK;
                    holds.merge(m.number(), at, (a, b) -> m.direction() > 0 ? Math.min(a, b) : Math.max(a, b));
                }
            }
        }
        return holds;
    }

    /** Whether convoy {@code number} holds stretch {@code index}. */
    boolean holds(int index, int number) {
        return holders.get(index).containsKey(number);
    }

    private static boolean free(Stretch stretch, Map<Integer, Integer> held, int direction) {
        if (held.isEmpty()) return true;
        if (stretch.exclusive()) return false;
        for (int d : held.values()) if (d != direction) return false;
        return true;
    }

    /** Whether the convoy's lead is at or past the stretch's hold point and short of its far end. */
    private static boolean reaching(Stretch s, Mover m, int lead) {
        return m.direction() > 0 ? lead >= s.lo() - HOLD_BACK && lead <= s.hi()
                : lead <= s.hi() + HOLD_BACK && lead >= s.lo();
    }

    /** Whether a truck of the convoy is already in the stretch. */
    private static boolean inside(Stretch s, Mover m) {
        for (int a : m.at()) if (a >= s.lo() && a <= s.hi()) return true;
        return false;
    }

    /** Whether every truck of the convoy is past the stretch's far end in its direction. */
    private static boolean clear(Stretch s, Mover m) {
        for (int a : m.at()) if (m.direction() > 0 ? a <= s.hi() : a >= s.lo()) return false;
        return m.at().length > 0;
    }

    private static int lead(Mover m) {
        int lead = Integer.MIN_VALUE;
        for (int a : m.at()) {
            if (lead == Integer.MIN_VALUE || (a - lead) * m.direction() > 0) lead = a;
        }
        return lead;
    }
}
