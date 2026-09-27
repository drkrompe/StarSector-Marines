package com.dillon.starsectormarines.battle.decision.goap.action;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * Bounded, action-local decision cadence, not a firing-position cache. Positive
 * entries delay target reconsideration for 30–36 ticks; negative entries delay
 * another failed position/path search for only 15–20 ticks. Neither stores zone
 * completion, a shot verdict, or a route. The caller validates the live enemy
 * and its zone before using either kind and checks live firing every tick.
 * Positive decisions retain normal movement; negative retries preserve the
 * existing failed-search hold, without resuming a potentially unrelated path.
 *
 * <p>Immutable slots permit concurrent squad-member callbacks without a shared
 * monitor. Collisions discard reuse, never identity checks. Hits allocate
 * nothing and do not slide the expiry; a new decision allocates one entry.
 */
final class ClearZoneDecisions {
    record Entry(long member, Object squad, Object assignment, Object goal,
                 long topology, int tick, int lifetime, int sourceX, int sourceY,
                 long target, int targetX, int targetY, float range, boolean negative) {
        boolean targetMoved(int x, int y) {
            return displaced(targetX, targetY, x, y);
        }
    }

    private final AtomicReferenceArray<Entry> slots;

    ClearZoneDecisions(int capacity) {
        if (capacity < 1 || Integer.bitCount(capacity) != 1) {
            throw new IllegalArgumentException("capacity must be a positive power of two");
        }
        slots = new AtomicReferenceArray<>(capacity);
    }

    Entry lookup(long member, Object squad, Object assignment, Object goal,
                 long topology, int tick, int sourceX, int sourceY, long selectedTarget, float range) {
        int slot = slot(member);
        Entry entry = slots.get(slot);
        if (entry == null || entry.member != member) return null;
        if (entry.squad != squad || !Objects.equals(entry.assignment, assignment)
                || !Objects.equals(entry.goal, goal) || entry.topology != topology
                || tick < entry.tick || (long) tick - entry.tick >= entry.lifetime
                || displaced(entry.sourceX, entry.sourceY, sourceX, sourceY)
                || Float.compare(entry.range, range) != 0
                || (selectedTarget != entry.target && !(entry.negative && selectedTarget == 0L))) {
            slots.compareAndSet(slot, entry, null);
            return null;
        }
        return entry;
    }

    void remember(long member, Object squad, Object assignment, Object goal,
                  long topology, int tick, int sourceX, int sourceY,
                  long target, int targetX, int targetY, float range, boolean negative) {
        int lifetime = negative ? 15 + Math.floorMod(Long.hashCode(member), 6)
                : 30 + Math.floorMod(Long.hashCode(member), 7);
        slots.set(slot(member), new Entry(member, squad, assignment, goal, topology,
                tick, lifetime, sourceX, sourceY, target, targetX, targetY, range, negative));
    }

    void forget(Entry entry) {
        slots.compareAndSet(slot(entry.member), entry, null);
    }

    private int slot(long member) {
        return Long.hashCode(member) & (slots.length() - 1);
    }

    private static boolean displaced(int oldX, int oldY, int x, int y) {
        return Math.abs((long) x - oldX) >= 2 || Math.abs((long) y - oldY) >= 2;
    }
}
