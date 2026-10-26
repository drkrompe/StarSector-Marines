package com.dillon.starsectormarines.marine;

import com.fs.starfarer.api.impl.campaign.ids.Commodities;

import java.util.List;

/**
 * What mobilizing one complete line squad takes: its marines out of cargo, and one credit price
 * for everything else (its stores, supplies and food, bought at a civilized port).
 */
public record SquadFoundingCost(List<Line> lines, int credits) {

    /** Twelve marines, and the 36 supplies and 24 food it was once billed in at their base prices. */
    public static final SquadFoundingCost STANDARD = new SquadFoundingCost(
            List.of(new Line(Commodities.MARINES, MarineSquad.CAPACITY)),
            (int) (FabricationCost.baseValue(Commodities.SUPPLIES, 36) + FabricationCost.baseValue(Commodities.FOOD, 24)));

    public SquadFoundingCost {
        lines = List.copyOf(lines);
        if (lines.isEmpty()) throw new IllegalArgumentException("founding cost is required");
        if (credits < 0) throw new IllegalArgumentException("price cannot be negative");
    }

    public record Line(String commodityId, int quantity) {
        public Line {
            if (commodityId == null || commodityId.isBlank() || quantity <= 0) {
                throw new IllegalArgumentException(
                        "commodity id and positive quantity are required");
            }
        }
    }
}
