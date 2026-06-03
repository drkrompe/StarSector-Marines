package com.dillon.starsectormarines.engine.ecs;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;

/**
 * A deferred structural-change buffer for an {@link EntityWorld}.
 *
 * <p>Destroying an entity or moving its archetype (add/remove component) is a
 * swap-and-pop that reorders rows in the table being walked — doing it inside a
 * {@link Query} iteration corrupts the walk (the documented swap-pop trap). So a
 * system records these ops here during the walk and the world applies them in FIFO
 * order at a tick barrier via {@link EntityWorld#flush()}. This formalizes the
 * gather-then-apply idiom into the engine.
 *
 * <p><b>Creates are NOT buffered</b> — {@link EntityWorld#createEntity} is safe
 * during iteration: a new row only ever lands at or past the iterator's captured
 * {@code rowCount} (invisible to the current walk), and a grow reallocs into a new
 * backing array while the captured alias still holds the live rows. Spawn child
 * entities (FX) immediately and set their fields inline.
 *
 * <p>Apply-time guards: a queued add/remove on an entity already destroyed earlier
 * in the same flush is silently skipped (not an error); a double-destroy is a
 * no-op. Ops are encoded into primitive parallel arrays — no per-command garbage.
 * Serial-only, like the rest of the world. Do not queue during a flush.
 */
public final class CommandBuffer {

    static final int DESTROY = 0;
    static final int ADD = 1;
    static final int REMOVE = 2;

    private static final int KIND_SHIFT = 8;
    private static final int ID_MASK = 0xFF;

    private final LongArrayList ids = new LongArrayList();
    private final IntArrayList ops = new IntArrayList();   // (kind << 8) | componentId

    /** Queue {@code entity} for destruction at the next flush. */
    public CommandBuffer destroy(long entity) {
        ids.add(entity);
        ops.add(DESTROY << KIND_SHIFT);
        return this;
    }

    /** Queue adding {@code ct} to {@code entity} (an archetype move) at the next flush. */
    public CommandBuffer add(long entity, ComponentType ct) {
        ids.add(entity);
        ops.add((ADD << KIND_SHIFT) | ct.id);
        return this;
    }

    /** Queue removing {@code ct} from {@code entity} (an archetype move) at the next flush. */
    public CommandBuffer remove(long entity, ComponentType ct) {
        ids.add(entity);
        ops.add((REMOVE << KIND_SHIFT) | ct.id);
        return this;
    }

    public boolean isEmpty() { return ids.isEmpty(); }

    public int size() { return ids.size(); }

    // ---- package-private decode, consumed by EntityWorld.flush() ----

    long entityAt(int i) { return ids.getLong(i); }

    int kindAt(int i) { return ops.getInt(i) >>> KIND_SHIFT; }

    int componentIdAt(int i) { return ops.getInt(i) & ID_MASK; }

    void clear() {
        ids.clear();
        ops.clear();
    }
}
