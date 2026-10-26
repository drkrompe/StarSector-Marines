package com.dillon.starsectormarines.marine;

import java.util.ArrayList;
import java.util.List;

/**
 * What the ship's machine shop can get back out of a spare: none while its machinery is down,
 * half once it runs, three quarters once its tooling is in.
 *
 * <p>The share is of what building that spare costs now, rounded down per line, so recycling
 * can never return more than a rebuild would spend: not with tooling's discount, not with the
 * Mech Lab's spares rack.
 */
public enum Reclamation {
    NONE(0, 1),
    MACHINERY(1, 2),
    TOOLED(3, 4);

    private final int numerator;
    private final int denominator;

    Reclamation(int numerator, int denominator) {
        this.numerator = numerator;
        this.denominator = denominator;
    }

    public boolean works() { return numerator > 0; }

    /** The materials back from one spare whose rebuild costs {@code quote}; null when nothing comes back. */
    public FabricationCost recover(FabricationCost quote) {
        if (quote == null || !works()) return null;
        List<FabricationCost.Line> lines = new ArrayList<>();
        for (FabricationCost.Line line : quote.lines()) {
            int back = (int) ((long) line.quantity() * numerator / denominator);
            if (back > 0) lines.add(new FabricationCost.Line(line.commodityId(), back));
        }
        return lines.isEmpty() ? null : new FabricationCost(lines);
    }
}
