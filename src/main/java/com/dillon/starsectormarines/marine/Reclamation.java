package com.dillon.starsectormarines.marine;

import java.util.List;

/**
 * What the ship's machine shop can get back out of a spare: none while its machinery is down,
 * half once it runs, three quarters once its tooling is in, all of it with a reclamation line.
 *
 * <p>The share is of what building that spare costs now, rounded down, so recycling can never
 * return more than a rebuild would spend: not with tooling's discount, not with the Mech Lab's
 * spares rack.
 */
public enum Reclamation {
    NONE(0, 1),
    MACHINERY(1, 2),
    TOOLED(3, 4),
    /** The reclamation line: every credit of the price back, so trying a part costs nothing. */
    FULL(1, 1);

    private final int numerator;
    private final int denominator;

    Reclamation(int numerator, int denominator) {
        this.numerator = numerator;
        this.denominator = denominator;
    }

    public boolean works() { return numerator > 0; }

    /** The credits back from one spare whose rebuild costs {@code quote}; null when nothing comes back. */
    public FabricationCost recover(FabricationCost quote) {
        if (quote == null || !works()) return null;
        return quote.share(numerator, denominator);
    }

    /**
     * What a whole machine's hull is worth: its price less the prices of the stock parts it was
     * built with, never below nothing. Those parts come back on their own as spares, so counting
     * them in the hull too would pay for them twice. Null when nothing is left.
     */
    public static FabricationCost hull(FabricationCost bill, List<FabricationCost> stockParts) {
        if (bill == null) return null;
        return FabricationCost.of(bill.credits() - FabricationCost.total(stockParts));
    }
}
