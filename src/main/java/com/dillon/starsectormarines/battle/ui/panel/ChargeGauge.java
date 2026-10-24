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
 * <p>A gun that counts nothing but fires in bursts (a mech's chaingun) is drawn the same way with
 * cartridges for cells ({@link #burst}): one round a shot of the burst, spent as the click's burst
 * goes out, and chambered again over the cycle.
 *
 * <p>A flamer's tank is drawn with the same gauge ({@link #fuel}) in fuel canisters for cells: one a charge
 * of fuel, lit while it is in the tank and dark once the held jet has spent it.
 *
 * @param charge cells' worth of charge, from 0 to {@code cells}
 * @param held whether flux holds the next shot
 * @param rounds whether the cells are drawn as cartridges rather than charge cells
 * @param fuel whether the cells are drawn as fuel canisters
 */
public record ChargeGauge(int cells, float charge, boolean held, boolean rounds, boolean fuel) implements AmmoStrip {

    public static final int SINGLE_SHOT_CELLS = 5;
    private static final int MAX_CELLS = 10;
    private static final int MAX_BURST_ROUNDS = 12;

    public enum Cell { CHARGED, CHARGING, EMPTY, HELD }

    public ChargeGauge {
        cells = Math.max(1, cells);
        charge = Math.max(0f, Math.min(cells, charge));
    }

    public ChargeGauge(int cells, float charge, boolean held, boolean rounds) {
        this(cells, charge, held, rounds, false);
    }

    /**
     * @param boltsLeft bolts still to leave in the burst under way, zero between bursts
     * @param cycleFraction how far the cycle to the next shot has come, 1 when ready
     */
    public static ChargeGauge of(int boltsPerTrigger, int boltsLeft, float cycleFraction, boolean held) {
        int cells = boltsPerTrigger > 1 ? Math.min(MAX_CELLS, boltsPerTrigger) : SINGLE_SHOT_CELLS;
        float charge = boltsLeft > 0 ? (float) boltsLeft * cells / Math.max(cells, boltsPerTrigger)
                : cycleFraction * cells;
        return new ChargeGauge(cells, charge, held, false);
    }

    /**
     * An uncounted gun's burst as rounds: {@code roundsLeft} still to leave in the burst under way,
     * otherwise the burst chambering over the cycle. Null for a gun that does not burst.
     */
    public static ChargeGauge burst(int roundsPerTrigger, int roundsLeft, float cycleFraction) {
        if (roundsPerTrigger <= 1) return null;
        int cells = Math.min(MAX_BURST_ROUNDS, roundsPerTrigger);
        float charge = roundsLeft > 0 ? (float) roundsLeft * cells / roundsPerTrigger : cycleFraction * cells;
        return new ChargeGauge(cells, charge, false, true);
    }

    /** A flamer's tank: {@code capacity} charges, {@code left} of them still in it. */
    public static ChargeGauge fuel(int capacity, int left) {
        int cells = Math.max(1, Math.min(MAX_CELLS, capacity));
        float charge = capacity <= 0 ? 0f : (float) Math.max(0, left) * cells / capacity;
        return new ChargeGauge(cells, charge, false, false, true);
    }

    public Cell cell(int index) {
        if (held) return Cell.HELD;
        if (index < (int) charge) return Cell.CHARGED;
        return index == (int) charge && charge - index > 0f ? Cell.CHARGING : Cell.EMPTY;
    }

    @Override public int icons() { return cells; }

    @Override public String iconKind() { return rounds ? "rifle" : fuel ? "fuel" : "cell"; }

    @Override public String iconState(int index) {
        Cell cell = cell(index);
        if (!rounds) return cell.name().toLowerCase(Locale.ROOT);
        return switch (cell) { case CHARGED -> "live"; case CHARGING -> "loading"; case EMPTY, HELD -> "spent"; };
    }

    @Override public boolean iconGroupEnd(int index) { return false; }

    @Override public int iconWidth() { return rounds ? 10 : 8; }

    @Override public int iconGap() { return rounds ? 4 : 3; }
}
