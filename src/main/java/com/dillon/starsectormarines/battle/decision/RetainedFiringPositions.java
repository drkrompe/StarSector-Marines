package com.dillon.starsectormarines.battle.decision;

import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Predicate;

/** Battle-owned, bounded positive execution choices; never caches hypothetical or negative answers. */
final class RetainedFiringPositions {
    static final int BASE_TTL = 300;
    private final AtomicReferenceArray<Entry> entries;

    record Cell(int x, int y) {}
    record Key(long target, Object assignment, Object goal, boolean constrained, int zone,
               float range, float selfAir, float targetAir, boolean cardinal) {}
    private record Entry(long member, Key key, Cell cell, int pickedTick, int lifetime) {}

    RetainedFiringPositions(int capacity) {
        if (Integer.bitCount(capacity) != 1) throw new IllegalArgumentException("power-of-two capacity required");
        entries = new AtomicReferenceArray<>(capacity);
    }

    Cell lookup(long member, Key key, int tick, Predicate<Cell> usable) {
        int slot = slot(member);
        Entry entry = entries.get(slot);
        if (entry == null || entry.member != member) return null;
        if (!entry.key.equals(key) || tick < entry.pickedTick
                || tick - entry.pickedTick >= entry.lifetime || !usable.test(entry.cell)) {
            entries.compareAndSet(slot, entry, null);
            return null;
        }
        return entry.cell;
    }

    void remember(long member, Key key, int tick, int x, int y) {
        // Fixed slots cap memory across casualties/reinforcements. A collision loses reuse,
        // never identity; atomics avoid a squad/global monitor in parallel unit execution.
        entries.set(slot(member), new Entry(member, key, new Cell(x, y), tick,
                BASE_TTL + Math.floorMod(Long.hashCode(member), 61)));
    }

    void forget(long member) {
        int slot = slot(member);
        Entry entry = entries.get(slot);
        if (entry != null && entry.member == member) entries.compareAndSet(slot, entry, null);
    }

    private int slot(long member) {
        int hash = Long.hashCode(member);
        return (hash ^ (hash >>> 16)) & (entries.length() - 1);
    }
}
