package com.dillon.starsectormarines.battle.world.gen.fit;

import java.util.ArrayList;
import java.util.List;

/**
 * One authored way a room meets the deck's circulation: which bulkhead cells
 * carry its doors, in the fitting's canonical frame.
 *
 * <p>Doors used to be an <em>outcome</em> of placement — the passage search
 * arrived somewhere and that became the door — which left a fill coping with
 * whatever it found. A vehicle bay dealt with a door landing halfway down its
 * side by clearing a band straight through both ranks of gantries, sacrificing
 * two bays to a hatch that could have been at the end.
 *
 * <p>So a room states where it hooks up and the placer satisfies it. Several
 * hookups may be authored as alternatives — a bay entered from both ends and a
 * bay entered amidships are different rooms, not a room and a defect — and the
 * placer takes whichever one the surrounding deck can actually serve.
 *
 * <p>Cells are on the bulkhead ring, so {@code -1} and the far extent are the
 * meaningful coordinates: a door is in the wall, not in the floor. Nothing here
 * mentions mirrors or turns, because {@link RoomPose} carries those — an
 * arrangement is authored once, facing one way.
 *
 * @param slots one doorway each, in priority order; the first is required and
 *     the rest are taken where the deck offers them
 */
public record Hookup(List<DoorSlot> slots) {

    public Hookup {
        if (slots.isEmpty()) throw new IllegalArgumentException("a hookup needs a doorway");
        slots = List.copyOf(slots);
    }

    public static Hookup of(DoorSlot... slots) {
        return new Hookup(List.of(slots));
    }

    /**
     * One doorway, and the bulkhead cells any of which may carry it.
     *
     * <p>A slot rather than a cell because a doorway is two cells wide wherever
     * the deck allows it, and because pinning a door to exactly one cell would
     * fail a placement over a single square of hull plating.
     */
    public record DoorSlot(List<int[]> cells) {

        public DoorSlot {
            if (cells.isEmpty()) throw new IllegalArgumentException("a doorway needs a cell");
            cells = List.copyOf(cells);
        }

        /** A run of cells along a bulkhead, from {@code (x, y)} inclusive. */
        public static DoorSlot run(int x, int y, int spanX, int spanY) {
            List<int[]> cells = new ArrayList<>();
            for (int dx = 0; dx < Math.max(1, spanX); dx++) {
                for (int dy = 0; dy < Math.max(1, spanY); dy++) {
                    cells.add(new int[]{ x + dx, y + dy });
                }
            }
            return new DoorSlot(cells);
        }
    }
}
