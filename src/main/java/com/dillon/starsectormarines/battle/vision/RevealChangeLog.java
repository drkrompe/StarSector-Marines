package com.dillon.starsectormarines.battle.vision;

/**
 * Where the player's reveal bitmap changed, one bounding rectangle per update.
 *
 * <p><b>Why a log and not a flag.</b> A consumer that keeps a picture derived
 * from the reveal bitmap — the resident fog field is the one this exists for —
 * needs to know what to redo, not merely that something happened. A boolean
 * would send it over the whole map; a rectangle sends it over the cells that
 * actually moved. The shape is deliberately {@code CellTopology}'s: a
 * monotonic sequence a reader remembers its own place in, so the reader is
 * read-only and nothing here has to know how many readers there are or when
 * they last looked.
 *
 * <p><b>A rectangle, not a cell list.</b> A vision update is one cohort of
 * contributors recasting their shadowcasts, which is thousands of cells in a
 * handful of blobs; enumerating them would cost more to carry than to redo.
 * What a reader needs is a bound, and the bound is cheap to keep.
 *
 * <p><b>Sealed at the mutation seams, not per cell.</b> {@link #note} widens an
 * open rectangle and {@link #seal} closes it into the ring. Every place the fog
 * service flips a cell's revealed state seals once it is finished, so a reader
 * never sees half of a vision tick.
 *
 * <p><b>It can be outrun.</b> A reader further behind than {@link #capacity}
 * updates is told so by {@link #unionSince} answering false, and rebuilds from
 * the bitmap — the same fallback the resident ground takes when it has fallen
 * off the end of the topology's log.
 */
public final class RevealChangeLog {

    /**
     * Sealed rectangles remembered.
     *
     * <p>Vision updates on every third simulation tick and a render frame reads
     * this at least as often, so a reader is normally one update behind. Sixty
     * four is a battle's worth of slack for a host that has stalled, and small
     * enough that the ring is a few hundred bytes.
     */
    private static final int CAPACITY = 64;

    private final int[] ring = new int[CAPACITY * 4];

    private int width;
    private int height;
    private long sealed;

    private int openMinX = Integer.MAX_VALUE;
    private int openMinY = Integer.MAX_VALUE;
    private int openMaxX = -1;
    private int openMaxY = -1;

    /** Rectangles the ring remembers; a reader further back than this rebuilds. */
    public static int capacity() {
        return CAPACITY;
    }

    /** Binds the log to a grid. Clears whatever it held for the previous one. */
    public void init(int width, int height) {
        this.width = width;
        this.height = height;
        this.sealed = 0L;
        resetOpen();
    }

    /** Records that the cell at this flat index changed. */
    public void note(int cellIndex) {
        if (width <= 0 || cellIndex < 0) return;
        int y = cellIndex / width;
        int x = cellIndex - y * width;
        if (x < openMinX) openMinX = x;
        if (x > openMaxX) openMaxX = x;
        if (y < openMinY) openMinY = y;
        if (y > openMaxY) openMaxY = y;
    }

    /** Records that every cell may have changed — the wholesale case. */
    public void noteAll() {
        if (width <= 0 || height <= 0) return;
        openMinX = 0;
        openMinY = 0;
        openMaxX = width - 1;
        openMaxY = height - 1;
    }

    /**
     * Closes the open rectangle into the ring, if anything was noted.
     *
     * <p>A seal with nothing open is deliberately not an update: a vision tick
     * in which no cell changed leaves {@link #changeCount} where it was, and a
     * reader that is already caught up does no work at all.
     */
    public void seal() {
        if (openMaxX < openMinX) return;
        int at = (int) (sealed % CAPACITY) * 4;
        ring[at] = openMinX;
        ring[at + 1] = openMinY;
        ring[at + 2] = openMaxX;
        ring[at + 3] = openMaxY;
        sealed++;
        resetOpen();
    }

    /** Updates sealed since this log was bound to its grid. */
    public long changeCount() {
        return sealed;
    }

    /**
     * Unions every rectangle sealed at or after {@code from} into {@code out} as
     * {@code {minX, minY, maxX, maxY}}.
     *
     * @return false when the log no longer remembers that far back, or when
     *         {@code from} is ahead of it — either way the caller must rebuild.
     *         True with {@code out[2] < out[0]} means nothing has changed.
     */
    public boolean unionSince(long from, int[] out) {
        if (from < 0L || from > sealed) return false;
        if (sealed - from > CAPACITY) return false;
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = -1;
        int maxY = -1;
        for (long sequence = from; sequence < sealed; sequence++) {
            int at = (int) (sequence % CAPACITY) * 4;
            if (ring[at] < minX) minX = ring[at];
            if (ring[at + 1] < minY) minY = ring[at + 1];
            if (ring[at + 2] > maxX) maxX = ring[at + 2];
            if (ring[at + 3] > maxY) maxY = ring[at + 3];
        }
        out[0] = minX;
        out[1] = minY;
        out[2] = maxX;
        out[3] = maxY;
        return true;
    }

    private void resetOpen() {
        openMinX = Integer.MAX_VALUE;
        openMinY = Integer.MAX_VALUE;
        openMaxX = -1;
        openMaxY = -1;
    }
}
