package com.dillon.starsectormarines.marine;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
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

    private Object readResolve() {
        if (counts == null) counts = new HashMap<>();
        counts.values().removeIf(count -> count == null || count <= 0);
        return this;
    }
}
