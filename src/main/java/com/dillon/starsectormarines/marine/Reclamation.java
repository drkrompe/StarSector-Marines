package com.dillon.starsectormarines.marine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the ship's machine shop can get back out of a spare: none while its machinery is down,
 * half once it runs, three quarters once its tooling is in, all of it with a reclamation line.
 *
 * <p>The share is of what building that spare costs now, rounded down per line, so recycling
 * can never return more than a rebuild would spend: not with tooling's discount, not with the
 * Mech Lab's spares rack.
 */
public enum Reclamation {
    NONE(0, 1),
    MACHINERY(1, 2),
    TOOLED(3, 4),
    /** The reclamation line: every unit of the bill back, so trying a part costs nothing. */
    FULL(1, 1);

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

    /**
     * What a whole machine's hull is worth: its bill less the bills of the stock parts it was
     * built with, line by line and never below nothing. Those parts come back on their own as
     * spares, so counting them in the hull too would pay for them twice. Null when nothing is left.
     */
    public static FabricationCost hull(FabricationCost bill, List<FabricationCost> stockParts) {
        if (bill == null) return null;
        Map<String, Integer> left = new LinkedHashMap<>();
        for (FabricationCost.Line line : bill.lines()) left.merge(line.commodityId(), line.quantity(), Integer::sum);
        for (FabricationCost part : stockParts) {
            if (part == null) continue;
            for (FabricationCost.Line line : part.lines()) left.computeIfPresent(line.commodityId(), (id, n) -> n - line.quantity());
        }
        List<FabricationCost.Line> lines = new ArrayList<>();
        left.forEach((id, n) -> { if (n > 0) lines.add(new FabricationCost.Line(id, n)); });
        return lines.isEmpty() ? null : new FabricationCost(lines);
    }
}
