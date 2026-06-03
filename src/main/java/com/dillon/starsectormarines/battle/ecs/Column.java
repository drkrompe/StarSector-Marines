package com.dillon.starsectormarines.battle.ecs;

import java.util.Arrays;

/**
 * One field's dense backing store inside an {@link ArchetypeTable} — a primitive
 * array (no boxing) or an object array. Rows are addressed by dense index; the
 * table owns the row count and drives swap-and-pop + archetype transitions through
 * these hooks. The four primitive/object specializations are nested here so the
 * whole column model is one file.
 */
public interface Column {

    /** Grow backing capacity to at least {@code cap} (no-op if already larger). */
    void ensureCapacity(int cap);

    /** Copy {@code srcRow}'s value into {@code dstRow} within this column (swap-and-pop hole fill). */
    void copyWithin(int dstRow, int srcRow);

    /** Copy {@code srcRow} from {@code src} (same kind) into {@code dstRow} here (archetype transition). */
    void copyFrom(Column src, int srcRow, int dstRow);

    /** Reset a row to its default (0 / null) — frees object refs and seeds appended rows. */
    void clear(int row);

    /** Shrink backing capacity toward {@code cap} (no-op if already smaller). */
    void trimTo(int cap);

    /** Create a column of the given kind with an initial capacity. */
    static Column of(FieldKind kind, int capacity) {
        int cap = Math.max(1, capacity);
        switch (kind) {
            case INT:    return new OfInt(cap);
            case LONG:   return new OfLong(cap);
            case FLOAT:  return new OfFloat(cap);
            case OBJECT: return new OfObject(cap);
            default:     throw new IllegalArgumentException("unknown field kind: " + kind);
        }
    }

    final class OfInt implements Column {
        int[] a;
        OfInt(int cap) { a = new int[cap]; }
        public int[] array() { return a; }
        public int get(int row) { return a[row]; }
        public void set(int row, int v) { a[row] = v; }
        public void ensureCapacity(int cap) { if (cap > a.length) a = Arrays.copyOf(a, cap); }
        public void copyWithin(int d, int s) { a[d] = a[s]; }
        public void copyFrom(Column src, int s, int d) { a[d] = ((OfInt) src).a[s]; }
        public void clear(int row) { a[row] = 0; }
        public void trimTo(int cap) { if (a.length > cap) a = Arrays.copyOf(a, Math.max(1, cap)); }
    }

    final class OfLong implements Column {
        long[] a;
        OfLong(int cap) { a = new long[cap]; }
        public long[] array() { return a; }
        public long get(int row) { return a[row]; }
        public void set(int row, long v) { a[row] = v; }
        public void ensureCapacity(int cap) { if (cap > a.length) a = Arrays.copyOf(a, cap); }
        public void copyWithin(int d, int s) { a[d] = a[s]; }
        public void copyFrom(Column src, int s, int d) { a[d] = ((OfLong) src).a[s]; }
        public void clear(int row) { a[row] = 0L; }
        public void trimTo(int cap) { if (a.length > cap) a = Arrays.copyOf(a, Math.max(1, cap)); }
    }

    final class OfFloat implements Column {
        float[] a;
        OfFloat(int cap) { a = new float[cap]; }
        public float[] array() { return a; }
        public float get(int row) { return a[row]; }
        public void set(int row, float v) { a[row] = v; }
        public void ensureCapacity(int cap) { if (cap > a.length) a = Arrays.copyOf(a, cap); }
        public void copyWithin(int d, int s) { a[d] = a[s]; }
        public void copyFrom(Column src, int s, int d) { a[d] = ((OfFloat) src).a[s]; }
        public void clear(int row) { a[row] = 0f; }
        public void trimTo(int cap) { if (a.length > cap) a = Arrays.copyOf(a, Math.max(1, cap)); }
    }

    final class OfObject implements Column {
        Object[] a;
        OfObject(int cap) { a = new Object[cap]; }
        public Object[] array() { return a; }
        public Object get(int row) { return a[row]; }
        public void set(int row, Object v) { a[row] = v; }
        public void ensureCapacity(int cap) { if (cap > a.length) a = Arrays.copyOf(a, cap); }
        public void copyWithin(int d, int s) { a[d] = a[s]; }
        public void copyFrom(Column src, int s, int d) { a[d] = ((OfObject) src).a[s]; }
        public void clear(int row) { a[row] = null; }
        public void trimTo(int cap) { if (a.length > cap) a = Arrays.copyOf(a, Math.max(1, cap)); }
    }
}
