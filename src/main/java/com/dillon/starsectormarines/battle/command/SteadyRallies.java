package com.dillon.starsectormarines.battle.command;

import java.util.Map;
import java.util.TreeMap;

/**
 * Keeps a track's rally from swinging back to the post it has just left.
 *
 * <p>Two answers often apply to one threatened track at once: the band behind a remembered contact and an alarmed
 * fort's works, or the band point and a pressure area's staging cell while that area's pressure wavers about its
 * threshold. They lie the same way, and choosing between them afresh on every plan sent each rallied squad back and
 * forth between the two, every switch a new route. A rally that would return within {@link #KEEP_CELLS} of the post it
 * moved away from less than {@link #DWELL_TICKS} ago keeps the post it has; a post that is genuinely new is taken at
 * once, and so is the old one once the dwell has run out.
 *
 * <p>Per commander and keyed by track; deterministic. Knows nothing of whose side it is on.
 */
final class SteadyRallies<P> {

    /** Two posts this near (Chebyshev cells) are the same post. */
    static final int KEEP_CELLS = 8;
    /** How long after a rally moves it may not move back to the post it left: eight command pulses. UNTUNED. */
    static final int DWELL_TICKS = 600;

    /** Where a post stands. */
    interface Cell<P> {
        int x(P post);

        int y(P post);
    }

    /** Per track: the post now, the post before it (or {@code null}), and the tick it last moved. */
    private final class Memory {
        P now;
        P before;
        int movedTick;
    }

    private final Cell<P> cell;
    private final Map<Integer, Memory> tracks = new TreeMap<>();

    SteadyRallies(Cell<P> cell) {
        this.cell = cell;
    }

    /**
     * The post a track's rally should use this plan, given the one it has just been worked out to be. Returns
     * {@code desired} itself unless it is a swing back, in which case the post the rally already has.
     */
    P steady(int track, P desired, int tick) {
        Memory memory = tracks.get(track);
        if (memory == null) {
            memory = new Memory();
            memory.now = desired;
            memory.movedTick = tick;
            tracks.put(track, memory);
            return desired;
        }
        if (same(desired, memory.now)) return desired;
        if (memory.before != null && same(desired, memory.before) && tick - memory.movedTick < DWELL_TICKS) {
            return memory.now;
        }
        memory.before = memory.now;
        memory.now = desired;
        memory.movedTick = tick;
        return desired;
    }

    private boolean same(P a, P b) {
        return b != null && Math.max(Math.abs(cell.x(a) - cell.x(b)), Math.abs(cell.y(a) - cell.y(b))) <= KEEP_CELLS;
    }
}
