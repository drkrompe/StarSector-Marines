package com.dillon.starsectormarines.marine;

import com.fs.starfarer.api.impl.campaign.ids.Commodities;

import java.util.List;

/** Cargo bill for mobilizing one complete line squad and its baseline field issue. */
public record SquadFoundingCost(List<Line> lines) {

    public static final SquadFoundingCost STANDARD = new SquadFoundingCost(List.of(
            new Line(Commodities.MARINES, MarineSquad.CAPACITY),
            new Line(Commodities.SUPPLIES, 24),
            new Line(Commodities.HAND_WEAPONS, MarineSquad.CAPACITY),
            new Line(Commodities.FOOD, 24)));

    public SquadFoundingCost {
        lines = List.copyOf(lines);
        if (lines.isEmpty()) throw new IllegalArgumentException("founding cost is required");
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
