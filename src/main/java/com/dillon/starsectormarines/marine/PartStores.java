package com.dillon.starsectormarines.marine;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * The spare parts a company holds for its machines, by part id: what is on the shelf, never what is fitted
 * (mechs-nouns.md, stores; owner: share how equipment works). The Mech Lab's stores ({@link MechBay}) and the Shuttle
 * Deck's ({@link BoatDeck}) are each one of these, and both keep it the same way: a fit takes a spare out, and
 * whatever comes off a machine is stowed as a spare. What is fitted is a fact about the machine, so the two are never
 * counted against each other.
 *
 * <p>Plain serializable data, persisted with its owner. A count never goes below zero, and an id with none held is
 * not kept.
 */
public final class PartStores implements Serializable {
    private static final long serialVersionUID = 1L;

    private Map<String, Integer> counts = new HashMap<>();

    /** How many spares of {@code partId} are held. */
    public int count(String partId) {
        if (partId == null) return 0;
        return Math.max(0, counts.getOrDefault(partId, 0));
    }

    /** Puts {@code quantity} spares of {@code partId} on the shelf; nothing for a null id or no quantity. */
    public void stow(String partId, int quantity) {
        if (partId == null || quantity <= 0) return;
        counts.merge(partId, quantity, Integer::sum);
    }

    public void stow(String partId) {
        stow(partId, 1);
    }

    /** Takes one spare of {@code partId} off the shelf; false, and nothing taken, when none is held. */
    public boolean take(String partId) {
        int held = count(partId);
        if (held <= 0) return false;
        if (held == 1) counts.remove(partId);
        else counts.put(partId, held - 1);
        return true;
    }

    /** Every part id with a spare held. */
    public Set<String> ids() {
        return Collections.unmodifiableSet(counts.keySet());
    }

    /** How many spares are held, of every part. */
    public int total() {
        int total = 0;
        for (int count : counts.values()) total += Math.max(0, count);
        return total;
    }

    /** Empties the shelf; returns how many spares were on it. */
    public int clear() {
        int lost = total();
        counts.clear();
        return lost;
    }

    /** Carries every spare over to {@code current}'s id for it (a renamed part), merging counts that meet. */
    public void rekey(UnaryOperator<String> current) {
        Map<String, Integer> rekeyed = new HashMap<>();
        counts.forEach((id, count) -> {
            String now = current.apply(id);
            if (now != null && count != null && count > 0) rekeyed.merge(now, count, Integer::sum);
        });
        counts = rekeyed;
    }

    /** Drops every spare whose id {@code retired} names (a part no longer in the game). */
    public void dropIf(Predicate<String> retired) {
        counts.keySet().removeIf(retired);
    }

    /**
     * One shelf a refit settles against, and how to count, on the machine being refitted, the parts that shelf keeps
     * (part id to how many are fitted). A shuttle has one shelf; a mech or a vehicle keeps its weapons apart from its
     * internals.
     */
    public record Shelf(PartStores stores, Supplier<Map<String, Integer>> fitted) { }

    /**
     * Runs one refit of a machine and settles each shelf by what it changed on the machine: every part that went on
     * is taken off its shelf and every part that came off is stowed (mechs-nouns.md, stores). One rule for every fit,
     * strip and move, on a mech, a vehicle and a shuttle. The caller checks first that the shelf holds what goes on.
     *
     * @param change the refit itself; false when it refused, which leaves the shelves untouched
     * @return whether the refit happened
     */
    public static boolean refit(BooleanSupplier change, Shelf... shelves) {
        List<Map<String, Integer>> before = new ArrayList<>(shelves.length);
        for (Shelf shelf : shelves) before.add(Map.copyOf(shelf.fitted().get()));
        if (!change.getAsBoolean()) return false;
        for (int i = 0; i < shelves.length; i++) {
            shelves[i].stores().settle(before.get(i), shelves[i].fitted().get());
        }
        return true;
    }

    /** Counts part ids, null entries left out: what a machine carries, for a {@link Shelf}. */
    public static Map<String, Integer> tally(Iterable<String> ids) {
        Map<String, Integer> counts = new HashMap<>();
        for (String id : ids) if (id != null) counts.merge(id, 1, Integer::sum);
        return counts;
    }

    private void settle(Map<String, Integer> before, Map<String, Integer> after) {
        Set<String> ids = new HashSet<>(before.keySet());
        ids.addAll(after.keySet());
        for (String id : ids) {
            int change = after.getOrDefault(id, 0) - before.getOrDefault(id, 0);
            if (change < 0) stow(id, -change);
            for (int i = 0; i < change; i++) {
                if (!take(id)) throw new IllegalStateException("a refit fitted a part stores did not hold: " + id);
            }
        }
    }

    private Object readResolve() {
        if (counts == null) counts = new HashMap<>();
        counts.values().removeIf(count -> count == null || count <= 0);
        return this;
    }
}
