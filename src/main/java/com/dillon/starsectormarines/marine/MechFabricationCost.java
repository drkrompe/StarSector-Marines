package com.dillon.starsectormarines.marine;

import java.util.List;

/** Base-game cargo commodities consumed by one Mech Lab fabrication commit. */
public record MechFabricationCost(List<Line> lines) {

    public MechFabricationCost {
        lines = List.copyOf(lines);
        if (lines.isEmpty()) throw new IllegalArgumentException("fabrication cost is required");
    }

    public record Line(String commodityId, int quantity) {
        public Line {
            if (commodityId == null || commodityId.isBlank() || quantity <= 0) {
                throw new IllegalArgumentException("commodity id and positive quantity are required");
            }
        }
    }
}
