package com.dillon.starsectormarines.battle.ui.panel;

import java.util.Locale;

/**
 * An energy weapon's capacitor, drawn as charge cells under its weapon row. Nothing is counted: the
 * cells fill as the weapon cycles, stand lit when it is ready, and empty one a bolt through a burst.
 * A weapon its flux cannot fund shows every cell held.
 *
 * <p>A weapon that bursts has a cell for each bolt of the burst; one that fires a single shot has
 * {@link #SINGLE_SHOT_CELLS}, so its recharge still reads as a filling bar.
 *
 * @param charge cells' worth of charge, from 0 to {@code cells}
 * @param held whether flux holds the next shot
 */
public record ChargeGauge(int cells, float charge, boolean held) implements AmmoStrip {

    public static final int SINGLE_SHOT_CELLS = 5;
    private static final int MAX_CELLS = 10;

    public enum Cell { CHARGED, CHARGING, EMPTY, HELD }

    public ChargeGauge {
        cells = Math.max(1, cells);
        charge = Math.max(0f, Math.min(cells, charge));
    }

    /**
     * @param boltsLeft bolts still to leave in the burst under way, zero between bursts
     * @param cycleFraction how far the cycle to the next shot has come, 1 when ready
     */
    public static ChargeGauge of(int boltsPerTrigger, int boltsLeft, float cycleFraction, boolean held) {
        int cells = boltsPerTrigger > 1 ? Math.min(MAX_CELLS, boltsPerTrigger) : SINGLE_SHOT_CELLS;
        float charge = boltsLeft > 0 ? (float) boltsLeft * cells / Math.max(cells, boltsPerTrigger)
                : cycleFraction * cells;
        return new ChargeGauge(cells, charge, held);
    }

    public Cell cell(int index) {
        if (held) return Cell.HELD;
        if (index < (int) charge) return Cell.CHARGED;
        return index == (int) charge && charge - index > 0f ? Cell.CHARGING : Cell.EMPTY;
    }

    @Override public int icons() { return cells; }

    @Override public String iconKind() { return "cell"; }

    @Override public String iconState(int index) { return cell(index).name().toLowerCase(Locale.ROOT); }

    @Override public boolean iconGroupEnd(int index) { return false; }

    @Override public int iconWidth() { return 8; }

    @Override public int iconGap() { return 3; }
}
