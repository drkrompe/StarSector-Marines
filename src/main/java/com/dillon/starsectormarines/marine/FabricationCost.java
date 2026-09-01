package com.dillon.starsectormarines.marine;

import java.util.List;

/**
 * Base-game cargo commodities consumed by one workshop commit.
 *
 * <p>The seam is the fleet's cargo rather than any one room's: a mech's
 * chassis, a weapon assembly and a boat's plating are all paid for out of the
 * same hold, and the bill has never known which bench it was standing at.
 */
public record FabricationCost(List<Line> lines) {

    public FabricationCost {
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
