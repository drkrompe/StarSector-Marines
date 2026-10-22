package com.dillon.starsectormarines.battle.command;

/**
 * Where a company following a walking barrage may stand (`heart-nouns.md`,
 * Following the barrage): a safe distance short of the line that is firing,
 * moving up as each line lifts. Before the first line opens the company forms
 * short of it; once the last has lifted the screen is gone and the ground is
 * the company's.
 *
 * <p>A pure function of the tick, because the barrage's timetable is frozen at
 * the briefing: the commander reads it from its plan, never from the shells.
 *
 * @param advancesAlongY whether the advance runs along the map's y axis
 * @param sign           +1 when the advance runs toward higher coordinates, -1 toward lower
 * @param lineAlong      each line's cell coordinate along the advance, in timetable order
 * @param openTick       the tick each line opens
 * @param liftTick       the tick each line lifts
 * @param safeCells      how far short of the firing line the company stands
 */
public record BarrageScreen(boolean advancesAlongY, int sign, int[] lineAlong, int[] openTick, int[] liftTick,
                            int safeCells) {

    public BarrageScreen {
        if (lineAlong.length != openTick.length || lineAlong.length != liftTick.length)
            throw new IllegalArgumentException("a barrage screen needs an open and a lift for every line");
        lineAlong = lineAlong.clone();
        openTick = openTick.clone();
        liftTick = liftTick.clone();
    }

    /**
     * The coordinate along the advance the company may come up to at {@code tick},
     * or {@link Integer#MIN_VALUE} once the barrage is done and nothing holds it.
     */
    public int holdAlong(int tick) {
        for (int i = 0; i < lineAlong.length; i++) {
            // The line firing now, or the next to open: the company waits short of it.
            if (tick < liftTick[i]) return lineAlong[i] - sign * safeCells;
        }
        return Integer.MIN_VALUE;
    }

    /** Whether {@code along} lies past the hold at {@code tick}, on the barrage's side of it. */
    public boolean beyond(int along, int tick) {
        int hold = holdAlong(tick);
        return hold != Integer.MIN_VALUE && sign * (along - hold) > 0;
    }

    /** A cell's coordinate along the advance. */
    public int along(int x, int y) {
        return advancesAlongY ? y : x;
    }
}
