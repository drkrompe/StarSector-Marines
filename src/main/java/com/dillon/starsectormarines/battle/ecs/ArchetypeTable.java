package com.dillon.starsectormarines.battle.ecs;

import java.util.Arrays;

/**
 * The SoA table for one archetype (component-set) — dense, swap-and-pop rows. For
 * each component in the archetype it holds one {@link Column} per field, and
 * {@code rowToEntity} maps each dense row back to its entity id. Grows by doubling,
 * shrinks at a low watermark.
 *
 * <p>Mutators are package-private: {@link EntityWorld} is the sole driver, so all
 * row/location bookkeeping stays in one place. Read accessors are public — systems
 * grab a typed column and walk {@code [0, rowCount())}.
 */
public final class ArchetypeTable {

    private static final int MIN_CAPACITY = 8;

    final long mask;
    /** Components of this archetype, sorted by id (ascending). */
    final ComponentType[] components;
    /** {@code columns[componentSlot][field]}. */
    final Column[][] columns;
    /** componentId -> slot in {@link #components}, or -1. */
    private final int[] slotByComponentId;

    long[] rowToEntity;
    int rowCount;
    private int capacity;

    ArchetypeTable(long mask, ComponentType[] componentsSortedById) {
        this.mask = mask;
        this.components = componentsSortedById;
        this.capacity = MIN_CAPACITY;
        this.rowToEntity = new long[capacity];
        this.columns = new Column[components.length][];
        this.slotByComponentId = new int[64];
        Arrays.fill(slotByComponentId, -1);
        for (int slot = 0; slot < components.length; slot++) {
            ComponentType ct = components[slot];
            slotByComponentId[ct.id] = slot;
            Column[] fieldCols = new Column[ct.fieldCount()];
            for (int f = 0; f < ct.fieldCount(); f++) {
                fieldCols[f] = Column.of(ct.fieldKind(f), capacity);
            }
            columns[slot] = fieldCols;
        }
    }

    // ---- read API (public; systems iterate these) ----

    public int rowCount() { return rowCount; }
    public long entityAt(int row) { return rowToEntity[row]; }
    public boolean has(ComponentType ct) { return (mask & ct.bit()) != 0; }

    public Column column(ComponentType ct, int field) {
        int slot = slotByComponentId[ct.id];
        if (slot < 0) throw new IllegalArgumentException("archetype lacks component " + ct);
        return columns[slot][field];
    }

    public Column.OfInt    ints(ComponentType ct, int field)    { return (Column.OfInt) column(ct, field); }
    public Column.OfLong   longs(ComponentType ct, int field)   { return (Column.OfLong) column(ct, field); }
    public Column.OfFloat  floats(ComponentType ct, int field)  { return (Column.OfFloat) column(ct, field); }
    public Column.OfObject objects(ComponentType ct, int field) { return (Column.OfObject) column(ct, field); }

    // ---- mutators (package-private; EntityWorld drives) ----

    int capacity() { return capacity; }

    int append(long entity) {
        ensureCapacity(rowCount + 1);
        int row = rowCount++;
        rowToEntity[row] = entity;
        clearRow(row);   // appended rows start at defaults; a transition then fills shared fields
        return row;
    }

    /** Swap-and-pop {@code row}; returns the entity moved into {@code row}, or {@code -1} if it was the tail. */
    long swapPop(int row) {
        int last = rowCount - 1;
        long moved = -1L;
        if (row != last) {
            for (Column[] cs : columns) for (Column c : cs) c.copyWithin(row, last);
            rowToEntity[row] = rowToEntity[last];
            moved = rowToEntity[row];
        }
        for (Column[] cs : columns) for (Column c : cs) c.clear(last);   // free the vacated tail
        rowToEntity[last] = 0L;
        rowCount--;
        return moved;
    }

    /** Copy every field of every component shared with {@code src} from {@code src@srcRow} into {@code this@dstRow}. */
    void copySharedFrom(ArchetypeTable src, int srcRow, int dstRow) {
        for (int slot = 0; slot < components.length; slot++) {
            int srcSlot = src.slotByComponentId[components[slot].id];
            if (srcSlot < 0) continue;   // a component only this (dst) table has keeps its default
            Column[] dstCols = columns[slot];
            Column[] srcCols = src.columns[srcSlot];
            for (int f = 0; f < dstCols.length; f++) {
                dstCols[f].copyFrom(srcCols[f], srcRow, dstRow);
            }
        }
    }

    void maybeShrink() {
        if (capacity > MIN_CAPACITY && rowCount < capacity / 4) {
            int nc = Math.max(MIN_CAPACITY, rowCount * 2);
            for (Column[] cs : columns) for (Column c : cs) c.trimTo(nc);
            rowToEntity = Arrays.copyOf(rowToEntity, nc);
            capacity = nc;
        }
    }

    private void ensureCapacity(int need) {
        if (need <= capacity) return;
        int nc = Math.max(need, capacity * 2);
        for (Column[] cs : columns) for (Column c : cs) c.ensureCapacity(nc);
        rowToEntity = Arrays.copyOf(rowToEntity, nc);
        capacity = nc;
    }

    private void clearRow(int row) {
        for (Column[] cs : columns) for (Column c : cs) c.clear(row);
    }
}
